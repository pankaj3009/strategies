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
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.SortedMap;
import java.util.Timer;
import java.util.TimerTask;
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
    private ArrayList<Boolean> lastTradeWasLosing = new ArrayList();
    private ArrayList<Long> advanceEntryOrder = new ArrayList();
    private ArrayList<Long> advanceExitOrder = new ArrayList();
    private int channelDuration;
    private int regressionLookBack;
    private double volumeSlopeLongMultiplier;
    private double volumeSlopeShortMultipler;
    private ArrayList<Boolean> breachUpInBar = new ArrayList();
    private ArrayList<Boolean> breachDownInBar = new ArrayList();
    private ArrayList<Integer> breachUp = new ArrayList();
    private ArrayList<Integer> breachDown = new ArrayList();
    private double exposure = 0;
    private int startBars;
    private ArrayList<Boolean> exPriceBarLong = new ArrayList();
    private ArrayList<Boolean> exPriceBarShort = new ArrayList();
    Timer openProcessing;
    private double maVolumeLong;
    private double maVolumeShort;
    private boolean paramAdvanceEntryOrders;
    private boolean paramAdvanceExitOrders;
    //Strategy Filters
    private boolean checkForHistoricalLiquidity = true;
    ;
    private boolean checkForDirectionalBreaches = true;
    private boolean skipAfterWins = false;
    private boolean checkADRTrend = true;
    private double paramADRTRINBuy = 100;
    private double paramADRTRINShort = 100;
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
            exPriceBarLong.add(Boolean.FALSE);
            exPriceBarShort.add(Boolean.FALSE);
            lastTradeWasLosing.add(Boolean.FALSE);
        }
        for (int i = 0; i < Parameters.symbol.size(); i++) {
            Parameters.symbol.get(i).getOneMinuteBar().addHistoricalBarListener(this);
            Parameters.symbol.get(i).getDailyBar().addHistoricalBarListener(this);
            Parameters.symbol.get(i).getFiveSecondBars().addHistoricalBarListener(this);
        }
                String[] tempStrategyArray=parameterFile.split("\\.")[0].split("-");
        for (BeanConnection c : Parameters.connection) {
            c.getWrapper().addTradeListener(this);
        c.initializeConnection(tempStrategyArray[tempStrategyArray.length-1]);
        }
        if (Subscribe.tes != null) {
            Subscribe.tes.addTradeListener(this);
        }
        populateLastTradePrice();
        getHistoricalData();
        if (!Launch.headless) {
            Launch.setMessage("Waiting for market open");
        }
        openProcessing = new Timer("Timer: IDT Waiting for Market Open");
        if (new Date().compareTo(startDate) < 0) { // if time is before startdate, schedule realtime bars
            openProcessing.schedule(realTimeBars, startDate);
        } else {
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
        if (lastOrderDate.compareTo(startDate) < 0 && new Date().compareTo(lastOrderDate) > 0) {
            lastOrderDate = DateUtil.addDays(lastOrderDate, 1); //system date is > start date time. Therefore we have not crossed the 12:00 am barrier
        }
        maVolumeLong = Double.parseDouble(System.getProperty("MAVolumeLong"));
        maVolumeShort = Double.parseDouble(System.getProperty("MAVolumeShort"));
        String strAdvanceOrders = System.getProperty("AdvanceEntryOrders");
        paramAdvanceEntryOrders = Boolean.valueOf(strAdvanceOrders);
        paramAdvanceExitOrders = Boolean.valueOf(System.getProperty("AdvanceExitOrders"));
        channelDuration = Integer.parseInt(System.getProperty("ChannelDuration"));
        volumeSlopeLongMultiplier = Double.parseDouble(System.getProperty("VolSlopeMultLong"));
        setVolumeSlopeShortMultipler(Double.parseDouble(System.getProperty("VolSlopeMultShort")));
        regressionLookBack = Integer.parseInt(System.getProperty("RegressionLookBack"));
        exposure = Double.parseDouble(System.getProperty("Exposure"));
        startBars = Integer.parseInt(System.getProperty("StartBars"));
        this.skipAfterWins = System.getProperty("SkipAfterWins") == null ? false : Boolean.valueOf(System.getProperty("SkipAfterWins"));
        checkForHistoricalLiquidity = System.getProperty("CheckForHistoricalLiquidity") == null ? true : Boolean.valueOf(System.getProperty("CheckForHistoricalLiquidity"));
        checkForDirectionalBreaches = System.getProperty("CheckForDirectionalBreaches") == null ? true : Boolean.valueOf(System.getProperty("CheckForDirectionalBreaches"));
        checkADRTrend = System.getProperty("CheckADRTrend") == null ? true : Boolean.valueOf(System.getProperty("CheckADRTrend"));
        paramADRTRINBuy = System.getProperty("ADRTRINBuy") == null ? 100 : Double.parseDouble(System.getProperty("ADRTRINBuy"));
        paramADRTRINShort = System.getProperty("ADRTRINShort") == null ? 100 : Double.parseDouble(System.getProperty("ADRTRINShort"));
        String concatAccountNames = "";
        for (String account : getAccounts()) {
            concatAccountNames = ":" + account;
        }
        logger.log(Level.INFO, "-----{0} Parameters----Accounts used {1} ----- Parameter File {2}", new Object[]{strategy.toUpperCase(), concatAccountNames, parameterFile});
        logger.log(Level.INFO, "Last Order Time: {0}", lastOrderDate);
        logger.log(Level.INFO, "Channel Duration: {0}", channelDuration);
        logger.log(Level.INFO, "Start Bars: {0}", startBars);
        logger.log(Level.INFO, "Regression Lookback: {0}", regressionLookBack);
        logger.log(Level.INFO, "Volume Slope Multiplier Long: {0}", volumeSlopeLongMultiplier);
        logger.log(Level.INFO, "Volume Slope Multiplier Short: {0}", volumeSlopeShortMultipler);
        logger.log(Level.INFO, "MA Volume Long: {0}", maVolumeLong);
        logger.log(Level.INFO, "MA Volume Short: {0}", maVolumeShort);
        logger.log(Level.INFO, "Exposure: {0}", exposure);
        logger.log(Level.INFO, "Advance Entry Orders: {0}", paramAdvanceEntryOrders);
        logger.log(Level.INFO, "Advance Exit Orders: {0}", paramAdvanceExitOrders);
        logger.log(Level.INFO, "Skip After Wins: {0}", skipAfterWins);
        logger.log(Level.INFO, "Check for Historical Liquidity: {0}", checkForHistoricalLiquidity);
        logger.log(Level.INFO, "Check for directional breaches: {0}", checkForDirectionalBreaches);
        logger.log(Level.INFO, "Check ADR Trend: {0}", checkADRTrend);
    }

    private void getHistoricalData() {
        try {
            //get historical data - this can be done before start time, assuming the program is started next day
            String type = Parameters.symbol.get(strategySymbols.get(0)).getType();
            Thread t = new Thread(new HistoricalBars(getStrategy(), type));
            t.setName("Historical Bars");
            if (!Launch.headless) {
                Launch.setMessage("Starting request of Historical Data for yesterday");
            }
            t.start();
            t.join();
        } catch (InterruptedException ex) {
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

    private synchronized void requestRealTimeBars() {
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
        if (this.strategySymbols.contains(outsideid)) {
            try {
                if (event.ohlc().getPeriodicity() == EnumBarSize.FiveSec) {
                    int id = event.getSymbol().getSerialno() - 1;
                    //Was there a need to have a position, but no position exists?
                    if (event.ohlc().getHigh() > highestHigh.get(id)) {
                        //place buy order as the last bar had a higher high and other conditions were met.
                        logger.log(Level.FINE, "Buy Order.Symbol:{0}", new Object[]{Parameters.symbol.get(id).getSymbol()});
                        generateOrders(id, true, false, true, false, true, false, true, false, true);
                    } else if (event.ohlc().getLow() < lowestLow.get(id)) {
                        //place sell order as the last bar had a lower low and other conditions were met.
                        logger.log(Level.FINE, "Short Order.Symbol:{0}", new Object[]{Parameters.symbol.get(id).getSymbol()});
                        generateOrders(id, false, true, false, true, false, true, false, true, true);
                        //generateOrders(id, ruleHighestHigh, ruleLowestLow, ruleCumVolumeLong, ruleCumVolumeShort, ruleSlopeLong, ruleSlopeShort, ruleVolumeLong, ruleVolumeShort);
                    }
                    //Similarly, check for squareoffs that were missed
                    if (position.get(id) == 1 && event.ohlc().getLow() < lowestLow.get(id)) {
                        logger.log(Level.FINE, "Sell Order.Symbol:{0}", new Object[]{Parameters.symbol.get(id).getSymbol()});
                        generateOrders(id, false, true, false, false, false, false, false, false, true);
                    } else if (position.get(id) == -1 && event.ohlc().getHigh() > highestHigh.get(id)) {
                        logger.log(Level.FINE, "Cover Order.Symbol:{0}", new Object[]{Parameters.symbol.get(id).getSymbol()});
                        generateOrders(id, true, false, false, false, false, false, false, false, true);
                    }
                } //For one minute bars
                else if (event.ohlc().getPeriodicity() == EnumBarSize.OneMin && startDate.compareTo(new Date()) < 0) {
                    int id = event.getSymbol().getSerialno() - 1;

                    this.close.set(id, event.ohlc().getClose());
                    int barno = event.barNumber();
                    //logger.log(Level.FINE, "Bar No:{0}, Date={1}, Symbol:{2},FirstBarTime:{3}, LastBarTime:{4}, LastKey-FirstKey:{5}",
                    //new Object[]{barno, DateUtil.getFormatedDate("yyyyMMdd HH:mm:ss", event.ohlc().getOpenTime()), Parameters.symbol.get(id).getSymbol(), DateUtil.getFormatedDate("yyyyMMdd HH:mm:ss", event.list().firstKey()), DateUtil.getFormatedDate("yyyyMMdd HH:mm:ss", event.list().lastKey()), (event.list().lastKey() - event.list().firstKey()) / (1000 * 60)});
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
//                        boolean ruleCumVolumeLong = this.getCumVolume().get(id).get(this.getCumVolume().get(id).size() - 1) >= 0.05 * Double.parseDouble(Parameters.symbol.get(id).getPriorDayVolume());
//                        boolean ruleCumVolumeShort = this.getCumVolume().get(id).get(this.getCumVolume().get(id).size() - 1) <= Double.parseDouble(Parameters.symbol.get(id).getPriorDayVolume());
//                        boolean ruleSlopeLong = this.getSlope().get(id) > Double.parseDouble(Parameters.symbol.get(id).getPriorDayVolume()) * this.getVolumeSlopeLongMultiplier() / 375;
//                        boolean ruleSlopeShort = this.getSlope().get(id) < -Double.parseDouble(Parameters.symbol.get(id).getPriorDayVolume()) * this.getVolumeSlopeLongMultiplier() / 375;
                        boolean ruleVolumeLong = this.getVolume().get(id) > maVolumeLong * this.getVolumeMA().get(id);
                        boolean ruleVolumeShort = this.getVolume().get(id) > maVolumeShort * this.getVolumeMA().get(id);
                        boolean ruleCumVolumeLong = true;
                        boolean ruleCumVolumeShort = true;
                        boolean ruleSlopeLong=true;
                        boolean ruleSlopeShort=true;
                        //ruleVolumeLong = true;
                        //ruleVolumeShort = true;

                        if (ruleCumVolumeLong && ruleSlopeLong && ruleVolumeLong) {
                            exPriceBarLong.set(id, Boolean.TRUE);
                        } else {
                            exPriceBarLong.set(id, Boolean.FALSE);
                        }

                        if (ruleCumVolumeShort && ruleSlopeShort && ruleVolumeShort) {
                            exPriceBarShort.set(id, Boolean.TRUE);
                        } else {
                            exPriceBarShort.set(id, Boolean.FALSE);
                        }
                        if (paramAdvanceEntryOrders) {
                            placeAdvancedEntryOrders(id);
                        }
                        if (paramAdvanceExitOrders) {
                            placeAdvancedExitOrders(id);
                        }

                        logger.log(Level.FINEST, "{0},{1}, HH:{2}, LL:{3}, CumVol:{4}, LongVolCutoff:{5}, ShortVolCutoff:{6}, Slope:{7}, SlopeCutoff:{8}, BarVol:{9}, VolMA:{10}, BreachUp:{11}, BreachDown:{12}", new Object[]{
                            Parameters.symbol.get(id).getSymbol(),
                            sdfDate.format(event.list().lastEntry().getKey()),
                            this.getHighestHigh().get(id).toString(),
                            this.getLowestLow().get(id).toString(),
                            this.getCumVolume().get(id).get(event.barNumber() - 1).toString(),
                            0,
                            0,
                            this.getSlope().get(id).toString(),
                            0,
                            this.getVolume().get(id).toString(),
                            this.getVolumeMA().get(id).toString(),
                            this.getBreachUp().get(id),
                            this.getBreachDown().get(id)
                        });
                    }
                } else if (event.ohlc().getPeriodicity() == EnumBarSize.Daily) {
                    //update symbol volumes
                    int id = event.getSymbol().getSerialno() - 1;
                    BeanSymbol s = Parameters.symbol.get(id);
                    if (Long.toString(event.list().lastKey()).equals(DateUtil.getFormatedDate("yyyyMMdd", System.currentTimeMillis()))) {
                        //do nothing
                    } else {
                        s.setPriorDayVolume(String.valueOf(event.list().lastEntry().getValue().getVolume()));
                    }
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, null, e);
            }
        }
    }

    //<editor-fold defaultstate="collapsed" desc="Trade Rules">  
    public void placeAdvancedEntryOrders(int id) {
        //Place advance orders
        double threshold = this.getHighestHigh().get(id) - this.getLowestLow().get(id) > 1 ? 0.5 : (this.getHighestHigh().get(id) - this.getLowestLow().get(id)) / 2;
        //boolean tradeable = Double.parseDouble(Parameters.symbol.get(id).getPriorDayVolume()) / (Parameters.symbol.get(id).getMinsize() * 375) >= 6.0 && this.getCumVolume().get(id).size() > this.getStartBars() && Parameters.symbol.get(id).getLastPrice() > 0;
        boolean tradeable=true;
        if (this.getExposure() != 0 && this.getCumVolume().get(id).size() > this.getStartBars() && Parameters.symbol.get(id).getLastPrice() > 0) {
            tradeable = true;
        }
        int size = this.getExposure() != 0 ? (int) (this.getExposure() / Parameters.symbol.get(id).getLastPrice()) : Parameters.symbol.get(id).getMinsize();
        double highTriggerPrice = this.getHighestHigh().get(id) + getTickSize();
        double lowTriggerPrice = this.getLowestLow().get(id) - getTickSize();
        //Amend Entry Advance orders
        if (position.get(id) == 0 && getAdvanceEntryOrder().get(id) == 1) { //advance order has been placed
            if ((Parameters.symbol.get(id).getLastPrice() + threshold) > this.getHighestHigh().get(id)
                    && (Parameters.symbol.get(id).getLastPrice() - threshold) > this.getLowestLow().get(id)
                    && this.getBreachUp().get(id) >= this.getBreachDown().get(id)
                    && exPriceBarLong.get(id) && BeanTurtle.getLastOrderDate().compareTo(new Date()) > 0 && this.getBreachUp().get(id) >= this.getBreachDown().get(id) && this.getBreachDown().get(id) >= 1) {
                //amend existing advance long order
                logger.log(Level.FINE, "Amend existing advance long order. Symbol:{0},LastPrice: {1}, LowPrice: :{2} ,HighPrice: :{3} ,Threshold: {4}", new Object[]{Parameters.symbol.get(id).getSymbol(), Parameters.symbol.get(id).getLastPrice(), this.getLowestLow().get(id), this.getHighestHigh().get(id), threshold});
                getOms().tes.fireOrderEvent(-1, -1, Parameters.symbol.get(id), EnumOrderSide.BUY, size, this.getHighestHigh().get(id), this.getHighestHigh().get(id), getStrategy(), 0, getExitType(), EnumOrderIntent.Amend, getMaxOrderDuration(), getDynamicOrderDuration(), getMaxSlippageEntry());
            } else {
                //cancel order. There is no need for advance buy order.
                logger.log(Level.FINE, "cancel order. There is no need for advance buy order. Symbol:{0},LastPrice: {1}, LowPrice: :{2} ,HighPrice: :{3} ,Threshold: {4}", new Object[]{Parameters.symbol.get(id).getSymbol(), Parameters.symbol.get(id).getLastPrice(), this.getLowestLow().get(id), this.getHighestHigh().get(id), threshold});
                getOms().tes.fireOrderEvent(-1, -1, Parameters.symbol.get(id), EnumOrderSide.BUY, size, this.getHighestHigh().get(id), highTriggerPrice, getStrategy(), 0, getExitType(), EnumOrderIntent.Cancel, getMaxOrderDuration(), getDynamicOrderDuration(), getMaxSlippageEntry());
                this.getAdvanceEntryOrder().set(id, 0L);
            }
        }


        if (position.get(id) == 0 && getAdvanceEntryOrder().get(id) == -1) {
            if ((Parameters.symbol.get(id).getLastPrice() - threshold) < this.getLowestLow().get(id)
                    && (Parameters.symbol.get(id).getLastPrice() + threshold) < this.getHighestHigh().get(id)
                    && this.getBreachDown().get(id) >= this.getBreachUp().get(id)
                    && exPriceBarShort.get(id) && BeanTurtle.getLastOrderDate().compareTo(new Date()) > 0 && this.getBreachDown().get(id) >= this.getBreachUp().get(id) && this.getBreachUp().get(id) >= 1) {
                //amend existing advance short order
                logger.log(Level.FINE, "Amend existing advance short order. Symbol:{0},LastPrice: {1}, LowPrice: :{2} ,HighPrice: :{3} ,Threshold: {4}", new Object[]{Parameters.symbol.get(id).getSymbol(), Parameters.symbol.get(id).getLastPrice(), this.getLowestLow().get(id), this.getHighestHigh().get(id), threshold});
                getOms().tes.fireOrderEvent(-1, -1, Parameters.symbol.get(id), EnumOrderSide.SHORT, size, this.getLowestLow().get(id) , lowTriggerPrice, getStrategy(), 0, getExitType(), EnumOrderIntent.Amend, getMaxOrderDuration(), getDynamicOrderDuration(), getMaxSlippageEntry());
            } else {
                //cancel order. There is no need for advance short order.
                logger.log(Level.FINE, "cancel order. There is no need for advance short order. Symbol:{0},LastPrice: {1}, LowPrice: :{2} ,HighPrice: :{3} ,Threshold: {4}", new Object[]{Parameters.symbol.get(id).getSymbol(), Parameters.symbol.get(id).getLastPrice(), this.getLowestLow().get(id), this.getHighestHigh().get(id), threshold});
                getOms().tes.fireOrderEvent(-1, -1, Parameters.symbol.get(id), EnumOrderSide.SHORT, size, this.getLowestLow().get(id), lowTriggerPrice, getStrategy(), 0, getExitType(), EnumOrderIntent.Cancel, getMaxOrderDuration(), getDynamicOrderDuration(), getMaxSlippageEntry());
                this.getAdvanceEntryOrder().set(id, 0L);
            }
        }

        //Place entry orders
        if (tradeable && position.get(id) == 0 && this.getCumVolume().get(id).size() >= this.getChannelDuration()) {
            if (position.get(id) == 0 && getAdvanceEntryOrder().get(id) == 0 && getLongOnly() && exPriceBarLong.get(id) && BeanTurtle.getLastOrderDate().compareTo(new Date()) > 0 && this.getBreachUp().get(id) >= this.getBreachDown().get(id) && this.getBreachDown().get(id) >= 1) {
                if ((Parameters.symbol.get(id).getLastPrice() + threshold) > this.getHighestHigh().get(id)
                        && (Parameters.symbol.get(id).getLastPrice() - threshold) > this.getLowestLow().get(id)
                        && getLongOnly()) {
                    //place advance order to buy
                    this.getAdvanceEntryOrder().set(id, 1L);
                    logger.log(Level.FINE, "place advance order to buy. Symbol:{0},LastPrice: {1}, LowPrice: :{2} ,HighPrice: :{3} ,Threshold: {4}", new Object[]{Parameters.symbol.get(id).getSymbol(), Parameters.symbol.get(id).getLastPrice(), this.getLowestLow().get(id), this.getHighestHigh().get(id), threshold});
                    getOms().tes.fireOrderEvent(-1, -1, Parameters.symbol.get(id), EnumOrderSide.BUY, size, this.getHighestHigh().get(id) , highTriggerPrice, getStrategy(), 0, getExitType(), EnumOrderIntent.Init, getMaxOrderDuration(), getDynamicOrderDuration(), getMaxSlippageEntry());
                }
            } else if (position.get(id) == 0 && getAdvanceEntryOrder().get(id) == 0 && getShortOnly() && exPriceBarShort.get(id) && BeanTurtle.getLastOrderDate().compareTo(new Date()) > 0 && this.getBreachDown().get(id) >= this.getBreachUp().get(id) && this.getBreachUp().get(id) >= 1) {
                if ((Parameters.symbol.get(id).getLastPrice() - threshold) < this.getLowestLow().get(id)
                        && (Parameters.symbol.get(id).getLastPrice() + threshold) < this.getHighestHigh().get(id)
                        && getShortOnly()) {
                    //place advance order to short
                    this.getAdvanceEntryOrder().set(id, -1L);
                    logger.log(Level.FINE, "place advance order to short. Symbol:{0},LastPrice: {1}, LowPrice: :{2} ,HighPrice: :{3} ,Threshold: {4}", new Object[]{Parameters.symbol.get(id).getSymbol(), Parameters.symbol.get(id).getLastPrice(), this.getLowestLow().get(id), this.getHighestHigh().get(id), threshold});
                    getOms().tes.fireOrderEvent(-1, -1, Parameters.symbol.get(id), EnumOrderSide.SHORT, size, this.getLowestLow().get(id), lowTriggerPrice, getStrategy(), 0, getExitType(), EnumOrderIntent.Init, getMaxOrderDuration(), getDynamicOrderDuration(), getMaxSlippageEntry());
                }
            }
        }
    }

    public void placeAdvancedExitOrders(int id) {
        //Place advance orders
        double threshold = this.getHighestHigh().get(id) - this.getLowestLow().get(id) > 1 ? 0.5 : (this.getHighestHigh().get(id) - this.getLowestLow().get(id)) / 2;
        //boolean tradeable = Double.parseDouble(Parameters.symbol.get(id).getPriorDayVolume()) / (Parameters.symbol.get(id).getMinsize() * 375) >= 6.0 && this.getCumVolume().get(id).size() > this.getStartBars() && Parameters.symbol.get(id).getLastPrice() > 0;
        boolean tradeable=true;
        if (this.getExposure() != 0 && this.getCumVolume().get(id).size() > this.getStartBars() && Parameters.symbol.get(id).getLastPrice() > 0) {
            tradeable = true;
        }
        int size = this.getExposure() != 0 ? (int) (this.getExposure() / Parameters.symbol.get(id).getLastPrice()) : Parameters.symbol.get(id).getMinsize();
        double highTriggerPrice = this.getHighestHigh().get(id);//+Double.parseDouble(tickSize);
        double lowTriggerPrice = this.getLowestLow().get(id);//-Double.parseDouble(tickSize);

        //amend advance sell order
        if (position.get(id) == 1 && getAdvanceExitOrder().get(id) == -1) {
            if ((Parameters.symbol.get(id).getLastPrice() - threshold) < this.getLowestLow().get(id)
                    && (Parameters.symbol.get(id).getLastPrice() + threshold) < this.getHighestHigh().get(id)) {
                //amend existing advance sell order
                logger.log(Level.FINE, "Amend existing advance sell order. Symbol:{0},LastPrice: {1}, LowPrice: :{2} ,HighPrice: :{3} ,Threshold: {4}", new Object[]{Parameters.symbol.get(id).getSymbol(), Parameters.symbol.get(id).getLastPrice(), this.getLowestLow().get(id), this.getHighestHigh().get(id), threshold});
                getOms().tes.fireOrderEvent(-1, -1, Parameters.symbol.get(id), EnumOrderSide.SELL, size, this.getLowestLow().get(id), lowTriggerPrice, getStrategy(), 0, getExitType(), EnumOrderIntent.Amend, getMaxOrderDuration(), getDynamicOrderDuration(), getMaxSlippageExit());
            } else {
                //cancel order. There is no need for advance sell order.
                logger.log(Level.FINE, "cancel order. There is no need for advance sell order. Symbol:{0},LastPrice: {1}, LowPrice: :{2} ,HighPrice: :{3} ,Threshold: {4}", new Object[]{Parameters.symbol.get(id).getSymbol(), Parameters.symbol.get(id).getLastPrice(), this.getLowestLow().get(id), this.getHighestHigh().get(id), threshold});
                getOms().tes.fireOrderEvent(-1, -1, Parameters.symbol.get(id), EnumOrderSide.SELL, size, this.getLowestLow().get(id), lowTriggerPrice, getStrategy(), 0, getExitType(), EnumOrderIntent.Cancel, getMaxOrderDuration(), getDynamicOrderDuration(), getMaxSlippageExit());
                this.getAdvanceExitOrder().set(id, 0L);
            }
        }
        //amend advance cover order
        if (position.get(id) == -1 && getAdvanceExitOrder().get(id) == 1) {
            if ((Parameters.symbol.get(id).getLastPrice() + threshold) > this.getHighestHigh().get(id)
                    && (Parameters.symbol.get(id).getLastPrice() - threshold) > this.getLowestLow().get(id)) {
                //amend existing advance cover order
                logger.log(Level.FINE, "Amend existing advance cover order. Symbol:{0},LastPrice: {1}, LowPrice: :{2} ,HighPrice: :{3} ,Threshold: {4}", new Object[]{Parameters.symbol.get(id).getSymbol(), Parameters.symbol.get(id).getLastPrice(), this.getLowestLow().get(id), this.getHighestHigh().get(id), threshold});
                getOms().tes.fireOrderEvent(-1, -1, Parameters.symbol.get(id), EnumOrderSide.COVER, size, this.getHighestHigh().get(id), highTriggerPrice, getStrategy(), 0, getExitType(), EnumOrderIntent.Amend, getMaxOrderDuration(), getDynamicOrderDuration(), getMaxSlippageExit());
            } else {
                //cancel order. There is no need for advance cover order.
                logger.log(Level.FINE, "cancel order. There is no need for advance cover order. Symbol:{0},LastPrice: {1}, LowPrice: :{2} ,HighPrice: :{3} ,Threshold: {4}", new Object[]{Parameters.symbol.get(id).getSymbol(), Parameters.symbol.get(id).getLastPrice(), this.getLowestLow().get(id), this.getHighestHigh().get(id), threshold});
                getOms().tes.fireOrderEvent(-1, -1, Parameters.symbol.get(id), EnumOrderSide.COVER, size, this.getHighestHigh().get(id), highTriggerPrice, getStrategy(), 0, getExitType(), EnumOrderIntent.Cancel, getMaxOrderDuration(), getDynamicOrderDuration(), getMaxSlippageExit());
                this.getAdvanceExitOrder().set(id, 0L);
            }
        }

        //now place sell and cover advance orders
        if (position.get(id) == -1 && getAdvanceExitOrder().get(id) == 0) {
            if ((Parameters.symbol.get(id).getLastPrice() + threshold) > this.getHighestHigh().get(id)
                    && (Parameters.symbol.get(id).getLastPrice() - threshold) > this.getHighestHigh().get(id)) //place advance order to cover
            {
                this.getAdvanceExitOrder().set(id, 1L);

                logger.log(Level.FINE, "place advance order to cover. Symbol:{0},LastPrice: {1}, LowPrice: :{2} ,HighPrice: :{3} ,Threshold: {4}", new Object[]{Parameters.symbol.get(id).getSymbol(), Parameters.symbol.get(id).getLastPrice(), this.getLowestLow().get(id), this.getHighestHigh().get(id), threshold});
                getOms().tes.fireOrderEvent(-1, -1, Parameters.symbol.get(id), EnumOrderSide.COVER, size, this.getHighestHigh().get(id), highTriggerPrice, getStrategy(), 0, getExitType(), EnumOrderIntent.Init, getMaxOrderDuration(), getDynamicOrderDuration(), getMaxSlippageExit());
            }
        }


        if (position.get(id) == 1 && getAdvanceExitOrder().get(id) == 0) {
            if ((Parameters.symbol.get(id).getLastPrice() - threshold) < this.getLowestLow().get(id)) {
                //place advance order to sell
                logger.log(Level.FINE, "place advance order to sell. Symbol:{0},LastPrice: {1}, LowPrice: :{2} ,HighPrice: :{3} ,Threshold: {4}", new Object[]{Parameters.symbol.get(id).getSymbol(), Parameters.symbol.get(id).getLastPrice(), this.getLowestLow().get(id), this.getHighestHigh().get(id), threshold});
                getOms().tes.fireOrderEvent(-1, -1, Parameters.symbol.get(id), EnumOrderSide.SELL, size, this.getLowestLow().get(id), lowTriggerPrice, getStrategy(), 0, getExitType(), EnumOrderIntent.Init, getMaxOrderDuration(), getDynamicOrderDuration(), getMaxSlippageExit());
                this.getAdvanceExitOrder().set(id, -1L);
            }
        }
    }

    @Override
    public synchronized void tradeReceived(TradeEvent event) {

        try {
            int id = event.getSymbolID(); //here symbolID is with zero base.
            if (this.strategySymbols.contains(id) && event.getTickType() == com.ib.client.TickType.LAST) {
                boolean ruleHighestHigh = Parameters.symbol.get(id).getLastPrice() > this.getHighestHigh().get(id);
                boolean ruleLowestLow = Parameters.symbol.get(id).getLastPrice() < this.getLowestLow().get(id);
                //boolean ruleCumVolumeLong = this.getCumVolume().get(id).get(this.getCumVolume().get(id).size() - 1) >= 0.05 * Double.parseDouble(Parameters.symbol.get(id).getPriorDayVolume());
                //boolean ruleCumVolumeShort = this.getCumVolume().get(id).get(this.getCumVolume().get(id).size() - 1) <= Double.parseDouble(Parameters.symbol.get(id).getPriorDayVolume());
                //boolean ruleSlopeLong = this.getSlope().get(id) > Double.parseDouble(Parameters.symbol.get(id).getPriorDayVolume()) * this.getVolumeSlopeLongMultiplier() / 375;
                //boolean ruleSlopeShort = this.getSlope().get(id) < -Double.parseDouble(Parameters.symbol.get(id).getPriorDayVolume()) * this.getVolumeSlopeShortMultipler() / 375;
                boolean ruleVolumeLong = this.getVolume().get(id) > maVolumeLong * this.getVolumeMA().get(id);
                boolean ruleVolumeShort = this.getVolume().get(id) > maVolumeShort * this.getVolumeMA().get(id);
                boolean ruleCumVolumeLong = true;
                boolean ruleCumVolumeShort = true;
                boolean ruleSlopeLong=true;
                boolean ruleSlopeShort=true;
                //ruleVolumeLong = true;
                //ruleVolumeShort = true;
                if (this.strategySymbols.contains(id)) {
                    generateOrders(id, ruleHighestHigh, ruleLowestLow, ruleCumVolumeLong, ruleCumVolumeShort, ruleSlopeLong, ruleSlopeShort, ruleVolumeLong, ruleVolumeShort, false);
                }
            }

        } catch (Exception e) {
            logger.log(Level.SEVERE, null, e);
        }
    }

    private synchronized void generateOrders(int id, boolean ruleHighestHigh, boolean ruleLowestLow, boolean ruleCumVolumeLong, boolean ruleCumVolumeShort, boolean ruleSlopeLong, boolean ruleSlopeShort, boolean ruleVolumeLong, boolean ruleVolumeShort, boolean sourceBars) {
        try {
//            boolean tradeable = Double.parseDouble(Parameters.symbol.get(id).getPriorDayVolume()) / (Parameters.symbol.get(id).getMinsize() * 375) >= 6.0 && this.getCumVolume().get(id).size() > this.getStartBars() && Parameters.symbol.get(id).getLastPrice() > 0;
            boolean tradeable=true;
            if (this.getExposure() != 0 && this.getCumVolume().get(id).size() > this.getStartBars() && Parameters.symbol.get(id).getLastPrice() > 0) {
                tradeable = true;
            }
            if (ruleHighestHigh && sourceBars) {
                this.getBreachUpInBar().set(id, true);
            }
            if (ruleLowestLow && sourceBars) {
                this.getBreachDownInBar().set(id, true);
            }
            double breachup = ((double) this.getBreachUp().get(id) + 1) / ((double) this.getBreachUp().get(id) + (double) this.getBreachDown().get(id) + 1D);
            double breachdown = ((double) this.getBreachDown().get(id) + 1) / ((double) this.getBreachUp().get(id) + (double) this.getBreachDown().get(id) + 1D);

            if (ruleHighestHigh || ruleLowestLow) {
                logger.log(Level.FINE, "{0},CumVolume:{1}, HH:{2}, LL:{3}, LastPrice:{4}, Vol:{5}, CumVol:{6}, Slope:{7}, SlopeCutoff:{8},VolMA:{9}, LongVolCutoff:{10}, ShortVolCutOff:{11}, LastPriceTime:{12}, BreachUp:{13}, BreachDown:{14},{15},{16},{17},{18},{19},{20},{21},{22},{23},{24},{25},{26},{27},{28}", new Object[]{
                    Parameters.symbol.get(id).getSymbol(),
                    String.valueOf(this.getCumVolume().get(id).size()),
                    this.getHighestHigh().get(id).toString(),
                    this.getLowestLow().get(id).toString(),
                    String.valueOf(Parameters.symbol.get(id).getLastPrice()),
                    this.getVolume().get(id).toString(),
                    this.getCumVolume().get(id).get(this.getCumVolume().get(id).size() - 1).toString(),
                    this.getSlope().get(id).toString(),
                    0,
                    this.getVolumeMA().get(id).toString(),
                    0,
                    0,
                    DateUtil.getFormatedDate("yyyyMMdd HH:mm:ss z", Parameters.symbol.get(id).getLastPriceTime()),
                    this.getBreachUp().get(id),
                    this.getBreachDown().get(id),
                    tradeable,
                    this.position.get(id),
                    getLongOnly(),
                    ruleHighestHigh,
                    ruleCumVolumeLong,
                    ruleSlopeLong,
                    ruleVolumeLong,
                    breachup,
                    getShortOnly(),
                    ruleLowestLow,
                    ruleCumVolumeShort,
                    ruleSlopeShort,
                    ruleVolumeShort,
                    breachdown
                });
            }

            if (this.position.get(id) == 0 && this.getCumVolume().get(id).size() >= this.getChannelDuration()) { //basic conditions met for testing entry
                if (getLongOnly() && ruleHighestHigh && (exPriceBarLong.get(id) && sourceBars || (!sourceBars && ruleCumVolumeLong && ruleSlopeLong && ruleVolumeLong)) && getLastOrderDate().compareTo(new Date()) > 0) {//basic turtle condition for long entry
                    //Buy Condition
                    position.put(id, 1);
                    int size = this.getExposure() != 0 ? (int) (this.getExposure() / Parameters.symbol.get(id).getLastPrice()) : Parameters.symbol.get(id).getMinsize();
                    logger.log(Level.INFO, "Buy. Symbol:{0},LL:{1},LastPrice:{2},HH{3},Slope:{4},SlopeThreshold:{5},Volume:{6},VolumeMA:{7}, Breachup:{8},Breachdown:{9}, ADRHigh:{10}, ADRLow:{11}, ADRAvg:{12}, ADR:{13}, ADRRTIN:{14}",
                            new Object[]{Parameters.symbol.get(id).getSymbol(), this.getLowestLow().get(id).toString(), Parameters.symbol.get(id).getLastPrice(), this.getHighestHigh().get(id).toString(), this.getSlope().get(id).toString(), 0, this.getVolume().get(id).toString(), this.getVolumeMA().get(id).toString(), this.getBreachUp().get(id) + 1, this.getBreachDown().get(id), Launch.algo.getParamADR().adrDayHigh, Launch.algo.getParamADR().adrDayLow, Launch.algo.getParamADR().adrAvg, Launch.algo.getParamADR().adr, Launch.algo.getParamADR().adrTRIN
                    });
                    //check for filters
                    boolean liquidity = this.checkForHistoricalLiquidity == true ? tradeable : true;
                    boolean breaches = checkForDirectionalBreaches == true ? breachup > 0.5 && this.getBreachDown().get(id) >= 1 : true;
                    boolean donotskip = skipAfterWins == true ? lastTradeWasLosing.get(id) : true;
                    boolean adrtrend = checkADRTrend == true ? (Launch.algo.getParamADR().adr > Launch.algo.getParamADR().adrDayLow + 0.75 * (Launch.algo.getParamADR().adrDayHigh - Launch.algo.getParamADR().adrDayLow) || Launch.algo.getParamADR().adr > Launch.algo.getParamADR().adrAvg) && Launch.algo.getParamADR().adrTRIN < paramADRTRINBuy : true;
                    getTrades().put(new OrderLink(internalOrderID, "Order"), new Trade(id, EnumOrderSide.BUY, this.getHighestHigh().get(id), size, this.internalOrderID++, liquidity && breaches && donotskip && adrtrend, timeZone, "Order"));
                    this.internalOpenOrders.put(id, internalOrderID - 1);
                    if (liquidity && breaches && donotskip && adrtrend) {
                        if (this.getAdvanceEntryOrder().get(id) == 0) { //no advance order present
                            getOms().tes.fireOrderEvent(this.internalOrderID - 1, this.internalOrderID - 1, Parameters.symbol.get(id), EnumOrderSide.BUY, size, this.getHighestHigh().get(id) , 0, getStrategy(), getMaxOrderDuration(), getExitType(), EnumOrderIntent.Init, getMaxOrderDuration(), getDynamicOrderDuration(), getMaxSlippageEntry());
                        } else if (this.getAdvanceEntryOrder().get(id) == 1) {
                            getOms().tes.fireOrderEvent(this.internalOrderID - 1, this.internalOrderID - 1, Parameters.symbol.get(id), EnumOrderSide.BUY, size, this.getHighestHigh().get(id) , 0, getStrategy(), getMaxOrderDuration(), getExitType(), EnumOrderIntent.Amend, getMaxOrderDuration(), getDynamicOrderDuration(), getMaxSlippageEntry());
                        } else if (this.getAdvanceEntryOrder().get(id) == -1) { //advance order is short.
                            getOms().tes.fireOrderEvent(this.internalOrderID - 1, this.internalOrderID - 1, Parameters.symbol.get(id), EnumOrderSide.BUY, size, this.getHighestHigh().get(id) , 0, getStrategy(), getMaxOrderDuration(), getExitType(), EnumOrderIntent.Cancel, getMaxOrderDuration(), getDynamicOrderDuration(), getMaxSlippageEntry());
                            getOms().tes.fireOrderEvent(this.internalOrderID - 1, this.internalOrderID - 1, Parameters.symbol.get(id), EnumOrderSide.BUY, size, this.getHighestHigh().get(id) , 0, getStrategy(), getMaxOrderDuration(), getExitType(), EnumOrderIntent.Init, getMaxOrderDuration(), getDynamicOrderDuration(), getMaxSlippageEntry());
                        }
                        this.advanceEntryOrder.set(id, 0L);
                    } else {
                        logger.log(Level.INFO, "Long order not placed for Symbol {0}. Filter: Liquidity: {1}, Directional Breach: {2}, Do No Skip Trade: {3}, ADR Trend: {4}", new Object[]{Parameters.symbol.get(id).getSymbol(), liquidity, breaches, donotskip, adrtrend});
                    }
                } else if (getShortOnly() && ruleLowestLow && (exPriceBarShort.get(id) && sourceBars || (!sourceBars && ruleCumVolumeShort && ruleSlopeShort && ruleVolumeShort)) && getLastOrderDate().compareTo(new Date()) > 0) {
                    //Short condition
                    position.put(id, -1);
                    int size = this.getExposure() != 0 ? (int) (this.getExposure() / Parameters.symbol.get(id).getLastPrice()) : Parameters.symbol.get(id).getMinsize();
                    logger.log(Level.FINE, "Short. Symbol:{0},LL:{1},LastPrice:{2},HH{3},Slope:{4},SlopeThreshold:{5},Volume:{6},VolumeMA:{7},Breachup:{8},Breachdown:{9}, ADRHigh:{10}, ADRLow:{11}, ADRAvg:{12}, ADR:{13}, ADRRTIN:{14}",
                            new Object[]{Parameters.symbol.get(id).getSymbol(), this.getLowestLow().get(id).toString(), Parameters.symbol.get(id).getLastPrice(), this.getHighestHigh().get(id).toString(), this.getSlope().get(id).toString(), 0, this.getVolume().get(id).toString(), this.getVolumeMA().get(id).toString(), this.getBreachUp().get(id), this.getBreachUp().get(id) + 1, Launch.algo.getParamADR().adrDayHigh, Launch.algo.getParamADR().adrDayLow, Launch.algo.getParamADR().adrAvg, Launch.algo.getParamADR().adr, Launch.algo.getParamADR().adrTRIN
                    });
                    //check for filters
                    boolean liquidity = this.checkForHistoricalLiquidity == true ? tradeable : true;
                    boolean breaches = checkForDirectionalBreaches == true ? breachdown > 0.5 && this.getBreachUp().get(id) >= 1 : true;
                    boolean donotskip = skipAfterWins == true ? lastTradeWasLosing.get(id) : true;
                    boolean adrtrend = checkADRTrend == true ? (Launch.algo.getParamADR().adr < Launch.algo.getParamADR().adrDayHigh - 0.75 * (Launch.algo.getParamADR().adrDayHigh - Launch.algo.getParamADR().adrDayLow) || Launch.algo.getParamADR().adr < Launch.algo.getParamADR().adrAvg) && Launch.algo.getParamADR().adrTRIN > paramADRTRINShort : true;
                    getTrades().put(new OrderLink(internalOrderID, "Order"), new Trade(id, EnumOrderSide.SHORT, this.getLowestLow().get(id), size, this.internalOrderID++, liquidity && breaches && donotskip && adrtrend, timeZone, "Order"));
                    this.internalOpenOrders.put(id, this.internalOrderID - 1);
                    if (liquidity && breaches && donotskip && adrtrend) {
                        if (this.getAdvanceEntryOrder().get(id) == 0) {
                            getOms().tes.fireOrderEvent(this.internalOrderID - 1, this.internalOrderID - 1, Parameters.symbol.get(id), EnumOrderSide.SHORT, size, this.getLowestLow().get(id), 0, getStrategy(), getMaxOrderDuration(), getExitType(), EnumOrderIntent.Init, getMaxOrderDuration(), getDynamicOrderDuration(), getMaxSlippageEntry());
                        } else if (this.getAdvanceEntryOrder().get(id) == -1) {
                            getOms().tes.fireOrderEvent(this.internalOrderID - 1, this.internalOrderID - 1, Parameters.symbol.get(id), EnumOrderSide.SHORT, size, this.getLowestLow().get(id), 0, getStrategy(), getMaxOrderDuration(), getExitType(), EnumOrderIntent.Amend, getMaxOrderDuration(), getDynamicOrderDuration(), getMaxSlippageEntry());
                        } else if (this.getAdvanceEntryOrder().get(id) == 1) {
                            getOms().tes.fireOrderEvent(this.internalOrderID - 1, this.internalOrderID - 1, Parameters.symbol.get(id), EnumOrderSide.SHORT, size, this.getLowestLow().get(id) , 0, getStrategy(), getMaxOrderDuration(), getExitType(), EnumOrderIntent.Cancel, getMaxOrderDuration(), getDynamicOrderDuration(), getMaxSlippageEntry());
                            getOms().tes.fireOrderEvent(this.internalOrderID - 1, this.internalOrderID - 1, Parameters.symbol.get(id), EnumOrderSide.SHORT, size, this.getLowestLow().get(id) , 0, getStrategy(), getMaxOrderDuration(), getExitType(), EnumOrderIntent.Init, getMaxOrderDuration(), getDynamicOrderDuration(), getMaxSlippageEntry());
                        }
                        this.advanceEntryOrder.set(id, 0L);
                    } else {
                        logger.log(Level.INFO, "Short order not placed for Symbol {0}. Filter: Liquidity: {1}, Directional Breach: {2}, Do No Skip Trade: {3}, ADR Trend: {4}", new Object[]{Parameters.symbol.get(id).getSymbol(), liquidity, breaches, donotskip, adrtrend});
                    }

                }
            } else if (position.get(id) == -1) { //position exists. Check for exit conditions for a short
                if (ruleHighestHigh || System.currentTimeMillis() > endDate.getTime()) {
                    position.put(id, 0);;
                    int size = this.getExposure() != 0 ? (int) (this.getExposure() / Parameters.symbol.get(id).getLastPrice()) : Parameters.symbol.get(id).getMinsize();
                    logger.log(Level.FINE, "Cover.Symbol:{0},LL:{1},LastPrice:{2},HH{3},Slope:{4},SlopeThreshold:{5},Volume:{6},VolumeMA:{7}",
                            new Object[]{Parameters.symbol.get(id).getSymbol(), this.getLowestLow().get(id).toString(), Parameters.symbol.get(id).getLastPrice(), this.getHighestHigh().get(id).toString(), this.getSlope().get(id).toString(),0, this.getVolume().get(id).toString(), this.getVolumeMA().get(id).toString()
                    });
                    int entryInternalOrderID = this.internalOpenOrders.get(id);
                    Trade originalTrade = getTrades().get(new OrderLink(entryInternalOrderID, "Order"));
                    originalTrade.updateExit(id, EnumOrderSide.COVER, this.getHighestHigh().get(id), size, this.internalOrderID++, timeZone, "Order");
                    getTrades().put(new OrderLink(entryInternalOrderID, "Order"), originalTrade);
                    if (entryInternalOrderID != 0) {
                        if (getTrades().get(new OrderLink(entryInternalOrderID, "Order")).getEntryPrice() >= this.getHighestHigh().get(id)) {
                            this.lastTradeWasLosing.set(id, Boolean.FALSE);
                        } else {
                            this.lastTradeWasLosing.set(id, Boolean.TRUE);
                        }
                    }
                    if (ruleHighestHigh) {
                        if (this.getAdvanceExitOrder().get(id) == 0) {
                            getOms().tes.fireOrderEvent(this.internalOrderID - 1, entryInternalOrderID, Parameters.symbol.get(id), EnumOrderSide.COVER, size, this.getHighestHigh().get(id), 0, getStrategy(), getMaxOrderDuration(), "", EnumOrderIntent.Init, getMaxOrderDuration(), getDynamicOrderDuration(), getMaxSlippageExit());
                        } else if (this.getAdvanceExitOrder().get(id) == 1) {
                            getOms().tes.fireOrderEvent(this.internalOrderID - 1, entryInternalOrderID, Parameters.symbol.get(id), EnumOrderSide.COVER, size, this.getHighestHigh().get(id), 0, getStrategy(), getMaxOrderDuration(), "", EnumOrderIntent.Amend, getMaxOrderDuration(), getDynamicOrderDuration(), getMaxSlippageExit());
                        } else if (this.getAdvanceExitOrder().get(id) == -1) {
                            getOms().tes.fireOrderEvent(this.internalOrderID - 1, entryInternalOrderID, Parameters.symbol.get(id), EnumOrderSide.COVER, size, this.getHighestHigh().get(id), 0, getStrategy(), getMaxOrderDuration(), "", EnumOrderIntent.Cancel, getMaxOrderDuration(), getDynamicOrderDuration(), getMaxSlippageExit());
                            getOms().tes.fireOrderEvent(this.internalOrderID - 1, entryInternalOrderID, Parameters.symbol.get(id), EnumOrderSide.COVER, size, this.getHighestHigh().get(id), 0, getStrategy(), getMaxOrderDuration(), "", EnumOrderIntent.Init, getMaxOrderDuration(), getDynamicOrderDuration(), getMaxSlippageExit());

                        }
                        this.getAdvanceExitOrder().set(id, 0L);
                    } else if (System.currentTimeMillis() > endDate.getTime()) {
                        logger.log(Level.INFO, "Current Time is after program end date. Cover. Cancel open orders and place closeout.");
                        getOms().tes.fireOrderEvent(this.internalOrderID - 1, entryInternalOrderID, Parameters.symbol.get(id), EnumOrderSide.COVER, size, this.getHighestHigh().get(id), 0, getStrategy(), getMaxOrderDuration(), "", EnumOrderIntent.Cancel, getMaxOrderDuration(), getDynamicOrderDuration(), getMaxSlippageExit());
                        getOms().tes.fireOrderEvent(this.internalOrderID - 1, entryInternalOrderID, Parameters.symbol.get(id), EnumOrderSide.COVER, size, this.getClose().get(id), 0, getStrategy(), getMaxOrderDuration(), "", EnumOrderIntent.Init, getMaxOrderDuration(), getDynamicOrderDuration(), getMaxSlippageExit());
                        this.advanceExitOrder.set(id, 0L);

                    }
                }

            } else if (position.get(id) == 1) { //position exists. Check for exit condition for long
                if (ruleLowestLow || System.currentTimeMillis() > endDate.getTime()) {
                    position.put(id, 0);
                    int size = this.getExposure() != 0 ? (int) (this.getExposure() / Parameters.symbol.get(id).getLastPrice()) : Parameters.symbol.get(id).getMinsize();
                    logger.log(Level.FINE, "Sell.Symbol:{0},LL:{1},LastPrice:{2},HH{3},Slope:{4},SlopeThreshold:{5},Volume:{6},VolumeMA:{7}",
                            new Object[]{Parameters.symbol.get(id).getSymbol(), this.getLowestLow().get(id).toString(), Parameters.symbol.get(id).getLastPrice(), this.getHighestHigh().get(id).toString(), this.getSlope().get(id).toString(), 0, this.getVolume().get(id).toString(), this.getVolumeMA().get(id).toString()
                    });
                    int entryInternalOrderID = this.internalOpenOrders.get(id);
                    Trade originalTrade = getTrades().get(new OrderLink(entryInternalOrderID, "Order"));
                    originalTrade.updateExit(id, EnumOrderSide.SELL, this.getLowestLow().get(id), size, this.internalOrderID++, timeZone, "Order");
                    getTrades().put(new OrderLink(entryInternalOrderID, "Order"), originalTrade);
                    if (entryInternalOrderID != 0) {
                        if (getTrades().get(new OrderLink(entryInternalOrderID, "Order")).getEntryPrice() <= this.getLowestLow().get(id)) {
                            this.lastTradeWasLosing.set(id, Boolean.FALSE);
                        } else {
                            this.lastTradeWasLosing.set(id, Boolean.TRUE);
                        }
                    }
                    if (ruleLowestLow) {
                        if (this.getAdvanceExitOrder().get(id) == 0) {
                            getOms().tes.fireOrderEvent(this.internalOrderID - 1, entryInternalOrderID, Parameters.symbol.get(id), EnumOrderSide.SELL, size, this.getLowestLow().get(id), 0, getStrategy(), getMaxOrderDuration(), "", EnumOrderIntent.Init, getMaxOrderDuration(), getDynamicOrderDuration(), getMaxSlippageExit());
                        } else if (this.getAdvanceExitOrder().get(id) == -1) {
                            getOms().tes.fireOrderEvent(this.internalOrderID - 1, entryInternalOrderID, Parameters.symbol.get(id), EnumOrderSide.SELL, size, this.getLowestLow().get(id), 0, getStrategy(), getMaxOrderDuration(), "", EnumOrderIntent.Amend, getMaxOrderDuration(), getDynamicOrderDuration(), getMaxSlippageExit());
                        } else if (this.getAdvanceExitOrder().get(id) == 1) {
                            getOms().tes.fireOrderEvent(this.internalOrderID - 1, entryInternalOrderID, Parameters.symbol.get(id), EnumOrderSide.SELL, size, this.getLowestLow().get(id), 0, getStrategy(), getMaxOrderDuration(), "", EnumOrderIntent.Cancel, getMaxOrderDuration(), getDynamicOrderDuration(), getMaxSlippageExit());
                            getOms().tes.fireOrderEvent(this.internalOrderID - 1, entryInternalOrderID, Parameters.symbol.get(id), EnumOrderSide.SELL, size, this.getLowestLow().get(id), 0, getStrategy(), getMaxOrderDuration(), "", EnumOrderIntent.Init, getMaxOrderDuration(), getDynamicOrderDuration(), getMaxSlippageExit());
                        }
                        this.getAdvanceExitOrder().set(id, 0L);
                    } else if (System.currentTimeMillis() > endDate.getTime()) {
                        logger.log(Level.INFO, "Current Time is after program end date. Sell. Cancel open orders and place closeout.");
                        getOms().tes.fireOrderEvent(this.internalOrderID - 1, entryInternalOrderID, Parameters.symbol.get(id), EnumOrderSide.SELL, size, this.getLowestLow().get(id), 0, getStrategy(), getMaxOrderDuration(), "", EnumOrderIntent.Cancel, getMaxOrderDuration(), getDynamicOrderDuration(), getMaxSlippageExit());
                        getOms().tes.fireOrderEvent(this.internalOrderID - 1, entryInternalOrderID, Parameters.symbol.get(id), EnumOrderSide.SELL, size, this.getClose().get(id), 0, getStrategy(), getMaxOrderDuration(), "", EnumOrderIntent.Init, getMaxOrderDuration(), getDynamicOrderDuration(), getMaxSlippageExit());
                        this.advanceExitOrder.set(id, 0L);

                    }
                }
            }

            //force close of all open positions, after closeTime
            if (System.currentTimeMillis() + 3000 > endDate.getTime()) { //i wait for 3 seconds as there could be a gap in clock synchronization
                for (Map.Entry<Integer, Integer> j : position.entrySet()) {
                    if (j.getValue() > 0) {
                        //close long
                        int size = this.getExposure() != 0 ? (int) (this.getExposure() / Parameters.symbol.get(j.getKey()).getLastPrice()) : Parameters.symbol.get(j.getKey()).getMinsize();
                        position.put(j.getKey(), 0);
                        int entryInternalOrderID = this.internalOpenOrders.get(j.getKey());
                        Trade originalTrade = getTrades().get(new OrderLink(entryInternalOrderID, "Order"));
                        originalTrade.updateExit(j.getKey(), EnumOrderSide.SELL, this.getClose().get(j.getKey()), size, this.internalOrderID++, timeZone, "Order");
                        getTrades().put(new OrderLink(entryInternalOrderID, "Order"), originalTrade);
                        logger.log(Level.INFO, "Sell. Force Close All Positions.Symbol:{0}", new Object[]{Parameters.symbol.get(j.getKey()).getSymbol()});
                        getOms().tes.fireOrderEvent(this.internalOrderID - 1, entryInternalOrderID, Parameters.symbol.get(j.getKey()), EnumOrderSide.SELL, size, this.getClose().get(j.getKey()), 0, getStrategy(), getMaxOrderDuration(), "", EnumOrderIntent.Cancel, getMaxOrderDuration(), getDynamicOrderDuration(), getMaxSlippageExit());
                        getOms().tes.fireOrderEvent(this.internalOrderID - 1, entryInternalOrderID, Parameters.symbol.get(j.getKey()), EnumOrderSide.SELL, size, this.getClose().get(j.getKey()), 0, getStrategy(), getMaxOrderDuration(), "", EnumOrderIntent.Init, getMaxOrderDuration(), getDynamicOrderDuration(), getMaxSlippageExit());
                        this.advanceExitOrder.set(j.getKey(), 0L);
                    } else if (j.getValue() < 0) {
                        //close short
                        int size = this.getExposure() != 0 ? (int) (this.getExposure() / Parameters.symbol.get(j.getKey()).getLastPrice()) : Parameters.symbol.get(j.getKey()).getMinsize();
                        this.position.put(j.getKey(), 0);
                        int entryInternalOrderID = this.internalOpenOrders.get(j.getKey());
                        Trade originalTrade = getTrades().get(new OrderLink(entryInternalOrderID, "Order"));
                        originalTrade.updateExit(j.getKey(), EnumOrderSide.COVER, this.getClose().get(j.getKey()), size, this.internalOrderID++, timeZone, "Order");
                        getTrades().put(new OrderLink(entryInternalOrderID, "Order"), originalTrade);
                        logger.log(Level.INFO, "Cover. Force Close All Positions.Symbol:{0}", new Object[]{Parameters.symbol.get(j.getKey()).getSymbol()});
                        getOms().tes.fireOrderEvent(this.internalOrderID - 1, entryInternalOrderID, Parameters.symbol.get(j.getKey()), EnumOrderSide.COVER, size, this.getClose().get(j.getKey()), 0, getStrategy(), getMaxOrderDuration(), "", EnumOrderIntent.Cancel, getMaxOrderDuration(), getDynamicOrderDuration(), getMaxSlippageExit());
                        getOms().tes.fireOrderEvent(this.internalOrderID - 1, entryInternalOrderID, Parameters.symbol.get(j.getKey()), EnumOrderSide.COVER, size, this.getClose().get(j.getKey()), 0, getStrategy(), getMaxOrderDuration(), "", EnumOrderIntent.Init, getMaxOrderDuration(), getDynamicOrderDuration(), getMaxSlippageExit());
                        this.advanceExitOrder.set(j.getKey(), 0L);
                    }
                    //symb = symb + 1;
                }
            }


        } catch (Exception e) {
            logger.log(Level.SEVERE, null, e);
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
    public double getVolumeSlopeLongMultiplier() {
        return volumeSlopeLongMultiplier;
    }

    /**
     * @param volumeSlopeLongMultiplier the volumeSlopeLongMultiplier to set
     */
    public void setVolumeSlopeLongMultiplier(double volumeSlopeLongMultiplier) {
        this.volumeSlopeLongMultiplier = volumeSlopeLongMultiplier;
    }

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
     * @return the exposure
     */
    public double getExposure() {
        return exposure;
    }

    /**
     * @param exposure the exposure to set
     */
    public void setExposure(double exposure) {
        this.exposure = exposure;
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
     * @return the volumeSlopeShortMultipler
     */
    public double getVolumeSlopeShortMultipler() {
        return volumeSlopeShortMultipler;
    }

    /**
     * @param volumeSlopeShortMultipler the volumeSlopeShortMultipler to set
     */
    public void setVolumeSlopeShortMultipler(double volumeSlopeShortMultipler) {
        this.volumeSlopeShortMultipler = volumeSlopeShortMultipler;
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
