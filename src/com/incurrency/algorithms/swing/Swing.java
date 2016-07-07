/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.algorithms.swing;

import bsh.Interpreter;
import com.incurrency.RatesClient.Subscribe;
import com.incurrency.framework.Algorithm;
import com.incurrency.framework.BeanConnection;
import com.incurrency.framework.BeanPosition;
import com.incurrency.framework.BeanSymbol;
import com.incurrency.framework.DateUtil;
import com.incurrency.framework.EnumBarSize;
import com.incurrency.framework.EnumOrderReason;
import com.incurrency.framework.EnumOrderSide;
import com.incurrency.framework.EnumOrderStage;
import com.incurrency.framework.EnumOrderType;
import com.incurrency.framework.EnumStopMode;
import com.incurrency.framework.EnumStopType;
import com.incurrency.framework.Mail;
import com.incurrency.framework.MainAlgorithm;
import com.incurrency.framework.Parameters;
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
import java.util.Map;
import java.util.TreeMap;

/**
 *
 * @author pankaj
 */
public class Swing extends Strategy implements TradeListener {

    private static final Logger logger = Logger.getLogger(Swing.class.getName());
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
    private String RStrategyFile;
    SimpleDateFormat sdf_default = new SimpleDateFormat("yyyy-MM-dd");
    private boolean rollover;
    private String expiry;

    public Swing(MainAlgorithm m, Properties p, String parameterFile, ArrayList<String> accounts, Integer stratCount) {
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

        if (testingTimer > 0) {
            Calendar tmpCalendar = Calendar.getInstance(TimeZone.getTimeZone(Algorithm.timeZone));
            tmpCalendar.add(Calendar.MINUTE, testingTimer);
            entryScanDate = tmpCalendar.getTime();
        }
        rollover = rolloverDay();
        if (rollover) {
            expiry = this.expiryFarMonth;
        } else {
            expiry = this.expiryNearMonth;
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
        Timer bodProcessing = new Timer("Timer: " + this.getStrategy() + " BODProcessing");
        bodProcessing.schedule(runBOD, 1 * 1000);
        Timer signals = new Timer("Timer: " + this.getStrategy() + " signalmonitor");
        signals.schedule(readTrades, 1 * 1000);
        //signals.schedule(readTrades, getStartDate(), tradeReadingFrequency * 1000);
        if(rollover){
            Timer rollProcessing = new Timer("Timer: Roll Positions");
            rollProcessing.schedule(processRolls, DateUtil.addSeconds(entryScanDate, 60));            
        }
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
        RStrategyFile=p.getProperty("RStrategyFile", "");
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
                        order.put("log", "SLTPExit" + delimiter + slTrigger + delimiter + tpTrigger + delimiter + Parameters.symbol.get(id).getLastPrice() + delimiter + slDistance + delimiter + sl + delimiter + tpDistance + delimiter + tp);
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
                        order.put("log", "SLTPExit" + delimiter + slTrigger + delimiter + tpTrigger + delimiter + Parameters.symbol.get(id).getLastPrice() + delimiter + slDistance + delimiter + sl + delimiter + tpDistance + delimiter + tp);
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
            for (BeanSymbol s : Parameters.symbol) {
                if (s.getType().equals(referenceCashType) && s.getStrategy().toLowerCase().contains("swing")) {
                    int symbolid = s.getSerialno() - 1;
                    scan(symbolid, false);
                    bodtasks();
                }
            }
        }
    };

