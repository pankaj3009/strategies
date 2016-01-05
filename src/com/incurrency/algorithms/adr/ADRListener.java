/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.incurrency.algorithms.adr;
import com.espertech.esper.client.UpdateListener;
import com.espertech.esper.client.EventBean;
import com.incurrency.framework.TradingUtil;
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
    private ADR adrStrategy;
    
    public ADRListener(ADR adr){
        this.adrStrategy=adr;
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
        String message = "ADR: TotalMoves: " + tTicks + " (+)Advances: " + pTicks + " (-)Declines: " + nTicks + " Unchanged: " + uChg +" Advancing Volume: "+pVolume +" Declining Volume: "+nVolume+ " Total Volume: "+tVolume;
        long now =TradingUtil.getAlgoDate().getTime();
        /*
        ADR.adrServer.send("IND-CUS-ALL",0+","+now+","+pTicks+","+"ADR");
        ADR.adrServer.send("IND-CUS-ALL",1+","+now+","+nTicks+","+"ADR" );
        ADR.adrServer.send("IND-CUS-ALL",2+","+now+","+tTicks+","+"ADR");
        ADR.adrServer.send("IND-CUS-ALL",3+","+now+","+pVolume+","+"ADR" );
        ADR.adrServer.send("IND-CUS-ALL",4+","+now+","+nVolume+","+"ADR" );
        ADR.adrServer.send("IND-CUS-ALL",5+","+now+","+tVolume+","+"ADR" );
        */
        double adr=pTicks+nTicks>0?(double)pTicks*100/(pTicks+nTicks):0;
        double adrTRINVolume=pVolume+nVolume>0?(double)adr*100/(double)(pVolume*100D/(pVolume+nVolume)):0;
        double adrTRINValue=pValue+nValue>0?(double)adr*100/(double)(pValue*100D/(pValue+nValue)):0;
        adrStrategy.db.setHash("indicators", "nifty", "adr", String.valueOf(adr));
        adrStrategy.db.setHash("indicators", "nifty", "adrtrinvolume", String.valueOf(adrTRINVolume));
        adrStrategy.db.setHash("indicators", "nifty", "adrtrinvalue", String.valueOf(adrTRINValue));
        if(tTicks>this.adrStrategy.threshold){
          adrStrategy.adr=adr;
          adrStrategy.adrTRINVolume=adrTRINVolume;
          adrStrategy.adrTRINValue=adrTRINValue;
          adrStrategy.adrDayHigh=adr>adrStrategy.adrDayHigh?adr:adrStrategy.adrDayHigh;
          adrStrategy.adrDayLow=adr<adrStrategy.adrDayLow?adr:adrStrategy.adrDayLow;
          adrStrategy.mEsperEvtProcessor.sendEvent(new ADREvent(ADRTickType.D_ADR,adr));
          adrStrategy.mEsperEvtProcessor.sendEvent(new ADREvent(ADRTickType.D_TRIN_VOLUME,adrTRINVolume));
          adrStrategy.mEsperEvtProcessor.sendEvent(new ADREvent(ADRTickType.D_TRIN_VALUE,adrTRINValue));
        }
    }
    
}
