/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.optsale;

import com.incurrency.algorithms.manager.Manager;
import com.incurrency.framework.Algorithm;
import com.incurrency.framework.BeanSymbol;
import com.incurrency.framework.DateUtil;
import com.incurrency.framework.EnumOrderReason;
import com.incurrency.framework.EnumOrderSide;
import com.incurrency.framework.EnumOrderStage;
import com.incurrency.framework.MainAlgorithm;
import com.incurrency.framework.Parameters;
import com.incurrency.framework.TradeEvent;
import com.incurrency.framework.TradeListener;
import com.incurrency.framework.TradingUtil;
import com.incurrency.framework.Utilities;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Properties;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jquantlib.time.JDate;

/**
 *
 * @author Pankaj
 */
public class OptSale extends Manager implements TradeListener {

    Date entryScanDate;
    int indexid;
    int futureid;
    SimpleDateFormat sdtf_default = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final Logger logger = Logger.getLogger(OptSale.class.getName());
    String indexDisplayName;
    double avgMovePerDayEntry;
    double avgMovePerDayExit;
    boolean buy;
    boolean shrt;
    String expiry;
    double thresholdReturn;
    double historicalVol;
    double dte = 1000000; //set dte to an arbitrarily large number so that trades are not exited before dte is actually calculated.
    double margin;

