/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.algorithms.turtle;

import com.RatesClient.Subscribe;
import com.incurrency.framework.*;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Serializable;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.SortedMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.regression.SimpleRegression;

/**
 *
 * @author pankaj
 */
public class BeanTurtle extends Strategy implements Serializable, HistoricalBarListener, TradeListener {

    private static Date lastOrderDate;
    private static final Logger logger = Logger.getLogger(BeanTurtle.class.getName());

    //</editor-fold>
    //<editor-fold defaultstate="collapsed" desc="getter-setter">
    /**
     * @return the logger
     */
    public static Logger getLOGGER() {
        return logger;
    }

    /**
     * @return the lastOrderDate
     */
    public static Date getLastOrderDate() {
        return lastOrderDate;
    }

    /**
     * @param aLastOrderDate the lastOrderDate to set
     */
    public static void setLastOrderDate(Date aLastOrderDate) {
        lastOrderDate = aLastOrderDate;
    }
    private ArrayList<ArrayList<Long>> cumVolume = new ArrayList<>();
    private ArrayList<Double> highestHigh = new <Double> ArrayList();  //algo parameter 
    private ArrayList<Double> lowestLow = new <Double> ArrayList(); //algo parameter 
    private ArrayList<Double> close = new <Double> ArrayList();
    private ArrayList<Long> barNumber = new <Long> ArrayList();
    private ArrayList<Double> slope = new <Double>ArrayList();
    private ArrayList<Long> Volume = new ArrayList();
    private ArrayList<Double> VolumeMA = new ArrayList();
    private ArrayList<Long> advanceEntryOrder = new ArrayList();
    private ArrayList<Long> advanceExitOrder = new ArrayList();
    private int channelDuration;
    private int regressionLookBack;
    private ArrayList<Boolean> breachUpInBar = new ArrayList();
    private ArrayList<Boolean> breachDownInBar = new ArrayList();
    private ArrayList<Integer> breachUp = new ArrayList();
    private ArrayList<Integer> breachDown = new ArrayList();
    private int startBars;
    Timer openProcessing;
    private double maVolumeLong;
    private double maVolumeShort;
    //Strategy Filters
    private ArrayList<Double> sd = new ArrayList<>();
    private ArrayList<Double> yesterdayZScore = new ArrayList<>();
    private ArrayList<Double> yesterdayClose = new ArrayList<>();
    private ArrayList<Double> zScoreOnEntry = new ArrayList<>();
    private ArrayList<Integer> entryBar = new ArrayList();
    private int index;
    private String expiry;
    double entryCushion;
    double exitCushion;
    // <editor-fold defaultstate="collapsed" desc="Helper Functions">
    TimerTask realTimeBars = new TimerTask() {
        @Override
        public void run() {
            requestRealTimeBars();
        }
    };

    public BeanTurtle(MainAlgorithm m, String parameterFile, ArrayList<String> accounts) {
        super(m, "idt", "FUT", parameterFile, accounts);
        loadParameters("idt", parameterFile);

        for (int i = 0; i < Parameters.symbol.size(); i++) {
            cumVolume.add(i, new ArrayList<Long>());
            cumVolume.get(i).add(0L);
            highestHigh.add(999999999D);
            lowestLow.add(0D);
            close.add(0D);
            barNumber.add(0L);
            slope.add(0D);
            Volume.add(Long.MIN_VALUE);
            VolumeMA.add(Double.MIN_VALUE);
            advanceEntryOrder.add(0L);
            advanceExitOrder.add(0L);
            breachUpInBar.add(Boolean.FALSE);
            breachDownInBar.add(Boolean.FALSE);
            breachUp.add(0);
            breachDown.add(0);
            sd.add(0D);
            yesterdayZScore.add(0D);
            yesterdayClose.add(0D);
            zScoreOnEntry.add(0D);
            entryBar.add(0);
        }
        for (int i = 0; i < Parameters.symbol.size(); i++) {
            Parameters.symbol.get(i).getOneMinuteBar().addHistoricalBarListener(this);
            Parameters.symbol.get(i).getDailyBar().addHistoricalBarListener(this);
            Parameters.symbol.get(i).getFiveSecondBars().addHistoricalBarListener(this);
        }
        String[] tempStrategyArray = parameterFile.split("\\.")[0].split("-");
        for (BeanConnection c : Parameters.connection) {
            c.getWrapper().addTradeListener(this);
            c.initializeConnection(tempStrategyArray[tempStrategyArray.length - 1]);
        }
        if (Subscribe.tes != null) {
            Subscribe.tes.addTradeListener(this);
        }
//        populateLastTradePrice();
        getHistoricalData("idt");
        TradingUtil.writeToFile(getStrategy() + "datalogs.csv", "symbol" + "," + "Completed Bars" + "," + "yesterday close" + "," + "yesterdayIndexZScore" + "," + "yesterdayZScore" + "," + "zscore" + "," + "highLevel" + "," + "lowLevel" + "," + "lastPrice" + "," + "lastbarClose" + ",Relative Vol,EntryBar,ZScore On Entry,Stop Loss,Change from yesterday,comment");
        /*
        for (int i : getStrategySymbols()) {
            ArrayList<BeanOHLC> prices = new ArrayList<>();
            if (Parameters.symbol.get(i).getType().equals("STK") || Parameters.symbol.get(i).getType().equals("IND")) {
                for (Map.Entry<Long, BeanOHLC> entry : Parameters.symbol.get(i).getDailyBar().getHistoricalBars().entrySet()) {
                    prices.add(entry.getValue());
                }
                Collections.reverse(prices);
                ArrayList<Double> sdArray = TechnicalUtil.getStandardDeviationOfReturns(prices, 30, 9);
                if (sdArray == null) {
                    logger.log(Level.INFO, "ALL,{0},SD Array was null. Symbol:{1}", new Object[]{"IDT", Parameters.symbol.get(i).getSymbol()});
                    sd.set(i, 0D);
                } else {
                    int size = sdArray.size();
                    double symbolSD = sdArray.get(size - 1);
                    sd.set(i, symbolSD);
                    long today = Long.parseLong(DateUtil.getFormatedDate("yyyyMMdd", getStartDate().getTime()));
                    long today_1 = Parameters.symbol.get(i).getDailyBar().getHistoricalBars().floorKey(today - 1);
                    long today_2 = Parameters.symbol.get(i).getDailyBar().getHistoricalBars().floorKey(today_1 - 1);
                    double close_1 = Parameters.symbol.get(i).getDailyBar().getHistoricalBars().get(today_1).getClose();
                    double close_2 = Parameters.symbol.get(i).getDailyBar().getHistoricalBars().get(today_2).getClose();
                    yesterdayZScore.set(i, ((close_1 / close_2) - 1) / symbolSD);
                    yesterdayClose.set(i, close_1);
                    TradingUtil.writeToFile(getStrategy() + "datalogs.csv", Parameters.symbol.get(i).getSymbol() + "," + 0 + "," + close_1 + "," + 0 + "," + (close_1 / close_2 - 1) / symbolSD + "," + 0 + "," + 0 + "," + 0 + "," + 0 + "," + 0 + "," + "initialization");
                }
            }
        }
        */
        openProcessing = new Timer("Timer: IDT Waiting for Market Open");
        if (new Date().compareTo(getStartDate()) < 0) { // if time is before startdate, schedule realtime bars
            openProcessing.schedule(realTimeBars, getStartDate());
            if (!Launch.headless) {
            Launch.setMessage("Waiting for market open");
            logger.log(Level.INFO,"Strategy,{0},{1}, Real time bars request waiting for market open", new Object[]{allAccounts,getStrategy()});
        }
        } else {
            Launch.setMessage("Requesting Realtime Bars");
            logger.log(Level.INFO," Strategy,{0},{1},Starting request of realtime bars", new Object[]{allAccounts,getStrategy()});
            requestRealTimeBars();
        }
    }

