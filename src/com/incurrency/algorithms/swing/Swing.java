/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.algorithms.swing;

import com.incurrency.algorithms.pairs.Pairs;
import com.incurrency.RatesClient.Subscribe;
import com.incurrency.framework.Algorithm;
import com.incurrency.framework.BeanConnection;
import com.incurrency.framework.BeanSymbol;
import com.incurrency.framework.DateUtil;
import com.incurrency.framework.EnumBarSize;
import com.incurrency.framework.EnumOrderReason;
import com.incurrency.framework.EnumOrderSide;
import com.incurrency.framework.EnumOrderStage;
import com.incurrency.framework.EnumOrderType;
import com.incurrency.framework.EnumSource;
import com.incurrency.framework.HistoricalBars;
import com.incurrency.framework.MainAlgorithm;
import com.incurrency.framework.MatrixMethods;
import com.incurrency.framework.Parameters;
import com.incurrency.framework.ReservedValues;
import com.incurrency.framework.Strategy;
import com.incurrency.framework.TradeEvent;
import com.incurrency.framework.TradeListener;
import com.incurrency.framework.TradingUtil;
import com.incurrency.framework.Utilities;
import com.incurrency.indicators.Indicators;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jblas.DoubleMatrix;
import org.rosuda.REngine.REXP;
import org.rosuda.REngine.Rserve.RConnection;
import static com.incurrency.framework.MatrixMethods.*;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 *
 * @author pankaj
 */
public class Swing extends Strategy implements TradeListener {

    private static final Logger logger = Logger.getLogger(Pairs.class.getName());
    String expiryNearMonth;
    String expiryFarMonth;
    String referenceCashType;
    String rServerIP;
    String parameterObjectPath;
    Date entryScanDate;
    double stopLoss;
    double takeProfit;
    double upProbabilityThreshold;
    double downProbabilityThreshold;
    String cassandraMetric;
    String[] timeSeries;
    long openTime;
    boolean customRange = false;
    Timer eodProcessing;
    boolean testing = false;
    boolean portfolio=false;
    boolean optimalThreshold=false;
    double sensitivityThreshold=0.5;
    double specificityThreshold=0.5;
    double exitLaziness=0;
    int maxPositions=0;
    int positionCount=0;
    TreeMap<Double,Integer> longPositionScore=new TreeMap<>();
    TreeMap<Double,Integer> shortPositionScore=new TreeMap<>();


