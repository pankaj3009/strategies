/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.scanneradr;

import com.incurrency.framework.Algorithm;
import com.incurrency.framework.BeanSymbol;
import com.incurrency.framework.EnumBarSize;
import com.incurrency.framework.Parameters;
import com.incurrency.framework.Utilities;
import com.incurrency.scan.Extract;
import java.util.logging.Logger;
import com.incurrency.scan.*;
import java.text.SimpleDateFormat;
import java.util.TimeZone;
import java.util.logging.Level;

/**
 *
 * @author Pankaj
 */
public class ExtractClient extends Extract {

    private static final Logger logger = Logger.getLogger(ExtractClient.class.getName());
    private boolean firstWrite = true;

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
        switch (com.incurrency.scan.Scanner.type) {
            case "ADRFEATURES":
                new Thread(new ADRManager()).run();
                BeanSymbol composite = Parameters.symbol.get(ADRManager.compositeID);
        String strRangewindows = com.incurrency.scan.Scanner.args.get("rangewindows");
        String[] arrstrRangeWindows = strRangewindows.split(",");
        int[] arrintRangeWindows = new int[arrstrRangeWindows.length];
        for (int i = 0; i < arrstrRangeWindows.length; i++) {
            arrintRangeWindows[i] = Integer.valueOf(arrstrRangeWindows[i]);
        }
        SimpleDateFormat sdf=new SimpleDateFormat("yyyyMMdd");
        sdf.setTimeZone(TimeZone.getTimeZone(Algorithm.timeZone));
        String fileName="adr"+"_"+sdf.format(ADRManager.startTime)+".csv";
        
        composite.saveToExternalFile(EnumBarSize.ONESECOND, new String[]{"adr", "adrtrinvolume","adrtrinvalue","movebackward","movebackwardtime",
                                                                                        arrintRangeWindows[0]+"adrhigh",arrintRangeWindows[0]+"adrlow",arrintRangeWindows[0]+"adravg",
                                                                                        arrintRangeWindows[0]+"trinvaluehigh",arrintRangeWindows[0]+"trinvaluelow",arrintRangeWindows[0]+"trinvalueavg",
                                                                                        arrintRangeWindows[0]+"trinvolumehigh",arrintRangeWindows[0]+"trinvolumelow",arrintRangeWindows[0]+"trinvolumeavg",
                                                                                        arrintRangeWindows[0]+"moveavg",
                                                                                        arrintRangeWindows[0]+"timehigh",arrintRangeWindows[0]+"timelow",arrintRangeWindows[0]+"timeavg",
                                                                                        arrintRangeWindows[1]+"adrhigh",arrintRangeWindows[1]+"adrlow",arrintRangeWindows[1]+"adravg",
                                                                                        arrintRangeWindows[1]+"trinvaluehigh",arrintRangeWindows[1]+"trinvaluelow",arrintRangeWindows[1]+"trinvalueavg",
                                                                                        arrintRangeWindows[1]+"trinvolumehigh",arrintRangeWindows[1]+"trinvolumelow",arrintRangeWindows[1]+"trinvolumeavg",
                                                                                        arrintRangeWindows[1]+"moveavg",
                                                                                        arrintRangeWindows[1]+"timehigh",arrintRangeWindows[1]+"timelow",arrintRangeWindows[1]+"timeavg",
                                                                                        "moveforward","index",
                                                                                        }, ADRManager.startTime+15*60000, ADRManager.endTime-15*60000, fileName, "yyyyMMdd HH:mm:ss", false);
                
                Scanner.dateProcessing.take();
                System.exit(0);
               break;
            default:
                break;
        }
    }
}
