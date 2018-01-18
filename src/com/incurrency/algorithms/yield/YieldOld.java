/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.algorithms.yield;

import com.incurrency.RatesClient.RedisSubscribe;
import com.incurrency.algorithms.manager.Manager;
import com.incurrency.framework.Algorithm;
import com.incurrency.framework.BeanConnection;
import com.incurrency.framework.BeanSymbol;
import com.incurrency.framework.EnumOrderReason;
import com.incurrency.framework.EnumOrderSide;
import com.incurrency.framework.EnumOrderStage;
import com.incurrency.framework.MainAlgorithm;
import com.incurrency.framework.OrderBean;
import com.incurrency.framework.Parameters;
import com.incurrency.framework.TradeEvent;
import com.incurrency.framework.TradeListener;
import com.incurrency.framework.Utilities;
import com.incurrency.framework.Utilities;
import java.io.File;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
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
public class YieldOld extends Manager implements TradeListener {

    int indexid;
    int futureid = -1;
    SimpleDateFormat sdtf_default = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final Logger logger = Logger.getLogger(YieldOld.class.getName());
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
    private final Object lockScan = new Object();

    public YieldOld(MainAlgorithm m, Properties p, String parameterFile, ArrayList<String> accounts, Integer stratCount) {
        super(m, p, parameterFile, accounts, "optsale");
        loadAdditionalParameters(p);

        // Add Trade Listeners
        for (BeanConnection c : Parameters.connection) {
            c.getWrapper().addTradeListener(this);
        }
        if (RedisSubscribe.tes != null) {
            RedisSubscribe.tes.addTradeListener(this);
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
                    Utilities.writeToFile(getStrategy() + ".csv", "DisplayName,DTE,LastPrice,AnnualizedReturn,Theta,Vega,Theta/Vega,YesterdayVol,CalculatedPremium");
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
                insertStrikes();
                Thread.sleep(4000);
                SimpleDateFormat sdf_yyyy_MM_dd = new SimpleDateFormat("yyyy-MM-dd");
                String date = sdf_yyyy_MM_dd.format(new Date());
                args = new String[]{"1", getStrategy(), String.valueOf(redisdborder), date};
                if (!RStrategyFile.equals("")) {
                    synchronized (lockScan) {
                        RConnection c;
                        c = new RConnection(rServerIP);
                        c.eval("setwd(\"" + wd + "\")");
                        REXP wd = c.eval("getwd()");
                        System.out.println(wd.asString());
                        c.eval("options(encoding = \"UTF-8\")");
                        c.assign("args", args);
                        logger.log(Level.INFO, "102,Invoking R Strategy,{0}:{1}:{2}:{3}:{4},args={5}",
                                new Object[]{getStrategy(), "Order", "unknown", -1, -1, Arrays.toString(args)});
                        c.eval("source(\"" + RStrategyFile + "\")");

                    }
                } else {
                    logger.log(Level.INFO, "102, R Strategy File Not Specified, {0}:{1}:{2}:{3}:{4}",
                            new Object[]{getStrategy(), "Order", -1, -1, -1});
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, null, e);
            }
        }
    };

    public void insertStrikes() {
        ArrayList<Integer> allOrderList = new ArrayList<>();
        try {
            double cushion = Math.sqrt(dte) * historicalVol * avgMovePerDayEntry;
            if (tradePriceExists(Parameters.symbol.get(futureid), 15)) {
                double futurePrice = Parameters.symbol.get(futureid).getLastPrice();
                double highestFloor = Utilities.roundTo((futurePrice - cushion * futurePrice / 100), Parameters.symbol.get(futureid).getStrikeDistance());
                double lowestCeiling = Utilities.roundTo((futurePrice + cushion * futurePrice / 100), Parameters.symbol.get(futureid).getStrikeDistance());
                //Get 2 strikes
                double[] putLevels = new double[3];
                putLevels[0] = highestFloor + Parameters.symbol.get(futureid).getStrikeDistance();;
                putLevels[1] = highestFloor;
                putLevels[2] = highestFloor - Parameters.symbol.get(futureid).getStrikeDistance();

                double[] callLevels = new double[3];
                callLevels[0] = lowestCeiling - Parameters.symbol.get(futureid).getStrikeDistance();
                callLevels[1] = lowestCeiling;
                callLevels[2] = lowestCeiling + Parameters.symbol.get(futureid).getStrikeDistance();

                for (double str : callLevels) {
                    int id = Utilities.insertStrike(Parameters.symbol, futureid, expiry, "CALL", Utilities.formatDouble(str, new DecimalFormat("#.##")));
                    allOrderList.add(id);
                }

                for (double str : putLevels) {
                    int id = Utilities.insertStrike(Parameters.symbol, futureid, expiry, "PUT", Utilities.formatDouble(str, new DecimalFormat("#.##")));
                    allOrderList.add(id);
                }

                for (int i : allOrderList) {
                    initSymbol(i, optionPricingUsingFutures);
                }

            } else {
                logger.log(Level.INFO, "Future Price not found for strategy {0}. Exiting", new Object[]{this.getStrategy()});
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, null, e);
        }
    }

    public boolean underlyingTradePriceExists(BeanSymbol s, int waitSeconds) {
        int underlying = s.getUnderlyingID();
        if (underlying == -1) {
            return false;
        } else {
            int i = 0;
            while (s.getUnderlying().value() <= 0) {
                if (i < waitSeconds) {
                    try {
                        Thread.sleep(1000);
                    } catch (Exception e) {
                        logger.log(Level.SEVERE, null, e);
                    }
                    Thread.yield();
                    i++;
                } else {
                    return false;
                }
            }
            return true;
        }
    }

    public boolean tradePriceExists(BeanSymbol s, int waitSeconds) {
        int id = s.getSerialno();
        if (id == -1) {
            return false;
        } else {
            int i = 0;
            while (s.getLastPrice() <= 0) {
                if (i < waitSeconds) {
                    try {
                        Thread.sleep(1000);
                    } catch (Exception e) {
                        logger.log(Level.SEVERE, null, e);
                    }
                    Thread.yield();
                    i++;
                } else {
                    return false;
                }
            }
            return true;
        }
    }

    private void loadAdditionalParameters(Properties p) {
        indexDisplayName = p.getProperty("IndexDisplayName", "NSENIFTY_IND___");
        avgMovePerDayEntry = Utilities.getDouble(p.getProperty("AverageMovePerDayEntry", "0.4"), 0.4);
        avgMovePerDayExit = Utilities.getDouble(p.getProperty("AverageMovePerDayExit", "0.2"), 0.2);
        thresholdReturnEntry = Utilities.getDouble(p.getProperty("ThresholdReturnEntry", "0.3"), 0.3);
        thresholdReturnExit = Utilities.getDouble(p.getProperty("ThresholdReturnExit", "0.15"), 0.15);
        historicalVol = Utilities.getDouble(p.getProperty("HistoricalVol", "0.7"), 0.7);
        margin = Utilities.getDouble(p.getProperty("Margin", "0.10"), 0.10);
        maxPositionSize = Utilities.getInt(p.getProperty("MaxPositionSize", "0"), 0);
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "IndexDisplayName" + delimiter + indexDisplayName});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "AvgMovePerDayEntry" + delimiter + avgMovePerDayEntry});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "AvgMovePerDayExit" + delimiter + avgMovePerDayExit});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "ThresholdReturnEntry" + delimiter + thresholdReturnEntry});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "ThresholdReturnExit" + delimiter + thresholdReturnExit});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "HistoricalVol" + delimiter + historicalVol});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "Margin" + delimiter + margin});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "MaxPositionSize" + delimiter + maxPositionSize});

    }

    @Override
    public void tradeReceived(TradeEvent event) {
        if (this.tradingWindow.get()) {
            Integer id = event.getSymbolID();
            if (getStrategySymbols().contains(id) && getPosition().get(id).getPosition() != 0) {
                int position = getPosition().get(id).getPosition();
                long optionDte = Parameters.symbol.get(id).getBdte();
                String right = Parameters.symbol.get(id).getRight();
                double optionReturn;
                double futurePrice;
                double indexPrice;
                double strikePrice;
                int underlyingid = Parameters.symbol.get(id).getUnderlyingID();
                if (Parameters.symbol.get(id).getCdte() > 0 && underlyingid >= 0) {
                    futurePrice = Parameters.symbol.get(underlyingid).getLastPrice();
                    //indexPrice = Parameters.symbol.get(indexid).getLastPrice();
                    strikePrice = Utilities.getDouble(Parameters.symbol.get(id).getOption(), 0);
                    optionReturn = Parameters.symbol.get(id).getLastPrice() * 365 / (Parameters.symbol.get(id).getCdte() * futurePrice * margin);
                    if (optionReturn == 0 || futurePrice == 0 || strikePrice == 0) {
                        logger.log(Level.FINE, "102,Flagging Calculated Option Return,{0}:{1}:{2}:{3}:{4},OptionReturn={5}:FuturePrice={6}:StrikePrice={7},DTE={8}",
                                new Object[]{getStrategy(), "Order", Parameters.symbol.get(id).getDisplayname(), -1, -1, optionReturn, futurePrice, strikePrice, Parameters.symbol.get(id).getCdte()});
                        return;
                    }
                    if (optionReturn > 0 && futurePrice > 0 && strikePrice > 0 && optionDte >= 0) {
                        OrderBean order = new OrderBean();
                        switch (right) {
                            case "CALL":
                                if ((optionReturn < thresholdReturnExit || (strikePrice - (Math.sqrt(optionDte) * historicalVol * avgMovePerDayExit * strikePrice / 100)) < futurePrice) && optionReturn > 0) {
                                    order.setOrderType(this.getOrdType());
                                    order.setParentDisplayName(Parameters.symbol.get(id).getDisplayname());
                                    order.setChildDisplayName(Parameters.symbol.get(id).getDisplayname());
                                    double limitprice = Utilities.getOptionLimitPriceForRel(Parameters.symbol, id, futureid, EnumOrderSide.COVER, "CALL", getTickSize());
                                    order.setLimitPrice(limitprice);
                                    order.setOrderSide(EnumOrderSide.COVER);
                                    order.setOriginalOrderSize(position);
                                    if (optionReturn < thresholdReturnExit) {
                                        order.setOrderReason(EnumOrderReason.TP);
                                    } else {
                                        order.setOrderReason(EnumOrderReason.SL);
                                    }
                                    order.setOrderStage(EnumOrderStage.INIT);
                                    order.setScale(this.scaleExit);
                                    order.setOrderAttributes(this.getOrderAttributes());
                                    if (limitprice > 0) {
                                        logger.log(Level.INFO, "101,Strategy COVER,{0}:{1}:{2}:{3}:{4},OptionReturn={5},FuturePrice={6}",
                                                new Object[]{getStrategy(), "Order", Parameters.symbol.get(id).getDisplayname(), -1, -1, optionReturn, String.valueOf(futurePrice)});
                                        exit(order);
                                    }
                                }
                                break;
                            case "PUT":
                                if ((optionReturn < thresholdReturnExit || (strikePrice + (Math.sqrt(optionDte) * historicalVol * avgMovePerDayExit * strikePrice) / 100) > futurePrice) && optionReturn > 0) {
                                    order.setOrderType(this.getOrdType());
                                    order.setParentDisplayName(Parameters.symbol.get(id).getDisplayname());
                                    order.setChildDisplayName(Parameters.symbol.get(id).getDisplayname());
                                    double limitprice = Utilities.getOptionLimitPriceForRel(Parameters.symbol, id, futureid, EnumOrderSide.COVER, "PUT", getTickSize());
                                    order.setLimitPrice(limitprice);
                                    order.setOrderSide(EnumOrderSide.COVER);
                                    order.setOriginalOrderSize(position);
                                    if (optionReturn < thresholdReturnExit) {
                                        order.setOrderReason(EnumOrderReason.TP);
                                    } else {
                                        order.setOrderReason(EnumOrderReason.TP);
                                    }
                                    order.setOrderStage(EnumOrderStage.INIT);
                                    order.setScale(scaleExit);
                                    order.setOrderAttributes(this.getOrderAttributes());
                                    if (limitprice > 0) {
                                        logger.log(Level.INFO, "101,Strategy COVER,{0}:{1}:{2}:{3}:{4},OptionReturn={5},FuturePrice={6}",
                                                new Object[]{getStrategy(), "Order", Parameters.symbol.get(id).getDisplayname(), -1, -1, optionReturn, String.valueOf(futurePrice)});
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
}
