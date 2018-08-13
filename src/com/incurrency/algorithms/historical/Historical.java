/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.algorithms.historical;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.incurrency.framework.*;
import com.incurrency.kairosresponse.QueryResponse;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.Socket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.rosuda.REngine.REXP;
import org.rosuda.REngine.Rserve.RConnection;

/**
 *
 * @author pankaj
 */
public class Historical {

    //  public static HashMap<String, String> input = new HashMap<>();
    private static final Logger logger = Logger.getLogger(Historical.class.getName());
    public static TimeZone timeZone;
    public static int runtime;
    public static HashMap<String, String> mysqlBarDestination = new HashMap<>();
    public static HashMap<String, String> cassandraBarDestination = new HashMap<>();
    public static HashMap<String, String> mysqlBarRequestDuration = new HashMap<>();
    public static HashMap<String, String> cassandraBarRequestDuration = new HashMap<>();
    public static HashMap<String, String> rBarRequestDuration = new HashMap<>();
    static String mysqlConnection;
    static String mysqlUserName;
    static String mysqlPassword;
    static String cassandraConnection;
    static String cassandraPort;
    static String kairosIP;
    static String kairosPort;
    static String rConnection;
    static String rfolder;
    static String rstartingdate;
    static String rendingdate;
    static boolean rbackfill;
    static int rnewfileperday;
    static String rscript;
    static String workingdirectory;
    static RConnection rcon;
    static DataCapture dc;
    static int tradingMinutes;
    static String openTime;
    static String closeTime;
    static int batch;
    public static String newline = System.getProperty("line.separator");
    static boolean scansplits = false;
    static double threshold = 0.7;
    static HashMap<String, Date> lastUpdateDate = new HashMap<>();
    private Properties properties;
    static String zerovolumeSymbols;

