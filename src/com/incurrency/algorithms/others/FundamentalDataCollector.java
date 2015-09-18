/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.algorithms.others;


import com.incurrency.framework.Algorithm;
import com.incurrency.framework.BeanConnection;
import com.incurrency.framework.BeanSymbol;
import com.incurrency.framework.fundamental.FundamentalDataOld;
import com.incurrency.framework.fundamental.FundamentalDataEvent;
import com.incurrency.framework.fundamental.FundamentalDataListener;
import com.incurrency.framework.Parameters;
import com.incurrency.framework.TWSConnection;
import java.util.ArrayList;
import java.util.HashMap;

    
/**
 *
 * @author pankaj
 */
public class FundamentalDataCollector extends Algorithm implements FundamentalDataListener {

    String[] args={"","symbols.csv","connection.csv",""};
    
    public FundamentalDataCollector(HashMap<String,String> input) throws Exception{
         super(input);
         for (BeanConnection c : Parameters.connection) {
            c.setWrapper(new TWSConnection(c));
        }
        for (BeanConnection c : Parameters.connection) {
            c.getWrapper().connectToTWS();
        }
        for (BeanConnection c : Parameters.connection) {
            c.getWrapper().eClientSocket.reqCurrentTime();
        }
        
        BeanConnection tempC = Parameters.connection.get(0);
        for (BeanSymbol s : Parameters.symbol) {
            tempC.getWrapper().getContractDetails(s,"");
            System.out.print("ContractDetails Requested:" + s.getBrokerSymbol());
        }

        while (TWSConnection.mTotalSymbols > 0) {
            //System.out.println(TWSConnection.mTotalSymbols);
            //do nothing
        }
        
                ArrayList <Boolean> arrayID=new ArrayList();
        for(BeanSymbol s: Parameters.symbol){
               arrayID.add(s.isStatus());
         }
        
        System.out.println("Symbol Size:"+Parameters.symbol.size());
        System.out.println("Array Size:"+arrayID.size());
        
        int j=0;
        Thread.sleep(1000);
        for(int i=0;i<arrayID.size();i++){
            if(!arrayID.get(i)){ //if status is false, remove the symbol
                Parameters.symbol.remove(i-j);
                j=j+1;
            }
        }
           
                Thread f = new Thread(new FundamentalDataOld());
                f.start();
                    }

    @Override
    public void fundamentalDataStatus(FundamentalDataEvent event) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    }    
