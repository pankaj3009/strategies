/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.algorithms.turtle;

import com.incurrency.framework.MainAlgorithmUI;
import com.incurrency.framework.Parameters;
import com.incurrency.framework.Trade;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;
import java.util.TimerTask;
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
    TurtleOrderManagement ord;

    BeanTurtleClosing(BeanTurtle b, TurtleOrderManagement ord) {
        beanturtle = b;
        this.ord = ord;
    }

    @Override
    public void run() {
        //print orders 
        FileWriter file;
        try {
            file = new FileWriter("orders.csv", true);
            String[] header = new String[]{
                "entrySymbol", "entryType", "entryExpiry", "entryRight", "entryStrike",
                "entrySide", "entryPrice", "entrySize", "entryTime", "entryID", "exitSymbol",
                "exitType", "exitExpiry", "exitRight", "exitStrike", "exitSide", "exitPrice",
                "exitSize", "exitTime", "exitID"};
            CsvBeanWriter writer = new CsvBeanWriter(file, CsvPreference.EXCEL_PREFERENCE);
            for (Map.Entry<Integer, Trade> trades : beanturtle.getTrades().entrySet()) {
                writer.write(trades.getValue(), header, Parameters.getTradeProcessors());
            }
            writer.close();
            System.out.println("Clean Exit after writing trades");
            file = new FileWriter("trades.csv", true);
            writer = new CsvBeanWriter(file, CsvPreference.EXCEL_PREFERENCE);
            for (Map.Entry<Integer, Trade> trades : ord.getTrades().entrySet()) {
                writer.write(trades.getValue(), header, Parameters.getTradeProcessors());
            }
            writer.close();
            System.out.println("Clean Exit after writing orders");
            System.exit(0);
        } catch (IOException ex) {
            Logger.getLogger(MainAlgorithmUI.class.getName()).log(Level.SEVERE, null, ex);
        }
        

    }
}
