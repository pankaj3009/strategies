/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.algorithms.swing;

import com.incurrency.algorithms.pairs.Pairs;
import com.incurrency.RatesClient.Subscribe;
import com.incurrency.algorithms.launch.Launch;
import com.incurrency.framework.Algorithm;
import com.incurrency.framework.BeanConnection;
import com.incurrency.framework.BeanPosition;
import com.incurrency.framework.BeanSymbol;
import com.incurrency.framework.DateUtil;
import com.incurrency.framework.EnumBarSize;
import com.incurrency.framework.EnumOrderReason;
import com.incurrency.framework.EnumOrderSide;
import com.incurrency.framework.EnumOrderStage;
import com.incurrency.framework.EnumOrderType;
import com.incurrency.framework.EnumSource;
import com.incurrency.framework.HistoricalBars;
import com.incurrency.framework.MainAlgorithm;
import com.incurrency.framework.Parameters;
import com.incurrency.framework.Strategy;
import com.incurrency.framework.TradeEvent;
import com.incurrency.framework.TradeListener;
import com.incurrency.framework.TradingUtil;
import com.incurrency.framework.Utilities;
import com.incurrency.indicators.Indicators;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Properties;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.math3.stat.StatUtils;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.util.FastMath;
import org.jblas.DoubleMatrix;


/**
 *
 * @author pankaj
 */
public class Swing extends Strategy implements TradeListener {

    private static final Logger logger = Logger.getLogger(Pairs.class.getName());
    String symbol;
    String type;
    String expiry;
    String nextExpiry;
    String rServerIP;
    Date entryScanDate;        
    double stopLoss;
    double takeProfit;
    double upProbabilityThreshold;
    double downProbabilityThreshold;
    String cassandraMetric;
    String[] timeSeries;

    public Swing(MainAlgorithm m,Properties p, String parameterFile, ArrayList<String> accounts, Integer stratCount) {
        super(m, "swing", "FUT", p,parameterFile, accounts,stratCount);
        loadParameters(p);
        
        TradingUtil.writeToFile(getStrategy() + ".csv","comma seperated header columns ");
        
        String[] tempStrategyArray = parameterFile.split("\\.")[0].split("-");
        for (BeanConnection c : Parameters.connection) {
            c.getWrapper().addTradeListener(this);
            c.initializeConnection(tempStrategyArray[tempStrategyArray.length - 1]);
        }
        if (Subscribe.tes != null) {
            Subscribe.tes.addTradeListener(this);
        }
        Calendar calToday=Calendar.getInstance(TimeZone.getTimeZone(Algorithm.timeZone));
        String hdEndDate=DateUtil.getFormatedDate("yyyyMMdd HH:mm:ss", calToday.getTimeInMillis(), TimeZone.getTimeZone(Algorithm.timeZone));
        calToday.add(Calendar.YEAR, -5);
        String hdStartDate=DateUtil.getFormatedDate("yyyyMMdd HH:mm:ss", calToday.getTimeInMillis(), TimeZone.getTimeZone(Algorithm.timeZone));        
        Thread t = new Thread(new HistoricalBars("swing", "IND",EnumSource.CASSANDRA,timeSeries,cassandraMetric,hdStartDate,hdEndDate,EnumBarSize.DAILY,false));
        t.setName("Historical Bars");
        logger.log(Level.INFO,"Historical Request Started");
        t.start();
    }

