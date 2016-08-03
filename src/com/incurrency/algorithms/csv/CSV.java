/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.algorithms.csv;

import com.incurrency.framework.BeanConnection;
import com.incurrency.framework.EnumOrderReason;
import com.incurrency.framework.EnumOrderSide;
import com.incurrency.framework.MainAlgorithm;
import com.incurrency.framework.Parameters;
import com.incurrency.framework.Strategy;
import com.incurrency.framework.Trade;
import com.incurrency.framework.Utilities;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author pankaj
 */
public class CSV extends Strategy {

    private static final Logger logger = Logger.getLogger(CSV.class.getName());
    String directory;
    String orderFile;
    private ArrayList<CSVOrder> oldOrderList = new ArrayList<>();
    private ArrayList<CSVOrder> newOrderList = new ArrayList<>();
    private ArrayList<CSVOrder> ocoOrderList = new ArrayList<>();
    OrderReader orderReader;
    public int test;
    HashMap<String, OrderMap> rowOrderMap = new HashMap<>();
    private final String delimiter = "_";

    public CSV(MainAlgorithm m, Properties prop,String parameterFile, ArrayList<String> accounts,Integer stratCount) {
        super(m, "CSV", "FUT", prop,parameterFile, accounts,stratCount);
        try {
            loadParameters("CSV", parameterFile);
            String[] tempStrategyArray = parameterFile.split("\\.")[0].split("-");
            for (BeanConnection c : Parameters.connection) {
                c.initializeConnection(tempStrategyArray[tempStrategyArray.length - 1],-1);
            }
            Path dir = Paths.get(directory.trim());
            File folder = new File(dir.toString());
            File f = new File(folder, orderFile);
            if (f.exists() && !f.isDirectory()) {
                new CSVOrder().reader(f.getCanonicalPath(), oldOrderList);
            }
            Thread t = new Thread(orderReader = new OrderReader(this, dir, false));
            t.start();
        } catch (Exception ex) {
            logger.log(Level.INFO, "101", ex);
        }
    }

    private class OrderMap {

        int entryid;
        int exitid;

        public OrderMap(int entryid, int exitid) {
            this.entryid = entryid;
            this.exitid = exitid;
        }
    }

    private void loadParameters(String strategy, String parameterFile) {
        Properties p=Utilities.loadParameters(parameterFile);
        orderFile = p.getProperty("OrderFileName") == null ? "orderfile.csv" : p.getProperty("OrderFileName");
        directory = p.getProperty("Directory") == null ? "orders" : p.getProperty("Directory");
        directory = directory.replace("\\", "/");
        directory = directory.replace("\"", "");
    }

