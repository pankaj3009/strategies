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
import com.incurrency.framework.MainAlgorithmUI;
import com.incurrency.framework.Parameters;
import com.incurrency.framework.Trade;
import com.incurrency.framework.TradeEvent;
import com.incurrency.framework.TradeListner;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.supercsv.io.CsvBeanWriter;
import org.supercsv.prefs.CsvPreference;

/**
 * Generates ADR, Tick , TRIN 
 * initialise ADR passing the symbols that need to be tracked.
 * ADR will immediately initiate polling market data in snapshot mode. Streaming mode is currently not supported
 * output is available via static fields
 * Advances, Declines, Tick Advances, Tick Declines, Advancing Volume, Declining Volume, Tick Advancing Volume, Tick Declining Volume
 * 
 * @author pankaj
 */
public class ADR implements TradeListner,UpdateListener{
    
    static EventProcessor mEsperEvtProcessor = null;
    HashMap<Integer,BeanSymbol> symbols=new HashMap();
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
    double entryPrice=0;
    static String window;
    double windowHurdle;
    double dayHurdle;
    int internalOrderID=1;
    HashMap <Integer,Integer> internalOpenOrders=new HashMap(); //holds mapping of symbol id to latest initialization internal order
    HashMap<Integer,Trade> trades=new HashMap();
    
    MainAlgorithm m;
    //----- updated by ADRListener and TickListener
    static double adr;
    static double adrTRIN;
    static double tick;
    static double tickTRIN;
    static double adrDayHigh=Double.MIN_VALUE;
    static double adrDayLow=Double.MAX_VALUE;
    
//----- updated by method updateListener    
    double adrHigh;
    double adrLow;
    double adrAvg;
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
    com.incurrency.framework.OrderPlacement orderADR;

    
    public ADR(MainAlgorithm m,ArrayList<BeanSymbol> symb){
        this.m=m;
        loadParameters();
        mEsperEvtProcessor = new EventProcessor();
        mEsperEvtProcessor.ADRStatement.addListener(this);
        for(BeanSymbol s: symb){
            symbols.put(s.getSerialno()-1, s);
        }
        orderADR=new ADROrderManagement(true,this.tickSize,"adr");
        for(BeanConnection c: Parameters.connection){
        c.getWrapper().addTradeListener(this);
        c.initializeConnection("adr");
        Timer closeProcessing=new Timer();
        closeProcessing.schedule(runPrintOrders, com.incurrency.framework.DateUtil.addSeconds(endDate, 600));
        
    }
        
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
        logger.log(Level.INFO, "-----Turtle Parameters----");
        logger.log(Level.INFO, "end Time: {0}", endDate);
        logger.log(Level.INFO, "Setup to Trade: {0}", trading);
        logger.log(Level.INFO, "Traded Index: {0}", index);
        logger.log(Level.INFO, "Index Type: {0}", type);
        logger.log(Level.INFO, "Index Contract Expiry: {0}", expiry);
        logger.log(Level.INFO, "TickSize: {0}", tickSize);
        logger.log(Level.INFO, "Number of quotes before data collection: {0}", threshold);
        logger.log(Level.INFO, "Number of contracts to be traded: {0}", numberOfContracts);
        logger.log(Level.INFO, "Stop Loss: {0}", stopLoss);
        logger.log(Level.INFO, "Sliding Window Duration: {0}", window);
        logger.log(Level.INFO, "Hurdle Index move needed for window duration: {0}", windowHurdle);
        logger.log(Level.INFO, "Hurdle Index move needed for day: {0}", dayHurdle);

    }

