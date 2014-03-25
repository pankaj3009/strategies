/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.algorithms.adr;

import com.espertech.esper.client.EventBean;
import com.espertech.esper.client.UpdateListener;
import com.incurrency.framework.MainAlgorithm;
import com.incurrency.framework.BeanConnection;
import com.incurrency.framework.BeanSymbol;
import com.incurrency.framework.DateUtil;
import com.incurrency.framework.EnumOrderIntent;
import com.incurrency.framework.EnumOrderSide;
import com.incurrency.framework.Parameters;
import com.incurrency.framework.ProfitLossManager;
import com.incurrency.framework.Trade;
import com.incurrency.framework.TradeEvent;
import com.incurrency.framework.TradeListener;
import com.incurrency.framework.TradingEventSupport;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
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
    static com.incurrency.framework.rateserver.RateServer adrServer= new com.incurrency.framework.rateserver.RateServer(5556);
    private static final Logger logger = Logger.getLogger(ADR.class.getName());
    Date endDate;
    boolean trading=false;
    String index;
    String type;
    String expiry;
    double tickSize;
    public static int threshold=1000000;
    int numberOfContracts=0;
    double stopLoss=5;
    double takeProfit=10;
    double entryPrice=0;
    static String window;
    double windowHurdle;
    double dayHurdle;
    double pointValue=1;
    int internalOrderID=1;
    HashMap <Integer,Integer> internalOpenOrders=new HashMap(); //holds mapping of symbol id to latest initialization internal order
    HashMap<Integer,Trade> trades=new HashMap();
    
    //--common parameters required for all strategies
    MainAlgorithm m;
    private Boolean longOnly = true;
    private Boolean shortOnly = true;
    private Boolean aggression = true;
    private double profitTarget=Double.MAX_VALUE;
    private double maxSlippageEntry=0;
    private double maxSlippageExit=0;
    private int maxOrderDuration=3;
    private int dynamicOrderDuration=1;
       
    
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
    double adrTRINAvg;
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
    private com.incurrency.framework.OrderPlacement omsADR;
    private ProfitLossManager plmanager;

    
    public ADR(MainAlgorithm m){
        this.m=m;
        loadParameters();
        mEsperEvtProcessor = new EventProcessor();
        mEsperEvtProcessor.ADRStatement.addListener(this);
        ArrayList<BeanSymbol> adrList = new ArrayList();
            //populate adrList with adrSymbols needed for ADR
            for (BeanSymbol s : Parameters.symbol) {
                if (Pattern.compile(Pattern.quote("ADR"), Pattern.CASE_INSENSITIVE).matcher(s.getStrategy()).find()) {
                    adrSymbols.add(s.getSerialno()-1);
                }
            }
        omsADR=new ADROrderManagement(aggression,this.tickSize,endDate,"adr",pointValue);
        for(BeanConnection c: Parameters.connection){
        c.getWrapper().addTradeListener(this);
        c.initializeConnection("adr");
        }
        plmanager=new ProfitLossManager("adr", this.adrSymbols, pointValue, profitTarget);
        Timer closeProcessing=new Timer();
        closeProcessing.schedule(runPrintOrders, com.incurrency.framework.DateUtil.addSeconds(endDate, 600));
        
    
        
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
        profitTarget=Double.parseDouble(System.getProperty("ProfitTarget"));
        maxSlippageEntry=Double.parseDouble(System.getProperty("MaxSlippageEntry"))/100; // divide by 100 as input was a percentage
        maxSlippageExit=Double.parseDouble(System.getProperty("MaxSlippageExit"))/100; // divide by 100 as input was a percentage
        maxOrderDuration = Integer.parseInt(System.getProperty("MaxOrderDuration"));
        dynamicOrderDuration = Integer.parseInt(System.getProperty("DynamicOrderDuration"));
       
        logger.log(Level.INFO, "-----ADR Parameters----");
        logger.log(Level.INFO, "end Time: {0}", endDate);
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
        logger.log(Level.INFO, "Strategy Profit Target: {0}", profitTarget);
        logger.log(Level.INFO, "PointValue: {0}", pointValue);
        logger.log(Level.INFO, "Maxmimum slippage allowed for entry: {0}", maxSlippageEntry);
        logger.log(Level.INFO, "Maximum slippage allowed for exit: {0}", maxSlippageExit);
        logger.log(Level.INFO, "Max Order Duration: {0}", maxOrderDuration);
        logger.log(Level.INFO, "Dynamic Order Duration: {0}", dynamicOrderDuration);

    }

    @Override
    public void tradeReceived(TradeEvent event) {
        int id=event.getSymbolID(); //zero based id
        if(adrSymbols.contains(id)){
            switch(event.getTickType()){
                case com.ib.client.TickType.LAST_SIZE:
                    //System.out.println("LASTSIZE, Symbol:"+Parameters.symbol.get(id).getSymbol()+" Value: "+Parameters.symbol.get(id).getLastSize()+" tickerID: "+id);
                    mEsperEvtProcessor.sendEvent(new TickPriceEvent(id,com.ib.client.TickType.LAST_SIZE,Parameters.symbol.get(id).getLastSize()));
                    //mEsperEvtProcessor.debugFireTickQuery();
                    break;
                case com.ib.client.TickType.VOLUME:
                   //System.out.println("VOLUME, Symbol:"+Parameters.symbol.get(id).getSymbol()+" Value: "+Parameters.symbol.get(id).getVolume()+" tickerID: "+id);
                    mEsperEvtProcessor.sendEvent(new TickPriceEvent(id,com.ib.client.TickType.VOLUME,Parameters.symbol.get(id).getVolume()));
                    //mEsperEvtProcessor.debugFireADRQuery();
                    break;
                case com.ib.client.TickType.LAST:
                    //System.out.println("LAST, Symbol:"+Parameters.symbol.get(id).getSymbol()+" Value: "+Parameters.symbol.get(id).getLastPrice()+" tickerID: "+id);
                    mEsperEvtProcessor.sendEvent(new TickPriceEvent(id,com.ib.client.TickType.LAST,Parameters.symbol.get(id).getLastPrice()));
                    //mEsperEvtProcessor.debugFireTickQuery();
                    //mEsperEvtProcessor.debugFireADRQuery();
                    break;
                case com.ib.client.TickType.CLOSE:
                    //System.out.println("CLOSE, Symbol:"+Parameters.symbol.get(id).getSymbol()+" Value: "+Parameters.symbol.get(id).getClosePrice()+" tickerID: "+id);
                    mEsperEvtProcessor.sendEvent(new TickPriceEvent(id,com.ib.client.TickType.CLOSE,Parameters.symbol.get(id).getClosePrice()));
                    //mEsperEvtProcessor.debugFireADRQuery();
                    break;
                default:
                    break;
            }
        }
        String symbolexpiry=Parameters.symbol.get(id).getExpiry()==null?"":Parameters.symbol.get(id).getExpiry();
        if(trading && Parameters.symbol.get(id).getSymbol().equals(index) && Parameters.symbol.get(id).getType().equals(type) && symbolexpiry.equals(expiry) && event.getTickType()==com.ib.client.TickType.LAST){
            double price=Parameters.symbol.get(id).getLastPrice();
            if(adr>0){ //calculate high low only after minimum ticks have been received.
            mEsperEvtProcessor.sendEvent(new ADREvent(ADRTickType.INDEX,price));
            if (price>indexDayHigh){
                indexDayHigh=price;
            }else if (price<indexDayLow){
                indexDayLow=price;
            }
            }
            boolean buyZone1=(adrHigh-adrLow>5 && adr>adrLow+0.75*(adrHigh-adrLow) && adr>adrAvg) ||
                            (adrDayHigh-adrDayLow>10 && adr>adrDayLow+0.75*(adrDayHigh-adrDayLow) && adr>adrAvg);
            boolean buyZone2=(indexHigh-indexLow>windowHurdle && (price>indexLow+0.75*(indexHigh-indexLow)&& price>indexAvg))||
                            (indexDayHigh-indexDayLow>dayHurdle && (price>indexDayLow+0.75*(indexDayHigh-indexDayLow)&& price>indexAvg));
            boolean buyZone3=this.adrTRINAvg<90 && this.adrTRINAvg>0;
            
            boolean shortZone1=(adrHigh-adrLow>5 && adr<adrHigh-0.75*(adrHigh-adrLow) && adr<adrAvg) ||
                            (adrDayHigh-adrDayLow>10 && adr<adrDayHigh-0.75*(adrDayHigh-adrDayLow ) && adr<adrAvg);
            boolean shortZone2=(indexHigh-indexLow>windowHurdle && (price<indexHigh-0.75*(indexHigh-indexLow)&& price<indexAvg))||
                            (indexDayHigh-indexDayLow>dayHurdle && (price<indexDayHigh-0.75*(indexDayHigh-indexDayLow) && price<indexAvg));
            boolean shortZone3=this.adrTRINAvg>95;
            
            Boolean buyZone=atLeastTwo(buyZone1,buyZone2,buyZone3);   
            Boolean shortZone=atLeastTwo(shortZone1,shortZone2,shortZone3);
            logger.log(Level.INFO," adrHigh: {0},adrLow: {1},adrAvg: {2},adrTRINHigh: {3},adrTRINLow: {4},adrTRINAvg: {5},indexHigh :{6},indexLow :{7},indexAvg: {8}, buyZone1: {9}, buyZone2: {10}, buyZone 3: {11}, shortZone1: {12}, shortZone2: {13}, ShortZone3:{14}, ADR: {15}, ADRTrin: {16}, Tick: {17}, TickTrin: {18}, adrDayHigh: {19}, adrDayLow: {20}, IndexDayHigh: {21}, IndexDayLow: {22}",new Object[]{adrHigh,adrLow,adrAvg,adrTRINHigh,adrTRINLow,adrTRINAvg,indexHigh,indexLow,indexAvg,buyZone1,buyZone2,buyZone3,shortZone1,shortZone2,shortZone3,adr,adrTRIN,tick,tickTRIN,adrDayHigh,adrDayLow,indexDayHigh,indexDayLow});
            //tickHigh,tickLow,tickAvg,tickTRINHigh,tickTRINLow,tickTRINAvg
            if(position==0 && new Date().compareTo(endDate)<0){           
            if(buyZone && (tick<45 || tickTRIN>120)){
                entryPrice=price;
                this.internalOpenOrders.put(id, internalOrderID);
                trades.put(this.internalOrderID, new Trade(id,EnumOrderSide.BUY,entryPrice,numberOfContracts,internalOrderID++));
                logger.log(Level.INFO,"Buy Order. Price: {0}",new Object[]{price});
                    getOmsADR().tes.fireOrderEvent(internalOrderID-1,internalOrderID-1,Parameters.symbol.get(id), EnumOrderSide.BUY, numberOfContracts, price, 0, "adr", 3, "", EnumOrderIntent.Init, maxOrderDuration, dynamicOrderDuration, maxSlippageEntry);
                position=1;
            }
            else if(shortZone && (tick>55  || tickTRIN<80)){
                entryPrice=price;
                this.internalOpenOrders.put(id, internalOrderID);
                trades.put(this.internalOrderID, new Trade(id,EnumOrderSide.BUY,entryPrice,numberOfContracts,internalOrderID++));
                logger.log(Level.INFO,"Short Order. Price: {0}",new Object[]{price});
                    getOmsADR().tes.fireOrderEvent(internalOrderID-1,internalOrderID-1,Parameters.symbol.get(id), EnumOrderSide.SHORT, numberOfContracts, price, 0, "adr", 3, "", EnumOrderIntent.Init, maxOrderDuration, dynamicOrderDuration, maxSlippageEntry);
                position=-1;
                
            }
            }
            else if(position==-1){
                if(buyZone || (price>indexLow+stopLoss && !shortZone)||new Date().compareTo(endDate)>0){ //stop loss
                    int tempinternalOrderID=internalOpenOrders.get(id);
                    Trade tempTrade=trades.get(tempinternalOrderID);
                    tempTrade.updateExit(id, EnumOrderSide.COVER, price, numberOfContracts, internalOrderID++);
                    logger.log(Level.INFO,"Cover Order. StopLoss. Price: {0}",new Object[]{price});
                    getOmsADR().tes.fireOrderEvent(internalOrderID-1,tempinternalOrderID,Parameters.symbol.get(id), EnumOrderSide.COVER, numberOfContracts, price, 0, "adr", 3, "", EnumOrderIntent.Init, maxOrderDuration, dynamicOrderDuration, maxSlippageExit);
                    position=0;
                }else if(!shortZone && price<entryPrice-takeProfit){
                    int tempinternalOrderID=internalOpenOrders.get(id);
                    Trade tempTrade=trades.get(tempinternalOrderID);
                    tempTrade.updateExit(id, EnumOrderSide.COVER, price, numberOfContracts, internalOrderID++);
                    logger.log(Level.INFO,"Cover Order. TakeProfit. Price: {0}",new Object[]{price});
                    getOmsADR().tes.fireOrderEvent(internalOrderID-1,tempinternalOrderID,Parameters.symbol.get(id), EnumOrderSide.COVER, numberOfContracts, price, 0, "adr", 3, "", EnumOrderIntent.Init, maxOrderDuration, dynamicOrderDuration, maxSlippageExit);
                    position=0;
                    
                }
            } else if(position==1){
                if(shortZone || (price<indexHigh-stopLoss && !buyZone)||new Date().compareTo(endDate)>0){
                    int tempinternalOrderID=internalOpenOrders.get(id);
                    Trade tempTrade=trades.get(tempinternalOrderID);
                    tempTrade.updateExit(id, EnumOrderSide.SELL, price, numberOfContracts, internalOrderID++);
                    logger.log(Level.INFO,"Sell Order. StopLoss. Price: {0}",new Object[]{price});
                    getOmsADR().tes.fireOrderEvent(internalOrderID-1,tempinternalOrderID,Parameters.symbol.get(id), EnumOrderSide.SELL, numberOfContracts, price, 0, "adr", 3, "", EnumOrderIntent.Init, maxOrderDuration, dynamicOrderDuration, maxSlippageExit);
                    position=0;
                }else if(!buyZone && price>entryPrice+takeProfit){
                    int tempinternalOrderID=internalOpenOrders.get(id);
                    Trade tempTrade=trades.get(tempinternalOrderID);
                    tempTrade.updateExit(id, EnumOrderSide.COVER, price, numberOfContracts, internalOrderID++);
                    logger.log(Level.INFO,"Sell Order. TakeProfit. Price: {0}",new Object[]{price});
                    getOmsADR().tes.fireOrderEvent(internalOrderID-1,tempinternalOrderID,Parameters.symbol.get(id), EnumOrderSide.SELL, numberOfContracts, price, 0, "adr", 3, "", EnumOrderIntent.Init, maxOrderDuration, dynamicOrderDuration, maxSlippageExit);
                    position=0; 
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
    
    TimerTask runPrintOrders = new TimerTask(){
    public void run(){
        printOrders();
    }
};
    private void printOrders(){
                FileWriter file;
        try {
            String fileSuffix=DateUtil.getFormatedDate("yyyyMMdd_HHmmss", new Date().getTime());
            String filename="orders"+fileSuffix+".csv";
            file = new FileWriter(filename, false);
            String[] header = new String[]{
                "entrySymbol", "entryType", "entryExpiry", "entryRight", "entryStrike",
                "entrySide", "entryPrice", "entrySize", "entryTime", "entryID", "exitSymbol",
                "exitType", "exitExpiry", "exitRight", "exitStrike", "exitSide", "exitPrice",
                "exitSize", "exitTime", "exitID"};
            CsvBeanWriter orderWriter = new CsvBeanWriter(file, CsvPreference.EXCEL_PREFERENCE);
            orderWriter.writeHeader(header);
            for (Map.Entry<Integer, Trade> order : trades.entrySet()) {
                orderWriter.write(order.getValue(), header, Parameters.getTradeProcessors());
            }
            orderWriter.close();
            logger.log(Level.INFO,"Clean Exit after writing orders");
            filename="trades"+fileSuffix+".csv";
            file = new FileWriter(filename, false);
            CsvBeanWriter tradeWriter = new CsvBeanWriter(file, CsvPreference.EXCEL_PREFERENCE);
            tradeWriter.writeHeader(header);
            for (Map.Entry<Integer, Trade> trade : getOmsADR().getTrades().entrySet()) {
                tradeWriter.write(trade.getValue(), header, Parameters.getTradeProcessors());
            }
            tradeWriter.close();
            logger.log(Level.INFO,"Clean Exit after writing trades");
            System.exit(0);
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
    public double getProfitTarget() {
        return profitTarget;
    }

    /**
     * @param profitTarget the profitTarget to set
     */
    public void setProfitTarget(double profitTarget) {
        this.profitTarget = profitTarget;
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
}
