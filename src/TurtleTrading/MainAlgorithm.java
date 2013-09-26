/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package TurtleTrading;

import incurrframework.*;
import java.util.List;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.SortedMap;
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
public class MainAlgorithm extends Algorithm implements HistoricalBarListener, TradeListner {

    /**
     * @return the param
     */
    public BeanTurtle getParam() {
        return param;
    }

    /**
     * @param aParam the param to set
     */
    public void setParam(BeanTurtle aParam) {
        param = aParam;
    }
    public MarketData m;
    public final static Logger LOGGER = Logger.getLogger(Algorithm.class.getName());
    public final static Logger logger=Logger.getLogger("KeyParameters");
    private BeanTurtle param;
    private OrderPlacement ordManagement;

    public MainAlgorithm(List<String> args) throws Exception {
        super(args); //this initializes the connection and symbols

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
            System.out.print("ContractDetails Requested:" + s.getSymbol());
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
                allocatedCapacity = allocatedCapacity + Math.min(count, connectionCapacity);
                count = count - Math.min(count, connectionCapacity);
                m.start();
            }
        }

        //ordManagement=new OrderPlacement(this);
        //initialize listners
        param = new BeanTurtle();
        ordManagement = new OrderPlacement(this);
        for (int i = 0; i < Parameters.symbol.size(); i++) {
            Parameters.symbol.get(i).getOneMinuteBar().addHistoricalBarListener(this);
            Parameters.symbol.get(i).getDailyBar().addHistoricalBarListener(this);
        }
        Parameters.addTradeListener(this);

        //Attempt realtime bars in a new thread

        Thread t = new Thread(new HistoricalBars());
        t.setName("Historical Bars");
        t.start();
        t.join();

        new RealTimeBars(getParam());
        //BoilerPlate Ends
        FileHandler fileHandler = new FileHandler("myLogFile");
        logger.addHandler(fileHandler);
        logger.setUseParentHandlers(false);
        
        LOGGER.log(Level.FINEST, ",Symbol" + "," + "BarNo" + "," + "HighestHigh" + "," + "LowestLow" + "," + "LastPrice" + "," + "Volume" + "," + "CumulativeVol" + "," + "VolumeSlope" + "," + "MinSlopeReqd" + "," + "MA" + "," + "LongVolume" + "," + "ShortVolume" + "," + "DateTime" + ","
                + "ruleHighestHigh" + "," + "ruleCumVolumeLong" + "," + "ruleSlopeLong" + "," + "ruleVolumeLong" + "," + "ruleLowestLow" + ","
                + "ruleCumVolumeShort" + "," + "ruleSlopeShort" + "," + "ruleVolumeShort");

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
            if (event.ohlc().getPeriodicity() == EnumBarSize.OneMin) {
                int id = event.getSymbol().getSerialno() - 1;
                getParam().getClose().set(id, event.ohlc().getClose());
                int barno = event.barNumber();
                LOGGER.log(Level.FINEST, "Bar No:{0}, Date={1}, Symbol:{2},FirstBarTime:{3}, LastBarTime:{4}, LastKey-FirstKey:{5}",
                        new Object[]{barno, DateUtil.getFormatedDate("yyyyMMdd HH:mm:ss", event.ohlc().getOpenTime()), Parameters.symbol.get(id).getSymbol(), DateUtil.getFormatedDate("yyyyMMdd HH:mm:ss", event.list().firstKey()), DateUtil.getFormatedDate("yyyyMMdd HH:mm:ss", event.list().lastKey()), (event.list().lastKey() - event.list().firstKey()) / (1000 * 60)});
                //Set cumVolume
                SortedMap<Long, BeanOHLC> temp = new TreeMap<Long, BeanOHLC>();
                int cumVolumeStartSize = getParam().getCumVolume().get(id).size();
                //           LOGGER.log(Level.INFO, "CumVolume.get(id).size()={0}", size);
                //check if bars are complete. If bars are not complete, send add to pending requests and exit.
                String startTime = System.getProperty("StartTime");
                SimpleDateFormat sdfDate = new SimpleDateFormat("HH:mm:ss");//dd/MM/yyyy
                String firstBarTime = sdfDate.format(event.list().firstEntry().getKey());
                //         String firstBarTime=DateUtil.toTimeString(event.list().firstEntry().getKey());
                boolean exclude = false; //this excludes cumvol calculation if its already calculated in the first loop.
                if (!firstBarTime.contains(startTime)) {
                    startTime = DateUtil.getFormatedDate("yyyyMMdd HH:mm:ss", event.list().firstEntry().getKey());
                    PendingHistoricalRequests temphistReq = new PendingHistoricalRequests(event.getSymbol().getSerialno(), startTime, "2 D", "1 min");
                    PendingHistoricalRequests histReq = getParam().getQueue().get(event.getSymbol().getSerialno()) == null ? temphistReq : (PendingHistoricalRequests) getParam().getQueue().get(event.getSymbol().getSerialno());
                    // if temphistReq.status==true AND 1 minute bars are complete, we should never have hit this loop.   
                    //if we have still hit this loop, its safe to assume that there was a race condition, and we need to re-request historical bars
                    //PendingHistoricalRequests histReq=temphistReq;
                    getParam().getQueue().put(event.getSymbol().getSerialno(), histReq);
                    return;
                } else if (event.list().size() - 1 == (event.list().lastKey() - event.list().firstKey()) / (1000 * 60)) {
                    BeanTurtle.getBarsCount().put(id + 1, 1);
                    if ((barno >= 2) && (cumVolumeStartSize < barno - 1)) {
                        LOGGER.log(Level.FINEST, "Setting Cumulative Vol in Loop 1. Bar No:{0}, cumVolumeStartSize={1}, Symbol:{2}", new Object[]{barno, cumVolumeStartSize, Parameters.symbol.get(id).getSymbol()});
                        //we have cumVolume from earlier bars that is not populated. Populate these
                        //int cumVolIndex=cumVolume.get(id).size()-1;
                        double priorClose = 0;
                        int i = 0;
                        for (Map.Entry<Long, BeanOHLC> entry : event.list().entrySet()) {
                            BeanOHLC OHLC = entry.getValue();
                            if (OHLC.getClose() > priorClose && i > 0) {
                                long tempVol = getParam().getCumVolume().get(id).get(i - 1) + OHLC.getVolume();
                                getParam().getCumVolume().get(id).add(tempVol);
                            } else if (OHLC.getClose() < priorClose && i > 0) {
                                long tempVol = getParam().getCumVolume().get(id).get(i - 1) - OHLC.getVolume();
                                getParam().getCumVolume().get(id).add(tempVol);
                            } else if (OHLC.getClose() == priorClose && i > 0) {
                                long tempVol = getParam().getCumVolume().get(id).get(i - 1);
                                getParam().getCumVolume().get(id).add(tempVol);
                            }
                            priorClose = OHLC.getClose();
                            i = i + 1;
                        }
                        exclude = true;
                    }


                } else {
                    return;
                }

                //the key = symbol id.
                //check if there are OHLC bars created that dont have a cumulative volume.

                if (barno == getParam().getCumVolume().get(id).size() + 1 && !exclude) {
                    int ref = barno - 1;
                    LOGGER.log(Level.FINEST, "Setting Cumulative Vol in Loop 2. Bar No:{0}, cumVolumeStartSize={1}, Symbol:{2}", new Object[]{barno, cumVolumeStartSize, Parameters.symbol.get(id).getSymbol()});
                    BeanOHLC OHLC = event.list().lastEntry().getValue();
                    Long OHLCPriorKey = event.list().lowerKey(event.list().lastKey());
                    BeanOHLC OHLCPrior = event.list().get(OHLCPriorKey);
                    if (OHLC.getClose() > OHLCPrior.getClose()) {
                        long tempVol = getParam().getCumVolume().get(id).get(ref - 1) + OHLC.getVolume();
                        getParam().getCumVolume().get(id).add(tempVol);
                    } else if (OHLC.getClose() < OHLCPrior.getClose()) {
                        long tempVol = getParam().getCumVolume().get(id).get(ref - 1) - OHLC.getVolume();
                        getParam().getCumVolume().get(id).add(tempVol);
                    } else if (OHLC.getClose() == OHLCPrior.getClose()) {
                        long tempVol = getParam().getCumVolume().get(id).get(ref - 1);
                        getParam().getCumVolume().get(id).add(tempVol);
                    }
                }

                int size1 = getParam().getCumVolume().get(id).size();
                LOGGER.log(Level.FINEST, "CumVolume Bars after Loop 2. Bar No:{0}, cumVolumeEndSize={1}, Symbol:{2}", new Object[]{barno, size1, Parameters.symbol.get(id).getSymbol()});
                if (getParam().getCumVolume().get(id).size() < event.barNumber()) {
                    //JOptionPane.showMessageDialog (null, "Error" ); 
                    LOGGER.log(Level.FINEST, "Error. Bars:{0}, cumVolumeEndSize={1}, Symbol:{2}", new Object[]{barno, size1, Parameters.symbol.get(id).getSymbol()});

                }
                getParam().getVolume().set(id, event.ohlc().getVolume());
//            LOGGER.log(Level.INFO, "Volume set to:{0}", Volume.get(id));
                //Set Highest High and Lowest Low

                if (event.barNumber() >= getParam().getChannelDuration()) {
                    temp = (SortedMap<Long, BeanOHLC>) event.list().subMap(event.ohlc().getOpenTime() - getParam().getChannelDuration() * 60 * 1000 + 1, event.ohlc().getOpenTime() + 1);
                    double HH = 0;
                    double LL = Double.MAX_VALUE;
                    for (Map.Entry<Long, BeanOHLC> entry : temp.entrySet()) {
                        HH = HH > entry.getValue().getHigh() ? HH : entry.getValue().getHigh();
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
                    getParam().getHighestHigh().set(id, HH);
                    getParam().getLowestLow().set(id, LL);
                }
                //Set Slope
                List<Long> tempCumVolume = new ArrayList();
                if (event.barNumber() >= getParam().getRegressionLookBack()) {
                    tempCumVolume = (List<Long>) getParam().getCumVolume().get(id).subList(event.barNumber() - getParam().getRegressionLookBack(), event.barNumber());
                    SimpleRegression regression = new SimpleRegression();
                    int itr = tempCumVolume.size();
                    double i = 0;
                    while (i < itr) {
                        regression.addData(i + 1, (Long) tempCumVolume.get((int) i));
                        i = i + 1;
                    }
                    double tempSlope = Double.isNaN(regression.getSlope()) == true ? 0D : regression.getSlope();
                    getParam().getSlope().set(id, tempSlope);
                }
                //set barupdown count
                 if(getParam().getBreachUpInBar().get(id)){
                     int breachup=getParam().getBreachUp().get(id);
                     getParam().getBreachUp().set(id, breachup+1);
                     getParam().getBreachUpInBar().set(id, false);
                 } else if(getParam().getBreachDownInBar().get(id)){
                     int breachdown=getParam().getBreachUp().get(id);
                     getParam().getBreachDown().set(id, breachdown+1);
                     getParam().getBreachDownInBar().set(id, false);
                 }
                //set MA of volume
                if (event.barNumber() >= getParam().getChannelDuration() - 1) {
                    temp = (SortedMap<Long, BeanOHLC>) event.list().subMap(event.ohlc().getOpenTime() - (getParam().getChannelDuration() - 1) * 60 * 1000 + 1, event.ohlc().getOpenTime() + 1);
                    DescriptiveStatistics stats = new DescriptiveStatistics();
                    for (Map.Entry<Long, BeanOHLC> entry : temp.entrySet()) {
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
                    getParam().getVolumeMA().set(id, stats.getMean());
                    
                }
            logger.log(Level.INFO,"{0},{1},{2},{3},{4},{5},{6},{7},{8},{9},{10}",new Object[]{
            Parameters.symbol.get(id).getSymbol(),
            sdfDate.format(event.list().lastEntry().getKey()),
            getParam().getHighestHigh().get(id).toString(),
            getParam().getLowestLow().get(id).toString(),
            getParam().getCumVolume().get(id).get(event.barNumber()-1).toString(),
            String.valueOf(0.05*Double.parseDouble(Parameters.symbol.get(id).getAdditionalInput())).replace(",", ""),
            String.valueOf(Double.parseDouble(Parameters.symbol.get(id).getAdditionalInput())).replace(",", ""),
            getParam().getSlope().get(id).toString(),
            String.valueOf(Double.parseDouble(Parameters.symbol.get(id).getAdditionalInput()) * getParam().getVolumeSlopeLongMultiplier() / 375).replace(",", ""),
            getParam().getVolume().get(id).toString(),
            getParam().getVolumeMA().get(id).toString()
                    });
            
            } else if (event.ohlc().getPeriodicity() == EnumBarSize.Daily) {
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
            LOGGER.log(Level.SEVERE, "{0} Symbol: {1}", new Object[]{e.toString(), event.getSymbol().getSymbol()});
        }
    }

    @Override
    public synchronized void tradeReceived(TradeEvent event) {
        try {
            int id = event.getSymbolID(); //here symbolID is with zero base.
            boolean ruleHighestHigh = Parameters.symbol.get(id).getLastPrice() > getParam().getHighestHigh().get(id);
            boolean ruleLowestLow = Parameters.symbol.get(id).getLastPrice() < getParam().getLowestLow().get(id);
            //boolean ruleCumVolumeLong = getParam().getCumVolume().get(id).get(getParam().getCumVolume().get(id).size() - 1) >= getParam().getLongVolume().get(id);
            boolean ruleCumVolumeLong = getParam().getCumVolume().get(id).get(getParam().getCumVolume().get(id).size() - 1) >=0.05* Double.parseDouble(Parameters.symbol.get(id).getAdditionalInput());
           // boolean ruleCumVolumeShort = getParam().getCumVolume().get(id).get(getParam().getCumVolume().get(id).size() - 1) <= -param.getShortVolume().get(id);
            boolean ruleCumVolumeShort = getParam().getCumVolume().get(id).get(getParam().getCumVolume().get(id).size() - 1) <= Double.parseDouble(Parameters.symbol.get(id).getAdditionalInput());
            boolean ruleSlopeLong = getParam().getSlope().get(id) > Double.parseDouble(Parameters.symbol.get(id).getAdditionalInput()) * getParam().getVolumeSlopeLongMultiplier() / 375;
            boolean ruleSlopeShort = getParam().getSlope().get(id) < -Double.parseDouble(Parameters.symbol.get(id).getAdditionalInput()) * getParam().getVolumeSlopeLongMultiplier() / 375;
            boolean ruleVolumeLong = getParam().getVolume().get(id) > getParam().getVolumeMA().get(id);
            boolean ruleVolumeShort = getParam().getVolume().get(id) > 2 * getParam().getVolumeMA().get(id);

            if(ruleHighestHigh){
                getParam().getBreachUpInBar().set(id, true);
            } else if(ruleLowestLow){
                getParam().getBreachDownInBar().set(id, true);
            }
            
            LOGGER.log(Level.FINEST, "," + "{0},{1},{2},{3},{4},{5},{6},{7},{8},{9},{10},{11},{12},{13},{14},{15},{16},{17},{18},{19},{20}", new Object[]{
                Parameters.symbol.get(id).getSymbol(), String.valueOf(getParam().getCumVolume().get(id).size()), getParam().getHighestHigh().get(id).toString(), getParam().getLowestLow().get(id).toString(), String.valueOf(Parameters.symbol.get(id).getLastPrice()), getParam().getVolume().get(id).toString(), getParam().getCumVolume().get(id).get(getParam().getCumVolume().get(id).size() - 1).toString(), getParam().getSlope().get(id).toString(), String.valueOf(Double.parseDouble(Parameters.symbol.get(id).getAdditionalInput()) * getParam().getVolumeSlopeLongMultiplier() / 375), getParam().getVolumeMA().get(id).toString(), getParam().getLongVolume().get(id).toString(), getParam().getShortVolume().get(id).toString(), DateUtil.getFormatedDate("yyyyMMdd HH:mm:ss z", Parameters.symbol.get(id).getLastPriceTime()), ruleHighestHigh, ruleCumVolumeLong, ruleSlopeLong, ruleVolumeLong, ruleLowestLow, ruleCumVolumeShort, ruleSlopeShort, ruleVolumeShort
            });


           if (getParam().getNotionalPosition().get(id) == 0 && getParam().getCumVolume().get(id).size() >= getParam().getChannelDuration()) {
            if (ruleHighestHigh && ruleCumVolumeLong && ruleSlopeLong && ruleVolumeLong && getParam().getEndDate().compareTo(new Date()) > 0) {
                //Buy Condition
                    getParam().getNotionalPosition().set(id, 1L);
                    LOGGER.log(Level.INFO, "Method:{0},Buy. Symbol:{1},LL:{2},LastPrice:{3},HH{4},Slope:{5},SlopeThreshold:{6},Volume:{7},VolumeMA:{8}",
                       new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(), Parameters.symbol.get(id).getSymbol(), getParam().getLowestLow().get(id).toString(), Parameters.symbol.get(id).getLastPrice(), getParam().getHighestHigh().get(id).toString(), getParam().getSlope().get(id).toString(), String.valueOf(Double.parseDouble(Parameters.symbol.get(id).getAdditionalInput()) * getParam().getVolumeSlopeLongMultiplier() / 375), getParam().getVolume().get(id).toString(), getParam().getVolumeMA().get(id).toString()
                    });
                    _fireOrderEvent(Parameters.symbol.get(id), OrderSide.BUY, Parameters.symbol.get(id).getMinsize(), getParam().getHighestHigh().get(id), 0);
                } else if (ruleLowestLow && ruleCumVolumeShort && ruleSlopeShort && ruleVolumeShort && getParam().getEndDate().compareTo(new Date()) > 0) {
                    //Short condition
                    getParam().getNotionalPosition().set(id, -1L);
                    LOGGER.log(Level.INFO, "Method:{0},Short. Symbol:{1},LL:{2},LastPrice:{3},HH{4},Slope:{5},SlopeThreshold:{6},Volume:{7},VolumeMA:{8}",
                            new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(), Parameters.symbol.get(id).getSymbol(), getParam().getLowestLow().get(id).toString(), Parameters.symbol.get(id).getLastPrice(), getParam().getHighestHigh().get(id).toString(), getParam().getSlope().get(id).toString(), String.valueOf(Double.parseDouble(Parameters.symbol.get(id).getAdditionalInput()) * getParam().getVolumeSlopeLongMultiplier() / 375), getParam().getVolume().get(id).toString(), getParam().getVolumeMA().get(id).toString()
                    });
                    _fireOrderEvent(Parameters.symbol.get(id), OrderSide.SHORT, Parameters.symbol.get(id).getMinsize(), getParam().getLowestLow().get(id), 0);
                }
            } else if (getParam().getNotionalPosition().get(id) == -1) {
                if (ruleHighestHigh || System.currentTimeMillis() > getParam().getEndDate().getTime()) {
                    getParam().getNotionalPosition().set(id, 0L);
                    LOGGER.log(Level.INFO, "Method:{0},Cover.Symbol:{1},LL:{2},LastPrice:{3},HH{4},Slope:{5},SlopeThreshold:{6},Volume:{7},VolumeMA:{8}",
                            new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(), Parameters.symbol.get(id).getSymbol(), getParam().getLowestLow().get(id).toString(), Parameters.symbol.get(id).getLastPrice(), getParam().getHighestHigh().get(id).toString(), getParam().getSlope().get(id).toString(), String.valueOf(Double.parseDouble(Parameters.symbol.get(id).getAdditionalInput()) * getParam().getVolumeSlopeLongMultiplier() / 375), getParam().getVolume().get(id).toString(), getParam().getVolumeMA().get(id).toString()
                    });
                    _fireOrderEvent(Parameters.symbol.get(id), OrderSide.COVER, Parameters.symbol.get(id).getMinsize(), Parameters.symbol.get(id).getLastPrice(), Parameters.symbol.get(id).getLastPrice());
                }

            } else if (getParam().getNotionalPosition().get(id) == 1) {
                if (ruleLowestLow || System.currentTimeMillis() > getParam().getEndDate().getTime()) {
                    getParam().getNotionalPosition().set(id, 0L);
                    LOGGER.log(Level.INFO, "Method:{0},Sell.Symbol:{1},LL:{2},LastPrice:{3},HH{4},Slope:{5},SlopeThreshold:{6},Volume:{7},VolumeMA:{8}",
                            new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(), Parameters.symbol.get(id).getSymbol(), getParam().getLowestLow().get(id).toString(), Parameters.symbol.get(id).getLastPrice(), getParam().getHighestHigh().get(id).toString(), getParam().getSlope().get(id).toString(), String.valueOf(Double.parseDouble(Parameters.symbol.get(id).getAdditionalInput()) * getParam().getVolumeSlopeLongMultiplier() / 375), getParam().getVolume().get(id).toString(), getParam().getVolumeMA().get(id).toString()
                    });
                    _fireOrderEvent(Parameters.symbol.get(id), OrderSide.SELL, Parameters.symbol.get(id).getMinsize(), Parameters.symbol.get(id).getLastPrice(), Parameters.symbol.get(id).getLastPrice());
                }
            }



        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, e.toString());
        }
    }

    private void _fireOrderEvent(BeanSymbol s, OrderSide side, int size, double lmtprice, double triggerprice) {
        if(getParam().getExposure()!=0){
            size=(int) (getParam().getExposure()/s.getLastPrice());
        }
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
}
