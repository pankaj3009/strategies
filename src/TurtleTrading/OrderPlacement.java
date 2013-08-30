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

/**
 *
 * @author admin
 */
public class OrderPlacement implements OrderListener, OrderStatusListener {

    private NewSwingAlgorithm a;
    
public OrderPlacement (NewSwingAlgorithm o){
    a=o;
}
    @Override
    public void orderReceived(OrderEvent event) {
        //for each connection eligible for trading
        int id = event.getSymbolBean().getSerialno();
        Boolean square = null;
        for (ConnectionBean c : Parameters.connection) {
            if (c.getPurpose() == "Trading") {
                    //check if system is square && order id is to initiate
                    if (c.getOrders().get(id).getPositionsize() == 0){
                        if (event.getSide()==OrderSide.BUY||event.getSide()==OrderSide.SHORT) {
                        //confirm current active order id is complete 
                        if (zilchOpenOrders(c, id)) {
                            Order ord = c.getWrapper().createOrder(event.getOrderSize(), event.getSide(), event.getLimitPrice(), event.getTriggerPrice(), "DAY", 180000);
                            Contract con=c.getWrapper().createContract(id);
                            c.getWrapper().placeOrder(c, id, OrderSide.BUY, ord, con);
                        }
                        //else cancel open orders
                        {
                            cancelOpenOrders(c, id);
                        }
                    } else if (event.getSide()==OrderSide.SELL||event.getSide()==OrderSide.COVER){
                        //do nothing as position size=0. Can open orders if any exist
                        cancelOpenOrders(c, id);
                    }
                    }
                        else if (c.getOrders().get(id).getPositionsize() != 0) {
                            if (event.getSide()==OrderSide.SELL||event.getSide()==OrderSide.COVER){
                                cancelOpenOrders(c,id);
                                if((event.getSide()==OrderSide.SELL && c.getPositions().get(id)>0) ||(event.getSide()==OrderSide.COVER && c.getPositions().get(id)<0)){
                                Order ord = c.getWrapper().createOrder(c.getPositions().get(id), event.getSide(), event.getLimitPrice(), event.getTriggerPrice(), "DAY", 180000);
                                Contract con=c.getWrapper().createContract(id);
                                c.getWrapper().placeOrder(c, id, event.getSide(), ord, con);
                                }
                                else
                                {
                                    //something is wrong in positions. Cancel all open orders and square all positions
                                    cancelOpenOrders(c,id);
                                Order ord = c.getWrapper().createOrder(c.getPositions().get(id), event.getSide(), event.getLimitPrice(), event.getTriggerPrice(), "DAY", 180000);
                                Contract con=c.getWrapper().createContract(id);
                                if(c.getPositions().get(id)>0){
                                 c.getWrapper().placeOrder(c, id, OrderSide.SELL, ord, con);
                                    }
                                    else if(c.getPositions().get(id)<0){
                                         c.getWrapper().placeOrder(c, id, OrderSide.COVER, ord, con);
                                    }
                                }
                            }
                            else if(event.getSide()==OrderSide.SELL||event.getSide()==OrderSide.COVER){
                                
                                
                            }
                            
                            
                        int tempOrderID = c.getOrdersSymbols().get(id).get(3);
                        if (tempOrderID > 0) {
                            fastClose(c, id, OrderSide.COVER, tempOrderID);
                        }

                    }
                    }
                }
            }
    
    
    
    private synchronized boolean squarePosition(ConnectionBean c, int id){
        boolean temp = ((c.getPositions().get(id)== 0) ? true : false);
        return temp;
    }
    
    private synchronized boolean zilchOpenOrders(ConnectionBean c, int id){
        boolean temp = ((c.getOrdersSymbols().get(id).size()== 0) ? true : false);
        return temp;
    }
    
    private synchronized void cancelOpenOrders (ConnectionBean c, int id){
        
        ArrayList<Integer> tempArray=c.getOrdersSymbols().get(id);
        int ordSide=0;
        OrderSide side=OrderSide.UNDEFINED;
        for(int orderID: tempArray){
            if(orderID>0 && (ordSide==0||ordSide==2)){
                //if order exists and open ordType="Buy" or "Short"
                //check an earlier cancellation request is not pending and if all ok then cancel
                if(!c.getOrders().get(orderID).isCancelRequested()){
                c.getWrapper().cancelOrder(c, orderID);
                }
            }
            if(orderID>0 && (ordSide==1||ordSide==3)){
                //if order exists and open ordType="Sell" or "Cover"
                switch(ordSide){
                    case 1: side=OrderSide.SELL;
                    case 3: side=OrderSide.COVER;
                    default: side=OrderSide.UNDEFINED;
                }
                fastClose(c, id, side,orderID);
            }
            ordSide=ordSide+1;
            
        }
    } 
    
