/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package TurtleTrading;

import incurrframework.Algorithm;
import incurrframework.DateUtil;
import incurrframework.OrderSide;
import incurrframework.Parameters;
import incurrframework.TradeEvent;
import incurrframework.TradeListner;
import java.beans.*;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

/**
 *
 * @author pankaj
 */
public class BeanGuds implements Serializable, TradeListner {

    public static final String PROP_SAMPLE_PROPERTY = "sampleProperty";
    private String sampleProperty;
    private PropertyChangeSupport propertySupport;
    private ArrayList<Double> standardDev = new <Double> ArrayList();  //algo parameter 
    private ArrayList<Double> low = new <Double> ArrayList();  //algo parameter 
    private ArrayList<Double> high = new <Double> ArrayList();  //algo parameter 
    private ArrayList<Double> lowThreshold = new <Double> ArrayList();  //algo parameter 
    private ArrayList<Double> highThreshold = new <Double> ArrayList();  //algo parameter 
    private MainAlgorithm m;
    private Date startDate;
    private Date endDate;
    private OrderPlacement ordManagement;
    private final static Logger logger = Logger.getLogger(Algorithm.class.getName());
    private ArrayList<Boolean> openPrice;
    private String exit;

    public BeanGuds(MainAlgorithm m) {
        this.m = m;
        propertySupport = new PropertyChangeSupport(this);
        Properties p = new Properties(System.getProperties());
        FileInputStream propFile;
        try {
            propFile = new FileInputStream("Guds.properties");
            try {
                p.load(propFile);
            } catch (IOException ex) {
                Logger.getLogger(BeanTurtle.class.getName()).log(Level.SEVERE, null, ex);
            }
        } catch (FileNotFoundException ex) {
            Logger.getLogger(BeanTurtle.class.getName()).log(Level.SEVERE, null, ex);
        }
        System.setProperties(p);
        String currDateStr = DateUtil.getFormatedDate("yyyyMMdd", Parameters.connection.get(0).getConnectionTime());
        String startDateStr = currDateStr + " " + System.getProperty("StartTime");
        String endDateStr = currDateStr + " " + System.getProperty("EndTime");
        String tickSize = System.getProperty("TickSize");
        startDate = DateUtil.parseDate("yyyyMMdd HH:mm:ss", startDateStr);
        endDate = DateUtil.parseDate("yyyyMMdd HH:mm:ss", endDateStr);
        exit = System.getProperty("Exit");
        if (endDate.compareTo(startDate) < 0 && new Date().compareTo(startDate) > 0) {
            //increase enddate by one calendar day
            endDate = DateUtil.addDays(endDate, 1); //system date is > start date time. Therefore we have not crossed the 12:00 am barrier
        } else if (endDate.compareTo(startDate) < 0 && new Date().compareTo(startDate) < 0) {
            startDate = DateUtil.addDays(startDate, -1); // we have moved beyond 12:00 am . adjust startdate to previous date
        }
        for (int i = 0; i < Parameters.symbol.size(); i++) {
            standardDev.add(Double.MAX_VALUE);
            low.add(Double.MIN_VALUE);
            high.add(Double.MAX_VALUE);
            lowThreshold.add(Double.MIN_VALUE);
            highThreshold.add(Double.MAX_VALUE);
//            histClose.put(Parameters.symbol.get(i).getSymbol()+"_FUT",new ArrayList<Double>());
//            histVolume.put(Parameters.symbol.get(i).getSymbol()+"_FUT",new ArrayList<Long>());

        }
        Parameters.addTradeListener(this);
        try {
            Class.forName("com.mysql.jdbc.Driver");
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(BeanGuds.class.getName()).log(Level.SEVERE, null, ex);
        }
        calculateSD();
        preOpenOrders();
    }

