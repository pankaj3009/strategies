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
import com.incurrency.framework.BrokerageRate;
import com.incurrency.framework.DateUtil;
import com.incurrency.framework.EnumOrderIntent;
import com.incurrency.framework.EnumOrderSide;
import com.incurrency.framework.OrderLink;
import com.incurrency.framework.Parameters;
import com.incurrency.framework.ProfitLossManager;
import com.incurrency.framework.Trade;
import com.incurrency.framework.TradeEvent;
import com.incurrency.framework.TradeListener;
import com.incurrency.framework.TradingUtil;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.supercsv.io.CsvBeanWriter;
import org.supercsv.prefs.CsvPreference;

/**
 * Generates ADR, Tick , TRIN 
 * initialise ADR passing the adrSymbols that need to be tracked.
 * ADR will immediately initiate polling market data in snapshot mode. Streaming mode is currently not supported
 * output is available via static fields
 * Advances, Declines, Tick Advances, Tick Declines, Advancing Volume, Declining Volume, Tick Advancing Volume, Tick Declining Volume
 * 
 * @author pankaj
 */
public class ADR implements TradeListener,UpdateListener{
    
    static EventProcessor mEsperEvtProcessor = null;
    List<Integer> adrSymbols=new ArrayList();
    static com.incurrency.framework.rateserver.RateServer adrServer= new com.incurrency.framework.rateserver.RateServer(5557);
    private static final Logger logger = Logger.getLogger(ADR.class.getName());
    boolean trading=false;
    String index;
    String type;
    String expiry;
    public static int threshold=1000000;
    double stopLoss=5;
    double takeProfit=10;
    double entryPrice=0;
    static String window;
    double windowHurdle;
    double dayHurdle;
    double lastLongExit;
    double lastShortExit;
    double reentryMinimumMove;
    boolean scalpingMode=true;
    
    //--common parameters required for all strategies
    MainAlgorithm m;
    HashMap <Integer,Integer> internalOpenOrders=new HashMap(); //holds mapping of symbol id to latest initialization internal order
    HashMap<OrderLink,Trade> trades=new HashMap();    
    double tickSize;    
    double pointValue=1;
    int internalOrderID=1;  
    int numberOfContracts=0;
    Date endDate;
    Date startDate;
    private Boolean longOnly = true;
    private Boolean shortOnly = true;
    private Boolean aggression = true;
    private double clawProfitTarget=0;
    private double dayProfitTarget=0;
    private double dayStopLoss=0;
    private double maxSlippageEntry=0;
    private double maxSlippageExit=0;
    private int maxOrderDuration=3;
    private int dynamicOrderDuration=1;
    private String futBrokerageFile;
    private ArrayList <BrokerageRate> brokerageRate =new ArrayList<>();
    private String tradeFile;
    private String orderFile;
    private String timeZone;
    private double startingCapital;   
    
    //----- updated by ADRListener and TickListener
    public static double adr;
    public static double adrTRIN;
    static double tick;
    static double tickTRIN;
    public static double adrDayHigh=Double.MIN_VALUE;
    public static double adrDayLow=Double.MAX_VALUE;
    
//----- updated by method updateListener    
    double adrHigh;
    double adrLow;
    public static double adrAvg;
    double adrTRINHigh;
    double adrTRINLow;
    static public double adrTRINAvg;
    double tickHigh;
    double tickLow;
    double tickAvg;
    double tickTRINHigh;
    double tickTRINLow;
    double tickTRINAvg;
    double indexHigh;
    double indexLow;
    double indexAvg;
    double indexDayHigh=Double.MIN_VALUE;
    double indexDayLow=Double.MAX_VALUE;
    int position=0;
    int tradingSide=0;
    private com.incurrency.framework.OrderPlacement omsADR;
    private ProfitLossManager plmanager;


    
    public ADR(MainAlgorithm m){
        this.m=m;
        loadParameters();
        TradingUtil.writeToFile("ADR.csv", "adr"+","+"adrTRIN"+","+"tick"+","+"tickTRIN"+","+"price"+","+"adrHigh"+","+"adrLow"+","+"adrAvg"+","+"adrTRINHigh"+","+"adrTRINLow"+","+"adrTRINAvg"+","+"indexHigh"+","+"indexLow"+","+"indexAvg"+","+"indexDayHigh"+ ","+"indexDayLow"+"," + "buyZone1"+ "," + "buyZone2"+ "," + "buyZone3"+ "," + "shortZone1"+ "," + "shortZone2"+ "," + "shortZone3"+ "," + "buyZone"+ "," + "shortZone");
        mEsperEvtProcessor = new EventProcessor();
        mEsperEvtProcessor.ADRStatement.addListener(this);
           //populate adrList with adrSymbols needed for ADR
            for (BeanSymbol s : Parameters.symbol) {
                if (Pattern.compile(Pattern.quote("ADR"), Pattern.CASE_INSENSITIVE).matcher(s.getStrategy()).find()) {
                    adrSymbols.add(s.getSerialno()-1);
                }
            }
        omsADR=new ADROrderManagement(aggression,this.tickSize,endDate,"adr",pointValue,timeZone);
        for(BeanConnection c: Parameters.connection){
        c.getWrapper().addTradeListener(this);
        c.initializeConnection("adr");
        }
        if (Subscribe.tes!=null){
            Subscribe.tes.addTradeListener(this);
        }
        plmanager=new ProfitLossManager("adr", this.adrSymbols, pointValue, clawProfitTarget,dayProfitTarget,dayStopLoss);
        Timer closeProcessing=new Timer("Timer: ADR CloseProcessing");
        closeProcessing.schedule(runPrintOrders, com.incurrency.framework.DateUtil.addSeconds(endDate, (this.maxOrderDuration+1)*60));
    }
    
