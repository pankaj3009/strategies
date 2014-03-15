/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.algorithms.adr;

import com.incurrency.algorithms.turtle.*;
import java.util.Date;

/**
 *
 * @author pankaj
 */
public class ADROrderManagement extends com.incurrency.framework.OrderPlacement{
    
    public ADROrderManagement(boolean aggression, double tickSize, Date endDate,String ordReference){
        super(aggression,tickSize,endDate,ordReference);
    }
    
}
