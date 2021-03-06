/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.algorithms.manual;

import com.google.common.reflect.TypeToken;
import com.incurrency.framework.Algorithm;
import com.incurrency.framework.BeanConnection;
import com.incurrency.framework.DateUtil;
import com.incurrency.framework.EnumOrderReason;
import com.incurrency.framework.EnumOrderSide;
import com.incurrency.framework.EnumOrderStage;
import com.incurrency.framework.Order.EnumOrderType;
import com.incurrency.framework.Mail;
import com.incurrency.framework.MainAlgorithm;
import com.incurrency.framework.OrderBean;
import com.incurrency.framework.Parameters;
import com.incurrency.framework.Stop;
import com.incurrency.framework.Strategy;
import com.incurrency.framework.Utilities;
import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Properties;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.ib.client.TickType;
import com.incurrency.RatesClient.RedisSubscribe;
import com.incurrency.framework.Trade;
import com.incurrency.framework.TradeEvent;
import com.incurrency.framework.TradeListener;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.rosuda.REngine.REXP;
import org.rosuda.REngine.Rserve.RConnection;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 *
 * @author psharma
 */
public class Manual extends Strategy implements TradeListener {

    private static final Logger logger = Logger.getLogger(Manual.class.getName());
    private WatchService watcher = null;
    String orderSource;
    Path dir;
    public boolean optionPricingUsingFutures = true;
    public String rServerIP;
    public Date RScriptRunTime;
    public String RStrategyFile;
    public String wd;
    public Boolean scaleEntry = Boolean.FALSE;
    public Boolean scaleExit = Boolean.FALSE;
    public Boolean useCashReferenceForSLTP = Boolean.FALSE;
    private final Object lockScan = new Object();

    public Manual(MainAlgorithm m, Properties p, String parameterFile, ArrayList<String> accounts, Boolean addTimers) {
        super(m, p, parameterFile, accounts);
        if (s_redisip != null) {
            loadParameters(p);
            String[] tempStrategyArray = parameterFile.split("\\.")[0].split("_");
            for (BeanConnection c : Parameters.connection) {
                c.initializeConnection(tempStrategyArray[tempStrategyArray.length - 1], -1);
                c.getWrapper().addTradeListener(this);
            }
            if (orderSource.compareToIgnoreCase("file") == 0) {
                try {
                    if (watcher == null) {
                        watcher = dir.getFileSystem().newWatchService();
                        dir.register(watcher,
                                ENTRY_CREATE,
                                 ENTRY_MODIFY
                        );
                    }
                    Timer tradeReader = new Timer("Timer: " + getStrategy() + " ReadTradesFromFile");
                    tradeReader.schedule(new ReadTrades(), new Date());
                } catch (Exception e) {
                    logger.log(Level.SEVERE, null, e);
                }
            }
            if (RedisSubscribe.tes != null) {
                RedisSubscribe.tes.addTradeListener(this);
            }

            if (addTimers) {
                scheduleTimers();
            }
        }
    }

    public void scheduleTimers() {
        rScriptTimer();
        orderTimer();
        marketDataTimer();

    }

    public void rScriptTimer() {
        if (rServerIP != null && RScriptRunTime != null && RStrategyFile != null) {
            Timer trigger = new Timer("Timer: " + this.getStrategy() + " RScriptProcessor");
            trigger.schedule(new RScript(), RScriptRunTime);
        }
    }

    public void orderTimer() {
        Timer monitor = new Timer("Timer: " + getStrategy() + " WaitForTrades");
        Date timerStart = new Date().after(getStartDate()) ? new Date() : getStartDate();
        monitor.schedule(new OrderProcessor(), timerStart);
        logger.log(Level.INFO,"501, Started OrderProcessor,,{0}",new Object[]{this.getStrategy()});
    }

    public void marketDataTimer() {
        Timer mdrequest = new Timer("Timer: " + this.getStrategy() + " WaitForMDRequest");
        mdrequest.schedule(new marketDataProcessor(), new Date());
    }

