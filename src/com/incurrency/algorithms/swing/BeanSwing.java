/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.algorithms.swing;

import com.RatesClient.Subscribe;
import com.incurrency.algorithms.turtle.BeanTurtle;
import com.incurrency.framework.MainAlgorithm;
import com.incurrency.framework.OrderPlacement;
import com.incurrency.framework.TradingUtil;
import com.incurrency.framework.OHLCCompare;
import com.incurrency.framework.Algorithm;
import com.incurrency.framework.BeanConnection;
import com.incurrency.framework.BeanOHLC;
import com.incurrency.framework.BeanSymbol;
import com.incurrency.framework.BrokerageRate;
import com.incurrency.framework.DateUtil;
import com.incurrency.framework.HistoricalBars;
import com.incurrency.framework.Launch;
import com.incurrency.framework.Parameters;
import com.incurrency.framework.Splits;
import com.incurrency.framework.Trade;
import com.incurrency.framework.TradeEvent;
import com.incurrency.framework.TradeListener;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;

/**
 *
 * @author pankaj
 */
public class BeanSwing extends Strategy implements Serializable, TradeListener {

    private ArrayList<ArrayList<BeanOHLC>> ohlcv = new <ArrayList<BeanOHLC>> ArrayList();  //algo parameter 
    private ArrayList<ArrayList<Integer>> swingHigh = new <ArrayList<Integer>> ArrayList();  //algo parameter 
    //private ArrayList<ArrayList<Boolean>> swingLow = new <ArrayList<Boolean>> ArrayList();  //algo parameter 
    private ArrayList<ArrayList<Double>> swing = new <ArrayList<Double>> ArrayList();  //algo parameter 
    private ArrayList<ArrayList<Integer>> pivotBars = new <ArrayList<Integer>> ArrayList();//internal variable 
    private ArrayList<ArrayList<Integer>> trend = new <ArrayList<Integer>> ArrayList();  //algo parameter 
    private final static Logger logger = Logger.getLogger(BeanSwing.class.getName());
    private String split;
    private ArrayList<Splits> splits = new ArrayList();    
    Timer preProcessing;

    public BeanSwing(MainAlgorithm m) {
        super(m, "swing", "FUT");
        loadParameters("swing");
        for (int i = 0; i < Parameters.symbol.size(); i++) {
        }
        
        for(BeanConnection c: Parameters.connection){
        c.getWrapper().addTradeListener(this);
        c.initializeConnection("swing");
        }
        if (Subscribe.tes!=null){
            Subscribe.tes.addTradeListener(this);
        }
            getDailyHistoricalData("swing","STK");
            
        try {
            Class.forName("com.mysql.jdbc.Driver");
        } catch (ClassNotFoundException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        for (BeanSymbol s : Parameters.symbol) {
            ArrayList<BeanOHLC> temp = TradingUtil.getDailyBarsFromOneMinCandle(90, s.getSymbol());
            Collections.sort(temp, new OHLCCompare());
            ohlcv.add(temp); //ohlcv has data in ascending date order. Latest data is at the end of the list
        }

        for (int i = 0; i < this.strategySymbols.size(); i++) {
            int id= strategySymbols.get(i);
            BeanSymbol s=Parameters.symbol.get(id);
            ArrayList<BeanOHLC> ohlcv=new ArrayList<>();
                    for(Map.Entry<Long, BeanOHLC> entry : s.getDailyBar().getHistoricalBars().entrySet()){
                    ohlcv.add(entry.getValue());
        }
            ArrayList<Double> tempSwing = TradingUtil.generateSwings(ohlcv);
            ArrayList<Integer> tempTrend = TradingUtil.generateTrend(tempSwing);
            swing.add(tempSwing);
            trend.add(tempTrend);
        }
    } //end of constructor

    private void loadParameters(String strategy) {
        Properties p = new Properties(System.getProperties());
        FileInputStream propFile;
        try {
            propFile = new FileInputStream(MainAlgorithm.input.get(strategy));
            try {
                p.load(propFile);
            } catch (Exception ex) {
                logger.log(Level.SEVERE, null, ex);
            }
        } catch (Exception ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        System.setProperties(p);
        // Initialize Variables
        split = System.getProperty("Splits")==null?"":System.getProperty("Splits");
        List<String> items = Arrays.asList(split.split("\\s*;\\s*"));
        for (String str : items) {
            List<String> temp = Arrays.asList(str.split("\\s*,\\s*"));
            try {
                int id = TradingUtil.getIDFromSymbol(temp.get(0), "STK", "", "", "");
                int oldShares = Integer.parseInt(temp.get(2).split("\\s*:\\s*")[0]);
                int newShares = Integer.parseInt(temp.get(2).split("\\s*:\\s*")[1]);
                long splitDate = Long.parseLong(temp.get(1));
                splits.add(new Splits(id, temp.get(0), oldShares, newShares, splitDate));

            } catch (Exception e) {
                logger.log(Level.INFO, "Split could not be processed. {0}", new Object[]{str});
                logger.log(Level.SEVERE, null, e);
            }
        }
    }

        private void getDailyHistoricalData(String strategy, String type) {
        try {
            //get historical data - this can be done before start time, assuming the program is started next day

            Thread t = new Thread(new HistoricalBars(strategy,type));
            t.setName("Historical Bars");
            if (!Launch.headless) {
                Launch.setMessage("Starting request of Historical Data");
            }
            t.start();
            t.join();
        } catch (InterruptedException ex) {
            logger.log(Level.SEVERE, null, ex);
        }

    }
    @Override
    public void tradeReceived(TradeEvent event) {
        int id = event.getSymbolID(); //here symbolID is with zero base.

    }
}
