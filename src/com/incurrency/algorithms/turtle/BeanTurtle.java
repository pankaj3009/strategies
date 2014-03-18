/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.algorithms.turtle;

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
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.SortedMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.regression.SimpleRegression;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Pattern;
import javax.swing.JFrame;

/**
 *
 * @author pankaj
 */
public class BeanTurtle implements Serializable, HistoricalBarListener, TradeListener {

    public MainAlgorithm m;
    private ArrayList<ArrayList<Long>> cumVolume = new ArrayList<ArrayList<Long>>();
    private ArrayList<Double> highestHigh = new <Double> ArrayList();  //algo parameter 
    private ArrayList<Double> lowestLow = new <Double> ArrayList(); //algo parameter 
    private ArrayList<Double> close = new <Double> ArrayList();
    private ArrayList<Long> barNumber = new <Long> ArrayList();
    private ArrayList<Double> slope = new <Double>ArrayList();
    private ArrayList<Long> Volume = new ArrayList();
    private ArrayList<Double> VolumeMA = new ArrayList();
    private ArrayList<Long> longVolume = new ArrayList();
    private ArrayList<Long> shortVolume = new ArrayList();
    private ArrayList<Long> notionalPosition = new ArrayList();
    private ArrayList<Boolean> lastTradeWasLosing=new ArrayList();
    private ArrayList<Long> advanceEntryOrder = new ArrayList();
    private ArrayList<Long> advanceExitOrder = new ArrayList();
    private HashMap<Integer,Trade> trades = new HashMap();
    private static Date startDate;
    private static Date lastOrderDate;
    private static Date endDate;
    private static Date closeDate;
    private int channelDuration;
    private int regressionLookBack;
    private double volumeSlopeLongMultiplier;
    private double volumeSlopeShortMultipler;
    private int maxOrderDuration;
    private int dynamicOrderDuration;
    private double maxSlippageEntry=0;
    private double maxSlippageExit=0;
    private static final Logger logger = Logger.getLogger(BeanTurtle.class.getName());
    private String tickSize;
    private String exit="TBD";
    private ArrayList<Boolean> breachUpInBar = new ArrayList();
    private ArrayList<Boolean> breachDownInBar = new ArrayList();
    private ArrayList<Integer> breachUp = new ArrayList();
    private ArrayList<Integer> breachDown = new ArrayList();
    private double exposure = 0;
    private int startBars;
    private int display;
    private Boolean longOnly = true;
    private Boolean shortOnly = true;
    private Boolean aggression = true;
    private ArrayList<Boolean> exPriceBarLong = new ArrayList();
    private ArrayList<Boolean> exPriceBarShort = new ArrayList();
    private String symbols;
    private List<Integer> tradeableSymbols = new ArrayList();
    Timer closeProcessing;
    Timer openProcessing;
    private double maVolumeLong;
    private double maVolumeShort;
    private boolean paramAdvanceEntryOrders;
    private boolean paramAdvanceExitOrders;
    private ProfitLossManager plmanager;
    private double profitTarget;
    public TurtleOrderManagement orderTurtle;
    private boolean skipAfterWins=false;
    private int internalorderID=1;
    private HashMap<Integer,Integer> internalOpenOrders=new HashMap();

    public BeanTurtle(MainAlgorithm m) {
        this.m = m;
        loadParameters();
        for(BeanSymbol s: Parameters.symbol){
        if(s.getStrategy().contains("idt")){
            tradeableSymbols.add(s.getSerialno()-1);
        }
    }
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
            longVolume.add(Parameters.symbol.get(i).getLongvolume());
            shortVolume.add(Parameters.symbol.get(i).getShortvolume());
            notionalPosition.add(0L);
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
        for (BeanConnection c : Parameters.connection){
            c.getWrapper().addTradeListener(this);
            c.initializeConnection("idt");
			}
        plmanager=new ProfitLossManager("idt");
        orderTurtle = new TurtleOrderManagement(this.aggression,Double.parseDouble(this.tickSize),endDate,"idt");		               
        populateLastTradePrice();
        //createAndShowGUI(m);
        getHistoricalData();
        Launch.setMessage("Waiting for market open");
        closeProcessing = new Timer();
        closeProcessing.schedule(new BeanTurtleClosing(this,orderTurtle), closeDate);
        openProcessing = new Timer();
        if(new Date().compareTo(startDate)<0){ // if time is before startdate, schedule realtime bars
        openProcessing.schedule(realTimeBars, startDate);
        }else{
            requestRealTimeBars();
        }
    }
    
    
TimerTask realTimeBars = new TimerTask(){
    public void run(){
        requestRealTimeBars();
    }
};
    private static void createAndShowGUI(MainAlgorithm m) {
        //Create and set up the window.
        JFrame frame1 = new JFrame("Positions");
        frame1.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        //Create and set up the content pane.
        GUIDashBoard newContentPane = new GUIDashBoard(m);
        newContentPane.setOpaque(true); //content panes must be opaque
        frame1.setContentPane(newContentPane);
        //Display the window.
        frame1.pack();
        frame1.setLocation(0, 0);
        frame1.setVisible(true);

        JFrame frame2 = new JFrame("Missed Orders");
        frame2.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        //Create and set up the content pane.
        GUIMissedOrders newContentPane2 = new GUIMissedOrders(m);
        newContentPane2.setOpaque(true); //content panes must be opaque
        frame2.setContentPane(newContentPane2);
        //Display the window.
        frame2.pack();
        frame2.setLocation(820, 142);
        frame2.setVisible(true);

        JFrame frame3 = new JFrame("Orders In Progress");
        frame3.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        //Create and set up the content pane.
        GUIInProgressOrders newContentPane3 = new GUIInProgressOrders(m);
        newContentPane3.setOpaque(true); //content panes must be opaque
        frame3.setContentPane(newContentPane3);
        //Display the window.
        frame3.pack();
        frame3.setLocation(819, 0);
        frame3.setVisible(true);

        JFrame frame4 = new JFrame("Total PNL");
        frame4.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        GUIPNLDashBoard newContentPane4 = new GUIPNLDashBoard(m);
        newContentPane4.setOpaque(true); //content panes must be opaque
        frame4.setContentPane(newContentPane4);
        //Display the window.
        frame4.pack();
        frame4.setLocation(820, 415);
        frame4.setVisible(true);
    }