    private void calculateSD() {
        Connection connect = null;
        Statement statement = null;
        PreparedStatement preparedStatement = null;
        ResultSet rs = null;


        try {

            connect = DriverManager.getConnection("jdbc:mysql://localhost:3306/histdata", "root", "spark123");
            //statement = connect.createStatement();
            for (int j = 0; j < Parameters.symbol.size(); j++) {
                String name = Parameters.symbol.get(j).getSymbol() + "_FUT";
                preparedStatement = connect.prepareStatement("select * from dharasymb where name=? order by date asc");
                preparedStatement.setString(1, name);
                rs = preparedStatement.executeQuery();
                //parse and create one minute bars
                Date priorDate = null;
                Long volume = 0L;
                Double close = 0D;
                Double high = Double.MIN_VALUE;
                Double low = Double.MAX_VALUE;
                ArrayList<Double> returns = new ArrayList<Double>();
                ArrayList<Double> histclose = new ArrayList<Double>();
                ArrayList<Double> histlow = new ArrayList<Double>();
                ArrayList<Double> histhigh = new ArrayList<Double>();
                ArrayList<Long> histvolume = new ArrayList<Long>();
                System.out.println("Symbol:" + Parameters.symbol.get(j).getSymbol());
                while (rs.next()) {
                    priorDate = priorDate == null ? rs.getDate("date") : priorDate;
                    //String name = rs.getString("name");
                    Date date = rs.getDate("date");
                    Date datetime = rs.getTimestamp("date");
                    if (date.compareTo(priorDate) > 0 && date.compareTo(DateUtil.addDays(new Date(), -150)) > 0) {
                        //new bar has started
                        priorDate = date;
                        String formattedDate = DateUtil.getFormatedDate("yyyyMMdd hh:mm:ss", datetime.getTime());
                        histclose.add(close);
                        histlow.add(low);
                        histhigh.add(high);
                        if (histclose.size() > 1) {
                            returns.add((close - histclose.get(histclose.size() - 2)) / histclose.get(histclose.size() - 2));
                        }
                        histvolume.add(volume);
                        volume = rs.getLong("volume");

                    } else {
                        volume = volume + rs.getLong("volume");
                        close = rs.getDouble("tickclose");
                        high = rs.getDouble("high");
                        low = rs.getDouble("low");
                    }
                }
                rs.close();
                List<Double> sublist = (List) returns.subList(returns.size() - 90, returns.size());
                double[] sample = new double[sublist.size()];
                int i = 0;
                DescriptiveStatistics stats = new DescriptiveStatistics();
                for (double value : sublist) {
                    sample[i] = value;
                    stats.addValue(value);
                    i = i + 1;
                }
                standardDev.set(j, stats.getStandardDeviation());
                low = histlow.get(histlow.size() - 1);
                high = histhigh.get(histhigh.size() - 1);
                lowThreshold.set(j, low * (1 - standardDev.get(j)));
                highThreshold.set(j, high * (1 + standardDev.get(j)));
            }
        } catch (SQLException ex) {
            Logger.getLogger(BeanGuds.class.getName()).log(Level.SEVERE, null, ex);
        }


    }

    private void preOpenOrders(){
        
    }
    
    @Override
    public void tradeReceived(TradeEvent event) {
        int id = event.getSymbolID() - 1; //here symbolID is with zero base.
        double lastPrice = Parameters.symbol.get(id).getLastPrice();
        if (!openPrice.get(id)) { //do if this is the open price
            openPrice.set(id, Boolean.TRUE);
            //Short Signal
            if (lastPrice > highThreshold.get(id) || Parameters.symbol.get(id).getOpenPrice() > highThreshold.get(id)) {
                m.fireOrderEvent(Parameters.symbol.get(id), OrderSide.SHORT, Parameters.symbol.get(id).getMinsize(), Math.round(highThreshold.get(id)), 0, "GUDS", 3, exit);
            }
            //Buy Signal
            if (lastPrice < lowThreshold.get(id) || Parameters.symbol.get(id).getOpenPrice() < lowThreshold.get(id)) {
                m.fireOrderEvent(Parameters.symbol.get(id), OrderSide.BUY, Parameters.symbol.get(id).getMinsize(), Math.round(lowThreshold.get(id)), 0, "GUDS", 3, exit);
            }

        }

    }

    /**
     * @return the openPrice
     */
    public ArrayList<Boolean> getOpenPrice() {
        return openPrice;
    }

    /**
     * @param openPrice the openPrice to set
     */
    public void setOpenPrice(ArrayList<Boolean> openPrice) {
        this.openPrice = openPrice;
    }

    /**
     * @return the lowThreshold
     */
    public ArrayList<Double> getLowThreshold() {
        return lowThreshold;
    }

    /**
     * @param lowThreshold the lowThreshold to set
     */
    public void setLowThreshold(ArrayList<Double> lowThreshold) {
        this.lowThreshold = lowThreshold;
    }

    /**
     * @return the highThreshold
     */
    public ArrayList<Double> getHighThreshold() {
        return highThreshold;
    }

    /**
     * @param highThreshold the highThreshold to set
     */
    public void setHighThreshold(ArrayList<Double> highThreshold) {
        this.highThreshold = highThreshold;
    }

    /**
     * @return the m
     */
    public MainAlgorithm getM() {
        return m;
    }

    /**
     * @param m the m to set
     */
    public void setM(MainAlgorithm m) {
        this.m = m;
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
     * @return the endDate
     */
    public Date getEndDate() {
        return endDate;
    }

    /**
     * @param endDate the endDate to set
     */
    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }
}
