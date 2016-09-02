/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.algorithms.swing;

import com.incurrency.algorithms.manager.Manager;
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

    public Swing(MainAlgorithm m, Properties p, String parameterFile, ArrayList<String> accounts, Integer stratCount) {
        super(m, p, parameterFile, accounts, stratCount);

        /*
         * Swing has 4 timer tasks
         * Timertask 1: eodProcessingTask - scans at the specified time for entry signals
         * Timertask 2: bodProcessingTask - scans at the start of program and updates stop levels
         * Timertask 3: tradeProcessingTask - scans for trades every 60 seconds. - uses manager implementation
         * Timertask 4: rollProcessingTask - if trading day is also a rollover day, rolls over any open positions.
         */

        Timer bodProcessing = new Timer("Timer: " + this.getStrategy() + " BODProcessing");
        bodProcessing.schedule(bodProcessingTask, 10 * 1000);

        if (rollover) {
            Timer rollProcessing = new Timer("Timer: " + this.getStrategy() + " RollProcessing");
            rollProcessing.schedule(rollProcessingTask, DateUtil.addSeconds(RScriptRunTime, 60));
        }
    }

    @Override
    public void tradeReceived(TradeEvent event) {
        synchronized (lockTradeReceived_1) {
            Integer id = event.getSymbolID();
            if (getStrategySymbols().contains(id) && !Parameters.symbol.get(id).getType().equals(referenceCashType)) {
                if (this.getPosition().get(id).getPosition() > 0 && this.getPosition().get(id).getStrategy().equalsIgnoreCase(this.getStrategy())) {
                    int referenceid = Utilities.getReferenceID(Parameters.symbol, id, referenceCashType);
                    int futureid = Utilities.getFutureIDFromExchangeSymbol(Parameters.symbol, referenceid, expiry);
                    Double tradePrice = this.getPosition().get(id).getPrice();
                    ArrayList<Stop> stops = Trade.getStop(db, this.getStrategy() + ":" + this.getFirstInternalOpenOrder(id, EnumOrderSide.SELL, "Order").iterator().next() + ":Order");
                    boolean tpTrigger = false;
                    boolean slTrigger = false;
                    double tpDistance = 0D;
                    double slDistance = 0D;
                    double sl = Double.MIN_VALUE;
                    double tp = Double.MAX_VALUE;
                    if (stops != null && Parameters.symbol.get(id).getLastPrice() != 0) {
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
                        double limitprice = Utilities.getOptionLimitPriceForRel(Parameters.symbol, id, futureid, EnumOrderSide.SELL, right, getTickSize());
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
            if(tradetuple.get(1).contains("BUY")||tradetuple.get(1).contains("SHORT")){
                expectedTrades.add(tradetuple.get(1));
            }
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
               // if (stop.recalculate == Boolean.TRUE) {
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
               // }
            }
        }
    }
    TimerTask tradeScannerTask = new TimerTask() {
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
        if(!getRStrategyFile().equals("")){
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
            logger.log(Level.INFO, "Invoking R. Strategy:{0},args: {1}", new Object[]{getStrategy(),Arrays.toString(args)});
            c.assign("args", args);
            c.eval("source(\"" + this.getRStrategyFile() + "\")");
        } catch (Exception e) {
            logger.log(Level.SEVERE, null, e);
        }
        }
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


    public void positionRollover(int initID, int targetID) {
        //get side, size of position
        EnumOrderSide origSide = this.getPosition().get(initID).getPosition() > 0 ? EnumOrderSide.BUY : this.getPosition().get(initID).getPosition() < 0 ? EnumOrderSide.SHORT : EnumOrderSide.UNDEFINED;
        int size = Math.abs(this.getPosition().get(initID).getPosition());
        ArrayList<Stop> stops = null;
        //square off position        

        switch (origSide) {
            case BUY:
                logger.log(Level.INFO, "501,Strategy Rollover EXIT BUY,{0}", new Object[]{getStrategy() + delimiter + Parameters.symbol.get(initID).getExchangeSymbol()});
                stops = Trade.getStop(db, this.getStrategy() + ":" + this.getFirstInternalOpenOrder(initID, EnumOrderSide.SELL, "Order").iterator().next() + ":Order");
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
                stops = Trade.getStop(db, this.getStrategy() + ":" + this.getFirstInternalOpenOrder(initID, EnumOrderSide.COVER, "Order").iterator().next() + ":Order");
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