    private void bodtasks() {
        logger.log(Level.INFO, "501,BODProcess,{0}", new Object[]{this.getStrategy()});
        List<String> tradetuple = db.brpop("recontrades:"+this.getStrategy(), "", 1); //pick trades for prior trading day
        List<String> expectedTrades = new ArrayList<>();
        while (tradetuple != null) {
            expectedTrades.add(tradetuple.get(1));
            tradetuple = db.brpop("recontrades:"+this.getStrategy(), "", 1);
        }
        for (String key : db.getKeys("opentrades")) {
            ArrayList<Stop> tradestops = Trade.getStop(db, key);
            String entrysymbol = db.getValue("opentrades", key, "entrysymbol");
            String entryside = db.getValue("opentrades", key, "entryside");
            String symbol = entrysymbol.split("_")[0];
            int stopindex = -1;
            if (tradestops.size() == 1) {
                Stop stop = tradestops.get(0);
                if (stop.recalculate == Boolean.TRUE) {
                    for (int i = 0; i < expectedTrades.size(); i++) {
                        if (expectedTrades.get(i).contains(symbol)) {
                            stopindex = i;
                            break;
                        }
                    }
                    if (stopindex >= 0) {
                        String side = expectedTrades.get(stopindex).split(":")[1];
                        if (side.equals("0")) {
                            side = "SHORT";
                        } else if (side.equals("1")) {
                            side = "BUY";
                        } else if (side.equals("2")) {
                            side = "AVOID";
                        }
                        if (side.equals(entryside)) {
                            //update stop
                            stop.stopValue = Double.valueOf(expectedTrades.get(stopindex).split(":")[1]);
                            stop.recalculate = false;
                            Trade.setStop(db, key, "opentrades", tradestops);
                        } else {
                            //alert we have an incorrect side
                            Thread t = new Thread(new Mail("Symbol has incorrect side: " + entrysymbol + " for strategy: " + this.getStrategy() + ".Expected trade direction: " + side + " Actual side in trade:" + entryside, "Algorithm ALERT"));
                            t.start();
                        }
                    } else {
                        //alert dont have trades in strategy
                        Thread t = new Thread(new Mail("Opening position where none expected for: " + entrysymbol + " for strategy: " + this.getStrategy() + ".Please review strategy results", "Algorithm ALERT"));
                        t.start();
                    }
                }
            }
        }
    }
    TimerTask eodProcessingTask = new TimerTask() {
        @Override
        public void run() {
            for (BeanSymbol s : Parameters.symbol) {
                if (s.getType().equals(referenceCashType) && s.getStrategy().toLowerCase().contains("swing")) {
                    int symbolid = s.getSerialno() - 1;
                    scan(symbolid, true);
                }
            }
        }
    };
    
    private void scan(int symbolid, boolean today) {
        logger.log(Level.INFO, "501,Scan,{0}", new Object[]{this.getStrategy()});
        RConnection c = null;
        try {
            c = new RConnection(rServerIP);
            REXP x = c.eval("R.version.string");
            c.eval("setwd("+"\"/home/psharma/Seafile/ML-Coursera/R/"+"\")");
            REXP wd = c.eval("getwd()");
            System.out.println(wd.asString());
            c.eval("options(encoding = \"UTF-8\")");
            String[] args = new String[1];
            if (today) {
                String open = String.valueOf(Parameters.symbol.get(symbolid).getOpenPrice());
                String high = String.valueOf(Parameters.symbol.get(symbolid).getHighPrice());
                String low = String.valueOf(Parameters.symbol.get(symbolid).getLowPrice());
                String close = String.valueOf(Parameters.symbol.get(symbolid).getClosePrice());
                String volume = String.valueOf(Parameters.symbol.get(symbolid).getVolume());
                String date = sdf_default.format(new Date());
                args = new String[]{"1", this.getStrategy(), this.getRedisDatabaseID(),
                    Parameters.symbol.get(symbolid).getDisplayname(), date, open, high, low, close, volume};
            } else {
                args = new String[]{"1", this.getStrategy(), this.getRedisDatabaseID(),Parameters.symbol.get(symbolid).getDisplayname()};
            }
            c.assign("args", args);
//            c.assign("commandArgs", "function() {"+args+"}");
//           c.eval("commandArgs<-function(){"+args+"}");
            c.eval("source(\"" + this.getRStrategyFile() + "\")");
        } catch (Exception e) {
            logger.log(Level.SEVERE, null, e);
        }

    }

    TimerTask readTrades = new TimerTask() {
        @Override
        public void run() {
            while (true) {
                waitForTrades();
            }
        }
    };

