/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.scanneradr;
import com.espertech.esper.client.EventBean;
import com.espertech.esper.client.UpdateListener;
import com.incurrency.framework.EnumBarSize;
import com.incurrency.framework.Parameters;
/**
 *
 * @author Pankaj
 */
public class RangeListener implements UpdateListener{

    @Override
    public void update(EventBean[] newEvents, EventBean[] oldEvents) {
        double high = newEvents[0].get("high") == null ? Double.MIN_VALUE : (Double) newEvents[0].get("high");
        double low = newEvents[0].get("low") == null ? Double.MAX_VALUE : (Double) newEvents[0].get("low");
        double average = newEvents[0].get("average") == null ? 0 : (Double) newEvents[0].get("average");
        String period=newEvents[0].get("period").toString();
        long time=(long)newEvents[0].get("ts");
            switch ((Integer) newEvents[0].get("field")) {
                case ADRTickType.D_ADR:
                    Parameters.symbol.get(ADRManager.compositeID).setTimeSeries(EnumBarSize.ONESECOND, time, new String[]{period+"adrhigh",period+"adrlow",period+"adravg"}, new double[]{high,low,average});
                    break;
                case ADRTickType.D_TRIN:
                    Parameters.symbol.get(ADRManager.compositeID).setTimeSeries(EnumBarSize.ONESECOND, time, new String[]{period+"trinhigh",period+"trinlow",period+"trinavg"}, new double[]{high,low,average});
                    break;
                case ADRTickType.INDEX:
                    Parameters.symbol.get(ADRManager.compositeID).setTimeSeries(EnumBarSize.ONESECOND, time, new String[]{period+"indexhigh",period+"indexlow",period+"indexavg"}, new double[]{high,low,average});
                    break;
                default:
                    break;
            }        
    }
    
}