    @Override
    public void tradeReceived(TradeEvent event) {
        if (event.getTickType() == TickType.LAST) {
            int id = event.getSymbolID();
            if (this.getStrategySymbols().contains(Integer.valueOf(id))) {
                int position = this.getPosition().get(id).getPosition();
                double price = Parameters.symbol.get(id).getLastPrice();
                if (price > 0) {
                    HashSet<Integer> internalorderids = new HashSet<>();
                    if (position > 0) {
                        internalorderids = EntryInternalOrderIDsForSquareOff(id, "Order", getStrategy(), EnumOrderSide.SELL);
                    } else if (position < 0) {
                        internalorderids = EntryInternalOrderIDsForSquareOff(id, "Order", getStrategy(), EnumOrderSide.COVER);
                    }
                    for (int internalorderid : internalorderids) {
                        Boolean sltriggered = Boolean.FALSE;
                        Boolean tptriggered = Boolean.FALSE;
                        String key = "";
                        if (internalorderid > 0) {
                            key = "opentrades_" + getStrategy() + ":" + internalorderid + ":" + "Order";
                            String entrySymbol=Trade.getEntrySymbol(getDb(), key,this.getTickSize());
                            double sl=0;
                            double tp=0;
                            if (entrySymbol.contains("_PUT_") && useCashReferenceForSLTP) {
                                tp = Trade.getSL(getDb(), key);
                                sl = Trade.getTP(getDb(), key);
                            } else {
                                sl = Trade.getSL(getDb(), key);
                                tp = Trade.getTP(getDb(), key);
                            }
                            int referenceid = id;
                            if (useCashReferenceForSLTP) {
                                referenceid = Parameters.symbol.get(id).getUnderlyingCashID();
                            }
                            if (referenceid >= 0 & (sl > 0 || tp > 0)) {
                                price = Parameters.symbol.get(referenceid).getLastPrice();
                                sltriggered = price > 0 && ((position > 0 && sl > price && sl > 0) || (position < 0 && sl < price & sl > 0)) ? Boolean.TRUE : Boolean.FALSE;
                                tptriggered = price > 0 && ((position > 0 && tp < price & tp > 0) || (position < 0 && tp > price && tp > 0)) ? Boolean.TRUE : Boolean.FALSE;
                            }
                        }
                        if (sltriggered | tptriggered) {
                            if (Parameters.symbol.get(id).getBidPrice() >= Parameters.symbol.get(id).getLastPrice() * 0.1 && Parameters.symbol.get(id).getAskPrice() <= Parameters.symbol.get(id).getLastPrice() * 10) {
                                OrderBean ord = new OrderBean();
                                ord.setParentDisplayName(Parameters.symbol.get(id).getDisplayname());
                                ord.setChildDisplayName(Parameters.symbol.get(id).getDisplayname());
                                ord.setOrderSide(position > 0 ? EnumOrderSide.SELL : EnumOrderSide.COVER);
                                ord.setOrderReason(tptriggered ? EnumOrderReason.TP : EnumOrderReason.SL);
                                ord.setOrderType(this.getOrdType());
                                ord.setLimitPrice(tptriggered ? Utilities.round(Trade.getTP(getDb(), key),this.getTickSize()) : Utilities.round(Trade.getSL(getDb(), key),this.getTickSize()));
                                ord.setOrderStage(EnumOrderStage.INIT);
                                int size = Trade.getEntrySize(getDb(), key) - Trade.getExitSize(getDb(), key);
                                ord.setStrategyOrderSize(size);
                                ord.setOriginalOrderSize(size);
                                int startingpos = Utilities.getNetPosition(Parameters.symbol, getPosition(), id, true);
                                ord.setStrategyStartingPosition(Math.abs(startingpos));
                                ord.setScale(scaleExit);
                                ord.setOrderReference(getStrategy());
                                ord.setOrderKeyForSquareOff(key);
                                exit(ord);
                            } else {
                                logger.log(Level.SEVERE, "Symbol: {0}, BidPrice: {1}, AskPrice:{2}, LastPrice:{3}, Order Not sent for execution as the prices did not meet safety check",
                                        new Object[]{Parameters.symbol.get(id).getDisplayname(), Parameters.symbol.get(id).getBidPrice(), Parameters.symbol.get(id).getAskPrice(), Parameters.symbol.get(id).getLastPrice()});
                            }
                        }
                    }

                }
            }
            List<String> potentialOrders = this.getDb().scanRedis("monitoring:" + this.getStrategy() + ":" + Parameters.symbol.get(id).getDisplayname() + "*");
            for (String s : potentialOrders) {
                ConcurrentHashMap<String, String> keyvalue = this.getDb().getValues("", s);
                try {
                    if (keyvalue.get("status").equals("ACTIVE")) {
                        if (keyvalue.get("starttime") == null | (keyvalue.get("starttime") != null & new Date().compareTo(DateUtil.getFormattedDate(keyvalue.get("starttime"), "yyyy-MM-dd HH:mm:ss", timeZone)) > 0)) {
                            if (keyvalue.get("endtime") == null | (keyvalue.get("endtime") != null & new Date().compareTo(DateUtil.getFormattedDate(keyvalue.get("endtime"), "yyyy-MM-dd HH:mm:ss", timeZone)) < 0)) {
                                if (keyvalue.get("side").equals("BUY") || keyvalue.get("side").equals("SHORT") || keyvalue.get("side").equals("SELL") || keyvalue.get("side").equals("COVER") || keyvalue.get("side").equals("CLOSEALL")) {
                                    boolean placeOrder = false;
                                    if (keyvalue.get("condition").equals("BREACHBELOW")) {
                                        double conditionprice = Utilities.getDouble(keyvalue.get("conditionprice"), 0);
                                        double slippage = Utilities.getDouble(keyvalue.get("maxpercentslippage"), Double.MAX_VALUE);
                                        switch (keyvalue.get("barsize")) {
                                            case "TICK":
                                                if (conditionprice > Parameters.symbol.get(id).getLastPrice() && Math.abs(Parameters.symbol.get(id).getLastPrice() - conditionprice) * 100 / conditionprice < slippage) {
                                                    logger.log(Level.INFO, "201,Order Generated from monitor Key={0},barsize={1},Side={2}", new Object[]{keyvalue, keyvalue.get("barsize"), keyvalue.get("side")});
                                                    placeOrder = true;
                                                } else if (conditionprice > Parameters.symbol.get(id).getLastPrice() && Math.abs(Parameters.symbol.get(id).getLastPrice() - conditionprice) * 100 / conditionprice > slippage) {
                                                    logger.log(Level.INFO, "201,Monitor did not generate order as exceeding slippage. Key={0},barsize={1},Side={2},AllowedSlippage={3},CalculatedSlippage={4},LastPrice={5}", new Object[]{s, keyvalue.get("barsize"), keyvalue.get("side"), slippage,
                                                        Math.abs(Parameters.symbol.get(id).getLastPrice() - conditionprice) * 100 / conditionprice, Parameters.symbol.get(id).getLastPrice()});
                                                }
                                                break;
                                            default:
                                                if (keyvalue.get("barsize") != null) {
                                                    //logger.log(Level.INFO,"{0},{1}",new Object[]{this.getStrategy(),event.getTickType()});
                                                    String barsize = keyvalue.get("barsize").split("[^A-Z0-9]+|(?<=[A-Z])(?=[0-9])|(?<=[0-9])(?=[A-Z])")[0];
                                                    int min = Utilities.getInt(barsize, 0);
                                                    if (min > 0 && DateUtil.barChange(id, min) && conditionprice > Parameters.symbol.get(id).getLastPrice() && Math.abs(Parameters.symbol.get(id).getLastPrice() - conditionprice) * 100 / conditionprice < slippage) {
                                                        logger.log(Level.INFO, "201,Order Generated from monitor Key={0},barsize={1},Side={2}", new Object[]{keyvalue, keyvalue.get("barsize"), keyvalue.get("side")});
                                                        placeOrder = true;
                                                    } else if (min > 0 && DateUtil.barChange(id, min) && conditionprice > Parameters.symbol.get(id).getLastPrice() && Math.abs(Parameters.symbol.get(id).getLastPrice() - conditionprice) * 100 / conditionprice > slippage) {
                                                        logger.log(Level.INFO, "201,Monitor did not generate order as exceeding slippage. Key={0},barsize={1},Side={2},AllowedSlippage={3},CalculatedSlippage={4},LastPrice={5}", new Object[]{s, keyvalue.get("barsize"), keyvalue.get("side"), slippage,
                                                            Math.abs(Parameters.symbol.get(id).getLastPrice() - conditionprice) * 100 / conditionprice, Parameters.symbol.get(id).getLastPrice()});

                                                    }
                                                }
                                                break;
                                        }
                                    } else if (keyvalue.get("condition").equals("BREACHABOVE")) {
                                        double conditionprice = Utilities.getDouble(keyvalue.get("conditionprice"), Double.MAX_VALUE);
                                        double slippage = Utilities.getDouble(keyvalue.get("maxpercentslippage"), Double.MAX_VALUE);
                                        switch (keyvalue.get("barsize")) {
                                            case "TICK":
                                                if (conditionprice < Parameters.symbol.get(id).getLastPrice() && Math.abs(Parameters.symbol.get(id).getLastPrice() - conditionprice) * 100 / conditionprice < slippage) {
                                                    logger.log(Level.INFO, "201,Order Generated from monitor Key={0},barsize={1},Side={2}", new Object[]{keyvalue, keyvalue.get("barsize"), keyvalue.get("side")});
                                                    placeOrder = true;
                                                } else if (conditionprice < Parameters.symbol.get(id).getLastPrice() & Math.abs(Parameters.symbol.get(id).getLastPrice() - conditionprice) * 100 / conditionprice > slippage) {
                                                    logger.log(Level.INFO, "201,Monitor did not generate order as exceeding slippage. Key={0},barsize={1},Side={2},AllowedSlippage={3},CalculatedSlippage={4},LastPrice={5}", new Object[]{s, keyvalue.get("barsize"), keyvalue.get("side"), slippage,
                                                        Math.abs(Parameters.symbol.get(id).getLastPrice() - conditionprice) * 100 / conditionprice, Parameters.symbol.get(id).getLastPrice()});
                                                }
                                                break;
                                            default:
                                                if (keyvalue.get("barsize") != null) {
                                                    String barsize = keyvalue.get("barsize").split("[^A-Z0-9]+|(?<=[A-Z])(?=[0-9])|(?<=[0-9])(?=[A-Z])")[0];
                                                    int min = Utilities.getInt(barsize, 0);
                                                    if (min > 0 && DateUtil.barChange(id, min) && conditionprice < Parameters.symbol.get(id).getLastPrice() && Math.abs(Parameters.symbol.get(id).getLastPrice() - conditionprice) * 100 / conditionprice < slippage) {
                                                        logger.log(Level.INFO, "201,Order Generated from monitor Key={0},barsize={1},Side={2},", new Object[]{keyvalue, keyvalue.get("barsize"), keyvalue.get("side")});
                                                        placeOrder = true;
                                                    } else if (min > 0 && DateUtil.barChange(id, min) && conditionprice < Parameters.symbol.get(id).getLastPrice() && Math.abs(Parameters.symbol.get(id).getLastPrice() - conditionprice) * 100 / conditionprice > slippage) {
                                                        logger.log(Level.INFO, "201,Monitor did not generate order as exceeding slippage. Key={0},barsize={1},Side={2},AllowedSlippage={3},CalculatedSlippage={4},LastPrice={5}", new Object[]{s, keyvalue.get("barsize"), keyvalue.get("side"), slippage,
                                                            Math.abs(Parameters.symbol.get(id).getLastPrice() - conditionprice) * 100 / conditionprice, Parameters.symbol.get(id).getLastPrice()});
                                                    }
                                                }
                                                break;
                                        }
                                    } else if (keyvalue.get("condition").equals("EFFECTIVEFROM")) {
                                        if (keyvalue.get("starttime") != null & new Date().compareTo(DateUtil.getFormattedDate(keyvalue.get("starttime"), "yyyy-MM-dd HH:mm:ss", timeZone)) > 0) {
                                            logger.log(Level.INFO, "201,Order Generated from monitor Key={0},barsize={1},Side={2}", new Object[]{keyvalue, keyvalue.get("barsize"), keyvalue.get("side")});
                                            placeOrder = true;
                                        }
                                    }
                                    if (placeOrder) {
                                        if (keyvalue.get("ordersymbol") != null & keyvalue.get("ordersize") != null) {
                                            insertSymbol(Parameters.symbol, keyvalue.get("ordersymbol"), optionPricingUsingFutures);
                                            int symbolid = Utilities.getIDFromDisplayName(Parameters.symbol, keyvalue.get("ordersymbol"));
                                            int size = Utilities.getInt(keyvalue.get("ordersize"), 0);
                                            if (symbolid >= 0 & size > 0) {
                                                OrderBean ord = new OrderBean();
                                                ord.setParentDisplayName(Parameters.symbol.get(symbolid).getDisplayname());
                                                ord.setChildDisplayName(Parameters.symbol.get(symbolid).getDisplayname());
                                                if (keyvalue.get("side").equals("CLOSEALL")) {
                                                    EnumOrderSide side = EnumOrderSide.UNDEFINED;
                                                    if (this.getPosition().get(symbolid).getPosition() > 0) {
                                                        side = EnumOrderSide.SELL;
                                                    } else if (this.getPosition().get(symbolid).getPosition() < 0) {
                                                        side = EnumOrderSide.COVER;
                                                    }
                                                    if (!side.equals(EnumOrderSide.UNDEFINED)) {
                                                        ord.setOrderSide(side);
                                                    }
                                                } else {
                                                    ord.setOrderSide(EnumOrderSide.valueOf(keyvalue.get("side")));
                                                }
                                                ord.setOrderType(EnumOrderType.valueOf(keyvalue.get("ordertype")));
                                                ord.setLimitPrice(0);
                                                ord.setOrderStage(EnumOrderStage.INIT);
                                                ord.setStrategyOrderSize(size);
                                                ord.setOriginalOrderSize(size);
                                                int startingpos = Utilities.getNetPosition(Parameters.symbol, getPosition(), symbolid, true);
                                                ord.setStrategyStartingPosition(Math.abs(startingpos));
                                                ord.setOrderReference(getStrategy());
                                                logger.log(Level.INFO, "201,Setting monitor as inactive Key={0},barsize={1},Side={2}", new Object[]{s, keyvalue.get("barsize"), keyvalue.get("side")});
                                                this.getDb().setHash(s, "status", "INACTIVE");
                                                if (ord.getOrderSide().equals(EnumOrderSide.BUY) | ord.getOrderSide().equals(EnumOrderSide.SHORT)) {
                                                    ord.setScale(scaleEntry);
                                                    createPosition(symbolid);
                                                    ord.setOrderReason(EnumOrderReason.REGULARENTRY);
                                                    entry(ord);
                                                } else {
                                                    ord.setScale(scaleExit);
                                                    ord.setOrderReason(EnumOrderReason.REGULAREXIT);
                                                    exit(ord);
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.log(Level.SEVERE, null, e);
                }
            }
        }
    }

    class RScript extends TimerTask {

        @Override
        public void run() {
            runRScript();
        }
    }

    public void runRScript() {
        try {
            SimpleDateFormat sdf_yyyy_MM_dd = new SimpleDateFormat("yyyy-MM-dd");
            String date = sdf_yyyy_MM_dd.format(new Date());
            String[] args = new String[]{"1", getStrategy(), String.valueOf(redisdborder), date};
            if (!RStrategyFile.equals("")) {
                synchronized (lockScan) {
                    RConnection c;
                    c = new RConnection(rServerIP);
                    c.eval("setwd(\"" + wd + "\")");
                    REXP wd = c.eval("getwd()");
                    logger.log(Level.INFO,"102, R Working Directory:{0}",new Object[]{wd.asString()});
                    c.eval("options(encoding = \"UTF-8\")");
                    c.assign("args", args);
                    logger.log(Level.INFO, "102,Invoking R Strategy,{0}:{1}:{2}:{3}:{4},args={5}",
                            new Object[]{getStrategy(), "Order", "unknown", -1, -1, Arrays.toString(args)});
                    c.eval("source(\"" + RStrategyFile + "\")");
                    c.close();
                }
            } else {
                logger.log(Level.INFO, "102, R Strategy File Not Specified, {0}:{1}:{2}:{3}:{4}",
                        new Object[]{getStrategy(), "Order", -1, -1, -1});
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "500,Exception triggered in RScript,,Strategy={0}", new Object[]{this.getStrategy()});
            logger.log(Level.SEVERE, null, e);
        }
    }

    class ReadTrades extends TimerTask {

        @Override
        public void run() {
            try {
                JedisPoolConfig jedisPoolConfig = new JedisPoolConfig();
                jedisPoolConfig.setMaxWaitMillis(1000); //write timeout
                JedisPool jPool = new JedisPool(jedisPoolConfig, s_redisip, s_redisport, 10000, null, redisdborder);
                WatchKey key;
                while ((key = watcher.take()) != null) {
                    Thread.sleep(50);
                    for (WatchEvent<?> event : key.pollEvents()) {
                        WatchEvent.Kind<?> kind = event.kind();
                        if (kind == OVERFLOW) {
                            continue;
                        }
                        WatchEvent<Path> ev = (WatchEvent<Path>) event;
                        Path filename = ev.context();
                        if (filename.toString().endsWith(".txt")) {
                            filename = dir.resolve(filename);
                            BufferedReader bufferedReader;
                            if (!Files.isDirectory(filename)) {
                                try (FileReader fileReader = new FileReader(filename.toString())) {
                                    String line = null;
                                    bufferedReader = new BufferedReader(fileReader);
                                    while ((line = bufferedReader.readLine()) != null) {
                                        OrderBean order = null;
                                        String[] contributors = line.split(":", 3);
                                        if (contributors.length == 3) {
                                            try (Jedis jedis = jPool.getResource()) {
                                                jedis.lpush(contributors[0] + ":" + contributors[1], contributors[2]);
                                            }
                                        }
                                    }
                                    bufferedReader.close();
                                } catch (Exception e) {
                                    key.reset();
                                    logger.log(Level.SEVERE, null, e);
                                }
                            }
                        }
                    }
                    key.reset();
                }
            } catch (InterruptedException ex) {
                Logger.getLogger(Manual.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

    }

    class marketDataProcessor extends TimerTask {

        @Override
        public void run() {
            for (;;) {
                List<String> tradetuple = getDb().blpop("mdrequest:" + getStrategy(), "", 60);
                if (tradetuple != null) {
                    logger.log(Level.INFO, "101,Received MarketData Request:{0} for strategy {1}", new Object[]{tradetuple.get(1), tradetuple.get(0)});
                    String displayName = tradetuple.get(1);
                    String symbol = displayName.split(":", -1)[0];
                    int symbolid = Utilities.getIDFromDisplayName(Parameters.symbol, symbol);
                    if (symbolid == -1) {
                        insertSymbol(Parameters.symbol, symbol, optionPricingUsingFutures);
                    }
                }
            }
        }
    }

    class OrderProcessor extends TimerTask {

        @Override
        public void run() {
            for (;;) {
                List<String> trade = getDb().blpop("trades:" + getStrategy(), "", 60);
                if (trade != null && trade.get(1) != null) {
                    OrderBean order = null;
                    if (isJSONValid(trade.get(1))) {
                        order = jsonOrderProcessor(trade.get(1));
                    } else {
                        order = simpleOrderProcessor(trade.get(1));
                    }
                    if (order != null) {
                        placeOrder(order);
                    }
                }
            }
        }
    }

    public OrderBean simpleOrderProcessor(String line) {
        logger.log(Level.INFO, "101,Received trade {0}", new Object[]{line});
        String[] tuple = line.split(":");
        if (tuple.length == 5) {
            //tuple as trades:strategy:symbol:tradesize:side:sl:initialsize
            Thread t = new Thread(new Mail(getIamail(), "Received Trade: " + line + " for strategy " + tuple[1], "Received Order from [R]"));
            t.start();
            OrderBean order = new OrderBean();
            order.setParentDisplayName(tuple[0]);
            order.setChildDisplayName(tuple[0]);
            order.setOrderSide(EnumOrderSide.valueOf(tuple[2]));
            order.setStrategyOrderSize(Integer.valueOf(tuple[1]));
            order.setOriginalOrderSize(Integer.valueOf(tuple[1]));
            order.setStopLoss(Utilities.round(Double.valueOf(tuple[3]), getTickSize(), 2));
            Utilities.round(order.getStopLoss(), getTickSize(), 2);
            order.setStrategyStartingPosition(Integer.valueOf(tuple[4]));
            order.setStartingPosition(Integer.valueOf(tuple[4]));

            int symbolid = Utilities.getIDFromDisplayName(Parameters.symbol, order.getParentDisplayName());
            if (symbolid == -1 && (order.getOrderSide().equals(EnumOrderSide.BUY) || order.getOrderSide().equals(EnumOrderSide.SHORT))) {
                //new symbol. insert symbol into database
                insertSymbol(Parameters.symbol, order.getParentDisplayName(), optionPricingUsingFutures);
                symbolid = Utilities.getIDFromDisplayName(Parameters.symbol, order.getParentDisplayName());
            }
            if (symbolid == -1) {
                logger.log(Level.SEVERE, "500, Unable to process order,{0}:{1}:{2}:{3}:{4},OrderString={5},SymbolID={6}", new Object[]{this.getStrategy(), "Order", order.getParentDisplayName(), -1, -1, line, symbolid});
                return null;
            }
            if (symbolid >= 0) { //only proceed if symbolid exists in our db
                order.setStartingPosition(Utilities.getNetPosition(Parameters.symbol, getPosition(), symbolid, true));
                initSymbol(symbolid, optionPricingUsingFutures);
                order = calculateAdjustedOrderSize(order);
                if (order.getOriginalOrderSize() > 0) {
                    order.setOrderType(getOrdType());
                    int orderid;
                    ArrayList<Stop> stops = new ArrayList<>();
                    Stop stp = new Stop();
                    double limitPrice;
                    int referenceid = -1;
                    HashMap<String, Object> tmpOrderAttributes = new HashMap<>();
                    referenceid = getUnderlyingReferenceID(symbolid);
                    limitPrice = Utilities.getLimitPriceForOrder(Parameters.symbol, symbolid, referenceid, order.getOrderSide(), getTickSize(), order.getOrderType(),order.getBarrierLimitPrice());
                    if(getOrderAttributes().get("barrierlimitprice")!=null && getOrderAttributes().get("barrierlimitprice").toString().equalsIgnoreCase("true") ){
                        order.setBarrierLimitPrice(limitPrice);
                    }
                    if (order.getOrderType().equals(EnumOrderType.CUSTOMREL)) {
                        order.setLimitPrice(limitPrice);
                    }

                    order.setOrderStage(EnumOrderStage.INIT);
                    order.setOrderLog(order.getOrderSide().toString() + delimiter + tuple[2]);
                    tmpOrderAttributes.putAll(getOrderAttributes());
                    order.setOrderAttributes(tmpOrderAttributes);
                    if (order.getOrderSide().equals(EnumOrderSide.BUY) || order.getOrderSide().equals(EnumOrderSide.SHORT)) {
                        order.setOrderReason(EnumOrderReason.REGULARENTRY);
                        order.setScale(scaleEntry);
                    } else {
                        order.setOrderReason(EnumOrderReason.REGULAREXIT);
                        order.setScale(scaleExit);
                    }
                    return order;
                } else {
                    return null;
                }
            } else {
                return null;
            }
        }
        logger.log(Level.INFO, "101, Invalid Order Received :{0} for Strategy {1}", new Object[]{line, getStrategy()});
        return null;
    }

    public OrderBean jsonOrderProcessor(String line) {
        logger.log(Level.INFO, "501,Received trade request,{0}", new Object[]{line});
        Thread t = new Thread(new Mail(getIamail(), "Received Trade: " + line + " for strategy " + getStrategy(), "Received Order from [R]"));
        t.start();
        Type type = new TypeToken<OrderBean>() {
        }.getType();
        Gson gson = new GsonBuilder().create();

        OrderBean ob = gson.fromJson((String) line, type);
        //fill default values if missing

        int symbolid = Utilities.getIDFromDisplayName(Parameters.symbol, ob.getParentDisplayName());
        if (symbolid == -1 && (ob.getOrderSide().equals(EnumOrderSide.BUY) || ob.getOrderSide().equals(EnumOrderSide.SHORT))) {
            //new symbol. insert symbol into database
            insertSymbol(Parameters.symbol, ob.getParentDisplayName(), optionPricingUsingFutures);
            symbolid = Utilities.getIDFromDisplayName(Parameters.symbol, ob.getParentDisplayName());
        }
        if (symbolid == -1) {
            logger.log(Level.SEVERE, "500, Unable to process order,{0}:{1}:{2}:{3}:{4},OrderString={5},SymbolID={6}", new Object[]{this.getStrategy(), "Order", ob.getParentDisplayName(), -1, -1, line, symbolid});
            return null;
        }
        createPosition(symbolid);
        int referenceid = getUnderlyingReferenceID(symbolid);
        if (ob.getLimitPrice() == 0) {
            double limitPrice = Utilities.getLimitPriceForOrder(Parameters.symbol, symbolid, referenceid, ob.getOrderSide(), getTickSize(), ob.getOrderType(),ob.getBarrierLimitPrice());
            if (getOrderAttributes().get("barrierlimitprice") != null && getOrderAttributes().get("barrierlimitprice").toString().equalsIgnoreCase("true")) {
                ob.setBarrierLimitPrice(limitPrice);
            }
            if (!ob.getOrderType().equals(EnumOrderType.MKT)) {
                ob.setLimitPrice(limitPrice);
            }
        }
        if (ob.getOriginalOrderSize() == 0) {
            ob.setOriginalOrderSize(ob.getStrategyOrderSize());
        }
        if (ob.getStartingPosition() == 0) {
            ob.setStartingPosition(ob.getStrategyStartingPosition());
        }
        ob.setStartingPosition(Utilities.getNetPosition(Parameters.symbol, getPosition(), symbolid, true));
        int catchUpPosition;
        int size = ob.getOriginalOrderSize();
        switch (ob.getOrderSide()) {
            case BUY:
                catchUpPosition = ob.getStrategyStartingPosition() - ob.getStartingPosition();
                size = Math.max(size + catchUpPosition, 0);
                break;
            case SHORT:
                catchUpPosition = -ob.getStrategyStartingPosition() - ob.getStartingPosition();
                size = Math.max(size - catchUpPosition, 0);
                break;
            case SELL:
                catchUpPosition = ob.getStrategyStartingPosition() - ob.getStartingPosition();
                size = Math.max(size - catchUpPosition, 0);
                //if actualpositionsize is -ve, that is an error condition.
                break;
            case COVER:
                catchUpPosition = ob.getStrategyStartingPosition() + ob.getStartingPosition();
                size = Math.max(size - catchUpPosition, 0);
                break;
            default:
                break;
        }
        ob.setOriginalOrderSize(size);

        return ob;
    }

    public int placeOrder(OrderBean order) {
        int orderid = -1;
        try {
            if (order != null) {
                Gson gson = new Gson();
                String json = gson.toJson(order);
                logger.log(Level.INFO, "500,OrderGenerated,{0}:{1}:{2}:{3}:{4},Order={5}", new Object[]{getStrategy(), "Order", order.getParentDisplayName(), -1, -1, json});
                if (order.getOriginalOrderSize() > 0) {
                    if ((order.getOrderType() != EnumOrderType.MKT && order.getLimitPrice() > 0) || order.getOrderType().equals(EnumOrderType.MKT)) {
                        if (order.getOrderSide().equals(EnumOrderSide.BUY) || order.getOrderSide().equals(EnumOrderSide.SHORT)) {
                            orderid = entry(order);
                        } else {
                            orderid = exit(order);
                        }
                    }
                } else {
                    logger.log(Level.INFO, "500, Unable to process order,{0}:{1}:{2}:{3}:{4},OrderSize={5}", new Object[]{this.getStrategy(), "Order", order.getParentDisplayName(), -1, -1, order.getOriginalOrderSize()});
                }
            } else {
                logger.log(Level.INFO, "500, Unable to process order,{0}:{1}:{2}:{3}:{4},Order=NULL", new Object[]{this.getStrategy(), "Order", "NULL", -1, -1, order.getOriginalOrderSize()});
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, null, e);
        }
        return orderid;
    }

    public OrderBean calculateAdjustedOrderSize(OrderBean order) {
        /*
            IF initpositionsize = 100, actual positionsize=0, we get a buy of 100. comp=100, size=200
            IF initpositionsize=0, actualpositionsize=100, we get buy of 100, comp=-100, size=0, probably a duplicate trade
            IF initpositionsize=-100,actualpositionsize=0, we get a short of 100,should be short, but are not, comp=-100,size=abs(-100-100)=200
            IF initpositionsize=200, actualpositionsize=100, we set a SELL of 200, comp=100, size=abs(-200+100)=100
         */
        int catchUpPosition;
        int size = order.getOriginalOrderSize();
        switch (order.getOrderSide()) {
            case BUY:
                catchUpPosition = order.getStrategyStartingPosition() - order.getStartingPosition();
                size = Math.max(size + catchUpPosition, 0);
                break;
            case SHORT:
                catchUpPosition = -order.getStrategyStartingPosition() - order.getStartingPosition();
                size = Math.max(size - catchUpPosition, 0);
                break;
            case SELL:
                catchUpPosition = order.getStrategyStartingPosition() - order.getStartingPosition();
                size = Math.max(size - catchUpPosition, 0);
                //if actualpositionsize is -ve, that is an error condition.
                break;
            case COVER:
                catchUpPosition = order.getStrategyStartingPosition() + order.getStartingPosition();
                size = Math.max(size - catchUpPosition, 0);
                break;
            default:
                break;
        }
        order.setOriginalOrderSize(size);
        return order;
    }

    public int getUnderlyingReferenceID(int symbolid) {
        int referenceid = -1;
        if (Parameters.symbol.get(symbolid).getType().equals("OPT")) {
            referenceid = Utilities.getCashReferenceID(Parameters.symbol, symbolid);
            String tempExpiry = Parameters.symbol.get(symbolid).getExpiry();
            referenceid = optionPricingUsingFutures ? Utilities.getFutureIDFromBrokerSymbol(Parameters.symbol, referenceid, tempExpiry) : referenceid;
        }
        return referenceid;
    }

    public boolean isJSONValid(String jsonInString) {
        try {
            new Gson().fromJson(jsonInString, Object.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void loadParameters(Properties p) {
        dir = Paths.get(p.getProperty("dir", "").trim()).toAbsolutePath();
        orderSource = p.getProperty("OrderSource", "redis").trim();
        optionPricingUsingFutures = Boolean.valueOf(p.getProperty("OptionPricingUsingFutures", "TRUE"));
        useCashReferenceForSLTP = Boolean.valueOf(p.getProperty("UseCashForSLTP", "FALSE"));
        String entryScanTime = p.getProperty("ScanStartTime", "").trim();
        Calendar calToday = Calendar.getInstance(TimeZone.getTimeZone(Algorithm.timeZone));
        String[] entryTimeComponents = entryScanTime.split(":");
        if (entryTimeComponents.length == 3) {
            calToday.set(Calendar.HOUR_OF_DAY, Utilities.getInt(entryTimeComponents[0], 15));
            calToday.set(Calendar.MINUTE, Utilities.getInt(entryTimeComponents[1], 20));
            calToday.set(Calendar.SECOND, Utilities.getInt(entryTimeComponents[2], 0));
            RScriptRunTime = calToday.getTime();
            rServerIP = p.getProperty("RServerIP").toString().trim();
            if (this.RScriptRunTime.compareTo(new Date()) < 0) {
                calToday.add(Calendar.DATE, 1);
                RScriptRunTime = calToday.getTime();
            }

            RStrategyFile = p.getProperty("RStrategyFile", "");
            wd = p.getProperty("wd", "/home/psharma/Seafile/R");
        }
        scaleEntry = Boolean.parseBoolean(p.getProperty("ScaleEntry", "FALSE"));
        scaleExit = Boolean.parseBoolean(p.getProperty("ScaleExit", "FALSE"));

    }

    public String calculateExpiry(int rolloverDays) {
        String today = DateUtil.getFormatedDate("yyyyMMdd", new Date().getTime(), TimeZone.getTimeZone(Algorithm.timeZone));
        String expiryNearMonth = Utilities.getLastThursday(today, "yyyyMMdd", 0);
        Date dtExpiry = DateUtil.parseDate("yyyyMMdd", expiryNearMonth, timeZone);
        String expiryplus = DateUtil.getFormatedDate("yyyyMMdd", DateUtil.addDays(dtExpiry, 1).getTime(), TimeZone.getTimeZone(Algorithm.timeZone));
        String expiryFarMonth = Utilities.getLastThursday(expiryplus, "yyyyMMdd", 0);
        boolean rollover = Utilities.rolloverDay(rolloverDays, getStartDate(), expiryNearMonth);
        String expiry;
        if (rollover) {
            expiry = expiryFarMonth;
        } else {
            expiry = expiryNearMonth;
        }
        return expiry;
    }
}
