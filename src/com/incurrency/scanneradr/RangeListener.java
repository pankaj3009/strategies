/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.scanneradr;

import com.espertech.esper.client.EventBean;
import com.espertech.esper.client.UpdateListener;
import com.incurrency.framework.EnumBarSize;
import com.incurrency.framework.Parameters;
import static com.incurrency.scanneradr.ADRManager.tradingStarted;
import static com.incurrency.scanneradr.ADRManager.tradingEnded;

/**
 *
 * @author Pankaj
 */
public class RangeListener implements UpdateListener {

    @Override
    public void update(EventBean[] newEvents, EventBean[] oldEvents) {
        double high = newEvents[0].get("high") == null ? Double.MIN_VALUE : (Double) newEvents[0].get("high");
        double low = newEvents[0].get("low") == null ? Double.MAX_VALUE : (Double) newEvents[0].get("low");
        double average = newEvents[0].get("average") == null ? 0 : (Double) newEvents[0].get("average");
        String period = newEvents[0].get("period").toString();
        long time = (long) newEvents[0].get("ts");
        if (tradingStarted && !tradingEnded) {
            
            switch ((Integer) newEvents[0].get("field")) {
                case ADRTickType.D_ADR:
                    Parameters.symbol.get(ADRManager.compositeID).setTimeSeries(EnumBarSize.ONESECOND, time, new String[]{period + "adrhigh", period + "adrlow", period + "adravg"}, new double[]{high, low, average});
                    break;
                case ADRTickType.D_TRIN_VALUE:
                    Parameters.symbol.get(ADRManager.compositeID).setTimeSeries(EnumBarSize.ONESECOND, time, new String[]{period + "trinvaluehigh", period + "trinvaluelow", period + "trinvalueavg"}, new double[]{high, low, average});
                    break;
                case ADRTickType.D_TRIN_VOLUME:
                    Parameters.symbol.get(ADRManager.compositeID).setTimeSeries(EnumBarSize.ONESECOND, time, new String[]{period + "trinvolumehigh", period + "trinvolumelow", period + "trinvolumeavg"}, new double[]{high, low, average});
                    break;
                case ADRTickType.T_MOVE:
                    Parameters.symbol.get(ADRManager.compositeID).setTimeSeries(EnumBarSize.ONESECOND, time, new String[]{period + "moveavg"}, new double[]{average});
                    break;
                case ADRTickType.T_TIME:
                    Parameters.symbol.get(ADRManager.compositeID).setTimeSeries(EnumBarSize.ONESECOND, time, new String[]{period + "timehigh", period + "timelow", period + "timeavg"}, new double[]{high, low, average});
                    break;
                default:
                    break;
            }
        }
    }
}
