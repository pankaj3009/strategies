/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.optsale;

import com.incurrency.RatesClient.Subscribe;
import com.incurrency.framework.Algorithm;
import com.incurrency.framework.BeanConnection;
import com.incurrency.framework.BeanSymbol;
import com.incurrency.framework.EnumOrderReason;
import com.incurrency.framework.EnumOrderSide;
import com.incurrency.framework.EnumOrderStage;
import com.incurrency.framework.EnumOrderType;
import com.incurrency.framework.MainAlgorithm;
import com.incurrency.framework.Parameters;
import com.incurrency.framework.Strategy;
import com.incurrency.framework.TradeEvent;
import com.incurrency.framework.TradeListener;
import com.incurrency.framework.TradingUtil;
import com.incurrency.framework.Utilities;
import java.io.File;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jquantlib.time.JDate;
import org.rosuda.REngine.REXP;
import org.rosuda.REngine.Rserve.RConnection;

/**
 *
 * @author Pankaj
 */
public class OptSale extends Strategy implements TradeListener {

    public String expiryNearMonth;
    public String expiryFarMonth;
    String referenceCashType;
    String rServerIP;
    private EnumOrderType ordType;
    Date monitoringStart;
    Boolean rollover;
    int rolloverDays;
    String expiry;
    String RStrategyFile;
    String wd;
    private Boolean scaleEntry = Boolean.FALSE;
    private Boolean scaleExit = Boolean.FALSE;
    Date entryScanDate;
    int indexid;
    int futureid = -1;
    SimpleDateFormat sdtf_default = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final Logger logger = Logger.getLogger(OptSale.class.getName());
    String indexDisplayName;
    double avgMovePerDayEntry;
    double avgMovePerDayExit;
    boolean buy;
    boolean shrt;
    double thresholdReturnEntry;
    double thresholdReturnExit;
    double historicalVol;
    double dte = 1000000; //set dte to an arbitrarily large number so that trades are not exited before dte is actually calculated.
    double margin;
    double maxPositionSize;
    String[] args = new String[1];

