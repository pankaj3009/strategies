/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package TurtleTrading;

import incurrframework.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.FileInputStream;
import java.util.List;
import java.lang.Math;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Queue;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JFrame;
import org.apache.commons.math3.stat.descriptive.*;
import org.apache.commons.math3.stat.regression.*;
import javax.swing.JOptionPane; 
import javax.swing.Timer;

/**
 *
 * @author admin
 */
public class MainAlgorithm extends Algorithm implements HistoricalBarListener, TradeListner {

    /**
     * @return the queue
     */
    public synchronized static ConcurrentHashMap getQueue() {
        return queue;
    }

    /**
     * @param aQueue the queue to set
     */
    public synchronized static void setQueue(ConcurrentHashMap aQueue) {
        queue = aQueue;
    }

    /**
     * @return the BarsCount
     */
    public static synchronized HashMap<Integer,Integer> getBarsCount() {
        return BarsCount;
    }

    /**
     * @param aBarsCount the BarsCount to set
     */
    public static synchronized void setBarsCount(HashMap<Integer,Integer> aBarsCount) {
        BarsCount = aBarsCount;
    }

    /**
     * @return the queueHistRequests
     */
    public static ConcurrentLinkedQueue getQueueHistRequests() {
        return queueHistRequests;
    }

    /**
     * @param aQueueHistRequests the queueHistRequests to set
     */
    public static void setQueueHistRequests(ConcurrentLinkedQueue aQueueHistRequests) {
        queueHistRequests = aQueueHistRequests;
    }



    public MarketData m;
    //strategy variables
    private ArrayList<ArrayList<Long>> cumVolume = new ArrayList<ArrayList<Long>>();
    //private ArrayList<Long> cumVolume = new <Long[]> ArrayList(); //algo parameter 
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
    private Date startDate;
    private Date endDate;
    private int channelDuration;
    private int regressionLookBack;
    private double volumeSlopeLongMultiplier;
    private double volumeSlopeShortMultipler;
    private boolean marketOpen = false; // market state
    private boolean mocPeriod = false; //market state
    private OrderPlacement ordManagement;
    public final static Logger LOGGER = Logger.getLogger(Algorithm.class.getName());
    //private RealTimeBars rtBars = new RealTimeBars();
    //private static LinkedHashMap queue = new <Integer,PendingHistoricalRequests> LinkedHashMap();
    private static ConcurrentHashMap queue = new <Integer,PendingHistoricalRequests> ConcurrentHashMap();
    
    private static ConcurrentLinkedQueue queueHistRequests=new ConcurrentLinkedQueue(new ArrayList<PendingHistoricalRequests>());
    private static ArrayList<PendingHistoricalRequests>temp=new ArrayList<PendingHistoricalRequests>();
 //   private static LinkedBlockingQueue<LinkedHashMap<Integer,PendingHistoricalRequests>> queue = new LinkedBlockingQueue<LinkedHashMap<Integer,PendingHistoricalRequests>>(1);
    
    private static HashMap<Integer,Integer> BarsCount=new HashMap();
    
    
    public MainAlgorithm(List<String> args) throws Exception {
        super(args); //this initializes the connection and symbols
        
        queueHistRequests=new ConcurrentLinkedQueue(temp);
        // initialize anything else 
        //initialized wrappers
        //BoilerPlate

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
            tempC.getWrapper().getContractDetails(s);
            System.out.print("ContractDetails Requested:"+s.getSymbol());
        }

        while (TWSConnection.mTotalSymbols > 0) {
            
            //System.out.println(TWSConnection.mTotalSymbols);
            //do nothing
        }
        //Request Market Data
        int count = Parameters.symbol.size();
        int allocatedCapacity = 0;
        for (BeanConnection c : Parameters.connection) {
            //if ("Data".equals(c.getPurpose())) {
                int connectionCapacity = c.getTickersLimit();
                if (count > 0) {
                    m = new MarketData(c, allocatedCapacity, Math.min(count, connectionCapacity));
                    count = count - Math.min(count, connectionCapacity);
                    allocatedCapacity = allocatedCapacity + Math.min(count, connectionCapacity);
                    m.start();
                }
            //}
        }
        
