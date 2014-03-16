/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.algorithms.others;

import com.incurrency.algorithms.others.BeanGuds;
import static com.incurrency.algorithms.turtle.TurtleMainUI.algo;
import com.incurrency.framework.BeanSymbol;
import com.incurrency.framework.EnumOrderIntent;
import com.incurrency.framework.EnumOrderSide;
import com.incurrency.framework.Launch;
import com.incurrency.framework.Parameters;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.TimerTask;

/**
 *
 * @author psharma
 */
public class BeanGudsPreOpen extends TimerTask{

    BeanGuds beanG;
    
    BeanGudsPreOpen(BeanGuds b){
    beanG=b;    
    }
    @Override
    public void run() {
        List<BeanSymbol> tempfilteredStocks = new ArrayList();
        for(BeanSymbol s1 : Parameters.symbol) {
        if( "Y".compareTo(s1.getPreopen())==0){    
            tempfilteredStocks.add(s1);
        }
        }
        List<BeanSymbol>filteredFutures=beanG.getGUDSSymbols();
        List<BeanSymbol> filteredStocks = new ArrayList();
        
        //ensure that filteredStock and filteredFutures are aligned correctly. Symbol Names should be at the same index
        for(Iterator<BeanSymbol> itrFut = filteredFutures.iterator(); itrFut.hasNext();){
            BeanSymbol s2=itrFut.next();
            //check tempfilteredstocks and add items to filteredstocks as matches are found
            for(Iterator<BeanSymbol> itrStock = tempfilteredStocks.iterator(); itrStock.hasNext();){
                BeanSymbol s3=itrStock.next();
                if (s3.getSymbol().compareTo(s2.getSymbol())==0){
                    filteredStocks.add(s3);
                    itrStock.remove();
                    return;
                }
            }
            filteredStocks.add(new BeanSymbol());
            filteredStocks.get(filteredStocks.size()-1).setSymbol(s2.getSymbol());
            }
        int i=0;
        for(BeanSymbol s:filteredFutures){
            double percentchange=(filteredStocks.get(i).getLastPrice()-filteredStocks.get(i).getClosePrice())/filteredStocks.get(i).getClosePrice();
            if(Math.abs(percentchange)>beanG.getStandardDev().get(i)){
                //place appropriate market open order
                if(percentchange>0){
                    //place sell futures order
              algo.getParamTurtle().orderTurtle.tes.fireOrderEvent(-1,-1,s, EnumOrderSide.SHORT, s.getMinsize(), Math.ceil(s.getClosePrice()*(1+percentchange) * 20) / 20, 0, "GUDS", 6, "MOC",EnumOrderIntent.Init,beanG.getMaxOrderDuration(),beanG.getDynamicOrderDuration(),beanG.getMaxSlippage());
                } else if (percentchange<0){
                    //place buy futures order
                     algo.getParamTurtle().orderTurtle.tes.fireOrderEvent(-1,-1,s, EnumOrderSide.SHORT, s.getMinsize(), Math.ceil(s.getClosePrice()*(1-percentchange) * 20) / 20, 0, "GUDS", 6, "MOC",EnumOrderIntent.Init,beanG.getMaxOrderDuration(),beanG.getDynamicOrderDuration(),beanG.getMaxSlippage());
             
                }
                beanG.getLuckyOrdersPlaced().set(i, Boolean.TRUE);
            }
            i=i+1;
        }
        
        }
        
    }
    
