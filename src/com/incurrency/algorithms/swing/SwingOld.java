/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.algorithms.swing;

import bsh.Interpreter;
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
import com.incurrency.framework.SimulationTimer;
import com.incurrency.framework.Stop;
import com.incurrency.framework.Trade;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import javax.swing.JFrame;

/**
 *
 * @author pankaj
 */
public class SwingOld extends Strategy implements TradeListener {

    private static final Logger logger = Logger.getLogger(Pairs.class.getName());
    String expiryNearMonth;
    String expiryFarMonth;
    String referenceCashType;
    String rServerIP;
    String parameterObjectPath;
    Date entryScanDate;
    String stopLossExpression;
    String takeProfitExpression;
    double stopLossPercentage;
    double takeProfitPercentage;
    double upProbabilityThreshold;
    double downProbabilityThreshold;
    String cassandraMetric;
    String[] timeSeries;
    long openTime;
    Timer eodProcessing;
    int testingTimer = 0;
    double sensitivityThreshold = 0.5;
    double specificityThreshold = 0.5;
    int maxPositions = 0;
    int longpositionCount = 0;
    int shortpositionCount = 0;
    boolean sameDayReentry = true;
    TreeMap<Double, Integer> longPositionScore = new TreeMap<>();
    TreeMap<Double, Integer> shortPositionScore = new TreeMap<>();
    HashMap<Integer, HashMap<String, Double>> signalValues = new HashMap<>(); //holds the symbol id: stats
    ArrayList<Integer> longsExitedToday = new ArrayList<>();
    ArrayList<Integer> shortsExitedToday = new ArrayList<>();
    Thread historicalDataRetriever;
    public Indicators ind = new Indicators();
    private final Object lockTradeReceived_1 = new Object();
    String buyCondition;
    String sellCondition;
    String shortCondition;
    String coverCondition;
    String ranking;
    HashSet<Integer> longIDs = new HashSet<>();
    HashSet<Integer> shortIDs = new HashSet<>();
    private DecimalFormat df = new DecimalFormat("#.00");
    private HashMap<Integer,CustomOrder> longOrders;
    private HashMap<Integer,CustomOrder> shortOrders;
    
    
    
