/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.eodmaintenance;

import com.google.common.collect.MapDifference;
import com.google.common.collect.MapDifference.ValueDifference;
import com.google.common.collect.Sets.SetView;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.incurrency.framework.Algorithm;
import com.incurrency.framework.BeanSymbol;
import com.incurrency.framework.DateUtil;
import com.incurrency.framework.MainAlgorithm;
import com.incurrency.framework.Utilities;
import com.incurrency.kairosresponse.DataPoint;
import com.incurrency.kairosresponse.QueryResponse;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.ScanResult;

/**
 *
 * @author Pankaj
 */
public class EODMaintenance {

    private Properties properties;
    private Map<String, String> symbols = new HashMap<>();
    private static final Logger logger = Logger.getLogger(EODMaintenance.class.getName());
    private Set<String> nifty50 = new HashSet<>();
    private Map<String, String> contactSize = new HashMap<>();
    private Map<String, String> allStocks = new HashMap<>();
    private Set<String> cnx500 = new HashSet<>();
    private String fnolotsizeurl;
    private String cnx500url;
    private String niftyurl;
    private String f_Strikes;
    private String currentDay;
    private String f_HistoricalFutures;
    private String f_HistoricalFuturesFwd;
    private String f_HistoricalStocks;
    private String f_Swing;
    private String f_RateServer;
    private String f_IBSymbol;
    private String ibsymbolurl;
    private JedisPool jPool;
    private String redisurl;

    public static JedisPool RedisConnect(String uri, Integer port, Integer database) {
        return new JedisPool(new JedisPoolConfig(), uri, port, 2000, null, database);
    }

    /**
     * @param args the command line arguments
     */
    public EODMaintenance(String propertyFileName) throws Exception {
        properties = Utilities.loadParameters(propertyFileName);
        SimpleDateFormat sdf_yyyyMMdd = new SimpleDateFormat("yyyyMMdd");
        fnolotsizeurl = properties.getProperty("fnolotsizeurl", "http://www.nseindia.com/content/fo/fo_mktlots.csv").toString().trim();
        cnx500url = properties.getProperty("cnx500url", "https://nseindia.com/content/indices/ind_nifty500list.csv").toString().trim();
        niftyurl = properties.getProperty("niftyurl", "http://www.nseindia.com/content/indices/ind_niftylist.csv").toString().trim();
        f_Strikes = properties.getProperty("filestrike", "sos_scheme.csv").toString().trim();
        currentDay = properties.getProperty("currentday", sdf_yyyyMMdd.format(Calendar.getInstance(TimeZone.getTimeZone(Algorithm.timeZone)).getTime()));
        f_HistoricalFutures = properties.getProperty("filenamehistoricalfutures");
        f_HistoricalFuturesFwd = properties.getProperty("filenamehistoricalfuturesfwd");
        f_HistoricalStocks = properties.getProperty("filenamehistoricalstocks");
        f_Swing = properties.getProperty("filenameswing");
        f_RateServer = properties.getProperty("filenamerateserver");
        f_IBSymbol = properties.getProperty("fileibsymbol");
        ibsymbolurl = properties.getProperty("ibsymbolurl");
        redisurl = properties.getProperty("redisurl", "127.0.0.1:6379:2");
        jPool = RedisConnect(redisurl.split(":")[0], Integer.valueOf(redisurl.split(":")[1]), Integer.valueOf(redisurl.split(":")[2]));

        String nextExpiry = Utilities.getLastThursday(currentDay,"yyyyMMdd",0);
        String secondExpiry=Utilities.getLastThursday(nextExpiry,"yyyyMMdd",1);
        File outputfile = new File("logs", f_IBSymbol);
        /*
         * Commented out the ability to create contractsize records
         */
        //createFNOContractSizeRecords(0,new Date().getTime());
//        if (!outputfile.exists()) {
        nifty50 = this.loadNifty50StocksFromRedis();
        contactSize = this.loadContractSizesFromRedis();
        cnx500 = this.loadCNX500StocksFromRedis();
        allStocks = this.loadAllStocksFromRedis();
        //save all symbols to redis
        saveAllSymbols(ibsymbolurl);
        //save nifty index data to redis
        saveNifty50ToRedis();
        saveCNX500ToRedis();
        saveContractSizeToRedis(nextExpiry);
        saveStrikeDifferenceToRedis(nextExpiry);
        saveContractSizeToRedis(secondExpiry);
        saveStrikeDifferenceToRedis(secondExpiry);


        MainAlgorithm.setCloseDate(new Date());
    }