    @Override
    public void tradeReceived(TradeEvent event) {
        int id=event.getSymbolID(); //zero based id
        if(symbols.containsKey(id)){
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
            boolean buyZone1=(adrHigh-adrLow>5 && adr>adrLow+0.75*(adrHigh-adrLow)) ||
                            (adrDayHigh-adrDayLow>10 && adr>adrDayLow+0.75*(adrDayHigh-adrDayLow))?true:false;
            boolean buyZone2=(indexHigh-indexLow)>windowHurdle && price>indexLow+0.75*(indexHigh-indexLow)||
                            (indexDayHigh-indexDayLow)>dayHurdle && price>indexDayLow+0.75*(indexDayHigh-indexDayLow);
            boolean buyZone3=this.adrTRINAvg<90 && this.adrTRINAvg>0;
            
            boolean shortZone1=(adrHigh-adrLow>5 && adr<adrHigh-0.75*(adrHigh-adrLow)) ||
                            (adrDayHigh-adrDayLow>10 && adr<adrDayHigh-0.75*(adrDayHigh-adrDayLow));
            boolean shortZone2=(indexHigh-indexLow)>windowHurdle && price<indexHigh-0.75*(indexHigh-indexLow)||
                            (indexDayHigh-indexDayLow)>dayHurdle && price<indexDayHigh-0.75*(indexDayHigh-indexDayLow);
            boolean shortZone3=this.adrTRINAvg>95;
            
            Boolean buyZone=atLeastTwo(buyZone1,buyZone2,buyZone3);   
            Boolean shortZone=atLeastTwo(shortZone1,shortZone2,shortZone3);
            logger.log(Level.INFO," adrHigh: {0},adrLow: {1},adrAvg: {2},adrTRINHigh: {3},adrTRINLow: {4},adrTRINAvg: {5},indexHigh :{6},indexLow :{7},indexAvg: {8}",new Object[]{adrHigh,adrLow,adrAvg,adrTRINHigh,adrTRINLow,adrTRINAvg,indexHigh,indexLow,indexAvg});
            //tickHigh,tickLow,tickAvg,tickTRINHigh,tickTRINLow,tickTRINAvg
            if(position==0 && new Date().compareTo(endDate)<0){           
            if(buyZone && (tick<45 || tickTRIN>120)){
                entryPrice=price;
                this.internalOpenOrders.put(id, internalOrderID);
                trades.put(this.internalOrderID, new Trade(id,EnumOrderSide.BUY,entryPrice,numberOfContracts,internalOrderID++));
                logger.log(Level.INFO,"Buy Order. Price: {0}, ADR: {1}, ADRTrin :{2}, Tick: {3}, TickTrin: {4}, BuyZone1: {5}, BuyZone2: {6}, BuyZone3: {7}",new Object[]{price,adr,adrTRIN,tick,tickTRIN,buyZone1,buyZone2,buyZone3});
                m.fireOrderEvent(internalOrderID-1,internalOrderID-1,Parameters.symbol.get(id), EnumOrderSide.BUY, numberOfContracts, price, 0, "adr", 3, "", EnumOrderIntent.Init, 3, 1, 0);
                position=1;
            }
            else if(shortZone && (tick>55  || tickTRIN<80)){
                entryPrice=price;
                this.internalOpenOrders.put(id, internalOrderID);
                trades.put(this.internalOrderID, new Trade(id,EnumOrderSide.BUY,entryPrice,numberOfContracts,internalOrderID++));
                logger.log(Level.INFO,"Short Order. Price: {0}, ADR: {1}, ADRTrin :{2}, Tick: {3}, TickTrin: {4}, ShortZone1: {5}, ShortZone2: {6}, ShortZone3: {7}",new Object[]{price,adr,adrTRIN,tick,tickTRIN,shortZone1,shortZone2,shortZone3});
                m.fireOrderEvent(internalOrderID-1,internalOrderID-1,Parameters.symbol.get(id), EnumOrderSide.SHORT, numberOfContracts, price, 0, "adr", 3, "", EnumOrderIntent.Init, 3, 1, 0);
                position=-1;
                
            }
            }
            else if(position==-1){
                if(buyZone || price>indexLow+stopLoss||new Date().compareTo(endDate)>0){ //stop loss
                    int tempinternalOrderID=internalOpenOrders.get(id);
                    Trade tempTrade=trades.get(tempinternalOrderID);
                    tempTrade.updateExit(id, EnumOrderSide.COVER, price, numberOfContracts, internalOrderID++);
                    logger.log(Level.INFO,"Cover Order. StopLoss. Price: {0}, ADR: {1}, ADRTrin :{2}, Tick: {3}, TickTrin: {4}, BuyZone1: {5}, BuyZone2: {6}, BuyZone3: {7}",new Object[]{price,adr,adrTRIN,tick,tickTRIN,buyZone1,buyZone2,buyZone3});
                    m.fireOrderEvent(internalOrderID-1,tempinternalOrderID,Parameters.symbol.get(id), EnumOrderSide.COVER, numberOfContracts, price, 0, "adr", 3, "", EnumOrderIntent.Init, 3, 1, 0);
                    position=0;
                }else if(!shortZone && price<entryPrice-2){
                    int tempinternalOrderID=internalOpenOrders.get(id);
                    Trade tempTrade=trades.get(tempinternalOrderID);
                    tempTrade.updateExit(id, EnumOrderSide.COVER, price, numberOfContracts, internalOrderID++);
                    logger.log(Level.INFO,"Cover Order. TakeProfit. Price: {0}, ADR: {1}, ADRTrin :{2}, Tick: {3}, TickTrin: {4}, BuyZone1: {5}, BuyZone2: {6}, BuyZone3: {7}",new Object[]{price,adr,adrTRIN,tick,tickTRIN,buyZone1,buyZone2,buyZone3});
                    m.fireOrderEvent(internalOrderID-1,tempinternalOrderID,Parameters.symbol.get(id), EnumOrderSide.COVER, numberOfContracts, price, 0, "adr", 3, "", EnumOrderIntent.Init, 3, 1, 0);
                    position=0;
                    
                }
            } else if(position==1){
                if(shortZone || price<indexHigh-stopLoss||new Date().compareTo(endDate)>0){
                    int tempinternalOrderID=internalOpenOrders.get(id);
                    Trade tempTrade=trades.get(tempinternalOrderID);
                    tempTrade.updateExit(id, EnumOrderSide.SELL, price, numberOfContracts, internalOrderID++);
                    logger.log(Level.INFO,"Sell Order. StopLoss. Price: {0}, ADR: {1}, ADRTrin :{2}, Tick: {3}, TickTrin: {4}, ShortZone1: {5}, ShortZone2: {6}, ShortZone3: {7}",new Object[]{price,adr,adrTRIN,tick,tickTRIN,shortZone1,shortZone2,shortZone3});
                    m.fireOrderEvent(internalOrderID-1,tempinternalOrderID,Parameters.symbol.get(id), EnumOrderSide.SELL, numberOfContracts, price, 0, "adr", 3, "", EnumOrderIntent.Init, 3, 1, 0);
                    position=0;
                }else if(!buyZone && price>entryPrice+2){
                    int tempinternalOrderID=internalOpenOrders.get(id);
                    Trade tempTrade=trades.get(tempinternalOrderID);
                    tempTrade.updateExit(id, EnumOrderSide.COVER, price, numberOfContracts, internalOrderID++);
                    logger.log(Level.INFO,"Sell Order. TakeProfit. Price: {0}, ADR: {1}, ADRTrin :{2}, Tick: {3}, TickTrin: {4}, BuyZone1: {5}, BuyZone2: {6}, BuyZone3: {7}",new Object[]{price,adr,adrTRIN,tick,tickTRIN,buyZone1,buyZone2,buyZone3});
                    m.fireOrderEvent(internalOrderID-1,tempinternalOrderID,Parameters.symbol.get(id), EnumOrderSide.SELL, numberOfContracts, price, 0, "adr", 3, "", EnumOrderIntent.Init, 3, 1, 0);
                    position=0;
                    
                
                }
            }
        }
    }

