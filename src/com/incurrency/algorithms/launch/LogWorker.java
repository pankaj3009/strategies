/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.algorithms.launch;

import java.awt.EventQueue;
import java.awt.Toolkit;
import java.io.OutputStream;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 *
 * @author Pankaj
 */
public class LogWorker extends OutputStream{
    
    String message;
    final static Lock lock = new ReentrantLock();  
    
    public LogWorker(){
       
    }
    
    @Override
    public void write(int b){
        message=String.valueOf((char) b);
        run();
    }
    
    public void run() {
        try{
        if (lock.tryLock()) {
        Object target;
        target = Launch.launch;
        EventQueue eventQueue = Toolkit.getDefaultToolkit().getSystemEventQueue();
        eventQueue.postEvent(new SimpleAWTEvent(target, message));
        } 
        }catch (Exception e){
            
        }finally{
            lock.unlock();
        }
        
    }
}
