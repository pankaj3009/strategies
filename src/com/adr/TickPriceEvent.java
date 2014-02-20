/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.adr;

import incurrframework.EnumTickType;

/**
 *
 * @author Admin
 */
public class TickPriceEvent {
    private int mTickerId = 0;
    private EnumTickType mField = EnumTickType.NULL;
    private double mPrice = 0.0;


    /*
     * Constructor
     */
    public TickPriceEvent(int id, EnumTickType type, double price)
    {
        mTickerId = id;
        mField = type;
        mPrice = price;
    }
    
    public int getTickerID() {return mTickerId;};
    
    public double getPrice() {return mPrice;};

    public EnumTickType getField() {return mField;}
}
