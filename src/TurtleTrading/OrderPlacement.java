/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package TurtleTrading;

import com.ib.client.Contract;
import com.ib.client.Order;
import incurrframework.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.Timer;

/**
 *
 * @author admin
 */
public class OrderPlacement implements OrderListener, OrderStatusListener {

    private MainAlgorithm a;
    private final static Logger logger = Logger.getLogger(DataBars.class.getName());

    public OrderPlacement(MainAlgorithm o) {
        a = o;

        // register listeners
        MainAlgorithm.addOrderListner(this);
        for (BeanConnection c : Parameters.connection) {
            c.getWrapper().addOrderStatusListener(this);
        }

        //Initialize timers
        new Timer(10000, cancelExpiredOrders).start();
        new Timer(2000, hastenCloseOut).start();
        new Timer(10000, reattemptOrders).start();
    }

    @Override
    public void orderReceived(OrderEvent event) {
        //for each connection eligible for trading
        // System.out.println(Thread.currentThread().getName());
        try {
            int id = event.getSymbolBean().getSerialno() - 1;
            logger.log(Level.FINEST, "OrderReceived. Symbol:{0}, OrderSide:{1}", new Object[]{Parameters.symbol.get(id).getSymbol(), event.getSide()});
            for (BeanConnection c : Parameters.connection) {
                if ("Trading".equals(c.getPurpose())) {
                    //check if system is square && order id is to initiate
                    int positions = c.getPositions().get(id) == null ? 0 : c.getPositions().get(id).getPosition();
                    if (positions == 0) {
                        //if (c.getOrders().get(id).getPositionsize() == 0){
                        if (event.getSide() == OrderSide.BUY || event.getSide() == OrderSide.SHORT) {
                            //confirm current active order id is complete 
                            if (zilchOpenOrders(c, id)) {
                                Order ord = c.getWrapper().createOrder(event.getOrderSize(), event.getSide(), event.getLimitPrice(), event.getTriggerPrice(), "DAY", 3, false);
                                Contract con = c.getWrapper().createContract(id);
                                logger.log(Level.INFO, "Method:{0},Action:Entry Order Placed, Symbol:{1}, Side={2}", new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(), Parameters.symbol.get(id).getSymbol(), event.getOrderSize()});
                                int orderid = c.getWrapper().placeOrder(c, id + 1, event.getSide(), ord, con);
                                BeanOutputTurtle summary=new BeanOutputTurtle();
                                summary.setPriceEntry(event.getLimitPrice());
                                summary.setSymbolName(Parameters.symbol.get(id).getSymbol());
                                summary.setQuantity(event.getOrderSize());
                                summary.setUpbar(a.getParam().getBreachUp().get(id));
                                summary.setDownbar(a.getParam().getBreachDown().get(id));
                                a.getParam().getSummary().put(orderid, summary);
                                long tempexpire = System.currentTimeMillis() + 3 * 60 * 1000;
                                c.getOrdersToBeCancelled().put(orderid, tempexpire);
                                logger.log(Level.FINEST, "Expiration time in object getOrdersToBeCancelled=" + DateUtil.getFormatedDate("yyyyMMdd HH:mm:ss", tempexpire));
                            } //else cancel open orders
                            else {
                                logger.log(Level.INFO, "Method:{0},Action:Open Orders Being Cancelled before Entry, Symbol:{1}, Side={2}", new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(), Parameters.symbol.get(id).getSymbol(), event.getOrderSize()});
                                cancelOpenOrders(c, id);
                                //Introduce Logic: Place orders when cancelation is successful
                                OrderBean ord = new OrderBean();
                                ord.setSymbolID(id + 1);
                                ord.setOrderSize(event.getOrderSize());
                                ord.setOrderSide(event.getSide());
                                ord.setLimitPrice(event.getLimitPrice());
                                ord.setTriggerPrice(event.getTriggerPrice());
                                c.getOrdersToBeRetried().put(System.currentTimeMillis(), ord);
                            }
                        } else if (event.getSide() == OrderSide.SELL || event.getSide() == OrderSide.COVER) {
                            //do nothing as position size=0. Original orders were not filled. 
                            logger.log(Level.INFO, "Method:{0},Action:No Entry Position. Cancel any open orders, Symbol:{1}, Side={2}", new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(), Parameters.symbol.get(id).getSymbol(), event.getOrderSize()});
                            cancelOpenOrders(c, id);
                        }

                        //next loop processes when positions !=0
                    } else if (positions != 0) {
                        if (event.getSide() == OrderSide.SELL || event.getSide() == OrderSide.COVER) {
                            cancelOpenOrders(c, id); //as this is a squareoff condition, first cancel all open orders
                            if ((event.getSide() == OrderSide.SELL && c.getPositions().get(id).getPosition() > 0) || (event.getSide() == OrderSide.COVER && c.getPositions().get(id).getPosition() < 0)) {
                                Order ord = c.getWrapper().createOrder(Math.abs(c.getPositions().get(id).getPosition()), event.getSide(), event.getLimitPrice(), event.getTriggerPrice(), "DAY", 3, false);
                                Contract con = c.getWrapper().createContract(id);
                                logger.log(Level.INFO, "Method:{0},Action:Exit Position, Symbol:{1}, Side={2}, position:{3}", new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(), Parameters.symbol.get(id).getSymbol(), event.getOrderSize(), positions});
                                int orderid = c.getWrapper().placeOrder(c, id + 1, event.getSide(), ord, con);
                                c.getOrdersToBeFastTracked().put(orderid, System.currentTimeMillis() + 3 * 60 * 1000);
                            } else {
                                //something is wrong in positions. Cancel all open orders and square all positions
                                cancelOpenOrders(c, id);
                                squareAllPositions(c, id);
                                logger.log(Level.INFO, "Method:{0},Action: Positions are inconsistent with state of orders. Square off positions, Symbol:{1}, Side:{2}, Positions:{3}", new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(), Parameters.symbol.get(id).getSymbol(), event.getOrderSize(), positions});
                            }
                        } else if (event.getSide() == OrderSide.BUY || event.getSide() == OrderSide.SHORT) {
                            //cancelOpenOrders(c, id);
                            //squareAllPositions(c, id);
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, e.toString());
        }
    }

    @Override
    public void orderStatusReceived(OrderStatusEvent event) {
        try {
            int orderid = event.getOrderID();
            //update HashMap orders
            OrderBean ob = event.getC().getOrders().get(orderid);
            if (ob != null) {
                int id = ob.getSymbolID() - 1;
                if (event.getRemaining() == 0) {
                    //completely filled
                    OrderSide tmpOrdSide = reverseLookup(event.getC(), event.getC().getOrdersSymbols().get(id), orderid);
                    Integer trailBuyOrderID = event.getC().getOrdersSymbols().get(id).get(4);
                    Integer trailSellOrderID = event.getC().getOrdersSymbols().get(id).get(5);
                    updateFilledOrders(event.getC(), id, orderid, event.getFilled(), event.getAvgFillPrice(), event.getLastFillPrice());
                    if (tmpOrdSide == OrderSide.BUY || tmpOrdSide == OrderSide.SHORT) {
                        manageTrailingOrders(event.getC(), trailBuyOrderID + trailSellOrderID, id, event.getFilled(), tmpOrdSide, event.getAvgFillPrice());
                    }
                } else if (event.getRemaining() > 0 && event.getAvgFillPrice() > 0 && !"Cancelled".equals(event.getStatus())) {
                    // partial fill
                    OrderSide tmpOrdSide = reverseLookup(event.getC(), event.getC().getOrdersSymbols().get(id), orderid);
                    updatePartialFills(event.getC(), id, orderid, event.getFilled(), event.getAvgFillPrice(), event.getLastFillPrice());
                    if (tmpOrdSide == OrderSide.BUY || tmpOrdSide == OrderSide.SHORT) {
                        Integer trailBuyOrderID = event.getC().getOrdersSymbols().get(id).get(4);
                        Integer trailSellOrderID = event.getC().getOrdersSymbols().get(id).get(5);
                        manageTrailingOrders(event.getC(), trailBuyOrderID + trailSellOrderID, id, event.getFilled(), tmpOrdSide, event.getAvgFillPrice());
                    }
                } else if ("Cancelled".equals(event.getStatus())) {
                    //cancelled
                    updateCancelledOrders(event.getC(), id, orderid);
                    
                } else if ("Submitted".equals(event.getStatus())) {
                    updateAcknowledgement(event.getC(), id, orderid);
                }
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, e.toString());
        }
    }
    ActionListener cancelExpiredOrders = new ActionListener() { //call this every 10 seconds
        @Override
        public void actionPerformed(ActionEvent e) {
            for (BeanConnection c : Parameters.connection) {
                if (c.getOrdersToBeCancelled().size() > 0) {
                    ArrayList<Integer> temp = new ArrayList();
                    for (Integer key : c.getOrdersToBeCancelled().keySet()) {
                        logger.log(Level.FINEST, "Expiration Time:{0},System Time:{1}", new Object[]{DateUtil.getFormatedDate("yyyyMMdd HH:mm:ss", c.getOrdersToBeCancelled().get(key)), DateUtil.getFormatedDate("yyyyMMdd HH:mm:ss", System.currentTimeMillis())});
                        if (c.getOrdersToBeCancelled().get(key) < System.currentTimeMillis()
                                && ((c.getOrders().get(key).getStatus() != EnumOrderStatus.Acknowledged) || c.getOrders().get(key).getStatus() == EnumOrderStatus.Submitted || c.getOrders().get(key).getStatus() == EnumOrderStatus.PartialFilled)) {
                            logger.log(Level.INFO, "Method:{0}, Symbol:{1}, OrderID:{2}", new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(), Parameters.symbol.get(c.getOrders().get(key).getSymbolID() - 1).getSymbol(), key});
                            //logger.log(Level.INFO,"Expired Order being cancelled. OrderID="+key);
                            c.getWrapper().cancelOrder(c, key);
                            temp.add(key); //holds all orders that have now been cancelled
                        }
                    }
                    for (int ordersToBeDeleted : temp) {
                        c.getOrdersToBeCancelled().remove(ordersToBeDeleted);
                        logger.log(Level.FINEST, "Expired Order being deleted from cancellation queue. OrderID=" + ordersToBeDeleted);
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
                    for (Integer key : c.getOrdersToBeFastTracked().keySet()) {
                        if (c.getOrdersToBeFastTracked().get(key) < System.currentTimeMillis()
                                && (c.getOrders().get(key).getStatus() != EnumOrderStatus.CompleteFilled)) {
                            logger.log(Level.INFO, "Method:{0}, Symbol:{1}, OrderID:{2}", new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(), Parameters.symbol.get(c.getOrders().get(key).getSymbolID() - 1).getSymbol(), key});
                            c.getWrapper().cancelOrder(c, key);
                            fastClose(c, key);
                            temp.add(key); //holds all orders that have now been cancelled
                        }
                    }
                    for (int ordersToBeDeleted : temp) {
                        c.getOrdersToBeFastTracked().remove(ordersToBeDeleted);
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
                        int tempID = c.getOrdersToBeRetried().get(key).getSymbolID() - 1;
                        OrderBean ordb = c.getOrdersToBeRetried().get(key);
                        if (System.currentTimeMillis() > key + 60 * 1000) {
                            temp.add(key); //if > 60 seconds have passed then add the key to temp. This record will be deleted from orders to be retried queue.
                        } else if (c.getPositions().get(tempID).getPosition() == 0 && zilchOpenOrders(c, tempID)) {
                            //update temp
                            temp.add(key);
                            //place orders
                            Order ord = c.getWrapper().createOrder(ordb.getOrderSize(), ordb.getOrderSide(), ordb.getLimitPrice(), ordb.getTriggerPrice(), "DAY", 3, false);
                            Contract con = c.getWrapper().createContract(tempID);
                            logger.log(Level.INFO, "Method:{0}, Symbol:{1}, OrderID:{2}", new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(), Parameters.symbol.get(ordb.getSymbolID() - 1).getSymbol(), key});
                            c.getWrapper().placeOrder(c, tempID + 1, ordb.getOrderSide(), ord, con);
                        }
                    }
                    for (long ordersToBeDeleted : temp) {
                        logger.log(Level.INFO, "Symbol Deleted from retry attempt. Method:{0}, Symbol:{1}", new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(),  Parameters.symbol.get(c.getOrdersToBeRetried().get(ordersToBeDeleted).getSymbolID()-1).getSymbol()});
                        c.getOrdersToBeRetried().remove(ordersToBeDeleted);
                    }

                }
            }
        }
    };

    private synchronized void manageTrailingOrders(BeanConnection c, int orderID, int id, int size, OrderSide underlyingSide, double fillprice) {
        if (orderID > 0) {
            //int tempid=c.getOrders().get(orderID).getSymbolID()-1;
            updateOrderAmount(c, orderID, size);
        } else {
            //place new trailbuy or trailsell order
            OrderSide tmpOrderSide = underlyingSide == OrderSide.BUY ? OrderSide.TRAILSELL : OrderSide.TRAILBUY;
            double tickSize = Double.parseDouble(a.getParam().getTickSize());
            double tmpTrailStop = ((int) (Parameters.symbol.get(id).getTrailstop() * fillprice / tickSize)) * tickSize;
            Order ord = c.getWrapper().createOrder(Math.abs(size), tmpOrderSide, 0, tmpTrailStop, "DAY", 0, true);
            Contract con = c.getWrapper().createContract(id);
            c.getWrapper().placeOrder(c, id + 1, tmpOrderSide, ord, con);
        }
        logger.log(Level.INFO, "Method:{0}, Symbol:{1}", new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(), Parameters.symbol.get(id).getSymbol()});

    }

    private synchronized void updateOrderAmount(BeanConnection c, int orderid, int size) {
        OrderSide ordSide = c.getOrders().get(orderid).getOrderSide();
        int id = c.getOrders().get(orderid).getSymbolID() - 1;
        logger.log(Level.INFO, "Method:{0}, Symbol:{1}, OrderID:{2}, Original Amount:{3}, New Amount:{4},OrderSide:{5}", new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(), Parameters.symbol.get(id).getSymbol(), orderid, c.getOrders().get(orderid).getOrderSize(), size, c.getOrders().get(orderid).getOrderType()});
        //logger.log(Level.INFO,"Update Trailing Order. Symbol:{0},Order ID:{1}, New Trailing Order Amount:{2}, order Type:{3}",new Object[]{Parameters.symbol.get(id).getSymbol(),orderid,size,c.getOrders().get(orderid).getOrderType()});
        Order ord = new Order();
        ord = c.getWrapper().createOrderFromExisting(c, orderid);
        ord.m_orderId = orderid;
        ord.m_totalQuantity = size;
        Contract con = c.getWrapper().createContract(id);
        c.getWrapper().placeOrder(c, id + 1, ordSide, ord, con);


    }

    private synchronized void squareAllPositions(BeanConnection c, int id) {
        int position = c.getPositions().get(id).getPosition();
        Contract con = c.getWrapper().createContract(id);
        Order ord = new Order();
        if (position > 0) {
            ord = c.getWrapper().createOrder(Math.abs(position), OrderSide.SELL, 0, 0, "DAY", 0, false);
            logger.log(Level.FINEST, "Order Placed. Square on Error. Symbol ID={0}", new Object[]{Parameters.symbol.get(id).getSymbol()});
            c.getWrapper().placeOrder(c, id + 1, OrderSide.SELL, ord, con);
        } else if (position < 0) {
            ord = c.getWrapper().createOrder(Math.abs(position), OrderSide.COVER, 0, 0, "DAY", 0, false);
            logger.log(Level.FINEST, "Order Placed. Square on Error. Symbol ID={0}", new Object[]{Parameters.symbol.get(id + 1).getSymbol()});
            c.getWrapper().placeOrder(c, id + 1, OrderSide.COVER, ord, con);
        }
        logger.log(Level.INFO, "Method:{0}, Symbol:{1}, OrderID:{2}, Position:{3}, OrderSide:{4}", new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(), Parameters.symbol.get(id).getSymbol(), ord.m_orderId, position, ord.m_action});
    }

    private synchronized boolean zilchOpenOrders(BeanConnection c, int id) {
        boolean zilchorders = c.getOrdersSymbols().get(id) == null ? true : false;
        if (!zilchorders) {
            ArrayList<Integer> tempArray = c.getOrdersSymbols().get(id);
            for (Integer i : tempArray) {
                if (i > 0) {
                    return false;
                }
            }
            zilchorders = true;
        }
        return zilchorders;
    }

    private synchronized void cancelOpenOrders(BeanConnection c, int id) {

        ArrayList<Integer> tempArray = c.getOrdersSymbols().get(id);
        for (int orderID : tempArray) {
            if (orderID > 0) {
                //if order exists and open ordType="Buy" or "Short"
                //check an earlier cancellation request is not pending and if all ok then cancel
                if (!c.getOrders().get(orderID).isCancelRequested()) {
                    logger.log(Level.INFO, "Method:{0}, Symbol:{1}, OrderID:{2}", new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(), Parameters.symbol.get(c.getOrders().get(orderID).getSymbolID() - 1).getSymbol(), orderID});
                    c.getWrapper().cancelOrder(c, orderID);
                }
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
        c.getWrapper().placeOrder(c, symbolID, origOrder.getOrderSide(), ord, con);
    }

    private synchronized boolean updateFilledOrders(BeanConnection c, int id, int orderID, int filled, double avgFillPrice, double lastFillPrice) {
        OrderBean ord = c.getOrders().get(orderID);
        int fill = filled - ord.getFillSize();
        ord.setFillSize(filled);
        ord.setFillPrice(avgFillPrice);
        ord.setStatus(EnumOrderStatus.CompleteFilled);
        BeanPosition p = c.getPositions().get(id);
        logger.log(Level.INFO, "Method:{0},Symbol:{1}, Incremental Fill Reported:{2},Total Fill={3},Position within Program={4}, Side={5}", new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(), Parameters.symbol.get(id).getSymbol(), fill, filled, ord.getFillSize(), ord.getOrderSide()});

        //update position
        int origposition = c.getPositions().get(id).getPosition();
        if (c.getOrders().get(orderID).getOrderSide() == OrderSide.SELL || c.getOrders().get(orderID).getOrderSide() == OrderSide.SHORT) {
            fill = -fill;
            logger.log(Level.FINEST, "Reversed fill sign as sell or short. Fill=" + fill);
        }
        double realizedPL = (origposition + fill) == 0 && origposition != 0 ? -(origposition * p.getPrice() + fill * lastFillPrice) : p.getProfit();
        double positionPrice = (origposition + fill) == 0 ? 0 : (p.getPosition() * p.getPrice() + fill * lastFillPrice) / (origposition + fill);
        logger.log(Level.INFO, "Method:{0},Symbol:{1},Position:{2},Position Price:{3},Realized P&L:{4}", new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(), Parameters.symbol.get(id).getSymbol(), origposition + fill, positionPrice, realizedPL});
        p.setPrice(positionPrice);
        p.setProfit(realizedPL);
        p.setPosition(origposition + fill);
        c.getPositions().put(id, p);
        //update orderid=0 from Symbols HashMap
        ArrayList<Integer> orders = c.getOrdersSymbols().get(id);
        if (orders.size() > 0) {
            int count = 0;
            for (int i : orders) {
                if (i == orderID) {
                    c.getOrdersSymbols().get(id).set(count, 0);
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

        //Delete order from Symbols HashMap
        ArrayList<Integer> orders = c.getOrdersSymbols().get(id);
        if (orders.size() > 0) {
            int count = 0;
            for (int i : orders) {
                if (i == orderID) {
                    c.getOrdersSymbols().get(id).set(count, 0);
                    return true;
                }
                count = count + 1;
            }
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
        BeanPosition p = c.getPositions().get(id);
        //update position
        int origposition = p.getPosition();
        if (c.getOrders().get(orderID).getOrderSide() == OrderSide.SELL || c.getOrders().get(orderID).getOrderSide() == OrderSide.SHORT || c.getOrders().get(orderID).getOrderSide() == OrderSide.TRAILSELL) {
            fill = -fill;
        }
        logger.log(Level.INFO, "Method:{0},Symbol:{1}, Incremental Fill Reported:{2},Total Fill={3},Position within Program={4}, Side={5}", new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(), Parameters.symbol.get(id).getSymbol(), fill, filled, ord.getFillSize(), ord.getOrderSide()});
        double realizedPL = (origposition + fill) == 0 && origposition != 0 ? -(origposition * p.getPrice() + fill * lastFillPrice) : 0;
        double positionPrice = (origposition + fill) == 0 ? 0 : (p.getPosition() * p.getPrice() + fill * lastFillPrice) / (origposition + fill);
        logger.log(Level.INFO, "Method:{0},Symbol:{1},Position:{2},Position Price:{3},Realized P&L:{4}", new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(), Parameters.symbol.get(id).getSymbol(), origposition + fill, positionPrice, realizedPL});
        p.setPrice(positionPrice);
        p.setProfit(realizedPL);
        p.setPosition(origposition + fill);
        c.getPositions().put(id, p);
        return true;
    }

    private synchronized OrderSide reverseLookup(BeanConnection c, ArrayList<Integer> arr, int orderid) {

        OrderSide ordSide = OrderSide.UNDEFINED;
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
}
