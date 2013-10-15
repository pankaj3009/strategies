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
public class MainAlgorithm extends Algorithm  {

    /**
     * @return the param
     */
    public MarketData m;
    public final static Logger LOGGER = Logger.getLogger(Algorithm.class.getName());
    public final static Logger logger=Logger.getLogger("KeyParameters");
    private BeanTurtle paramTurtle;
    private BeanGuds paramGuds;
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
        paramTurtle = new BeanTurtle(this);
        //paramGuds = new BeanGuds(this);
        ordManagement = new OrderPlacement(this);


        //Attempt realtime bars in a new thread
        createAndShowGUI(this);
        Thread t = new Thread(new HistoricalBars());
        t.setName("Historical Bars");
        t.start();
        t.join();

        new RealTimeBars(getParamTurtle());
        //BoilerPlate Ends

        
        LOGGER.log(Level.FINEST, ",Symbol" + "," + "BarNo" + "," + "HighestHigh" + "," + "LowestLow" + "," + "LastPrice" + "," + "Volume" + "," + "CumulativeVol" + "," + "VolumeSlope" + "," + "MinSlopeReqd" + "," + "MA" + "," + "LongVolume" + "," + "ShortVolume" + "," + "DateTime" + ","
                + "ruleHighestHigh" + "," + "ruleCumVolumeLong" + "," + "ruleSlopeLong" + "," + "ruleVolumeLong" + "," + "ruleLowestLow" + ","
                + "ruleCumVolumeShort" + "," + "ruleSlopeShort" + "," + "ruleVolumeShort");

        

    }
 
    public void fireOrderEvent(BeanSymbol s, OrderSide side, int size, double lmtprice, double triggerprice, String ordReference, int expireTime) {
        OrderEvent order = new OrderEvent(this, s, side, size, lmtprice, triggerprice,ordReference,expireTime);
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
        frame1.setVisible(true);
        
        JFrame frame2 = new JFrame("Missed Orders");
        frame2.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        //Create and set up the content pane.
        GUIMissedOrders newContentPane2 = new GUIMissedOrders(m);
        newContentPane2.setOpaque(true); //content panes must be opaque
        frame2.setContentPane(newContentPane2);
        //Display the window.
        frame2.pack();
        frame2.setVisible(true);
        
        JFrame frame3 = new JFrame("Orders In Progress");
        frame3.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        //Create and set up the content pane.
        GUIInProgressOrders newContentPane3 = new GUIInProgressOrders(m);
        newContentPane3.setOpaque(true); //content panes must be opaque
        frame3.setContentPane(newContentPane3);
        //Display the window.
        frame3.pack();
        frame3.setVisible(true);
  
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
}
