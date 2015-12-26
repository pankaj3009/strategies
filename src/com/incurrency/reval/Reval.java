/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.reval;

import com.incurrency.framework.Algorithm;
import com.incurrency.framework.BeanSymbol;
import com.incurrency.framework.Database;
import com.incurrency.framework.DateUtil;
import com.incurrency.framework.EnumBarSize;
import com.incurrency.framework.EnumOrderSide;
import static com.incurrency.framework.EnumOrderSide.BUY;
import static com.incurrency.framework.EnumOrderSide.SHORT;
import com.incurrency.framework.RedisConnect;
import com.incurrency.framework.Trade;
import com.incurrency.framework.TradingUtil;
import com.incurrency.framework.Utilities;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.kairosdb.client.HttpClient;
import org.kairosdb.client.builder.DataPoint;
import org.kairosdb.client.builder.QueryBuilder;
import org.kairosdb.client.builder.QueryMetric;
import org.kairosdb.client.response.QueryResponse;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 *
 * @author Pankaj
 */
public class Reval {

    private Properties properties;
    private String redisurl;
    private String uri;
    private int port;
    private int database;
    private String sStartDate;
    private String sEndDate;
    private JedisPool pool;
    private Database<String, String> db;
    private static final Logger logger = Logger.getLogger(Reval.class.getName());

    public Reval(String propertyFileName) throws ParseException {
        properties = Utilities.loadParameters(propertyFileName);
        redisurl = properties.getProperty("redisurl");
        sStartDate = properties.getProperty("startdate");
        sEndDate = properties.getProperty("enddate");
        uri = redisurl.split(":")[0];
        port = Integer.valueOf(redisurl.split(":")[1]);
        database = Integer.valueOf(redisurl.split(":")[2]);
        pool = new redis.clients.jedis.JedisPool(new JedisPoolConfig(), uri, port, 2000, null, database);
        db = new RedisConnect(uri, port, database);
        calculatePNL();
    }

    private void calculatePNL() throws ParseException {
        //Get all keys for closedorders
        //get all strategy:accountname combo
        Set<String> keys1 = db.getKeys("opentrades");
        Set<String> keys2 = db.getKeys("closedtrades");
        keys1.addAll(keys2);
        Set<String> strategyaccount = new HashSet<String>();
        for (String key : keys1) {
            String temp = key.split(":")[0].split("_")[1] + ":" + key.split(":")[2];
            strategyaccount.add(temp);
        }
        for (String s : strategyaccount) {
            String startDate = getDateOfLastPNLRecord(s);
            if (startDate.equals("")) {
                startDate = this.sStartDate;
            }
            createpnlrecords(s, startDate, this.sEndDate);
        }
        //for each strategyaccountname
        //get last pnl record date
        //get keys for strategyaccountname
        //for each key, if entrydate<=enddate
        // if exitdate>lastpnlrecord && entrydate<=startdate, calculate pnl
        // 
    }

    private String getDateOfLastPNLRecord(String strategyaccountname) {
        String yesterday = "";
        Set<String> dates = db.getKeys("pnl");
        for (String d : dates) {
            if (d.contains(strategyaccountname)) {
                int len = d.split("_")[1].split(":").length;
                String yesterday1 = d.split("_")[1].split(":")[len - 1];
                yesterday1 = new SimpleDateFormat("yyyyMMdd").format(DateUtil.parseDate("yyyy-MM-dd", yesterday1));
                if (yesterday1.compareTo(sStartDate) < 0 && yesterday.compareTo(yesterday1) < 0) {
                    yesterday = yesterday1;
                }
            }
        }
        if (!yesterday.equals("")) {
            yesterday = new SimpleDateFormat("yyyyMMdd").format(DateUtil.parseDate("yyyyMMdd", yesterday));
        } else {
            yesterday = sStartDate;
        }
        return yesterday;

//                    Double yesterdayPNL=Utilities.getDouble(db.getValue("pnl", strategyaccountname + ":" + yesterday, "ytd"),0);

    }

