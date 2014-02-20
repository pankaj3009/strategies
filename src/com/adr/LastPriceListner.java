/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.adr;
import com.espertech.esper.client.UpdateListener;
import com.espertech.esper.client.EventBean;
import java.text.DecimalFormat;
//import com.incur.client.strat.MarketApp;

/**
 *
 * @author Admin
 */
public class LastPriceListner implements UpdateListener{

    public void update(EventBean[] newEvents, EventBean[] oldEvents) {

        DecimalFormat df = new DecimalFormat("0.00");
        df.setMaximumFractionDigits(2);
        df.setMinimumIntegerDigits(1);

        Long tTicks = (Long) newEvents[0].get("tTicks");
        Long pTicks =  (Long) newEvents[0].get("pTicks");
        Long nTicks =  (Long) newEvents[0].get("nTicks");
        Long uChg = tTicks - (pTicks + nTicks);
        double adr = pTicks;
        if (tTicks > 0) adr = (double)pTicks/tTicks;
        String message = "TotalTicks: " + tTicks + " (+)Ticks: " + pTicks + " (-)Ticks: " + nTicks + " Unchanged: " + uChg;
        //System.out.println("Listner update: " + message);
        //MarketApp.setADRLP(df.format(adr), message); //ADR Tick
    }
}
