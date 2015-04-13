/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.algorithms.adr;

import com.google.common.base.Preconditions;
import java.lang.reflect.Constructor;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import scanner.BeanSymbol;
import scanner.EnumBarSize;
import scanner.MatrixMethods;
import scanner.ReservedValues;
import scanner.Scanner;
import static scanner.Scanner.dateProcessing;
import scanner.Utilities;

/**
 *
 * @author Pankaj
 */
public class ExtractClient extends scanner.Extract {

    private static final Logger logger = Logger.getLogger(ExtractClient.class.getName());
    public static boolean buy=false;
    
    @Override
    public void run() {
        while (!Scanner.dateProcessing.empty()) {
            String header = null;
            String content = null;
            SimpleDateFormat sdfTime=new SimpleDateFormat("yyyyMMdd HH:mm:ss");
            switch (Scanner.type) {
                
                case "DAILYSCAN":
                    new Thread(new SwingManager(EnumBarSize.DAILY)).run();
                    Scanner.dateProcessing.take(); //removes the dateProcessing value of "abcd" after the date is finished.
                        BeanSymbol s =scanner.Scanner.symbol.get(0);
                        int dLength = s.getTimeSeriesLength(EnumBarSize.DAILY);
                        double swing=s.getTimeSeriesValue(EnumBarSize.DAILY, dLength - 1, "swing");
                        double stickyswing=s.getTimeSeriesValue(EnumBarSize.DAILY, dLength - 1, "stickyswing");
                        double finalswing=swing==0?-stickyswing:swing;    
                        if(finalswing>0){
                            buy=true;
                        }
                        logger.log(Level.INFO,"FinalSwing:{0}, Swing:{1},StickSwing:{2}",new Object[]{finalswing,swing,stickyswing});
                        
                        
                    Scanner.dateProcessing.take(); //removes "finished" from dateProcessing
                    break;      
                default:
                    break;
                    
                    } 
                               
                        
            }
            //print extendedhash map
            //Scanner.syncDates.take();
        }
    }


