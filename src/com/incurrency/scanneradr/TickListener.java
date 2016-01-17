/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.incurrency.scanneradr;
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
    ADRManager adrManager;
    public TickListener(ADRManager adrManager){
        this.adrManager=adrManager;
    }
    
    @Override
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
 
        String message = "Tick: TotalTicks: " + tTicks + " (+)Ticks: " + pTicks + " (-)Ticks: " + nTicks + " Unchanged: " + uTicks;
        message += " (+)Vol: "+pVolume+" (-)Vol:"+nVolume+" Tot LastSize:"+ tVolume;
                //long now =ADRData.time;
        
        double tick=pTicks+nTicks>0?pTicks*100/(pTicks+nTicks):0;
        double volume=pVolume+nVolume>0?(pVolume*100/(pVolume+nVolume)):0;
        double tickTRIN=pVolume+nVolume>0?tick*100/volume:0;

        if(tTicks>ADRManager.threshold){
          //adrManager.mEsperEvtProcessor.sendEvent(new TickPriceEvent(ADRTickType.T_TICK,ADRTickType.T_TICK,tick));
          //adrManager.mEsperEvtProcessor.sendEvent(new TickPriceEvent(ADRTickType.T_TRIN,ADRTickType.T_TRIN,tickTRIN));         
          }
    }
}
