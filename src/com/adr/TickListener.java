/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.adr;
import com.espertech.esper.client.UpdateListener;
import com.espertech.esper.client.EventBean;
import java.text.DecimalFormat;
import java.util.Date;
//import com.incur.client.strat.MarketApp;

/**
 *
 * @author Admin
 */
public class TickListener implements UpdateListener{

    public void update(EventBean[] newEvents, EventBean[] oldEvents) {

        DecimalFormat df = new DecimalFormat("0.00");
        df.setMaximumFractionDigits(2);
        df.setMinimumIntegerDigits(1);

        Long tTicks = (Long) newEvents[0].get("tTicks");
        Long pTicks =  (Long) newEvents[0].get("pTicks");
        Long nTicks =  (Long) newEvents[0].get("nTicks");
        Long uChg = tTicks - (pTicks + nTicks);
        long pVolume= newEvents[0].get("pLastSize")==null? 0:Math.round((double)newEvents[0].get("pLastSize"));
        long nVolume= newEvents[0].get("nLastSize")==null?0:Math.round((double) newEvents[0].get("nLastSize"));
        long tVolume= newEvents[0].get("tLastSize")==null?0:Math.round((double) newEvents[0].get("tLastSize"));
 
        double adr = pTicks;
        if (tTicks > 0) adr = (double)pTicks/tTicks;
        String message = "Tick: TotalTicks: " + tTicks + " (+)Ticks: " + pTicks + " (-)Ticks: " + nTicks + " Unchanged: " + uChg;
        message += " (+)Vol: "+pVolume+" (-)Vol:"+nVolume+" Tot LastSize:"+ tVolume;
                long now =new Date().getTime();
        ADR.adrServer.send("IND:CUS:ALL",6+","+now+","+pTicks );
        ADR.adrServer.send("IND:CUS:ALL",7+","+now+","+nTicks );
        ADR.adrServer.send("IND:CUS:ALL",8+","+now+","+tTicks );
        ADR.adrServer.send("IND:CUS:ALL",9+","+now+","+pVolume );
        ADR.adrServer.send("IND:CUS:ALL",10+","+now+","+nVolume );
        ADR.adrServer.send("IND:CUS:ALL",11+","+now+","+tVolume );
        System.out.println(message);
        //System.out.println("Listner update: " + message);
        //MarketApp.setADRLP(df.format(adr), message); //ADR Tick
    }
}
