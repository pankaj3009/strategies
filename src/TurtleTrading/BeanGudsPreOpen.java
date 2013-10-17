/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package TurtleTrading;

import incurrframework.BeanSymbol;
import incurrframework.Parameters;
import java.util.ArrayList;
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
        List<BeanSymbol> filteredStocks = new ArrayList();
        for(BeanSymbol s : Parameters.symbol) {
        if( "Y".compareTo(s.getPreopen())==0){    
            filteredStocks.add(s);
        }
        
        //check if filtered stocks need a lucky trade at preopen.
        
    }
    
}
}