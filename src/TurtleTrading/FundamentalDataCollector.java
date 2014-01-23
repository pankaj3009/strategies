/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package TurtleTrading;

import incurrframework.Algorithm;
import incurrframework.BeanConnection;
import incurrframework.BeanSymbol;
import incurrframework.FundamentalData;
import incurrframework.FundamentalDataEvent;
import incurrframework.FundamentalDataListener;
import incurrframework.Parameters;
import incurrframework.TWSConnection;
import java.util.ArrayList;

    
/**
 *
 * @author pankaj
 */
public class FundamentalDataCollector extends Algorithm implements FundamentalDataListener {

    String[] args={"","symbols.csv","connection.csv",""};
    
    public FundamentalDataCollector(String[] args) throws Exception{
         super(args);
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
            System.out.print("ContractDetails Requested:" + s.getSymbol());
        }

        while (TWSConnection.mTotalSymbols > 0) {
            //System.out.println(TWSConnection.mTotalSymbols);
            //do nothing
             if(!MainAlgorithmUI.headless){
                 MainAlgorithmUI.setMessage("Waiting for contract information to be retrieved");
             }
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
           
                Thread f = new Thread(new FundamentalData());
                f.start();
                    }

    @Override
    public void fundamentalDataStatus(FundamentalDataEvent event) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    }    
