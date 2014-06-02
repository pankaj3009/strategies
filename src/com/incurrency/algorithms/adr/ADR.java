/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.algorithms.adr;

import com.RatesClient.Subscribe;
import com.espertech.esper.client.EventBean;
import com.espertech.esper.client.UpdateListener;
import com.incurrency.framework.MainAlgorithm;
import com.incurrency.framework.BeanConnection;
import com.incurrency.framework.BeanSymbol;
import com.incurrency.framework.EnumOrderSide;
import com.incurrency.framework.Parameters;
import com.incurrency.framework.Strategy;
import com.incurrency.framework.TradeEvent;
import com.incurrency.framework.TradeListener;
import com.incurrency.framework.TradingUtil;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Generates ADR, Tick , TRIN 
 *
 * @author pankaj
 */
public class ADR extends Strategy implements TradeListener, UpdateListener {

    public EventProcessor mEsperEvtProcessor = null;
    private static final Logger logger = Logger.getLogger(ADR.class.getName());

    //----- updated by ADRListener and TickListener
    public double adr;
    public double adrTRIN;
    public double tick;
    public double tickTRIN;
    public double adrDayHigh = Double.MIN_VALUE;
    public double adrDayLow = Double.MAX_VALUE;

    //----- updated by method updateListener    
    double adrHigh;
    double adrLow;
    public double adrAvg;
    double adrTRINHigh;
    double adrTRINLow;
    public double adrTRINAvg;
    double tickHigh;
    double tickLow;
    double tickAvg;
    double tickTRINHigh;
    double tickTRINLow;
    double tickTRINAvg;
    double indexHigh;
    double indexLow;
    double indexAvg;
    double indexDayHigh = Double.MIN_VALUE;
    double indexDayLow = Double.MAX_VALUE;
    int tradingSide = 0;
    private Boolean trading;
    private String index;
    private String type;
    private String expiry;
    public int threshold;
    private double stopLoss;
    String window;
    private double windowHurdle;
    private double dayHurdle;
    private double takeProfit;
    private boolean scalpingMode;
    private double reentryMinimumMove;
    private double entryPrice;
    private double lastLongExit;
    private double lastShortExit;
    private String adrRuleName;
    HashMap<Integer, Boolean> closePriceReceived = new HashMap<>();

    public ADR(MainAlgorithm m, String parameterFile, ArrayList<String> accounts) {
        super(m, "adr", "FUT", parameterFile, accounts);
        loadParameters("adr", parameterFile);
        getStrategySymbols().clear();
        for (BeanSymbol s : Parameters.symbol) {
            if (Pattern.compile(Pattern.quote(adrRuleName), Pattern.CASE_INSENSITIVE).matcher(s.getStrategy()).find()) {
                getStrategySymbols().add(s.getSerialno() - 1);
                getPosition().put(s.getSerialno() - 1, 0);
                closePriceReceived.put(s.getSerialno() - 1, Boolean.FALSE);
            }
            if (Pattern.compile(Pattern.quote("adr"), Pattern.CASE_INSENSITIVE).matcher(s.getStrategy()).find() && s.getType().equals("FUT")) {
                getStrategySymbols().add(s.getSerialno() - 1);
                getPosition().put(s.getSerialno() - 1, 0);
            }
        }
        TradingUtil.writeToFile(getStrategy() + ".csv", "buyZone,ShortZone,TradingSide,adr,adrHigh,adrLow,adrDayHigh,adrDayLow,adrAvg,BuyZone1,ShortZone1,index,indexHigh,indexLow,indexDayHigh,indexDayLow,indexAvg,BuyZone2,ShortZone2,adrTRIN,adrTRINAvg,BuyZone3,ShortZone3,tick,tickTRIN,adrTRINHigh,adrTRINLow,Comment");

        mEsperEvtProcessor = new EventProcessor(this);
        mEsperEvtProcessor.ADRStatement.addListener(this);
        String[] tempStrategyArray = parameterFile.split("\\.")[0].split("-");

        for (BeanConnection c : Parameters.connection) {
            c.getWrapper().addTradeListener(this);
            c.initializeConnection(tempStrategyArray[tempStrategyArray.length - 1]);
        }
        if (Subscribe.tes != null) {
            Subscribe.tes.addTradeListener(this);
        }
    }

