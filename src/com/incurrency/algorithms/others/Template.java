/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.algorithms.others;

import com.incurrency.algorithms.pairs.Pairs;
import com.RatesClient.Subscribe;
import com.incurrency.framework.BeanConnection;
import com.incurrency.framework.BeanSymbol;
import com.incurrency.framework.MainAlgorithm;
import com.incurrency.framework.Parameters;
import com.incurrency.framework.PositionDetails;
import com.incurrency.framework.Strategy;
import com.incurrency.framework.TradeEvent;
import com.incurrency.framework.TradeListener;
import com.incurrency.framework.TradingUtil;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author pankaj
 */
public class Template extends Strategy implements TradeListener {

    private static final Logger logger = Logger.getLogger(Pairs.class.getName());

    public Template(MainAlgorithm m, String parameterFile, ArrayList<String> accounts) {
        super(m, "pair", "FUT", parameterFile, accounts);
        loadParameters("pair", parameterFile);
        for (BeanSymbol s : Parameters.symbol) {
            getPosition().put(s.getSerialno() - 1, new PositionDetails(s.getSerialno()-1));
        }
        TradingUtil.writeToFile(getStrategy() + ".csv","comma seperated header columns ");
        
        String[] tempStrategyArray = parameterFile.split("\\.")[0].split("-");
        for (BeanConnection c : Parameters.connection) {
            c.getWrapper().addTradeListener(this);
            c.initializeConnection(tempStrategyArray[tempStrategyArray.length - 1]);
        }
        if (Subscribe.tes != null) {
            Subscribe.tes.addTradeListener(this);
        }
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
