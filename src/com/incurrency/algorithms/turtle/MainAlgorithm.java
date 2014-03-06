/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.algorithms.turtle;

import com.incurrency.framework.TWSConnection;
import com.incurrency.framework.EnumOrderIntent;
import com.incurrency.framework.GUIMissedOrders;
import com.incurrency.framework.Algorithm;
import com.incurrency.framework.GUIDashBoard;
import com.incurrency.framework.DateUtil;
import com.incurrency.framework.OrderEvent;
import com.incurrency.framework.BeanConnection;
import com.incurrency.framework.GUIInProgressOrders;
import com.incurrency.framework.TradingUtil;
import com.incurrency.framework.Parameters;
import com.incurrency.framework.TWSConnection;
import com.incurrency.framework.MarketData;
import com.incurrency.framework.RealTimeBars;
import com.incurrency.framework.BeanSymbol;
import com.incurrency.framework.GUIInProgressOrders;
import com.incurrency.framework.GUIPNLDashBoard;
import com.incurrency.framework.TradingUtil;
import com.incurrency.framework.OrderListener;
import com.incurrency.framework.HistoricalBars;
import com.incurrency.framework.RealTimeBars;
import com.incurrency.framework.BeanConnection;
import com.incurrency.framework.GUIMissedOrders;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.Properties;
import java.util.Timer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.swing.JFrame;
import com.RatesClient.Subscribe;
import com.incurrency.algorithms.adr.ADR;
import com.incurrency.framework.ADRRates;
import com.incurrency.framework.BeanOHLC;
import com.incurrency.framework.BeanSymbolCompare;
import com.incurrency.framework.EnumOrderSide;
import java.util.HashMap;
import java.util.Map;
/**
 *
 * @author admin
 */
public class MainAlgorithm extends Algorithm  {

    //public MarketData m;
    //public MarketData mSnap;
    static HashMap<String,String> input = new HashMap();
    public final static Logger logger = Logger.getLogger(MainAlgorithm.class.getName());
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
    private String mySQL;
    //private boolean marketDataNotStarted=true;
    private String realAccountTrading="False";
    private boolean license=false;
    private String accounts="";
    private String macID="";
    private Date expiryDate;
    private static String collectTicks;
    private double profitTarget;
    private boolean tradingAlgoInitialized=false;
    
