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
import java.text.SimpleDateFormat;
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
    String referenceCashType;
    String rServerIP;
    private EnumOrderType ordType;
    Date monitoringStart;
    Boolean rollover;
    int rolloverDays;
    String expiry;
    String RStrategyFile;
    String wd;
    private Boolean scaleEntry = Boolean.FALSE;
    private Boolean scaleExit = Boolean.FALSE;
    private static final Logger logger = Logger.getLogger(Manager.class.getName());

    public Manager(MainAlgorithm m, Properties p, String parameterFile, ArrayList<String> accounts, Integer stratCount) {
        super(m, "manager", "FUT", p, parameterFile, accounts, stratCount);
        loadParameters(p);
        String[] tempStrategyArray = parameterFile.split("\\.")[0].split("-|_");
        for (BeanConnection c : Parameters.connection) {
            c.initializeConnection(tempStrategyArray[tempStrategyArray.length - 1], -1);
        }
        rollover = rolloverDay(rolloverDays);
        if (rollover) {
            expiry = this.expiryFarMonth;
        } else {
            expiry = this.expiryNearMonth;
        }
        Timer monitor = new Timer("Timer: " + this.getStrategy() + " TradeScanner");
        monitor.schedule(tradeScannerTask, monitoringStart);
    }

    private void loadParameters(Properties p) {
        expiryNearMonth = p.getProperty("NearMonthExpiry").toString().trim();
        expiryFarMonth = p.getProperty("FarMonthExpiry").toString().trim();
        referenceCashType = p.getProperty("ReferenceCashType", "STK").toString().trim();
        rServerIP = p.getProperty("RServerIP").toString().trim();
        ordType = EnumOrderType.valueOf(p.getProperty("OrderType", "LMT"));
        String entryScanTime = p.getProperty("ScanStartTime");
        Calendar calToday = Calendar.getInstance(TimeZone.getTimeZone(Algorithm.timeZone));
        String[] entryTimeComponents = entryScanTime.split(":");
        calToday.set(Calendar.HOUR_OF_DAY, Utilities.getInt(entryTimeComponents[0], 15));
        calToday.set(Calendar.MINUTE, Utilities.getInt(entryTimeComponents[1], 20));
        calToday.set(Calendar.SECOND, Utilities.getInt(entryTimeComponents[2], 0));
        monitoringStart = calToday.getTime();
        rolloverDays = Integer.valueOf(p.getProperty("RolloverDays", "0"));
        RStrategyFile = p.getProperty("RStrategyFile", "");
        wd = p.getProperty("wd", "/home/psharma/Seafile/R");
        scaleEntry = Boolean.parseBoolean(p.getProperty("ScaleEntry", "FALSE"));
        scaleExit = Boolean.parseBoolean(p.getProperty("ScaleExit", "FALSE"));


    }

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
    TimerTask tradeScannerTask = new TimerTask() {
        @Override
        public void run() {
            if (!RStrategyFile.equals("")) {
                logger.log(Level.INFO, "501,Scan,{0}", new Object[]{getStrategy()});
                RConnection c = null;
                try {
                    c = new RConnection(rServerIP);
                    c.eval("setwd(\"" + wd + "\")");
                    REXP wd = c.eval("getwd()");
                    System.out.println(wd.asString());
                    c.eval("options(encoding = \"UTF-8\")");
                    c.eval("source(\"" + RStrategyFile + "\")");
                } catch (Exception e) {
                    logger.log(Level.SEVERE, null, e);
                }

            }
            while (true) {
                waitForTrades();
            }
        }
    };

    void waitForTrades() {
        try {
            List<String> tradetuple = db.blpop("trades:" + this.getStrategy(), "", 60);
            if (tradetuple != null) {
                logger.log(Level.INFO, "Received Trade:{0} for strategy {1}", new Object[]{tradetuple.get(1), tradetuple.get(0)});
                //tradetuple as symbol:size:side:sl
                String symbol = tradetuple.get(1).split(":")[0];
                int symbolid = Utilities.getIDFromDisplayName(Parameters.symbol, symbol);
                int futureid = Utilities.getFutureIDFromExchangeSymbol(Parameters.symbol, symbolid, expiry);
                int nearfutureid = futureid;
                int size = Integer.valueOf(tradetuple.get(1).split(":")[1]);
                EnumOrderSide side = EnumOrderSide.valueOf(tradetuple.get(1).split(":")[2]);

                double sl = Double.valueOf(tradetuple.get(1).split(":")[3]);
                sl = Utilities.round(sl, getTickSize(), 2);
                HashMap<String, Object> order = new HashMap<>();
                ArrayList<Integer> orderidlist = new ArrayList<>();
                ArrayList<Integer> nearorderidlist = new ArrayList<>();
                if (tradetuple.get(1).contains("_OPT")) {
                    int newid = Utilities.getIDFromDisplayName(Parameters.symbol, tradetuple.get(1).split(":")[0]);
                    orderidlist.add(newid);

                } else {
                    orderidlist = Utilities.getOrInsertOptionIDForLongSystem(Parameters.symbol, this.getPosition(), futureid, side, expiry);
                }
                nearorderidlist = orderidlist;
                if (rollover) {
                    if (tradetuple.get(1).contains("_OPT")) {
                        int newid = Utilities.getIDFromDisplayName(Parameters.symbol, tradetuple.get(1).split(":")[0]);
                        orderidlist.add(newid);

                    } else {
                        nearorderidlist = Utilities.getOrInsertOptionIDForLongSystem(Parameters.symbol, this.getPosition(), symbolid, side, this.expiryNearMonth);
                    }
                }
                if (rollover) {
                    nearfutureid = Utilities.getFutureIDFromBrokerSymbol(Parameters.symbol, symbolid, this.expiryNearMonth);
                }
                if (orderidlist.size() > 0) {
                    //orderidlist.size can be zero if we get a SELL or COVER order, without an option position.
                    //In this case, no symbols will be added and an empty orderidlist will be returned.
                    for (int i : orderidlist) {
                        this.initSymbol(i);
                    }
                    for (int i : nearorderidlist) {
                        this.initSymbol(i);
                    }
                    int initPositionSize = Integer.valueOf(tradetuple.get(1).split(":")[4]);
                    int actualPositionSize = Utilities.getNetPositionFromOptions(Parameters.symbol, this.getPosition(), orderidlist.get(0));
//            int actualPositionSize = this.getPosition().get(id) == null ? 0 : this.getPosition().get(id).getPosition();
                    int compensation = initPositionSize - actualPositionSize;
                    size = (side == EnumOrderSide.BUY || side == EnumOrderSide.COVER) ? size + compensation : Math.abs(-size + compensation);
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
                        switch (side) {
                            case BUY:
                                for (int id : orderidlist) {
                                    if (id >= 0) {
                                        order.put("id", id);
                                        double limitprice = Utilities.getOptionLimitPriceForRel(Parameters.symbol, id, futureid, EnumOrderSide.BUY, "CALL", getTickSize());
                                        order.put("limitprice", limitprice);
                                        order.put("side", EnumOrderSide.BUY);
                                        order.put("size", size);
                                        order.put("reason", EnumOrderReason.REGULARENTRY);
                                        order.put("orderstage", EnumOrderStage.INIT);
                                        order.put("scale", getScaleEntry());
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
                                        double limitprice = Utilities.getOptionLimitPriceForRel(Parameters.symbol, nearid, nearfutureid, EnumOrderSide.SELL, "CALL", getTickSize());
                                        order.put("limitprice", limitprice);
                                        order.put("side", EnumOrderSide.SELL);
                                        order.put("size", size);
                                        order.put("reason", EnumOrderReason.REGULAREXIT);
                                        order.put("orderstage", EnumOrderStage.INIT);
                                        order.put("scale", getScaleExit());
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
                                        double limitprice = Utilities.getOptionLimitPriceForRel(Parameters.symbol, id, futureid, EnumOrderSide.BUY, "PUT", getTickSize());
                                        order.put("limitprice", limitprice);
                                        order.put("side", EnumOrderSide.BUY);
                                        order.put("size", size);
                                        order.put("reason", EnumOrderReason.REGULARENTRY);
                                        order.put("scale", getScaleEntry());
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
                                        double limitprice = Utilities.getOptionLimitPriceForRel(Parameters.symbol, nearid, nearfutureid, EnumOrderSide.SELL, "PUT", getTickSize());
                                        order.put("limitprice", limitprice);
                                        order.put("side", EnumOrderSide.SELL);
                                        order.put("size", size);
                                        order.put("reason", EnumOrderReason.REGULAREXIT);
                                        order.put("scale", getScaleExit());
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
                }


            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, null, e);
        }
    }

    /**
     * @return the ordType
     */
    public EnumOrderType getOrdType() {
        return ordType;
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
