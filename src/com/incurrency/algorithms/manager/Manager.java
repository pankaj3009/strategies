/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.algorithms.manager;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.incurrency.framework.Algorithm;
import com.incurrency.framework.BeanConnection;
import com.incurrency.framework.BeanPosition;
import com.incurrency.framework.DateUtil;
import com.incurrency.framework.EnumOrderReason;
import com.incurrency.framework.EnumOrderSide;
import com.incurrency.framework.EnumOrderStage;
import com.incurrency.framework.Order.EnumOrderType;
import com.incurrency.framework.Mail;
import com.incurrency.framework.MainAlgorithm;
import com.incurrency.framework.OrderBean;
import com.incurrency.framework.Parameters;
import com.incurrency.framework.Stop;
import com.incurrency.framework.Strategy;
import com.incurrency.framework.Utilities;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Pankaj
 */
public class Manager extends Strategy {

    public String expiryNearMonth;
    public String expiryFarMonth;
    public String referenceCashType;
    public String rServerIP;
    public Date RScriptRunTime;
    public Boolean rollover;
    public int rolloverDays;
    public String expiry;
    public String RStrategyFile;
    public String wd;
    public String securityType;
    public boolean optionPricingUsingFutures = true;
    public String optionSystem;
    public Boolean scaleEntry = Boolean.FALSE;
    public Boolean scaleExit = Boolean.FALSE;
    public Boolean aggregatePositions = Boolean.TRUE;

    private static final Logger logger = Logger.getLogger(Manager.class.getName());

    public Manager(MainAlgorithm m, Properties p, String parameterFile, ArrayList<String> accounts, String strategy) {
        super(m,p, parameterFile, accounts);
        loadParameters(p);
        String[] tempStrategyArray = parameterFile.split("\\.")[0].split("_");
        for (BeanConnection c : Parameters.connection) {
            c.initializeConnection(tempStrategyArray[tempStrategyArray.length - 1], -1);
        }
        rollover = Utilities.rolloverDay(rolloverDays, this.getStartDate(), this.expiryNearMonth);
        if (rollover) {
            expiry = this.expiryFarMonth;
        } else {
            expiry = this.expiryNearMonth;
        }
        logger.log(Level.FINE, "102,ExpiryDate For EntryTrades Set,{0}:{1}:{2}:{3}:{4},ExpiryDate={5}",
                new Object[]{this.getStrategy(), "Order", "Unknown", "-1", "-1", expiry});

        Timer monitor = new Timer("Timer: " + this.getStrategy() + " WaitForTrades");
        monitor.schedule(TradeProcessor, new Date());
        Timer Jsonmonitor = new Timer("Timer: " + this.getStrategy() + " WaitForJsonTrades");
        Jsonmonitor.schedule(JsonTradeProcessor, new Date());
        
        Timer mdrequest = new Timer("Timer: " + this.getStrategy() + " WaitForMDRequest");
        mdrequest.schedule(MarketDataRequestProcessor, new Date());
        
    }

    private void loadParameters(Properties p) {
        //expiryNearMonth = p.getProperty("NearMonthExpiry").toString().trim();
        //expiryFarMonth = p.getProperty("FarMonthExpiry").toString().trim();
        String today = DateUtil.getFormatedDate("yyyyMMdd", new Date().getTime(), TimeZone.getTimeZone(Algorithm.timeZone));
        expiryNearMonth = Utilities.getLastThursday(today, "yyyyMMdd", 0);
        Date dtExpiry = DateUtil.parseDate("yyyyMMdd", expiryNearMonth, timeZone);
        String expiryplus = DateUtil.getFormatedDate("yyyyMMdd", DateUtil.addDays(dtExpiry, 1).getTime(), TimeZone.getTimeZone(Algorithm.timeZone));
        expiryFarMonth = Utilities.getLastThursday(expiryplus, "yyyyMMdd", 0);
        referenceCashType = p.getProperty("ReferenceCashType", null);
        rServerIP = p.getProperty("RServerIP").toString().trim();
        securityType = p.getProperty("SecurityType", "PASSTHROUGH");
        optionPricingUsingFutures = Boolean.valueOf(p.getProperty("OptionPricingUsingFutures", "TRUE"));
        optionSystem = p.getProperty("OptionSystem", "PAY");
        aggregatePositions = Boolean.valueOf(p.getProperty("AggregatePositions", "True"));
        String entryScanTime = p.getProperty("ScanStartTime");
        Calendar calToday = Calendar.getInstance(TimeZone.getTimeZone(Algorithm.timeZone));
        String[] entryTimeComponents = entryScanTime.split(":");
        calToday.set(Calendar.HOUR_OF_DAY, Utilities.getInt(entryTimeComponents[0], 15));
        calToday.set(Calendar.MINUTE, Utilities.getInt(entryTimeComponents[1], 20));
        calToday.set(Calendar.SECOND, Utilities.getInt(entryTimeComponents[2], 0));
        RScriptRunTime = calToday.getTime();
        if (this.RScriptRunTime.compareTo(new Date()) < 0) {
            calToday.add(Calendar.DATE, 1);
            RScriptRunTime = calToday.getTime();
        }
        rolloverDays = Integer.valueOf(p.getProperty("RolloverDays", "0"));
        RStrategyFile = p.getProperty("RStrategyFile", "");
        wd = p.getProperty("wd", "/home/psharma/Seafile/R");
        scaleEntry = Boolean.parseBoolean(p.getProperty("ScaleEntry", "FALSE"));
        scaleExit = Boolean.parseBoolean(p.getProperty("ScaleExit", "FALSE"));

    }

