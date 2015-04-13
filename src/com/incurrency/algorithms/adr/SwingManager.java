   /*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.algorithms.adr;

import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jblas.DoubleMatrix;
import scanner.BeanSymbol;
import scanner.EnumBarSize;
import scanner.MatrixMethods;
import scanner.ReservedValues;
import scanner.Utilities;
import static scanner.MatrixMethods.*;

/**
 *
 * @author admin
 */
public class SwingManager implements Runnable {

    EnumBarSize barSize;
    private static final Logger logger = Logger.getLogger(SwingManager.class.getName());

    public SwingManager(EnumBarSize barSize) {
        this.barSize = barSize;
    }

    @Override
    public void run() {
        for (BeanSymbol s : scanner.Scanner.symbol) {
            /*
             * Step 1: Get the data bar
             * Step 2: Generate updown bar vector
             * Step 3: for each change in value in updown vector
             *              calculate HH for +1, calculate LL for -1
             *              Update HH and LL in swinglevel vector
             * Step 4: For swinglevel vector
             *              Get prior three price changes
             *              Check if upswing or downswing or neural
             *              update swing vector
             */
            logger.log(Level.FINE,"Symbol: {0}, Processing for swing information",new Object[]{s.getDisplayname()});
            try {
                Preconditions.checkArgument(s.getTimeSeries(barSize, "settle").length>0, "Bar for symbol: %s, Barsize: %s does not have any data", s.getDisplayname(), barSize.toString());
                DoubleMatrix mO = s.getTimeSeries(barSize,"open");
                DoubleMatrix mH = s.getTimeSeries(barSize, "high");
                DoubleMatrix mL = s.getTimeSeries(barSize, "low");
                //get missing data
                int[] indices=mH.ne(ReservedValues.EMPTY).findIndices();
                mO=mO.get(indices).reshape(1, indices.length);
                mH=mH.get(indices).reshape(1, indices.length);
                mL=mL.get(indices).reshape(1, indices.length);
                
                if(mH.length>0){
                List <Long> dT = s.getColumnLabels().get(barSize);
                dT=Utilities.subList(indices, dT);
/*
                mO.reshape(1, mO.length);
                mH.reshape(1, mH.length);
                mL.reshape(1, mL.length);
                mC.reshape(1, mC.length);
*/
                //DoubleMatrix mH_1= mH.put(0, Double.MIN_VALUE);
                //DoubleMatrix mL_1=mL.put(0, Double.MAX_VALUE);
                DoubleMatrix HH = new DoubleMatrix();
                DoubleMatrix HL = new DoubleMatrix();
                DoubleMatrix LH = new DoubleMatrix();
                DoubleMatrix LL = new DoubleMatrix();
                DoubleMatrix out = DoubleMatrix.zeros(1, mO.columns);
                DoubleMatrix mH_1 = ref(mH, -1);
                DoubleMatrix mL_1 = ref(mL, -1);
                mH.gti(mH_1, HH);
                mH.lti(mH_1, LH);
                mL.gti(mL_1, HL);
                mL.lti(mL_1, LL);
                DoubleMatrix upbar = HH.and(HL);
                DoubleMatrix downbar = LL.and(LH);
                DoubleMatrix outsidebar=HH.and(LL);
                DoubleMatrix upbar_1 = ref(upbar, -1);
                DoubleMatrix downbar_1 = ref(downbar, -1);

                upbar = upbar.or(upbar_1.and(LH.and(HL))).or(upbar_1.and(HH.and(LL)));
                downbar = downbar.or(downbar_1.and(LH.and(HL))).or(downbar_1.and(HH.and(LL)));                
                downbar.negi();
                DoubleMatrix updownbar = upbar.add(downbar);
                //fill 0's with prior values
                updownbar=valueWhen(updownbar, updownbar, 1);
                int [] outsidebarindices=outsidebar.eq(1).findIndices();
                updownbar.put(outsidebarindices, DoubleMatrix.zeros(outsidebarindices.length));

                DoubleMatrix swingHighSignal = HighestvalueSignalWhen(updownbar.eq(1).or(updownbar.eq(0)), mH, 1);
                DoubleMatrix swingLowSignal = LowestvalueSignalWhen(updownbar.eq(-1).or(updownbar.eq(0)), mL, 1);
                DoubleMatrix swingHigh = valueWhen(swingHighSignal, swingHighSignal, 1);
                DoubleMatrix swingLow = valueWhen(swingLowSignal, swingLowSignal, 1);
                DoubleMatrix swingHighSignal_1 = HighestvalueSignalWhen(swingHighSignal, swingHigh, 2);
                DoubleMatrix swingLowSignal_1 = LowestvalueSignalWhen(swingLowSignal, swingLow, 2);
                DoubleMatrix swingHighSignal_2 = HighestvalueSignalWhen(swingHighSignal, swingHigh, 3);
                DoubleMatrix swingLowSignal_2 = LowestvalueSignalWhen(swingLowSignal, swingLow, 3);
                DoubleMatrix swingHigh_1 = valueWhen(swingHighSignal_1, swingHighSignal_1, 1);
                DoubleMatrix swingLow_1 = valueWhen(swingLowSignal_1, swingLowSignal_1, 1);
                DoubleMatrix swingHigh_2 = valueWhen(swingHighSignal_2, swingHighSignal_2, 1);
                DoubleMatrix swingLow_2 = valueWhen(swingLowSignal_2, swingLowSignal_2, 1);

                DoubleMatrix out_1 = updownbar.eq(1).and((swingHigh.gt(swingHigh_1)).and(swingLow.gt(swingLow_1))); //upbar which has made a higher high
                DoubleMatrix out_2 = updownbar.eq(1).and((swingHigh_1.gt(swingHigh_2)).and(swingLow.gt(swingLow_1)));//upbar which has not made a higher high
                DoubleMatrix out_3 = (updownbar.eq(-1).or(updownbar.eq(0))).and((swingHigh.gt(swingHigh_1)).and(swingLow_1.gt(swingLow_2)).and(mL.gt(swingLow_1)));//downbar which has not made a lower low
                DoubleMatrix upswing = out_1.or(out_2).or(out_3);
                DoubleMatrix out_4 = updownbar.eq(-1).and((swingLow.lt(swingLow_1)).and(swingHigh.lt(swingHigh_1))); //downbar which has made a lower low
                DoubleMatrix out_5 = updownbar.eq(-1).and((swingLow_1.lt(swingLow_2)).and(swingHigh.lt(swingHigh_1)));//downbar which has not made a lower low
                DoubleMatrix out_6 = (updownbar.eq(1).or(updownbar.eq(0))).and((swingLow.lt(swingLow_1)).and(swingHigh_1.lt(swingHigh_2)).and(mH.lt(swingHigh_1)));//upbar which has not made a higher high
                DoubleMatrix downswing = out_4.or(out_5).or(out_6);
                downswing.negi();
                DoubleMatrix swing = upswing.add(downswing);          
                DoubleMatrix stickySwing=valueWhen(swing, swing, 1);
                DoubleMatrix swing_1=ref(swing, -1);
                DoubleMatrix swing_up=swing.eq(0).or(swing.eq(-1));
                DoubleMatrix swing_down=swing.eq(0).or(swing.eq(1));
                DoubleMatrix barsInSwing_up=barsSince(swing_up);
                DoubleMatrix barsInSwing_down=barsSince(swing_down);
                DoubleMatrix barsInSwing=barsInSwing_up.add(barsInSwing_down);
                DoubleMatrix barsOutsideSwing=barsSince(swing);
                logger.log(Level.FINE,"Symbol:{0},Swings Calculated:{1}",new Object[]{s.getDisplayname(),swing.length});
                s.setTimeSeries(barSize, dT,new String[]{"swing","stickyswing","swingh","swingl","swingh_1","swingl_1","barsinswing","barsoutsideswing","updownbar"},
                new double[][]{swing.data,stickySwing.data,swingHigh.data, swingLow.data,swingHigh_1.data,
                    swingLow_1.data,barsInSwing.data,barsOutsideSwing.data,updownbar.data});
                }
                } catch (Exception e) {
                logger.log(Level.SEVERE,null,e);
            }
        }
    }
}
