/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.algorithms.swing;

import com.incurrency.RatesClient.RedisSubscribe;
import com.incurrency.algorithms.manager.Manager;
import com.incurrency.framework.Algorithm;
import com.incurrency.framework.BeanConnection;
import com.incurrency.framework.BeanPosition;
import com.incurrency.framework.BeanSymbol;
import com.incurrency.framework.DateUtil;
import com.incurrency.framework.EnumOrderReason;
import com.incurrency.framework.EnumOrderSide;
import com.incurrency.framework.EnumOrderStage;
import com.incurrency.framework.EnumOrderType;
import com.incurrency.framework.Mail;
import com.incurrency.framework.MainAlgorithm;
import com.incurrency.framework.Parameters;
import com.incurrency.framework.Stop;
import com.incurrency.framework.Trade;
import com.incurrency.framework.TradeEvent;
import com.incurrency.framework.TradeListener;
import com.incurrency.framework.Utilities;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
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
public class Swing extends Manager implements TradeListener {

    private static final Logger logger = Logger.getLogger(Swing.class.getName());
    int testingTimer = 0;
    private final Object lockTradeReceived_1 = new Object();
    SimpleDateFormat sdf_default = new SimpleDateFormat("yyyy-MM-dd");
    SimpleDateFormat sdtf_default = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private final Object lockScan = new Object();

    public Swing(MainAlgorithm m, Properties p, String parameterFile, ArrayList<String> accounts, Integer stratCount) {
        super(m, p, parameterFile, accounts, stratCount, "swing");

        // Add Trade Listeners
        for (BeanConnection c : Parameters.connection) {
            c.getWrapper().addTradeListener(this);
        }
        if (RedisSubscribe.tes != null) {
            RedisSubscribe.tes.addTradeListener(this);
        }
        MainAlgorithm.tes.addTradeListener(this);

        /*
         * Swing has 4 timer tasks
         * Timertask 1: eodProcessingTask - scans at the specified time for entry signals
         * Timertask 2: bodProcessingTask - scans at the start of program and updates stop levels
         * Timertask 3: tradeProcessingTask - scans for trades every 60 seconds. - uses manager implementation
         * Timertask 4: rollProcessingTask - if trading day is also a rollover day, rolls over any open positions.
         */
        Timer trigger = new Timer("Timer: " + this.getStrategy() + " RScriptProcessor");
        trigger.schedule(RScriptRunTask, RScriptRunTime);

        Timer bodProcessing = new Timer("Timer: " + this.getStrategy() + " BODProcessing");
        bodProcessing.schedule(bodProcessingTask, 10 * 1000);

        if (rollover) {
            //Timer rollProcessing = new Timer("Timer: " + this.getStrategy() + " RollProcessing");
            //rollProcessing.schedule(rollProcessingTask, DateUtil.addSeconds(RScriptRunTime, 60));
        }
    }

