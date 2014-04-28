/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.algorithms.swing;

import com.RatesClient.Subscribe;
import com.incurrency.framework.BeanConnection;
import com.incurrency.framework.BeanOHLC;
import com.incurrency.framework.BeanSymbol;
import com.incurrency.framework.DateUtil;
import com.incurrency.framework.EnumBarValue;
import com.incurrency.framework.EnumOrderSide;
import com.incurrency.framework.HistoricalBars;
import com.incurrency.framework.Launch;
import com.incurrency.framework.MainAlgorithm;
import com.incurrency.framework.Parameters;
import com.incurrency.framework.PositionDetails;
import com.incurrency.framework.Splits;
import com.incurrency.framework.TechnicalUtil;
import com.incurrency.framework.Trade;
import com.incurrency.framework.TradeEvent;
import com.incurrency.framework.TradeListener;
import com.incurrency.framework.TradingUtil;
import java.io.File;
import java.io.FileInputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author pankaj
 */
public class BeanSwing extends Strategy implements Serializable, TradeListener {
    private final static Logger logger = Logger.getLogger(BeanSwing.class.getName());
    private HashMap<Integer, ArrayList<BeanOHLC>> ohlcv = new HashMap<>();  //algo parameter
    private ArrayList<ArrayList<Double>> swing = new <ArrayList<Double>> ArrayList();  //algo parameter
    private ArrayList<ArrayList<Integer>> trend = new <ArrayList<Integer>> ArrayList();  //algo parameter
    private String split;
    private String firstMonthExpiry;
    private ArrayList<Splits> splits = new ArrayList();
    Timer preProcessing;
    private HashMap<Integer, PositionDetails> allPositions = new HashMap<>();
     
    TimerTask runOpeningOrders = new TimerTask() {
        @Override
        public void run() {
            System.out.println("In runOpeningOrders");
            long today;
            if (timeZone.compareTo("") == 0) {
                today = Long.parseLong(DateUtil.getFormatedDate("yyyymmdd", new Date().getTime(), TimeZone.getDefault()));
            } else {
                today = Long.parseLong(DateUtil.getFormatedDate("yyyymmdd", new Date().getTime(), TimeZone.getTimeZone(timeZone)));
            }
            //place SL and TP OCO orders
            for (PositionDetails p : allPositions.values()) {
                double takeProfit;
                double stopLoss;
                ArrayList<Double> atr = TechnicalUtil.getATR(ohlcv.get(p.getId()), 10);
                ArrayList<BeanOHLC> b = ohlcv.get(p.getId());
                if (b.get(b.size() - 1).getOpenTime() == today) {
                    takeProfit = atr.get(atr.size() - 2) * 2; //market has opened and the last beanOHLC is for today. So take the second last bar.
                    stopLoss = atr.get(atr.size() - 2) * 3;
                } else {
                    takeProfit = atr.get(atr.size() - 1) * 2;
                    stopLoss = atr.get(atr.size() - 1) * 3;
                }
                if (p.getPosition() > 0) {
                    //get take profit
                    exit(p.getId(), EnumOrderSide.SELL, p.getPositionPrice() + takeProfit, 0, Parameters.symbol.get(p.getId()).getSymbol(), false, "DAY"); //take profit
                    exit(p.getId(), EnumOrderSide.SELL, p.getPositionPrice() - stopLoss, p.getPositionPrice() - stopLoss, Parameters.symbol.get(p.getId()).getSymbol(), true, "DAY"); //stop loss
                } else if (p.getPosition() < 0) {
                    exit(p.getId(), EnumOrderSide.COVER, p.getPositionPrice() - takeProfit, 0, Parameters.symbol.get(p.getId()).getSymbol(), false, "DAY"); //take profit
                    exit(p.getId(), EnumOrderSide.COVER, p.getPositionPrice() + stopLoss, p.getPositionPrice() + stopLoss, Parameters.symbol.get(p.getId()).getSymbol(), true, "DAY"); //stop loss
                }
            }
        }
    };

