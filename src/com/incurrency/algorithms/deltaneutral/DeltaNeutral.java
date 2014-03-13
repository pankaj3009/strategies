/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.algorithms.deltaneutral;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.incurrency.framework.BeanConnection;
import com.incurrency.framework.BeanOHLC;
import com.incurrency.framework.BeanSymbol;
import com.incurrency.framework.BidAskEvent;
import com.incurrency.framework.BidAskListener;
import com.incurrency.framework.DateUtil;
import com.incurrency.framework.EnumBarSize;
import com.incurrency.framework.HistoricalBarEvent;
import com.incurrency.framework.HistoricalBarListener;
import com.incurrency.framework.HistoricalBars;
import com.incurrency.framework.MainAlgorithm;
import com.incurrency.framework.MainAlgorithmUI;
import com.incurrency.framework.Parameters;
import com.incurrency.framework.Splits;
import com.incurrency.framework.Trade;
import com.incurrency.framework.TradeEvent;
import com.incurrency.framework.TradeListner;
import com.incurrency.framework.TradingUtil;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

/**
 *
 * @author pankaj
 */
public class DeltaNeutral implements TradeListner, BidAskListener, HistoricalBarListener {

    private static final Logger logger = Logger.getLogger(DeltaNeutral.class.getName());
    HashMap<Integer, Trade> trades = new HashMap();
    Double volatilityMarkup;
    Double zscore;
    Boolean trading;
    Date endDate;
    int numberOfContracts;
    double takeProfit;
    double stopLoss;
    double tickSize;
    Boolean aggression;
    int totalOrderDuration;
    int dynamicOrderDuration;
    int maxOpenPositions = 0;
    int transactionCostPerCombination = 0;
    DeltaNeutralOrderManagement ord;
    String split;
    private ArrayList<Splits> splits = new ArrayList();
    private ArrayList<TreeMap<Long, BeanOHLC>> historicalData = new ArrayList();
    private ArrayList<Double> dailyReturns;
    private ArrayList<Double> opencloseReturns;
    private ArrayList<ArrayList<Double>> threeMonthMeanReturn;
    private ArrayList<ArrayList<Double>> oneMonthMeanReturn;
    private ArrayList<ArrayList<Double>> threeMonthHistoricalVol;
    private ArrayList<ArrayList<Double>> oneMonthHistoricalVol;
    private ArrayList<ArrayList<Double>> threeMonthOpenCloseMeanReturn;
    private ArrayList<ArrayList<Double>> oneMonthOpenCloseMeanReturn;
    private ArrayList<ArrayList<Double>> threeMonthOpenCloseHistoricalVol;
    private ArrayList<ArrayList<Double>> oneMonthOpenCloseHistoricalVol;
    private List<Integer> tradeableSymbols = new ArrayList();

    public DeltaNeutral() {
        loadParameters();
        ord = new DeltaNeutralOrderManagement(aggression, tickSize, "dno");
        for (BeanConnection c : Parameters.connection) {
            c.getWrapper().addTradeListener(this);
            c.getWrapper().addBidAskListener(this);
            c.initializeConnection("dno");
        }
        for (BeanSymbol s : Parameters.symbol) {
            if (s.getStrategy().contains("dno")) {
                tradeableSymbols.add(s.getSerialno() - 1);
                historicalData.add(new <Long, BeanOHLC>TreeMap());
                threeMonthMeanReturn.add(new <Double>ArrayList());
                this.oneMonthMeanReturn.add(new <Double>ArrayList());
                this.threeMonthHistoricalVol.add(new <Double>ArrayList());
                this.oneMonthHistoricalVol.add(new <Double>ArrayList());
                threeMonthOpenCloseMeanReturn.add(new <Double>ArrayList());
                this.oneMonthOpenCloseMeanReturn.add(new <Double>ArrayList());
                this.threeMonthOpenCloseHistoricalVol.add(new <Double>ArrayList());
                this.oneMonthOpenCloseHistoricalVol.add(new <Double>ArrayList());

            }
            getDailyHistoricalData("dno");
            generateEODStats();

        }
    }

