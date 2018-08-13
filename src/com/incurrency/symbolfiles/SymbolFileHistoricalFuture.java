/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.symbolfiles;

import com.incurrency.framework.Algorithm;
import com.incurrency.framework.BeanSymbol;
import com.incurrency.framework.DateUtil;
import com.incurrency.framework.MainAlgorithm;
import com.incurrency.framework.RedisConnect;
import com.incurrency.framework.Utilities;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 *
 * @author psharma
 */
public class SymbolFileHistoricalFuture {

    private static final Logger logger = Logger.getLogger(SymbolFileHistoricalFuture.class.getName());

    public static JedisPool RedisConnect(String uri, Integer port, Integer database) {
        return new JedisPool(new JedisPoolConfig(), uri, port, 10000, null, database);
    }

    private RedisConnect jPool;
    private String currentDay;
    private List<BeanSymbol> nifty50 = new ArrayList<>();
    private List<BeanSymbol> symbols = new ArrayList<>();
    private List<BeanSymbol> cnx500 = new ArrayList<>();
    private String symbolFileName;

    public SymbolFileHistoricalFuture(String redisurl, String symbolFileName) {
        this.symbolFileName = symbolFileName;
        jPool = new RedisConnect(redisurl.split(":")[0], Integer.valueOf(redisurl.split(":")[1]), Integer.valueOf(redisurl.split(":")[2]));
        currentDay = DateUtil.getFormatedDate("yyyyMMdd", new Date().getTime(), TimeZone.getTimeZone(MainAlgorithm.timeZone));
        loadAllSymbols();
        nifty50 = loadNifty50Stocks();
        cnx500 = loadNifty50Stocks();
        historicalFuture();
    }

    public void historicalFuture() {
        ArrayList<BeanSymbol> out = new ArrayList<>();
        String expiry = Utilities.getLastThursday(currentDay, "yyyyMMdd", 0);
        BeanSymbol s = new BeanSymbol("NIFTY50", "NSENIFTY", "FUT", expiry, "", "");
        s.setCurrency("INR");
        s.setExchange("NSE");
        s.setStreamingpriority(1);
        s.setStrategy("DATA");
        s.setStrikeDistance(100);
        out.add(s.clone(s));
        ArrayList<BeanSymbol> fno = loadFutures(expiry);
        out.addAll(fno);
        Date dtExpiry = DateUtil.parseDate("yyyyMMdd", expiry, MainAlgorithm.timeZone);
        String expiryplus = DateUtil.getFormatedDate("yyyyMMdd", DateUtil.addDays(dtExpiry, 1).getTime(), TimeZone.getTimeZone(Algorithm.timeZone));
        String nextExpiry = Utilities.getLastThursday(expiryplus, "yyyyMMdd", 0);
        s = new BeanSymbol("NIFTY50", "NSENIFTY", "FUT", nextExpiry, "", "");
        s.setCurrency("INR");
        s.setExchange("NSE");
        s.setStreamingpriority(1);
        s.setStrategy("DATA");
        s.setStrikeDistance(100);
        out.add(s.clone(s));
        ArrayList<BeanSymbol> fwdout = loadFutures(nextExpiry);
        out.addAll(fwdout);
        for (int i = 0; i < out.size(); i++) {
            //   out.get(i).setDisplayname(out.get(i).getExchangeSymbol().replaceAll("[^A-Za-z0-9]", ""));
            //out.get(i).setDisplayname(out.get(i).getExchangeSymbol().replaceAll(" ", ""));
        }

        Utilities.printSymbolsToFile(out, symbolFileName, true);
    }

    public void loadAllSymbols() {
        String today = DateUtil.getFormatedDate("yyyyMMdd", new Date().getTime(), TimeZone.getTimeZone(Algorithm.timeZone));
        String shortlistedkey = Utilities.getShorlistedKey(jPool, "ibsymbols", today);
        Map<String, String> ibsymbols = new HashMap<>();
        try (Jedis jedis = jPool.pool.getResource()) {
            ibsymbols = jedis.hgetAll(shortlistedkey);
            for (Map.Entry<String, String> entry : ibsymbols.entrySet()) {
                String exchangeSymbol = entry.getKey().trim().toUpperCase();
                String brokerSymbol = entry.getValue().trim().toUpperCase();
                BeanSymbol tempContract = new BeanSymbol();
                tempContract.setExchange("NSE");
                tempContract.setCurrency("INR");
                tempContract.setType("STK");
                tempContract.setExchangeSymbol(exchangeSymbol);
                tempContract.setBrokerSymbol(brokerSymbol);
                tempContract.setSerialno(symbols.size());
                symbols.add(tempContract);
            }

        }

    }

