/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.algorithms.optsale;

import com.incurrency.RatesClient.Subscribe;
import com.incurrency.algorithms.manager.Manager;
import com.incurrency.framework.Algorithm;
import com.incurrency.framework.BeanConnection;
import com.incurrency.framework.BeanSymbol;
import com.incurrency.framework.EnumOrderReason;
import com.incurrency.framework.EnumOrderSide;
import com.incurrency.framework.EnumOrderStage;
import com.incurrency.framework.MainAlgorithm;
import com.incurrency.framework.Parameters;
import com.incurrency.framework.TradeEvent;
import com.incurrency.framework.TradeListener;
import com.incurrency.framework.TradingUtil;
import com.incurrency.framework.Utilities;
import java.io.File;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
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
public class OptSale extends Manager implements TradeListener {

   
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
        super(m, p, parameterFile, accounts, stratCount);
        loadAdditionalParameters(p);       

        // Add Trade Listeners
        for (BeanConnection c : Parameters.connection) {
            c.getWrapper().addTradeListener(this);
        }
        if (Subscribe.tes != null) {
            Subscribe.tes.addTradeListener(this);
        }
        MainAlgorithm.tes.addTradeListener(this);
  
        Timer trigger = new Timer("Timer: " + this.getStrategy() + " RScriptProcessor");
        trigger.schedule(RScriptRunTask, RScriptRunTime);

        
        indexid = Utilities.getIDFromDisplayName(Parameters.symbol, indexDisplayName);
        if (indexid >= 0) {
            try {
                SimpleDateFormat sdf_yyyyMMdd = new SimpleDateFormat("yyyyMMdd");
                SimpleDateFormat sdf_yyyy_MM_dd = new SimpleDateFormat("yyyy-MM-dd");
                JDate expiryDate = new JDate(sdf_yyyyMMdd.parse(expiryNearMonth));
                dte = Algorithm.ind.businessDaysBetween(new JDate(new Date()), expiryDate);
                expiry = expiryNearMonth;
                futureid = Utilities.getFutureIDFromExchangeSymbol(Parameters.symbol, indexid, expiry);
                if (dte <= rolloverDays) {
                    expiryDate = new JDate(sdf_yyyyMMdd.parse(expiryFarMonth));
                    dte = Algorithm.ind.businessDaysBetween(new JDate(new Date()), expiryDate);
                    expiry = expiryFarMonth;
                    futureid = Utilities.getFutureIDFromExchangeSymbol(Parameters.symbol, indexid, expiry);
                }
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
    private TimerTask RScriptRunTask = new TimerTask() {
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
                        RConnection c;
                        try {
                            c = new RConnection(rServerIP);
                            c.eval("setwd(\"" + wd + "\")");
                            REXP wd = c.eval("getwd()");
                            System.out.println(wd.asString());
                            c.eval("options(encoding = \"UTF-8\")");
                            c.assign("args", args);
                             logger.log(Level.INFO, "Invoking R. Strategy:{0}, args: {1}", new Object[]{getStrategy(),Arrays.toString(args)});
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
                        double futurePrice = Parameters.symbol.get(indexid).getLastPrice();
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
                                int id = Utilities.insertStrike(Parameters.symbol, futureid, expiry, "CALL", Utilities.formatDouble(str,  new DecimalFormat("#.##")));
                                allOrderList.add(id);
                            }
                        }

                        if (shrt) {
                            for (double str : putLevels) {
                                int id = Utilities.insertStrike(Parameters.symbol, futureid, expiry, "PUT", Utilities.formatDouble(str,  new DecimalFormat("#.##")));
                                allOrderList.add(id);
                            }
                        }

                        for (int i : allOrderList) {
                            initSymbol(i,optionPricingUsingFutures,referenceCashType);
                        }

                        Thread.sleep(4000); //wait for 4 seconds
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
                        if (filteredOrderList.isEmpty()) {
                            logger.log(Level.INFO, "501,{0},{1},{2},{3},{4}, No Orders Generated",
                                    new Object[]{getStrategy(), "Order", Parameters.symbol.get(indexid).getDisplayname(),
                                -1, -1});
                        }
                        for (int i : filteredOrderList) {
                            int actualPositionSize = Math.abs(Utilities.getNetPosition(Parameters.symbol, getPosition(), i,"OPT"));
                            if (actualPositionSize < maxPositionSize) {
                                //int position = getPosition().get(i).getPosition();
                                db.lpush("trades:" + getStrategy(), Parameters.symbol.get(i).getDisplayname() + ":" + getNumberOfContracts() + ":SHORT" + ":0:" + actualPositionSize);
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
        }
    };

    private void loadAdditionalParameters(Properties p) {
        indexDisplayName = p.getProperty("IndexDisplayName", "NSENIFTY_IND___");
        avgMovePerDayEntry = Utilities.getDouble(p.getProperty("AverageMovePerDayEntry", "0.4"), 0.4);
        avgMovePerDayExit = Utilities.getDouble(p.getProperty("AverageMovePerDayExit", "0.2"), 0.2);
        thresholdReturnEntry = Utilities.getDouble(p.getProperty("ThresholdReturnEntry", "0.3"), 0.3);
        thresholdReturnExit = Utilities.getDouble(p.getProperty("ThresholdReturnExit", "0.15"), 0.15);
        historicalVol = Utilities.getDouble(p.getProperty("HistoricalVol", "0.7"), 0.7);
        margin = Utilities.getDouble(p.getProperty("Margin", "0.10"), 0.10);
        maxPositionSize = Utilities.getInt(p.getProperty("MaxPositionSize", "0"), 0);

    }

    @Override
    public void tradeReceived(TradeEvent event) {
        Integer id = event.getSymbolID();
        if (getStrategySymbols().contains(id) && getPosition().get(id).getPosition() != 0) {
            int position = getPosition().get(id).getPosition();
            long optionDte = Parameters.symbol.get(id).getBdte();
            String right = Parameters.symbol.get(id).getRight();
            double optionReturn;
            double futurePrice;
            double indexPrice;
            double strikePrice;
            int underlyingid=Parameters.symbol.get(id).getUnderlyingID();
            if (Parameters.symbol.get(id).getCdte() > 0 && underlyingid >= 0 ) {
                futurePrice = Parameters.symbol.get(underlyingid).getLastPrice();
                indexPrice = Parameters.symbol.get(indexid).getLastPrice();
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
                            if ((optionReturn < thresholdReturnExit || (strikePrice - (Math.sqrt(optionDte) *historicalVol* avgMovePerDayExit * indexPrice / 100)) < futurePrice) && optionReturn > 0) {
                                order.put("type", this.getOrdType());
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
                                order.put("orderattributes",this.getOrderAttributes());
                                if (limitprice > 0) {
                                    logger.log(Level.INFO, "501,Strategy BUY,{0},", new Object[]{getStrategy() + delimiter + "BUY" + delimiter + Parameters.symbol.get(id).getDisplayname() + delimiter + optionReturn});
                                    exit(order);
                                }
                            }
                            break;
                        case "PUT":
                            if ((optionReturn < thresholdReturnExit || (strikePrice + (Math.sqrt(optionDte) *historicalVol* avgMovePerDayExit * indexPrice) / 100) > futurePrice) && optionReturn > 0) {
                                order.put("type", this.getOrdType());
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
                                order.put("orderattributes",this.getOrderAttributes());
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