    public Swing(MainAlgorithm m, Properties p, String parameterFile, ArrayList<String> accounts, Integer stratCount) {
        super(m, "swing", "FUT", p, parameterFile, accounts, stratCount);
        loadParameters(p);
        File dir = new File("logs");
        File f = new File(dir, getStrategy() + ".csv");
        if (!f.exists()) {
            TradingUtil.writeToFile(getStrategy() + ".csv", "trend,daysinupswing,daysindownswing,daysoutsidetrend,daysintrend,closezscore,highzscore,lowzscore,mazscore,nextdayprob,y");
        }

        String[] tempStrategyArray = parameterFile.split("\\.")[0].split("-");
        for (BeanConnection c : Parameters.connection) {
            c.getWrapper().addTradeListener(this);
            c.initializeConnection(tempStrategyArray[tempStrategyArray.length - 1]);
        }
        if (Subscribe.tes != null) {
            Subscribe.tes.addTradeListener(this);
        }
        Calendar calToday = Calendar.getInstance(TimeZone.getTimeZone(Algorithm.timeZone));
        String hdEndDate = DateUtil.getFormatedDate("yyyyMMdd HH:mm:ss", calToday.getTimeInMillis(), TimeZone.getTimeZone(Algorithm.timeZone));
        calToday.set(Calendar.HOUR_OF_DAY, Algorithm.openHour);
        calToday.set(Calendar.MINUTE, Algorithm.openMinute);
        calToday.set(Calendar.SECOND, 0);
        calToday.set(Calendar.MILLISECOND, 0);
        openTime = calToday.getTimeInMillis();
        calToday.add(Calendar.YEAR, -5);
        String hdStartDate = DateUtil.getFormatedDate("yyyyMMdd HH:mm:ss", calToday.getTimeInMillis(), TimeZone.getTimeZone(Algorithm.timeZone));
        try {
            for (BeanSymbol s : Parameters.symbol) {
                if (s.getType().equals("STK") || s.getType().equals("IND")) {
                    Thread t = new Thread(new HistoricalBars(s, EnumSource.CASSANDRA, timeSeries, cassandraMetric, hdStartDate, hdEndDate, EnumBarSize.DAILY, false));
                    t.start();
                    while (t.getState() != Thread.State.TERMINATED) {
                        Thread.sleep(1000);
                    }
                }
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, null, e);
        }
        if (testing) {
            Calendar tmpCalendar = Calendar.getInstance(TimeZone.getTimeZone(Algorithm.timeZone));
            tmpCalendar.add(Calendar.HOUR, 15);
            entryScanDate = tmpCalendar.getTime();
        }

        eodProcessing = new Timer("Timer: Close Positions");
        eodProcessing.schedule(eodProcessingTask, entryScanDate);
        if(this.getLongOnly()){
        positionCount=Utilities.openPositionCount(Parameters.symbol, this.getOrderFile(), this.getStrategy(), this.getPointValue(), true);
        }
        if(this.getShortOnly()){
        positionCount=positionCount+Utilities.openPositionCount(Parameters.symbol, this.getOrderFile(), this.getStrategy(), this.getPointValue(), false);
        }
        logger.log(Level.INFO,"100,StrategyParameters,{0}",new Object[]{getStrategy() + delimiter + "Max Open Position" + delimiter + maxPositions});
        logger.log(Level.INFO,"100,StrategyParameters,{0}",new Object[]{getStrategy() + delimiter + "Current Open Position" + delimiter + positionCount});
        logger.log(Level.INFO,"100,StrategyParameters,{0}",new Object[]{getStrategy() + delimiter + "Portfolio Mode" + delimiter + portfolio});

    }

    private void loadParameters(Properties p) {
        expiryNearMonth = p.getProperty("NearMonthExpiry").toString().trim();
        expiryFarMonth = p.getProperty("FarMonthExpiry").toString().trim();
        referenceCashType = p.getProperty("ReferenceCashType", "STK").toString().trim();
        rServerIP = p.getProperty("RServerIP").toString().trim();
        parameterObjectPath = p.getProperty("ParameterObjectPath").toString().trim();
        String entryScanTime = p.getProperty("EntryScanTime");
        Calendar calToday = Calendar.getInstance(TimeZone.getTimeZone(Algorithm.timeZone));
        String[] entryTimeComponents = entryScanTime.split(":");
        calToday.set(Calendar.HOUR_OF_DAY, Utilities.getInt(entryTimeComponents[0], 15));
        calToday.set(Calendar.MINUTE, Utilities.getInt(entryTimeComponents[1], 20));
        calToday.set(Calendar.SECOND, Utilities.getInt(entryTimeComponents[2], 0));
        entryScanDate = calToday.getTime();
        stopLoss = Utilities.getDouble(p.getProperty("StopLoss", "0.01"), 0.01);
        takeProfit = Utilities.getDouble(p.getProperty("TakeProfit", "0.05"), 0.05);
        upProbabilityThreshold = Utilities.getDouble(p.getProperty("UpProbabilityThreshold", "0.70"), 0.7);
        downProbabilityThreshold = Utilities.getDouble(p.getProperty("DownProbabilityThreshold", "0.3"), 0.3);
        timeSeries = p.getProperty("timeseries", "").toString().trim().split(",");
        cassandraMetric = p.getProperty("cassandrametric", "").toString().trim();
        customRange = Boolean.parseBoolean(p.getProperty("UseCustomDateRangeForTraining", "false").toString().trim());
        testing = Boolean.parseBoolean(p.getProperty("Testing", "false").toString().trim());
        portfolio = Boolean.parseBoolean(p.getProperty("Portfolio", "false").toString().trim());
        maxPositions = Integer.parseInt(p.getProperty("MaxPositions", "1").toString().trim());
        optimalThreshold=Boolean.parseBoolean(p.getProperty("OptimalThreshold", "false").toString().trim());
        sensitivityThreshold=Utilities.getDouble(p.getProperty("SensitivityThreshold"), 0.5);
        specificityThreshold=Utilities.getDouble(p.getProperty("SpecificityThreshold"),0.5);
        exitLaziness=Utilities.getDouble(p.getProperty("ExitLaziness"), 0);
    }

