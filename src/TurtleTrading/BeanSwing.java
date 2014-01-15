/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package TurtleTrading;

import incurrframework.*;
import incurrframework.BeanOHLC;
import incurrframework.BeanSymbol;
import incurrframework.DateUtil;
import incurrframework.Parameters;
import incurrframework.TradeEvent;
import incurrframework.TradeListner;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Collections;
import java.util.Timer;


/**
 *
 * @author pankaj
 */
public class BeanSwing implements Serializable, TradeListner {

    private ArrayList<ArrayList<BeanOHLC>>  ohlcv = new <ArrayList<BeanOHLC>> ArrayList();  //algo parameter 
    private ArrayList<ArrayList<Integer>> swingHigh = new <ArrayList<Integer>> ArrayList();  //algo parameter 
    //private ArrayList<ArrayList<Boolean>> swingLow = new <ArrayList<Boolean>> ArrayList();  //algo parameter 
    private ArrayList<ArrayList<Double>> swing = new <ArrayList<Double>> ArrayList();  //algo parameter 
    private ArrayList<ArrayList<Integer>> pivotBars=new<ArrayList<Integer>> ArrayList();//internal variable 
    private ArrayList<ArrayList<Integer>> trend = new <ArrayList<Integer>> ArrayList();  //algo parameter 
    private MainAlgorithm m;
    private Date algoStartDate;
    private Date marketOpenDate;
    private Date marketCloseDate;
    private String tickSize;
    private String exit;
    private OrderPlacement ordManagement;
    private final static Logger logger = Logger.getLogger(Algorithm.class.getName());
    Timer preProcessing;

    public BeanSwing(MainAlgorithm m) {
        this.m = m;
        Properties p = new Properties(System.getProperties());
        FileInputStream propFile;
        try {
            propFile = new FileInputStream("Swing.properties");
            try {
                p.load(propFile);
            } catch (IOException ex) {
                Logger.getLogger(BeanTurtle.class.getName()).log(Level.SEVERE, null, ex);
            }
        } catch (FileNotFoundException ex) {
            Logger.getLogger(BeanTurtle.class.getName()).log(Level.SEVERE, null, ex);
        }
        System.setProperties(p);
        //String currDateStr = DateUtil.getFormatedDate("yyyyMMdd", Parameters.connection.get(0).getConnectionTime());
          String currDateStr = DateUtil.getFormatedDate("yyyyMMdd", 0);
        String AlgoStartStr = currDateStr + " " + System.getProperty("AlgoStart");
        String MarketOpenStr = currDateStr + " " + System.getProperty("MarketOpen");
        String MarketCloseStr = currDateStr + " " + System.getProperty("MarketClose");

        tickSize = System.getProperty("TickSize");
        algoStartDate = DateUtil.parseDate("yyyyMMdd HH:mm:ss", AlgoStartStr);
        marketOpenDate = DateUtil.parseDate("yyyyMMdd HH:mm:ss", MarketOpenStr);
        marketCloseDate = DateUtil.parseDate("yyyyMMdd HH:mm:ss", MarketCloseStr);
        if (marketCloseDate.compareTo(marketOpenDate) < 0 && new Date().compareTo(marketOpenDate) > 0) {
            //increase enddate by one calendar day
            marketCloseDate = DateUtil.addDays(marketCloseDate, 1); //system date is > start date time. Therefore we have not crossed the 12:00 am barrier
        } else if (marketCloseDate.compareTo(marketOpenDate) < 0 && new Date().compareTo(marketOpenDate) < 0) {
            marketOpenDate = DateUtil.addDays(marketOpenDate, -1); // we have moved beyond 12:00 am . adjust startdate to previous date
        }
        exit = System.getProperty("Exit");

        for (int i = 0; i < Parameters.symbol.size(); i++) {
        }
        Parameters.addTradeListener(this);

        try {
            Class.forName("com.mysql.jdbc.Driver");
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(BeanSwing.class.getName()).log(Level.SEVERE, null, ex);
        }
        for (BeanSymbol s : Parameters.symbol) {
            ArrayList<BeanOHLC> temp = TradingUtil.getDailyBarsFromOneMinCandle(90, s.getSymbol() + "_FUT");
            Collections.sort(temp, new OHLCCompare());
            ohlcv.add(temp); //ohlcv has data in ascending date order. Latest data is at the end of the list
        }
        for (int i = 0; i < ohlcv.size(); i++){
             ArrayList<Double>tempSwing=TradingUtil.generateSwings(ohlcv.get(i));
             ArrayList<Integer>tempTrend=TradingUtil.generateTrend(tempSwing);
             swing.add(tempSwing);
             trend.add(tempTrend);
        }
        
        preProcessing=new Timer();
        long t=m.getPreopenDate().getTime();
        Date tempDate=new Date(t+1*60000);// process one minute after preopen time.
        //preopenProcessing.schedule(new BeanGudsPreOpen(this), tempDate);
    } //end of constructor

    @Override
    public void tradeReceived(TradeEvent event) {
        int id = event.getSymbolID(); //here symbolID is with zero base.

    }

}

