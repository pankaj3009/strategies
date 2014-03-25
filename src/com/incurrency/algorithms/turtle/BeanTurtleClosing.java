/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.algorithms.turtle;

import com.incurrency.framework.DateUtil;
import com.incurrency.framework.Parameters;
import com.incurrency.framework.Trade;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Date;
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
    private static final Logger logger = Logger.getLogger(BeanTurtleClosing.class.getName());

    BeanTurtleClosing(BeanTurtle b, TurtleOrderManagement ord) {
        beanturtle = b;
        this.ord = ord;
    }

    @Override
    public void run() {
        //print orders 
        FileWriter file;
        try {
            String fileSuffix=DateUtil.getFormatedDate("yyyyMMdd_HHmmss", new Date().getTime());
            String filename="ordersIDT"+fileSuffix+".csv";
            file = new FileWriter(filename, false);
            String[] header = new String[]{
                "entrySymbol", "entryType", "entryExpiry", "entryRight", "entryStrike",
                "entrySide", "entryPrice", "entrySize", "entryTime", "entryID", "exitSymbol",
                "exitType", "exitExpiry", "exitRight", "exitStrike", "exitSide", "exitPrice",
                "exitSize", "exitTime", "exitID"};
            CsvBeanWriter ordersWriter = new CsvBeanWriter(file, CsvPreference.EXCEL_PREFERENCE);
            ordersWriter.writeHeader(header);
            for (Map.Entry<Integer, Trade> order : beanturtle.getTrades().entrySet()) {
                ordersWriter.write(order.getValue(), header, Parameters.getTradeProcessors());
            }
            ordersWriter.close();
            System.out.println("Clean Exit after writing trades");
            filename="tradesIDT"+fileSuffix+".csv";
            file = new FileWriter(filename, false);
            CsvBeanWriter tradeWriter = new CsvBeanWriter(file, CsvPreference.EXCEL_PREFERENCE);
            tradeWriter.writeHeader(header);
            for (Map.Entry<Integer, Trade> trade : ord.getTrades().entrySet()) {
                tradeWriter.write(trade.getValue(), header, Parameters.getTradeProcessors());
            }
            tradeWriter.close();
            System.out.println("Clean Exit after writing orders");
           // System.exit(0);
        } catch (IOException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        

    }
}
