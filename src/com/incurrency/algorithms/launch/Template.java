/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.algorithms.launch;

import com.incurrency.RatesClient.RedisSubscribe;
import com.incurrency.framework.BeanConnection;
import com.incurrency.framework.BeanPosition;
import com.incurrency.framework.BeanSymbol;
import com.incurrency.framework.MainAlgorithm;
import com.incurrency.framework.Parameters;
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
    private static final Logger logger = Logger.getLogger(Template.class.getName());

    public Template(MainAlgorithm m,Properties p, String parameterFile, ArrayList<String> accounts, Integer stratCount) {
        super(m, "pair", "FUT", p,parameterFile, accounts,stratCount);
        loadParameters(p);
        for (BeanSymbol s : Parameters.symbol) {
            getPosition().put(s.getSerialno() - 1, new BeanPosition(s.getSerialno()-1,getStrategy()));
        }
        TradingUtil.writeToFile(getStrategy() + ".csv","comma seperated header columns ");
        
        String[] tempStrategyArray = parameterFile.split("\\.")[0].split("-");
        for (BeanConnection c : Parameters.connection) {
            c.getWrapper().addTradeListener(this);
            c.initializeConnection(tempStrategyArray[tempStrategyArray.length - 1],-1);
        }
        if (RedisSubscribe.tes != null) {
            RedisSubscribe.tes.addTradeListener(this);
        }
    }

    private void loadParameters(Properties p) {

    }

    @Override
    public void tradeReceived(TradeEvent event) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
}