    /**
     * Blocks on Redis and waits for trades to be reported.
     */
    void waitForTrades() {
        List<String> tradetuple = db.blpop("trades:"+this.getStrategy(), "", 60);
        if (tradetuple != null) {
            //tradetuple as symbol:size:side:sl
            String symbol = tradetuple.get(1).split(":")[0];
            int symbolid = Utilities.getIDFromDisplayName(Parameters.symbol, symbol);
            int id = -1, nearid = -1;
            if (rollover) {
                id = Utilities.getFutureIDFromSymbol(Parameters.symbol, symbolid, expiry);
                nearid = Utilities.getFutureIDFromSymbol(Parameters.symbol, symbolid, this.expiryNearMonth);
            }else{
                id= Utilities.getFutureIDFromSymbol(Parameters.symbol, symbolid, this.expiryNearMonth);
                nearid=id;
            }
            //TODO: add logic to pick either expiryNearMonth or expiryFarMonth, based on expiration day
            int size = Integer.valueOf(tradetuple.get(1).split(":")[1]);
            EnumOrderSide side = EnumOrderSide.valueOf(tradetuple.get(1).split(":")[2]);
            double sl = Double.valueOf(tradetuple.get(1).split(":")[3]);
            sl = Utilities.round(sl, getTickSize(), 2);
            HashMap<String, Object> order = new HashMap<>();
            order.put("type", EnumOrderType.LMT);
            order.put("limitprice", Parameters.symbol.get(id).getLastPrice());
            order.put("expiretime", getMaxOrderDuration());
            order.put("dynamicorderduration", getDynamicOrderDuration());
            order.put("maxslippage", this.getMaxSlippageEntry());
            int orderid;
            ArrayList<Stop> stops = new ArrayList<>();
            Stop stp = new Stop();
            switch (side) {
                case BUY:
                    order.put("id", id);
                    order.put("side", EnumOrderSide.BUY);
                    order.put("size", size);
                    order.put("reason", EnumOrderReason.REGULARENTRY);
                    order.put("orderstage", EnumOrderStage.INIT);
                    order.put("log", "BUY" + delimiter + tradetuple.get(1));
                    logger.log(Level.INFO, "501,Strategy BUY,{0}", new Object[]{getStrategy() + delimiter + "BUY" + delimiter + Parameters.symbol.get(id).getDisplayname()});
                    orderid = entry(order);
                    stp.stopValue = sl;
                    stp.stopType = EnumStopType.STOPLOSS;
                    stp.stopMode = EnumStopMode.POINT;
                    stp.recalculate = true;
                    stops.add(stp);
                    Trade.setStop(db, this.getStrategy() + ":" + orderid + ":" + "Order", "opentrades", stops);
                    break;
                case SELL:
                    order.put("id", nearid);
                    order.put("side", EnumOrderSide.SELL);
                    order.put("size", size);
                    order.put("reason", EnumOrderReason.REGULAREXIT);
                    order.put("orderstage", EnumOrderStage.INIT);
                    order.put("log", "SELL" + delimiter + tradetuple.get(1));
                    logger.log(Level.INFO, "501,Strategy SELL,{0}", new Object[]{getStrategy() + delimiter + "SELL" + delimiter + Parameters.symbol.get(id).getDisplayname()});
                    orderid = exit(order);
                    break;
                case SHORT:
                    order.put("id", id);
                    order.put("side", EnumOrderSide.SHORT);
                    order.put("size", size);
                    order.put("reason", EnumOrderReason.REGULARENTRY);
                    order.put("orderstage", EnumOrderStage.INIT);
                    order.put("log", "SHORT" + delimiter + tradetuple.get(1));
                    logger.log(Level.INFO, "501,Strategy SHORT,{0}", new Object[]{getStrategy() + delimiter + "SHORT" + delimiter + Parameters.symbol.get(id).getDisplayname()});
                    orderid = entry(order);
                    stp = new Stop();
                    stp.stopValue = sl;
                    stp.stopType = EnumStopType.STOPLOSS;
                    stp.stopMode = EnumStopMode.POINT;
                    stp.recalculate = true;
                    stops.add(stp);
                    Trade.setStop(db, this.getStrategy() + ":" + orderid + ":" + "Order", "opentrades", stops);
                    break;
                case COVER:
                    order.put("id", nearid);
                    order.put("side", EnumOrderSide.COVER);
                    order.put("size", size);
                    order.put("reason", EnumOrderReason.REGULAREXIT);
                    order.put("orderstage", EnumOrderStage.INIT);
                    order.put("log", "COVER" + delimiter + tradetuple.get(1));
                    logger.log(Level.INFO, "501,Strategy COVER,{0}", new Object[]{getStrategy() + delimiter + "COVER" + delimiter + Parameters.symbol.get(id).getDisplayname()});
                    orderid = exit(order);
                    break;
                default:
                    break;
            }
        }
    }

