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

    public Swing(MainAlgorithm m, Properties p, String parameterFile, ArrayList<String> accounts, Integer stratCount) {
        super(m, "swing", "FUT", p, parameterFile, accounts, stratCount);
        loadParameters(p);

        TradingUtil.writeToFile(getStrategy() + ".csv", "comma seperated header columns ");

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
            Thread t = new Thread(new HistoricalBars("swing", "IND", EnumSource.CASSANDRA, timeSeries, cassandraMetric, hdStartDate, hdEndDate, EnumBarSize.DAILY, false));
            t.setName("Historical Bars");
            logger.log(Level.INFO, "Historical Request Started");
            t.start();
//        t.join();
            t = new Thread(new HistoricalBars("swing", "STK", EnumSource.CASSANDRA, timeSeries, cassandraMetric, hdStartDate, hdEndDate, EnumBarSize.DAILY, false));
            t.setName("Historical Bars");
            logger.log(Level.INFO, "Historical Request Started");
            t.start();
//        t.join();
        } catch (Exception e) {
            logger.log(Level.SEVERE, null, e);
        }
        // Next three lines to be removed in production
        //**************
        Calendar tmpCalendar=Calendar.getInstance(TimeZone.getTimeZone(Algorithm.timeZone));
        tmpCalendar.add(Calendar.SECOND, 15);
        entryScanDate=tmpCalendar.getTime();
        //*******
        eodProcessing = new Timer("Timer: Close Positions");
        eodProcessing.schedule(eodProcessingTask, entryScanDate);
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
        
    }

    @Override
    public void tradeReceived(TradeEvent event) {
        Integer id = event.getSymbolID() - 1;
        if (this.getStrategySymbols().contains(id) && !Parameters.symbol.get(id).getType().equals(referenceCashType)) {
            if (this.getPosition().get(id).getPosition() > 0) {
                Double tradePrice = this.getPosition().get(id).getPrice();
                if (Parameters.symbol.get(id).getLastPrice()!=0 && Parameters.symbol.get(id).getLastPrice() <= tradePrice * (1 - stopLoss/100)) {
                    int size = this.getPosition().get(id).getPosition();
                    this.entry(id, EnumOrderSide.SELL, size, EnumOrderType.LMT, Parameters.symbol.get(id).getLastPrice(), 0, EnumOrderReason.REGULAREXIT, EnumOrderStage.INIT, this.getMaxOrderDuration(), this.getDynamicOrderDuration(), this.getMaxSlippageExit(), "", "GTC", "", false, true);
                }
            }else if (this.getPosition().get(id).getPosition() < 0) {
                Double tradePrice = this.getPosition().get(id).getPrice();
                if (Parameters.symbol.get(id).getLastPrice()!=0 && Parameters.symbol.get(id).getLastPrice() >= tradePrice * (1 + stopLoss/100)) {
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
        for (BeanSymbol s : Parameters.symbol) {
            if (!s.getType().equals(referenceCashType)) {
                int id = s.getSerialno() - 1;
                int referenceid = Utilities.getReferenceID(Parameters.symbol, id, referenceCashType);
                BeanSymbol sRef = Parameters.symbol.get(referenceid);
                //add current bar'sRef data to matrix
                if (sRef.getTimeSeriesLength(EnumBarSize.DAILY)>-1 && sRef.getTimeSeriesLength(EnumBarSize.DAILY) > 0) {
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
                    DoubleMatrix dma=Indicators.ma(dclose, 10);
                    DoubleMatrix dmazscore=Indicators.zscore(dma, 10);
                    DoubleMatrix dy = MatrixMethods.ref(dupdownbar, 1).eq(1);
                    RConnection c = null;
                    try {
                        //c = new RConnection("162.251.114.10");
                        int length = dtrend.length;
                        c = new RConnection(rServerIP);
                        REXP x = c.eval("R.version.string");
                        System.out.println(x.asString());
                        REXP wd=c.eval("getwd()");
                        System.out.println(wd.asString());
                        c.eval("options(encoding = \"UTF-8\")");
                        c.eval("rm(list=ls())");
                        c.eval("set.seed(42)");
                        c.eval("library(nnet)");
                        String[] out=c.eval("search()").asStrings();
                        for(int i=0;i<out.length;i++){
                            System.out.println("Loaded Package:"+out[i]);
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
                        String path="\""+parameterObjectPath+"/"+"fit_"+sRef.getDisplayname()+".RData"+"\"";
                        c.eval("load("+path+")");
                        c.eval("today_predict_prob<-predict(fit, newdata = data, type=\"raw\")");
                        double[] predict_prob = c.eval("today_predict_prob").asDoubles();
                        int output = predict_prob.length;
                        double today_predict_prob = predict_prob[output - 1];
                        int size = this.getPosition().get(id).getPosition();
                        if (this.getLongOnly() && size == 0 && today_predict_prob >= upProbabilityThreshold) {
                            //BUY ORDER
                            this.entry(id, EnumOrderSide.BUY, size, EnumOrderType.LMT, Parameters.symbol.get(id).getLastPrice(), 0, EnumOrderReason.REGULARENTRY, EnumOrderStage.INIT, this.getMaxOrderDuration(), this.getDynamicOrderDuration(), this.getMaxSlippageExit(), "", "GTC", "", false, true);
                        } else if (this.getLongOnly() && size > 0 && today_predict_prob <= downProbabilityThreshold) {
                            //SELL ORDER 
                            this.entry(id, EnumOrderSide.SELL, size, EnumOrderType.LMT, Parameters.symbol.get(id).getLastPrice(), 0, EnumOrderReason.REGULAREXIT, EnumOrderStage.INIT, this.getMaxOrderDuration(), this.getDynamicOrderDuration(), this.getMaxSlippageExit(), "", "GTC", "", false, true);
                        } else {
                            //do nothing
                        }
                        
                        if (this.getShortOnly() && size == 0 && today_predict_prob <= downProbabilityThreshold) {
                            //SHORT ORDER
                            this.entry(id, EnumOrderSide.SHORT, size, EnumOrderType.LMT, Parameters.symbol.get(id).getLastPrice(), 0, EnumOrderReason.REGULARENTRY, EnumOrderStage.INIT, this.getMaxOrderDuration(), this.getDynamicOrderDuration(), this.getMaxSlippageExit(), "", "GTC", "", false, true);
                        } else if (this.getShortOnly() && size < 0 && today_predict_prob >= upProbabilityThreshold) {
                            //COVER ORDER 
                            this.entry(id, EnumOrderSide.COVER, size, EnumOrderType.LMT, Parameters.symbol.get(id).getLastPrice(), 0, EnumOrderReason.REGULAREXIT, EnumOrderStage.INIT, this.getMaxOrderDuration(), this.getDynamicOrderDuration(), this.getMaxSlippageExit(), "", "GTC", "", false, true);
                        } else {
                            //do nothing
                        }

                    } catch (Exception ex) {
                        logger.log(Level.SEVERE, null, ex);
                    }
                    //calculate indicators and check probability

                }
            }
        }
    }
}



/*
 c.assign("tradedate", Utilities.convertLongListToArray(dT));
                    c.assign("trend", dtrend.data);
                    c.assign("daysinupswing", ddaysinupswing.data);
                    c.assign("daysindownswing", ddaysindownswing.data);
                    c.assign("daysoutsidetrend", ddaysoutsidetrend.data);
                    c.assign("daysintrend",ddaysintrend.data);
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
                    c.assign("y", dy.data);
                    c.eval("data<-data.frame("
                            +"tradedate="
                            + "trend=trend,"
                            + "daysinupswing=daysinupswing,"
                            + "daysindownswing=daysindownswing,"
                            + "daysoutsidetrend=daysoutsidetrend,"
                            + "daysintrend=daysintrend,"
                            + "stickytrend=stickytrend,"
                            + "daysintrend=daysintrend,"
                            + "stickytrend=stickytrend,"
                            + "fliptrend=fliptrend,"
                            + "daysinuptrend=daysinuptrend,"
                            + "daysindowntrend=daysindowntrend,"
                            + "updownbarclean=updownbarclean,"
                            + "updownbar=updownbar,"
                            + "rsizscore=rsizscore,"
                            + "closezscore=closezscore,"
                            + "highzscore=highzscore,"
                            + "lowzscore=lowzscore,"
                            + "y=y)");
                    c.eval("data[data==-1000001] = NA");
                    c.eval("data<-na.omit(data)");
                    c.eval("data$y<-as.factor(data$y)");
 */