    @Override
    public void tradeReceived(TradeEvent event) {
        Integer id = event.getSymbolID() - 1;
        if (this.getStrategySymbols().contains(id) && !Parameters.symbol.get(id).getType().equals(referenceCashType)) {
            if (this.getPosition().get(id).getPosition() > 0) {
                Double tradePrice = this.getPosition().get(id).getPrice();
                if (Parameters.symbol.get(id).getLastPrice() != 0 && Parameters.symbol.get(id).getLastPrice() <= tradePrice * (1 - stopLoss / 100)) {
                    int size = this.getPosition().get(id).getPosition();
                    this.entry(id, EnumOrderSide.SELL, size, EnumOrderType.LMT, Parameters.symbol.get(id).getLastPrice(), 0, EnumOrderReason.REGULAREXIT, EnumOrderStage.INIT, this.getMaxOrderDuration(), this.getDynamicOrderDuration(), this.getMaxSlippageExit(), "", "GTC", "", false, true);
                }
            } else if (this.getPosition().get(id).getPosition() < 0) {
                Double tradePrice = this.getPosition().get(id).getPrice();
                if (Parameters.symbol.get(id).getLastPrice() != 0 && Parameters.symbol.get(id).getLastPrice() >= tradePrice * (1 + stopLoss / 100)) {
                    int size = this.getPosition().get(id).getPosition();
                    this.entry(id, EnumOrderSide.COVER, size, EnumOrderType.LMT, Parameters.symbol.get(id).getLastPrice(), 0, EnumOrderReason.REGULAREXIT, EnumOrderStage.INIT, this.getMaxOrderDuration(), this.getDynamicOrderDuration(), this.getMaxSlippageExit(), "", "GTC", "", false, true);
                }
            }
        }
    }
    TimerTask eodProcessingTask = new TimerTask() {
        @Override
        public void run() {
            scan();
        }
    };