    private void loadParameters(String strategy, String parameterFile) {
        Properties p = new Properties(System.getProperties());
        FileInputStream propFile;
        try {
            propFile = new FileInputStream(parameterFile);
            try {
                p.load(propFile);
            } catch (Exception ex) {
                logger.log(Level.SEVERE, null, ex);
            }
        } catch (Exception ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        System.setProperties(p);
        trading = Boolean.valueOf(System.getProperty("Trading"));
        index = System.getProperty("Index");
        type = System.getProperty("Type");
        expiry = System.getProperty("Expiry") == null ? "" : System.getProperty("Expiry");
        threshold = Integer.parseInt(System.getProperty("Threshold"));
        stopLoss = Double.parseDouble(System.getProperty("StopLoss"));
        window = System.getProperty("Window");
        windowHurdle = Double.parseDouble(System.getProperty("WindowHurdle"));
        dayHurdle = Double.parseDouble(System.getProperty("DayHurdle"));
        takeProfit = Double.parseDouble(System.getProperty("TakeProfit"));
        scalpingMode = System.getProperty("ScalpingMode") == null ? false : Boolean.parseBoolean(System.getProperty("ScalpingMode"));
        reentryMinimumMove = System.getProperty("ReentryMinimumMove") == null ? 0D : Double.parseDouble(System.getProperty("ReentryMinimumMove"));
        reentryMinimumMove = System.getProperty("ReentryMinimumMove") == null ? 0D : Double.parseDouble(System.getProperty("ReentryMinimumMove"));
        adrRuleName = System.getProperty("ADRSymbolTag") == null ? "" : System.getProperty("ADRSymbolTag");

        String concatAccountNames = "";
        for (String account : getAccounts()) {
            concatAccountNames = ":" + account;
        }

        logger.log(Level.INFO, "-----{0} Parameters----Accounts used {1} ----- Parameter File {2}", new Object[]{strategy.toUpperCase(), concatAccountNames, parameterFile});
        logger.log(Level.INFO, "Setup to Trade: {0}", trading);
        logger.log(Level.INFO, "Traded Index: {0}", index);
        logger.log(Level.INFO, "Index Type: {0}", type);
        logger.log(Level.INFO, "Index Contract Expiry: {0}", expiry);
        logger.log(Level.INFO, "Number of quotes before data collection: {0}", threshold);
        logger.log(Level.INFO, "Stop Loss: {0}", stopLoss);
        logger.log(Level.INFO, "Take Profit: {0}", takeProfit);
        logger.log(Level.INFO, "Sliding Window Duration: {0}", window);
        logger.log(Level.INFO, "Hurdle Index move needed for window duration: {0}", windowHurdle);
        logger.log(Level.INFO, "Hurdle Index move needed for day: {0}", dayHurdle);
        logger.log(Level.INFO, "Scalping Mode: {0}", scalpingMode);
        logger.log(Level.INFO, "Minimum move before re-entry: {0}", reentryMinimumMove);
        logger.log(Level.INFO, "Symbol tag picked for ADR Calculation: {0}", adrRuleName);
    }

    @Override
    public void tradeReceived(TradeEvent event) {
        int id = event.getSymbolID(); //zero based id
        if (getStrategySymbols().contains(id) && Parameters.symbol.get(id).getType().compareTo("STK") == 0) {
            switch (event.getTickType()) {
                case com.ib.client.TickType.LAST_SIZE:
                    //System.out.println("LASTSIZE, Symbol:"+Parameters.symbol.get(id).getSymbol()+" Value: "+Parameters.symbol.get(id).getLastSize()+" tickerID: "+id);
                    mEsperEvtProcessor.sendEvent(new TickPriceEvent(id, com.ib.client.TickType.LAST_SIZE, Parameters.symbol.get(id).getLastSize()));
                    //mEsperEvtProcessor.debugFireTickQuery();
                    break;
                case com.ib.client.TickType.VOLUME:
                    //System.out.println("VOLUME, Symbol:"+Parameters.symbol.get(id).getSymbol()+" Value: "+Parameters.symbol.get(id).getVolume()+" tickerID: "+id);
                    mEsperEvtProcessor.sendEvent(new TickPriceEvent(id, com.ib.client.TickType.VOLUME, Parameters.symbol.get(id).getVolume()));
                    //mEsperEvtProcessor.debugFireADRQuery();
                    break;
                case com.ib.client.TickType.LAST:
                    mEsperEvtProcessor.sendEvent(new TickPriceEvent(id, com.ib.client.TickType.LAST, Parameters.symbol.get(id).getLastPrice()));
                    if (Parameters.symbol.get(id).getClosePrice() > 0 && !this.closePriceReceived.get(id)) {
                        mEsperEvtProcessor.sendEvent(new TickPriceEvent(id, com.ib.client.TickType.CLOSE, Parameters.symbol.get(id).getClosePrice()));
                        this.closePriceReceived.put(id, Boolean.TRUE);
                    }
                    //mEsperEvtProcessor.debugFireTickQuery();
                    //mEsperEvtProcessor.debugFireADRQuery();
                    break;
                case com.ib.client.TickType.CLOSE:
                    mEsperEvtProcessor.sendEvent(new TickPriceEvent(id, com.ib.client.TickType.CLOSE, Parameters.symbol.get(id).getClosePrice()));
                    //mEsperEvtProcessor.debugFireADRQuery();
                    break;
                default:
                    break;
            }
        }
        String symbolexpiry = Parameters.symbol.get(id).getExpiry() == null ? "" : Parameters.symbol.get(id).getExpiry();
        if (trading && Parameters.symbol.get(id).getSymbol().equals(index) && Parameters.symbol.get(id).getType().equals(type) && symbolexpiry.equals(expiry) && event.getTickType() == com.ib.client.TickType.LAST) {
            double price = Parameters.symbol.get(id).getLastPrice();
            if (adr > 0) { //calculate high low only after minimum ticks have been received.
                mEsperEvtProcessor.sendEvent(new ADREvent(ADRTickType.INDEX, price));
                if (price > indexDayHigh) {
                    indexDayHigh = price;
                } else if (price < indexDayLow) {
                    indexDayLow = price;
                }
            }
//            boolean buyZone1 = ((adrHigh - adrLow > 5 && adr > adrLow + 0.75 * (adrHigh - adrLow) && adr > adrAvg)
//                    || (adrDayHigh - adrDayLow > 10 && adr > adrDayLow + 0.75 * (adrDayHigh - adrDayLow) && adr > adrAvg));// && adrTRIN < 90;
            boolean buyZone1 = ((adrHigh - adrLow > 5 && adr > adrLow + 0.75 * (adrHigh - adrLow) && adr > adrAvg)
                    || (adrDayHigh - adrDayLow > 10 && adr > adrDayLow + 0.75 * (adrDayHigh - adrDayLow) && adr > adrAvg));// && adrTRIN < 90;
            boolean buyZone2 = ((indexHigh - indexLow > windowHurdle && price > indexLow + 0.75 * (indexHigh - indexLow) && price > indexAvg)
                    || (indexDayHigh - indexDayLow > dayHurdle && price > indexDayLow + 0.75 * (indexDayHigh - indexDayLow) && price > indexAvg));// && adrTRIN < 90;
            //boolean buyZone3 = this.adrTRINAvg < 90 && this.adrTRINAvg > 0;
            boolean buyZone3 = (this.adrTRIN < this.adrTRINAvg - 5 && this.adrTRIN > 90 && this.adrTRIN < 110) || (this.adrTRIN < 90 && this.adrTRINAvg < 90);

//            boolean shortZone1 = ((adrHigh - adrLow > 5 && adr < adrHigh - 0.75 * (adrHigh - adrLow) && adr < adrAvg)
//                    || (adrDayHigh - adrDayLow > 10 && adr < adrDayHigh - 0.75 * (adrDayHigh - adrDayLow) && adr < adrAvg));// && adrTRIN > 95;
            boolean shortZone1 = ((adrHigh - adrLow > 5 && adr < adrHigh - 0.75 * (adrHigh - adrLow))
                    || (adrDayHigh - adrDayLow > 10 && adr < adrDayHigh - 0.75 * (adrDayHigh - adrDayLow)));// && adrTRIN > 95;
            boolean shortZone2 = ((indexHigh - indexLow > windowHurdle && price < indexHigh - 0.75 * (indexHigh - indexLow) && price < indexAvg)
                    || (indexDayHigh - indexDayLow > dayHurdle && price < indexDayHigh - 0.75 * (indexDayHigh - indexDayLow) && price < indexAvg));// && adrTRIN > 95;
            //boolean shortZone3 = this.adrTRINAvg > 95;
            boolean shortZone3 = (this.adrTRIN > this.adrTRINAvg + 5 && this.adrTRIN > 90 && this.adrTRIN < 110) || (this.adrTRIN > 110 && this.adrTRINAvg > 110);

            Boolean buyZone = false;
            Boolean shortZone = false;

            buyZone = atLeastTwo(buyZone1, buyZone2, buyZone3);
            shortZone = atLeastTwo(shortZone1, shortZone2, shortZone3);
            /*
             if (!scalpingMode) {
             buyZone = (atLeastTwo(buyZone1, buyZone2, buyZone3) && adrTRIN < 90) || ((adr > 80 || adr < 20) && atLeastTwo(buyZone1, buyZone2, buyZone3) && adrTRIN < adrTRINAvg);
             shortZone = (atLeastTwo(shortZone1, shortZone2, shortZone3) && adrTRIN > 95) || ((adr > 80 || adr < 20) && atLeastTwo(shortZone1, shortZone2, shortZone3) && adrTRIN > adrTRINAvg);
             } else if (scalpingMode) {
             buyZone = (atLeastTwo(buyZone1, buyZone2, buyZone3) && adrTRIN < 90) || (atLeastTwo(buyZone1, buyZone2, buyZone3) && adrTRIN < adrTRINAvg);
             shortZone = (atLeastTwo(shortZone1, shortZone2, shortZone3) && adrTRIN > 95) || (atLeastTwo(shortZone1, shortZone2, shortZone3) && adrTRIN > adrTRINAvg);
             }
             */
            /*            
             if (!scalpingMode) {
             buyZone = (atLeastTwo(buyZone1, buyZone2, buyZone3) && adrTRIN < 90) || ((adr > 80 || adr < 20) && atLeastTwo(buyZone1, buyZone2, buyZone3) && adr > adrAvg && adrTRIN < adrTRINAvg);
             shortZone = (atLeastTwo(shortZone1, shortZone2, shortZone3) && adrTRIN > 95) || ((adr > 80 || adr < 20) && atLeastTwo(shortZone1, shortZone2, shortZone3) && adr < adrAvg && adrTRIN > adrTRINAvg);
             } else if (scalpingMode) {
             buyZone = (atLeastTwo(buyZone1, buyZone2, buyZone3) && adrTRIN < 90) || (atLeastTwo(buyZone1, buyZone2, buyZone3) && adr > adrAvg && adrTRIN < adrTRINAvg);
             shortZone = (atLeastTwo(shortZone1, shortZone2, shortZone3) && adrTRIN > 95) || (atLeastTwo(shortZone1, shortZone2, shortZone3) && adr < adrAvg && adrTRIN > adrTRINAvg);
             }
             */
            TradingUtil.writeToFile(getStrategy() + ".csv", buyZone + "," + shortZone + ","+tradingSide+"," + adr + "," + adrHigh + "," + adrLow + "," + adrDayHigh + "," + adrDayLow + "," + adrAvg + "," + buyZone1 + "," + shortZone1 + "," + price + "," + indexHigh + "," + indexLow + "," + indexDayHigh + "," + indexDayLow + "," + indexAvg + "," + buyZone2 + "," + shortZone2 + "," + adrTRIN + "," + adrTRINAvg + "," + buyZone3 + "," + shortZone3 + "," + tick + "," + tickTRIN + "," + adrTRINHigh + "," + adrTRINLow + "," + "SCAN");
            logger.log(Level.FINEST, " adrHigh: {0},adrLow: {1},adrAvg: {2},adrTRINHigh: {3},adrTRINLow: {4},adrTRINAvg: {5},indexHigh :{6},indexLow :{7},indexAvg: {8}, buyZone1: {9}, buyZone2: {10}, buyZone 3: {11}, shortZone1: {12}, shortZone2: {13}, ShortZone3:{14}, ADR: {15}, ADRTrin: {16}, Tick: {17}, TickTrin: {18}, adrDayHigh: {19}, adrDayLow: {20}, IndexDayHigh: {21}, IndexDayLow: {22}", new Object[]{adrHigh, adrLow, adrAvg, adrTRINHigh, adrTRINLow, adrTRINAvg, indexHigh, indexLow, indexAvg, buyZone1, buyZone2, buyZone3, shortZone1, shortZone2, shortZone3, adr, adrTRIN, tick, tickTRIN, adrDayHigh, adrDayLow, indexDayHigh, indexDayLow});
            if ((!buyZone && tradingSide == 1 && getPosition().get(id) == 0) || (!shortZone && tradingSide == -1 && getPosition().get(id) == 0)) {
                logger.log(Level.INFO, "Trading Side Reset. New Trading Side: {0}, Earlier trading Side: {1}", new Object[]{0, tradingSide});
                tradingSide = 0;
            }

            if (getPosition().get(id) == 0 && new Date().compareTo(getEndDate()) < 0) {
                if (tradingSide == 0 && buyZone && (tick < 45 || tickTRIN > 120) && getLongOnly() && price > indexHigh - 0.75 * stopLoss) {
                    entryPrice = price;
                    logger.log(Level.INFO, " ALL,{0}, Buy, adrHigh: {1},adrLow: {2},adrAvg: {3},adrTRINHigh: {4},adrTRINLow: {5},adrTRINAvg: {6},indexHigh :{7},indexLow :{8},indexAvg: {9}, buyZone1: {10}, buyZone2: {11}, buyZone 3: {12}, shortZone1: {13}, shortZone2: {14}, ShortZone3:{15}, ADR: {16}, ADRTrin: {17}, Tick: {18}, TickTrin: {19}, adrDayHigh: {20}, adrDayLow: {21}, IndexDayHigh: {22}, IndexDayLow: {23}, Price: {24}", new Object[]{getStrategy(), adrHigh, adrLow, adrAvg, adrTRINHigh, adrTRINLow, adrTRINAvg, indexHigh, indexLow, indexAvg, buyZone1, buyZone2, buyZone3, shortZone1, shortZone2, shortZone3, adr, adrTRIN, tick, tickTRIN, adrDayHigh, adrDayLow, indexDayHigh, indexDayLow, price});
                    entry(id, EnumOrderSide.BUY, entryPrice, 0, true);
                    tradingSide = 1;
                    TradingUtil.writeToFile(getStrategy() + ".csv", buyZone + "," + shortZone + "," +tradingSide+"," +  adr + "," + adrHigh + "," + adrLow + "," + adrDayHigh + "," + adrDayLow + "," + adrAvg + "," + buyZone1 + "," + shortZone1 + "+" + index + "," + indexHigh + "," + indexLow + "," + indexDayHigh + "," + indexDayLow + "," + indexAvg + "," + buyZone2 + "," + shortZone2 + "," + adrTRIN + "," + adrTRINAvg + "," + buyZone3 + "," + shortZone3 + "," + tick + "," + tickTRIN + "," + adrTRINHigh + "," + adrTRINLow + "," + "BUY");
                } else if (tradingSide == 0 && shortZone && (tick > 55 || tickTRIN < 80) && getShortOnly() && price < indexLow + 0.75 * stopLoss) {
                    entryPrice = price;
                    logger.log(Level.INFO, " ALL,{0}, Short, adrHigh: {1},adrLow: {2},adrAvg: {3},adrTRINHigh: {4},adrTRINLow: {5},adrTRINAvg: {6},indexHigh :{7},indexLow :{8},indexAvg: {9}, buyZone1: {10}, buyZone2: {11}, buyZone 3: {12}, shortZone1: {13}, shortZone2: {14}, ShortZone3:{15}, ADR: {16}, ADRTrin: {17}, Tick: {18}, TickTrin: {19}, adrDayHigh: {20}, adrDayLow: {21}, IndexDayHigh: {22}, IndexDayLow: {23}, Price: {24}", new Object[]{getStrategy(), adrHigh, adrLow, adrAvg, adrTRINHigh, adrTRINLow, adrTRINAvg, indexHigh, indexLow, indexAvg, buyZone1, buyZone2, buyZone3, shortZone1, shortZone2, shortZone3, adr, adrTRIN, tick, tickTRIN, adrDayHigh, adrDayLow, indexDayHigh, indexDayLow, price});
                    entry(id, EnumOrderSide.SHORT, entryPrice, 0, true);
                    tradingSide = -1;
                    TradingUtil.writeToFile(getStrategy() + ".csv", buyZone + "," + shortZone + "," + tradingSide+"," + adr + "," + adrHigh + "," + adrLow + "," + adrDayHigh + "," + adrDayLow + "," + adrAvg + "," + buyZone1 + "," + shortZone1 + "+" + index + "," + indexHigh + "," + indexLow + "," + indexDayHigh + "," + indexDayLow + "," + indexAvg + "," + buyZone2 + "," + shortZone2 + "," + adrTRIN + "," + adrTRINAvg + "," + buyZone3 + "," + shortZone3 + "," + tick + "," + tickTRIN + "," + adrTRINHigh + "," + adrTRINLow + "," + "SHORT");
                } else if (tradingSide == 1 && price < this.lastLongExit - this.reentryMinimumMove && scalpingMode && this.lastLongExit > 0) { //used in scalping mode
                    entryPrice = price;
                    logger.log(Level.INFO, " ALL,{0}, Scalping Buy, adrHigh: {1},adrLow: {2},adrAvg: {3},adrTRINHigh: {4},adrTRINLow: {5},adrTRINAvg: {6},indexHigh :{7},indexLow :{8},indexAvg: {9}, buyZone1: {10}, buyZone2: {11}, buyZone 3: {12}, shortZone1: {13}, shortZone2: {14}, ShortZone3:{15}, ADR: {16}, ADRTrin: {17}, Tick: {18}, TickTrin: {19}, adrDayHigh: {20}, adrDayLow: {21}, IndexDayHigh: {22}, IndexDayLow: {23}, Price: {24}", new Object[]{getStrategy(), adrHigh, adrLow, adrAvg, adrTRINHigh, adrTRINLow, adrTRINAvg, indexHigh, indexLow, indexAvg, buyZone1, buyZone2, buyZone3, shortZone1, shortZone2, shortZone3, adr, adrTRIN, tick, tickTRIN, adrDayHigh, adrDayLow, indexDayHigh, indexDayLow, price});
                    entry(id, EnumOrderSide.BUY, entryPrice, 0, true);;
                    getPosition().put(id, 1);
                    TradingUtil.writeToFile(getStrategy() + ".csv", buyZone + "," + shortZone + "," + tradingSide+"," + adr + "," + adrHigh + "," + adrLow + "," + adrDayHigh + "," + adrDayLow + "," + adrAvg + "," + buyZone1 + "," + shortZone1 + "+" + index + "," + indexHigh + "," + indexLow + "," + indexDayHigh + "," + indexDayLow + "," + indexAvg + "," + buyZone2 + "," + shortZone2 + "," + adrTRIN + "," + adrTRINAvg + "," + buyZone3 + "," + shortZone3 + "," + tick + "," + tickTRIN + "," + adrTRINHigh + "," + adrTRINLow + "," + "SCALPING BUY");
                } else if (tradingSide == -1 && price > this.lastShortExit + this.reentryMinimumMove && scalpingMode && this.lastShortExit > 0) {
                    entryPrice = price;
                    logger.log(Level.INFO, " ALL,{0}, Scalping Short, adrHigh: {1},adrLow: {2},adrAvg: {3},adrTRINHigh: {4},adrTRINLow: {5},adrTRINAvg: {6},indexHigh :{7},indexLow :{8},indexAvg: {9}, buyZone1: {10}, buyZone2: {11}, buyZone 3: {12}, shortZone1: {13}, shortZone2: {14}, ShortZone3:{15}, ADR: {16}, ADRTrin: {17}, Tick: {18}, TickTrin: {19}, adrDayHigh: {20}, adrDayLow: {21}, IndexDayHigh: {22}, IndexDayLow: {23}, Price: {24}", new Object[]{getStrategy(), adrHigh, adrLow, adrAvg, adrTRINHigh, adrTRINLow, adrTRINAvg, indexHigh, indexLow, indexAvg, buyZone1, buyZone2, buyZone3, shortZone1, shortZone2, shortZone3, adr, adrTRIN, tick, tickTRIN, adrDayHigh, adrDayLow, indexDayHigh, indexDayLow, price});
                    entry(id, EnumOrderSide.SHORT, entryPrice, 0, true);
                    getPosition().put(id, -1);
                    TradingUtil.writeToFile(getStrategy() + ".csv", buyZone + "," + shortZone + "," + tradingSide+"," + adr + "," + adrHigh + "," + adrLow + "," + adrDayHigh + "," + adrDayLow + "," + adrAvg + "," + buyZone1 + "," + shortZone1 + "+" + index + "," + indexHigh + "," + indexLow + "," + indexDayHigh + "," + indexDayLow + "," + indexAvg + "," + buyZone2 + "," + shortZone2 + "," + adrTRIN + "," + adrTRINAvg + "," + buyZone3 + "," + shortZone3 + "," + tick + "," + tickTRIN + "," + adrTRINHigh + "," + adrTRINLow + "," + "SCALPING SHORT");
                }
            } else if (getPosition().get(id) == -1) {
                if (buyZone || ((price > indexLow + stopLoss && !shortZone) || (price > entryPrice + stopLoss)) || new Date().compareTo(getEndDate()) > 0) { //stop loss
                    logger.log(Level.INFO, " ALL,{0}, StopLoss Cover, adrHigh: {1},adrLow: {2},adrAvg: {3},adrTRINHigh: {4},adrTRINLow: {5},adrTRINAvg: {6},indexHigh :{7},indexLow :{8},indexAvg: {9}, buyZone1: {10}, buyZone2: {11}, buyZone 3: {12}, shortZone1: {13}, shortZone2: {14}, ShortZone3:{15}, ADR: {16}, ADRTrin: {17}, Tick: {18}, TickTrin: {19}, adrDayHigh: {20}, adrDayLow: {21}, IndexDayHigh: {22}, IndexDayLow: {23}, Price: {24}", new Object[]{getStrategy(), adrHigh, adrLow, adrAvg, adrTRINHigh, adrTRINLow, adrTRINAvg, indexHigh, indexLow, indexAvg, buyZone1, buyZone2, buyZone3, shortZone1, shortZone2, shortZone3, adr, adrTRIN, tick, tickTRIN, adrDayHigh, adrDayLow, indexDayHigh, indexDayLow, price});
                    exit(id, EnumOrderSide.COVER, price, 0, "", true, "DAY", true);
                    getPosition().put(id, 0);
                    tradingSide = 0;
                    TradingUtil.writeToFile(getStrategy() + ".csv", buyZone + "," + shortZone + "," + tradingSide+"," + adr + "," + adrHigh + "," + adrLow + "," + adrDayHigh + "," + adrDayLow + "," + adrAvg + "," + buyZone1 + "," + shortZone1 + "+" + index + "," + indexHigh + "," + indexLow + "," + indexDayHigh + "," + indexDayLow + "," + indexAvg + "," + buyZone2 + "," + shortZone2 + "," + adrTRIN + "," + adrTRINAvg + "," + buyZone3 + "," + shortZone3 + "," + tick + "," + tickTRIN + "," + adrTRINHigh + "," + adrTRINLow + "," + "COVER STOP LOSS");

                } else if ((scalpingMode || !shortZone) && (price < entryPrice - takeProfit) && (price > indexLow + 0.5 * takeProfit)) {
                    logger.log(Level.INFO, " ALL,{0}, TakeProfit Cover, adrHigh: {1},adrLow: {2},adrAvg: {3},adrTRINHigh: {4},adrTRINLow: {5},adrTRINAvg: {6},indexHigh :{7},indexLow :{8},indexAvg: {9}, buyZone1: {10}, buyZone2: {11}, buyZone 3: {12}, shortZone1: {13}, shortZone2: {14}, ShortZone3:{15}, ADR: {16}, ADRTrin: {17}, Tick: {18}, TickTrin: {19}, adrDayHigh: {20}, adrDayLow: {21}, IndexDayHigh: {22}, IndexDayLow: {23}, Price: {24}", new Object[]{getStrategy(), adrHigh, adrLow, adrAvg, adrTRINHigh, adrTRINLow, adrTRINAvg, indexHigh, indexLow, indexAvg, buyZone1, buyZone2, buyZone3, shortZone1, shortZone2, shortZone3, adr, adrTRIN, tick, tickTRIN, adrDayHigh, adrDayLow, indexDayHigh, indexDayLow, price});
                    exit(id, EnumOrderSide.COVER, price, 0, "", true, "DAY", true);
                    getPosition().put(id, 0);
                    lastShortExit = price;
                    TradingUtil.writeToFile(getStrategy() + ".csv", buyZone + "," + shortZone + "," + tradingSide+"," + adr + "," + adrHigh + "," + adrLow + "," + adrDayHigh + "," + adrDayLow + "," + adrAvg + "," + buyZone1 + "," + shortZone1 + "+" + index + "," + indexHigh + "," + indexLow + "," + indexDayHigh + "," + indexDayLow + "," + indexAvg + "," + buyZone2 + "," + shortZone2 + "," + adrTRIN + "," + adrTRINAvg + "," + buyZone3 + "," + shortZone3 + "," + tick + "," + tickTRIN + "," + adrTRINHigh + "," + adrTRINLow + "," + "COVER TAKE PROFIT");
                }
            } else if (getPosition().get(id) == 1) {
                if (shortZone || ((price < indexHigh - stopLoss && !buyZone) || (price < entryPrice - stopLoss)) || new Date().compareTo(getEndDate()) > 0) {
                    logger.log(Level.INFO, " ALL,{0}, StopLoss Sell, adrHigh: {1},adrLow: {2},adrAvg: {3},adrTRINHigh: {4},adrTRINLow: {5},adrTRINAvg: {6},indexHigh :{7},indexLow :{8},indexAvg: {9}, buyZone1: {10}, buyZone2: {11}, buyZone 3: {12}, shortZone1: {13}, shortZone2: {14}, ShortZone3:{15}, ADR: {16}, ADRTrin: {17}, Tick: {18}, TickTrin: {19}, adrDayHigh: {20}, adrDayLow: {21}, IndexDayHigh: {22}, IndexDayLow: {23}, Price: {24}", new Object[]{getStrategy(), adrHigh, adrLow, adrAvg, adrTRINHigh, adrTRINLow, adrTRINAvg, indexHigh, indexLow, indexAvg, buyZone1, buyZone2, buyZone3, shortZone1, shortZone2, shortZone3, adr, adrTRIN, tick, tickTRIN, adrDayHigh, adrDayLow, indexDayHigh, indexDayLow, price});
                    exit(id, EnumOrderSide.SELL, price, 0, "", true, "DAY", true);
                    getPosition().put(id, 0);
                    TradingUtil.writeToFile(getStrategy() + ".csv", buyZone + "," + shortZone + "," + tradingSide+"," + adr + "," + adrHigh + "," + adrLow + "," + adrDayHigh + "," + adrDayLow + "," + adrAvg + "," + buyZone1 + "," + shortZone1 + "+" + index + "," + indexHigh + "," + indexLow + "," + indexDayHigh + "," + indexDayLow + "," + indexAvg + "," + buyZone2 + "," + shortZone2 + "," + adrTRIN + "," + adrTRINAvg + "," + buyZone3 + "," + shortZone3 + "," + tick + "," + tickTRIN + "," + adrTRINHigh + "," + adrTRINLow + "," + "SELL STOP LOSS");
                } else if ((scalpingMode || !buyZone) && (price > entryPrice + takeProfit) && (price < indexHigh - 0.5 * takeProfit)) {
                    logger.log(Level.INFO, " ALL,{0}, TakeProfit Sell, adrHigh: {1},adrLow: {2},adrAvg: {3},adrTRINHigh: {4},adrTRINLow: {5},adrTRINAvg: {6},indexHigh :{7},indexLow :{8},indexAvg: {9}, buyZone1: {10}, buyZone2: {11}, buyZone 3: {12}, shortZone1: {13}, shortZone2: {14}, ShortZone3:{15}, ADR: {16}, ADRTrin: {17}, Tick: {18}, TickTrin: {19}, adrDayHigh: {20}, adrDayLow: {21}, IndexDayHigh: {22}, IndexDayLow: {23}, Price: {24}", new Object[]{getStrategy(), adrHigh, adrLow, adrAvg, adrTRINHigh, adrTRINLow, adrTRINAvg, indexHigh, indexLow, indexAvg, buyZone1, buyZone2, buyZone3, shortZone1, shortZone2, shortZone3, adr, adrTRIN, tick, tickTRIN, adrDayHigh, adrDayLow, indexDayHigh, indexDayLow, price});
                    exit(id, EnumOrderSide.SELL, price, 0, "", true, "DAY", true);
                    getPosition().put(id, 0);
                    lastLongExit = price;
                    TradingUtil.writeToFile(getStrategy() + ".csv", buyZone + "," + shortZone + "," + tradingSide+"," + adr + "," + adrHigh + "," + adrLow + "," + adrDayHigh + "," + adrDayLow + "," + adrAvg + "," + buyZone1 + "," + shortZone1 + "+" + index + "," + indexHigh + "," + indexLow + "," + indexDayHigh + "," + indexDayLow + "," + indexAvg + "," + buyZone2 + "," + shortZone2 + "," + adrTRIN + "," + adrTRINAvg + "," + buyZone3 + "," + shortZone3 + "," + tick + "," + tickTRIN + "," + adrTRINHigh + "," + adrTRINLow + "," + "SELL TAKE PROFIT");
                }
            }
        }
    }

    @Override
    public void update(EventBean[] newEvents, EventBean[] oldEvents) {
        double high = newEvents[0].get("high") == null ? Double.MIN_VALUE : (Double) newEvents[0].get("high");
        double low = newEvents[0].get("low") == null ? Double.MAX_VALUE : (Double) newEvents[0].get("low");
        double average = newEvents[0].get("average") == null ? adrAvg : (Double) newEvents[0].get("average");
        if (adr > 0) {
            switch ((Integer) newEvents[0].get("field")) {
                case ADRTickType.D_ADR:
                    adrHigh = high;
                    adrLow = low;
                    adrAvg = average;
                    break;
                case ADRTickType.D_TRIN:
                    adrTRINHigh = high;
                    adrTRINLow = low;
                    adrTRINAvg = average;
                    break;
                case ADRTickType.T_TICK:
                    tickHigh = high;
                    tickLow = low;
                    tickAvg = average;
                    break;
                case ADRTickType.T_TRIN:
                    tickTRINHigh = high;
                    tickTRINLow = low;
                    tickTRINAvg = average;
                    break;
                case ADRTickType.INDEX:
                    indexHigh = high;
                    indexLow = low;
                    indexAvg = average;
                    break;
                default:
                    break;
            }
        }
    }

    boolean atLeastTwo(boolean a, boolean b, boolean c) {
        return a && (b || c) || (b && c);
    }
}
