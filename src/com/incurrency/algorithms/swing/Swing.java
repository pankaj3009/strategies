/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.algorithms.swing;

import com.incurrency.RatesClient.Subscribe;
import com.incurrency.framework.Algorithm;
import com.incurrency.framework.BeanConnection;
import com.incurrency.framework.BeanPosition;
import com.incurrency.framework.BeanSymbol;
import com.incurrency.framework.DateUtil;
import com.incurrency.framework.EnumOrderReason;
import com.incurrency.framework.EnumOrderSide;
import com.incurrency.framework.EnumOrderStage;
import com.incurrency.framework.EnumOrderType;
import com.incurrency.framework.EnumStopMode;
import com.incurrency.framework.EnumStopType;
import com.incurrency.framework.Index;
import com.incurrency.framework.Mail;
import com.incurrency.framework.MainAlgorithm;
import com.incurrency.framework.Parameters;
import com.incurrency.framework.SimulationTimer;
import com.incurrency.framework.Stop;
import com.incurrency.framework.Strategy;
import com.incurrency.framework.Trade;
import com.incurrency.framework.TradeEvent;
import com.incurrency.framework.TradeListener;
import com.incurrency.framework.Utilities;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.rosuda.REngine.REXP;
import org.rosuda.REngine.Rserve.RConnection;

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
    Date entryScanDate;
    Timer eodProcessing;
    int testingTimer = 0;
    private final Object lockTradeReceived_1 = new Object();
    private String RStrategyFile;
    SimpleDateFormat sdf_default = new SimpleDateFormat("yyyy-MM-dd");
    SimpleDateFormat sdtf_default = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private boolean rollover;
    int rolloverDays;
    private String expiry;
    private String wd;
    private Boolean optionTrades = Boolean.FALSE;
    private EnumOrderType ordType = EnumOrderType.LMT;

    public Swing(MainAlgorithm m, Properties p, String parameterFile, ArrayList<String> accounts, Integer stratCount) {
        super(m, "swing", "FUT", p, parameterFile, accounts, stratCount);
        loadParameters(p);
        String[] tempStrategyArray = parameterFile.split("\\.")[0].split("-|_");
        for (BeanConnection c : Parameters.connection) {
            c.getWrapper().addTradeListener(this);
            c.initializeConnection(tempStrategyArray[tempStrategyArray.length - 1], -1);
        }
        if (Subscribe.tes != null) {
            Subscribe.tes.addTradeListener(this);
        }

        if (testingTimer > 0) {
            Calendar tmpCalendar = Calendar.getInstance(TimeZone.getTimeZone(Algorithm.timeZone));
            tmpCalendar.add(Calendar.MINUTE, testingTimer);
            entryScanDate = tmpCalendar.getTime();
        }
        rollover = rolloverDay(rolloverDays);
        if (rollover) {
            expiry = this.expiryFarMonth;
        } else {
            expiry = this.expiryNearMonth;
        }

        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "Entry Scan Time" + delimiter + entryScanDate});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "Testing Timer Duration" + delimiter + testingTimer});

        /*
         * Strategy has 4 timer tasks
         * Timertask 1: eodProcessingTask - scans at the specified time for entry signals
         * Timertask 2: bodProcessingTask - scans at the start of program and updates stop levels
         * Timertask 3: tradeProcessingTask - scans for trades every 60 seconds.
         * Timertask 4: rollProcessingTask - if trading day is also a rollover day, rolls over any open positions.
         */
        if (MainAlgorithm.isUseForSimulation()) {
            String entryScanTime = p.getProperty("EntryScanTime");
            // *********** SECTION USED FOR SIMULATING TRADES *************
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
            //*************************************************************
        } else {
            Calendar cal = Calendar.getInstance();
            cal.setTime(new Date());
            cal.setTimeZone(TimeZone.getTimeZone(Algorithm.timeZone));
            cal.add(Calendar.DATE, -1);
            Date priorEndDate = cal.getTime();
            if (new Date().before(this.getEndDate()) && new Date().after(priorEndDate)) {
                logger.log(Level.INFO, "Set EODProcessing Task at {0}", new Object[]{sdtf_default.format(entryScanDate)});
                eodProcessing = new Timer("Timer: " + this.getStrategy() + " EODProcessing");
                eodProcessing.schedule(eodProcessingTask, entryScanDate);
            }
        }
        Timer bodProcessing = new Timer("Timer: " + this.getStrategy() + " BODProcessing");
        bodProcessing.schedule(bodProcessingTask, 10 * 1000);

        Timer signals = new Timer("Timer: " + this.getStrategy() + " TradeProcessing");
        signals.schedule(tradeProcessingTask, 1 * 1000);
        if (rollover) {
            Timer rollProcessing = new Timer("Timer: " + this.getStrategy() + " RollProcessing");
            rollProcessing.schedule(rollProcessingTask, DateUtil.addSeconds(entryScanDate, 60));
        }
    }

    private void loadParameters(Properties p) {
        expiryNearMonth = p.getProperty("NearMonthExpiry").toString().trim();
        expiryFarMonth = p.getProperty("FarMonthExpiry").toString().trim();
        referenceCashType = p.getProperty("ReferenceCashType", "STK").toString().trim();
        rServerIP = p.getProperty("RServerIP").toString().trim();
        String entryScanTime = p.getProperty("EntryScanTime");
        Calendar calToday = Calendar.getInstance(TimeZone.getTimeZone(Algorithm.timeZone));
        calToday.setTime(this.getEndDate());
        String[] entryTimeComponents = entryScanTime.split(":");
        calToday.set(Calendar.HOUR_OF_DAY, Utilities.getInt(entryTimeComponents[0], 15));
        calToday.set(Calendar.MINUTE, Utilities.getInt(entryTimeComponents[1], 20));
        calToday.set(Calendar.SECOND, Utilities.getInt(entryTimeComponents[2], 0));
        entryScanDate = calToday.getTime();
        testingTimer = Utilities.getInt(p.getProperty("TestingTimer"), 0);
        RStrategyFile = p.getProperty("RStrategyFile", "");
        wd = p.getProperty("wd", "/home/psharma/Seafile/R");
        optionTrades = Boolean.parseBoolean(p.getProperty("UseOptions", "FALSE"));
        ordType = EnumOrderType.valueOf(p.getProperty("OrderType", "LMT"));
        rolloverDays = Integer.valueOf(p.getProperty("RolloverDays", "0"));
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
                    double sl = Double.MIN_VALUE;
                    double tp = Double.MAX_VALUE;
                    if (!optionTrades && stops == null && Parameters.symbol.get(id).getLastPrice() != 0) { //Set SL and TP at 5%
                        slTrigger = tradePrice > 0 && Parameters.symbol.get(id).getLastPrice() != 0 && (Parameters.symbol.get(id).getLastPrice() <= tradePrice * (1 - 5 / 100));
                        tpTrigger = tradePrice > 0 && Parameters.symbol.get(id).getLastPrice() != 0 && (Parameters.symbol.get(id).getLastPrice() >= tradePrice * (1 + 5 / 100));
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
                                        if (Parameters.symbol.get(id).getDisplayname().contains("PUT")) {
                                            slDistance = Parameters.symbol.get(referenceid).getLastPrice() - stop.underlyingEntry;

                                        } else {
                                            slDistance = stop.underlyingEntry - Parameters.symbol.get(referenceid).getLastPrice();

                                        }
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
                        order.put("type", EnumOrderType.CUSTOMREL);
                        String right = Parameters.symbol.get(id).getDisplayname().contains("CALL") ? "CALL" : "PUT";
                        double limitprice = this.getOptionLimitPriceForRel(id, referenceid, EnumOrderSide.SELL, right);
                        order.put("limitprice", limitprice);
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
                    }
                } else if (this.getPosition().get(id).getPosition() < 0 && this.getPosition().get(id).getStrategy().equalsIgnoreCase(this.getStrategy())) {
                    //We do not expect a short position. This is an error. Email
                    Thread t = new Thread(new Mail("psharma@incurrency.com", "Unexpected Short Position encountered: " + Parameters.symbol.get(id).getDisplayname() + " for strategy: " + this.getStrategy() + ".Please review strategy results", "Algorithm ALERT"));
                    t.start();
                }
            }
        }
    }
    TimerTask bodProcessingTask = new TimerTask() {
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
        List<String> tradetuple = db.brpop("recontrades:" + this.getStrategy(), "", 1); //pick trades for prior trading day
        List<String> expectedTrades = new ArrayList<>();
        while (tradetuple != null) {
            logger.log(Level.INFO, "Received BOD Position: {0} for strategy: {1}", new Object[]{tradetuple.get(1), tradetuple.get(0)});
            expectedTrades.add(tradetuple.get(1));
            tradetuple = db.brpop("recontrades:" + this.getStrategy(), "", 1);
        }
        for (String key : db.getKeys("opentrades_" + this.getStrategy())) {
            ArrayList<Stop> tradestops = Trade.getStop(db, key);
            String entrysymbol = db.getValue("opentrades", key, "entrysymbol");
            String entryside = db.getValue("opentrades", key, "entryside");
            String symbol = entrysymbol.split("_")[0];
            int stopindex = -1;
            if (tradestops != null && tradestops.size() == 1) {
                Stop stop = tradestops.get(0);
                if (stop.recalculate == Boolean.TRUE) {
                    for (int i = 0; i < expectedTrades.size(); i++) {
                        if (expectedTrades.get(i).contains(symbol)) {
                            stopindex = i;
                            break;
                        }
                    }
                    if (stopindex >= 0) {
                        String side = expectedTrades.get(stopindex).split(":")[2];
                        if ((side.equals("BUY") && entryside.equals("BUY") && entrysymbol.contains("CALL"))
                                || (side.equals("SHORT") && entryside.equals("BUY") && entrysymbol.contains("PUT"))) {
                            //update stop
                            stop.stopValue = Double.valueOf(expectedTrades.get(stopindex).split(":")[3]);
                            stop.stopValue = Utilities.roundTo(stop.stopValue, getTickSize());
                            stop.recalculate = false;
                            stop.underlyingEntry = Double.valueOf(expectedTrades.get(stopindex).split(":")[4]);
                            Trade.setStop(db, key, "opentrades", tradestops);
                            logger.log(Level.INFO, "Updated Trade Stop. Symbol:{0}, Underlying value:{1},StopPoints:{2}", new Object[]{symbol, stop.underlyingEntry, stop.stopValue});
                        } else {
                            //alert we have an incorrect side
                            Thread t = new Thread(new Mail("psharma@incurrency.com", "Symbol has incorrect side: " + entrysymbol + " for strategy: " + this.getStrategy() + ".Expected trade direction: " + side + " Actual side in trade:" + entryside, "Algorithm ALERT"));
                            t.start();
                        }
                    } else {
                        //alert dont have trades in strategy
                        Thread t = new Thread(new Mail("psharma@incurrency.com", "Opening position where none expected for: " + entrysymbol + " for strategy: " + this.getStrategy() + ".Please review strategy results", "Algorithm ALERT"));
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
            c.eval("setwd(\"" + wd + "\")");
            REXP wd = c.eval("getwd()");
            System.out.println(wd.asString());
            c.eval("options(encoding = \"UTF-8\")");
            String[] args = new String[1];
            if (today) {
                String open = String.valueOf(Parameters.symbol.get(symbolid).getOpenPrice());
                String high = String.valueOf(Parameters.symbol.get(symbolid).getHighPrice());
                String low = String.valueOf(Parameters.symbol.get(symbolid).getLowPrice());
                String close = String.valueOf(Parameters.symbol.get(symbolid).getLastPrice());
                String volume = String.valueOf(Parameters.symbol.get(symbolid).getVolume());
                String date = sdf_default.format(new Date());
                args = new String[]{"1", this.getStrategy(), this.getRedisDatabaseID(),
                    Parameters.symbol.get(symbolid).getDisplayname(), date, open, high, low, close, volume};
            } else {
                args = new String[]{"1", this.getStrategy(), this.getRedisDatabaseID(), Parameters.symbol.get(symbolid).getDisplayname()};
            }
            logger.log(Level.INFO, "Invoking R. args: {0}", new Object[]{Arrays.toString(args)});
            c.assign("args", args);
            c.eval("source(\"" + this.getRStrategyFile() + "\")");
        } catch (Exception e) {
            logger.log(Level.SEVERE, null, e);
        }

    }
    TimerTask tradeProcessingTask = new TimerTask() {
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
        try {
            List<String> tradetuple = db.blpop("trades:" + this.getStrategy(), "", 60);
            if (tradetuple != null) {
                logger.log(Level.INFO, "Received Trade:{0} for strategy {1}", new Object[]{tradetuple.get(1), tradetuple.get(0)});
                //tradetuple as symbol:size:side:sl
                String symbol = tradetuple.get(1).split(":")[0];
                int symbolid = Utilities.getIDFromDisplayName(Parameters.symbol, symbol);
                int futureid = Utilities.getFutureIDFromBrokerSymbol(Parameters.symbol, symbolid, expiry);
                int nearfutureid = futureid;
                int size = Integer.valueOf(tradetuple.get(1).split(":")[1]);
                EnumOrderSide side = EnumOrderSide.valueOf(tradetuple.get(1).split(":")[2]);
                double sl = Double.valueOf(tradetuple.get(1).split(":")[3]);
                sl = Utilities.round(sl, getTickSize(), 2);
                HashMap<String, Object> order = new HashMap<>();
                ArrayList<Integer> orderidlist = new ArrayList<>();
                ArrayList<Integer> nearorderidlist = new ArrayList<>();
                orderidlist = Utilities.getOrInsertOptionIDForLongSystem(Parameters.symbol, this.getPosition(), futureid, side, expiry);
                nearorderidlist = orderidlist;

                if (this.optionTrades) {
                    if (rollover) {
                        nearorderidlist = Utilities.getOrInsertOptionIDForLongSystem(Parameters.symbol, this.getPosition(), symbolid, side, this.expiryNearMonth);
                    }
                }
                for (int i : orderidlist) {
                    this.initSymbol(i);
                }
                for (int i : nearorderidlist) {
                    this.initSymbol(i);
                }
                order.put("type", ordType);
                order.put("expiretime", getMaxOrderDuration());
                order.put("dynamicorderduration", getDynamicOrderDuration());
                order.put("maxslippage", this.getMaxSlippageEntry());
                int orderid;
                ArrayList<Stop> stops = new ArrayList<>();
                Stop stp = new Stop();
                switch (side) {
                    case BUY:
                        for (int id : orderidlist) {
                            if (id >= 0) {
                                order.put("id", id);
                                double limitprice = getOptionLimitPriceForRel(id, futureid, EnumOrderSide.BUY, "CALL");
                                order.put("limitprice", limitprice);
                                order.put("side", EnumOrderSide.BUY);
                                order.put("size", size);
                                order.put("reason", EnumOrderReason.REGULARENTRY);
                                order.put("orderstage", EnumOrderStage.INIT);
                                order.put("scale", "FALSE");
                                order.put("dynamicorderduration", this.getDynamicOrderDuration());
                                order.put("expiretime", 0);
                                order.put("log", "BUY" + delimiter + tradetuple.get(1));
                                logger.log(Level.INFO, "501,Strategy BUY,{0}", new Object[]{getStrategy() + delimiter + "BUY" + delimiter + Parameters.symbol.get(id).getDisplayname()});
                                orderid = entry(order);
                                stp.stopValue = sl;
                                stp.underlyingEntry = Parameters.symbol.get(symbolid).getLastPrice();
                                stp.stopType = EnumStopType.STOPLOSS;
                                stp.stopMode = EnumStopMode.POINT;
                                stp.recalculate = true;
                                stops.add(stp);
                                Trade.setStop(db, this.getStrategy() + ":" + orderid + ":" + "Order", "opentrades", stops);

                            }
                        }
                        break;
                    case SELL:
                        for (int nearid : nearorderidlist) {
                            if (nearid >= 0) {
                                order.put("id", nearid);
                                double limitprice = getOptionLimitPriceForRel(nearid, nearfutureid, EnumOrderSide.SELL, "CALL");
                                order.put("limitprice", limitprice);
                                order.put("side", EnumOrderSide.SELL);
                                order.put("size", size);
                                order.put("reason", EnumOrderReason.REGULAREXIT);
                                order.put("orderstage", EnumOrderStage.INIT);
                                order.put("scale", "FALSE");
                                order.put("dynamicorderduration", this.getDynamicOrderDuration());
                                order.put("expiretime", 0);
                                order.put("log", "SELL" + delimiter + tradetuple.get(1));
                                logger.log(Level.INFO, "501,Strategy SELL,{0}", new Object[]{getStrategy() + delimiter + "SELL" + delimiter + Parameters.symbol.get(nearid).getDisplayname()});
                                orderid = exit(order);
                            }
                        }
                        break;
                    case SHORT:
                        for (int id : orderidlist) {
                            if (id >= 0) {
                                order.put("id", id);
                                double limitprice = this.getOptionLimitPriceForRel(id, futureid, EnumOrderSide.BUY, "PUT");
                                order.put("limitprice", limitprice);
                                order.put("side", EnumOrderSide.BUY);
                                order.put("size", size);
                                order.put("reason", EnumOrderReason.REGULARENTRY);
                                order.put("scale", "FALSE");
                                order.put("orderstage", EnumOrderStage.INIT);
                                order.put("dynamicorderduration", this.getDynamicOrderDuration());
                                order.put("expiretime", 0);
                                order.put("log", "SHORT" + delimiter + tradetuple.get(1));
                                logger.log(Level.INFO, "501,Strategy SHORT,{0}", new Object[]{getStrategy() + delimiter + "SHORT" + delimiter + Parameters.symbol.get(id).getDisplayname()});
                                orderid = entry(order);
                                Trade.setStop(db, this.getStrategy() + ":" + orderid + ":" + "Order", "opentrades", stops);
                                stp.stopValue = sl;
                                stp.underlyingEntry = Parameters.symbol.get(symbolid).getLastPrice();
                                stp.stopType = EnumStopType.STOPLOSS;
                                stp.stopMode = EnumStopMode.POINT;
                                stp.recalculate = true;
                                stops.add(stp);
                                Trade.setStop(db, this.getStrategy() + ":" + orderid + ":" + "Order", "opentrades", stops);
                            }
                        }
                        break;
                    case COVER:
                        for (int nearid : nearorderidlist) {
                            if (nearid >= 0) {
                                order.put("id", nearid);
                                double limitprice = this.getOptionLimitPriceForRel(nearid, nearfutureid, EnumOrderSide.SELL, "PUT");
                                order.put("limitprice", limitprice);
                                order.put("side", EnumOrderSide.SELL);
                                order.put("size", size);
                                order.put("reason", EnumOrderReason.REGULAREXIT);
                                order.put("scale", "FALSE");
                                order.put("orderstage", EnumOrderStage.INIT);
                                order.put("dynamicorderduration", this.getDynamicOrderDuration());
                                order.put("expiretime", 0);
                                order.put("log", "COVER" + delimiter + tradetuple.get(1));
                                logger.log(Level.INFO, "501,Strategy COVER,{0}", new Object[]{getStrategy() + delimiter + "COVER" + delimiter + Parameters.symbol.get(nearid).getDisplayname()});
                                orderid = exit(order);
                            }
                        }
                        break;
                    default:
                        break;
                }
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, null, e);
        }
    }

    double getOptionLimitPriceForRel(int id, int underlyingid, EnumOrderSide side, String right) {
        double price = Parameters.symbol.get(id).getLastPrice();
        try {
            double optionlastprice = 0;
//        Object[] optionlastpriceset = Utilities.getLastSettlePriceOption(Parameters.symbol, id, new Date().getTime() - 10 * 24 * 60 * 60 * 1000, new Date().getTime() - 1000000, "india.nse.option.s4.daily.settle");
            Object[] optionlastpriceset = Utilities.getSettlePrice(Parameters.symbol.get(id), new Date());
            Object[] underlyinglastpriceset = Utilities.getSettlePrice(Parameters.symbol.get(underlyingid), new Date());
            double underlyingpriorclose = Utilities.getDouble(underlyinglastpriceset[1], 0);

            if (optionlastpriceset != null && optionlastpriceset.length == 2) {
                long settletime = Utilities.getLong(optionlastpriceset[0], 0);
                optionlastprice = Utilities.getDouble(optionlastpriceset[1], 0);
                double vol = Utilities.getImpliedVol(Parameters.symbol.get(id), underlyingpriorclose, optionlastprice, new Date(settletime));
                Parameters.symbol.get(id).setCloseVol(vol);

            }

            if (price == 0 && optionlastprice > 0) {
                double underlyingprice = Parameters.symbol.get(underlyingid).getLastPrice();
                double underlyingchange = 0;
                if (underlyingprice != 0) {
                    underlyingchange = underlyingprice - underlyingpriorclose;//+ve if up
                }
                switch (right) {
                    case "CALL":
                        price = optionlastprice + 0.5 * underlyingchange;
                        break;
                    case "PUT":
                        price = optionlastprice - 0.5 * underlyingchange;
                        break;
                }
            }
            double bidprice = Parameters.symbol.get(id).getBidPrice();
            double askprice = Parameters.symbol.get(id).getAskPrice();
            logger.log(Level.INFO, "Symbol:{0},price:{1},BidPrice:{2},AskPrice:{3}", new Object[]{Parameters.symbol.get(id).getDisplayname(), price, bidprice, askprice});
            switch (side) {
                case BUY:
                case COVER:
                    if (bidprice > 0) {
                        price = Math.min(bidprice, price);

                    } else {
                        price = 0.80 * price;
                        logger.log(Level.INFO, "Calculated Price as bidprice is zero. Symbol {0}, BidPrice:{1}", new Object[]{Parameters.symbol.get(id).getDisplayname(), price});
                    }
                    break;
                case SHORT:
                case SELL:
                    if (askprice > 0) {
                        price = Math.max(askprice, price);

                    } else {
                        price = 1.2 * price;
                        logger.log(Level.INFO, "Calculated Price as askprice is zero. Symbol {0}, BidPrice:{1}", new Object[]{Parameters.symbol.get(id).getDisplayname(), price});
                    }
                    break;
                default:
                    break;

            }
            price = Utilities.roundTo(price, this.getTickSize());

        } catch (Exception e) {
            logger.log(Level.SEVERE, null, e);
        }
        return price;
    }
    TimerTask rollProcessingTask = new TimerTask() {
        @Override
        public void run() {
            for (Map.Entry<Integer, BeanPosition> entry : getPosition().entrySet()) {
                if (entry.getValue().getPosition() != 0) {
                    String expiry = Parameters.symbol.get(entry.getKey()).getExpiry();
                    if (expiry.equals(expiryNearMonth)) {
                        int initID = entry.getKey();
                        int targetID = Utilities.getFutureIDFromBrokerSymbol(Parameters.symbol, initID, expiryFarMonth);
                        positionRollover(initID, targetID);
                    }
                }
            }
        }
    };

    private boolean rolloverDay(int daysBeforeExpiry) {
        rollover = false;
        try {
            SimpleDateFormat sdf_yyyyMMdd = new SimpleDateFormat("yyyyMMdd");
            String currentDay = sdf_yyyyMMdd.format(getStartDate());
            Date today = sdf_yyyyMMdd.parse(currentDay);
            Calendar expiry = Calendar.getInstance();
            expiry.setTime(sdf_yyyyMMdd.parse(expiryNearMonth));
            expiry.set(Calendar.DATE, expiry.get(Calendar.DATE) - daysBeforeExpiry);
            if (today.compareTo(expiry.getTime()) >= 0) {
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
                order.put("type", ordType);
                order.put("side", EnumOrderSide.SELL);
                order.put("size", size);
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
                order.put("type", ordType);
                order.put("side", EnumOrderSide.COVER);
                order.put("size", size);
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
                    order.put("type", ordType);
                    order.put("limitprice", Parameters.symbol.get(targetID).getLastPrice());
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
                    order.put("type", ordType);
                    order.put("limitprice", Parameters.symbol.get(targetID).getLastPrice());
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
