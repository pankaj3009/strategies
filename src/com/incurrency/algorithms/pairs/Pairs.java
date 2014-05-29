/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.algorithms.pairs;

import com.RatesClient.Subscribe;
import com.incurrency.framework.BeanConnection;
import com.incurrency.framework.BeanSymbol;
import com.incurrency.framework.BidAskEvent;
import com.incurrency.framework.BidAskListener;
import com.incurrency.framework.DateUtil;
import com.incurrency.framework.EnumOrderSide;
import com.incurrency.framework.MainAlgorithm;
import com.incurrency.framework.Parameters;
import com.incurrency.framework.Strategy;
import com.incurrency.framework.TradeEvent;
import com.incurrency.framework.TradeListener;
import com.incurrency.framework.TradingUtil;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author pankaj
 */
public class Pairs extends Strategy implements BidAskListener {

    private static final Logger logger = Logger.getLogger(Pairs.class.getName());
    ArrayList<PairDefinition> targetOrders = new ArrayList<>();
    ArrayList<Double> entryPrice = new ArrayList<>();
    private Date lastOrderDate;
    private double pairProfitTarget;
    private String expiry;
    private String path;
    private String pairsFileName;

    public Pairs(MainAlgorithm m, String parameterFile, ArrayList<String> accounts) {
        super(m, "pair", "FUT", parameterFile, accounts);
        loadParameters("pair", parameterFile);
        for (BeanSymbol s : Parameters.symbol) {
            getPosition().put(s.getSerialno() - 1, 0);
        }
        TradingUtil.writeToFile(getStrategy() + ".csv", "Bought Symbol, Sold Symbol,Target Spread, Spread Available,Trade Type");

        String[] tempStrategyArray = parameterFile.split("\\.")[0].split("-");
        for (BeanConnection c : Parameters.connection) {
            c.getWrapper().addBidAskListener(this);
            c.initializeConnection(tempStrategyArray[tempStrategyArray.length - 1]);
        }
        if (Subscribe.tes != null) {
            Subscribe.tes.addBidAskListener(this);
        }
        Timer TradeReader = new Timer("Timer: " + getStrategy() + " CloseProcessing");
        TradeReader.schedule(readTrades, getStartDate(), 10 * 60 * 1000);
        //TradeReader.schedule(readTrades, new Date(),10*60*1000);
    }
    TimerTask readTrades = new TimerTask() {
        @Override
        public void run() {
            try {
                System.out.println("Reading Trades");
                logger.log(Level.INFO, "Print Orders and Trades Called in {0}", getStrategy());
                readTrades(pairsFileName);
            } catch (Exception ex) {
                logger.log(Level.SEVERE, null, ex);
            }
        }
    };

    private void readTrades(String filename) {
        logger.log(Level.INFO, "Reading Trades");
        File dir = new File(path);
        File inputFile = new File(dir, filename);
        if (inputFile.exists() && !inputFile.isDirectory()) {
            try {
                List<String> initialLoad = Files.readAllLines(Paths.get(inputFile.getCanonicalPath()), StandardCharsets.UTF_8);
                int size = Math.min(initialLoad.size(), 4);
                for (PairDefinition p : targetOrders) {
                    p.active = false;
                }
                for (int i = 0; i < size; i++) {
                    if (i > 0) {
                        boolean updated = false;
                        String[] tempLine = initialLoad.get(i).split(",");

                        for (PairDefinition p : targetOrders) {
                            if (p.buySymbol.equals(tempLine[2]) && p.shortSymbol.equals(tempLine[0])) {
                                p.entryPrice = tempLine[11];
                                p.timeStamp = tempLine[8];
                                p.active = true;
                                updated = true;
                            }
                        }
                        if (!updated) {
                            PairDefinition tempPair = new PairDefinition(tempLine[2], tempLine[0], tempLine[8], tempLine[11], expiry);
                            targetOrders.add(tempPair);
                        }
                    }
                }
                logger.log(Level.INFO, "Pairs read: {0}, Size of Pairs being monitored: {1}", new Object[]{size, targetOrders.size()});
            } catch (IOException ex) {
                logger.log(Level.SEVERE, null, ex);
            }
        }

    }

