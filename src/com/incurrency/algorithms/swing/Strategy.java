/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.algorithms.swing;

import com.incurrency.framework.BeanConnection;
import com.incurrency.framework.BeanSymbol;
import com.incurrency.framework.BrokerageRate;
import com.incurrency.framework.DateUtil;
import com.incurrency.framework.MainAlgorithm;
import com.incurrency.framework.OrderPlacement;
import com.incurrency.framework.Parameters;
import com.incurrency.framework.ProfitLossManager;
import com.incurrency.framework.Trade;
import com.incurrency.framework.TradingUtil;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.supercsv.io.CsvBeanWriter;
import org.supercsv.prefs.CsvPreference;

/**
 *
 * @author pankaj
 */
public class Strategy {

    //--common parameters required for all strategies
    MainAlgorithm m;
    HashMap<Integer, Integer> internalOpenOrders = new HashMap(); //holds mapping of symbol id to latest initialization internal order
    HashMap<Integer, Trade> trades = new HashMap();
    double tickSize;
    double pointValue = 1;
    int internalOrderID = 1;
    int numberOfContracts = 0;
    Date endDate;
    Date startDate;
    private Boolean longOnly = true;
    private Boolean shortOnly = true;
    private Boolean aggression = true;
    private double clawProfitTarget = 0;
    private double dayProfitTarget = 0;
    private double dayStopLoss = 0;
    private double maxSlippageEntry = 0;
    private double maxSlippageExit = 0;
    private int maxOrderDuration = 3;
    private int dynamicOrderDuration = 1;
    private int maxOpenPositions = 1;
    private String futBrokerageFile;
    private ArrayList<BrokerageRate> brokerageRate = new ArrayList<>();
    private String tradeFile;
    private String orderFile;
    private String timeZone;
    private double startingCapital; 
    private ProfitLossManager plmanager;
    List<Integer> strategySymbols = new ArrayList();
    private static final Logger logger = Logger.getLogger(Strategy.class.getName());
    private OrderPlacement oms;
    private String strategy;

    public Strategy(MainAlgorithm m, String strategy, String type) {
        this.m = m;
        loadParameters(strategy, type);
        for (BeanSymbol s : Parameters.symbol) {
            if (Pattern.compile(Pattern.quote(strategy), Pattern.CASE_INSENSITIVE).matcher(s.getStrategy()).find()) {
                strategySymbols.add(s.getSerialno() - 1);
            }
        }
        this.strategy = strategy;
        oms = new OrderPlacement(getAggression(), this.tickSize, endDate, strategy, pointValue, maxOpenPositions, timeZone);
        plmanager = new ProfitLossManager(strategy, this.strategySymbols, pointValue, getClawProfitTarget(), getDayProfitTarget(), getDayStopLoss());
        Timer closeProcessing = new Timer("Timer: " + strategy + " CloseProcessing");
        closeProcessing.schedule(runPrintOrders, com.incurrency.framework.DateUtil.addSeconds(endDate, (this.maxOrderDuration + 1) * 60));

    }

