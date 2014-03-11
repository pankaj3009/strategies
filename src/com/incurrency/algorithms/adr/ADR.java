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
import com.incurrency.framework.TradeEvent;
import com.incurrency.framework.TradeListner;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

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
        orderADR=new ADROrderManagement(true,this.tickSize,"ADR");
        for(BeanConnection c: Parameters.connection){
        c.getWrapper().addTradeListener(this);
        
    }
        
    }
    
    public void loadParameters() {
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
            mEsperEvtProcessor.sendEvent(new ADREvent(ADRTickType.INDEX,price));
            if (price>indexDayHigh){
                indexDayHigh=price;
            }else if (price<indexDayLow){
                indexDayLow=price;
            }
            
            boolean buyZone1=(adrHigh-adrLow>5 && adr>adrLow+0.75*(adrHigh-adrLow)) ||
                            (adrDayHigh-adrDayLow>10 && adr>adrDayLow+0.75*(adrDayHigh-adrDayLow))?true:false;
            boolean buyZone2=(indexHigh-indexLow)>10 && price>indexLow+0.75*(indexHigh-indexLow)||
                            (indexDayHigh-indexDayLow)>20 && price>indexDayLow+0.75*(indexDayHigh-indexDayLow);
            boolean buyZone3=this.adrTRINAvg<95 && this.adrTRINAvg>0;
            
            boolean shortZone1=(adrHigh-adrLow>5 && adr<adrHigh-0.75*(adrHigh-adrLow)) ||
                            (adrDayHigh-adrDayLow>10 && adr<adrDayHigh-0.75*(adrDayHigh-adrDayLow));
            boolean shortZone2=(indexHigh-indexLow)>10 && price<indexHigh-0.75*(indexHigh-indexLow)||
                            (indexDayHigh-indexDayLow)>20 && price<indexDayHigh-0.75*(indexDayHigh-indexDayLow);
            boolean shortZone3=this.adrTRINAvg>105;
            
            Boolean buyZone=atLeastTwo(buyZone1,buyZone2,buyZone3);   
            Boolean shortZone=atLeastTwo(shortZone1,shortZone2,shortZone3);
            logger.log(Level.INFO," adrHigh: {0},adrLow: {1},adrAvg: {2},adrTRINHigh: {3},adrTRINLow: {4},adrTRINAvg: {5},indexHigh :{6},indexLow :{7},indexAvg: {8}",new Object[]{adrHigh,adrLow,adrAvg,adrTRINHigh,adrTRINLow,adrTRINAvg,indexHigh,indexLow,indexAvg});
            //tickHigh,tickLow,tickAvg,tickTRINHigh,tickTRINLow,tickTRINAvg
            if(position==0){           
            if(buyZone && tick<45 && tickTRIN>120){
                logger.log(Level.INFO,"Buy Order. ADR: {0}, ADRTrin :{1}, Tick: {2}, TickTrin: {3}, BuyZone1: {4}, BuyZone2: {5}, BuyZone3: {6}",new Object[]{adr,adrTRIN,tick,tickTRIN,buyZone1,buyZone2,buyZone3});
                m.fireOrderEvent(Parameters.symbol.get(id), EnumOrderSide.BUY, 50, price, 0, "ADR", 3, null, EnumOrderIntent.Init, 3, 1, 0);
                position=1;
            }
            else if(shortZone && tick>55  && tickTRIN<80){
            logger.log(Level.INFO,"Short Order. ADR: {0}, ADRTrin :{1}, Tick: {2}, TickTrin: {3}, ShortZone1: {4}, ShortZone2: {5}, ShortZone3: {6}",new Object[]{adr,adrTRIN,tick,tickTRIN,shortZone1,shortZone2,shortZone3});
            m.fireOrderEvent(Parameters.symbol.get(id), EnumOrderSide.SHORT, 50, price, 0, "ADR", 3, null, EnumOrderIntent.Init, 3, 1, 0);
            position=-1;
                
            }
            }
            else if(position==-1){
                if(!shortZone){
                    logger.log(Level.INFO,"Cover Order. ADR: {0}, ADRTrin :{1}, Tick: {2}, TickTrin: {3}, ShortZone1: {4}, ShortZone2: {5}, ShortZone3: {6}",new Object[]{adr,adrTRIN,tick,tickTRIN,shortZone1,shortZone2,shortZone3});
                    m.fireOrderEvent(Parameters.symbol.get(id), EnumOrderSide.COVER, 50, price, 0, "ADR", 3, null, EnumOrderIntent.Init, 3, 1, 0);
                    position=0;
                }
            } else if(position==1){
                if(!buyZone){
                    logger.log(Level.INFO,"Sell Order. ADR: {0}, ADRTrin :{1}, Tick: {2}, TickTrin: {3}, BuyZone1: {4}, BuyZone2: {5}, BuyZone3: {6}",new Object[]{adr,adrTRIN,tick,tickTRIN,buyZone1,buyZone2,buyZone3});
                    m.fireOrderEvent(Parameters.symbol.get(id), EnumOrderSide.SELL, 50, price, 0, "ADR", 3, null, EnumOrderIntent.Init, 3, 1, 0);
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
}