    private void loadParameters(String strategy, String parameterFile) {
        Properties p = new Properties(System.getProperties());
        FileInputStream propFile;
        try {
            propFile = new FileInputStream(parameterFile);
            try {
                p.load(propFile);
            } catch (IOException ex) {
                logger.log(Level.SEVERE, null, ex);
            }
        } catch (FileNotFoundException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        System.setProperties(p);
        String currDateStr = DateUtil.getFormatedDate("yyyyMMdd", Parameters.connection.get(0).getConnectionTime());
        String lastOrderDateStr = currDateStr + " " + System.getProperty("LastOrderTime");
        lastOrderDate = DateUtil.parseDate("yyyyMMdd HH:mm:ss", lastOrderDateStr);
        if (lastOrderDate.compareTo(getStartDate()) < 0 && new Date().compareTo(lastOrderDate) > 0) {
            lastOrderDate = DateUtil.addDays(lastOrderDate, 1); //system date is > start date time. Therefore we have not crossed the 12:00 am barrier
        }
        maVolumeLong = Double.parseDouble(System.getProperty("MAVolumeLong"));
        maVolumeShort = Double.parseDouble(System.getProperty("MAVolumeShort"));
        channelDuration = Integer.parseInt(System.getProperty("ChannelDuration"));
        regressionLookBack = Integer.parseInt(System.getProperty("RegressionLookBack"));
        startBars = Integer.parseInt(System.getProperty("StartBars"));
        index = TradingUtil.getIDFromSymbol(System.getProperty("Index"), "IND", "", "", "");
        expiry = System.getProperty("FutureExpiry") == null ? "" : System.getProperty("FutureExpiry");
        entryCushion = System.getProperty("EntryCushion") == null ? 0D : Double.parseDouble(System.getProperty("EntryCushion"));
        exitCushion = System.getProperty("ExitCushion") == null ? 0D : Double.parseDouble(System.getProperty("ExitCushion"));

        String concatAccountNames = "";
        for (String account : getAccounts()) {
            concatAccountNames = ":" + account;
        }
        logger.log(Level.INFO, "-----{0} Parameters----Accounts used {1} ----- Parameter File {2}", new Object[]{strategy.toUpperCase(), concatAccountNames, parameterFile});
        logger.log(Level.INFO, "Last Order Time: {0}", lastOrderDate);
        logger.log(Level.INFO, "Channel Duration: {0}", channelDuration);
        logger.log(Level.INFO, "Index: {0}", index);
        logger.log(Level.INFO, "Expiry: {0}", expiry);
        logger.log(Level.INFO, "Entry Cushion: {0}", entryCushion);
        logger.log(Level.INFO, "Exit Cushion: {0}", exitCushion);
    }

    private void getHistoricalData(String mainStrategy) {
        try {
            //get historical data - this can be done before start time, assuming the program is started next day
            //String type = Parameters.symbol.get(strategySymbols.get(0)).getType();
            Thread t = new Thread(new HistoricalBars(mainStrategy, "STK"));
            t.setName("Historical Bars");
            if (!Launch.headless) {
                Launch.setMessage("Starting request of Historical Data for yesterday");
            }
            t.start();
//            t.join();
            Thread i = new Thread(new HistoricalBars(mainStrategy, "IND"));
            i.setName("Index");
            i.start();
//            i.join();
            Thread.sleep(10000);
        } catch (Exception ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    private void populateLastTradePrice() {
        try {
            Connection connect;
            PreparedStatement preparedStatement;
            ResultSet rs;
            connect = DriverManager.getConnection("jdbc:mysql://72.55.179.5:3306/histdata", "root", "spark123");
            //statement = connect.createStatement();
            for (int j = 0; j < Parameters.symbol.size(); j++) {
                if (Pattern.compile(Pattern.quote("idt"), Pattern.CASE_INSENSITIVE).matcher(Parameters.symbol.get(j).getStrategy()).find()) {
                    String name = Parameters.symbol.get(j).getSymbol() + "_FUT";
                    preparedStatement = connect.prepareStatement("select * from dharasymb where name=? order by date desc LIMIT 1");
                    preparedStatement.setString(1, name);
                    rs = preparedStatement.executeQuery();
                    if (rs != null) {
                        while (rs.next()) {
                            double tempPrice = rs.getDouble("tickclose");
                            Parameters.symbol.get(j).setYesterdayLastPrice(tempPrice);
                            logger.log(Level.FINE, "YesterDay Close:{0}", new Object[]{tempPrice});
                        }
                    } else {
                        Parameters.symbol.get(j).setYesterdayLastPrice(Parameters.symbol.get(j).getClosePrice());
                        logger.log(Level.FINE, "Another YesterDay Close:{0}", new Object[]{rs.getDouble("tickclose")});
                    }
                }
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, null, e);
        }
    }

    private void requestRealTimeBars() {
        if (!Launch.headless) {
            //TurtleMainUI.setStart(false);
            Launch.setMessage("Starting request of RealTime Bars");
        }
        new RealTimeBars();
    }

    // </editor-fold>
    @Override
    public void barsReceived(HistoricalBarEvent event) {
        int outsideid = event.getSymbol().getSerialno() - 1;
        if (getStrategySymbols().contains(outsideid)) {
            try {
                if (event.ohlc().getPeriodicity() == EnumBarSize.FiveSec) {
                    int id = event.getSymbol().getSerialno() - 1;
                    //Was there a need to have a position, but no position exists?
                    if (event.ohlc().getHigh() >= highestHigh.get(id)) {
                        //update lastprice and fire tradeevent
                        Parameters.symbol.get(id).setLastPrice(event.ohlc().getHigh());
                        this.tradeReceived(new TradeEvent(new Object(), id, 4));
                    } else if (event.ohlc().getLow() < lowestLow.get(id)) {
                        Parameters.symbol.get(id).setLastPrice(event.ohlc().getLow());
                        this.tradeReceived(new TradeEvent(new Object(), id, 4));
                    }
                } //For one minute bars
                else if (event.ohlc().getPeriodicity() == EnumBarSize.OneMin && getStartDate().compareTo(new Date()) < 0) {
                    int id = event.getSymbol().getSerialno() - 1;
                    this.close.set(id, event.ohlc().getClose());
                    int barno = event.barNumber();
                    logger.log(Level.FINE, "Bar No:{0}, Date={1}, Symbol:{2},FirstBarTime:{3}, LastBarTime:{4}, LastKey-FirstKey:{5}",
                    new Object[]{barno, DateUtil.getFormatedDate("yyyyMMdd HH:mm:ss", event.ohlc().getOpenTime()), Parameters.symbol.get(id).getSymbol(), DateUtil.getFormatedDate("yyyyMMdd HH:mm:ss", event.list().firstKey()), DateUtil.getFormatedDate("yyyyMMdd HH:mm:ss", event.list().lastKey()), (event.list().lastKey() - event.list().firstKey()) / (1000 * 60)});
                    SimpleDateFormat sdfDate = new SimpleDateFormat("HH:mm:ss");//dd/MM/yyyy
                    String firstBarTime = sdfDate.format(event.list().firstEntry().getKey());
                    if (firstBarTime.contains(Parameters.symbol.get(id).getBarsstarttime()) && event.barNumber() == (event.list().lastEntry().getKey() - event.list().firstEntry().getKey()) / 60000 + 1) {//all bars till the latest bar are available
                        if (this.cumVolume.get(id).size() < event.list().size()) {
                            if (this.cumVolume.get(id).size() == 1) {
                                //first bar received
                                double priorClose = 0;
                                int i = 0;
                                for (Map.Entry<Long, BeanOHLC> entry : event.list().entrySet()) {
                                    BeanOHLC OHLC = entry.getValue();
                                    if (i == 0 && OHLC.getClose() > Parameters.symbol.get(id).getYesterdayLastPrice()) {
                                        this.cumVolume.get(id).set(0, OHLC.getVolume());
                                    } else if (i == 0 && OHLC.getClose() < Parameters.symbol.get(id).getYesterdayLastPrice()) {
                                        this.cumVolume.get(id).set(0, -OHLC.getVolume());
                                    }
                                    if (OHLC.getClose() > priorClose && i > 0) {
                                        long tempVol = this.cumVolume.get(id).get(i - 1) + OHLC.getVolume();
                                        this.cumVolume.get(id).add(tempVol);
                                    } else if (OHLC.getClose() < priorClose && i > 0) {
                                        long tempVol = this.cumVolume.get(id).get(i - 1) - OHLC.getVolume();
                                        this.cumVolume.get(id).add(tempVol);
                                    } else if (OHLC.getClose() == priorClose && i > 0) {
                                        long tempVol = this.getCumVolume().get(id).get(i - 1);
                                        this.getCumVolume().get(id).add(tempVol);
                                    }
                                    priorClose = OHLC.getClose();
                                    i = i + 1;
                                }
                            } else { //first bar has been processed
                                int ref = barno - 1;
                                BeanOHLC OHLC = event.list().lastEntry().getValue();
                                Long OHLCPriorKey = event.list().lowerKey(event.list().lastKey());
                                BeanOHLC OHLCPrior = event.list().get(OHLCPriorKey);
                                if (OHLC.getClose() > OHLCPrior.getClose()) {
                                    long tempVol = this.getCumVolume().get(id).get(ref - 1) + OHLC.getVolume();
                                    this.getCumVolume().get(id).add(tempVol);
                                } else if (OHLC.getClose() < OHLCPrior.getClose()) {
                                    long tempVol = this.getCumVolume().get(id).get(ref - 1) - OHLC.getVolume();
                                    this.getCumVolume().get(id).add(tempVol);
                                } else if (OHLC.getClose() == OHLCPrior.getClose()) {
                                    long tempVol = this.getCumVolume().get(id).get(ref - 1);
                                    this.getCumVolume().get(id).add(tempVol);
                                }
                            }
                        }

                        this.getVolume().set(id, event.ohlc().getVolume());
                        //Set Highest High and Lowest Low

                        if (event.barNumber() >= this.getChannelDuration()) {
                            Map<Long, BeanOHLC> temp;
                            temp = (SortedMap<Long, BeanOHLC>) event.list().subMap(event.ohlc().getOpenTime() - this.getChannelDuration() * 60 * 1000 + 1, event.ohlc().getOpenTime() + 1);
                            double HH = 0;
                            double LL = Double.MAX_VALUE;
                            for (Map.Entry<Long, BeanOHLC> entry : temp.entrySet()) {
                                HH = HH > entry.getValue().getHigh() ? HH : entry.getValue().getHigh();
                                LL = LL < entry.getValue().getLow() && LL != 0 ? LL : entry.getValue().getLow();
                            }
                            this.getHighestHigh().set(id, HH);
                            this.getLowestLow().set(id, LL);
                        }
                        //Set Slope
                        List<Long> tempCumVolume;
                        if (event.barNumber() >= this.getRegressionLookBack()) {
                            tempCumVolume = (List<Long>) this.getCumVolume().get(id).subList(event.barNumber() - this.getRegressionLookBack(), event.barNumber());
                            SimpleRegression regression = new SimpleRegression();
                            int itr = tempCumVolume.size();
                            double i = 0;
                            while (i < itr) {
                                regression.addData(i + 1, (Long) tempCumVolume.get((int) i));
                                i = i + 1;
                            }
                            double tempSlope = Double.isNaN(regression.getSlope()) == true ? 0D : regression.getSlope();
                            this.getSlope().set(id, tempSlope);
                        }
                        //set barupdown count
                        if (this.getBreachUpInBar().get(id) && this.getCumVolume().get(id).size() > this.getChannelDuration()) {
                            int breachup = this.getBreachUp().get(id);
                            this.getBreachUp().set(id, breachup + 1);
                            this.getBreachUpInBar().set(id, false);
                        }
                        if (this.getBreachDownInBar().get(id) && this.getCumVolume().get(id).size() > this.getChannelDuration()) {
                            int breachdown = this.getBreachDown().get(id);
                            this.getBreachDown().set(id, breachdown + 1);
                            this.getBreachDownInBar().set(id, false);
                        }
                        //set MA of volume
                        if (event.barNumber() >= this.getChannelDuration() - 1) {
                            Map<Long, BeanOHLC> temp;
                            temp = (SortedMap<Long, BeanOHLC>) event.list().subMap(event.ohlc().getOpenTime() - (this.getChannelDuration() - 1) * 60 * 1000 + 1, event.ohlc().getOpenTime() + 1);
                            DescriptiveStatistics stats = new DescriptiveStatistics();
                            for (Map.Entry<Long, BeanOHLC> entry : temp.entrySet()) {
                                stats.addValue(entry.getValue().getVolume());
                            }
                            this.getVolumeMA().set(id, stats.getMean());

                        }
                        logger.log(Level.FINE,"Strategy,{0},{1} Bars Updated, Symbol:{2}, BarCount:{3}",new Object[]{allAccounts,getStrategy(),Parameters.symbol.get(id).getSymbol(),this.cumVolume.get(id).size()});
                    }
                } else if (event.ohlc().getPeriodicity() == EnumBarSize.Daily) {
                    //update symbol volumes
                    int id = event.getSymbol().getSerialno() - 1;
                    if (getStrategySymbols().contains(id) && event.getSymbol().getDailyBar().isFinished()) {
                        BeanSymbol s = event.getSymbol();
                        if (Long.toString((long) event.list().lastKey()).equals(DateUtil.getFormatedDate("yyyyMMdd", System.currentTimeMillis()))) {
                            int key = event.list().size() - 3; //last value is today's date. I need day before yesterday. Therefore -3
                            s.setPriorDayVolume(String.valueOf(event.list().getValue(key).getVolume()));
                        } else {
                            int key = event.list().size() - 2;
                            s.setPriorDayVolume(String.valueOf(event.list().getValue(key).getVolume()));
                        }
                    //update stats
                   ArrayList<BeanOHLC> prices = new ArrayList<>();
                if (s.getType().equals("STK") || s.getType().equals("IND")) {
                for (Map.Entry<Long, BeanOHLC> entry : s.getDailyBar().getHistoricalBars().entrySet()) {
                    prices.add(entry.getValue());
                }
                Collections.reverse(prices);
                ArrayList<Double> sdArray = TechnicalUtil.getStandardDeviationOfReturns(prices, 30, 9);
                if (sdArray == null) {
                    logger.log(Level.INFO, "Strategy,{0},{1},SD Array was null. Symbol:{2}", new Object[]{allAccounts,getStrategy(), s.getSymbol()});
                    sd.set(id, 0D);
                } else {
                    int size = sdArray.size();
                    double symbolSD = sdArray.get(size - 1);
                    sd.set(id, symbolSD);
                    long today = Long.parseLong(DateUtil.getFormatedDate("yyyyMMdd", getStartDate().getTime()));
                    long today_1 = s.getDailyBar().getHistoricalBars().floorKey(today - 1);
                    long today_2 = s.getDailyBar().getHistoricalBars().floorKey(today_1 - 1);
                    double close_1 = s.getDailyBar().getHistoricalBars().get(today_1).getClose();
                    double close_2 = s.getDailyBar().getHistoricalBars().get(today_2).getClose();
                    yesterdayZScore.set(id, ((close_1 / close_2) - 1) / symbolSD);
                    yesterdayClose.set(id, close_1);
                    TradingUtil.writeToFile(getStrategy() + "datalogs.csv", s.getSymbol() + "," + 0 + "," + close_1 + "," + 0 + "," + (close_1 / close_2 - 1) / symbolSD + "," + 0 + "," + 0 + "," + 0 + "," + 0 + "," + 0 + "," + "initialization");
                }
            }    
                    
                    }

                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, null, e);
            }
        }
    }


        @Override
        public void tradeReceived(TradeEvent event) {
        try{
        int id = event.getSymbolID(); //here symbolID is with zero base.
        int futureid = expiry.equals("") ? id : TradingUtil.getFutureIDFromSymbol(id, expiry);
        if (getPosition().get(futureid) != null && yesterdayClose.get(id)>0) { //do initialization checks 
        if (getStrategySymbols().contains(id) && event.getTickType() == com.ib.client.TickType.LAST && Parameters.symbol.get(id).getType().equals("STK")) {
            double symbolClose = getClose().get(id);
            double indexZScoreYesterday = this.yesterdayZScore.get(index);
            double symbolZScoreYesterday = this.yesterdayZScore.get(id);
            double zscore = (symbolClose - this.yesterdayClose.get(id)) / symbolClose / this.sd.get(id);
            double highBoundary = getHighestHigh().get(id);
            double lowBoundary = getLowestLow().get(id);
            double relativeVol = sd.get(id) / sd.get(index);
            double worstCaseLoss = zScoreOnEntry.get(id) != 0 ? zScoreOnEntry.get(id) / relativeVol : 0D;
            synchronized(getPosition().get(futureid).lock){
                if (getLongOnly() && this.getCumVolume().get(id).size() >= this.getChannelDuration() && getPosition().get(futureid).getPosition() == 0 && zscore > 2 && Parameters.symbol.get(id).getLastPrice() >= highBoundary && relativeVol > 2 && getLastOrderDate().compareTo(new Date()) > 0) {
                    double midPoint=(Math.round((Parameters.symbol.get(futureid).getBidPrice()+Parameters.symbol.get(futureid).getAskPrice())/2/getTickSize()))*getTickSize();
                    double entryPrice=Math.min(Parameters.symbol.get(futureid).getLastPrice(),midPoint );
                    if (futureid == id) {
                        entry(futureid, EnumOrderSide.BUY,EnumOrderType.LMT, entryPrice, 0, false,EnumNotification.REGULARENTRY,"");
                    } else {
                        double cushion = ((int) (entryCushion / Parameters.symbol.get(futureid).getMinsize() / getTickSize())) * getTickSize();
                        entry(futureid, EnumOrderSide.BUY,EnumOrderType.LMT, entryPrice- cushion, 0, false,EnumNotification.REGULARENTRY,"");
                    }
                    zScoreOnEntry.set(id, zscore);
                    entryBar.set(id, this.getCumVolume().get(id).size());
                    TradingUtil.writeToFile(getStrategy() + "datalogs.csv", Parameters.symbol.get(id).getSymbol() + "," + cumVolume.get(id).size() + "," + 0 + "," + indexZScoreYesterday + "," + symbolZScoreYesterday + "," + zscore + "," + highBoundary + "," + lowBoundary + "," + Parameters.symbol.get(futureid).getLastPrice() + "," + close.get(id) + "," + relativeVol + "," + entryBar.get(id) + "," + zScoreOnEntry.get(id) + "," + (zScoreOnEntry.get(id) - worstCaseLoss) * (this.getCumVolume().get(id).size() - entryBar.get(id)) / (365 - entryBar.get(id)) + "," + (getClose().get(id)-yesterdayClose.get(id))/yesterdayClose.get(id)+","+"BUY");
                } else if (getShortOnly() && this.getCumVolume().get(id).size() >= this.getChannelDuration() && getPosition().get(futureid).getPosition() == 0 && zscore < -1 && Parameters.symbol.get(id).getLastPrice() <= lowBoundary && relativeVol > 2 && getLastOrderDate().compareTo(new Date()) > 0) {
                    double midPoint=(Math.round((Parameters.symbol.get(futureid).getBidPrice()+Parameters.symbol.get(futureid).getAskPrice())/2/getTickSize()))*getTickSize();
                    double entryPrice=Math.max(Parameters.symbol.get(futureid).getLastPrice(),midPoint );
                    if (futureid == id) {
                        entry(futureid, EnumOrderSide.SHORT,EnumOrderType.LMT, entryPrice, 0, false,EnumNotification.REGULARENTRY,"");
                    } else {
                        double cushion = ((int) (entryCushion / Parameters.symbol.get(futureid).getMinsize() / getTickSize())) * getTickSize();
                        entry(futureid, EnumOrderSide.SHORT,EnumOrderType.LMT, entryPrice + cushion, 0, false,EnumNotification.REGULARENTRY,"");
                    }
                    zScoreOnEntry.set(id, zscore);
                    entryBar.set(id, this.getCumVolume().get(id).size());
                    TradingUtil.writeToFile(getStrategy() + "datalogs.csv", Parameters.symbol.get(id).getSymbol() + "," + cumVolume.get(id).size() + "," + 0 + "," + indexZScoreYesterday + "," + symbolZScoreYesterday + "," + zscore + "," + highBoundary + "," + lowBoundary + "," + Parameters.symbol.get(futureid).getLastPrice() + "," + close.get(id) + "," + relativeVol + "," + entryBar.get(id) + "," + zScoreOnEntry.get(id) + "," + (zScoreOnEntry.get(id) - worstCaseLoss) * (this.getCumVolume().get(id).size() - entryBar.get(id)) / (365 - entryBar.get(id)) + ","+ (getClose().get(id)-yesterdayClose.get(id))/yesterdayClose.get(id)+"," + "SHORT");
                } else if (getPosition().get(futureid).getPosition() > 0 && (zscore < worstCaseLoss + (zScoreOnEntry.get(id) - worstCaseLoss) * (this.getCumVolume().get(id).size() - entryBar.get(id)) / (365 - entryBar.get(id)) || System.currentTimeMillis() > getEndDate().getTime())) {
                    double midPoint=(Math.round((Parameters.symbol.get(futureid).getBidPrice()+Parameters.symbol.get(futureid).getAskPrice())/2/getTickSize()))*getTickSize();
                    double entryPrice=Math.max(Parameters.symbol.get(futureid).getLastPrice(),midPoint );
                    this.exit(futureid, EnumOrderSide.SELL,EnumOrderType.LMT, entryPrice, 0, "", true, "DAY", false,EnumNotification.REGULAREXIT,"");
                    TradingUtil.writeToFile(getStrategy() + "datalogs.csv", Parameters.symbol.get(id).getSymbol() + "," + cumVolume.get(id).size() + "," + 0 + "," + indexZScoreYesterday + "," + symbolZScoreYesterday + "," + zscore + "," + highBoundary + "," + lowBoundary + "," + Parameters.symbol.get(futureid).getLastPrice() + "," + close.get(id) + "," + relativeVol + "," + getCumVolume().get(id).size()+ "," + zScoreOnEntry.get(id) + "," + (zScoreOnEntry.get(id) - worstCaseLoss) * (this.getCumVolume().get(id).size() - entryBar.get(id)) / (365 - entryBar.get(id)) + (getClose().get(id)-yesterdayClose.get(id))/yesterdayClose.get(id)+","+ "," + "SELL");
                    zScoreOnEntry.set(id, 0D);
                    entryBar.set(id, 0);
                } else if (getPosition().get(futureid).getPosition() < 0 && (zscore > worstCaseLoss + (zScoreOnEntry.get(id) - worstCaseLoss) * (this.getCumVolume().get(id).size() - entryBar.get(id)) / (365 - entryBar.get(id)) || System.currentTimeMillis() > getEndDate().getTime())) {
                    double midPoint=(Math.round((Parameters.symbol.get(futureid).getBidPrice()+Parameters.symbol.get(futureid).getAskPrice())/2/getTickSize()))*getTickSize();
                    double entryPrice=Math.min(Parameters.symbol.get(futureid).getLastPrice(),midPoint );
                    this.exit(futureid, EnumOrderSide.COVER,EnumOrderType.LMT, entryPrice, 0, "", true, "DAY", false,EnumNotification.REGULAREXIT,"");
                    TradingUtil.writeToFile(getStrategy() + "datalogs.csv", Parameters.symbol.get(id).getSymbol() + "," + cumVolume.get(id).size() + "," + 0 + "," + indexZScoreYesterday + "," + symbolZScoreYesterday + "," + zscore + "," + highBoundary + "," + lowBoundary + "," + Parameters.symbol.get(futureid).getLastPrice() + "," + close.get(id) + "," + relativeVol + "," + getCumVolume().get(id).size() + "," + zScoreOnEntry.get(id) + "," + (zScoreOnEntry.get(id) - worstCaseLoss) * (this.getCumVolume().get(id).size() - entryBar.get(id)) / (365 - entryBar.get(id)) + (getClose().get(id)-yesterdayClose.get(id))/yesterdayClose.get(id)+","+ "," + "COVER");
                    entryBar.set(id, 0);
                    zScoreOnEntry.set(id, 0D);
                } else if (this.getCumVolume().get(id).size() >= this.getChannelDuration()) {
                    //TradingUtil.writeToFile(getStrategy() + "datalogs.csv", Parameters.symbol.get(id).getSymbol() + "," + cumVolume.get(id).size() + "," + 0 + "," + indexZScoreYesterday + "," + symbolZScoreYesterday + "," + zscore + "," + highBoundary + "," + lowBoundary + "," + Parameters.symbol.get(futureid).getLastPrice() + "," + close.get(id) + "," + relativeVol + "," + entryBar.get(id) + "," + zScoreOnEntry.get(id) + "," + (zScoreOnEntry.get(id) - worstCaseLoss) * (this.getCumVolume().get(id).size() - entryBar.get(id)) / (365 - entryBar.get(id)) + "," + "SCAN");
                }
            }
            }
        }

        //force close of all open positions, after closeTime
        if (System.currentTimeMillis() + 3000 > getEndDate().getTime()) { //i wait for 3 seconds as there could be a gap in clock synchronization
            for (Map.Entry<Integer, BeanPosition> j : getPosition().entrySet()) {
                if (j.getValue().getPosition() > 0) {
                    //close long
                    int size = getNumberOfContracts() == 0 ? (int) (getExposure() / Parameters.symbol.get(j.getKey()).getLastPrice()) : Parameters.symbol.get(j.getKey()).getMinsize() * getNumberOfContracts();
                    getPosition().put(j.getKey(), new BeanPosition(j.getKey(), getStrategy()));
                    int entryInternalOrderID = this.internalOpenOrders.get(j.getKey());
                    Trade originalTrade = getTrades().get(new OrderLink(entryInternalOrderID, "Order"));
                    originalTrade.updateExit(j.getKey(), EnumOrderSide.SELL, Parameters.symbol.get(j.getKey()).getLastPrice(), size, this.internalOrderID++, getTimeZone(), "Order");
                    getTrades().put(new OrderLink(entryInternalOrderID, "Order"), originalTrade);
                    logger.log(Level.INFO, "Strategy,{0},{1},Sell. Force Close All Positions,Symbol:{2}", new Object[]{allAccounts,getStrategy(),Parameters.symbol.get(j.getKey()).getSymbol()});
                    double cushion = 0;
                    if (!expiry.equals("")) {
                        cushion = ((int) (exitCushion / Parameters.symbol.get(j.getKey()).getMinsize() / getTickSize())) * getTickSize();
                    }
                    double midPoint=(Math.round((Parameters.symbol.get(j.getKey()).getBidPrice()+Parameters.symbol.get(j.getKey()).getAskPrice())/2/getTickSize()))*getTickSize();
                    double entryPrice=Math.max(Parameters.symbol.get(j.getKey()).getLastPrice(),midPoint );
                   
                    getOms().tes.fireOrderEvent(this.internalOrderID - 1, entryInternalOrderID, Parameters.symbol.get(j.getKey()), EnumOrderSide.SELL ,EnumNotification.REGULAREXIT,EnumOrderType.LMT,size, entryPrice + cushion, 0, getStrategy(), getMaxOrderDuration(), EnumOrderStage.Cancel, getMaxOrderDuration(), getDynamicOrderDuration(), getMaxSlippageExit(), true,"");
                    getOms().tes.fireOrderEvent(this.internalOrderID - 1, entryInternalOrderID, Parameters.symbol.get(j.getKey()), EnumOrderSide.SELL,EnumNotification.REGULAREXIT,EnumOrderType.LMT, size, entryPrice + cushion, 0, getStrategy(), getMaxOrderDuration(), EnumOrderStage.Init, getMaxOrderDuration(), getDynamicOrderDuration(), getMaxSlippageExit(), true,"");
                    TradingUtil.writeToFile(getStrategy() + "datalogs.csv", Parameters.symbol.get(j.getKey()).getSymbol() + "," + cumVolume.get(j.getKey()).size() + "," + 0 + "," + 0 + "," + 0 + "," + 0 + "," + 0 + "," + 0 + "," + Parameters.symbol.get(j.getKey()).getLastPrice() + "," + close.get(id) + "," + "SELL CLOSE");
                    this.advanceExitOrder.set(j.getKey(), 0L);
                } else if (j.getValue().getPosition() < 0) {
                    //close short
                    int size = getNumberOfContracts() == 0 ? (int) (getExposure() / Parameters.symbol.get(j.getKey()).getLastPrice()) : Parameters.symbol.get(j.getKey()).getMinsize() * getNumberOfContracts();
                    getPosition().put(j.getKey(), new BeanPosition(j.getKey(), getStrategy()));
                    int entryInternalOrderID = this.internalOpenOrders.get(j.getKey());
                    Trade originalTrade = getTrades().get(new OrderLink(entryInternalOrderID, "Order"));
                    originalTrade.updateExit(j.getKey(), EnumOrderSide.COVER, Parameters.symbol.get(j.getKey()).getLastPrice(), size, this.internalOrderID++, getTimeZone(), "Order");
                    getTrades().put(new OrderLink(entryInternalOrderID, "Order"), originalTrade);
                    logger.log(Level.INFO, "Strategy,{0},{1},Cover. Force Close All Positions,Symbol:{2}", new Object[]{allAccounts,getStrategy(),Parameters.symbol.get(j.getKey()).getSymbol()});
                    double cushion = 0;
                    if (!expiry.equals("")) {
                        cushion = ((int) (exitCushion / Parameters.symbol.get(j.getKey()).getMinsize() / getTickSize())) * getTickSize();
                    }
                    double midPoint=(Math.round((Parameters.symbol.get(futureid).getBidPrice()+Parameters.symbol.get(futureid).getAskPrice())/2/getTickSize()))*getTickSize();
                    double entryPrice=Math.min(Parameters.symbol.get(futureid).getLastPrice(),midPoint );
                    
                    getOms().tes.fireOrderEvent(this.internalOrderID - 1, entryInternalOrderID, Parameters.symbol.get(j.getKey()), EnumOrderSide.COVER,EnumNotification.REGULAREXIT,EnumOrderType.LMT, size, entryPrice - cushion, 0, getStrategy(), getMaxOrderDuration(), EnumOrderStage.Cancel, getMaxOrderDuration(), getDynamicOrderDuration(), getMaxSlippageExit(), true,"");
                    getOms().tes.fireOrderEvent(this.internalOrderID - 1, entryInternalOrderID, Parameters.symbol.get(j.getKey()), EnumOrderSide.COVER,EnumNotification.REGULAREXIT,EnumOrderType.LMT, size, entryPrice - cushion, 0, getStrategy(), getMaxOrderDuration(), EnumOrderStage.Init, getMaxOrderDuration(), getDynamicOrderDuration(), getMaxSlippageExit(), true,"");
                    TradingUtil.writeToFile(getStrategy() + "datalogs.csv", Parameters.symbol.get(j.getKey()).getSymbol() + "," + cumVolume.get(j.getKey()).size() + "," + 0 + "," + 0 + "," + 0 + "," + 0 + "," + 0 + "," + 0 + "," + Parameters.symbol.get(j.getKey()).getLastPrice() + "," + close.get(id) + "," + "COVER CLOSE");
                    this.advanceExitOrder.set(j.getKey(), 0L);
                }
            }
        }
        }catch (Exception e){
            logger.log(Level.SEVERE,null,e);
        }
    }
    
    /**
     * @return the cumVolume
     */
    public ArrayList<ArrayList<Long>> getCumVolume() {
        return cumVolume;
    }

    /**
     * @param cumVolume the cumVolume to set
     */
    public void setCumVolume(ArrayList<ArrayList<Long>> cumVolume) {
        this.cumVolume = cumVolume;
    }

    /**
     * @return the highestHigh
     */
    public ArrayList<Double> getHighestHigh() {
        return highestHigh;
    }

    /**
     * @param highestHigh the highestHigh to set
     */
    public void setHighestHigh(ArrayList<Double> highestHigh) {
        this.highestHigh = highestHigh;
    }

    /**
     * @return the lowestLow
     */
    public ArrayList<Double> getLowestLow() {
        return lowestLow;
    }

    /**
     * @param lowestLow the lowestLow to set
     */
    public void setLowestLow(ArrayList<Double> lowestLow) {
        this.lowestLow = lowestLow;
    }

    /**
     * @return the close
     */
    public ArrayList<Double> getClose() {
        return close;
    }

    /**
     * @param close the close to set
     */
    public void setClose(ArrayList<Double> close) {
        this.close = close;
    }

    /**
     * @return the barNumber
     */
    public ArrayList<Long> getBarNumber() {
        return barNumber;
    }

    /**
     * @param barNumber the barNumber to set
     */
    public void setBarNumber(ArrayList<Long> barNumber) {
        this.barNumber = barNumber;
    }

    /**
     * @return the slope
     */
    public ArrayList<Double> getSlope() {
        return slope;
    }

    /**
     * @param slope the slope to set
     */
    public void setSlope(ArrayList<Double> slope) {
        this.slope = slope;
    }

    /**
     * @return the Volume
     */
    public ArrayList<Long> getVolume() {
        return Volume;
    }

    /**
     * @param Volume the Volume to set
     */
    public void setVolume(ArrayList<Long> Volume) {
        this.Volume = Volume;
    }

    /**
     * @return the VolumeMA
     */
    public ArrayList<Double> getVolumeMA() {
        return VolumeMA;
    }

    /**
     * @param VolumeMA the VolumeMA to set
     */
    public void setVolumeMA(ArrayList<Double> VolumeMA) {
        this.VolumeMA = VolumeMA;
    }

    /**
     * @return the channelDuration
     */
    public int getChannelDuration() {
        return channelDuration;
    }

    /**
     * @param channelDuration the channelDuration to set
     */
    public void setChannelDuration(int channelDuration) {
        this.channelDuration = channelDuration;
    }

    /**
     * @return the regressionLookBack
     */
    public int getRegressionLookBack() {
        return regressionLookBack;
    }

    /**
     * @param regressionLookBack the regressionLookBack to set
     */
    public void setRegressionLookBack(int regressionLookBack) {
        this.regressionLookBack = regressionLookBack;
    }

    /**
     * @return the volumeSlopeLongMultiplier
     */
    /**
     * @return the breachUpInBar
     */
    public ArrayList<Boolean> getBreachUpInBar() {
        return breachUpInBar;
    }

    /**
     * @param breachUpInBar the breachUpInBar to set
     */
    public void setBreachUpInBar(ArrayList<Boolean> breachUpInBar) {
        this.breachUpInBar = breachUpInBar;
    }

    /**
     * @return the breachDownInBar
     */
    public ArrayList<Boolean> getBreachDownInBar() {
        return breachDownInBar;
    }

    /**
     * @param breachDownInBar the breachDownInBar to set
     */
    public void setBreachDownInBar(ArrayList<Boolean> breachDownInBar) {
        this.breachDownInBar = breachDownInBar;
    }

    /**
     * @return the breachUp
     */
    public ArrayList<Integer> getBreachUp() {
        return breachUp;
    }

    /**
     * @param breachUp the breachUp to set
     */
    public void setBreachUp(ArrayList<Integer> breachUp) {
        this.breachUp = breachUp;
    }

    /**
     * @return the breachDown
     */
    public ArrayList<Integer> getBreachDown() {
        return breachDown;
    }

    /**
     * @param breachDown the breachDown to set
     */
    public void setBreachDown(ArrayList<Integer> breachDown) {
        this.breachDown = breachDown;
    }

    /**
     * @return the startBars
     */
    public int getStartBars() {
        return startBars;
    }

    /**
     * @param startBars the startBars to set
     */
    public void setStartBars(int startBars) {
        this.startBars = startBars;
    }

    /**
     * @return the advanceEntryOrder
     */
    public synchronized ArrayList<Long> getAdvanceEntryOrder() {
        return advanceEntryOrder;
    }

    /**
     * @param advanceEntryOrder the advanceEntryOrder to set
     */
    public synchronized void setAdvanceEntryOrder(ArrayList<Long> advanceEntryOrder) {
        this.advanceEntryOrder = advanceEntryOrder;
    }

    /**
     * @return the maVolumeLong
     */
    public double getMaVolumeLong() {
        return maVolumeLong;
    }

    /**
     * @param maVolumeLong the maVolumeLong to set
     */
    public void setMaVolumeLong(double maVolumeLong) {
        this.maVolumeLong = maVolumeLong;
    }

    /**
     * @return the maVolumeShort
     */
    public double getMaVolumeShort() {
        return maVolumeShort;
    }

    /**
     * @param maVolumeShort the maVolumeShort to set
     */
    public void setMaVolumeShort(double maVolumeShort) {
        this.maVolumeShort = maVolumeShort;
    }

    /**
     * @return the advanceExitOrder
     */
    public ArrayList<Long> getAdvanceExitOrder() {
        return advanceExitOrder;
    }

    /**
     * @param advanceExitOrder the advanceExitOrder to set
     */
    public void setAdvanceExitOrder(ArrayList<Long> advanceExitOrder) {
        this.advanceExitOrder = advanceExitOrder;
    }
    //</editor-fold>
}