    public TimerTask TradeProcessor = new TimerTask() {
        @Override
        public void run() {
            while (true && Parameters.symbol.size()>0) {
                waitForTrades();
            }
        }
    };
    
        public TimerTask JsonTradeProcessor = new TimerTask() {
        @Override
        public void run() {
            while (true) {
                waitForJsonTrades();
            }
        }
    };


    public TimerTask MarketDataRequestProcessor = new TimerTask() {
        @Override
        public void run() {
            while (true) {
                waitForMarketDataRequest();
            }
        }
    };

    public void waitForMarketDataRequest() {
        List<String> tradetuple = this.getDb().blpop("mdrequest:" + this.getStrategy(), "", 60);
        if (tradetuple != null) {
            logger.log(Level.INFO, "101,Received MarketData Request:{0} for strategy {1}", new Object[]{tradetuple.get(1), tradetuple.get(0)});
            String displayName = tradetuple.get(1);
            String symbol = displayName.split(":", -1)[0];
            int symbolid = Utilities.getIDFromDisplayName(Parameters.symbol, symbol);
            if (symbolid == -1) {
                insertSymbol(Parameters.symbol, symbol, optionPricingUsingFutures);
            }
        }
    }

    public void waitForTrades() {
        try {
            List<String> tradetuple = this.getDb().blpop("trades:" + this.getStrategy(), "", 60);
            if (tradetuple != null) {
                logger.log(Level.INFO, "101,Received Trade:{0} for strategy {1}", new Object[]{tradetuple.get(1), tradetuple.get(0)});
                //tradetuple as symbol:size:side:sl
                Thread t = new Thread(new Mail(getIamail(), "Received Trade: " + tradetuple.get(1) + " for strategy " + tradetuple.get(0), "Received Order from [R]"));
                t.start();
                String displayName = tradetuple.get(1);
                String symbol = displayName.split(":", -1)[0];
                EnumOrderSide side = EnumOrderSide.valueOf(displayName.split(":", -1)[2]);
                EnumOrderSide derivedSide = side;
                int size = Integer.valueOf(displayName.split(":", -1)[1]);
                double stoploss = Double.valueOf(displayName.split(":", -1)[3]);
                int initPositionSize = Integer.valueOf(displayName.split(":", -1)[4]);
                int actualPositionSize = initPositionSize;
                int symbolid = Utilities.getIDFromDisplayName(Parameters.symbol, symbol);
                if (symbolid == -1 && securityType.equals("PASSTHROUGH") && (side.equals(EnumOrderSide.BUY) || side.equals(EnumOrderSide.SHORT))) {
                    //insert symbol into database
                    insertSymbol(Parameters.symbol, symbol, optionPricingUsingFutures);
                    symbolid = Utilities.getIDFromDisplayName(Parameters.symbol, symbol);
                }
                if (securityType.equals("OPT") || displayName.contains("OPT") || symbolid >= 0) { //only proceed if symbolid exists in our db
                    ArrayList<Integer> entryorderidlist = new ArrayList<>();
                    ArrayList<Integer> exitorderidlist = new ArrayList<>();
                    /*
                     * We generate orderidlist, which is a list of all ids 
                     * This is based on the securityType.
                     */
                    if (securityType.equals("OPT")) {
                        if (displayName.contains("OPT")) { //passthrough
                            if (side.equals(EnumOrderSide.SELL) || side.equals(EnumOrderSide.COVER)) {
                                exitorderidlist.add(symbolid);
                                actualPositionSize = Utilities.getNetPosition(Parameters.symbol, this.getPosition(), exitorderidlist.get(0), true);
                            } else if (side.equals(EnumOrderSide.BUY) || side.equals(EnumOrderSide.SHORT)) {
                                int referenceid = Utilities.getCashReferenceID(Parameters.symbol, symbolid);
                                if (optionPricingUsingFutures) {
                                    int futureid = Utilities.getFutureIDFromBrokerSymbol(Parameters.symbol, referenceid, expiry);
                                    if (futureid >= 0) { //future exists in db
                                        symbolid = Utilities.insertStrike(Parameters.symbol, futureid, symbol.split("_", -1)[2], symbol.split("_", -1)[3], symbol.split("_", -1)[4]);
                                    }
                                    entryorderidlist.add(symbolid);
                                } else {
                                    symbolid = Utilities.insertStrike(Parameters.symbol, referenceid, symbol.split("_", -1)[2], symbol.split("_", -1)[3], symbol.split("_", -1)[4]);
                                    entryorderidlist.add(symbolid);
                                }
                                if (exitorderidlist.size() > 0) {
                                    actualPositionSize = Utilities.getNetPosition(Parameters.symbol, this.getPosition(), entryorderidlist.get(0), true);
                                } else {
                                    actualPositionSize = 0;
                                }
                            }
                        } else { //symbolid needs to be derived
                            int referenceid = Utilities.getCashReferenceID(Parameters.symbol, symbolid);
                            if (side.equals(EnumOrderSide.SELL) || side.equals(EnumOrderSide.COVER)) {
                                if (optionPricingUsingFutures) {
                                    int futureid = Utilities.getFutureIDFromBrokerSymbol(Parameters.symbol, referenceid, expiryNearMonth);
                                    if (optionSystem.equals("PAY")) {
                                        exitorderidlist = Utilities.getOrInsertOptionIDForPaySystem(Parameters.symbol, this.getPosition(), futureid, side, this.expiryNearMonth);
                                    } else {
                                        exitorderidlist = Utilities.getOrInsertOptionIDForReceiveSystem(Parameters.symbol, this.getPosition(), futureid, side, this.expiryNearMonth);
                                    }
                                    futureid = Utilities.getFutureIDFromBrokerSymbol(Parameters.symbol, referenceid, expiryFarMonth);
                                    if (optionSystem.equals("PAY")) {
                                        exitorderidlist.addAll(Utilities.getOrInsertOptionIDForPaySystem(Parameters.symbol, this.getPosition(), futureid, side, this.expiryFarMonth));
                                    } else {
                                        exitorderidlist.addAll(Utilities.getOrInsertOptionIDForReceiveSystem(Parameters.symbol, this.getPosition(), futureid, side, this.expiryFarMonth));
                                    }
                                } else {
                                    if (optionSystem.equals("PAY")) {
                                        //exitorderidlist = Utilities.getOrInsertOptionIDForPaySystem(Parameters.symbol, this.getPosition(), referenceid, side, this.expiryNearMonth);
                                        exitorderidlist.addAll(Utilities.getOrInsertOptionIDForPaySystem(Parameters.symbol, this.getPosition(), referenceid, side, this.expiryFarMonth));
                                    } else {
                                        //exitorderidlist = Utilities.getOrInsertOptionIDForReceiveSystem(Parameters.symbol, this.getPosition(), referenceid, side, this.expiryNearMonth);
                                        exitorderidlist.addAll(Utilities.getOrInsertOptionIDForReceiveSystem(Parameters.symbol, this.getPosition(), referenceid, side, this.expiryFarMonth));
                                    }
                                }
                                if (optionSystem.equals("RECEIVE")) {
                                    derivedSide = EnumOrderSide.COVER;
                                } else {
                                    derivedSide = EnumOrderSide.SELL;
                                }
                                if (exitorderidlist.size() > 0) {
                                    actualPositionSize = Utilities.getNetPosition(Parameters.symbol, this.getPosition(), exitorderidlist.get(0), true);
                                } else {
                                    actualPositionSize = 0;
                                }
                            } else if (side.equals(EnumOrderSide.BUY) || side.equals(EnumOrderSide.SHORT)) {
                                if (optionPricingUsingFutures) {
                                    int futureid = Utilities.getFutureIDFromBrokerSymbol(Parameters.symbol, referenceid, expiry);
                                    if (optionSystem.equals("PAY")) {
                                        entryorderidlist = Utilities.getOrInsertOptionIDForPaySystem(Parameters.symbol, this.getPosition(), futureid, side, expiry);
                                    } else {
                                        entryorderidlist = Utilities.getOrInsertOptionIDForReceiveSystem(Parameters.symbol, this.getPosition(), futureid, side, expiry);
                                    }
                                } else {
                                    if (optionSystem.equals("PAY")) {
                                        entryorderidlist = Utilities.getOrInsertOptionIDForPaySystem(Parameters.symbol, this.getPosition(), referenceid, side, expiry);
                                    } else {
                                        entryorderidlist = Utilities.getOrInsertOptionIDForReceiveSystem(Parameters.symbol, this.getPosition(), referenceid, side, expiry);
                                    }
                                }
                                if (optionSystem.equals("RECEIVE")) {
                                    derivedSide = EnumOrderSide.SHORT;
                                } else {
                                    derivedSide = EnumOrderSide.BUY;
                                }
                                actualPositionSize = Utilities.getNetPosition(Parameters.symbol, this.getPosition(), entryorderidlist.get(0), true);
                            }

                        }
                    }

                    if (securityType.equals("FUT")) {
                        if (displayName.contains("FUT")) { //passthrough
                            if (side.equals(EnumOrderSide.SELL) || side.equals(EnumOrderSide.COVER)) {
                                exitorderidlist.add(symbolid);
                                actualPositionSize = Utilities.getNetPosition(Parameters.symbol, this.getPosition(), exitorderidlist.get(0), true);
                            } else if (side.equals(EnumOrderSide.BUY) || side.equals(EnumOrderSide.SHORT)) {
                                int referenceid = Utilities.getCashReferenceID(Parameters.symbol, symbolid);
                                int futureid = Utilities.getFutureIDFromBrokerSymbol(Parameters.symbol, referenceid, expiry);
                                entryorderidlist.add(futureid);
                                actualPositionSize = Utilities.getNetPosition(Parameters.symbol, this.getPosition(), entryorderidlist.get(0), true);
                            }
                        } else { //symbolid needs to be derived
                            int referenceid = Utilities.getCashReferenceID(Parameters.symbol, symbolid);
                            if (side.equals(EnumOrderSide.SELL) || side.equals(EnumOrderSide.COVER)) {
                                int futureid = Utilities.getFutureIDFromBrokerSymbol(Parameters.symbol, referenceid, expiryNearMonth);
                                exitorderidlist.add(this.EntryInternalOrderIDForSquareOff(futureid, "Order",getStrategy(), side));
                                futureid = Utilities.getFutureIDFromBrokerSymbol(Parameters.symbol, referenceid, expiryFarMonth);
                                exitorderidlist.add(this.EntryInternalOrderIDForSquareOff(futureid, "Order",getStrategy(), side));
                                actualPositionSize = Utilities.getNetPosition(Parameters.symbol, this.getPosition(), exitorderidlist.get(0), true);
                            } else if (side.equals(EnumOrderSide.BUY) || side.equals(EnumOrderSide.SHORT)) {
                                if (optionPricingUsingFutures) {
                                    int futureid = Utilities.getFutureIDFromBrokerSymbol(Parameters.symbol, referenceid, expiry);
                                    entryorderidlist.add(futureid);
                                }
                                actualPositionSize = Utilities.getNetPosition(Parameters.symbol, this.getPosition(), entryorderidlist.get(0), true);
                            }
                        }
                    }

                    if (securityType.equals("PASSTHROUGH")) {
                        if (side.equals(EnumOrderSide.SELL) || side.equals(EnumOrderSide.COVER)) {
                            exitorderidlist.add(symbolid);
                            actualPositionSize = Utilities.getNetPosition(Parameters.symbol, this.getPosition(), exitorderidlist.get(0), true);
                        } else if (side.equals(EnumOrderSide.BUY) || side.equals(EnumOrderSide.SHORT)) {
                            entryorderidlist.add(symbolid);
                            actualPositionSize = Utilities.getNetPosition(Parameters.symbol, this.getPosition(), entryorderidlist.get(0), true);
                        }
                    }

                    stoploss = Utilities.round(stoploss, getTickSize(), 2);
                    OrderBean order = new OrderBean();

                    if (entryorderidlist.size() > 0) {
                        for (int i : entryorderidlist) {
                            this.initSymbol(i, optionPricingUsingFutures);
                        }
                    }
                    if (exitorderidlist.size() > 0) {
                        for (int i : exitorderidlist) {
                            this.initSymbol(i, optionPricingUsingFutures);
                        }
                    }

                    // Thread.sleep(4000); //wait for 4 seconds
                    // Thread.yield();
                    /*
                     * IF initpositionsize = 100, actual positionsize=0, we get a buy of 100. comp=100, size=200
                     * IF initpositionsize=0, actualpositionsize=100, we get buy of 100, comp=-100, size=0, probably a duplicate trade
                     * IF initpositionsize=-100,actualpositionsize=0, we get a short of 100,should be short, but are not, comp=-100,size=abs(-100-100)=200
                     * IF initpositionsize=200, actualpositionsize=100, we set a SELL of 200, comp=100, size=abs(-200+100)=100
                     */
                    if (!aggregatePositions) {
                        if (exitorderidlist.size() > 0) {
                            actualPositionSize = this.getPosition().get(exitorderidlist.get(0)).getPosition();
                        } else if (entryorderidlist.size() > 0) {
                            if (this.getPosition().get(entryorderidlist.get(0)) == null) {
                                this.getPosition().put(entryorderidlist.get(0), new BeanPosition(entryorderidlist.get(0), getStrategy()));
                                //this.initSymbol(entryorderidlist.get(0), this.optionPricingUsingFutures, referenceCashType);
                            }
                            actualPositionSize = this.getPosition().get(entryorderidlist.get(0)).getPosition();
                        }
                    }
                    int catchUpPosition = 0;
                    switch (derivedSide) {
                        case BUY:
                            catchUpPosition = initPositionSize - actualPositionSize;
                            size = Math.max(size + catchUpPosition, 0);
                            break;
                        case SHORT:
                            catchUpPosition = -initPositionSize - actualPositionSize;
                            size = Math.max(size - catchUpPosition, 0);
                            break;
                        case SELL:
                            catchUpPosition = initPositionSize - actualPositionSize;
                            size = Math.max(size - catchUpPosition, 0);
                            //if actualpositionsize is -ve, that is an error condition.
                            break;
                        case COVER:
                            catchUpPosition = initPositionSize + actualPositionSize;
                            size = Math.max(size - catchUpPosition, 0);
                            break;
                        default:
                            break;
                    }
                    //int excessposition=initPositionSize-actualpositionsize;
                    //size = (derivedSide == EnumOrderSide.BUY || derivedSide == EnumOrderSide.COVER) ? size + compensation : Math.abs(-size + compensation);

                    /*
                     * IF initpositionsize = 100, actual positionsize=0, we get a buy of 100. comp=100, size=200
                     * IF initpositionsize=0, actualpositionsize=100, we get buy of 100, comp=-100, size=0, probably a duplicate trade
                     * IF initpositionsize=-100,actualpositionsize=0, we get a short of 100,should be short, but are not, comp=-100,size=abs(-100-100)=200
                     * IF initpositionsize=200, actualpositionsize=100, we set a SELL of 200, comp=100, size=abs(-200+100)=100
                     */
                    if (size > 0) {
                        order.setOrderType(getOrdType());
                        int orderid;
                        ArrayList<Stop> stops = new ArrayList<>();
                        Stop stp = new Stop();
                        switch (derivedSide) {
                            case BUY:
                                for (int id : entryorderidlist) {
                                    if (id >= 0) {
                                        order.setParentDisplayName(Parameters.symbol.get(id).getDisplayname());
                                        order.setChildDisplayName(Parameters.symbol.get(id).getDisplayname());
                                        int referenceid = -1;
                                        if (Parameters.symbol.get(id).getType().equals("OPT")) {
                                            referenceid = Utilities.getCashReferenceID(Parameters.symbol, id);
                                            String tempExpiry = Parameters.symbol.get(id).getExpiry();
                                            referenceid = this.optionPricingUsingFutures ? Utilities.getFutureIDFromBrokerSymbol(Parameters.symbol, referenceid, tempExpiry) : referenceid;
                                        }
                                        double limitprice = Utilities.getLimitPriceForOrder(Parameters.symbol, id, referenceid, derivedSide, getTickSize(), this.getOrdType(),0);
                                        if (getOrderAttributes().get("barrierlimitprice") != null && getOrderAttributes().get("barrierlimitprice").toString().equalsIgnoreCase("true")) {
                                            order.setBarrierLimitPrice(limitprice);
                                        }
                                        order.setLimitPrice(limitprice);
                                        order.setOrderSide(derivedSide);
                                        order.setOriginalOrderSize(size);
                                        order.setOrderReason(EnumOrderReason.REGULARENTRY);
                                        order.setOrderStage(EnumOrderStage.INIT);
                                        order.setScale(getScaleEntry());
                                        order.setOrderLog("BUY" + delimiter + tradetuple.get(1));
                                        HashMap<String, Object> tmpOrderAttributes = new HashMap<>();
                                        tmpOrderAttributes.putAll(this.getOrderAttributes());
                                        order.setOrderAttributes(tmpOrderAttributes);
                                        order.setStopLoss(stoploss);
                                        if ((this.getOrdType() != EnumOrderType.MKT && limitprice > 0) || this.getOrdType().equals(EnumOrderType.MKT)) {
                                            logger.log(Level.INFO, "501,Strategy BUY,{0}", new Object[]{getStrategy() + delimiter + "BUY" + delimiter + Parameters.symbol.get(id).getDisplayname()});
                                            orderid = entry(order);
                                        }
                                    }
                                }
                                break;
                            case SELL:
                                for (int id : exitorderidlist) {
                                    if (id >= 0) {
                                        order.setParentDisplayName(Parameters.symbol.get(id).getDisplayname());
                                        order.setChildDisplayName(Parameters.symbol.get(id).getDisplayname());
                                        int referenceid = -1;
                                        if (Parameters.symbol.get(id).getType().equals("OPT")) {
                                            referenceid = Utilities.getCashReferenceID(Parameters.symbol, id);
                                            String tempExpiry = Parameters.symbol.get(id).getExpiry();
                                            referenceid = this.optionPricingUsingFutures ? Utilities.getFutureIDFromBrokerSymbol(Parameters.symbol, referenceid, tempExpiry) : referenceid;
                                        }
                                        double limitprice = Utilities.getLimitPriceForOrder(Parameters.symbol, id, referenceid, derivedSide, getTickSize(), this.getOrdType(),0);
                                        if (getOrderAttributes().get("barrierlimitprice") != null && getOrderAttributes().get("barrierlimitprice").toString().equalsIgnoreCase("true")) {
                                            order.setBarrierLimitPrice(limitprice);
                                        }
                                        order.setLimitPrice(limitprice);
                                        order.setOrderSide(derivedSide);
                                        order.setOriginalOrderSize(size);
//                                        order.setCurrentOrderSize(size);
                                        order.setOrderReason(EnumOrderReason.REGULAREXIT);
                                        order.setOrderStage(EnumOrderStage.INIT);
                                        order.setScale(getScaleExit());
                                        order.setOrderLog("SELL" + delimiter + tradetuple.get(1));
                                        HashMap<String, Object> tmpOrderAttributes = new HashMap<>();
                                        tmpOrderAttributes.putAll(this.getOrderAttributes());
                                        order.setOrderAttributes(tmpOrderAttributes);
                                        if ((this.getOrdType() != EnumOrderType.MKT && limitprice > 0) || this.getOrdType().equals(EnumOrderType.MKT)) {
                                            logger.log(Level.INFO, "501,Strategy SELL,{0}", new Object[]{getStrategy() + delimiter + "SELL" + delimiter + Parameters.symbol.get(id).getDisplayname()});
                                            exit(order);
                                        }
                                    }
                                }
                                break;
                            case SHORT:
                                for (int id : entryorderidlist) {
                                    if (id >= 0) {
                                        order.setParentDisplayName(Parameters.symbol.get(id).getDisplayname());
                                        order.setChildDisplayName(Parameters.symbol.get(id).getDisplayname());
                                        int referenceid = -1;
                                        if (Parameters.symbol.get(id).getType().equals("OPT")) {
                                            referenceid = Utilities.getCashReferenceID(Parameters.symbol, id);
                                            String tempExpiry = Parameters.symbol.get(id).getExpiry();
                                            referenceid = this.optionPricingUsingFutures ? Utilities.getFutureIDFromBrokerSymbol(Parameters.symbol, referenceid, tempExpiry) : referenceid;
                                        }
                                        double limitprice = Utilities.getLimitPriceForOrder(Parameters.symbol, id, referenceid, derivedSide, getTickSize(), this.getOrdType(),0);
                                         if (getOrderAttributes().get("barrierlimitprice") != null && getOrderAttributes().get("barrierlimitprice").toString().equalsIgnoreCase("true")) {
                                            order.setBarrierLimitPrice(limitprice);
                                        }
                                        order.setLimitPrice(limitprice);
                                        order.setOrderSide(derivedSide);
                                        order.setOriginalOrderSize(size);
                                        order.setCurrentOrderSize(size);
                                        order.setOrderReason(EnumOrderReason.REGULARENTRY);
                                        order.setOrderStage(EnumOrderStage.INIT);
                                        order.setScale(getScaleEntry());
                                        order.setOrderLog("SHORT" + delimiter + tradetuple.get(1));
                                        HashMap<String, Object> tmpOrderAttributes = new HashMap<>();
                                        tmpOrderAttributes.putAll(this.getOrderAttributes());
                                        order.setOrderAttributes(tmpOrderAttributes);
                                        order.setStopLoss(stoploss);
                                        if ((this.getOrdType() != EnumOrderType.MKT && limitprice > 0) || this.getOrdType().equals(EnumOrderType.MKT)) {
                                            logger.log(Level.INFO, "501,Strategy SHORT,{0}", new Object[]{getStrategy() + delimiter + "SHORT" + delimiter + Parameters.symbol.get(id).getDisplayname()});
                                            orderid = entry(order);
                                        }
                                    }
                                }
                                break;
                            case COVER:
                                for (int id : exitorderidlist) {
                                    if (id >= 0) {
                                        order.setParentDisplayName(Parameters.symbol.get(id).getDisplayname());
                                        order.setChildDisplayName(Parameters.symbol.get(id).getDisplayname());
                                        int referenceid = -1;
                                        if (Parameters.symbol.get(id).getType().equals("OPT")) {
                                            referenceid = Utilities.getCashReferenceID(Parameters.symbol, id);
                                            String tempExpiry = Parameters.symbol.get(id).getExpiry();
                                            referenceid = this.optionPricingUsingFutures ? Utilities.getFutureIDFromBrokerSymbol(Parameters.symbol, referenceid, tempExpiry) : referenceid;
                                        }
                                        double limitprice = Utilities.getLimitPriceForOrder(Parameters.symbol, id, referenceid, derivedSide, getTickSize(), this.getOrdType(),0);
                                        if (getOrderAttributes().get("barrierlimitprice") != null && getOrderAttributes().get("barrierlimitprice").toString().equalsIgnoreCase("true")) {
                                            order.setBarrierLimitPrice(limitprice);
                                        }
                                        order.setLimitPrice(limitprice);
                                        order.setOrderSide(derivedSide);
                                        order.setOriginalOrderSize(size);
                                        order.setCurrentOrderSize(size);
                                        order.setOrderReason(EnumOrderReason.REGULAREXIT);
                                        order.setOrderStage(EnumOrderStage.INIT);
                                        order.setScale(getScaleExit());
                                        order.setOrderLog("SELL" + delimiter + tradetuple.get(1));
                                        HashMap<String, Object> tmpOrderAttributes = new HashMap<>();
                                        tmpOrderAttributes.putAll(this.getOrderAttributes());
                                        order.setOrderAttributes(tmpOrderAttributes);
                                        if ((this.getOrdType() != EnumOrderType.MKT && limitprice > 0) || this.getOrdType().equals(EnumOrderType.MKT)) {
                                            logger.log(Level.INFO, "501,Strategy COVER,{0}", new Object[]{getStrategy() + delimiter + "COVER" + delimiter + Parameters.symbol.get(id).getDisplayname()});
                                            exit(order);
                                        }
                                    }
                                }
                                break;
                            default:
                                break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, null, e);
        }
    }

        public void waitForJsonTrades() {
        try {
            List<String> tradetuple = this.getDb().blpop("jsontrades:" + this.getStrategy(), "", 60);
            if (tradetuple != null) {
                logger.log(Level.INFO, "101,Received Trade:{0} for strategy {1}", new Object[]{tradetuple.get(1), tradetuple.get(0)});
                //tradetuple as symbol:size:side:sl
                Thread t = new Thread(new Mail(getIamail(), "Received Trade: " + tradetuple.get(1) + " for strategy " + tradetuple.get(0), "Received Order from [R]"));
                t.start();
                String displayName = tradetuple.get(1);
                   Type type = new TypeToken<OrderBean>() {
                }.getType();
                Gson gson = new GsonBuilder().create();
                OrderBean ob = gson.fromJson((String) displayName, type);
                    int id=ob.getParentSymbolID();
                    if (ob.getOriginalOrderSize() > 0) {
                        switch (ob.getOrderSide()) {
                            case BUY:
                                    if (id>=0) {
                                        int referenceid = -1;
                                        if (Parameters.symbol.get(id).getType().equals("OPT")) {
                                            referenceid = Utilities.getCashReferenceID(Parameters.symbol, id);
                                            String tempExpiry = Parameters.symbol.get(id).getExpiry();
                                            referenceid = this.optionPricingUsingFutures ? Utilities.getFutureIDFromBrokerSymbol(Parameters.symbol, referenceid, tempExpiry) : referenceid;
                                        }
                                        ob.setOrderReason(EnumOrderReason.REGULARENTRY);
                                        ob.setOrderStage(EnumOrderStage.INIT);
                                        ob.setOrderLog("BUY" + delimiter + tradetuple.get(1));
                                        HashMap<String, Object> tmpOrderAttributes = new HashMap<>();
                                        tmpOrderAttributes.putAll(this.getOrderAttributes());
                                        ob.setOrderAttributes(tmpOrderAttributes);
                                        if ((this.getOrdType() != EnumOrderType.MKT && ob.getLimitPrice() > 0) || this.getOrdType().equals(EnumOrderType.MKT)) {
                                            logger.log(Level.INFO, "501,Strategy BUY,{0}", new Object[]{getStrategy() + delimiter + "BUY" + delimiter + Parameters.symbol.get(id).getDisplayname()});
                                            entry(ob);
                                        }
                                    }
                                
                                break;
                            case SELL:
                                    if (id >= 0) {
                                        int referenceid = -1;
                                        if (Parameters.symbol.get(id).getType().equals("OPT")) {
                                            referenceid = Utilities.getCashReferenceID(Parameters.symbol, id);
                                            String tempExpiry = Parameters.symbol.get(id).getExpiry();
                                            referenceid = this.optionPricingUsingFutures ? Utilities.getFutureIDFromBrokerSymbol(Parameters.symbol, referenceid, tempExpiry) : referenceid;
                                        }
                                        ob.setOrderReason(EnumOrderReason.REGULAREXIT);
                                        ob.setOrderStage(EnumOrderStage.INIT);
                                        ob.setOrderLog("SELL" + delimiter + tradetuple.get(1));
                                        HashMap<String, Object> tmpOrderAttributes = new HashMap<>();
                                        tmpOrderAttributes.putAll(this.getOrderAttributes());
                                        ob.setOrderAttributes(tmpOrderAttributes);
                                        if ((this.getOrdType() != EnumOrderType.MKT && ob.getLimitPrice() > 0) || this.getOrdType().equals(EnumOrderType.MKT)) {
                                            logger.log(Level.INFO, "501,Strategy SELL,{0}", new Object[]{getStrategy() + delimiter + "SELL" + delimiter + Parameters.symbol.get(id).getDisplayname()});
                                            exit(ob);
                                        }
                                    }
                                break;
                            case SHORT:
                                    if (id >= 0) {
                                        int referenceid = -1;
                                        if (Parameters.symbol.get(id).getType().equals("OPT")) {
                                            referenceid = Utilities.getCashReferenceID(Parameters.symbol, id);
                                            String tempExpiry = Parameters.symbol.get(id).getExpiry();
                                            referenceid = this.optionPricingUsingFutures ? Utilities.getFutureIDFromBrokerSymbol(Parameters.symbol, referenceid, tempExpiry) : referenceid;
                                        }
                                        ob.setOrderReason(EnumOrderReason.REGULARENTRY);
                                        ob.setOrderStage(EnumOrderStage.INIT);
                                        ob.setOrderLog("SHORT" + delimiter + tradetuple.get(1));
                                        HashMap<String, Object> tmpOrderAttributes = new HashMap<>();
                                        tmpOrderAttributes.putAll(this.getOrderAttributes());
                                        ob.setOrderAttributes(tmpOrderAttributes);
                                        if ((this.getOrdType() != EnumOrderType.MKT && ob.getLimitPrice() > 0) || this.getOrdType().equals(EnumOrderType.MKT)) {
                                            logger.log(Level.INFO, "501,Strategy SHORT,{0}", new Object[]{getStrategy() + delimiter + "SHORT" + delimiter + Parameters.symbol.get(id).getDisplayname()});
                                            entry(ob);
                                        }
                                    }
                                
                                break;
                            case COVER:
                                    if (id >= 0) {
                                        int referenceid = -1;
                                        if (Parameters.symbol.get(id).getType().equals("OPT")) {
                                            referenceid = Utilities.getCashReferenceID(Parameters.symbol, id);
                                            String tempExpiry = Parameters.symbol.get(id).getExpiry();
                                            referenceid = this.optionPricingUsingFutures ? Utilities.getFutureIDFromBrokerSymbol(Parameters.symbol, referenceid, tempExpiry) : referenceid;
                                        }
                                        ob.setOrderReason(EnumOrderReason.REGULAREXIT);
                                        ob.setOrderStage(EnumOrderStage.INIT);
                                        ob.setOrderLog("SELL" + delimiter + tradetuple.get(1));
                                        HashMap<String, Object> tmpOrderAttributes = new HashMap<>();
                                        tmpOrderAttributes.putAll(this.getOrderAttributes());
                                        ob.setOrderAttributes(tmpOrderAttributes);
                                        if ((this.getOrdType() != EnumOrderType.MKT && ob.getLimitPrice() > 0) || this.getOrdType().equals(EnumOrderType.MKT)) {
                                            logger.log(Level.INFO, "501,Strategy COVER,{0}", new Object[]{getStrategy() + delimiter + "COVER" + delimiter + Parameters.symbol.get(id).getDisplayname()});
                                            exit(ob);
                                        }
                                    }
                                
                                break;
                            default:
                                break;
                        }
                    }
                }
        } catch (Exception e) {
            logger.log(Level.SEVERE, null, e);
        }
    }

    
    /**
     * @return the scaleEntry
     */
    public Boolean getScaleEntry() {
        return scaleEntry;
    }

    /**
     * @return the scaleExit
     */
    public Boolean getScaleExit() {
        return scaleExit;
    }
}
