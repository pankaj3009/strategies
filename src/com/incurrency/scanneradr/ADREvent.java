/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.incurrency.scanneradr;

import java.util.logging.Logger;

/**
 *
 * @author Admin
 */
public class ADREvent {
    private int field = 0;
    private double price = 0.0;
    private static final Logger logger = Logger.getLogger(ADREvent.class.getName());

    /*
     * Constructor
     */
    public ADREvent(int type, double price)
    {
        field = type;
        this.price = price;
    }
    
   
    public double getPrice() {return price;};

    public int getField() {return field;}
}