    private void createpnlrecords(String strategyaccount, String startDate, String endDate) throws ParseException {
        //get all closed trades in ascending order
        TreeMap<Long, String> pair = new TreeMap<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        for (String key : db.getKeys("closedtrades")) {
            if (key.contains(strategyaccount.split(":")[0])) {
                pair.put(sdf.parse(Trade.getExitTime(db, key)).getTime(), key);
            }
        }
        for (String key : db.getKeys("opentrades")) {
            if (key.contains(strategyaccount.split(":")[0])) {
                pair.put(sdf.parse(Trade.getEntryTime(db, key)).getTime(), key);
            }
        }
        double ytdPNL = 0;
        int longwins = 0;
        int longlosses = 0;
        int shortwins = 0;
        int shortlosses = 0;
        int tradeCount = 0;
        boolean firstCalc = true;
        ArrayList<Double> dailyEquity = new ArrayList();
        ArrayList<String> pnlDates = new ArrayList<>();
        pnlDates.add(startDate);
        for (Date d = DateUtil.parseDate("yyyyMMdd", startDate); d.compareTo(DateUtil.parseDate("yyyyMMdd", endDate)) <= 0; d=DateUtil.addDays(d, 1)) {
            String temp = new SimpleDateFormat("yyyyMMdd").format(TradingUtil.nextGoodDay(d, 24 * 60, Algorithm.timeZone, 9, 15, 15, 30, Algorithm.holidays, true));
            if (temp.compareTo(endDate) <= 0) {
                if(!pnlDates.contains(temp)){
                    pnlDates.add(temp);
                }
            }
        }
        for (String d : pnlDates) {
            for (String key : pair.values()) {
                String entryTime = Trade.getEntryTime(db, key);
                String exitTime = Trade.getExitTime(db, key);
                entryTime = new SimpleDateFormat("yyyyMMdd").format(DateUtil.parseDate("yyyy-MM-dd", entryTime.substring(0, 10)));
                if(!exitTime.equals("")){
                    exitTime = new SimpleDateFormat("yyyyMMdd").format(DateUtil.parseDate("yyyy-MM-dd", exitTime.substring(0, 10)));
                }
                double exitPrice = Trade.getExitPrice(db, key);
                double entryPrice = Trade.getEntryPrice(db, key);
                int exitSize = Trade.getExitSize(db, key);
                double entryBrokerage = Trade.getEntryBrokerage(db, key);
                double exitBrokerage = Trade.getExitBrokerage(db, key);
                EnumOrderSide entrySide = Trade.getEntrySide(db, key);
                if (firstCalc) {
                    if (entryTime.compareTo(d) <= 0 && !exitTime.equals("")&& exitTime.compareTo(d) <= 0) {//trades that are completed before the startdate
                        if (key.contains("closedtrades")) {//no mtm needed
                            double buypnl = (exitSize) * (exitPrice - entryPrice);
                            double tradePNL = entrySide == EnumOrderSide.BUY ? buypnl : -buypnl;
                            ytdPNL = ytdPNL + tradePNL - entryBrokerage - exitBrokerage;
                            EnumOrderSide side = Trade.getEntrySide(db, key);
                            switch (side) {
                                case BUY:
                                    if (Trade.getExitPrice(db, key) > Trade.getEntryPrice(db, key)) {
                                        longwins = longwins + 1;
                                    } else {
                                        longlosses = longlosses + 1;
                                    }
                                    break;
                                case SHORT:
                                    if (Trade.getExitPrice(db, key) < Trade.getEntryPrice(db, key)) {
                                        shortwins = shortwins + 1;
                                    } else {
                                        shortlosses = shortlosses + 1;
                                    }
                                    break;
                                default:
                                    break;
                            }
                            tradeCount = tradeCount + 1;

                        } else { //mtm needed
                            int entrySize = Trade.getEntrySize(db, key);
                            String symbolFullName = Trade.getEntrySymbol(db, key);
                            BeanSymbol s = new BeanSymbol(symbolFullName);
                            double mtmToday = this.getSettlePrice(Algorithm.cassandraIP,s, DateUtil.parseDate("yyyyMMdd", startDate));
//                    Utilities.requestHistoricalData(s, new String[]{"settle"}, "india.nse.fut.s4.daily", "yyyyMMdd", startDate, startDate, EnumBarSize.DAILY, false);
                            //                  double mtmToday = s.getTimeSeriesValue(EnumBarSize.DAILY, 0, "settle");
                            double buypnl = exitSize * (exitPrice - entryPrice) + (entrySize - exitSize) * (mtmToday - entryPrice);
                            double tradePNL = entrySide == EnumOrderSide.BUY ? buypnl : -buypnl;
                            ytdPNL = ytdPNL + tradePNL - entryBrokerage - exitBrokerage;
                        }
                    }else{
                    firstCalc = false;
                    }
                }

                if (!firstCalc && entryTime.compareTo(d) <= 0 && (exitTime.equals("")||exitTime.compareTo(d) >= 0)) {
                    if (exitTime.compareTo(d)==0 ) {//no mtm needed
                        if(entryTime.compareTo(d)!=0){
                            entryPrice=Trade.getMtmToday(db, key);
                        }
                        double buypnl = (exitSize) * (exitPrice - entryPrice);
                        double tradePNL = entrySide == EnumOrderSide.BUY ? buypnl : -buypnl;
                        ytdPNL = ytdPNL + tradePNL - exitBrokerage;
                        if(entryTime.compareTo(d)==0){
                            ytdPNL=ytdPNL-entryBrokerage;
                        }
                        EnumOrderSide side = Trade.getEntrySide(db, key);
                        switch (side) {
                            case BUY:
                                if (Trade.getExitPrice(db, key) > Trade.getEntryPrice(db, key)) {
                                    longwins = longwins + 1;
                                } else {
                                    longlosses = longlosses + 1;
                                }
                                break;
                            case SHORT:
                                if (Trade.getExitPrice(db, key) < Trade.getEntryPrice(db, key)) {
                                    shortwins = shortwins + 1;
                                } else {
                                    shortlosses = shortlosses + 1;
                                }
                                break;
                            default:
                                break;
                        }
                        tradeCount = tradeCount + 1;

                    } else { //mtm needed
                        int entrySize = Trade.getEntrySize(db, key);
                        String symbolFullName = Trade.getEntrySymbol(db, key);
                        BeanSymbol s = new BeanSymbol(symbolFullName);
                        double mtmToday = this.getSettlePrice(Algorithm.cassandraIP,s, DateUtil.parseDate("yyyyMMdd", d));
                        String tradeStatus=key.contains("closedtrades")?"closedtrades":"opentrades";
                        if(!entryTime.equals(d)){
                            entryPrice=Trade.getMtmToday(db, key);
                        }
                        Trade.setMtmToday(db, key, tradeStatus, mtmToday);
                        double buypnl = entrySize * (mtmToday - entryPrice) ;
                        double tradePNL = entrySide == EnumOrderSide.BUY ? buypnl : -buypnl;
                        ytdPNL = ytdPNL + tradePNL - entryBrokerage;
                    }

                }else if(entryTime.compareTo(d) > 0 ){
                    break;
                }
            }


//Add last record

            dailyEquity.add(1000000 + ytdPNL);
            double drawdowndaysmax = TradingUtil.drawdownDays(dailyEquity)[0];
            double drawdownpercentage = TradingUtil.maxDrawDownPercentage(dailyEquity);
            double sharpe = TradingUtil.sharpeRatio(dailyEquity);
            double winratio = longwins + shortwins + longlosses + shortlosses > 0 ? ((longwins + shortwins) * 100 / (longwins + shortwins + longlosses + shortlosses)) : 0;
            double longwinratio = longwins + longlosses > 0 ? (longwins * 100 / (longwins + longlosses)) : 0;
            double shortwinratio = shortwins + shortlosses > 0 ? (shortwins * 100 / (shortwins + shortlosses)) : 0;
            String sd = new SimpleDateFormat("yyyy-MM-dd").format(DateUtil.parseDate("yyyyMMdd", d));
            db.setHash("pnl", strategyaccount + ":" + sd, "ytd", String.valueOf(Utilities.round(ytdPNL, 0)));
            db.setHash("pnl", strategyaccount + ":" + sd, "winratio", String.valueOf(Utilities.round(winratio, 0)));
            db.setHash("pnl", strategyaccount + ":" + sd, "longwinratio", String.valueOf(Utilities.round(longwinratio, 0)));
            db.setHash("pnl", strategyaccount + ":" + sd, "shortwinratio", String.valueOf(Utilities.round(shortwinratio, 0)));
            db.setHash("pnl", strategyaccount + ":" + sd, "tradecount", String.valueOf(tradeCount));
            double todaypnl = dailyEquity.size() > 1 ? dailyEquity.get(dailyEquity.size() - 1) - dailyEquity.get(dailyEquity.size() - 2) : dailyEquity.get(dailyEquity.size() - 1) - 1000000;
            db.setHash("pnl", strategyaccount + ":" + sd, "todaypnl", String.valueOf(todaypnl));
            db.setHash("pnl", strategyaccount + ":" + sd, "sharpe", String.valueOf(Utilities.round(sharpe, 2)));
            db.setHash("pnl", strategyaccount + ":" + sd, "drawdowndaysmax", String.valueOf(Utilities.round(drawdowndaysmax, 0)));
            db.setHash("pnl", strategyaccount + ":" + sd, "drawdownpercentmax", String.valueOf(Utilities.round(drawdownpercentage, 2)));
        }
    }