    /**
     * @param parameterFile the command line arguments
     */
    public Historical(String parameterFile) throws Exception {
        properties = Utilities.loadParameters(parameterFile);
        Historical.scansplits = true;
        zerovolumeSymbols = properties.getProperty("zerovolumesymbols", "");

        boolean done = false;
        boolean target_mysql = Boolean.parseBoolean(properties.getProperty("mysql", "false").toString().trim());
        boolean target_cassandra = Boolean.parseBoolean(properties.getProperty("cassandra", "false").toString().trim());
        boolean target_r = Boolean.parseBoolean(properties.getProperty("rdb", "false").toString().trim());
        Date shutdownDate = new Date();
        loadParameters(target_mysql, target_cassandra,target_r);
        if (runtime > 0) {
            shutdownDate = addMinutes(new Date(), runtime);
        } else {
            String shutdownDateString = properties.getProperty("shutdowndate");
            shutdownDate = addDays(new Date(), 7);

            if (shutdownDateString != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd HH:mm:ss");
                try {
                    shutdownDate = sdf.parse(shutdownDateString.toString().trim());
                } catch (Exception e) {
                }
            }
        }
        logger.log(Level.INFO, "103,ShutDownDate,{0}", new Object[]{shutdownDate});
        MainAlgorithm.setCloseDate(shutdownDate);
        //make requests
        HashMap<String, Boolean> barSizeProcessed = new HashMap<>();
        Connection mySQLConnect = null;
        PreparedStatement mySQLQuery = null;
        Socket cassandraConnect = null;
        if (mysqlConnection != null) {
            mySQLConnect = DriverManager.getConnection(Historical.mysqlConnection, Historical.mysqlUserName, Historical.mysqlPassword);
            mySQLQuery = mySQLConnect.prepareStatement("select date from ? where symbol=? order by date asc limit 1");
        } else if (cassandraConnection != null) {
            cassandraConnect = new Socket(Historical.cassandraConnection, Integer.valueOf(Historical.cassandraPort));
        } else if (rConnection!=null){
              rcon = new RConnection(Historical.rConnection);
              String command="setwd(\"" + workingdirectory + "\")";
              rcon.eval(command);
              REXP wd = rcon.eval("getwd()");
              System.out.println(wd.asString());
              command="source(\"" + rscript + "\")";
              rcon.eval(command);
        }
        //process mysql first    
        String barSize = null;
        Date endDate = new Date();
        Date specifiedEndDate=DateUtil.getFormattedDate(Historical.rendingdate+ " 23:59:00", "yyyyMMdd hh:mm:ss", Algorithm.timeZone);
        endDate=endDate.after(specifiedEndDate)?specifiedEndDate:endDate;
        Date startDate = null;
        int connectionCount = Parameters.connection.size();
        int i = 0; //i = symbols requested, used for identifying the beanconnection to use
        Thread t = new Thread();
        if (mysqlConnection != null) {
            for (String b : mysqlBarDestination.keySet()) {
                if (!barSizeProcessed.containsKey(b)) {
                    barSize = b;
                    dc = new DataCapture(mysqlBarDestination.get(b), batch);
                    for (BeanSymbol s : Parameters.symbol) {//for each symbol
                        //get start date
                        if (!done) {
                            mySQLQuery = mySQLConnect.prepareStatement("select date from " + mysqlBarDestination.get(b) + " where symbol=? order by date desc limit 1");
                            mySQLQuery.setString(1, s.getDisplayname());
                            ResultSet rs = mySQLQuery.executeQuery();
                            while (rs.next()) {
                                startDate = rs.getDate("date");
                                startDate = addSeconds(startDate, 1);
                            }
                            if (startDate == null) {//no data in database for symbol
                                if (b.equals("1sec")) {
                                    startDate = addDays(endDate, -180);
                                } else {
                                    startDate = addDays(endDate, -365);
                                }
                            }

                            while (t.isAlive()) {
                                Thread.sleep(10000);
                            }
                            long minutesToClose = (shutdownDate.getTime() - new Date().getTime()) / (1000);
                            long estimatedTime;
                            HistoricalBarsAll bar;
                            lastUpdateDate.put(s.getDisplayname().trim(), startDate);
                            t = new Thread(bar = new HistoricalBarsAll(barSize, startDate, endDate, s, tradingMinutes, openTime, closeTime, timeZone, Algorithm.holidays));
                            t.setName("Historical Bars:" + s.getDisplayname());
                            estimatedTime = bar.estimatedTime();

                            if (estimatedTime < minutesToClose) {
                                t.start();
                            } else {
                                done = true;
                            }
                            t.start();
                            startDate = null;
                        }
                    }
                }
            }
        } else if (cassandraConnection != null) {
            for (String b : cassandraBarDestination.keySet()) {
                if (!barSizeProcessed.containsKey(b)) {
                    barSize = b;
                    dc = new DataCapture(b, batch);
                    int j = -1;
                    for (BeanSymbol s : Parameters.symbol) {//for each symbol
                        j = j + 1;
                        endDate = new Date();
                        //get start date
                        if (!done) {
                            startDate = new Date(getLastTimeFromKairos(kairosIP, Utilities.getInt(kairosPort, 8085), s, cassandraBarDestination.get(b) + ".close"));
                            lastUpdateDate.put(s.getDisplayname().trim(), startDate);
                            if (s.getType().equals("FUT") || s.getType().equals("OPT")) {
                                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
                                Date expiration = sdf.parse(s.getExpiry());
                                if (expiration.before(endDate)) {
                                    endDate = expiration;
                                    endDate = addDays(endDate, 1);
                                    endDate = addSeconds(endDate, -1);//bring time to 23.59
                                }
                            }
                            if (startDate.getTime() != 0L) {
                                //increase startdate by barsize
                                startDate = adjustDate(startDate, barSize);
                            } else if (startDate.getTime() == 0L) {//no data in database for symbol
                                if (s.getType().equals("FUT") || s.getType().equals("OPT")) {
                                    SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
                                    Date expiration = sdf.parse(s.getExpiry());
                                    startDate = addDays(expiration, -90);
                                } else {
                                    if (b.equals("1sec")) {
                                        startDate = addDays(endDate, -180);
                                    } else {
                                        startDate = addDays(endDate, -365);
                                    }
                                }
                            }
                            int connectionid = i % connectionCount;
                            BeanConnection c = Parameters.connection.get(connectionid);
                            while (t.isAlive()) {
                                Thread.sleep(1000);
                            }
                            long minutesToClose = (shutdownDate.getTime() - new Date().getTime()) / (1000);
                            long estimatedTime;
                            HistoricalBarsAll bar;
                            t = new Thread(bar = new HistoricalBarsAll(barSize, startDate, endDate, s, tradingMinutes, openTime, closeTime, timeZone, Algorithm.holidays));
                            t.setName("Historical Bars:" + s.getDisplayname());
                            estimatedTime = bar.estimatedTime();
                            if (estimatedTime < minutesToClose) {
                                t.start();
                            } else {
                                done = true;
                            }
                        }
                    }
                }
            }
        }else if(rConnection!=null){
             for (String b : rBarRequestDuration.keySet()) {
                    barSize = b;
                    dc = new DataCapture(b, batch);
                    int j = -1;
                    for (BeanSymbol s : Parameters.symbol) {//for each symbol
                        j = j + 1;
                        endDate = new Date();
                        //get start date
                        if (!done) {
                            startDate = new Date(getLastTimeFromR(s,rstartingdate,rfolder,rnewfileperday,100,"historical.R" ));
                            if(!rbackfill){
                                Date requiredStart=DateUtil.getFormattedDate(Historical.rstartingdate, "yyyyMMdd", Algorithm.timeZone);
                                startDate=requiredStart.after(startDate)?requiredStart:startDate;
                            }                            
                            lastUpdateDate.put(s.getDisplayname().trim(), startDate);
                            if (s.getType().equals("FUT") || s.getType().equals("OPT")) {
                                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
                                Date expiration = sdf.parse(s.getExpiry());
                                if (expiration.before(endDate)) {
                                    endDate = expiration;
                                    endDate = addDays(endDate, 1);
                                    endDate = addSeconds(endDate, -1);//bring time to 23.59
                                }
                            }
                            if (startDate.getTime() > 0L) {
                                //increase startdate by barsize
                                startDate = adjustDate(startDate, barSize);
                            } else if (startDate.getTime() <= 0L) {//no data in database for symbol
                                if (s.getType().equals("FUT") || s.getType().equals("OPT")) {
                                    SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
                                    Date expiration = sdf.parse(s.getExpiry());
                                    startDate = addDays(expiration, -90);
                                } else {
                                    if (b.equals("1sec")) {
                                        startDate = addDays(endDate, -180);
                                    } else {
                                        startDate = addDays(endDate, -365);
                                    }
                                }
                            }
                            int connectionid = i % connectionCount;
                            BeanConnection c = Parameters.connection.get(connectionid);
                            while (t.isAlive()) {
                                Thread.sleep(1000);
                            }
                            long minutesToClose = (shutdownDate.getTime() - new Date().getTime()) / (1000);
                            long estimatedTime;
                            HistoricalBarsAll bar;
                            t = new Thread(bar = new HistoricalBarsAll(barSize, startDate, endDate, s, tradingMinutes, openTime, closeTime, timeZone, Algorithm.holidays));
                            t.setName("Historical Bars:" + s.getDisplayname());
                            estimatedTime = bar.estimatedTime();
                            if (estimatedTime < minutesToClose) {
                                t.start();
                            } else {
                                done = true;
                            }
                        }
                    }
                
            }
        }
        System.exit(0);

    }
   