    public void processOrders(Path dir) {
        File folder = new File(dir.toString());
        File f = new File(folder, orderFile);
        if (f.exists() && !f.isDirectory()) {
            try {
                newOrderList.clear();
                new CSVOrder().reader(f.getCanonicalPath(), newOrderList);
                //read file
                if(oldOrderList.size()!=newOrderList.size()){
                logger.log(Level.INFO, "310,ReadCSV,{0}", new Object[]{orderFile + delimiter + oldOrderList.size() + delimiter + newOrderList.size()});
                }
                if (newOrderList.size() > oldOrderList.size()) {//send addition to OMS
                    for (int i = oldOrderList.size(); i < newOrderList.size(); i++) {
                        placeOrder(newOrderList.get(i));
                        oldOrderList = (ArrayList<CSVOrder>) newOrderList.clone();

                    }
                }
                //place any OCO orders
                while (ocoOrderList.size() > 0) {
                    int id = Integer.valueOf(ocoOrderList.get(0).getOrder().get("id").toString());
                    ArrayList<CSVOrder> activeOCOOrderList = new ArrayList<>();
                    Iterator<CSVOrder> it = ocoOrderList.iterator();
                    while (it.hasNext()) {
                        CSVOrder ord = it.next();
                        int sid = Integer.valueOf(ord.getOrder().get("id").toString());
                        
                        if (sid == id) {
                            activeOCOOrderList.add(ord);
                        }
                        it.remove();
                    }
                    //place OCO orders
                    String link ;
                    if (activeOCOOrderList.size() > 0) {
                        //link = activeOCOOrderList.get(0).getSymbol() + DateUtil.getFormatedDate("yyyyMMdd", TradingUtil.getAlgoDate().getTime(), TimeZone.getTimeZone(getTimeZone()));
                    }
                    for (int i = 0; i < activeOCOOrderList.size() - 1; i++) {//loop through the first n-1 orders
                        //update internal orders
                        int entryID = 0;
                        int exitID = 0;
                        CSVOrder ord = activeOCOOrderList.get(i);
                        int sid = Integer.valueOf(ord.getOrder().get("id").toString());
                        EnumOrderSide sside=EnumOrderSide.valueOf(ord.getOrder().get("side").toString());
                        EnumOrderReason sreason=EnumOrderReason.valueOf(ord.getOrder().get("reason").toString());
                        EnumOrderReason sstage=EnumOrderReason.valueOf(ord.getOrder().get("stage").toString());
                        int ssize=Integer.valueOf(ord.getOrder().get("size").toString());
                        switch (sside) {
                            case BUY:
                            case SHORT:
                                int internalorderid = getInternalOrderID();
                                entryID = internalorderid;
                                exitID = entryID;
                                this.internalOpenOrders.put(id, internalorderid);
//                                int parentorderid=Trade.getParentExitOrderIDInternal(getTrades(), internalorderid);
 //                               new Trade(getTrades(),id, id, sreason, sside, Parameters.symbol.get(id).getLastPrice(), ssize, internalorderid, 0, parentorderid,getTimeZone(), "Order");
                                //this.entry(ord.getOrder());
                                break;
                            case SELL:
                            case COVER:
                                internalorderid = getInternalOrderID();
                                int tempinternalOrderID=0;
                                //  int tempinternalOrderID = this.getFirstInternalOpenOrder(id, sside, "Order");
                                entryID = tempinternalOrderID;
                                exitID = internalorderid;
//                                parentorderid=Trade.getParentExitOrderIDInternal(getTrades(), tempinternalOrderID);
//                                Trade.updateExit(getTrades(), id, EnumOrderReason.SL, EnumOrderSide.BUY, i, i, internalorderid, id, parentorderid, entryID, timeZone, allAccounts);
                                //this.exit(ord.getOrder());
                                break;
                            default:
                                break;
                        }
                        logger.log(Level.INFO, "Strategy,{0}, {1},OCO Order, New Position: {2}, Position Price:{3}, OrderSide: {4}, Order Stage: {5}, Order Reason:{6},", new Object[]{allAccounts, getStrategy(), getPosition().get(id).getPosition(), getPosition().get(id).getPrice(), sside, sstage, sreason});
                        getOms().tes.fireOrderEvent(ord.getOrder());

                    }
                    //place the last leg of the OCO and set transmit = true;
                    //update internal orders
                    int entryID = 0;
                    int exitID = 0;
                    CSVOrder ord = activeOCOOrderList.get(activeOCOOrderList.size() - 1); //get last element
                    int sid = Integer.valueOf(ord.getOrder().get("id").toString());
                        EnumOrderSide sside=EnumOrderSide.valueOf(ord.getOrder().get("side").toString());
                        EnumOrderReason sreason=EnumOrderReason.valueOf(ord.getOrder().get("reason").toString());
                        EnumOrderReason sstage=EnumOrderReason.valueOf(ord.getOrder().get("stage").toString());
                        int ssize=Integer.valueOf(ord.getOrder().get("size").toString());
                        switch (sside) {
                        case BUY:
                        case SHORT:
                            int internalorderid = getInternalOrderID();
                            entryID = internalorderid;
                            exitID = entryID;
//                            int parentorderid=Trade.getParentExitOrderIDInternal(getTrades(), internalorderid);
                            this.internalOpenOrders.put(id, internalorderid);
                            //new Trade(getTrades(),id, id, sreason, sside, Parameters.symbol.get(id).getLastPrice(), ssize, internalorderid, 0, parentorderid,getTimeZone(), "Order");
                            //this.entry(ord.getOrder());
                            break;
                        case SELL:
                        case COVER:
                            internalorderid = getInternalOrderID();
                            int tempinternalOrderID=0;
                            //int tempinternalOrderID = this.getFirstInternalOpenOrder(id, sside, "Order");
                            entryID = tempinternalOrderID;
                            exitID = internalorderid;
//                            parentorderid=Trade.getParentExitOrderIDInternal(getTrades(), tempinternalOrderID);
                            //Trade.updateExit(getTrades(),id, sreason, sside,Parameters.symbol.get(id).getLastPrice(), ssize, internalorderid, 0, parentorderid,entryID,getTimeZone(), "Order");
                            //this.exit(ord.getOrder());
                            break;
                        default:
                            break;
                    }
                    logger.log(Level.INFO, "Strategy,{0}, {1},OCO Order, New Position: {2}, Position Price:{3}, OrderSide: {4}, Order Stage: {5}, Order Reason:{6},", new Object[]{allAccounts, getStrategy(), getPosition().get(id).getPosition(), getPosition().get(id).getPrice(), sside, sstage, sreason});

                    getOms().tes.fireOrderEvent(ord.getOrder());
                }
            } catch (IOException ex) {
                logger.log(Level.INFO, "101", ex + "," + getStrategy());
            }

        }
    }

