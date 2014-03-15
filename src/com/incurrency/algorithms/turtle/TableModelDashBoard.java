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
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Map;
import javax.swing.Timer;
import javax.swing.table.AbstractTableModel;

/**
 *
 * @author pankaj
 */
public class TableModelDashBoard extends AbstractTableModel{

//    private String[] headers={"Symbol","Position","PositionPrice","P&L","HH","LL","Market","CumVol","Slope","20PerThreshold","Volume","MA","Strategy"};
    private String[] headers={"Symbol","Position","PositionPrice","P&L","LL","Market","HH","Strategy"};

    int delay = 1000; //milliseconds
    MainAlgorithm m;
    int display=0;
    NumberFormat df = DecimalFormat.getInstance();
    
     ActionListener taskPerformer = new ActionListener() {

        @Override
        public void actionPerformed(ActionEvent e) {
            fireTableDataChanged();
        }
    }; 

    public TableModelDashBoard(MainAlgorithm m) {
        new Timer(delay, taskPerformer).start();
        this.m=m;
        this.display=m.getParamTurtle().getDisplay();
        df.setMinimumFractionDigits(2);
        df.setMaximumFractionDigits(4);
        df.setRoundingMode(RoundingMode.DOWN);
    }
        
     public String getColumnName(int column) {
         return headers[column];
     }
 
  
    
    @Override
    public int getRowCount() {
//       return Parameters.connection.get(display).getPositions().size();
        return Parameters.symbol.size();
               }

    @Override
    public int getColumnCount() {
        return headers.length;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        Index ind=new Index("idt",rowIndex);
        /* This section displays only rows with positions
        Set<Index> keys = Parameters.connection.get(display).getPositions().keySet();
        //tempArray=new Index[keys.size()];
        Index[] tempArray=keys.toArray(new Index[keys.size()]);
        Index ind=tempArray[rowIndex];
        */
        int strategyid=0;
        String strategy=ind.getStrategy();
        for (int i = 0; i < m.getStrategies().size(); i++) {
            if(m.getStrategies().get(i).compareTo(strategy)==0){
            strategyid=i;
        }
    }
        
        switch (columnIndex) {
            case 0:
              return Parameters.symbol.get(ind.getSymbolID()).getSymbol();
            case 1:
                return Parameters.connection.get(display)==null || Parameters.connection.get(display).getPositions()==null||Parameters.connection.get(display).getPositions().get(ind)==null ?0:Parameters.connection.get(display).getPositions().get(ind).getPosition();
            case 2:
                return df.format(Parameters.connection.get(display)==null || Parameters.connection.get(display).getPositions()==null||Parameters.connection.get(display).getPositions().get(ind)==null ?0:Parameters.connection.get(display).getPositions().get(ind).getPrice());
            case 3:
                double pnl=0;
                //calculate max, min pnl
                if(!(Parameters.connection.get(display)==null || Parameters.connection.get(display).getPositions()==null||Parameters.connection.get(display).getPositions().get(ind)==null )){
                for(Map.Entry<Index,BeanPosition> entry: Parameters.connection.get(display).getPositions().entrySet()){
                    if(entry.getKey().getStrategy().compareTo(m.getStrategies().get(strategyid))==0 && entry.getValue().getPosition()>0 && Parameters.symbol.get(entry.getKey().getSymbolID()).getLastPrice()>0){
                        pnl=pnl-entry.getValue().getPosition()*(entry.getValue().getPrice()-Parameters.symbol.get(entry.getKey().getSymbolID()).getLastPrice())+entry.getValue().getProfit();
                    } else if(entry.getKey().getStrategy().compareTo(m.getStrategies().get(strategyid))==0 && entry.getValue().getPosition()<0 && Parameters.symbol.get(entry.getKey().getSymbolID()).getLastPrice()>0){
                    pnl=pnl- entry.getValue().getPosition()*(entry.getValue().getPrice()-Parameters.symbol.get(entry.getKey().getSymbolID()).getLastPrice())+entry.getValue().getProfit();
                    } else if(entry.getKey().getStrategy().compareTo(m.getStrategies().get(strategyid))==0 && entry.getValue().getPosition()==0){
                        pnl=pnl+entry.getValue().getProfit();
                    }
                }
                    double maxpnl=pnl>m.getMaxPNL().get(strategyid)?pnl:m.getMaxPNL().get(strategyid);
                    double minpnl=pnl<m.getMinPNL().get(strategyid)?pnl:m.getMinPNL().get(strategyid);
                    m.getMaxPNL().set(strategyid, maxpnl);
                    m.getMinPNL().set(strategyid, minpnl);
                    
                if(Parameters.connection.get(display).getPositions().get(ind).getPosition()>0) {
                    return (int)Math.round(-Parameters.connection.get(display).getPositions().get(ind).getPosition()*(Parameters.connection.get(display).getPositions().get(ind).getPrice()-Parameters.symbol.get(ind.getSymbolID()).getLastPrice())+Parameters.connection.get(display).getPositions().get(ind).getProfit());
                    //return String.format("%.02f",(-Parameters.connection.get(display).getPositions().get(ind).getPosition()*(Parameters.connection.get(display).getPositions().get(ind).getPrice()-Parameters.symbol.get(ind.getSymbolID()).getLastPrice())+Parameters.connection.get(display).getPositions().get(ind).getProfit())); 

                }
                else if (Parameters.connection.get(display).getPositions().get(ind).getPosition()<0) {
                    return (int)Math.round(-Parameters.connection.get(display).getPositions().get(ind).getPosition()*(Parameters.connection.get(display).getPositions().get(ind).getPrice()-Parameters.symbol.get(ind.getSymbolID()).getLastPrice())+Parameters.connection.get(display).getPositions().get(ind).getProfit()); 
                //return String.format("0.02f",(-Parameters.connection.get(display).getPositions().get(ind).getPosition()*(Parameters.connection.get(display).getPositions().get(ind).getPrice()-Parameters.symbol.get(ind.getSymbolID()).getLastPrice())+Parameters.connection.get(display).getPositions().get(ind).getProfit())); 
                }                  
                else return (int)Math.round(Parameters.connection.get(display).getPositions().get(ind).getProfit());
                //else return String.format("0.02f",(Parameters.connection.get(display).getPositions().get(ind).getProfit()));
                }else return pnl;
                
            case 6: 
                return m.getParamTurtle().getHighestHigh().get(ind.getSymbolID());
            case 4: 
                return m.getParamTurtle().getLowestLow().get(ind.getSymbolID());
            case 5:
                return Parameters.symbol.get(ind.getSymbolID()).getLastPrice();
            /* commented out to handle reduced column sizes
            case 7:
                int size=m.getParamTurtle().getCumVolume().get(ind.getSymbolID()).size();
                return m.getParamTurtle().getCumVolume().get(ind.getSymbolID()).get(size-1);
            case 8: 
                return m.getParamTurtle().getSlope().get(ind.getSymbolID());
            case 9: 
                return Long.parseLong(Parameters.symbol.get(ind.getSymbolID()).getAdditionalInput())*0.2/375;
            case 10: 
                return m.getParamTurtle().getVolume().get(ind.getSymbolID());
            case 11:
                return m.getParamTurtle().getVolumeMA().get(ind.getSymbolID());
            */
            case 7:
                return ind.getStrategy();
            default:
                throw new IndexOutOfBoundsException(); 
                
    }
        
        
        
    }
    
    
}
