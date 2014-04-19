/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.algorithms.adr;

import java.util.Date;

/**
 *
 * @author pankaj
 */
public class ADROrderManagement extends com.incurrency.framework.OrderPlacement{
    
    public ADROrderManagement(boolean aggression, double tickSize, Date endDate,String ordReference,double pointValue, String timeZone){
        super(aggression,tickSize,endDate,ordReference,pointValue,1,timeZone);
    }
    
}
