/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.algorithms.turtle;

import java.util.Date;

/**
 *
 * @author pankaj
 */
public class TurtleOrderManagement extends com.incurrency.framework.OrderPlacement{
    
    public TurtleOrderManagement(boolean aggression, double tickSize,Date endDate, String ordReference,double pointValue){
        super(aggression,tickSize,endDate,ordReference,pointValue);
    }
    
}
