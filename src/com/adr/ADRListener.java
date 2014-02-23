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

/*
 *
 * @author Admin
 */
public class ADRListener implements UpdateListener{

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
        ADR.adrServer.send("IND:CUS:ALL",ADRTickType.D_ADVANCE+","+now+","+pTicks );
        ADR.adrServer.send("IND:CUS:ALL",ADRTickType.D_DECLINE+","+now+","+nTicks );
        ADR.adrServer.send("IND:CUS:ALL",ADRTickType.D_TOTAL_SYMB+","+now+","+tTicks );
        ADR.adrServer.send("IND:CUS:ALL",ADRTickType.D_ADVANCE_VOL+","+now+","+pVolume );
        ADR.adrServer.send("IND:CUS:ALL",ADRTickType.D_DECLINE_VOL+","+now+","+nVolume );
        ADR.adrServer.send("IND:CUS:ALL",ADRTickType.D_TOTAL_VOL+","+now+","+tVolume );
        
        System.out.println(message);
        //System.out.println("Listner update: " + message);
//        MarketApp.setADRLC(df.format(adr), message); //ADR Market
    }
    
}
