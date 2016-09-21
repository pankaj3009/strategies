/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.algorithms.manager;

import com.incurrency.framework.Algorithm;
import com.incurrency.framework.BeanConnection;
import com.incurrency.framework.EnumOrderReason;
import com.incurrency.framework.EnumOrderSide;
import com.incurrency.framework.EnumOrderStage;
import com.incurrency.framework.EnumOrderType;
import com.incurrency.framework.EnumStopMode;
import com.incurrency.framework.EnumStopType;
import com.incurrency.framework.MainAlgorithm;
import com.incurrency.framework.Parameters;
import com.incurrency.framework.Stop;
import com.incurrency.framework.Strategy;
import com.incurrency.framework.Trade;
import com.incurrency.framework.Utilities;
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
import org.rosuda.REngine.REXP;
import org.rosuda.REngine.Rserve.RConnection;

/**
 *
 * @author Pankaj
 */
public class Manager extends Strategy {

    public String expiryNearMonth;
    public String expiryFarMonth;
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

    private static final Logger logger = Logger.getLogger(Manager.class.getName());

    public Manager(MainAlgorithm m, Properties p, String parameterFile, ArrayList<String> accounts, Integer stratCount) {
        super(m, "manager", "FUT", p, parameterFile, accounts, stratCount);
        loadParameters(p);
        String[] tempStrategyArray = parameterFile.split("\\.")[0].split("-|_");
        for (BeanConnection c : Parameters.connection) {
            c.initializeConnection(tempStrategyArray[tempStrategyArray.length - 1], -1);
        }
        rollover = Utilities.rolloverDay(rolloverDays, this.getStartDate(), this.expiryNearMonth);
        if (rollover) {
            expiry = this.expiryFarMonth;
        } else {
            expiry = this.expiryNearMonth;
        }     

        Timer monitor=new Timer("Timer: "+this.getStrategy() +" WaitForTrades");
        monitor.schedule(TradeProcessor, new Date());
    }

    private void loadParameters(Properties p) {
        expiryNearMonth = p.getProperty("NearMonthExpiry").toString().trim();
        expiryFarMonth = p.getProperty("FarMonthExpiry").toString().trim();
        rServerIP = p.getProperty("RServerIP").toString().trim();
        securityType = p.getProperty("SecurityType", "PASSTHROUGH");
        optionPricingUsingFutures = Boolean.valueOf(p.getProperty("OptionPricingUsingFutures", "TRUE"));
        optionSystem = p.getProperty("OptionSystem", "PAY");
        String entryScanTime = p.getProperty("ScanStartTime");
        Calendar calToday = Calendar.getInstance(TimeZone.getTimeZone(Algorithm.timeZone));
        String[] entryTimeComponents = entryScanTime.split(":");
        calToday.set(Calendar.HOUR_OF_DAY, Utilities.getInt(entryTimeComponents[0], 15));
        calToday.set(Calendar.MINUTE, Utilities.getInt(entryTimeComponents[1], 20));
        calToday.set(Calendar.SECOND, Utilities.getInt(entryTimeComponents[2], 0));
        RScriptRunTime = calToday.getTime();
        if(this.getEndDate().compareTo(new Date())<0){
          calToday.add(Calendar.DATE, 1);
          RScriptRunTime=calToday.getTime();
        }
        rolloverDays = Integer.valueOf(p.getProperty("RolloverDays", "0"));
        RStrategyFile = p.getProperty("RStrategyFile", "");
        wd = p.getProperty("wd", "/home/psharma/Seafile/R");
        scaleEntry = Boolean.parseBoolean(p.getProperty("ScaleEntry", "FALSE"));
        scaleExit = Boolean.parseBoolean(p.getProperty("ScaleExit", "FALSE"));


    }
    

    public TimerTask TradeProcessor=new TimerTask(){
        @Override
        public void run() {
            while (true) {
                waitForTrades();
            }
        }
    };
        