     public static void writeToFile(String filename, TreeMap<Long, OHLCV> content) {
        try {
            File file = new File(filename.toUpperCase() + ".csv");

            //if file doesnt exists, then create it
            if (file.exists()) {
                file.delete();
            } else {
                file.createNewFile();
            }

            //true = append file
            SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd");
            SimpleDateFormat timeFormatter = new SimpleDateFormat("HH:mm:ss");

            FileWriter fileWritter = new FileWriter(file, true);
            BufferedWriter bufferWritter = new BufferedWriter(fileWritter);

            for (Map.Entry<Long, OHLCV> entry : content.entrySet()) {
                String dateString = getFormatedDate("yyyy-MM-dd", entry.getKey(), timeZone);
                String timeString = getFormatedDate("HH:mm:ss", entry.getKey(), timeZone);
                bufferWritter.write(dateString + "," + timeString + "," + filename.toUpperCase() + ","
                        + entry.getValue().getOpen() + "," + entry.getValue().getHigh() + "," + entry.getValue().getLow() + ","
                        + entry.getValue().getClose() + "," + entry.getValue().getVolume() + newline);
            }

            bufferWritter.close();
        } catch (IOException ex) {
        }
    }

    private void loadParameters(boolean mysql, boolean cassandra, boolean r) {
        timeZone = TimeZone.getTimeZone(Algorithm.timeZone);
        tradingMinutes = Integer.valueOf(properties.getProperty("tradingminutesinday", "375"));
        openTime = properties.getProperty("opentime", "9:15:00");
        closeTime = properties.getProperty("closetime", "15:30:00");
        batch = Integer.valueOf(properties.getProperty("batch", "500"));
        runtime = Integer.valueOf(properties.getProperty("runtime", "0"));
        if (mysql) {
            String barSizeAllSQL = properties.getProperty("sqlbarsize", "daily").toString().trim();
            String[] barSizeSQL = barSizeAllSQL.split(",");
            mysqlConnection = properties.getProperty("mysqlconnection", "127.0.0.1");
            mysqlUserName = properties.getProperty("mysqlusername", "user");
            mysqlPassword = properties.getProperty("mysqlpassword", "incurrency");
            for (String bar : barSizeSQL) {
                String destination = properties.getProperty("sql" + bar);
                if (destination != null) {
                    mysqlBarDestination.put(bar, destination);
                }
            }
            for (String bar : barSizeSQL) {
                String duration = properties.getProperty(bar);
                if (duration != null) {
                    mysqlBarRequestDuration.put(bar, duration);
                }
            }
        }

        if (cassandra) {
            String barSizeAllCass = properties.getProperty("cassandrabarsize", "daily").toString().trim();
            String[] barSizeCass = barSizeAllCass.split(",");
            cassandraConnection = properties.getProperty("cassandraconnection", "127.0.0.1");
            cassandraPort = properties.getProperty("cassandraport", "4242");
            kairosIP = properties.getProperty("kairosip", "127.0.0.1");
            kairosPort = properties.getProperty("kairosport", "8085");
            for (String bar : barSizeCass) {
                String destination = properties.getProperty("cassandra" + bar);
                if (destination != null) {
                    cassandraBarDestination.put(bar, destination);
                }
            }
            for (String bar : barSizeCass) {
                String duration = properties.getProperty(bar);
                if (duration != null) {
                    cassandraBarRequestDuration.put(bar, duration);
                }
            }
        }
        if(r){
            rConnection = properties.getProperty("rconnection", "127.0.0.1");
             String barSizeAllCass = properties.getProperty("rbarsize", "daily").toString().trim();
            String[] barSizeCass = barSizeAllCass.split(",");
            for (String bar : barSizeCass) {
                String duration = properties.getProperty(bar);
                if (duration != null) {
                    rBarRequestDuration.put(bar, duration);
                }
            }
            rfolder=properties.getProperty("rfolder", "daily").toString().trim();
            rnewfileperday=Integer.valueOf(properties.getProperty("newfileperday", "0"));
            String today=DateUtil.getFormatedDate("yyyyMMdd", new Date().getTime(), TimeZone.getTimeZone(Algorithm.timeZone));
            rstartingdate=properties.getProperty("rstartingdate", today).toString().trim();
            if(rstartingdate.isEmpty()){
                rstartingdate=today;
            }
            rendingdate=properties.getProperty("rendingdate", today).toString().trim();
            if(rendingdate.isEmpty()){
                rendingdate=today;
            }
            rscript=properties.getProperty("rscript","historical.R").toString().trim();
            workingdirectory=properties.getProperty("workingdirectory", "/home/psharma").toString().trim();
            rbackfill=Boolean.valueOf(properties.getProperty("rbackfill","false"));
        }

    }

