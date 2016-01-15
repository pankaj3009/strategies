/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.incurrency.scanneradr;
import com.espertech.esper.client.UpdateListener;
import com.espertech.esper.client.EventBean;
import com.incurrency.framework.BeanSymbol;
import com.incurrency.framework.EnumBarSize;
import com.incurrency.framework.Parameters;
import java.text.DecimalFormat;
import java.util.logging.Logger;

//import com.incur.client.strat.MarketApp;

/*
 *
 * @author Admin
 */
public class ADRListener implements UpdateListener{

    private static final Logger logger = Logger.getLogger(ADRListener.class.getName());
    public ADRManager adrManager;
    
    public ADRListener(ADRManager adrManager){
    this.adrManager=adrManager;
    }
    
    @Override
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
        long time =(long)newEvents[0].get("ts");
        double adr=pTicks+nTicks>0?pTicks*100/(pTicks+nTicks):0;
        double daily_volume=pVolume+nVolume>0?pVolume*100/(pVolume+nVolume):0;
        double TRIN=daily_volume>0?adr*100/daily_volume:0;
          BeanSymbol s=Parameters.symbol.get(ADRManager.compositeID);
          s.setTimeSeries(EnumBarSize.ONESECOND, time, new String[]{"adratio","advolumeratio","trin","pticks","nticks","tticks","pvolume","nvolume"}, new double[]{adr,daily_volume,TRIN,pTicks,nTicks,tTicks,pVolume,nVolume});
          adrManager.mEsperEvtProcessor.sendEvent(new ADREvent(ADRTickType.D_ADR,adr));
          adrManager.mEsperEvtProcessor.sendEvent(new ADREvent(ADRTickType.D_TRIN,TRIN));
          
    }
    
}
