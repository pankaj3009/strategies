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

/**
 *
 * @author Admin
 */
public class TickListener implements UpdateListener{

    private static final Logger logger = Logger.getLogger(TickListener.class.getName());
    public void update(EventBean[] newEvents, EventBean[] oldEvents) {

        DecimalFormat df = new DecimalFormat("0.00");
        df.setMaximumFractionDigits(2);
        df.setMinimumIntegerDigits(1);

        Long tTicks = (Long) newEvents[0].get("tTicks");
        Long pTicks =  (Long) newEvents[0].get("pTicks");
        Long nTicks =  (Long) newEvents[0].get("nTicks");
        Long uTicks =   (Long) newEvents[0].get("uTicks");
        long pVolume= newEvents[0].get("pLastSize")==null? 0:Math.round((double)newEvents[0].get("pLastSize"));
        long nVolume= newEvents[0].get("nLastSize")==null?0:Math.round((double) newEvents[0].get("nLastSize"));
        long tVolume= newEvents[0].get("tLastSize")==null?0:Math.round((double) newEvents[0].get("tLastSize"));
        long uVolume= newEvents[0].get("uLastSize")==null?0:Math.round((double) newEvents[0].get("uLastSize"));
 
        double adr = pTicks;
        if (tTicks > 0) adr = (double)pTicks/tTicks;
        String message = "Tick: TotalTicks: " + tTicks + " (+)Ticks: " + pTicks + " (-)Ticks: " + nTicks + " Unchanged: " + uTicks;
        message += " (+)Vol: "+pVolume+" (-)Vol:"+nVolume+" Tot LastSize:"+ tVolume;
                long now =new Date().getTime();
        ADR.adrServer.send("IND:CUS:ALL",6+","+now+","+pTicks +"ADR");
        ADR.adrServer.send("IND:CUS:ALL",7+","+now+","+nTicks +"ADR" );
        ADR.adrServer.send("IND:CUS:ALL",8+","+now+","+tTicks +"ADR");
        ADR.adrServer.send("IND:CUS:ALL",9+","+now+","+pVolume +"ADR" );
        ADR.adrServer.send("IND:CUS:ALL",10+","+now+","+nVolume +"ADR");
        ADR.adrServer.send("IND:CUS:ALL",11+","+now+","+tVolume +"ADR" );
        ADR.adrServer.send("IND:CUS:ALL",12+","+now+","+uTicks +"ADR");
        ADR.adrServer.send("IND:CUS:ALL",13+","+now+","+uVolume +"ADR");
        
        //System.out.println(message);
        //System.out.println("Listner update: " + message);
        //MarketApp.setADRLP(df.format(adr), message); //ADR Tick
    }
}
