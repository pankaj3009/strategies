/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.algorithms.pairs;

import com.incurrency.framework.TradingUtil;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

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
    private int position;
    boolean active;
    double positionPrice;
    Date slHitTime=new Date(0);
    double pairStopLoss;
    double pairTakeProfit;
    final Object lockPosition=new Object();
    double buyratio=1;
    double shortratio=1;
    private static final Logger logger = Logger.getLogger(PairDefinition.class.getName());
    

    
        public PairDefinition(String buySymbol, String shortSymbol, String timeStamp, String entryPrice, String expiry,String stopLoss, String takeProfit,String buyratio,String shortratio, String type) {
        this.buySymbol = buySymbol;
        this.shortSymbol = shortSymbol;
        this.timeStamp = timeStamp;
        this.entryPrice = entryPrice;
        expiry=expiry==null?"":expiry;
        buyid=TradingUtil.getIDFromSymbol(buySymbol, type, expiry, "", "");
        shortid=TradingUtil.getIDFromSymbol(shortSymbol, type, expiry, "", "");
        this.pairStopLoss=TradingUtil.isDouble(stopLoss)?Double.parseDouble(stopLoss):0D;
        this.pairTakeProfit=TradingUtil.isDouble(takeProfit)?Double.parseDouble(takeProfit):0D;
        this.buyratio=Double.parseDouble(buyratio);
        this.shortratio=Double.parseDouble(shortratio);
        logger.log(Level.INFO,"{0},{1},Strategy,Pairs Read,BuySymbol: {2}, ShortSymbol: {3}, TimeStamp: {4},EntryPrice: {5},Expiry: {6},StopLoss: {7},TakeProfit: {8},BuyRatio:{9},ShortRatio: {10}, BuyID: {11}, ShortID:{12},Type:{13}",
                new Object[]{"","",buySymbol,shortSymbol,timeStamp,entryPrice,expiry,stopLoss,takeProfit,buyratio,shortratio,buyid,shortid,type});
    }

    /**
     * @return the position
     */
    public int getPosition() {
        return position;
    }

    /**
     * @param position the position to set
     */
    public void setPosition(int position) {
        this.position = position;
    }
    
    
    
}
