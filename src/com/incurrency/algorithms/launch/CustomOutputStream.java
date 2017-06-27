/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.algorithms.launch;

import java.io.OutputStream;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 *
 * @author pankaj
 */
public class CustomOutputStream extends OutputStream {

    final static Lock lock = new ReentrantLock();

    @Override
    public void write(int b) {
        //LogWorker logger=new LogWorker(String.valueOf((char) b));
        //logger.start();
    }
    /*
    public void write(int b) {
        // redirects data to the text area
        try {
            if (lock.tryLock()) {
                Launch.txtAreaLog.append(String.valueOf((char) b));
                // scrolls the text area to the end of data
                Launch.txtAreaLog.setCaretPosition(Launch.txtAreaLog.getDocument().getLength());
            }
        } catch (Exception e) {
        } finally {
            lock.unlock();
        }
    }
     */
}
