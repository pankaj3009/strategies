/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package TurtleTrading;

import com.ib.client.Contract;
import com.ib.client.Order;
import incurrframework.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

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


    }

    @Override
    public void orderReceived(OrderEvent event) {
        //for each connection eligible for trading
       // System.out.println(Thread.currentThread().getName());
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
                            c.getWrapper().placeOrder(c, id+1, OrderSide.BUY, ord, con);
                        } //else cancel open orders
                        else {
                            cancelOpenOrders(c, id);
                            //Introduce Logic: Place orders when cancelation is successful
                        }
                    } else if (event.getSide() == OrderSide.SELL || event.getSide() == OrderSide.COVER) {
                        //do nothing as position size=0. Original orders were not filled. Can open orders if any exist
                        cancelOpenOrders(c, id); 
                    }
                
                //next loop processes when positions !=0
                } else if (c.getOrders().get(id).getPositionsize() != 0) {
                    if (event.getSide() == OrderSide.SELL || event.getSide() == OrderSide.COVER) {
                        cancelOpenOrders(c, id); //as this is a squareoff condition, first cancel all open orders
                        if ((event.getSide() == OrderSide.SELL && c.getPositions().get(id) > 0) || (event.getSide() == OrderSide.COVER && c.getPositions().get(id) < 0)) {
                            Order ord = c.getWrapper().createOrder(c.getPositions().get(id), event.getSide(), event.getLimitPrice(), event.getTriggerPrice(), "DAY", 3, false);
                            Contract con = c.getWrapper().createContract(id);
                            c.getWrapper().placeOrder(c, id+1, event.getSide(), ord, con);
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

    @Override
    public void orderStatusReceived(OrderStatusEvent event) {
        int orderid = event.getOrderID();

        //update HashMap orders
        OrderBean ob = event.getC().getOrders().get(orderid);
        if (ob != null) {
            int id = ob.getSymbolID()-1;
            if (event.getRemaining()==0) {
                //completely filled
                OrderSide tmpOrdSide = reverseLookup(event.getC().getOrdersSymbols().get(id), orderid);
                updateFilledOrders(event.getC(), id, orderid, event.getFilled(), event.getAvgFillPrice());
                //Reverse lookup on OrderSymbols to get the orderside for the fill
                    if (tmpOrdSide == OrderSide.BUY || tmpOrdSide == OrderSide.SHORT) {
                    logger.log(Level.INFO,"OrderSide="+tmpOrdSide+" Position="+ event.getC().getPositions().get(id));
                    OrderSide tmpOrderSide = event.getC().getPositions().get(id) > 0 ? OrderSide.TRAILSELL : OrderSide.TRAILBUY;
                    double tmpTrailStop = ((int) (Parameters.symbol.get(id).getTrailstop() * event.getAvgFillPrice() / 0.05)) * 0.05;
                    Order ord = event.getC().getWrapper().createOrder(event.getC().getPositions().get(id), tmpOrderSide, 0, tmpTrailStop, "DAY", 0, true);
                    Contract con = event.getC().getWrapper().createContract(id);
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

    private synchronized void squareAllPositions(ConnectionBean c, int id) {
        int position = c.getPositions().get(id);
        Contract con = c.getWrapper().createContract(id);
        if (position > 0) {
            Order ord = c.getWrapper().createOrder(position, OrderSide.SELL, 0, 0, "DAY", 0, false);
            c.getWrapper().placeOrder(c, id+1, OrderSide.SELL, ord, con);
        } else if (position < 0) {
            Order ord = c.getWrapper().createOrder(-position, OrderSide.COVER, 0, 0, "DAY", 0, false);
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
        ord.setFillSize(filled);
        ord.setFillPrice(avgFillPrice);
        ord.setStatus(OrderStatus.CompleteFilled);

        //update position
        int origposition = c.getPositions().get(id);
        if (c.getOrders().get(orderID).getOrderSide() == OrderSide.SELL || c.getOrders().get(orderID).getOrderSide() == OrderSide.SHORT) {
            fill = -fill;
        }
        c.getPositions().put(id, origposition + fill);

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

    private synchronized OrderSide reverseLookup(ArrayList<Integer> arr, int orderid) {

        int side = 0;
        OrderSide ordSide = OrderSide.UNDEFINED;
        for (int i : arr) {
            if (i == orderid) {
                switch (side) {
                    case 0:
                        ordSide = OrderSide.BUY;
                        break;
                    case 1:
                        ordSide = OrderSide.SELL;
                        break;
                    case 2:
                        ordSide = OrderSide.SHORT;
                        break;
                    case 3:
                        ordSide = OrderSide.COVER;
                        break;
                    case 4:
                        ordSide = OrderSide.TRAILBUY;
                        break;
                    case 5:
                        ordSide = OrderSide.TRAILSELL;
                        break;
                }
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