    private void placeOrder(CSVOrder orderItem) {
        EnumOrderSide side=EnumOrderSide.valueOf(orderItem.getOrder().get("side").toString());
        if(side.equals(EnumOrderSide.BUY)||side.equals(EnumOrderSide.SHORT)){
            this.entry(orderItem.getOrder());
        }else{
            this.exit(orderItem.getOrder());
        }
        /*
        if (TradingUtil.getAlgoDate().after(getStartDate()) && TradingUtil.getAlgoDate().before(getEndDate())) {
            int id = Utilities.getIDFromDisplayName(Parameters.symbol,orderItem.getHappyName());
            if (orderItem.getType().equals("COMBO")) {
                if (id == -1 && !Strategy.getCombosAdded().containsKey(orderItem.getHappyName())) {
                    Parameters.symbol.add(new BeanSymbol(orderItem.getSymbol(), orderItem.getHappyName(), getStrategy()));
                    Strategy.getCombosAdded().put(orderItem.getHappyName(), orderItem.getSymbol());
                    id = Utilities.getIDFromDisplayName(Parameters.symbol,orderItem.getHappyName());
                } else {
                    id = Utilities.getIDFromBrokerSymbol(Parameters.symbol,orderItem.getSymbol(), orderItem.getType(), "", "", "");
                }
                if (!getStrategySymbols().contains(id)) {
                    getStrategySymbols().add(id);
                    getPosition().put(id, new BeanPosition(id, getStrategy()));
                    Index ind = new Index(getStrategy(), id);
                    for (BeanConnection c : Parameters.connection) {
                        c.getOrdersSymbols().put(ind, new ArrayList<SymbolOrderMap>());
                        c.getPnlBySymbol().put(ind, 0D);
                    }
                }


            } else {
                id = Utilities.getIDFromDisplayName(Parameters.symbol,orderItem.getHappyName());
            }


            //generate next internal order id
            if (id > -1 && orderItem.getStage().equals(EnumOrderStage.INIT) && orderItem.getOrderType() != EnumOrderType.UNDEFINED && orderItem.getSide() != EnumOrderSide.UNDEFINED && orderItem.getReason() != EnumOrderReason.UNDEFINED) {
                BeanPosition pd = getPosition().get(id);
                double expectedFillPrice = orderItem.getLimitPrice() != 0 ? orderItem.getLimitPrice() : Parameters.symbol.get(id).getLastPrice();
                int symbolPosition = 0;
                double positionPrice = 0;
                if (orderItem.getSide() == EnumOrderSide.BUY || orderItem.getSide() == EnumOrderSide.COVER) {
                    symbolPosition = pd.getPosition() + orderItem.getSize();
                    positionPrice = symbolPosition == 0 ? 0D : Math.abs((expectedFillPrice * orderItem.getSize() + pd.getPrice() * pd.getPosition()) / (symbolPosition));
                } else {
                    symbolPosition = pd.getPosition() - orderItem.getSize();
                    positionPrice = symbolPosition == 0 ? 0D : Math.abs((-expectedFillPrice * orderItem.getSize() + pd.getPrice() * pd.getPosition()) / (symbolPosition));
                }
                pd.setPosition(symbolPosition);
                pd.setPositionInitDate(TradingUtil.getAlgoDate());
                pd.setPrice(positionPrice);
                getPosition().put(id, pd);

                //update internal orders
                int entryID = 0;
                int exitID = 0;
                switch (orderItem.getSide()) {
                    case BUY:
                    case SHORT:
                        int internalorderid = getInternalOrderID();
                        entryID = internalorderid;
                        exitID = entryID;
                        this.internalOpenOrders.put(id, internalorderid);
                        int parentorderid=Trade.getParentEntryOrderIDInternal(getTrades(), internalorderid);
                        new Trade(getTrades(),id, id, orderItem.getReason(), orderItem.getSide(), Parameters.symbol.get(id).getLastPrice(), orderItem.getSize(), internalorderid, 0, parentorderid,getTimeZone(), "Order");
                        break;
                    case SELL:
                    case COVER:
                        internalorderid = getInternalOrderID();
                        int tempinternalOrderID = getFirstInternalOpenOrder(id, orderItem.getSide(), "Order");
                        exitID = tempinternalOrderID;
                        entryID = internalorderid;
                        parentorderid=Trade.getParentExitOrderIDInternal(getTrades(), tempinternalOrderID);
                        Trade.updateExit(getTrades(),id, orderItem.getReason(), orderItem.getSide(), Parameters.symbol.get(id).getLastPrice(), orderItem.getSize(), internalorderid, 0,parentorderid, tempinternalOrderID,getTimeZone(), "Order");
                        break;
                    default:
                        break;
                }
                if (orderItem.getReason() == EnumOrderReason.OCOSL || orderItem.getReason() == EnumOrderReason.OCOTP) {
                    //keep aside for OCO processing
                    ocoOrderList.add(orderItem);
                } else {
                    int expireTime = 0;
                    int duration = 0;
                    double limitPrice = 0;
                    double triggerPrice = 0;
                    if (!orderItem.getOrderType().equals(EnumOrderType.MKT)) {
                        expireTime = orderItem.getEffectiveDuration();
                        duration = orderItem.getDynamicDuration();
                        limitPrice = orderItem.getLimitPrice();
                        if (!orderItem.getOrderType().equals("COMBO")) {
                            triggerPrice = orderItem.getTriggerPrice();
                        }
                    }
                    if (orderItem.getReason().equals(EnumOrderReason.REGULARENTRY)) {
                        logger.log(Level.INFO, "310,EntryOrder,{0},", new Object[]{getStrategy() + delimiter + entryID + delimiter + pd.getPosition() + delimiter + pd.getPrice()});
                    } else if (orderItem.getReason().equals(EnumOrderReason.REGULAREXIT)) {
                        logger.log(Level.INFO, "310,ExitOrder,{0},", new Object[]{getStrategy() + delimiter + entryID + delimiter + pd.getPosition() + delimiter + pd.getPrice()});

                    }

                    rowOrderMap.put(orderItem.getRowreference(), new OrderMap(entryID, exitID));
                    getOms().tes.fireOrderEvent(entryID, exitID, Parameters.symbol.get(id), orderItem.getSide(), orderItem.getReason(), orderItem.getOrderType(), orderItem.getSize(), limitPrice, triggerPrice, getStrategy(), expireTime, orderItem.getStage(), duration, orderItem.getSlippage(), true, orderItem.getTif(), orderItem.isScaleIn(), "", orderItem.getEffectiveFrom(), null);
                    //String link,boolean transmit                    
                }
            } else if (id > -1 && (orderItem.getStage().equals(EnumOrderStage.AMEND) || orderItem.getStage().equals(EnumOrderStage.CANCEL)) && orderItem.getOrderType() != EnumOrderType.UNDEFINED && orderItem.getSide() != EnumOrderSide.UNDEFINED && orderItem.getReason() != EnumOrderReason.UNDEFINED) {
                int entryID = rowOrderMap.get(orderItem.getRowreference()).entryid;
                int exitID = rowOrderMap.get(orderItem.getRowreference()).exitid;
                int expireTime = 0;
                int duration = 0;
                double limitPrice = 0;
                double triggerPrice = 0;
                if (!orderItem.getOrderType().equals(EnumOrderType.MKT)) {
                    expireTime = orderItem.getEffectiveDuration();
                    duration = orderItem.getDynamicDuration();
                    limitPrice = orderItem.getLimitPrice();
                    if (!orderItem.getOrderType().equals("COMBO")) {
                        triggerPrice = orderItem.getTriggerPrice();
                    }
                }
                if (orderItem.getStage().equals(EnumOrderStage.AMEND)) {
                    logger.log(Level.INFO, "310,AmendOrder,{0},", new Object[]{getStrategy() + delimiter + entryID});
                } else if (orderItem.getStage().equals(EnumOrderStage.CANCEL)) {
                    logger.log(Level.INFO, "310,CancelOrder,{0},", new Object[]{getStrategy() + delimiter + entryID});

                }
                getOms().tes.fireOrderEvent(entryID, exitID, Parameters.symbol.get(id), orderItem.getSide(), orderItem.getReason(), orderItem.getOrderType(), orderItem.getSize(), limitPrice, triggerPrice, getStrategy(), expireTime, orderItem.getStage(), duration, orderItem.getSlippage(), true, orderItem.getTif(), orderItem.isScaleIn(), "", orderItem.getEffectiveFrom(), null);

            }
        }
        */
    }
}
