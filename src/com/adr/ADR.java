/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.adr;

import incurrframework.BeanSymbol;
import incurrframework.EnumTickType;
import incurrframework.Parameters;
import incurrframework.TradeEvent;
import incurrframework.TradeListner;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Generates ADR, Tick , TRIN 
 * initialise ADR passing the symbols that need to be tracked.
 * ADR will immediately initiate polling market data in snapshot mode. Streaming mode is currently not supported
 * output is available via static fields
 * Advances, Declines, Tick Advances, Tick Declines, Advancing Volume, Declining Volume, Tick Advancing Volume, Tick Declining Volume
 * 
 * @author pankaj
 */
public class ADR implements TradeListner{
    
    private EventProcessor mEsperEvtProcessor = null;
    HashMap<Integer,BeanSymbol> symbols=new HashMap();

    public ADR(ArrayList<BeanSymbol> symb){
        mEsperEvtProcessor = new EventProcessor();
        for(BeanSymbol s: symb){
            symbols.put(s.getSerialno(), s);
        }
    }

    @Override
    public void tradeReceived(TradeEvent event) {
        int id=event.getSymbolID(); //zero based id
        if(symbols.containsKey(id)){
            mEsperEvtProcessor.sendEvent(new TickPriceEvent(id,EnumTickType.LASTPRICE,Parameters.symbol.get(id).getLastPrice()));
            mEsperEvtProcessor.sendEvent(new TickPriceEvent(id,EnumTickType.DAYVOL,Parameters.symbol.get(id).getVolume()));
            mEsperEvtProcessor.sendEvent(new TickPriceEvent(id,EnumTickType.LASTVOL,Parameters.symbol.get(id).getLastSize()));
        }
    }
    
}