    public void waitForTrades() {
        try {
            List<String> tradetuple = db.blpop("trades:" + this.getStrategy(), "", 60);
            if (tradetuple != null) {
                logger.log(Level.INFO, "Received Trade:{0} for strategy {1}", new Object[]{tradetuple.get(1), tradetuple.get(0)});
                //tradetuple as symbol:size:side:sl
                String displayName = tradetuple.get(1);
                String symbol = displayName.split(":", -1)[0];
                EnumOrderSide side = EnumOrderSide.valueOf(displayName.split(":",-1)[2]);
                EnumOrderSide derivedSide=side;
                int size = Integer.valueOf(displayName.split(":",-1)[1]);
                double stoploss = Double.valueOf(displayName.split(":",-1)[3]);
                int initPositionSize = Integer.valueOf(displayName.split(":",-1)[4]);
                int actualPositionSize = initPositionSize;
                int symbolid = Utilities.getIDFromDisplayName(Parameters.symbol, symbol);
                if (securityType.equals("OPT") || displayName.contains("OPT")|| symbolid >= 0) { //only proceed if symbolid exists in our db
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
                                actualPositionSize = Utilities.getNetPosition(Parameters.symbol, this.getPosition(), exitorderidlist.get(0), "OPT");
                            } else if (side.equals(EnumOrderSide.BUY) || side.equals(EnumOrderSide.SHORT)) {
                                int referenceid = Utilities.getCashReferenceID(Parameters.symbol, symbolid, getReferenceCashType());
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
                                    actualPositionSize = Utilities.getNetPosition(Parameters.symbol, this.getPosition(), entryorderidlist.get(0), "OPT");
                                } else {
                                    actualPositionSize = 0;
                                }
                            }
                        } else { //symbolid needs to be derived
                            int referenceid = Utilities.getCashReferenceID(Parameters.symbol, symbolid, getReferenceCashType());
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
                                    actualPositionSize = Utilities.getNetPosition(Parameters.symbol, this.getPosition(), exitorderidlist.get(0), "OPT");
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
                            actualPositionSize = Utilities.getNetPosition(Parameters.symbol, this.getPosition(), entryorderidlist.get(0), "OPT");
                            }
                            
                        }
                    }

                    if (securityType.equals("FUT")) {
                        if (displayName.contains("FUT")) { //passthrough
                            if (side.equals(EnumOrderSide.SELL) || side.equals(EnumOrderSide.COVER)) {
                                exitorderidlist.add(symbolid);
                                actualPositionSize = Utilities.getNetPosition(Parameters.symbol, this.getPosition(), exitorderidlist.get(0), "OPT");
                            } else if (side.equals(EnumOrderSide.BUY) || side.equals(EnumOrderSide.SHORT)) {
                                int referenceid = Utilities.getCashReferenceID(Parameters.symbol, symbolid, getReferenceCashType());
                                int futureid = Utilities.getFutureIDFromBrokerSymbol(Parameters.symbol, referenceid, expiry);
                                entryorderidlist.add(futureid);
                                actualPositionSize = Utilities.getNetPosition(Parameters.symbol, this.getPosition(), entryorderidlist.get(0), "OPT");
                            }
                        } else { //symbolid needs to be derived
                            int referenceid = Utilities.getCashReferenceID(Parameters.symbol, symbolid, getReferenceCashType());
                            if (side.equals(EnumOrderSide.SELL) || side.equals(EnumOrderSide.COVER)) {
                                int futureid = Utilities.getFutureIDFromBrokerSymbol(Parameters.symbol, referenceid, expiryNearMonth);
                                exitorderidlist.addAll(this.getFirstInternalOpenOrder(futureid, side, "Order"));
                                futureid = Utilities.getFutureIDFromBrokerSymbol(Parameters.symbol, referenceid, expiryFarMonth);
                                exitorderidlist.addAll(this.getFirstInternalOpenOrder(futureid, side, "Order"));
                                actualPositionSize = Utilities.getNetPosition(Parameters.symbol, this.getPosition(), exitorderidlist.get(0), "OPT");
                            } else if (side.equals(EnumOrderSide.BUY) || side.equals(EnumOrderSide.SHORT)) {
                                if (optionPricingUsingFutures) {
                                    int futureid = Utilities.getFutureIDFromBrokerSymbol(Parameters.symbol, referenceid, expiry);
                                    entryorderidlist.add(futureid);
                                }
                                actualPositionSize = Utilities.getNetPosition(Parameters.symbol, this.getPosition(), entryorderidlist.get(0), "OPT");
                            }
                        }
                    }

                    if (securityType.equals("PASSTHROUGH")) {
                        if (side.equals(EnumOrderSide.SELL) || side.equals(EnumOrderSide.COVER)) {
                            exitorderidlist.add(symbolid);
                            actualPositionSize = Utilities.getNetPosition(Parameters.symbol, this.getPosition(), exitorderidlist.get(0), "OPT");
                        } else if (side.equals(EnumOrderSide.BUY) || side.equals(EnumOrderSide.SHORT)) {
                            entryorderidlist.add(symbolid);
                            actualPositionSize = Utilities.getNetPosition(Parameters.symbol, this.getPosition(), entryorderidlist.get(0), "OPT");
                        }
                    }

                    stoploss = Utilities.round(stoploss, getTickSize(), 2);
                    HashMap<String, Object> order = new HashMap<>();

                    if (entryorderidlist.size() > 0) {
                        for (int i : entryorderidlist) {
                            this.initSymbol(i,optionPricingUsingFutures,getReferenceCashType());
                        }
                    }
                    if (exitorderidlist.size() > 0) {
                        for (int i : exitorderidlist) {
                            this.initSymbol(i,optionPricingUsingFutures,getReferenceCashType());
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
                    int compensation = initPositionSize - actualPositionSize;
                    size = (derivedSide == EnumOrderSide.BUY || derivedSide == EnumOrderSide.COVER) ? size + compensation : Math.abs(-size + compensation);
                    /*
                     * IF initpositionsize = 100, actual positionsize=0, we get a buy of 100. comp=100, size=200
                     * IF initpositionsize=0, actualpositionsize=100, we get buy of 100, comp=-100, size=0, probably a duplicate trade
                     * IF initpositionsize=-100,actualpositionsize=0, we get a short of 100,should be short, but are not, comp=-100,size=abs(-100-100)=200
                     * IF initpositionsize=200, actualpositionsize=100, we set a SELL of 200, comp=100, size=abs(-200+100)=100
                     */
                    if (size > 0) {
                        order.put("type", getOrdType());
                        order.put("expiretime", getMaxOrderDuration());
                        order.put("dynamicorderduration", getDynamicOrderDuration());
                        order.put("maxslippage", this.getMaxSlippageEntry());
                        int orderid;
                        ArrayList<Stop> stops = new ArrayList<>();
                        Stop stp = new Stop();
                        switch (derivedSide) {
                            case BUY:
                                for (int id : entryorderidlist) {
                                    if (id >= 0) {
                                        order.put("id", id);
                                        int referenceid = -1;
                                        if (Parameters.symbol.get(id).getType().equals("OPT")) {
                                            referenceid = Utilities.getCashReferenceID(Parameters.symbol, id, getReferenceCashType());
                                            String tempExpiry = Parameters.symbol.get(id).getExpiry();
                                            referenceid = this.optionPricingUsingFutures ? Utilities.getFutureIDFromBrokerSymbol(Parameters.symbol, referenceid, tempExpiry) : referenceid;
                                        }
                                        double limitprice = Utilities.getLimitPriceForOrder(Parameters.symbol, id, referenceid, derivedSide, getTickSize(), this.getOrdType());
                                        order.put("limitprice", limitprice);
                                        order.put("side", derivedSide);
                                        order.put("size", size);
                                        order.put("reason", EnumOrderReason.REGULARENTRY);
                                        order.put("orderstage", EnumOrderStage.INIT);
                                        order.put("scale", getScaleEntry());
                                        order.put("dynamicorderduration", this.getDynamicOrderDuration());
                                        order.put("expiretime", 0);
                                        order.put("disclosedsize", Parameters.symbol.get(id).getMinsize());
                                        order.put("log", "BUY" + delimiter + tradetuple.get(1));
                                        HashMap<String,Object>tmpOrderAttributes=new HashMap<>();
                                        tmpOrderAttributes.putAll(this.getOrderAttributes());
                                        order.put ("orderattributes",tmpOrderAttributes);
                                        if ((this.getOrdType() != EnumOrderType.MKT && limitprice > 0) || this.getOrdType().equals(EnumOrderType.MKT)) {
                                            logger.log(Level.INFO, "501,Strategy BUY,{0}", new Object[]{getStrategy() + delimiter + "BUY" + delimiter + Parameters.symbol.get(id).getDisplayname()});
                                            orderid = entry(order);
                                            stp.stopValue = stoploss;
                                            stp.underlyingEntry = Parameters.symbol.get(symbolid).getLastPrice();
                                            stp.stopType = EnumStopType.STOPLOSS;
                                            stp.stopMode = EnumStopMode.POINT;
                                            stp.recalculate = true;
                                            stops.add(stp);
                                            Trade.setStop(db, this.getStrategy() + ":" + orderid + ":" + "Order", "opentrades", stops);
                                        }
                                    }
                                }
                                break;
                            case SELL:
                                for (int id : exitorderidlist) {
                                    if (id >= 0) {
                                        order.put("id", id);
                                        int referenceid = -1;
                                        if (Parameters.symbol.get(id).getType().equals("OPT")) {
                                            referenceid = Utilities.getCashReferenceID(Parameters.symbol, id, getReferenceCashType());
                                            String tempExpiry = Parameters.symbol.get(id).getExpiry();
                                            referenceid = this.optionPricingUsingFutures ? Utilities.getFutureIDFromBrokerSymbol(Parameters.symbol, referenceid, tempExpiry) : referenceid;
                                        }
                                        double limitprice = Utilities.getLimitPriceForOrder(Parameters.symbol, id, referenceid, derivedSide, getTickSize(), this.getOrdType());
                                        order.put("limitprice", limitprice);
                                        order.put("side", derivedSide);
                                        order.put("size", size);
                                        order.put("reason", EnumOrderReason.REGULAREXIT);
                                        order.put("orderstage", EnumOrderStage.INIT);
                                        order.put("scale", getScaleExit());
                                        order.put("dynamicorderduration", this.getDynamicOrderDuration());
                                        order.put("expiretime", 0);
                                        order.put("log", "SELL" + delimiter + tradetuple.get(1));
                                        HashMap<String,Object>tmpOrderAttributes=new HashMap<>();
                                        tmpOrderAttributes.putAll(this.getOrderAttributes());
                                        order.put ("orderattributes",tmpOrderAttributes);                                      if ((this.getOrdType() != EnumOrderType.MKT && limitprice > 0) || this.getOrdType().equals(EnumOrderType.MKT)) {
                                            logger.log(Level.INFO, "501,Strategy SELL,{0}", new Object[]{getStrategy() + delimiter + "SELL" + delimiter + Parameters.symbol.get(id).getDisplayname()});
                                            exit(order);
                                        }
                                    }
                                }
                                break;
                            case SHORT:
                                for (int id : entryorderidlist) {
                                    if (id >= 0) {
                                        order.put("id", id);
                                        int referenceid = -1;
                                        if (Parameters.symbol.get(id).getType().equals("OPT")) {
                                            referenceid = Utilities.getCashReferenceID(Parameters.symbol, id, getReferenceCashType());
                                            String tempExpiry = Parameters.symbol.get(id).getExpiry();
                                            referenceid = this.optionPricingUsingFutures ? Utilities.getFutureIDFromBrokerSymbol(Parameters.symbol, referenceid, tempExpiry) : referenceid;
                                        }
                                        double limitprice = Utilities.getLimitPriceForOrder(Parameters.symbol, id, referenceid, derivedSide, getTickSize(), this.getOrdType());
                                        order.put("limitprice", limitprice);
                                        order.put("side", derivedSide);
                                        order.put("size", size);
                                        order.put("reason", EnumOrderReason.REGULARENTRY);
                                        order.put("scale", getScaleEntry());
                                        order.put("orderstage", EnumOrderStage.INIT);
                                        order.put("dynamicorderduration", this.getDynamicOrderDuration());
                                        order.put("expiretime", 0);
                                        order.put("log", "SHORT" + delimiter + tradetuple.get(1));
                                        HashMap<String,Object>tmpOrderAttributes=new HashMap<>();
                                        tmpOrderAttributes.putAll(this.getOrderAttributes());
                                        order.put ("orderattributes",tmpOrderAttributes);                                      if ((this.getOrdType() != EnumOrderType.MKT && limitprice > 0) || this.getOrdType().equals(EnumOrderType.MKT)) {
                                            logger.log(Level.INFO, "501,Strategy SHORT,{0}", new Object[]{getStrategy() + delimiter + "SHORT" + delimiter + Parameters.symbol.get(id).getDisplayname()});
                                            orderid = entry(order);
                                            Trade.setStop(db, this.getStrategy() + ":" + orderid + ":" + "Order", "opentrades", stops);
                                            stp.stopValue = stoploss;
                                            stp.underlyingEntry = Parameters.symbol.get(symbolid).getLastPrice();
                                            stp.stopType = EnumStopType.STOPLOSS;
                                            stp.stopMode = EnumStopMode.POINT;
                                            stp.recalculate = true;
                                            stops.add(stp);
                                            Trade.setStop(db, this.getStrategy() + ":" + orderid + ":" + "Order", "opentrades", stops);
                                        }
                                    }
                                }
                                break;
                            case COVER:
                                for (int id : exitorderidlist) {
                                    if (id >= 0) {
                                        order.put("id", id);
                                        int referenceid = -1;
                                        if (Parameters.symbol.get(id).getType().equals("OPT")) {
                                            referenceid = Utilities.getCashReferenceID(Parameters.symbol, id, getReferenceCashType());
                                            String tempExpiry = Parameters.symbol.get(id).getExpiry();
                                            referenceid = this.optionPricingUsingFutures ? Utilities.getFutureIDFromBrokerSymbol(Parameters.symbol, referenceid, tempExpiry) : referenceid;
                                        }
                                        double limitprice = Utilities.getLimitPriceForOrder(Parameters.symbol, id, referenceid, derivedSide, getTickSize(), this.getOrdType());
                                        order.put("limitprice", limitprice);
                                        order.put("side", derivedSide);
                                        order.put("size", size);
                                        order.put("reason", EnumOrderReason.REGULAREXIT);
                                        order.put("scale", getScaleExit());
                                        order.put("orderstage", EnumOrderStage.INIT);
                                        order.put("dynamicorderduration", this.getDynamicOrderDuration());
                                        order.put("expiretime", 0);
                                        order.put("log", "COVER" + delimiter + tradetuple.get(1));
                                        HashMap<String,Object>tmpOrderAttributes=new HashMap<>();
                                        tmpOrderAttributes.putAll(this.getOrderAttributes());
                                        order.put ("orderattributes",tmpOrderAttributes);                                     if ((this.getOrdType() != EnumOrderType.MKT && limitprice > 0) || this.getOrdType().equals(EnumOrderType.MKT)) {
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
