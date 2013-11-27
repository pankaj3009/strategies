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
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.Timer;

/**
 *
 * @author admin
 */
public class OrderPlacement implements OrderListener, OrderStatusListener,TWSErrorListener {

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
            if (event.getOrderIntent() == EnumOrderIntent.Init) {
                int id = event.getSymbolBean().getSerialno() - 1;
                logger.log(Level.FINEST, "OrderReceived. Symbol:{0}, OrderSide:{1}", new Object[]{Parameters.symbol.get(id).getSymbol(), event.getSide()});
                for (BeanConnection c : Parameters.connection) {
                    if ("Trading".equals(c.getPurpose()) && c.getStrategy().contains(event.getOrdReference())) {
                        //check if system is square && order id is to initiate
                        Index ind = new Index(event.getOrdReference(), id);
                        int positions = c.getPositions().get(ind) == null ? 0 : c.getPositions().get(ind).getPosition();
                        if (positions == 0) {
                            if (event.getSide() == EnumOrderSide.BUY || event.getSide() == EnumOrderSide.SHORT) {
                                //confirm current active order id is complete 
                                if (zilchOpenOrders(c, id, event.getOrdReference())) {
                                    Order ord = c.getWrapper().createOrder(event.getOrderSize(), event.getSide(), event.getLimitPrice(), event.getTriggerPrice(), "DAY", event.getExpireTime(), false, event.getOrdReference(), "");
                                    Contract con = c.getWrapper().createContract(id);
                                    logger.log(Level.INFO, "Method:{0},Action:Entry Order Placed, Symbol:{1}, Size={2}", new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(), Parameters.symbol.get(id).getSymbol(), event.getOrderSize()});
                                    int orderid = c.getWrapper().placeOrder(c, id + 1, event.getSide(), ord, con, event.getExitType());
                                    long tempexpire = System.currentTimeMillis() + event.getExpireTime() * 60 * 1000;
                                    c.getOrdersToBeCancelled().put(orderid, tempexpire);
                                    logger.log(Level.FINEST, "Expiration time in object getOrdersToBeCancelled=" + DateUtil.getFormatedDate("yyyyMMdd HH:mm:ss", tempexpire));
                                } //else cancel open orders
                                else {
                                    logger.log(Level.INFO, "Method:{0},Action:Open Orders Being Cancelled before Entry, Symbol:{1}, Side={2}", new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(), Parameters.symbol.get(id).getSymbol(), event.getOrderSize()});
                                    cancelOpenOrders(c, id, event.getOrdReference());
                                    logger.log(Level.INFO, "Method:{0},Entry order received while orders were still active with zero position.Symbol:{1}", new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(), Parameters.symbol.get(id).getSymbol()});
                                    //Introduce Logic: Place orders when cancelation is successful
                                    OrderBean ord = new OrderBean();
                                    ord.setSymbolID(id + 1);
                                    ord.setOrderSize(event.getOrderSize());
                                    ord.setOrderSide(event.getSide());
                                    ord.setLimitPrice(event.getLimitPrice());
                                    ord.setTriggerPrice(event.getTriggerPrice());
                                    ord.setExpireTime(Integer.toString(event.getExpireTime()));
                                    c.getOrdersToBeRetried().put(System.currentTimeMillis(), ord);
                                }
                            } else if (event.getSide() == EnumOrderSide.SELL || event.getSide() == EnumOrderSide.COVER) {
                                //do nothing as position size=0. Original orders were not filled. 
                                logger.log(Level.INFO, "Method:{0},Action:No Entry Position. Cancel any open orders, Symbol:{1}, Side={2}", new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(), Parameters.symbol.get(id).getSymbol(), event.getOrderSize()});
                                //update missedorders list
                                //get orderid
                               int orderid = c.getOrdersSymbols().get(ind).get(0) > 0 ? c.getOrdersSymbols().get(ind).get(0) : c.getOrdersSymbols().get(ind).get(2);
                                
                               // int orderid = c.getOrdersSymbols().get(ind).get(0) > 0 ? c.getOrdersSymbols().get(ind).get(0) : c.getOrdersSymbols().get(ind).get(2);
                                //logger.log(Level.INFO, "Method:{0},Action:Store orderid for removal from missed orders as exit order reported, Symbol:{1}, OrderID={2}", new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(), Parameters.symbol.get(id).getSymbol(), orderid});
                                if (c.getOrdersMissed().contains(new Integer(orderid))) {
                                    c.getOrdersMissed().remove(new Integer(orderid));
                                    logger.log(Level.INFO, "Method:{0},Action:Remove from missed orders as exit order reported, Symbol:{1}, OrderID={2}", new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(), Parameters.symbol.get(id).getSymbol(), orderid});
                                }
                                cancelOpenOrders(c, id, event.getOrdReference());

                            }

                            //next loop processes when positions !=0
                        } else if (positions != 0) {
                            if (event.getSide() == EnumOrderSide.SELL || event.getSide() == EnumOrderSide.COVER) {
                                cancelOpenOrders(c, id, event.getOrdReference()); //as this is a squareoff condition, first cancel all open orders
                                if ((event.getSide() == EnumOrderSide.SELL && c.getPositions().get(ind).getPosition() > 0) || (event.getSide() == EnumOrderSide.COVER && c.getPositions().get(ind).getPosition() < 0)) {
                                    Order ord = c.getWrapper().createOrder(Math.abs(c.getPositions().get(ind).getPosition()), event.getSide(), event.getLimitPrice(), event.getTriggerPrice(), "DAY", event.getExpireTime(), false, event.getOrdReference(), "");
                                    Contract con = c.getWrapper().createContract(id);
                                    logger.log(Level.INFO, "Method:{0},Action:Exit Position, Symbol:{1}, Side={2}, position:{3}", new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(), Parameters.symbol.get(id).getSymbol(), event.getOrderSize(), positions});
                                    int orderid = c.getWrapper().placeOrder(c, id + 1, event.getSide(), ord, con, event.getExitType());
                                    c.getOrdersToBeFastTracked().put(orderid, System.currentTimeMillis() + event.getExpireTime() * 60 * 1000);
                                } else {
                                    //something is wrong in positions. Cancel all open orders and square all positions
                                    cancelOpenOrders(c, id, event.getOrdReference());
                                    squareAllPositions(c, id, event.getOrdReference());
                                    logger.log(Level.INFO, "Method:{0},Action: Positions are inconsistent with state of orders. Square off positions, Symbol:{1}, Side:{2}, Positions:{3}", new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(), Parameters.symbol.get(id).getSymbol(), event.getOrderSize(), positions});
                                }
                            } else if (event.getSide() == EnumOrderSide.BUY || event.getSide() == EnumOrderSide.SHORT) {
                                //This will happen if an earlier squareoff order is still not filled and we get a new entry
                                logger.log(Level.INFO, "Method:{0},Entry order received while position was not zero.Symbol:{1}", new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(), Parameters.symbol.get(id).getSymbol()});
                                cancelOpenOrders(c, id, event.getOrdReference());
                                squareAllPositions(c, id, event.getOrdReference());
                                //Introduce Logic: Place orders when cancelation is successful

                                OrderBean ord = new OrderBean();
                                ord.setSymbolID(id + 1);
                                ord.setOrderSize(event.getOrderSize());
                                ord.setOrderSide(event.getSide());
                                ord.setLimitPrice(event.getLimitPrice());
                                ord.setTriggerPrice(event.getTriggerPrice());
                                ord.setExpireTime(Integer.toString(event.getExpireTime()));
                                ord.setExitLogic(event.getExitType());
                                ord.setOrderReference(event.getOrdReference());
                                c.getOrdersToBeRetried().put(System.currentTimeMillis(), ord);
                                logger.log(Level.INFO, "Method:{0},Added requirement for OrdersToBeRetried for symbol{1}", new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(), Parameters.symbol.get(id).getSymbol()});
                            }
                        }
                    }
                }
            } else if (event.getOrderIntent() == EnumOrderIntent.Amend) {
                int id = event.getSymbolBean().getSerialno() - 1;
                logger.log(Level.FINEST, "OrderReceived. Symbol:{0}, OrderSide:{1}", new Object[]{Parameters.symbol.get(id).getSymbol(), event.getSide()});
                for (BeanConnection c : Parameters.connection) {
                    if ("Trading".equals(c.getPurpose()) && c.getStrategy().contains(event.getOrdReference())) {
                        //check if system is square && order id is to initiate
                        Index ind = new Index(event.getOrdReference(), id);
                        int positions = c.getPositions().get(ind) == null ? 0 : c.getPositions().get(ind).getPosition();
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
                            if (event.getExpireTime() > 0) {
                                long expirationTimeMS = System.currentTimeMillis() + event.getExpireTime() * 60 * 1000;
                                //String expirationTime = DateUtil.getFormatedDate("yyyyMMdd HH:mm:ss z", expirationTimeMS);
                                String expirationTime = DateUtil.getFormatedDate("yyyyMMdd HH:mm:ss", expirationTimeMS);
                                ord.m_goodTillDate = expirationTime;
                                if (event.getExpireTime() != 0) {
                                    ord.m_tif = "GTD";
                                } else {
                                    ord.m_tif = "DAY";
                                }

                            }
                        } else
                        {
                            //no order to amend. Do nothing. Probably earlier order was filled. Write to log
                            logger.log(Level.INFO, "No orders to amend Method:{0}, Symbol:{1}, Order Side:{2}, position:{3}", new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(), Parameters.symbol.get(id).getSymbol(),event.getSide(),positions});
                            
                        }
                    }
                }

            } else if (event.getOrderIntent() == EnumOrderIntent.Cancel) {
                int id = event.getSymbolBean().getSerialno() - 1;
                logger.log(Level.FINEST, "OrderReceived. Symbol:{0}, OrderSide:{1}", new Object[]{Parameters.symbol.get(id).getSymbol(), event.getSide()});
                for (BeanConnection c : Parameters.connection) {
                    if ("Trading".equals(c.getPurpose()) && c.getStrategy().contains(event.getOrdReference())) {
                        //check if system is square && order id is to initiate
                        Index ind = new Index(event.getOrdReference(), id);
                        int positions = c.getPositions().get(ind) == null ? 0 : c.getPositions().get(ind).getPosition();
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
                Index ind = new Index(ob.getOrderReference(), id);
                if (event.getRemaining() == 0) {
                    //completely filled
                    EnumOrderSide tmpOrdSide = reverseLookup(event.getC(), event.getC().getOrdersSymbols().get(ind), orderid);
                    updateFilledOrders(event.getC(), id, orderid, event.getFilled(), event.getAvgFillPrice(), event.getLastFillPrice());
                    event.getC().getOrdersInProgress().remove(new Integer(orderid));
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
                    } else{
                        updatePartialFills(event.getC(), id, orderid, event.getFilled(), event.getAvgFillPrice(), event.getLastFillPrice());
                    }
                } else if ("Cancelled".equals(event.getStatus())) {
                    //cancelled
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
                            logger.log(Level.INFO, "cancelExpiredOrders Method:{0}, Symbol:{1}, OrderID:{2}", new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(), Parameters.symbol.get(c.getOrders().get(key).getSymbolID() - 1).getSymbol(), key});
                            //logger.log(Level.INFO,"Expired Order being cancelled. OrderID="+key);
                            c.getWrapper().cancelOrder(c, key);
                            if ((c.getOrders().get(key).getOrderSide() == EnumOrderSide.BUY || c.getOrders().get(key).getOrderSide() == EnumOrderSide.SHORT)
                                    && (c.getOrders().get(key).getStatus() == EnumOrderStatus.CancelledNoFill || c.getOrders().get(key).getStatus() == EnumOrderStatus.CancelledPartialFill)) {
                                c.getOrdersMissed().add(key);
                            }
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
                            logger.log(Level.INFO, "hastenCloseOut Method:{0}, Symbol:{1}, OrderID:{2}", new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(), Parameters.symbol.get(c.getOrders().get(key).getSymbolID() - 1).getSymbol(), key});
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
                        Index ind = new Index(ordb.getOrderReference(), tempID);
                        if (System.currentTimeMillis() > key + 60 * 1000) {
                            temp.add(key); //if > 60 seconds have passed then add the key to temp. This record will be deleted from orders to be retried queue.
                        } else if (c.getPositions().containsKey(ind) && c.getPositions().get(ind) != null) {
                            if( c.getPositions().get(ind).getPosition() == 0 && zilchOpenOrders(c, tempID, ordb.getOrderReference())) {
                            //update temp
                            temp.add(key);
                            //place orders
                            Order ord = c.getWrapper().createOrder(ordb.getOrderSize(), ordb.getOrderSide(), ordb.getLimitPrice(), ordb.getTriggerPrice(), "DAY", Integer.parseInt(ordb.getExpireTime()), false, ordb.getOrderReference(), "");
                            Contract con = c.getWrapper().createContract(tempID);
                            logger.log(Level.INFO, "reattemptOrders Method:{0}, Symbol:{1}, OrderID:{2}", new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(), Parameters.symbol.get(ordb.getSymbolID() - 1).getSymbol(), key});
                            c.getWrapper().placeOrder(c, tempID + 1, ordb.getOrderSide(), ord, con, ordb.getExitLogic());
                        }
                    }
                    }
                    for (long ordersToBeDeleted : temp) {
                        logger.log(Level.INFO, "Symbol Deleted from retry attempt. Method:{0}, Symbol:{1}", new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(), Parameters.symbol.get(c.getOrdersToBeRetried().get(ordersToBeDeleted).getSymbolID() - 1).getSymbol()});
                        c.getOrdersToBeRetried().remove(ordersToBeDeleted);
                    }

                }
            }
        }
    };

    private synchronized void manageTrailingOrders(BeanConnection c, int orderID, int id, int size, EnumOrderSide underlyingSide, double fillprice, String ordReference) {
        if (orderID > 0) {
            //int tempid=c.getOrders().get(orderID).getSymbolID()-1;
            updateOrderAmount(c, orderID, size);
        } else {
            //place new trailbuy or trailsell order
            EnumOrderSide tmpOrderSide = underlyingSide == EnumOrderSide.BUY ? EnumOrderSide.TRAILSELL : EnumOrderSide.TRAILBUY;
            double tickSize = Double.parseDouble(a.getParamTurtle().getTickSize());
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

    private synchronized void squareAllPositions(BeanConnection c, int id, String strategy) {
        Index ind = new Index(strategy, id);
        int position = c.getPositions().get(ind).getPosition();
        Contract con = c.getWrapper().createContract(id);
        Order ord = new Order();
        if (position > 0) {
            ord = c.getWrapper().createOrder(Math.abs(position), EnumOrderSide.SELL, 0, 0, "DAY", 0, false, strategy, "");
            logger.log(Level.INFO, "Order Placed. Square on Error. Symbol ID={0}", new Object[]{Parameters.symbol.get(id).getSymbol()});
            c.getWrapper().placeOrder(c, id + 1, EnumOrderSide.SELL, ord, con, "");
        } else if (position < 0) {
            ord = c.getWrapper().createOrder(Math.abs(position), EnumOrderSide.COVER, 0, 0, "DAY", 0, false, strategy, "");
            logger.log(Level.INFO, "Order Placed. Square on Error. Symbol ID={0}", new Object[]{Parameters.symbol.get(id + 1).getSymbol()});
            c.getWrapper().placeOrder(c, id + 1, EnumOrderSide.COVER, ord, con, "");
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

    private synchronized void cancelOpenOrders(BeanConnection c, int id, String strategy) {
        Index ind = new Index(strategy, id);
        ArrayList<Integer> tempArray = c.getOrdersSymbols().get(ind);
        ArrayList<Integer> temp = new ArrayList();
        for (int orderID : tempArray) {
            if (orderID > 0) {
                //if order exists and open ordType="Buy" or "Short"
                //check an earlier cancellation request is not pending and if all ok then cancel
                for (Integer key : c.getOrdersToBeFastTracked().keySet()){
                    if (key==orderID){
                        temp.add(key);
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
            logger.log(Level.FINEST, "Reversed fill sign as sell or short. Fill=" + fill);
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
        if(event.getErrorCode()==201){
            int id=event.getConnection().getOrders().get(event.getId()).getSymbolID()-1;
            logger.log(Level.INFO, "Method:{0},Symbol:{1}, ErrorCode:{2},OrderID={3},Message={4}", new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(), Parameters.symbol.get(id).getSymbol(), event.getErrorCode(), event.getId(), event.getErrorMessage()});
            this.updateCancelledOrders(event.getConnection(), id, event.getId());
        }
    }
}
