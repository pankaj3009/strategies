/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.algorithms.adr;

/**
 *
 * @author Pankaj
 */
public class FlushEvent {
    
    private int field=0;

    public FlushEvent(int field) {
    this.field=field;
    }

    /**
     * @return the field
     */
    public int getField() {
        return field;
    }
    
    
    
}
