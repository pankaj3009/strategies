/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.algorithms.fundamental;

import com.incurrency.algorithms.launch.*;
import com.incurrency.algorithms.pairs.Pairs;
import com.incurrency.RatesClient.Subscribe;
import com.incurrency.framework.BeanConnection;
import com.incurrency.framework.BeanPosition;
import com.incurrency.framework.BeanSymbol;
import com.incurrency.framework.EnumRequestType;
import com.incurrency.framework.MainAlgorithm;
import com.incurrency.framework.Parameters;
import com.incurrency.framework.Strategy;
import com.incurrency.framework.TradeEvent;
import com.incurrency.framework.TradeListener;
import com.incurrency.framework.TradingUtil;
import com.incurrency.framework.fundamental.FundamentalData;
import com.incurrency.framework.fundamental.FundamentalDataOld;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author pankaj
 */
public class Fundamental implements TradeListener {

    private static final Logger logger = Logger.getLogger(Pairs.class.getName());

    public Fundamental(String parameterFile) {
       
        String[] tempStrategyArray = parameterFile.split("\\.")[0].split("-");
        for (BeanConnection c : Parameters.connection) {
            c.getWrapper().addTradeListener(this);
            c.initializeConnection(tempStrategyArray[tempStrategyArray.length - 1]);
        }
        if (Subscribe.tes != null) {
            Subscribe.tes.addTradeListener(this);
        }
        EnumRequestType[]request=new EnumRequestType[]{EnumRequestType.ESTIMATES,EnumRequestType.SNAPSHOT,EnumRequestType.FINSTAT};
        Thread t=new Thread(new FundamentalData(request) );
        t.start();
    }

    private void loadParameters(String strategy, String parameterFile) {
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
    }

    @Override
    public void tradeReceived(TradeEvent event) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
}