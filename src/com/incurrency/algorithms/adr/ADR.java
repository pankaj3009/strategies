/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.algorithms.adr;

import com.incurrency.RatesClient.Subscribe;
import com.espertech.esper.client.EventBean;
import com.espertech.esper.client.UpdateListener;
import com.espertech.esper.client.time.CurrentTimeEvent;
import com.incurrency.framework.MainAlgorithm;
import com.incurrency.framework.BeanConnection;
import com.incurrency.framework.BeanSymbol;
import com.incurrency.framework.DateUtil;
import com.incurrency.framework.EnumOrderReason;
import com.incurrency.framework.EnumOrderSide;
import com.incurrency.framework.EnumOrderType;
import com.incurrency.framework.Parameters;
import com.incurrency.framework.Strategy;
import com.incurrency.framework.TradeEvent;
import com.incurrency.framework.TradeListener;
import com.incurrency.framework.TradingUtil;
import java.io.FileInputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.joda.time.DateTimeComparator;

/**
 * Generates ADR, Tick , TRIN
 *
 * @author pankaj
 */
public class ADR extends Strategy implements TradeListener, UpdateListener {

    public EventProcessor mEsperEvtProcessor = null;
    private static final Logger logger = Logger.getLogger(ADR.class.getName());
    private final String delimiter = "_";
    private AtomicBoolean eodCompleted = new AtomicBoolean(Boolean.FALSE);
    private AtomicBoolean bodStarted = new AtomicBoolean(Boolean.TRUE);
    private AtomicBoolean initializing=new AtomicBoolean(Boolean.FALSE);
    private SimpleDateFormat sdf;
    private SimpleDateFormat openingTimeFormat=new SimpleDateFormat("HH:mm:ss");
    private final Date openDate;
    private final Date openDateBuffer;
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
    double stopLoss;
    String window;
    private double windowHurdle;
    private double dayHurdle;
    double takeProfit;
    boolean scalpingMode;
    double reentryMinimumMove;
    private double entryPrice;
    private double lastLongExit;
    private double lastShortExit;
    private String adrRuleName;
    HashMap<Integer, Boolean> closePriceReceived = new HashMap<>();
    ArrayList<TradeRestriction> noTradeZone = new ArrayList<>();
    private double highRange;
    private double lowRange;
    private boolean trackLosingZone;
    private boolean takeProfitHit = false;
    private boolean stopLossHit=false;
    private final Object lockHighRange = new Object();
    private final Object lockLowRange = new Object();
    private final Object lockFlush = new Object();
    private final Object lockBOD=new Object();
    DateTimeComparator comparator;

    public ADR(MainAlgorithm m, Properties prop, String parameterFile, ArrayList<String> accounts, Integer stratCount) throws ParseException {
        super(m, "adr", "FUT", prop, parameterFile, accounts,stratCount);
        this.openDate = openingTimeFormat.parse("09:15:00");
        this.openDateBuffer=openingTimeFormat.parse("09:16:00");
        loadParameters(prop, parameterFile);
        getStrategySymbols().clear();
        for (BeanSymbol s : Parameters.symbol) {
            if (Pattern.compile(Pattern.quote(adrRuleName), Pattern.CASE_INSENSITIVE).matcher(s.getStrategy()).find()) {
                getStrategySymbols().add(s.getSerialno() - 1);
                closePriceReceived.put(s.getSerialno() - 1, Boolean.FALSE);
            }
            if (Pattern.compile(Pattern.quote("adr"), Pattern.CASE_INSENSITIVE).matcher(s.getStrategy()).find() && s.getType().equals("FUT")) {
                getStrategySymbols().add(s.getSerialno() - 1);
            }
        }
        TradingUtil.writeToFile(getStrategy() + ".csv", "buyZone,ShortZone,TradingSide,adr,adrHigh,adrLow,adrDayHigh,adrDayLow,adrAvg,BuyZone1,ShortZone1,index,indexHigh,indexLow,indexDayHigh,indexDayLow,indexAvg,BuyZone2,ShortZone2,adrTRIN,adrTRINAvg,BuyZone3,ShortZone3,tick,tickTRIN,adrTRINHigh,adrTRINLow,HighRange,LowRange,Comment");

        mEsperEvtProcessor = new EventProcessor(this);
        mEsperEvtProcessor.ADRStatement.addListener(this);
        CurrentTimeEvent timeEvent = new CurrentTimeEvent(DateUtil.addSeconds(getStartDate(), -1).getTime());
        mEsperEvtProcessor.sendEvent(timeEvent);
        String[] tempStrategyArray = parameterFile.split("\\.")[0].split("-");
        if (MainAlgorithm.isUseForTrading()) {
            for (BeanConnection c : Parameters.connection) {
                c.getWrapper().addTradeListener(this);
                c.initializeConnection(tempStrategyArray[tempStrategyArray.length - 1]);
            }
        }
        if (Subscribe.tes != null) {
            Subscribe.tes.addTradeListener(this);
        }
        comparator = DateTimeComparator.getTimeOnlyInstance();
        sdf = new SimpleDateFormat("yyyyMMdd");

        
    }

