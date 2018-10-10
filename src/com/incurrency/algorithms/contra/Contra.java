/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.algorithms.contra;

import com.incurrency.algorithms.manager.Manager;
import com.incurrency.framework.BeanPosition;
import com.incurrency.framework.DateUtil;
import com.incurrency.framework.EnumOrderReason;
import com.incurrency.framework.EnumOrderSide;
import static com.incurrency.framework.EnumOrderSide.BUY;
import static com.incurrency.framework.EnumOrderSide.SHORT;
import com.incurrency.framework.EnumOrderStage;
import com.incurrency.framework.MainAlgorithm;
import com.incurrency.framework.OrderBean;
import com.incurrency.framework.Parameters;
import com.incurrency.framework.Stop;
import com.incurrency.framework.Trade;
import com.incurrency.framework.Utilities;
import java.util.ArrayList;
import java.util.Map;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.rosuda.REngine.REXP;
import org.rosuda.REngine.Rserve.RConnection;

/**
 *
 * @author psharma
 */
public class Contra extends Manager {

    private static final Logger logger = Logger.getLogger(Contra.class.getName());
    private final Object lockScan = new Object();

    public Contra(MainAlgorithm m, Properties p, String parameterFile, ArrayList<String> accounts, Integer stratCount) {
        super(m, p, parameterFile, accounts, "contra");
        Timer trigger = new Timer("Timer: " + this.getStrategy() + " RScriptProcessor");
        trigger.schedule(RScriptRunTask, RScriptRunTime);
        boolean contractRollover = Utilities.rolloverDay(Math.max(1, rolloverDays - 2), this.getStartDate(), this.expiryNearMonth);

        if (contractRollover) {
            Timer rollProcessing = new Timer("Timer: " + this.getStrategy() + " RollProcessing");
            rollProcessing.schedule(rollProcessingTask, DateUtil.addSeconds(RScriptRunTime, 60));
        }
    }

    private TimerTask RScriptRunTask = new TimerTask() {
        @Override
        public void run() {
            if (!RStrategyFile.equals("")) {
                synchronized (lockScan) {
                    logger.log(Level.INFO, "501,Scan,{0}", new Object[]{getStrategy()});
                    RConnection c = null;
                    try {
                        c = new RConnection(rServerIP);
                        c.eval("setwd(\"" + wd + "\")");
                        REXP wd = c.eval("getwd()");
                        System.out.println(wd.asString());
                        c.eval("options(encoding = \"UTF-8\")");
                        String[] args = new String[1];
                        args = new String[]{"1", getStrategy(), String.valueOf(redisdborder)};
                        c.assign("args", args);
                        c.eval("source(\"" + RStrategyFile + "\")");
                    } catch (Exception e) {
                        logger.log(Level.SEVERE, null, e);
                    }
                }
            }
        }
    };

    TimerTask rollProcessingTask = new TimerTask() {
        @Override
        public void run() {
            try {
                for (Map.Entry<Integer, BeanPosition> entry : getPosition().entrySet()) {
                    if (entry.getValue().getPosition() != 0) {
                        String expiry = Parameters.symbol.get(entry.getKey()).getExpiry();
                        if (expiry.equals(expiryNearMonth)) {
                            ArrayList<Integer> entryorderidlist = new ArrayList<>();
                            int initID = entry.getKey();
                            EnumOrderSide side = EnumOrderSide.UNDEFINED;

                            if (Parameters.symbol.get(initID).getType().equals("OPT")) {//Calculate Side for option
                                if (optionSystem.equals("RECEIVE")) {
                                    if (entry.getValue().getPosition() < 0) {
                                        side = Parameters.symbol.get(initID).getRight().equals("CALL") ? EnumOrderSide.SHORT : EnumOrderSide.BUY;
                                    }
                                } else if (optionSystem.equals("PAY")) {
                                    if (entry.getValue().getPosition() > 0) {
                                        side = Parameters.symbol.get(initID).getRight().equals("CALL") ? EnumOrderSide.BUY : EnumOrderSide.SHORT;
                                    }
                                }
                            } else { //calculate side for future
                                side = Parameters.symbol.get(initID).getRight().equals("CALL") ? EnumOrderSide.BUY : EnumOrderSide.SHORT;
                            }

                            if (Parameters.symbol.get(initID).getType().equals("OPT") && optionPricingUsingFutures) { //calculate targetID for option
                                int futureid = Utilities.getFutureIDFromBrokerSymbol(Parameters.symbol, initID, expiryFarMonth);
                                if (optionSystem.equals("PAY")) {
                                    entryorderidlist = Utilities.getOrInsertOptionIDForPaySystem(Parameters.symbol, getPosition(), futureid, side, expiryFarMonth);
                                } else {
                                    entryorderidlist = Utilities.getOrInsertOptionIDForReceiveSystem(Parameters.symbol, getPosition(), futureid, side, expiryFarMonth);
                                }
                            } else if (Parameters.symbol.get(initID).getType().equals("OPT") && !optionPricingUsingFutures) {
                                int symbolid = Utilities.getCashReferenceID(Parameters.symbol, initID);
                                int referenceid = Utilities.getCashReferenceID(Parameters.symbol, symbolid);
                                if (optionSystem.equals("PAY")) {
                                    entryorderidlist = Utilities.getOrInsertOptionIDForPaySystem(Parameters.symbol, getPosition(), referenceid, side, expiryFarMonth);
                                } else {
                                    entryorderidlist = Utilities.getOrInsertOptionIDForReceiveSystem(Parameters.symbol, getPosition(), referenceid, side, expiryFarMonth);
                                }
                            } else if (Parameters.symbol.get(initID).getType().equals("FUT")) { //calculate targetID for futures
                                entryorderidlist.add(Utilities.getFutureIDFromBrokerSymbol(Parameters.symbol, initID, expiryFarMonth));
                            }

                            if (!entryorderidlist.isEmpty() && entryorderidlist.get(0) != -1) {
                                initSymbol(entryorderidlist.get(0), optionPricingUsingFutures);
                                positionRollover(initID, entryorderidlist.get(0));
                            } else {
                                logger.log(Level.INFO, "100,Rollover not completed as invalid Target symbol,{0}:{1}:{2}:{3}:{4}", new Object[]{getStrategy(), "Order", Parameters.symbol.get(initID).getDisplayname(), -1, -1});
                            }
                        }
                    }
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, null, e);
            }
        }
    };