    public MainAlgorithm(HashMap<String,String> args) throws Exception {
        super(args); //this initializes the connection and symbols
        this.input=args;
         /*
         * MainAlgorithm does the following
         * 1. Inititalizes the thread to listen and place messages to each connection
         * 2. Gets the time difference of system clock with each connection
         * 3. Checks for license
         * 4. Only if license is available, retrieves contracts for all symbols. Cleans symbols file for missing contracts.
         *    and writes to logfile if any symbols are invalid
         * 5.Initializes each strategy file
         * 
         */
        
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

        //check license
        
        //Request account numbers. Store account code + Trading requests
        //Request Account Updates for each account in connection.csv
        
        for(BeanConnection c:Parameters.connection){
            c.getWrapper().getAccountUpdates();
            c.setAccountName(c.getWrapper().getAccountIDSync().take());
        }
        
        for(BeanConnection c:Parameters.connection){
            System.out.print("Going to cancel account updated");
            c.getWrapper().cancelAccountUpdates();
            System.out.println("Account updates cancelled");
        }
         if(!MainAlgorithmUI.headless){
             MainAlgorithmUI.setIBMessage("");
         }
  
       System.out.println(Boolean.valueOf(realAccountTrading));
        if(Boolean.valueOf(realAccountTrading)){
        for(BeanConnection c:Parameters.connection){//check license for each real account setup with strategy
            System.out.println("Purpose Real: "+"Trading".equals(c.getPurpose()));
            System.out.println("Contains Real: "+c.getStrategy().contains("IDT"));
            System.out.println("Account:"+c.getAccountName());
            if("Trading".equals(c.getPurpose()) && c.getStrategy().contains("IDT")){
                String account=c.getAccountName();
                macID=TradingUtil.populateMACID();
                 if (accounts.compareTo("")==0){
                          accounts=account;
                      }else{
                          accounts=accounts+","+account;
                      }   
        }
        }
        expiryDate=TradingUtil.getExpiryDate(macID, accounts,Boolean.valueOf(realAccountTrading));
        
        }else {
            for(BeanConnection c:Parameters.connection){ //check license for each real account setup with strategy
            System.out.println("Purpose Paper: "+"Trading".equals(c.getPurpose()));
            System.out.println("Contains Paper: "+c.getStrategy().contains("IDT"));
            System.out.println("Account:"+c.getAccountName());
                if("Trading".equals(c.getPurpose()) && c.getStrategy().contains("IDT")){
                String account=c.getAccountName();
                macID=TradingUtil.populateMACID();
                  if (account.substring(0,1).compareTo("D")==0){
                      if (accounts.compareTo("")==0){
                          accounts=account;
                      }else{
                          accounts=accounts+","+account;
                      }
                  }
            }
            }
            expiryDate=TradingUtil.getExpiryDate(macID, accounts,Boolean.valueOf(realAccountTrading));
        }
         if(expiryDate.compareTo(new Date())>0){
             license=true;
         }
        
         if(!license){
              if(!MainAlgorithmUI.headless){
                  MainAlgorithmUI.setMessage("No License. Please register or contact license@incurrency.com");
              }
              if(!MainAlgorithmUI.headless){
                  MainAlgorithmUI.displayRegistration(true);
              }
         } else{
             if(!MainAlgorithmUI.headless){
                 MainAlgorithmUI.displayRegistration(false);
             }
            
            
         }
        
        //Populate Contract Details
        
         if(license){
        BeanConnection tempC = Parameters.connection.get(0);
        for (BeanSymbol s : Parameters.symbol) {
            tempC.getWrapper().getContractDetails(s,"");
            System.out.print("ContractDetails Requested:" + s.getSymbol());
        }

        while (TWSConnection.mTotalSymbols > 0) {
            //System.out.println(TWSConnection.mTotalSymbols);
            //do nothing
             if(!MainAlgorithmUI.headless){
                 MainAlgorithmUI.setMessage("Waiting for contract information to be retrieved");
             }
        }
        
        ArrayList <Boolean> arrayID=new ArrayList();
        for(BeanSymbol s: Parameters.symbol){
               arrayID.add(s.isStatus());
         }
        
        for(int i=0;i<arrayID.size();i++){
            if(!arrayID.get(i)){ //if status is false, remove the symbol
                Parameters.symbol.remove(i);
            }
        }
         ArrayList<BeanSymbol> adrList=new ArrayList();
        //populate adrList with symbols needed for ADR
        for(BeanSymbol s: Parameters.symbol){
            if (Pattern.compile(Pattern.quote("ADR"), Pattern.CASE_INSENSITIVE).matcher(s.getStrategy()).find()){
                adrList.add(s);
            }
        }
        ADR adr =new ADR(adrList);
        //Request Market Data
        
        Thread.sleep(1000);
        
        List<BeanSymbol> filteredSymbols = new ArrayList();
        for(BeanSymbol s : Parameters.symbol) {
        if( "N".compareTo(s.getPreopen())==0){
             filteredSymbols.add(s);
        }
}
        Collections.sort(filteredSymbols,new BeanSymbolCompare()); //sorts symbols in order of preference streaming priority. low priority is higher
        int count = filteredSymbols.size();
        int allocatedCapacity = 0;
        for (BeanConnection c : Parameters.connection) {
            //if ("Data".equals(c.getPurpose())) {
            int connectionCapacity = c.getTickersLimit();
            if (count > 0) {
                MarketData m = new MarketData(c, allocatedCapacity, Math.min(count, connectionCapacity),filteredSymbols);
                allocatedCapacity = allocatedCapacity + Math.min(count, connectionCapacity);
                count = count - Math.min(count, connectionCapacity);
                m.start();
            }
        }
        
        boolean getsnapshotfromallconnections=(input.get("getsnapshotfromallconnections")==null||input.get("getsnapshotfromallconnections").compareTo("false")==0)?false:true;
        //If there are symbols left, request snapshot. Distribute across accounts
        if(getsnapshotfromallconnections){
        for (BeanConnection c : Parameters.connection) {
             int snapshotcount=count/Parameters.connection.size();
             MarketData msnap = new MarketData(c, allocatedCapacity, snapshotcount,filteredSymbols);
            msnap.setSnapshot(true);
            msnap.start();
         }
        }
        //Alternatively, we use 1st connection for snapshot. Make sure it has the number of symbols permitted as zero
        else {
            if(count>0){
             int snapshotcount=count;;
             MarketData msnap = new MarketData(Parameters.connection.get(0), allocatedCapacity, snapshotcount,filteredSymbols);
             msnap.setSnapshot(true);
             msnap.start();
             }
        }
             
        //ADRRates rates=new ADRRates();
        //Subscribe prices=new Subscribe("103.250.184.15:5556");
	
        int strategyCount=0;
            for (Map.Entry value : input.entrySet()) {
                if(value.getKey().equals("idt")||value.getKey().equals("swing")||value.getKey().equals("guds")){
                    strategyCount=strategyCount+1;
                }
            }
         for (int i = 0; i < strategyCount; i++) {
          minPNL.add(0D);
          maxPNL.add(0D);
         }
         

        //preopen=new Timer();
       // preopen.schedule(new SnapShotPreOpenPrice(), preopenDate);       
        //initialize listners
        if(input.containsKey("idt")){
            paramTurtle = new BeanTurtle(this);
            strategies.add("IDT");
            this.tradingAlgoInitialized=true;
        }
        
        if(args.containsKey("guds")){
            BeanGuds paramGuds = new BeanGuds(this);
            strategies.add("GUDS");
            this.tradingAlgoInitialized=true;
        }
            
        if(args.containsKey("swing")){
            BeanSwing paramSwing = new BeanSwing(this);
            strategies.add("SWING");
            this.tradingAlgoInitialized=true;
        }
         
       // paramGuds = new BeanGuds(this);
       // paramSwing=new BeanSwing(this);
        if(tradingAlgoInitialized){
        ordManagement = new OrderPlacement(this);
        }
       
        //Attempt realtime bars in a new thread
        if(!MainAlgorithmUI.headless && tradingAlgoInitialized){
            createAndShowGUI(this);
        }
       
        
        /*
        if(Boolean.valueOf(mySQL)){
            for(BeanSymbol s: Parameters.symbol){
        ArrayList<BeanOHLC> yestOHLC=TradingUtil.getDailyBarsFromOneMinCandle(7,s.getSymbol()+"_FUT");
        s.setAdditionalInput(Long.toString(yestOHLC.get(0).getVolume()));
          }
        }
        */
        
     
        
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

        /**
     * @return the collectTicks
     */
    public static String getCollectTicks() {
        return collectTicks;
    }

    /**
     * @param aCollectTicks the collectTicks to set
     */
    public static void setCollectTicks(String aCollectTicks) {
        collectTicks = aCollectTicks;
    }

    /**
     * @return the profitTarget
     */
    public synchronized double getProfitTarget() {
        return profitTarget;
    }

    /**
     * @param profitTarget the profitTarget to set
     */
    public synchronized void setProfitTarget(double profitTarget) {
        this.profitTarget = profitTarget;
    }

    /**
     * @return the tradingAlgoInitialized
     */
    public boolean isTradingAlgoInitialized() {
        return tradingAlgoInitialized;
    }

    /**
     * @param tradingAlgoInitialized the tradingAlgoInitialized to set
     */
    public void setTradingAlgoInitialized(boolean tradingAlgoInitialized) {
        this.tradingAlgoInitialized = tradingAlgoInitialized;
    }
}
