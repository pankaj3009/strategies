/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.algorithms.launch;

import java.io.IOException;
import java.io.OutputStream;
import javax.swing.JTextArea;

/**
 *
 * @author pankaj
 */
public class CustomOutputStream extends OutputStream {
        
     
     
    @Override
    public void write(int b) {
        // redirects data to the text area
        Launch.txtAreaLog.append(String.valueOf((char)b));
        // scrolls the text area to the end of data
        Launch.txtAreaLog.setCaretPosition(Launch.txtAreaLog.getDocument().getLength());
    }
}