    TimerTask processRolls=new TimerTask(){

        @Override
        public void run() {
            for(Map.Entry<Integer, BeanPosition> entry : getPosition().entrySet()){
                if(entry.getValue().getPosition()!=0){
                    String expiry=Parameters.symbol.get(entry.getKey()).getExpiry();
                    if(expiry.equals(expiryNearMonth)){
                        int initID=entry.getKey();
                        int targetID=Utilities.getFutureIDFromSymbol(Parameters.symbol, initID, expiryFarMonth);
                        positionRollover(initID,targetID);
                    }
                }
            }
        }
        
    };  
     
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
        EnumOrderSide origSide = this.getPosition().get(initID).getPosition() > 0 ? EnumOrderSide.BUY : this.getPosition().get(initID).getPosition() < 0 ? EnumOrderSide.SHORT : EnumOrderSide.UNDEFINED;
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
        double targetContracts = size / Parameters.symbol.get(targetID).getMinsize();
        int newSize = Math.max((int) Math.round(targetContracts), 1);//size/Parameters.symbol.get(targetID).getMinsize() + ((size % Parameters.symbol.get(targetID).getMinsize() == 0) ? 0 : 1); 
        newSize = newSize * Parameters.symbol.get(targetID).getMinsize();
        switch (origSide) {
            case BUY:
                if (this.getLongOnly()) {
                    logger.log(Level.INFO, "501,Strategy Rollover ENTER BUY,{0}", new Object[]{getStrategy() + delimiter + Parameters.symbol.get(targetID).getExchangeSymbol() + delimiter + newSize});
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
                    orderid = this.entry(order);
                    //orderid = this.getFirstInternalOpenOrder(initID, EnumOrderSide.SELL, "Order");
                }
                break;
            case SHORT:
                if (this.getShortOnly()) {
                    logger.log(Level.INFO, "501,Strategy Rollover ENTER SHORT,{0}", new Object[]{getStrategy() + delimiter + Parameters.symbol.get(targetID).getExchangeSymbol() + delimiter + newSize});
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
                    orderid = this.entry(order);
                    //orderid = this.getFirstInternalOpenOrder(initID, EnumOrderSide.COVER, "Order");
                }
                break;
            default:
                break;
        }
        //update stop information
        logger.log(Level.INFO, "501,Strategy Rollover Stop Update,{0}", new Object[]{getStrategy() + delimiter + Parameters.symbol.get(targetID).getExchangeSymbol() + delimiter + newSize + delimiter + orderid + delimiter + stops});
        if (orderid >= 0) {
            Trade.setStop(db, this.getStrategy() + ":" + orderid + ":" + "Order", "opentrades", stops);
        }
    }

    public void displayStrategyValues() {
    }


    /**
     * @return the RStrategyFile
     */
    public String getRStrategyFile() {
        return RStrategyFile;
    }

    /**
     * @param RStrategyFile the RStrategyFile to set
     */
    public void setRStrategyFile(String RStrategyFile) {
        this.RStrategyFile = RStrategyFile;
    }
}
