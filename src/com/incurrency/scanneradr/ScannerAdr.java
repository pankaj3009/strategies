/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.scanneradr;

import com.incurrency.framework.Utilities;
import com.incurrency.scan.ExtendedHashMap;
import com.incurrency.scan.Scanner;
import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Constructor;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;


/**
 *
 * @author Pankaj
 */
public class ScannerAdr {

    public static ExtendedHashMap<String, String, Double> output = new ExtendedHashMap<>();
    public static Scanner scan;
    private static final Logger logger = Logger.getLogger(ScannerAdr.class.getName());

    /**
     * @param args the command line arguments
     */
    
    public ScannerAdr(String parameterFileName){
        try {
            HashMap<String,String> args=Utilities.loadParameters(parameterFileName,false);

            //Constructor c = ExtractClient.class.getConstructor();
            new Thread(scan=new com.incurrency.scan.Scanner(args)).start();
            new Thread(new ExtractClient()).start();
        } catch(Exception e){
            logger.log(Level.SEVERE,null,e);
        }
    }
    public static void main(String[] args) throws NoSuchMethodException, Exception {
        if (new File("logging.properties").exists()) {
            FileInputStream configFile = new FileInputStream("logging.properties");
            LogManager.getLogManager().readConfiguration(configFile);
        }
        
        SimpleDateFormat sdf=new SimpleDateFormat("yyyyMMdd");
        //scan.start(args, c);//this gets the daily swing data
        
        /*
        while (!Scanner.dateProcessing.value().equals("finished")) {
            Thread.yield();
            Thread.sleep(10000);
        }

        scan.start(new String[]{"dailyvol.txt"}, c);
        while (!dateProcessing.empty()) {
            Thread.yield();
            Thread.sleep(10000);
        }
    
        System.out.println(output.store.size());
        int size=Scanner.symbol.get(0).getTimeStampSeries(EnumBarSize.DAILY).size();
        long timeStamp=Scanner.symbol.get(0).getTimeStampSeries(EnumBarSize.DAILY).get(size-1);
        Utilities.print(ScannerClient.output, "dailyscan_"+sdf.format(new Date(timeStamp))+".csv", new String[]{"mswing","mstickyswing","mbarsinswing","mbarsoutsideswing","wswing","wstickyswing","wbarsinswing","wbarsoutsideswing","dswing", "dswingyest", "dbarsinswing", "dbarsoutsideswing", "intradayvol", "bodyvol", "rangevol"});
        System.out.println("Finished Printing output");
        */
    }
}