    private void loadParameters(Properties p) {
        symbol=p.getProperty("Symbol");
        type=p.getProperty("Type");
        expiry=p.getProperty("Expiry");
        nextExpiry=p.getProperty("NextExpiry");
        rServerIP=p.getProperty("RServerIP"); 
        String entryScanTime=p.getProperty("EntryScanTime");
        Calendar calToday=Calendar.getInstance(TimeZone.getTimeZone(Algorithm.timeZone));
        String[] entryTimeComponents=entryScanTime.split(":");
        calToday.set(Calendar.HOUR, Utilities.getInt(entryTimeComponents[0],15));
        calToday.set(Calendar.MINUTE, Utilities.getInt(entryTimeComponents[1],20));
        calToday.set(Calendar.SECOND, Utilities.getInt(entryTimeComponents[2],0));
        entryScanDate=calToday.getTime();
        stopLoss=Utilities.getDouble(p.getProperty("StopLoss", "0.01"),0.01);
        takeProfit=Utilities.getDouble(p.getProperty("TakeProfit", "0.05"),0.05);
        upProbabilityThreshold=Utilities.getDouble(p.getProperty("UpProbabilityThreshold", "0.70"),0.7);
        downProbabilityThreshold=Utilities.getDouble(p.getProperty("DownProbabilityThreshold", "0.3"),0.3);       
        timeSeries=p.getProperty("timeseries","").toString().trim().split(",");
        cassandraMetric=p.getProperty("cassandrametric","").toString().trim();
    }

    @Override
    public void tradeReceived(TradeEvent event) {
        Integer id=event.getSymbolID()-1;
        if(this.getStrategySymbols().contains(id)){
            if(this.getPosition().get(id).getPosition()>0){
                Double tradePrice=this.getPosition().get(id).getPrice();
                if(Parameters.symbol.get(id).getLastPrice()<=tradePrice*(1-stopLoss)){
                    int size=this.getPosition().get(id).getPosition();
                    this.entry(id, EnumOrderSide.SELL, size, EnumOrderType.LMT, Parameters.symbol.get(id).getLastPrice(), 0, EnumOrderReason.REGULAREXIT, EnumOrderStage.INIT, this.getMaxOrderDuration(), this.getDynamicOrderDuration(), this.getMaxSlippageExit(), "", "GTC", "", false, true);
                }
            }
            
            if(new Date().compareTo(entryScanDate)>0 && Parameters.symbol.get(id).getTimeSeriesLength(EnumBarSize.DAILY)>0){
                BeanSymbol s=Parameters.symbol.get(id);
                DoubleMatrix dtrend=Indicators.swing(s,EnumBarSize.DAILY).getTimeSeries(EnumBarSize.DAILY, "trend");
                DoubleMatrix dclose=s.getTimeSeries(EnumBarSize.DAILY, "settle");
                DoubleMatrix dhigh=s.getTimeSeries(EnumBarSize.DAILY, "high");
                DoubleMatrix dlow=s.getTimeSeries(EnumBarSize.DAILY, "low");
                DoubleMatrix ddaysinupswing=s.getTimeSeries(EnumBarSize.DAILY, "daysinupswing");
                DoubleMatrix ddaysindownswing=s.getTimeSeries(EnumBarSize.DAILY, "daysindownswing");
                DoubleMatrix ddaysoutsidetrend=s.getTimeSeries(EnumBarSize.DAILY, "daysoutsidetrend");
                DoubleMatrix ddaysintrend=s.getTimeSeries(EnumBarSize.DAILY, "daysintrend");
                DoubleMatrix ddaysindowntrend=s.getTimeSeries(EnumBarSize.DAILY, "daysindowntrend");
                DoubleMatrix ddaysinuptrend=s.getTimeSeries(EnumBarSize.DAILY, "daysinuptrend");
                DoubleMatrix dstickytrend=s.getTimeSeries(EnumBarSize.DAILY, "stickytrend");
                DoubleMatrix dfliptrend=s.getTimeSeries(EnumBarSize.DAILY, "fliptrend");
                DoubleMatrix dupdownbar=s.getTimeSeries(EnumBarSize.DAILY, "updownbar");
                DoubleMatrix dupdownbarclean=s.getTimeSeries(EnumBarSize.DAILY, "updownbarclean");
                double stddev=FastMath.sqrt(StatUtils.variance(dclose.data));
                DoubleMatrix dma_10=Indicators.ma(dclose, 10);
                DoubleMatrix dstd_10=Indicators.stddev(dclose, 10);
                DoubleMatrix dclosezscore=(dclose.sub(dma_10)).div(dstd_10);
                

                //calculate indicators and check probability
                
                
            }
            
        }
    }
}