    @Override
    public void update(EventBean[] newEvents, EventBean[] oldEvents) {
       double high = (Double) newEvents[0].get("high");
        double low = (Double) newEvents[0].get("low");
        double average = (Double) newEvents[0].get("average");
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
            file = new FileWriter("orders.csv", true);
            String[] header = new String[]{
                "entrySymbol", "entryType", "entryExpiry", "entryRight", "entryStrike",
                "entrySide", "entryPrice", "entrySize", "entryTime", "entryID", "exitSymbol",
                "exitType", "exitExpiry", "exitRight", "exitStrike", "exitSide", "exitPrice",
                "exitSize", "exitTime", "exitID"};
            CsvBeanWriter writer = new CsvBeanWriter(file, CsvPreference.EXCEL_PREFERENCE);
            for (Map.Entry<Integer, Trade> trade : trades.entrySet()) {
                writer.write(trade.getValue(), header, Parameters.getTradeProcessors());
            }
            file = new FileWriter("trades.csv", true);
            writer = new CsvBeanWriter(file, CsvPreference.EXCEL_PREFERENCE);
            for (Map.Entry<Integer, Trade> trades : orderADR.getTrades().entrySet()) {
                writer.write(trades.getValue(), header, Parameters.getTradeProcessors());
            }
            writer.close();
            System.out.println("Clean Exit after writing orders");
            System.exit(0);
        } catch (IOException ex) {
            Logger.getLogger(MainAlgorithmUI.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
