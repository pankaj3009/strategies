/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.algorithms.swing;

import com.incurrency.framework.Strategy;
import com.RatesClient.Subscribe;
import com.incurrency.framework.BeanConnection;
import com.incurrency.framework.BeanOHLC;
import com.incurrency.framework.BeanPosition;
import com.incurrency.framework.BeanSymbol;
import com.incurrency.framework.BrokerageRate;
import com.incurrency.framework.DateUtil;
import com.incurrency.framework.EnumBarValue;
import com.incurrency.framework.EnumNotification;
import com.incurrency.framework.EnumOrderStage;
import com.incurrency.framework.EnumOrderSide;
import com.incurrency.framework.EnumOrderType;
import com.incurrency.framework.HistoricalBars;
import com.incurrency.framework.Launch;
import com.incurrency.framework.MainAlgorithm;
import com.incurrency.framework.NotificationEvent;
import com.incurrency.framework.Parameters;
import com.incurrency.framework.Splits;
import com.incurrency.framework.TechnicalUtil;
import com.incurrency.framework.TradeEvent;
import com.incurrency.framework.TradeListener;
import com.incurrency.framework.TradingUtil;
import java.io.FileInputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
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
    boolean tradeFutures=true;
    Timer preProcessing;

    TimerTask runOpeningOrders = new TimerTask() {
        @Override
        public void run() {
            System.out.println("In runOpeningOrders");
            long today;
            if (getTimeZone().compareTo("") == 0) {
                today = Long.parseLong(DateUtil.getFormatedDate("yyyyMMdd", new Date().getTime(), TimeZone.getDefault()));
            } else {
                today = Long.parseLong(DateUtil.getFormatedDate("yyyyMMdd", new Date().getTime(), TimeZone.getTimeZone(getTimeZone())));
            }
            //place SL and TP OCO orders
            for (BeanPosition p : getPosition().values()) {
                double takeProfit;
                double stopLoss;
                int stockid = TradingUtil.getIDFromFuture(p.getSymbolid());
                ArrayList<Double> atr = TechnicalUtil.getATR(ohlcv.get(stockid), 10);
                ArrayList<BeanOHLC> b = ohlcv.get(stockid);
                if (b.get(b.size() - 1).getOpenTime() == today) {
                    takeProfit = atr.get(atr.size() - 2) * 2; //market has opened and the last beanOHLC is for today. So take the second last bar.
                    stopLoss = atr.get(atr.size() - 2) * 3;
                } else {
                    takeProfit = atr.get(atr.size() - 1) * 2;
                    stopLoss = atr.get(atr.size() - 1) * 3;
                }
                if (p.getPosition() > 0) {
                    //get take profit
                    String passToOrderObject=p.getSymbolid()+DateUtil.getFormatedDate("yyyyMMdd", p.getPositionInitDate().getTime());
                    exit(p.getSymbolid(), EnumOrderSide.SELL,EnumOrderType.LMT, p.getPrice() + takeProfit, 0, Parameters.symbol.get(p.getSymbolid()).getSymbol(), false, "DAY", false,EnumNotification.OCOTP,passToOrderObject); //take profit
                    exit(p.getSymbolid(), EnumOrderSide.SELL,EnumOrderType.LMT, p.getPrice() - stopLoss, p.getPrice() - stopLoss, Parameters.symbol.get(p.getSymbolid()).getSymbol(), true, "DAY", false,EnumNotification.OCOSL,passToOrderObject); //stop loss
                } else if (p.getPosition() < 0) {
                    String passToOrderObject=p.getSymbolid()+DateUtil.getFormatedDate("yyyyMMdd", p.getPositionInitDate().getTime());
                    exit(p.getSymbolid(), EnumOrderSide.COVER,EnumOrderType.LMT, p.getPrice() - takeProfit, 0, Parameters.symbol.get(p.getSymbolid()).getSymbol(), false, "DAY", false,EnumNotification.OCOTP,passToOrderObject); //take profit
                    exit(p.getSymbolid(), EnumOrderSide.COVER,EnumOrderType.LMT, p.getPrice() + stopLoss, p.getPrice() + stopLoss, Parameters.symbol.get(p.getSymbolid()).getSymbol(), true, "DAY", false,EnumNotification.OCOSL,passToOrderObject); //stop loss
                }
            }
        }
    };
    
    public BeanSwing(MainAlgorithm m, String parameterFile, ArrayList<String> accounts) {
        super(m, "swing", "FUT", parameterFile, accounts);
        if(!tradeFutures){
         for (BrokerageRate b: getBrokerageRate()){
            b.type="STK";
        }
        }
        loadParameters("swing", parameterFile);
        String[] tempStrategyArray = parameterFile.split("\\.")[0].split("-");
        for (BeanConnection c : Parameters.connection) {
            c.getWrapper().addTradeListener(this);
            c.initializeConnection(tempStrategyArray[tempStrategyArray.length - 1]);
        }
        if (Subscribe.tes != null) {
            Subscribe.tes.addTradeListener(this);
        }
        TradingUtil.writeToFile(getStrategy() + ".csv", "symbol, stock price,future price,volume,trend yesterday,trend today,swing level today,MA Volume yesterday, MACD yesterday,MACD Price Percentage,RSI,B1,B2,B3,B4,S1,S2,S3,S4,Current Position,Event");                
        getDailyHistoricalData("swing", "STK");
        getOms().addNotificationListeners(this);
        
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
        
        for (int i = 0; i < this.getStrategySymbols().size(); i++) {
            int id = getStrategySymbols().get(i);
            BeanSymbol s = Parameters.symbol.get(id);
            ArrayList<BeanOHLC> tempOHLCV = new ArrayList();
            for (Map.Entry<Long, BeanOHLC> entry : s.getDailyBar().getHistoricalBars().entrySet()) {
                tempOHLCV.add(entry.getValue());
            }
            ohlcv.put(id, tempOHLCV);
            getPosition().put(id, new BeanPosition(id,getStrategy()));
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
        // Initialize Variables
        split = System.getProperty("Splits") == null ? "" : System.getProperty("Splits");
        firstMonthExpiry = System.getProperty("FirstMonthExpiry");
        tradeFutures=Boolean.parseBoolean(System.getProperty("TradeFutures"));
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
    
    @Override
        public void notificationReceived(NotificationEvent event) {
        switch (event.getNotify().getNotificationType()){
            case SL:
                logger.log(Level.INFO,"{0},{1},SL Triggered, Symbol:{2}",new Object[]{event.getNotify().getStrategy(),event.getNotify().getAccount(),Parameters.symbol.get(event.getNotify().getId()).getSymbol()});
                //update SL order to a fast execution for accounts and update positions
                int id=event.getNotify().getId();
               // getOms().tes.fireOrderEvent(-1, internalOpenOrders.get(id), Parameters.symbol.get(id), EnumOrderSide.SELL, size, Parameters.symbol.get(j.getKey()).getLastPrice() + cushion, 0, getStrategy(), getMaxOrderDuration(), "", EnumOrderStage.Amend, getMaxOrderDuration(), getDynamicOrderDuration(), getMaxSlippageExit(), true);

                
                break;
            case TP:
                 logger.log(Level.INFO,"{0},{1},TP Triggered, Symbol:{2}",new Object[]{event.getNotify().getStrategy(),event.getNotify().getAccount(),Parameters.symbol.get(event.getNotify().getId()).getSymbol()});
                //update TP order to a fast execution for accounts.

                break;
            default:
                break;
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
            } catch (Exception ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }
    
    @Override
    public void tradeReceived(TradeEvent event) {
        try {
            int id = event.getSymbolID(); //here symbolID is with zero base.
            if (Parameters.symbol.get(id).getType().compareTo("STK") == 0 && getStrategySymbols().contains(id)) {
                int futureID=-1;
                if(tradeFutures){
                futureID = TradingUtil.getFutureIDFromSymbol(id, firstMonthExpiry);
                }else{
                    futureID=id;
                }
                BeanPosition p = getPosition().get(futureID);
                if (p != null && ohlcv.get(id) != null && ohlcv.get(id).size() > 0 && event.getTickType() == com.ib.client.TickType.LAST && getStrategySymbols().contains(id) && p.getPosition() == 0 && getEndDate().after(new Date())) {//check for entry
                    BeanOHLC lastBar = ohlcv.get(id).get(ohlcv.get(id).size() - 1);
                    long today;
                    if (getTimeZone().compareTo("") == 0) {
                        today = Long.parseLong(DateUtil.getFormatedDate("yyyyMMdd", new Date().getTime(), TimeZone.getDefault()));
                    } else {
                        today = Long.parseLong(DateUtil.getFormatedDate("yyyyMMdd", new Date().getTime(), TimeZone.getTimeZone(getTimeZone())));
                    }
                    lastBar.setClose(Parameters.symbol.get(id).getLastPrice());
                    lastBar.setHigh(Parameters.symbol.get(id).getHighPrice());
                    lastBar.setLow(Parameters.symbol.get(id).getLowPrice());
                    lastBar.setOpen(Parameters.symbol.get(id).getOpenPrice());
                    lastBar.setVolume(Parameters.symbol.get(id).getVolume());
                    lastBar.setOpenTime(today);
                    ArrayList<BeanOHLC> tempHistoricalData = ohlcv.get(id);
                    if (lastBar.getOpenTime() == today) { //replace the last bar
                        tempHistoricalData.set(tempHistoricalData.size() - 1, lastBar);
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
                    int position=getPosition().get(futureID)==null?0:getPosition().get(futureID).getPosition();
                    //log scan
                    TradingUtil.writeToFile(getStrategy() + ".csv", Parameters.symbol.get(id).getSymbol()+","+Parameters.symbol.get(id).getLastPrice()+","+Parameters.symbol.get(futureID).getLastPrice()+","+Parameters.symbol.get(id).getVolume()+","+
                            tempTrend.get(tempTrend.size() - 2)+","+tempTrend.get(tempTrend.size()-1)+","+tempSwing.get(tempSwing.size()-1)+","+volumeMA.get(volumeMA.size() - 2)+","+macd.get(macd.size() - 2)+","+
                            macdPercentageOfPrice+","+rsi.get(rsi.size() - 1)+","+B1+","+B2+","+B3+","+B4+","+S1+","+S2+","+S3+","+S4+","+position+","+"SCAN");

                    if (B1 && B2 && B3 && B4 && position<=0) {
                        entry(futureID, EnumOrderSide.BUY, EnumOrderType.LMT,Parameters.symbol.get(futureID).getLastPrice(), 0, false,EnumNotification.REGULARENTRY,"");
                        p.setSymbolid(futureID);
                        p.setPosition(Parameters.symbol.get(futureID).getMinsize() * getNumberOfContracts());
                        p.setPrice(Parameters.symbol.get(futureID).getLastPrice());
                        
                    } else if (S1 && S2 && S3 && S4 && position>=0) {
                        entry(futureID, EnumOrderSide.SHORT,EnumOrderType.LMT, Parameters.symbol.get(id).getLastPrice(), 0, false,EnumNotification.REGULARENTRY,"");
                        p.setSymbolid(futureID);
                        p.setPosition(-Parameters.symbol.get(futureID).getMinsize() * getNumberOfContracts());
                        p.setPrice(Parameters.symbol.get(futureID).getLastPrice());
                    }
                }                
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, null, e);
        }
    }
}