    public ArrayList<BeanSymbol> loadNifty50Stocks() {
        ArrayList<BeanSymbol> out = new ArrayList<>();
        try {
            String today = DateUtil.getFormatedDate("yyyyMMdd", new Date().getTime(), TimeZone.getTimeZone(Algorithm.timeZone));
            String shortlistedkey = Utilities.getShorlistedKey(jPool, "nifty50", today);
            Set<String> niftySymbols = new HashSet<>();
            try (Jedis jedis = jPool.pool.getResource()) {
                niftySymbols = jedis.smembers(shortlistedkey);
                Iterator iterator = niftySymbols.iterator();
                while (iterator.hasNext()) {
                    String exchangeSymbol = iterator.next().toString().toUpperCase();
                    int id = Utilities.getIDFromExchangeSymbol(symbols, exchangeSymbol, "STK", "", "", "");
                    if (id >= 0) {
                        BeanSymbol s = symbols.get(id);
                        BeanSymbol s1 = s.clone(s);
                        out.add(s1);
                    } else {
                        logger.log(Level.SEVERE, "500,NIFTY50 symbol not found in ibsymbols,{0}:{1}:{2}:{3}:{4},SymbolNotFound={5}",
                                new Object[]{"Unknown", "Unknown", "Unknown", -1, -1, exchangeSymbol});
                    }
                }
            }
            for (int i = 0; i < out.size(); i++) {
                out.get(i).setSerialno(i);
            }

            //Capture Strike levels
            String expiry = Utilities.getLastThursday(currentDay, "yyyyMMdd", 0);;
            shortlistedkey = Utilities.getShorlistedKey(jPool, "strikedistance", expiry);
            Map<String, String> strikeLevels = new HashMap<>();
            try (Jedis jedis = jPool.pool.getResource()) {
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

    public ArrayList<BeanSymbol> loadFutures(String expiry) {
        ArrayList<BeanSymbol> out = new ArrayList<>();
        ArrayList<BeanSymbol> interimout = new ArrayList<>();
        try {
            String shortlistedkey = Utilities.getShorlistedKey(jPool, "contractsize", expiry);
            Map<String, String> contractSizes = new HashMap<>();
            try (Jedis jedis = jPool.pool.getResource()) {
                contractSizes = jedis.hgetAll(shortlistedkey);
                for (Map.Entry<String, String> entry : contractSizes.entrySet()) {
                    String exchangeSymbol = entry.getKey().trim().toUpperCase();
                    int minsize = Utilities.getInt(entry.getValue().trim(), 0);
                    if (minsize > 0) {
                        int id = Utilities.getIDFromExchangeSymbol(symbols, exchangeSymbol, "STK", "", "", "");
                        if (id >= 0) {
                            BeanSymbol s = symbols.get(id);
                            BeanSymbol s1 = s.clone(s);
                            s1.setType("FUT");
                            s1.setExpiry(expiry);
                            s1.setMinsize(minsize);
                            s1.setStrategy("DATA");
                            s1.setStreamingpriority(2);
                            s1.setSerialno(out.size());
                            interimout.add(s1);
                        } else {
                            logger.log(Level.SEVERE, "Exchange Symbol {0} not found in IB database", new Object[]{exchangeSymbol});
                        }
                    }
                }
            }

            //Fix sequential serial numbers
            for (int i = 0; i < interimout.size(); i++) {
                interimout.get(i).setSerialno(i);
            }

            //Capture Strike levels
            shortlistedkey = Utilities.getShorlistedKey(jPool, "strikedistance", expiry);
            Map<String, String> strikeLevels = new HashMap<>();
            try (Jedis jedis = jPool.pool.getResource()) {
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
            out.get(i).setSerialno(i);
        }
        return out;

    }

    public ArrayList<BeanSymbol> loadCNX500Stocks() {
        ArrayList<BeanSymbol> out = new ArrayList<>();
        try {
            String today = DateUtil.getFormatedDate("yyyyMMdd", new Date().getTime(), TimeZone.getTimeZone(Algorithm.timeZone));
            String shortlistedkey = Utilities.getShorlistedKey(jPool, "cnx500", today);
            Set<String> niftySymbols = new HashSet<>();
            try (Jedis jedis = jPool.pool.getResource()) {
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
                out.get(i).setSerialno(i);
            }

            //Capture Strike levels
            String expiry = Utilities.getLastThursday(currentDay, "yyyyMMdd", 0);;
            shortlistedkey = Utilities.getShorlistedKey(jPool, "strikedistance", expiry);
            Map<String, String> strikeLevels = new HashMap<>();
            try (Jedis jedis = jPool.pool.getResource()) {
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
}
