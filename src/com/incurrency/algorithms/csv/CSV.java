/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.algorithms.csv;

import com.incurrency.framework.BeanConnection;
import com.incurrency.framework.MainAlgorithm;
import com.incurrency.framework.Parameters;
import com.incurrency.framework.Strategy;
import java.io.FileInputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author pankaj
 */
public class CSV extends Strategy {
    private static final Logger logger = Logger.getLogger(CSV.class.getName());
    String orderFile;
    OrderReader orderReader;
    
        public CSV(MainAlgorithm m, String parameterFile, ArrayList<String> accounts) {
            super(m,"CSV","FUT",parameterFile,accounts);
            loadParameters("CSV", parameterFile);
            String[] tempStrategyArray = parameterFile.split("\\.")[0].split("-");
             for (BeanConnection c : Parameters.connection) {
            c.initializeConnection(tempStrategyArray[tempStrategyArray.length - 1]);
        }
        Path dir = Paths.get(orderFile);
        orderReader=new OrderReader(dir, false).processEvents();             
        }
        
        private void loadParameters(String strategy,String parameterFile){
        Properties p = new Properties(System.getProperties());
        FileInputStream propFile;
        try {
            propFile = new FileInputStream(parameterFile);
            try {
                p.load(propFile);
            } catch (Exception ex) {
                logger.log(Level.SEVERE, null, ex);
            }
        } catch (Exception ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        System.setProperties(p);
        orderFile=System.getProperty("OrderFileName")==null?"orderfile.csv":System.getProperty("OrderFileName");
        
        }
}