    private void loadParameters() {
        Properties p = new Properties(System.getProperties());
        FileInputStream propFile;
        try {
            propFile = new FileInputStream(MainAlgorithm.input.get("idt"));
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
        String startDateStr=currDateStr + " " + System.getProperty("StartTime");
        String lastOrderDateStr = currDateStr + " " + System.getProperty("LastOrderTime");
        String endDateStr = currDateStr + " " + System.getProperty("EndTime");
        String closeDateStr = currDateStr + " " + System.getProperty("CloseTime");
        startDate=DateUtil.parseDate("yyyyMMdd HH:mm:ss", startDateStr);
        lastOrderDate= DateUtil.parseDate("yyyyMMdd HH:mm:ss", lastOrderDateStr);
        endDate=DateUtil.parseDate("yyyyMMdd HH:mm:ss", endDateStr);
        closeDate=DateUtil.parseDate("yyyyMMdd HH:mm:ss", closeDateStr);

        if (lastOrderDate.compareTo(startDate) < 0 && new Date().compareTo(lastOrderDate) > 0) {
            //increase enddate by one calendar day
            lastOrderDate=DateUtil.addDays(lastOrderDate,1); //system date is > start date time. Therefore we have not crossed the 12:00 am barrier
            endDate = DateUtil.addDays(endDate, 1); 
            closeDate=DateUtil.addDays(closeDate,1);
            
        } else if (lastOrderDate.compareTo(startDate) < 0 && new Date().compareTo(lastOrderDate) < 0) {
            startDate=DateUtil.addDays(startDate, -1);
        } else if(new Date().compareTo(startDate)>0 && new Date().compareTo(lastOrderDate)>0){ //program started after lastorderDate
            startDate=DateUtil.addDays(startDate, 1);
            endDate=DateUtil.addDays(lastOrderDate, 1);
            endDate=DateUtil.addDays(endDate, 1);
            endDate=DateUtil.addDays(closeDate, 1);
        }
        double tempprofitTarget= "".equals(System.getProperty("ProfitTarget"))? Double.MAX_VALUE:Double.parseDouble(System.getProperty("ProfitTarget"));
        setProfitTarget(tempprofitTarget);
        //Launch.setProfitTarget(getProfitTarget());
        tickSize = System.getProperty("TickSize");
        maxOrderDuration = Integer.parseInt(System.getProperty("MaxOrderDuration"));
        dynamicOrderDuration = Integer.parseInt(System.getProperty("DynamicOrderDuration"));
        maVolumeLong=Double.parseDouble(System.getProperty("MAVolumeLong"));
        maVolumeShort=Double.parseDouble(System.getProperty("MAVolumeShort"));
        String strAdvanceOrders=System.getProperty("AdvanceEntryOrders");
        paramAdvanceEntryOrders=Boolean.valueOf(strAdvanceOrders);
        paramAdvanceExitOrders=Boolean.valueOf(System.getProperty("AdvanceExitOrders"));
        //maxSlippage = Double.parseDouble(System.getProperty("MaxSlippage"));
        channelDuration = Integer.parseInt(System.getProperty("ChannelDuration"));
        volumeSlopeLongMultiplier = Double.parseDouble(System.getProperty("VolSlopeMultLong"));
        setVolumeSlopeShortMultipler(Double.parseDouble(System.getProperty("VolSlopeMultShort")));
        regressionLookBack = Integer.parseInt(System.getProperty("RegressionLookBack"));
        exposure = Double.parseDouble(System.getProperty("Exposure"));
        startBars = Integer.parseInt(System.getProperty("StartBars"));
        display = Integer.parseInt(System.getProperty("Display"));
        maxSlippageEntry=Double.parseDouble(System.getProperty("MaxSlippageEntry"))/100; // divide by 100 as input was a percentage
        maxSlippageExit=Double.parseDouble(System.getProperty("MaxSlippageExit"))/100; // divide by 100 as input was a percentage
        //exit = System.getProperty("Exit");
        this.skipAfterWins=Boolean.getBoolean(System.getProperty("SkipAfterWins"));
        logger.log(Level.INFO, "-----Turtle Parameters----");
        logger.log(Level.INFO, "start Time: {0}", startDate);
        logger.log(Level.INFO, "Last Order Time: {0}", lastOrderDate);
        logger.log(Level.INFO, "end Time: {0}", endDate);
        logger.log(Level.INFO, "Close Time: {0}", closeDate);
        logger.log(Level.INFO, "Channel Duration: {0}", channelDuration);
        logger.log(Level.INFO, "Start Bars: {0}", startBars);
        logger.log(Level.INFO, "Display: {0}", display);
        logger.log(Level.INFO, "Regression Lookback: {0}", regressionLookBack);
        logger.log(Level.INFO, "Volume Slope Multiplier Long: {0}", volumeSlopeLongMultiplier);
        logger.log(Level.INFO, "Volume Slope Multiplier Short: {0}", volumeSlopeShortMultipler);
        logger.log(Level.INFO, "MA Volume Long: {0}", maVolumeLong);
        logger.log(Level.INFO, "MA Volume Short: {0}", maVolumeShort);
        logger.log(Level.INFO, "TickSize: {0}", tickSize);
        logger.log(Level.INFO, "Exposure: {0}", exposure);
        logger.log(Level.INFO, "Max Order Duration: {0}", maxOrderDuration);
        logger.log(Level.INFO, "Dynamic Order Duration: {0}", dynamicOrderDuration);
        logger.log(Level.INFO, "Advance Entry Orders: {0}", paramAdvanceEntryOrders);
        logger.log(Level.INFO, "Advance Exit Orders: {0}", paramAdvanceExitOrders);
        logger.log(Level.INFO, "Profit Target: {0}", tempprofitTarget);
        logger.log(Level.INFO, "Max Slippage Entry: {0}", maxSlippageEntry);
        logger.log(Level.INFO, "Max Slippage Exit: {0}", maxSlippageExit);
        logger.log(Level.INFO, "Skip After Wins: {0}", skipAfterWins);
        
        

        
        
    }
    
    private void getHistoricalData(){
        try {
            //get historical data - this can be done before start time, assuming the program is started next day
             String type=Parameters.symbol.get(tradeableSymbols.get(0)).getType();
             Thread t = new Thread(new HistoricalBars("idt",type));
             t.setName("Historical Bars");
              if(!Launch.headless){
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
            Connection connect = null;
            PreparedStatement preparedStatement = null;
            ResultSet rs = null;
            connect = DriverManager.getConnection("jdbc:mysql://72.55.179.5:3306/histdata", "root", "spark123");
            //statement = connect.createStatement();
            for (int j = 0; j < Parameters.symbol.size(); j++) {
                if(Pattern.compile(Pattern.quote("idt"), Pattern.CASE_INSENSITIVE).matcher(Parameters.symbol.get(j).getStrategy()).find()){
                String name = Parameters.symbol.get(j).getSymbol() + "_FUT";
                preparedStatement = connect.prepareStatement("select * from dharasymb where name=? order by date desc LIMIT 1");
                preparedStatement.setString(1, name);
                rs = preparedStatement.executeQuery();
                if (rs != null) {
                    while (rs.next()) {
                        double tempPrice = rs.getDouble("tickclose");
                        Parameters.symbol.get(j).setYesterdayLastPrice(tempPrice);
                        logger.log(Level.INFO, "YesterDay Close:{0}", new Object[]{tempPrice});
                    }
                } else {
                    Parameters.symbol.get(j).setYesterdayLastPrice(Parameters.symbol.get(j).getClosePrice());
                    logger.log(Level.INFO, "Another YesterDay Close:{0}", new Object[]{rs.getDouble("tickclose")});

                }

            }
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE,null,e);
        }
    }
    
    private synchronized void requestRealTimeBars() {
/*
        if (!TurtleMainUI.headless) {
            TurtleMainUI.setStart(false);
            TurtleMainUI.setPauseTrading(true);
            TurtleMainUI.setcmdLong(true);
            TurtleMainUI.setcmdShort(true);
            TurtleMainUI.setcmdBoth(true);
            TurtleMainUI.setcmdExitShorts(true);
            TurtleMainUI.setcmdExitLongs(true);
            TurtleMainUI.setcmdSquareAll(true);
            TurtleMainUI.setcmdAggressionDisable(true);
            TurtleMainUI.setcmdAggressionEnable(true);
        }*/
        if (!Launch.headless) {
            //TurtleMainUI.setStart(false);
            Launch.setMessage("Starting request of RealTime Bars");
        }
        new RealTimeBars();
        logger.log(Level.FINE, ",Symbol" + "," + "BarNo" + "," + "HighestHigh" + "," + "LowestLow" + "," + "LastPrice" + "," + "Volume" + "," + "CumulativeVol" + "," + "VolumeSlope" + "," + "MinSlopeReqd" + "," + "MA" + "," + "LongVolume" + "," + "ShortVolume" + "," + "DateTime" + ","
                + "ruleHighestHigh" + "," + "ruleCumVolumeLong" + "," + "ruleSlopeLong" + "," + "ruleVolumeLong" + "," + "ruleLowestLow" + ","
                + "ruleCumVolumeShort" + "," + "ruleSlopeShort" + "," + "ruleVolumeShort");
    }

    @Override
    public void barsReceived(HistoricalBarEvent event) {
        int outsideid = event.getSymbol().getSerialno() - 1;
        if (this.tradeableSymbols.contains(outsideid)) {
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
                    if (this.getNotionalPosition().get(id) == 1L && event.ohlc().getLow() < lowestLow.get(id)) {
                        logger.log(Level.FINE, "Sell Order.Symbol:{0}", new Object[]{Parameters.symbol.get(id).getSymbol()});
                        generateOrders(id, false, true, false, false, false, false, false, false, true);
                    } else if (this.getNotionalPosition().get(id) == -1L && event.ohlc().getHigh() > highestHigh.get(id)) {
                        logger.log(Level.FINE, "Cover Order.Symbol:{0}", new Object[]{Parameters.symbol.get(id).getSymbol()});
                        generateOrders(id, true, false, false, false, false, false, false, false, true);
                    }
                }
                //For one minute bars
                else if (event.ohlc().getPeriodicity() == EnumBarSize.OneMin && startDate.compareTo(new Date()) < 0) {
                    int id = event.getSymbol().getSerialno() - 1;
                   
                    this.close.set(id, event.ohlc().getClose());
                    int barno = event.barNumber();
                    //logger.log(Level.FINE, "Bar No:{0}, Date={1}, Symbol:{2},FirstBarTime:{3}, LastBarTime:{4}, LastKey-FirstKey:{5}",
                            //new Object[]{barno, DateUtil.getFormatedDate("yyyyMMdd HH:mm:ss", event.ohlc().getOpenTime()), Parameters.symbol.get(id).getSymbol(), DateUtil.getFormatedDate("yyyyMMdd HH:mm:ss", event.list().firstKey()), DateUtil.getFormatedDate("yyyyMMdd HH:mm:ss", event.list().lastKey()), (event.list().lastKey() - event.list().firstKey()) / (1000 * 60)});
                    String startTime = System.getProperty("StartTime");
                    SimpleDateFormat sdfDate = new SimpleDateFormat("HH:mm:ss");//dd/MM/yyyy
                    String firstBarTime = sdfDate.format(event.list().firstEntry().getKey());
                    if (firstBarTime.contains(startTime) && event.barNumber()==(event.list().lastEntry().getKey()-event.list().firstEntry().getKey())/60000+1 ) {//all bars till the latest bar are available
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
                            Map<Long, BeanOHLC> temp = new HashMap();
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
                        List<Long> tempCumVolume = new ArrayList();
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
                            Map<Long, BeanOHLC> temp = new HashMap();
                            temp = (SortedMap<Long, BeanOHLC>) event.list().subMap(event.ohlc().getOpenTime() - (this.getChannelDuration() - 1) * 60 * 1000 + 1, event.ohlc().getOpenTime() + 1);
                            DescriptiveStatistics stats = new DescriptiveStatistics();
                            for (Map.Entry<Long, BeanOHLC> entry : temp.entrySet()) {
                                stats.addValue(entry.getValue().getVolume());
                            }
                            this.getVolumeMA().set(id, stats.getMean());

                        }
                        boolean ruleCumVolumeLong = this.getCumVolume().get(id).get(this.getCumVolume().get(id).size() - 1) >= 0.05 * Double.parseDouble(Parameters.symbol.get(id).getAdditionalInput());
                        boolean ruleCumVolumeShort = this.getCumVolume().get(id).get(this.getCumVolume().get(id).size() - 1) <= Double.parseDouble(Parameters.symbol.get(id).getAdditionalInput());
                        boolean ruleSlopeLong = this.getSlope().get(id) > Double.parseDouble(Parameters.symbol.get(id).getAdditionalInput()) * this.getVolumeSlopeLongMultiplier() / 375;
                        boolean ruleSlopeShort = this.getSlope().get(id) < -Double.parseDouble(Parameters.symbol.get(id).getAdditionalInput()) * this.getVolumeSlopeLongMultiplier() / 375;
                        boolean ruleVolumeLong = this.getVolume().get(id) > maVolumeLong * this.getVolumeMA().get(id);
                        boolean ruleVolumeShort = this.getVolume().get(id) > maVolumeShort * this.getVolumeMA().get(id);
                        ruleCumVolumeLong = true;
                        ruleCumVolumeShort = true;
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
                        if(paramAdvanceExitOrders){
                            placeAdvancedExitOrders(id);
                        }

                        logger.log(Level.FINEST, "{0},{1}, HH:{2}, LL:{3}, CumVol:{4}, LongVolCutoff:{5}, ShortVolCutoff:{6}, Slope:{7}, SlopeCutoff:{8}, BarVol:{9}, VolMA:{10}, BreachUp:{11}, BreachDown:{12}", new Object[]{
                            Parameters.symbol.get(id).getSymbol(),
                            sdfDate.format(event.list().lastEntry().getKey()),
                            this.getHighestHigh().get(id).toString(),
                            this.getLowestLow().get(id).toString(),
                            this.getCumVolume().get(id).get(event.barNumber() - 1).toString(),
                            String.valueOf(0.05 * Double.parseDouble(Parameters.symbol.get(id).getAdditionalInput())).replace(",", ""),
                            String.valueOf(Double.parseDouble(Parameters.symbol.get(id).getAdditionalInput())).replace(",", ""),
                            this.getSlope().get(id).toString(),
                            String.valueOf(Double.parseDouble(Parameters.symbol.get(id).getAdditionalInput()) * this.getVolumeSlopeLongMultiplier() / 375).replace(",", ""),
                            this.getVolume().get(id).toString(),
                            this.getVolumeMA().get(id).toString(),
                            this.getBreachUp().get(id),
                            this.getBreachDown().get(id)
                        });
                    }
                }
                     else if (event.ohlc().getPeriodicity() == EnumBarSize.Daily) {
                        //update symbol volumes
                        int id = event.getSymbol().getSerialno() - 1;
                        BeanSymbol s = Parameters.symbol.get(id);
                        if (Long.toString(event.list().lastKey()).equals(DateUtil.getFormatedDate("yyyyMMdd", System.currentTimeMillis()))) {
                            return;
                        } else {
                            s.setAdditionalInput(String.valueOf(event.list().lastEntry().getValue().getVolume()));
                        }
                    }   
            } catch (Exception e) {
                logger.log(Level.SEVERE, null, e);
            }
        }
    }

    public void placeAdvancedEntryOrders(int id) {
        //Place advance orders
        double threshold = this.getHighestHigh().get(id) - this.getLowestLow().get(id) > 1 ? 0.5 : (this.getHighestHigh().get(id) - this.getLowestLow().get(id)) / 2;
        boolean tradeable = Double.parseDouble(Parameters.symbol.get(id).getAdditionalInput()) / (Parameters.symbol.get(id).getMinsize() * 375) >= 6.0 && this.getCumVolume().get(id).size() > this.getStartBars() && Parameters.symbol.get(id).getLastPrice() > 0;
        if (this.getExposure() != 0 && this.getCumVolume().get(id).size() > this.getStartBars() && Parameters.symbol.get(id).getLastPrice() > 0) {
            tradeable = true;
        }
        int size = this.getExposure() != 0 ? (int) (this.getExposure() / Parameters.symbol.get(id).getLastPrice()) : Parameters.symbol.get(id).getMinsize();
        double highTriggerPrice=this.getHighestHigh().get(id)+Double.parseDouble(tickSize);
        double lowTriggerPrice=this.getLowestLow().get(id)-Double.parseDouble(tickSize);
        //Amend Entry Advance orders
        if (notionalPosition.get(id) == 0 && getAdvanceEntryOrder().get(id) == 1) { //advance order has been placed
            if ((Parameters.symbol.get(id).getLastPrice() + threshold) > this.getHighestHigh().get(id)
                    && (Parameters.symbol.get(id).getLastPrice() - threshold) > this.getLowestLow().get(id)
                    && this.getBreachUp().get(id) >= this.getBreachDown().get(id)
                    && exPriceBarLong.get(id) && this.getLastOrderDate().compareTo(new Date()) > 0 && this.getBreachUp().get(id) >= this.getBreachDown().get(id) && this.getBreachDown().get(id) >= 1) {
                //amend existing advance long order
                logger.log(Level.INFO, "Amend existing advance long order. Symbol:{0},LastPrice: {1}, LowPrice: :{2} ,HighPrice: :{3} ,Threshold: {4}", new Object[]{Parameters.symbol.get(id).getSymbol(), Parameters.symbol.get(id).getLastPrice(), this.getLowestLow().get(id), this.getHighestHigh().get(id), threshold});
                orderTurtle.tes.fireOrderEvent(-1,-1,Parameters.symbol.get(id), EnumOrderSide.BUY, size, this.getHighestHigh().get(id) + Parameters.symbol.get(id).getAggression(), this.getHighestHigh().get(id), "idt", 0, exit, EnumOrderIntent.Amend, maxOrderDuration, dynamicOrderDuration, maxSlippageEntry);
            } else {
                //cancel order. There is no need for advance buy order.
                logger.log(Level.INFO, "cancel order. There is no need for advance buy order. Symbol:{0},LastPrice: {1}, LowPrice: :{2} ,HighPrice: :{3} ,Threshold: {4}", new Object[]{Parameters.symbol.get(id).getSymbol(), Parameters.symbol.get(id).getLastPrice(), this.getLowestLow().get(id), this.getHighestHigh().get(id), threshold});
                orderTurtle.tes.fireOrderEvent(-1,-1,Parameters.symbol.get(id), EnumOrderSide.BUY, size, this.getHighestHigh().get(id) + Parameters.symbol.get(id).getAggression(), highTriggerPrice, "idt", 0, exit, EnumOrderIntent.Cancel, maxOrderDuration, dynamicOrderDuration, maxSlippageEntry);
                this.getAdvanceEntryOrder().set(id, 0L);
            }
        }


        if (notionalPosition.get(id) == 0 && getAdvanceEntryOrder().get(id) == -1) {
            if ((Parameters.symbol.get(id).getLastPrice() - threshold) < this.getLowestLow().get(id)
                    && (Parameters.symbol.get(id).getLastPrice() + threshold) < this.getHighestHigh().get(id)
                    && this.getBreachDown().get(id) >= this.getBreachUp().get(id)
                    && exPriceBarShort.get(id) && this.getLastOrderDate().compareTo(new Date()) > 0 && this.getBreachDown().get(id) >= this.getBreachUp().get(id) && this.getBreachUp().get(id) >= 1) {
                //amend existing advance short order
                logger.log(Level.INFO, "Amend existing advance short order. Symbol:{0},LastPrice: {1}, LowPrice: :{2} ,HighPrice: :{3} ,Threshold: {4}", new Object[]{Parameters.symbol.get(id).getSymbol(), Parameters.symbol.get(id).getLastPrice(), this.getLowestLow().get(id), this.getHighestHigh().get(id), threshold});
                orderTurtle.tes.fireOrderEvent(-1,-1,Parameters.symbol.get(id), EnumOrderSide.SHORT, size, this.getLowestLow().get(id) - Parameters.symbol.get(id).getAggression(), lowTriggerPrice, "idt", 0, exit, EnumOrderIntent.Amend, maxOrderDuration, dynamicOrderDuration, maxSlippageEntry);
            } else {
                //cancel order. There is no need for advance short order.
                logger.log(Level.INFO, "cancel order. There is no need for advance short order. Symbol:{0},LastPrice: {1}, LowPrice: :{2} ,HighPrice: :{3} ,Threshold: {4}", new Object[]{Parameters.symbol.get(id).getSymbol(), Parameters.symbol.get(id).getLastPrice(), this.getLowestLow().get(id), this.getHighestHigh().get(id), threshold});
                orderTurtle.tes.fireOrderEvent(-1,-1,Parameters.symbol.get(id), EnumOrderSide.SHORT, size, this.getLowestLow().get(id) - Parameters.symbol.get(id).getAggression(), lowTriggerPrice, "idt", 0, exit, EnumOrderIntent.Cancel, maxOrderDuration, dynamicOrderDuration, maxSlippageEntry);
                this.getAdvanceEntryOrder().set(id, 0L);
            }
        }
       
        //Place entry orders
        if (tradeable && this.getNotionalPosition().get(id) == 0 && this.getCumVolume().get(id).size() >= this.getChannelDuration()) {
            if (notionalPosition.get(id) == 0 && getAdvanceEntryOrder().get(id) == 0 && longOnly && exPriceBarLong.get(id) && this.getLastOrderDate().compareTo(new Date()) > 0 && this.getBreachUp().get(id) >= this.getBreachDown().get(id) && this.getBreachDown().get(id) >= 1) {
                if ((Parameters.symbol.get(id).getLastPrice() + threshold) > this.getHighestHigh().get(id)
                        && (Parameters.symbol.get(id).getLastPrice() - threshold) > this.getLowestLow().get(id)
                        && this.longOnly) {
                    //place advance order to buy
                    this.getAdvanceEntryOrder().set(id, 1L);
                    logger.log(Level.INFO, "place advance order to buy. Symbol:{0},LastPrice: {1}, LowPrice: :{2} ,HighPrice: :{3} ,Threshold: {4}", new Object[]{Parameters.symbol.get(id).getSymbol(), Parameters.symbol.get(id).getLastPrice(), this.getLowestLow().get(id), this.getHighestHigh().get(id), threshold});
                    orderTurtle.tes.fireOrderEvent(-1,-1,Parameters.symbol.get(id), EnumOrderSide.BUY, size, this.getHighestHigh().get(id) + Parameters.symbol.get(id).getAggression(), highTriggerPrice, "idt", 0, exit, EnumOrderIntent.Init, maxOrderDuration, dynamicOrderDuration, maxSlippageEntry);
                }
            } else if (notionalPosition.get(id) == 0 && getAdvanceEntryOrder().get(id) == 0 && shortOnly && exPriceBarShort.get(id) && this.getLastOrderDate().compareTo(new Date()) > 0 && this.getBreachDown().get(id) >= this.getBreachUp().get(id) && this.getBreachUp().get(id) >= 1) {
                if ((Parameters.symbol.get(id).getLastPrice() - threshold) < this.getLowestLow().get(id)
                        && (Parameters.symbol.get(id).getLastPrice() + threshold) < this.getHighestHigh().get(id)
                        && this.shortOnly) {
                    //place advance order to short
                    this.getAdvanceEntryOrder().set(id, -1L);
                    logger.log(Level.INFO, "place advance order to short. Symbol:{0},LastPrice: {1}, LowPrice: :{2} ,HighPrice: :{3} ,Threshold: {4}", new Object[]{Parameters.symbol.get(id).getSymbol(), Parameters.symbol.get(id).getLastPrice(), this.getLowestLow().get(id), this.getHighestHigh().get(id), threshold});
                    orderTurtle.tes.fireOrderEvent(-1,-1,Parameters.symbol.get(id), EnumOrderSide.SHORT, size, this.getLowestLow().get(id) - Parameters.symbol.get(id).getAggression(), lowTriggerPrice, "idt", 0, exit, EnumOrderIntent.Init, maxOrderDuration, dynamicOrderDuration, maxSlippageEntry);
                }
            }
        }        
    }

    public void placeAdvancedExitOrders(int id){
         //Place advance orders
        double threshold = this.getHighestHigh().get(id) - this.getLowestLow().get(id) > 1 ? 0.5 : (this.getHighestHigh().get(id) - this.getLowestLow().get(id)) / 2;
        boolean tradeable = Double.parseDouble(Parameters.symbol.get(id).getAdditionalInput()) / (Parameters.symbol.get(id).getMinsize() * 375) >= 6.0 && this.getCumVolume().get(id).size() > this.getStartBars() && Parameters.symbol.get(id).getLastPrice() > 0;
        if (this.getExposure() != 0 && this.getCumVolume().get(id).size() > this.getStartBars() && Parameters.symbol.get(id).getLastPrice() > 0) {
            tradeable = true;
        }
        int size = this.getExposure() != 0 ? (int) (this.getExposure() / Parameters.symbol.get(id).getLastPrice()) : Parameters.symbol.get(id).getMinsize();
        double highTriggerPrice=this.getHighestHigh().get(id);//+Double.parseDouble(tickSize);
        double lowTriggerPrice=this.getLowestLow().get(id);//-Double.parseDouble(tickSize);
        
        //amend advance sell order
        if (notionalPosition.get(id) == 1 && getAdvanceExitOrder().get(id) == -1) {
            if ((Parameters.symbol.get(id).getLastPrice() - threshold) < this.getLowestLow().get(id)
                    && (Parameters.symbol.get(id).getLastPrice() + threshold) < this.getHighestHigh().get(id)) {
                //amend existing advance sell order
                logger.log(Level.INFO, "Amend existing advance sell order. Symbol:{0},LastPrice: {1}, LowPrice: :{2} ,HighPrice: :{3} ,Threshold: {4}", new Object[]{Parameters.symbol.get(id).getSymbol(), Parameters.symbol.get(id).getLastPrice(), this.getLowestLow().get(id), this.getHighestHigh().get(id), threshold});
                orderTurtle.tes.fireOrderEvent(-1,-1,Parameters.symbol.get(id), EnumOrderSide.SELL, size, this.getLowestLow().get(id) - Parameters.symbol.get(id).getAggression(), lowTriggerPrice, "idt", 0, exit, EnumOrderIntent.Amend, maxOrderDuration, dynamicOrderDuration, maxSlippageExit);
            } else {
                //cancel order. There is no need for advance sell order.
                logger.log(Level.INFO, "cancel order. There is no need for advance sell order. Symbol:{0},LastPrice: {1}, LowPrice: :{2} ,HighPrice: :{3} ,Threshold: {4}", new Object[]{Parameters.symbol.get(id).getSymbol(), Parameters.symbol.get(id).getLastPrice(), this.getLowestLow().get(id), this.getHighestHigh().get(id), threshold});
                orderTurtle.tes.fireOrderEvent(-1,-1,Parameters.symbol.get(id), EnumOrderSide.SELL, size, this.getLowestLow().get(id) - Parameters.symbol.get(id).getAggression(), lowTriggerPrice, "idt", 0, exit, EnumOrderIntent.Cancel, maxOrderDuration, dynamicOrderDuration, maxSlippageExit);
                this.getAdvanceExitOrder().set(id, 0L);
            }
        }
        //amend advance cover order
        if (notionalPosition.get(id) == -1 && getAdvanceExitOrder().get(id) == 1) {
            if ((Parameters.symbol.get(id).getLastPrice() + threshold) > this.getHighestHigh().get(id)
                    && (Parameters.symbol.get(id).getLastPrice() - threshold) > this.getLowestLow().get(id)) {
                //amend existing advance cover order
                logger.log(Level.INFO, "Amend existing advance cover order. Symbol:{0},LastPrice: {1}, LowPrice: :{2} ,HighPrice: :{3} ,Threshold: {4}", new Object[]{Parameters.symbol.get(id).getSymbol(), Parameters.symbol.get(id).getLastPrice(), this.getLowestLow().get(id), this.getHighestHigh().get(id), threshold});
                orderTurtle.tes.fireOrderEvent(-1,-1,Parameters.symbol.get(id), EnumOrderSide.COVER, size, this.getHighestHigh().get(id) + Parameters.symbol.get(id).getAggression(), highTriggerPrice, "idt", 0, exit, EnumOrderIntent.Amend, maxOrderDuration, dynamicOrderDuration, maxSlippageExit);
            } else {
                //cancel order. There is no need for advance cover order.
                logger.log(Level.INFO, "cancel order. There is no need for advance cover order. Symbol:{0},LastPrice: {1}, LowPrice: :{2} ,HighPrice: :{3} ,Threshold: {4}", new Object[]{Parameters.symbol.get(id).getSymbol(), Parameters.symbol.get(id).getLastPrice(), this.getLowestLow().get(id), this.getHighestHigh().get(id), threshold});
                orderTurtle.tes.fireOrderEvent(-1,-1,Parameters.symbol.get(id), EnumOrderSide.COVER, size, this.getHighestHigh().get(id) + Parameters.symbol.get(id).getAggression(), highTriggerPrice, "idt", 0, exit, EnumOrderIntent.Cancel, maxOrderDuration, dynamicOrderDuration, maxSlippageExit);
                this.getAdvanceExitOrder().set(id, 0L);
            }
        }
        
        //now place sell and cover advance orders
        if (notionalPosition.get(id) == -1 && getAdvanceExitOrder().get(id) == 0) {
            if ((Parameters.symbol.get(id).getLastPrice() + threshold) > this.getHighestHigh().get(id)
                    && (Parameters.symbol.get(id).getLastPrice() - threshold) > this.getHighestHigh().get(id)) //place advance order to cover
            {
                this.getAdvanceExitOrder().set(id, 1L);

                logger.log(Level.INFO, "place advance order to cover. Symbol:{0},LastPrice: {1}, LowPrice: :{2} ,HighPrice: :{3} ,Threshold: {4}", new Object[]{Parameters.symbol.get(id).getSymbol(), Parameters.symbol.get(id).getLastPrice(), this.getLowestLow().get(id), this.getHighestHigh().get(id), threshold});
                orderTurtle.tes.fireOrderEvent(-1,-1,Parameters.symbol.get(id), EnumOrderSide.COVER, size, this.getHighestHigh().get(id) + Parameters.symbol.get(id).getAggression(), highTriggerPrice, "idt", 0, exit, EnumOrderIntent.Init, maxOrderDuration, dynamicOrderDuration, maxSlippageExit);
            }
        }


        if (notionalPosition.get(id) == 1 && getAdvanceExitOrder().get(id) == 0) {
            if ((Parameters.symbol.get(id).getLastPrice() - threshold) < this.getLowestLow().get(id)) {
                //place advance order to sell
                logger.log(Level.INFO, "place advance order to sell. Symbol:{0},LastPrice: {1}, LowPrice: :{2} ,HighPrice: :{3} ,Threshold: {4}", new Object[]{Parameters.symbol.get(id).getSymbol(), Parameters.symbol.get(id).getLastPrice(), this.getLowestLow().get(id), this.getHighestHigh().get(id), threshold});
                orderTurtle.tes.fireOrderEvent(-1,-1,Parameters.symbol.get(id), EnumOrderSide.SELL, size, this.getLowestLow().get(id) - Parameters.symbol.get(id).getAggression(), lowTriggerPrice, "idt", 0, exit, EnumOrderIntent.Init, maxOrderDuration, dynamicOrderDuration, maxSlippageExit);
                this.getAdvanceExitOrder().set(id, -1L);
            }
        }
    }
    @Override
    public synchronized void tradeReceived(TradeEvent event) {

        try {
            int id = event.getSymbolID(); //here symbolID is with zero base.
            if (this.tradeableSymbols.contains(id)) {
                boolean ruleHighestHigh = Parameters.symbol.get(id).getLastPrice() > this.getHighestHigh().get(id);
                boolean ruleLowestLow = Parameters.symbol.get(id).getLastPrice() < this.getLowestLow().get(id);
                boolean ruleCumVolumeLong = this.getCumVolume().get(id).get(this.getCumVolume().get(id).size() - 1) >= 0.05 * Double.parseDouble(Parameters.symbol.get(id).getAdditionalInput());
                boolean ruleCumVolumeShort = this.getCumVolume().get(id).get(this.getCumVolume().get(id).size() - 1) <= Double.parseDouble(Parameters.symbol.get(id).getAdditionalInput());
                boolean ruleSlopeLong = this.getSlope().get(id) > Double.parseDouble(Parameters.symbol.get(id).getAdditionalInput()) * this.getVolumeSlopeLongMultiplier() / 375;
                boolean ruleSlopeShort = this.getSlope().get(id) < -Double.parseDouble(Parameters.symbol.get(id).getAdditionalInput()) * this.getVolumeSlopeShortMultipler() / 375;
                boolean ruleVolumeLong = this.getVolume().get(id) > maVolumeLong*this.getVolumeMA().get(id);
                boolean ruleVolumeShort = this.getVolume().get(id) > maVolumeShort* this.getVolumeMA().get(id);
                ruleCumVolumeLong = true;
                ruleCumVolumeShort = true;
                //ruleVolumeLong = true;
                //ruleVolumeShort = true;
                if (this.tradeableSymbols.contains(id)) {
                    generateOrders(id, ruleHighestHigh, ruleLowestLow, ruleCumVolumeLong, ruleCumVolumeShort, ruleSlopeLong, ruleSlopeShort, ruleVolumeLong, ruleVolumeShort, false);
                }
            }

        } catch (Exception e) {
            logger.log(Level.SEVERE, null,e);
        }
    }

    private synchronized void generateOrders(int id, boolean ruleHighestHigh, boolean ruleLowestLow, boolean ruleCumVolumeLong, boolean ruleCumVolumeShort, boolean ruleSlopeLong,
            boolean ruleSlopeShort, boolean ruleVolumeLong, boolean ruleVolumeShort, boolean sourceBars) {
        try {
            boolean tradeable = Double.parseDouble(Parameters.symbol.get(id).getAdditionalInput()) / (Parameters.symbol.get(id).getMinsize() * 375) >= 6.0 && this.getCumVolume().get(id).size() > this.getStartBars() && Parameters.symbol.get(id).getLastPrice() > 0;
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
                    String.valueOf(Double.parseDouble(Parameters.symbol.get(id).getAdditionalInput()) * this.getVolumeSlopeLongMultiplier() / 375),
                    this.getVolumeMA().get(id).toString(),
                    0.05 * Double.parseDouble(Parameters.symbol.get(id).getAdditionalInput()),
                    Double.parseDouble(Parameters.symbol.get(id).getAdditionalInput()),
                    DateUtil.getFormatedDate("yyyyMMdd HH:mm:ss z", Parameters.symbol.get(id).getLastPriceTime()),
                    this.getBreachUp().get(id),
                    this.getBreachDown().get(id),
                    tradeable,
                    this.getNotionalPosition().get(id),
                    longOnly,
                    ruleHighestHigh,
                    ruleCumVolumeLong,
                    ruleSlopeLong,
                    ruleVolumeLong,
                    breachup,
                    shortOnly,
                    ruleLowestLow,
                    ruleCumVolumeShort,
                    ruleSlopeShort,
                    ruleVolumeShort,
                    breachdown
                });
            }

            if (tradeable && this.getNotionalPosition().get(id) == 0 && this.getCumVolume().get(id).size() >= this.getChannelDuration()) { //basic conditions met for testing entry
                if (longOnly && ruleHighestHigh && (exPriceBarLong.get(id) && sourceBars || (!sourceBars && ruleCumVolumeLong && ruleSlopeLong && ruleVolumeLong)) && this.getLastOrderDate().compareTo(new Date()) > 0 && breachup > 0.5 && this.getBreachDown().get(id) >= 1) {
                    //Buy Condition
                    this.getNotionalPosition().set(id, 1L);
                    int size = this.getExposure() != 0 ? (int) (this.getExposure() / Parameters.symbol.get(id).getLastPrice()) : Parameters.symbol.get(id).getMinsize();
                    logger.log(Level.INFO, "Buy. Symbol:{0},LL:{1},LastPrice:{2},HH{3},Slope:{4},SlopeThreshold:{5},Volume:{6},VolumeMA:{7}, Breachup:{8},Breachdown:{9}",
                            new Object[]{Parameters.symbol.get(id).getSymbol(), this.getLowestLow().get(id).toString(), Parameters.symbol.get(id).getLastPrice(), this.getHighestHigh().get(id).toString(), this.getSlope().get(id).toString(), String.valueOf(Double.parseDouble(Parameters.symbol.get(id).getAdditionalInput()) * this.getVolumeSlopeLongMultiplier() / 375), this.getVolume().get(id).toString(), this.getVolumeMA().get(id).toString(), this.getBreachUp().get(id) + 1, this.getBreachDown().get(id)
                    });
                    getTrades().put(internalorderID, new Trade(id,EnumOrderSide.BUY,this.getHighestHigh().get(id),size,this.internalorderID++));
                    this.internalOpenOrders.put(id, this.internalorderID-1);
                    if(!skipAfterWins || lastTradeWasLosing.get(id)){
                    if (this.getAdvanceEntryOrder().get(id) == 0) { //no advance order present
                        orderTurtle.tes.fireOrderEvent(this.internalorderID-1,this.internalorderID-1,Parameters.symbol.get(id), EnumOrderSide.BUY, size, this.getHighestHigh().get(id) + Parameters.symbol.get(id).getAggression(), 0, "idt", maxOrderDuration, exit, EnumOrderIntent.Init, maxOrderDuration, dynamicOrderDuration, maxSlippageEntry);
                    } else if (this.getAdvanceEntryOrder().get(id) == 1) {
                        orderTurtle.tes.fireOrderEvent(this.internalorderID-1,this.internalorderID-1,Parameters.symbol.get(id), EnumOrderSide.BUY, size, this.getHighestHigh().get(id) + Parameters.symbol.get(id).getAggression(), 0, "idt", maxOrderDuration, exit, EnumOrderIntent.Amend, maxOrderDuration, dynamicOrderDuration, maxSlippageEntry);
                    } else if (this.getAdvanceEntryOrder().get(id) == -1) { //advance order is short.
                        orderTurtle.tes.fireOrderEvent(this.internalorderID-1,this.internalorderID-1,Parameters.symbol.get(id), EnumOrderSide.BUY, size, this.getHighestHigh().get(id) + Parameters.symbol.get(id).getAggression(), 0, "idt", maxOrderDuration, exit, EnumOrderIntent.Cancel, maxOrderDuration, dynamicOrderDuration, maxSlippageEntry);
                        orderTurtle.tes.fireOrderEvent(this.internalorderID-1,this.internalorderID-1,Parameters.symbol.get(id), EnumOrderSide.BUY, size, this.getHighestHigh().get(id) + Parameters.symbol.get(id).getAggression(), 0, "idt", maxOrderDuration, exit, EnumOrderIntent.Init, maxOrderDuration, dynamicOrderDuration, maxSlippageEntry);
                    }
                    this.advanceEntryOrder.set(id, 0L);
                    }else{
                        logger.log(Level.INFO,"First Trade or Last Trade was a winning Trade. No orders placed for Symbol {1}",new Object[]{Parameters.symbol.get(id).getSymbol()});
                    }
                } else if (shortOnly && ruleLowestLow && (exPriceBarShort.get(id) && sourceBars || (!sourceBars && ruleCumVolumeShort && ruleSlopeShort && ruleVolumeShort)) && this.getLastOrderDate().compareTo(new Date()) > 0 && breachdown > 0.5 && this.getBreachUp().get(id) >= 1) {
                    //Short condition
                    this.getNotionalPosition().set(id, -1L);
                    int size = this.getExposure() != 0 ? (int) (this.getExposure() / Parameters.symbol.get(id).getLastPrice()) : Parameters.symbol.get(id).getMinsize();
                    logger.log(Level.INFO, "Short. Symbol:{0},LL:{1},LastPrice:{2},HH{3},Slope:{4},SlopeThreshold:{5},Volume:{6},VolumeMA:{7},Breachup:{8},Breachdown:{9}",
                            new Object[]{Parameters.symbol.get(id).getSymbol(), this.getLowestLow().get(id).toString(), Parameters.symbol.get(id).getLastPrice(), this.getHighestHigh().get(id).toString(), this.getSlope().get(id).toString(), String.valueOf(Double.parseDouble(Parameters.symbol.get(id).getAdditionalInput()) * this.getVolumeSlopeLongMultiplier() / 375), this.getVolume().get(id).toString(), this.getVolumeMA().get(id).toString(), this.getBreachUp().get(id), this.getBreachUp().get(id) + 1
                    });
                    getTrades().put(internalorderID, new Trade(id,EnumOrderSide.SHORT,this.getLowestLow().get(id),size,this.internalorderID++));
                    this.internalOpenOrders.put(id, this.internalorderID-1);
                    if(!skipAfterWins || lastTradeWasLosing.get(id)){
                    if (this.getAdvanceEntryOrder().get(id) == 0) {
                        orderTurtle.tes.fireOrderEvent(this.internalorderID-1,this.internalorderID-1,Parameters.symbol.get(id), EnumOrderSide.SHORT, size, this.getLowestLow().get(id) - Parameters.symbol.get(id).getAggression(), 0, "idt", maxOrderDuration, exit, EnumOrderIntent.Init, maxOrderDuration, dynamicOrderDuration, maxSlippageEntry);
                    } else if (this.getAdvanceEntryOrder().get(id) == -1) {
                        orderTurtle.tes.fireOrderEvent(this.internalorderID-1,this.internalorderID-1,Parameters.symbol.get(id), EnumOrderSide.SHORT, size, this.getLowestLow().get(id) - Parameters.symbol.get(id).getAggression(), 0, "idt", maxOrderDuration, exit, EnumOrderIntent.Amend, maxOrderDuration, dynamicOrderDuration, maxSlippageEntry);
                    } else if (this.getAdvanceEntryOrder().get(id) == 1) {
                        orderTurtle.tes.fireOrderEvent(this.internalorderID-1,this.internalorderID-1,Parameters.symbol.get(id), EnumOrderSide.SHORT, size, this.getLowestLow().get(id) - Parameters.symbol.get(id).getAggression(), 0, "idt", maxOrderDuration, exit, EnumOrderIntent.Cancel, maxOrderDuration, dynamicOrderDuration, maxSlippageEntry);
                        orderTurtle.tes.fireOrderEvent(this.internalorderID-1,this.internalorderID-1,Parameters.symbol.get(id), EnumOrderSide.SHORT, size, this.getLowestLow().get(id) - Parameters.symbol.get(id).getAggression(), 0, "idt", maxOrderDuration, exit, EnumOrderIntent.Init, maxOrderDuration, dynamicOrderDuration, maxSlippageEntry);
                    }
                    this.advanceEntryOrder.set(id, 0L);
                    }else{
                        logger.log(Level.INFO,"First Trade or Last Trade was a winning Trade. No orders placed for Symbol {1}",new Object[]{Parameters.symbol.get(id).getSymbol()});
                    }

                }
            } else if (this.getNotionalPosition().get(id) == -1) { //position exists. Check for exit conditions for a short
                if (ruleHighestHigh || System.currentTimeMillis() > endDate.getTime()) {
                    this.getNotionalPosition().set(id, 0L);
                    int size = this.getExposure() != 0 ? (int) (this.getExposure() / Parameters.symbol.get(id).getLastPrice()) : Parameters.symbol.get(id).getMinsize();
                    logger.log(Level.INFO, "Cover.Symbol:{0},LL:{1},LastPrice:{2},HH{3},Slope:{4},SlopeThreshold:{5},Volume:{6},VolumeMA:{7}",
                            new Object[]{Parameters.symbol.get(id).getSymbol(), this.getLowestLow().get(id).toString(), Parameters.symbol.get(id).getLastPrice(), this.getHighestHigh().get(id).toString(), this.getSlope().get(id).toString(), String.valueOf(Double.parseDouble(Parameters.symbol.get(id).getAdditionalInput()) * this.getVolumeSlopeLongMultiplier() / 375), this.getVolume().get(id).toString(), this.getVolumeMA().get(id).toString()
                    });
                    int entryInternalOrderID=this.internalOpenOrders.get(id);
                    Trade originalTrade=getTrades().get(entryInternalOrderID);
                    originalTrade.updateExit(id,EnumOrderSide.COVER,this.getHighestHigh().get(id),size,this.internalorderID++);                    
                    getTrades().put(entryInternalOrderID, originalTrade);
                    if(entryInternalOrderID!=0){
                   if(  getTrades().get(entryInternalOrderID).getEntryPrice()>=this.getHighestHigh().get(id)){
                        this.lastTradeWasLosing.set(id,Boolean.FALSE);
                    }
                    else{
                        this.lastTradeWasLosing.set(id,Boolean.TRUE);
                    }
                    }
                    if (ruleHighestHigh) {
                        if (this.getAdvanceExitOrder().get(id) == 0) {
                            orderTurtle.tes.fireOrderEvent(this.internalorderID-1,entryInternalOrderID,Parameters.symbol.get(id), EnumOrderSide.COVER, size, this.getHighestHigh().get(id), 0, "idt", maxOrderDuration, "", EnumOrderIntent.Init, maxOrderDuration, dynamicOrderDuration, maxSlippageExit);
                        } else if (this.getAdvanceExitOrder().get(id) == 1) {
                            orderTurtle.tes.fireOrderEvent(this.internalorderID-1,entryInternalOrderID,Parameters.symbol.get(id), EnumOrderSide.COVER, size, this.getHighestHigh().get(id), 0, "idt", maxOrderDuration, "", EnumOrderIntent.Amend, maxOrderDuration, dynamicOrderDuration, maxSlippageExit);
                        } else if (this.getAdvanceExitOrder().get(id) == -1) {
                            orderTurtle.tes.fireOrderEvent(this.internalorderID-1,entryInternalOrderID,Parameters.symbol.get(id), EnumOrderSide.COVER, size, this.getHighestHigh().get(id), 0, "idt", maxOrderDuration, "", EnumOrderIntent.Cancel, maxOrderDuration, dynamicOrderDuration, maxSlippageExit);
                            orderTurtle.tes.fireOrderEvent(this.internalorderID-1,entryInternalOrderID,Parameters.symbol.get(id), EnumOrderSide.COVER, size, this.getHighestHigh().get(id), 0, "idt", maxOrderDuration, "", EnumOrderIntent.Init, maxOrderDuration, dynamicOrderDuration, maxSlippageExit);
                        }
                        this.getAdvanceExitOrder().set(id, 0L);
                    } else if (System.currentTimeMillis() > endDate.getTime()) {
                        logger.log(Level.INFO,"Current Time is after program end date. Cover. Cancel open orders and place closeout.");
                        orderTurtle.tes.fireOrderEvent(this.internalorderID-1,entryInternalOrderID,Parameters.symbol.get(id), EnumOrderSide.COVER, size, this.getHighestHigh().get(id), 0, "idt", maxOrderDuration, "", EnumOrderIntent.Cancel, maxOrderDuration, dynamicOrderDuration, maxSlippageExit);
                        orderTurtle.tes.fireOrderEvent(this.internalorderID-1,entryInternalOrderID,Parameters.symbol.get(id), EnumOrderSide.COVER, size, this.getClose().get(id), 0, "idt", maxOrderDuration, "", EnumOrderIntent.Init, maxOrderDuration, dynamicOrderDuration, maxSlippageExit);
                        this.advanceExitOrder.set(id, 0L);

                    }
                }

            } else if (this.getNotionalPosition().get(id) == 1) { //position exists. Check for exit condition for long
                if (ruleLowestLow || System.currentTimeMillis() > endDate.getTime()) {
                    this.getNotionalPosition().set(id, 0L);
                    int size = this.getExposure() != 0 ? (int) (this.getExposure() / Parameters.symbol.get(id).getLastPrice()) : Parameters.symbol.get(id).getMinsize();
                    logger.log(Level.INFO, "Sell.Symbol:{0},LL:{1},LastPrice:{2},HH{3},Slope:{4},SlopeThreshold:{5},Volume:{6},VolumeMA:{7}",
                            new Object[]{Parameters.symbol.get(id).getSymbol(), this.getLowestLow().get(id).toString(), Parameters.symbol.get(id).getLastPrice(), this.getHighestHigh().get(id).toString(), this.getSlope().get(id).toString(), String.valueOf(Double.parseDouble(Parameters.symbol.get(id).getAdditionalInput()) * this.getVolumeSlopeLongMultiplier() / 375), this.getVolume().get(id).toString(), this.getVolumeMA().get(id).toString()
                    });
                     int entryInternalOrderID=this.internalOpenOrders.get(id);
                    Trade originalTrade=getTrades().get(entryInternalOrderID);
                    originalTrade.updateExit(id,EnumOrderSide.SELL,this.getLowestLow().get(id),size,this.internalorderID++);                    
                    getTrades().put(entryInternalOrderID, originalTrade);
                    if(entryInternalOrderID!=0){
                    if( getTrades().get(entryInternalOrderID).getEntryPrice()<=this.getLowestLow().get(id)){
                        this.lastTradeWasLosing.set(id,Boolean.FALSE);
                    }
                    else{
                        this.lastTradeWasLosing.set(id,Boolean.TRUE);
                    }
                    }
                    if (ruleLowestLow) {
                        if (this.getAdvanceExitOrder().get(id) == 0) {
                            orderTurtle.tes.fireOrderEvent(this.internalorderID-1,entryInternalOrderID,Parameters.symbol.get(id), EnumOrderSide.SELL, size, this.getLowestLow().get(id), 0, "idt", maxOrderDuration, "", EnumOrderIntent.Init, maxOrderDuration, dynamicOrderDuration, maxSlippageExit);
                        } else if (this.getAdvanceExitOrder().get(id) == -1) {
                            orderTurtle.tes.fireOrderEvent(this.internalorderID-1,entryInternalOrderID,Parameters.symbol.get(id), EnumOrderSide.SELL, size, this.getLowestLow().get(id), 0, "idt", maxOrderDuration, "", EnumOrderIntent.Amend, maxOrderDuration, dynamicOrderDuration, maxSlippageExit);
                        } else if (this.getAdvanceExitOrder().get(id) == 1) {
                            orderTurtle.tes.fireOrderEvent(this.internalorderID-1,entryInternalOrderID,Parameters.symbol.get(id), EnumOrderSide.SELL, size, this.getLowestLow().get(id), 0, "idt", maxOrderDuration, "", EnumOrderIntent.Cancel, maxOrderDuration, dynamicOrderDuration, maxSlippageExit);
                            orderTurtle.tes.fireOrderEvent(this.internalorderID-1,entryInternalOrderID,Parameters.symbol.get(id), EnumOrderSide.SELL, size, this.getLowestLow().get(id), 0, "idt", maxOrderDuration, "", EnumOrderIntent.Init, maxOrderDuration, dynamicOrderDuration, maxSlippageExit);
                        }
                        this.getAdvanceExitOrder().set(id, 0L);
                    } else if (System.currentTimeMillis() > endDate.getTime()) {
                        logger.log(Level.INFO,"Current Time is after program end date. Sell. Cancel open orders and place closeout.");
                        orderTurtle.tes.fireOrderEvent(this.internalorderID-1,entryInternalOrderID,Parameters.symbol.get(id), EnumOrderSide.SELL, size, this.getLowestLow().get(id), 0, "idt", maxOrderDuration, "", EnumOrderIntent.Cancel, maxOrderDuration, dynamicOrderDuration, maxSlippageExit);
                        orderTurtle.tes.fireOrderEvent(this.internalorderID-1,entryInternalOrderID,Parameters.symbol.get(id), EnumOrderSide.SELL, size, this.getClose().get(id), 0, "idt", maxOrderDuration, "", EnumOrderIntent.Init, maxOrderDuration, dynamicOrderDuration, maxSlippageExit);
                        this.advanceExitOrder.set(id, 0L);

                    }
                }
            }

            //force close of all open positions, after closeTime
            if (System.currentTimeMillis() + 3000 > endDate.getTime()) { //i wait for 3 seconds as there could be a gap in clock synchronization
                int symb = 0; //symb replaces id in this loop
                for (Long j : this.getNotionalPosition()) {
                    if (j > 0) {
                        //close long
                        int size = this.getExposure() != 0 ? (int) (this.getExposure() / Parameters.symbol.get(symb).getLastPrice()) : Parameters.symbol.get(symb).getMinsize();
                        this.getNotionalPosition().set(symb, 0L);
                        int entryInternalOrderID=this.internalOpenOrders.get(symb);
                        Trade originalTrade=getTrades().get(entryInternalOrderID);
                        originalTrade.updateExit(symb,EnumOrderSide.SELL,this.getClose().get(symb),size,this.internalorderID++);                    
                        getTrades().put(entryInternalOrderID, originalTrade);
                        logger.log(Level.INFO, "Sell. Force Close All Positions.Symbol:{0}", new Object[]{Parameters.symbol.get(symb).getSymbol()});
                        orderTurtle.tes.fireOrderEvent(this.internalorderID-1,entryInternalOrderID,Parameters.symbol.get(symb), EnumOrderSide.SELL, size, this.getClose().get(symb), 0, "idt", maxOrderDuration, "", EnumOrderIntent.Cancel, maxOrderDuration, dynamicOrderDuration, maxSlippageExit);
                        orderTurtle.tes.fireOrderEvent(this.internalorderID-1,entryInternalOrderID,Parameters.symbol.get(symb), EnumOrderSide.SELL, size, this.getClose().get(symb), 0, "idt", maxOrderDuration, "", EnumOrderIntent.Init, maxOrderDuration, dynamicOrderDuration, maxSlippageExit);
                        this.advanceExitOrder.set(symb, 0L);
                    } else if (j < 0) {
                        //close short
                        int size = this.getExposure() != 0 ? (int) (this.getExposure() / Parameters.symbol.get(symb).getLastPrice()) : Parameters.symbol.get(symb).getMinsize();
                        this.getNotionalPosition().set(symb, 0L);
                        int entryInternalOrderID=this.internalOpenOrders.get(symb);
                        Trade originalTrade=getTrades().get(entryInternalOrderID);
                        originalTrade.updateExit(symb,EnumOrderSide.COVER,this.getClose().get(symb),size,this.internalorderID++);                    
                        getTrades().put(entryInternalOrderID, originalTrade);
                        logger.log(Level.INFO, "Cover. Force Close All Positions.Symbol:{0}", new Object[]{Parameters.symbol.get(symb).getSymbol()});
                        orderTurtle.tes.fireOrderEvent(this.internalorderID-1,entryInternalOrderID,Parameters.symbol.get(symb), EnumOrderSide.COVER, size, this.getClose().get(symb), 0, "idt", maxOrderDuration, "", EnumOrderIntent.Cancel, maxOrderDuration, dynamicOrderDuration, maxSlippageExit);
                        orderTurtle.tes.fireOrderEvent(this.internalorderID-1,entryInternalOrderID,Parameters.symbol.get(symb), EnumOrderSide.COVER, size, this.getClose().get(symb), 0, "idt", maxOrderDuration, "", EnumOrderIntent.Init, maxOrderDuration, dynamicOrderDuration, maxSlippageExit);
                        this.advanceExitOrder.set(symb, 0L);
                    }
                    symb = symb + 1;
                }
            }


        } catch (Exception e) {
            logger.log(Level.SEVERE, null,e);
        }
    }

    /**
     * @return the logger
     */
    public static Logger getLOGGER() {
        return logger;
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
     * @return the longVolume
     */
    public ArrayList<Long> getLongVolume() {
        return longVolume;
    }

    /**
     * @param longVolume the longVolume to set
     */
    public void setLongVolume(ArrayList<Long> longVolume) {
        this.longVolume = longVolume;
    }

    /**
     * @return the shortVolume
     */
    public ArrayList<Long> getShortVolume() {
        return shortVolume;
    }

    /**
     * @param shortVolume the shortVolume to set
     */
    public void setShortVolume(ArrayList<Long> shortVolume) {
        this.shortVolume = shortVolume;
    }

    /**
     * @return the notionalPosition
     */
    public synchronized ArrayList<Long> getNotionalPosition() {
        return notionalPosition;
    }

    /**
     * @param notionalPosition the notionalPosition to set
     */
    public synchronized void setNotionalPosition(ArrayList<Long> notionalPosition) {
        this.notionalPosition = notionalPosition;
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
     * @return the display
     */
    public int getDisplay() {
        return display;
    }

    /**
     * @param display the display to set
     */
    public void setDisplay(int display) {
        this.display = display;
    }

    /**
     * @return the longOnly
     */
    public synchronized Boolean getLongOnly() {
        return longOnly;
    }

    /**
     * @param longOnly the longOnly to set
     */
    public synchronized void setLongOnly(Boolean longOnly) {
        this.longOnly = longOnly;
    }

    /**
     * @return the shortOnly
     */
    public synchronized Boolean getShortOnly() {
        return shortOnly;
    }

    /**
     * @param shortOnly the shortOnly to set
     */
    public synchronized void setShortOnly(Boolean shortOnly) {
        this.shortOnly = shortOnly;
    }

    /**
     * @return the exit
     */
    public String getExit() {
        return exit;
    }

    /**
     * @param exit the exit to set
     */
    public void setExit(String exit) {
        this.exit = exit;
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
     * @return the maxOrderDuration
     */
    public int getMaxOrderDuration() {
        return maxOrderDuration;
    }

    /**
     * @param maxOrderDuration the maxOrderDuration to set
     */
    public void setMaxOrderDuration(int maxOrderDuration) {
        this.maxOrderDuration = maxOrderDuration;
    }

    /**
     * @return the dynamicOrderDuration
     */
    public int getDynamicOrderDuration() {
        return dynamicOrderDuration;
    }

    /**
     * @param dynamicOrderDuration the dynamicOrderDuration to set
     */
    public void setDynamicOrderDuration(int dynamicOrderDuration) {
        this.dynamicOrderDuration = dynamicOrderDuration;
    }

    /**
     * @return the maxSlippageEntry
     */
    public double getMaxSlippageEntry() {
        return maxSlippageEntry;
    }

    /**
     * @param maxSlippageEntry the maxSlippageEntry to set
     */
    public void setMaxSlippageEntry(double maxSlippageEntry) {
        this.maxSlippageEntry = maxSlippageEntry;
    }

    /**
     * @return the aggression
     */
    public Boolean getAggression() {
        return aggression;
    }

    /**
     * @param aggression the aggression to set
     */
    public void setAggression(Boolean aggression) {
        this.aggression = aggression;
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

    /**
     * @return the endDate
     */
    public static Date getEndDate() {
        return endDate;
    }

    /**
     * @param aEndDate the endDate to set
     */
    public static void setEndDate(Date aEndDate) {
        endDate = aEndDate;
    }

    /**
     * @return the tickSize
     */
    public String getTickSize() {
        return tickSize;
    }

    /**
     * @param aTickSize the tickSize to set
     */
    public void setTickSize(String aTickSize) {
        tickSize = aTickSize;
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
     * @return the profitTarget
     */
    public double getProfitTarget() {
        return profitTarget;
    }

    /**
     * @param profitTarget the profitTarget to set
     */
    public void setProfitTarget(double profitTarget) {
        this.profitTarget = profitTarget;
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

    /**
     * @return the trades
     */
    public HashMap<Integer,Trade> getTrades() {
        return trades;
    }

    /**
     * @param trades the trades to set
     */
    public void setTrades(HashMap<Integer,Trade> trades) {
        this.trades = trades;
    }
    }
