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
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeMap;
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
        Set<String>keys1=db.getKeys("opentrades");
        Set<String>keys2=db.getKeys("closedtrades");
        keys1.addAll(keys2);
        Set<String>strategyaccount=new HashSet<String>();
        for(String key:keys1){
            String temp=key.split(":")[0].split("_")[1]+":"+key.split(":")[2];
            strategyaccount.add(temp);
        }
        for(String s:strategyaccount){
            String startDate=getDateOfLastPNLRecord(s);
            if(startDate.equals("")){
                startDate=this.sStartDate;
            }
            createpnlrecords(s,startDate,this.sEndDate);
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
    yesterday1= new SimpleDateFormat("yyyyMMdd").format(DateUtil.parseDate("yyyy-MM-dd", yesterday1));       
                if (yesterday1.compareTo(sStartDate) < 0 && yesterday.compareTo(yesterday1) < 0) {
                    yesterday = yesterday1;
                }
            }
        }
        if(!yesterday.equals("")){
    yesterday= new SimpleDateFormat("yyyyMMdd").format(DateUtil.parseDate("yyyy-MM-dd", yesterday));       
        }else{
            yesterday=sStartDate;
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
        ArrayList<Double> dailyEquity = new ArrayList();
        for (String key : pair.values()) {
            String entryTime = Trade.getEntryTime(db, key);
            entryTime = new SimpleDateFormat("yyyyMMdd").format(DateUtil.parseDate("yyyy-MM-dd", entryTime.substring(0, 10)));
            while (entryTime.compareTo(startDate) > 0 && entryTime.compareTo(endDate)<=0) {
                dailyEquity.add(1000000 + ytdPNL);
                double drawdowndaysmax = TradingUtil.drawdownDays(dailyEquity)[0];
                double drawdownpercentage = TradingUtil.maxDrawDownPercentage(dailyEquity);
                double sharpe = TradingUtil.sharpeRatio(dailyEquity);
                double winratio = longwins + shortwins + longlosses + shortlosses > 0 ? ((longwins + shortwins) * 100 / (longwins + shortwins + longlosses + shortlosses)) : 0;
                double longwinratio = longwins + longlosses > 0 ? (longwins * 100 / (longwins + longlosses)) : 0;
                double shortwinratio = shortwins + shortlosses > 0 ? (shortwins * 100 / (shortwins + shortlosses)) : 0;
                String sd = new SimpleDateFormat("yyyy-MM-dd").format(DateUtil.parseDate("yyyyMMdd", startDate));
                db.setHash("pnl", strategyaccount + ":" + sd, "ytd", String.valueOf(Utilities.round(ytdPNL, 0)));
                db.setHash("pnl", strategyaccount + ":" + sd, "winratio", String.valueOf(Utilities.round(winratio, 0)));
                db.setHash("pnl", strategyaccount + ":" + sd, "longwinratio", String.valueOf(Utilities.round(longwinratio, 0)));
                db.setHash("pnl", strategyaccount + ":" + sd, "shortwinratio", String.valueOf(Utilities.round(shortwinratio, 0)));
                db.setHash("pnl", strategyaccount + ":" + sd, "tradecount", String.valueOf(tradeCount));
                double todaypnl = dailyEquity.size() > 1 ? dailyEquity.get(dailyEquity.size() - 1) - dailyEquity.get(dailyEquity.size() - 2) : dailyEquity.get(dailyEquity.size() - 1)-1000000;
                db.setHash("pnl", strategyaccount + ":" + sd, "todaypnl", String.valueOf(todaypnl));
                db.setHash("pnl", strategyaccount + ":" + sd, "sharpe", String.valueOf(Utilities.round(sharpe, 2)));
                db.setHash("pnl", strategyaccount + ":" + sd, "drawdowndaysmax", String.valueOf(Utilities.round(drawdowndaysmax, 0)));
                db.setHash("pnl", strategyaccount + ":" + sd, "drawdownpercentmax", String.valueOf(Utilities.round(drawdownpercentage, 2)));
                //get next startDate
                startDate = new SimpleDateFormat("yyyyMMdd").format(TradingUtil.nextGoodDay(DateUtil.parseDate("yyyyMMdd", startDate), 24*60, Algorithm.timeZone, 9, 15, 15, 30, Algorithm.holidays, true));
            }

            if (entryTime.compareTo(startDate) <= 0 && entryTime.compareTo(endDate) < 0) {
                double exitPrice = Trade.getExitPrice(db, key);
                double entryPrice = Trade.getEntryPrice(db, key);
                int exitSize = Trade.getExitSize(db, key);
                double entryBrokerage = Trade.getEntryBrokerage(db, key);
                double exitBrokerage = Trade.getExitBrokerage(db, key);
                EnumOrderSide entrySide = Trade.getEntrySide(db, key);
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

                    Utilities.requestHistoricalData(s, new String[]{"settle"}, "india.nse.fut.s4.daily", "yyyyMMdd", startDate, startDate, EnumBarSize.DAILY, false);
                    double mtmToday = s.getTimeSeriesValue(EnumBarSize.DAILY, 0, "settle");
                    double buypnl = exitSize * (exitPrice - entryPrice) + (entrySize - exitSize) * (mtmToday - entryPrice);
                    double tradePNL = entrySide == EnumOrderSide.BUY ? buypnl : -buypnl;
                    ytdPNL = ytdPNL + tradePNL - entryBrokerage - exitBrokerage;
                }
            } else {
            }

        }

        //Loop
        //if tradeentrydate<=startDate && tradeendtrydate>enddate, calculate pnl
        //else
        //createpnlrecord using startdate
        //calculate next startdate (using holiday calendar)
        //if nextstartdate>enddate break;
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
