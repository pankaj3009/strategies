/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.algorithms.swing;

import com.espertech.esper.client.util.DateTime;
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
import com.incurrency.framework.EnumStopMode;
import com.incurrency.framework.EnumStopType;
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
import com.incurrency.framework.Stop;
import com.incurrency.framework.Trade;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.TreeMap;
import org.rosuda.REngine.REXPMismatchException;
import org.rosuda.REngine.REngineException;

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
    int testingTimer = 0;
    boolean portfolio = false;
    boolean optimalThreshold = false;
    double sensitivityThreshold = 0.5;
    double specificityThreshold = 0.5;
    double exitLaziness = 0;
    double entryLaziness = 0;
    int maxPositions = 0;
    int longpositionCount = 0;
    int shortpositionCount = 0;
    TreeMap<Double, Integer> longPositionScore = new TreeMap<>();
    TreeMap<Double, Integer> shortPositionScore = new TreeMap<>();
    HashMap<Integer, Double> thresholdDistance = new HashMap<>(); //holds the symbol id: probability-threshold
    ArrayList<Integer> longsExitedToday = new ArrayList<>();
    ArrayList<Integer> shortsExitedToday = new ArrayList<>();
    Thread historicalDataRetriever;
    public Indicators ind=new Indicators();
    private final Object lockTradeReceived_1 = new Object();
    
    public Swing(MainAlgorithm m, Properties p, String parameterFile, ArrayList<String> accounts, Integer stratCount) {
        super(m, "swing", "FUT", p, parameterFile, accounts, stratCount);
        loadParameters(p);
        File dir = new File("logs");
        File f = new File(dir, getStrategy() + ".csv");
        if (!f.exists()) {
            TradingUtil.writeToFile(getStrategy() + ".csv", "symbol,scan,high,low,close,trend,daysinupswing,daysindownswing,daysoutsidetrend,daysintrend,closezscore,highzscore,lowzscore,mazscore,nextdayprob,threshold,sensitivity,specificity,y");
        }

        String[] tempStrategyArray = parameterFile.split("\\.")[0].split("-");
        for (BeanConnection c : Parameters.connection) {
            c.getWrapper().addTradeListener(this);
            c.initializeConnection(tempStrategyArray[tempStrategyArray.length - 1]);
        }
        if (Subscribe.tes != null) {
            Subscribe.tes.addTradeListener(this);
        }
        historicalDataRetriever = new Thread() {
            public void run() {
                Calendar calToday = Calendar.getInstance(TimeZone.getTimeZone(Algorithm.timeZone));
                String hdEndDate = DateUtil.getFormatedDate("yyyyMMdd HH:mm:ss", calToday.getTimeInMillis(), TimeZone.getTimeZone(Algorithm.timeZone));
                calToday.set(Calendar.HOUR_OF_DAY, Algorithm.openHour);
                calToday.set(Calendar.MINUTE, Algorithm.openMinute);
                calToday.set(Calendar.SECOND, 0);
                calToday.set(Calendar.MILLISECOND, 0);
                openTime = calToday.getTimeInMillis();
                calToday.add(Calendar.YEAR, -5);
                String hdStartDate = DateUtil.getFormatedDate("yyyyMMdd HH:mm:ss", calToday.getTimeInMillis(), TimeZone.getTimeZone(Algorithm.timeZone));
                for (BeanSymbol s : Parameters.symbol) {
                    if (s.getDataLength(EnumBarSize.DAILY,"settle")==0 && (s.getType().equals("STK") || s.getType().equals("IND")) && s.getStrategy().toLowerCase().contains("swing")) {
                        Thread t = new Thread(new HistoricalBars(s, EnumSource.CASSANDRA, timeSeries, cassandraMetric, hdStartDate, hdEndDate, EnumBarSize.DAILY, false));
                        t.start();
                        while (t.getState() != Thread.State.TERMINATED) {
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException ex) {
                                Logger.getLogger(Swing.class.getName()).log(Level.SEVERE, null, ex);
                            }
                        }
                    }
                }
            }
        };
        historicalDataRetriever.start();

        if (testingTimer > 0) {
            Calendar tmpCalendar = Calendar.getInstance(TimeZone.getTimeZone(Algorithm.timeZone));
            tmpCalendar.add(Calendar.MINUTE, testingTimer);
            entryScanDate = tmpCalendar.getTime();
        }

        eodProcessing = new Timer("Timer: Close Positions");
        eodProcessing.schedule(eodProcessingTask, entryScanDate);
        if (this.getLongOnly()) {
            longpositionCount = Utilities.openPositionCount(Parameters.symbol, this.getOrderFile(), this.getStrategy(), this.getPointValue(), true);
        }
        if (this.getShortOnly()) {
            shortpositionCount = Utilities.openPositionCount(Parameters.symbol, this.getOrderFile(), this.getStrategy(), this.getPointValue(), false);
        }
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "Max Open Position" + delimiter + maxPositions});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "Current Long Open Position" + delimiter + longpositionCount});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "Current Short Open Position" + delimiter + shortpositionCount});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "Portfolio Mode" + delimiter + portfolio});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "Entry Scan Time" + delimiter + entryScanDate});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "Stop Loss %" + delimiter + stopLoss});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "Take Profit %" + delimiter + takeProfit});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "Time Series" + delimiter + timeSeries});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "Cassandra Metric" + delimiter + cassandraMetric});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "Testing Timer Duration" + delimiter + testingTimer});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "Optimal Threshold set" + delimiter + optimalThreshold});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "Sensitivity Threshold" + delimiter + sensitivityThreshold});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "Specificity Threshold" + delimiter + specificityThreshold});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "Exit Laziness" + delimiter + exitLaziness});

        Timer bodProcessing = new Timer("Timer: " + this.getStrategy() + " BODProcessing");
        bodProcessing.schedule(runBOD, 1 * 60 * 1000);
    }
    
    TimerTask runBOD = new TimerTask() {
        public void run() {
            while (historicalDataRetriever != null && historicalDataRetriever.getState() != Thread.State.TERMINATED) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ex) {
                    Logger.getLogger(Swing.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            updateStops();
        }
    };

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
        testingTimer = Utilities.getInt(p.getProperty("TestingTimer"), 0);
        portfolio = Boolean.parseBoolean(p.getProperty("Portfolio", "false").toString().trim());
        maxPositions = Integer.parseInt(p.getProperty("MaxPositions", "1").toString().trim());
        optimalThreshold = Boolean.parseBoolean(p.getProperty("OptimalThreshold", "false").toString().trim());
        sensitivityThreshold = Utilities.getDouble(p.getProperty("SensitivityThreshold"), 0.5);
        specificityThreshold = Utilities.getDouble(p.getProperty("SpecificityThreshold"), 0.5);
        exitLaziness = Utilities.getDouble(p.getProperty("ExitLaziness"), 0);
        entryLaziness = Utilities.getDouble(p.getProperty("EntryLaziness"), 0);
    }

    @Override
    public void tradeReceived(TradeEvent event) {
        synchronized(lockTradeReceived_1){
        Integer id = event.getSymbolID();
        if (this.getStrategySymbols().contains(id) && !Parameters.symbol.get(id).getType().equals(referenceCashType)) {
            if (this.getPosition().get(id).getPosition() > 0) {
                Double tradePrice = this.getPosition().get(id).getPrice();
                ArrayList<Stop> stops = Trade.getStop(getTrades(), internalOpenOrders.get(id));
                boolean tpTrigger = false;
                boolean slTrigger = false;
                double tpDistance=0D;
                double slDistance=0D;
                double sl=Double.MAX_VALUE;
                double tp=Double.MAX_VALUE;
                if (stops == null && Parameters.symbol.get(id).getLastPrice() != 0) {
                    slTrigger = Parameters.symbol.get(id).getLastPrice()!=0 && (Parameters.symbol.get(id).getLastPrice() <= tradePrice * (1 - stopLoss / 100));
                    tpTrigger = Parameters.symbol.get(id).getLastPrice()!=0 && (Parameters.symbol.get(id).getLastPrice() >= tradePrice * (1 + takeProfit / 100));
                } else if (stops != null && Parameters.symbol.get(id).getLastPrice() != 0) {
                    for (Stop stop : stops) {
                        switch (stop.stopType) {
                            case TAKEPROFIT:
                                tpDistance=Parameters.symbol.get(id).getLastPrice() - tradePrice;
                                tp=stop.stopValue;
                                tpTrigger = Parameters.symbol.get(id).getLastPrice()!=0 && tpDistance >= tp;
                                break;
                            case STOPLOSS:
                                slDistance=tradePrice - Parameters.symbol.get(id).getLastPrice();
                                sl=stop.stopValue;
                                slTrigger = Parameters.symbol.get(id).getLastPrice()!=0 && slDistance >= sl;
                                break;
                            default:
                                break;
                        }
                    }
                }
                if (slTrigger || tpTrigger) {
                   logger.log(Level.INFO,"501,Long SLTP Exit,{0}",new Object[]{this.getStrategy()+delimiter+Parameters.symbol.get(id).getDisplayname()+delimiter+slTrigger+delimiter+tpTrigger+delimiter+Parameters.symbol.get(id).getLastPrice()+delimiter+slDistance+delimiter+tpDistance+delimiter+sl+delimiter+tp});
                    int size = this.getPosition().get(id).getPosition();
                    HashMap<String, Object> order = new HashMap<>();
                    order.put("id", id);
                    order.put("side", EnumOrderSide.SELL);
                    order.put("size", size);
                    order.put("type", EnumOrderType.LMT);
                    order.put("limitprice", Parameters.symbol.get(id).getLastPrice());
                    order.put("reason", EnumOrderReason.REGULAREXIT);
                    order.put("orderstage", EnumOrderStage.INIT);
                    order.put("expiretime", this.getMaxOrderDuration());
                    order.put("dynamicorderduration", getDynamicOrderDuration());
                    order.put("maxslippage", this.getMaxSlippageExit());
                    this.exit(order);
//                    this.exit(id, EnumOrderSide.SELL, size, EnumOrderType.LMT, Parameters.symbol.get(id).getLastPrice(), 0, EnumOrderReason.REGULAREXIT, EnumOrderStage.INIT, this.getMaxOrderDuration(), this.getDynamicOrderDuration(), this.getMaxSlippageExit(), "", "GTC", "", false, true);
                    if (rolloverDay()) {
                        id = Utilities.getNextExpiryID(Parameters.symbol, id, expiryFarMonth);
                    }
                    longsExitedToday.add(id);
                }
            } else if (this.getPosition().get(id).getPosition() < 0) {
                Double tradePrice = this.getPosition().get(id).getPrice();
                ArrayList<Stop> stops = Trade.getStop(getTrades(), internalOpenOrders.get(id));
                boolean tpTrigger = false;
                boolean slTrigger = false;
                double tpDistance=0D;
                double slDistance=0D;
                double sl=Double.MAX_VALUE;
                double tp=Double.MAX_VALUE;
                if (stops == null && Parameters.symbol.get(id).getLastPrice() != 0) {
                    slTrigger = Parameters.symbol.get(id).getLastPrice()!=0 && (Parameters.symbol.get(id).getLastPrice() >= tradePrice * (1 + stopLoss / 100));
                    tpTrigger = Parameters.symbol.get(id).getLastPrice()!=0 && (Parameters.symbol.get(id).getLastPrice() <= tradePrice * (1 - takeProfit / 100));

                } else if (stops != null && Parameters.symbol.get(id).getLastPrice() != 0) {
                    for (Stop stop : stops) {
                        switch (stop.stopType) {
                            case TAKEPROFIT:
                                tpDistance=tradePrice - Parameters.symbol.get(id).getLastPrice();
                                tp=stop.stopValue;
                                tpTrigger = Parameters.symbol.get(id).getLastPrice()!=0 && tpDistance>= tp;
                                break;
                            case STOPLOSS:
                                slDistance=Parameters.symbol.get(id).getLastPrice() - tradePrice;
                                sl=stop.stopValue;
                                slTrigger = Parameters.symbol.get(id).getLastPrice()!=0 && slDistance >= sl;
                                break;
                            default:
                                break;
                        }
                    }
                }
                if (slTrigger || tpTrigger) {
                   logger.log(Level.INFO,"501,Short SLTP Exit,{0}",new Object[]{this.getStrategy()+delimiter+Parameters.symbol.get(id).getDisplayname()+delimiter+slTrigger+delimiter+tpTrigger+delimiter+Parameters.symbol.get(id).getLastPrice()+delimiter+slDistance+delimiter+tpDistance+delimiter+sl+delimiter+tp});
                    int size = this.getPosition().get(id).getPosition();
                    HashMap<String, Object> order = new HashMap<>();
                    order.put("id", id);
                    order.put("side", EnumOrderSide.COVER);
                    order.put("size", size);
                    order.put("type", EnumOrderType.LMT);
                    order.put("limitprice", Parameters.symbol.get(id).getLastPrice());
                    order.put("reason", EnumOrderReason.REGULAREXIT);
                    order.put("orderstage", EnumOrderStage.INIT);
                    order.put("expiretime", this.getMaxOrderDuration());
                    order.put("dynamicorderduration", getDynamicOrderDuration());
                    order.put("maxslippage", this.getMaxSlippageExit());
                    this.exit(order);

                    //this.exit(id, EnumOrderSide.COVER, Math.abs(size), EnumOrderType.LMT, Parameters.symbol.get(id).getLastPrice(), 0, EnumOrderReason.REGULAREXIT, EnumOrderStage.INIT, this.getMaxOrderDuration(), this.getDynamicOrderDuration(), this.getMaxSlippageExit(), "", "GTC", "", false, true);
                    if (rolloverDay()) {
                        id = Utilities.getNextExpiryID(Parameters.symbol, id, expiryFarMonth);
                    }
                    shortsExitedToday.add(id);
                }
            }
        }
    }
    }

    private boolean rolloverDay() {
        boolean rollover = false;
        try {
            SimpleDateFormat sdf_yyyyMMdd = new SimpleDateFormat("yyyyMMdd");
            String currentDay = sdf_yyyyMMdd.format(getStartDate());
            Date today = sdf_yyyyMMdd.parse(currentDay);
            Date expiry = sdf_yyyyMMdd.parse(expiryNearMonth);
            if (today.compareTo(expiry) >= 0) {
                rollover = true;
            }
        } catch (Exception e) {
            logger.log(Level.INFO, null, e);
        }
        return rollover;
    }

    private void updateStops() {
        logger.log(Level.INFO,"501,BOD Update Stop Process started");
        RConnection c = null;
        try {
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
        } catch (Exception e) {
            logger.log(Level.SEVERE, null, e);
        }
        for (Entry entry : getTrades().store.entrySet()) {
            String key = (String) entry.getKey();
            ArrayList<Stop> stops = Trade.getStop(getTrades(), key);
            if (stops != null) {
                String childsymboldisplayname = Trade.getEntrySymbol(getTrades(), key);
                int childid = Utilities.getIDFromDisplayName(Parameters.symbol, childsymboldisplayname);
                int referenceid = Utilities.getReferenceID(Parameters.symbol, childid, referenceCashType);
                HashMap<String, Double> stats = getStats(c, referenceid, false);
                if (!stats.isEmpty()) {
                    for (Stop stop : stops) {
                        if (stop.recalculate = Boolean.TRUE) {
                            if (childid >= 0) {
                                updateStop(stats, key, childid, stop);
                            }
                        }
                    }
                }
            }
        }
        if (c != null) {
            c.close();
        }
    }

    private void updateStop(HashMap<String, Double> stats, String key, int childid, Stop stop) {
        int referenceid = Utilities.getReferenceID(Parameters.symbol, childid, referenceCashType);
        if (!stats.isEmpty()) {
            double threshold = Utilities.getDouble(stats.get("threshold"), 0.5);
            double today_predict_prob = Utilities.getDouble(stats.get("probability"), -1);
            String tradeTimeFormat = "yyyy-MM-dd HH:mm:ss";
            String tradeDateString = Trade.getEntryTime(getTrades(), key);
            Date tradeDate = DateUtil.parseDate(tradeTimeFormat, tradeDateString, Algorithm.timeZone);
            long historicalTime = Parameters.symbol.get(referenceid).getTimeFloor(EnumBarSize.DAILY, tradeDate.getTime(), "settle");
            if (historicalTime != ReservedValues.EMPTY) {
                //check if dates match
                String historicalDateString = DateUtil.getFormatedDate("yyyy-MM-dd", historicalTime, TimeZone.getTimeZone(Algorithm.timeZone));
                tradeDateString = tradeDateString.substring(0, 10);//get the day part
                if (tradeDateString.equals(historicalDateString)) {
                    double close = Parameters.symbol.get(referenceid).getTimeSeriesValue(EnumBarSize.DAILY, historicalTime, "settle");
                    double high = Parameters.symbol.get(referenceid).getTimeSeriesValue(EnumBarSize.DAILY, historicalTime, "high");
                    double low = Parameters.symbol.get(referenceid).getTimeSeriesValue(EnumBarSize.DAILY, historicalTime, "low");
                    EnumOrderSide side = Trade.getEntrySide(getTrades(), key);
                    switch (side) {
                        case BUY:
                            EnumStopType type = stop.stopType;
                            switch (type) {
                                case STOPLOSS:
                                    double sl = close - low;
                                    stop.stopValue = Math.max(getTickSize(), Utilities.roundTo(sl, getTickSize()));
                                    logger.log(Level.INFO, "501,UpdatedSLStop,{0}", new Object[]{getStrategy() + delimiter + Parameters.symbol.get(referenceid).getDisplayname()});
                                    break;
                                case TAKEPROFIT:
                                    double distance = today_predict_prob - threshold > 0 ? 1 + today_predict_prob - threshold : 1;
                                    stop.StopLevel = distance;
                                    double tp = (high - low) * distance;
                                    stop.stopValue = tp;
                                    stop.stopValue = Math.max(getTickSize(), Utilities.roundTo(stop.stopValue, getTickSize()));
                                    logger.log(Level.INFO, "501,UpdatedTPStop,{0}", new Object[]{getStrategy() + delimiter + Parameters.symbol.get(referenceid).getDisplayname()});
                                    break;
                                default:
                                    break;
                            }
                            break;
                        case SHORT:
                            type = stop.stopType;
                            switch (type) {
                                case STOPLOSS:
                                    double sl = high - close;
                                    stop.stopValue = Math.max(getTickSize(), Utilities.roundTo(sl, getTickSize()));
                                    logger.log(Level.INFO, "501,UpdatedSLStop,{0}", new Object[]{getStrategy() + delimiter + Parameters.symbol.get(referenceid).getDisplayname()});
                                    break;
                                case TAKEPROFIT:
                                    double distance = threshold - today_predict_prob > 0 ? 1 + threshold - today_predict_prob : 1;
                                    stop.StopLevel = distance;
                                    double tp = (high - low) * distance;
                                    stop.stopValue = tp;
                                    stop.stopValue = Math.max(getTickSize(), Utilities.roundTo(stop.stopValue, getTickSize()));
                                    logger.log(Level.INFO, "501,UpdatedTPStop,{0}", new Object[]{getStrategy() + delimiter + Parameters.symbol.get(referenceid).getDisplayname()});
                                    break;
                                default:
                                    break;
                            }
                            break;
                        default:
                            break;
                    }
                    stop.recalculate = Boolean.FALSE;
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

    private void scan() {
        logger.log(Level.INFO,"501,Scan Started");
        RConnection c = null;
        try {
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
        } catch (Exception e) {
            logger.log(Level.SEVERE, null, e);
        }
        for (BeanSymbol s : Parameters.symbol) {
            if (!s.getType().equals(referenceCashType) && s.getExpiry()!=null && !s.getExpiry().equals(expiryFarMonth)) {
                int id = s.getSerialno() - 1;
                int referenceid = Utilities.getReferenceID(Parameters.symbol, id, referenceCashType);
                if(referenceid>=0){
                BeanSymbol sRef=Parameters.symbol.get(referenceid);
                HashMap<String, Double> stats = getStats(c, referenceid, true);
                if (!stats.isEmpty()) {
                    double threshold = Utilities.getDouble(stats.get("threshold"), 0.5);
                    double today_predict_prob = Utilities.getDouble(stats.get("probability"), -1);
                    double sensitivity = Utilities.getDouble(stats.get("sensitivity"), 0);
                    double specificity = Utilities.getDouble(stats.get("specificity"), 0);
                    DoubleMatrix datr=ind.atr(sRef.getTimeSeries(EnumBarSize.DAILY, "high"), sRef.getTimeSeries(EnumBarSize.DAILY, "low"), sRef.getTimeSeries(EnumBarSize.DAILY, "settle"), 10);
                    int size = this.getPosition().get(id).getPosition();
                    Trigger swingTrigger = Trigger.UNDEFINED;
                    if (optimalThreshold) {
                        boolean cBuy = today_predict_prob >= 0 && today_predict_prob > (threshold + entryLaziness) && s.getLastPrice() != 0 && sensitivity > sensitivityThreshold && this.getLongOnly() && size == 0;
                        boolean cSell = today_predict_prob >= 0 && today_predict_prob < (threshold - exitLaziness) && s.getLastPrice() != 0 && this.getLongOnly() && size > 0;
                        boolean cShort = today_predict_prob >= 0 && today_predict_prob < (threshold - entryLaziness) && s.getLastPrice() != 0 && specificity > specificityThreshold && this.getShortOnly() && size == 0;
                        boolean cCover = today_predict_prob >= 0 && today_predict_prob > (threshold + exitLaziness) && s.getLastPrice() != 0 && this.getShortOnly() && size < 0;
                        //First Handle Exits
                        if (cSell) {
                            swingTrigger = Trigger.SELL;
                        } else if (cCover) {
                            swingTrigger = Trigger.COVER;
                        }
                        processSignal(id,swingTrigger, stats);

                        //Then handle entries                       
                        swingTrigger = Trigger.UNDEFINED;
                        if (cBuy && !portfolio) {
                            swingTrigger = Trigger.BUY;
                        } else if (cShort && !portfolio) {
                            swingTrigger = Trigger.SHORT;
                        } else if (cBuy && portfolio) {
                            swingTrigger = Trigger.BUYPORT;
                        } else if (cShort && portfolio) {
                            swingTrigger = Trigger.SHORTPORT;
                        }
                        processSignal(id,swingTrigger, stats);

                    } else { //threshold is specified in the parameter file
                        boolean cBuy = today_predict_prob > upProbabilityThreshold && s.getLastPrice() != 0 && this.getLongOnly() && size == 0;
                        boolean cSell = today_predict_prob < downProbabilityThreshold && s.getLastPrice() != 0 && this.getLongOnly() && size > 0;
                        boolean cShort = today_predict_prob < downProbabilityThreshold && s.getLastPrice() != 0 && this.getShortOnly() && size == 0;
                        boolean cCover = today_predict_prob >= upProbabilityThreshold && s.getLastPrice() != 0 && this.getShortOnly() && size < 0;
                        //First handle exits
                        swingTrigger = Trigger.UNDEFINED;
                        if (cSell) {
                            swingTrigger = Trigger.SELL;
                        } else if (cCover) {
                            swingTrigger = Trigger.COVER;
                        }
                        processSignal(id,swingTrigger, stats);

                        //Then handle entries
                        swingTrigger = Trigger.UNDEFINED;
                        if (cBuy && !portfolio) {
                            swingTrigger = Trigger.BUY;
                        } else if (cShort && !portfolio) {
                            swingTrigger = Trigger.SHORT;
                        } else if (cBuy && portfolio) {
                            swingTrigger = Trigger.BUYPORT;
                        } else if (cShort && portfolio) {
                            swingTrigger = Trigger.SHORTPORT;
                        }
                        processSignal(id,swingTrigger, stats);

                    }
                }
                }
            }
        }
        portfolioTrades();
    }

    private void processSignal(int id,Trigger swingTrigger, HashMap<String, Double> stats) {
        int size = 0;
        boolean rollover = rolloverDay();
        double threshold = Utilities.getDouble(stats.get("threshold"), 0.5);
        double today_predict_prob = Utilities.getDouble(stats.get("probability"), -1);
        double sensitivity = Utilities.getDouble(stats.get("sensitivity"), 0);
        double specificity = Utilities.getDouble(stats.get("specificity"), 0);

        switch (swingTrigger) {
            case BUY:
                if (rollover) {
                    id = Utilities.getNextExpiryID(Parameters.symbol, id, expiryFarMonth);
                }
                if (!longsExitedToday.contains(Integer.valueOf(id))) {
                    size = Parameters.symbol.get(id).getMinsize() * this.getNumberOfContracts();
                    logger.log(Level.INFO, "501,Strategy BUY,{0}", new Object[]{getStrategy() + delimiter + Parameters.symbol.get(id)});
                    HashMap<String, Object> order = new HashMap<>();
                    order.put("id", id);
                    order.put("side", EnumOrderSide.BUY);
                    order.put("size", size);
                    order.put("type", EnumOrderType.LMT);
                    order.put("limitprice", Parameters.symbol.get(id).getLastPrice());
                    order.put("reason", EnumOrderReason.REGULARENTRY);
                    order.put("orderstage", EnumOrderStage.INIT);
                    order.put("expiretime", this.getMaxOrderDuration());
                    order.put("dynamicorderduration", getDynamicOrderDuration());
                    order.put("maxslippage", this.getMaxSlippageEntry());
                    this.entry(order);
                    //this.entry(id, EnumOrderSide.BUY, size, EnumOrderType.LMT, Parameters.symbol.get(id).getLastPrice(), 0, EnumOrderReason.REGULARENTRY, EnumOrderStage.INIT, this.getMaxOrderDuration(), this.getDynamicOrderDuration(), this.getMaxSlippageExit(), "", "GTC", "", false, true);
                }
                break;
            case SELL:
                logger.log(Level.INFO, "501,Strategy SELL,{0}", new Object[]{getStrategy() + delimiter + Parameters.symbol.get(id)});
                    HashMap<String, Object> order = new HashMap<>();
                    order.put("id", id);
                    order.put("side", EnumOrderSide.SELL);
                    order.put("size", size);
                    order.put("type", EnumOrderType.LMT);
                    order.put("limitprice", Parameters.symbol.get(id).getLastPrice());
                    order.put("reason", EnumOrderReason.REGULAREXIT);
                    order.put("orderstage", EnumOrderStage.INIT);
                    order.put("expiretime", this.getMaxOrderDuration());
                    order.put("dynamicorderduration", getDynamicOrderDuration());
                    order.put("maxslippage", this.getMaxSlippageExit());
                this.exit(order);
                  //  this.exit(id, EnumOrderSide.SELL, size, EnumOrderType.LMT, Parameters.symbol.get(id).getLastPrice(), 0, EnumOrderReason.REGULAREXIT, EnumOrderStage.INIT, this.getMaxOrderDuration(), this.getDynamicOrderDuration(), this.getMaxSlippageExit(), "", "GTC", "", false, true);
                longsExitedToday.add(id);
                break;
            case SHORT:
                if (rollover) {
                    id = Utilities.getNextExpiryID(Parameters.symbol, id, expiryFarMonth);
                }
                if (!shortsExitedToday.contains(Integer.valueOf(id))) {
                    size = Parameters.symbol.get(id).getMinsize() * this.getNumberOfContracts();
                    logger.log(Level.INFO, "501,Strategy SHORT,{0}", new Object[]{getStrategy() + delimiter + Parameters.symbol.get(id)});
                    order = new HashMap<>();
                    order.put("id", id);
                    order.put("side", EnumOrderSide.SHORT);
                    order.put("size", size);
                    order.put("type", EnumOrderType.LMT);
                    order.put("limitprice", Parameters.symbol.get(id).getLastPrice());
                    order.put("reason", EnumOrderReason.REGULARENTRY);
                    order.put("orderstage", EnumOrderStage.INIT);
                    order.put("expiretime", this.getMaxOrderDuration());
                    order.put("dynamicorderduration", getDynamicOrderDuration());
                    order.put("maxslippage", this.getMaxSlippageEntry());
                    this.entry(order);
//                    this.entry(id, EnumOrderSide.SHORT, size, EnumOrderType.LMT, Parameters.symbol.get(id).getLastPrice(), 0, EnumOrderReason.REGULARENTRY, EnumOrderStage.INIT, this.getMaxOrderDuration(), this.getDynamicOrderDuration(), this.getMaxSlippageExit(), "", "GTC", "", false, true);
                }
                break;
            case COVER:
                logger.log(Level.INFO, "501,Strategy COVER,{0}", new Object[]{getStrategy() + delimiter + Parameters.symbol.get(id)});
                    order = new HashMap<>();
                    order.put("id", id);
                    order.put("side", EnumOrderSide.COVER);
                    order.put("size", size);
                    order.put("type", EnumOrderType.LMT);
                    order.put("limitprice", Parameters.symbol.get(id).getLastPrice());
                    order.put("reason", EnumOrderReason.REGULAREXIT);
                    order.put("orderstage", EnumOrderStage.INIT);
                    order.put("expiretime", this.getMaxOrderDuration());
                    order.put("dynamicorderduration", getDynamicOrderDuration());
                    order.put("maxslippage", this.getMaxSlippageExit());
                    this.exit(order);
                //this.exit(id, EnumOrderSide.COVER, size, EnumOrderType.LMT, Parameters.symbol.get(id).getLastPrice(), 0, EnumOrderReason.REGULAREXIT, EnumOrderStage.INIT, this.getMaxOrderDuration(), this.getDynamicOrderDuration(), this.getMaxSlippageExit(), "", "GTC", "", false, true);
                if (rollover) {
                    id = Utilities.getNextExpiryID(Parameters.symbol, id, expiryFarMonth);
                }
                shortsExitedToday.add(id);
                break;
            case BUYPORT:
                if (rollover) {
                    id = Utilities.getNextExpiryID(Parameters.symbol, id, expiryFarMonth);
                }
                if (!longsExitedToday.contains(Integer.valueOf(id))) {
                    double distance = today_predict_prob - threshold;
                    logger.log(Level.INFO, "501,Strategy BUYPORT,{0}", new Object[]{getStrategy() + delimiter + Parameters.symbol.get(id).getDisplayname() + delimiter + distance + delimiter + (100 - (sensitivity * 100))});
                    thresholdDistance.put(id, distance);
                    longPositionScore.put(100 - (sensitivity * 100), id);
                }
                break;
            case SHORTPORT:
                if (rollover) {
                    id = Utilities.getNextExpiryID(Parameters.symbol, id, expiryFarMonth);
                }
                if (!shortsExitedToday.contains(Integer.valueOf(id))) {
                    double distance = today_predict_prob - threshold;
                    logger.log(Level.INFO, "501,Strategy SHORTPORT,{0}", new Object[]{getStrategy() + delimiter + Parameters.symbol.get(id).getDisplayname() + delimiter + distance + delimiter + (100 - (specificity * 100))});
                    thresholdDistance.put(id, today_predict_prob - threshold);
                    shortPositionScore.put(100 - (specificity * 100), id);
                }
                break;
            default:
                break;
        }
    }

    private HashMap<String, Double> getStats(RConnection c, int referenceid, boolean today) {
        HashMap<String, Double> out = new HashMap<>();
        BeanSymbol sRef = Parameters.symbol.get(referenceid);
        if (sRef.getDataLength(EnumBarSize.DAILY, "settle") > 100) {
            if (today) {
                //create the last bar
                sRef.setTimeSeries(EnumBarSize.DAILY, openTime, new String[]{"open", "high", "low", "settle", "volume"}, new double[]{sRef.getOpenPrice(), sRef.getHighPrice(), sRef.getLowPrice(), sRef.getLastPrice(), sRef.getVolume()});
            }
            DoubleMatrix datr=ind.atr(sRef.getTimeSeries(EnumBarSize.DAILY, "high"), sRef.getTimeSeries(EnumBarSize.DAILY, "low"), sRef.getTimeSeries(EnumBarSize.DAILY, "settle"), 1);
            DoubleMatrix dtrend = ind.swing(sRef, EnumBarSize.DAILY).getTimeSeries(EnumBarSize.DAILY, "trend");
            int[] indices = dtrend.ne(ReservedValues.EMPTY).findIndices();
            logger.log(Level.INFO, "102,SymbolDataLength,{0}", new Object[]{sRef.getDisplayname() + delimiter + indices.length});
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
            DoubleMatrix dclosezscore = ind.zscore(dclose, 10);
            DoubleMatrix dhighzscore = ind.zscore(dhigh, 10);
            DoubleMatrix dlowzscore = ind.zscore(dlow, 10);
            DoubleMatrix drsi = ind.rsi(dclose, 14);
            DoubleMatrix drsizscore = ind.zscore(drsi, 10);
            DoubleMatrix dma = ind.ma(dclose, 10);
            DoubleMatrix dmazscore = ind.zscore(dma, 10);
            DoubleMatrix dy = MatrixMethods.ref(dupdownbar, 1).eq(1);
            try {
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
                        + "closezscore=closezscore,"
                        + "highzscore=highzscore,"
                        + "lowzscore=lowzscore,"
                        + "mazscore=mazscore,"
                        + "y=y)");
                c.eval("data$tradedate<-as.POSIXct(as.numeric(as.character(data$tradedate))/1000,tz=\"Asia/Kolkata\",origin=\"1970-01-01\")");
                c.eval("data[data==-1000001] = NA");
                c.eval("data<-na.omit(data)");
//                c.eval("save(data,file=\"data_" + sRef.getDisplayname() + ".Rdata\")");
                c.eval("data$y<-as.factor(data$y)");
                String path = "\"" + parameterObjectPath + "/" + "fit_" + sRef.getDisplayname() + ".Rdata" + "\"";
                c.eval("load(" + path + ")");
                c.eval("today_predict_prob<-predict(fit$finalModel, newdata = data, type=\"raw\")");
                c.eval("actual<-fit$finalModel$fitted.values+fit$finalModel$residuals");
                c.eval("actual<-as.numeric(actual)");
                c.eval("prediction<-fit$finalModel$fitted.values");
                c.eval("prediction<-as.numeric(prediction)");
                c.eval("rocCurve   <- roc(response = actual, predictor = prediction)");
                c.eval("result.coords <- coords(rocCurve, \"best\", best.method=\"closest.topleft\", ret=c(\"threshold\", \"accuracy\",\"specificity\",\"sensitivity\"))");
                double threshold = c.eval("result.coords[\"threshold\"]").asDouble();
                double specificity = c.eval("result.coords[\"specificity\"]").asDouble();
                double sensitivity = c.eval("result.coords[\"sensitivity\"]").asDouble();
                double[] predict_prob = c.eval("today_predict_prob").asDoubles();
                int output = predict_prob.length;
                double today_predict_prob = predict_prob[output - 1];
                double atr=lValue(datr);
                TradingUtil.writeToFile(getStrategy() + ".csv", Parameters.symbol.get(referenceid).getDisplayname()
                        + "," + today
                        + "," + lValue(dhigh)
                        + "," + lValue(dlow)
                        + "," + lValue(dclose)
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
                        + "," + threshold
                        + "," + sensitivity
                        + "," + specificity
                        + "," + atr                        
                        + "," + lValue(dy));

                out.put("threshold", threshold);
                out.put("specificity", specificity);
                out.put("sensitivity", sensitivity);
                out.put("probability", today_predict_prob);
                out.put("atr", atr);
                
            } catch (Exception e) {
                logger.log(Level.SEVERE, null, e);
            }
        }
        return out;
    }

    private void portfolioTrades() {
        if (portfolio) {
            //Recalculate open positions to handle changes during scan.    
            if (this.getLongOnly()) {
                longpositionCount = Utilities.openPositionCount(Parameters.symbol, this.getOrderFile(), this.getStrategy(), this.getPointValue(), true);
            }
            if (this.getShortOnly()) {
                shortpositionCount = Utilities.openPositionCount(Parameters.symbol, this.getOrderFile(), this.getStrategy(), this.getPointValue(), false);
            }
            logger.log(Level.INFO, "501,LongPositionCount,{0}", new Object[]{getStrategy() + delimiter + longpositionCount});
            logger.log(Level.INFO, "501,ShortPositionCount,{0}", new Object[]{getStrategy() + delimiter + shortpositionCount});
  
            int longgap = maxPositions - longpositionCount;
            int shortgap = maxPositions - shortpositionCount;
            for (int id : longPositionScore.values()) {
                if (longgap > 0) {
                    longgap = longgap - 1;
                    int referenceid = Utilities.getReferenceID(Parameters.symbol, id, referenceCashType);
                   BeanSymbol sRef=Parameters.symbol.get(referenceid);
                   DoubleMatrix datr=ind.atr(sRef.getTimeSeries(EnumBarSize.DAILY, "high"), sRef.getTimeSeries(EnumBarSize.DAILY, "low"), sRef.getTimeSeries(EnumBarSize.DAILY, "settle"), 1);
                   int size = Parameters.symbol.get(id).getMinsize() * this.getNumberOfContracts();
                    HashMap<String, Object> order = new HashMap<>();
                    order.put("id", id);
                    order.put("side", EnumOrderSide.BUY);
                    order.put("size", size);
                    order.put("type", EnumOrderType.LMT);
                    order.put("limitprice", Parameters.symbol.get(id).getLastPrice());
                    order.put("reason", EnumOrderReason.REGULARENTRY);
                    order.put("orderstage", EnumOrderStage.INIT);
                    order.put("expiretime", this.getMaxOrderDuration());
                    order.put("dynamicorderduration", getDynamicOrderDuration());
                    order.put("maxslippage", this.getMaxSlippageEntry());
                    logger.log(Level.INFO, "501,Strategy BUY,{0}", new Object[]{getStrategy() + delimiter + "SHORT" + delimiter + Parameters.symbol.get(id).getDisplayname()});
                    int orderid = entry(order);
                    Stop tp = new Stop();
                    tp.stopType = EnumStopType.TAKEPROFIT;
                    tp.stopMode = EnumStopMode.POINT;
                    tp.stopValue=3*lValue(datr);
                    double distance = thresholdDistance.get(id);
                    distance = distance <= 0 ? 1 : (1 + Math.abs(distance));
                    tp.StopLevel = distance;
                    logger.log(Level.INFO, "501,StopParameters,{0}", new Object[]{Parameters.symbol.get(referenceid).getHighPrice() + delimiter + Parameters.symbol.get(referenceid).getLowPrice() + delimiter + Parameters.symbol.get(referenceid).getLastPrice() + delimiter + distance});
                    //tp.stopValue = Parameters.symbol.get(referenceid).getHighPrice() - Parameters.symbol.get(referenceid).getLowPrice();
                    //tp.stopValue = distance * tp.stopValue;
                    tp.stopValue = Math.max(getTickSize(), Utilities.roundTo(tp.stopValue, getTickSize()));
                    tp.recalculate = true;
                    Stop sl = new Stop();
                    sl.stopType = EnumStopType.STOPLOSS;
                    sl.stopMode = EnumStopMode.POINT;
                    sl.stopValue=lValue(datr);
                    //sl.stopValue = Parameters.symbol.get(referenceid).getLastPrice() - Parameters.symbol.get(referenceid).getLowPrice();
                    sl.stopValue = Math.max(getTickSize(), Utilities.roundTo(sl.stopValue, getTickSize()));
                    sl.recalculate = true;
                    ArrayList<Stop> stops = new ArrayList<>();
                    stops.add(sl);
                    stops.add(tp);
                    Trade.setStop(getTrades(), orderid + "_" + "Order", stops);
                }
            }

            for (int id : shortPositionScore.values()) {
                if (shortgap > 0) {
                    shortgap = shortgap - 1;
                    int size = Parameters.symbol.get(id).getMinsize() * this.getNumberOfContracts();
                    HashMap<String, Object> order = new HashMap<>();
                    order.put("id", id);
                    order.put("side", EnumOrderSide.SHORT);
                    order.put("size", size);
                    order.put("type", EnumOrderType.LMT);
                    order.put("limitprice", Parameters.symbol.get(id).getLastPrice());
                    order.put("reason", EnumOrderReason.REGULARENTRY);
                    order.put("orderstage", EnumOrderStage.INIT);
                    order.put("expiretime", this.getMaxOrderDuration());
                    order.put("dynamicorderduration", getDynamicOrderDuration());
                    order.put("maxslippage", this.getMaxSlippageExit());
                    logger.log(Level.INFO, "501,Strategy SHORT,{0}", new Object[]{getStrategy() + delimiter + "BUY" + delimiter + Parameters.symbol.get(id).getDisplayname()});
                    int orderid = entry(order);
                    Stop tp = new Stop();
                    tp.stopType = EnumStopType.TAKEPROFIT;
                    tp.stopMode = EnumStopMode.POINT;
                    int referenceid = Utilities.getReferenceID(Parameters.symbol, id, referenceCashType);
                    tp.stopValue = Parameters.symbol.get(id).getHighPrice() - Parameters.symbol.get(id).getLowPrice();
                    double distance = thresholdDistance.get(id);
                    distance = distance >= 0 ? 1 : (1 + Math.abs(distance));
                    tp.StopLevel = distance;
                    logger.log(Level.INFO, "501,StopParameters,{0}", new Object[]{Parameters.symbol.get(id).getHighPrice() + delimiter + Parameters.symbol.get(id).getLowPrice() + delimiter + Parameters.symbol.get(id).getLastPrice() + delimiter + distance});
                    tp.stopValue = Parameters.symbol.get(referenceid).getHighPrice() - Parameters.symbol.get(referenceid).getLowPrice();
                    tp.stopValue = distance * tp.stopValue;
                    tp.stopValue = Math.max(getTickSize(), Utilities.roundTo(tp.stopValue, getTickSize()));
                    tp.recalculate = true;
                    Stop sl = new Stop();
                    sl.stopType = EnumStopType.STOPLOSS;
                    sl.stopMode = EnumStopMode.POINT;
                    sl.stopValue = Parameters.symbol.get(referenceid).getHighPrice() - Parameters.symbol.get(referenceid).getLastPrice();
                    sl.stopValue = Math.max(getTickSize(), Utilities.roundTo(sl.stopValue, getTickSize()));
                    sl.recalculate = true;
                    ArrayList<Stop> stops = new ArrayList<>();
                    stops.add(sl);
                    stops.add(tp);
                    Trade.setStop(getTrades(), orderid + "_" + "Order", stops);
                }
            }
        }
    }
}
