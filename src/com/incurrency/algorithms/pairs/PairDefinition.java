/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.algorithms.pairs;

import com.incurrency.framework.TradingUtil;
import java.util.Date;

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

    public PairDefinition(String buySymbol, String shortSymbol, String timeStamp, String entryPrice, String expiry,String stopLoss, String takeProfit,String buyratio,String shortratio) {
        this.buySymbol = buySymbol;
        this.shortSymbol = shortSymbol;
        this.timeStamp = timeStamp;
        this.entryPrice = entryPrice;
        buyid=TradingUtil.getIDFromSymbol(buySymbol, "FUT", expiry, "", "");
        shortid=TradingUtil.getIDFromSymbol(shortSymbol, "FUT", expiry, "", "");
        this.pairStopLoss=TradingUtil.isDouble(stopLoss)?Double.parseDouble(stopLoss):0D;
        this.pairTakeProfit=TradingUtil.isDouble(takeProfit)?Double.parseDouble(takeProfit):0D;
        this.buyratio=Double.parseDouble(buyratio);
        this.shortratio=Double.parseDouble(shortratio);
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
