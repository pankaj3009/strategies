/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.algorithms.pairs;

import com.incurrency.framework.BidAskEvent;


/**
 *
 * @author pankaj
 */
public class PairsBidAskReceived implements Runnable{

    private Pairs s;
    private BidAskEvent event;
    
    public PairsBidAskReceived(Pairs s,BidAskEvent event){
        this.s=s;
        this.event=event;
    }
    
    @Override
    public void run() {

    }
    
}
