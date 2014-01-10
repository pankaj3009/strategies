/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package TurtleTrading;

import incurrframework.*;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.SortedMap;
import java.util.Timer;
import java.util.TreeMap;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JFrame;
import org.apache.commons.math3.stat.descriptive.*;
import org.apache.commons.math3.stat.regression.*;

/**
 *
 * @author admin
 */
public class MainAlgorithm extends Algorithm  {

    /**
     * @return the param
     */
    public MarketData m;
    public final static Logger LOGGER = Logger.getLogger(Algorithm.class.getName());
    public final static Logger logger=Logger.getLogger("KeyParameters");
    private BeanTurtle paramTurtle;
    private BeanGuds paramGuds;
    public OrderPlacement ordManagement;
    private Date preopenDate;
    private static Date startDate;
    private Date closeDate;
    Timer preopen;
    public static Boolean preOpenCompleted=false;
    private BeanSwing paramSwing;
    private List<String> strategies=new ArrayList();
    private List<Double> maxPNL=new ArrayList();
    private List<Double>minPNL=new ArrayList();
    private String historicalData;
    private String realTimeBars;
    
    public MainAlgorithm(List<String> args) throws Exception {
        super(args); //this initializes the connection and symbols
       
        // initialize anything else 
        //initialized wrappers
        //BoilerPlate

        Properties pmaster = new Properties(System.getProperties());
        Properties pstrategy=new Properties(System.getProperties());
        FileInputStream propFileMaster;
        FileInputStream propFileStrategy;
        try {
            propFileMaster = new FileInputStream("Master.properties");
            propFileStrategy=new FileInputStream(args.get(0));
            try {
                pmaster.load(propFileMaster);
                pstrategy.load(propFileStrategy);
            } catch (IOException ex) {
                Logger.getLogger(BeanTurtle.class.getName()).log(Level.SEVERE, null, ex);
            }
        } catch (FileNotFoundException ex) {
            Logger.getLogger(BeanTurtle.class.getName()).log(Level.SEVERE, null, ex);
        }
 
        
        for (BeanConnection c : Parameters.connection) {
            c.setWrapper(new TWSConnection(c));
        }

        for (BeanConnection c : Parameters.connection) {
            c.getWrapper().connectToTWS();
        }

        //Synchronize clocks
        for (BeanConnection c : Parameters.connection) {
            c.getWrapper().eClientSocket.reqCurrentTime();
        }

        //Populate Contract Details
        
        BeanConnection tempC = Parameters.connection.get(0);
        for (BeanSymbol s : Parameters.symbol) {
            tempC.getWrapper().getContractDetails(s,"");
            System.out.print("ContractDetails Requested:" + s.getSymbol());
        }

        while (TWSConnection.mTotalSymbols > 0) {
            //System.out.println(TWSConnection.mTotalSymbols);
            //do nothing
            MainAlgorithmUI.setMessage("Waiting for contract information to be retrieved");
        }
        //Request Market Data
        List<BeanSymbol> filteredSymbols = new ArrayList();
        for(BeanSymbol s : Parameters.symbol) {
        if( "N".compareTo(s.getPreopen())==0){
             filteredSymbols.add(s);
        }
}
        int count = filteredSymbols.size();
        int allocatedCapacity = 0;
        for (BeanConnection c : Parameters.connection) {
            //if ("Data".equals(c.getPurpose())) {
            int connectionCapacity = c.getTickersLimit();
            if (count > 0) {
                m = new MarketData(c, allocatedCapacity, Math.min(count, connectionCapacity),filteredSymbols);
                allocatedCapacity = allocatedCapacity + Math.min(count, connectionCapacity);
                count = count - Math.min(count, connectionCapacity);
                m.start();
            }
        }
                
        
        System.setProperties(pstrategy);
        String currDateStr = DateUtil.getFormatedDate("yyyyMMdd", Parameters.connection.get(0).getConnectionTime());
        String preopenStr = currDateStr + " " + System.getProperty("PreOpenTime");
        String startDateStr=currDateStr + " " + System.getProperty("StartTime");
        String realtimebarsStr = currDateStr + " " + System.getProperty("RealTimeBarsTime");
        String closeStr = currDateStr + " " + System.getProperty("CloseTime");
        preopenDate = DateUtil.parseDate("yyyyMMdd HH:mm:ss", preopenStr);
        startDate=DateUtil.parseDate("yyyyMMdd HH:mm:ss", startDateStr);
        closeDate = DateUtil.parseDate("yyyyMMdd HH:mm:ss", closeStr);
        System.setProperties(pmaster);
        historicalData=System.getProperty("HistoricalData");
        realTimeBars=System.getProperty("RealTimeBars");
        if (closeDate.compareTo(preopenDate) < 0 && new Date().compareTo(closeDate) > 0) {
            //increase enddate by one calendar day
            closeDate = DateUtil.addDays(closeDate, 1); //system date is > start date time. Therefore we have not crossed the 12:00 am barrier
        } else if (closeDate.compareTo(preopenDate) < 0 && new Date().compareTo(preopenDate) < 0) {
            preopenDate = DateUtil.addDays(preopenDate, -1); // we have moved beyond 12:00 am . adjust startdate to previous date
            startDate=DateUtil.addDays(startDate, -1);
        } else if(new Date().compareTo(preopenDate)>0 && new Date().compareTo(closeDate)>0){
            preopenDate=DateUtil.addDays(preopenDate, 1);
            startDate=DateUtil.addDays(startDate, 1);
            closeDate=DateUtil.addDays(closeDate, 1);
        }
        String tempstrategies = System.getProperty("StrategyCount");
        this.setStrategies( Arrays.asList(tempstrategies.split("\\s*,\\s*")));
         for (int i = 0; i < strategies.size(); i++) {
          minPNL.add(0D);
          maxPNL.add(0D);
         }
        preopen=new Timer();
       // preopen.schedule(new SnapShotPreOpenPrice(), preopenDate);       
        //initialize listners
       paramTurtle = new BeanTurtle(this);
       // paramGuds = new BeanGuds(this);
       // paramSwing=new BeanSwing(this);
        ordManagement = new OrderPlacement(this);

       
        //Attempt realtime bars in a new thread
        createAndShowGUI(this);
        //get historical data - this can be done before start time, assuming the program is started next day
                try{
            if(Boolean.valueOf(historicalData)){
        Thread t = new Thread(new HistoricalBars());
        t.setName("Historical Bars");
        MainAlgorithmUI.setMessage("Starting request of Historical Data for yesterday");
        t.start();
        t.join();
        }
        }catch (Exception e){
            
        }
       
        if(!Boolean.valueOf(historicalData)){
            for(BeanSymbol s: Parameters.symbol){
        ArrayList<BeanOHLC> yestOHLC=TradingUtil.getDailyBarsFromOneMinCandle(7,s.getSymbol()+"_FUT");
        s.setAdditionalInput(Long.toString(yestOHLC.get(0).getVolume()));
          }
        }
        
        
        
        startDataCollection(historicalData,realTimeBars,startDate);
        
    }
 