    public void saveAllSymbols(String urlName) throws IOException {
        Map<String, String> symbols = new HashMap<>();
        String constant = "&page=";
        String today = new SimpleDateFormat("yyyyMMdd").format(new Date());
        if (urlName != null) {
            String exchange = urlName.split("&")[1].split("=")[1].toUpperCase();
            String type = urlName.split("&")[2].split("=")[1].toUpperCase();
            for (int pageno = 1; pageno < 100; pageno = pageno + 1) {
                String url = urlName + constant + pageno;
                System.out.println("Parsing :" + pageno);
                org.jsoup.nodes.Document stockList = Jsoup.connect(url).timeout(0).get();
                if (stockList.getElementsMatchingText("No result for this combination").size() > 0) {
                    break;
                } else {
                    Element tbl = null;
                    Elements shortlist = stockList.getElementsByClass("table-responsive");
                    for (Element e : shortlist) {
                        if (e.getElementsMatchingOwnText("IB Symbol").size() > 0 && e.getElementsMatchingOwnText("Product Description").size() > 0) {
                            tbl = e;
                            break;
                        }
                    }
                    //tbl = stockList.getElementsByClass("comm_table_background").get(4); //Todo: Check for 404 error
                    Elements body = tbl.select("tbody");
                    if (body.size() == 0) {
                        break;
                    }

                    Elements rows = body.select("tr");
                    int i = 0;
                    if (rows.size() == 0) {
                        break;
                    }

                    for (Element stockRow : rows) {
                        if (i >= 0) {
                            //if (stockRow.attr("class").equals("linebottom")) {
                            String tempIBSymbol = stockRow.getElementsByTag("td").get(0).text().toUpperCase().trim();
                            String tempLongName = stockRow.getElementsByTag("td").get(1).text().toUpperCase().trim();
                            String tempContractID = stockRow.getElementsByTag("td").get(1).getElementsByTag("a").get(0).attr("href").split("&")[2].split("conid=")[1].split("'")[0].toUpperCase().trim();
                            String tempCurrency = stockRow.getElementsByTag("td").get(3).text().toUpperCase().trim();
                            String tempExchangeSymbol = stockRow.getElementsByTag("td").get(2).text().toUpperCase().trim();
                            int index = tempExchangeSymbol.indexOf("_");
                            tempExchangeSymbol = index > 0 ? tempExchangeSymbol.substring(0, index) : tempExchangeSymbol;
                            symbols.put(tempExchangeSymbol, tempIBSymbol);
                            System.out.println(tempExchangeSymbol);
                        }
                        i++;
                    }
                }
            }
            if (symbols.size() > 1000) {//Minimum threshold to confirm the IB website is up
                if (!Utilities.equalMaps(symbols, allStocks)) {
                    MapDifference<String,String> difference = com.google.common.collect.Maps.difference(symbols, allStocks);
                    for(Map.Entry<String,String> entry:difference.entriesOnlyOnLeft().entrySet()){
                       logger.log(Level.INFO,"All Symbols Entries in New Data,Key:{0},Value:{1}",new Object[]{entry.getKey(),entry.getValue()});
                         
                    }
                    for(Map.Entry<String,String> entry:difference.entriesOnlyOnRight().entrySet()){
                       logger.log(Level.INFO,"All Symbols Entries in Old Data,Key:{0},Value:{1}",new Object[]{entry.getKey(),entry.getValue()});
                         
                    }
                    for(Map.Entry<String,ValueDifference<String>> entry:difference.entriesDiffering().entrySet()){
                       logger.log(Level.INFO,"All Symbols ValueDifference,Key:{0},Value:{1}",new Object[]{entry.getValue().leftValue(),entry.getValue().rightValue()});
                         
                    }
                    
                    for (Map.Entry<String, String> s : symbols.entrySet()) {
                        try (Jedis jedis = jPool.getResource()) {
                            jedis.hset("ibsymbols:" + today, s.getKey(), s.getValue());
                        }
                    }
                }
            }
        }
    }

    public void saveNifty50ToRedis() throws IOException {
        Set<String> newNifty = new HashSet<>();
        if (getResponseCode(niftyurl) != 404) {
            URL niftyURL = new URL(niftyurl);
            BufferedReader in = new BufferedReader(new InputStreamReader(niftyURL.openStream()));
            int j = 0;
            int i = 0;
            String line;
            while ((line = in.readLine()) != null) {
                j = j + 1;
                if (j > 1) {//skip first row
                    String[] input = line.split(",");
                    String exchangeSymbol = input[2].trim().toUpperCase();//2nd column of nse file
                    newNifty.add(exchangeSymbol);
                }
            }
        }
        if (!newNifty.equals(nifty50)) {
            SimpleDateFormat sdf_yyyyMMdd = new SimpleDateFormat("yyyyMMdd");
            SetView<String> difference = com.google.common.collect.Sets.difference(nifty50, newNifty);
            logger.log(Level.INFO,"Nifty50 Entries in old data,{0}",new Object[]{Arrays.toString(difference.toArray())});
            difference = com.google.common.collect.Sets.difference(newNifty,nifty50);
            logger.log(Level.INFO,"Nifty50 Entries in new data,{0}",new Object[]{Arrays.toString(difference.toArray())});
            for (Object s : newNifty.toArray()) {
                try (Jedis jedis = jPool.getResource()) {
                    jedis.sadd("nifty50:" + sdf_yyyyMMdd.format(new Date()), s.toString());
                }
            }

        }

    }

