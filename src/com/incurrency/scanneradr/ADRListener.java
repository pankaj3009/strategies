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
import static com.incurrency.scanneradr.ADRManager.tradingStarted;
import static com.incurrency.scanneradr.ADRManager.tradingEnded;
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
        Long pValue = newEvents[0].get("pValue")==null? 0:Math.round((double)newEvents[0].get("pValue"));
        Long nValue = newEvents[0].get("nValue")==null? 0:Math.round((double)newEvents[0].get("nValue"));
        Long tValue = newEvents[0].get("tradedValue")==null? 0:Math.round((double)newEvents[0].get("tradedValue"));
        
        Long uChg = tTicks - (pTicks + nTicks);
        double adr=pTicks+nTicks>0?(double)pTicks*100/(pTicks+nTicks):0;
        double adrTRINVolume=pVolume+nVolume>0 && pVolume>0?(double)adr*100/(double)(pVolume*100D/(pVolume+nVolume)):0;
        double adrTRINValue=pValue+nValue>0 && pValue>0?(double)adr*100/(double)(pValue*100D/(pValue+nValue)):0;        
        long time =(long)newEvents[0].get("ts");
        if(tTicks>=ADRManager.threshold){
          BeanSymbol s=Parameters.symbol.get(ADRManager.compositeID);
          if(tradingStarted && !tradingEnded){
          s.setTimeSeries(EnumBarSize.ONESECOND, time, new String[]{"adr","adrtrinvolume","adrtrinvalue"}, new double[]{adr,adrTRINVolume,adrTRINValue});
          }
          adrManager.mEsperEvtProcessor.sendEvent(new ADREvent(ADRTickType.D_ADR,adr));
          adrManager.mEsperEvtProcessor.sendEvent(new ADREvent(ADRTickType.D_TRIN_VOLUME,adrTRINVolume));
          adrManager.mEsperEvtProcessor.sendEvent(new ADREvent(ADRTickType.D_TRIN_VALUE,adrTRINValue));        }
    }
    
}