    private void loadParameters() {
        Properties p = new Properties(System.getProperties());
        FileInputStream propFile;
        try {
            propFile = new FileInputStream(MainAlgorithm.input.get("adr"));
            try {
                p.load(propFile);
            } catch (Exception ex) {
                logger.log(Level.SEVERE, null, ex);
            }
        } catch (Exception ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        System.setProperties(p);   
        String currDateStr = DateUtil.getFormatedDate("yyyyMMdd", Parameters.connection.get(0).getConnectionTime());
        String endDateStr = currDateStr + " " + System.getProperty("EndTime");
        endDate=DateUtil.parseDate("yyyyMMdd HH:mm:ss", endDateStr);
        if (new Date().compareTo(endDate) > 0) {
            //increase enddate by one calendar day
            endDate = DateUtil.addDays(endDate, 1); 
        }
        setMaxSlippageEntry(Double.parseDouble(System.getProperty("MaxSlippageEntry"))/100); // divide by 100 as input was a percentage
        setMaxSlippageExit(Double.parseDouble(System.getProperty("MaxSlippageExit"))/100); // divide by 100 as input was a percentage
        setMaxOrderDuration(Integer.parseInt(System.getProperty("MaxOrderDuration")));
        setDynamicOrderDuration(Integer.parseInt(System.getProperty("DynamicOrderDuration")));        
        m.setCloseDate(DateUtil.addSeconds(endDate, (this.maxOrderDuration+2)*60)); //2 minutes after the enddate+max order duaration
        trading=Boolean.valueOf(System.getProperty("Trading"));
        index=System.getProperty("Index");
        type=System.getProperty("Type");
        expiry=System.getProperty("Expiry")==null?"":System.getProperty("Expiry");
        tickSize=Double.parseDouble(System.getProperty("TickSize"));
        threshold=Integer.parseInt(System.getProperty("Threshold"));
        numberOfContracts=Integer.parseInt(System.getProperty("NumberOfContracts"));
        stopLoss=Double.parseDouble(System.getProperty("StopLoss"));
        window=System.getProperty("Window");
        windowHurdle=Double.parseDouble(System.getProperty("WindowHurdle"));
        dayHurdle=Double.parseDouble(System.getProperty("DayHurdle"));
        takeProfit=Double.parseDouble(System.getProperty("TakeProfit"));
        pointValue="".compareTo(System.getProperty("PointValue"))==0?1:Double.parseDouble(System.getProperty("PointValue"));
        clawProfitTarget=System.getProperty("ClawProfitTarget")!=null?Double.parseDouble(System.getProperty("ClawProfitTarget")):0D;
        dayProfitTarget=System.getProperty("DayProfitTarget")!=null?Double.parseDouble(System.getProperty("DayProfitTarget")):0D;
        dayStopLoss=System.getProperty("DayStopLoss")!=null?Double.parseDouble(System.getProperty("DayStopLoss")):0D;
        futBrokerageFile=System.getProperty("BrokerageFile")==null?"":System.getProperty("BrokerageFile");
        tradeFile=System.getProperty("TradeFile");
        orderFile=System.getProperty("OrderFile");
        timeZone=System.getProperty("TradeTimeZone")==null?"":System.getProperty("TradeTimeZone");  
        startingCapital=System.getProperty("StartingCapital")==null?0D:Double.parseDouble(System.getProperty("StartingCapital"));  
        scalpingMode=System.getProperty("ScalpingMode")==null?false:Boolean.parseBoolean(System.getProperty("ScalpingMode"));
        reentryMinimumMove=System.getProperty("ReentryMinimumMove")==null?0D:Double.parseDouble(System.getProperty("ReentryMinimumMove"));
        
        
        logger.log(Level.INFO, "-----ADR Parameters----");
        logger.log(Level.INFO, "end Time: {0}", endDate);
        logger.log(Level.INFO, "Print Time: {0}", com.incurrency.framework.DateUtil.addSeconds(endDate, (this.maxOrderDuration+1)*60));        
        logger.log(Level.INFO, "ShutDown time: {0}",DateUtil.addSeconds(endDate, (this.maxOrderDuration+2)*60));
        logger.log(Level.INFO, "Setup to Trade: {0}", trading);
        logger.log(Level.INFO, "Traded Index: {0}", index);
        logger.log(Level.INFO, "Index Type: {0}", type);
        logger.log(Level.INFO, "Index Contract Expiry: {0}", expiry);
        logger.log(Level.INFO, "TickSize: {0}", tickSize);
        logger.log(Level.INFO, "Number of quotes before data collection: {0}", threshold);
        logger.log(Level.INFO, "Number of contracts to be traded: {0}", numberOfContracts);
        logger.log(Level.INFO, "Stop Loss: {0}", stopLoss);
        logger.log(Level.INFO, "Take Profit: {0}", takeProfit);
        logger.log(Level.INFO, "Sliding Window Duration: {0}", window);
        logger.log(Level.INFO, "Hurdle Index move needed for window duration: {0}", windowHurdle);
        logger.log(Level.INFO, "Hurdle Index move needed for day: {0}", dayHurdle);
        logger.log(Level.INFO, "Claw Profit in increments of: {0}", clawProfitTarget);
        logger.log(Level.INFO, "Day Profit Target: {0}", dayProfitTarget);
        logger.log(Level.INFO, "Day Stop Loss: {0}", dayStopLoss);        
        logger.log(Level.INFO, "PointValue: {0}", pointValue);
        logger.log(Level.INFO, "Maxmimum slippage allowed for entry: {0}", getMaxSlippageEntry());
        logger.log(Level.INFO, "Maximum slippage allowed for exit: {0}", getMaxSlippageExit());
        logger.log(Level.INFO, "Max Order Duration: {0}", getMaxOrderDuration());
        logger.log(Level.INFO, "Dynamic Order Duration: {0}", getDynamicOrderDuration());
        logger.log(Level.INFO, "Brokerage File: {0}", futBrokerageFile);
        logger.log(Level.INFO, "Trade File: {0}", tradeFile);
        logger.log(Level.INFO, "Order File: {0}", orderFile);
        logger.log(Level.INFO, "Time Zone: {0}", timeZone);
        logger.log(Level.INFO, "Starting Capital: {0}", startingCapital);
        logger.log(Level.INFO, "Scalping Mode: {0}", scalpingMode);        
        logger.log(Level.INFO, "Minimum move before re-entry: {0}", reentryMinimumMove);        
        if(futBrokerageFile.compareTo("")!=0){
            try {
                p.clear();
                //retrieve parameters from brokerage file
                 propFile = new FileInputStream(futBrokerageFile);
                try {
                    p.load(propFile);
                    System.setProperties(p); 
                } catch (IOException ex) {
                    logger.log(Level.SEVERE, null, ex);
                }
            } catch (Exception ex) {
                logger.log(Level.SEVERE, null, ex);
            }
            String brokerage1=System.getProperty("Brokerage");
            String addOn1=System.getProperty("AddOn1");
            String addOn2=System.getProperty("AddOn2");
            String addOn3=System.getProperty("AddOn3");
            String addOn4=System.getProperty("AddOn4");
            
            if(brokerage1!=null){
                getBrokerageRate().add(TradingUtil.parseBrokerageString(brokerage1, "FUT"));
            }
            if(addOn1!=null){
                getBrokerageRate().add(TradingUtil.parseBrokerageString(addOn1, "FUT"));
            }
            if(addOn2!=null){
                getBrokerageRate().add(TradingUtil.parseBrokerageString(addOn2, "FUT"));
            }
            if(addOn3!=null){
                getBrokerageRate().add(TradingUtil.parseBrokerageString(addOn3, "FUT"));
            }
            if(addOn4!=null){
                getBrokerageRate().add(TradingUtil.parseBrokerageString(addOn4, "FUT"));
            }           
               
        }

    }

    @Override
    public void tradeReceived(TradeEvent event) {
        int id = event.getSymbolID(); //zero based id
        if (adrSymbols.contains(id)) {
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
                    //System.out.println("LAST, Symbol:"+Parameters.symbol.get(id).getSymbol()+" Value: "+Parameters.symbol.get(id).getLastPrice()+" tickerID: "+id);
                    mEsperEvtProcessor.sendEvent(new TickPriceEvent(id, com.ib.client.TickType.LAST, Parameters.symbol.get(id).getLastPrice()));
                    //mEsperEvtProcessor.debugFireTickQuery();
                    //mEsperEvtProcessor.debugFireADRQuery();
                    break;
                case com.ib.client.TickType.CLOSE:
                    //System.out.println("CLOSE, Symbol:"+Parameters.symbol.get(id).getSymbol()+" Value: "+Parameters.symbol.get(id).getClosePrice()+" tickerID: "+id);
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
            boolean buyZone1 = ((adrHigh - adrLow > 5 && adr > adrLow + 0.75 * (adrHigh - adrLow) && adr > adrAvg)
                    || (adrDayHigh - adrDayLow > 10 && adr > adrDayLow + 0.75 * (adrDayHigh - adrDayLow) && adr > adrAvg));// && adrTRIN < 90;
            boolean buyZone2 = ((indexHigh - indexLow > windowHurdle && price > indexLow + 0.75 * (indexHigh - indexLow) && price > indexAvg)
                    || (indexDayHigh - indexDayLow > dayHurdle && price > indexDayLow + 0.75 * (indexDayHigh - indexDayLow) && price > indexAvg));// && adrTRIN < 90;
            boolean buyZone3 = ADR.adrTRINAvg < 90 && ADR.adrTRINAvg > 0;

            boolean shortZone1 = ((adrHigh - adrLow > 5 && adr < adrHigh - 0.75 * (adrHigh - adrLow) && adr < adrAvg)
                    || (adrDayHigh - adrDayLow > 10 && adr < adrDayHigh - 0.75 * (adrDayHigh - adrDayLow) && adr < adrAvg));// && adrTRIN > 95;
            boolean shortZone2 = ((indexHigh - indexLow > windowHurdle && price < indexHigh - 0.75 * (indexHigh - indexLow) && price < indexAvg)
                    || (indexDayHigh - indexDayLow > dayHurdle && price < indexDayHigh - 0.75 * (indexDayHigh - indexDayLow) && price < indexAvg));// && adrTRIN > 95;
            boolean shortZone3 = ADR.adrTRINAvg > 95;

            Boolean buyZone = (atLeastTwo(buyZone1, buyZone2, buyZone3) && adrTRIN<90 )|| (atLeastTwo(buyZone1, buyZone2, buyZone3)&&adr > adrAvg && adrTRIN < adrTRINAvg);
            Boolean shortZone = (atLeastTwo(shortZone1, shortZone2, shortZone3) && adrTRIN>95)||(atLeastTwo(shortZone1, shortZone2, shortZone3)&& adr<adrAvg && adrTRIN>adrTRINAvg);
            TradingUtil.writeToFile("ADR.csv", adr + "," + adrTRIN + "," + tick + "," + tickTRIN + "," + price + "," + adrHigh + "," + adrLow + "," + adrAvg + "," + adrTRINHigh + "," + adrTRINLow + "," + adrTRINAvg + "," + indexHigh + "," + indexLow + "," + indexAvg+ "," + indexDayHigh+ "," + indexDayLow+"," + buyZone1+ "," + buyZone2+ "," + buyZone3+ "," + shortZone1+ "," + shortZone2+ "," + shortZone3+ "," + buyZone+ "," + shortZone);
            logger.log(Level.FINEST, " adrHigh: {0},adrLow: {1},adrAvg: {2},adrTRINHigh: {3},adrTRINLow: {4},adrTRINAvg: {5},indexHigh :{6},indexLow :{7},indexAvg: {8}, buyZone1: {9}, buyZone2: {10}, buyZone 3: {11}, shortZone1: {12}, shortZone2: {13}, ShortZone3:{14}, ADR: {15}, ADRTrin: {16}, Tick: {17}, TickTrin: {18}, adrDayHigh: {19}, adrDayLow: {20}, IndexDayHigh: {21}, IndexDayLow: {22}", new Object[]{adrHigh, adrLow, adrAvg, adrTRINHigh, adrTRINLow, adrTRINAvg, indexHigh, indexLow, indexAvg, buyZone1, buyZone2, buyZone3, shortZone1, shortZone2, shortZone3, adr, adrTRIN, tick, tickTRIN, adrDayHigh, adrDayLow, indexDayHigh, indexDayLow});
            //tickHigh,tickLow,tickAvg,tickTRINHigh,tickTRINLow,tickTRINAvg
            if((!buyZone && tradingSide==1 && position==0)||(!shortZone && tradingSide==-1 && position==0)){
                 logger.log(Level.INFO, " Strategy: ADR. adrHigh: {0},adrLow: {1},adrAvg: {2},adrTRINHigh: {3},adrTRINLow: {4},adrTRINAvg: {5},indexHigh :{6},indexLow :{7},indexAvg: {8}, buyZone1: {9}, buyZone2: {10}, buyZone 3: {11}, shortZone1: {12}, shortZone2: {13}, ShortZone3:{14}, ADR: {15}, ADRTrin: {16}, Tick: {17}, TickTrin: {18}, adrDayHigh: {19}, adrDayLow: {20}, IndexDayHigh: {21}, IndexDayLow: {22}", new Object[]{adrHigh, adrLow, adrAvg, adrTRINHigh, adrTRINLow, adrTRINAvg, indexHigh, indexLow, indexAvg, buyZone1, buyZone2, buyZone3, shortZone1, shortZone2, shortZone3, adr, adrTRIN, tick, tickTRIN, adrDayHigh, adrDayLow, indexDayHigh, indexDayLow});
                 logger.log(Level.INFO,"Trading Side Reset. New Trading Side: {0}, Earlier trading Side: {}",new Object[]{0,tradingSide});
                tradingSide=0;
            }
            
            if (position == 0  && new Date().compareTo(endDate) < 0) {
                if (tradingSide==0 && buyZone && (tick < 45 || tickTRIN > 120) && longOnly) {
                    entryPrice = price;
                    this.internalOpenOrders.put(id, internalOrderID);
                    trades.put(new OrderLink(this.internalOrderID,"Order"), new Trade(id, EnumOrderSide.BUY, entryPrice, numberOfContracts, internalOrderID++, timeZone, "Order"));
                    logger.log(Level.INFO, " Strategy: ADR. adrHigh: {0},adrLow: {1},adrAvg: {2},adrTRINHigh: {3},adrTRINLow: {4},adrTRINAvg: {5},indexHigh :{6},indexLow :{7},indexAvg: {8}, buyZone1: {9}, buyZone2: {10}, buyZone 3: {11}, shortZone1: {12}, shortZone2: {13}, ShortZone3:{14}, ADR: {15}, ADRTrin: {16}, Tick: {17}, TickTrin: {18}, adrDayHigh: {19}, adrDayLow: {20}, IndexDayHigh: {21}, IndexDayLow: {22}", new Object[]{adrHigh, adrLow, adrAvg, adrTRINHigh, adrTRINLow, adrTRINAvg, indexHigh, indexLow, indexAvg, buyZone1, buyZone2, buyZone3, shortZone1, shortZone2, shortZone3, adr, adrTRIN, tick, tickTRIN, adrDayHigh, adrDayLow, indexDayHigh, indexDayLow});
                    logger.log(Level.INFO, "Strategy: ADR. Buy Order. Price: {0}", new Object[]{price});
                    getOmsADR().tes.fireOrderEvent(internalOrderID - 1, internalOrderID - 1, Parameters.symbol.get(id), EnumOrderSide.BUY, numberOfContracts, price, 0, "adr", 3, "", EnumOrderIntent.Init, getMaxOrderDuration(), getDynamicOrderDuration(), getMaxSlippageEntry());
                    position = 1;
                    tradingSide = 1; 
                } else if (tradingSide==0 && shortZone && (tick > 55 || tickTRIN < 80) && shortOnly) {
                    entryPrice = price;
                    this.internalOpenOrders.put(id, internalOrderID);
                    trades.put(new OrderLink(this.internalOrderID,"Order"), new Trade(id, EnumOrderSide.SHORT, entryPrice, numberOfContracts, internalOrderID++, timeZone, "Order"));
                    logger.log(Level.INFO, " Strategy: ADR. adrHigh: {0},adrLow: {1},adrAvg: {2},adrTRINHigh: {3},adrTRINLow: {4},adrTRINAvg: {5},indexHigh :{6},indexLow :{7},indexAvg: {8}, buyZone1: {9}, buyZone2: {10}, buyZone 3: {11}, shortZone1: {12}, shortZone2: {13}, ShortZone3:{14}, ADR: {15}, ADRTrin: {16}, Tick: {17}, TickTrin: {18}, adrDayHigh: {19}, adrDayLow: {20}, IndexDayHigh: {21}, IndexDayLow: {22}", new Object[]{adrHigh, adrLow, adrAvg, adrTRINHigh, adrTRINLow, adrTRINAvg, indexHigh, indexLow, indexAvg, buyZone1, buyZone2, buyZone3, shortZone1, shortZone2, shortZone3, adr, adrTRIN, tick, tickTRIN, adrDayHigh, adrDayLow, indexDayHigh, indexDayLow});
                    logger.log(Level.INFO, "Strategy: ADR. Short Order. Price: {0}", new Object[]{price});
                    getOmsADR().tes.fireOrderEvent(internalOrderID - 1, internalOrderID - 1, Parameters.symbol.get(id), EnumOrderSide.SHORT, numberOfContracts, price, 0, "adr", 3, "", EnumOrderIntent.Init, getMaxOrderDuration(), getDynamicOrderDuration(), getMaxSlippageEntry());
                    position = -1;
                    tradingSide = -1;
                 } else if (tradingSide == 1 && price < this.lastLongExit - this.reentryMinimumMove && scalpingMode && this.lastLongExit>0) { //used in scalping mode
                    entryPrice = price;
                    this.internalOpenOrders.put(id, internalOrderID);
                    trades.put(new OrderLink(this.internalOrderID,"Order"), new Trade(id, EnumOrderSide.BUY, entryPrice, numberOfContracts, internalOrderID++, timeZone, "Order"));
                    logger.log(Level.INFO, " Strategy: ADR. adrHigh: {0},adrLow: {1},adrAvg: {2},adrTRINHigh: {3},adrTRINLow: {4},adrTRINAvg: {5},indexHigh :{6},indexLow :{7},indexAvg: {8}, buyZone1: {9}, buyZone2: {10}, buyZone 3: {11}, shortZone1: {12}, shortZone2: {13}, ShortZone3:{14}, ADR: {15}, ADRTrin: {16}, Tick: {17}, TickTrin: {18}, adrDayHigh: {19}, adrDayLow: {20}, IndexDayHigh: {21}, IndexDayLow: {22}", new Object[]{adrHigh, adrLow, adrAvg, adrTRINHigh, adrTRINLow, adrTRINAvg, indexHigh, indexLow, indexAvg, buyZone1, buyZone2, buyZone3, shortZone1, shortZone2, shortZone3, adr, adrTRIN, tick, tickTRIN, adrDayHigh, adrDayLow, indexDayHigh, indexDayLow});
                    logger.log(Level.INFO, "Strategy: ADR. Scalping Buy Order. Price: {0}", new Object[]{price});
                    getOmsADR().tes.fireOrderEvent(internalOrderID - 1, internalOrderID - 1, Parameters.symbol.get(id), EnumOrderSide.BUY, numberOfContracts, price, 0, "adr", 3, "", EnumOrderIntent.Init, getMaxOrderDuration(), getDynamicOrderDuration(), getMaxSlippageEntry());
                    position = 1;
                } else if (tradingSide == -1 && price > this.lastShortExit + this.reentryMinimumMove && scalpingMode && this.lastShortExit>0) {
                    entryPrice = price;
                    this.internalOpenOrders.put(id, internalOrderID);
                    trades.put(new OrderLink(this.internalOrderID,"Order"), new Trade(id, EnumOrderSide.SHORT, entryPrice, numberOfContracts, internalOrderID++, timeZone, "Order"));
                    logger.log(Level.INFO, " Strategy: ADR. adrHigh: {0},adrLow: {1},adrAvg: {2},adrTRINHigh: {3},adrTRINLow: {4},adrTRINAvg: {5},indexHigh :{6},indexLow :{7},indexAvg: {8}, buyZone1: {9}, buyZone2: {10}, buyZone 3: {11}, shortZone1: {12}, shortZone2: {13}, ShortZone3:{14}, ADR: {15}, ADRTrin: {16}, Tick: {17}, TickTrin: {18}, adrDayHigh: {19}, adrDayLow: {20}, IndexDayHigh: {21}, IndexDayLow: {22}", new Object[]{adrHigh, adrLow, adrAvg, adrTRINHigh, adrTRINLow, adrTRINAvg, indexHigh, indexLow, indexAvg, buyZone1, buyZone2, buyZone3, shortZone1, shortZone2, shortZone3, adr, adrTRIN, tick, tickTRIN, adrDayHigh, adrDayLow, indexDayHigh, indexDayLow});
                    logger.log(Level.INFO, "Strategy: ADR. Scalping Short Order. Price: {0}", new Object[]{price});
                    getOmsADR().tes.fireOrderEvent(internalOrderID - 1, internalOrderID - 1, Parameters.symbol.get(id), EnumOrderSide.SHORT, numberOfContracts, price, 0, "adr", 3, "", EnumOrderIntent.Init, getMaxOrderDuration(), getDynamicOrderDuration(), getMaxSlippageEntry());
                    position = -1;
                }
            } else if (position == -1) {
                if (buyZone || (price > indexLow + stopLoss && !shortZone) || new Date().compareTo(endDate) > 0) { //stop loss
                    int tempinternalOrderID = internalOpenOrders.get(id);
                    Trade tempTrade = trades.get(new OrderLink(tempinternalOrderID,"Order"));
                    tempTrade.updateExit(id, EnumOrderSide.COVER, price, numberOfContracts, internalOrderID++, timeZone, "Order");
                    logger.log(Level.INFO, " Strategy: ADR. adrHigh: {0},adrLow: {1},adrAvg: {2},adrTRINHigh: {3},adrTRINLow: {4},adrTRINAvg: {5},indexHigh :{6},indexLow :{7},indexAvg: {8}, buyZone1: {9}, buyZone2: {10}, buyZone 3: {11}, shortZone1: {12}, shortZone2: {13}, ShortZone3:{14}, ADR: {15}, ADRTrin: {16}, Tick: {17}, TickTrin: {18}, adrDayHigh: {19}, adrDayLow: {20}, IndexDayHigh: {21}, IndexDayLow: {22}", new Object[]{adrHigh, adrLow, adrAvg, adrTRINHigh, adrTRINLow, adrTRINAvg, indexHigh, indexLow, indexAvg, buyZone1, buyZone2, buyZone3, shortZone1, shortZone2, shortZone3, adr, adrTRIN, tick, tickTRIN, adrDayHigh, adrDayLow, indexDayHigh, indexDayLow});
                    logger.log(Level.INFO, "Strategy: ADR. Cover Order. StopLoss. Price: {0}", new Object[]{price});
                    getOmsADR().tes.fireOrderEvent(internalOrderID - 1, tempinternalOrderID, Parameters.symbol.get(id), EnumOrderSide.COVER, numberOfContracts, price, 0, "adr", 3, "", EnumOrderIntent.Init, getMaxOrderDuration(), getDynamicOrderDuration(), getMaxSlippageExit());
                    position = 0;
                    tradingSide = 0;
                 } else if ((scalpingMode || !shortZone) && (price < entryPrice - takeProfit)) {
                    int tempinternalOrderID = internalOpenOrders.get(id);
                    Trade tempTrade = trades.get(new OrderLink(tempinternalOrderID,"Order"));
                    tempTrade.updateExit(id, EnumOrderSide.COVER, price, numberOfContracts, internalOrderID++, timeZone, "Order");
                    logger.log(Level.INFO, " Strategy: ADR. adrHigh: {0},adrLow: {1},adrAvg: {2},adrTRINHigh: {3},adrTRINLow: {4},adrTRINAvg: {5},indexHigh :{6},indexLow :{7},indexAvg: {8}, buyZone1: {9}, buyZone2: {10}, buyZone 3: {11}, shortZone1: {12}, shortZone2: {13}, ShortZone3:{14}, ADR: {15}, ADRTrin: {16}, Tick: {17}, TickTrin: {18}, adrDayHigh: {19}, adrDayLow: {20}, IndexDayHigh: {21}, IndexDayLow: {22}", new Object[]{adrHigh, adrLow, adrAvg, adrTRINHigh, adrTRINLow, adrTRINAvg, indexHigh, indexLow, indexAvg, buyZone1, buyZone2, buyZone3, shortZone1, shortZone2, shortZone3, adr, adrTRIN, tick, tickTRIN, adrDayHigh, adrDayLow, indexDayHigh, indexDayLow});
                    logger.log(Level.INFO, " Strategy: ADR. Cover Order. TakeProfit. Price: {0}", new Object[]{price});
                    getOmsADR().tes.fireOrderEvent(internalOrderID - 1, tempinternalOrderID, Parameters.symbol.get(id), EnumOrderSide.COVER, numberOfContracts, price, 0, "adr", 3, "", EnumOrderIntent.Init, getMaxOrderDuration(), getDynamicOrderDuration(), getMaxSlippageExit());
                    position = 0;
                    lastShortExit = price;
                  }
            } else if (position == 1) {
                if (shortZone || (price < indexHigh - stopLoss && !buyZone) || new Date().compareTo(endDate) > 0) {
                    int tempinternalOrderID = internalOpenOrders.get(id);
                    Trade tempTrade = trades.get(new OrderLink(tempinternalOrderID,"Order"));
                    tempTrade.updateExit(id, EnumOrderSide.SELL, price, numberOfContracts, internalOrderID++, timeZone, "Order");
                    logger.log(Level.INFO, " Strategy: ADR. adrHigh: {0},adrLow: {1},adrAvg: {2},adrTRINHigh: {3},adrTRINLow: {4},adrTRINAvg: {5},indexHigh :{6},indexLow :{7},indexAvg: {8}, buyZone1: {9}, buyZone2: {10}, buyZone 3: {11}, shortZone1: {12}, shortZone2: {13}, ShortZone3:{14}, ADR: {15}, ADRTrin: {16}, Tick: {17}, TickTrin: {18}, adrDayHigh: {19}, adrDayLow: {20}, IndexDayHigh: {21}, IndexDayLow: {22}", new Object[]{adrHigh, adrLow, adrAvg, adrTRINHigh, adrTRINLow, adrTRINAvg, indexHigh, indexLow, indexAvg, buyZone1, buyZone2, buyZone3, shortZone1, shortZone2, shortZone3, adr, adrTRIN, tick, tickTRIN, adrDayHigh, adrDayLow, indexDayHigh, indexDayLow});
                    logger.log(Level.INFO, " Strategy: ADR. Sell Order. StopLoss. Price: {0}", new Object[]{price});
                    getOmsADR().tes.fireOrderEvent(internalOrderID - 1, tempinternalOrderID, Parameters.symbol.get(id), EnumOrderSide.SELL, numberOfContracts, price, 0, "adr", 3, "", EnumOrderIntent.Init, getMaxOrderDuration(), getDynamicOrderDuration(), getMaxSlippageExit());
                    position = 0;
                 } else if ((scalpingMode || !buyZone) && price > entryPrice + takeProfit) {
                    int tempinternalOrderID = internalOpenOrders.get(id);
                    Trade tempTrade = trades.get(new OrderLink(tempinternalOrderID,"Order"));
                    tempTrade.updateExit(id, EnumOrderSide.SELL, price, numberOfContracts, internalOrderID++, timeZone, "Order");
                    logger.log(Level.INFO, " Strategy: ADR. adrHigh: {0},adrLow: {1},adrAvg: {2},adrTRINHigh: {3},adrTRINLow: {4},adrTRINAvg: {5},indexHigh :{6},indexLow :{7},indexAvg: {8}, buyZone1: {9}, buyZone2: {10}, buyZone 3: {11}, shortZone1: {12}, shortZone2: {13}, ShortZone3:{14}, ADR: {15}, ADRTrin: {16}, Tick: {17}, TickTrin: {18}, adrDayHigh: {19}, adrDayLow: {20}, IndexDayHigh: {21}, IndexDayLow: {22}", new Object[]{adrHigh, adrLow, adrAvg, adrTRINHigh, adrTRINLow, adrTRINAvg, indexHigh, indexLow, indexAvg, buyZone1, buyZone2, buyZone3, shortZone1, shortZone2, shortZone3, adr, adrTRIN, tick, tickTRIN, adrDayHigh, adrDayLow, indexDayHigh, indexDayLow});
                    logger.log(Level.INFO, "Strategy ADR. Sell Order. TakeProfit. Price: {0}", new Object[]{price});
                    getOmsADR().tes.fireOrderEvent(internalOrderID - 1, tempinternalOrderID, Parameters.symbol.get(id), EnumOrderSide.SELL, numberOfContracts, price, 0, "adr", 3, "", EnumOrderIntent.Init, getMaxOrderDuration(), getDynamicOrderDuration(), getMaxSlippageExit());
                    position = 0;
                    lastLongExit = price;
                }
            }
        }
    }

    @Override
    public void update(EventBean[] newEvents, EventBean[] oldEvents) {
       double high = newEvents[0].get("high")==null?Double.MIN_VALUE:(Double) newEvents[0].get("high");
        double low = newEvents[0].get("low")==null?Double.MAX_VALUE:(Double) newEvents[0].get("low");
        double average = newEvents[0].get("average")==null?adrAvg:(Double) newEvents[0].get("average");
        if(adr>0){
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
    
    TimerTask runPrintOrders = new TimerTask() {
        @Override
        public void run() {
            System.out.println("In Printorders");
            logger.log(Level.INFO, "Print Orders Called in ADR");
            printOrders("");
        }
    };
    
    public void printOrders(String prefix) {
        FileWriter file;
        double[] profitGrid;
        DecimalFormat df = new DecimalFormat("#.##");
        try {
            boolean writeHeader;
            String filename = prefix + orderFile;
            profitGrid = TradingUtil.applyBrokerage(trades, brokerageRate, pointValue, orderFile, timeZone, startingCapital, "Order");
            TradingUtil.writeToFile("body.txt", "-----------------Orders:ADR--------------------------------------------------");
            TradingUtil.writeToFile("body.txt", "Gross P&L today: " + df.format(profitGrid[0]));
            TradingUtil.writeToFile("body.txt", "Brokerage today: " + df.format(profitGrid[1]));
            TradingUtil.writeToFile("body.txt", "Net P&L today: " + df.format(profitGrid[2]));
            TradingUtil.writeToFile("body.txt", "MTD P&L: " + df.format(profitGrid[3]));
            TradingUtil.writeToFile("body.txt", "YTD P&L: " + df.format(profitGrid[4]));
            TradingUtil.writeToFile("body.txt", "Max Drawdown (%): " + df.format(profitGrid[5]));
            TradingUtil.writeToFile("body.txt", "Max Drawdown (days): " + df.format(profitGrid[6]));
            TradingUtil.writeToFile("body.txt", "Avg Drawdown (days): " + df.format(profitGrid[7]));
            TradingUtil.writeToFile("body.txt", "Sharpe Ratio: " + df.format(profitGrid[8]));
            TradingUtil.writeToFile("body.txt", "# days in history: " + df.format(profitGrid[9]));
            if (new File(filename).exists()) {
                writeHeader = false;
            } else {
                writeHeader = true;
            }
            file = new FileWriter(filename, true);
            String[] header = new String[]{
                "entrySymbol", "entryType", "entryExpiry", "entryRight", "entryStrike",
                "entrySide", "entryPrice", "entrySize", "entryTime", "entryID", "entryBrokerage", "filtered", "exitSymbol",
                "exitType", "exitExpiry", "exitRight", "exitStrike", "exitSide", "exitPrice",
                "exitSize", "exitTime", "exitID", "exitBrokerage","accountName"};
            CsvBeanWriter orderWriter = new CsvBeanWriter(file, CsvPreference.EXCEL_PREFERENCE);
            if (writeHeader) {//this ensures header is written only the first time
                orderWriter.writeHeader(header);
            }
            for (Map.Entry<OrderLink, Trade> order : trades.entrySet()) {
                orderWriter.write(order.getValue(), header, Parameters.getTradeProcessorsWrite());
            }
            orderWriter.close();
            logger.log(Level.INFO, "Clean Exit after writing orders");

            //Write trade summary for each account
            for (BeanConnection c : Parameters.connection) {
                if (c.getStrategy().contains("adr")) {
                    filename = prefix + tradeFile;
                    profitGrid = TradingUtil.applyBrokerage(getOmsADR().getTrades(), brokerageRate, pointValue, tradeFile, timeZone, startingCapital, c.getAccountName());
                    TradingUtil.writeToFile("body.txt", "-----------------Trades: ADR, Account: " + c.getAccountName() + "----------------------");
                    TradingUtil.writeToFile("body.txt", "Gross P&L today: " + df.format(profitGrid[0]));
                    TradingUtil.writeToFile("body.txt", "Brokerage today: " + df.format(profitGrid[1]));
                    TradingUtil.writeToFile("body.txt", "Net P&L today: " + df.format(profitGrid[2]));
                    TradingUtil.writeToFile("body.txt", "MTD P&L: " + df.format(profitGrid[3]));
                    TradingUtil.writeToFile("body.txt", "YTD P&L: " + df.format(profitGrid[4]));
                    TradingUtil.writeToFile("body.txt", "Max Drawdown (%): " + df.format(profitGrid[5]));
                    TradingUtil.writeToFile("body.txt", "Max Drawdown (days): " + df.format(profitGrid[6]));
                    TradingUtil.writeToFile("body.txt", "Avg Drawdown (days): " + df.format(profitGrid[7]));
                    TradingUtil.writeToFile("body.txt", "Sharpe Ratio: " + df.format(profitGrid[8]));
                    TradingUtil.writeToFile("body.txt", "# days in history: " + df.format(profitGrid[9]));
                }
            }
                    if (new File(filename).exists()) {
                        writeHeader = false;
                    } else {
                        writeHeader = true;
                    }
                    file = new FileWriter(filename, true);
                    CsvBeanWriter tradeWriter = new CsvBeanWriter(file, CsvPreference.EXCEL_PREFERENCE);
                    if (writeHeader) {//this ensures header is written only the first time
                        tradeWriter.writeHeader(header);
                    }
                    for (Map.Entry<OrderLink, Trade> trade : getOmsADR().getTrades().entrySet()) {
                        tradeWriter.write(trade.getValue(), header, Parameters.getTradeProcessorsWrite());
                    }
                    tradeWriter.close();
                    logger.log(Level.INFO, "Clean Exit after writing trades");
                    //System.exit(0);

        } catch (IOException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    /**
     * @return the longOnly
     */
    public Boolean getLongOnly() {
        return longOnly;
    }

    /**
     * @param longOnly the longOnly to set
     */
    public void setLongOnly(Boolean longOnly) {
        this.longOnly = longOnly;
    }

    /**
     * @return the shortOnly
     */
    public Boolean getShortOnly() {
        return shortOnly;
    }

    /**
     * @param shortOnly the shortOnly to set
     */
    public void setShortOnly(Boolean shortOnly) {
        this.shortOnly = shortOnly;
    }

    /**
     * @return the aggression
     */
    public Boolean getAggression() {
        return aggression;
    }

    /**
     * @param aggression the aggression to set
     */
    public void setAggression(Boolean aggression) {
        this.aggression = aggression;
    }

    /**
     * @return the omsADR
     */
    public com.incurrency.framework.OrderPlacement getOmsADR() {
        return omsADR;
    }

    /**
     * @param omsADR the omsADR to set
     */
    public void setOmsADR(com.incurrency.framework.OrderPlacement omsADR) {
        this.omsADR = omsADR;
    }

    /**
     * @return the profitTarget
     */
    public double getClawProfitTarget() {
        return clawProfitTarget;
    }

    /**
     * @param profitTarget the profitTarget to set
     */
    public void setClawProfitTarget(double clawProfitTarget) {
        this.clawProfitTarget = clawProfitTarget;
    }

    /**
     * @return the plmanager
     */
    public ProfitLossManager getPlmanager() {
        return plmanager;
    }

    /**
     * @param plmanager the plmanager to set
     */
    public void setPlmanager(ProfitLossManager plmanager) {
        this.plmanager = plmanager;
    }

    /**
     * @return the brokerageRate
     */
    public ArrayList <BrokerageRate> getBrokerageRate() {
        return brokerageRate;
    }

    /**
     * @param brokerageRate the brokerageRate to set
     */
    public void setBrokerageRate(ArrayList <BrokerageRate> brokerageRate) {
        this.brokerageRate = brokerageRate;
    }

    /**
     * @return the maxSlippageEntry
     */
    public double getMaxSlippageEntry() {
        return maxSlippageEntry;
    }

    /**
     * @param maxSlippageEntry the maxSlippageEntry to set
     */
    public void setMaxSlippageEntry(double maxSlippageEntry) {
        this.maxSlippageEntry = maxSlippageEntry;
    }

    /**
     * @return the maxSlippageExit
     */
    public double getMaxSlippageExit() {
        return maxSlippageExit;
    }

    /**
     * @param maxSlippageExit the maxSlippageExit to set
     */
    public void setMaxSlippageExit(double maxSlippageExit) {
        this.maxSlippageExit = maxSlippageExit;
    }

    /**
     * @return the maxOrderDuration
     */
    public int getMaxOrderDuration() {
        return maxOrderDuration;
    }

    /**
     * @param maxOrderDuration the maxOrderDuration to set
     */
    public void setMaxOrderDuration(int maxOrderDuration) {
        this.maxOrderDuration = maxOrderDuration;
    }

    /**
     * @return the dynamicOrderDuration
     */
    public int getDynamicOrderDuration() {
        return dynamicOrderDuration;
    }

    /**
     * @param dynamicOrderDuration the dynamicOrderDuration to set
     */
    public void setDynamicOrderDuration(int dynamicOrderDuration) {
        this.dynamicOrderDuration = dynamicOrderDuration;
    }

    /**
     * @return the timeZone
     */
    public String getTimeZone() {
        return timeZone;
    }

    /**
     * @param timeZone the timeZone to set
     */
    public void setTimeZone(String timeZone) {
        this.timeZone = timeZone;
    }

    /**
     * @return the dayProfitTarget
     */
    public double getDayProfitTarget() {
        return dayProfitTarget;
    }

    /**
     * @param dayProfitTarget the dayProfitTarget to set
     */
    public void setDayProfitTarget(double dayProfitTarget) {
        this.dayProfitTarget = dayProfitTarget;
    }

    /**
     * @return the dayStopLoss
     */
    public double getDayStopLoss() {
        return dayStopLoss;
    }

    /**
     * @param dayStopLoss the dayStopLoss to set
     */
    public void setDayStopLoss(double dayStopLoss) {
        this.dayStopLoss = dayStopLoss;
    }
}