    public void saveCNX500ToRedis() throws IOException {
        Set<String> newCNX500 = new HashSet<>();
        if (getResponseCode(cnx500url) != 404) {
            URL CNX500URL = new URL(cnx500url);
            BufferedReader in = new BufferedReader(new InputStreamReader(CNX500URL.openStream()));
            int i = 0;
            int j = 0;
            String line;
            while ((line = in.readLine()) != null) {
                j = j + 1;
                if (j > 1) {//skip first row
                    String[] input = line.split(",");
                    String exchangeSymbol = input[2].trim().toUpperCase();//2nd column of nse file
                    newCNX500.add(exchangeSymbol);
                }
            }
        }

        if (!newCNX500.equals(cnx500)) {
            SetView<String> difference = com.google.common.collect.Sets.difference(cnx500, newCNX500);
            logger.log(Level.INFO,"CNX500 Entries in Old Data,{0}",new Object[]{Arrays.toString(difference.toArray())});
            difference = com.google.common.collect.Sets.difference(newCNX500, cnx500);
            logger.log(Level.INFO,"CNX500 Entries in New Data,{0}",new Object[]{Arrays.toString(difference.toArray())});
            SimpleDateFormat sdf_yyyyMMdd = new SimpleDateFormat("yyyyMMdd");
            for (Object s : newCNX500.toArray()) {
                try (Jedis jedis = jPool.getResource()) {
                    jedis.sadd("cnx500:" + sdf_yyyyMMdd.format(new Date()), s.toString());
                }
            }

        }

    }

    public void saveContractSizeToRedis(String expiry) throws IOException, ParseException {
        Map<String, String> newContractSize = new HashMap<>();
        int rowsToSkipFNO = 11;
        SimpleDateFormat sdf_formatInFile1 = new SimpleDateFormat("MMM-yy");
        SimpleDateFormat sdf_formatInFile2 = new SimpleDateFormat("yy-MMM");
        SimpleDateFormat sdf_yyyyMMdd = new SimpleDateFormat("yyyyMMdd");
        SimpleDateFormat sdf_yyyyMM = new SimpleDateFormat("yyyyMM");

        if (getResponseCode(fnolotsizeurl) != 404) {
            URL fnoURL = new URL(fnolotsizeurl);
            BufferedReader in = new BufferedReader(new InputStreamReader(fnoURL.openStream()));
            int j = 0;
            int i = 0;
            String line;
            int columnNumber = -1;
            while ((line = in.readLine()) != null) {
                j = j + 1;
                if (j > rowsToSkipFNO) {//skip first 11 rows
                    String[] input = line.split(",");
                    if (j == rowsToSkipFNO + 1) { //this row contains expiry dates
                        //data of interest will be in one of the columns. That column is identified and stored in columnNumber
                        String expiryFormattedAsInput1 = sdf_formatInFile1.format(sdf_yyyyMMdd.parse(expiry));//expiry is formatted in MMM-yy format
                        String expiryFormattedAsInput2 = sdf_formatInFile2.format(sdf_yyyyMMdd.parse(expiry));//expiry is formatted in MMM-yy format
                        for (String inp : input) {
                            if (Utilities.isDate(inp, sdf_formatInFile1) || Utilities.isDate(inp, sdf_formatInFile2)) {
                                String expiration = inp.trim().toUpperCase();
                                if (expiration.equalsIgnoreCase(expiryFormattedAsInput1) || expiration.equalsIgnoreCase(expiryFormattedAsInput2)) {
                                    columnNumber = i;
                                    break;
                                }
                            }
                            i = i + 1;
                        }
                    } else if (columnNumber >= 0) {
                        if (input[1].trim().length() > 0) {//not an empty row
                            String exchangesymbol = input[1].trim().toUpperCase();
//                                String displayName = input[1].trim().toUpperCase().replaceAll("[^A-Za-z0-9]", "");
                            String displayName = input[1].trim().toUpperCase();
                            int minsize = Utilities.getInt(input[columnNumber], 0);
                            if (minsize > 0) {
                                newContractSize.put(exchangesymbol, String.valueOf(minsize));
                            }
                        }
                    }
                }
            }
        }
        if (!Utilities.equalMaps(newContractSize, contactSize)) {
                      MapDifference<String,String> difference = com.google.common.collect.Maps.difference(newContractSize, contactSize);
                    for(Map.Entry<String,String> entry:difference.entriesOnlyOnLeft().entrySet()){
                       logger.log(Level.INFO,"ContractSize Entries in New Data,Key:{0},Value:{1}",new Object[]{entry.getKey(),entry.getValue()});
                         
                    }
                    for(Map.Entry<String,String> entry:difference.entriesOnlyOnRight().entrySet()){
                       logger.log(Level.INFO,"ContractSize Entries in Old Data,Key:{0},Value:{1}",new Object[]{entry.getKey(),entry.getValue()});
                         
                    }
                    for(Map.Entry<String,ValueDifference<String>> entry:difference.entriesDiffering().entrySet()){
                       logger.log(Level.INFO,"ContractSize ValueDifference,Key: {0},NewValue:{0},OldValue:{1}",new Object[]{entry.getKey(),entry.getValue().leftValue(),entry.getValue().rightValue()});
                         
                    }
            for (Map.Entry<String, String> entry : newContractSize.entrySet()) {
                try (Jedis jedis = jPool.getResource()) {
                    jedis.hset("contractsize:" + expiry, entry.getKey(), entry.getValue());
                }
            }
        }
    }

