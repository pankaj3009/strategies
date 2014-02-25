/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.algorithms.turtle;

import static com.incurrency.algorithms.turtle.MainAlgorithmUI.algo;
import com.incurrency.framework.BeanEODTradeRecord;
import com.incurrency.framework.EnumOrderStatus;
import com.incurrency.framework.OrderBean;
import com.incurrency.framework.Parameters;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;
import java.util.TimerTask;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.supercsv.io.CsvBeanWriter;
import org.supercsv.prefs.CsvPreference;

/**
 *
 * @author psharma
 */
public class BeanTurtleClosing extends TimerTask {

    BeanTurtle beanturtle;
    
     BeanTurtleClosing(BeanTurtle b){
    beanturtle=b;    
    }
    
    @Override
    public void run() {
        //print orders 
       FileWriter file;
       try {
           file = new FileWriter("orders.csv", true);
           String[] header = new String[] {
                "symbolID", "orderID", "orderSide", "orderType",
                "status", "orderSize", "fillSize", "fillPrice","cancelRequested", "positionsize", "limitPrice",
                "triggerPrice","orderValidity","expireTime","orderReference","exitLogic"};
           CsvBeanWriter writer = new CsvBeanWriter(file, CsvPreference.EXCEL_PREFERENCE);
           
           if(algo!=null){
              
           for (Map.Entry<Integer,OrderBean> orders : Parameters.connection.get(algo.getParamTurtle().getDisplay()).getOrders().entrySet()) {
                 writer.write(orders.getValue(), header,Parameters.getOrderProcessors());
                 //writer.write(orders.getValue(), header);
             } 
             TreeMap<Integer,BeanEODTradeRecord> output=new TreeMap();
             
             for (Map.Entry<Integer,OrderBean> orders : Parameters.connection.get(algo.getParamTurtle().getDisplay()).getOrders().entrySet()) {
                if(orders.getValue().getStatus()==EnumOrderStatus.CompleteFilled||orders.getValue().getStatus()==EnumOrderStatus.CompleteFilled){
                    output.put(orders.getValue().getOrderID(), new BeanEODTradeRecord(orders.getValue()));
                }
                //now we have a list of completed orders, sorted by orderID. Iterate through this to identify output
                for (Map.Entry<Integer,BeanEODTradeRecord> trades : output.entrySet()) {
                
                }
                
             } 
           writer.close();
            System.out.println("Clean Exit after writing orders");
            System.exit(0);
           
           }
             
            } catch (IOException ex) {
           Logger.getLogger(MainAlgorithmUI.class.getName()).log(Level.SEVERE, null, ex);
       }
        //execute clean exit
       
    }
    
}