    @Override
    public void tradeReceived(TradeEvent event) {
        synchronized (lockTradeReceived_1) {
            if (this.tradingWindow.get()) {
                Integer id = event.getSymbolID();
                if (getStrategySymbols().contains(id) && !Parameters.symbol.get(id).getType().equals(referenceCashType)) {
                    if (this.getPosition().get(id).getPosition() != 0 && this.getPosition().get(id).getStrategy().equalsIgnoreCase(this.getStrategy())) {
                        Double tradePrice = this.getPosition().get(id).getPrice();
                        int referenceid = Utilities.getCashReferenceID(Parameters.symbol, id, referenceCashType);
                        EnumOrderSide derivedSide = this.getPosition().get(id).getPosition() > 0 ? EnumOrderSide.SELL : EnumOrderSide.COVER;
                        ArrayList<Stop> stops = Trade.getStop(this.getDb(), this.getStrategy() + ":" + this.getFirstInternalOpenOrder(id, derivedSide, "Order").iterator().next() + ":Order");
                        boolean tpTrigger = false;
                        boolean slTrigger = false;
                        double tpDistance = 0D;
                        double slDistance = 0D;
                        double sl = Double.MIN_VALUE;
                        double tp = Double.MAX_VALUE;
                        if (stops != null && Parameters.symbol.get(id).getLastPrice() > 0) {
                            for (Stop stop : stops) {
                                switch (stop.stopType) {
                                    case TAKEPROFIT:
                                        tpDistance = Parameters.symbol.get(id).getLastPrice() - tradePrice;
                                        tp = stop.stopValue;
                                        tpTrigger = tp != 0 && Parameters.symbol.get(id).getLastPrice() != 0 && tpDistance >= tp;
                                        break;
                                    case STOPLOSS:
                                        if (stop.underlyingEntry != 0) {
                                            if (Parameters.symbol.get(id).getDisplayname().contains("PUT") || (Parameters.symbol.get(id).getDisplayname().contains("FUT") && this.getPosition().get(id).getPosition() < 0))   {
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
                            //int futureid = Utilities.getFutureIDFromExchangeSymbol(Parameters.symbol, referenceid, expiry);
                            String entryTime = Trade.getEntryTime(this.getDb(), this.getStrategy() + ":" + this.getFirstInternalOpenOrder(id, derivedSide, "Order").iterator().next() + ":Order");
                            String today = DateUtil.getFormatedDate("yyyy-MM-dd", new Date().getTime(), TimeZone.getTimeZone(Algorithm.timeZone));
                            if (!entryTime.contains(today)) {
                                logger.log(Level.INFO, "101, SLTP Exit,{0}:{1}:{2}:{3}:{4},sltrigger={5},tptrigger={6},lastprice={7},sl={8},distancefromsl={9},tp={10},distancefromtp={11}",
                                        new Object[]{this.getStrategy(), "Order", Parameters.symbol.get(id).getDisplayname(), -1, -1,
                                            slTrigger, tpTrigger, Parameters.symbol.get(id).getLastPrice(), sl, slDistance, tp, tpDistance});
                                int size = Math.abs(this.getPosition().get(id).getPosition());
                                HashMap<String, Object> order = new HashMap<>();
                                order.put("id", id);
                                order.put("side", derivedSide);
                                order.put("size", size);
                                order.put("type", EnumOrderType.CUSTOMREL);
                                double limitprice = Utilities.getLimitPriceForOrder(Parameters.symbol, id, referenceid, derivedSide, getTickSize(), this.getOrdType());
                                //double limitprice = Utilities.getOptionLimitPriceForRel(Parameters.symbol, id, futureid, EnumOrderSide.SELL, right, getTickSize());
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
                                order.put("orderattributes", this.getOrderAttributes());
                                order.put("log", "SLTPExit" + delimiter + slTrigger + delimiter + tpTrigger + delimiter + Parameters.symbol.get(id).getLastPrice() + delimiter + slDistance + delimiter + sl + delimiter + tpDistance + delimiter + tp);
                                this.exit(order);
                            }
                        }
                    }
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
        logger.log(Level.INFO, "102,BODProcess Initiated,{0}:{1};{2}:{3}:{4}",
                new Object[]{this.getStrategy(), "Order", "Unknown", -1, -1});
        List<String> tradetuple = this.getDb().brpop("recontrades:" + this.getStrategy(), "", 1); //pick trades for prior trading day
        List<String> expectedTrades = new ArrayList<>();
        while (tradetuple != null) {
            logger.log(Level.INFO, "102, Received BOD Position,{0}:{1};{2}:{3}:{4},RedisValue={5}",
                    new Object[]{this.getStrategy(), "Order", "Unknown", -1, -1, Arrays.toString(tradetuple.toArray())});
            if (tradetuple.get(1).contains("BUY") || tradetuple.get(1).contains("SHORT")) {
                expectedTrades.add(tradetuple.get(1));
            }
            tradetuple = this.getDb().brpop("recontrades:" + this.getStrategy(), "", 1);
        }
        for (String key : this.getDb().getKeys("opentrades_" + this.getStrategy())) {
            ArrayList<Stop> tradestops = Trade.getStop(this.getDb(), key);
            String entrysymbol = this.getDb().getValue("opentrades", key, "entrysymbol");
            String actualSide = this.getDb().getValue("opentrades", key, "entryside");
            String symbol = entrysymbol.split("_")[0];
            int stopindex = -1;
            if (tradestops != null && tradestops.size() == 1) {
                Stop stop = tradestops.get(0);
                // if (stop.recalculate == Boolean.TRUE) {
                for (int i = 0; i < expectedTrades.size(); i++) {
                    if (expectedTrades.get(i).contains(symbol)) {
                        stopindex = i;
                        break;
                    }
                }
                if (stopindex >= 0) {
                    String expectedSide = expectedTrades.get(stopindex).split(":")[2];
                    String expectedSymbol = expectedTrades.get(stopindex).split(":")[0].trim();
                    if ((expectedSide.equals("BUY") && actualSide.equals("BUY") && entrysymbol.equals(expectedSymbol))
                            || (expectedSide.equals("SHORT") && actualSide.equals("SHORT") && entrysymbol.equals(expectedSymbol))) {
                        //update stop
                        stop.stopValue = Double.valueOf(expectedTrades.get(stopindex).split(":")[3]);
                        stop.stopValue = Utilities.roundTo(stop.stopValue, getTickSize());
                        stop.recalculate = false;
                        stop.underlyingEntry = Double.valueOf(expectedTrades.get(stopindex).split(":")[4]);
                        Trade.setStop(this.getDb(), key, "opentrades", tradestops);
                        logger.log(Level.INFO, "102,Updated Trade Stop, {0}:{1};{2}:{3}:{4},UnderlyingValue={5}:StopPoints:{6}",
                                new Object[]{getStrategy(), "Order", symbol, -1, -1, stop.underlyingEntry, stop.stopValue});
                    } else {
                        //alert we have an incorrect side
                        Thread t = new Thread(new Mail("psharma@incurrency.com", "Symbol has incorrect side: " + entrysymbol + " for strategy: " + this.getStrategy() + ".Expected trade direction: " + expectedSide, "Algorithm ALERT"));
                        t.start();
                    }
                } else {
                    //alert dont have trades in strategy
                    Thread t = new Thread(new Mail("psharma@incurrency.com", "Opening position where none expected for: " + entrysymbol + " for strategy: " + this.getStrategy() + ".Please review strategy results", "Algorithm ALERT"));
                    t.start();
                }
                // }
            }
        }
    }
    TimerTask RScriptRunTask = new TimerTask() {
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
        synchronized (lockScan) {
            if (!getRStrategyFile().equals("")) {
                logger.log(Level.INFO, "102,Scan Initiated,{0}:{1};{2}:{3}:{4}",
                        new Object[]{this.getStrategy(), "Order", "Unknown", -1, -1});
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
                        args = new String[]{"4", this.getStrategy(), this.getRedisDatabaseID(), Parameters.symbol.get(symbolid).getDisplayname()};
                    }
                    logger.log(Level.INFO, "102,Invoking R Strategy,{0}:{1}:{2}:{3}:{4},args={5}",
                            new Object[]{getStrategy(), "Order", "Unknown", -1, -1, Arrays.toString(args)});
                    c.assign("args", args);
                    c.eval("source(\"" + this.getRStrategyFile() + "\")");

                } catch (Exception e) {
                    logger.log(Level.SEVERE, null, e);
                }
            }
        }
    }
    TimerTask rollProcessingTask = new TimerTask() {
        @Override
        public void run() {
            try {
                for (Map.Entry<Integer, BeanPosition> entry : getPosition().entrySet()) {
                    if (entry.getValue().getPosition() != 0) {
                        String expiry = Parameters.symbol.get(entry.getKey()).getExpiry();
                        if (expiry.equals(expiryNearMonth)) {
                            ArrayList<Integer> entryorderidlist = new ArrayList<>();
                            int initID = entry.getKey();
                            EnumOrderSide side = EnumOrderSide.UNDEFINED;

                            if (Parameters.symbol.get(initID).getType().equals("OPT")) {//Calculate Side for option
                                if (optionSystem.equals("RECEIVE")) {
                                    if (entry.getValue().getPosition() < 0) {
                                        side = Parameters.symbol.get(initID).getRight().equals("CALL") ? EnumOrderSide.SHORT : EnumOrderSide.BUY;
                                    }
                                } else if (optionSystem.equals("PAY")) {
                                    if (entry.getValue().getPosition() > 0) {
                                        side = entry.getValue().getPosition() > 0 ? EnumOrderSide.BUY : EnumOrderSide.SHORT;
                                    }
                                }
                            } else { //calculate side for future
                                side = Parameters.symbol.get(initID).getRight().equals("CALL") ? EnumOrderSide.BUY : EnumOrderSide.SHORT;
                            }

                            if (Parameters.symbol.get(initID).getType().equals("OPT") && optionPricingUsingFutures) { //calculate targetID for option
                                int futureid = Utilities.getFutureIDFromBrokerSymbol(Parameters.symbol, initID, expiryFarMonth);
                                if (optionSystem.equals("PAY")) {
                                    entryorderidlist = Utilities.getOrInsertOptionIDForPaySystem(Parameters.symbol, getPosition(), futureid, side, expiryFarMonth);
                                } else {
                                    entryorderidlist = Utilities.getOrInsertOptionIDForReceiveSystem(Parameters.symbol, getPosition(), futureid, side, expiryFarMonth);
                                }
                            } else if (Parameters.symbol.get(initID).getType().equals("OPT") && !optionPricingUsingFutures) {
                                int symbolid = Utilities.getCashReferenceID(Parameters.symbol, initID, referenceCashType);
                                int referenceid = Utilities.getCashReferenceID(Parameters.symbol, symbolid, referenceCashType);
                                if (optionSystem.equals("PAY")) {
                                    entryorderidlist = Utilities.getOrInsertOptionIDForPaySystem(Parameters.symbol, getPosition(), referenceid, side, expiryFarMonth);
                                } else {
                                    entryorderidlist = Utilities.getOrInsertOptionIDForReceiveSystem(Parameters.symbol, getPosition(), referenceid, side, expiryFarMonth);
                                }
                            } else if (Parameters.symbol.get(initID).getType().equals("FUT")) { //calculate targetID for futures
                                entryorderidlist.add(Utilities.getFutureIDFromBrokerSymbol(Parameters.symbol, initID, expiryFarMonth));
                            }

                            if (!entryorderidlist.isEmpty() && entryorderidlist.get(0) != -1) {
                                initSymbol(entryorderidlist.get(0), optionPricingUsingFutures, referenceCashType);
                                positionRollover(initID, entryorderidlist.get(0));
                            } else {
                                logger.log(Level.INFO, "100,Rollover not completed as invalid Target symbol,{0}:{1}:{2}:{3}:{4}", new Object[]{getStrategy(), "Order", Parameters.symbol.get(initID).getDisplayname(), -1, -1});
                            }
                        }
                    }
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, null, e);
            }
        }
    };

    public void positionRollover(int initID, int targetID) {
        if (optionSystem.equals("PAY")) {
            //get side, size of position
            EnumOrderSide origSide = this.getPosition().get(initID).getPosition() > 0 ? EnumOrderSide.BUY : this.getPosition().get(initID).getPosition() < 0 ? EnumOrderSide.SHORT : EnumOrderSide.UNDEFINED;
            int size = Math.abs(this.getPosition().get(initID).getPosition());
            ArrayList<Stop> stops = null;
            //square off position        

            switch (origSide) {
                case BUY:
                    logger.log(Level.INFO, "101,Rollover SELL,{0}:{1}:{2}:{3}:{4}",
                            new Object[]{getStrategy(), "Order", Parameters.symbol.get(initID).getDisplayname(), -1, -1});
                    stops = Trade.getStop(this.getDb(), this.getStrategy() + ":" + this.getFirstInternalOpenOrder(initID, EnumOrderSide.SELL, "Order").iterator().next() + ":Order");
                    HashMap<String, Object> order = new HashMap<>();
                    int referenceid = Utilities.getCashReferenceID(Parameters.symbol, targetID, referenceCashType);
                    double limitprice = Utilities.getLimitPriceForOrder(Parameters.symbol, targetID, referenceid, EnumOrderSide.SELL, getTickSize(), this.getOrdType());
                    order.put("id", initID);
                    order.put("type", this.getOrdType());
                    order.put("side", EnumOrderSide.SELL);
                    order.put("size", size);
                    order.put("limitprice", limitprice);
                    order.put("reason", EnumOrderReason.REGULAREXIT);
                    order.put("orderstage", EnumOrderStage.INIT);
                    order.put("expiretime", this.getMaxOrderDuration());
                    order.put("dynamicorderduration", getDynamicOrderDuration());
                    order.put("maxslippage", this.getMaxSlippageExit());
                    order.put("orderattributes", this.getOrderAttributes());
                    order.put("scale", getScaleExit());
                    order.put("log", "ROLLOVERSQUAREOFF");
                    this.exit(order);
                    break;
                case SHORT:
                    logger.log(Level.INFO, "101,Strategy Rollover EXIT SHORT,{0}:{1}:{2}:{3}:{4}",
                            new Object[]{getStrategy(), "Order", Parameters.symbol.get(initID).getDisplayname(), -1, -1});
                    stops = Trade.getStop(this.getDb(), this.getStrategy() + ":" + this.getFirstInternalOpenOrder(initID, EnumOrderSide.COVER, "Order").iterator().next() + ":Order");
                    order = new HashMap<>();
                    referenceid = Utilities.getCashReferenceID(Parameters.symbol, targetID, referenceCashType);
                    limitprice = Utilities.getLimitPriceForOrder(Parameters.symbol, targetID, referenceid, EnumOrderSide.COVER, getTickSize(), this.getOrdType());
                    order.put("id", initID);
                    order.put("type", this.getOrdType());
                    order.put("side", EnumOrderSide.COVER);
                    order.put("size", size);
                    order.put("limitprice", limitprice);
                    order.put("reason", EnumOrderReason.REGULAREXIT);
                    order.put("orderstage", EnumOrderStage.INIT);
                    order.put("expiretime", this.getMaxOrderDuration());
                    order.put("dynamicorderduration", getDynamicOrderDuration());
                    order.put("maxslippage", this.getMaxSlippageExit());
                    order.put("orderattributes", this.getOrderAttributes());
                    order.put("scale", getScaleExit());
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
                        logger.log(Level.INFO, "101,Strategy Rollover ENTER BUY,{0}:{1}:{2}:{3}:{4},NewPositionSize={5}",
                                new Object[]{getStrategy(), "Order", Parameters.symbol.get(targetID).getDisplayname(), -1, -1, newSize});
                        HashMap<String, Object> order = new HashMap<>();
                        int referenceid = Utilities.getCashReferenceID(Parameters.symbol, targetID, referenceCashType);
                        double limitprice = Utilities.getLimitPriceForOrder(Parameters.symbol, targetID, referenceid, EnumOrderSide.BUY, getTickSize(), this.getOrdType());
                        order.put("id", targetID);
                        order.put("side", EnumOrderSide.BUY);
                        order.put("size", newSize);
                        order.put("type", this.getOrdType());
                        order.put("limitprice", limitprice);
                        order.put("reason", EnumOrderReason.REGULARENTRY);
                        order.put("orderstage", EnumOrderStage.INIT);
                        order.put("expiretime", this.getMaxOrderDuration());
                        order.put("dynamicorderduration", getDynamicOrderDuration());
                        order.put("maxslippage", this.getMaxSlippageExit());
                        order.put("scale", getScaleEntry());
                        order.put("orderattributes", this.getOrderAttributes());
                        order.put("log", "ROLLOVERENTRY");
                        orderid = this.entry(order);
                        //orderid = this.getFirstInternalOpenOrder(initID, EnumOrderSide.SELL, "Order");
                    }
                    break;
                case SHORT:
                    if (this.getShortOnly()) {
                        logger.log(Level.INFO, "101,Strategy Rollover ENTER SHORT,{0}:{1}:{2}:{3}:{4},NewPositionSize={5}",
                                new Object[]{getStrategy(), "Order", Parameters.symbol.get(targetID).getDisplayname(), -1, -1, newSize});
                        HashMap<String, Object> order = new HashMap<>();
                        order = new HashMap<>();
                        int referenceid = Utilities.getCashReferenceID(Parameters.symbol, targetID, referenceCashType);
                        double limitprice = Utilities.getLimitPriceForOrder(Parameters.symbol, targetID, referenceid, EnumOrderSide.SHORT, getTickSize(), this.getOrdType());
                        order.put("id", targetID);
                        order.put("side", EnumOrderSide.SHORT);
                        order.put("size", newSize);
                        order.put("type", this.getOrdType());
                        order.put("limitprice", Parameters.symbol.get(targetID).getLastPrice());
                        order.put("reason", EnumOrderReason.REGULARENTRY);
                        order.put("orderstage", EnumOrderStage.INIT);
                        order.put("expiretime", this.getMaxOrderDuration());
                        order.put("dynamicorderduration", getDynamicOrderDuration());
                        order.put("maxslippage", this.getMaxSlippageExit());
                        order.put("orderattributes", this.getOrderAttributes());
                        order.put("scale", getScaleEntry());
                        order.put("log", "ROLLOVERENTRY");
                        orderid = this.entry(order);
                        //orderid = this.getFirstInternalOpenOrder(initID, EnumOrderSide.COVER, "Order");
                    }
                    break;
                default:
                    break;
            }
            //update stop information
            logger.log(Level.INFO, "102,Strategy Rollover Stop Update,{0}:{1}:{2}:{3}:{4},NewPositionSize={5},StopArray={6}",
                    new Object[]{getStrategy(), "Order", Parameters.symbol.get(targetID).getDisplayname(), orderid, -1, newSize, Arrays.toString(stops.toArray())});
            if (orderid >= 0) {
                Trade.setStop(this.getDb(), this.getStrategy() + ":" + orderid + ":" + "Order", "opentrades", stops);
            }
        }
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
