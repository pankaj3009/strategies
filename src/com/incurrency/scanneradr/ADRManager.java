/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.scanneradr;

import com.espertech.esper.client.time.CurrentTimeEvent;
import com.google.common.base.Preconditions;
import com.incurrency.framework.Algorithm;
import com.incurrency.framework.BeanSymbol;
import com.incurrency.framework.EnumBarSize;
import com.incurrency.framework.ReservedValues;
import com.incurrency.framework.Utilities;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jblas.DoubleMatrix;
import org.jblas.ranges.IntervalRange;
import com.incurrency.framework.Parameters;
import org.jblas.ranges.Range;

/**
 *
 * @author Pankaj
 */
public class ADRManager implements Runnable {

    public static int threshold;
    public static int window = 30;
    public String outFileName = "adr";
    static boolean tradingStarted = Boolean.FALSE;
    static boolean tradingEnded = Boolean.FALSE;
    int startMinute = 30;
    int endMinute = 15;
    public EventProcessor mEsperEvtProcessor;
    boolean openTimeSet = false;
    boolean bod = true;
    public DoubleMatrix mfemae;
    private static final Logger logger = Logger.getLogger(ADRManager.class.getName());
    public double baselineEntryPrice = 0D;
    int startIndex = 0;
    int endIndex = 0;
    int closeIndex = Parameters.symbol.get(0).getRowLabels().get(EnumBarSize.ONESECOND).indexOf("close");
    int volumeIndex = Parameters.symbol.get(0).getRowLabels().get(EnumBarSize.ONESECOND).indexOf("volume");
    int iADR;
    String dateString = null;
    static long startTime;
    static long endTime;
    static int compositeID = -1;

    public ADRManager() {
        String strRangewindows = com.incurrency.scan.Scanner.args.get("rangewindows");
        String[] arrstrRangeWindows = strRangewindows.split(",");
        int[] arrintRangeWindows = new int[arrstrRangeWindows.length];
        for (int i = 0; i < arrstrRangeWindows.length; i++) {
            arrintRangeWindows[i] = Integer.valueOf(arrstrRangeWindows[i]);
        }
        int periods = arrintRangeWindows.length;
        //ADRData.adr_high = new double[periods];
        //ADRData.adr_low = new double[periods];
        //ADRData.adr_average = new double[periods];

        mEsperEvtProcessor = new EventProcessor(this, arrintRangeWindows);
        //mEsperBarProcessor = new OHLCBarProcessor();

        //create new timeseries, if does not exist
        if (Utilities.getIDFromDisplayName(Parameters.symbol, "Composite") == -1) {
            BeanSymbol s = new BeanSymbol("Composite", "Composite", "CUS", "CUS", "INR", "", "", "", 0);
            int serialno = Parameters.symbol.size();
            ADRManager.compositeID = serialno;
            s.setSerialno(serialno);
            Parameters.symbol.add(s);
        }
        iADR = Parameters.symbol.get(0).indexOfRowLabel(EnumBarSize.ONESECOND, "adratio", Parameters.symbol);
    }

