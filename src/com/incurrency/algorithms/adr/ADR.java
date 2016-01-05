/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.algorithms.adr;

import com.incurrency.RatesClient.Subscribe;
import com.espertech.esper.client.EventBean;
import com.espertech.esper.client.UpdateListener;
import com.espertech.esper.client.time.CurrentTimeEvent;
import com.incurrency.framework.Algorithm;
import com.incurrency.framework.MainAlgorithm;
import com.incurrency.framework.BeanConnection;
import com.incurrency.framework.BeanSymbol;
import com.incurrency.framework.DateUtil;
import com.incurrency.framework.EnumBarSize;
import com.incurrency.framework.EnumOrderReason;
import com.incurrency.framework.EnumOrderSide;
import com.incurrency.framework.EnumOrderStage;
import com.incurrency.framework.EnumOrderType;
import com.incurrency.framework.Parameters;
import com.incurrency.framework.Strategy;
import com.incurrency.framework.TradeEvent;
import com.incurrency.framework.TradeListener;
import com.incurrency.framework.TradingUtil;
import com.incurrency.framework.Utilities;
import com.incurrency.indicators.Indicators;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Properties;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.swing.JFrame;
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
    private String swingSymbol;
    private AtomicBoolean eodCompleted = new AtomicBoolean(Boolean.FALSE);
    private AtomicBoolean bodStarted = new AtomicBoolean(Boolean.TRUE);
    private AtomicBoolean initializing = new AtomicBoolean(Boolean.FALSE);
    private SimpleDateFormat sdf;
    private SimpleDateFormat openingTimeFormat = new SimpleDateFormat("HH:mm:ss");
    private final Date openDate;
    private final Date openDateBuffer;
    //----- updated by ADRListener and TickListener
    public double adr;
    public double adrTRINVolume;
    public double adrTRINValue;
    public double tick;
    public double tickTRIN;
    public double adrDayHigh = Double.MIN_VALUE;
    public double adrDayLow = Double.MAX_VALUE;
    //----- updated by method updateListener    
    double adrHigh;
    double adrLow;
    public double adrAvg;
    double adrTRINVOLUMEHigh;
    double adrTRINVOLUMELow;
    public double adrTRINVOLUMEAvg;
    double adrTRINVALUEHigh;
    double adrTRINVALUELow;
    public double adrTRINVALUEAvg;
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
    double trailingTP;
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
    private boolean trailingTPActive = false;
    private boolean stopLossHit = false;
    private Double[] scaleoutTargets;
    private Integer[] scaleOutSizes;
    private int scaleoutCount = 1;
    public Indicators ind=new Indicators();
    private final Object lockHighRange = new Object();
    private final Object lockLowRange = new Object();
    private final Object lockFlush = new Object();
    private final Object lockBOD = new Object();
    DateTimeComparator comparator;
    Properties p;

    public ADR(MainAlgorithm m, Properties prop, String parameterFile, ArrayList<String> accounts, Integer stratCount) throws ParseException {
        super(m, "adr", "FUT", prop, parameterFile, accounts, null);
        this.openDate = openingTimeFormat.parse("09:15:00");
        this.openDateBuffer = openingTimeFormat.parse("09:16:00");
        this.p=prop;
        loadParameters(prop);
        getStrategySymbols().clear();
        for (BeanSymbol s : Parameters.symbol) {
            if (Pattern.compile(Pattern.quote(adrRuleName), Pattern.CASE_INSENSITIVE).matcher(s.getStrategy()).find()) {
                getStrategySymbols().add(s.getSerialno() - 1);
                closePriceReceived.put(s.getSerialno() - 1, Boolean.FALSE);
            }
            /*
            if (Pattern.compile(Pattern.quote("adr"), Pattern.CASE_INSENSITIVE).matcher(s.getStrategy()).find() && s.getType().equals("FUT")) {
                getStrategySymbols().add(s.getSerialno() - 1);
            }
           */
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
        if (swingSymbol != null) {
            int id = Utilities.getIDFromDisplayName(Parameters.symbol,swingSymbol.toUpperCase());
            if (id >= 0) {
                BeanSymbol symb = Parameters.symbol.get(id);
                Date endDate = new Date();
                Calendar cal_endDate = Calendar.getInstance(TimeZone.getTimeZone(Algorithm.timeZone));
                cal_endDate.add(Calendar.YEAR, -5);
                Date startDate = cal_endDate.getTime();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd HH:mm:ss");
                Utilities.requestHistoricalData(symb, new String[]{"open", "high", "low", "settle"}, "india.nse.index.s4.daily", "yyyyMMdd HH:mm:ss",sdf.format(startDate), sdf.format(endDate), EnumBarSize.DAILY, false);
                ind.swing(symb, EnumBarSize.DAILY);
                int length = symb.getTimeSeriesLength(EnumBarSize.DAILY);
                double[] trends =  symb.getTimeSeries(EnumBarSize.DAILY, "trend").data;
                double[] flipTrends = symb.getTimeSeries(EnumBarSize.DAILY, "fliptrend").data;
                int trend=(int)trends[trends.length-1];
                int flipTrend=(int)flipTrends[flipTrends.length-1];
                switch (trend) {
                    case 1:
                        this.setLongOnly(true);
                        this.setShortOnly(false);
                        break;
                    case -1:
                        this.setLongOnly(false);
                        this.setShortOnly(true);
                        break;
                    case 0:
                        if (flipTrend == 1) {
                            this.setLongOnly(true);
                            this.setShortOnly(false);
                        } else {
                            this.setLongOnly(false);
                            this.setShortOnly(true);
                        }
                        break;
                    default:
                        break;
                }
            }
        }
        if(Boolean.parseBoolean(Algorithm.globalProperties.getProperty("backtest", "false"))){
            Thread t=new Thread(new ADRManager(this));
            t.run();
        }
    }

    @Override
    public void displayStrategyValues() {
        JFrame f = new ADRValues(this);
        f.setVisible(true);


    }

    private void loadParameters(Properties p) {
        setTrading(Boolean.valueOf(p.getProperty("Trading")));
        setIndex(p.getProperty("Index"));
        setType(p.getProperty("Type"));
        setExpiry(p.getProperty("Expiry") == null ? "" : p.getProperty("Expiry"));
        threshold = Integer.parseInt(p.getProperty("Threshold"));
        setStopLoss(Double.parseDouble(p.getProperty("StopLoss")));
        window = p.getProperty("Window");
        setWindowHurdle(Double.parseDouble(p.getProperty("WindowHurdle")));
        setDayHurdle(Double.parseDouble(p.getProperty("DayHurdle")));
        trailingTP = Double.parseDouble(p.getProperty("TakeProfit"));
        scalpingMode = p.getProperty("ScalpingMode") == null ? false : Boolean.parseBoolean(p.getProperty("ScalpingMode"));
        reentryMinimumMove = p.getProperty("ReentryMinimumMove") == null ? 0D : Double.parseDouble(p.getProperty("ReentryMinimumMove"));
        reentryMinimumMove = p.getProperty("ReentryMinimumMove") == null ? 0D : Double.parseDouble(p.getProperty("ReentryMinimumMove"));
        adrRuleName = p.getProperty("ADRSymbolTag") == null ? "" : p.getProperty("ADRSymbolTag");
        setTrackLosingZone(p.getProperty("TrackLosingZones") == null ? Boolean.FALSE : Boolean.parseBoolean(p.getProperty("TrackLosingZones")));
        setScaleoutTargets(p.getProperty("ScaleOutTargets") != null ? TradingUtil.convertArrayToDouble(p.getProperty("ScaleOutTargets").split(",")) : null);
        setScaleOutSizes(p.getProperty("ScaleOutSizes") != null ? TradingUtil.convertArrayToInteger(p.getProperty("ScaleOutSizes").split(",")) : null);
        swingSymbol = p.getProperty("swingsymbol");
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "Use for Trading" + delimiter + MainAlgorithm.isUseForTrading()});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "TradingAllowed" + delimiter + getTrading()});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "Index" + delimiter + getIndex()});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "IndexType" + delimiter + getType()});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "ContractExpiry" + delimiter + getExpiry()});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "Threshold" + delimiter + threshold});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "StopLoss" + delimiter + getStopLoss()});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "TakeProfit" + delimiter + trailingTP});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "Window" + delimiter + window});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "WindowMinimumMove" + delimiter + getWindowHurdle()});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "DayMinimumMove" + delimiter + getDayHurdle()});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "ScalpingMode" + delimiter + scalpingMode});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "ReentryMinimumMove" + delimiter + reentryMinimumMove});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "ADRRule" + delimiter + adrRuleName});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "LosingZoneNoTrade" + delimiter + isTrackLosingZone()});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "ScaleOutTargets" + delimiter + Arrays.toString(getScaleoutTargets())});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "ScaleOutRatios" + delimiter + Arrays.toString(getScaleOutSizes())});


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
                    synchronized (lockBOD) {
                        if (event.getTickType() != 99 && eodCompleted.get() && !bodStarted.get()) {
                            bodStarted.set(Boolean.TRUE);
                            eodCompleted.set(Boolean.FALSE);
                            this.clearVariablesBOD();
                        }
                    }
                }
            }
            if (getStrategySymbols().contains(id) && Parameters.symbol.get(id).getType().compareTo("STK") == 0) {
                CurrentTimeEvent timeEvent = new CurrentTimeEvent(TradingUtil.getAlgoDate().getTime());
                switch (event.getTickType()) {
                    case com.ib.client.TickType.LAST_SIZE:
                        mEsperEvtProcessor.sendEvent(timeEvent);
                        mEsperEvtProcessor.sendEvent(new TickPriceEvent(id, com.ib.client.TickType.LAST_SIZE, Parameters.symbol.get(id).getLastSize()));
                        mEsperEvtProcessor.sendEvent(new TickPriceEvent(id, com.ib.client.TickType.TRADEDVALUE, Parameters.symbol.get(id).getTradedValue()));
                        break;
                    case com.ib.client.TickType.VOLUME:
                        mEsperEvtProcessor.sendEvent(timeEvent);
                        mEsperEvtProcessor.sendEvent(new TickPriceEvent(id, com.ib.client.TickType.VOLUME, Parameters.symbol.get(id).getVolume()));
                        break;
                    case com.ib.client.TickType.LAST:
                        mEsperEvtProcessor.sendEvent(timeEvent);
                        mEsperEvtProcessor.sendEvent(new TickPriceEvent(id, com.ib.client.TickType.LAST, Parameters.symbol.get(id).getLastPrice()));
                        if (Parameters.symbol.get(id).getClosePrice() == 0 && !this.closePriceReceived.get(id)) {
                            mEsperEvtProcessor.sendEvent(timeEvent);
                            mEsperEvtProcessor.sendEvent(new TickPriceEvent(id, com.ib.client.TickType.CLOSE, Parameters.symbol.get(id).getClosePrice()));
                            this.closePriceReceived.put(id, Boolean.TRUE);

                        }
                        break;
                    case com.ib.client.TickType.CLOSE:
                        mEsperEvtProcessor.sendEvent(timeEvent);
                        mEsperEvtProcessor.sendEvent(new TickPriceEvent(id, com.ib.client.TickType.CLOSE, Parameters.symbol.get(id).getClosePrice()));
                        break;
                    case 99:
                        //historical data. Data finished
                        synchronized (lockFlush) {
                            if (!eodCompleted.get() && bodStarted.get()) {
                                this.printOrders("", this);
                                clearVariablesEOD();
                                eodCompleted.set(Boolean.TRUE);
                                bodStarted.set(Boolean.FALSE);
                            }
                        }
                    default:
                        break;
                }
            }
            String symbolexpiry = Parameters.symbol.get(id).getExpiry() == null ? "" : Parameters.symbol.get(id).getExpiry();
            if (getTrading() && Parameters.symbol.get(id).getBrokerSymbol().equals(getIndex()) && Parameters.symbol.get(id).getType().equals(getType()) && symbolexpiry.equals(getExpiry()) && event.getTickType() == com.ib.client.TickType.LAST) {
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
                
                //Buy = ADR IS UPWARD SLOPING ((adrHigh - adrLow > 5 && adr > adrLow + 0.75 * (adrHigh - adrLow) 
                        // VAL = adr/adrTRIN  IS UPWARD SLOPING
                
                boolean buyZone1=adrHigh-adrLow>5 && adr > adrLow + 0.5 * (adrHigh - adrLow) && adrTRINVolume<100 && adrTRINValue<100;
                boolean shortZone1= adrHigh-adrLow>5 && adr < adrHigh - 0.5 * (adrHigh - adrLow)  && adrTRINVolume>100 && adrTRINValue>100;
                
                /*
                boolean buyZone1 = ((adrHigh - adrLow > 5 && adr > adrLow + 0.75 * (adrHigh - adrLow) && adr > adrAvg)
                        || (adrDayHigh - adrDayLow > 10 && adr > adrDayLow + 0.75 * (adrDayHigh - adrDayLow) && adr > adrAvg));// && adrTRIN < 90;
                boolean buyZone2 = ((indexHigh - indexLow > getWindowHurdle() && price > indexLow + 0.75 * (indexHigh - indexLow) && price > indexAvg)
                        || (indexDayHigh - indexDayLow > getDayHurdle() && price > indexDayLow + 0.75 * (indexDayHigh - indexDayLow) && price > indexAvg));// && adrTRIN < 90;
                boolean buyZone3 = (this.adrTRINVOLUMEAvg < 95);
                */
/*
                boolean shortZone1 = ((adrHigh - adrLow > 5 && adr < adrHigh - 0.75 * (adrHigh - adrLow) && adr < adrAvg)
                        || (adrDayHigh - adrDayLow > 10 && adr < adrDayHigh - 0.75 * (adrDayHigh - adrDayLow) && adr < adrAvg));// && adrTRIN > 95;
                boolean shortZone2 = ((indexHigh - indexLow > getWindowHurdle() && price < indexHigh - 0.75 * (indexHigh - indexLow) && price < indexAvg)
                        || (indexDayHigh - indexDayLow > getDayHurdle() && price < indexDayHigh - 0.75 * (indexDayHigh - indexDayLow) && price < indexAvg));// && adrTRIN > 95;
                boolean shortZone3 = (this.adrTRINVOLUMEAvg > 105);
*/
                Boolean buyZone = false;
                Boolean shortZone = false;
                buyZone = buyZone1;// && buyZone2 && buyZone3;
                shortZone = shortZone1;// && shortZone2 && shortZone3;

                TradingUtil.writeToFile(getStrategy() + ".csv", buyZone + "," + shortZone + "," + tradingSide + "," + adr + "," + adrHigh + "," + adrLow + "," + adrDayHigh + "," + adrDayLow + "," + adrAvg + "," + buyZone1 + "," + shortZone1 + "," + price + "," + indexHigh + "," + indexLow + "," + indexDayHigh + "," + indexDayLow + "," + indexAvg + ","+ adrTRINValue + "," + adrTRINVolume + "," + adrTRINVOLUMEAvg + "," + tick + "," + tickTRIN + "," + adrTRINVOLUMEHigh + "," + adrTRINVOLUMELow + "," + getHighRange() + "," + getLowRange() + "," + "SCAN", Parameters.symbol.get(id).getLastPriceTime());
                if (MainAlgorithm.isUseForTrading()) {
                    TradingUtil.writeToFile(getStrategy() + ".csv", buyZone + "," + shortZone + "," + tradingSide + "," + adr + "," + adrHigh + "," + adrLow + "," + adrDayHigh + "," + adrDayLow + "," + adrAvg + "," + buyZone1 + "," + shortZone1 + "," + price + "," + indexHigh + "," + indexLow + "," + indexDayHigh + "," + indexDayLow + "," + indexAvg + "," + adrTRINValue + "," + adrTRINVolume + "," + adrTRINVOLUMEAvg + "," + tick + "," + tickTRIN + "," + adrTRINVOLUMEHigh + "," + adrTRINVOLUMELow + "," + getHighRange() + "," + getLowRange() + "," + "SCAN", Parameters.symbol.get(id).getLastPriceTime());
                } else if (isStrategyLog()) {
                    TradingUtil.writeToFile(sdf.format(TradingUtil.getAlgoDate()) + "_" + getStrategy() + ".csv", buyZone + "," + shortZone + "," + tradingSide + "," + adr + "," + adrHigh + "," + adrLow + "," + adrDayHigh + "," + adrDayLow + "," + adrAvg + "," + buyZone1 + "," + shortZone1 + "," + price + "," + indexHigh + "," + indexLow + "," + indexDayHigh + "," + indexDayLow + "," + indexAvg + "," + adrTRINValue + "," + adrTRINVolume + "," + adrTRINVOLUMEAvg + "," + tick + "," + tickTRIN + "," + adrTRINVOLUMEHigh + "," + adrTRINVOLUMELow + "," + getHighRange() + "," + getLowRange() + "," + "SCAN", Parameters.symbol.get(id).getLastPriceTime());
                }
                if ((!buyZone && tradingSide == 1 && getPosition().get(id).getPosition() == 0) || (!shortZone && tradingSide == -1 && getPosition().get(id).getPosition() == 0)) {
                    logger.log(Level.INFO, "502,TradingSideReset,{0}", new Object[]{getStrategy() + delimiter + 0 + delimiter + tradingSide});
                    tradingSide = 0;
                }
                synchronized (getPosition().get(id).lock) {
                    Trigger adrTrigger = Trigger.UNDEFINED;
                    boolean cEntry = getPosition().get(id).getPosition() == 0 && comparator.compare(TradingUtil.getAlgoDate(), getEndDate()) < 0;
                    //boolean cBuy = tradingSide == 0 && buyZone && (tick < 45 || tickTRIN > 120) && getLongOnly() && price > indexHigh - 0.75 * getStopLoss();
                    boolean cBuy = tradingSide == 0 && buyZone && getLongOnly();
                    boolean cScalpingBuy = tradingSide == 1 && price < this.getLastLongExit() - this.reentryMinimumMove && scalpingMode && this.getLastLongExit() > 0;
                    //boolean cShort = tradingSide == 0 && shortZone && (tick > 55 || tickTRIN < 80) && getShortOnly() && price < indexLow + 0.75 * getStopLoss();
                    boolean cShort = tradingSide == 0 && shortZone && getShortOnly();
                    boolean cScalpingShort = tradingSide == -1 && price > this.getLastShortExit() + this.reentryMinimumMove && scalpingMode && this.getLastShortExit() > 0;
                    boolean cCover = getPosition().get(id).getPosition() < 0;
                    boolean cSLCover = buyZone || comparator.compare(TradingUtil.getAlgoDate(), getEndDate()) > 0;
//                    boolean cSLCover = buyZone || ((price > indexLow + getStopLoss() && !shortZone) || (price > getEntryPrice() + getStopLoss())) || comparator.compare(TradingUtil.getAlgoDate(), getEndDate()) > 0;
                    boolean cTPCover = (!shortZone) && (price <= getEntryPrice() - trailingTP);
                    boolean cTPScalpingCover = (scalpingMode) && (price <= getEntryPrice() - trailingTP);
                    boolean cScaleOutCover = getScaleOutSizes() != null && scaleoutCount - 1 < getScaleOutSizes().length && price <= getEntryPrice() - getScaleoutTargets()[scaleoutCount - 1];
                    boolean cSell = getPosition().get(id).getPosition() > 0;
                    //boolean cSLSell = shortZone || ((price < indexHigh - getStopLoss() && !buyZone) || (price < getEntryPrice() - getStopLoss())) || comparator.compare(TradingUtil.getAlgoDate(), getEndDate()) > 0;
                    boolean cSLSell = shortZone ||price < getEntryPrice() - getStopLoss() || comparator.compare(TradingUtil.getAlgoDate(), getEndDate()) > 0;
                    boolean cTPSell = (!buyZone) && (price >= getEntryPrice() + trailingTP);
                    boolean cTPScalpingSell = ((scalpingMode) && (price >= getEntryPrice() + trailingTP));
                    boolean cScaleOutSell = getScaleOutSizes() != null && scaleoutCount - 1 < getScaleOutSizes().length && price >= getEntryPrice() + getScaleoutTargets()[scaleoutCount - 1];
                    if (cEntry && (cBuy || cScalpingBuy)) {
                        adrTrigger = Trigger.BUY;
                    } else if (cEntry && (cShort || cScalpingShort)) {
                        adrTrigger = Trigger.SHORT;
                    } else if (cCover && cSLCover) {
                        adrTrigger = Trigger.SLCOVER;
                    } else if (cCover && cTPCover) {
                        adrTrigger = Trigger.TPCOVER;
                    } else if (cCover && cTPScalpingCover) {
                        adrTrigger = Trigger.TPSCALPINGCOVER;
                    }else if (cCover && cScaleOutCover) {
                        adrTrigger = Trigger.SCALEOUTCOVER;
                    } else if (cSell && cSLSell) {
                        adrTrigger = Trigger.SLSELL;
                    } else if (cSell && cTPSell) {
                        adrTrigger = Trigger.TPSELL;
                    } else if (cSell && cTPScalpingSell) {
                        adrTrigger = Trigger.TPSCALPINGSELL;                    
                    } else if (cSell && cScaleOutSell) {
                        adrTrigger = Trigger.SCALEOUTSELL;
                    }
                    boolean tradeZone = true;
                    HashMap<String, Object> order = new HashMap<>();
                    switch (adrTrigger) {
                        case BUY:
                            if (isTrackLosingZone()) {
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
                                logger.log(Level.INFO, "501,Strategy BUY,{0}", new Object[]{getStrategy() + delimiter + "BUY" + delimiter + adrHigh + delimiter + adrLow + delimiter + adrAvg + delimiter + adrTRINVOLUMEHigh + delimiter + adrTRINVOLUMELow + delimiter + adrTRINVOLUMEAvg + delimiter + indexHigh + delimiter + indexLow + delimiter + indexAvg + delimiter + buyZone1 + delimiter + shortZone1 + delimiter + adr + delimiter +adrTRINValue+delimiter+ adrTRINVolume + delimiter + tick + delimiter + tickTRIN + delimiter + adrDayHigh + delimiter + adrDayLow + delimiter + indexDayHigh + delimiter + indexDayLow + delimiter + price});
                                order.put("id", id);
                                order.put("side", EnumOrderSide.BUY);
                                order.put("size", 0);
                                order.put("type", EnumOrderType.LMT);
                                order.put("limitprice", getEntryPrice());
                                order.put("reason", EnumOrderReason.REGULARENTRY);
                                order.put("orderstage", EnumOrderStage.INIT);
                                order.put("expiretime", this.getMaxOrderDuration());
                                order.put("dynamicorderduration", getDynamicOrderDuration());
                                order.put("maxslippage", this.getMaxSlippageEntry());
                                entry(order);
//                              entry(id, EnumOrderSide.BUY, 0, EnumOrderType.LMT, getEntryPrice(), 0, EnumOrderReason.REGULARENTRY, EnumOrderStage.INIT, getMaxOrderDuration(), getDynamicOrderDuration(), getMaxSlippageExit(), "", "DAY", "", false, true);
                                tradingSide = 1;
                                TradingUtil.writeToFile(getStrategy() + ".csv", buyZone + "," + shortZone + "," + tradingSide + "," + adr + "," + adrHigh + "," + adrLow + "," + adrDayHigh + "," + adrDayLow + "," + adrAvg + "," + buyZone1 + "," + shortZone1 + "," + price + "," + indexHigh + "," + indexLow + "," + indexDayHigh + "," + indexDayLow + "," + indexAvg + "," + adrTRINValue + "," + adrTRINVolume + "," + adrTRINVOLUMEAvg + "," + tick + "," + tickTRIN + "," + adrTRINVOLUMEHigh + "," + adrTRINVOLUMELow + "," + getHighRange() + "," + getLowRange() + "," + "BUY");
                            }
                            scaleoutCount = 1;
                            break;
                        case TPSELL:
                        case TPSCALPINGSELL:
                                int size=0;
                               if(adrTrigger.equals(Trigger.TPSELL)){
                               logger.log(Level.INFO, "501,Strategy TPSELL,{0}", new Object[]{getStrategy() + delimiter + "BUY" + delimiter + adrHigh + delimiter + adrLow + delimiter + adrAvg + delimiter + adrTRINVOLUMEHigh + delimiter + adrTRINVOLUMELow + delimiter + adrTRINVOLUMEAvg + delimiter + indexHigh + delimiter + indexLow + delimiter + indexAvg + delimiter + buyZone1 + delimiter + shortZone1 + delimiter + adr + delimiter +adrTRINValue+delimiter+ adrTRINVolume + delimiter + tick + delimiter + tickTRIN + delimiter + adrDayHigh + delimiter + adrDayLow + delimiter + indexDayHigh + delimiter + indexDayLow + delimiter + price});
                               }else{
                               size = getScaleOutSizes()[scaleoutCount - 1] * Parameters.symbol.get(id).getMinsize();
                               scaleoutCount = scaleoutCount + 1;
                               logger.log(Level.INFO, "501,Strategy TPSCALPINGSELL,{0}", new Object[]{getStrategy() + delimiter + "BUY" + delimiter + adrHigh + delimiter + adrLow + delimiter + adrAvg + delimiter + adrTRINVOLUMEHigh + delimiter + adrTRINVOLUMELow + delimiter + adrTRINVOLUMEAvg + delimiter + indexHigh + delimiter + indexLow + delimiter + indexAvg + delimiter + buyZone1 + delimiter + shortZone1 + delimiter + adr + delimiter +adrTRINValue+delimiter+ adrTRINVolume + delimiter + tick + delimiter + tickTRIN + delimiter + adrDayHigh + delimiter + adrDayLow + delimiter + indexDayHigh + delimiter + indexDayLow + delimiter + price+delimiter+size});
                               }
                               
                                order.put("id", id);
                                order.put("side", EnumOrderSide.SELL);
                                order.put("size",size );
                                order.put("type", EnumOrderType.LMT);
                                order.put("limitprice", price);
                                order.put("reason", EnumOrderReason.TP);
                                order.put("orderstage", EnumOrderStage.INIT);
                                order.put("expiretime", this.getMaxOrderDuration());
                                order.put("dynamicorderduration", getDynamicOrderDuration());
                                order.put("maxslippage", this.getMaxSlippageExit());
                                exit(order);
                                //exit(id, EnumOrderSide.SELL, 0, EnumOrderType.LMT, price, 0, EnumOrderReason.REGULAREXIT, EnumOrderStage.INIT, getMaxOrderDuration(), getDynamicOrderDuration(), getMaxSlippageExit(), "", "DAY", "", false, true);
                                setLastLongExit(price);
                                trailingTPActive = false;
                                if (price < getEntryPrice()) {
                                    noTradeZone.add(new TradeRestriction(EnumOrderSide.BUY, getHighRange(), getLowRange()));
                                }
                                TradingUtil.writeToFile(getStrategy() + ".csv", buyZone + "," + shortZone + "," + tradingSide + "," + adr + "," + adrHigh + "," + adrLow + "," + adrDayHigh + "," + adrDayLow + "," + adrAvg + "," + buyZone1 + "," + shortZone1 + "," + price + "," + indexHigh + "," + indexLow + "," + indexDayHigh + "," + indexDayLow + "," + indexAvg + "," + adrTRINValue + "," + adrTRINVolume + "," + adrTRINVOLUMEAvg + "," + tick + "," + tickTRIN + "," + adrTRINVOLUMEHigh + "," + adrTRINVOLUMELow + "," + getHighRange() + "," + getLowRange() + "," + "TPSELL", Parameters.symbol.get(id).getLastPriceTime());
                            
                            break;
                        case SLSELL:
                            logger.log(Level.INFO, "501,Strategy SLSELL,{0}", new Object[]{getStrategy() + delimiter + "BUY" + delimiter + adrHigh + delimiter + adrLow + delimiter + adrAvg + delimiter + adrTRINVOLUMEHigh + delimiter + adrTRINVOLUMELow + delimiter + adrTRINVOLUMEAvg + delimiter + indexHigh + delimiter + indexLow + delimiter + indexAvg + delimiter + buyZone1 + delimiter + shortZone1 + delimiter + adr + delimiter +adrTRINValue+delimiter+ adrTRINVolume + delimiter + tick + delimiter + tickTRIN + delimiter + adrDayHigh + delimiter + adrDayLow + delimiter + indexDayHigh + delimiter + indexDayLow + delimiter + price});
                                order.put("id", id);
                                order.put("side", EnumOrderSide.SELL);
                                order.put("size", 0);
                                order.put("type", EnumOrderType.LMT);
                                order.put("limitprice", price);
                                order.put("reason", EnumOrderReason.REGULAREXIT);
                                order.put("orderstage", EnumOrderStage.INIT);
                                order.put("expiretime", this.getMaxOrderDuration());
                                order.put("dynamicorderduration", getDynamicOrderDuration());
                                order.put("maxslippage", this.getMaxSlippageExit());
                                exit(order);
                            //exit(id, EnumOrderSide.SELL, 0, EnumOrderType.LMT, price, 0, EnumOrderReason.REGULAREXIT, EnumOrderStage.INIT, getMaxOrderDuration(), getDynamicOrderDuration(), getMaxSlippageExit(), "", "DAY", "", false, true);
                            setLastLongExit(price);
                            if (price < getEntryPrice()) {
                                noTradeZone.add(new TradeRestriction(EnumOrderSide.BUY, getHighRange(), getLowRange()));
                            }
                            trailingTPActive = false;
                            TradingUtil.writeToFile(getStrategy() + ".csv", buyZone + "," + shortZone + "," + tradingSide + "," + adr + "," + adrHigh + "," + adrLow + "," + adrDayHigh + "," + adrDayLow + "," + adrAvg + "," + buyZone1 + "," + shortZone1 + "," + price + "," + indexHigh + "," + indexLow + "," + indexDayHigh + "," + indexDayLow + "," + indexAvg + "," + adrTRINValue+ "," + adrTRINVolume + "," + adrTRINVOLUMEAvg + ","  + tick + "," + tickTRIN + "," + adrTRINVOLUMEHigh + "," + adrTRINVOLUMELow + "," + getHighRange() + "," + getLowRange() + "," + "SLSELL", Parameters.symbol.get(id).getLastPriceTime());
                            break;
                        case SCALEOUTSELL:
                            size = getScaleOutSizes()[scaleoutCount - 1] * Parameters.symbol.get(id).getMinsize();
                            logger.log(Level.INFO, "501,Strategy SCALEOUTSELL,{0}", new Object[]{getStrategy() + delimiter + "BUY" + delimiter + adrHigh + delimiter + adrLow + delimiter + adrAvg + delimiter + adrTRINVOLUMEHigh + delimiter + adrTRINVOLUMELow + delimiter + adrTRINVOLUMEAvg + delimiter + indexHigh + delimiter + indexLow + delimiter + indexAvg + delimiter + buyZone1 + delimiter + shortZone1 + delimiter + adr + delimiter +adrTRINValue+delimiter+ adrTRINVolume + delimiter + tick + delimiter + tickTRIN + delimiter + adrDayHigh + delimiter + adrDayLow + delimiter + indexDayHigh + delimiter + indexDayLow + delimiter + price+delimiter+size});
                                order.put("id", id);
                                order.put("side", EnumOrderSide.SELL);
                                order.put("size", size);
                                order.put("type", EnumOrderType.LMT);
                                order.put("limitprice", price);
                                order.put("reason", EnumOrderReason.TP);
                                order.put("orderstage", EnumOrderStage.INIT);
                                order.put("expiretime", this.getMaxOrderDuration());
                                order.put("dynamicorderduration", getDynamicOrderDuration());
                                order.put("maxslippage", this.getMaxSlippageExit());
                                order.put("scale", "true");
                                exit(order);
                            //exit(id, EnumOrderSide.SELL, size, EnumOrderType.LMT, price, 0, EnumOrderReason.REGULAREXIT, EnumOrderStage.INIT, getMaxOrderDuration(), getDynamicOrderDuration(), getMaxSlippageExit(), "", "DAY", "", true, true);
                            scaleoutCount = scaleoutCount + 1;
                            TradingUtil.writeToFile(getStrategy() + ".csv", buyZone + "," + shortZone + "," + tradingSide + "," + adr + "," + adrHigh + "," + adrLow + "," + adrDayHigh + "," + adrDayLow + "," + adrAvg + "," + buyZone1 + "," + shortZone1 + "," + price + "," + indexHigh + "," + indexLow + "," + indexDayHigh + "," + indexDayLow + "," + indexAvg + "," + adrTRINValue + "," + adrTRINVolume + "," + adrTRINVOLUMEAvg + "," + tick + "," + tickTRIN + "," + adrTRINVOLUMEHigh + "," + adrTRINVOLUMELow + "," + getHighRange() + "," + getLowRange() + "," + "SCALEOUTSELL", Parameters.symbol.get(id).getLastPriceTime());
                            break;
                        case SHORT:
                            if (isTrackLosingZone()) {
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
                                logger.log(Level.INFO, "501,Strategy SHORT,{0}", new Object[]{getStrategy() + delimiter + "BUY" + delimiter + adrHigh + delimiter + adrLow + delimiter + adrAvg + delimiter + adrTRINVOLUMEHigh + delimiter + adrTRINVOLUMELow + delimiter + adrTRINVOLUMEAvg + delimiter + indexHigh + delimiter + indexLow + delimiter + indexAvg + delimiter + buyZone1 + delimiter + shortZone1 + delimiter + adr + delimiter +adrTRINValue+delimiter+ adrTRINVolume + delimiter + tick + delimiter + tickTRIN + delimiter + adrDayHigh + delimiter + adrDayLow + delimiter + indexDayHigh + delimiter + indexDayLow + delimiter + price});
                                order.put("id", id);
                                order.put("side", EnumOrderSide.SHORT);
                                order.put("size", 0);
                                order.put("type", EnumOrderType.LMT);
                                order.put("limitprice", getEntryPrice());
                                order.put("reason", EnumOrderReason.REGULARENTRY);
                                order.put("orderstage", EnumOrderStage.INIT);
                                order.put("expiretime", this.getMaxOrderDuration());
                                order.put("dynamicorderduration", getDynamicOrderDuration());
                                order.put("maxslippage", this.getMaxSlippageEntry());
                                entry(order);
                                //entry(id, EnumOrderSide.SHORT, 0, EnumOrderType.LMT, getEntryPrice(), 0, EnumOrderReason.REGULARENTRY, EnumOrderStage.INIT, getMaxOrderDuration(), getDynamicOrderDuration(), getMaxSlippageExit(), "", "DAY", "", false, true);
                                tradingSide = -1;
                                TradingUtil.writeToFile(getStrategy() + ".csv", buyZone + "," + shortZone + "," + tradingSide + "," + adr + "," + adrHigh + "," + adrLow + "," + adrDayHigh + "," + adrDayLow + "," + adrAvg + "," + buyZone1 + "," + shortZone1 + "," + price + "," + indexHigh + "," + indexLow + "," + indexDayHigh + "," + indexDayLow + "," + indexAvg + "," + adrTRINValue + "," + adrTRINVolume + "," + adrTRINVOLUMEAvg + "," + tick + "," + tickTRIN + "," + adrTRINVOLUMEHigh + "," + adrTRINVOLUMELow + "," + getHighRange() + "," + getLowRange() + "," + "SHORT", Parameters.symbol.get(id).getLastPriceTime());
                            }
                            scaleoutCount = 1;
                            break;
                        case TPCOVER:
                        case TPSCALPINGCOVER:
                            trailingTPActive = true;
                            size=0;
                               if(adrTrigger.equals(Trigger.TPCOVER)){
                                logger.log(Level.INFO, "501,Strategy TPCOVER,{0}", new Object[]{getStrategy() + delimiter + "BUY" + delimiter + adrHigh + delimiter + adrLow + delimiter + adrAvg + delimiter + adrTRINVOLUMEHigh + delimiter + adrTRINVOLUMELow + delimiter + adrTRINVOLUMEAvg + delimiter + indexHigh + delimiter + indexLow + delimiter + indexAvg + delimiter + buyZone1 + delimiter + shortZone1 + delimiter + adr + delimiter +adrTRINValue+delimiter+ adrTRINVolume + delimiter + tick + delimiter + tickTRIN + delimiter + adrDayHigh + delimiter + adrDayLow + delimiter + indexDayHigh + delimiter + indexDayLow + delimiter + price});                               
                               }else{
                               size = getScaleOutSizes()[scaleoutCount - 1] * Parameters.symbol.get(id).getMinsize();
                               scaleoutCount = scaleoutCount + 1;
                               logger.log(Level.INFO, "501,Strategy TPSCALINGCOVER,{0}", new Object[]{getStrategy() + delimiter + "BUY" + delimiter + adrHigh + delimiter + adrLow + delimiter + adrAvg + delimiter + adrTRINVOLUMEHigh + delimiter + adrTRINVOLUMELow + delimiter + adrTRINVOLUMEAvg + delimiter + indexHigh + delimiter + indexLow + delimiter + indexAvg + delimiter + buyZone1 + delimiter + shortZone1 + delimiter + adr + delimiter +adrTRINValue+delimiter+ adrTRINVolume + delimiter + tick + delimiter + tickTRIN + delimiter + adrDayHigh + delimiter + adrDayLow + delimiter + indexDayHigh + delimiter + indexDayLow + delimiter + price+delimiter+size});                               
                               }
                                order.put("id", id);
                                order.put("side", EnumOrderSide.COVER);
                                order.put("size", size);
                                order.put("type", EnumOrderType.LMT);
                                order.put("limitprice", price);
                                order.put("reason", EnumOrderReason.TP);
                                order.put("orderstage", EnumOrderStage.INIT);
                                order.put("expiretime", this.getMaxOrderDuration());
                                order.put("dynamicorderduration", getDynamicOrderDuration());
                                order.put("maxslippage", this.getMaxSlippageExit());
                                exit(order);
                                //exit(id, EnumOrderSide.COVER, 0, EnumOrderType.LMT, price, 0, EnumOrderReason.REGULAREXIT, EnumOrderStage.INIT, getMaxOrderDuration(), getDynamicOrderDuration(), getMaxSlippageExit(), "", "DAY", "", false, true);
                                setLastShortExit(price);
                                trailingTPActive = false;
                                if (price > getEntryPrice()) {
                                    noTradeZone.add(new TradeRestriction(EnumOrderSide.SHORT, getHighRange(), getLowRange()));
                                }
                                TradingUtil.writeToFile(getStrategy() + ".csv", buyZone + "," + shortZone + "," + tradingSide + "," + adr + "," + adrHigh + "," + adrLow + "," + adrDayHigh + "," + adrDayLow + "," + adrAvg + "," + buyZone1 + "," + shortZone1 + "," + price + "," + indexHigh + "," + indexLow + "," + indexDayHigh + "," + indexDayLow + "," + indexAvg + "," + adrTRINValue +"," + adrTRINVolume + "," + adrTRINVOLUMEAvg + "," + tick + "," + tickTRIN + "," + adrTRINVOLUMEHigh + "," + adrTRINVOLUMELow + "," + getHighRange() + "," + getLowRange() + "," + "TPCOVER", Parameters.symbol.get(id).getLastPriceTime());
                            break;
                        case SLCOVER:
                            logger.log(Level.INFO, "501,Strategy SLCOVER,{0}", new Object[]{getStrategy() + delimiter + "BUY" + delimiter + adrHigh + delimiter + adrLow + delimiter + adrAvg + delimiter + adrTRINVOLUMEHigh + delimiter + adrTRINVOLUMELow + delimiter + adrTRINVOLUMEAvg + delimiter + indexHigh + delimiter + indexLow + delimiter + indexAvg + delimiter + buyZone1 + delimiter + shortZone1 + delimiter + adr + delimiter +adrTRINValue+delimiter+ adrTRINVolume + delimiter + tick + delimiter + tickTRIN + delimiter + adrDayHigh + delimiter + adrDayLow + delimiter + indexDayHigh + delimiter + indexDayLow + delimiter + price});                            
                                order.put("id", id);
                                order.put("side", EnumOrderSide.COVER);
                                order.put("size", 0);
                                order.put("type", EnumOrderType.LMT);
                                order.put("limitprice", price);
                                order.put("reason", EnumOrderReason.REGULAREXIT);
                                order.put("orderstage", EnumOrderStage.INIT);
                                order.put("expiretime", this.getMaxOrderDuration());
                                order.put("dynamicorderduration", getDynamicOrderDuration());
                                order.put("maxslippage", this.getMaxSlippageExit());
                                exit(order);
//                            exit(id, EnumOrderSide.COVER, 0, EnumOrderType.LMT, price, 0, EnumOrderReason.REGULAREXIT, EnumOrderStage.INIT, getMaxOrderDuration(), getDynamicOrderDuration(), getMaxSlippageExit(), "", "DAY", "", false, true);
                            setLastShortExit(price);
                            if (price < getEntryPrice()) {
                                noTradeZone.add(new TradeRestriction(EnumOrderSide.BUY, getHighRange(), getLowRange()));
                            }
                            trailingTPActive = false;
                            TradingUtil.writeToFile(getStrategy() + ".csv", buyZone + "," + shortZone + "," + tradingSide + "," + adr + "," + adrHigh + "," + adrLow + "," + adrDayHigh + "," + adrDayLow + "," + adrAvg + "," + buyZone1 + "," + shortZone1 + "," + price + "," + indexHigh + "," + indexLow + "," + indexDayHigh + "," + indexDayLow + "," + indexAvg + "," + adrTRINValue+ "," + adrTRINVolume + "," + adrTRINVOLUMEAvg + "," + tick + "," + tickTRIN + "," + adrTRINVOLUMEHigh + "," + adrTRINVOLUMELow + "," + getHighRange() + "," + getLowRange() + "," + "SLCOVER", Parameters.symbol.get(id).getLastPriceTime());
                            break;
                        case SCALEOUTCOVER:
                            size = getScaleOutSizes()[scaleoutCount - 1] * Parameters.symbol.get(id).getMinsize();
                            logger.log(Level.INFO, "501,Strategy SCALEOUTCOVER,{0}", new Object[]{getStrategy() + delimiter + "BUY" + delimiter + adrHigh + delimiter + adrLow + delimiter + adrAvg + delimiter + adrTRINVOLUMEHigh + delimiter + adrTRINVOLUMELow + delimiter + adrTRINVOLUMEAvg + delimiter + indexHigh + delimiter + indexLow + delimiter + indexAvg + delimiter + buyZone1 + delimiter + shortZone1 + delimiter + adr + delimiter +adrTRINValue+delimiter+ adrTRINVolume + delimiter + tick + delimiter + tickTRIN + delimiter + adrDayHigh + delimiter + adrDayLow + delimiter + indexDayHigh + delimiter + indexDayLow + delimiter + price+delimiter+size});                            
                                order.put("id", id);
                                order.put("side", EnumOrderSide.COVER);
                                order.put("size", size);
                                order.put("type", EnumOrderType.LMT);
                                order.put("limitprice", price);
                                order.put("reason", EnumOrderReason.TP);
                                order.put("orderstage", EnumOrderStage.INIT);
                                order.put("expiretime", this.getMaxOrderDuration());
                                order.put("dynamicorderduration", getDynamicOrderDuration());
                                order.put("maxslippage", this.getMaxSlippageExit());
                                order.put("scale", "true");
                                exit(order);
                                //exit(id, EnumOrderSide.COVER, size, EnumOrderType.LMT, price, 0, EnumOrderReason.REGULAREXIT, EnumOrderStage.INIT, getMaxOrderDuration(), getDynamicOrderDuration(), getMaxSlippageExit(), "", "DAY", "", true, true);
                            scaleoutCount = scaleoutCount + 1;
                            TradingUtil.writeToFile(getStrategy() + ".csv", buyZone + "," + shortZone + "," + tradingSide + "," + adr + "," + adrHigh + "," + adrLow + "," + adrDayHigh + "," + adrDayLow + "," + adrAvg + "," + buyZone1 + "," + shortZone1 + "," + price + "," + indexHigh + "," + indexLow + "," + indexDayHigh + "," + indexDayLow + "," + indexAvg + "," + adrTRINValue + "," + adrTRINVolume + "," + adrTRINVOLUMEAvg + "," + tick + "," + tickTRIN + "," + adrTRINVOLUMEHigh + "," + adrTRINVOLUMELow + "," + getHighRange() + "," + getLowRange() + "," + "SCALEOUTCOVER", Parameters.symbol.get(id).getLastPriceTime());
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

    private void clearVariablesEOD() throws ParseException {
        logger.log(Level.INFO, "100,EODClear,{0}", new Object[]{TradingUtil.getAlgoDate()});
        for (BeanSymbol s : Parameters.symbol) {
            s.clear();
            closePriceReceived.put(s.getSerialno() - 1, Boolean.FALSE);
        }
        mEsperEvtProcessor.destroy();
        mEsperEvtProcessor = new EventProcessor(this);
        mEsperEvtProcessor.ADRStatement.addListener(this);
        CurrentTimeEvent timeEvent = new CurrentTimeEvent(TradingUtil.getAlgoDate().getTime());
        mEsperEvtProcessor.sendEvent(timeEvent);
        //mEsperEvtProcessor.sendEvent(new FlushEvent(0));
        //adjust future expiry
        String expiry = sdf.format(TradingUtil.getAlgoDate());
        String expectedExpiry = null;
        Date ad = TradingUtil.getAlgoDate();
        if (ad.after(sdf.parse("20140530")) && ad.before(sdf.parse("20140626"))) {
            expectedExpiry = "20140626";
        } else if (ad.after(sdf.parse("20140626")) && ad.before(sdf.parse("20140731"))) {
            expectedExpiry = "20140731";
        }
        if (ad.after(sdf.parse("20140731")) && ad.before(sdf.parse("20140828"))) {
            expectedExpiry = "20140828";
        }
        if (ad.after(sdf.parse("20140828")) && ad.before(sdf.parse("20140925"))) {
            expectedExpiry = "20140925";
        }
        if (ad.after(sdf.parse("20140925")) && ad.before(sdf.parse("20141030"))) {
            expectedExpiry = "20141030";
        }
        if (ad.after(sdf.parse("20141030")) && ad.before(sdf.parse("20141127"))) {
            expectedExpiry = "20141127";
        }
        if (ad.after(sdf.parse("20141127")) && ad.before(sdf.parse("20141224"))) {
            expectedExpiry = "20141224";
        }
        Parameters.symbol.get(0).setExpiry(expectedExpiry);
        this.setExpiry(expectedExpiry);
        adr = 0;
        adrTRINVolume = 0;
        tick = 0;
        tickTRIN = 0;
        adrDayHigh = Double.MIN_VALUE;
        adrDayLow = Double.MAX_VALUE;
        adrHigh = 0;
        adrLow = 0;
        adrAvg = 0;
        adrTRINVOLUMEHigh = 0;
        adrTRINVOLUMELow = 0;
        adrTRINVOLUMEAvg = 0;
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
        trailingTPActive = false;
        stopLossHit = false;
        //getTrades().hashStore.clear();
        long memoryNow = Runtime.getRuntime().freeMemory();
        System.gc();
        long memoryLater = Runtime.getRuntime().freeMemory();
        long memoryCleared = memoryNow - memoryLater;
        System.out.println("Memory cleared:" + memoryCleared);

    }

    private void clearVariablesBOD() {
        logger.log(Level.INFO, "100,BODClear,{0}", new Object[]{TradingUtil.getAlgoDate()});


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
                    db.setHash("indicators", "nifty", "adrhigh", String.valueOf(adrHigh));
                    db.setHash("indicators", "nifty", "adrlow", String.valueOf(adrLow));
                    db.setHash("indicators", "nifty", "adravg", String.valueOf(adrAvg));

                    break;
                case ADRTickType.D_TRIN_VOLUME:
                    adrTRINVOLUMEHigh = high;
                    adrTRINVOLUMELow = low;
                    adrTRINVOLUMEAvg = average;
                    db.setHash("indicators", "nifty", "adrtrinvolumehigh", String.valueOf(adrTRINVOLUMEHigh));
                    db.setHash("indicators", "nifty", "adrtrinvolumelow", String.valueOf(adrTRINVOLUMELow));
                    db.setHash("indicators", "nifty", "adrtrinvolumeavg", String.valueOf(adrTRINVOLUMEAvg));
                    break;
                case ADRTickType.D_TRIN_VALUE:
                    adrTRINVALUEHigh = high;
                    adrTRINVALUELow = low;
                    adrTRINVALUEAvg = average;
                    db.setHash("indicators", "nifty", "adrtrinvaluehigh", String.valueOf(adrTRINVALUEHigh));
                    db.setHash("indicators", "nifty", "adrtrinvaluelow", String.valueOf(adrTRINVALUELow));
                    db.setHash("indicators", "nifty", "adrtrinvalueavg", String.valueOf(adrTRINVALUEAvg));
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

    /**
     * @return the trackLosingZone
     */
    public boolean isTrackLosingZone() {
        return trackLosingZone;
    }

    /**
     * @param trackLosingZone the trackLosingZone to set
     */
    public void setTrackLosingZone(boolean trackLosingZone) {
        this.trackLosingZone = trackLosingZone;
    }

    /**
     * @return the scaleoutTargets
     */
    public Double[] getScaleoutTargets() {
        return scaleoutTargets;
    }

    /**
     * @param scaleoutTargets the scaleoutTargets to set
     */
    public void setScaleoutTargets(Double[] scaleoutTargets) {
        this.scaleoutTargets = scaleoutTargets;
    }

    /**
     * @return the scaleOutSizes
     */
    public Integer[] getScaleOutSizes() {
        return scaleOutSizes;
    }

    /**
     * @param scaleOutSizes the scaleOutSizes to set
     */
    public void setScaleOutSizes(Integer[] scaleOutSizes) {
        this.scaleOutSizes = scaleOutSizes;
    }
}
