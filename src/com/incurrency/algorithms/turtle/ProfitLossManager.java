/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.algorithms.turtle;

import com.incurrency.algorithms.turtle.TurtleMainUI;
import com.incurrency.framework.BeanConnection;
import com.incurrency.framework.EnumOrderIntent;
import com.incurrency.framework.EnumOrderSide;
import com.incurrency.framework.Index;
import com.incurrency.framework.Launch;
import com.incurrency.framework.Parameters;
import com.incurrency.framework.TradeEvent;
import com.incurrency.framework.TradeListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author pankaj
 */
public class ProfitLossManager implements TradeListener {

    public HashMap<Index, Double> pnlBySymbol = new HashMap();
    public ArrayList<Double> pnl = new ArrayList();
    double takeProfit;
    double stopLoss;
    ArrayList<Integer> profitsToBeTaken = new ArrayList();
    private static final Logger logger = Logger.getLogger(ProfitLossManager.class.getName());


    public ProfitLossManager() {

        //initialize Hashmap
        for (int i = 0; i < Parameters.symbol.size(); i++) {
            //ArrayList <Integer> arr=new ArrayList();
            //arr.add(0);
            Index ind = new Index("idt", i);
            //Positions.put(ind, new BeanPosition());
            pnlBySymbol.put(ind, 0D);
        }

        //initiliaze arraylist
        for (int j = 0; j < Parameters.connection.size(); j++) {
            pnl.add(0D);
            profitsToBeTaken.add(1);
            Parameters.connection.get(j).getWrapper().addTradeListener(this);
        }
        
    }

    @Override
    public void tradeReceived(TradeEvent event) {
        int id = event.getSymbolID();
        Index index = new Index("idt", id);

        for (int j = 0; j < Parameters.connection.size(); j++) {
            if ("Trading".equals(Parameters.connection.get(j).getPurpose())) {
                int positions = Parameters.connection.get(j).getPositions().get(index)!=null?Parameters.connection.get(j).getPositions().get(index).getPosition():0;
                double realizedPNL = Parameters.connection.get(j).getPositions().get(index)!=null?Parameters.connection.get(j).getPositions().get(index).getProfit():0;
                double entryprice = Parameters.connection.get(j).getPositions().get(index)!=null?Parameters.connection.get(j).getPositions().get(index).getPrice():0;
                double unrealizedPNL = 0D;
                if (positions != 0) {
                    unrealizedPNL = positions * (Parameters.symbol.get(id).getLastPrice() - entryprice);
                }
                double symbolOldPNL = pnlBySymbol.get(index);
                double symbolNewPNL = realizedPNL + unrealizedPNL;
                double newpnl=pnl.get(j)+symbolNewPNL-symbolOldPNL;
                pnl.set(j, newpnl);
                pnlBySymbol.put(index, symbolNewPNL);
            }
            takeProfit=Launch.algo!=null?Launch.algo.getParamTurtle().getProfitTarget():Double.MAX_VALUE;
        
                
            if (pnl.get(j) > takeProfit * profitsToBeTaken.get(j)) {
                int profitsTaken = profitsToBeTaken.get(j);
                TurtleMainUI.setProfitTaken(Integer.toString(profitsTaken));
                profitsToBeTaken.set(j, profitsTaken + 1);
                for (BeanConnection c : Parameters.connection) {
                    if ("Trading".equals(c.getPurpose()) && c.getStrategy().contains("idt")) {
                        for (int symbolid = 0; symbolid < Parameters.symbol.size(); symbolid++) {
                            //Launch.algo.ordManagement.cancelOpenOrders(c, symbolid, "idt");
                            Index ind=new Index("idt",symbolid);
                            int position = c.getPositions().get(ind) == null ? 0 : c.getPositions().get(ind).getPosition();
                            if(position>0){
                            logger.log(Level.INFO,"Profit Target Hit. Sell. Profit Target:{0}",new Object[]{takeProfit * profitsToBeTaken.get(j)});
                            Launch.algo.getParamTurtle().orderTurtle.tes.fireOrderEvent(0,0,Parameters.symbol.get(symbolid), EnumOrderSide.SELL, Math.abs(position), Parameters.symbol.get(symbolid).getLastPrice(), 0, "idt", 3, "", EnumOrderIntent.Init, 3, 2, 0);
                            } else if (position<0){
                            logger.log(Level.INFO,"Profit Target Hit. Cover. Profit Target:{0}",new Object[]{takeProfit * profitsToBeTaken.get(j)});
                            Launch.algo.getParamTurtle().orderTurtle.tes.fireOrderEvent(0,0,Parameters.symbol.get(symbolid), EnumOrderSide.COVER, Math.abs(position), Parameters.symbol.get(symbolid).getLastPrice(), 0, "idt", 3, "", EnumOrderIntent.Init, 3, 2, 0);                                
                            }
                        }
                    }
               }
            }
        }
        
    }
}
