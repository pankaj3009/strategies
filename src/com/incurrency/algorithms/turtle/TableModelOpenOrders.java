/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.algorithms.turtle;

import com.incurrency.framework.MainAlgorithm;
import com.incurrency.framework.OrderBean;
import com.incurrency.framework.Parameters;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;
import javax.swing.Timer;
import javax.swing.table.AbstractTableModel;

/**
 *
 * @author pankaj
 */
public class TableModelOpenOrders extends AbstractTableModel{

    private String[] headers={"Strategy","Symbol","OrderID","Side","Size","OrderPrice","Market"};
    int delay = 1000; //milliseconds
    MainAlgorithm m;
    int display=0;
    private HashMap <Integer,OrderBean> openOrders= new HashMap <Integer,OrderBean>();
    
     ActionListener taskPerformer = new ActionListener() {

        @Override
        public void actionPerformed(ActionEvent e) {
            //create a subset of orders that are open
            /*
             openOrders.clear();
             for (Map.Entry<Integer,OrderBean> orders : Parameters.connection.get(m.getParam().getDisplay()).getOrders().entrySet()){
                 if(orders.getValue().getOrderType()!=EnumOrderType.Trail && (orders.getValue().getStatus()==EnumOrderStatus.CancelledNoFill  
                         ||orders.getValue().getStatus()==EnumOrderStatus.CancelledPartialFill)){
                     openOrders.put(orders.getKey(), orders.getValue());
                 }
             }
             */
            fireTableDataChanged();
        }
    }; 

    public TableModelOpenOrders(MainAlgorithm m) {
        new Timer(delay, taskPerformer).start();
        this.m=m;
        this.display=m.getParamTurtle().getDisplay();
    }
        
     public String getColumnName(int column) {
         return headers[column];
     }
 
  
    
    @Override
    public int getRowCount() {
       return Parameters.connection.get(display).getOrdersInProgress().size();
               }

    @Override
    public int getColumnCount() {
        return headers.length;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
       int orderid=Parameters.connection.get(display).getOrdersInProgress().get(rowIndex);
       int id=Parameters.connection.get(display).getOrders().get(orderid).getSymbolID()-1;
        switch (columnIndex) {
            case 0:
                
                return Parameters.connection.get(display).getOrders().get(orderid).getOrderReference();        
            case 1:
                return Parameters.symbol.get(id).getSymbol();
            case 2:
                return Parameters.connection.get(display).getOrders().get(orderid).getOrderID();
            case 3:
                return Parameters.connection.get(display).getOrders().get(orderid).getOrderSide();
            case 4:
                return Parameters.connection.get(display).getOrders().get(orderid).getOrderSize();   
            case 5: 
                return Parameters.connection.get(display).getOrders().get(orderid).getLimitPrice();   
            case 6: 
                return Parameters.symbol.get(id).getLastPrice();            
            default:
                throw new IndexOutOfBoundsException(); 
    }        
    }
}
