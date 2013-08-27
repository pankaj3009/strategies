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
        int id=event.getSymbolBean().getSerialno();
        Boolean square= null;
        for(ConnectionBean c: Parameters.connection){
            if(c.getPurpose()=="Trading"){
                if(event.getSide()=="BUY"){
                    //check if system is square
                    if(c.getOrders().get(id).getPositionsize()==0){
                        //confirm current active order id is complete else cleanup orders
                        //place orders to buy
                    }
                    else if(c.getOrders().get(id).getPositionsize()<0){
                        //occurs if there was a stop and reverse. Ideally, the stop would have thrown the first event.
                        
                    }
                        
                        
                    
                }
                int orderid=c.getIdmanager().getNextOrderId();
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
    
    private synchronized boolean updateCancelledOrders(ConnectionBean c,int symbolID, int orderID ){
         OrderBean ord=c.getOrders().get(symbolID);
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
