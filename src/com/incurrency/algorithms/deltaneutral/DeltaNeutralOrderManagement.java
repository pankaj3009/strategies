/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.algorithms.deltaneutral;

import com.incurrency.framework.OrderPlacement;
import java.util.Date;
/**
 *
 * @author pankaj
 */
public class DeltaNeutralOrderManagement extends com.incurrency.framework.OrderPlacement {
    
        public DeltaNeutralOrderManagement(boolean aggression, double tickSize, Date endDate,String ordReference,double pointValue,String timeZone){
        super(aggression,tickSize,endDate,ordReference,pointValue,1,timeZone);
    }
}
