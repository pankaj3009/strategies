/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package TurtleTrading;

import com.ib.client.Contract;
import com.ib.client.Order;
import incurrframework.*;
import static incurrframework.EnumOrderSide.BUY;
import static incurrframework.EnumOrderSide.COVER;
import static incurrframework.EnumOrderSide.SELL;
import static incurrframework.EnumOrderSide.SHORT;
import static incurrframework.EnumOrderSide.TRAILBUY;
import static incurrframework.EnumOrderSide.TRAILSELL;
import static incurrframework.EnumOrderType.Limit;
import static incurrframework.EnumOrderType.Market;
import static incurrframework.EnumOrderType.Stop;
import static incurrframework.EnumOrderType.StopLimit;
import static incurrframework.EnumOrderType.Trail;
import static incurrframework.EnumOrderType.TrailLimit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.Timer;

/**
 *
 * @author admin
 */
public class OrderPlacement implements OrderListener, OrderStatusListener, TWSErrorListener,BidAskListener {

    private MainAlgorithm a;
    private final static Logger logger = Logger.getLogger(DataBars.class.getName());
    final OrderPlacement parentorder=this;
    private static HashMap<Integer,BeanOrderInformation> activeOrders=new HashMap(); //holds the symbol id and corresponding order information
    double tickSize;
    
    public OrderPlacement(MainAlgorithm o) {
        a = o;
        tickSize = Double.parseDouble(a.getParamTurtle().getTickSize());
        // register listeners
        MainAlgorithm.addOrderListner(this);
        for (BeanConnection c : Parameters.connection) {
            c.getWrapper().addOrderStatusListener(this);
            c.getWrapper().addBidAskListener(this);
            
        }

        //Initialize timers
        new Timer(10000, cancelExpiredOrders).start();
        new Timer(2000, hastenCloseOut).start();
        new Timer(10000, reattemptOrders).start();
        
        //new Timer(2000,cancelTimeOrders).start();
    }