        //Initialize algo variables
        ordManagement=new OrderPlacement(this);
        Properties p = new Properties(System.getProperties());
        FileInputStream propFile = new FileInputStream("NewSwing.properties");
        p.load(propFile);
        System.setProperties(p);
        String currDateStr = DateUtil.getFormatedDate("yyyyMMdd", Parameters.connection.get(0).getConnectionTime());
        String startDateStr = currDateStr + " " + System.getProperty("StartTime");
        String endDateStr = currDateStr + " " + System.getProperty("EndTime");
        startDate = DateUtil.parseDate("yyyyMMdd HH:mm:ss", startDateStr);
        endDate = DateUtil.parseDate("yyyyMMdd HH:mm:ss", endDateStr);
        channelDuration = Integer.parseInt(System.getProperty("ChannelDuration"));
        volumeSlopeLongMultiplier = Double.parseDouble(System.getProperty("VolSlopeMultLong"));
        volumeSlopeShortMultipler = Double.parseDouble(System.getProperty("VolSlopeMultLong"));
        regressionLookBack = Integer.parseInt(System.getProperty("RegressionLookBack"));

        for (int i = 0; i < Parameters.symbol.size(); i++) {
            cumVolume.add(i,new ArrayList<Long>());
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
            Parameters.symbol.get(i).getOneMinuteBar().addHistoricalBarListener(this);
            Parameters.symbol.get(i).getDailyBar().addHistoricalBarListener(this);
        }
        //Attempt realtime bars in a new thread
        //new Thread(new RealTimeBars(),"RealTime").start();
     /*
        Thread t = new Thread(new HistoricalBars());
     t.setName("Historical Bars");
     t.start();
     t.join();
     */
     
     new RealTimeBars();

        
      
        //BoilerPlate Ends



