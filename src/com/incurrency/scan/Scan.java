/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.scan;

import com.incurrency.framework.Utilities;
import java.util.Properties;

/**
 *
 * @author Pankaj
 */
public class Scan {
    
        private Properties properties;
        
    public Scan(String propertyFileName){
        properties = Utilities.loadParameters(propertyFileName);
        
    }
}