    public OptSale(MainAlgorithm m, Properties p, String parameterFile, ArrayList<String> accounts, Integer stratCount) {
        super(m, p, parameterFile, accounts, stratCount);
        Timer eodProcessing;
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());
        cal.setTimeZone(TimeZone.getTimeZone(Algorithm.timeZone));
        cal.add(Calendar.DATE, -1);
        Date priorEndDate = cal.getTime();
        if (new Date().before(this.getEndDate()) && new Date().after(priorEndDate)) {
            logger.log(Level.INFO, "Set EODProcessing Task at {0}", new Object[]{sdtf_default.format(entryScanDate)});
            eodProcessing = new Timer("Timer: " + this.getStrategy() + " EODProcessing");
            eodProcessing.schedule(eodProcessingTask, entryScanDate);
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
                    double indexPrice = Parameters.symbol.get(indexid).getLastPrice();
                    JDate expiryDate = new JDate(sdf_yyyyMMdd.parse(expiryNearMonth));
                    long dte = Algorithm.ind.businessDaysBetween(new JDate(new Date()), expiryDate);
                    expiry = expiryNearMonth;
                    futureid = Utilities.getFutureIDFromExchangeSymbol(Parameters.symbol, indexid, expiry);
                    if (dte <= 7) {
                        expiryDate = new JDate(sdf_yyyyMMdd.parse(expiryFarMonth));
                        dte = Algorithm.ind.businessDaysBetween(new JDate(new Date()), expiryDate);
                        expiry = expiryFarMonth;
                        futureid = Utilities.getFutureIDFromExchangeSymbol(Parameters.symbol, indexid, expiry);
                    }

                    
                    double cushion = dte * avgMovePerDayEntry;
                    double highestFloor = Utilities.roundTo(indexPrice - cushion, Parameters.symbol.get(futureid).getStrikeDistance());
                    double lowestCeiling = Utilities.roundTo(indexPrice + cushion, Parameters.symbol.get(futureid).getStrikeDistance());

                    //Get 2 strikes
                    double[] putLevels = new double[2];
                    putLevels[0] = highestFloor;
                    putLevels[1] = highestFloor - Parameters.symbol.get(futureid).getStrikeDistance();

                    double[] callLevels = new double[2];
                    callLevels[0] = lowestCeiling;
                    callLevels[1] = lowestCeiling + Parameters.symbol.get(futureid).getStrikeDistance();

                    //Initialize 
                    ArrayList<Integer> allOrderList = new ArrayList<>();
                    if (buy) {
                        for (double str : putLevels) {
                            allOrderList = Utilities.getOrInsertATMOptionIDForShortSystem(Parameters.symbol, getPosition(), futureid, EnumOrderSide.SHORT, String.valueOf(str));
                        }
                    }

                    if (shrt) {
                        for (double str : callLevels) {
                            allOrderList.addAll(Utilities.getOrInsertATMOptionIDForShortSystem(Parameters.symbol, getPosition(), futureid, EnumOrderSide.SHORT, String.valueOf(str)));
                        }
                    }

                    ArrayList<Integer> filteredOrderList = new ArrayList<>();
                    //Place Orders
                    for (int i : allOrderList) {
                        BeanSymbol s = Parameters.symbol.get(i);
                        double annualizedRet = s.getLastPrice() * 252 / (dte * indexPrice);
                        double theta = s.getOptionProcess().theta();
                        double vega = s.getOptionProcess().vega();
                        double metric = theta / vega;
                        if (annualizedRet > thresholdReturn && theta > 1.2 * historicalVol && filteredOrderList.size() == 0) {
                            filteredOrderList.add(i);
                        } else if (annualizedRet > thresholdReturn && theta > 1.2 * historicalVol && metric < -0.3 && filteredOrderList.size() == 0) {
                            filteredOrderList.add(i);
                        }
                    }
                    //write orders to redis
                    for (int i : filteredOrderList) {
                        int position = getPosition().get(i).getPosition();
                        db.lpush("trades:" + getStrategy(), Parameters.symbol.get(i).getDisplayname() + ":" + getNumberOfContracts() + ":short" + ":" + position);
                    }

                } else {
                    logger.log(Level.INFO, "501, {0},{1},{2},{3},{4} No Index ID Found. Index:{5}", new Object[]{getStrategy(), "Order", -1, -1, -1, indexDisplayName});
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

    private void loadParameters(String strategy, String type, Properties p) {
        indexDisplayName = p.getProperty("IndexDisplayName", "NSENIFTY_IND___");
        avgMovePerDayEntry = Utilities.getDouble(p.getProperty("AverageMovePerDayEntry", "0.4"), 0.4);
        avgMovePerDayExit = Utilities.getDouble(p.getProperty("AverageMovePerDayExit", "0.2"), 0.2);
        thresholdReturn = Utilities.getDouble(p.getProperty("ThresholdReturn", "0.3"), 0.3);
        historicalVol = Utilities.getDouble(p.getProperty("HistoricalVol", "0.15"), 0.15);
        margin = Utilities.getDouble(p.getProperty("Margin", "0.10"), 0.10);
    }

    @Override
    public void tradeReceived(TradeEvent event) {
        Integer id = event.getSymbolID();
        if (getStrategySymbols().contains(id) && getPosition().get(id).getPosition() > 0) {
            int position = getPosition().get(id).getPosition();
            int optionDte = Parameters.symbol.get(id).getDte();
            String right = Parameters.symbol.get(id).getRight();
            if (Parameters.symbol.get(id).getDte() > 0) {
                double futurePrice = Parameters.symbol.get(futureid).getLastPrice();
                double strikePrice = Utilities.getDouble(Parameters.symbol.get(id).getOption(), 0);
                double optionReturn = Parameters.symbol.get(id).getLastPrice() * 365 / (Parameters.symbol.get(indexid).getDte() * futurePrice * margin);
                HashMap<String, Object> order = new HashMap<>();
                switch (right) {
                    case "CALL":
                        if (optionReturn < 0.1 || (strikePrice - optionDte * avgMovePerDayExit) < futurePrice) {
                            order.put("type", getOrdType());
                            order.put("expiretime", getMaxOrderDuration());
                            order.put("dynamicorderduration", getDynamicOrderDuration());
                            order.put("maxslippage", this.getMaxSlippageEntry());
                            order.put("id", id);
                            double limitprice = getOptionLimitPriceForRel(id, indexid, EnumOrderSide.COVER, "CALL");
                            order.put("limitprice", limitprice);
                            order.put("side", EnumOrderSide.BUY);
                            order.put("size", position);
                            order.put("reason", EnumOrderReason.REGULARENTRY);
                            order.put("orderstage", EnumOrderStage.INIT);
                            order.put("scale", getScaleExit());
                            order.put("dynamicorderduration", this.getDynamicOrderDuration());
                            order.put("expiretime", 0);
                            logger.log(Level.INFO, "501,Strategy BUY,{0}", new Object[]{getStrategy() + delimiter + "BUY" + delimiter + Parameters.symbol.get(id).getDisplayname()});
                            int orderid = exit(order);
                        }
                        break;
                    case "PUT":
                        if (optionReturn < 0.1 || (strikePrice + optionDte * avgMovePerDayExit) > futurePrice) {
                            double limitprice = getOptionLimitPriceForRel(id, indexid, EnumOrderSide.COVER, "PUT");
                            order.put("limitprice", limitprice);
                            order.put("side", EnumOrderSide.BUY);
                            order.put("size", position);
                            order.put("reason", EnumOrderReason.REGULARENTRY);
                            order.put("orderstage", EnumOrderStage.INIT);
                            order.put("scale", getScaleExit());
                            order.put("dynamicorderduration", this.getDynamicOrderDuration());
                            order.put("expiretime", 0);
                            logger.log(Level.INFO, "501,Strategy BUY,{0}", new Object[]{getStrategy() + delimiter + "BUY" + delimiter + Parameters.symbol.get(id).getDisplayname()});
                            int orderid = exit(order);
                        }
                        break;
                    default:

                }
            }

        }
    }
}