    public OptSale(MainAlgorithm m, Properties p, String parameterFile, ArrayList<String> accounts, Integer stratCount) {
        super(m, "optsale", "FUT", p, parameterFile, accounts, stratCount);
        loadParameters(p);
        String[] tempStrategyArray = parameterFile.split("\\.")[0].split("-|_");
        for (BeanConnection c : Parameters.connection) {
            c.getWrapper().addTradeListener(this);
            c.initializeConnection(tempStrategyArray[tempStrategyArray.length - 1], -1);
        }
        if (Subscribe.tes != null) {
            Subscribe.tes.addTradeListener(this);
        }
        MainAlgorithm.tes.addTradeListener(this);
        Timer eodProcessing;
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());
        cal.setTimeZone(TimeZone.getTimeZone(Algorithm.timeZone));
        cal.add(Calendar.DATE, -1);
        Date priorEndDate = cal.getTime();
        indexid = Utilities.getIDFromDisplayName(Parameters.symbol, indexDisplayName);
        if (indexid >= 0) {
            try {
                SimpleDateFormat sdf_yyyyMMdd = new SimpleDateFormat("yyyyMMdd");
                SimpleDateFormat sdf_yyyy_MM_dd = new SimpleDateFormat("yyyy-MM-dd");
                JDate expiryDate = new JDate(sdf_yyyyMMdd.parse(expiryNearMonth));
                long dte = Algorithm.ind.businessDaysBetween(new JDate(new Date()), expiryDate);
                expiry = expiryNearMonth;
                futureid = Utilities.getFutureIDFromExchangeSymbol(Parameters.symbol, indexid, expiry);
                if (dte <= rolloverDays) {
                    expiryDate = new JDate(sdf_yyyyMMdd.parse(expiryFarMonth));
                    dte = Algorithm.ind.businessDaysBetween(new JDate(new Date()), expiryDate);
                    expiry = expiryFarMonth;
                    futureid = Utilities.getFutureIDFromExchangeSymbol(Parameters.symbol, indexid, expiry);
                }

                if (new Date().before(this.getEndDate()) && new Date().after(priorEndDate)) {
                    logger.log(Level.INFO, "Set EODProcessing Task at {0}", new Object[]{sdtf_default.format(entryScanDate)});
                    eodProcessing = new Timer("Timer: " + this.getStrategy() + " EODProcessing");
                    eodProcessing.schedule(eodProcessingTask, entryScanDate);
                }
                Timer monitor = new Timer("Timer: " + this.getStrategy() + " TradeScanner");
                monitor.schedule(tradeScannerTask, monitoringStart);
                File dir = new File("logs");
                File file = new File(dir, getStrategy() + ".csv");

                //if file doesnt exists, then create it
                if (!file.exists()) {
                    TradingUtil.writeToFile(getStrategy() + ".csv", "DisplayName,DTE,LastPrice,AnnualizedReturn,Theta,Vega,Theta/Vega,YesterdayVol,CalculatedPremium");
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, null, e);
            }
        }
    }
    TimerTask eodProcessingTask = new TimerTask() {
        @Override
        public void run() {
            //Strategy
            //Connection
            //Symbol Display Name
            // InternalOrderID
            //ExternalOrderID
            // Get current market price of NSENIFTY index.
            try {
                indexid = Utilities.getIDFromDisplayName(Parameters.symbol, indexDisplayName);
                if (indexid >= 0) {
                    SimpleDateFormat sdf_yyyyMMdd = new SimpleDateFormat("yyyyMMdd");
                    SimpleDateFormat sdf_yyyy_MM_dd = new SimpleDateFormat("yyyy-MM-dd");
                    JDate expiryDate = new JDate(sdf_yyyyMMdd.parse(expiryNearMonth));
                    long dte = Algorithm.ind.businessDaysBetween(new JDate(new Date()), expiryDate);
                    expiry = expiryNearMonth;
                    futureid = Utilities.getFutureIDFromExchangeSymbol(Parameters.symbol, indexid, expiry);
                    if (dte <= rolloverDays) {
                        expiryDate = new JDate(sdf_yyyyMMdd.parse(expiryFarMonth));
                        dte = Algorithm.ind.businessDaysBetween(new JDate(new Date()), expiryDate);
                        expiry = expiryFarMonth;
                        futureid = Utilities.getFutureIDFromExchangeSymbol(Parameters.symbol, indexid, expiry);
                    }
                    //Initialize 
                    ArrayList<Integer> allOrderList = new ArrayList<>();
                    Thread.sleep(2000);
                    Thread.yield();
                    String open = String.valueOf(Parameters.symbol.get(indexid).getOpenPrice());
                    String high = String.valueOf(Parameters.symbol.get(indexid).getHighPrice());
                    String low = String.valueOf(Parameters.symbol.get(indexid).getLowPrice());
                    String close = String.valueOf(Parameters.symbol.get(indexid).getLastPrice());
                    String volume = String.valueOf(Parameters.symbol.get(indexid).getVolume());
                    String date = sdf_yyyy_MM_dd.format(new Date());
                    args = new String[]{"1", getStrategy(), getRedisDatabaseID(),
                        Parameters.symbol.get(indexid).getDisplayname(), date, open, high, low, close, volume};
                    if (!RStrategyFile.equals("")) {
                        logger.log(Level.INFO, "501,Scan,{0}", new Object[]{getStrategy()});
                        RConnection c = null;
                        try {
                            c = new RConnection(rServerIP);
                            c.eval("setwd(\"" + wd + "\")");
                            REXP wd = c.eval("getwd()");
                            System.out.println(wd.asString());
                            c.eval("options(encoding = \"UTF-8\")");
                            c.assign("args", args);
                            c.eval("source(\"" + RStrategyFile + "\")");
                        } catch (Exception e) {
                            logger.log(Level.SEVERE, null, e);
                        }
                        List<String> tradetuple = db.blpop("signals:" + getStrategy(), "", 60);
                        if (tradetuple != null) {
                            logger.log(Level.INFO, "Received Trade:{0} for strategy {1}", new Object[]{tradetuple.get(1), tradetuple.get(0)});
                            switch (tradetuple.get(1)) {
                                case "BUY":
                                    buy = true;
                                    shrt = false;
                                    break;
                                case "SHORT":
                                    buy = false;
                                    shrt = true;
                                    break;
                                default:
                                    buy = false;
                                    shrt = false;
                                    break;

                            }
                        }
                        double cushion = Math.sqrt(dte) * historicalVol* avgMovePerDayEntry;
                        double futurePrice = Parameters.symbol.get(futureid).getLastPrice();
                        double highestFloor = Utilities.roundTo((futurePrice - cushion * futurePrice / 100), Parameters.symbol.get(futureid).getStrikeDistance());
                        double lowestCeiling = Utilities.roundTo((futurePrice + cushion * futurePrice / 100), Parameters.symbol.get(futureid).getStrikeDistance());

                        //Get 2 strikes
                        double[] putLevels = new double[2];
                        putLevels[0] = highestFloor;
                        putLevels[1] = highestFloor - Parameters.symbol.get(futureid).getStrikeDistance();

                        double[] callLevels = new double[2];
                        callLevels[0] = lowestCeiling;
                        callLevels[1] = lowestCeiling + Parameters.symbol.get(futureid).getStrikeDistance();

                        if (buy) {
                            for (double str : callLevels) {
                                int id = Utilities.insertStrike(Parameters.symbol, futureid, expiry, "CALL", String.valueOf(str));
                                allOrderList.add(id);
                            }
                        }

                        if (shrt) {
                            for (double str : putLevels) {
                                int id = Utilities.insertStrike(Parameters.symbol, futureid, expiry, "PUT", String.valueOf(str));
                                allOrderList.add(id);
                            }
                        }

                        for (int i : allOrderList) {
                            initSymbol(i);
                        }

                        Thread.sleep(2000);
                        Thread.yield();
                        ArrayList<Integer> filteredOrderList = new ArrayList<>();
                        //Place Orders
                        for (int i : allOrderList) {
                            BeanSymbol s = Parameters.symbol.get(i);
                            double annualizedRet = s.getLastPrice() * 365 / (s.getCdte() * futurePrice * margin);
                            double calcPremium = s.getOptionProcess().NPV();
                            double theta = s.getOptionProcess().theta();
                            double vega = s.getOptionProcess().vega();
                            double metric = theta / vega;
                            DecimalFormat df = new DecimalFormat("#.##");
                            TradingUtil.writeToFile(getStrategy() + ".csv", s.getDisplayname() + ","
                                    + s.getBdte() + "," + s.getLastPrice() + "," + annualizedRet + ","
                                    + Utilities.formatDouble(theta, df) + ","
                                    + Utilities.formatDouble(vega, df) + ","
                                    + Utilities.formatDouble(metric, df) + ","
                                    + Utilities.formatDouble(s.getLastVol(), df) + ","
                                    + Utilities.formatDouble(calcPremium, df));

                            if (annualizedRet > thresholdReturnEntry ) {
                                //if(true){
                                filteredOrderList.add(i);
                            }
                        }

                        //write orders to redis
                        if (filteredOrderList.size() == 0) {
                            logger.log(Level.INFO, "501,{0},{1},{2},{3},{4}, No Orders Generated",
                                    new Object[]{getStrategy(), "Order", Parameters.symbol.get(indexid).getDisplayname(),
                                -1, -1});
                        }
                        for (int i : filteredOrderList) {
                            int actualPositionSize = Math.abs(Utilities.getNetPositionFromOptions(Parameters.symbol, getPosition(), i));
                            if (actualPositionSize < maxPositionSize) {
                                int position = getPosition().get(i).getPosition();
                                db.lpush("trades:" + getStrategy(), Parameters.symbol.get(i).getDisplayname() + ":" + getNumberOfContracts() + ":SHORT" + ":0:" + position);
                            } else {
                                logger.log(Level.INFO, "501,{0},{1},{2},{3},{4},{5},Position Limit Hit. No Order Placed. Current Position Size: {6}",
                                        new Object[]{getStrategy(), "Order",
                                    Parameters.symbol.get(i).getDisplayname(), -1, -1, actualPositionSize});
                            }
                        }

                    } else {
                        logger.log(Level.INFO, "501, {0},{1},{2},{3},{4} No Index ID Found. Index:{5}", new Object[]{getStrategy(), "Order", -1, -1, -1, indexDisplayName});
                    }
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, null, e);
            }
            //Set exipry date to current future 
            //If time to expiry for current month future <=7 days, set expiry date to next month future.
            //Get time to expiry
            //Calculate high and low strike price at 0.4% for each working day, rounded.
            //Get Max and Min strike price, at 10% from current index, rounded.
            //Get array of Call strike prices between [high,Max] and Put strike price between [Min,low]
            //Get symbol id for each strike
            //Wait for one minute - this is to ensure we are able to get prices for all requests.
            //Order calls and puts by vega/theta [this will be negative] in ascending order. The ones at the top are preferred
            //Execute if abs(vega/theta) >0.3 and premium*365/(index value*dtm)>0.5
        }
    };
    TimerTask tradeScannerTask = new TimerTask() {
        @Override
        public void run() {
            while (true) {
                waitForTrades();
            }
        }
    };

    void waitForTrades() {
        try {
            List<String> tradetuple = db.blpop("trades:" + this.getStrategy(), "", 60);
            if (tradetuple != null) {
                logger.log(Level.INFO, "Received Trade:{0} for strategy {1}", new Object[]{tradetuple.get(1), tradetuple.get(0)});
                //tradetuple as symbol:size:side:sl
                String symbol = tradetuple.get(1).split(":")[0];
                int symbolid = Utilities.getIDFromDisplayName(Parameters.symbol, symbol);
                if (symbolid < 0) {
                    symbolid = Utilities.insertStrike(Parameters.symbol, futureid, symbol.split("_", -1)[2], symbol.split("_", -1)[3], symbol.split("_", -1)[4]);
                    this.initSymbol(symbolid);
                    Thread.sleep(2000);
                }
                int localfutureid = Utilities.getFutureIDFromExchangeSymbol(Parameters.symbol, symbolid, expiry);
                int nearfutureid = localfutureid;
                int size = Integer.valueOf(tradetuple.get(1).split(":")[1]);
                EnumOrderSide side = EnumOrderSide.valueOf(tradetuple.get(1).split(":")[2]);

                double sl = Double.valueOf(tradetuple.get(1).split(":")[3]);
                sl = Utilities.round(sl, getTickSize(), 2);
                HashMap<String, Object> order = new HashMap<>();
                ArrayList<Integer> orderidlist = new ArrayList<>();
                ArrayList<Integer> nearorderidlist = new ArrayList<>();
                int newid = Utilities.getIDFromDisplayName(Parameters.symbol, tradetuple.get(1).split(":")[0]);
                orderidlist.add(newid);
                nearorderidlist = orderidlist;
                if (orderidlist.size() > 0) {
                    for (int i : orderidlist) {
                        this.initSymbol(i);
                    }
                    for (int i : nearorderidlist) {
                        this.initSymbol(i);
                    }
                    /*
                     * IF initpositionsize = 100, actual positionsize=0, we get a buy of 100. comp=100, size=200
                     * IF initpositionsize=0, actualpositionsize=100, we get buy of 100, comp=-100, size=0, probably a duplicate trade
                     * IF initpositionsize=-100,actualpositionsize=0, we get a short of 100,should be short, but are not, comp=-100,size=abs(-100-100)=200
                     * IF initpositionsize=200, actualpositionsize=100, we set a SELL of 200, comp=100, size=abs(-200+100)=100
                     */
                    if (size > 0) {
                        order.put("type", ordType);
                        order.put("expiretime", getMaxOrderDuration());
                        order.put("dynamicorderduration", getDynamicOrderDuration());
                        order.put("maxslippage", this.getMaxSlippageEntry());
                        int orderid;
                        switch (side) {
                            case BUY:
                                for (int id : orderidlist) {
                                    if (id >= 0) {
                                        order.put("id", id);
                                        double limitprice = Utilities.getOptionLimitPriceForRel(Parameters.symbol, id, localfutureid, EnumOrderSide.BUY, "CALL", getTickSize());
                                        order.put("limitprice", limitprice);
                                        order.put("side", EnumOrderSide.BUY);
                                        order.put("size", size);
                                        order.put("reason", EnumOrderReason.REGULARENTRY);
                                        order.put("orderstage", EnumOrderStage.INIT);
                                        order.put("scale", scaleEntry);
                                        order.put("dynamicorderduration", this.getDynamicOrderDuration());
                                        order.put("expiretime", 0);
                                        order.put("log", "BUY" + delimiter + tradetuple.get(1));
                                        if (limitprice > 0) {
                                            logger.log(Level.INFO, "501,Strategy BUY,{0}", new Object[]{getStrategy() + delimiter + "BUY" + delimiter + Parameters.symbol.get(id).getDisplayname()});
                                            orderid = entry(order);
                                        }
                                    }
                                }
                                break;
                            case SELL:
                                for (int nearid : nearorderidlist) {
                                    if (nearid >= 0) {
                                        order.put("id", nearid);
                                        double limitprice = Utilities.getOptionLimitPriceForRel(Parameters.symbol, nearid, nearfutureid, EnumOrderSide.SELL, "CALL", getTickSize());
                                        order.put("limitprice", limitprice);
                                        order.put("side", EnumOrderSide.SELL);
                                        order.put("size", size);
                                        order.put("reason", EnumOrderReason.REGULAREXIT);
                                        order.put("orderstage", EnumOrderStage.INIT);
                                        order.put("scale", scaleExit);
                                        order.put("dynamicorderduration", this.getDynamicOrderDuration());
                                        order.put("expiretime", 0);
                                        order.put("log", "SELL" + delimiter + tradetuple.get(1));
                                        if (limitprice > 0) {
                                            logger.log(Level.INFO, "501,Strategy SELL,{0}", new Object[]{getStrategy() + delimiter + "SELL" + delimiter + Parameters.symbol.get(nearid).getDisplayname()});
                                            exit(order);
                                        }
                                    }
                                }
                                break;
                            case SHORT:
                                for (int id : orderidlist) {
                                    if (id >= 0) {
                                        order.put("id", id);
                                        double limitprice = Utilities.getOptionLimitPriceForRel(Parameters.symbol, id, localfutureid, EnumOrderSide.BUY, "PUT", getTickSize());
                                        order.put("limitprice", limitprice);
                                        order.put("side", EnumOrderSide.SHORT);
                                        order.put("size", size);
                                        order.put("reason", EnumOrderReason.REGULARENTRY);
                                        order.put("scale", scaleEntry);
                                        order.put("orderstage", EnumOrderStage.INIT);
                                        order.put("dynamicorderduration", this.getDynamicOrderDuration());
                                        order.put("expiretime", 0);
                                        order.put("log", "SHORT" + delimiter + tradetuple.get(1));
                                        if (limitprice > 0) {
                                            logger.log(Level.INFO, "501,Strategy SHORT,{0}", new Object[]{getStrategy() + delimiter + "SHORT" + delimiter + Parameters.symbol.get(id).getDisplayname()});
                                            orderid = entry(order);
                                        }
                                    }
                                }
                                break;
                            case COVER:
                                for (int nearid : nearorderidlist) {
                                    if (nearid >= 0) {
                                        order.put("id", nearid);
                                        double limitprice = Utilities.getOptionLimitPriceForRel(Parameters.symbol, nearid, nearfutureid, EnumOrderSide.SELL, "PUT", getTickSize());
                                        order.put("limitprice", limitprice);
                                        order.put("side", EnumOrderSide.COVER);
                                        order.put("size", size);
                                        order.put("reason", EnumOrderReason.REGULAREXIT);
                                        order.put("scale", scaleExit);
                                        order.put("orderstage", EnumOrderStage.INIT);
                                        order.put("dynamicorderduration", this.getDynamicOrderDuration());
                                        order.put("expiretime", 0);
                                        order.put("log", "COVER" + delimiter + tradetuple.get(1));
                                        if (limitprice > 0) {
                                            logger.log(Level.INFO, "501,Strategy COVER,{0}", new Object[]{getStrategy() + delimiter + "COVER" + delimiter + Parameters.symbol.get(nearid).getDisplayname()});
                                            exit(order);
                                        }
                                    }
                                }
                                break;
                            default:
                                break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, null, e);
        }
    }

    private void loadParameters(Properties p) {
        expiryNearMonth = p.getProperty("NearMonthExpiry").toString().trim();
        expiryFarMonth = p.getProperty("FarMonthExpiry").toString().trim();
        referenceCashType = p.getProperty("ReferenceCashType", "STK").toString().trim();
        rServerIP = p.getProperty("RServerIP").toString().trim();
        ordType = EnumOrderType.valueOf(p.getProperty("OrderType", "LMT"));
        String entryScanTime = p.getProperty("ScanStartTime");
        Calendar calToday = Calendar.getInstance(TimeZone.getTimeZone(Algorithm.timeZone));
        String[] entryTimeComponents = entryScanTime.split(":");
        calToday.set(Calendar.HOUR_OF_DAY, Utilities.getInt(entryTimeComponents[0], 15));
        calToday.set(Calendar.MINUTE, Utilities.getInt(entryTimeComponents[1], 20));
        calToday.set(Calendar.SECOND, Utilities.getInt(entryTimeComponents[2], 0));
        monitoringStart = calToday.getTime();
        rolloverDays = Integer.valueOf(p.getProperty("RolloverDays", "0"));
        RStrategyFile = p.getProperty("RStrategyFile", "");
        wd = p.getProperty("wd", "/home/psharma/Seafile/R");
        scaleEntry = Boolean.parseBoolean(p.getProperty("ScaleEntry", "FALSE"));
        scaleExit = Boolean.parseBoolean(p.getProperty("ScaleExit", "FALSE"));
        indexDisplayName = p.getProperty("IndexDisplayName", "NSENIFTY_IND___");
        avgMovePerDayEntry = Utilities.getDouble(p.getProperty("AverageMovePerDayEntry", "0.4"), 0.4);
        avgMovePerDayExit = Utilities.getDouble(p.getProperty("AverageMovePerDayExit", "0.2"), 0.2);
        thresholdReturnEntry = Utilities.getDouble(p.getProperty("ThresholdReturnEntry", "0.3"), 0.3);
        thresholdReturnExit = Utilities.getDouble(p.getProperty("ThresholdReturnExit", "0.15"), 0.15);
        historicalVol = Utilities.getDouble(p.getProperty("HistoricalVol", "0.7"), 0.7);
        margin = Utilities.getDouble(p.getProperty("Margin", "0.10"), 0.10);
        entryScanTime = p.getProperty("EntryStartTime");
        calToday = Calendar.getInstance(TimeZone.getTimeZone(Algorithm.timeZone));
        calToday.setTime(this.getEndDate());
        entryTimeComponents = entryScanTime.split(":");
        calToday.set(Calendar.HOUR_OF_DAY, Utilities.getInt(entryTimeComponents[0], 15));
        calToday.set(Calendar.MINUTE, Utilities.getInt(entryTimeComponents[1], 20));
        calToday.set(Calendar.SECOND, Utilities.getInt(entryTimeComponents[2], 0));
        entryScanDate = calToday.getTime();
        maxPositionSize = Utilities.getInt(p.getProperty("MaxPositionSize", "0"), 0);

    }

    @Override
    public void tradeReceived(TradeEvent event) {
        Integer id = event.getSymbolID();
        if (getStrategySymbols().contains(id) && getPosition().get(id).getPosition() != 0) {
            int position = getPosition().get(id).getPosition();
            long optionDte = Parameters.symbol.get(id).getBdte();
            String right = Parameters.symbol.get(id).getRight();
            double optionReturn = 0;
            double futurePrice = 0;
            double strikePrice = 0;
            if (Parameters.symbol.get(id).getCdte() > 0 && futureid >= 0 ) {
                futurePrice = Parameters.symbol.get(futureid).getLastPrice();
                strikePrice = Utilities.getDouble(Parameters.symbol.get(id).getOption(), 0);
                optionReturn = Parameters.symbol.get(id).getLastPrice() * 365 / (Parameters.symbol.get(id).getCdte() * futurePrice * margin);
                if (optionReturn == 0 || futurePrice == 0 ||strikePrice==0) {
                    logger.log(Level.INFO, "Option Return for Symbol:{0}, lastPrice:{1}, DTE: {2} is {3}",
                            new Object[]{Parameters.symbol.get(id).getDisplayname(), Parameters.symbol.get(id).getLastPrice(), Parameters.symbol.get(id).getCdte(), optionReturn});
                    return;
                }
                if (optionReturn > 0 && futurePrice > 0 && strikePrice > 0 && optionDte>=0) {
                    HashMap<String, Object> order = new HashMap<>();
                    switch (right) {
                        case "CALL":
                            if ((optionReturn < thresholdReturnExit || (strikePrice - (Math.sqrt(optionDte) *historicalVol* avgMovePerDayExit * futurePrice / 100)) < futurePrice) && optionReturn > 0) {
                                order.put("type", ordType);
                                order.put("expiretime", getMaxOrderDuration());
                                order.put("dynamicorderduration", getDynamicOrderDuration());
                                order.put("maxslippage", this.getMaxSlippageEntry());
                                order.put("id", id);
                                double limitprice = Utilities.getOptionLimitPriceForRel(Parameters.symbol, id, futureid, EnumOrderSide.COVER, "CALL", getTickSize());
                                order.put("limitprice", limitprice);
                                order.put("side", EnumOrderSide.COVER);
                                order.put("size", position);
                                order.put("reason", EnumOrderReason.REGULARENTRY);
                                order.put("orderstage", EnumOrderStage.INIT);
                                order.put("scale", this.scaleExit);
                                order.put("dynamicorderduration", this.getDynamicOrderDuration());
                                order.put("expiretime", 0);
                                if (limitprice > 0) {
                                    logger.log(Level.INFO, "501,Strategy BUY,{0},", new Object[]{getStrategy() + delimiter + "BUY" + delimiter + Parameters.symbol.get(id).getDisplayname() + delimiter + optionReturn});
                                    exit(order);
                                }
                            }
                            break;
                        case "PUT":
                            if ((optionReturn < thresholdReturnExit || (strikePrice + (Math.sqrt(optionDte) *historicalVol* avgMovePerDayExit * futurePrice) / 100) > futurePrice) && optionReturn > 0) {
                                order.put("type", ordType);
                                order.put("expiretime", getMaxOrderDuration());
                                order.put("dynamicorderduration", getDynamicOrderDuration());
                                order.put("maxslippage", this.getMaxSlippageEntry());
                                order.put("id", id);
                                double limitprice = Utilities.getOptionLimitPriceForRel(Parameters.symbol, id, futureid, EnumOrderSide.COVER, "PUT", getTickSize());
                                order.put("limitprice", limitprice);
                                order.put("side", EnumOrderSide.COVER);
                                order.put("size", position);
                                order.put("reason", EnumOrderReason.REGULARENTRY);
                                order.put("orderstage", EnumOrderStage.INIT);
                                order.put("scale", scaleExit);
                                order.put("dynamicorderduration", this.getDynamicOrderDuration());
                                order.put("expiretime", 0);
                                if (limitprice > 0) {
                                    logger.log(Level.INFO, "501,Strategy BUY,{0}", new Object[]{getStrategy() + delimiter + "BUY" + delimiter + Parameters.symbol.get(id).getDisplayname() + delimiter + optionReturn});
                                    exit(order);
                                }
                            }
                            break;
                        default:

                    }
                }
            }
        }
    }
}
