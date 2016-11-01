/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.algorithms.historical;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.incurrency.framework.*;
import com.incurrency.kairosresponse.KairosResponse;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.SortedMap;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 *
 * @author pankaj
 */
public class Historical {

    //  public static HashMap<String, String> input = new HashMap<>();
    private static final Logger logger = Logger.getLogger(Historical.class.getName());
    public static TimeZone timeZone;
    public static int runtime;
    public static HashMap<String, String> mysqlBarSize = new HashMap<>();
    public static HashMap<String, String> cassandraBarSize = new HashMap<>();
    static String mysqlConnection;
    static String mysqlUserName;
    static String mysqlPassword;
    static String cassandraConnection;
    static String cassandraPort;
    static String kairosIP;
    static String kairosPort;
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
     * @param args the command line arguments
     */
    public Historical(String parameterFile) throws Exception {
        boolean export = false;
        boolean validate = false;
        boolean gaps = false;
        boolean historical = false;
        boolean scansplits = false;
        boolean getsymbols = false;
        properties = Utilities.loadParameters(parameterFile);
        export = Boolean.parseBoolean(properties.getProperty("export", "false"));
        validate = Boolean.parseBoolean(properties.getProperty("validate", "false"));
        gaps = Boolean.parseBoolean(properties.getProperty("gaps", "false"));
        scansplits = Boolean.parseBoolean(properties.getProperty("scansplits", "false"));
        Historical.scansplits = true;
        getsymbols = Boolean.parseBoolean(properties.getProperty("getsymbols", "false"));
        historical = Boolean.parseBoolean(properties.getProperty("historical", "false"));
        zerovolumeSymbols=properties.getProperty("zerovolumesymbols","");

        if (getsymbols) {
            String metric = properties.getProperty("metric", "").toString().trim();
            //getSymbols(Algorithm.cassandraIP,metric);
        } else if (historical) {
            boolean done = false;
            boolean target_mysql = Boolean.parseBoolean(properties.getProperty("mysql", "false").toString().trim());
            boolean target_cassandra = Boolean.parseBoolean(properties.getProperty("cassandra", "false").toString().trim());
            Date shutdownDate = new Date();
            loadParameters(target_mysql, target_cassandra);
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
            }

            //process mysql first    
            String barSize = null;
            Date endDate = new Date();
            Date startDate = null;
            int connectionCount = Parameters.connection.size();
            int i = 0; //i = symbols requested, used for identifying the beanconnection to use
            Thread t = new Thread();
            if (mysqlConnection != null) {
                for (String b : mysqlBarSize.keySet()) {
                    if (!barSizeProcessed.containsKey(b)) {
                        barSize = b;
                        dc = new DataCapture(mysqlBarSize.get(b), batch);
                        for (BeanSymbol s : Parameters.symbol) {//for each symbol
                            //get start date
                            if (!done) {
                                mySQLQuery = mySQLConnect.prepareStatement("select date from " + mysqlBarSize.get(b) + " where symbol=? order by date desc limit 1");
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
                                lastUpdateDate.put(s.getDisplayname().replace("&", ""), startDate);
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
                for (String b : cassandraBarSize.keySet()) {
                    if (!barSizeProcessed.containsKey(b)) {
                        barSize = b;
                        dc = new DataCapture(mysqlBarSize.get(b), batch);
                        int j = -1;
                        for (BeanSymbol s : Parameters.symbol) {//for each symbol
                            j = j + 1;
                            endDate = new Date();
                            //get start date
                            if (!done) {
                                startDate=new Date(getLastTime(kairosIP,Utilities.getInt(kairosPort,8085),s, cassandraBarSize.get(b) + ".close"));
                                lastUpdateDate.put(s.getDisplayname().replace("&", ""), startDate);
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
            }
            System.exit(0);
        } else if (export) {
            //put export functionality here
            String barSize = properties.getProperty("barsize", "1min").toString().trim();
            String startDate = properties.getProperty("startdate", "1970-01-01 00:00:00").toString().trim();
            String endDate = properties.getProperty("enddate", getFormatedDate("yyyy-MM-dd HH:mm:ss", new Date().getTime(), timeZone)).toString().trim();
            String metric = properties.getProperty("metric", "");
            String symbolFile = properties.getProperty("symbolfile", "symbols.csv").toString().trim();
            String rollFile = properties.getProperty("rollfile", "rolls.csv").toString().trim();
            String splitFile = properties.getProperty("splitfile", "splits.csv").toString().trim();
            List<String> symbols;
            if (symbolFile.contains(".csv")) {
                symbols = Files.readAllLines(Paths.get(symbolFile), StandardCharsets.UTF_8);
                symbols.remove(0);
            } else {
                symbols = Arrays.asList(symbolFile.split(","));
            }
            HashMap<String, ArrayList<SplitInformation>> splits = new HashMap<>();
            if (!splitFile.equals("")) {
                List<String> splitData = Files.readAllLines(Paths.get(splitFile), StandardCharsets.UTF_8);
                splitData.remove(0);
                for (String sp : splitData) {
                    String str[] = sp.split(",");
                    if (splits.containsKey(str[1].toLowerCase())) {
                        SplitInformation si = new SplitInformation();
                        si.actualDate = str[0];
                        si.symbol = str[1];
                        si.oldShares = Integer.valueOf(str[2]);
                        si.newShares = Integer.valueOf(str[3]);
                        splits.get(str[1].toLowerCase()).add(si);
                    } else {
                        ArrayList<SplitInformation> tmp = new ArrayList<>();
                        SplitInformation si = new SplitInformation();
                        si.actualDate = str[0];
                        si.symbol = str[1];
                        si.oldShares = Integer.valueOf(str[2]);
                        si.newShares = Integer.valueOf(str[3]);
                        tmp.add(si);
                        splits.put(str[1].toLowerCase(), tmp);
                    }
                }
            }

            List<String> expirationList = new ArrayList();
            if (!rollFile.equals("")) {
                expirationList = Files.readAllLines(Paths.get(rollFile), StandardCharsets.UTF_8);
            }
            for (String s : symbols) {
                //exportAsCSV(s, startDate, endDate, barSize, metric, (ArrayList<String>) expirationList, splits);
            }

        } else if (scansplits) {

            String startDate = properties.getProperty("startdate", "\"1970-01-01 00:00:00\"").toString().trim();
            String endDate = properties.getProperty("enddate", getFormatedDate("yyyy-MM-dd HH:mm:ss", new Date().getTime(), timeZone)).toString().trim();
            String metric = properties.getProperty("metric", "");
            String symbolFile = properties.getProperty("symbolfile", "symbols.csv").toString().trim();
            threshold = Double.parseDouble(properties.getProperty("splitthreshold", "0.7").toString().trim());

            List<String> symbols;
            if (symbolFile.contains(".csv")) {
                symbols = Files.readAllLines(Paths.get(symbolFile), StandardCharsets.UTF_8);
                symbols.remove(0);
            } else {
                symbols = Arrays.asList(symbolFile.split(","));
            }
            HashMap<String, ArrayList<SplitInformation>> splits = new HashMap<>();
            List<String> expirationList = new ArrayList();
            for (String s : symbols) {
                //exportAsCSV(s, startDate, endDate, "1min", metric, (ArrayList<String>) expirationList, splits);
            }
        }
    }
/*
    private static void exportAsCSV(String symbol, String startDate, String endDate, String barSize, String metric, ArrayList<String> expiry, HashMap<String, ArrayList<SplitInformation>> splits) throws MalformedURLException, URISyntaxException, IOException, ParseException {

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        SimpleDateFormat sdfExpiry = new SimpleDateFormat("yyyy-MM-dd");
        Date start = sdf.parse(startDate);
        Date end = sdf.parse(endDate);
        if (symbol.contains(",")) {
            symbol = symbol.split(",")[2].toLowerCase();
        } else {
            symbol = symbol.toLowerCase();
        }
        TreeMap<Long, OHLCV> t = new TreeMap();
        long time = new Date().getTime();
        if (expiry.size() > 0) {
            //get each expiration date for symbol
            for (int i = 0; i < expiry.size() - 1; i++) {
                Date startPeriod = sdfExpiry.parse(expiry.get(i));
                Date endPeriod = sdfExpiry.parse(expiry.get(i + 1));
                endPeriod = new Date(endPeriod.getTime() + 24 * 60 * 60 * 1000 - 1);//move time to 23:59:00
                if (start.before(startPeriod) || end.after(startPeriod)) {
                    t.putAll(exportAsCSVSubFunction(Algorithm.cassandraIP,symbol, startPeriod, endPeriod, metric, expiry.get(i + 1)));
                }
            }
        } else {
            t.putAll(exportAsCSVSubFunction(Algorithm.cassandraIP,symbol, start, end, metric, null));

        }

        if (!splits.isEmpty()) {
            if (splits.containsKey(symbol)) {
                ArrayList<SplitInformation> splitDates = splits.get(symbol);
                for (SplitInformation si : splitDates) {
                    Date splitDate = parseDate("yyyy-MM-dd", si.actualDate, timeZone);
                    if (t.floorEntry(splitDate.getTime()) != null) {//data exists for before split date
                        Date splitStartDate = addDays(splitDate, -5);
                        Date splitEndDate = addDays(splitDate, 5);
                        double expPricePercentage = ((double) si.oldShares) / si.newShares;
                        for (Date d = splitStartDate; d.before(splitEndDate); d = addDays(d, 1)) {
                            //get first quote after splitstartdate
                            double nextprice = Double.valueOf(t.ceilingEntry(d.getTime()).getValue().getClose());
                            double priorprice = Double.valueOf(t.floorEntry(d.getTime()).getValue().getClose());
                            if (nextprice / priorprice < expPricePercentage + 0.1 && nextprice / priorprice > expPricePercentage - 0.1) {
                                long splitTime = t.floorKey(d.getTime());
                                SortedMap<Long, OHLCV> subt = t.subMap(t.firstKey(), splitTime + 1);//we need both firstkey and splittime values

                                for (OHLCV ohlcv : subt.values()) {
                                    ohlcv.setOpen(String.valueOf(Double.valueOf(ohlcv.getOpen()) * expPricePercentage));
                                    ohlcv.setHigh(String.valueOf(Double.valueOf(ohlcv.getHigh()) * expPricePercentage));
                                    ohlcv.setLow(String.valueOf(Double.valueOf(ohlcv.getLow()) * expPricePercentage));
                                    ohlcv.setClose(String.valueOf(Double.valueOf(ohlcv.getClose()) * expPricePercentage));
                                    long volume = (long) Double.parseDouble(ohlcv.getVolume());
                                    ohlcv.setVolume(String.valueOf((long) volume / expPricePercentage));
                                }
                            }
                        }
                    }
                }
            }
        }
        if (scansplits) {
            if (t.size() > 0) {
                Date splitStartDate = new Date(t.firstKey());
                splitStartDate = addDays(splitStartDate, 1);
                boolean finished = false;
                for (Date d = splitStartDate; !finished; d = addDays(d, 1)) {
                    if (t.ceilingEntry(d.getTime()) != null) {
                        double price = Double.valueOf(t.ceilingEntry(d.getTime()).getValue().getClose());

                        double priorprice = Double.valueOf(t.floorEntry(addDays(d, -1).getTime()).getValue().getClose());
                        if (price / priorprice < threshold) {
                            //we have a potential split, write to splitinformation
                            SplitInformation si = new SplitInformation();
                            si.expectedDate = getFormatedDate("yyyy-MM-dd", d.getTime(), timeZone);
                            si.symbol = symbol;
                            si.expectedNewShares = priorprice / price;
                            si.oldShares = 1;
                            writeSplits(si);
                        }
                    } else {
                        finished = true;
                    }
                }
            }
        }
        if (!scansplits) {
            writeToFile(symbol, t);
        }
        t.clear();
        System.out.println("Data Exported:" + symbol + " ,Time Taken:" + (new Date().getTime() - time) / (1000) + " seconds");

    }
*/
    private static void writeSplits(SplitInformation si) {
        try {
            File file = new File("suggestedsplits" + ".csv");

            //if file doesnt exists, then create it
            if (!file.exists()) {
                file.createNewFile();
            }
            FileWriter fileWritter = new FileWriter(file, true);
            BufferedWriter bufferWritter = new BufferedWriter(fileWritter);
            bufferWritter.write(si.expectedDate + "," + si.symbol + "," + si.oldShares + "," + si.expectedNewShares + newline);
            bufferWritter.close();
        } catch (IOException ex) {
        }
    }

    /*
    private static TreeMap<Long, OHLCV> exportAsCSVSubFunction(String url,String symbol, Date startDate, Date endDate, String metric, String expiry) throws URISyntaxException, IOException {
        HttpClient client = new HttpClient("http://"+url+":8085");
        TreeMap<Long, OHLCV> t = new TreeMap<>();
        String metricnew = null;
        int startCounter = 0;
        if (scansplits) {
            startCounter = 4;
        }
        for (int i = startCounter; i < 5; i++) {

            switch (i) {
                case 0:
                    metricnew = metric + ".open";
                    break;
                case 1:
                    metricnew = metric + ".high";
                    break;
                case 2:
                    metricnew = metric + ".low";
                    break;
                case 3:
                    metricnew = metric + ".volume";
                    break;
                case 4:
                    metricnew = metric + ".close";
                    break;
                default:
                    break;
            }

            QueryBuilder builder = QueryBuilder.getInstance();
            builder.setStart(startDate)
                    .setEnd(endDate)
                    .addMetric(metricnew)
                    .addTag("symbol", symbol.toLowerCase());
            //.setOrder(QueryMetric.Order.ASCENDING);

            // .addAggregator(AggregatorFactory.create;
//            builder.getMetrics().get(0).setOrder(QueryMetric.Order.ASCENDING);
            if (expiry != null) {
                builder.getMetrics().get(0).addTag("expiry", expiry.replace("-", ""));
            }
            long time = new Date().getTime();
            QueryResponse response = client.query(builder);

            List<DataPoint> dataPoints = response.getQueries().get(0).getResults().get(0).getDataPoints();
            for (DataPoint dataPoint : dataPoints) {
                long lastTime = dataPoint.getTimestamp();
                Object value = dataPoint.getValue();
                //System.out.println("Date:" + new Date(lastTime) + ",Value:" + value.toString());
                switch (i) {
                    case 0:
                        if (t.get(lastTime) == null) {
                            OHLCV temp = new OHLCV();
                            temp.setOpen(value.toString());
                            t.put(lastTime, temp);
                        } else {
                            OHLCV temp = t.get(lastTime);
                            temp.setOpen(value.toString());
                            t.put(lastTime, temp);
                        }
                        break;
                    case 1:
                        if (t.get(lastTime) == null) {
                            OHLCV temp = new OHLCV();
                            temp.setHigh(value.toString());
                            t.put(lastTime, temp);
                        } else {
                            OHLCV temp = t.get(lastTime);
                            temp.setHigh(value.toString());
                            t.put(lastTime, temp);
                        }
                        break;
                    case 2:
                        if (t.get(lastTime) == null) {
                            OHLCV temp = new OHLCV();
                            temp.setLow(value.toString());
                            t.put(lastTime, temp);
                        } else {
                            OHLCV temp = t.get(lastTime);
                            temp.setLow(value.toString());
                            t.put(lastTime, temp);
                        }
                        break;
                    case 4:
                        if (t.get(lastTime) == null) {
                            OHLCV temp = new OHLCV();
                            temp.setClose(value.toString());
                            t.put(lastTime, temp);
                        } else {
                            OHLCV temp = t.get(lastTime);
                            temp.setClose(value.toString());
                            t.put(lastTime, temp);
                        }
                        break;
                    case 3:
                        if (t.get(lastTime) == null) {
                            OHLCV temp = new OHLCV();
                            Long vol = Long.valueOf(value.toString());
                            temp.setVolume(String.format("%d", vol));
                            t.put(lastTime, temp);
                        } else {
                            OHLCV temp = t.get(lastTime);
                            Long vol = Long.valueOf(value.toString());
                            temp.setVolume(String.format("%d", vol));
                            t.put(lastTime, temp);
                        }
                        break;
                    default:
                        break;
                }
            }
        }
        return t;
    }
*/
    /*
    private void getSymbols(String url,String metric) {
        try {
            HttpClient client = new HttpClient("http://"+url+":8085");
            GetResponse response = client.getTagValues();


            System.out.println("response=" + response.getStatusCode());
            for (String name : response.getResults()) {
                System.out.println(name);
                client.shutdown();
            }
        } catch (Exception e) {
        }

    }
*/
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


    private void loadParameters(boolean mysql, boolean cassandra) {
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
                String destination = properties.getProperty("sql"+bar);
                if (destination != null) {
                    mysqlBarSize.put(bar, destination);
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
                String destination = properties.getProperty("cassandra"+bar);
                if (destination != null) {
                    cassandraBarSize.put(bar, destination);
                }
            }
        }

    }

    public static long getLastTime(String kairosIP,int kairosPort, BeanSymbol s, String metric) throws IOException {
        List<String> out = new ArrayList<>();
        HashMap<String, Object> param = new HashMap();
        param.put("TYPE", Boolean.FALSE);
        String[] names;
        if(s.getRight()!=null){
            names=new String[]{"symbol","expiry","strike","option"};
        }else if (s.getExpiry()!=null){
            names=new String[]{"symbol","expiry"};
        }else{
            names=new String[]{"symbol"};
        }
        String[] values;
        if(s.getRight()!=null){
            String formattedStrike = Utilities.formatDouble(Utilities.getDouble(s.getOption(), 0), new DecimalFormat("#.##"));
            values=new String[]{s.getExchangeSymbol().trim().toLowerCase(),s.getExpiry(),formattedStrike,s.getRight()};
        }else if (s.getExpiry()!=null){
            values=new String[]{s.getExchangeSymbol().trim().toLowerCase(),s.getExpiry()};
        }else{
            values=new String[]{s.getExchangeSymbol().trim().toLowerCase()};
        }
        
        HistoricalRequestJson request = new HistoricalRequestJson(metric,
                names,
                values,
                null,
                null,
                null,
                String.valueOf(0),
                String.valueOf(new Date().getTime()),1,"desc");
        //http://stackoverflow.com/questions/7181534/http-post-using-json-in-java
        //        String json_string = JsonWriter.objectToJson(request, param);
        Gson gson = new GsonBuilder().create();
        String json_string = gson.toJson(request);
        String response_json=Utilities.getJsonUsingPut("http://" + kairosIP + ":"+kairosPort+"/api/v1/datapoints/query/tags", 0, json_string);
        KairosResponse response;   
        response=gson.fromJson(response_json, KairosResponse.class);
        //long time=response.getQueries().get(querysize-1).getResults().get(resultsize-1).getValues().get(valuesize-1).get(datapoints-1).longValue();
        long time=response.queries[0].results[0].values[0].time;
        return time;
        } 

    /*
    private static Date getLastTime(String url, String metric, BeanSymbol s) throws MalformedURLException, ParseException, IOException, URISyntaxException {
        HttpClient client = new HttpClient("http://"+url+":8085");
        String symbol = s.getDisplayname();
        String expiry = s.getExpiry();

        symbol = symbol.replace("&", "");
        QueryBuilder builder = QueryBuilder.getInstance();
        builder.setStart(new Date(0))
                .addMetric(metric)
                .addTag("symbol", symbol.toLowerCase());
        if (!(expiry == null)) {
            builder.getMetrics().get(0).addTag("expiry", expiry);
        }
        builder.getMetrics().get(0).setLimit(1);
        builder.getMetrics().get(0).setOrder(QueryMetric.Order.DESCENDING);
        QueryResponse response = client.query(builder);
        List<DataPoint> dataPoints = response.getQueries().get(0).getResults().get(0).getDataPoints();
        long lastTime = 0;
        for (DataPoint dataPoint : dataPoints) {
            lastTime = dataPoint.getTimestamp();
        }
        client.shutdown();
        return new Date(lastTime);

    }
*/
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
