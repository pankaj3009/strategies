/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.optsale;

import com.incurrency.algorithms.manager.Manager;
import com.incurrency.framework.Algorithm;
import com.incurrency.framework.BeanSymbol;
import com.incurrency.framework.EnumOrderSide;
import com.incurrency.framework.MainAlgorithm;
import com.incurrency.framework.Parameters;
import com.incurrency.framework.TradeEvent;
import com.incurrency.framework.TradeListener;
import com.incurrency.framework.Utilities;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Properties;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jquantlib.time.JDate;

/**
 *
 * @author Pankaj
 */
public class OptSale extends Manager implements TradeListener {

    Date entryScanDate;
    SimpleDateFormat sdtf_default = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final Logger logger = Logger.getLogger(OptSale.class.getName());
    String indexDisplayName;
    double avgMovePerDay;
    boolean buy;
    boolean shrt;
    String expiry;
    double threshold;
    double historicalVol;
    int size;

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
                int indexid = Utilities.getIDFromDisplayName(Parameters.symbol, indexDisplayName);
                if (indexid >= 0) {
                    SimpleDateFormat sdf_yyyyMMdd = new SimpleDateFormat("yyyyMMdd");
                    double indexPrice = Parameters.symbol.get(indexid).getLastPrice();
                    JDate expiryDate = new JDate(sdf_yyyyMMdd.parse(expiryNearMonth));
                    long dte = Algorithm.ind.businessDaysBetween(new JDate(new Date()), expiryDate);
                    expiry = expiryNearMonth;
                    if (dte <= 7) {
                        expiryDate = new JDate(sdf_yyyyMMdd.parse(expiryFarMonth));
                        dte = Algorithm.ind.businessDaysBetween(new JDate(new Date()), expiryDate);
                        expiry = expiryFarMonth;
                    }
                    double cushion = dte * avgMovePerDay;
                    double highestFloor = Utilities.roundTo(indexPrice - cushion, Parameters.symbol.get(indexid).getStrikeDistance());
                    double lowestCeiling = Utilities.roundTo(indexPrice + cushion, Parameters.symbol.get(indexid).getStrikeDistance());

                    //Get 2 strikes
                    double[] putLevels = new double[2];
                    putLevels[0] = highestFloor;
                    putLevels[1] = highestFloor - Parameters.symbol.get(indexid).getStrikeDistance();

                    double[] callLevels = new double[2];
                    callLevels[0] = lowestCeiling;
                    callLevels[1] = lowestCeiling + Parameters.symbol.get(indexid).getStrikeDistance();

                    //Initialize 
                    ArrayList<Integer> allOrderList = new ArrayList<>();
                    if (buy) {
                        for (double str : putLevels) {
                            int futureid = Utilities.getFutureIDFromExchangeSymbol(Parameters.symbol, indexid, expiry);
                            allOrderList = Utilities.getOptionIDForShortSystem(Parameters.symbol, getPosition(), futureid, EnumOrderSide.SHORT, String.valueOf(str));

                        }
                    }

                    if (shrt) {
                        for (double str : callLevels) {
                            int futureid = Utilities.getFutureIDFromExchangeSymbol(Parameters.symbol, indexid, expiry);
                            allOrderList.addAll(Utilities.getOptionIDForShortSystem(Parameters.symbol, getPosition(), futureid, EnumOrderSide.SHORT, String.valueOf(str)));
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
                        if (annualizedRet > threshold && theta > 1.2 * historicalVol && filteredOrderList.size() == 0) {
                            filteredOrderList.add(i);
                        } else if (annualizedRet > threshold && theta > 1.2 * historicalVol && metric < -0.3 && filteredOrderList.size() == 0) {
                            filteredOrderList.add(i);
                        }
                    }
                    //write orders to redis
                    for (int i : filteredOrderList) {
                        int position = getPosition().get(i).getPosition();
                        db.lpush("trades:" + getStrategy(), Parameters.symbol.get(i).getDisplayname() + ":" + size + ":short" + ":" + position);

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

    @Override
    public void tradeReceived(TradeEvent event) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
}