    @Override
    public void run() {
        /*
         * For each incremental time {
         *   For each symbol {
         *      Publish data to Esper.
         *      Write specified data to ADRData
         *   }         
         * Write ADRData to file
         * }
         * Clear ADRData
         * Update BeanSymbol ClosePrice
         * Clear BeanSymbol dailyBars. 
         */
        int indexFuture = -1;
        threshold= Utilities.getInt(com.incurrency.scan.Scanner.args.get("threshold"),49);
        DoubleMatrix moveForward = new DoubleMatrix();
        DoubleMatrix moveBackward = new DoubleMatrix();
        DoubleMatrix moveForwardTime = new DoubleMatrix();
        DoubleMatrix moveBackwardTime = new DoubleMatrix();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
        SimpleDateFormat datetimeFormat = new SimpleDateFormat("yyyyMMddHHmmss");
        SimpleDateFormat datetimeCleanFormat = new SimpleDateFormat("yyyyMMdd HH:mm:ss");


        /*
         * IDNETIFY THE PROCESSING DATE
         */
        long adrNextTime = 0L;
        for (BeanSymbol s : Parameters.symbol) {
            Preconditions.checkArgument(s.getRowLabels().get(EnumBarSize.ONESECOND).indexOf("close") >= 0, "Bar for index: %s, Barsize: %s is null", s.getDisplayname(), EnumBarSize.ONESECOND.toString());
            if (s.getColumnLabels().get(EnumBarSize.ONESECOND).size() > 0) {
                int[] startADRIndex = s.getTimeSeries().get(EnumBarSize.ONESECOND).getRow(iADR).eq(ReservedValues.EMPTY).findIndices();
                adrNextTime = startADRIndex.length == 0 ? 0L : s.getColumnLabels().get(EnumBarSize.ONESECOND).get(startADRIndex[0]);
                if (adrNextTime > 0) {
                    dateString = dateFormat.format(adrNextTime);
                    startIndex = startADRIndex[0];
                    startTime = adrNextTime;
                    Calendar endTimeCal = Calendar.getInstance(TimeZone.getTimeZone(Algorithm.timeZone));
                    Calendar now = Calendar.getInstance(TimeZone.getTimeZone(Algorithm.timeZone));
                    now.set(Calendar.HOUR_OF_DAY, Algorithm.closeHour);
                    now.set(Calendar.MINUTE, Algorithm.closeMinute);
                    now.set(Calendar.SECOND, 0);
                    now.set(Calendar.MILLISECOND, 0);
                    now.add(Calendar.SECOND, -1);
                    endTimeCal.setTime(new Date(startTime));
                    endTimeCal.set(Calendar.MILLISECOND, 0);
                    endTimeCal.set(Calendar.SECOND, now.get(Calendar.SECOND));
                    endTimeCal.set(Calendar.MINUTE, now.get(Calendar.MINUTE));
                    endTimeCal.set(Calendar.HOUR_OF_DAY, now.get(Calendar.HOUR_OF_DAY));
                    endTime = endTimeCal.getTimeInMillis();
                }
            }
            if (dateString != null) {
                endIndex=s.getTimeStampSeries(EnumBarSize.ONESECOND).size();
                break;
            }
        }
        for (BeanSymbol s : Parameters.symbol) {
            if (s.getDisplayname().contains("NIFTY")) {
                indexFuture = s.getSerialno() - 1;
                break;
            }
        }

        if (indexFuture >= 0) {
            moveForward = calculateMoveForward(startIndex, endIndex, Parameters.symbol.get(0), EnumBarSize.ONESECOND, 10);
            moveBackward = calculateMoveBackward(startIndex, endIndex, Parameters.symbol.get(0), EnumBarSize.ONESECOND, 10);
            moveForwardTime = calculateMoveForwardTime(startIndex, endIndex, Parameters.symbol.get(0), EnumBarSize.ONESECOND, 10);
            moveBackwardTime = calculateMoveBackwardTime(startIndex, endIndex, Parameters.symbol.get(0), EnumBarSize.ONESECOND, 10);
        }

        for (BeanSymbol s : Parameters.symbol) {
            //BOD. Send open price
            if (!s.getDisplayname().contains("Composite")) {
                mEsperEvtProcessor.sendEvent(new TickPriceEvent(s.getSerialno(), com.ib.client.TickType.OPEN, s.getTimeSeriesValueCeil(EnumBarSize.ONESECOND, adrNextTime, "close")));
            }
        }

        for (int hour = Algorithm.openHour; hour <= Algorithm.closeHour; hour++) {
            for (int minute = 0; minute <= 59; minute++) {
                if (tradingStarted || minute >= 30) {
                    tradingStarted = Boolean.TRUE;
                }
                if (hour == 15 && minute >= 15) {
                    tradingEnded = Boolean.TRUE;
                }
                for (int second = 0; second <= 59; second++) {
                    //For each symbol
                    String timeString = String.format("%02d", hour) + String.format("%02d", minute) + String.format("%02d", second);
                    String datetimeString = dateString + timeString;
                    Date currentDateTime = new Date(0);
                    try {
                        currentDateTime = datetimeFormat.parse(datetimeString);
                        //ADRData.time = currentDateTime.getTime();
                    } catch (ParseException ex) {
                        Logger.getLogger(ADRManager.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    CurrentTimeEvent timeEvent = new CurrentTimeEvent(currentDateTime.getTime());
                    mEsperEvtProcessor.sendEvent(timeEvent);
                    if (bod) {
                        bod = false;
                    }
                    System.out.println("\r" + "Processing for : " + datetimeCleanFormat.format(new Date(currentDateTime.getTime())));
                    for (BeanSymbol s : Parameters.symbol) {
                        int index = s.getColumnLabels().get(EnumBarSize.ONESECOND).indexOf(currentDateTime.getTime());
                        if (index >= 0) {
                            if (!s.getDisplayname().contains("NIFTY")) {//send to esper for non index stocks
                                //only publish data if there is value for the timestamp
                                if (!s.getDisplayname().contains("Composite")){
                                double lastPrice = s.getTimeSeries().get(EnumBarSize.ONESECOND).getRow(closeIndex).get(index);
                                if (lastPrice > 0) {
                                    double lastSize=s.getTimeSeries().get(EnumBarSize.ONESECOND).getRow(volumeIndex).get(index);
                                    mEsperEvtProcessor.sendEvent(new TickPriceEvent(s.getSerialno(), com.ib.client.TickType.LAST_SIZE, lastSize));
                                    IntervalRange range = new IntervalRange(startIndex, index + 1);
                                    int[] indexesOfTrades = s.getTimeSeries().get(EnumBarSize.ONESECOND).get(volumeIndex, range).ne(ReservedValues.EMPTY).findIndices();
                                    mEsperEvtProcessor.sendEvent(new TickPriceEvent(s.getSerialno(), com.ib.client.TickType.VOLUME, s.getTimeSeries().get(EnumBarSize.ONESECOND).get(volumeIndex, indexesOfTrades).sum()));
                                    mEsperEvtProcessor.sendEvent(new TickPriceEvent(s.getSerialno(), com.ib.client.TickType.LAST, lastPrice));
                                    mEsperEvtProcessor.sendEvent(new TickPriceEvent(s.getSerialno(), com.ib.client.TickType.TRADEDVALUE, lastPrice*lastSize));
                                }
                                }
                            } else {
                                mEsperEvtProcessor.sendEvent(new ADREvent(ADRTickType.INDEX, s.getTimeSeries().get(EnumBarSize.ONESECOND).get(closeIndex, index)));
                                if (tradingStarted && !tradingEnded) {
                                    Parameters.symbol.get(ADRManager.compositeID).setTimeSeries(EnumBarSize.ONESECOND, currentDateTime.getTime(), new String[]{"movebackward"}, new double[]{moveBackward.get(index)});
                                    Parameters.symbol.get(ADRManager.compositeID).setTimeSeries(EnumBarSize.ONESECOND, currentDateTime.getTime(), new String[]{"movebackwardtime"}, new double[]{moveBackwardTime.get(index)});
                                    Parameters.symbol.get(ADRManager.compositeID).setTimeSeries(EnumBarSize.ONESECOND, currentDateTime.getTime(), new String[]{"moveforward"}, new double[]{moveForward.get(index)});
                                    Parameters.symbol.get(ADRManager.compositeID).setTimeSeries(EnumBarSize.ONESECOND, currentDateTime.getTime(), new String[]{"moveforwardtime"}, new double[]{moveForwardTime.get(index)});
                                    Parameters.symbol.get(ADRManager.compositeID).setTimeSeries(EnumBarSize.ONESECOND, currentDateTime.getTime(), new String[]{"index"}, new double[]{s.getTimeSeries().get(EnumBarSize.ONESECOND).get(closeIndex, index)});
                                    mEsperEvtProcessor.sendEvent(new ADREvent(ADRTickType.T_MOVE, moveBackward.get(index)));
                                    mEsperEvtProcessor.sendEvent(new ADREvent(ADRTickType.T_TIME, moveBackwardTime.get(index)));
                                }
                            }
                        }
                    }
                }
            }
        }
        tradingStarted=Boolean.FALSE;
        tradingEnded=Boolean.FALSE;
        mEsperEvtProcessor.destroy();
    }

    /**
     * Calculates direction of forwarding looking move
     *
     * @param startIndex
     * @param endIndex
     * @param s
     * @param barSize
     * @param move
     * @return
     */
    public DoubleMatrix calculateMoveForward(int startIndex, int endIndex, BeanSymbol s, EnumBarSize barSize, double move) {
        DoubleMatrix out = DoubleMatrix.ones(1, endIndex - startIndex);
        for (int i = startIndex; i < endIndex; i++) {
            double entryPrice = s.getTimeSeries().get(barSize).get(closeIndex, i);
            double upTarget = entryPrice + move;
            double downTarget = entryPrice - move;
            Range r = new IntervalRange(i, endIndex);
            DoubleMatrix m = s.getTimeSeries().get(barSize).get(closeIndex, r);
            int[] upTargetIndices = m.ge(upTarget).findIndices();
            int[] downTargetIndices = m.le(downTarget).findIndices();
            if (upTargetIndices.length != 0 && downTargetIndices.length != 0) {
                if (upTargetIndices[0] > downTargetIndices[0]) {
                    //downmove happened first. Set matrix
                    out.put(i, 0);
                } else {
                    out.put(i, 1);
                }
            } else if (upTargetIndices.length != 0) {
                out.put(i, 1);
            } else if (downTargetIndices.length != 0) {
                out.put(i, 0);
            } else { //find the max move size
                DoubleMatrix temp = s.getTimeSeries().get(barSize).get(closeIndex, r);
                double upMove = temp.max() - entryPrice;
                double downMove = entryPrice - temp.min();
                if (upMove > downMove) {
                    out.put(i, 1);
                } else {
                    out.put(i, 0);
                }
            }
        }
        return out;
    }

    /**
     * Calculates time for forwarding looking move
     *
     * @param startIndex
     * @param endIndex
     * @param s
     * @param barSize
     * @param move
     * @return
     */
    public DoubleMatrix calculateMoveForwardTime(int startIndex, int endIndex, BeanSymbol s, EnumBarSize barSize, double move) {
        DoubleMatrix out = DoubleMatrix.ones(1, endIndex - startIndex);
        for (int i = startIndex; i < endIndex; i++) {
            double entryPrice = s.getTimeSeries().get(barSize).get(closeIndex, i);
            double upTarget = entryPrice + move;
            double downTarget = entryPrice - move;
            Range r = new IntervalRange(i, endIndex);
            DoubleMatrix m = s.getTimeSeries().get(barSize).get(closeIndex, r);
            int[] upTargetIndices = m.ge(upTarget).findIndices();
            int[] downTargetIndices = m.le(downTarget).findIndices();
            if (upTargetIndices.length != 0 && downTargetIndices.length != 0) {
                if (upTargetIndices[0] > downTargetIndices[0]) {
                    //downmove happened first. Set matrix
                    out.put(i, downTargetIndices[0]);
                } else {
                    out.put(i, upTargetIndices[0]);
                }
            } else if (upTargetIndices.length != 0) {
                out.put(i, upTargetIndices[0]);
            } else if (downTargetIndices.length != 0) {
                out.put(i, downTargetIndices[0]);
            } else { //find the max move size
                DoubleMatrix temp = s.getTimeSeries().get(barSize).get(closeIndex, r);
                double tempMax=temp.max();
                double tempMin=temp.min();
                double upMove = tempMax - entryPrice;
                double downMove = entryPrice - tempMin;
                if (upMove > downMove) {
                    int index=temp.eq(tempMax).findIndices()[0];
                    out.put(i, index);
                } else {
                    int index=temp.eq(tempMin).findIndices()[0];
                    out.put(i, index);
                }
            }
        }
        return out;
    }

    /**
     * Calculates the direction of the historical move
     *
     * @param startIndex
     * @param endIndex
     * @param s
     * @param barSize
     * @param move
     * @return
     */
    public DoubleMatrix calculateMoveBackward(int startIndex, int endIndex, BeanSymbol s, EnumBarSize barSize, double move) {
        DoubleMatrix out = DoubleMatrix.ones(1, endIndex - startIndex);
        for (int i = startIndex; i < endIndex; i++) {
            double entryPrice = s.getTimeSeries().get(barSize).get(closeIndex, i);
            double upTarget = entryPrice - move;
            double downTarget = entryPrice + move;
            Range r = new IntervalRange(startIndex, i);
            DoubleMatrix m = s.getTimeSeries().get(barSize).get(closeIndex, r);
            int[] upTargetIndices = m.le(upTarget).findIndices();
            int[] downTargetIndices = m.ge(downTarget).findIndices();
            if (upTargetIndices.length != 0 && downTargetIndices.length != 0) {
                int upTargetLength=upTargetIndices.length;
                int downTargetLength=downTargetIndices.length;
                if (upTargetIndices[upTargetLength-1] > downTargetIndices[downTargetLength-1]) {
                    //upmove happened first. Set matrix
                    out.put(i, 1);
                } else {
                    out.put(i, 0);
                }
            } else if (upTargetIndices.length != 0) {
                out.put(i, 1);
            } else if (downTargetIndices.length != 0) {
                out.put(i, 0);
            } else { //find the max move size
                DoubleMatrix temp = s.getTimeSeries().get(barSize).get(closeIndex, r);
                if (temp.isEmpty()) {
                    out.put(i, 1);
                } else {
                    double upMove = temp.max() - entryPrice;
                    double downMove = entryPrice - temp.min();
                    if (upMove < downMove) {
                        out.put(i, 1);
                    } else {
                        out.put(i, 0);
                    }
                }
            }
        }
        return out;
    }

    /**
     * Calculates the time for the historical move
     *
     * @param startIndex
     * @param endIndex
     * @param s
     * @param barSize
     * @param move
     * @return
     */
    public DoubleMatrix calculateMoveBackwardTime(int startIndex, int endIndex, BeanSymbol s, EnumBarSize barSize, double move) {
        DoubleMatrix out = DoubleMatrix.ones(1, endIndex - startIndex);
        for (int i = startIndex; i < endIndex; i++) {
            double entryPrice = s.getTimeSeries().get(barSize).get(closeIndex, i);
            double upTarget = entryPrice - move;
            double downTarget = entryPrice + move;
            Range r = new IntervalRange(startIndex, i);
            DoubleMatrix m = s.getTimeSeries().get(barSize).get(closeIndex, r);
            int[] upTargetIndices = m.le(upTarget).findIndices();
            int[] downTargetIndices = m.ge(downTarget).findIndices();
            if (upTargetIndices.length != 0 && downTargetIndices.length != 0) {
                int upTargetLength=upTargetIndices.length;
                int downTargetLength=downTargetIndices.length;
                if (upTargetIndices[upTargetLength-1] > downTargetIndices[downTargetLength-1]) {
                    //upmove happened first. Set matrix
                    out.put(i, i - upTargetIndices[upTargetLength-1]);
                } else {
                    out.put(i, i - downTargetIndices[downTargetLength-1]);
                }
            } else if (upTargetIndices.length != 0) {
                int upTargetLength=upTargetIndices.length;
                out.put(i, i - upTargetIndices[upTargetLength-1]);
            } else if (downTargetIndices.length != 0) {
                int downTargetLength=downTargetIndices.length;
                out.put(i, i - downTargetIndices[downTargetLength-1]);
            } else { //find the max move size
                DoubleMatrix temp = s.getTimeSeries().get(barSize).get(closeIndex, r);
                if (temp.isEmpty()) {
                    out.put(i, 1);
                } else {
                    double tempMax=temp.max();
                    double tempMin=temp.min();
                    double upMove = tempMax- entryPrice;
                    double downMove = entryPrice - tempMin;
                    if (upMove < downMove) {
                        int[] indices=temp.eq(tempMin).findIndices();
                        int indicesLength=indices.length;
                        out.put(i, i-indices[indicesLength-1]);
                    } else {
                        int[] indices=temp.eq(tempMax).findIndices();
                        int indicesLength=indices.length;
                        out.put(i, i-indices[indicesLength-1]);
                    }
                }
            }
        }
        return out;
    }

    /**
     *
     */
    public DoubleMatrix calculateMFEMAE(int startIndex, BeanSymbol s, boolean reuse) {
        double entryPrice = s.getTimeSeries().get(EnumBarSize.ONESECOND).get(closeIndex, startIndex);
        IntervalRange range = new IntervalRange(startIndex, s.getColumnLabels().get(EnumBarSize.ONESECOND).indexOf(endTime));
        DoubleMatrix tempMFEMAE = mfemae.get(new IntervalRange(0, 2), range);
        return tempMFEMAE.sub(entryPrice - this.baselineEntryPrice);
    }

    public double[][] calculateMFEMAE(int startIndex, BeanSymbol s) {
        double entryPrice = s.getTimeSeries().get(EnumBarSize.ONESECOND).get(closeIndex, startIndex);
        int endIndex = s.getColumnLabels().get(EnumBarSize.ONESECOND).indexOf(endTime);
        int size = endIndex - startIndex + 1;
        double barMAE = 0D;
        double barMFE = 0D;
        double[] tempMFE = new double[size - startIndex];
        double[] tempMAE = new double[size - startIndex];
        int i = 0;
        for (double close : s.getTimeSeries().get(EnumBarSize.ONESECOND).get(closeIndex, new IntervalRange(startIndex, endIndex)).data) {
            double change = close - entryPrice;
            if (change > 0) {
                if (barMFE < change) {
                    barMFE = change;
                    tempMFE[i] = barMFE;
                    if (i > 0) {
                        tempMAE[i] = tempMAE[i - 1];
                    } else {
                        tempMAE[i] = 0;
                    }
                } else {
                    tempMFE[i] = tempMFE[i - 1];
                    tempMAE[i] = tempMAE[i - 1];
                }
            } else if (change < 0) {
                if (barMAE < Math.abs(change)) {
                    barMAE = Math.abs(change);
                    tempMAE[i] = barMAE;
                    if (i > 0) {
                        tempMFE[i] = tempMFE[i - 1];
                    } else {
                        tempMFE[i] = 0;
                    }
                } else {
                    tempMFE[i] = tempMFE[i - 1];
                    tempMAE[i] = tempMAE[i - 1];
                }
            } else {
                if (i > 0) {
                    tempMFE[i] = tempMFE[i - 1];
                    tempMAE[i] = tempMAE[i - 1];
                } else {
                    tempMFE[i] = 0;
                    tempMAE[i] = 0;
                }

            }
            i = i + 1;
        }
        this.mfemae = new DoubleMatrix(new double[][]{tempMFE, tempMAE});
        this.baselineEntryPrice = entryPrice;
        return new double[][]{tempMFE, tempMAE};
    }

    public int calculateY(DoubleMatrix mfemae) {
        //calculate index of y=1
        int y = -1;
        int bar = -1;
        int longPos = -1;
        int longBar = -1;
        int shortPos = -1;
        int shortBar = -1;
        DoubleMatrix longMatrix = new DoubleMatrix();
        longMatrix.copy(mfemae);//[2row ,n columns]
        int longMFEIndex = findGreaterEfficient(longMatrix, 0, 40);
        int longMAEIndex = findGreaterEfficient(longMatrix, 1, 20);

        //Condition 1: y1 is set to equal the bar of MFE (if MFE occurs before MAE) or else the bar of MAE (if MAE occurs after MFE)
        //Condition 2:if MFE is not present, MFE is very high and trade should not be entered. y1 is set to a high value.
        // Condition 3:If MAE is not present, MAE is very high, MFE is deemed to occur before MAE. Condition 3 => condition 1
        //Condition 4: If both MFE and MAE are not present, the equality condition should result in y=5
        int y1 = (longMFEIndex == ADRTickType.NOMFEMAE && longMAEIndex != ADRTickType.NOMFEMAE) ? 1000000 : longMFEIndex < longMAEIndex ? longMFEIndex : longMAEIndex;
        int y2 = longMAEIndex;
        if (y1 > y2) {//y2 occurs first. This is a SELL sign
            longPos = 2;
            longBar = y2;
        } else if (y1 < y2) {//y1 occurs first. This is a BUY Sign
            longPos = 1;
            longBar = y1;
        }
        DoubleMatrix shortMatrix = new DoubleMatrix();
        shortMatrix.copy(longMatrix);
        shortMatrix.putRow(0, longMatrix.getRow(1));
        shortMatrix.putRow(1, longMatrix.getRow(0));
        int shortMFEIndex = findGreaterEfficient(shortMatrix, 0, 40);
        int shortMAEIndex = findGreaterEfficient(shortMatrix, 1, 20);
        int y3 = (shortMFEIndex == ADRTickType.NOMFEMAE && shortMAEIndex != ADRTickType.NOMFEMAE) ? 1000000 : shortMFEIndex < shortMAEIndex ? shortMFEIndex : shortMAEIndex;
        int y4 = shortMAEIndex;
        if (y3 > y4) {
            shortPos = 4;
            shortBar = y4;
        } else if (y3 < y4) {
            shortPos = 3;
            shortBar = y3;
        }
        if (longPos == 1 && y1 < y3) {
            y = 1;
        } else if (shortPos == 3 && y3 < y1) {
            y = 3;
        } else if (y4 < y2) {
            y = 4;
        } else if (y4 > y2) {
            y = 2;
        } else {
            y = 5;
        }
        return y;
    }

    public int[] findGreater(DoubleMatrix m, int row, double value) {
        int[] out = new int[2];
        for (int i = 0; i < m.getRow(row).length; i++) {
            if (value < m.get(row, i)) {
                out[1] = i;
                out[0] = row;
                break;
            } else {
                out[1] = ADRTickType.NOMFEMAE;
                out[0] = row;
            }
        }

        return out;
    }

    public int findGreaterEfficient(DoubleMatrix m, int row, double value) {
        DoubleMatrix result = m.getRow(row).ge(value);
        if (result.findIndices().length > 0) {
            return result.findIndices()[0];
        } else {
            return 1000000;
        }
    }

    private void binarySearch(double[] tmpArray, double target) {
        int low = 0;
        int high = tmpArray.length - 1;

        while (low != high) {
            int mid = (low + high) / 2; // Or a fancy way to avoid int overflow
            if (tmpArray[mid] <= target) {
                /* This index, and everything below it, must not be the first element
                 * greater than what we're looking for because this element is no greater
                 * than the element.
                 */
                low = mid + 1;
            } else {
                /* This element is at least as large as the element, so anything after it can't
                 * be the first element that's at least as large.
                 */
                high = mid;
            }
        }
    }
}
