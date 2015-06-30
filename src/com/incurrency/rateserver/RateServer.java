/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.rateserver;

import com.incurrency.framework.Algorithm;
import com.incurrency.framework.BeanConnection;
import com.incurrency.framework.DateUtil;
import com.incurrency.framework.MainAlgorithm;
import com.incurrency.framework.Parameters;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.incurrency.framework.Utilities;
import java.io.PrintStream;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Properties;

/**
 * Generates ServerPubSub, Tick , TRIN initialise ServerPubSub passing the adrSymbols that
 * need to be tracked. ServerPubSub will immediately initiate polling market data in
 * snapshot mode. Streaming mode is currently not supported output is available
 * via static fields Advances, Declines, Tick Advances, Tick Declines, Advancing
 * Volume, Declining Volume, Tick Advancing Volume, Tick Declining Volume
 *
 * @author pankaj
 */
public class RateServer {

    private static int publishport;
    private static int responseport;
    public static com.incurrency.rateserver.ServerPubSub pubsub;
    private static final Logger logger = Logger.getLogger(ServerPubSub.class.getName());
    Date endDate;
    public static String country;
    public static boolean useRTVolume = false;
    //--common parameters required for all strategies
    Properties properties;
    public static String tickFutureMetric;
    public static String tickEquityMetric;
    public static String tickOptionMetric;
    public static String rtFutureMetric;
    public static String rtEquityMetric;
    public static String rtOptionMetric;
    public static boolean pushToCassandra = false;

    public RateServer(String parameterFile) {
        loadParameters(parameterFile);
        pubsub= new com.incurrency.rateserver.ServerPubSub(publishport);
        Thread t = new Thread(new ServerResponse(responseport));
        t.setName("ResponseServer");
        t.start();
        Timer closeProcessing = new Timer("Timer: Rates CloseProcessing");
        if (endDate != null) {
            closeProcessing.schedule(runPrintOrders, com.incurrency.framework.DateUtil.addSeconds(endDate, 600));
        }
    }

    private void loadParameters(String ParameterFile) {
        properties=Utilities.loadParameters(ParameterFile);
        String currDateStr = DateUtil.getFormatedDate("yyyyMMdd", new Date().getTime());
        String endDateStr;
        if (Boolean.getBoolean(properties.getProperty("historicaldata","false"))) {
            endDateStr = DateUtil.getFormatedDate("yyyyMMdd", new Date().getTime() + 24 * 60 * 60 * 1000);
        } else {
            endDateStr = currDateStr + " " + properties.getProperty("endtime");
        }
        if(Utilities.isDate(endDateStr, new SimpleDateFormat("yyyyMMdd HH:mm:ss"))){
            endDate = DateUtil.parseDate("yyyyMMdd HH:mm:ss", endDateStr, Algorithm.timeZone);
            if (new Date().compareTo(endDate) > 0) {
                //increase enddate by one calendar day
                endDate = DateUtil.addDays(endDate, 1);
            }
        }
        if(endDate!=null){
            MainAlgorithm.setCloseDate(endDate);
        }
        responseport=Integer.parseInt(properties.getProperty("responseport", "5555"));
        publishport=Integer.parseInt(properties.getProperty("publishport", "5556"));
        String cassandraIP=properties.getProperty("cassandraip","192.187.112.162");
        int cassandraPort=Integer.valueOf(properties.getProperty("cassandraport","4242"));
        String topic=properties.getProperty("topic","INR");
        useRTVolume = Boolean.parseBoolean(properties.getProperty("usertvolume","false").toString().trim());
        country = properties.getProperty("countrycode","INR");
        tickFutureMetric = properties.getProperty("tickfuturemetric");
        tickEquityMetric = properties.getProperty("tickequitymetric");
        tickOptionMetric = properties.getProperty("tickoptionmetric");
        rtFutureMetric = properties.getProperty("rtfuturemetric");
        rtEquityMetric = properties.getProperty("rtequitymetric");
        rtOptionMetric = properties.getProperty("rtoptionmetric");
        boolean realtime=Boolean.parseBoolean(properties.getProperty("realtime","false"));
        pushToCassandra = Boolean.parseBoolean(properties.getProperty("savetocassandra","false")) ;
        boolean savetocassandra=Boolean.parseBoolean(properties.getProperty("savetocassandra","false"));
        if (Parameters.connection.size()>0) {
            for (BeanConnection c : Parameters.connection) {
                c.getWrapper().cassandraIP=cassandraIP;
                c.getWrapper().cassandraPort=cassandraPort;
                c.getWrapper().topic=topic;
                c.getWrapper().saveToCassandra = pushToCassandra;
                c.getWrapper().tickEquityMetric = tickEquityMetric;
                c.getWrapper().tickFutureMetric = tickFutureMetric;
                c.getWrapper().tickOptionMetric = tickOptionMetric;
                c.getWrapper().rtEquityMetric = rtEquityMetric;
                c.getWrapper().rtFutureMetric = rtFutureMetric;
                c.getWrapper().rtOptionMetric = rtOptionMetric;
                c.getWrapper().realtime=realtime;
                c.getWrapper().saveToCassandra = savetocassandra;
                if (savetocassandra) {
                    try {
                        c.getWrapper().cassandraConnection = new Socket(cassandraIP, cassandraPort);
                        c.getWrapper().output = new PrintStream(c.getWrapper().cassandraConnection.getOutputStream());
                    } catch (Exception e) {
                        logger.log(Level.SEVERE, null, e);
                    }
                }
            }
        }
        logger.log(Level.INFO, "-----RateServer Parameters----");
        logger.log(Level.INFO, "end Time: {0}", endDate);
        logger.log(Level.INFO, "Country code: {0}", country);
        logger.log(Level.INFO, "Historical Mode: {0}", Boolean.parseBoolean(properties.getProperty("historical").toString()));
        logger.log(Level.INFO, "Use RT Volume: {0}", useRTVolume);
        logger.log(Level.INFO, "Save Data To Cassandra: {0}", pushToCassandra);
        logger.log(Level.INFO, "Tick Equity Metric: {0}", tickEquityMetric);
        logger.log(Level.INFO, "Tick Future Metric: {0}", tickFutureMetric);
        logger.log(Level.INFO, "Tick Option Metric {0}", tickOptionMetric);
        logger.log(Level.INFO, "RT Equity Metric: {0}", rtEquityMetric);
        logger.log(Level.INFO, "RT Future Metric: {0}", rtFutureMetric);
        logger.log(Level.INFO, "RT Option Metric {0}", rtOptionMetric);


    }
    TimerTask runPrintOrders = new TimerTask() {
        public void run() {
            closeSystem();
        }
    };

    private void closeSystem() {
        logger.log(Level.INFO, "EndTime Reached. Closing RateServer");
        System.exit(0);
    }
}
