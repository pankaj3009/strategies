/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.incurrency.algorithms.adr;
import com.espertech.esper.client.UpdateListener;
import com.espertech.esper.client.EventBean;
import java.text.DecimalFormat;
import java.util.Date;
import java.util.logging.Logger;
//import com.incur.client.strat.MarketApp;

/*
 *
 * @author Admin
 */
public class ADRListener implements UpdateListener{

    private static final Logger logger = Logger.getLogger(ADRListener.class.getName());
    
    public void update(EventBean[] newEvents, EventBean[] oldEvents) {

        DecimalFormat df = new DecimalFormat("0.00");
        df.setMaximumFractionDigits(2);
        df.setMinimumIntegerDigits(1);

        Long pTicks = (Long) newEvents[0].get("pTicks");
        Long tTicks =  (Long) newEvents[0].get("tTicks");
        Long nTicks =  (Long) newEvents[0].get("nTicks");
        Long pVolume = newEvents[0].get("pVolume")==null? 0:Math.round((double)newEvents[0].get("pVolume"));
        Long nVolume = newEvents[0].get("nVolume")==null? 0:Math.round((double)newEvents[0].get("nVolume"));
        Long tVolume = newEvents[0].get("volume")==null? 0:Math.round((double)newEvents[0].get("volume"));
        
        Long uChg = tTicks - (pTicks + nTicks);
        double adr = pTicks;
        if (tTicks > 0) adr = (double)pTicks/tTicks;
        String message = "ADR: TotalMoves: " + tTicks + " (+)Advances: " + pTicks + " (-)Declines: " + nTicks + " Unchanged: " + uChg +" Advancing Volume: "+pVolume +" Declining Volume: "+nVolume+ " Total Volume: "+tVolume;
        long now =new Date().getTime();
        ADR.adrServer.send("IND-CUS-ALL",0+","+now+","+pTicks+","+"ADR");
        ADR.adrServer.send("IND-CUS-ALL",1+","+now+","+nTicks+","+"ADR" );
        ADR.adrServer.send("IND-CUS-ALL",2+","+now+","+tTicks+","+"ADR");
        ADR.adrServer.send("IND-CUS-ALL",3+","+now+","+pVolume+","+"ADR" );
        ADR.adrServer.send("IND-CUS-ALL",4+","+now+","+nVolume+","+"ADR" );
        ADR.adrServer.send("IND-CUS-ALL",5+","+now+","+tVolume+","+"ADR" );
        
       // System.out.println(message);
        //System.out.println("Listner update: " + message);
//        MarketApp.setADRLC(df.format(adr), message); //ADR Market
    }
    
}