    public SwingOld(MainAlgorithm m, Properties p, String parameterFile, ArrayList<String> accounts, Integer stratCount) {
        super(m, "swing", "FUT", p, parameterFile, accounts, stratCount);
        loadParameters(p);
        File dir = new File("logs");
        File f = new File(dir, getStrategy() + ".csv");
        if (!f.exists()) {
            TradingUtil.writeToFile(getStrategy() + ".csv", "symbol,scan,high,low,close,trend,daysinupswing,daysindownswing,daysoutsidetrend,daysintrend,closezscore,highzscore,lowzscore,mazscore,result,nextdayprob,atr,y");
        }

        String[] tempStrategyArray = parameterFile.split("\\.")[0].split("-|_");
        for (BeanConnection c : Parameters.connection) {
            c.getWrapper().addTradeListener(this);
            c.initializeConnection(tempStrategyArray[tempStrategyArray.length - 1]);
        }
        if (Subscribe.tes != null) {
            Subscribe.tes.addTradeListener(this);
        }
        // setStopOrders(true);
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
//                    if (s.getDataLength(EnumBarSize.DAILY, "settle") == 0 && (s.getType().equals("STK") || s.getType().equals("IND")) && s.getStrategy().toLowerCase().contains("swing")) {
                    if (s.getDataLength(EnumBarSize.DAILY, "settle") == 0 && s.getType().equals(referenceCashType) && s.getStrategy().toLowerCase().contains("swing")) {
                        Thread t = new Thread(new HistoricalBars(s, EnumSource.CASSANDRA, timeSeries, cassandraMetric, "yyyyMMdd HH:mm:ss", hdStartDate, hdEndDate, EnumBarSize.DAILY, false));
                        t.start();
                        while (t.getState() != Thread.State.TERMINATED) {
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException ex) {
                                Logger.getLogger(SwingOld.class.getName()).log(Level.SEVERE, null, ex);
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

        if (MainAlgorithm.isUseForSimulation()) {
            String entryScanTime = p.getProperty("EntryScanTime");
            String algorithmEndDate = Algorithm.globalProperties.getProperty("BackTestEndDate");
            Calendar c = Calendar.getInstance(TimeZone.getTimeZone(Algorithm.timeZone));
            Date algoDate = DateUtil.getFormattedDate(algorithmEndDate, "yyyyMMddHHmmss", Algorithm.timeZone);
            c.setTime(algoDate);
            String[] entryTimeComponents = entryScanTime.split(":");
            c.set(Calendar.HOUR_OF_DAY, Utilities.getInt(entryTimeComponents[0], 15));
            c.set(Calendar.MINUTE, Utilities.getInt(entryTimeComponents[1], 20));
            c.set(Calendar.SECOND, Utilities.getInt(entryTimeComponents[2], 0));
            entryScanDate = c.getTime();
            Thread s = new Thread(new SimulationTimer(entryScanDate, eodProcessingTask));
            s.start();
        } else {
            eodProcessing = new Timer("Timer: Close Positions");
            eodProcessing.schedule(eodProcessingTask, entryScanDate);
        }

        if (this.getLongOnly()) {
            longpositionCount = Utilities.openPositionCount(db, Parameters.symbol, this.getStrategy(), this.getPointValue(), true);
        }
        if (this.getShortOnly()) {
            shortpositionCount = Utilities.openPositionCount(db, Parameters.symbol, this.getStrategy(), this.getPointValue(), false);
        }
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "Max Open Position" + delimiter + maxPositions});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "Current Long Open Position" + delimiter + longpositionCount});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "Current Short Open Position" + delimiter + shortpositionCount});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "Entry Scan Time" + delimiter + entryScanDate});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "Stop Loss %" + delimiter + stopLossExpression});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "Take Profit %" + delimiter + takeProfitExpression});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "Time Series" + delimiter + timeSeries});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "Cassandra Metric" + delimiter + cassandraMetric});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "Testing Timer Duration" + delimiter + testingTimer});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "StopLoss" + delimiter + stopLossExpression});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "TakeProfit" + delimiter + takeProfitExpression});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "BuyCondition" + delimiter + buyCondition});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "SellCondition" + delimiter + sellCondition});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "ShortCondition" + delimiter + shortCondition});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "CoverCondition" + delimiter + coverCondition});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "RankingRule" + delimiter + ranking});

        Timer bodProcessing = new Timer("Timer: " + this.getStrategy() + " BODProcessing");
        bodProcessing.schedule(runBOD, 1 * 60 * 1000);
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
        stopLossExpression = p.getProperty("StopLoss", "0");
        takeProfitExpression = p.getProperty("TakeProfit", "0");
        upProbabilityThreshold = Utilities.getDouble(p.getProperty("UpProbabilityThreshold", "0.70"), 0.7);
        downProbabilityThreshold = Utilities.getDouble(p.getProperty("DownProbabilityThreshold", "0.3"), 0.3);
        timeSeries = p.getProperty("timeseries", "").toString().trim().split(",");
        cassandraMetric = p.getProperty("cassandrametric", "").toString().trim();
        testingTimer = Utilities.getInt(p.getProperty("TestingTimer"), 0);
        maxPositions = Integer.parseInt(p.getProperty("MaxPositions", "1").toString().trim());
        sameDayReentry = Boolean.parseBoolean(p.getProperty("SameDayReentry", "true").toString().trim());
        buyCondition = p.getProperty("BuyCondition", "false");
        shortCondition = p.getProperty("ShortCondition", "false");
        sellCondition = p.getProperty("SellCondition", "false");
        coverCondition = p.getProperty("CoverCondition", "false");
        sameDayReentry = Boolean.parseBoolean(p.getProperty("SameDayReentry", "true").toString().trim());
        stopLossPercentage = Utilities.getDouble(p.getProperty("StopLossPercentage"), 1);
        ranking = p.getProperty("RankingRule", "1");
        takeProfitPercentage = Utilities.getDouble(p.getProperty("TakeProfitPercentage"), 5);
        String[] symbolNames = p.getProperty("longsymbols", "").split(",");
        for (String s : symbolNames) {
            int id = Utilities.getIDFromDisplaySubString(Parameters.symbol, s, referenceCashType);
            if (id >= 0) {
                this.longIDs.add(id);
            }
        }
        symbolNames = p.getProperty("shortsymbols", "").split(",");
        for (String s : symbolNames) {
            int id = Utilities.getIDFromDisplaySubString(Parameters.symbol, s, referenceCashType);
            if (id >= 0) {
                this.shortIDs.add(id);
            }
        }
    }

    @Override
    public void tradeReceived(TradeEvent event) {
        synchronized (lockTradeReceived_1) {
            Integer id = event.getSymbolID();
            if (this.getStrategySymbols().contains(id) && !Parameters.symbol.get(id).getType().equals(referenceCashType)) {
                if (this.getPosition().get(id).getPosition() > 0 && this.getPosition().get(id).getStrategy().equalsIgnoreCase(this.getStrategy())) {
                    int referenceid = Utilities.getReferenceID(Parameters.symbol, id, referenceCashType);
                    Double tradePrice = this.getPosition().get(id).getPrice();
                    ArrayList<Stop> stops = Trade.getStop(db, this.getStrategy() + ":" + this.getFirstInternalOpenOrder(id, EnumOrderSide.SELL, "Order") + ":Order");
                    boolean tpTrigger = false;
                    boolean slTrigger = false;
                    double tpDistance = 0D;
                    double slDistance = 0D;
                    double sl = Double.MAX_VALUE;
                    double tp = Double.MAX_VALUE;
                    if (stops == null && Parameters.symbol.get(id).getLastPrice() != 0) {
                        slTrigger = Parameters.symbol.get(id).getLastPrice() != 0 && (Parameters.symbol.get(id).getLastPrice() <= tradePrice * (1 - stopLossPercentage / 100));
                        tpTrigger = Parameters.symbol.get(id).getLastPrice() != 0 && (Parameters.symbol.get(id).getLastPrice() >= tradePrice * (1 + takeProfitPercentage / 100));
                    } else if (stops != null && Parameters.symbol.get(id).getLastPrice() != 0) {
                        for (Stop stop : stops) {
                            switch (stop.stopType) {
                                case TAKEPROFIT:
                                    tpDistance = Parameters.symbol.get(id).getLastPrice() - tradePrice;
                                    tp = stop.stopValue;
                                    tpTrigger = tp != 0 && Parameters.symbol.get(id).getLastPrice() != 0 && tpDistance >= tp;
                                    break;
                                case STOPLOSS:
                                    if (stop.underlyingEntry != 0) {
                                        slDistance = stop.underlyingEntry - Parameters.symbol.get(referenceid).getLastPrice();
                                        sl = stop.stopValue;
                                        slTrigger = sl != 0 && Parameters.symbol.get(id).getLastPrice() != 0 && Parameters.symbol.get(referenceid).getLastPrice() != 0 && slDistance >= sl;
                                    }
                                    break;
                                default:
                                    break;
                            }
                        }
                    }
                    if (!this.isStopOrders() && (slTrigger || tpTrigger)) {
                        logger.log(Level.INFO, "501,Long SLTP Exit,{0}", new Object[]{this.getStrategy() + delimiter + Parameters.symbol.get(id).getDisplayname() + delimiter + slTrigger + delimiter + tpTrigger + delimiter + Parameters.symbol.get(id).getLastPrice() + delimiter + slDistance + delimiter + tpDistance + delimiter + sl + delimiter + tp});
                        int size = this.getPosition().get(id).getPosition();
                        HashMap<String, Object> order = new HashMap<>();
                        order.put("id", id);
                        order.put("side", EnumOrderSide.SELL);
                        order.put("size", size);
                        order.put("type", EnumOrderType.LMT);
                        order.put("limitprice", Parameters.symbol.get(id).getLastPrice());
                        if (slTrigger) {
                            order.put("reason", EnumOrderReason.SL);
                        } else {
                            order.put("reason", EnumOrderReason.TP);
                        }
                        order.put("orderstage", EnumOrderStage.INIT);
                        order.put("expiretime", this.getMaxOrderDuration());
                        order.put("dynamicorderduration", getDynamicOrderDuration());
                        order.put("maxslippage", this.getMaxSlippageExit());
                        order.put("log","SLTPExit"+delimiter+ slTrigger + delimiter + tpTrigger + delimiter + Parameters.symbol.get(id).getLastPrice() + delimiter + slDistance + delimiter + sl + delimiter + tpDistance + delimiter + tp);
                        this.exit(order);
//                    this.exit(id, EnumOrderSide.SELL, size, EnumOrderType.LMT, Parameters.symbol.get(id).getLastPrice(), 0, EnumOrderReason.REGULAREXIT, EnumOrderStage.INIT, this.getMaxOrderDuration(), this.getDynamicOrderDuration(), this.getMaxSlippageExit(), "", "GTC", "", false, true);
                        if (rolloverDay()) {
                            id = Utilities.getNextExpiryID(Parameters.symbol, id, expiryFarMonth);
                        }
                        longsExitedToday.add(id);
                    }
                } else if (this.getPosition().get(id).getPosition() < 0 && this.getPosition().get(id).getStrategy().equalsIgnoreCase(this.getStrategy())) {
                    Double tradePrice = this.getPosition().get(id).getPrice();
                    int referenceid = Utilities.getReferenceID(Parameters.symbol, id, referenceCashType);
                    int internalorderid = this.getFirstInternalOpenOrder(id, EnumOrderSide.COVER, "Order");
                    ArrayList<Stop> stops = Trade.getStop(db, this.getStrategy() + ":" + internalorderid + ":Order");
                    boolean tpTrigger = false;
                    boolean slTrigger = false;
                    double tpDistance = 0D;
                    double slDistance = 0D;
                    double sl = Double.MAX_VALUE;
                    double tp = Double.MAX_VALUE;
                    if (stops == null && Parameters.symbol.get(id).getLastPrice() != 0) {
                        slTrigger = Parameters.symbol.get(id).getLastPrice() != 0 && (Parameters.symbol.get(id).getLastPrice() >= tradePrice * (1 + stopLossPercentage / 100));
                        tpTrigger = Parameters.symbol.get(id).getLastPrice() != 0 && (Parameters.symbol.get(id).getLastPrice() <= tradePrice * (1 - takeProfitPercentage / 100));
                    } else if (stops != null && Parameters.symbol.get(id).getLastPrice() != 0) {
                        for (Stop stop : stops) {
                            switch (stop.stopType) {
                                case TAKEPROFIT:
                                    tpDistance = tradePrice - Parameters.symbol.get(id).getLastPrice();
                                    tp = stop.stopValue;
                                    tpTrigger = tp != 0 && Parameters.symbol.get(id).getLastPrice() != 0 && tpDistance >= tp;
                                    break;
                                case STOPLOSS:
                                    if (stop.underlyingEntry != 0) {
                                        slDistance = Parameters.symbol.get(referenceid).getLastPrice() - stop.underlyingEntry;
                                        sl = stop.stopValue;
                                        slTrigger = sl != 0 && Parameters.symbol.get(id).getLastPrice() != 0 && Parameters.symbol.get(referenceid).getLastPrice() != 0 && slDistance >= sl;
                                    }
                                    break;
                                default:
                                    break;
                            }
                        }
                    }
                    if (!this.isStopOrders() && (slTrigger || tpTrigger)) {
                        logger.log(Level.INFO, "501,Short SLTP Exit,{0}", new Object[]{this.getStrategy() + delimiter + Parameters.symbol.get(id).getDisplayname() + delimiter + slTrigger + delimiter + tpTrigger + delimiter + Parameters.symbol.get(id).getLastPrice() + delimiter + slDistance + delimiter + tpDistance + delimiter + sl + delimiter + tp});
                        int size = this.getPosition().get(id).getPosition();
                        HashMap<String, Object> order = new HashMap<>();
                        order.put("id", id);
                        order.put("side", EnumOrderSide.COVER);
                        order.put("size", size);
                        order.put("type", EnumOrderType.LMT);
                        order.put("limitprice", Parameters.symbol.get(id).getLastPrice());
                        if (slTrigger) {
                            order.put("reason", EnumOrderReason.SL);
                        } else {
                            order.put("reason", EnumOrderReason.TP);
                        }
                        order.put("orderstage", EnumOrderStage.INIT);
                        order.put("expiretime", this.getMaxOrderDuration());
                        order.put("dynamicorderduration", getDynamicOrderDuration());
                        order.put("maxslippage", this.getMaxSlippageExit());
                        order.put("orderref", this.getStrategy());
                        order.put("log","SLTPExit"+delimiter+ slTrigger + delimiter + tpTrigger + delimiter + Parameters.symbol.get(id).getLastPrice() + delimiter + slDistance + delimiter + sl + delimiter + tpDistance + delimiter + tp);
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
    TimerTask runBOD = new TimerTask() {
        public void run() {
            while (historicalDataRetriever != null && historicalDataRetriever.getState() != Thread.State.TERMINATED) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ex) {
                    Logger.getLogger(SwingOld.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            updateStops();
        }
    };
    TimerTask eodProcessingTask = new TimerTask() {
        @Override
        public void run() {
            scan(true);
        }
    };

    private void updateStops() {
        logger.log(Level.INFO, "501,BODProcess_UpdateStop,{0}", new Object[]{this.getStrategy()});
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
        for (String key : db.getKeys("opentrades")) {
            ArrayList<Stop> stops = Trade.getStop(db, key);
            if (stops != null) {
                String childsymboldisplayname = Trade.getEntrySymbol(db, key);
                int childid = Utilities.getIDFromDisplayName(Parameters.symbol, childsymboldisplayname);
                int referenceid = -1;
                if (childid >= 0) {
                    referenceid = Utilities.getReferenceID(Parameters.symbol, childid, referenceCashType);
                }
                if (referenceid >= 0) {
                    HashMap<String, Double> stats = getStats(c, referenceid, false);
                    if (!stats.isEmpty()) {
                        for (Stop stop : stops) {
                            if (stop.recalculate == Boolean.TRUE) {
                                if (childid >= 0) {
                                    updateStop(stats, key, childid, stop);
                                }
                            }
                        }
                        Trade.setStop(db, key, "opentrades", stops);
                    }
                }
            }
        }
        if (c != null) {
            c.close();
        }
    }

    private void reconTrades(){
        //calculate expected positions for each symbol
        //check if position exists. 
        //If exists on correct side, do nothigh
        //if exists on wrong side, exit position. 
        //if does not exist and current price is within threshold, reenter position.
        
        scan(false);
        Set<Integer>longPositionsAtRisk=new HashSet<Integer>();
        Set<Integer>shortPositionsAtRisk=new HashSet<Integer>();
        for (String key : db.getKeys("opentrades")) {
            ArrayList<Stop> stops = Trade.getStop(db, key);
            String entryTime=Trade.getEntryTime(db, key);
            entryTime=entryTime.substring(0, 10);
            entryTime=entryTime.replaceAll("-", "");
            if (stops != null) {
                String childsymboldisplayname = Trade.getEntrySymbol(db, key);
                int childid = Utilities.getIDFromDisplayName(Parameters.symbol, childsymboldisplayname);
                int referenceid = -1;
                if (childid >= 0) {
                    referenceid = Utilities.getReferenceID(Parameters.symbol, childid, referenceCashType);
                }
                if (referenceid >= 0 && Trade.getEntrySide(db, key).equals(EnumOrderSide.BUY) && entryTime.equals(Algorithm.instratInfo.getProperty("prd"))) {
                    longPositionsAtRisk.add(referenceid);
                } else if (referenceid >= 0 && Trade.getEntrySide(db, key).equals(EnumOrderSide.SHORT) && entryTime.equals(Algorithm.instratInfo.getProperty("prd"))) {
                    shortPositionsAtRisk.add(referenceid);
                }
            }
        }
        
        //add positions taken on on the prior working day
        
        //update mising long orders
        
        
    }
    
    private void updateStop(HashMap<String, Double> stats, String key, int childid, Stop stop) {
        int referenceid = Utilities.getReferenceID(Parameters.symbol, childid, referenceCashType);
        if (!stats.isEmpty()) {
            double threshold = Utilities.getDouble(stats.get("threshold"), 0.5);
            double prob = Utilities.getDouble(stats.get("probability"), 0.5);
            double atr = Utilities.getDouble(stats.get("atr"), 1);
            String tradeTimeFormat = "yyyy-MM-dd HH:mm:ss";
            String tradeDateString = Trade.getEntryTime(db, key);
            Date tradeDate = DateUtil.parseDate(tradeTimeFormat, tradeDateString, Algorithm.timeZone);
            long historicalTime = Parameters.symbol.get(referenceid).getTimeFloor(EnumBarSize.DAILY, tradeDate.getTime(), "settle");
            if (historicalTime != ReservedValues.EMPTY) {
                //check if dates match
                Interpreter interpreter = new Interpreter();
                String historicalDateString = DateUtil.getFormatedDate("yyyy-MM-dd", historicalTime, TimeZone.getTimeZone(Algorithm.timeZone));
                tradeDateString = tradeDateString.substring(0, 10);//get the day part
                //               if (tradeDateString.equals(historicalDateString)) {//only update stops if historical data exists for the trade date
                double close = Parameters.symbol.get(referenceid).getTimeSeriesValue(EnumBarSize.DAILY, historicalTime, "settle");
                double high = Parameters.symbol.get(referenceid).getTimeSeriesValue(EnumBarSize.DAILY, historicalTime, "high");
                double low = Parameters.symbol.get(referenceid).getTimeSeriesValue(EnumBarSize.DAILY, historicalTime, "low");
                try {
                    interpreter.set("atr", atr);
                    interpreter.set("high", high);
                    interpreter.set("low", low);
                    interpreter.set("close", close);
                    interpreter.set("prob", prob);
                } catch (Exception e) {
                    logger.log(Level.SEVERE, null, e);
                }
                EnumOrderSide side = Trade.getEntrySide(db, key);
                switch (side) {
                    case BUY:
                        EnumStopType type = stop.stopType;
                        switch (type) {
                            case STOPLOSS:
                                try {
                                    interpreter.set("barslpoints", close - low);
                                    interpreter.set("bartppoints", high - close);
                                    interpreter.eval("stop=" + stopLossExpression);
                                    stop.stopValue = Utilities.getDouble(interpreter.get("stop"), 0);
                                } catch (Exception e) {
                                    logger.log(Level.SEVERE, null, e);
                                }
                                stop.stopValue = Math.max(stop.stopValue, 0);
                                stop.stopValue = Utilities.round(stop.stopValue, getTickSize(), 2);
                                stop.underlyingEntry = close;
                                logger.log(Level.INFO, "501,UpdatedSLStop,{0}", new Object[]{getStrategy() + delimiter + Parameters.symbol.get(referenceid).getDisplayname() + delimiter + atr + delimiter + 1 + delimiter + stop.stopValue + delimiter + stop.underlyingEntry});
                                break;
                            case TAKEPROFIT:
                                try {
                                    interpreter.set("barslpoints", close - low);
                                    interpreter.set("bartppoints", high - close);
                                    interpreter.eval("stop=" + takeProfitExpression);
                                    stop.stopValue = Utilities.getDouble(interpreter.get("stop"), 0);
                                } catch (Exception e) {
                                    logger.log(Level.SEVERE, null, e);
                                }
                                stop.stopValue = Math.max(stop.stopValue, 0);
                                stop.stopValue = Utilities.round(stop.stopValue, getTickSize(), 2);
                                logger.log(Level.INFO, "501,UpdatedTPStop,{0}", new Object[]{getStrategy() + delimiter + Parameters.symbol.get(referenceid).getDisplayname() + delimiter + atr + delimiter + 1 + delimiter + stop.stopValue});
                                break;
                            default:
                                break;
                        }
                        break;
                    case SHORT:
                        type = stop.stopType;
                        switch (type) {
                            case STOPLOSS:
                                try {
                                    interpreter.set("barslpoints", high - close);
                                    interpreter.set("bartppoints", close - low);
                                    interpreter.eval("stop=" + stopLossExpression);
                                    stop.stopValue = Utilities.getDouble(interpreter.get("stop"), 0);
                                } catch (Exception e) {
                                    logger.log(Level.SEVERE, null, e);
                                }
                                stop.stopValue = Math.max(stop.stopValue, 0);
                                stop.stopValue = Utilities.round(stop.stopValue, getTickSize(), 2);
                                stop.underlyingEntry = close;
                                logger.log(Level.INFO, "501,UpdatedSLStop,{0}", new Object[]{getStrategy() + delimiter + Parameters.symbol.get(referenceid).getDisplayname() + delimiter + atr + delimiter + 1 + delimiter + stop.stopValue + delimiter + stop.underlyingEntry});
                                break;
                            case TAKEPROFIT:
                                try {
                                    interpreter.set("barslpoints", high - close);
                                    interpreter.set("bartppoints", close - low);
                                    interpreter.eval("stop=" + takeProfitExpression);
                                    stop.stopValue = Utilities.getDouble(interpreter.get("stop"), 0);
                                } catch (Exception e) {
                                    logger.log(Level.SEVERE, null, e);
                                }
                                stop.stopValue = Math.max(stop.stopValue, 0);
                                stop.stopValue = Utilities.round(stop.stopValue, getTickSize(), 2);
                                logger.log(Level.INFO, "501,UpdatedTPStop,{0}", new Object[]{getStrategy() + delimiter + Parameters.symbol.get(referenceid).getDisplayname() + delimiter + atr + delimiter + stop.stopValue});
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

    private void scan(boolean today) {
        logger.log(Level.INFO, "501,Scan,{0}", new Object[]{this.getStrategy()});
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
            c.eval("library(caret)");
            String[] out = c.eval("search()").asStrings();
            for (int i = 0; i < out.length; i++) {
                System.out.println("Loaded Package:" + out[i]);
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, null, e);
        }
        for (BeanSymbol s : Parameters.symbol) {
            if (!s.getType().equals(referenceCashType) && s.getExpiry() != null && !s.getExpiry().equals(expiryFarMonth)) {
                int id = s.getSerialno() - 1;
                int referenceid = Utilities.getReferenceID(Parameters.symbol, id, referenceCashType);
                if (referenceid >= 0) {
                    BeanSymbol sRef = Parameters.symbol.get(referenceid);
                    HashMap<String, Double> stats = getStats(c, referenceid, today);
                    if (!stats.isEmpty()) {
                        double today_predict_prob = Utilities.getDouble(stats.get("probability"), -1);
                        double result = Utilities.getDouble(stats.get("result"), 2);
                        double trend = Utilities.getDouble(stats.get("trend"), 0);
                        int size = this.getPosition().get(id).getPosition();
                        Trigger swingTrigger = Trigger.UNDEFINED;
                        Interpreter interpreter = new Interpreter();
                        boolean cBuy = false;
                        boolean cSell = false;
                        boolean cShort = false;
                        boolean cCover = false;
                        try {
                            interpreter.set("result", Utilities.roundTo(result, 1));
                            interpreter.set("prob", today_predict_prob);
                            interpreter.set("trend", trend);
                            interpreter.eval("cBuy=" + buyCondition);//.getLastPrice() != 0 && this.getLongOnly() && size == 0");
                            interpreter.eval("cShort=" + shortCondition);
                            interpreter.eval("cSell=" + sellCondition);
                            interpreter.eval("cCover=" + coverCondition);
                            logger.log(Level.INFO, "Symbol:{0},Result:{1},Today Prob:{2},Trend:{3},Current Position:{4},BuyCondition:{5},ShortCondition:{6},SellCondition:{7},CoverCondition:{8}",
                                    new Object[]{s.getDisplayname(), Utilities.roundTo(result, 1), today_predict_prob, trend, size, interpreter.get("cBuy").toString(),
                                interpreter.get("cShort").toString(), interpreter.get("cSell").toString(), interpreter.get("cCover").toString()});
                            cBuy = (boolean) interpreter.get("cBuy") && s.getLastPrice() != 0 && this.getLongOnly() && !this.isStopOrders() && size <= 0;
                            cShort = (boolean) interpreter.get("cShort") && s.getLastPrice() != 0 && this.getShortOnly() && !this.isStopOrders() && size >= 0;
                            cSell = (boolean) interpreter.get("cSell") && s.getLastPrice() != 0 && !this.isStopOrders() && size > 0;
                            cCover = (boolean) interpreter.get("cCover") && s.getLastPrice() != 0 && !this.isStopOrders() && size < 0;
                            logger.log(Level.INFO, "Symbol:{0},Result:{1},Today Prob:{2},Trend:{3},Current Position:{4},BuySignal:{5},ShortSignal:{6},SellSignal:{7},CoverSignal:{8}",
                                    new Object[]{s.getDisplayname(), Utilities.roundTo(result, 1), today_predict_prob, trend, size, cBuy, cShort, cSell, cCover});
                        } catch (Exception e) {
                            logger.log(Level.SEVERE, null, e);
                        }

                        //First Handle Exits
                        if (cSell) {
                            swingTrigger = Trigger.SELL;
                        } else if (cCover) {
                            swingTrigger = Trigger.COVER;
                        }
                        processSignal(id, swingTrigger, stats);

                        //Then handle entries                       
                        swingTrigger = Trigger.UNDEFINED;
                        if (cBuy) {
                            swingTrigger = Trigger.BUY;
                        } else if (cShort) {
                            swingTrigger = Trigger.SHORT;
                        }
                        processSignal(id, swingTrigger, stats);
                        }
                    }
                }
            }
        portfolioTrades();
        }

    private void processSignal(int id, Trigger swingTrigger, HashMap<String, Double> stats) {
        int size = 0;
        boolean rollover = rolloverDay();

        switch (swingTrigger) {
            case SELL:
                logger.log(Level.INFO, "501,Strategy SELL,{0}", new Object[]{getStrategy() + delimiter + Parameters.symbol.get(id).getDisplayname()});
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
                order.put("log","SELL"+delimiter+ stats.get("trend") + delimiter + df.format(stats.get("probability")));
                this.exit(order);
                //  this.exit(id, EnumOrderSide.SELL, size, EnumOrderType.LMT, Parameters.symbol.get(id).getLastPrice(), 0, EnumOrderReason.REGULAREXIT, EnumOrderStage.INIT, this.getMaxOrderDuration(), this.getDynamicOrderDuration(), this.getMaxSlippageExit(), "", "GTC", "", false, true);
                longsExitedToday.add(id);
                break;
            case COVER:
                logger.log(Level.INFO, "501,Strategy COVER,{0}", new Object[]{getStrategy() + delimiter + Parameters.symbol.get(id).getDisplayname()});
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
                order.put("log","COVER"+delimiter+ stats.get("trend") + delimiter + df.format(stats.get("probability")));
                this.exit(order);
                //this.exit(id, EnumOrderSide.COVER, size, EnumOrderType.LMT, Parameters.symbol.get(id).getLastPrice(), 0, EnumOrderReason.REGULAREXIT, EnumOrderStage.INIT, this.getMaxOrderDuration(), this.getDynamicOrderDuration(), this.getMaxSlippageExit(), "", "GTC", "", false, true);
                if (rollover) {
                    id = Utilities.getNextExpiryID(Parameters.symbol, id, expiryFarMonth);
                }
                shortsExitedToday.add(id);
                break;
            case BUY:
                if (rollover) {
                    id = Utilities.getNextExpiryID(Parameters.symbol, id, expiryFarMonth);
                }
                if (sameDayReentry || (!sameDayReentry && !longsExitedToday.contains(Integer.valueOf(id)))) {
                    double score = 1;//specifiy rule for calculating the score
                    try {
                        Interpreter interpreter = new Interpreter();
                        interpreter.set("prob", stats.get("probability"));
                        interpreter.set("daysinswing", stats.get("daysinupswing") + stats.get("daysindownswing"));
                        interpreter.eval("result=" + ranking);
                        score = Double.valueOf(interpreter.get("result").toString());
                    } catch (Exception e) {
                        logger.log(Level.SEVERE, null, e);
                    }
                    logger.log(Level.INFO, "501,Strategy BUYPORT,{0}", new Object[]{getStrategy() + delimiter + Parameters.symbol.get(id).getDisplayname() + delimiter + score});
                    longPositionScore.put(score, id);
                    signalValues.put(id, stats);
                }
                break;
            case SHORT:
                if (rollover) {
                    id = Utilities.getNextExpiryID(Parameters.symbol, id, expiryFarMonth);
                }
                if (sameDayReentry || (!sameDayReentry && !shortsExitedToday.contains(Integer.valueOf(id)))) {
                    double score = 1;//specifiy rule for calculating the score
                    try {
                        Interpreter interpreter = new Interpreter();
                        interpreter.set("prob", stats.get("probability"));
                        interpreter.set("daysinswing", stats.get("daysinupswing") + stats.get("daysindownswing"));
                        interpreter.eval("result=" + ranking);
                        score = Double.valueOf(interpreter.get("result").toString());
                    } catch (Exception e) {
                        logger.log(Level.SEVERE, null, e);
                    }
                    logger.log(Level.INFO, "501,Strategy SHORTPORT,{0}", new Object[]{getStrategy() + delimiter + Parameters.symbol.get(id).getDisplayname() + delimiter + score});
                    shortPositionScore.put(score, id);
                    signalValues.put(id, stats);

                }
                break;
            default:
                break;
        }
    }

    private HashMap<String, Double> getStats(RConnection c, int referenceid, boolean today) {
        HashMap<String, Double> out = new HashMap<>();
        try {
            BeanSymbol sRef = Parameters.symbol.get(referenceid);
            if (sRef.getDataLength(EnumBarSize.DAILY, "settle") > 100) {
                if (today) {
                    //create the last bar
                    sRef.setTimeSeries(EnumBarSize.DAILY, openTime, new String[]{"open", "high", "low", "settle", "volume"}, new double[]{sRef.getOpenPrice(), sRef.getHighPrice(), sRef.getLowPrice(), sRef.getLastPrice(), sRef.getVolume()});
                }
                DoubleMatrix dtrend = ind.swing(sRef, EnumBarSize.DAILY).getTimeSeries(EnumBarSize.DAILY, "trend");
                int[] indices = dtrend.ne(ReservedValues.EMPTY).findIndices();
                logger.log(Level.FINE, "502,SymbolDataLength,{0}", new Object[]{sRef.getDisplayname() + delimiter + indices.length});
                DoubleMatrix datr = ind.atr(sRef.getTimeSeries(EnumBarSize.DAILY, "high"), sRef.getTimeSeries(EnumBarSize.DAILY, "low"), sRef.getTimeSeries(EnumBarSize.DAILY, "settle"), 10);
                indices = Utilities.addArraysNoDuplicates(indices, datr.ne(ReservedValues.EMPTY).findIndices());
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
                datr = MatrixMethods.getSubSetVector(datr, indices);
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
                c.eval("predict.raw<-predict(fit, newdata = data, type=\"prob\")");
                c.eval("result<-apply(predict.raw,1,which.max)-1");
                c.eval("predict.raw<-apply(predict.raw,1,max)");
                double[] predict_prob = c.eval("predict.raw").asDoubles();
                double[] result = c.eval("result").asDoubles();
                int output = predict_prob.length;
                double today_predict_prob = predict_prob[output - 1];
                double today_result = result[output - 1];
                double atr = lValue(datr);
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
                        + "," + today_result
                        + "," + today_predict_prob
                        + "," + atr
                        + "," + lValue(dy));

                out.put("result", today_result);
                out.put("probability", today_predict_prob);
                out.put("atr", atr);
                out.put("trend", lValue(dtrend));
                out.put("daysinupswing", lValue(ddaysinupswing));
                out.put("daysindownswing", lValue(ddaysindownswing));
                out.put("trend", lValue(dtrend));

            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, null, e);
        }
        return out;
    }

    private void portfolioTrades() {
        //Recalculate open positions to handle changes during scan.    
        if (this.getLongOnly()) {
            longpositionCount = Utilities.openPositionCount(db, Parameters.symbol, this.getStrategy(), this.getPointValue(), true);
        }else{
            longpositionCount=maxPositions;
        }
        if (this.getShortOnly()) {
            shortpositionCount = Utilities.openPositionCount(db, Parameters.symbol, this.getStrategy(), this.getPointValue(), false);
        }else{
            shortpositionCount=maxPositions;
        }
        logger.log(Level.INFO, "501,LongPositionCount,{0}", new Object[]{getStrategy() + delimiter + longpositionCount});
        logger.log(Level.INFO, "501,ShortPositionCount,{0}", new Object[]{getStrategy() + delimiter + shortpositionCount});
        logger.log(Level.INFO, "501,LongCandidatesCount,{0}", new Object[]{getStrategy() + delimiter + longPositionScore.size()});
        logger.log(Level.INFO, "501,ShortCandidatesCount,{0}", new Object[]{getStrategy() + delimiter + shortPositionScore.size()});

        int longgap = maxPositions - longpositionCount;
        int shortgap = maxPositions - shortpositionCount;
        for (int id : longPositionScore.values()) {
            try {
                int referenceid = Utilities.getReferenceID(Parameters.symbol, id, referenceCashType);
                BeanSymbol sRef = Parameters.symbol.get(referenceid);
                int sRefid = sRef.getSerialno() - 1;
                if (longgap > 0 && this.longIDs.contains(new Integer(sRefid))) {
                    longgap = longgap - 1;
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
                    order.put("log","BUY"+delimiter+ signalValues.get(id).get("trend") + delimiter + df.format(signalValues.get(id).get("probability")));
                    logger.log(Level.INFO, "501,Strategy BUY,{0}", new Object[]{getStrategy() + delimiter + "BUY" + delimiter + Parameters.symbol.get(id).getDisplayname()});
                    int orderid = entry(order);
                    Stop tp = new Stop();
                    Stop sl = new Stop();
                    DoubleMatrix datr = ind.atr(sRef.getTimeSeries(EnumBarSize.DAILY, "high"), sRef.getTimeSeries(EnumBarSize.DAILY, "low"), sRef.getTimeSeries(EnumBarSize.DAILY, "settle"), 10);
                    Double atr = lValue(datr);
                    Double low = sRef.getLowPrice();
                    Double high = sRef.getHighPrice();
                    Double close = sRef.getLastPrice();
                    Double prob = Utilities.getDouble(signalValues.get(id).get("probability"), 0.5);
                    Interpreter interpreter = new Interpreter();
                    try {
                        interpreter.set("atr", atr);
                        interpreter.set("high", high);
                        interpreter.set("low", low);
                        interpreter.set("close", close);
                        interpreter.set("prob", prob);
                        interpreter.set("barslpoints", close - low);
                        interpreter.set("bartppoints", high - close);
                        interpreter.eval("profit=" + takeProfitExpression);
                        interpreter.eval("stop=" + stopLossExpression);
                        sl.stopValue = Utilities.getDouble(interpreter.get("stop"), 0);
                        tp.stopValue = Utilities.getDouble(interpreter.get("profit"), 0);
                        sl.stopValue = Utilities.round(sl.stopValue, getTickSize(), 2);
                        tp.stopValue = Utilities.round(tp.stopValue, getTickSize(), 2);
                    } catch (Exception e) {
                        logger.log(Level.SEVERE, null, e);
                    }
                    tp.stopType = EnumStopType.TAKEPROFIT;
                    tp.stopMode = EnumStopMode.POINT;
                    tp.recalculate = true;
                    sl.stopType = EnumStopType.STOPLOSS;
                    sl.stopMode = EnumStopMode.POINT;
                    sl.underlyingEntry = sRef.getLastPrice();
                    sl.recalculate = true;
                    logger.log(Level.INFO, "501,StopParameters,{0}", new Object[]{sRef.getHighPrice() + delimiter + sRef.getLowPrice() + delimiter + sRef.getLastPrice()});
                    ArrayList<Stop> stops = new ArrayList<>();
                    stops.add(sl);
                    stops.add(tp);
                    Trade.setStop(db, this.getStrategy() + ":" + orderid + ":" + "Order", "opentrades", stops);
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, null, e);
            }
        }

        for (int id : shortPositionScore.values()) {
            try {
                int referenceid = Utilities.getReferenceID(Parameters.symbol, id, referenceCashType);
                BeanSymbol sRef = Parameters.symbol.get(referenceid);
                int sRefid = sRef.getSerialno() - 1;
                if (shortgap > 0 && this.shortIDs.contains(new Integer(sRefid))) {
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
                    order.put("log","SHORT"+delimiter+ signalValues.get(id).get("trend") + delimiter + df.format(signalValues.get(id).get("probability")));
                    logger.log(Level.INFO, "501,Strategy SHORT,{0}", new Object[]{getStrategy() + delimiter + "SHORT" + delimiter + Parameters.symbol.get(id).getDisplayname()});
                    int orderid = entry(order);
                    Stop tp = new Stop();
                    Stop sl = new Stop();
                    DoubleMatrix datr = ind.atr(sRef.getTimeSeries(EnumBarSize.DAILY, "high"), sRef.getTimeSeries(EnumBarSize.DAILY, "low"), sRef.getTimeSeries(EnumBarSize.DAILY, "settle"), 10);
                    Double atr = lValue(datr);
                    Double low = sRef.getLowPrice();
                    Double high = sRef.getHighPrice();
                    Double close = sRef.getLastPrice();
                    Double prob = Utilities.getDouble(signalValues.get(id).get("probability"), 0.5);
                    Interpreter interpreter = new Interpreter();
                    try {
                        interpreter.set("atr", atr);
                        interpreter.set("high", high);
                        interpreter.set("low", low);
                        interpreter.set("close", close);
                        interpreter.set("prob", prob);
                        interpreter.set("barslpoints", high - close);
                        interpreter.set("bartppoints", close - low);
                        interpreter.eval("profit=" + takeProfitExpression);
                        interpreter.eval("stop=" + stopLossExpression);
                        sl.stopValue = Utilities.getDouble(interpreter.get("stop"), 0);
                        tp.stopValue = Utilities.getDouble(interpreter.get("profit"), 0);
                        sl.stopValue = Utilities.round(sl.stopValue, getTickSize(), 2);
                        tp.stopValue = Utilities.round(tp.stopValue, getTickSize(), 2);
                    } catch (Exception e) {
                        logger.log(Level.SEVERE, null, e);
                    }
                    tp.stopType = EnumStopType.TAKEPROFIT;
                    tp.stopMode = EnumStopMode.POINT;
                    tp.recalculate = true;
                    sl.stopType = EnumStopType.STOPLOSS;
                    sl.stopMode = EnumStopMode.POINT;
                    sl.underlyingEntry = sRef.getLastPrice();
                    sl.recalculate = true;
                    logger.log(Level.INFO, "501,StopParameters,{0}", new Object[]{sRef.getHighPrice() + delimiter + sRef.getLowPrice() + delimiter + sRef.getLastPrice()});
                    ArrayList<Stop> stops = new ArrayList<>();
                    stops.add(sl);
                    stops.add(tp);
                    Trade.setStop(db, this.getStrategy() + ":" + orderid + ":" + "Order", "opentrades", stops);
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, null, e);
            }
        }
        boolean rollover = rolloverDay();
        if (rollover) {
            for (int id : this.getPosition().keySet()) {
                if (this.getStrategySymbols().contains(id)) {
                    int nextid = Utilities.getNextExpiryID(Parameters.symbol, id, expiryFarMonth);
                    if (nextid >= 0 && nextid != id) {//we have a rollover
                        positionRollover(id, nextid);
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

    public void positionRollover(int initID, int targetID) {
        //get side, size of position
        EnumOrderSide origSide = this.getPosition().get(initID).getPosition() > 0 ? EnumOrderSide.BUY : this.getPosition().get(initID).getPosition() <0?EnumOrderSide.SHORT:EnumOrderSide.UNDEFINED;
        int size = Math.abs(this.getPosition().get(initID).getPosition());
        ArrayList<Stop> stops = null;
        //square off position        
        switch (origSide) {
            case BUY:
                logger.log(Level.INFO, "501,Strategy Rollover EXIT BUY,{0}", new Object[]{getStrategy() + delimiter + Parameters.symbol.get(initID).getExchangeSymbol()});
                stops = Trade.getStop(db, this.getStrategy() + ":" + this.getFirstInternalOpenOrder(initID, EnumOrderSide.SELL, "Order") + ":Order");
                HashMap<String, Object> order = new HashMap<>();
                order.put("id", initID);
                order.put("side", EnumOrderSide.SELL);
                order.put("size", size);
                order.put("type", EnumOrderType.LMT);
                order.put("limitprice", Parameters.symbol.get(initID).getLastPrice());
                order.put("reason", EnumOrderReason.REGULAREXIT);
                order.put("orderstage", EnumOrderStage.INIT);
                order.put("expiretime", this.getMaxOrderDuration());
                order.put("dynamicorderduration", getDynamicOrderDuration());
                order.put("maxslippage", this.getMaxSlippageExit());
                order.put("log", "ROLLOVERSQUAREOFF");
                this.exit(order);
                break;
            case SHORT:
                logger.log(Level.INFO, "501,Strategy Rollover EXIT SHORT,{0}", new Object[]{getStrategy() + delimiter + Parameters.symbol.get(initID).getExchangeSymbol()});
                stops = Trade.getStop(db, this.getStrategy() + ":" + this.getFirstInternalOpenOrder(initID, EnumOrderSide.COVER, "Order") + ":Order");
                order = new HashMap<>();
                order.put("id", initID);
                order.put("side", EnumOrderSide.COVER);
                order.put("size", size);
                order.put("type", EnumOrderType.LMT);
                order.put("limitprice", Parameters.symbol.get(initID).getLastPrice());
                order.put("reason", EnumOrderReason.REGULAREXIT);
                order.put("orderstage", EnumOrderStage.INIT);
                order.put("expiretime", this.getMaxOrderDuration());
                order.put("dynamicorderduration", getDynamicOrderDuration());
                order.put("maxslippage", this.getMaxSlippageExit());
                order.put("log", "ROLLOVERSQUAREOFF");
                this.exit(order);
                break;
            default:
                break;
        }

        //enter new position
        int orderid = -1;
        double targetContracts=size/Parameters.symbol.get(targetID).getMinsize();
        int newSize= Math.max((int)Math.round(targetContracts),1);//size/Parameters.symbol.get(targetID).getMinsize() + ((size % Parameters.symbol.get(targetID).getMinsize() == 0) ? 0 : 1); 
        newSize=newSize*Parameters.symbol.get(targetID).getMinsize();
        switch (origSide) {
            case BUY:
                if(this.getLongOnly()){
                logger.log(Level.INFO, "501,Strategy Rollover ENTER BUY,{0}", new Object[]{getStrategy() + delimiter + Parameters.symbol.get(targetID).getExchangeSymbol()+delimiter+newSize});
                HashMap<String, Object> order = new HashMap<>();
                order.put("id", targetID);
                order.put("side", EnumOrderSide.BUY);
                order.put("size", newSize);
                order.put("type", EnumOrderType.LMT);
                order.put("limitprice", Parameters.symbol.get(initID).getLastPrice());
                order.put("reason", EnumOrderReason.REGULARENTRY);
                order.put("orderstage", EnumOrderStage.INIT);
                order.put("expiretime", this.getMaxOrderDuration());
                order.put("dynamicorderduration", getDynamicOrderDuration());
                order.put("maxslippage", this.getMaxSlippageExit());
                order.put("log", "ROLLOVERENTRY");
                orderid=this.entry(order);
                //orderid = this.getFirstInternalOpenOrder(initID, EnumOrderSide.SELL, "Order");
                }
                break;
            case SHORT:
                if(this.getShortOnly()){
                logger.log(Level.INFO, "501,Strategy Rollover ENTER SHORT,{0}", new Object[]{getStrategy() + delimiter + Parameters.symbol.get(targetID).getExchangeSymbol()+delimiter+newSize});
                HashMap<String, Object> order = new HashMap<>();
                order = new HashMap<>();
                order.put("id", targetID);
                order.put("side", EnumOrderSide.SHORT);
                order.put("size", newSize);
                order.put("type", EnumOrderType.LMT);
                order.put("limitprice", Parameters.symbol.get(initID).getLastPrice());
                order.put("reason", EnumOrderReason.REGULARENTRY);
                order.put("orderstage", EnumOrderStage.INIT);
                order.put("expiretime", this.getMaxOrderDuration());
                order.put("dynamicorderduration", getDynamicOrderDuration());
                order.put("maxslippage", this.getMaxSlippageExit());
                order.put("log", "ROLLOVERENTRY");
                orderid=this.entry(order);
                //orderid = this.getFirstInternalOpenOrder(initID, EnumOrderSide.COVER, "Order");
                }
                break;
            default:
                break;
        }
        //update stop information
        logger.log(Level.INFO, "501,Strategy Rollover Stop Update,{0}", new Object[]{getStrategy() + delimiter + Parameters.symbol.get(targetID).getExchangeSymbol()+delimiter+newSize+delimiter+orderid+delimiter+stops});
        if (orderid >= 0) {
            Trade.setStop(db, this.getStrategy() + ":" + orderid + ":" + "Order", "opentrades", stops);
        }
    }

    public void displayStrategyValues() {
        JFrame f = new SwingValues(this);
        f.setVisible(true);
    }
}