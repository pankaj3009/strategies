/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.algorithms.others;

import com.incurrency.algorithms.turtle.BeanTurtle;
import com.incurrency.framework.MainAlgorithm;
import com.incurrency.framework.OrderPlacement;
import com.incurrency.framework.Algorithm;
import com.incurrency.framework.BeanSymbol;
import com.incurrency.framework.DateUtil;
import com.incurrency.framework.EnumOrderIntent;
import com.incurrency.framework.EnumOrderSide;
import com.incurrency.framework.Launch;
import com.incurrency.framework.Parameters;
import com.incurrency.framework.TradeEvent;
import com.incurrency.framework.TradeListener;
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
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Timer;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

/**
 *
 * @author pankaj
 */
public class BeanGuds implements Serializable, TradeListener {

    public static final String PROP_SAMPLE_PROPERTY = "sampleProperty";
    private String sampleProperty;
    private PropertyChangeSupport propertySupport;
    private ArrayList<Double> standardDev = new <Double> ArrayList();  //algo parameter 
    private ArrayList<Double> low = new <Double> ArrayList();  //algo parameter 
    private ArrayList<Double> high = new <Double> ArrayList();  //algo parameter 
    private ArrayList<Double> lowThreshold = new <Double> ArrayList();  //algo parameter 
    private ArrayList<Double> highThreshold = new <Double> ArrayList();  //algo parameter 
    private ArrayList<BeanSymbol> GUDSSymbols = new <BeanSymbol>ArrayList();
    private ArrayList<Boolean> luckyOrdersPlaced = new <Boolean>ArrayList();
    private MainAlgorithm m;
    private Date startDate;
    private Date endDate;
    private OrderPlacement ordManagement;
    private final static Logger logger = Logger.getLogger(Algorithm.class.getName());
    private ArrayList<Boolean> openPrice;
    private String exit;
    Timer preopenProcessing;
    private int maxOrderDuration;
    private int dynamicOrderDuration;
    private double maxSlippage;

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
        maxOrderDuration = Integer.parseInt(System.getProperty("MaxOrderDuration"));
        dynamicOrderDuration = Integer.parseInt(System.getProperty("DynamicOrderDuration"));
        maxSlippage = Double.parseDouble(System.getProperty("MaxSlippage"));
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
            GUDSSymbols.add(new BeanSymbol());
            luckyOrdersPlaced.add(Boolean.FALSE);

        }
        for (int i = 0; i < Parameters.connection.size(); i++) {
            Parameters.connection.get(i).getWrapper().addTradeListener(this);
        }
        try {
            Class.forName("com.mysql.jdbc.Driver");
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(BeanGuds.class.getName()).log(Level.SEVERE, null, ex);
        }
        calculateSD();
        preopenProcessing = new Timer();
        long t = m.getPreopenDate().getTime();
        Date tempDate = new Date(t + 1 * 60000);// process one minute after preopen time.
        preopenProcessing.schedule(new BeanGudsPreOpen(this), tempDate);
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
                List<Double> sublist = returns.size() > 90 ? returns.subList(returns.size() - 90, returns.size()) : null;
                if (sublist != null) {
                    double[] sample = new double[sublist.size()];
                    int i = 0;
                    DescriptiveStatistics stats = new DescriptiveStatistics();
                    for (double value : sublist) {
                        sample[i] = value;
                        stats.addValue(value);
                        i = i + 1;
                    }
                    getStandardDev().set(j, stats.getStandardDeviation());
                    low = histlow.get(histlow.size() - 1);
                    high = histhigh.get(histhigh.size() - 1);
                    lowThreshold.set(j, low * (1 - getStandardDev().get(j)));
                    highThreshold.set(j, high * (1 + getStandardDev().get(j)));
                    GUDSSymbols.set(j, Parameters.symbol.get(j));
                }
            }
        } catch (SQLException ex) {
            Logger.getLogger(BeanGuds.class.getName()).log(Level.SEVERE, null, ex);
        }


    }

    @Override
    public void tradeReceived(TradeEvent event) {
        int id = event.getSymbolID() - 1; //here symbolID is with zero base.
        double lastPrice = Parameters.symbol.get(id).getLastPrice();
        if (!openPrice.get(id)) { //do if this is the open price
            openPrice.set(id, Boolean.TRUE);
            //if(!luckyOrdersPlaced.get(id)){
            if (true) {
                //Short Signal
                if (lastPrice > highThreshold.get(id) || Parameters.symbol.get(id).getOpenPrice() > highThreshold.get(id)) {
                    Launch.algo.getParamTurtle().getOms().tes.fireOrderEvent(-1, -1, Parameters.symbol.get(id), EnumOrderSide.SHORT, Parameters.symbol.get(id).getMinsize(), Math.ceil(highThreshold.get(id) * 20) / 20, 0, "GUDS", 3, exit, EnumOrderIntent.Init, maxOrderDuration, dynamicOrderDuration, maxSlippage);
                } //Buy Signal
                else if (lastPrice < lowThreshold.get(id) || Parameters.symbol.get(id).getOpenPrice() < lowThreshold.get(id)) {
                    Launch.algo.getParamTurtle().getOms().tes.fireOrderEvent(-1, -1, Parameters.symbol.get(id), EnumOrderSide.BUY, Parameters.symbol.get(id).getMinsize(), Math.ceil(lowThreshold.get(id) * 20) / 20, 0, "GUDS", 3, exit, EnumOrderIntent.Init, maxOrderDuration, dynamicOrderDuration, maxSlippage);
                }
            } else if (!luckyOrdersPlaced.get(id)) {
                //amend orders and replace
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

    /**
     * @return the GUDSSymbols
     */
    public ArrayList<BeanSymbol> getGUDSSymbols() {
        return GUDSSymbols;
    }

    /**
     * @param GUDSSymbols the GUDSSymbols to set
     */
    public void setGUDSSymbols(ArrayList<BeanSymbol> GUDSSymbols) {
        this.GUDSSymbols = GUDSSymbols;
    }

    /**
     * @return the standardDev
     */
    public ArrayList<Double> getStandardDev() {
        return standardDev;
    }

    /**
     * @param standardDev the standardDev to set
     */
    public void setStandardDev(ArrayList<Double> standardDev) {
        this.standardDev = standardDev;
    }

    /**
     * @return the luckyOrdersPlaced
     */
    public ArrayList<Boolean> getLuckyOrdersPlaced() {
        return luckyOrdersPlaced;
    }

    /**
     * @param luckyOrdersPlaced the luckyOrdersPlaced to set
     */
    public void setLuckyOrdersPlaced(ArrayList<Boolean> luckyOrdersPlaced) {
        this.luckyOrdersPlaced = luckyOrdersPlaced;
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
     * @return the maxSlippage
     */
    public double getMaxSlippage() {
        return maxSlippage;
    }

    /**
     * @param maxSlippage the maxSlippage to set
     */
    public void setMaxSlippage(double maxSlippage) {
        this.maxSlippage = maxSlippage;
    }
}