    public void saveStrikeDifferenceToRedis(String expiry) throws IOException, ParseException {
        Map<String, String> fnoSymbols = this.loadContractSizesFromRedis();
        Map<String, String> strikeDistance = loadStrikeDistanceFromRedis();
        Map<String, String> newStrikeDistance = new HashMap<>();
        long endTime = DateUtil.getFormattedDate(expiry, "yyyyMMdd", Algorithm.timeZone).getTime();
        endTime = endTime + 24 * 60 * 60 * 1000;
        long diff=120L * 24 * 60 * 60 * 1000;
        long startTime = endTime - diff;

        for (Map.Entry<String, String> fnosymbol : fnoSymbols.entrySet()) {
            //get strikes for expiry
            List<String> strikes = getOptionStrikesFromKDB("91.121.168.138", 8085, fnosymbol.getKey().toLowerCase(), expiry, startTime, endTime, "india.nse.option.s4.daily.settle");
            if (strikes!=null && strikes.size() >= 3) {
                List<Double>dstrikes=new ArrayList<>();
                for(String s:strikes){
                    dstrikes.add(Utilities.getDouble(s, 0));
                }
                Collections.sort(dstrikes);
                double distance = dstrikes.get(2) - dstrikes.get(1);
                newStrikeDistance.put(fnosymbol.getKey(), Utilities.formatDouble(distance, new DecimalFormat("#.##")));
            }
        }

        if (!Utilities.equalMaps(strikeDistance, newStrikeDistance)) {
                                 MapDifference<String,String> difference = com.google.common.collect.Maps.difference(newStrikeDistance, strikeDistance);
                    for(Map.Entry<String,String> entry:difference.entriesOnlyOnLeft().entrySet()){
                       logger.log(Level.INFO,"StrikeDistance Entries in New Data,Key:{0},Value:{1}",new Object[]{entry.getKey(),entry.getValue()});
                         
                    }
                    for(Map.Entry<String,String> entry:difference.entriesOnlyOnRight().entrySet()){
                       logger.log(Level.INFO,"StrikeDistance Entries in Old Data,Key:{0},Value:{1}",new Object[]{entry.getKey(),entry.getValue()});
                         
                    }
                    for(Map.Entry<String,ValueDifference<String>> entry:difference.entriesDiffering().entrySet()){
                       logger.log(Level.INFO,"StrikeDistance ValueDifference,Key:{0},Value:{1}",new Object[]{entry.getValue().leftValue(),entry.getValue().rightValue()});
                         
                    }
            for (Map.Entry<String, String> entry : newStrikeDistance.entrySet()) {
                try (Jedis jedis = jPool.getResource()) {
                    jedis.hset("strikedistance:" + expiry, entry.getKey(), entry.getValue());
                }
            }
        }
    }

