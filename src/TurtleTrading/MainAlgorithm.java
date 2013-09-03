/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package TurtleTrading;

import incurrframework.*;
import java.io.FileInputStream;
import java.util.List;
import java.lang.Math;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.math3.stat.descriptive.*;
import org.apache.commons.math3.stat.regression.*;

/**
 *
 * @author admin
 */
public class MainAlgorithm extends Algorithm implements OneMinBarsListner, TradeListner {

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
    
    public MainAlgorithm(List<String> args) throws Exception {
        super(args); //this initializes the connection and symbols
        
        // initialize anything else 
        //initialized wrappers
        //BoilerPlate
        
        for (ConnectionBean c : Parameters.connection) {
            c.setWrapper(new TWSConnection(c));
        }

        for (ConnectionBean c : Parameters.connection) {
            c.getWrapper().connectToTWS();
        }

        //Synchronize clocks
        for (ConnectionBean c : Parameters.connection) {
            c.getWrapper().eClientSocket.reqCurrentTime();
        }
        //Populate Contract Details
        ConnectionBean tempC = Parameters.connection.get(0);
        for (SymbolBean s : Parameters.symbol) {
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
        for (ConnectionBean c : Parameters.connection) {
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

        //Request RealTime Bars
        tempC = Parameters.connection.get(0);
        for (SymbolBean s : Parameters.symbol) {
            if (s.getBars()) {
                
                System.out.println("Requesting RealTime Bars. Thread going to sleep: "+Thread.currentThread().getName());
                tempC.getWrapper().getRealTimeBars(s);
                Thread.sleep(11000);
            }
        }
        //BoilerPlate Ends

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
            Parameters.symbol.get(i).getmDataBar().addOneMinBarListener(this);
            Parameters.addTradeListener(this);
        }
        //initialize listners
      LOGGER.log(Level.INFO,",Symbol"+","+"BarNo"+","+"HighestHigh"+","+"LowestLow"+","+"LastPrice"+","+"Volume"+","+"CumulativeVol"+","+"VolumeSlope"+","+"MinSlopeReqd"+","+"MA"+","+"LongVolume"+","+"ShortVolume" );

        
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
    public synchronized void barsReceived(OneMinBarsEvent event) {

        //get the symbolbean
        try {
            int id = event.getSymbol().getSerialno() - 1;
            close.set(id, event.ohlc().getClose());
            int barno = event.barNumber();
            System.out.println("Size:"+ event.list().size()+"BarNumber: "+event.barNumber()+"Symbol:"+Parameters.symbol.get(id));

            //Set cumVolume
            List<OHLC> temp = new ArrayList();
            if (barno >= 2) {
                if (event.list().get(barno - 1).getClose() > event.list().get(barno - 2).getClose()) {
                    cumVolume.get(id).add(cumVolume.get(id).get(barno - 2).longValue() + event.ohlc().getVolume());
                } else if (event.list().get(barno - 1).getClose() < event.list().get(barno - 2).getClose()) {
                    cumVolume.get(id).add(cumVolume.get(id).get(barno - 2).longValue() - event.ohlc().getVolume());
                }
            }

            Volume.set(id, event.ohlc().getVolume());

            //Set Highest High and Lowest Low

            if (event.barNumber() >= channelDuration) {
                temp = (List) event.list().subList(event.barNumber() - channelDuration, event.barNumber());

                Iterator itr = temp.iterator();
                double HH = 0;
                double LL = Double.MAX_VALUE;
                while (itr.hasNext()) {
                    OHLC tempOHLC = (OHLC) itr.next();
                    HH = HH > tempOHLC.getHigh() ? HH : tempOHLC.getHigh();
                    LL = LL < tempOHLC.getLow() && LL != 0 ? LL : tempOHLC.getLow();
                }
                highestHigh.set(id, HH);
                lowestLow.set(id, LL);
            }
            //Set Slope
            List<Long> tempCumVolume = new ArrayList();
            if (event.barNumber() >= regressionLookBack) {
                tempCumVolume = (List<Long>) cumVolume.get(id).subList(event.barNumber() - regressionLookBack, event.barNumber());

                SimpleRegression regression = new SimpleRegression();
                int itr = tempCumVolume.size();
                double i = 0;
                while (i < itr) {
                    regression.addData(i + 1, (Long) tempCumVolume.get((int) i));
                    i = i + 1;
                }
                double tempSlope = Double.isNaN(regression.getSlope()) == true ? 0D : regression.getSlope();
                slope.set(id, tempSlope);
            }
            //set MA of volume
            if (event.barNumber() >= channelDuration - 1) {

                temp = (List) event.list().subList(event.barNumber() - channelDuration+1, event.barNumber());

                DescriptiveStatistics stats = new DescriptiveStatistics();
                Iterator itr = temp.iterator();
                int i = 1;
                while (itr.hasNext()) {
                    OHLC tempOHLC = (OHLC) itr.next();
                    stats.addValue(tempOHLC.getVolume());
                }
                VolumeMA.set(id, stats.getMean());
            }
            System.out.println();
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
        } catch (Exception e) {
            LOGGER.log(Level.INFO, e.getMessage());
        }
    }

    @Override
    public synchronized  void tradeReceived(TradeEvent event) {
        //Algo signals generated here based o trade event.
        //System.out.println("TradeReceived. Thread: "+Thread.currentThread().getName());
       
               int id = event.getSymbolID();
        //System.out.print(":"+Parameters.symbol.get(id).getLastPrice());
        System.out.println("Thread Name:"+Thread.currentThread().getName());
        //LOGGER.log(Level.INFO,"Symbol"+","+"BarNo"+","+"HighestHigh"+","+"LowestLow"+","+"LastPrice"+","+"Volume"+","+"CumulativeVol"+","+"VolumeSlope"+","+"MinSlopeReqd"+","+"MA"+","+"LongVolume"+","+"ShortVolume" );
        //Write to Log
        LOGGER.log(Level.INFO,","+ "{0},{1},{2},{3},{4},{5},{6},{7},{8},{9},{10},{11}",new Object[]{
            Parameters.symbol.get(id).getSymbol()
            ,String.valueOf(cumVolume.get(id).size())
            ,highestHigh.get(id).toString()
            ,lowestLow.get(id).toString()
            ,String.valueOf(Parameters.symbol.get(id).getLastPrice())
            ,Volume.get(id).toString()
            ,cumVolume.get(id).get(cumVolume.get(id).size()-1).toString()
            ,slope.get(id).toString()
            ,String.valueOf(Double.parseDouble(Parameters.symbol.get(id).getAdditionalInput()) * volumeSlopeLongMultiplier / 375)
            ,VolumeMA.get(id).toString()
            ,longVolume.get(id).toString()
            ,shortVolume.get(id).toString()});
        
        
        if (notionalPosition.get(id) == 0 && cumVolume.get(id).size()>0) {
            if (Parameters.symbol.get(id).getLastPrice() > highestHigh.get(id)
                    && cumVolume.get(id).get(cumVolume.get(id).size()-1) >= longVolume.get(id)
                    && slope.get(id) > Double.parseDouble(Parameters.symbol.get(id).getAdditionalInput()) * volumeSlopeLongMultiplier / 375
                    && Volume.get(id) > VolumeMA.get(id)) {
                //Buy Condition
                LOGGER.log(Level.INFO,"BUY");
                notionalPosition.set(id, 1L);
                _fireOrderEvent(Parameters.symbol.get(id), OrderSide.BUY, Parameters.symbol.get(id).getMinsize(), highestHigh.get(id), 0);
            } else if (Parameters.symbol.get(id).getLastPrice() < lowestLow.get(id)
                    && cumVolume.get(id).get(cumVolume.get(id).size()-1) <= -shortVolume.get(id)
                    && slope.get(id) < -Double.parseDouble(Parameters.symbol.get(id).getAdditionalInput()) * volumeSlopeLongMultiplier / 375
                    && Volume.get(id) > 2 * VolumeMA.get(id)) {
                //Sell condition
                LOGGER.log(Level.INFO,"SELL");
                notionalPosition.set(id, -1L);
                _fireOrderEvent(Parameters.symbol.get(id), OrderSide.SHORT,Parameters.symbol.get(id).getMinsize(), lowestLow.get(id), 0);
            }
        }
        else  if (notionalPosition.get(id) == -1){
            if(Parameters.symbol.get(id).getLastPrice()>highestHigh.get(id)||System.currentTimeMillis()>endDate.getTime()){
                notionalPosition.set(id, 0L);
                _fireOrderEvent(Parameters.symbol.get(id), OrderSide.COVER,Parameters.symbol.get(id).getMinsize(), highestHigh.get(id), highestHigh.get(id));
            }
            
        } 
        else if (notionalPosition.get(id) == 1){
            if(Parameters.symbol.get(id).getLastPrice()<lowestLow.get(id)||System.currentTimeMillis()>endDate.getTime()){
                notionalPosition.set(id, 0L);
                _fireOrderEvent(Parameters.symbol.get(id), OrderSide.SELL,Parameters.symbol.get(id).getMinsize(), lowestLow.get(id), lowestLow.get(id));
            }
        }        

        
 
    }

    private void _fireOrderEvent(SymbolBean s, OrderSide side, int size, double lmtprice, double triggerprice) {
        OrderEvent order = new OrderEvent(this, s, side, size, lmtprice, triggerprice);
        Iterator listeners = _listeners.iterator();
        while (listeners.hasNext()) {
            ((OrderListener) listeners.next()).orderReceived(order);
        }
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
}