    public static long getLastTimeFromKairos(String kairosIP, int kairosPort, BeanSymbol s, String metric) throws IOException {
        try {
            List<String> out = new ArrayList<>();
            HashMap<String, Object> param = new HashMap();
            param.put("TYPE", Boolean.FALSE);
            String[] names;
            if (s.getRight() != null) {
                names = new String[]{"symbol", "expiry", "strike", "option"};
            } else if (s.getExpiry() != null) {
                names = new String[]{"symbol", "expiry"};
            } else {
                names = new String[]{"symbol"};
            }
            String[] values;
            if (s.getRight() != null) {
                String formattedStrike = Utilities.formatDouble(Utilities.getDouble(s.getOption(), 0), new DecimalFormat("#.##"));
                values = new String[]{s.getExchangeSymbol().trim().toLowerCase(), s.getExpiry(), formattedStrike, s.getRight()};
            } else if (s.getExpiry() != null) {
                values = new String[]{s.getExchangeSymbol().trim().toLowerCase(), s.getExpiry()};
            } else {
                values = new String[]{s.getExchangeSymbol().trim().toLowerCase()};
            }

            HistoricalRequestJson request = new HistoricalRequestJson(metric,
                    names,
                    values,
                    null,
                    null,
                    null,
                    String.valueOf(0),
                    String.valueOf(new Date().getTime()), 1, "desc");
            //http://stackoverflow.com/questions/7181534/http-post-using-json-in-java
            //        String json_string = JsonWriter.objectToJson(request, param);
            Gson gson = new GsonBuilder().create();
            String json_string = gson.toJson(request);
            String response_json = Utilities.getJsonUsingPut("http://" + kairosIP + ":" + kairosPort + "/api/v1/datapoints/query", 0, json_string);
            QueryResponse response;
            //Type type = new com.google.common.reflect.TypeToken<QueryResponse>() {
            //}.getType();
            response = gson.fromJson(response_json, QueryResponse.class);
            //long time=response.getQueries().get(querysize-1).getResults().get(resultsize-1).getValues().get(valuesize-1).get(datapoints-1).longValue();
            long time = Double.valueOf(response.getQueries().get(0).getResults().get(0).getDataPoints().get(0).get(0).toString()).longValue();
            //long time=response.queries[0].results[0].values[0].time;
            return time;
        } catch (Exception e) {
            return 0;
        }
    }