    private void loadParameters(Properties p, String parameterFile) {
        setTrading(Boolean.valueOf(p.getProperty("Trading")));
        setIndex(p.getProperty("Index"));
        setType(p.getProperty("Type"));
        setExpiry(p.getProperty("Expiry") == null ? "" : p.getProperty("Expiry"));
        threshold = Integer.parseInt(p.getProperty("Threshold"));
        setStopLoss(Double.parseDouble(p.getProperty("StopLoss")));
        window = p.getProperty("Window");
        setWindowHurdle(Double.parseDouble(p.getProperty("WindowHurdle")));
        setDayHurdle(Double.parseDouble(p.getProperty("DayHurdle")));
        takeProfit = Double.parseDouble(p.getProperty("TakeProfit"));
        scalpingMode = p.getProperty("ScalpingMode") == null ? false : Boolean.parseBoolean(p.getProperty("ScalpingMode"));
        reentryMinimumMove = p.getProperty("ReentryMinimumMove") == null ? 0D : Double.parseDouble(p.getProperty("ReentryMinimumMove"));
        reentryMinimumMove = p.getProperty("ReentryMinimumMove") == null ? 0D : Double.parseDouble(p.getProperty("ReentryMinimumMove"));
        adrRuleName = p.getProperty("ADRSymbolTag") == null ? "" : p.getProperty("ADRSymbolTag");
        trackLosingZone = p.getProperty("TrackLosingZones") == null ? Boolean.FALSE : Boolean.parseBoolean(p.getProperty("TrackLosingZones"));


        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "TradingAllowed" + delimiter + getTrading()});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "Index" + delimiter + getIndex()});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "IndexType" + delimiter + getType()});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "ContractExpiry" + delimiter + getExpiry()});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "Threshold" + delimiter + threshold});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "StopLoss" + delimiter + getStopLoss()});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "TakeProfit" + delimiter + takeProfit});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "Window" + delimiter + window});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "WindowMinimumMove" + delimiter + getWindowHurdle()});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "DayMinimumMove" + delimiter + getDayHurdle()});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "ScalpingMode" + delimiter + scalpingMode});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "ReentryMinimumMove" + delimiter + reentryMinimumMove});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "ADRRule" + delimiter + adrRuleName});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "LosingZoneNoTrade" + delimiter + trackLosingZone});
    }

    @Override
    public void tradeReceived(TradeEvent event) {
        if (getStrategySymbols().contains(event.getSymbolID())) {
            //new Thread(new ADRTradeReceived(this,event)).start();            
            processTradeReceived(event);

        }
    }

    void processTradeReceived(TradeEvent event) {
       try {
            int id = event.getSymbolID(); //zero based id
            //System.out.println(TradingUtil.getAlgoDate());
            if (!bodStarted.get() && !MainAlgorithm.isUseForTrading()) {                
                if (event.getTickType() != 99 && eodCompleted.get() && !bodStarted.get()) {
                    synchronized(lockBOD){
                        if(event.getTickType() != 99 && eodCompleted.get() && !bodStarted.get()){
                    Thread.sleep(10);
                    Thread.yield();
                    bodStarted.set(Boolean.TRUE);
                    eodCompleted.set(Boolean.FALSE);
                    initializing.set(Boolean.TRUE);
                    this.clearVariablesBOD();
                        }
                            }
                         }
                }
            if (getStrategySymbols().contains(id) && Parameters.symbol.get(id).getType().compareTo("STK") == 0) {
                logger.log(Level.FINER, "ADR Data Received, {0}", new Object[]{"Symbol" + delimiter + Parameters.symbol.get(id).getDisplayname() + delimiter + "Type" + delimiter + event.getTickType() + delimiter + Parameters.symbol.get(id).getLastPriceTime()});
                CurrentTimeEvent timeEvent = new CurrentTimeEvent(TradingUtil.getAlgoDate().getTime());
                while(initializing.get()){
                    Thread.sleep(10);
                    Thread.yield();
                }
                switch (event.getTickType()) {
                    case com.ib.client.TickType.LAST_SIZE:
                        //System.out.println("LASTSIZE, Symbol:"+Parameters.symbol.get(id).getSymbol()+" Value: "+Parameters.symbol.get(id).getLastSize()+" tickerID: "+id);
                        mEsperEvtProcessor.sendEvent(timeEvent);
                        mEsperEvtProcessor.sendEvent(new TickPriceEvent(id, com.ib.client.TickType.LAST_SIZE, Parameters.symbol.get(id).getLastSize()));
                        //mEsperEvtProcessor.debugFireTickQuery();
                        break;
                    case com.ib.client.TickType.VOLUME:
                        //System.out.println("VOLUME, Symbol:"+Parameters.symbol.get(id).getSymbol()+" Value: "+Parameters.symbol.get(id).getVolume()+" tickerID: "+id);
                        mEsperEvtProcessor.sendEvent(timeEvent);
                        mEsperEvtProcessor.sendEvent(new TickPriceEvent(id, com.ib.client.TickType.VOLUME, Parameters.symbol.get(id).getVolume()));
                        //mEsperEvtProcessor.debugFireADRQuery();
                        break;
                    case com.ib.client.TickType.LAST:
                        mEsperEvtProcessor.sendEvent(timeEvent);
                        mEsperEvtProcessor.sendEvent(new TickPriceEvent(id, com.ib.client.TickType.LAST, Parameters.symbol.get(id).getLastPrice()));
                        if (Parameters.symbol.get(id).getClosePrice() == 0 && !this.closePriceReceived.get(id)) {
                            mEsperEvtProcessor.sendEvent(timeEvent);
                            mEsperEvtProcessor.sendEvent(new TickPriceEvent(id, com.ib.client.TickType.CLOSE, Parameters.symbol.get(id).getClosePrice()));
                            this.closePriceReceived.put(id, Boolean.TRUE);

                        }
                        //logger.log(Level.INFO,"DEBUG,Symbol:{1},Time:{0}",new Object[]{new Date(Parameters.symbol.get(id).getLastPriceTime()),Parameters.symbol.get(id).getDisplayname()});
                        //mEsperEvtProcessor.debugFireTickQuery();
                        //mEsperEvtProcessor.debugFireADRQuery();
                        break;
                    case com.ib.client.TickType.CLOSE:
                        mEsperEvtProcessor.sendEvent(timeEvent);
                        mEsperEvtProcessor.sendEvent(new TickPriceEvent(id, com.ib.client.TickType.CLOSE, Parameters.symbol.get(id).getClosePrice()));
                        //mEsperEvtProcessor.debugFireADRQuery();
                        break;
                    case 99:
                        //historical data. Data finished
                        synchronized (lockFlush) {
                            if (!eodCompleted.get() && bodStarted.get()) {
                                initializing.set(Boolean.TRUE);
                                logger.log(Level.INFO, "100,Flush Called,{0}", new Object[]{TradingUtil.getAlgoDate()});
                                this.printOrders("", this);
                                eodCompleted.set(Boolean.TRUE);
                                bodStarted.set(Boolean.FALSE);
                                clearVariablesEOD();

                                //m.setCloseDate(DateUtil.addSeconds(getEndDate(), (this.getMaxOrderDuration() + 2) * 60)); 
                            }
                        }
                    default:
                        break;
                }
            }
            String symbolexpiry = Parameters.symbol.get(id).getExpiry() == null ? "" : Parameters.symbol.get(id).getExpiry();
            if (getTrading() && Parameters.symbol.get(id).getDisplayname().equals(getIndex()) && Parameters.symbol.get(id).getType().equals(getType()) && symbolexpiry.equals(getExpiry()) && event.getTickType() == com.ib.client.TickType.LAST) {
                double price = Parameters.symbol.get(id).getLastPrice();
                if (adr > 0) { //calculate high low only after minimum ticks have been received.
                    mEsperEvtProcessor.sendEvent(new ADREvent(ADRTickType.INDEX, price));
                    if (price > indexDayHigh) {
                        indexDayHigh = price;
                    } else if (price < indexDayLow) {
                        indexDayLow = price;
                    }
                }

                if (getPosition().get(id).getPosition() != 0) {
                    this.setHighRange(price > getHighRange() ? price : getHighRange());
                    this.setLowRange(price < getLowRange() ? price : getLowRange());
                }
                // logger.log(Level.INFO,"ADR Calculated1:"+adr);
//            boolean buyZone1 = ((adrHigh - adrLow > 5 && adr > adrLow + 0.75 * (adrHigh - adrLow) && adr > adrAvg)
//                    || (adrDayHigh - adrDayLow > 10 && adr > adrDayLow + 0.75 * (adrDayHigh - adrDayLow) && adr > adrAvg));// && adrTRIN < 90;
                boolean buyZone1 = ((adrHigh - adrLow > 5 && adr > adrLow + 0.75 * (adrHigh - adrLow) && adr > adrAvg)
                        || (adrDayHigh - adrDayLow > 10 && adr > adrDayLow + 0.75 * (adrDayHigh - adrDayLow) && adr > adrAvg));// && adrTRIN < 90;
                boolean buyZone2 = ((indexHigh - indexLow > getWindowHurdle() && price > indexLow + 0.75 * (indexHigh - indexLow) && price > indexAvg)
                        || (indexDayHigh - indexDayLow > getDayHurdle() && price > indexDayLow + 0.75 * (indexDayHigh - indexDayLow) && price > indexAvg));// && adrTRIN < 90;
                //boolean buyZone3 = this.adrTRINAvg < 90 && this.adrTRINAvg > 0;
                //boolean buyZone3 = (this.adrTRIN < this.adrTRINAvg - 5 && this.adrTRIN > 90 && this.adrTRIN < 110) || (this.adrTRIN < 90 && this.adrTRINAvg < 90);
                //boolean buyZone3 = (this.adrTRIN < this.adrTRINAvg && this.adrTRINAvg < 95);
                boolean buyZone3 = (this.adrTRINAvg < 95);


                //boolean buyZone3=(this.adrTRIN < 90 && this.adrTRINAvg < 90);
//            boolean shortZone1 = ((adrHigh - adrLow > 5 && adr < adrHigh - 0.75 * (adrHigh - adrLow) && adr < adrAvg)
//                    || (adrDayHigh - adrDayLow > 10 && adr < adrDayHigh - 0.75 * (adrDayHigh - adrDayLow) && adr < adrAvg));// && adrTRIN > 95;
                boolean shortZone1 = ((adrHigh - adrLow > 5 && adr < adrHigh - 0.75 * (adrHigh - adrLow) && adr < adrAvg)
                        || (adrDayHigh - adrDayLow > 10 && adr < adrDayHigh - 0.75 * (adrDayHigh - adrDayLow) && adr < adrAvg));// && adrTRIN > 95;
                boolean shortZone2 = ((indexHigh - indexLow > getWindowHurdle() && price < indexHigh - 0.75 * (indexHigh - indexLow) && price < indexAvg)
                        || (indexDayHigh - indexDayLow > getDayHurdle() && price < indexDayHigh - 0.75 * (indexDayHigh - indexDayLow) && price < indexAvg));// && adrTRIN > 95;
                //boolean shortZone3 = this.adrTRINAvg > 110;
                //boolean shortZone3 = (this.adrTRIN > this.adrTRINAvg + 5 && this.adrTRIN > 90 && this.adrTRIN < 110) || (this.adrTRIN > 110 && this.adrTRINAvg > 110);
                //boolean shortZone3 = (this.adrTRIN > this.adrTRINAvg && this.adrTRINAvg > 105);
                boolean shortZone3 = (this.adrTRINAvg > 105);

                Boolean buyZone = false;
                Boolean shortZone = false;

                //buyZone = atLeastTwo(buyZone1, buyZone2, buyZone3);
                //shortZone = atLeastTwo(shortZone1, shortZone2, shortZone3);

                buyZone = buyZone1 && buyZone2 && buyZone3;
                shortZone = shortZone1 && shortZone2 && shortZone3;
                //TradingUtil.writeToFile(getStrategy() + ".csv", buyZone + "," + shortZone + "," + tradingSide + "," + adr + "," + adrHigh + "," + adrLow + "," + adrDayHigh + "," + adrDayLow + "," + adrAvg + "," + buyZone1 + "," + shortZone1 + "," + price + "," + indexHigh + "," + indexLow + "," + indexDayHigh + "," + indexDayLow + "," + indexAvg + "," + buyZone2 + "," + shortZone2 + "," + adrTRIN + "," + adrTRINAvg + "," + buyZone3 + "," + shortZone3 + "," + tick + "," + tickTRIN + "," + adrTRINHigh + "," + adrTRINLow + "," + getHighRange() + "," + getLowRange() + "," + "SCAN", Parameters.symbol.get(id).getLastPriceTime());
                if (MainAlgorithm.isUseForTrading()) {
                    TradingUtil.writeToFile(getStrategy() + ".csv", buyZone + "," + shortZone + "," + tradingSide + "," + adr + "," + adrHigh + "," + adrLow + "," + adrDayHigh + "," + adrDayLow + "," + adrAvg + "," + buyZone1 + "," + shortZone1 + "," + price + "," + indexHigh + "," + indexLow + "," + indexDayHigh + "," + indexDayLow + "," + indexAvg + "," + buyZone2 + "," + shortZone2 + "," + adrTRIN + "," + adrTRINAvg + "," + buyZone3 + "," + shortZone3 + "," + tick + "," + tickTRIN + "," + adrTRINHigh + "," + adrTRINLow + "," + getHighRange() + "," + getLowRange() + "," + "SCAN", Parameters.symbol.get(id).getLastPriceTime());
                } else if (MainAlgorithm.getInput().containsKey("strategylog")){
                    TradingUtil.writeToFile(sdf.format(TradingUtil.getAlgoDate()) + "_" + getStrategy() + ".csv", buyZone + "," + shortZone + "," + tradingSide + "," + adr + "," + adrHigh + "," + adrLow + "," + adrDayHigh + "," + adrDayLow + "," + adrAvg + "," + buyZone1 + "," + shortZone1 + "," + price + "," + indexHigh + "," + indexLow + "," + indexDayHigh + "," + indexDayLow + "," + indexAvg + "," + buyZone2 + "," + shortZone2 + "," + adrTRIN + "," + adrTRINAvg + "," + buyZone3 + "," + shortZone3 + "," + tick + "," + tickTRIN + "," + adrTRINHigh + "," + adrTRINLow + "," + getHighRange() + "," + getLowRange() + "," + "SCAN", Parameters.symbol.get(id).getLastPriceTime());
                }
                if ((!buyZone && tradingSide == 1 && getPosition().get(id).getPosition() == 0) || (!shortZone && tradingSide == -1 && getPosition().get(id).getPosition() == 0)) {
                    logger.log(Level.INFO, "502,TradingSideReset,{0}", new Object[]{getStrategy() + delimiter + 0 + delimiter + tradingSide});
                    tradingSide = 0;
                }

                synchronized (getPosition().get(id).lock) {
                    if (getPosition().get(id).getPosition() == 0 && comparator.compare(TradingUtil.getAlgoDate(), getEndDate()) < 0) {
                        //!stopLossHit && !takeProfitHit &&
                        if ( tradingSide == 0 && buyZone && (tick < 45 || tickTRIN > 120) && getLongOnly() && price > indexHigh - 0.75 * getStopLoss()) {
                            boolean tradeZone = true;
                            if (trackLosingZone) {
                                for (TradeRestriction tr : noTradeZone) {
                                    if (tr.side.equals(EnumOrderSide.BUY) && price > tr.lowRange && price < tr.highRange) {
                                        tradeZone = tradeZone && false;
                                    }
                                }
                            }
                            if (tradeZone) {
                                setEntryPrice(price);
                                setHighRange(Double.MIN_VALUE);
                                setLowRange(Double.MAX_VALUE);
                                logger.log(Level.INFO, "501,StrategyEntry,{0}", new Object[]{getStrategy() + delimiter + "BUY" + delimiter + adrHigh + delimiter + adrLow + delimiter + adrAvg + delimiter + adrTRINHigh + delimiter + adrTRINLow + delimiter + adrTRINAvg + delimiter + indexHigh + delimiter + indexLow + delimiter + indexAvg + delimiter + buyZone1 + delimiter + buyZone2 + delimiter + buyZone3 + delimiter + shortZone1 + delimiter + shortZone2 + delimiter + shortZone3 + delimiter + adr + delimiter + adrTRIN + delimiter + tick + delimiter + tickTRIN + delimiter + adrDayHigh + delimiter + adrDayLow + delimiter + indexDayHigh + delimiter + indexDayLow + delimiter + price});
                                entry(id, EnumOrderSide.BUY, EnumOrderType.LMT, getEntryPrice(), 0, false, EnumOrderReason.REGULARENTRY, "");
                                tradingSide = 1;
                                TradingUtil.writeToFile(getStrategy() + ".csv", buyZone + "," + shortZone + "," + tradingSide + "," + adr + "," + adrHigh + "," + adrLow + "," + adrDayHigh + "," + adrDayLow + "," + adrAvg + "," + buyZone1 + "," + shortZone1 + "," + price + "," + indexHigh + "," + indexLow + "," + indexDayHigh + "," + indexDayLow + "," + indexAvg + "," + buyZone2 + "," + shortZone2 + "," + adrTRIN + "," + adrTRINAvg + "," + buyZone3 + "," + shortZone3 + "," + tick + "," + tickTRIN + "," + adrTRINHigh + "," + adrTRINLow + "," + getHighRange() + "," + getLowRange() + "," + "BUY");
                            }
                            //!stopLossHit && !takeProfitHit && 
                        } else if (tradingSide == 0 && shortZone && (tick > 55 || tickTRIN < 80) && getShortOnly() && price < indexLow + 0.75 * getStopLoss()) {
                            boolean tradeZone = true;
                            if (trackLosingZone) {
                                for (TradeRestriction tr : noTradeZone) {
                                    if (tr.side.equals(EnumOrderSide.SHORT) && price > tr.lowRange && price < tr.highRange) {
                                        tradeZone = tradeZone && false;
                                    }
                                }
                            }
                            if (tradeZone) {
                                setEntryPrice(price);
                                setHighRange(Double.MIN_VALUE);
                                setLowRange(Double.MAX_VALUE);
                                logger.log(Level.INFO, "501,StrategyEntry,{0}", new Object[]{getStrategy() + delimiter + "SHORT" + delimiter + adrHigh + delimiter + adrLow + delimiter + adrAvg + delimiter + adrTRINHigh + delimiter + adrTRINLow + delimiter + adrTRINAvg + delimiter + indexHigh + delimiter + indexLow + delimiter + indexAvg + delimiter + buyZone1 + delimiter + buyZone2 + delimiter + buyZone3 + delimiter + shortZone1 + delimiter + shortZone2 + delimiter + shortZone3 + delimiter + adr + delimiter + adrTRIN + delimiter + tick + delimiter + tickTRIN + delimiter + adrDayHigh + delimiter + adrDayLow + delimiter + indexDayHigh + delimiter + indexDayLow + delimiter + price});
                                entry(id, EnumOrderSide.SHORT, EnumOrderType.LMT, getEntryPrice(), 0, false, EnumOrderReason.REGULARENTRY, "");
                                tradingSide = -1;
                                TradingUtil.writeToFile(getStrategy() + ".csv", buyZone + "," + shortZone + "," + tradingSide + "," + adr + "," + adrHigh + "," + adrLow + "," + adrDayHigh + "," + adrDayLow + "," + adrAvg + "," + buyZone1 + "," + shortZone1 + "," + price + "," + indexHigh + "," + indexLow + "," + indexDayHigh + "," + indexDayLow + "," + indexAvg + "," + buyZone2 + "," + shortZone2 + "," + adrTRIN + "," + adrTRINAvg + "," + buyZone3 + "," + shortZone3 + "," + tick + "," + tickTRIN + "," + adrTRINHigh + "," + adrTRINLow + "," + getHighRange() + "," + getLowRange() + "," + "SHORT", Parameters.symbol.get(id).getLastPriceTime());
                            }
                            //!stopLossHit && !takeProfitHit && 
                        } else if (tradingSide == 1 && price < this.getLastLongExit() - this.reentryMinimumMove && scalpingMode && this.getLastLongExit() > 0) { //used in scalping mode
                            setEntryPrice(price);
                            setHighRange(Double.MIN_VALUE);
                            setLowRange(Double.MAX_VALUE);
                            logger.log(Level.INFO, "501,StrategyEntry,{0}", new Object[]{getStrategy() + delimiter + "SCALPINGBUY" + delimiter + adrHigh + delimiter + adrLow + delimiter + adrAvg + delimiter + adrTRINHigh + delimiter + adrTRINLow + delimiter + adrTRINAvg + delimiter + indexHigh + delimiter + indexLow + delimiter + indexAvg + delimiter + buyZone1 + delimiter + buyZone2 + delimiter + buyZone3 + delimiter + shortZone1 + delimiter + shortZone2 + delimiter + shortZone3 + delimiter + adr + delimiter + adrTRIN + delimiter + tick + delimiter + tickTRIN + delimiter + adrDayHigh + delimiter + adrDayLow + delimiter + indexDayHigh + delimiter + indexDayLow + delimiter + price});
                            entry(id, EnumOrderSide.BUY, EnumOrderType.LMT, getEntryPrice(), 0, false, EnumOrderReason.REGULARENTRY, "");;
                            TradingUtil.writeToFile(getStrategy() + ".csv", buyZone + "," + shortZone + "," + tradingSide + "," + adr + "," + adrHigh + "," + adrLow + "," + adrDayHigh + "," + adrDayLow + "," + adrAvg + "," + buyZone1 + "," + shortZone1 + "," + price + "," + indexHigh + "," + indexLow + "," + indexDayHigh + "," + indexDayLow + "," + indexAvg + "," + buyZone2 + "," + shortZone2 + "," + adrTRIN + "," + adrTRINAvg + "," + buyZone3 + "," + shortZone3 + "," + tick + "," + tickTRIN + "," + adrTRINHigh + "," + adrTRINLow + "," + getHighRange() + "," + getLowRange() + "," + "SCALPING BUY", Parameters.symbol.get(id).getLastPriceTime());
                        //!stopLossHit && !takeProfitHit && 
                        } else if (tradingSide == -1 && price > this.getLastShortExit() + this.reentryMinimumMove && scalpingMode && this.getLastShortExit() > 0) {
                            setEntryPrice(price);
                            setHighRange(Double.MIN_VALUE);
                            setLowRange(Double.MAX_VALUE);
                            logger.log(Level.INFO, "501,StrategyEntry,{0}", new Object[]{getStrategy() + delimiter + "SCALPINGSHORT" + delimiter + adrHigh + delimiter + adrLow + delimiter + adrAvg + delimiter + adrTRINHigh + delimiter + adrTRINLow + delimiter + adrTRINAvg + delimiter + indexHigh + delimiter + indexLow + delimiter + indexAvg + delimiter + buyZone1 + delimiter + buyZone2 + delimiter + buyZone3 + delimiter + shortZone1 + delimiter + shortZone2 + delimiter + shortZone3 + delimiter + adr + delimiter + adrTRIN + delimiter + tick + delimiter + tickTRIN + delimiter + adrDayHigh + delimiter + adrDayLow + delimiter + indexDayHigh + delimiter + indexDayLow + delimiter + price});
                            entry(id, EnumOrderSide.SHORT, EnumOrderType.LMT, getEntryPrice(), 0, false, EnumOrderReason.REGULARENTRY, "");
                            TradingUtil.writeToFile(getStrategy() + ".csv", buyZone + "," + shortZone + "," + tradingSide + "," + adr + "," + adrHigh + "," + adrLow + "," + adrDayHigh + "," + adrDayLow + "," + adrAvg + "," + buyZone1 + "," + shortZone1 + "," + price + "," + indexHigh + "," + indexLow + "," + indexDayHigh + "," + indexDayLow + "," + indexAvg + "," + buyZone2 + "," + shortZone2 + "," + adrTRIN + "," + adrTRINAvg + "," + buyZone3 + "," + shortZone3 + "," + tick + "," + tickTRIN + "," + adrTRINHigh + "," + adrTRINLow + "," + getHighRange() + "," + getLowRange() + "," + "SCALPING SHORT", Parameters.symbol.get(id).getLastPriceTime());
                        }
                    } else if (getPosition().get(id).getPosition() < 0) {
                        if (buyZone || ((price > indexLow + getStopLoss() && !shortZone) || (price > getEntryPrice() + getStopLoss())) || comparator.compare(TradingUtil.getAlgoDate(), getEndDate()) > 0) { //stop loss
                            if(price > getEntryPrice() + getStopLoss()){
                                stopLossHit=true;
                            }
                            logger.log(Level.INFO, "501,StrategySL,{0}", new Object[]{getStrategy() + delimiter + "COVER" + delimiter + adrHigh + delimiter + adrLow + delimiter + adrAvg + delimiter + adrTRINHigh + delimiter + adrTRINLow + delimiter + adrTRINAvg + delimiter + indexHigh + delimiter + indexLow + delimiter + indexAvg + delimiter + buyZone1 + delimiter + buyZone2 + delimiter + buyZone3 + delimiter + shortZone1 + delimiter + shortZone2 + delimiter + shortZone3 + delimiter + adr + delimiter + adrTRIN + delimiter + tick + delimiter + tickTRIN + delimiter + adrDayHigh + delimiter + adrDayLow + delimiter + indexDayHigh + delimiter + indexDayLow + delimiter + price});
                            exit(id, EnumOrderSide.COVER, EnumOrderType.LMT, price, 0, "", true, "DAY", false, EnumOrderReason.REGULAREXIT, "");
                            if (price > getEntryPrice()) {
                                noTradeZone.add(new TradeRestriction(EnumOrderSide.SHORT, getHighRange(), getLowRange()));//stoploss on short. Therefore entryprice
                            }
                            tradingSide = 0;
                            takeProfitHit = false;
                            TradingUtil.writeToFile(getStrategy() + ".csv", buyZone + "," + shortZone + "," + tradingSide + "," + adr + "," + adrHigh + "," + adrLow + "," + adrDayHigh + "," + adrDayLow + "," + adrAvg + "," + buyZone1 + "," + shortZone1 + "," + price + "," + indexHigh + "," + indexLow + "," + indexDayHigh + "," + indexDayLow + "," + indexAvg + "," + buyZone2 + "," + shortZone2 + "," + adrTRIN + "," + adrTRINAvg + "," + buyZone3 + "," + shortZone3 + "," + tick + "," + tickTRIN + "," + adrTRINHigh + "," + adrTRINLow + "," + getHighRange() + "," + getLowRange() + "," + "STOPLOSS COVER", Parameters.symbol.get(id).getLastPriceTime());
                        } else if (((scalpingMode || !shortZone) && (price <= getEntryPrice() - takeProfit)) || takeProfitHit) {
                            takeProfitHit = true;
                            if (price >= indexLow + 0.5 * takeProfit) {
                                logger.log(Level.INFO, "501,StrategyTP,{0}", new Object[]{getStrategy() + delimiter + "COVER" + delimiter + adrHigh + delimiter + adrLow + delimiter + adrAvg + delimiter + adrTRINHigh + delimiter + adrTRINLow + delimiter + adrTRINAvg + delimiter + indexHigh + delimiter + indexLow + delimiter + indexAvg + delimiter + buyZone1 + delimiter + buyZone2 + delimiter + buyZone3 + delimiter + shortZone1 + delimiter + shortZone2 + delimiter + shortZone3 + delimiter + adr + delimiter + adrTRIN + delimiter + tick + delimiter + tickTRIN + delimiter + adrDayHigh + delimiter + adrDayLow + delimiter + indexDayHigh + delimiter + indexDayLow + delimiter + price});
                                exit(id, EnumOrderSide.COVER, EnumOrderType.LMT, price, 0, "", true, "DAY", false, EnumOrderReason.REGULAREXIT, "");
                                setLastShortExit(price);
                                takeProfitHit = false;
                                TradingUtil.writeToFile(getStrategy() + ".csv", buyZone + "," + shortZone + "," + tradingSide + "," + adr + "," + adrHigh + "," + adrLow + "," + adrDayHigh + "," + adrDayLow + "," + adrAvg + "," + buyZone1 + "," + shortZone1 + "," + price + "," + indexHigh + "," + indexLow + "," + indexDayHigh + "," + indexDayLow + "," + indexAvg + "," + buyZone2 + "," + shortZone2 + "," + adrTRIN + "," + adrTRINAvg + "," + buyZone3 + "," + shortZone3 + "," + tick + "," + tickTRIN + "," + adrTRINHigh + "," + adrTRINLow + "," + getHighRange() + "," + getLowRange() + "," + "TAKEPROFIT COVER", Parameters.symbol.get(id).getLastPriceTime());
                            }
                        }
                    } else if (getPosition().get(id).getPosition() > 0) {
                        if (shortZone || ((price < indexHigh - getStopLoss() && !buyZone) || (price < getEntryPrice() - getStopLoss())) || comparator.compare(TradingUtil.getAlgoDate(), getEndDate()) > 0) {
                            if(price < getEntryPrice() - getStopLoss()){
                                stopLossHit=true;
                            }
                            logger.log(Level.INFO, "501,StrategySL,{0}", new Object[]{getStrategy() + delimiter + "SELL" + delimiter + adrHigh + delimiter + adrLow + delimiter + adrAvg + delimiter + adrTRINHigh + delimiter + adrTRINLow + delimiter + adrTRINAvg + delimiter + indexHigh + delimiter + indexLow + delimiter + indexAvg + delimiter + buyZone1 + delimiter + buyZone2 + delimiter + buyZone3 + delimiter + shortZone1 + delimiter + shortZone2 + delimiter + shortZone3 + delimiter + adr + delimiter + adrTRIN + delimiter + tick + delimiter + tickTRIN + delimiter + adrDayHigh + delimiter + adrDayLow + delimiter + indexDayHigh + delimiter + indexDayLow + delimiter + price});
                            exit(id, EnumOrderSide.SELL, EnumOrderType.LMT, price, 0, "", true, "DAY", false, EnumOrderReason.REGULAREXIT, "");
                            if (price < getEntryPrice()) {
                                noTradeZone.add(new TradeRestriction(EnumOrderSide.BUY, getHighRange(), getLowRange()));
                            }
                            tradingSide = 0;
                            takeProfitHit = false;
                            TradingUtil.writeToFile(getStrategy() + ".csv", buyZone + "," + shortZone + "," + tradingSide + "," + adr + "," + adrHigh + "," + adrLow + "," + adrDayHigh + "," + adrDayLow + "," + adrAvg + "," + buyZone1 + "," + shortZone1 + "," + price + "," + indexHigh + "," + indexLow + "," + indexDayHigh + "," + indexDayLow + "," + indexAvg + "," + buyZone2 + "," + shortZone2 + "," + adrTRIN + "," + adrTRINAvg + "," + buyZone3 + "," + shortZone3 + "," + tick + "," + tickTRIN + "," + adrTRINHigh + "," + adrTRINLow + "," + getHighRange() + "," + getLowRange() + "," + "STOPLOSS SELL", Parameters.symbol.get(id).getLastPriceTime());
                        } else if (((scalpingMode || !buyZone) && (price >= getEntryPrice() + takeProfit)) || takeProfitHit) {
                            takeProfitHit = true;
                            if (price <= indexHigh - 0.5 * takeProfit) {
                                logger.log(Level.INFO, "501,StrategyTP,{0}", new Object[]{getStrategy() + delimiter + "SELL" + delimiter + adrHigh + delimiter + adrLow + delimiter + adrAvg + delimiter + adrTRINHigh + delimiter + adrTRINLow + delimiter + adrTRINAvg + delimiter + indexHigh + delimiter + indexLow + delimiter + indexAvg + delimiter + buyZone1 + delimiter + buyZone2 + delimiter + buyZone3 + delimiter + shortZone1 + delimiter + shortZone2 + delimiter + shortZone3 + delimiter + adr + delimiter + adrTRIN + delimiter + tick + delimiter + tickTRIN + delimiter + adrDayHigh + delimiter + adrDayLow + delimiter + indexDayHigh + delimiter + indexDayLow + delimiter + price});
                                exit(id, EnumOrderSide.SELL, EnumOrderType.LMT, price, 0, "", true, "DAY", false, EnumOrderReason.REGULAREXIT, "");
                                setLastLongExit(price);
                                takeProfitHit = false;
                                TradingUtil.writeToFile(getStrategy() + ".csv", buyZone + "," + shortZone + "," + tradingSide + "," + adr + "," + adrHigh + "," + adrLow + "," + adrDayHigh + "," + adrDayLow + "," + adrAvg + "," + buyZone1 + "," + shortZone1 + "," + price + "," + indexHigh + "," + indexLow + "," + indexDayHigh + "," + indexDayLow + "," + indexAvg + "," + buyZone2 + "," + shortZone2 + "," + adrTRIN + "," + adrTRINAvg + "," + buyZone3 + "," + shortZone3 + "," + tick + "," + tickTRIN + "," + adrTRINHigh + "," + adrTRINLow + "," + getHighRange() + "," + getLowRange() + "," + "TAKEPROFIT SELL", Parameters.symbol.get(id).getLastPriceTime());
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, null, e);
        }
    }

    private void clearVariablesEOD() throws ParseException {
        logger.log(Level.INFO, "100,EODClear,{0}", new Object[]{TradingUtil.getAlgoDate()});
        for(BeanSymbol s:Parameters.symbol){
            s.clear();
            closePriceReceived.put(s.getSerialno() - 1, Boolean.FALSE);
        }
        mEsperEvtProcessor.initialize();
        //mEsperEvtProcessor = new EventProcessor(this);
        //mEsperEvtProcessor.ADRStatement.addListener(this);
        CurrentTimeEvent timeEvent = new CurrentTimeEvent(TradingUtil.getAlgoDate().getTime());
        mEsperEvtProcessor.sendEvent(timeEvent);
        //mEsperEvtProcessor.sendEvent(new FlushEvent(0));
        //adjust future expiry
        String expiry=sdf.format(TradingUtil.getAlgoDate());
        String expectedExpiry=null;
        Date ad=TradingUtil.getAlgoDate();
        if(ad.after(sdf.parse("20140530")) && ad.before(sdf.parse("20140626"))){
            expectedExpiry="20140626";
        }else  if(ad.after(sdf.parse("20140626")) && ad.before(sdf.parse("20140731"))){
            expectedExpiry="20140731";
        } if(ad.after(sdf.parse("20140731")) && ad.before(sdf.parse("20140828"))){
            expectedExpiry="20140828";
        } if(ad.after(sdf.parse("20140828")) && ad.before(sdf.parse("20140925"))){
            expectedExpiry="20140925";
        } if(ad.after(sdf.parse("20140925")) && ad.before(sdf.parse("20141030"))){
            expectedExpiry="20141030";
        } if(ad.after(sdf.parse("20141030")) && ad.before(sdf.parse("20141127"))){
            expectedExpiry="20141127";
        } if(ad.after(sdf.parse("20141127")) && ad.before(sdf.parse("20141224"))){
            expectedExpiry="20141224";
        }
        Parameters.symbol.get(0).setExpiry(expectedExpiry);
        this.setExpiry(expectedExpiry);
        adr = 0;
        adrTRIN = 0;
        tick = 0;
        tickTRIN = 0;
        adrDayHigh = Double.MIN_VALUE;
        adrDayLow = Double.MAX_VALUE;
        adrHigh = 0;
        adrLow = 0;
        adrAvg = 0;
        adrTRINHigh = 0;
        adrTRINLow = 0;
        adrTRINAvg = 0;
        tickHigh = 0;
        tickLow = 0;
        tickAvg = 0;
        tickTRINHigh = 0;
        tickTRINLow = 0;
        tickTRINAvg = 0;
        indexHigh = 0;
        indexLow = 0;
        indexAvg = 0;
        indexDayHigh = Double.MIN_VALUE;
        indexDayLow = Double.MAX_VALUE;
        tradingSide = 0;
        entryPrice = 0;
        lastLongExit = 0;
        lastShortExit = 0;
        noTradeZone.clear();
        highRange = 0;
        lowRange = 0;
        takeProfitHit = false;
        stopLossHit=false;
        long memoryNow = Runtime.getRuntime().freeMemory();
        System.gc();
        long memoryLater = Runtime.getRuntime().freeMemory();
        long memoryCleared=memoryNow-memoryLater;
        System.out.println("Memory cleared:"+memoryCleared);
        initializing.set(Boolean.FALSE);

    }

    private void clearVariablesBOD(){
        logger.log(Level.INFO, "100,BODClear,{0}", new Object[]{TradingUtil.getAlgoDate()});
        initializing.set(Boolean.FALSE);
        
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

    /**
     * @return the windowHurdle
     */
    public double getWindowHurdle() {
        return windowHurdle;
    }

    /**
     * @param windowHurdle the windowHurdle to set
     */
    public void setWindowHurdle(double windowHurdle) {
        this.windowHurdle = windowHurdle;
    }

    /**
     * @return the dayHurdle
     */
    public double getDayHurdle() {
        return dayHurdle;
    }

    /**
     * @param dayHurdle the dayHurdle to set
     */
    public void setDayHurdle(double dayHurdle) {
        this.dayHurdle = dayHurdle;
    }

    /**
     * @return the entryPrice
     */
    public double getEntryPrice() {
        return entryPrice;
    }

    /**
     * @param entryPrice the entryPrice to set
     */
    public void setEntryPrice(double entryPrice) {
        this.entryPrice = entryPrice;
    }

    /**
     * @return the index
     */
    public String getIndex() {
        return index;
    }

    /**
     * @param index the index to set
     */
    public void setIndex(String index) {
        this.index = index;
    }

    /**
     * @return the trading
     */
    public Boolean getTrading() {
        return trading;
    }

    /**
     * @param trading the trading to set
     */
    public void setTrading(Boolean trading) {
        this.trading = trading;
    }

    /**
     * @return the stopLoss
     */
    public double getStopLoss() {
        return stopLoss;
    }

    /**
     * @param stopLoss the stopLoss to set
     */
    public void setStopLoss(double stopLoss) {
        this.stopLoss = stopLoss;
    }

    /**
     * @return the type
     */
    public String getType() {
        return type;
    }

    /**
     * @param type the type to set
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * @return the expiry
     */
    public String getExpiry() {
        return expiry;
    }

    /**
     * @param expiry the expiry to set
     */
    public void setExpiry(String expiry) {
        this.expiry = expiry;
    }

    /**
     * @return the lastShortExit
     */
    public double getLastShortExit() {
        return lastShortExit;
    }

    /**
     * @param lastShortExit the lastShortExit to set
     */
    public void setLastShortExit(double lastShortExit) {
        this.lastShortExit = lastShortExit;
    }

    /**
     * @return the lastLongExit
     */
    public double getLastLongExit() {
        return lastLongExit;
    }

    /**
     * @param lastLongExit the lastLongExit to set
     */
    public void setLastLongExit(double lastLongExit) {
        this.lastLongExit = lastLongExit;
    }

    /**
     * @return the highRange
     */
    public double getHighRange() {
        synchronized (lockHighRange) {
            return highRange;
        }
    }

    /**
     * @param highRange the highRange to set
     */
    public void setHighRange(double highRange) {
        synchronized (lockLowRange) {
            this.highRange = highRange;
        }
    }

    /**
     * @return the lowRange
     */
    public double getLowRange() {
        synchronized (lockLowRange) {
            return lowRange;
        }
    }

    /**
     * @param lowRange the lowRange to set
     */
    public void setLowRange(double lowRange) {
        synchronized (lockLowRange) {
            this.lowRange = lowRange;
        }
    }
}