    private void loadParameters() {
        Properties p = new Properties(System.getProperties());
        FileInputStream propFile;
        try {
            propFile = new FileInputStream(MainAlgorithm.input.get("dno"));
            try {
                p.load(propFile);
            } catch (Exception ex) {
                logger.log(Level.SEVERE, null, ex);
            }
        } catch (Exception ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        System.setProperties(p);
        String currDateStr = DateUtil.getFormatedDate("yyyyMMdd", Parameters.connection.get(0).getConnectionTime());
        String endDateStr = currDateStr + " " + System.getProperty("EndTime");
        endDate = DateUtil.parseDate("yyyyMMdd HH:mm:ss", endDateStr);
        if (new Date().compareTo(endDate) > 0) {
            //increase enddate by one calendar day
            endDate = DateUtil.addDays(endDate, 1);
        }
        trading = Boolean.valueOf(System.getProperty("Trading"));
        numberOfContracts = Integer.parseInt(System.getProperty("NumberOfContracts"));
        stopLoss = Double.parseDouble(System.getProperty("StopLoss"));
        tickSize = Double.parseDouble(System.getProperty("TickSize"));
        aggression = Boolean.parseBoolean(System.getProperty("Aggression"));
        totalOrderDuration = Integer.parseInt(System.getProperty("TotalOrderDuration"));
        dynamicOrderDuration = Integer.parseInt(System.getProperty("DynamicOrderDuration"));
        maxOpenPositions = Integer.parseInt(System.getProperty("MaxOpenPositions"));
        transactionCostPerCombination = Integer.parseInt(System.getProperty("TransactionCostPerComibination"));
        split = System.getProperty("Splits");
        volatilityMarkup = Double.parseDouble(System.getProperty("VolatilityMarkup"));
        zscore = Double.parseDouble(System.getProperty("zscore"));
        List<String> items = Arrays.asList(split.split("\\s*;\\s*"));
        for (String str : items) {
            List<String> temp = Arrays.asList(str.split("\\s*,\\s*"));
            try {
                int id = TradingUtil.getIDFromSymbol(temp.get(0), "STK", "", "", "");
                int oldShares = Integer.parseInt(temp.get(2).split("\\s*:\\s*")[0]);
                int newShares = Integer.parseInt(temp.get(2).split("\\s*:\\s*")[1]);
                Date splitDate = DateUtil.parseDate("dd/mm/yyyy", temp.get(1));
                splits.add(new Splits(id, temp.get(0), oldShares, newShares, splitDate));

            } catch (Exception e) {
                logger.log(Level.INFO, "Split could not be processed. {0}", new Object[]{str});
                logger.log(Level.SEVERE, null, e);
            }
        }


        logger.log(Level.INFO, "-----Delta Neutral Options Parameters----");
        logger.log(Level.INFO, "end Time: {0}", endDate);
        logger.log(Level.INFO, "Volatility Threshold: {0}", volatilityMarkup);
        logger.log(Level.INFO, "zscore: {0}", zscore);
        logger.log(Level.INFO, "Number of contracts to be traded: {0}", numberOfContracts);
        logger.log(Level.INFO, "Stop Loss: {0}", stopLoss);
        logger.log(Level.INFO, "Stop Loss: {0}", takeProfit);
        logger.log(Level.INFO, "TickSize: {0}", tickSize);
        logger.log(Level.INFO, "Aggression: {0}", aggression);
        logger.log(Level.INFO, "Total Order Duration: {0}", totalOrderDuration);
        logger.log(Level.INFO, "Dynamic Order Duration: {0}", dynamicOrderDuration);
        logger.log(Level.INFO, "Max Open Positions: {0}", maxOpenPositions);
        logger.log(Level.INFO, "Transaction Cost Per Combination: {0}", transactionCostPerCombination);

    }

    private void getDailyHistoricalData(String strategy) {
        try {
            //get historical data - this can be done before start time, assuming the program is started next day

            Thread t = new Thread(new HistoricalBars(strategy));
            t.setName("Historical Bars");
            if (!MainAlgorithmUI.headless) {
                MainAlgorithmUI.setMessage("Starting request of Historical Data for yesterday");
            }
            t.start();
            t.join();
        } catch (InterruptedException ex) {
            logger.log(Level.SEVERE, null, ex);
        }

    }

    private void generateEODStats() {
        /*
         * Generates
         * a. Adjusts for any splits
         * a. Daily Returns. Returns are linked to open date
         * b. Open Returns. Percentage Returns between open and close. Returns are linked to open date
         * c. Calculate mean and sd of daily returns for 3 month and one month
         * d. Calculate mean and sd of open returns for 3 month and one month
         */
        //a. Adjust for splits
        for (Splits s : splits) {
            int id = s.getId();
            TreeMap<Long, BeanOHLC> hist = Parameters.symbol.get(id).getDailyBar().getHistoricalBars();
            for (Map.Entry<Long, BeanOHLC> entry : hist.entrySet()) {
                if (entry.getKey() < s.getEffectiveDate().getTime()) {
                    entry.getValue().setOpen(entry.getValue().getOpen() * s.getOldShares() / s.getNewShares());
                    entry.getValue().setHigh(entry.getValue().getHigh() * s.getOldShares() / s.getNewShares());
                    entry.getValue().setLow(entry.getValue().getLow() * s.getOldShares() / s.getNewShares());
                    entry.getValue().setClose(entry.getValue().getClose() * s.getOldShares() / s.getNewShares());
                    entry.getValue().setVolume(entry.getValue().getVolume() * s.getNewShares() / s.getOldShares());
                }
            }
        }

        int i = 0;

        for (BeanSymbol symb : Parameters.symbol) {
            if (symb.getDailyBar().getHistoricalBars() == null) {
                this.dailyReturns.add(0D);
                this.opencloseReturns.add(0D);
                this.oneMonthMeanReturn.add(null);
                this.oneMonthHistoricalVol.add(null);
                this.threeMonthMeanReturn.add(null);
                this.threeMonthHistoricalVol.add(null);
            } else {
                List<BeanOHLC> lastValues;
                lastValues = Lists.newArrayList(Iterables.limit(symb.getDailyBar().getHistoricalBars().descendingMap().values(), 300));
                lastValues = com.google.common.collect.Lists.reverse(lastValues);
                for (i = 0; i < lastValues.size() - 1; i++) {
                    this.dailyReturns.add((lastValues.get(i + 1).getClose() - lastValues.get(i).getClose()) / lastValues.get(i).getClose());
                    this.opencloseReturns.add((lastValues.get(i + 1).getOpen() - lastValues.get(i).getClose()) / lastValues.get(i).getClose());
                }
                DescriptiveStatistics dailystats90 = new DescriptiveStatistics();
                dailystats90.setWindowSize(90);
                int count = 0;
                for (double value : dailyReturns) {
                    count = count + 1;
                    dailystats90.addValue(value);
                    if (count > 90) {
                        this.threeMonthMeanReturn.get(i).add(dailystats90.getMean());
                        this.threeMonthHistoricalVol.get(i).add(Math.sqrt(dailystats90.getVariance()));
                    }
                }
                DescriptiveStatistics dailystats30 = new DescriptiveStatistics();
                dailystats30.setWindowSize(30);
                count = 0;
                for (double value : dailyReturns) {
                    count = count + 1;
                    dailystats30.addValue(value);
                    if (count > 30) {
                        this.oneMonthMeanReturn.get(i).add(dailystats30.getMean());
                        this.oneMonthHistoricalVol.get(i).add(Math.sqrt(dailystats30.getVariance()));
                    }
                }
                DescriptiveStatistics openclosestats30 = new DescriptiveStatistics();
                openclosestats30.setWindowSize(30);
                count = 0;
                for (double value : opencloseReturns) {
                    count = count + 1;
                    dailystats30.addValue(value);
                    if (count > 30) {
                        this.oneMonthOpenCloseMeanReturn.get(i).add(dailystats30.getMean());
                        this.oneMonthOpenCloseHistoricalVol.get(i).add(Math.sqrt(dailystats30.getVariance()));
                    }
                }

                DescriptiveStatistics openclosestats90 = new DescriptiveStatistics();
                openclosestats90.setWindowSize(90);
                count = 0;
                for (double value : opencloseReturns) {
                    count = count + 1;
                    dailystats90.addValue(value);
                    if (count > 90) {
                        this.threeMonthOpenCloseMeanReturn.get(i).add(dailystats90.getMean());
                        this.threeMonthOpenCloseHistoricalVol.get(i).add(Math.sqrt(dailystats90.getVariance()));
                    }
                }
            }
        }
    }

    @Override
    public void tradeReceived(TradeEvent event) {
        int id = event.getSymbolID() - 1;
        if (tradeableSymbols.contains(id) && Parameters.symbol.get(id).getType().compareTo("OPT") == 0) {
            //Entry
            //option (both call and put) vol/1 month vol>2 && option vol/3 month vol >2 and 1 month vol < historical 3 m vol 
            //buy sell option
            //buy or sell future
            //update combination position.
            //Exit
            //if market closing and position is in profit, exit
            //else write position to log file
            //
        }
    }

    @Override
    public void bidaskChanged(BidAskEvent event) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void barsReceived(HistoricalBarEvent event) {
        /*Dont need this code as bars are stored in symbol
        int id = event.getSymbol().getSerialno() - 1;
        if (event.ohlc().getPeriodicity() == EnumBarSize.Daily && tradeableSymbols.contains(event.getSymbol().getSerialno() - 1)) {
            //bars are thrown starting from first day in the range to the last day in range
            historicalData.get(id).put(event.ohlc().getOpenTime(), new BeanOHLC(event.ohlc()));
        }*/
    }
    
}
