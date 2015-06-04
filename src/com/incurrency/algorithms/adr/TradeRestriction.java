/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.algorithms.adr;

import com.incurrency.framework.EnumOrderSide;

/**
 *
 * @author pankaj
 */
public class TradeRestriction {
    EnumOrderSide side;
    double highRange;
    double lowRange;

    public TradeRestriction(EnumOrderSide side, double highRange, double lowRange) {
        this.side = side;
        this.highRange = highRange;
        this.lowRange = lowRange;
    }
    
    
    
}
