/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.eodmaintenance;

import com.incurrency.framework.Algorithm;
import com.incurrency.framework.BeanSymbol;
import com.incurrency.framework.MainAlgorithm;
import com.incurrency.framework.Utilities;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
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
    private List<BeanSymbol> symbols = new ArrayList<>();
    private static final Logger logger = Logger.getLogger(EODMaintenance.class.getName());
    private List<BeanSymbol> nifty50 = new ArrayList<>();
    private List<BeanSymbol> fno = new ArrayList<>();
    private List<BeanSymbol> cnx500 = new ArrayList<>();
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
        return new JedisPool(new JedisPoolConfig(),uri, port,2000,null,database);
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
        redisurl = properties.getProperty("redisurl","127.0.0.1:6379:2");
        jPool=RedisConnect(redisurl.split(":")[0], Integer.valueOf(redisurl.split(":")[1]), Integer.valueOf(redisurl.split(":")[2]));
                
        File outputfile = new File("logs", f_IBSymbol);
        if (!outputfile.exists()) {
            extractSymbolsFromIB(ibsymbolurl, f_IBSymbol, symbols);
        } else {
            new Symbol().reader("logs/" + f_IBSymbol, (ArrayList) symbols);
        }
        String nextExpiry = getNextExpiry(currentDay);
        nifty50 = loadNifty50Stocks(niftyurl, f_Strikes);
        saveToRedis(fnolotsizeurl, f_Strikes, nextExpiry);
        saveToRedis(fnolotsizeurl, f_Strikes, getNextExpiry(nextExpiry));
        fno = loadFutures(fnolotsizeurl, f_Strikes, nextExpiry);
        cnx500 = loadCNX500Stocks(cnx500url, f_Strikes);
        rateserver();
        historicalstocks();
        historicalfutures();
        historicalfuturesfwd();
        swing();
        contra();
        allsymbols();
        
        MainAlgorithm.setCloseDate(new Date());
    }

    public void saveToRedis(String url, String f_strike, String expiry)  {
        try {
            ArrayList<BeanSymbol> out = new ArrayList<>();
            ArrayList<BeanSymbol> interimout = new ArrayList<>();
            URL niftyURL = new URL(niftyurl);
            if (getResponseCode(niftyurl) != 404) {
                BufferedReader in = new BufferedReader(new InputStreamReader(niftyURL.openStream()));
                int j = 0;
                int i = 0;
                String line;
                SimpleDateFormat sdf_yyyyMMdd = new SimpleDateFormat("yyyyMMdd");
                while ((line = in.readLine()) != null) {
                    j = j + 1;
                    if (j > 1) {//skip first row
                        String[] input = line.split(",");
                        String exchangeSymbol = input[2].trim().toUpperCase();//2nd column of nse file
                        try (Jedis jedis = jPool.getResource()) {
                            jedis.sadd("nifty50:" + sdf_yyyyMMdd.format(new Date()), exchangeSymbol);
                        }
                    }
                }
            }


            URL CNX500 = new URL(cnx500url);
            if (getResponseCode(cnx500url) != 404) {
                BufferedReader in = new BufferedReader(new InputStreamReader(CNX500.openStream()));
                int i = 0;
                int j = 0;
                String line;
                SimpleDateFormat sdf_yyyyMMdd = new SimpleDateFormat("yyyyMMdd");
                while ((line = in.readLine()) != null) {
                    j = j + 1;
                    if (j > 1) {//skip first row
                        String[] input = line.split(",");
                        String exchangeSymbol = input[2].trim().toUpperCase();//2nd column of nse file
                        try (Jedis jedis = jPool.getResource()) {
                            jedis.sadd("cnx500:" + sdf_yyyyMMdd.format(new Date()), exchangeSymbol);
                        }
                    }
                }
            }

            int rowsToSkipFNO = 11;
            SimpleDateFormat sdf_formatInFile1 = new SimpleDateFormat("MMM-yy");
            SimpleDateFormat sdf_formatInFile2 = new SimpleDateFormat("yy-MMM");
            SimpleDateFormat sdf_yyyyMMdd = new SimpleDateFormat("yyyyMMdd");
            SimpleDateFormat sdf_yyyyMM = new SimpleDateFormat("yyyyMM");
            URL fnoURL = new URL(url);
            if (getResponseCode(url) != 404) {
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
                                String displayName = input[1].trim().toUpperCase().replaceAll("[^A-Za-z0-9]", "");
                                int minsize = Utilities.getInt(input[columnNumber], 0);
                                if (minsize > 0) {
                                    int id = Utilities.getIDFromExchangeSymbol(symbols, exchangesymbol, "STK", "", "", "");
                                    if (id >= 0) {
                                        try (Jedis jedis = jPool.getResource()) {
                                            jedis.hset("contractsize:" + expiry.substring(0, 6), exchangesymbol, String.valueOf(minsize));
                                        }
                                    } else {
                                        logger.log(Level.SEVERE, "Exchange Symbol {} not found in IB database", new Object[]{exchangesymbol});
                                    }
                                }
                            }
                        }
                    }
                }
            }
            //Fix sequential serial numbers
            for (int i = 0; i < interimout.size(); i++) {
                interimout.get(i).setSerialno(i + 1);
            }
            //Capture Strike levels
            BufferedReader in = new BufferedReader(new FileReader(f_strike));
            int j = 0;
            int i = 0;
            String line;
            while ((line = in.readLine()) != null) {
                j = j + 1;
                if (j > 1) {//skip first row
                    String[] input = line.split(",");
                    String exchangeSymbol = input[0].trim().toUpperCase();//2nd column of nse file                        
                        try (Jedis jedis = jPool.getResource()) {
                            jedis.hset("strikedistance:" + expiry.substring(0, 6), exchangeSymbol, String.valueOf(input[1].trim()));
                        }
                    
                }
            }

        } catch (Exception e) {
            logger.log(Level.SEVERE, null, e);
        }

    }

    public ArrayList<BeanSymbol> loadNifty50Stocks(String url, String f_strike) {
        ArrayList<BeanSymbol> out = new ArrayList<>();
        try {
            String cursor = "";
            String shortlistedkey = "";
            while (!cursor.equals("0")) {
                cursor = cursor.equals("") ? "0" : cursor;
                try (Jedis jedis = jPool.getResource()) {
                    ScanResult s = jedis.scan(cursor);
                    cursor = s.getStringCursor();
                    for (Object key : s.getResult()) {
                        if (key.toString().contains("nifty50")) {
                            if (shortlistedkey.equals("")) {
                                shortlistedkey = key.toString();
                            } else {
                                int date = Integer.valueOf(shortlistedkey.split(":")[1]);
                                int newdate = Integer.valueOf(key.toString().split(":")[1]);
                                if (newdate > date && newdate <= Integer.valueOf(getNextExpiry(currentDay))) {
                                    shortlistedkey = key.toString();//replace with latest nifty setup
                                }
                            }
                        }
                    }
                }
            }
            Set<String> niftySymbols = new HashSet<>();
            try (Jedis jedis = jPool.getResource()) {
                niftySymbols = jedis.smembers(shortlistedkey);
                Iterator iterator = niftySymbols.iterator();
                while (iterator.hasNext()) {
                    String exchangeSymbol = iterator.next().toString().toUpperCase();
                    int id = Utilities.getIDFromExchangeSymbol(symbols, exchangeSymbol, "STK", "", "", "");
                    if (id >= 0) {
                        BeanSymbol s = symbols.get(id);
                        BeanSymbol s1 = s.clone(s);
                        out.add(s1);
                    }
                }
            }
            for (int i = 0; i < out.size(); i++) {
                out.get(i).setSerialno(i + 1);
            }

            //Capture Strike levels
            cursor = "";
            shortlistedkey = "";
            while (!cursor.equals("0")) {
                cursor = cursor.equals("") ? "0" : cursor;
                try (Jedis jedis = jPool.getResource()) {
                    ScanResult s = jedis.scan(cursor);
                    cursor = s.getStringCursor();
                    for (Object key : s.getResult()) {
                        if (key.toString().contains("strikedistance")) {
                            if (shortlistedkey.equals("")) {
                                shortlistedkey = key.toString();
                            } else {
                                int date = Integer.valueOf(shortlistedkey.split(":")[1]);
                                int newdate = Integer.valueOf(key.toString().split(":")[1]);
                                if (newdate > date && newdate <= Integer.valueOf(getNextExpiry(currentDay).substring(0,6))) {
                                    shortlistedkey = key.toString();//replace with latest nifty setup
                                }
                            }
                        }
                    }
                }
            }
            Map<String, String> strikeLevels = new HashMap<>();
            try (Jedis jedis = jPool.getResource()) {
                strikeLevels = jedis.hgetAll(shortlistedkey);
                for (Map.Entry<String, String> entry : strikeLevels.entrySet()) {
                    String exchangeSymbol = entry.getKey().toUpperCase();//2nd column of nse file                        
                    int id = Utilities.getIDFromExchangeSymbol(out, exchangeSymbol, "STK", "", "", "");
                    if (id >= 0) {
                        BeanSymbol s = out.get(id);
                        s.setStrikeDistance(Double.parseDouble(entry.getValue().trim()));
                    }
                }
            }

        } catch (Exception e) {
            logger.log(Level.SEVERE, null, e);
        }
        return out;

    }

    
    /**
     * Loads FNO stocks in ArrayList comprising BeanSymbols .Expiration date and
     * contract sizes are set.
     *
     * @param url
     * @param expiry
     * @return
     */
    public ArrayList<BeanSymbol> loadFutures(String url, String f_strike, String expiry) {
        ArrayList<BeanSymbol> out = new ArrayList<>();
        ArrayList<BeanSymbol> interimout = new ArrayList<>();
        try {
            String cursor = "";
            String shortlistedkey = "";
            while (!cursor.equals("0")) {
                cursor = cursor.equals("") ? "0" : cursor;
                try (Jedis jedis = jPool.getResource()) {
                    ScanResult s = jedis.scan(cursor);
                    cursor = s.getStringCursor();
                    for (Object key : s.getResult()) {
                        if (key.toString().contains("contractsize")) {
                            if (shortlistedkey.equals("")) {
                                shortlistedkey = key.toString();
                            } else {
                                int date = Integer.valueOf(shortlistedkey.split(":")[1]);
                                int newdate = Integer.valueOf(key.toString().split(":")[1]);
                                if (newdate > date && newdate <= Integer.valueOf(expiry.substring(0,6))) {
                                    shortlistedkey = key.toString();//replace with latest nifty setup
                                }
                            }
                        }
                    }
                }
            }
             Map<String, String> contractSizes = new HashMap<>();
            try (Jedis jedis = jPool.getResource()) {
                contractSizes = jedis.hgetAll(shortlistedkey);
                for (Map.Entry<String, String> entry : contractSizes.entrySet()) {
                    String exchangeSymbol = entry.getKey().trim().toUpperCase();
                     int minsize = Utilities.getInt(entry.getValue().trim(),0);
                     if(minsize>0){
                         int id = Utilities.getIDFromExchangeSymbol(symbols, exchangeSymbol, "STK", "", "", "");
                                    if (id >= 0) {
                                        BeanSymbol s = symbols.get(id);
                                        BeanSymbol s1 = s.clone(s);
                                        s1.setType("FUT");
                                        s1.setExpiry(expiry);
                                        s1.setMinsize(minsize);
                                        s1.setStrategy("DATA");
                                        s1.setStreamingpriority(2);
                                        s1.setSerialno(out.size() + 1);
                                        interimout.add(s1);
                                    } else {
                                        logger.log(Level.SEVERE, "Exchange Symbol {} not found in IB database", new Object[]{exchangeSymbol});
                                    }
                     }
                }
            }
            
            //Fix sequential serial numbers
            for (int i = 0; i < interimout.size(); i++) {
                interimout.get(i).setSerialno(i + 1);
            }
            
          //Capture Strike levels
            cursor = "";
            shortlistedkey = "";
            while (!cursor.equals("0")) {
                cursor = cursor.equals("") ? "0" : cursor;
                try (Jedis jedis = jPool.getResource()) {
                    ScanResult s = jedis.scan(cursor);
                    cursor = s.getStringCursor();
                    for (Object key : s.getResult()) {
                        if (key.toString().contains("strikedistance")) {
                            if (shortlistedkey.equals("")) {
                                shortlistedkey = key.toString();
                            } else {
                                int date = Integer.valueOf(shortlistedkey.split(":")[1]);
                                int newdate = Integer.valueOf(key.toString().split(":")[1]);
                                if (newdate > date && newdate <= Integer.valueOf(getNextExpiry(currentDay).substring(0,6))) {
                                    shortlistedkey = key.toString();//replace with latest nifty setup
                                }
                            }
                        }
                    }
                }
            }
            Map<String, String> strikeLevels = new HashMap<>();
            try (Jedis jedis = jPool.getResource()) {
                strikeLevels = jedis.hgetAll(shortlistedkey);
                for (Map.Entry<String, String> entry : strikeLevels.entrySet()) {
                    String exchangeSymbol = entry.getKey().toUpperCase();//2nd column of nse file                        
                    int id = Utilities.getIDFromExchangeSymbol(interimout, exchangeSymbol, "FUT", expiry, "", "");
                    if (id >= 0) {
                        BeanSymbol s = interimout.get(id);
                        BeanSymbol s1 = s.clone(s);
                        s1.setType("FUT");
                        s1.setStrikeDistance(Double.parseDouble(entry.getValue().trim()));
                        out.add(s1);
                    }
                }
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, null, e);
        }
        for (int i = 0; i < out.size(); i++) {
            out.get(i).setSerialno(i + 1);
        }
        return out;

    }

    public ArrayList<BeanSymbol> loadCNX500Stocks(String url, String f_strike) {
         ArrayList<BeanSymbol> out = new ArrayList<>();
        try {
            String cursor = "";
            String shortlistedkey = "";
            while (!cursor.equals("0")) {
                cursor = cursor.equals("") ? "0" : cursor;
                try (Jedis jedis = jPool.getResource()) {
                    ScanResult s = jedis.scan(cursor);
                    cursor = s.getStringCursor();
                    for (Object key : s.getResult()) {
                        if (key.toString().contains("cnx500")) {
                            if (shortlistedkey.equals("")) {
                                shortlistedkey = key.toString();
                            } else {
                                int date = Integer.valueOf(shortlistedkey.split(":")[1]);
                                int newdate = Integer.valueOf(key.toString().split(":")[1]);
                                if (newdate > date && newdate <= Integer.valueOf(getNextExpiry(currentDay))) {
                                    shortlistedkey = key.toString();//replace with latest nifty setup
                                }
                            }
                        }
                    }
                }
            }
            Set<String> niftySymbols = new HashSet<>();
            try (Jedis jedis = jPool.getResource()) {
                niftySymbols = jedis.smembers(shortlistedkey);
                Iterator iterator = niftySymbols.iterator();
                while (iterator.hasNext()) {
                    String exchangeSymbol = iterator.next().toString().toUpperCase();
                    int id = Utilities.getIDFromExchangeSymbol(symbols, exchangeSymbol, "STK", "", "", "");
                    if (id >= 0) {
                        BeanSymbol s = symbols.get(id);
                        BeanSymbol s1 = s.clone(s);
                        out.add(s1);
                    }
                }
            }
            for (int i = 0; i < out.size(); i++) {
                out.get(i).setSerialno(i + 1);
            }

            //Capture Strike levels
            cursor = "";
            shortlistedkey = "";
            while (!cursor.equals("0")) {
                cursor = cursor.equals("") ? "0" : cursor;
                try (Jedis jedis = jPool.getResource()) {
                    ScanResult s = jedis.scan(cursor);
                    cursor = s.getStringCursor();
                    for (Object key : s.getResult()) {
                        if (key.toString().contains("strikedistance")) {
                            if (shortlistedkey.equals("")) {
                                shortlistedkey = key.toString();
                            } else {
                                int date = Integer.valueOf(shortlistedkey.split(":")[1]);
                                int newdate = Integer.valueOf(key.toString().split(":")[1]);
                                if (newdate > date && newdate <= Integer.valueOf(getNextExpiry(currentDay).substring(0,6))) {
                                    shortlistedkey = key.toString();//replace with latest nifty setup
                                }
                            }
                        }
                    }
                }
            }
            Map<String, String> strikeLevels = new HashMap<>();
            try (Jedis jedis = jPool.getResource()) {
                strikeLevels = jedis.hgetAll(shortlistedkey);
                for (Map.Entry<String, String> entry : strikeLevels.entrySet()) {
                    String exchangeSymbol = entry.getKey().toUpperCase();//2nd column of nse file                        
                    int id = Utilities.getIDFromExchangeSymbol(out, exchangeSymbol, "STK", "", "", "");
                    if (id >= 0) {
                        BeanSymbol s = out.get(id);
                        s.setStrikeDistance(Double.parseDouble(entry.getValue().trim()));
                    }
                }
            }

        } catch (Exception e) {
            logger.log(Level.SEVERE, null, e);
        }
        return out;          
    }

    public void rateserver() throws MalformedURLException, IOException, ParseException {
        ArrayList<BeanSymbol> out = new ArrayList<>();

        //NSENIFTY and Index is priority 1
        //FNO stocks in NIFTY are priority 2
        //Residual F&O Stocks are priority 3
        String expiry = getNextExpiry(currentDay);
        BeanSymbol s = new BeanSymbol("NIFTY50", "NSENIFTY", "IND", "", "", "");
        s.setCurrency("INR");
        s.setExchange("NSE");
        s.setStreamingpriority(1);
        s.setStrategy("DATA");
        out.add(s);
        s = new BeanSymbol("NIFTY50", "NSENIFTY", "FUT", expiry, "", "");
        s.setCurrency("INR");
        s.setExchange("NSE");
        s.setStreamingpriority(1);
        s.setStrategy("DATA");
        s.setMinsize(75);
        s.setStrikeDistance(100);
        out.add(s);
        s = new BeanSymbol("NIFTY50", "NSENIFTY", "FUT", this.getNextExpiry(expiry), "", "");
        s.setCurrency("INR");
        s.setExchange("NSE");
        s.setStreamingpriority(1);
        s.setStrategy("DATA");
        s.setMinsize(75);
        s.setStrikeDistance(100);
        out.add(s);

        //Add nifty stocks. Priority =1
        for (int i = 0; i < nifty50.size(); i++) {
            nifty50.get(i).setStreamingpriority(1);
            nifty50.get(i).setStrategy("DATA");
        }
        out.addAll(nifty50);

        //Add F&O Stocks on Nifty50. Priority = 2
        // Other F&O, Priority 3
        for (int i = 0; i < fno.size(); i++) {
            String exchangesymbol = fno.get(i).getExchangeSymbol();
            int id = Utilities.getIDFromExchangeSymbol(nifty50, exchangesymbol, "STK", "", "", "");
            if (id >= 0) {
                id = Utilities.getIDFromExchangeSymbol(fno, exchangesymbol, "FUT", expiry, "", "");
                s = fno.get(id);
                BeanSymbol s1 = s.clone(s);
                s1.setStreamingpriority(2);
                s1.setStrategy("DATA");
                s1.setExpiry(expiry);
                s1.setType("FUT");
                out.add(s1);
            } else {
                id = Utilities.getIDFromExchangeSymbol(fno, exchangesymbol, "FUT", expiry, "", "");
                s = fno.get(id);
                BeanSymbol s1 = s.clone(s);
                s1.setStreamingpriority(3);
                s1.setStrategy("DATA");
                out.add(s1);
            }
        }

        expiry = getNextExpiry(expiry);
        ArrayList<BeanSymbol> fwdout = loadFutures(this.fnolotsizeurl, this.f_Strikes, expiry);
        for (int i = 0; i < fwdout.size(); i++) {
            String exchangesymbol = fwdout.get(i).getExchangeSymbol();
            int id = Utilities.getIDFromExchangeSymbol(nifty50, exchangesymbol, "STK", "", "", "");
            if (id >= 0) {
                id = Utilities.getIDFromExchangeSymbol(fwdout, exchangesymbol, "FUT", expiry, "", "");
                s = fwdout.get(id);
                BeanSymbol s1 = s.clone(s);
                s1.setStreamingpriority(2);
                s1.setStrategy("DATA");
                s1.setExpiry(expiry);
                s1.setType("FUT");
                out.add(s1);
            } else {
                id = Utilities.getIDFromExchangeSymbol(fwdout, exchangesymbol, "FUT", expiry, "", "");
                s = fwdout.get(id);
                BeanSymbol s1 = s.clone(s);
                s1.setStreamingpriority(3);
                s1.setStrategy("DATA");
                out.add(s1);
            }
        }

        printToFile(out, this.f_RateServer, false);
    }

    public void historicalstocks() throws MalformedURLException, IOException, ParseException {
        ArrayList<BeanSymbol> out = new ArrayList<>();
        BeanSymbol s = new BeanSymbol("NIFTY50", "NSENIFTY", "IND", "", "", "");
        s.setCurrency("INR");
        s.setExchange("NSE");
        s.setStreamingpriority(1);
        s.setStrategy("DATA");
        s.setDisplayname("NSENIFTY");
        out.add(s);
        out.addAll(cnx500);
        for(int i=0;i<cnx500.size();i++){
           cnx500.get(i).setDisplayname(cnx500.get(i).getExchangeSymbol().replaceAll("[^A-Za-z0-9]", ""));
        }
        printToFile(out, this.f_HistoricalStocks, true);
    }

    public void historicalfutures() throws MalformedURLException, IOException, ParseException {
        ArrayList<BeanSymbol> out = new ArrayList<>();
        String expiry = getNextExpiry(currentDay);
        BeanSymbol s = new BeanSymbol("NIFTY50", "NSENIFTY", "FUT", expiry, "", "");
        s.setCurrency("INR");
        s.setExchange("NSE");
        s.setStreamingpriority(1);
        s.setStrategy("DATA");
        s.setStrikeDistance(100);
        out.add(s);
        out.addAll(fno);
        for (int i = 0; i < out.size(); i++) {
            out.get(i).setDisplayname(out.get(i).getExchangeSymbol().replaceAll("[^A-Za-z0-9]", ""));
        }
        printToFile(out, this.f_HistoricalFutures, true);
    }

    public void historicalfuturesfwd() throws MalformedURLException, IOException, ParseException {
        ArrayList<BeanSymbol> out = new ArrayList<>();
        String expiry = getNextExpiry(currentDay);
        expiry = getNextExpiry(expiry);
        BeanSymbol s = new BeanSymbol("NIFTY50", "NSENIFTY", "FUT", expiry, "", "");
        s.setCurrency("INR");
        s.setExchange("NSE");
        s.setStreamingpriority(1);
        s.setStrategy("DATA");
        s.setStrikeDistance(100);
        out.add(s);
        ArrayList<BeanSymbol> fwdout = loadFutures(this.fnolotsizeurl, this.f_Strikes, expiry);
        out.addAll(fwdout);
        for (int i = 0; i < out.size(); i++) {
            out.get(i).setDisplayname(out.get(i).getExchangeSymbol().replaceAll("[^A-Za-z0-9]", ""));
        }
        printToFile(out, this.f_HistoricalFuturesFwd, true);
    }

    public void swing() throws MalformedURLException, IOException, ParseException {
        ArrayList<BeanSymbol> out = new ArrayList<>();
        String expiry = getNextExpiry(currentDay);
        BeanSymbol s = new BeanSymbol("NIFTY50", "NSENIFTY", "IND", "", "", "");
        s.setCurrency("INR");
        s.setExchange("NSE");
        s.setStreamingpriority(1);
        s.setStrategy("SWING");
        out.add(s);
        s = new BeanSymbol("NIFTY50", "NSENIFTY", "FUT", expiry, "", "");
        s.setCurrency("INR");
        s.setExchange("NSE");
        s.setStreamingpriority(1);
        s.setStrategy("SWING");
        s.setMinsize(75);
        s.setStrikeDistance(100);
        out.add(s);
        s = new BeanSymbol("NIFTY50", "NSENIFTY", "FUT", this.getNextExpiry(expiry), "", "");
        s.setCurrency("INR");
        s.setExchange("NSE");
        s.setStreamingpriority(1);
        s.setStrategy("SWING");
        s.setMinsize(75);
        s.setStrikeDistance(100);
        out.add(s);
        
        ArrayList<BeanSymbol> out2 = new ArrayList<>();
       for(int i=0;i<nifty50.size();i++){
           s=nifty50.get(i);
           s.setStrategy("MANAGER");
           out2.add(s);
       }
        fno= loadFutures(this.fnolotsizeurl, this.f_Strikes, expiry);
        for(int i=0;i<fno.size();i++){
            String exchangesymbol = fno.get(i).getExchangeSymbol();
            int id = Utilities.getIDFromExchangeSymbol(nifty50, exchangesymbol, "STK", "", "", "");
            if (id >= 0) {
                id = Utilities.getIDFromExchangeSymbol(fno, exchangesymbol, "FUT", expiry, "", "");
                s = fno.get(id);
                BeanSymbol s1 = s.clone(s);
                s1.setStreamingpriority(2);
                s1.setStrategy("MANAGER");
                out2.add(s1);
            }
        }
        ArrayList<BeanSymbol> fwdout = loadFutures(this.fnolotsizeurl, this.f_Strikes, getNextExpiry(expiry));
        for(int i=0;i<fno.size();i++){
            String exchangesymbol = fno.get(i).getExchangeSymbol();
            int id = Utilities.getIDFromExchangeSymbol(nifty50, exchangesymbol, "STK", "", "", "");
            if (id >= 0) {
                id = Utilities.getIDFromExchangeSymbol(fwdout, exchangesymbol, "FUT", getNextExpiry(expiry), "", "");
                s = fwdout.get(id);
                BeanSymbol s1 = s.clone(s);
                s1.setStreamingpriority(2);
                s1.setStrategy("MANAGER");
                out2.add(s1);
            }
        }
        out.addAll(out2);
        printToFile(out, this.f_Swing, false);
    }

    public void contra() throws IOException, ParseException {
        ArrayList<BeanSymbol> out = new ArrayList<>();
        out.addAll(nifty50);
        out.addAll(fno);
        String expiry = getNextExpiry(currentDay);
        expiry = getNextExpiry(expiry);
        ArrayList<BeanSymbol> fwdout = loadFutures(this.fnolotsizeurl, this.f_Strikes, expiry);
        out.addAll(fwdout);
        for (int i = 0; i < out.size(); i++) {
            out.get(i).setStrategy("MANAGER");
        }
        printToFile(out, "04-symbols-inr.csv", false);

    }

    public void allsymbols(){
        printToFile(symbols,"symbols-inr.csv",true);
    }
    
    public void extractSymbolsFromIB(String urlName, String fileName, List<BeanSymbol> symbols) throws IOException {
        String constant = "&page=";
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
                    Elements rows = tbl.select("tr");
                    int i = 0;
                    if(rows.size()==1){
                        break;
                    }
                    for (Element stockRow : rows) {
                        if (i >= 2) {                           
                            //if (stockRow.attr("class").equals("linebottom")) {
                            BeanSymbol tempContract = new BeanSymbol();
                            String tempIBSymbol = stockRow.getElementsByTag("td").get(0).text().toUpperCase().trim();
                            String tempLongName = stockRow.getElementsByTag("td").get(1).text().toUpperCase().trim();
                            String tempContractID = stockRow.getElementsByTag("td").get(1).getElementsByTag("a").get(0).attr("href").split("&")[2].split("conid=")[1].split("'")[0].toUpperCase().trim();
                            String tempCurrency = stockRow.getElementsByTag("td").get(3).text().toUpperCase().trim();
                            String tempExchangeSymbol = stockRow.getElementsByTag("td").get(2).text().toUpperCase().trim();
                            tempContract.setContractID(Utilities.getInt(tempContractID, 0));
                            tempContract.setCurrency(tempCurrency);
                            tempContract.setBrokerSymbol(tempIBSymbol);
                            tempContract.setLongName(tempLongName);
                            int index = tempExchangeSymbol.indexOf("_");
                            tempExchangeSymbol = index > 0 ? tempExchangeSymbol.substring(0, index) : tempExchangeSymbol;
                            tempContract.setExchangeSymbol(tempExchangeSymbol);
                            tempContract.setExchange(exchange);
                            tempContract.setType(type);
                            symbols.add(tempContract);
                            System.out.println(tempContract.getExchangeSymbol());
                        }
                        i++;
                    }
                }
            }
            //add serial nos
            //update serial nos
            for (int k = 0; k < symbols.size(); k++) {
                symbols.get(k).setSerialno(k + 1);
            }
            Utilities.deleteFile(fileName);
            //Save to filename, if provided
            if (fileName != null) {
                for (BeanSymbol c : symbols) {
                    c.writer(fileName);
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
    public String getNextExpiry(String currentDay) throws IOException, ParseException {
        SimpleDateFormat sdf_yyyMMdd = new SimpleDateFormat("yyyyMMdd");
        Date today = sdf_yyyMMdd.parse(currentDay);
        Calendar cal_today = Calendar.getInstance(TimeZone.getTimeZone(Algorithm.timeZone));
        cal_today.setTime(today);
        int year = Utilities.getInt(currentDay.substring(0, 4), 0);
        int month = Utilities.getInt(currentDay.substring(4, 6), 0) - 1;//calendar month starts at 0
        Date expiry = getLastThursday(month, year);
        expiry = Utilities.nextGoodDay(expiry, 0, Algorithm.timeZone, Algorithm.openHour, Algorithm.openMinute, Algorithm.closeHour, Algorithm.closeMinute, null, true);
        Calendar cal_expiry = Calendar.getInstance(TimeZone.getTimeZone(Algorithm.timeZone));
        cal_expiry.setTime(expiry);
        if (cal_expiry.get(Calendar.DAY_OF_MONTH) > cal_today.get(Calendar.DAY_OF_MONTH)) {
            return sdf_yyyMMdd.format(expiry);
        } else {
            if (month == 11) {//we are in decemeber
                //expiry will be at BOD, so we get the next month, till new month==0
                while (month != 0) {
                    expiry = Utilities.nextGoodDay(expiry, 24 * 60, Algorithm.timeZone, Algorithm.openHour, Algorithm.openMinute, Algorithm.closeHour, Algorithm.closeMinute, null, true);
                    year = Utilities.getInt(sdf_yyyMMdd.format(expiry).substring(0, 4), 0);
                    month = Utilities.getInt(sdf_yyyMMdd.format(expiry).substring(4, 6), 0) - 1;//calendar month starts at 0
                }
                expiry = getLastThursday(month, year);
                expiry = Utilities.nextGoodDay(expiry, 0, Algorithm.timeZone, Algorithm.openHour, Algorithm.openMinute, Algorithm.closeHour, Algorithm.closeMinute, null, true);
                return sdf_yyyMMdd.format(expiry);
            } else {
                expiry = getLastThursday(month + 1, year);
                expiry = Utilities.nextGoodDay(expiry, 0, Algorithm.timeZone, Algorithm.openHour, Algorithm.openMinute, Algorithm.closeHour, Algorithm.closeMinute, null, true);
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
}
