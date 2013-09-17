/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package TurtleTrading;

import incurrframework.Algorithm;
import incurrframework.BeanOHLC;
import incurrframework.DateUtil;
import incurrframework.EnumBarSize;
import incurrframework.HistoricalBarEvent;
import incurrframework.HistoricalBarListener;
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
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;

/**
 *
 * @author pankaj
 */
public class BeanGuds implements Serializable, HistoricalBarListener, TradeListner {
    
    public static final String PROP_SAMPLE_PROPERTY = "sampleProperty";
    private String sampleProperty;
    private PropertyChangeSupport propertySupport;
    private ArrayList<Double> standardDev = new <Double> ArrayList();  //algo parameter 
    private ArrayList<Double> low = new <Double> ArrayList();  //algo parameter 
    private ArrayList<Double> high = new <Double> ArrayList();  //algo parameter 
    private ArrayList<Double> lowThreshold = new <Double> ArrayList();  //algo parameter 
    private ArrayList<Double> highThreshold = new <Double> ArrayList();  //algo parameter 
    
    private Date startDate;
    private Date endDate;
    private OrderPlacement ordManagement;
    private final static Logger logger = Logger.getLogger(Algorithm.class.getName());
    private ArrayList<Boolean> openPrice;
    
    public BeanGuds() {
        propertySupport = new PropertyChangeSupport(this);
        Properties p = new Properties(System.getProperties());
        FileInputStream propFile;
            try {
                propFile = new FileInputStream("Algo.properties");
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
        String tickSize=System.getProperty("TickSize");
        startDate = DateUtil.parseDate("yyyyMMdd HH:mm:ss", startDateStr);
        endDate = DateUtil.parseDate("yyyyMMdd HH:mm:ss", endDateStr);
        if(endDate.compareTo(startDate)<0 && new Date().compareTo(startDate)>0){
        //increase enddate by one calendar day
            endDate=DateUtil.addDays(endDate, 1); //system date is > start date time. Therefore we have not crossed the 12:00 am barrier
    }
        else if(endDate.compareTo(startDate)<0 && new Date().compareTo(startDate)<0){
            startDate=DateUtil.addDays(startDate, -1); // we have moved beyond 12:00 am . adjust startdate to previous date
        }
            for (int i = 0; i < Parameters.symbol.size(); i++) {
            standardDev.add(Double.MAX_VALUE);
            low.add(Double.MIN_VALUE);
            high.add(Double.MAX_VALUE);
            lowThreshold.add(Double.MIN_VALUE);
            highThreshold.add(Double.MAX_VALUE);
            Parameters.symbol.get(i).getDailyBar().addHistoricalBarListener(this);
        }
        Parameters.addTradeListener(this);
    }
    
    public String getSampleProperty() {
        return sampleProperty;
    }
    
    public void setSampleProperty(String value) {
        String oldValue = sampleProperty;
        sampleProperty = value;
        propertySupport.firePropertyChange(PROP_SAMPLE_PROPERTY, oldValue, sampleProperty);
    }
    
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        propertySupport.addPropertyChangeListener(listener);
    }
    
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        propertySupport.removePropertyChangeListener(listener);
    }

    @Override
    public void barsReceived(HistoricalBarEvent event) {
       try {
            if (event.ohlc().getPeriodicity() == EnumBarSize.Daily && event.ohlc().getOpenTime() == 0L) {
                //Last Bar. Calculate standard deviation
                //1. Calculate Daily Returns
                ArrayList<Double> returns = new ArrayList<Double>();
                double priorClose = 0;
                for (Map.Entry<Long, BeanOHLC> entry : event.list().entrySet()) {
                    if (entry.getKey() == event.list().firstKey()) {
                        //first entry. Ignore.
                        priorClose = entry.getValue().getClose();
                    } else {
                        returns.add((entry.getValue().getClose() - priorClose) / priorClose);
                    }
                }
                //getsublist
                ArrayList<Double> sublist = (ArrayList) returns.subList(returns.size() - 90, returns.size());
                double[] sample = new double[sublist.size()];
                int i = 0;
                for (double value : sublist) {
                    sample[i] = value;
                    i = i + 1;
                }
                StandardDeviation std = new StandardDeviation();
                std.evaluate(sample);
                std.getResult();
                int id=event.getSymbol().getSerialno() - 1;
                standardDev.set(id, std.getResult());
                high.set(id,event.list().lastEntry().getValue().getHigh());
                low.set(id,event.list().lastEntry().getValue().getLow());
                lowThreshold.set(id, low.get(id)*(1-standardDev.get(id)));
                highThreshold.set(id, low.get(id)*(1+standardDev.get(id)));
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "{0} Symbol: {1}", new Object[]{e.toString(), event.getSymbol().getSymbol()});
        }
    }

    @Override
    public void tradeReceived(TradeEvent event) {
        int id = event.getSymbolID()-1; //here symbolID is with zero base.
        double lastPrice=Parameters.symbol.get(id).getLastPrice();
  //      double bidPrice=Parameters.symbol.get(id).getBidPrice();
  //      double askPrice=Parameters.symbol.get(id).getAskPrice();
        if(!openPrice.get(id)){ //do if this is the open price
            openPrice.set(id, Boolean.TRUE);
            //Short Signal
            if (lastPrice>highThreshold.get(id)){ 
                
            }
            //Buy Signal
             if (lastPrice<lowThreshold.get(id)){ 
                
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
}
