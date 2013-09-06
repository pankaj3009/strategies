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
    private final static Logger logger = Logger.getLogger(DataBars.class .getName());

    public OrderPlacement(MainAlgorithm o) {
        a = o;

        // register listeners
        MainAlgorithm.addOrderListner(this);
        for (ConnectionBean c : Parameters.connection) {
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
        try{
        int id = event.getSymbolBean().getSerialno() - 1;
        for (ConnectionBean c : Parameters.connection) {
            if ("Trading".equals(c.getPurpose())) {
                //check if system is square && order id is to initiate
                int positions = c.getPositions().get(id) == null ? 0 : c.getPositions().get(id);
                if (positions == 0) {
                    //if (c.getOrders().get(id).getPositionsize() == 0){
                    if (event.getSide() == OrderSide.BUY || event.getSide() == OrderSide.SHORT) {
                        //confirm current active order id is complete 
                        if (zilchOpenOrders(c, id)) {
                            Order ord = c.getWrapper().createOrder(event.getOrderSize(), event.getSide(), event.getLimitPrice(), event.getTriggerPrice(), "DAY", 3, false);
                            Contract con = c.getWrapper().createContract(id);
                            logger.log(Level.INFO,"Order Placed. Entry. Symbol ID={0}",new Object[]{id+1});
                            int orderid = c.getWrapper().placeOrder(c, id+1, event.getSide(), ord, con);
                            long tempexpire=System.currentTimeMillis()+3*60*1000;
                            c.getOrdersToBeCancelled().put(orderid, tempexpire);
                            logger.log(Level.INFO,"Expiration time in object getOrdersToBeCancelled="+tempexpire);
                            
                        } //else cancel open orders
                        else {
                            cancelOpenOrders(c, id);
                            //Introduce Logic: Place orders when cancelation is successful
                            OrderBean ord=new OrderBean();
                            ord.setSymbolID(id+1);
                            ord.setOrderSize(event.getOrderSize());
                            ord.setOrderSide(event.getSide());
                            ord.setLimitPrice(event.getLimitPrice());
                            ord.setTriggerPrice(event.getTriggerPrice());
                            c.getOrdersToBeRetried().put(System.currentTimeMillis(), ord);
                        }
                    } else if (event.getSide() == OrderSide.SELL || event.getSide() == OrderSide.COVER) {
                        //do nothing as position size=0. Original orders were not filled. 
                        cancelOpenOrders(c, id); 
                    }
                
                //next loop processes when positions !=0
                } else if (c.getOrders().get(id).getPositionsize() != 0) {
                    if (event.getSide() == OrderSide.SELL || event.getSide() == OrderSide.COVER) {
                        cancelOpenOrders(c, id); //as this is a squareoff condition, first cancel all open orders
                        if ((event.getSide() == OrderSide.SELL && c.getPositions().get(id) > 0) || (event.getSide() == OrderSide.COVER && c.getPositions().get(id) < 0)) {
                            Order ord = c.getWrapper().createOrder(c.getPositions().get(id), event.getSide(), event.getLimitPrice(), event.getTriggerPrice(), "DAY", 3, false);
                            Contract con = c.getWrapper().createContract(id);
                            logger.log(Level.INFO,"Order Placed. Exit. Symbol ID={0}",new Object[]{id+1});
                            int orderid=c.getWrapper().placeOrder(c, id+1, event.getSide(), ord, con);
                            c.getOrdersToBeFastTracked().put(orderid, System.currentTimeMillis()+3*60*1000);
                        } else {
                            //something is wrong in positions. Cancel all open orders and square all positions
                            cancelOpenOrders(c, id);
                            squareAllPositions(c, id);

                        }
                    } else if (event.getSide() == OrderSide.BUY || event.getSide() == OrderSide.SHORT) {
                        cancelOpenOrders(c, id);
                        squareAllPositions(c, id);

                    }
                }
            }
        }
        }
        catch (Exception e){
            logger.log(Level.SEVERE,e.toString());
        }
    }

    @Override
    public void orderStatusReceived(OrderStatusEvent event) {
        try{
        int orderid = event.getOrderID();

        //update HashMap orders
        OrderBean ob = event.getC().getOrders().get(orderid);
        if (ob != null) {
            int id = ob.getSymbolID()-1;
            if (event.getRemaining()==0) {
                //completely filled
                OrderSide tmpOrdSide = reverseLookup(event.getC(),event.getC().getOrdersSymbols().get(id), orderid);
                updateFilledOrders(event.getC(), id, orderid, event.getFilled(), event.getAvgFillPrice());
                //Reverse lookup on OrderSymbols to get the orderside for the fill
                    if (tmpOrdSide == OrderSide.BUY || tmpOrdSide == OrderSide.SHORT) {
                    logger.log(Level.INFO, "Check if Trailing Required. SymbolID={0}, OrderSide={1} Position={2}", new Object[]{id+1,tmpOrdSide, event.getC().getPositions().get(id)});
                    OrderSide tmpOrderSide = event.getC().getPositions().get(id) > 0 ? OrderSide.TRAILSELL : OrderSide.TRAILBUY;
                    double tmpTrailStop = ((int) (Parameters.symbol.get(id).getTrailstop() * event.getAvgFillPrice() / 0.05)) * 0.05;
                    Order ord = event.getC().getWrapper().createOrder(Math.abs(event.getC().getPositions().get(id)), tmpOrderSide, 0, tmpTrailStop, "DAY", 0, true);
                    Contract con = event.getC().getWrapper().createContract(id);
                    logger.log(Level.INFO,"Order Placed. Trailing. Symbol ID={0}",new Object[]{id+1});
                    event.getC().getWrapper().placeOrder(event.getC(), id+1, tmpOrderSide, ord, con);
                   }
            } else if ( event.getRemaining()> 0 && event.getAvgFillPrice()>0) {
                // partial fill
                updatePartialFills(event.getC(), id, orderid, event.getFilled(), event.getAvgFillPrice());
            } else if (event.getStatus() == "Canceled") {
                //cancelled
                updateCancelledOrders(event.getC(), id, orderid);
            } else if (event.getStatus() == "Submitted") {
                updateAcknowledgement(event.getC(), id, orderid);
            }
        }
        }
        catch (Exception e){
            logger.log(Level.SEVERE,e.toString());
        }
    }

    ActionListener cancelExpiredOrders = new ActionListener() { //call this every 10 seconds
        @Override
        public void actionPerformed(ActionEvent e) {
            for (ConnectionBean c : Parameters.connection) {
                if (c.getOrdersToBeCancelled().size() > 0) {
                    ArrayList<Integer> temp = new ArrayList();
                    for (Integer key : c.getOrdersToBeCancelled().keySet()) {
                        logger.log(Level.INFO,"Expiration Time:{0},System Time:{1}",new Object[]{c.getOrdersToBeCancelled().get(key),System.currentTimeMillis()});
                        if (c.getOrdersToBeCancelled().get(key) < System.currentTimeMillis()
                                && ((c.getOrders().get(key).getStatus() != OrderStatus.Acknowledged) || c.getOrders().get(key).getStatus() == OrderStatus.Submitted || c.getOrders().get(key).getStatus() == OrderStatus.PartialFilled)) {
                            logger.log(Level.INFO,"Expired Order being cancelled. OrderID="+key);
                            c.getWrapper().cancelOrder(c, key);
                            temp.add(key); //holds all orders that have now been cancelled
                        }
                    }
                    for (int ordersToBeDeleted : temp) {
                        c.getOrdersToBeCancelled().remove(ordersToBeDeleted);
                    }
                }
            }
        }
    };
    
    ActionListener hastenCloseOut = new ActionListener() { //call this every 1 second
        @Override
        public void actionPerformed(ActionEvent e) {
            for (ConnectionBean c : Parameters.connection) {
                if (c.getOrdersToBeFastTracked().size() > 0) {
                    ArrayList<Integer> temp = new ArrayList();
                    for (Integer key : c.getOrdersToBeFastTracked().keySet()) {
                        if (c.getOrdersToBeCancelled().get(key) < System.currentTimeMillis()
                                && (c.getOrders().get(key).getStatus() != OrderStatus.Acknowledged) || c.getOrders().get(key).getStatus() == OrderStatus.Submitted || c.getOrders().get(key).getStatus() == OrderStatus.PartialFilled) {
                            logger.log(Level.INFO,"Order being converted to market. OrderID="+key);
                            fastClose(c, c.getOrders().get(key).getSymbolID(), c.getOrders().get(key).getOrderSide(), key);
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
            for (ConnectionBean c : Parameters.connection) {
                if (c.getOrdersToBeRetried().size() > 0) {
                    ArrayList<Long> temp = new ArrayList();
                    for (Long key : c.getOrdersToBeRetried().keySet()) {
                        int tempID = c.getOrdersToBeRetried().get(key).getSymbolID() - 1;
                        OrderBean ordb = c.getOrdersToBeRetried().get(key);
                        if (System.currentTimeMillis() > key + 60 * 1000) {
                            temp.add(key);
                        } else if (c.getPositions().get(tempID) == 0 && zilchOpenOrders(c, tempID)) {
                            //update temp
                            temp.add(key);
                            //place orders
                            Order ord = c.getWrapper().createOrder(ordb.getOrderSize(), ordb.getOrderSide(), ordb.getLimitPrice(), ordb.getTriggerPrice(), "DAY", 3, false);
                            Contract con = c.getWrapper().createContract(tempID);
                            logger.log(Level.INFO,"Order Placed. Delayed. Symbol ID={0}",new Object[]{ordb.getSymbolID()});
                            c.getWrapper().placeOrder(c, tempID + 1, ordb.getOrderSide(), ord, con);
                           }
                    }
                    for (long ordersToBeDeleted : temp) {
                        c.getOrdersToBeRetried().remove(ordersToBeDeleted);
                    }

                }
            }
        }
    };
    
    private synchronized void squareAllPositions(ConnectionBean c, int id) {
        int position = c.getPositions().get(id);
        Contract con = c.getWrapper().createContract(id);
        if (position > 0) {
            Order ord = c.getWrapper().createOrder(position, OrderSide.SELL, 0, 0, "DAY", 0, false);
            logger.log(Level.INFO,"Order Placed. Square on Error. Symbol ID={0}",new Object[]{id+1});
            c.getWrapper().placeOrder(c, id+1, OrderSide.SELL, ord, con);
            } else if (position < 0) {
            Order ord = c.getWrapper().createOrder(-position, OrderSide.COVER, 0, 0, "DAY", 0, false);
            logger.log(Level.INFO,"Order Placed. Square on Error. Symbol ID={0}",new Object[]{id+1});
            c.getWrapper().placeOrder(c, id+1, OrderSide.COVER, ord, con);
            }
    }

    private synchronized boolean squarePosition(ConnectionBean c, int id) {
        boolean temp = ((c.getPositions().get(id) == 0) ? true : false);
        return temp;
    }

    private synchronized boolean zilchOpenOrders(ConnectionBean c, int id) {
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

    private synchronized void cancelOpenOrders(ConnectionBean c, int id) {

        ArrayList<Integer> tempArray = c.getOrdersSymbols().get(id);
        for (int orderID : tempArray) {
            if (orderID > 0) {
                //if order exists and open ordType="Buy" or "Short"
                //check an earlier cancellation request is not pending and if all ok then cancel
                if (!c.getOrders().get(orderID).isCancelRequested()) {
                    logger.log(Level.INFO,"Open orders being cancelled. OrderID="+orderID);
                    c.getWrapper().cancelOrder(c, orderID);
                }
            }

        }
    }

    private synchronized void fastClose(ConnectionBean c, int symbolID, OrderSide side, int orderID) {
        //Check if order is already market. If yes, return
        if (c.getOrders().get(orderID).getOrderType() == OrderType.Market) {
            return;
        }

        //amend order to market
        Order ord = new Order();
        String ordValidity = "DAY";
        int expireMinutes = 0;

        int size = Parameters.symbol.get(symbolID).getMinsize();

        ord = c.getWrapper().createOrder(size, side, 0, 0, ordValidity, expireMinutes, false);
        c.getWrapper().modifyOrder(c, symbolID, orderID, side, ord, null);

    }

    private synchronized boolean updateFilledOrders(ConnectionBean c, int id, int orderID, int filled, double avgFillPrice) {
        OrderBean ord = c.getOrders().get(orderID);
        int fill=filled-ord.getFillSize();
        logger.log(Level.INFO, "Symbol: {0}, Fill Amount: {1},Reported by Event={2},Existing in Program={3}, Side={4}",new Object[]{ord.getSymbolID(),fill,filled,ord.getFillSize(),ord.getOrderSide()});
        ord.setFillSize(filled);
        ord.setFillPrice(avgFillPrice);
        ord.setStatus(OrderStatus.CompleteFilled);

        //update position
        int origposition = c.getPositions().get(id);
        if (c.getOrders().get(orderID).getOrderSide() == OrderSide.SELL || c.getOrders().get(orderID).getOrderSide() == OrderSide.SHORT) {
            fill = -fill;
            logger.log(Level.INFO,"Reversed fill sign as sell or short. Fill="+fill);
        }
        c.getPositions().put(id, origposition + fill);
        logger.log(Level.INFO, "Symbol: {0}, Current Position: {1}",new Object[]{id+1,origposition + fill});
        //update orderid=0 from Symbols HashMap
        ArrayList<Integer> orders = c.getOrdersSymbols().get(id);
        if (orders.size() > 0) {
            int count = 0;
            for (int i : orders) {
                if (i == orderID) {
                    orders.set(count, 0);
                    return true;
                }
                count = count + 1;
            }
        }
        return false;

    }

    private synchronized boolean updateCancelledOrders(ConnectionBean c, int id, int orderID) {
        OrderBean ord = c.getOrders().get(id);
        c.getOrders().get(orderID).setCancelRequested(false);
        if (ord.getFillSize() > 0) {
            ord.setStatus(OrderStatus.CancelledPartialFill);
        } else {
            ord.setStatus(OrderStatus.CancelledNoFill);
        }

        //Delete order from Symbols HashMap
        ArrayList<Integer> orders = c.getOrdersSymbols().get(id);
        if (orders.size() > 0) {
            for (int i : orders) {
                if (i == orderID) {
                    orders.remove(i);
                    return true;
                } else {
                    return false;
                }
            }
        }
        return false;

    }

    private synchronized boolean updatePartialFills(ConnectionBean c, int id, int orderID, int filled, double avgFillPrice) {
        OrderBean ord = c.getOrders().get(orderID);
        //identify incremental fill
        int fill=filled-ord.getFillSize();
        ord.setFillSize(filled);
        ord.setFillPrice(avgFillPrice);
        ord.setStatus(OrderStatus.PartialFilled);

        //update position
        int origposition = c.getPositions().get(id);
        if (c.getOrders().get(orderID).getOrderSide() == OrderSide.SELL || c.getOrders().get(orderID).getOrderSide() == OrderSide.SHORT ||c.getOrders().get(orderID).getOrderSide() == OrderSide.TRAILSELL) {
            fill = -fill;
        }

        c.getPositions().put(id, origposition + fill);
        return true;

    }

    private synchronized OrderSide reverseLookup(ConnectionBean c, ArrayList<Integer> arr, int orderid) {

        OrderSide ordSide = OrderSide.UNDEFINED;
        for (int i : arr) {
            if (i == orderid) {
                logger.log(Level.INFO,"In reverseLookup. Orderside={0},Array Position={1}",new Object[]{c.getOrders().get(orderid).getOrderSide(),i});
                ordSide=c.getOrders().get(orderid).getOrderSide();
                return ordSide;
            }
        }
        return ordSide;
    }

    private synchronized void updateAcknowledgement(ConnectionBean c, int id, int orderID) {
        OrderBean ord = c.getOrders().get(orderID);
        ord.setStatus(OrderStatus.Acknowledged);
    }
}