    private void loadParameters(String strategy, String type) {
        Properties p = new Properties(System.getProperties());
        FileInputStream propFile;
        try {
            propFile = new FileInputStream(MainAlgorithm.input.get(strategy));
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
        String startDateStr = currDateStr + " " + System.getProperty("StartTime");
        String endDateStr = currDateStr + " " + System.getProperty("EndTime");
        startDate = DateUtil.parseDate("yyyyMMdd HH:mm:ss", startDateStr);
        endDate = DateUtil.parseDate("yyyyMMdd HH:mm:ss", endDateStr);
        if (new Date().compareTo(endDate) > 0) {
            //increase enddate by one calendar day
            endDate = DateUtil.addDays(endDate, 1);
        }
        maxSlippageEntry = Double.parseDouble(System.getProperty("MaxSlippageEntry")) / 100; // divide by 100 as input was a percentage
        setMaxSlippageExit(Double.parseDouble(System.getProperty("MaxSlippageExit")) / 100); // divide by 100 as input was a percentage
        setMaxOrderDuration(Integer.parseInt(System.getProperty("MaxOrderDuration")));
        setDynamicOrderDuration(Integer.parseInt(System.getProperty("DynamicOrderDuration")));
        m.setCloseDate(DateUtil.addSeconds(endDate, (this.getMaxOrderDuration() + 2) * 60)); //2 minutes after the enddate+max order duaration
        tickSize = Double.parseDouble(System.getProperty("TickSize"));
        numberOfContracts = Integer.parseInt(System.getProperty("NumberOfContracts"));
        pointValue = System.getProperty("PointValue") == null ? 1 : Double.parseDouble(System.getProperty("PointValue"));
        setClawProfitTarget(System.getProperty("ClawProfitTarget") != null ? Double.parseDouble(System.getProperty("ClawProfitTarget")) : 0D);
        setDayProfitTarget(System.getProperty("DayProfitTarget") != null ? Double.parseDouble(System.getProperty("DayProfitTarget")) : 0D);
        setDayStopLoss(System.getProperty("DayStopLoss") != null ? Double.parseDouble(System.getProperty("DayStopLoss")) : 0D);
        maxOpenPositions = System.getProperty("MaxOpenPositions") == null ? 1 : Integer.parseInt(System.getProperty("MaximumOpenPositions"));
        futBrokerageFile = System.getProperty("BrokerageFile") == null ? "" : System.getProperty("BrokerageFile");
        tradeFile = System.getProperty("TradeFile");
        orderFile = System.getProperty("OrderFile");
        timeZone = System.getProperty("TradeTimeZone") == null ? "" : System.getProperty("TradeTimeZone");
        startingCapital = System.getProperty("StartingCapital") == null ? 0D : Double.parseDouble(System.getProperty("StartingCapital"));


        logger.log(Level.INFO, "-----" + strategy.toUpperCase() + " Parameters----");
        logger.log(Level.INFO, "end Time: {0}", endDate);
        logger.log(Level.INFO, "Print Time: {0}", com.incurrency.framework.DateUtil.addSeconds(endDate, (this.getMaxOrderDuration() + 1) * 60));
        logger.log(Level.INFO, "ShutDown time: {0}", DateUtil.addSeconds(endDate, (this.getMaxOrderDuration() + 2) * 60));
        logger.log(Level.INFO, "TickSize: {0}", tickSize);
        logger.log(Level.INFO, "Number of contracts to be traded: {0}", numberOfContracts);
        logger.log(Level.INFO, "Claw Profit in increments of: {0}", getClawProfitTarget());
        logger.log(Level.INFO, "Day Profit Target: {0}", getDayProfitTarget());
        logger.log(Level.INFO, "Day Stop Loss: {0}", getDayStopLoss());
        logger.log(Level.INFO, "PointValue: {0}", pointValue);
        logger.log(Level.INFO, "Maxmimum slippage allowed for entry: {0}", maxSlippageEntry);
        logger.log(Level.INFO, "Maximum slippage allowed for exit: {0}", getMaxSlippageExit());
        logger.log(Level.INFO, "Max Order Duration: {0}", getMaxOrderDuration());
        logger.log(Level.INFO, "Dynamic Order Duration: {0}", getDynamicOrderDuration());
        logger.log(Level.INFO, "Open Positions Limit: {0}", maxOpenPositions);
        logger.log(Level.INFO, "Brokerage File: {0}", futBrokerageFile);
        logger.log(Level.INFO, "Trade File: {0}", tradeFile);
        logger.log(Level.INFO, "Order File: {0}", orderFile);
        logger.log(Level.INFO, "Time Zone: {0}", timeZone);
        logger.log(Level.INFO, "Starting Capital: {0}", startingCapital);
        if (futBrokerageFile.compareTo("") != 0) {
            try {
                p.clear();
                //retrieve parameters from brokerage file
                propFile = new FileInputStream(futBrokerageFile);
                try {
                    p.load(propFile);
                    System.setProperties(p);
                } catch (Exception ex) {
                    logger.log(Level.SEVERE, null, ex);
                }
            } catch (Exception ex) {
                logger.log(Level.SEVERE, null, ex);
            }
            String brokerage1 = System.getProperty("Brokerage");
            String addOn1 = System.getProperty("AddOn1");
            String addOn2 = System.getProperty("AddOn2");
            String addOn3 = System.getProperty("AddOn3");
            String addOn4 = System.getProperty("AddOn4");

            if (brokerage1 != null) {
                brokerageRate.add(TradingUtil.parseBrokerageString(brokerage1, type));
            }
            if (addOn1 != null) {
                brokerageRate.add(TradingUtil.parseBrokerageString(addOn1, type));
            }
            if (addOn2 != null) {
                brokerageRate.add(TradingUtil.parseBrokerageString(addOn2, type));
            }
            if (addOn3 != null) {
                brokerageRate.add(TradingUtil.parseBrokerageString(addOn3, type));
            }
            if (addOn4 != null) {
                brokerageRate.add(TradingUtil.parseBrokerageString(addOn4, type));
            }

        }

    }
    TimerTask runPrintOrders = new TimerTask() {
        public void run() {
            System.out.println("In Printorders");
            logger.log(Level.INFO, "Print Orders Called in {0}", strategy);
            printOrders("");
        }
    };

    public void printOrders(String prefix) {
        FileWriter file;
        double[] profitGrid = new double[5];
        DecimalFormat df = new DecimalFormat("#.##");
        try {
            boolean writeHeader = false;
            String filename = prefix + orderFile;
            profitGrid = TradingUtil.applyBrokerage(trades, brokerageRate, pointValue, orderFile, timeZone, startingCapital, "Order");
            TradingUtil.writeToFile("body.txt", "-----------------Orders:" + strategy + " --------------------------------------------------");
            TradingUtil.writeToFile("body.txt", "Gross P&L today: " + df.format(profitGrid[0]));
            TradingUtil.writeToFile("body.txt", "Brokerage today: " + df.format(profitGrid[1]));
            TradingUtil.writeToFile("body.txt", "Net P&L today: " + df.format(profitGrid[2]));
            TradingUtil.writeToFile("body.txt", "MTD P&L: " + df.format(profitGrid[3]));
            TradingUtil.writeToFile("body.txt", "YTD P&L: " + df.format(profitGrid[4]));
            TradingUtil.writeToFile("body.txt", "Max Drawdown (%): " + df.format(profitGrid[5]));
            TradingUtil.writeToFile("body.txt", "Max Drawdown (days): " + df.format(profitGrid[6]));
            TradingUtil.writeToFile("body.txt", "Avg Drawdown (days): " + df.format(profitGrid[7]));
            TradingUtil.writeToFile("body.txt", "Sharpe Ratio: " + df.format(profitGrid[8]));
            TradingUtil.writeToFile("body.txt", "# days in history: " + df.format(profitGrid[9]));
            if (new File(filename).exists()) {
                writeHeader = false;
            } else {
                writeHeader = true;
            }
            file = new FileWriter(filename, true);
            String[] header = new String[]{
                "entrySymbol", "entryType", "entryExpiry", "entryRight", "entryStrike",
                "entrySide", "entryPrice", "entrySize", "entryTime", "entryID", "entryBrokerage", "filtered", "exitSymbol",
                "exitType", "exitExpiry", "exitRight", "exitStrike", "exitSide", "exitPrice",
                "exitSize", "exitTime", "exitID", "exitBrokerage", "accountName"};
            CsvBeanWriter orderWriter = new CsvBeanWriter(file, CsvPreference.EXCEL_PREFERENCE);
            if (writeHeader) {//this ensures header is written only the first time
                orderWriter.writeHeader(header);
            }
            for (Map.Entry<Integer, Trade> order : trades.entrySet()) {
                orderWriter.write(order.getValue(), header, Parameters.getTradeProcessorsWrite());
            }
            orderWriter.close();
            logger.log(Level.INFO, "Clean Exit after writing orders");

            //Write trade summary for each account
            for (BeanConnection c : Parameters.connection) {
                if (c.getStrategy().contains(strategy)) {
                    filename = prefix + tradeFile;
                    profitGrid = TradingUtil.applyBrokerage(getOms().getTrades(), brokerageRate, pointValue, tradeFile, timeZone, startingCapital, c.getAccountName());
                    TradingUtil.writeToFile("body.txt", "-----------------Trades: " + strategy + " , Account: " + c.getAccountName() + "----------------------");
                    TradingUtil.writeToFile("body.txt", "Gross P&L today: " + df.format(profitGrid[0]));
                    TradingUtil.writeToFile("body.txt", "Brokerage today: " + df.format(profitGrid[1]));
                    TradingUtil.writeToFile("body.txt", "Net P&L today: " + df.format(profitGrid[2]));
                    TradingUtil.writeToFile("body.txt", "MTD P&L: " + df.format(profitGrid[3]));
                    TradingUtil.writeToFile("body.txt", "YTD P&L: " + df.format(profitGrid[4]));
                    TradingUtil.writeToFile("body.txt", "Max Drawdown (%): " + df.format(profitGrid[5]));
                    TradingUtil.writeToFile("body.txt", "Max Drawdown (days): " + df.format(profitGrid[6]));
                    TradingUtil.writeToFile("body.txt", "Avg Drawdown (days): " + df.format(profitGrid[7]));
                    TradingUtil.writeToFile("body.txt", "Sharpe Ratio: " + df.format(profitGrid[8]));
                    TradingUtil.writeToFile("body.txt", "# days in history: " + df.format(profitGrid[9]));

                    if (new File(filename).exists()) {
                        writeHeader = false;
                    } else {
                        writeHeader = true;
                    }
                    file = new FileWriter(filename, true);
                    CsvBeanWriter tradeWriter = new CsvBeanWriter(file, CsvPreference.EXCEL_PREFERENCE);
                    if (writeHeader) {//this ensures header is written only the first time
                        tradeWriter.writeHeader(header);
                    }
                    for (Map.Entry<Integer, Trade> trade : getOms().getTrades().entrySet()) {
                        tradeWriter.write(trade.getValue(), header, Parameters.getTradeProcessorsWrite());
                    }
                    tradeWriter.close();
                    logger.log(Level.INFO, "Clean Exit after writing trades");
                    //System.exit(0);
                }
            }
        } catch (IOException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    /**
     * @return the oms
     */
    public OrderPlacement getOms() {
        return oms;
    }

    /**
     * @param oms the oms to set
     */
    public void setOms(OrderPlacement oms) {
        this.oms = oms;
    }

    /**
     * @return the longOnly
     */
    public Boolean getLongOnly() {
        return longOnly;
    }

    /**
     * @param longOnly the longOnly to set
     */
    public void setLongOnly(Boolean longOnly) {
        this.longOnly = longOnly;
    }

    /**
     * @return the shortOnly
     */
    public Boolean getShortOnly() {
        return shortOnly;
    }

    /**
     * @param shortOnly the shortOnly to set
     */
    public void setShortOnly(Boolean shortOnly) {
        this.shortOnly = shortOnly;
    }

    /**
     * @return the aggression
     */
    public Boolean getAggression() {
        return aggression;
    }

    /**
     * @param aggression the aggression to set
     */
    public void setAggression(Boolean aggression) {
        this.aggression = aggression;
    }

    /**
     * @return the clawProfitTarget
     */
    public double getClawProfitTarget() {
        return clawProfitTarget;
    }

    /**
     * @param clawProfitTarget the clawProfitTarget to set
     */
    public void setClawProfitTarget(double clawProfitTarget) {
        this.clawProfitTarget = clawProfitTarget;
    }

    /**
     * @return the dayProfitTarget
     */
    public double getDayProfitTarget() {
        return dayProfitTarget;
    }

    /**
     * @param dayProfitTarget the dayProfitTarget to set
     */
    public void setDayProfitTarget(double dayProfitTarget) {
        this.dayProfitTarget = dayProfitTarget;
    }

    /**
     * @return the dayStopLoss
     */
    public double getDayStopLoss() {
        return dayStopLoss;
    }

    /**
     * @param dayStopLoss the dayStopLoss to set
     */
    public void setDayStopLoss(double dayStopLoss) {
        this.dayStopLoss = dayStopLoss;
    }

    /**
     * @return the plmanager
     */
    public ProfitLossManager getPlmanager() {
        return plmanager;
    }

    /**
     * @param plmanager the plmanager to set
     */
    public void setPlmanager(ProfitLossManager plmanager) {
        this.plmanager = plmanager;
    }

    /**
     * @return the maxOrderDuration
     */
    public int getMaxOrderDuration() {
        return maxOrderDuration;
    }

    /**
     * @param maxOrderDuration the maxOrderDuration to set
     */
    public void setMaxOrderDuration(int maxOrderDuration) {
        this.maxOrderDuration = maxOrderDuration;
    }

    /**
     * @return the dynamicOrderDuration
     */
    public int getDynamicOrderDuration() {
        return dynamicOrderDuration;
    }

    /**
     * @param dynamicOrderDuration the dynamicOrderDuration to set
     */
    public void setDynamicOrderDuration(int dynamicOrderDuration) {
        this.dynamicOrderDuration = dynamicOrderDuration;
    }

    /**
     * @return the maxSlippageExit
     */
    public double getMaxSlippageExit() {
        return maxSlippageExit;
    }

    /**
     * @param maxSlippageExit the maxSlippageExit to set
     */
    public void setMaxSlippageExit(double maxSlippageExit) {
        this.maxSlippageExit = maxSlippageExit;
    }
}