    public double getSettlePrice(String url,BeanSymbol s, Date d) {
        double settlePrice = -1;
        try {
            HttpClient client = new HttpClient("http://"+url+":8085");
            String metric;
            switch (s.getType()) {
                case "STK":
                    metric = "india.nse.equity.s4.daily.settle";
                    break;
                case "FUT":
                    metric = "india.nse.future.s4.daily.settle";
                    break;
                case "OPT":
                    metric = "india.nse.option.s4.daily.settle";
                    break;
                default:
                    metric = null;
                    break;
            }
            Date startDate = d;
            Date endDate = d;
            QueryBuilder builder = QueryBuilder.getInstance();
            builder.setStart(startDate)
                    .setEnd(DateUtil.addSeconds(d, 1))
                    .addMetric(metric)
                    .addTag("symbol", s.getBrokerSymbol().toLowerCase());
            if (!s.getExpiry().equals("")) {
                builder.getMetrics().get(0).addTag("expiry", s.getExpiry());
            }
            if (!s.getRight().equals("")) {
                builder.getMetrics().get(0).addTag("option", s.getRight());
                builder.getMetrics().get(0).addTag("strike", s.getOption());
            }

            builder.getMetrics().get(0).setLimit(1);
            builder.getMetrics().get(0).setOrder(QueryMetric.Order.DESCENDING);
            long time = new Date().getTime();
            QueryResponse response = client.query(builder);

            List<DataPoint> dataPoints = response.getQueries().get(0).getResults().get(0).getDataPoints();
            for (DataPoint dataPoint : dataPoints) {
                long lastTime = dataPoint.getTimestamp();
                Object value = dataPoint.getValue();
                settlePrice = Double.parseDouble(value.toString());
            }
        } catch (Exception e) {
            logger.log(Level.INFO, null, e);
        }
        return settlePrice;
    }

    private Long setHash(String key, String field, String value) {
        try (Jedis jedis = pool.getResource()) {
            if (!value.equals("")) {
                return jedis.hset(key, field.toString(), value.toString());
            } else {
                return -1L;
            }

        }
    }

    private String getValue(String key, String field) {
        try (Jedis jedis = pool.getResource()) {
            Object out = jedis.hget(key, field.toString());
            if (out != null) {
                return jedis.hget(key, field.toString());
            } else {
                return "";
            }
        }
    }

    private Set<String> getKeys(String key) {
        Set<String> out = new HashSet<>();
        try (Jedis jedis = pool.getResource()) {
            out = jedis.keys(key + "*");
        }
        return out;
    }
}