    public synchronized void startDataCollection(String historicalData, String realTimeBars,Date startDate){
        
      if(startDate.compareTo(new Date())<0){
        if(Boolean.valueOf(realTimeBars)){
            MainAlgorithmUI.setMessage("Starting request of RealTime Bars");
        new RealTimeBars(getParamTurtle());
        //BoilerPlate Ends
        }
        
        LOGGER.log(Level.FINEST, ",Symbol" + "," + "BarNo" + "," + "HighestHigh" + "," + "LowestLow" + "," + "LastPrice" + "," + "Volume" + "," + "CumulativeVol" + "," + "VolumeSlope" + "," + "MinSlopeReqd" + "," + "MA" + "," + "LongVolume" + "," + "ShortVolume" + "," + "DateTime" + ","
                + "ruleHighestHigh" + "," + "ruleCumVolumeLong" + "," + "ruleSlopeLong" + "," + "ruleVolumeLong" + "," + "ruleLowestLow" + ","
                + "ruleCumVolumeShort" + "," + "ruleSlopeShort" + "," + "ruleVolumeShort");
            
            }else{
                MainAlgorithmUI.setMessage("Please Click Button 'Start Program' after the market open time");
                
            }
        
}
    public void fireOrderEvent(BeanSymbol s, EnumOrderSide side, int size, double lmtprice, double triggerprice, String ordReference, int expireTime,String exitType, EnumOrderIntent intent,int maxorderduration, int dynamicorderduration, double maxslippage) {
        OrderEvent order = new OrderEvent(this, s, side, size, lmtprice, triggerprice,ordReference,expireTime,exitType,intent,maxorderduration,dynamicorderduration,maxslippage);
        Iterator listeners = _listeners.iterator();
        while (listeners.hasNext()) {
            ((OrderListener) listeners.next()).orderReceived(order);
        }
    }

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

    /**
     * @return the paramGuds
     */
    public BeanGuds getParamGuds() {
        return paramGuds;
    }

    /**
     * @param paramGuds the paramGuds to set
     */
    public void setParamGuds(BeanGuds paramGuds) {
        this.paramGuds = paramGuds;
    }

    /**
     * @return the paramTurtle
     */
    public BeanTurtle getParamTurtle() {
        return paramTurtle;
    }

    /**
     * @param paramTurtle the paramTurtle to set
     */
    public void setParamTurtle(BeanTurtle paramTurtle) {
        this.paramTurtle = paramTurtle;
    }

    /**
     * @return the preopenDate
     */
    public Date getPreopenDate() {
        return preopenDate;
    }

    /**
     * @return the strategies
     */
    public List<String> getStrategies() {
        return strategies;
    }

    /**
     * @param strategies the strategies to set
     */
    public void setStrategies(List<String> strategies) {
        this.strategies = strategies;
    }

    /**
     * @return the maxPNL
     */
    public List<Double> getMaxPNL() {
        return maxPNL;
    }

    /**
     * @param maxPNL the maxPNL to set
     */
    public void setMaxPNL(List<Double> maxPNL) {
        this.maxPNL = maxPNL;
    }

    /**
     * @return the minPNL
     */
    public List<Double> getMinPNL() {
        return minPNL;
    }

    /**
     * @param minPNL the minPNL to set
     */
    public void setMinPNL(List<Double> minPNL) {
        this.minPNL = minPNL;
    }

    /**
     * @return the startDate
     */
    public static Date getStartDate() {
        return startDate;
    }

    /**
     * @param startDate the startDate to set
     */
    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    /**
     * @return the historicalData
     */
    public String getHistoricalData() {
        return historicalData;
    }

    /**
     * @param historicalData the historicalData to set
     */
    public void setHistoricalData(String historicalData) {
        this.historicalData = historicalData;
    }

    /**
     * @return the realTimeBars
     */
    public String getRealTimeBars() {
        return realTimeBars;
    }

    /**
     * @param realTimeBars the realTimeBars to set
     */
    public void setRealTimeBars(String realTimeBars) {
        this.realTimeBars = realTimeBars;
    }

    /**
     * @return the closeDate
     */
    public Date getCloseDate() {
        return closeDate;
    }

    /**
     * @param closeDate the closeDate to set
     */
    public void setCloseDate(Date closeDate) {
        this.closeDate = closeDate;
    }

}