    public void scan() {
        SimpleDateFormat sdf_yyyyMMdd = new SimpleDateFormat("yyyyMMdd");
        String currentDay = sdf_yyyyMMdd.format(getStartDate());
        boolean rollover = false;
        try {
            Date today = sdf_yyyyMMdd.parse(currentDay);
            Date expiry = sdf_yyyyMMdd.parse(expiryNearMonth);
            if (today.compareTo(expiry) >= 0) {
                rollover = true;
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, null, e);
        }
        for (BeanSymbol s : Parameters.symbol) {
            if (!s.getType().equals(referenceCashType) && !s.getExpiry().equals(expiryFarMonth)) {
                int id = s.getSerialno() - 1;
                int referenceid = Utilities.getReferenceID(Parameters.symbol, id, referenceCashType);
                BeanSymbol sRef = Parameters.symbol.get(referenceid);
                //add current bar'sRef data to matrix
                if (sRef.getTimeSeriesLength(EnumBarSize.DAILY) > -1 && sRef.getTimeSeriesLength(EnumBarSize.DAILY) > 0) {
                    sRef.setTimeSeries(EnumBarSize.DAILY, openTime, new String[]{"open", "high", "low", "settle", "volume"}, new double[]{sRef.getOpenPrice(), sRef.getHighPrice(), sRef.getLowPrice(), sRef.getLastPrice(), sRef.getVolume()});
                    DoubleMatrix dtrend = Indicators.swing(sRef, EnumBarSize.DAILY).getTimeSeries(EnumBarSize.DAILY, "trend");
                    int[] indices = dtrend.ne(ReservedValues.EMPTY).findIndices();
                    DoubleMatrix dclose = sRef.getTimeSeries(EnumBarSize.DAILY, "settle");
                    indices = Utilities.addArraysNoDuplicates(indices, dclose.ne(ReservedValues.EMPTY).findIndices());
                    DoubleMatrix dhigh = sRef.getTimeSeries(EnumBarSize.DAILY, "high");
                    indices = Utilities.addArraysNoDuplicates(indices, dhigh.ne(ReservedValues.EMPTY).findIndices());
                    DoubleMatrix dlow = sRef.getTimeSeries(EnumBarSize.DAILY, "low");
                    indices = Utilities.addArraysNoDuplicates(indices, dlow.ne(ReservedValues.EMPTY).findIndices());
                    DoubleMatrix ddaysinupswing = sRef.getTimeSeries(EnumBarSize.DAILY, "daysinupswing");
                    indices = Utilities.addArraysNoDuplicates(indices, ddaysinupswing.ne(ReservedValues.EMPTY).findIndices());
                    DoubleMatrix ddaysindownswing = sRef.getTimeSeries(EnumBarSize.DAILY, "daysindownswing");
                    indices = Utilities.addArraysNoDuplicates(indices, ddaysindownswing.ne(ReservedValues.EMPTY).findIndices());
                    DoubleMatrix ddaysoutsidetrend = sRef.getTimeSeries(EnumBarSize.DAILY, "daysoutsidetrend");
                    indices = Utilities.addArraysNoDuplicates(indices, ddaysoutsidetrend.ne(ReservedValues.EMPTY).findIndices());
                    DoubleMatrix ddaysintrend = sRef.getTimeSeries(EnumBarSize.DAILY, "daysintrend");
                    indices = Utilities.addArraysNoDuplicates(indices, ddaysintrend.ne(ReservedValues.EMPTY).findIndices());
                    DoubleMatrix ddaysindowntrend = sRef.getTimeSeries(EnumBarSize.DAILY, "daysindowntrend");
                    indices = Utilities.addArraysNoDuplicates(indices, ddaysindowntrend.ne(ReservedValues.EMPTY).findIndices());
                    DoubleMatrix ddaysinuptrend = sRef.getTimeSeries(EnumBarSize.DAILY, "daysinuptrend");
                    indices = Utilities.addArraysNoDuplicates(indices, ddaysinuptrend.ne(ReservedValues.EMPTY).findIndices());
                    DoubleMatrix dstickytrend = sRef.getTimeSeries(EnumBarSize.DAILY, "stickytrend");
                    indices = Utilities.addArraysNoDuplicates(indices, dstickytrend.ne(ReservedValues.EMPTY).findIndices());
                    DoubleMatrix dfliptrend = sRef.getTimeSeries(EnumBarSize.DAILY, "fliptrend");
                    indices = Utilities.addArraysNoDuplicates(indices, dfliptrend.ne(ReservedValues.EMPTY).findIndices());
                    DoubleMatrix dupdownbar = sRef.getTimeSeries(EnumBarSize.DAILY, "updownbar");
                    indices = Utilities.addArraysNoDuplicates(indices, dupdownbar.ne(ReservedValues.EMPTY).findIndices());
                    DoubleMatrix dupdownbarclean = sRef.getTimeSeries(EnumBarSize.DAILY, "updownbarclean");
                    indices = Utilities.addArraysNoDuplicates(indices, dupdownbarclean.ne(ReservedValues.EMPTY).findIndices());
                    //Remove NA values
                    dtrend = MatrixMethods.getSubSetVector(dtrend, indices);
                    dclose = MatrixMethods.getSubSetVector(dclose, indices);
                    dhigh = MatrixMethods.getSubSetVector(dhigh, indices);
                    dlow = MatrixMethods.getSubSetVector(dlow, indices);
                    ddaysinupswing = MatrixMethods.getSubSetVector(ddaysinupswing, indices);
                    ddaysindownswing = MatrixMethods.getSubSetVector(ddaysindownswing, indices);
                    ddaysoutsidetrend = MatrixMethods.getSubSetVector(ddaysoutsidetrend, indices);
                    ddaysintrend = MatrixMethods.getSubSetVector(ddaysintrend, indices);
                    ddaysindowntrend = MatrixMethods.getSubSetVector(ddaysindowntrend, indices);
                    ddaysinuptrend = MatrixMethods.getSubSetVector(ddaysinuptrend, indices);
                    dstickytrend = MatrixMethods.getSubSetVector(dstickytrend, indices);
                    dfliptrend = MatrixMethods.getSubSetVector(dfliptrend, indices);
                    dupdownbar = MatrixMethods.getSubSetVector(dupdownbar, indices);
                    dupdownbarclean = MatrixMethods.getSubSetVector(dupdownbarclean, indices);
                    List<Long> dT = sRef.getColumnLabels().get(EnumBarSize.DAILY);
                    dT = Utilities.subList(indices, dT);
                    DoubleMatrix dclosezscore = Indicators.zscore(dclose, 10);
                    DoubleMatrix dhighzscore = Indicators.zscore(dhigh, 10);
                    DoubleMatrix dlowzscore = Indicators.zscore(dlow, 10);
                    DoubleMatrix drsi = Indicators.rsi(dclose, 14);
                    DoubleMatrix drsizscore = Indicators.zscore(drsi, 10);
                    DoubleMatrix dma = Indicators.ma(dclose, 10);
                    DoubleMatrix dmazscore = Indicators.zscore(dma, 10);
                    DoubleMatrix dy = MatrixMethods.ref(dupdownbar, 1).eq(1);
                    RConnection c = null;
                    try {
                        //c = new RConnection("162.251.114.10");
                        int length = dtrend.length;
                        c = new RConnection(rServerIP);
                        REXP x = c.eval("R.version.string");
                        System.out.println(x.asString());
                        REXP wd = c.eval("getwd()");
                        System.out.println(wd.asString());
                        c.eval("options(encoding = \"UTF-8\")");
                        c.eval("rm(list=ls())");
                        c.eval("set.seed(42)");
                        c.eval("library(nnet)");
                        c.eval("library(pROC)");
                        String[] out = c.eval("search()").asStrings();
                        for (int i = 0; i < out.length; i++) {
                            System.out.println("Loaded Package:" + out[i]);
                        }
                        //c.eval("setwd(" + rWorkingDirectory + ")");
                        c.assign("tradedate", Utilities.convertLongListToArray(dT));
                        c.assign("trend", dtrend.data);
                        c.assign("daysinupswing", ddaysinupswing.data);
                        c.assign("daysindownswing", ddaysindownswing.data);
                        c.assign("daysoutsidetrend", ddaysoutsidetrend.data);
                        c.assign("daysintrend", ddaysintrend.data);
                        c.assign("stickytrend", dstickytrend.data);
                        c.assign("fliptrend", dfliptrend.data);
                        c.assign("daysinuptrend", ddaysinuptrend.data);
                        c.assign("daysindowntrend", ddaysindowntrend.data);
                        c.assign("updownbarclean", dupdownbarclean.data);
                        c.assign("updownbar", dupdownbar.data);
                        c.assign("rsizscore", drsizscore.data);
                        c.assign("closezscore", dclosezscore.data);
                        c.assign("highzscore", dhighzscore.data);
                        c.assign("lowzscore", dlowzscore.data);
                        c.assign("mazscore", dmazscore.data);
                        c.assign("y", dy.data);
                        c.eval("save(lowzscore,file=\"lowzscore_" + sRef.getDisplayname() + ".Rdata\")");
                        c.eval("data<-data.frame("
                                + "tradedate=tradedate,"
                                + "trend=trend,"
                                + "daysinupswing=daysinupswing,"
                                + "daysindownswing=daysindownswing,"
                                + "daysoutsidetrend=daysoutsidetrend,"
                                + "daysintrend=daysintrend,"
                                // + "stickytrend=stickytrend,"
                                // + "fliptrend=fliptrend,"
                                // + "daysinuptrend=daysinuptrend,"
                                // + "daysindowntrend=daysindowntrend,"
                                // + "updownbarclean=updownbarclean,"
                                // + "updownbar=updownbar,"
                                // + "rsizscore=rsizscore,"
                                + "closezscore=closezscore,"
                                + "highzscore=highzscore,"
                                + "lowzscore=lowzscore,"
                                + "mazscore=mazscore,"
                                + "y=y)");
                        c.eval("data$tradedate<-as.POSIXct(as.numeric(as.character(data$tradedate))/1000,tz=\"Asia/Kolkata\",origin=\"1970-01-01\")");
                        c.eval("data[data==-1000001] = NA");
                        c.eval("data<-na.omit(data)");
                        c.eval("save(data,file=\"data_" + sRef.getDisplayname() + ".Rdata\")");
                        c.eval("data$y<-as.factor(data$y)");
//                        c.eval(("data<-data[dim(data)[1],]"));
                        String path = "\"" + parameterObjectPath + "/" + "fit_" + sRef.getDisplayname() + ".Rdata" + "\"";
                        c.eval("load(" + path + ")");
                        c.eval("today_predict_prob<-predict(fit$finalModel, newdata = data, type=\"raw\")");
                        c.eval("actual<-fit$finalModel$fitted.values+fit$finalModel$residuals");
                        c.eval("actual<-as.numeric(actual)");
                        c.eval("prediction<-fit$finalModel$fitted.values");
                        c.eval("prediction<-as.numeric(prediction)");
                        c.eval("rocCurve   <- roc(response = actual, predictor = prediction)");
                        c.eval("result.coords <- coords(rocCurve, \"best\", best.method=\"closest.topleft\", ret=c(\"threshold\", \"accuracy\",\"specificity\",\"sensitivity\"))");
                        double threshold=c.eval("result.coords[\"threshold\"]").asDouble();
                        double specificity=c.eval("result.coords[\"specificity\"]").asDouble();
                        double sensitivity=c.eval("result.coords[\"sensitivity\"]").asDouble();
                        double[] predict_prob = c.eval("today_predict_prob").asDoubles();
                        int output = predict_prob.length;
                        double today_predict_prob = predict_prob[output - 1];
                        int size = this.getPosition().get(id).getPosition();
                        TradingUtil.writeToFile(getStrategy() + ".csv",s.getDisplayname()
                                + "," + lValue(dtrend)
                                + "," + lValue(ddaysinupswing)
                                + "," + lValue(ddaysindownswing)
                                + "," + lValue(ddaysoutsidetrend)
                                + "," + lValue(ddaysintrend)
                                + "," + lValue(dclosezscore)
                                + "," + lValue(dhighzscore)
                                + "," + lValue(dlowzscore)
                                + "," + lValue(dmazscore)
                                + "," + today_predict_prob
                                + "," + lValue(dy));                        
                        Trigger swingTrigger = Trigger.UNDEFINED;
                        if(optimalThreshold){
                        boolean cBuy= today_predict_prob > threshold && sensitivity>sensitivityThreshold && s.getLastPrice()!=0 && this.getLongOnly() && size==0;
                        boolean cSell= today_predict_prob < threshold-exitLaziness && s.getLastPrice()!=0 && this.getLongOnly() && size>0;
                        boolean cShort= today_predict_prob < threshold && specificity>specificityThreshold && s.getLastPrice() != 0 && this.getShortOnly() && size == 0 ;
                        boolean cCover=today_predict_prob > threshold+exitLaziness && s.getLastPrice() != 0 && this.getShortOnly() && size < 0;
                        if(cBuy && !portfolio){
                            swingTrigger=Trigger.BUY;
                        }else if(cSell){
                            swingTrigger=Trigger.SELL;
                        }else if(cShort && !portfolio){
                            swingTrigger=Trigger.SHORT;
                        }else if (cCover){
                            swingTrigger=Trigger.COVER;
                        }else if (cBuy && portfolio){
                            swingTrigger=Trigger.BUYPORT;
                        }else if (cShort && portfolio){
                            swingTrigger=Trigger.SHORTPORT;
                        }                            
                        }else{
                        boolean cBuy= today_predict_prob > upProbabilityThreshold && s.getLastPrice()!=0 && this.getLongOnly() && size==0;
                        boolean cSell= today_predict_prob < downProbabilityThreshold && s.getLastPrice()!=0 && this.getLongOnly() && size>0;
                        boolean cShort= today_predict_prob < downProbabilityThreshold && s.getLastPrice() != 0 && this.getShortOnly() && size == 0 ;
                        boolean cCover=today_predict_prob >= upProbabilityThreshold && s.getLastPrice() != 0 && this.getShortOnly() && size < 0;
                        if(cBuy && !portfolio){
                            swingTrigger=Trigger.BUY;
                        }else if(cSell){
                            swingTrigger=Trigger.SELL;
                        }else if(cShort && !portfolio){
                            swingTrigger=Trigger.SHORT;
                        }else if (cCover){
                            swingTrigger=Trigger.COVER;
                        }else if (cBuy && portfolio){
                            swingTrigger=Trigger.BUYPORT;
                        }else if (cShort && portfolio){
                            swingTrigger=Trigger.SHORTPORT;
                        }
                        }
                        switch(swingTrigger){
                            case BUY:
                                if(rollover){
                                   id=Utilities.getNextExpiryID(Parameters.symbol, id, expiryFarMonth);
                                }
                                size=Parameters.symbol.get(id).getMinsize()*this.getNumberOfContracts();
                                this.entry(id, EnumOrderSide.BUY, size, EnumOrderType.LMT, Parameters.symbol.get(id).getLastPrice(), 0, EnumOrderReason.REGULARENTRY, EnumOrderStage.INIT, this.getMaxOrderDuration(), this.getDynamicOrderDuration(), this.getMaxSlippageExit(), "", "GTC", "", false, true);                                
                                break;
                            case SELL:
                                this.entry(id, EnumOrderSide.SELL, size, EnumOrderType.LMT, Parameters.symbol.get(id).getLastPrice(), 0, EnumOrderReason.REGULAREXIT, EnumOrderStage.INIT, this.getMaxOrderDuration(), this.getDynamicOrderDuration(), this.getMaxSlippageExit(), "", "GTC", "", false, true);
                                break;
                            case SHORT:
                                if(rollover){
                                   id=Utilities.getNextExpiryID(Parameters.symbol, id, expiryFarMonth);
                                }
                                size=Parameters.symbol.get(id).getMinsize()*this.getNumberOfContracts();
                                this.entry(id, EnumOrderSide.SHORT, size, EnumOrderType.LMT, Parameters.symbol.get(id).getLastPrice(), 0, EnumOrderReason.REGULARENTRY, EnumOrderStage.INIT, this.getMaxOrderDuration(), this.getDynamicOrderDuration(), this.getMaxSlippageExit(), "", "GTC", "", false, true);
                                break;
                            case COVER:
                                 this.entry(id, EnumOrderSide.COVER, size, EnumOrderType.LMT, Parameters.symbol.get(id).getLastPrice(), 0, EnumOrderReason.REGULAREXIT, EnumOrderStage.INIT, this.getMaxOrderDuration(), this.getDynamicOrderDuration(), this.getMaxSlippageExit(), "", "GTC", "", false, true);
                                break;
                            case BUYPORT:
                                if(rollover){
                                    id=Utilities.getNextExpiryID(Parameters.symbol, id, expiryFarMonth);
                                }
                                longPositionScore.put(100-(sensitivity*100), id);
                                break;
                            case SHORTPORT:
                                if(rollover){
                                    id=Utilities.getNextExpiryID(Parameters.symbol, id, expiryFarMonth);
                                }
                                shortPositionScore.put(100-(specificity*100), id);
                                break;
                            default:
                                break;
                                
                        }
                        
                    } catch (Exception ex) {
                        logger.log(Level.SEVERE, null, ex);
                    }                    
                }
            }
        }
        portfolioTrades();
    }
    
