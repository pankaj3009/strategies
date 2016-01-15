/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.scanneradr;


import com.incurrency.framework.BeanSymbol;
import com.incurrency.framework.EnumBarSize;
import com.incurrency.framework.Parameters;
import com.incurrency.framework.Utilities;
import com.incurrency.scan.Extract;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Logger;
import com.incurrency.scan.*;
import java.io.File;
import java.util.logging.Level;


/**
 *
 * @author Pankaj
 */
public class ExtractClient extends Extract {

    private static final Logger logger = Logger.getLogger(ExtractClient.class.getName());

    
    @Override
    public void run() {
        while (Scanner.dateProcessing.empty()) {
        Thread.yield();
            try {
                Thread.sleep(10000);
            } catch (InterruptedException ex) {
                Logger.getLogger(ExtractClient.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
            String header = null;
            String content = null;
            SimpleDateFormat sdfTime=new SimpleDateFormat("yyyyMMdd HH:mm:ss");
            switch (com.incurrency.scan.Scanner.type) {                
                case "ADRFEATURES":
                     new Thread(new ADRManager()).run();
                        //write adr data to file
                        SimpleDateFormat datetimeCleanFormat = new SimpleDateFormat("yyyyMMdd HH:mm:ss");
                        String time = datetimeCleanFormat.format(new Date());
                        int hour = Integer.valueOf(time.substring(9, 11));
                        int minute = Integer.valueOf(time.substring(12, 14));                        
                            //Utilities.writeToFile("adr" + "_" + time.substring(0, 8) + ".csv", content);
                    Scanner.dateProcessing.take();
                    //print features to file
                    BeanSymbol future=Parameters.symbol.get(0);
                    BeanSymbol composite=Parameters.symbol.get(Parameters.symbol.size()-1);
                    String strRangewindows = com.incurrency.scan.Scanner.args.get("rangewindows");
                    String[] arrstrRangeWindows = strRangewindows.split(",");
                    double [] indexRange=new double[arrstrRangeWindows.length];
                    double [] adrRange=new double[arrstrRangeWindows.length];
                    double [] trinRange=new double[arrstrRangeWindows.length];
                    for(long t:composite.getColumnLabels().get(EnumBarSize.ONESECOND)){
                        int tDay=composite.getTargetIndex(EnumBarSize.DAILY,t);
                        double adratio=composite.getTimeSeriesValue(EnumBarSize.ONESECOND, t, "adratio");
                        double advolumeratio=composite.getTimeSeriesValue(EnumBarSize.ONESECOND, t, "advolumeratio");
                        double trin=composite.getTimeSeriesValue(EnumBarSize.ONESECOND, t, "trin");
                        double pticks=composite.getTimeSeriesValue(EnumBarSize.ONESECOND, t, "pticks")/500;
                        double nticks=composite.getTimeSeriesValue(EnumBarSize.ONESECOND, t, "nticks")/500;
                        double tticks=composite.getTimeSeriesValue(EnumBarSize.ONESECOND, t, "tticks")/500;
                        double tpvolume=composite.getTimeSeriesValue(EnumBarSize.ONESECOND, t, "pvolume");
                        double tnvolume=composite.getTimeSeriesValue(EnumBarSize.ONESECOND, t, "nvolume");
                        double pvolume=tpvolume/(tpvolume+tnvolume);
                        double nvolume=tnvolume/(tpvolume+tnvolume);
                        int i=0;
                        for(String period:arrstrRangeWindows){
                            String label;
                            switch(i){
                                case 0:
                                    label="high";
                                    break;
                                    
                                case 1:
                                    label="low";
                                    break;
                                case 2:
                                    label="avg";
                                    break;
                                default:
                                    label="";
                                    break;
                            }
                            indexRange[i]=composite.getTimeSeriesValue(EnumBarSize.ONESECOND, t, period+ "index"+label);
                            adrRange[i]=composite.getTimeSeriesValue(EnumBarSize.ONESECOND, t, period+ "adr"+label);
                            trinRange[i]=composite.getTimeSeriesValue(EnumBarSize.ONESECOND, t, period+ "trin"+label);
                            i=i+1;
                        }
                        double indexclose=future.getTimeSeriesValue(EnumBarSize.ONESECOND, t, "close");
                        double mfemae=composite.getTimeSeriesValue(EnumBarSize.ONESECOND, t, "mfemae");
                        content=adratio+","+advolumeratio+","+trin+","+pticks+","+nticks+","+tticks+","+pvolume+","+nvolume;
                        for(double d:indexRange){
                            content=content+","+d;
                        }
                        for(double d:adrRange){
                            content=content+","+d;
                        }
                        for(double d:trinRange){
                            content=content+","+d;
                        }
                        content=content+","+","+indexclose+","+mfemae+","+sdfTime.format(new Date(t));
                        Utilities.writeToFile(new File("adr.csv"), content);
                    }
                    
                    break;
                default:
                    break;
            }
            //print extendedhash map
            //Scanner.syncDates.take();
        }
    }