    public static long getLastTimeFromR (BeanSymbol s,String endTime,String rfolder,int rnewfileperday,int lookback, String script){
        // if rnewfileperday==TRUE, get list of directories in rfolder
        try{
        String type=s.getDisplayname().split("_")[1].toLowerCase();
        String command="source(\"" + rscript + "\")";
              rcon.eval(command);
              command="getStartingTime(\""+rfolder+type+"/"+ "\",\""+s.getDisplayname()+"\",\""+endTime+"\",\""+rnewfileperday+"\",\""+lookback+"\")";
        REXP time;

        time=rcon.eval(command);
        long out=DateUtil.getFormattedDate(time.asString(), "yyyy-MM-dd HH:mm:ss", Algorithm.timeZone).getTime();
        return out;
        }catch (Exception e){
            return 0L;
        }
    }
    
    private static Date adjustDate(Date date, String barSize) {
        Date out = null;
        switch (barSize) {
            case "1min":
                out = addMinutes(date, 1);
                break;
            case "1sec":
                out = addSeconds(date, 1);
                break;
            case "daily":
                out = addDays(date, 1);
                break;
            default:
                break;
        }
        return out;
    }

//"start_absolute":1340875800000,"end_absolute":1403947920000,"cacheTime":0,"metrics":[{"name":"india.nse.equity.s1.1min.volume","tags":{"symbol":["nifty50"]},"group_by":[],"aggregators":[],"limit":1}]}
//{"metric":"india.nse.equity.s1.1min.volume","tag":{"symbol":"nifty"},"limit":1,"sortOrder":"asc"}
    public static long getTime(Integer year, Integer month, Integer date, Integer hour, Integer minute, Integer second, Integer millisecond) {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("Etc/UTC")); // locale-specific, set timezone to empty

        cal.set(Calendar.YEAR, year);
        cal.set(Calendar.MONTH, month);
        cal.set(Calendar.DATE, date);
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        cal.set(Calendar.SECOND, second);
        cal.set(Calendar.MILLISECOND, millisecond);
        return cal.getTimeInMillis();
    }

    public static Date addSeconds(Date date, int seconds) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.add(Calendar.SECOND, seconds); //minus number would decrement the days
        return cal.getTime();
    }

    public static Date addDays(Date date, int days) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.add(Calendar.DATE, days); //minus number would decrement the days
        return cal.getTime();
    }

    public static Date addMinutes(Date date, int minutes) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.add(Calendar.MINUTE, minutes); //minus number would decrement the days
        return cal.getTime();
    }

    public static String getFormatedDate(String format, long timeMS, TimeZone tmz) {
        SimpleDateFormat sdf = new SimpleDateFormat(format);
        sdf.setTimeZone(tmz);
        String date = sdf.format(new Date(timeMS));
        return date;
    }

    public static Date parseDate(String format, String date, TimeZone timeZone) {
        Date dt = null;
        try {

            SimpleDateFormat sdf1 = new SimpleDateFormat(format);
            sdf1.setTimeZone(timeZone);
            dt = sdf1.parse(date);

        } catch (Exception e) {
            logger.log(Level.INFO, "101", e);
        }
        return dt;
    }
}