    private void portfolioTrades() {
        if (this.getLongOnly()) {
            positionCount = Utilities.openPositionCount(Parameters.symbol, this.getOrderFile(), this.getStrategy(), this.getPointValue(), true);
        }
        if (this.getShortOnly()) {
            positionCount = positionCount + Utilities.openPositionCount(Parameters.symbol, this.getOrderFile(), this.getStrategy(), this.getPointValue(), false);
        }
        int gap = maxPositions - positionCount;
        for (int id : longPositionScore.values()) {
            if (gap > 0) {
                gap = gap - 1;
                int size = Parameters.symbol.get(id).getMinsize() * this.getNumberOfContracts();
                HashMap<String,Object>order=new HashMap<>();
                order.put("id",id);
                order.put("side", EnumOrderSide.BUY);
                order.put("size", size);
                order.put("type", EnumOrderType.LMT);
                order.put("limitprice", Parameters.symbol.get(id).getLastPrice());
                order.put("reason", EnumOrderReason.REGULARENTRY);
                order.put("orderstage", EnumOrderStage.INIT);
                order.put("expiretime", this.getMaxOrderDuration());
                order.put("dynamicorderduration", getDynamicOrderDuration());
                order.put("maxslippage", this.getMaxSlippageEntry());
                entry(order);
//                this.entry(id, EnumOrderSide.BUY, size, EnumOrderType.LMT, Parameters.symbol.get(id).getLastPrice(), 0, EnumOrderReason.REGULARENTRY, EnumOrderStage.INIT, this.getMaxOrderDuration(), this.getDynamicOrderDuration(), this.getMaxSlippageExit(), "", "GTC", "", false, true);
            }
        }

        for (int id : shortPositionScore.values()) {
            if (gap > 0) {
                gap = gap - 1;
                int size = Parameters.symbol.get(id).getMinsize() * this.getNumberOfContracts();
                HashMap<String,Object>order=new HashMap<>();
                order.put("id",id);
                order.put("side", EnumOrderSide.SHORT);
                order.put("size", size);
                order.put("type", EnumOrderType.LMT);
                order.put("limitprice", Parameters.symbol.get(id).getLastPrice());
                order.put("reason", EnumOrderReason.REGULARENTRY);
                order.put("orderstage", EnumOrderStage.INIT);
                order.put("expiretime", this.getMaxOrderDuration());
                order.put("dynamicorderduration", getDynamicOrderDuration());
                order.put("maxslippage", this.getMaxSlippageExit());
                entry(order);
                //this.entry(id, EnumOrderSide.SHORT, size, EnumOrderType.LMT, Parameters.symbol.get(id).getLastPrice(), 0, EnumOrderReason.REGULARENTRY, EnumOrderStage.INIT, this.getMaxOrderDuration(), this.getDynamicOrderDuration(), this.getMaxSlippageExit(), "", "GTC", "", false, true);
            }
        }
    }
}