    public void positionRollover(int initID, int targetID) {
        if (optionSystem.equals("PAY")) {
            //get side, size of position
            EnumOrderSide origSide = this.getPosition().get(initID).getPosition() > 0 ? EnumOrderSide.BUY : this.getPosition().get(initID).getPosition() < 0 ? EnumOrderSide.SHORT : EnumOrderSide.UNDEFINED;
            int size = Math.abs(this.getPosition().get(initID).getPosition());
            ArrayList<Stop> stops = null;
            //square off position        

            switch (origSide) {
                case BUY:
                    logger.log(Level.INFO, "101,Rollover SELL,{0}:{1}:{2}:{3}:{4}",
                            new Object[]{getStrategy(), "Order", Parameters.symbol.get(initID).getDisplayname(), -1, -1});
                    //stops = Trade.getStop(this.getDb(), this.getStrategy() + ":" + this.ParentInternalOrderIDForSquareOff("Order", ob) this.getFirstInternalOpenOrder(initID, EnumOrderSide.SELL, "Order").iterator().next() + ":Order");
                    OrderBean order = new OrderBean();
                    int referenceid = Utilities.getCashReferenceID(Parameters.symbol, targetID);
                    double limitprice = Utilities.getLimitPriceForOrder(Parameters.symbol, initID, referenceid, EnumOrderSide.SELL, getTickSize(), this.getOrdType(),0);
                    order.setParentDisplayName(Parameters.symbol.get(initID).getDisplayname());
                    order.setChildDisplayName(Parameters.symbol.get(initID).getDisplayname());
                    order.setOrderType(this.getOrdType());
                    order.setOrderSide(EnumOrderSide.SELL);
                    order.setOriginalOrderSize(size);
                    order.setLimitPrice(limitprice);
                    order.setOrderReason(EnumOrderReason.REGULAREXIT);
                    order.setOrderStage(EnumOrderStage.INIT);
                    order.setOrderAttributes(this.getOrderAttributes());
                    order.setScale(getScaleExit());
                    order.setOrderLog("ROLLOVERSQUAREOFF");
                    this.exit(order);
                    break;
                case SHORT:
                    logger.log(Level.INFO, "101,Rollover COVER,{0}:{1}:{2}:{3}:{4}",
                            new Object[]{getStrategy(), "Order", Parameters.symbol.get(initID).getDisplayname(), -1, -1});
                    // stops = Trade.getStop(this.getDb(), this.getStrategy() + ":" + this.getFirstInternalOpenOrder(initID, EnumOrderSide.COVER, "Order").iterator().next() + ":Order");
                    order = new OrderBean();
                    referenceid = Utilities.getCashReferenceID(Parameters.symbol, targetID);
                    limitprice = Utilities.getLimitPriceForOrder(Parameters.symbol, initID, referenceid, EnumOrderSide.COVER, getTickSize(), this.getOrdType(),0);
                    order.setParentDisplayName(Parameters.symbol.get(initID).getDisplayname());
                    order.setChildDisplayName(Parameters.symbol.get(initID).getDisplayname());
                    order.setOrderType(this.getOrdType());
                    order.setOrderSide(EnumOrderSide.COVER);
                    order.setOriginalOrderSize(size);
                    order.setLimitPrice(limitprice);
                    order.setOrderReason(EnumOrderReason.REGULAREXIT);
                    order.setOrderStage(EnumOrderStage.INIT);
                    order.setOrderAttributes(this.getOrderAttributes());
                    order.setScale(getScaleExit());
                    order.setOrderLog("ROLLOVERSQUAREOFF");
                    this.exit(order);
                    break;
                default:
                    break;
            }

            //enter new position
            int orderid = -1;
            double targetContracts = size / Parameters.symbol.get(targetID).getMinsize();
            int newSize = Math.max((int) Math.round(targetContracts), 1);//size/Parameters.symbol.get(targetID).getMinsize() + ((size % Parameters.symbol.get(targetID).getMinsize() == 0) ? 0 : 1); 
            newSize = newSize * Parameters.symbol.get(targetID).getMinsize();
            switch (origSide) {
                case BUY:
                    if (this.getLongOnly()) {
                        logger.log(Level.INFO, "101,Rollover BUY,{0}:{1}:{2}:{3}:{4},NewPositionSize={5}",
                                new Object[]{getStrategy(), "Order", Parameters.symbol.get(targetID).getDisplayname(), -1, -1, newSize});
                        int referenceid = Utilities.getCashReferenceID(Parameters.symbol, targetID);
                        double limitprice = Utilities.getLimitPriceForOrder(Parameters.symbol, targetID, referenceid, EnumOrderSide.BUY, getTickSize(), this.getOrdType(),0);
                        OrderBean order = new OrderBean();
                        order.setParentDisplayName(Parameters.symbol.get(targetID).getDisplayname());
                        order.setChildDisplayName(Parameters.symbol.get(initID).getDisplayname());
                        order.setOrderType(this.getOrdType());
                        order.setOrderSide(EnumOrderSide.BUY);
                        order.setOriginalOrderSize(newSize);
                        order.setLimitPrice(limitprice);
                        order.setOrderReason(EnumOrderReason.REGULARENTRY);
                        order.setOrderStage(EnumOrderStage.INIT);
                        order.setOrderAttributes(this.getOrderAttributes());
                        order.setScale(getScaleEntry());
                        order.setOrderLog("ROLLOVERENTRY");
                        orderid = this.entry(order);
                    }
                    break;
                case SHORT:
                    if (this.getShortOnly()) {
                        logger.log(Level.INFO, "101,Rollover SHORT,{0}:{1}:{2}:{3}:{4},NewPositionSize={5}",
                                new Object[]{getStrategy(), "Order", Parameters.symbol.get(targetID).getDisplayname(), -1, -1, newSize});
                        OrderBean order = new OrderBean();
                        int referenceid = Utilities.getCashReferenceID(Parameters.symbol, targetID);
                        double limitprice = Utilities.getLimitPriceForOrder(Parameters.symbol, targetID, referenceid, EnumOrderSide.SHORT, getTickSize(), this.getOrdType(),0);
                        order.setParentDisplayName(Parameters.symbol.get(targetID).getDisplayname());
                        order.setChildDisplayName(Parameters.symbol.get(initID).getDisplayname());
                        order.setOrderType(this.getOrdType());
                        order.setOrderSide(EnumOrderSide.SHORT);
                        order.setOriginalOrderSize(newSize);
                        order.setLimitPrice(limitprice);
                        order.setOrderReason(EnumOrderReason.REGULARENTRY);
                        order.setOrderStage(EnumOrderStage.INIT);
                        order.setOrderAttributes(this.getOrderAttributes());
                        order.setScale(getScaleEntry());
                        order.setOrderLog("ROLLOVERENTRY");
                        orderid = this.entry(order);
                    }
                    break;
                default:
                    break;
            }
            //update stop information
            logger.log(Level.INFO, "102,Rollover StopValue Update,{0}:{1}:{2}:{3}:{4},NewPositionSize={5},StopArray={6}",
                    new Object[]{getStrategy(), "Order", Parameters.symbol.get(targetID).getDisplayname(), orderid, -1, newSize, Utilities.listToString(stops)});
            if (orderid >= 0) {
                Trade.setStop(this.getDb(), this.getStrategy() + ":" + orderid + ":" + "Order", "opentrades", stops);
            }
        }
    }
}