    public BeanSwing(MainAlgorithm m) {
        super(m, "swing", "FUT");
        loadParameters("swing");
        for (int i = 0; i < strategySymbols.size(); i++) {
            allPositions.put(i, new PositionDetails(strategySymbols.get(i)));
        }
        for (BeanConnection c : Parameters.connection) {
            c.getWrapper().addTradeListener(this);
            c.initializeConnection("swing");
        }
        if (Subscribe.tes != null) {
            Subscribe.tes.addTradeListener(this);
        }
        try {
            // Load open positions in the program.
            File f=new File(orderFile);
            if(f.exists() && !f.isDirectory()){
            ArrayList<Trade> allTrades = Parameters.readTradesWithCsvBeanReader(orderFile);
            Timer openProcessing = new Timer("Timer: Swing Opening Orders");
            openProcessing.schedule(runOpeningOrders, com.incurrency.framework.DateUtil.addSeconds(new Date(), (1) * 60));
            Iterator<Trade> iter = allTrades.iterator();
            while (iter.hasNext()) {
                if (iter.next().getExitPrice() > 0) {
                    iter.remove();
                }
            }
            iter = allTrades.iterator();
            while (iter.hasNext()) {
                Trade tr = iter.next();
                String symbol = tr.getEntrySymbol();
                String type = "STK";
                String expiry = "";
                String option = "";
                String right = "";
                int tempPosition = 0;
                int id = TradingUtil.getIDFromSymbol(symbol, type, expiry, option, right);
                if (id >= 0 && strategySymbols.contains(id)) {
                    tempPosition = allPositions.get(id).getPosition();
                    tempPosition = tr.getEntrySide() == EnumOrderSide.BUY ? tempPosition + tr.getEntrySize() : tempPosition - tr.getEntrySize();
                    PositionDetails p = new PositionDetails(id);
                    p.setPositionInitDate(DateUtil.parseDate("yyyy-MM-dd HH:mm:ss", tr.getEntryTime(), timeZone));
                    p.setPosition(tempPosition);
                    p.setPositionPrice(tr.getEntryPrice());
                    p.setId(id);
                    iter.remove();
                } else {
                    logger.log(Level.SEVERE, "Error compiling open positions. No symbol found for symbol:{0},Type:{1},Expiry:{2},Option:{3},Right:{4}", new Object[]{symbol, type, expiry, option, right});
                }
            }
            }
        } catch (Exception ex) {
            logger.log(Level.SEVERE, null, ex);
        }

        getDailyHistoricalData("swing", "STK");

        try {
            Class.forName("com.mysql.jdbc.Driver");
        } catch (ClassNotFoundException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        /*      This code segment commented out as we are retrieving historical data from IB only.
         for (BeanSymbol s : Parameters.symbol) {
         ArrayList<BeanOHLC> temp = TradingUtil.getDailyBarsFromOneMinCandle(90, s.getSymbol());
         Collections.sort(temp, new OHLCCompare());
         ohlcv.add(temp); //ohlcv has data in ascending date order. Latest data is at the end of the list
         }
         */

        for (int i = 0; i < this.strategySymbols.size(); i++) {
            int id = strategySymbols.get(i);
            BeanSymbol s = Parameters.symbol.get(id);
            ArrayList<BeanOHLC> tempOHLCV = new ArrayList();
            for (Map.Entry<Long, BeanOHLC> entry : s.getDailyBar().getHistoricalBars().entrySet()) {
                tempOHLCV.add(entry.getValue());
            }
            ohlcv.put(id, tempOHLCV);
            position.put(id, 0);
        }

        // Process Splits
        for (Splits sp : splits) {
            int id = sp.getId();
            if (id >= 0 && ohlcv.get(id).size() > 0) {
                ArrayList<BeanOHLC> tempOHLCV = new ArrayList<>();
                ArrayList<BeanOHLC> hist = ohlcv.get(id);
                for (BeanOHLC entry : hist) {
                    if (entry.getOpenTime() < sp.getEffectiveDate()) {
                        entry.setOpen(entry.getOpen() * sp.getOldShares() / sp.getNewShares());
                        entry.setHigh(entry.getHigh() * sp.getOldShares() / sp.getNewShares());
                        entry.setLow(entry.getLow() * sp.getOldShares() / sp.getNewShares());
                        entry.setClose(entry.getClose() * sp.getOldShares() / sp.getNewShares());
                        entry.setVolume(entry.getVolume() * sp.getNewShares() / sp.getOldShares());
                    }
                }
            }
        }
        for (ArrayList hist : ohlcv.values()) {
            ArrayList<Double> tempSwing = TradingUtil.generateSwings(hist);
            ArrayList<Integer> tempTrend = TradingUtil.generateTrend(tempSwing);
            swing.add(tempSwing);
            trend.add(tempTrend);
        }
    }

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
        split = System.getProperty("Splits") == null ? "" : System.getProperty("Splits");
        firstMonthExpiry=System.getProperty("FirstMonthExpiry");
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

            Thread t = new Thread(new HistoricalBars(strategy, type));
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
        int futureID=TradingUtil.getFutureIDFromSymbol(id,firstMonthExpiry);
        if(allPositions.size()==this.strategySymbols.size()){ //allPositions has been initialized
        PositionDetails p = allPositions.get(futureID);
        if (event.getTickType() == com.ib.client.TickType.LAST && this.strategySymbols.contains(id) && p.getPosition() == 0 && endDate.before(new Date())) {//check for entry
            BeanOHLC lastBar = ohlcv.get(id).get(ohlcv.get(id).size() - 1);
            long today;
            if (timeZone.compareTo("") == 0) {
                today = Long.parseLong(DateUtil.getFormatedDate("yyyymmdd", new Date().getTime(), TimeZone.getDefault()));
            } else {
                today = Long.parseLong(DateUtil.getFormatedDate("yyyymmdd", new Date().getTime(), TimeZone.getTimeZone(timeZone)));
            }
            lastBar.setClose(Parameters.symbol.get(id).getLastPrice());
            lastBar.setHigh(Parameters.symbol.get(id).getHighPrice());
            lastBar.setLow(Parameters.symbol.get(id).getLowPrice());
            lastBar.setOpen(Parameters.symbol.get(id).getOpenPrice());
            lastBar.setVolume(Parameters.symbol.get(id).getVolume());
            lastBar.setOpenTime(today);
            ArrayList<BeanOHLC> tempHistoricalData = ohlcv.get(id);
            if (lastBar.getOpenTime() == today) { //replace the last bar
                tempHistoricalData.add(tempHistoricalData.size() - 1, lastBar);
                ohlcv.put(id, tempHistoricalData);
            } else { //insert a new bar
                tempHistoricalData.add(lastBar);
                ohlcv.put(id, tempHistoricalData);
            }
            ArrayList<Double> tempSwing = TradingUtil.generateSwings(ohlcv.get(id));
            ArrayList<Integer> tempTrend = TradingUtil.generateTrend(tempSwing);
            ArrayList<Double> volumeMA = TechnicalUtil.getSimpleMovingAverage(ohlcv.get(id), EnumBarValue.Volume, 19, 2);
            ArrayList<Double> macd = TechnicalUtil.getMACD(ohlcv.get(id), EnumBarValue.Close, 5, 20);
            ArrayList<Double> rsi = TechnicalUtil.getCutlerRSI(ohlcv.get(id), EnumBarValue.Close, 5);
            Double macdPercentageOfPrice = macd.get(macd.size() - 2) * 100 / Parameters.symbol.get(id).getLastPrice();
            Boolean B1 = tempTrend.get(tempTrend.size() - 1) == 1 && tempTrend.get(tempTrend.size() - 1) > tempTrend.get(tempTrend.size() - 2);   
            Boolean B2 = lastBar.getVolume() > volumeMA.get(volumeMA.size() - 2);
            Boolean B3 = macdPercentageOfPrice > -1 && macdPercentageOfPrice < 1;
            Boolean B4 = rsi.get(rsi.size() - 1) > 70 && rsi.get(rsi.size() - 1) < 100;
            Boolean S1 = tempTrend.get(tempTrend.size() - 1) == -1 && tempTrend.get(tempTrend.size() - 1) < tempTrend.get(tempTrend.size() - 2);
            Boolean S2 = lastBar.getVolume() > volumeMA.get(volumeMA.size() - 2);
            Boolean S3 = macdPercentageOfPrice < 0 && macdPercentageOfPrice > -100;
            Boolean S4 = rsi.get(rsi.size() - 1) < 30 && rsi.get(rsi.size() - 1) > 10;
            //log scan
            logger.log(Level.INFO,"Symbol: {0}, tempSwing: {1}, tempTrend: {2}, volumeMA: {3}, macd: {4}, rsi: {5},B1: {6}, B2: {7}, B3: {8}, B4: {9}, S1: {10}, S2: {11}, S3: {12}, S4 :{13}",
                    new Object[]{Parameters.symbol.get(id).getSymbol(),tempSwing.get(tempSwing.size() - 1),tempTrend.get(tempTrend.size() - 1),volumeMA.get(volumeMA.size() - 2),macd.get(macd.size() - 2),rsi.get(rsi.size() - 1)
                    ,B1,B2,B3,B4,S1,S2,S3,S4});            
            
            if (B1 && B2 && B3 && B4) {
                logger.log(Level.INFO, "Buy Entry");
                entry(futureID, EnumOrderSide.BUY, Parameters.symbol.get(futureID).getLastPrice(), 0);
                p.setId(futureID);
                p.setPosition(Parameters.symbol.get(futureID).getMinsize() * this.numberOfContracts);
                p.setPositionPrice(Parameters.symbol.get(futureID).getLastPrice());

            } else if (S1 && S2 && S3 && S4) {
                logger.log(Level.INFO, "Short Entry");
                entry(futureID, EnumOrderSide.SHORT, Parameters.symbol.get(id).getLastPrice(), 0);
                p.setId(futureID);
                p.setPosition(-Parameters.symbol.get(futureID).getMinsize() * this.numberOfContracts);
                p.setPositionPrice(Parameters.symbol.get(futureID).getLastPrice());
            }
        }
    }
    }
}
