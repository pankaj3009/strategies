/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.algorithms.turtle;

import com.incurrency.framework.TradeEvent;

/**
 *
 * @author pankaj
 */
public class IDTTradeReceived implements Runnable{

    private BeanTurtle s;
    private TradeEvent event;
    
    public IDTTradeReceived(BeanTurtle s,TradeEvent event){
        this.s=s;
        this.event=event;
    }
    
    @Override
    public void run() {
        s.processTradeReceived(event);
    }
    
}
