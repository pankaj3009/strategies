/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.algorithms.manager;

import com.incurrency.framework.Algorithm;
import com.incurrency.framework.BeanConnection;
import com.incurrency.framework.BeanPosition;
import com.incurrency.framework.EnumOrderReason;
import com.incurrency.framework.EnumOrderSide;
import com.incurrency.framework.EnumOrderStage;
import com.incurrency.framework.EnumOrderType;
import com.incurrency.framework.EnumStopMode;
import com.incurrency.framework.EnumStopType;
import com.incurrency.framework.Index;
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

/**
 *
 * @author Pankaj
 */
public class Manager extends Strategy {

    String expiryNearMonth;
    String expiryFarMonth;
    String referenceCashType;
    String rServerIP;
    EnumOrderType ordType;
    Date monitoringStart;
    Boolean rollover;
    int rolloverDays;
    String expiry;
    private static final Logger logger = Logger.getLogger(Manager.class.getName());

    public Manager(MainAlgorithm m, Properties p, String parameterFile, ArrayList<String> accounts, Integer stratCount) {
        super(m, "swing", "FUT", p, parameterFile, accounts, stratCount);
        loadParameters(p);
        String[] tempStrategyArray = parameterFile.split("\\.")[0].split("-|_");
        for (BeanConnection c : Parameters.connection) {
            c.initializeConnection(tempStrategyArray[tempStrategyArray.length - 1],-1);
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
            while (true) {
                waitForTrades();
            }
        }
    };

    void waitForTrades() {
        List<String> tradetuple = db.blpop("trades:" + this.getStrategy(), "", 60);
        if (tradetuple != null) {
            logger.log(Level.INFO, "Received Trade:{0} for strategy {1}", new Object[]{tradetuple.get(1), tradetuple.get(0)});
            //tradetuple as symbol:size:side:sl
            String symbol = tradetuple.get(1).split(":")[0];
            int symbolid = Utilities.getIDFromDisplayName(Parameters.symbol, symbol);
            int futureid=Utilities.getFutureIDFromSymbol(Parameters.symbol, symbolid, expiry);
            int id = -1, nearid = -1;
            int size = Integer.valueOf(tradetuple.get(1).split(":")[1]);
            EnumOrderSide side = EnumOrderSide.valueOf(tradetuple.get(1).split(":")[2]);
            double sl = Double.valueOf(tradetuple.get(1).split(":")[3]);
            sl = Utilities.round(sl, getTickSize(), 2);
            HashMap<String, Object> order = new HashMap<>();
            id = Utilities.getOptionIDForLongSystem(Parameters.symbol, this.getPosition(), futureid, side, expiry);
            nearid = id;
            if(Parameters.symbol.get(id).isAddedToSymbols()){
                //do housekeeping
                //1. ensure it exists in positions for strategy and oms
                if(!this.getStrategySymbols().contains(Integer.valueOf(id))){
                   this.getStrategySymbols().add(id);
                    this.getPosition().put(id, new BeanPosition(id, getStrategy()));
                    Index ind=new Index(this.getStrategy(),id);
                    //request market data
                    Parameters.connection.get(0).getWrapper().getMktData(Parameters.symbol.get(id), false);
                }                
            }
            if (rollover) {
                nearid = Utilities.getOptionIDForLongSystem(Parameters.symbol, this.getPosition(), symbolid, side, this.expiryNearMonth);
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
                    order.put("id", id);
                    double limitprice = this.getOptionLimitPriceForRel(id, symbolid, EnumOrderSide.BUY, "CALL");
                    order.put("limitprice", limitprice);
                    order.put("side", EnumOrderSide.BUY);
                    order.put("size", size);
                    order.put("reason", EnumOrderReason.REGULARENTRY);
                    order.put("orderstage", EnumOrderStage.INIT);
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
                    break;
                case SELL:
                    order.put("id", nearid);
                    limitprice = this.getOptionLimitPriceForRel(nearid, symbolid, EnumOrderSide.SELL, "CALL");
                    order.put("limitprice", limitprice);
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
                    limitprice = this.getOptionLimitPriceForRel(id, symbolid, EnumOrderSide.BUY, "PUT");
                    order.put("limitprice", limitprice);
                    order.put("side", EnumOrderSide.BUY);
                    order.put("size", size);
                    order.put("reason", EnumOrderReason.REGULARENTRY);
                    order.put("orderstage", EnumOrderStage.INIT);
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
                    break;
                case COVER:
                    order.put("id", nearid);
                    limitprice = this.getOptionLimitPriceForRel(nearid, symbolid, EnumOrderSide.SELL, "PUT");
                    order.put("limitprice", limitprice);
                    order.put("side", EnumOrderSide.SELL);
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

    double getOptionLimitPriceForRel(int id, int underlyingid, EnumOrderSide side, String right) {
        double price = Parameters.symbol.get(id).getLastPrice();
        if (price == 0) {
            double underlyingprice = Parameters.symbol.get(underlyingid).getLastPrice();
            double underlyingpriorclose = Parameters.symbol.get(underlyingid).getClosePrice();
            double underlyingchange = underlyingprice - underlyingpriorclose;//+ve if up
            double optionlastprice = Parameters.symbol.get(id).getClosePrice();
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
                    price = bidprice;

                } else if (askprice > 0) {
                    price = 0.80 * askprice;
                } else {
                    price = 0.80 * price;
                }
                break;
            case SHORT:
            case SELL:
                if (askprice > 0) {
                    price = askprice;

                } else if (bidprice > 0) {
                    price = 1.2 * askprice;
                } else {
                    price = 1.2 * price;
                }
                break;
            default:
                break;

        }
        price = Utilities.roundTo(price, this.getTickSize());
        return price;
    }
}
