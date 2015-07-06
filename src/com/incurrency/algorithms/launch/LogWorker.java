/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.algorithms.launch;

import java.awt.EventQueue;
import java.awt.Toolkit;
import java.io.OutputStream;

/**
 *
 * @author Pankaj
 */
public class LogWorker extends OutputStream{
    
    String message;
    
    public LogWorker(){
       
    }
    
    @Override
    public void write(int b){
        message=String.valueOf((char) b);
        run();
    }
    
    public void run() {
        Object target;
        target = Launch.launch;
        EventQueue eventQueue = Toolkit.getDefaultToolkit().getSystemEventQueue();
        eventQueue.postEvent(new SimpleAWTEvent(target, message));

    }
}
