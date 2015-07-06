/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.algorithms.launch;

import java.awt.AWTEvent;

/**
 *
 * @author Pankaj
 */
public class SimpleAWTEvent extends AWTEvent {

    public static final int EVENT_ID = AWTEvent.RESERVED_ID_MAX + 1;
    private String str;

   public SimpleAWTEvent(Object target, String str) {
        super(target, EVENT_ID);
        this.str = str;

    }

    public String getStr() {
        return (str);
    }

}