    public int getResponseCode(String urlString) throws MalformedURLException, IOException {
        URL u = new URL(urlString);
        HttpURLConnection huc = (HttpURLConnection) u.openConnection();
        huc.setRequestMethod("GET");
        huc.connect();
        return huc.getResponseCode();
    }

   
    /**
     * Returns the next expiration date, given today's date.It assumes that the
     * program is run EOD, so the next expiration date is calculated after the
     * completion of today.
     *
     * @param currentDay
     * @return
     */
    /*
    public String getNextExpiry(String currentDay) throws IOException, ParseException {
        SimpleDateFormat sdf_yyyMMdd = new SimpleDateFormat("yyyyMMdd");
        Date today = sdf_yyyMMdd.parse(currentDay);
        Calendar cal_today = Calendar.getInstance(TimeZone.getTimeZone(Algorithm.timeZone));
        cal_today.setTime(today);
        int year = Utilities.getInt(currentDay.substring(0, 4), 0);
        int month = Utilities.getInt(currentDay.substring(4, 6), 0) - 1;//calendar month starts at 0
        Date expiry = getLastThursday(month, year);
        expiry = Utilities.previousGoodDay(expiry, 0, Algorithm.timeZone, Algorithm.openHour, Algorithm.openMinute, Algorithm.closeHour, Algorithm.closeMinute, Algorithm.holidays, true);
        Calendar cal_expiry = Calendar.getInstance(TimeZone.getTimeZone(Algorithm.timeZone));
        cal_expiry.setTime(expiry);
        if (cal_expiry.get(Calendar.DAY_OF_MONTH) > cal_today.get(Calendar.DAY_OF_MONTH)) {
            return sdf_yyyMMdd.format(expiry);
        } else {
            if (month == 11) {//we are in decemeber
                //expiry will be at BOD, so we get the next month, till new month==0
                while (month != 0) {
                    expiry = Utilities.nextGoodDay(expiry, 24 * 60, Algorithm.timeZone, Algorithm.openHour, Algorithm.openMinute, Algorithm.closeHour, Algorithm.closeMinute, Algorithm.holidays, true);
                    year = Utilities.getInt(sdf_yyyMMdd.format(expiry).substring(0, 4), 0);
                    month = Utilities.getInt(sdf_yyyMMdd.format(expiry).substring(4, 6), 0) - 1;//calendar month starts at 0
                }
                expiry = getLastThursday(month, year);
                expiry = Utilities.previousGoodDay(expiry, 0, Algorithm.timeZone, Algorithm.openHour, Algorithm.openMinute, Algorithm.closeHour, Algorithm.closeMinute, Algorithm.holidays, true);
                return sdf_yyyMMdd.format(expiry);
            } else {
                expiry = getLastThursday(month + 1, year);
                expiry = Utilities.previousGoodDay(expiry, 0, Algorithm.timeZone, Algorithm.openHour, Algorithm.openMinute, Algorithm.closeHour, Algorithm.closeMinute, Algorithm.holidays, true);
                return sdf_yyyMMdd.format(expiry);
            }
        }
    }

    public Date getLastThursday(int month, int year) {
        //http://stackoverflow.com/questions/76223/get-last-friday-of-month-in-java
        Calendar cal = Calendar.getInstance();
        cal.set(year, month, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        cal.set(GregorianCalendar.DAY_OF_WEEK, Calendar.THURSDAY);
        cal.set(GregorianCalendar.DAY_OF_WEEK_IN_MONTH, -1);
        return cal.getTime();
    }
*/
    public void printToFile(List<BeanSymbol> symbolList, String fileName, boolean printLastLine) {
        for (int i = 0; i < symbolList.size(); i++) {
            symbolList.get(i).setSerialno(i + 1);
        }

        //now write data to file
        File outputFile = new File("logs", fileName);
        if (symbolList.size() > 0) {
            //write header
            Utilities.deleteFile(outputFile);
            String header = "serialno,brokersymbol,exchangesymbol,displayname,type,exchange,primaryexchange,currency,expiry,option,right,minsize,barstarttime,streaming,strikedistance,strategy";
            Utilities.writeToFile(outputFile, header);
            String content = "";
            for (BeanSymbol s1 : symbolList) {
                content = s1.getSerialno() + "," + s1.getBrokerSymbol() + "," + (s1.getExchangeSymbol() == null ? "" : s1.getExchangeSymbol())
                        + "," + (s1.getDisplayname() == null ? "" : s1.getDisplayname())
                        + "," + (s1.getType() == null ? "" : s1.getType())
                        + "," + (s1.getExchange() == null ? "" : s1.getExchange())
                        + "," + (s1.getPrimaryexchange() == null ? "" : s1.getPrimaryexchange())
                        + "," + (s1.getCurrency() == null ? "" : s1.getCurrency())
                        + "," + (s1.getExpiry() == null ? "" : s1.getExpiry())
                        + "," + (s1.getOption() == null ? "" : s1.getOption())
                        + "," + (s1.getRight() == null ? "" : s1.getRight())
                        + "," + (s1.getMinsize() == 0 ? 1 : s1.getMinsize())
                        + "," + (s1.getBarsstarttime() == null ? "" : s1.getBarsstarttime())
                        + "," + (s1.getStreamingpriority() == 0 ? "10" : s1.getStreamingpriority())
                        + "," + (s1.getStrikeDistance() == 0 ? "0" : s1.getStrikeDistance())
                        + "," + (s1.getStrategy() == null ? "NONE" : s1.getStrategy());
                Utilities.writeToFile(outputFile, content);
            }
            if (printLastLine) {
                String lastline = symbolList.size() + 1 + "," + "END" + "," + "END"
                        + "," + "END"
                        + "," + "END"
                        + "," + "END"
                        + "," + "END"
                        + "," + "END"
                        + "," + "END"
                        + "," + ""
                        + "," + ""
                        + "," + 0
                        + "," + ""
                        + "," + 100
                        + "," + 0
                        + "," + "EXTRA";
                Utilities.writeToFile(outputFile, lastline);
            }
        }
    }

