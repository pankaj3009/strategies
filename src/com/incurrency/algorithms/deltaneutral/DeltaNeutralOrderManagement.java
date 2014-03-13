/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.algorithms.deltaneutral;

import com.incurrency.framework.OrderPlacement;
/**
 *
 * @author pankaj
 */
public class DeltaNeutralOrderManagement extends com.incurrency.framework.OrderPlacement {
    
        public DeltaNeutralOrderManagement(boolean aggression, double tickSize, String ordReference){
        super(aggression,tickSize,ordReference);
    }
}
