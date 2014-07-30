/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.algorithms.csv;

import com.incurrency.framework.BeanConnection;
import com.incurrency.framework.BeanPosition;
import com.incurrency.framework.BeanSymbol;
import com.incurrency.framework.DateUtil;
import com.incurrency.framework.EnumOrderReason;
import com.incurrency.framework.EnumOrderSide;
import com.incurrency.framework.EnumOrderStage;
import com.incurrency.framework.EnumOrderType;
import com.incurrency.framework.Index;
import com.incurrency.framework.Launch;
import com.incurrency.framework.MainAlgorithm;
import com.incurrency.framework.OrderLink;
import com.incurrency.framework.Parameters;
import com.incurrency.framework.Strategy;
import com.incurrency.framework.SymbolOrderMap;
import com.incurrency.framework.Trade;
import com.incurrency.framework.TradingUtil;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Properties;
import java.util.TimeZone;
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
    HashMap<String,OrderMap> rowOrderMap=new HashMap<>();

    public CSV(MainAlgorithm m, String parameterFile, ArrayList<String> accounts) {
        super(m, "CSV", "FUT", parameterFile, accounts);
        try {
            loadParameters("CSV", parameterFile);
            String[] tempStrategyArray = parameterFile.split("\\.")[0].split("-");
            for (BeanConnection c : Parameters.connection) {
                c.initializeConnection(tempStrategyArray[tempStrategyArray.length - 1]);
            }
            Path dir = Paths.get(directory.trim());
            Thread t = new Thread(orderReader = new OrderReader(this, dir, false));
            t.start();
        } catch (IOException ex) {
            Logger.getLogger(CSV.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

     private class OrderMap{
         int entryid;
         int exitid;
         
         public OrderMap(int entryid,int exitid){
             this.entryid=entryid;
             this.exitid=exitid;
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
        orderFile = System.getProperty("OrderFileName") == null ? "orderfile.csv" : System.getProperty("OrderFileName");
        directory = System.getProperty("Directory") == null ? "orders" : System.getProperty("Directory");
        directory = directory.replace("\\", "/");
    }

    public void processOrders(Path dir) {
        File folder = new File(dir.toString());
        File f = new File(folder, orderFile);
        if (f.exists() && !f.isDirectory()) {
            try {
                newOrderList.clear();
                new CSVOrder().reader(f.getCanonicalPath(), newOrderList);
                //read file
                if (newOrderList.size() > oldOrderList.size()) {//send addition to OMS
                    for (int i = oldOrderList.size(); i < newOrderList.size(); i++) {
                        placeOrder(newOrderList.get(i));
                        oldOrderList = (ArrayList<CSVOrder>) newOrderList.clone();

                    }
                }
                //place any OCO orders
                while (ocoOrderList.size() > 0) {
                    int id = ocoOrderList.get(0).getId();
                    ArrayList<CSVOrder> activeOCOOrderList = new ArrayList<>();
                    Iterator<CSVOrder> it = ocoOrderList.iterator();
                    while (it.hasNext()) {
                        CSVOrder ord = it.next();
                        if (ord.getId() == id) {
                            activeOCOOrderList.add(ord);
                        }
                        it.remove();
                    }
                    //place OCO orders
                    String link = "";
                    if (activeOCOOrderList.size() > 0) {
                        link = activeOCOOrderList.get(0).getSymbol() + DateUtil.getFormatedDate("yyyyMMdd", new Date().getTime(), TimeZone.getTimeZone(getTimeZone()));
                    }
                    for (int i = 0; i < activeOCOOrderList.size() - 1; i++) {//loop through the first n-1 orders
                        //update internal orders
                        int entryID = 0;
                        int exitID = 0;
                        CSVOrder ord = activeOCOOrderList.get(i);
                        switch (ord.getSide()) {
                            case BUY:
                            case SHORT:
                                int internalorderid=getInternalOrderID();
                                entryID = internalorderid;
                                exitID = entryID;
                                this.internalOpenOrders.put(id, internalorderid);
                                getTrades().put(new OrderLink(internalorderid,0, "Order"), new Trade(id, ord.getSide(), Parameters.symbol.get(id).getLastPrice(), ord.getSize(), internalorderid,0, getTimeZone(), "Order"));
                                break;
                            case SELL:
                            case COVER:
                                internalorderid=getInternalOrderID();
                                int tempinternalOrderID = this.getFirstInternalOpenOrder(id, ord.getSide(), "Order");
                                entryID = tempinternalOrderID;
                                exitID = internalorderid;
                                Trade tempTrade = getTrades().get(new OrderLink(tempinternalOrderID,0, "Order"));
                                tempTrade.updateExit(id, ord.getSide(), Parameters.symbol.get(id).getLastPrice(), ord.getSize(), internalorderid,0, getTimeZone(), "Order");
                                getTrades().put(new OrderLink(tempinternalOrderID,0, "Order"), tempTrade);
                                break;
                            default:
                                break;
                        }
                        logger.log(Level.INFO, "Strategy,{0}, {1},OCO Order, New Position: {2}, Position Price:{3}, OrderSide: {4}, Order Stage: {5}, Order Reason:{6},", new Object[]{allAccounts, getStrategy(), getPosition().get(id).getPosition(), getPosition().get(id).getPrice(), ord.getSide(), ord.getStage(), ord.getReason()});
                        getOms().tes.fireOrderEvent(entryID, exitID, Parameters.symbol.get(id), ord.getSide(), ord.getReason(), ord.getOrderType(), ord.getSize(), ord.getLimitPrice(), ord.getTriggerPrice(), getStrategy(), ord.getEffectiveDuration(), ord.getStage(), ord.getEffectiveDuration(), ord.getDynamicDuration(), ord.getSlippage(), link, false, ord.getTif(), true, "", ord.getEffectiveFrom(),null);

                    }
                    //place the last leg of the OCO and set transmit = true;
                    //update internal orders
                    int entryID = 0;
                    int exitID = 0;
                    CSVOrder ord = activeOCOOrderList.get(activeOCOOrderList.size() - 1); //get last element
                    switch (ord.getSide()) {
                        case BUY:
                        case SHORT:
                            int internalorderid=getInternalOrderID();
                            entryID = internalorderid;
                            exitID = entryID;
                            this.internalOpenOrders.put(id, internalorderid);
                            getTrades().put(new OrderLink(internalorderid,0, "Order"), new Trade(id, ord.getSide(), Parameters.symbol.get(id).getLastPrice(), ord.getSize(), internalorderid, 0,getTimeZone(), "Order"));
                            break;
                        case SELL:
                        case COVER:
                            internalorderid=getInternalOrderID();
                            int tempinternalOrderID = this.getFirstInternalOpenOrder(id, ord.getSide(), "Order");
                            entryID = tempinternalOrderID;
                            exitID = internalorderid;
                            Trade tempTrade = getTrades().get(new OrderLink(tempinternalOrderID,0, "Order"));
                            tempTrade.updateExit(id, ord.getSide(), Parameters.symbol.get(id).getLastPrice(), ord.getSize(), internalorderid,0, getTimeZone(), "Order");
                            getTrades().put(new OrderLink(tempinternalOrderID,0, "Order"), tempTrade);
                            break;
                        default:
                            break;
                    }
                    logger.log(Level.INFO, "Strategy,{0}, {1},OCO Order, New Position: {2}, Position Price:{3}, OrderSide: {4}, Order Stage: {5}, Order Reason:{6},", new Object[]{allAccounts, getStrategy(), getPosition().get(id).getPosition(), getPosition().get(id).getPrice(), ord.getSide(), ord.getStage(), ord.getReason()});
                    
                    getOms().tes.fireOrderEvent(entryID, exitID, Parameters.symbol.get(id), ord.getSide(), ord.getReason(), ord.getOrderType(), ord.getSize(), ord.getLimitPrice(), ord.getTriggerPrice(), getStrategy(), ord.getEffectiveDuration(), ord.getStage(), ord.getEffectiveDuration(), ord.getDynamicDuration(), ord.getSlippage(), link, true, ord.getTif(), true, "", ord.getEffectiveFrom(),null);
                }
            } catch (IOException ex) {
                Logger.getLogger(CSV.class.getName()).log(Level.SEVERE, null, ex);
            }

        }
    }

    private void placeOrder(CSVOrder orderItem) {
        int id = TradingUtil.getIDFromSymbol(orderItem.getHappyName(), orderItem.getType(), "", "", "");
        if (orderItem.getType().equals("COMBO")) {
            if (id==-1 && !Strategy.getCombosAdded().containsKey(orderItem.getHappyName())) {
                Parameters.symbol.add(new BeanSymbol(orderItem.getSymbol(), orderItem.getHappyName(),getStrategy()));
                Strategy.getCombosAdded().put(orderItem.getHappyName(), orderItem.getSymbol());
                id = TradingUtil.getIDFromSymbol(orderItem.getHappyName(), orderItem.getType(), "", "", "");
            }else{
                id = TradingUtil.getIDFromSymbol(orderItem.getHappyName(), orderItem.getType(), "", "", "");
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
            id = TradingUtil.getIDFromSymbol(orderItem.getSymbol(), orderItem.getType(), orderItem.getExpiry(), orderItem.getRight(), orderItem.getOptionStrike());
        }


        //generate next internal order id
        if (id > -1 && orderItem.getStage().equals(EnumOrderStage.INIT) && orderItem.getOrderType() != EnumOrderType.UNDEFINED && orderItem.getSide() != EnumOrderSide.UNDEFINED && orderItem.getReason()!=EnumOrderReason.UNDEFINED) {
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
            pd.setPositionInitDate(new Date());
            pd.setPrice(positionPrice);
            getPosition().put(id, pd);

            //update internal orders
            int entryID = 0;
            int exitID = 0;
            switch (orderItem.getSide()) {
                case BUY:
                case SHORT:
                    int internalorderid=getInternalOrderID();
                    entryID = internalorderid;
                    exitID = entryID;
                    this.internalOpenOrders.put(id, internalorderid);
                    getTrades().put(new OrderLink(internalorderid,0, "Order"), new Trade(id, orderItem.getSide(), Parameters.symbol.get(id).getLastPrice(), orderItem.getSize(), internalorderid,0, getTimeZone(), "Order"));
                    break;
                case SELL:
                case COVER:
                     internalorderid=getInternalOrderID();
                    int tempinternalOrderID = getFirstInternalOpenOrder(id, orderItem.getSide(), "Order");
                    exitID = tempinternalOrderID;
                    entryID = internalorderid;
                    Trade tempTrade = getTrades().get(new OrderLink(tempinternalOrderID,0, "Order"));
                    if(tempTrade!=null){
                    tempTrade.updateExit(id, orderItem.getSide(), Parameters.symbol.get(id).getLastPrice(), orderItem.getSize(), internalorderid,0, getTimeZone(), "Order");
                    getTrades().put(new OrderLink(tempinternalOrderID,0, "Order"), tempTrade);
                    }
                    break;
                default:
                    break;
            }
            if (orderItem.getReason() == EnumOrderReason.OCOSL || orderItem.getReason() == EnumOrderReason.OCOTP) {
                //keep aside for OCO processing
                ocoOrderList.add(orderItem);
            } else {
                logger.log(Level.INFO, "{0}, {1},Strategy,Order Placed with Execution Manager, Internal Order ID: {2},New Position: {3}, Position Price:{4}, OrderSide: {5}, Order Stage: {6}, Order Reason:{7},", new Object[]{allAccounts, getStrategy(),entryID, getPosition().get(id).getPosition(), getPosition().get(id).getPrice(), orderItem.getSide(), orderItem.getStage(), orderItem.getReason()});
                rowOrderMap.put(orderItem.getRowreference(), new OrderMap(entryID,exitID));
                getOms().tes.fireOrderEvent(entryID, exitID, Parameters.symbol.get(id), orderItem.getSide(), orderItem.getReason(), orderItem.getOrderType(), orderItem.getSize(), orderItem.getLimitPrice(), orderItem.getTriggerPrice(), getStrategy(), orderItem.getEffectiveDuration(), orderItem.getStage(), orderItem.getEffectiveDuration(), orderItem.getDynamicDuration(), orderItem.getSlippage(), "", true, orderItem.getTif(), orderItem.isScaleIn(), "", orderItem.getEffectiveFrom(),null);
                //String link,boolean transmit                    
            }
        }else if(id > -1 && (orderItem.getStage().equals(EnumOrderStage.AMEND)||orderItem.getStage().equals(EnumOrderStage.CANCEL)) && orderItem.getOrderType() != EnumOrderType.UNDEFINED && orderItem.getSide() != EnumOrderSide.UNDEFINED && orderItem.getReason()!=EnumOrderReason.UNDEFINED){
            int entryID=rowOrderMap.get(orderItem.getRowreference()).entryid;
            int exitID=rowOrderMap.get(orderItem.getRowreference()).exitid;
            getOms().tes.fireOrderEvent(entryID, exitID, Parameters.symbol.get(id), orderItem.getSide(), orderItem.getReason(), orderItem.getOrderType(), orderItem.getSize(), orderItem.getLimitPrice(), orderItem.getTriggerPrice(), getStrategy(), orderItem.getEffectiveDuration(), orderItem.getStage(), orderItem.getEffectiveDuration(), orderItem.getDynamicDuration(), orderItem.getSlippage(), "", true, orderItem.getTif(), orderItem.isScaleIn(), "", orderItem.getEffectiveFrom(),null);
                
        }
    }
}