    private void loadParameters(String strategy, String parameterFile) {
        Properties p = new Properties(System.getProperties());
        FileInputStream propFile;
        try {
            propFile = new FileInputStream(parameterFile);
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
        String lastOrderDateStr = currDateStr + " " + System.getProperty("LastOrderTime");
        lastOrderDate = DateUtil.parseDate("yyyyMMdd HH:mm:ss", lastOrderDateStr);
        if (lastOrderDate.compareTo(getStartDate()) < 0 && new Date().compareTo(lastOrderDate) > 0) {
            lastOrderDate = DateUtil.addDays(lastOrderDate, 1); //system date is > start date time. Therefore we have not crossed the 12:00 am barrier
        }
        expiry = System.getProperty("Expiry");
        pairProfitTarget = Double.parseDouble(System.getProperty("PairProfitTarget"));
        path=System.getProperty("Path");
        pairsFileName=System.getProperty("PairsFileName");
        String concatAccountNames = "";
        for (String account : getAccounts()) {
            concatAccountNames = ":" + account;
        }
        logger.log(Level.INFO, "-----{0} Parameters----Accounts used {1} ----- Parameter File {2}", new Object[]{strategy.toUpperCase(), concatAccountNames, parameterFile});
        logger.log(Level.INFO, "Last Order Time: {0}", lastOrderDate);
        logger.log(Level.INFO, "futures expiry being traded: {0}", expiry);
        logger.log(Level.INFO, "Pair Profit Target: {0}", pairProfitTarget);
        logger.log(Level.INFO, "File Path: {0}", path);
        logger.log(Level.INFO, "Pairs File: {0}", pairsFileName);   
    }

    @Override
    public void bidaskChanged(BidAskEvent event) {

        //buy logic. There is no short logic
        try {
            int id = event.getSymbolID();
            ArrayList<PairDefinition> inScope = new ArrayList<>();
            if (getStrategySymbols().contains(id)) {
                for (PairDefinition p : targetOrders) {
                    if (id == p.buyid || id == p.shortid) {
                        inScope.add(p);
                    }
                }
            }

            for (PairDefinition p : inScope) {
                int buySize = Parameters.symbol.get(p.buyid).getMinsize() * this.getNumberOfContracts();
                int shortSize = Parameters.symbol.get(p.shortid).getMinsize() * this.getNumberOfContracts();
                double level=0;
                if (Parameters.symbol.get(p.buyid).getAskPrice()>0 && Parameters.symbol.get(p.shortid).getBidPrice()>0){
                level = -Parameters.symbol.get(p.buyid).getAskPrice() * buySize + Parameters.symbol.get(p.shortid).getBidPrice() * shortSize;
                }
TradingUtil.writeToFile(getStrategy() + ".csv",Parameters.symbol.get(p.buyid).getSymbol()+","+Parameters.symbol.get(p.shortid).getSymbol()+","+p.entryPrice+","+level+","+"SCAN");
                if (p.position == 0 && DateUtil.addSeconds(DateUtil.parseDate("yyyyMMddHHmmss", p.timeStamp), 10 * 60).after(new Date()) && lastOrderDate.after(new Date())) {
//                    if (level < Double.parseDouble(p.entryPrice) && Parameters.symbol.get(p.buyid).getAskPrice()>0 && Parameters.symbol.get(p.shortid).getBidPrice()>0) {
                    if (Parameters.symbol.get(p.buyid).getAskPrice()>0 && Parameters.symbol.get(p.shortid).getBidPrice()>0) {
                        this.entry(p.buyid, EnumOrderSide.BUY, 0, 0,false);
                        this.entry(p.shortid, EnumOrderSide.SHORT, 0, 0,false);
                        p.position = 1;
                        p.positionPrice = level;
                        TradingUtil.writeToFile(getStrategy() + ".csv",Parameters.symbol.get(p.buyid).getSymbol()+","+Parameters.symbol.get(p.shortid).getSymbol()+","+p.entryPrice+","+level+","+"ENTRY");
                    }
                } else if (p.position > 0) {
                    if (level!=0 && level < p.positionPrice - 5000) { //profit by a threshold
                        this.exit(p.buyid, EnumOrderSide.SELL, 0, 0, "", true, "",false);
                        this.exit(p.shortid, EnumOrderSide.COVER, 0, 0, "", true, "",false);
                        TradingUtil.writeToFile(getStrategy() + ".csv",Parameters.symbol.get(p.buyid).getSymbol()+","+Parameters.symbol.get(p.shortid).getSymbol()+","+p.positionPrice+","+level+","+"PROFIT");
                        p.position = 0;
                        p.positionPrice = 0D;

                    }
                }
            }

            if (new Date().after(this.getEndDate())) {
                for (PairDefinition p : targetOrders) {
                    if (p.position != 0) {
                        int buySize = Parameters.symbol.get(p.buyid).getMinsize() * this.getNumberOfContracts();
                        int shortSize = Parameters.symbol.get(p.shortid).getMinsize() * this.getNumberOfContracts();
                        double level = Parameters.symbol.get(p.buyid).getAskPrice() * buySize - Parameters.symbol.get(p.shortid).getBidPrice() * shortSize;
                        this.exit(p.buyid, EnumOrderSide.SELL, 0, 0, "", true, "",true);
                        this.exit(p.shortid, EnumOrderSide.COVER, 0, 0, "", true, "",true);
                        p.position = 0;
                        if(level<p.positionPrice){
                        TradingUtil.writeToFile(getStrategy() + ".csv",Parameters.symbol.get(p.buyid).getSymbol()+","+Parameters.symbol.get(p.shortid).getSymbol()+","+p.positionPrice+","+level+","+"EOD Close Profit");
                        }else{
                        TradingUtil.writeToFile(getStrategy() + ".csv",Parameters.symbol.get(p.buyid).getSymbol()+","+Parameters.symbol.get(p.shortid).getSymbol()+","+p.positionPrice+","+level+","+"EOD Close Loss");                            
                        }
                    }
                }
            }

        } catch (Exception ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }
}
