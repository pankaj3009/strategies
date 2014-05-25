/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.algorithm.pairs;

import com.incurrency.framework.TradingUtil;

/**
 *
 * @author pankaj
 */
public class PairDefinition {
    String buySymbol;
    String shortSymbol;
    String timeStamp;
    String entryPrice;
    int buyid;
    int shortid;
    int position;
    boolean active;
    double positionPrice;

    public PairDefinition(String buySymbol, String shortSymbol, String timeStamp, String entryPrice, String expiry) {
        this.buySymbol = buySymbol;
        this.shortSymbol = shortSymbol;
        this.timeStamp = timeStamp;
        this.entryPrice = entryPrice;
        buyid=TradingUtil.getIDFromSymbol(buySymbol, "FUT", expiry, "", "");
        shortid=TradingUtil.getIDFromSymbol(shortSymbol, "FUT", expiry, "", "");
    }
    
    
    
}
