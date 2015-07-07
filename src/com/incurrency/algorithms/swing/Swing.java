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
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.math3.stat.StatUtils;
import org.apache.commons.math3.util.FastMath;
import org.jblas.DoubleMatrix;
import org.rosuda.REngine.REXP;
import org.rosuda.REngine.REXPMismatchException;
import org.rosuda.REngine.REngineException;
import org.rosuda.REngine.Rserve.RConnection;
import org.rosuda.REngine.Rserve.RserveException;

/**
 *
 * @author pankaj
 */
public class Swing extends Strategy implements TradeListener {

    private static final Logger logger = Logger.getLogger(Pairs.class.getName());
    String symbol;
    String type;
    String expiry;
    String nextExpiry;
    String rServerIP;
    Date entryScanDate;
    double stopLoss;
    double takeProfit;
    double upProbabilityThreshold;
    double downProbabilityThreshold;
    String cassandraMetric;
    String[] timeSeries;
    long openTime;
    boolean customRange = false;

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
        calToday.set(Calendar.HOUR, Algorithm.openHour);
        calToday.set(Calendar.MINUTE, Algorithm.openMinute);
        calToday.set(Calendar.SECOND, 0);
        calToday.set(Calendar.MILLISECOND, 0);
        openTime = calToday.getTimeInMillis();
        calToday.add(Calendar.YEAR, -5);
        String hdStartDate = DateUtil.getFormatedDate("yyyyMMdd HH:mm:ss", calToday.getTimeInMillis(), TimeZone.getTimeZone(Algorithm.timeZone));
        Thread t = new Thread(new HistoricalBars("swing", "IND", EnumSource.CASSANDRA, timeSeries, cassandraMetric, hdStartDate, hdEndDate, EnumBarSize.DAILY, false));
        t.setName("Historical Bars");
        logger.log(Level.INFO, "Historical Request Started");
        t.start();
    }

    private void loadParameters(Properties p) {
        symbol = p.getProperty("Symbol");
        type = p.getProperty("Type");
        expiry = p.getProperty("Expiry");
        nextExpiry = p.getProperty("NextExpiry");
        rServerIP = p.getProperty("RServerIP");
        String entryScanTime = p.getProperty("EntryScanTime");
        Calendar calToday = Calendar.getInstance(TimeZone.getTimeZone(Algorithm.timeZone));
        String[] entryTimeComponents = entryScanTime.split(":");
        calToday.set(Calendar.HOUR, Utilities.getInt(entryTimeComponents[0], 15));
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
        if (this.getStrategySymbols().contains(id)) {
            if (this.getPosition().get(id).getPosition() > 0) {
                Double tradePrice = this.getPosition().get(id).getPrice();
                if (Parameters.symbol.get(id).getLastPrice() <= tradePrice * (1 - stopLoss)) {
                    int size = this.getPosition().get(id).getPosition();
                    this.entry(id, EnumOrderSide.SELL, size, EnumOrderType.LMT, Parameters.symbol.get(id).getLastPrice(), 0, EnumOrderReason.REGULAREXIT, EnumOrderStage.INIT, this.getMaxOrderDuration(), this.getDynamicOrderDuration(), this.getMaxSlippageExit(), "", "GTC", "", false, true);
                }
                int size = this.getPosition().get(id).getPosition();
                this.entry(id, EnumOrderSide.SELL, size, EnumOrderType.LMT, Parameters.symbol.get(id).getLastPrice(), 0, EnumOrderReason.REGULAREXIT, EnumOrderStage.INIT, this.getMaxOrderDuration(), this.getDynamicOrderDuration(), this.getMaxSlippageExit(), "", "GTC", "", false, true);
            }
        }

        if (new Date().compareTo(entryScanDate) > 0 && Parameters.symbol.get(id).getTimeSeriesLength(EnumBarSize.DAILY) > 0) {
            BeanSymbol s = Parameters.symbol.get(id);
            //add current bar's data to matrix
            s.setTimeSeries(EnumBarSize.DAILY, openTime, new String[]{"open", "high", "low", "settle", "volume"}, new double[]{s.getOpenPrice(), s.getHighPrice(), s.getLowPrice(), s.getClosePrice(), s.getVolume()});
            DoubleMatrix dtrend = Indicators.swing(s, EnumBarSize.DAILY).getTimeSeries(EnumBarSize.DAILY, "trend");
            int[] indices = dtrend.ne(ReservedValues.EMPTY).findIndices();
            DoubleMatrix dclose = s.getTimeSeries(EnumBarSize.DAILY, "settle");
            indices = Utilities.addArrays(indices, dclose.ne(ReservedValues.EMPTY).findIndices());
            DoubleMatrix dhigh = s.getTimeSeries(EnumBarSize.DAILY, "high");
            indices = Utilities.addArrays(indices, dhigh.ne(ReservedValues.EMPTY).findIndices());
            DoubleMatrix dlow = s.getTimeSeries(EnumBarSize.DAILY, "low");
            indices = Utilities.addArrays(indices, dlow.ne(ReservedValues.EMPTY).findIndices());
            DoubleMatrix ddaysinupswing = s.getTimeSeries(EnumBarSize.DAILY, "daysinupswing");
            indices = Utilities.addArrays(indices, ddaysinupswing.ne(ReservedValues.EMPTY).findIndices());
            DoubleMatrix ddaysindownswing = s.getTimeSeries(EnumBarSize.DAILY, "daysindownswing");
            indices = Utilities.addArrays(indices, ddaysindownswing.ne(ReservedValues.EMPTY).findIndices());
            DoubleMatrix ddaysoutsidetrend = s.getTimeSeries(EnumBarSize.DAILY, "daysoutsidetrend");
            indices = Utilities.addArrays(indices, ddaysoutsidetrend.ne(ReservedValues.EMPTY).findIndices());
            DoubleMatrix ddaysintrend = s.getTimeSeries(EnumBarSize.DAILY, "daysintrend");
            indices = Utilities.addArrays(indices, ddaysintrend.ne(ReservedValues.EMPTY).findIndices());
            DoubleMatrix ddaysindowntrend = s.getTimeSeries(EnumBarSize.DAILY, "daysindowntrend");
            indices = Utilities.addArrays(indices, ddaysindowntrend.ne(ReservedValues.EMPTY).findIndices());
            DoubleMatrix ddaysinuptrend = s.getTimeSeries(EnumBarSize.DAILY, "daysinuptrend");
            indices = Utilities.addArrays(indices, ddaysinuptrend.ne(ReservedValues.EMPTY).findIndices());
            DoubleMatrix dstickytrend = s.getTimeSeries(EnumBarSize.DAILY, "stickytrend");
            indices = Utilities.addArrays(indices, dstickytrend.ne(ReservedValues.EMPTY).findIndices());
            DoubleMatrix dfliptrend = s.getTimeSeries(EnumBarSize.DAILY, "fliptrend");
            indices = Utilities.addArrays(indices, dfliptrend.ne(ReservedValues.EMPTY).findIndices());
            DoubleMatrix dupdownbar = s.getTimeSeries(EnumBarSize.DAILY, "updownbar");
            indices = Utilities.addArrays(indices, dupdownbar.ne(ReservedValues.EMPTY).findIndices());
            DoubleMatrix dupdownbarclean = s.getTimeSeries(EnumBarSize.DAILY, "updownbarclean");
            indices = Utilities.addArrays(indices, dupdownbarclean.ne(ReservedValues.EMPTY).findIndices());
            //Remove NA values
            dtrend = dtrend.get(indices);
            dclose = dclose.get(indices);
            dhigh = dhigh.get(indices);
            dlow = dlow.get(indices);
            ddaysinupswing = ddaysinupswing.get(indices);
            ddaysindownswing = ddaysindownswing.get(indices);
            ddaysoutsidetrend = ddaysoutsidetrend.get(indices);
            ddaysintrend = ddaysintrend.get(indices);
            ddaysindowntrend = ddaysindowntrend.get(indices);
            ddaysinuptrend = ddaysinuptrend.get(indices);
            dstickytrend = dstickytrend.get(indices);
            dfliptrend = dfliptrend.get(indices);
            dupdownbar = dupdownbar.get(indices);
            dupdownbarclean = dupdownbarclean.get(indices);
            List<Long> dT = s.getColumnLabels().get(EnumBarSize.DAILY);
            dT = Utilities.subList(indices, dT);
            DoubleMatrix dclosezscore = Indicators.zscore(dclose, 10);
            DoubleMatrix dhighzscore = Indicators.zscore(dhigh, 10);
            DoubleMatrix dlowzscore = Indicators.zscore(dlow, 10);
            DoubleMatrix drsi = Indicators.rsi(dclose, 14);
            DoubleMatrix drsizscore = Indicators.zscore(drsi, 10);
            DoubleMatrix dy = MatrixMethods.ref(dupdownbar, 1).eq(1);

            RConnection c = null;
            try {
                //c = new RConnection("162.251.114.10");
                int length = dtrend.length;
                c = new RConnection(rServerIP);
                REXP x = c.eval("R.version.string");
                System.out.println(x.asString());
                c.eval("options(encoding = \"UTF-8\")");
                c.eval("rm(list=ls())");
                c.eval("set.seed(42)");
                c.eval("library(nnet)");
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
                c.assign("y", dy.data);
                c.eval("data<-data.frame("
                        + "tradedate="
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
                c.eval(("data<-data[dim(data)[1],]"));
                c.eval("load(<DQ>fit_" + s.getDisplayname() + ".RData<DQ>)");
                c.eval("today_predict_prob<-predict(fit, newdata = data, type=\"raw \")");
                double[] predict_prob = c.eval("today_predict_prob").asDoubles();
                int output = predict_prob.length;
                double today_predict_prob = predict_prob[output - 1];
                int size = this.getPosition().get(id).getPosition();
                if (size == 0 && today_predict_prob >= upProbabilityThreshold) {
                    //BUY ORDER
                    this.entry(id, EnumOrderSide.BUY, size, EnumOrderType.LMT, Parameters.symbol.get(id).getLastPrice(), 0, EnumOrderReason.REGULARENTRY, EnumOrderStage.INIT, this.getMaxOrderDuration(), this.getDynamicOrderDuration(), this.getMaxSlippageExit(), "", "GTC", "", false, true);
                } else if (size > 0 && today_predict_prob <= downProbabilityThreshold) {
                    //SELL ORDER 
                    this.entry(id, EnumOrderSide.SELL, size, EnumOrderType.LMT, Parameters.symbol.get(id).getLastPrice(), 0, EnumOrderReason.REGULAREXIT, EnumOrderStage.INIT, this.getMaxOrderDuration(), this.getDynamicOrderDuration(), this.getMaxSlippageExit(), "", "GTC", "", false, true);
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