        @Override
    public void bidaskChanged(BidAskEvent event) {

      //if the symbol exists in ordersSymbols (order exists) && ordersToBeCancelled (the order has potential for dynamic management)
       
            int id=event.getSymbolID();
            if(activeOrders.containsKey(id)) {
           
           BeanOrderInformation tempOrderInfo=activeOrders.get(id);
           if(tempOrderInfo.getExpireTime()-tempOrderInfo.getOrigEvent().getDynamicOrderDuration()*60*1000>System.currentTimeMillis()){
               //amendement scenario is valid. 
               //Check for level of agression
               LinkedList<Double> e=new LinkedList();
               e=Parameters.symbol.get(id).getTradedPrices();
               int size=e.size();
               double uptick=0;
               double downtick=0;
               double aggression=0;
               
               if(size>1){
               for (int i=1;i<10;i++){
                   if(e.get(i)>e.get(i-1)){uptick++;}
                   else if (e.get(i)<e.get(i-1)){downtick++;}
                    }
               }
               if(uptick+downtick>0){
                aggression=uptick/(uptick+downtick);
               }
               double limitprice=activeOrders.get(id).getOrigEvent().getLimitPrice();
               
               double bidprice=0;
               double askprice=0;
               bidprice=Parameters.symbol.get(id).getBidPrice();
               askprice=Parameters.symbol.get(id).getAskPrice();
               double newlimitprice=0;
               switch (activeOrders.get(id).getOrigEvent().getSide()){
                   case BUY:
                   case COVER: 
                       if(bidprice==limitprice){
                           newlimitprice=0;
                       }else{
                       newlimitprice=((int) ((bidprice+((askprice-bidprice)*aggression)) / tickSize)) * tickSize;
                       }
                       break;
                   case SHORT:
                   case SELL:if(askprice==limitprice){
                   newlimitprice=0;
                   }
                   else {
                       newlimitprice=((int) ((askprice-(askprice-bidprice)*(1-aggression)) / tickSize)) * tickSize;
                   }
                       break;
               }
               logger.log(Level.INFO,"Method:{0}, Symbol:{1}, OrderID:{2}, bidprice:{3}, askprice:{4}, aggression:{5}, limitprice:{6}, new limit price:{7}",new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(), Parameters.symbol.get(id).getSymbol(),activeOrders.get(id).getOrderID(),bidprice,askprice,aggression,limitprice,newlimitprice});
               Boolean placeorder=newlimitprice>0 && (Math.abs(newlimitprice-limitprice)> tickSize?Boolean.TRUE:Boolean.FALSE) && Math.abs(newlimitprice-limitprice)<0.1*limitprice;
               if(placeorder){
                   OrderEvent eventnew=activeOrders.get(id).getOrigEvent();
                   eventnew.setLimitPrice(newlimitprice);
                   eventnew.setOrderIntent(EnumOrderIntent.Amend);
                   logger.log(Level.INFO,"Order Amendment Needed. Method:{0}, Symbol:{1}, OrderID:{2}",new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(), Parameters.symbol.get(id).getSymbol(),activeOrders.get(id).getOrderID()});
                    orderReceived(eventnew);
                   }
           }
       }
    }
    
    @Override
    public void orderReceived(OrderEvent event) {
        //for each connection eligible for trading
        // System.out.println(Thread.currentThread().getName());
        try {
            //we first handle initial orders given by EnumOrderIntent=Init
            if (event.getOrderIntent() == EnumOrderIntent.Init) {
                int id = event.getSymbolBean().getSerialno() - 1;
                logger.log(Level.FINEST, "OrderReceived. Symbol:{0}, OrderSide:{1}", new Object[]{Parameters.symbol.get(id).getSymbol(), event.getSide()});
                for (BeanConnection c : Parameters.connection) {
                    if ("Trading".equals(c.getPurpose()) && c.getStrategy().contains(event.getOrdReference())) {
                        //check if system is square
                        Index ind = new Index(event.getOrdReference(), id);
                        Integer position = c.getPositions().get(ind) == null ? 0 : c.getPositions().get(ind).getPosition();
                        position = position != 0 ? 1 : 0;
                        int signedPositions = c.getPositions().get(ind) == null ? 0 : c.getPositions().get(ind).getPosition();
                        Integer openorders = zilchOpenOrders(c, id, event.getOrdReference()) == Boolean.TRUE ? 0 : 1;
                        Integer entry = (event.getSide() == EnumOrderSide.BUY || event.getSide() == EnumOrderSide.SHORT) ? 1 : 0;
                        String rule = Integer.toBinaryString(position) + Integer.toBinaryString(openorders) + Integer.toBinaryString(entry);
                        switch (rule) {
                            case "000"://position=0, no openorder=0, exit order as entry=0
                                //if(event.getSide()==EnumOrderSide.SELL||event.getSide()==EnumOrderSide.COVER){
                                logger.log(Level.INFO, "Method:{0},Error Case:000, Symbol:{1}, Size={2}, Side:{3}, Limit:{4}, Trigger:{5}, Expiration Time:{6}", new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(), Parameters.symbol.get(id).getSymbol(), event.getOrderSize(),event.getSide(),event.getLimitPrice(),event.getTriggerPrice(),event.getExpireTime()});
                                //logger.log(Level.INFO, "Method:{0},Case:000, Symbol:{1}, Size={2}, Side:{3}, Limit:{4}, Trigger:{5}, Expiration Time:{6}", new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(), Parameters.symbol.get(id).getSymbol(), event.getOrderSize(),event.getSide(),event.getLimitPrice(),event.getTriggerPrice(),event.getExpireTime()});
                                //processEntryOrder(id, c, event);}
                                //}
                                break;
                            case "001": //position=0, no openorder=0, entry order as entry=1
                                if(signedPositions==0 && (event.getSide()==EnumOrderSide.BUY||event.getSide()==EnumOrderSide.SHORT)){
                                logger.log(Level.INFO, "Method:{0},Case:001, Symbol:{1}, Size={2}, Side:{3}, Limit:{4}, Trigger:{5}, Expiration Time:{6}", new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(), Parameters.symbol.get(id).getSymbol(), event.getOrderSize(),event.getSide(),event.getLimitPrice(),event.getTriggerPrice(),event.getExpireTime()});
                                processEntryOrder(id, c, event);
                                } else{
                                logger.log(Level.INFO, "Method:{0},Error Case:001, Symbol:{1}, Size={2}, Side:{3}, Limit:{4}, Trigger:{5}, Expiration Time:{6}", new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(), Parameters.symbol.get(id).getSymbol(), event.getOrderSize(),event.getSide(),event.getLimitPrice(),event.getTriggerPrice(),event.getExpireTime()});
                                    
                                }
                                break;
                            case "100": //position=1, no open order=0, exit order 
                                if ((signedPositions > 0 && event.getSide() == EnumOrderSide.SELL) || (signedPositions < 0 && event.getSide() == EnumOrderSide.COVER)) {
                                logger.log(Level.INFO, "Method:{0},Case:100, Symbol:{1}, Size={2}, Side:{3}, Limit:{4}, Trigger:{5}, Expiration Time:{6}", new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(), Parameters.symbol.get(id).getSymbol(), event.getOrderSize(),event.getSide(),event.getLimitPrice(),event.getTriggerPrice(),event.getExpireTime()});
                                processExitOrder(id, c, event);
                                } else {
                                    logger.log(Level.INFO, "Method:{0},Error Case:100, Symbol:{1}, Size={2}, Side:{3}, Limit:{4}, Trigger:{5}, Expiration Time:{6}", new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(), Parameters.symbol.get(id).getSymbol(), event.getOrderSize(),event.getSide(),event.getLimitPrice(),event.getTriggerPrice(),event.getExpireTime()});
                                    this.cancelOpenOrders(c, id, event.getOrdReference());
                                    this.squareAllPositions(c, id, event.getOrdReference());
                                    //Stop trading the instrument
                                }
                                break;
                            case "010": //position=0, open order, exit order as entry=0
                                if ((c.getOrdersSymbols().get(ind).get(0) > 0 && event.getSide() == EnumOrderSide.SELL) || (c.getOrdersSymbols().get(ind).get(2) > 0 && event.getSide() == EnumOrderSide.COVER)) {
                                    logger.log(Level.INFO, "Method:{0},Case:010, Symbol:{1}, Size={2}, Side:{3}, Limit:{4}, Trigger:{5}, Expiration Time:{6}", new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(), Parameters.symbol.get(id).getSymbol(), event.getOrderSize(), event.getSide(), event.getLimitPrice(), event.getTriggerPrice(), event.getExpireTime()});
                                    if (event.getExpireTime() > 0) {
                                        //this is an exit order. Cancel open orders and square all positions
                                        this.cancelOpenOrders(c, id, event.getOrdReference());
                                        addOrdersToBeRetried(id, c, event);
                                    } else {
                                        addOrdersToBeRetried(id, c, event); //what will happen if the entry orders were not filled?
                                    }
                                } else { //not a valid scenario. cancel open orders
                                    logger.log(Level.INFO, "Method:{0},Error Case:100, Symbol:{1}, Size={2}, Side:{3}, Limit:{4}, Trigger:{5}, Expiration Time:{6}", new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(), Parameters.symbol.get(id).getSymbol(), event.getOrderSize(), event.getSide(), event.getLimitPrice(), event.getTriggerPrice(), event.getExpireTime()});
                                    this.cancelOpenOrders(c, id, event.getOrdReference());
                                }
                                break;
                            case "110": //position=1, open order exists, exit order
                                //scenario: entry position not completely filled. Advance exit order received
                                logger.log(Level.INFO, "Method:{0},Case:110, Symbol:{1}, Size={2}, Side:{3}, Limit:{4}, Trigger:{5}, Expiration Time:{6}", new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(), Parameters.symbol.get(id).getSymbol(), event.getOrderSize(),event.getSide(),event.getLimitPrice(),event.getTriggerPrice(),event.getExpireTime()});
                                  if (event.getExpireTime() == 0) {
                                    addOrdersToBeRetried(id, c, event);
                                } else { //its an exit order
                                    this.cancelOpenOrders(c, id, event.getOrdReference());
                                    addOrdersToBeRetried(id, c, event);
                                }
                                break;
                            case "011": //no position, open order exists, entry order
                                //cancel open orders and place new orders
                                logger.log(Level.INFO, "Method:{0},Case:011, Symbol:{1}, Size={2}, Side:{3}, Limit:{4}, Trigger:{5}, Expiration Time:{6}", new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(), Parameters.symbol.get(id).getSymbol(), event.getOrderSize(),event.getSide(),event.getLimitPrice(),event.getTriggerPrice(),event.getExpireTime()});
                                this.cancelOpenOrders(c, id, event.getOrdReference());
                                addOrdersToBeRetried(id, c, event);
                                break;
                            case "101": //position, no open order, entry order received
                                //system is broken for the symbol
                                logger.log(Level.INFO, "Method:{0},Error Case:101, Symbol:{1}, Size={2}, Side:{3}, Limit:{4}, Trigger:{5}, Expiration Time:{6}, position:{7}", new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(), Parameters.symbol.get(id).getSymbol(), event.getOrderSize(),event.getSide(),event.getLimitPrice(),event.getTriggerPrice(),event.getExpireTime(),signedPositions});
                                this.cancelOpenOrders(c, id, event.getOrdReference());
                                this.squareAllPositions(c, id, event.getOrdReference());
                                //Stop trading the instrument
                                break;
                            case "111"://position, open order, entry order received.
                                //if stop and reverse, execute
                                if (event.getExpireTime() != 0) {
                                    if ((c.getOrdersSymbols().get(ind).get(1) > 0 && event.getSide() == EnumOrderSide.SHORT) || (c.getOrdersSymbols().get(ind).get(3) > 0 && event.getSide() == EnumOrderSide.BUY)) {
                                        logger.log(Level.INFO, "Method:{0},Case:111, Symbol:{1}, Size={2}, Side:{3}, Limit:{4}, Trigger:{5}, Expiration Time:{6}", new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(), Parameters.symbol.get(id).getSymbol(), event.getOrderSize(),event.getSide(),event.getLimitPrice(),event.getTriggerPrice(),event.getExpireTime()});
                                        int orderid=event.getSide() == EnumOrderSide.SHORT?c.getOrdersSymbols().get(ind).get(1):c.getOrdersSymbols().get(ind).get(3);
                                        this.fastClose(c, orderid);
                                        this.processEntryOrder(id, c, event);
                                        //addOrdersToBeRetried(id,c,event); //what will happen if the entry orders were not filled?
                                    } else {
                                        //system is broken for the symbol
                                        logger.log(Level.INFO, "Method:{0},Error Case:111, Symbol:{1}, Size={2}, Side:{3}, Limit:{4}, Trigger:{5}, Expiration Time:{6}", new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(), Parameters.symbol.get(id).getSymbol(), event.getOrderSize(),event.getSide(),event.getLimitPrice(),event.getTriggerPrice(),event.getExpireTime()});
                                        this.cancelOpenOrders(c, id, event.getOrdReference());
                                        this.squareAllPositions(c, id, event.getOrdReference());
                                        //Stop trading the instrument
                                    }
                                } else if (event.getExpireTime() == 0) {
                                    //advance order. 
                                    logger.log(Level.INFO, "Method:{0},Expire Time=0. Case:111, Symbol:{1}, Size={2}, Side:{3}, Limit:{4}, Trigger:{5}, Expiration Time:{6}", new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(), Parameters.symbol.get(id).getSymbol(), event.getOrderSize(),event.getSide(),event.getLimitPrice(),event.getTriggerPrice(),event.getExpireTime()});
                                    addOrdersToBeRetried(id, c, event);
                                }
                                break;
                            default: //print message with details
                                logger.log(Level.INFO, "Method:{0},Case:Default, Symbol:{1}, Size={2}, Side:{3}, Limit:{4}, Trigger:{5}, Expiration Time:{6}", new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(), Parameters.symbol.get(id).getSymbol(), event.getOrderSize(),event.getSide(),event.getLimitPrice(),event.getTriggerPrice(),event.getExpireTime()});
                                break;
                        }
                    }
                }
            } else if (event.getOrderIntent() == EnumOrderIntent.Amend) {
                int id = event.getSymbolBean().getSerialno() - 1;
                logger.log(Level.FINEST, "OrderReceived. Symbol:{0}, OrderSide:{1}", new Object[]{Parameters.symbol.get(id).getSymbol(), event.getSide()});
                for (BeanConnection c : Parameters.connection) {
                    if ("Trading".equals(c.getPurpose()) && c.getStrategy().contains(event.getOrdReference())) {
                        processOrderAmend(id, c, event);
                    }
                }
            } else if (event.getOrderIntent() == EnumOrderIntent.Cancel) {
                int id = event.getSymbolBean().getSerialno() - 1;
                logger.log(Level.FINEST, "OrderReceived. Symbol:{0}, OrderSide:{1}", new Object[]{Parameters.symbol.get(id).getSymbol(), event.getSide()});
                for (BeanConnection c : Parameters.connection) {
                    if ("Trading".equals(c.getPurpose()) && c.getStrategy().contains(event.getOrdReference())) {
                        //check if system is square && order id is to initiate
                        processOrderCancel(id, c, event);
                    }
                }
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, e.toString());
        }
    }

    void processEntryOrder(int id, BeanConnection c, OrderEvent event) {
        
        Order ord = c.getWrapper().createOrder(event.getOrderSize(), event.getSide(), event.getLimitPrice(), event.getTriggerPrice(), "DAY", event.getExpireTime(), false, event.getOrdReference(), "");
        Contract con = c.getWrapper().createContract(id);
        logger.log(Level.INFO, "Method:{0},Action:Entry Order Placed, Symbol:{1}, Size={2}, limit price={3}, trigger price={4}", new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(), Parameters.symbol.get(id).getSymbol(), event.getOrderSize(),event.getLimitPrice(),event.getTriggerPrice()});
        int orderid = c.getWrapper().placeOrder(c, id + 1, event.getSide(), ord, con, event.getExitType());
        long tempexpire = System.currentTimeMillis() + event.getExpireTime() * 60 * 1000;
        //dont cancel if advance orders
        if (event.getExpireTime() != 0) {
            c.getOrdersToBeCancelled().put(orderid, new BeanOrderInformation(id,c,orderid,tempexpire,event));
            activeOrders.put(id, new BeanOrderInformation(id,c,orderid,tempexpire,event));
            logger.log(Level.FINEST, "Expiration time in object getOrdersToBeCancelled=" + DateUtil.getFormatedDate("yyyyMMdd HH:mm:ss", tempexpire));
        }
    }

    void processExitOrder(int id, BeanConnection c, OrderEvent event) {
        Index ind = new Index(event.getOrdReference(), id);
        int positions = Math.abs(c.getPositions().get(ind).getPosition());
        Order ord = c.getWrapper().createOrder(positions, event.getSide(), event.getLimitPrice(), event.getTriggerPrice(), "DAY", event.getExpireTime(), false, event.getOrdReference(), "");
        Contract con = c.getWrapper().createContract(id);
        logger.log(Level.INFO, "Method:{0},Action:Exit Position, Symbol:{1}, Side={2}, position:{3}, limit price={4}, trigger price={5}", new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(), Parameters.symbol.get(id).getSymbol(), event.getOrderSize(), positions,event.getLimitPrice(),event.getTriggerPrice()});
        int orderid = c.getWrapper().placeOrder(c, id + 1, event.getSide(), ord, con, event.getExitType());
        if (event.getExpireTime() != 0) {
            long tempexpire=System.currentTimeMillis() + event.getExpireTime() * 60 * 1000;
            c.getOrdersToBeFastTracked().put(orderid, new BeanOrderInformation(id,c,orderid,tempexpire,event));
            activeOrders.put(id, new BeanOrderInformation(id,c,orderid,tempexpire,event));
        }
    }
    
    void processOrderAmend(int id, BeanConnection c, OrderEvent event) {
        Index ind = new Index(event.getOrdReference(), id);
        int orderid = 0;
        switch (event.getSide()) {
            case BUY:
                orderid = c.getOrdersSymbols().get(ind).get(0);
                break;
            case SELL:
                orderid = c.getOrdersSymbols().get(ind).get(1);
                break;
            case SHORT:
                orderid = c.getOrdersSymbols().get(ind).get(2);
                break;
            case COVER:
                orderid = c.getOrdersSymbols().get(ind).get(3);
                break;
            case TRAILBUY:
                orderid = c.getOrdersSymbols().get(ind).get(4);
                break;
            case TRAILSELL:
                orderid = c.getOrdersSymbols().get(ind).get(5);
                break;
            default:
                break;
        }
        if (orderid > 0) { //order exists that can be amended.
            Order ord = new Order();
            ord = c.getWrapper().createOrderFromExisting(c, orderid);
            ord.m_orderId = orderid;
            if (ord.m_auxPrice != event.getTriggerPrice() || ord.m_lmtPrice != event.getLimitPrice() || (ord.m_goodTillDate == null && event.getExpireTime() > 0)) {
                //amendment is processed if limit or trigger price changes or if there is an expiration time added
                ord.m_auxPrice = event.getTriggerPrice() > 0 ? event.getTriggerPrice() : 0;
                ord.m_lmtPrice = event.getLimitPrice() > 0 ? event.getLimitPrice() : 0;
                if (event.getSide() != EnumOrderSide.TRAILBUY || event.getSide() != EnumOrderSide.TRAILSELL) {
                    if (event.getLimitPrice() > 0 & event.getTriggerPrice() == 0) {
                        ord.m_orderType = "LMT";
                        ord.m_lmtPrice = event.getLimitPrice();
                    } else if (event.getLimitPrice() == 0 && event.getTriggerPrice() > 0 && (event.getSide() == EnumOrderSide.SELL || event.getSide() == EnumOrderSide.COVER)) {
                        ord.m_orderType = "STP";
                        ord.m_lmtPrice = event.getLimitPrice();
                        ord.m_auxPrice = event.getTriggerPrice();
                    } else if (event.getLimitPrice() > 0 && event.getTriggerPrice() > 0) {
                        ord.m_orderType = "STP LMT";
                        ord.m_lmtPrice = event.getLimitPrice();
                        ord.m_auxPrice = event.getTriggerPrice();
                    } else {
                        ord.m_orderType = "MKT";
                        ord.m_lmtPrice = 0;
                        ord.m_auxPrice = 0;
                    }
                }

                ord.m_totalQuantity = event.getOrderSize(); //pending: check for any fills on the original order
                if (event.getExpireTime() != 0 && (!(c.getOrdersToBeCancelled().containsKey(orderid) || c.getOrdersToBeFastTracked().containsKey(orderid)))) {
                    //we will place the order in the cancelled/hastened queue only if it was not existing before
                    long tempexpire = System.currentTimeMillis() + event.getExpireTime() * 60 * 1000;
                    c.getOrdersToBeCancelled().put(orderid, new BeanOrderInformation(id, c, orderid, tempexpire, event));
                    logger.log(Level.INFO, "Entry Order amendment placed in cancellation queue. Symbol:{0}, Cancellation Time: {1}",new Object[]{Parameters.symbol.get(id).getSymbol(), DateUtil.getFormatedDate("yyyyMMdd HH:mm:ss", tempexpire)});
                    activeOrders.put(id, new BeanOrderInformation(id, c, orderid, tempexpire, event));
                    //place orders if there is a change in limit price
                    if (event.getLimitPrice() != ord.m_lmtPrice) {
                        Contract con = c.getWrapper().createContract(id);
                        logger.log(Level.INFO, "{0}, Symbol:{1}, Order Side:{2},orderID:{3},limit price={4}, trigger price={5}", new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(), Parameters.symbol.get(id).getSymbol(), event.getSide(), orderid, event.getLimitPrice(), event.getTriggerPrice()});
                        c.getWrapper().placeOrder(c, id + 1, event.getSide(), ord, con, event.getExitType());
                    }
                    //update orders information
                    c.getOrders().get(orderid).setExpireTime(String.valueOf(tempexpire));
                    c.getOrders().get(orderid).setTriggerPrice(ord.m_auxPrice);
                    c.getOrders().get(orderid).setLimitPrice(ord.m_lmtPrice);

                } else if (event.getLimitPrice() != ord.m_lmtPrice || event.getTriggerPrice() != ord.m_auxPrice) {
                        //update orders if advance orders or second amendment after expiration time added
                        Contract con = c.getWrapper().createContract(id);
                        logger.log(Level.INFO, "{0}, Symbol:{1}, Order Side:{2},orderID:{3},limit price={4}, trigger price={5}", new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(), Parameters.symbol.get(id).getSymbol(), event.getSide(), orderid, event.getLimitPrice(), event.getTriggerPrice()});
                        c.getWrapper().placeOrder(c, id + 1, event.getSide(), ord, con, event.getExitType());
                        activeOrders.put(id, new BeanOrderInformation(id, c, orderid, event.getExpireTime(), event));
                        c.getOrders().get(orderid).setTriggerPrice(ord.m_auxPrice);
                        c.getOrders().get(orderid).setLimitPrice(ord.m_lmtPrice);
                    }               
            }
      } else {
            int positions = c.getPositions().get(ind).getPosition();
            //Retry orders if there is an open BUY order and we get a corresponding sell. Same for COVER
            if ((c.getOrdersSymbols().get(ind).get(0) > 0 && event.getSide() == EnumOrderSide.SELL) || (c.getOrdersSymbols().get(ind).get(2) > 0 && event.getSide() == EnumOrderSide.COVER)) {
                this.addOrdersToBeRetried(id, c, event);
            } else if ((event.getSide() == EnumOrderSide.BUY && positions == 0) || (event.getSide() == EnumOrderSide.SHORT && positions == 0)) { //if for some reason, there is no open order for entry, init entry is attempted
                logger.log(Level.INFO, "Changed Amend intent to Init :{0}, Symbol:{1}, Order Side:{2}, orderID:{3}", new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(), Parameters.symbol.get(id).getSymbol(), event.getSide(), orderid});
                event.setOrderIntent(EnumOrderIntent.Init);
                parentorder.orderReceived(event);
            } else {
                //no order to amend. Do nothing. Probably earlier order was filled. Write to log
                logger.log(Level.INFO, "No orders to amend Method:{0}, Symbol:{1}, Order Side:{2}, orderID:{3}", new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(), Parameters.symbol.get(id).getSymbol(), event.getSide(), orderid});
            }
        }
    }

    void processOrderCancel(int id, BeanConnection c, OrderEvent event) {
        Index ind = new Index(event.getOrdReference(), id);
        int orderid = 0;
        switch (event.getSide()) {
            case BUY:
                orderid = c.getOrdersSymbols().get(ind).get(0);
                break;
            case SELL:
                orderid = c.getOrdersSymbols().get(ind).get(1);
                break;
            case SHORT:
                orderid = c.getOrdersSymbols().get(ind).get(2);
                break;
            case COVER:
                orderid = c.getOrdersSymbols().get(ind).get(3);
                break;
            case TRAILBUY:
                orderid = c.getOrdersSymbols().get(ind).get(4);
                break;
            case TRAILSELL:
                orderid = c.getOrdersSymbols().get(ind).get(5);
                break;
            default:
                break;
        }
        if (orderid > 0) {
            c.getWrapper().cancelOrder(c, orderid);
            logger.log(Level.INFO, "Orders Cancelled via request:{0}, Symbol:{1}, OrderID:{2}, Order Side:{3}", new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(), Parameters.symbol.get(id).getSymbol(), orderid, event.getSide()});

        }
    }

    void addOrdersToBeRetried(int id, BeanConnection c, OrderEvent event) {
     
        OrderBean ord = new OrderBean();
        ord.setSymbolID(id + 1);
        ord.setOrderSize(event.getOrderSize());
        ord.setOrderSide(event.getSide());
        ord.setLimitPrice(event.getLimitPrice());
        ord.setTriggerPrice(event.getTriggerPrice());
        ord.setExpireTime(Integer.toString(event.getExpireTime()));
        ord.setExitLogic(event.getExitType());
        ord.setOrderReference(event.getOrdReference());
        boolean orderupdated=Boolean.FALSE;
         //if an existing order exists for the same id and side, it is amended to the new values
         for (Long key : c.getOrdersToBeRetried().keySet()) {
             int tempID = c.getOrdersToBeRetried().get(key).getSymbolBean().getSerialno() - 1;
             EnumOrderSide ordSide = c.getOrdersToBeRetried().get(key).getSide();
             String ordRef=c.getOrdersToBeRetried().get(key).getOrdReference();
             if(tempID==id && ordSide==event.getSide() && ordRef.equals(event.getOrdReference()) ){
                 c.getOrdersToBeRetried().put(key, event);
                 orderupdated=Boolean.TRUE;
                 logger.log(Level.INFO, "Method:{0},Updated OrdersToBeRetried for symbol: {1}", new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(), Parameters.symbol.get(id).getSymbol()});
                 break;
             }
         }
         if(!orderupdated){
        c.getOrdersToBeRetried().put(System.currentTimeMillis(), event);
        logger.log(Level.INFO, "Method:{0},Added requirement for OrdersToBeRetried for symbol: {1}", new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(), Parameters.symbol.get(id).getSymbol()});
         }
    }

        
    ActionListener cancelExpiredOrders = new ActionListener() { //call this every 10 seconds
        @Override
        public void actionPerformed(ActionEvent e) {
            for (BeanConnection c : Parameters.connection) {
                if (c.getOrdersToBeCancelled().size() > 0) {
                    ArrayList<Integer> temp = new ArrayList();
                    ArrayList<Integer> symbols=new ArrayList();
                    for (Integer key : c.getOrdersToBeCancelled().keySet()) {
                        logger.log(Level.FINEST, "Expiration Time:{0},System Time:{1}", new Object[]{DateUtil.getFormatedDate("yyyyMMdd HH:mm:ss", c.getOrdersToBeCancelled().get(key).getExpireTime()), DateUtil.getFormatedDate("yyyyMMdd HH:mm:ss", System.currentTimeMillis())});
                        if (c.getOrdersToBeCancelled().get(key).getExpireTime() < System.currentTimeMillis()
                                && ((c.getOrders().get(key).getStatus() != EnumOrderStatus.Acknowledged) || c.getOrders().get(key).getStatus() == EnumOrderStatus.Submitted || c.getOrders().get(key).getStatus() == EnumOrderStatus.PartialFilled)) {
                            logger.log(Level.INFO, "cancelExpiredOrders Method:{0}, Symbol:{1}, OrderID:{2}", new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(), Parameters.symbol.get(c.getOrders().get(key).getSymbolID() - 1).getSymbol(), key});
                            //logger.log(Level.INFO,"Expired Order being cancelled. OrderID="+key);
                            c.getWrapper().cancelOrder(c, key);
                            if ((c.getOrders().get(key).getOrderSide() == EnumOrderSide.BUY || c.getOrders().get(key).getOrderSide() == EnumOrderSide.SHORT)
                                    && (c.getOrders().get(key).getStatus() == EnumOrderStatus.CancelledNoFill || c.getOrders().get(key).getStatus() == EnumOrderStatus.CancelledPartialFill)) {
                                c.getOrdersMissed().add(key);
                            }
                            
                            temp.add(key); //holds all orders that have now been cancelled
                            symbols.add(c.getOrdersToBeCancelled().get(key).getSymbolid());
                        }
                    }
                    for (int ordersToBeDeleted : temp) {
                        c.getOrdersToBeCancelled().remove(ordersToBeDeleted);
                        logger.log(Level.FINEST, "Expired Order being deleted from cancellation queue. OrderID=" + ordersToBeDeleted);
                    }
                        for (int symbolsToBeDeleted : symbols) {
                        activeOrders.remove(symbolsToBeDeleted);
                        logger.log(Level.FINEST, "Expired symbols being deleted from active orders queue. Symbol=" + symbolsToBeDeleted);
                    }
                }
            }
        }
    };
   
    ActionListener hastenCloseOut = new ActionListener() { //call this every 1 second
        @Override
        public void actionPerformed(ActionEvent e) {
            for (BeanConnection c : Parameters.connection) {
                if (c.getOrdersToBeFastTracked().size() > 0) {
                    ArrayList<Integer> temp = new ArrayList();
                    ArrayList<Integer>symbols=new ArrayList();
                    for (Integer key : c.getOrdersToBeFastTracked().keySet()) {
                        BeanOrderInformation boi= c.getOrdersToBeFastTracked().get(key);
                        if (boi.getExpireTime() < System.currentTimeMillis()
                                && (c.getOrders().get(key).getStatus() != EnumOrderStatus.CompleteFilled)) {
                            logger.log(Level.INFO, "hastenCloseOut Method:{0}, Symbol:{1}, OrderID:{2}", new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(), Parameters.symbol.get(c.getOrders().get(key).getSymbolID() - 1).getSymbol(), key});
                            c.getWrapper().cancelOrder(c, key);
                            boi.getOrigEvent().setLimitPrice(0);
                            boi.getOrigEvent().setTriggerPrice(0);
                            parentorder.addOrdersToBeRetried(boi.getSymbolid(), c, boi.getOrigEvent());
                            //fastClose(c, key);
                            temp.add(key); //holds all orders that have now been cancelled
                            symbols.add(c.getOrdersToBeFastTracked().get(key).getSymbolid());
                        }
                    }
                    for (int ordersToBeDeleted : temp) {
                        c.getOrdersToBeFastTracked().remove(ordersToBeDeleted);
                    }
                    for (int symbolsToBeDeleted : symbols) {
                        activeOrders.remove(symbolsToBeDeleted);
                    }
                    
                }
            }
        }
    };
   
    
        ActionListener reattemptOrders = new ActionListener() { //call this every 10 seconds
        
        @Override
        public void actionPerformed(ActionEvent e) {
            for (BeanConnection c : Parameters.connection) {
                if (c.getOrdersToBeRetried().size() > 0) {
                    ArrayList<Long> temp = new ArrayList();
                    for (Long key : c.getOrdersToBeRetried().keySet()) {
                        OrderEvent ordb = c.getOrdersToBeRetried().get(key);
                        parentorder.orderReceived(ordb);
                        temp.add(key);
                    }
                        for (long ordersToBeDeleted : temp) {
                        logger.log(Level.INFO, "Symbol Deleted from retry attempt. Method:{0}, Symbol:{1}", new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(), Parameters.symbol.get(c.getOrdersToBeRetried().get(ordersToBeDeleted).getSymbolBean().getSerialno() - 1).getSymbol()});
                        c.getOrdersToBeRetried().remove(ordersToBeDeleted);
                    }
                }
            }
        }
    };
        
    @Override
    public void orderStatusReceived(OrderStatusEvent event) {
        try {
            int orderid = event.getOrderID();
            //update HashMap orders
            OrderBean ob = event.getC().getOrders().get(orderid);
            if (ob != null) {
                int id = ob.getSymbolID() - 1;
                Index ind = new Index(ob.getOrderReference(), id);
                if (event.getRemaining() == 0) {
                    //completely filled
                    EnumOrderSide tmpOrdSide = reverseLookup(event.getC(), event.getC().getOrdersSymbols().get(ind), orderid);
                    updateFilledOrders(event.getC(), id, orderid, event.getFilled(), event.getAvgFillPrice(), event.getLastFillPrice());
                    event.getC().getOrdersInProgress().remove(new Integer(orderid));
                    activeOrders.remove(id);
                    if ("TRAIL".compareTo(ob.getExitLogic()) == 0) {
                        Integer trailBuyOrderID = event.getC().getOrdersSymbols().get(ind).get(4);
                        Integer trailSellOrderID = event.getC().getOrdersSymbols().get(ind).get(5);
                        if (tmpOrdSide == EnumOrderSide.BUY || tmpOrdSide == EnumOrderSide.SHORT) {
                            //manageTrailingOrders(event.getC(), trailBuyOrderID + trailSellOrderID, id, event.getFilled(), tmpOrdSide, event.getAvgFillPrice(),ob.getOrderReference());
                        }
                    } else if ("MOC".compareTo(ob.getExitLogic()) == 0) {
                        Integer BuyOrderID = event.getC().getOrdersSymbols().get(ind).get(4);
                        Integer SellOrderID = event.getC().getOrdersSymbols().get(ind).get(5);
                        if (tmpOrdSide == EnumOrderSide.BUY || tmpOrdSide == EnumOrderSide.SHORT) {
                            manageMOCOrders(event.getC(), BuyOrderID + SellOrderID, id, event.getFilled(), tmpOrdSide, event.getAvgFillPrice(), ob.getOrderReference());

                        }
                    }


                } else if (event.getRemaining() > 0 && event.getAvgFillPrice() > 0 && !"Cancelled".equals(event.getStatus())) {
                    // partial fill
                    if ("TRAIL".compareTo(ob.getExitLogic()) == 0) {
                        EnumOrderSide tmpOrdSide = reverseLookup(event.getC(), event.getC().getOrdersSymbols().get(ind), orderid);
                        updatePartialFills(event.getC(), id, orderid, event.getFilled(), event.getAvgFillPrice(), event.getLastFillPrice());
                        if (tmpOrdSide == EnumOrderSide.BUY || tmpOrdSide == EnumOrderSide.SHORT) {
                            Integer trailBuyOrderID = event.getC().getOrdersSymbols().get(ind).get(4);
                            Integer trailSellOrderID = event.getC().getOrdersSymbols().get(ind).get(5);
                            //manageTrailingOrders(event.getC(), trailBuyOrderID + trailSellOrderID, id, event.getFilled(), tmpOrdSide, event.getAvgFillPrice(),ob.getOrderReference());
                        }
                    } else if ("MOC".compareTo(ob.getExitLogic()) == 0) {
                        EnumOrderSide tmpOrdSide = reverseLookup(event.getC(), event.getC().getOrdersSymbols().get(ind), orderid);
                        updatePartialFills(event.getC(), id, orderid, event.getFilled(), event.getAvgFillPrice(), event.getLastFillPrice());
                        if (tmpOrdSide == EnumOrderSide.BUY || tmpOrdSide == EnumOrderSide.SHORT) {
                            Integer BuyOrderID = event.getC().getOrdersSymbols().get(ind).get(4);
                            Integer SellOrderID = event.getC().getOrdersSymbols().get(ind).get(5);
                            manageMOCOrders(event.getC(), BuyOrderID + SellOrderID, id, event.getFilled(), tmpOrdSide, event.getAvgFillPrice(), ob.getOrderReference());
                        }
                    } else {
                        updatePartialFills(event.getC(), id, orderid, event.getFilled(), event.getAvgFillPrice(), event.getLastFillPrice());
                    }
                } else if ("Cancelled".equals(event.getStatus())) {
                    //cancelled
                    logger.log(Level.INFO, "Method:{0},Symbol:{1},OrderID:{2}", new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(),Parameters.symbol.get(id).getSymbol(), orderid});
                    updateCancelledOrders(event.getC(), id, orderid);
                    event.getC().getOrdersInProgress().remove(new Integer(orderid));

                } else if ("Submitted".equals(event.getStatus())) {
                    updateAcknowledgement(event.getC(), id, orderid);
                }
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, e.toString());
        }
    }
    
    private synchronized void manageTrailingOrders(BeanConnection c, int orderID, int id, int size, EnumOrderSide underlyingSide, double fillprice, String ordReference) {
        if (orderID > 0) {
            //int tempid=c.getOrders().get(orderID).getSymbolID()-1;
            updateOrderAmount(c, orderID, size);
        } else {
            //place new trailbuy or trailsell order
            EnumOrderSide tmpOrderSide = underlyingSide == EnumOrderSide.BUY ? EnumOrderSide.TRAILSELL : EnumOrderSide.TRAILBUY;
            double tmpTrailStop = ((int) (Parameters.symbol.get(id).getTrailstop() * fillprice / tickSize)) * tickSize;
            Order ord = c.getWrapper().createOrder(Math.abs(size), tmpOrderSide, 0, tmpTrailStop, "DAY", 0, true, ordReference, "");
            Contract con = c.getWrapper().createContract(id);
            c.getWrapper().placeOrder(c, id + 1, tmpOrderSide, ord, con, "");
        }
        logger.log(Level.INFO, "Method:{0}, Symbol:{1}", new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(), Parameters.symbol.get(id).getSymbol()});

    }

    private synchronized void manageMOCOrders(BeanConnection c, int orderID, int id, int size, EnumOrderSide underlyingSide, double fillprice, String ordReference) {
        if (orderID > 0) {
            //int tempid=c.getOrders().get(orderID).getSymbolID()-1;
            updateOrderAmount(c, orderID, size);
        } else {
            //place new trailbuy or trailsell order
            EnumOrderSide tmpOrderSide = underlyingSide == EnumOrderSide.BUY ? EnumOrderSide.SELL : EnumOrderSide.COVER;
            Format formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String validAfter = formatter.format(a.getParamGuds().getEndDate());
            Order ord = c.getWrapper().createOrder(Math.abs(size), tmpOrderSide, 0, 0, "DAY", 0, true, ordReference, validAfter);
            Contract con = c.getWrapper().createContract(id);
            c.getWrapper().placeOrder(c, id + 1, tmpOrderSide, ord, con, "");
        }
        logger.log(Level.INFO, "Method:{0}, Symbol:{1}", new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(), Parameters.symbol.get(id).getSymbol()});

    }

    private synchronized void updateOrderAmount(BeanConnection c, int orderid, int size) {
        EnumOrderSide ordSide = c.getOrders().get(orderid).getOrderSide();
        int id = c.getOrders().get(orderid).getSymbolID() - 1;
        logger.log(Level.INFO, "Method:{0}, Symbol:{1}, OrderID:{2}, Original Amount:{3}, New Amount:{4},OrderSide:{5}", new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(), Parameters.symbol.get(id).getSymbol(), orderid, c.getOrders().get(orderid).getOrderSize(), size, c.getOrders().get(orderid).getOrderType()});
        //logger.log(Level.INFO,"Update Trailing Order. Symbol:{0},Order ID:{1}, New Trailing Order Amount:{2}, order Type:{3}",new Object[]{Parameters.symbol.get(id).getSymbol(),orderid,size,c.getOrders().get(orderid).getOrderType()});
        Order ord = new Order();
        ord = c.getWrapper().createOrderFromExisting(c, orderid);
        ord.m_orderId = orderid;
        ord.m_totalQuantity = size;
        Contract con = c.getWrapper().createContract(id);
        c.getWrapper().placeOrder(c, id + 1, ordSide, ord, con, c.getOrders().get(orderid).getExitLogic());


    }

    public synchronized void squareAllPositions(BeanConnection c, int id, String strategy) {
        Index ind = new Index(strategy, id);
        int position = c.getPositions().get(ind).getPosition();
        Contract con = c.getWrapper().createContract(id);
        Order ord = new Order();
        if (position > 0) {
            ord = c.getWrapper().createOrder(Math.abs(position), EnumOrderSide.SELL, 0, 0, "DAY", 0, false, strategy, "");
            logger.log(Level.INFO, "Order Placed. Square on Error. Symbol ID={0}", new Object[]{Parameters.symbol.get(id).getSymbol()});
            ord.m_orderId=c.getWrapper().placeOrder(c, id + 1, EnumOrderSide.SELL, ord, con, "");
        } else if (position < 0) {
            ord = c.getWrapper().createOrder(Math.abs(position), EnumOrderSide.COVER, 0, 0, "DAY", 0, false, strategy, "");
            logger.log(Level.INFO, "Order Placed. Square on Error. Symbol ID={0}", new Object[]{Parameters.symbol.get(id + 1).getSymbol()});
            ord.m_orderId=c.getWrapper().placeOrder(c, id + 1, EnumOrderSide.COVER, ord, con, "");
        }
        logger.log(Level.INFO, "Method:{0}, Symbol:{1}, OrderID:{2}, Position:{3}, OrderSide:{4}", new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(), Parameters.symbol.get(id).getSymbol(), ord.m_orderId, position, ord.m_action});
    }

    private synchronized boolean zilchOpenOrders(BeanConnection c, int id, String strategy) {
        Index ind = new Index(strategy, id);
        boolean zilchorders = c.getOrdersSymbols().get(ind) == null ? true : false;
        if (!zilchorders) {
            ArrayList<Integer> tempArray = c.getOrdersSymbols().get(ind);
            for (Integer i : tempArray) {
                if (i > 0) {
                    return false;
                }
            }
            zilchorders = true;
        }
        return zilchorders;
    }

    public synchronized void cancelOpenOrders(BeanConnection c, int id, String strategy) {
        Index ind = new Index(strategy, id);
        ArrayList<Integer> tempArray = c.getOrdersSymbols().get(ind);
        ArrayList<Integer> temp = new ArrayList();
        ArrayList<Integer>symbols=new ArrayList();
        for (int orderID : tempArray) {
            if (orderID > 0) {
                //if order exists and open ordType="Buy" or "Short"
                //check an earlier cancellation request is not pending and if all ok then cancel
                for (Integer key : c.getOrdersToBeFastTracked().keySet()) {
                    if (key == orderID) {
                        temp.add(key);
                        symbols.add(c.getOrdersToBeFastTracked().get(key).getSymbolid());
                        logger.log(Level.INFO, "Orders will be removed from fasttrack. Method:{0}, Symbol:{1}, OrderID:{2}", new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(), Parameters.symbol.get(c.getOrders().get(orderID).getSymbolID() - 1).getSymbol(), orderID});
                    }
                }
                if (!c.getOrders().get(orderID).isCancelRequested()) {
                    logger.log(Level.INFO, "Method:{0}, Symbol:{1}, OrderID:{2}", new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(), Parameters.symbol.get(c.getOrders().get(orderID).getSymbolID() - 1).getSymbol(), orderID});
                    c.getWrapper().cancelOrder(c, orderID);
                }
            }
            for (int ordersToBeDeleted : temp) {
                c.getOrdersToBeFastTracked().remove(ordersToBeDeleted);
            }
            for (int symbolsToBeDeleted : symbols) {
                activeOrders.remove(symbolsToBeDeleted);
            }

        }
    }

    private synchronized void fastClose(BeanConnection c, int orderID) {
        //Check if order is already market. If yes, return
        if (c.getOrders().get(orderID).getOrderType() == EnumOrderType.Market) {
            return;
        }
        OrderBean origOrder = c.getOrders().get(orderID);
        int symbolID = origOrder.getSymbolID();
        //amend order to market
        Order ord = new Order();
        ord = c.getWrapper().createOrderFromExisting(c, orderID);
        ord.m_tif = "DAY";
        ord.m_goodAfterTime = "";
        ord.m_orderType = "MKT";
        ord.m_totalQuantity = origOrder.getOrderSize() - origOrder.getFillSize();
        Contract con = c.getWrapper().createContract(symbolID - 1);
        logger.log(Level.INFO, "Method:{0}, Symbol:{1}", new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(), Parameters.symbol.get(symbolID - 1).getSymbol()});
        c.getWrapper().placeOrder(c, symbolID, origOrder.getOrderSide(), ord, con, origOrder.getExitLogic());
    }

    private synchronized boolean updateFilledOrders(BeanConnection c, int id, int orderID, int filled, double avgFillPrice, double lastFillPrice) {
        OrderBean ord = c.getOrders().get(orderID);
        String strategy = ord.getOrderReference();
        Index ind = new Index(strategy, id);
        int fill = filled - ord.getFillSize();
        ord.setFillSize(filled);
        ord.setFillPrice(avgFillPrice);
        ord.setStatus(EnumOrderStatus.CompleteFilled);
        BeanPosition p = c.getPositions().get(ind) == null ? new BeanPosition() : c.getPositions().get(ind);
        logger.log(Level.INFO, "Method:{0},Symbol:{1}, Incremental Fill Reported:{2},Total Fill={3},Position within Program={4}, Side={5}", new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(), Parameters.symbol.get(id).getSymbol(), fill, filled, ord.getFillSize(), ord.getOrderSide()});

        //update position
        int origposition = p.getPosition();
        if (c.getOrders().get(orderID).getOrderSide() == EnumOrderSide.SELL || c.getOrders().get(orderID).getOrderSide() == EnumOrderSide.SHORT || c.getOrders().get(orderID).getOrderSide() == EnumOrderSide.TRAILSELL) {
            fill = -fill;
            logger.log(Level.FINEST, "Reversed fill sign as sell or short. Symbol:{1}, Fill={2}",new Object[]{Parameters.symbol.get(id).getSymbol(),fill});
        }
        double realizedPL = (origposition + fill) == 0 && origposition != 0 ? -(origposition * p.getPrice() + fill * lastFillPrice) + p.getProfit() : p.getProfit();
        double positionPrice = (origposition + fill) == 0 ? 0 : (p.getPosition() * p.getPrice() + fill * lastFillPrice) / (origposition + fill);
        logger.log(Level.INFO, "Method:{0},Symbol:{1},Position:{2},Position Price:{3},Realized P&L:{4}", new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(), Parameters.symbol.get(id).getSymbol(), origposition + fill, positionPrice, realizedPL});
        p.setPrice(positionPrice);
        p.setProfit(realizedPL);
        p.setPosition(origposition + fill);
        c.getPositions().put(ind, p);
        //update orderid=0 from Symbols HashMap
        ArrayList<Integer> orders = c.getOrdersSymbols().get(ind);
        if (orders.size() > 0) {
            int count = 0;
            for (int i : orders) {
                if (i == orderID) {
                    c.getOrdersSymbols().get(ind).set(count, 0);
                    return true;
                }
                count = count + 1;
            }
        }
        return false;

    }

    private synchronized boolean updateCancelledOrders(BeanConnection c, int id, int orderID) {
        OrderBean ord = c.getOrders().get(orderID);
        ord.setCancelRequested(false);
        if (ord.getFillSize() > 0) {
            ord.setStatus(EnumOrderStatus.CancelledPartialFill);
        } else {
            ord.setStatus(EnumOrderStatus.CancelledNoFill);
        }
        Index ind = new Index(ord.getOrderReference(), id);
        //Delete order from Symbols HashMap
        ArrayList<Integer> orders = c.getOrdersSymbols().get(ind);
        if (orders.size() > 0) {
            int count = 0;
            for (int i : orders) {
                if (i == orderID) {
                    c.getOrdersSymbols().get(ind).set(count, 0);
                    logger.log(Level.INFO, "Method:{0},Symbol:{1},OrderID:{2},Position in orderSymbols: {3}", new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(),Parameters.symbol.get(id).getSymbol(), orderID, count});
                    return true;
                }
                count = count + 1;
            }
        }
        //Delete orders from expired orders list
        if(c.getOrdersToBeCancelled().containsKey(orderID)){
            c.getOrdersToBeCancelled().remove(orderID);
        }
        if(activeOrders.containsKey(id)){
            activeOrders.remove(id);
        }
        return false;

    }

    private synchronized boolean updatePartialFills(BeanConnection c, int id, int orderID, int filled, double avgFillPrice, double lastFillPrice) {
        OrderBean ord = c.getOrders().get(orderID);
        //identify incremental fill
        int fill = filled - ord.getFillSize();
        ord.setFillSize(filled);
        ord.setFillPrice(avgFillPrice);
        ord.setStatus(EnumOrderStatus.PartialFilled);
        String strategy = ord.getOrderReference();
        Index ind = new Index(strategy, id);
        BeanPosition p = c.getPositions().get(ind) == null ? new BeanPosition() : c.getPositions().get(ind);
        //update position
        int origposition = p.getPosition();
        if (c.getOrders().get(orderID).getOrderSide() == EnumOrderSide.SELL || c.getOrders().get(orderID).getOrderSide() == EnumOrderSide.SHORT || c.getOrders().get(orderID).getOrderSide() == EnumOrderSide.TRAILSELL) {
            fill = -fill;
        }
        logger.log(Level.INFO, "Method:{0},Symbol:{1}, Incremental Fill Reported:{2},Total Fill={3},Position within Program={4}, Side={5}", new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(), Parameters.symbol.get(id).getSymbol(), fill, filled, ord.getFillSize(), ord.getOrderSide()});
        double realizedPL = (origposition + fill) == 0 && origposition != 0 ? -(origposition * p.getPrice() + fill * lastFillPrice) : 0;
        double positionPrice = (origposition + fill) == 0 ? 0 : (p.getPosition() * p.getPrice() + fill * lastFillPrice) / (origposition + fill);
        logger.log(Level.INFO, "Method:{0},Symbol:{1},Position:{2},Position Price:{3},Realized P&L:{4}", new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(), Parameters.symbol.get(id).getSymbol(), origposition + fill, positionPrice, realizedPL});
        p.setPrice(positionPrice);
        p.setProfit(realizedPL);
        p.setPosition(origposition + fill);
        c.getPositions().put(ind, p);
        return true;
    }

    private synchronized EnumOrderSide reverseLookup(BeanConnection c, ArrayList<Integer> arr, int orderid) {

        EnumOrderSide ordSide = EnumOrderSide.UNDEFINED;
        for (int i : arr) {
            if (i == orderid) {
                ordSide = c.getOrders().get(orderid).getOrderSide();
                logger.log(Level.INFO, "Method:{0},Symbol:{1},OrderSide:{2}", new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(), Parameters.symbol.get(c.getOrders().get(orderid).getSymbolID() - 1).getSymbol(), ordSide});
                return ordSide;
            }
        }
        return ordSide;
    }

    private synchronized void updateAcknowledgement(BeanConnection c, int id, int orderID) {
        OrderBean ord = c.getOrders().get(orderID);
        ord.setStatus(EnumOrderStatus.Acknowledged);
    }

    @Override
    public void TWSErrorReceived(TWSErrorEvent event) {
        if (event.getErrorCode() == 201) {
            int id = event.getConnection().getOrders().get(event.getId()).getSymbolID() - 1;
            logger.log(Level.INFO, "Method:{0},Symbol:{1}, ErrorCode:{2},OrderID={3},Message={4}", new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(), Parameters.symbol.get(id).getSymbol(), event.getErrorCode(), event.getId(), event.getErrorMessage()});
            this.updateCancelledOrders(event.getConnection(), id, event.getId());
        }
    }

}