    public static long getLastTimeFromKDB(String kairosIP, int kairosPort, BeanSymbol s, String metric) throws IOException {
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
            Type type = new com.google.common.reflect.TypeToken<QueryResponse>() {
            }.getType();
            response = gson.fromJson(response_json, QueryResponse.class);
            //long time=response.getQueries().get(querysize-1).getResults().get(resultsize-1).getValues().get(valuesize-1).get(datapoints-1).longValue();
            long time = Double.valueOf(response.getQueries().get(0).getResults().get(0).getDataPoints().get(0).get(0).toString()).longValue();
            //long time=response.queries[0].results[0].values[0].time;
            return time;
        } catch (Exception e) {
            return 0;
        }
    }

    public List<String> getSymbolsFromKDB(String kairosIP, int kairosPort, long startTime, long endTime, String metric) {
        Object[] out = new Object[1];
        HashMap<String, Object> param = new HashMap();
        param.put("TYPE", Boolean.FALSE);
        String strike = null;
        String expiry = null;
        HistoricalRequestJson request = new HistoricalRequestJson(metric,
                null,
                null,
                null,
                null,
                null,
                String.valueOf(startTime),
                String.valueOf(endTime), -1, null);
        Gson gson = new GsonBuilder().create();
        String json_string = gson.toJson(request);
        String response_json = Utilities.getJsonUsingPut("http://" + kairosIP + ":" + kairosPort + "/api/v1/datapoints/query/tags", 0, json_string);
        QueryResponse response;
        Type type = new com.google.common.reflect.TypeToken<QueryResponse>() {
        }.getType();
        response = gson.fromJson(response_json, QueryResponse.class);
        List<String> symbols = response.getQueries().get(0).getResults().get(0).getTags().get("symbol");
        return symbols;
    }

    public List<String> getExpiriesFromKDB(String kairosIP, int kairosPort, String symbol, long startTime, long endTime, String metric) {
        HashMap<String, Object> param = new HashMap();
        param.put("TYPE", Boolean.FALSE);
        String strike = null;
        String expiry = null;
        HistoricalRequestJson request = new HistoricalRequestJson(metric,
                new String[]{"symbol"},
                new String[]{symbol},
                null,
                null,
                null,
                String.valueOf(startTime),
                String.valueOf(endTime), -1, null);

        Gson gson = new GsonBuilder().create();
        String json_string = gson.toJson(request);
        String response_json = Utilities.getJsonUsingPut("http://" + kairosIP + ":" + kairosPort + "/api/v1/datapoints/query/tags", 0, json_string);
        QueryResponse response;
        Type type = new com.google.common.reflect.TypeToken<QueryResponse>() {
        }.getType();
        response = gson.fromJson(response_json, QueryResponse.class);
        List<String> expiries = response.getQueries().get(0).getResults().get(0).getTags().get("expiry");
        return expiries;
    }

    public List<String> getOptionStrikesFromKDB(String kairosIP, int kairosPort, String symbol, String expiry, long startTime, long endTime, String metric) {
        Object[] out = new Object[1];
        HashMap<String, Object> param = new HashMap();
        param.put("TYPE", Boolean.FALSE);
        HistoricalRequestJson request = new HistoricalRequestJson(metric,
                new String[]{"symbol", "expiry"},
                new String[]{symbol, expiry},
                null,
                null,
                null,
                String.valueOf(startTime),
                String.valueOf(endTime), -1, null);
        Gson gson = new GsonBuilder().create();
        String json_string = gson.toJson(request);
        String response_json = Utilities.getJsonUsingPut("http://" + kairosIP + ":" + kairosPort + "/api/v1/datapoints/query/tags", 0, json_string);
        QueryResponse response;
        Type type = new com.google.common.reflect.TypeToken<QueryResponse>() {
        }.getType();
        response = gson.fromJson(response_json, QueryResponse.class);
        List<String> strikes = response.getQueries().get(0).getResults().get(0).getTags().get("strike");
        return strikes;
    }

    public TreeMap<Long, Double> getPricesFromKDB(String kairosIP, int kairosPort, String symbol, String expiry, String right, String strike, long startTime, long endTime, String metric) {
        TreeMap<Long, Double> out = new TreeMap<>();
        HashMap<String, Object> param = new HashMap();
        param.put("TYPE", Boolean.FALSE);
        String[] names;
        if (right != null) {
            names = new String[]{"symbol", "expiry", "strike", "option"};
        } else if (expiry != null) {
            names = new String[]{"symbol", "expiry"};
        } else {
            names = new String[]{"symbol"};
        }
        String[] values;
        if (right != null) {
            String formattedStrike = Utilities.formatDouble(Utilities.getDouble(strike, 0), new DecimalFormat("#.##"));
            values = new String[]{symbol.trim().toLowerCase(), expiry, formattedStrike, right};
        } else if (expiry != null) {
            values = new String[]{symbol.trim().toLowerCase(), expiry};
        } else {
            values = new String[]{symbol.trim().toLowerCase()};
        }

        HistoricalRequestJson request = new HistoricalRequestJson(metric,
                names,
                values,
                null,
                null,
                null,
                String.valueOf(0),
                String.valueOf(new Date().getTime()), -1, null);
        Gson gson = new GsonBuilder().create();
        String json_string = gson.toJson(request);
        String response_json = Utilities.getJsonUsingPut("http://" + kairosIP + ":" + kairosPort + "/api/v1/datapoints/query", 0, json_string);
        QueryResponse response;
        Type type = new com.google.common.reflect.TypeToken<QueryResponse>() {
        }.getType();
        response = gson.fromJson(response_json, QueryResponse.class);
        List<List<Object>> dataPoints = response.getQueries().get(0).getResults().get(0).getDataPoints();
        DecimalFormat df = new DecimalFormat("#");
        df.setMaximumFractionDigits(0);
        for (List<Object> d : dataPoints) {
            if (Utilities.getDouble(d.get(1), 0) > 0) {
                out.put(Utilities.getLong(df.format(d.get(0)), 0), Utilities.getDouble(d.get(1), 0));
            }
        }
        return out;
    }

    public long getMinOIFromKDB(String kairosIP, int kairosPort, String symbol, String expiry, String right, String strike, String metric) {
        if (leadingZerosCount(expiry) > 0) {
            return 0L;
        }
        HashSet<Long> out = new HashSet<>();
        HashMap<String, Object> param = new HashMap();
        param.put("TYPE", Boolean.FALSE);
        String[] names;
        if (right != null) {
            names = new String[]{"symbol", "expiry", "strike", "option"};
        } else if (expiry != null) {
            names = new String[]{"symbol", "expiry"};
        } else {
            names = new String[]{"symbol"};
        }
        String[] values;
        if (right != null) {
            String formattedStrike = Utilities.formatDouble(Utilities.getDouble(strike, 0), new DecimalFormat("#.##"));
            values = new String[]{symbol.trim().toLowerCase(), expiry, formattedStrike, right};
        } else if (expiry != null) {
            values = new String[]{symbol.trim().toLowerCase(), expiry};
        } else {
            values = new String[]{symbol.trim().toLowerCase()};
        }
        long endTime = DateUtil.getFormattedDate(expiry, "yyyyMMdd", Algorithm.timeZone).getTime();
        endTime = endTime + 24 * 60 * 60 * 1000;
        HistoricalRequestJson request = new HistoricalRequestJson(metric,
                names,
                values,
                null,
                null,
                null,
                String.valueOf(endTime - 120L * 24 * 60 * 60 * 1000),
                String.valueOf(endTime), -1, null);
        Gson gson = new GsonBuilder().create();
        String json_string = gson.toJson(request);
        String response_json = Utilities.getJsonUsingPut("http://" + kairosIP + ":" + kairosPort + "/api/v1/datapoints/query", 0, json_string);
        QueryResponse response;
        Type type = new com.google.common.reflect.TypeToken<QueryResponse>() {
        }.getType();
        if (response_json != null) {
            response = gson.fromJson(response_json, QueryResponse.class);
            List<List<Object>> dataPoints = response.getQueries().get(0).getResults().get(0).getDataPoints();
            DecimalFormat df = new DecimalFormat("#");
            df.setMaximumFractionDigits(0);
            for (List<Object> d : dataPoints) {
                if (Utilities.getLong(df.format(d.get(1)), 0) > 0) {
                    out.add(Utilities.getLong(df.format(d.get(1)), 0));
                }
            }
            if (out.size() > 0) {
                return Collections.min(out);
            } else {
                return 0L;
            }
        } else {
            return 0L;
        }
    }

    public int leadingZerosCount(String s) {
        int zeros = 0;
        for (int i = 0; i < 1 && i < s.length(); i++) {
            if (s.charAt(i) == '0') {
                zeros++;
            } else {
                break;
            }
        }
        return zeros;
    }

    public Map<String, String> loadContractSizesFromRedis() {
        String cursor = "";
        String shortlistedkey = "";
         int today=Integer.valueOf(DateUtil.getFormatedDate("yyyyMMdd", new Date().getTime(), TimeZone.getTimeZone(Algorithm.timeZone)));
         int date=0;
        while (!cursor.equals("0")) {
            cursor = cursor.equals("") ? "0" : cursor;
            try (Jedis jedis = jPool.getResource()) {
                ScanResult s = jedis.scan(cursor);
                cursor = s.getCursor();
                for (Object key : s.getResult()) {
                    if (key.toString().contains("contractsize")) {
                        if (shortlistedkey.equals("")&& Integer.valueOf(key.toString().split(":")[1])<today) {
                            shortlistedkey = key.toString();
                        } else {
                            int newdate = Integer.valueOf(key.toString().split(":")[1]);
                            if (newdate>date && newdate < Integer.valueOf(DateUtil.getFormatedDate("yyyyMMdd", new Date().getTime(), TimeZone.getTimeZone(Algorithm.timeZone)))) {
                                shortlistedkey = key.toString();//replace with latest nifty setup
                                date = Integer.valueOf(shortlistedkey.split(":")[1]);
                            }
                        }
                    }
                }
            }
        }
        Map<String, String> contractSizes = new HashMap<>();
        try (Jedis jedis = jPool.getResource()) {
            contractSizes = jedis.hgetAll(shortlistedkey);
        }
        return contractSizes;
    }

    public Map<String, String> loadStrikeDistanceFromRedis() {
        String cursor = "";
        String shortlistedkey = "";
         int today=Integer.valueOf(DateUtil.getFormatedDate("yyyyMMdd", new Date().getTime(), TimeZone.getTimeZone(Algorithm.timeZone)));
        int date=0;
         while (!cursor.equals("0")) {
            cursor = cursor.equals("") ? "0" : cursor;
            try (Jedis jedis = jPool.getResource()) {
                ScanResult s = jedis.scan(cursor);
                cursor = s.getCursor();
                for (Object key : s.getResult()) {
                    if (key.toString().contains("strikedistance")) {
                        if (shortlistedkey.equals("")&& Integer.valueOf(key.toString().split(":")[1])<today) {
                            shortlistedkey = key.toString();
                        } else {
                            int newdate = Integer.valueOf(key.toString().split(":")[1]);
                            if (newdate>date && newdate < Integer.valueOf(DateUtil.getFormatedDate("yyyyMMdd", new Date().getTime(), TimeZone.getTimeZone(Algorithm.timeZone)))) {
                                shortlistedkey = key.toString();//replace with latest nifty setup
                                date = Integer.valueOf(shortlistedkey.split(":")[1]);
                            
                            }
                        }
                    }
                }
            }
        }
        Map<String, String> strikeDistance = new HashMap<>();
        try (Jedis jedis = jPool.getResource()) {
            strikeDistance = jedis.hgetAll(shortlistedkey);
        }
        return strikeDistance;
    }

    public Set<String> loadCNX500StocksFromRedis() {
        String cursor = "";
        String shortlistedkey = "";
        int today=Integer.valueOf(DateUtil.getFormatedDate("yyyyMMdd", new Date().getTime(), TimeZone.getTimeZone(Algorithm.timeZone)));
        int date=0;
        while (!cursor.equals("0")) {
            cursor = cursor.equals("") ? "0" : cursor;
            try (Jedis jedis = jPool.getResource()) {
                ScanResult s = jedis.scan(cursor);
                cursor = s.getCursor();
                for (Object key : s.getResult()) {
                    if (key.toString().contains("cnx500")) {
                        if (shortlistedkey.equals("")&& Integer.valueOf(key.toString().split(":")[1])<today) {
                            shortlistedkey = key.toString();
                        } else {
                            int newdate = Integer.valueOf(key.toString().split(":")[1]);
                            if (newdate>date && newdate < Integer.valueOf(DateUtil.getFormatedDate("yyyyMMdd", new Date().getTime(), TimeZone.getTimeZone(Algorithm.timeZone)))) {
                                shortlistedkey = key.toString();//replace with latest nifty setup
                               date = Integer.valueOf(shortlistedkey.split(":")[1]);
                            }
                        }
                    }
                }
            }
        }
        Set<String> niftySymbols = new HashSet<>();
        try (Jedis jedis = jPool.getResource()) {
            niftySymbols = jedis.smembers(shortlistedkey);
        }
        return niftySymbols;
    }

    public Set<String> loadNifty50StocksFromRedis() {
        String cursor = "";
        String shortlistedkey = "";
         int today=Integer.valueOf(DateUtil.getFormatedDate("yyyyMMdd", new Date().getTime(), TimeZone.getTimeZone(Algorithm.timeZone)));
        int date=0;
         while (!cursor.equals("0")) {
            cursor = cursor.equals("") ? "0" : cursor;
            try (Jedis jedis = jPool.getResource()) {
                ScanResult s = jedis.scan(cursor);
                cursor = s.getCursor();
                for (Object key : s.getResult()) {
                    if (key.toString().contains("nifty50")) {
                        if (shortlistedkey.equals("")&& Integer.valueOf(key.toString().split(":")[1])<today) {
                            shortlistedkey = key.toString();
                        } else {
                            int newdate = Integer.valueOf(key.toString().split(":")[1]);
                            if (newdate>date && newdate < Integer.valueOf(DateUtil.getFormatedDate("yyyyMMdd", new Date().getTime(), TimeZone.getTimeZone(Algorithm.timeZone)))) {
                                shortlistedkey = key.toString();//replace with latest nifty setup
                            date = Integer.valueOf(shortlistedkey.split(":")[1]);
                            }
                        }
                    }
                }
            }
        }
        Set<String> niftySymbols = new HashSet<>();
        try (Jedis jedis = jPool.getResource()) {
            niftySymbols = jedis.smembers(shortlistedkey);
        }
        return niftySymbols;

    }

    public Map<String, String> loadAllStocksFromRedis() {
        String cursor = "";
        String shortlistedkey = "";
         int today=Integer.valueOf(DateUtil.getFormatedDate("yyyyMMdd", new Date().getTime(), TimeZone.getTimeZone(Algorithm.timeZone)));
        int date=0;
         while (!cursor.equals("0")) {
            cursor = cursor.equals("") ? "0" : cursor;
            try (Jedis jedis = jPool.getResource()) {
                ScanResult s = jedis.scan(cursor);
                cursor = s.getCursor();
                for (Object key : s.getResult()) {
                    if (key.toString().contains("ibsymbols")) {
                        if (shortlistedkey.equals("")&& Integer.valueOf(key.toString().split(":")[1])<today) {
                            shortlistedkey = key.toString();
                        } else {
                            int newdate = Integer.valueOf(key.toString().split(":")[1]);
                            if (newdate>date && newdate < Integer.valueOf(DateUtil.getFormatedDate("yyyyMMdd", new Date().getTime(), TimeZone.getTimeZone(Algorithm.timeZone)))) {
                                shortlistedkey = key.toString();//replace with latest nifty setup
                            date = Integer.valueOf(shortlistedkey.split(":")[1]);
                            }
                        }
                    }
                }
            }
        }
        Map<String, String> allSymbols = new HashMap<>();
        try (Jedis jedis = jPool.getResource()) {
            allSymbols = jedis.hgetAll(shortlistedkey);
        }
        return allSymbols;
    }
}
