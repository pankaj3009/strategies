/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package TurtleTrading;

import incurrframework.Algorithm;
import incurrframework.DateUtil;
import incurrframework.Parameters;
import incurrframework.PendingHistoricalRequests;
import java.beans.*;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author pankaj
 */
public class BeanTurtle implements Serializable {

    /**
     * @return the tickSize
     */
    public  String getTickSize() {
        return tickSize;
    }

    /**
     * @param aTickSize the tickSize to set
     */
    public void setTickSize(String aTickSize) {
        tickSize = aTickSize;
    }

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
    private static Date startDate;
    private static Date endDate;
    private int channelDuration;
    private int regressionLookBack;
    private double volumeSlopeLongMultiplier;
    private static final Logger LOGGER = Logger.getLogger(Algorithm.class.getName());
    private static ConcurrentHashMap queue = new <Integer,PendingHistoricalRequests> ConcurrentHashMap();
    private static ConcurrentLinkedQueue queueHistRequests=new ConcurrentLinkedQueue(new ArrayList<PendingHistoricalRequests>());
    private static ArrayList<PendingHistoricalRequests>temp=new ArrayList<PendingHistoricalRequests>();
    private static HashMap<Integer,Integer> BarsCount=new HashMap(); 
    private  String tickSize;

    
    public BeanTurtle() {
        queueHistRequests=new ConcurrentLinkedQueue(temp);
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
        tickSize=System.getProperty("TickSize");
        startDate = DateUtil.parseDate("yyyyMMdd HH:mm:ss", startDateStr);
        endDate = DateUtil.parseDate("yyyyMMdd HH:mm:ss", endDateStr);
        if(endDate.compareTo(startDate)<0 && new Date().compareTo(startDate)>0){
        //increase enddate by one calendar day
            endDate=DateUtil.addDays(endDate, 1); //system date is > start date time. Therefore we have not crossed the 12:00 am barrier
    }
        else if(endDate.compareTo(startDate)<0 && new Date().compareTo(startDate)<0){
            startDate=DateUtil.addDays(startDate, -1); // we have moved beyond 12:00 am . adjust startdate to previous date
        }
        channelDuration = Integer.parseInt(System.getProperty("ChannelDuration"));
        volumeSlopeLongMultiplier = Double.parseDouble(System.getProperty("VolSlopeMultLong"));
        //volumeSlopeShortMultipler = Double.parseDouble(System.getProperty("VolSlopeMultLong"));
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

        }
    }

    
    /**
     * @return the LOGGER
     */
    public static Logger getLOGGER() {
        return LOGGER;
    }

    /**
     * @return the queue
     */
    public synchronized static ConcurrentHashMap getQueue() {
        return queue;
    }

    /**
     * @param aQueue the queue to set
     */
    public static void setQueue(ConcurrentHashMap aQueue) {
        queue = aQueue;
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

    /**
     * @return the temp
     */
    public static ArrayList<PendingHistoricalRequests> getTemp() {
        return temp;
    }

    /**
     * @param aTemp the temp to set
     */
    public static void setTemp(ArrayList<PendingHistoricalRequests> aTemp) {
        temp = aTemp;
    }

    /**
     * @return the BarsCount
     */
    public synchronized static HashMap<Integer,Integer> getBarsCount() {
        return BarsCount;
    }

    /**
     * @param aBarsCount the BarsCount to set
     */
    public static void setBarsCount(HashMap<Integer,Integer> aBarsCount) {
        BarsCount = aBarsCount;
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
     * @return the startDate
     */
    static public Date getStartDate() {
        return startDate;
    }

    /**
     * @param startDate the startDate to set
     */
    public void setStartDate(Date startDate) {
        startDate = startDate;
    }

    /**
     * @return the endDate
     */
    static public Date getEndDate() {
        return endDate;
    }

    /**
     * @param endDate the endDate to set
     */
    public void setEndDate(Date endDate) {
        endDate = endDate;
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
    

}
