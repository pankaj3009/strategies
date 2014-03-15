/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.algorithms.turtle;

import com.incurrency.framework.BeanPosition;
import com.incurrency.framework.Index;
import com.incurrency.framework.MainAlgorithm;
import com.incurrency.framework.Parameters;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Map;
import javax.swing.Timer;
import javax.swing.table.AbstractTableModel;

/**
 *
 * @author pankaj
 */
public class TableModelPNL extends AbstractTableModel{

    private String[] headers={"Strategy","PNL","MaxPNL","MinPNL"};
    int delay = 1000; //milliseconds
    MainAlgorithm m;
    int display=0;
     ActionListener taskPerformer = new ActionListener() {

        @Override
        public void actionPerformed(ActionEvent e) {
            fireTableDataChanged();
        }
    }; 

    public TableModelPNL(MainAlgorithm m) {
        new Timer(delay, taskPerformer).start();
        this.m=m;
        this.display=m.getParamTurtle().getDisplay();
    }
        
     public String getColumnName(int column) {
         return headers[column];
     }
 
  
    
    @Override
    public int getRowCount() {
       return m.getStrategies().size();
    }

    @Override
    public int getColumnCount() {
        return headers.length;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        
        
        switch (columnIndex) {
            case 0:
              return m.getStrategies().get(rowIndex);
            case 1:
                double pnl=0;
                for(Map.Entry<Index,BeanPosition> entry: Parameters.connection.get(display).getPositions().entrySet()){
                    if(entry.getKey().getStrategy().compareTo(m.getStrategies().get(rowIndex))==0 && entry.getValue().getPosition()>0 && Parameters.symbol.get(entry.getKey().getSymbolID()).getLastPrice()>0){
                        //System.out.println(Parameters.symbol.get(entry.getKey().getSymbolID()).getSymbol()+","+-entry.getValue().getPosition()*(entry.getValue().getPrice()-Parameters.symbol.get(entry.getKey().getSymbolID()).getLastPrice())+entry.getValue().getProfit());
                        pnl=pnl-entry.getValue().getPosition()*(entry.getValue().getPrice()-Parameters.symbol.get(entry.getKey().getSymbolID()).getLastPrice())+entry.getValue().getProfit();
                    } else if(entry.getKey().getStrategy().compareTo(m.getStrategies().get(rowIndex))==0 && entry.getValue().getPosition()<0 && Parameters.symbol.get(entry.getKey().getSymbolID()).getLastPrice()>0){
                     //System.out.println(Parameters.symbol.get(entry.getKey().getSymbolID()).getSymbol()+","+entry.getValue().getPosition()*(entry.getValue().getPrice()-Parameters.symbol.get(entry.getKey().getSymbolID()).getLastPrice())+entry.getValue().getProfit());
                        pnl=pnl- entry.getValue().getPosition()*(entry.getValue().getPrice()-Parameters.symbol.get(entry.getKey().getSymbolID()).getLastPrice())+entry.getValue().getProfit();
                    } else if(entry.getKey().getStrategy().compareTo(m.getStrategies().get(rowIndex))==0 && entry.getValue().getPosition()==0){
                     //System.out.println(Parameters.symbol.get(entry.getKey().getSymbolID()).getSymbol()+","+entry.getValue().getProfit());
                    
                        pnl=pnl+entry.getValue().getProfit();
                    }
                }
                return ((int)Math.round(pnl*100))/100;
                
            case 2:
                return ((int)Math.round(m.getMaxPNL().get(rowIndex)*100))/100;

            case 3:
                return ((int)Math.round(m.getMinPNL().get(rowIndex)*100))/100;
            default:
                throw new IndexOutOfBoundsException(); 
                
    }
        
        
        
    }
    
    
}