            Parameters.addTradeListener(this);
        //initialize listners
      LOGGER.log(Level.INFO,",Symbol"+","+"BarNo"+","+"HighestHigh"+","+"LowestLow"+","+"LastPrice"+","+"Volume"+","+"CumulativeVol"+","+"VolumeSlope"+","+"MinSlopeReqd"+","+"MA"+","+"LongVolume"+","+"ShortVolume"+","+"DateTime"+","+
              "ruleHighestHigh"+","+"ruleCumVolumeLong"+","+"ruleSlopeLong"+","+"ruleVolumeLong"+","+"ruleLowestLow"+","+
              "ruleCumVolumeShort"+","+"ruleSlopeShort"+","+"ruleVolumeShort" );
      createAndShowGUI(this);
        
    }
    /*
     * barsReceived() - fired when one minute bar is completed.
     * a. update bar close
     * b. update bar number
     * c. update cumVolume
     * d. Update Highest High
     * e. Update Lowest Low
     */

    @Override
    public synchronized void barsReceived(HistoricalBarEvent event) {

        //get the symbolbean
        try {
             //For one minute bars
            if(event.ohlc().getPeriodicity()==EnumBarSize.OneMin){
            int id = event.getSymbol().getSerialno() - 1;
            close.set(id, event.ohlc().getClose());
            int barno = event.barNumber();
            LOGGER.log(Level.INFO,"Bar No:{0}, Date={1}, Symbol:{2},FirstBarTime:{3}, LastBarTime:{4}, LastKey-FirstKey:{5}",
                    new Object[]{barno,DateUtil.getFormatedDate("yyyyMMdd HH:mm:ss",event.ohlc().getOpenTime()),Parameters.symbol.get(id).getSymbol()
                    ,DateUtil.getFormatedDate("yyyyMMdd HH:mm:ss",event.list().firstKey()),DateUtil.getFormatedDate("yyyyMMdd HH:mm:ss",event.list().lastKey())
                    ,(event.list().lastKey()-event.list().firstKey())/(1000*60)});
            //Set cumVolume
            int size=getCumVolume().get(id).size();
            SortedMap <Long,BeanOHLC> temp = new TreeMap<Long,BeanOHLC>();   
            int cumVolumeStartSize=getCumVolume().get(id).size();
 //           LOGGER.log(Level.INFO, "CumVolume.get(id).size()={0}", size);
           //check if bars are complete. If bars are not complete, send add to pending requests and exit.
            String startTime=System.getProperty("StartTime");
            SimpleDateFormat sdfDate = new SimpleDateFormat("HH:mm:ss");//dd/MM/yyyy
            String firstBarTime = sdfDate.format(event.list().firstEntry().getKey());
   //         String firstBarTime=DateUtil.toTimeString(event.list().firstEntry().getKey());
            boolean exclude=false; //this excludes cumvol calculation if its already calculated in the first loop.
            if(!firstBarTime.contains(startTime)){
                startTime=DateUtil.getFormatedDate("yyyyMMdd HH:mm:ss",event.list().firstEntry().getKey());
                PendingHistoricalRequests temphistReq =new PendingHistoricalRequests(event.getSymbol().getSerialno(),startTime,"1 D","1 min");
                PendingHistoricalRequests histReq=getQueue().get(event.getSymbol().getSerialno())==null?temphistReq:(PendingHistoricalRequests)getQueue().get(event.getSymbol().getSerialno());    
                getQueue().put(event.getSymbol().getSerialno(), histReq);
                return;
            } 
            else if(event.list().size()-1==(event.list().lastKey()-event.list().firstKey())/(1000*60)){
                MainAlgorithm.getBarsCount().put(id+1, 1);
                if((barno>=2) && (cumVolumeStartSize<barno-1)){
                LOGGER.log(Level.INFO,"Setting Cumulative Vol in Loop 1. Bar No:{0}, cumVolumeStartSize={1}, Symbol:{2}",new Object[]{barno,cumVolumeStartSize,Parameters.symbol.get(id).getSymbol()});
                    //we have cumVolume from earlier bars that is not populated. Populate these
                //int cumVolIndex=cumVolume.get(id).size()-1;
                double priorClose=0;
                int i=0;
                for(Map.Entry<Long,BeanOHLC> entry : event.list().entrySet()) {
                    BeanOHLC OHLC = entry.getValue();
                    if(OHLC.getClose()>priorClose && i>0){
                        long tempVol=cumVolume.get(id).get(i-1)+OHLC.getVolume();
                        cumVolume.get(id).add(tempVol);
                    } else if(OHLC.getClose()<priorClose && i>0){
                        long tempVol=cumVolume.get(id).get(i-1)-OHLC.getVolume();
                        cumVolume.get(id).add(tempVol);
                    } else if(OHLC.getClose()==priorClose && i>0){
                        long tempVol=cumVolume.get(id).get(i-1);
                        cumVolume.get(id).add(tempVol);
                    }                        
                    priorClose=OHLC.getClose();
                    i=i+1;
                } 
                exclude=true;
            }
             
                
            }
            else return;
            
            //the key = symbol id.
            //check if there are OHLC bars created that dont have a cumulative volume.
 
            if (barno ==getCumVolume().get(id).size()+1 && !exclude) {
             int ref=barno-1;
             LOGGER.log(Level.INFO,"Setting Cumulative Vol in Loop 2. Bar No:{0}, cumVolumeStartSize={1}, Symbol:{2}",new Object[]{barno,cumVolumeStartSize,Parameters.symbol.get(id).getSymbol()});
             BeanOHLC OHLC = event.list().lastEntry().getValue();
             Long OHLCPriorKey=event.list().lowerKey(event.list().lastKey());
             BeanOHLC OHLCPrior=event.list().get(OHLCPriorKey);
                    if(OHLC.getClose()>OHLCPrior.getClose()){
                        long tempVol=cumVolume.get(id).get(ref-1)+OHLC.getVolume();
                        cumVolume.get(id).add(tempVol);
                    } else if(OHLC.getClose()<OHLCPrior.getClose()){
                        long tempVol=cumVolume.get(id).get(ref-1)-OHLC.getVolume();
                        cumVolume.get(id).add(tempVol);
                    } else if(OHLC.getClose()==OHLCPrior.getClose()){
                        long tempVol=cumVolume.get(id).get(ref-1);
                        cumVolume.get(id).add(tempVol);
                    }
            }
            
            int size1=getCumVolume().get(id).size();
            LOGGER.log(Level.INFO,"CumVolume Bars after Loop 2. Bar No:{0}, cumVolumeEndSize={1}, Symbol:{2}",new Object[]{barno,size1,Parameters.symbol.get(id).getSymbol()});            
            if( getCumVolume().get(id).size()<event.barNumber()){
                //JOptionPane.showMessageDialog (null, "Error" ); 
                 LOGGER.log(Level.INFO,"Error. Bars:{0}, cumVolumeEndSize={1}, Symbol:{2}",new Object[]{barno,size1,Parameters.symbol.get(id).getSymbol()});
                
            }
                getVolume().set(id, event.ohlc().getVolume());
//            LOGGER.log(Level.INFO, "Volume set to:{0}", Volume.get(id));
            //Set Highest High and Lowest Low

            if (event.barNumber() >= channelDuration) {
                temp = (SortedMap<Long, BeanOHLC>) event.list().subMap(event.ohlc().getOpenTime()-channelDuration*60*1000+1, event.ohlc().getOpenTime()+1);
                double HH = 0;
                double LL = Double.MAX_VALUE;
                for (Map.Entry<Long, BeanOHLC> entry : temp.entrySet())
                {
                    HH = HH > entry.getValue().getHigh()? HH : entry.getValue().getHigh();
                    LL = LL < entry.getValue().getLow() && LL != 0 ? LL : entry.getValue().getLow();
                }
                /*
                Iterator itr = temp.entrySet().iterator();
                while (itr.hasNext()) {
                    TreeMap<Long,BeanOHLC> tempOHLC = (TreeMap<Long,BeanOHLC>) itr.next();
                    HH = HH > tempOHLC.getHigh() ? HH : tempOHLC.getHigh();
                    LL = LL < tempOHLC.getLow() && LL != 0 ? LL : tempOHLC.getLow();
                }
                */
                    getHighestHigh().set(id, HH);
                    getLowestLow().set(id, LL);
            }
            //Set Slope
            List<Long> tempCumVolume = new ArrayList();
            if (event.barNumber() >= regressionLookBack) {
                tempCumVolume = (List<Long>) getCumVolume().get(id).subList(event.barNumber() - regressionLookBack, event.barNumber());

                SimpleRegression regression = new SimpleRegression();
                int itr = tempCumVolume.size();
                double i = 0;
                while (i < itr) {
                    regression.addData(i + 1, (Long) tempCumVolume.get((int) i));
                    i = i + 1;
                }
                double tempSlope = Double.isNaN(regression.getSlope()) == true ? 0D : regression.getSlope();
                    getSlope().set(id, tempSlope);
            }
            //set MA of volume
            if (event.barNumber() >= channelDuration - 1) {
                temp = (SortedMap<Long, BeanOHLC>) event.list().subMap(event.ohlc().getOpenTime()-(channelDuration-1)*60*1000+1, event.ohlc().getOpenTime()+1);
                DescriptiveStatistics stats = new DescriptiveStatistics();
                for (Map.Entry<Long, BeanOHLC> entry : temp.entrySet())
                {
                    stats.addValue(entry.getValue().getVolume());
                }
                /*
                Iterator itr = temp.iterator();
                int i = 1;
                while (itr.hasNext()) {
                    BeanOHLC tempOHLC = (BeanOHLC) itr.next();
                    stats.addValue(tempOHLC.getVolume());
                }
                */
                    getVolumeMA().set(id, stats.getMean());
            }
            }
            else if(event.ohlc().getPeriodicity()==EnumBarSize.Daily){
                //update symbol volumes
                int id=event.getSymbol().getSerialno()-1;
                BeanSymbol s=Parameters.symbol.get(id);
                if(Long.toString(event.list().lastKey())==DateUtil.getFormatedDate("YYYYMMDD", System.currentTimeMillis())){
                    long tempkey=event.list().lastKey();
                    long tempVol=event.list().lowerKey(tempkey-1);
                    s.setAdditionalInput(String.valueOf(tempVol));
                } else
                s.setAdditionalInput(String.valueOf(event.list().lastEntry().getValue().getVolume()));
            }
            //System.out.println();
            /*
            System.out.println(event.ohlc().getOpenTime().toString() + "," + event.ohlc().getOpen()
                    + ":" + event.ohlc().getHigh()
                    + ":" + event.ohlc().getLow()
                    + ":" + event.ohlc().getClose()
                    + " , HH: " + highestHigh.get(id)
                    + " , LL: " + lowestLow.get(id)
                    + " , Volume MA: " + VolumeMA.get(id)
                    + " , CumVolume: " + cumVolume.get(id).get(cumVolume.get(id).size() - 1)
                    + " , Slope: " + slope.get(id)
                    + ", Bar: " + event.barNumber());
            */
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "{0} Symbol: {1}", new Object[]{e.toString(), event.getSymbol().getSymbol()});
        }
    }

    @Override
    public synchronized  void tradeReceived(TradeEvent event) {
       try{
        int id = event.getSymbolID(); //here symbolID is with zero base.
        boolean ruleHighestHigh=Parameters.symbol.get(id).getLastPrice() > getHighestHigh().get(id);
        boolean ruleLowestLow=Parameters.symbol.get(id).getLastPrice() < getLowestLow().get(id);
        boolean ruleCumVolumeLong =getCumVolume().get(id).get(getCumVolume().get(id).size()-1) >= longVolume.get(id);
        boolean ruleCumVolumeShort =getCumVolume().get(id).get(getCumVolume().get(id).size()-1) <= -shortVolume.get(id);
        boolean ruleSlopeLong= getSlope().get(id) > Double.parseDouble(Parameters.symbol.get(id).getAdditionalInput()) * volumeSlopeLongMultiplier / 375;
        boolean ruleSlopeShort=getSlope().get(id) < -Double.parseDouble(Parameters.symbol.get(id).getAdditionalInput()) * volumeSlopeLongMultiplier / 375;
        boolean ruleVolumeLong=getVolume().get(id) > getVolumeMA().get(id);
        boolean ruleVolumeShort=getVolume().get(id) > 2 * getVolumeMA().get(id);
        
        LOGGER.log(Level.FINEST,","+ "{0},{1},{2},{3},{4},{5},{6},{7},{8},{9},{10},{11},{12},{13},{14},{15},{16},{17},{18},{19},{20}",new Object[]{
            Parameters.symbol.get(id).getSymbol()
            ,String.valueOf(getCumVolume().get(id).size())
            ,getHighestHigh().get(id).toString()
            ,getLowestLow().get(id).toString()
            ,String.valueOf(Parameters.symbol.get(id).getLastPrice())
            ,getVolume().get(id).toString()
            ,getCumVolume().get(id).get(getCumVolume().get(id).size()-1).toString()
            ,getSlope().get(id).toString()
            ,String.valueOf(Double.parseDouble(Parameters.symbol.get(id).getAdditionalInput()) * volumeSlopeLongMultiplier / 375)
            ,getVolumeMA().get(id).toString()
            ,longVolume.get(id).toString()
            ,shortVolume.get(id).toString()
            ,DateUtil.getFormatedDate("yyyyMMdd HH:mm:ss z", Parameters.symbol.get(id).getLastPriceTime())
            ,ruleHighestHigh
            ,ruleCumVolumeLong
            ,ruleSlopeLong
            ,ruleVolumeLong
            ,ruleLowestLow
            ,ruleCumVolumeShort
            ,ruleSlopeShort
            ,ruleVolumeShort
        });
        
        
        if (notionalPosition.get(id) == 0 && getCumVolume().get(id).size()>=channelDuration ) {
            if (ruleHighestHigh && ruleCumVolumeLong && ruleSlopeLong && ruleVolumeLong && endDate.compareTo(new Date())<0) {
        //Buy Condition
                LOGGER.log(Level.INFO,"BUY.Symbol ID={0}",new Object[]{Parameters.symbol.get(id+1).getSymbol()});
                notionalPosition.set(id, 1L);
                _fireOrderEvent(Parameters.symbol.get(id), OrderSide.BUY, Parameters.symbol.get(id).getMinsize(), getHighestHigh().get(id), 0);
          } else if (ruleLowestLow && ruleCumVolumeShort && ruleSlopeShort && ruleVolumeShort && endDate.compareTo(new Date())<0) {
                //Sell condition
                LOGGER.log(Level.INFO,"SHORT. Symbol ID={0}",new Object[]{Parameters.symbol.get(id+1).getSymbol()});
                notionalPosition.set(id, -1L);
                _fireOrderEvent(Parameters.symbol.get(id), OrderSide.SHORT,Parameters.symbol.get(id).getMinsize(), getLowestLow().get(id), 0);
            }
        }
        else  if (notionalPosition.get(id) == -1){
            if(ruleHighestHigh||System.currentTimeMillis()>endDate.getTime()){
                LOGGER.log(Level.INFO,"COVER. Symbol ID={0}", new Object[]{Parameters.symbol.get(id+1).getSymbol()});
                notionalPosition.set(id, 0L);
                _fireOrderEvent(Parameters.symbol.get(id), OrderSide.COVER,Parameters.symbol.get(id).getMinsize(), getHighestHigh().get(id), getHighestHigh().get(id));
            }
            
        } 
        else if (notionalPosition.get(id) == 1){
            if(ruleLowestLow||System.currentTimeMillis()>endDate.getTime()){
                LOGGER.log(Level.INFO,"SELL. Symbol ID={0}",new Object[]{Parameters.symbol.get(id+1).getSymbol()});
                notionalPosition.set(id, 0L);
                _fireOrderEvent(Parameters.symbol.get(id), OrderSide.SELL,Parameters.symbol.get(id).getMinsize(), getLowestLow().get(id), getLowestLow().get(id));
            }
        }        

        
 
    }
       catch (Exception e){
           LOGGER.log(Level.SEVERE,e.toString());
       }
    }


    
    private void _fireOrderEvent(BeanSymbol s, OrderSide side, int size, double lmtprice, double triggerprice) {
        OrderEvent order = new OrderEvent(this, s, side, size, lmtprice, triggerprice);
        Iterator listeners = _listeners.iterator();
        while (listeners.hasNext()) {
            ((OrderListener) listeners.next()).orderReceived(order);
        }
    }

    
    private static void createAndShowGUI(MainAlgorithm m) {
        //Create and set up the window.
        JFrame frame = new JFrame("Positions");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
 
        //Create and set up the content pane.
        GUITable newContentPane = new GUITable(m);
        newContentPane.setOpaque(true); //content panes must be opaque
        frame.setContentPane(newContentPane);
 
        //Display the window.
        frame.pack();
        frame.setVisible(true);
    }
    /**
     * @return the notionalPosition
     */
    public ArrayList<Long> getNotionalPosition() {
        return notionalPosition;
    }

    /**
     * @param notionalPosition the notionalPosition to set
     */
    public void setNotionalPosition(ArrayList<Long> notionalPosition) {
        this.notionalPosition = notionalPosition;
    }

    /**
     * @return the highestHigh
     */
    public ArrayList<Double> getHighestHigh() {
        return highestHigh;
    }

    /**
     * @return the lowestLow
     */
    public ArrayList<Double> getLowestLow() {
        return lowestLow;
    }

    /**
     * @return the slope
     */
    public ArrayList<Double> getSlope() {
        return slope;
    }

    /**
     * @return the Volume
     */
    public ArrayList<Long> getVolume() {
        return Volume;
    }

    /**
     * @return the VolumeMA
     */
    public ArrayList<Double> getVolumeMA() {
        return VolumeMA;
    }

    /**
     * @return the cumVolume
     */
    public ArrayList<ArrayList<Long>> getCumVolume() {
        return cumVolume;
    }
}