    private synchronized void fastClose(ConnectionBean c, int symbolID, OrderSide side, int orderID){
        //Check if order is already market. If yes, return
        if(c.getOrders().get(orderID).getOrderType()==OrderType.Market){
        return;
        }
        
        //amend order to market
        Order ord=new Order();
        String ordValidity="DAY";
        int expireMinutes=0;
       
        int size=Parameters.symbol.get(symbolID).getMinsize();

        ord=c.getWrapper().createOrder(size, side, 0,0, ordValidity, expireMinutes);
        c.getWrapper().modifyOrder(c, symbolID, orderID, side, ord, null);
        
        }
      
    private synchronized boolean updateFilledOrders(ConnectionBean c,int symbolID, int orderID, int filled,double avgFillPrice){
        OrderBean ord=c.getOrders().get(symbolID);
        ord.setFillSize(filled);
        ord.setFillPrice(avgFillPrice);
        ord.setStatus(OrderStatus.CompleteFilled);
        
        //update position
        int origposition=c.getPositions().get(symbolID);
        if(c.getOrders().get(symbolID).getOrderSide()==OrderSide.SELL||c.getOrders().get(symbolID).getOrderSide()==OrderSide.SHORT){
            filled=-filled;
        }
        c.getPositions().put(symbolID, origposition+filled);
        
        //Delete order from Symbols HashMap
        ArrayList <Integer> orders= c.getOrdersSymbols().get(symbolID);
        if(orders.size()>0){
            int count=0;
            for(int i: orders){
                if(i==orderID){
                    orders.set(count, 0);
                    return true;
                    }
                count=count+1;
            }
        }
        return false;

    }
    
    private synchronized boolean updateCancelledOrders(ConnectionBean c,int symbolID, int orderID ){
         OrderBean ord=c.getOrders().get(symbolID);
         c.getOrders().get(orderID).setCancelRequested(false);
         if(ord.getFillSize()>0)
         ord.setStatus(OrderStatus.CancelledPartialFill);
         else 
             ord.setStatus(OrderStatus.CancelledNoFill);
         
         //Delete order from Symbols HashMap
        ArrayList <Integer> orders= c.getOrdersSymbols().get(symbolID);
        if(orders.size()>0){
            for(int i: orders){
                if(i==orderID){
                    orders.remove(i);
                    return true;
                    }
                else return false;
            }
        }
        return false;

    }
    
    private synchronized boolean updatePartialFills(ConnectionBean c,int symbolID, int orderID, int filled,double avgFillPrice){
        OrderBean ord=c.getOrders().get(symbolID);
        ord.setFillSize(filled);
        ord.setFillPrice(avgFillPrice);
        ord.setStatus(OrderStatus.PartialFilled);
        
        //update position
        int origposition=c.getPositions().get(symbolID);
        if(c.getOrders().get(symbolID).getOrderSide()==OrderSide.SELL||c.getOrders().get(symbolID).getOrderSide()==OrderSide.SHORT){
            filled=-filled;
        }
        c.getPositions().put(symbolID, origposition+filled);
        return true;
        
    }

    private synchronized void placeTrailingStops(){
        
    }
    
    @Override
    public void orderStatusReceived(OrderStatusEvent event) {
       int orderid=event.getOrderID();
          
       //update HashMap orders
       OrderBean ob=event.getC().getOrders().get(orderid);
       int symbolid=ob.getSymbolID();
      
       if(ob.getFillSize()==ob.getOrderSize()){
           //completely filled
           updateFilledOrders(event.getC(), symbolid, orderid,event.getFilled(),event.getAvgFillPrice());
           placeTrailingStops();
       } else if (ob.getFillSize()<ob.getOrderSize() && ob.getFillSize()>0){
           // partial fill
           updatePartialFills(event.getC(),symbolid, orderid,event.getFilled(),event.getAvgFillPrice());
       } else if (event.getStatus()=="Canceled") {
           //cancelled
           updateCancelledOrders(event.getC(),symbolid,orderid );
       }
    }
}
