/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.algorithms.deltaneutral;

//import com.google.common.collect.Iterables;
//import com.google.common.collect.Lists;
import com.incurrency.framework.BeanConnection;
import com.incurrency.framework.BeanOHLC;
import com.incurrency.framework.BeanSymbol;
import com.incurrency.framework.BidAskEvent;
import com.incurrency.framework.BidAskListener;
import com.incurrency.framework.DateUtil;
import com.incurrency.framework.HistoricalBarEvent;
import com.incurrency.framework.HistoricalBarListener;
import com.incurrency.framework.HistoricalBars;
import com.incurrency.framework.MainAlgorithm;
import com.incurrency.algorithms.launch.Launch;
import com.incurrency.framework.EnumSource;
import com.incurrency.framework.Parameters;
import com.incurrency.framework.Splits;
import com.incurrency.framework.Trade;
import com.incurrency.framework.TradeEvent;
import com.incurrency.framework.TradeListener;
import com.incurrency.framework.TradingUtil;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

/**
 *
 * @author pankaj
 */
public class BeanDNO implements TradeListener, BidAskListener, HistoricalBarListener {

    private MainAlgorithm m;
    private static final Logger logger = Logger.getLogger(BeanDNO.class.getName());
    HashMap<Integer, Trade> trades = new HashMap();
    Double volatilityMarkup;
    Double zscore;
    Boolean trading;
    Date endDate;
    int numberOfContracts;
    double takeProfit;
    double stopLoss;
    double tickSize;
    Boolean aggression;
    int totalOrderDuration;
    int dynamicOrderDuration;
    int maxOpenPositions = 0;
    int transactionCostPerCombination = 0;
//    DeltaNeutralOrderManagement ord;
    String split;
    private ArrayList<Splits> splits = new ArrayList();
    private ArrayList<TreeMap<Long, BeanOHLC>> historicalData = new ArrayList();
    private ArrayList<Double> dailyReturns=new ArrayList();
    private ArrayList<Double> opencloseReturns=new ArrayList();
    private ArrayList<ArrayList<Double>> sevenDayMeanReturn= new ArrayList<>();
    private ArrayList<ArrayList<Double>> oneMonthMeanReturn= new ArrayList<>();
    private ArrayList<ArrayList<Double>> sevenDayHistoricalVol= new ArrayList<>();
    private ArrayList<ArrayList<Double>> oneMonthHistoricalVol= new ArrayList<>();
    private ArrayList<ArrayList<Double>> sevenDayOpenCloseMeanReturn= new ArrayList<>();
    private ArrayList<ArrayList<Double>> oneMonthOpenCloseMeanReturn= new ArrayList<>();
    private ArrayList<ArrayList<Double>> sevenDayOpenCloseHistoricalVol= new ArrayList<>();
    private ArrayList<ArrayList<Double>> oneMonthOpenCloseHistoricalVol= new ArrayList<>();
    private List<Integer> tradeableSymbols = new ArrayList();

    

    public BeanDNO(MainAlgorithm m) {
        this.m=m;
//        ord = new DeltaNeutralOrderManagement(aggression, tickSize, endDate,"dno",1,"");
        for (BeanConnection c : Parameters.connection) {
            c.getWrapper().addTradeListener(this);
            c.getWrapper().addBidAskListener(this);
            c.initializeConnection("dno");
        }
        int i=0;
        for (BeanSymbol s : Parameters.symbol) {
            if (s.getStrategy().contains("dno")) {
                tradeableSymbols.add(s.getSerialno() - 1);
                historicalData.add(new <Long, BeanOHLC>TreeMap());
                sevenDayMeanReturn.add(i,new <Double>ArrayList());
                this.oneMonthMeanReturn.add(i,new <Double>ArrayList());
                this.sevenDayHistoricalVol.add(i,new <Double>ArrayList());
                this.oneMonthHistoricalVol.add(i,new <Double>ArrayList());
                sevenDayOpenCloseMeanReturn.add(i,new <Double>ArrayList());
                this.oneMonthOpenCloseMeanReturn.add(i,new <Double>ArrayList());
                this.sevenDayOpenCloseHistoricalVol.add(i,new <Double>ArrayList());
                this.oneMonthOpenCloseHistoricalVol.add(i,new <Double>ArrayList());
                i=i+1;
            }
        }
            getDailyHistoricalData("dno","IND");
            getDailyHistoricalData("dno","STK");
            generateEODStats();

        }
    

    
    private void getDailyHistoricalData(String strategy, String type) {
        try {
            //get historical data - this can be done before start time, assuming the program is started next day

            Thread t = new Thread(new HistoricalBars(strategy,type,EnumSource.IB));
            t.setName("Historical Bars");
            if (!Launch.headless) {
//                Launch.setMessage("Starting request of Historical Data for yesterday");
            }
            t.start();
            t.join();
        } catch (InterruptedException ex) {
            logger.log(Level.SEVERE, null, ex);
        }

    }

    private void generateEODStats() {
        /*
         * Generates
         * a. Adjusts for any splits
         * a. Daily Returns. Returns are linked to open date
         * b. Open Returns. Percentage Returns between open and close. Returns are linked to open date
         * c. Calculate mean and sd of daily returns for 3 month and one month
         * d. Calculate mean and sd of open returns for 3 month and one month
         */
        //a. Adjust for splits
        for (Splits s : splits) {
            int id = s.getId();
            if(id>0 && Parameters.symbol.get(id).getDailyBar().getHistoricalBars().size()>0){
            TreeMap<Long, BeanOHLC> hist = Parameters.symbol.get(id).getDailyBar().getHistoricalBars();
            for (Map.Entry<Long, BeanOHLC> entry : hist.entrySet()) {
                if (entry.getKey()<s.getEffectiveDate()) {
                    entry.getValue().setOpen(entry.getValue().getOpen() * s.getOldShares() / s.getNewShares());
                    entry.getValue().setHigh(entry.getValue().getHigh() * s.getOldShares() / s.getNewShares());
                    entry.getValue().setLow(entry.getValue().getLow() * s.getOldShares() / s.getNewShares());
                    entry.getValue().setClose(entry.getValue().getClose() * s.getOldShares() / s.getNewShares());
                    entry.getValue().setVolume(entry.getValue().getVolume() * s.getNewShares() / s.getOldShares());
                }
            }
            }
        }

        int symbolCount = 0;
        for (BeanSymbol symb : Parameters.symbol) {
            if (symb.getDailyBar().getHistoricalBars().size() == 0) {
                this.dailyReturns.add(0D);
                this.opencloseReturns.add(0D);
            } else {
                List<BeanOHLC> lastValues;
//                lastValues = Lists.newArrayList(Iterables.limit(symb.getDailyBar().getHistoricalBars().descendingMap().values(), 300));
                //lastValues = com.google.common.collect.Lists.reverse(lastValues);
//                for (int i = 0; i < lastValues.size() - 1; i++) {
                 //        this.dailyReturns.add(Math.log(lastValues.get(i + 1).getClose()/lastValues.get(i).getClose()));
                 //   this.opencloseReturns.add(Math.log(lastValues.get(i + 1).getOpen()/lastValues.get(i).getClose()));
               // }
                DescriptiveStatistics dailystats7 = new DescriptiveStatistics();
                dailystats7.setWindowSize(7);
                int count = 0;
                for (double value : dailyReturns) {
                    count = count + 1;
                    dailystats7.addValue(value);
                    logger.log(Level.INFO,"Bar: {0}, Daily Return:{1}", new Object[]{count,value});
                    if(count>89){
                       this.sevenDayMeanReturn.get(symbolCount).add(dailystats7.getMean());
                       //TradingUtil.writeToFile("mean.csv", value+","+dailystats7.getMean()+"\n");
                        this.sevenDayHistoricalVol.get(symbolCount).add(Math.sqrt(dailystats7.getVariance()*252));
                        TradingUtil.writeToFile("historical vol.csv", value+","+Math.sqrt(dailystats7.getVariance()*252)+"\n");
                    }
                }
                DescriptiveStatistics dailystats30 = new DescriptiveStatistics();
                dailystats30.setWindowSize(30);
                count = 0;
                for (double value : dailyReturns) {
                    count = count + 1;
                    dailystats30.addValue(value);
                    if (count > 30) {
                        this.oneMonthMeanReturn.get(symbolCount).add(dailystats30.getMean());
                        this.oneMonthHistoricalVol.get(symbolCount).add(Math.sqrt(dailystats30.getVariance()));
                    }
                }
                DescriptiveStatistics openclosestats30 = new DescriptiveStatistics();
                openclosestats30.setWindowSize(30);
                count = 0;
                for (double value : opencloseReturns) {
                    count = count + 1;
                    dailystats30.addValue(value);
                    if (count > 30) {
                        this.oneMonthOpenCloseMeanReturn.get(symbolCount).add(dailystats30.getMean());
                        this.oneMonthOpenCloseHistoricalVol.get(symbolCount).add(Math.sqrt(dailystats30.getVariance()));
                    }
                }

                DescriptiveStatistics openclosestats7 = new DescriptiveStatistics();
                openclosestats7.setWindowSize(7);
                count = 0;
                for (double value : opencloseReturns) {
                    count = count + 1;
                    dailystats7.addValue(value);
                    if (count > 90) {
                        this.sevenDayOpenCloseMeanReturn.get(symbolCount).add(dailystats7.getMean());
                        this.sevenDayOpenCloseHistoricalVol.get(symbolCount).add(Math.sqrt(dailystats7.getVariance()));
                    }
                }
            }
            symbolCount=symbolCount+1;
        }
    }

    @Override
    public void tradeReceived(TradeEvent event) {
        /*
        int id = event.getSymbolID() - 1;
        if (tradeableSymbols.contains(id) && Parameters.symbol.get(id).getType().compareTo("OPT") == 0) {
            //Entry
            int temp1MVolArraySize=this.oneMonthHistoricalVol.size();
            int temp7DVolArraySize=this.sevenDayHistoricalVol.size();
            int callid=Parameters.symbol.get(id).getRight().compareTo("CALL")==0?id:TradingUtil.getEntryIDFromSymbol(Parameters.symbol.get(id).getSymbol(), "OPT", Parameters.symbol.get(id).getExpiry(), "CALL", Parameters.symbol.get(id).getOption());
            int putid=Parameters.symbol.get(id).getRight().compareTo("PUT")==0?id:TradingUtil.getEntryIDFromSymbol(Parameters.symbol.get(id).getSymbol(), "OPT", Parameters.symbol.get(id).getExpiry(), "PUT", Parameters.symbol.get(id).getOption());
            int underlyingid=TradingUtil.getEntryIDFromSymbol(Parameters.symbol.get(id).getSymbol(), "STK", "", "","")>=0?TradingUtil.getEntryIDFromSymbol(Parameters.symbol.get(id).getSymbol(), "STK", "", "",""):TradingUtil.getEntryIDFromSymbol(Parameters.symbol.get(id).getSymbol(), "IND", "", "","");
            
            //Call condition met
            Boolean callCondition= (Parameters.symbol.get(callid).getBidVol()/this.oneMonthHistoricalVol.get(underlyingid).get(temp1MVolArraySize-1)>2)
                && (Parameters.symbol.get(callid).getBidVol()/this.sevenDayHistoricalVol.get(underlyingid).get(temp1MVolArraySize-1)>2)
                && this.sevenDayHistoricalVol.get(underlyingid).get(temp7DVolArraySize-1)<this.oneMonthHistoricalVol.get(underlyingid).get(temp1MVolArraySize-1);
 
            Boolean putCondition= (Parameters.symbol.get(putid).getBidVol()/this.oneMonthHistoricalVol.get(underlyingid).get(temp1MVolArraySize-1)>2)
                && (Parameters.symbol.get(putid).getBidVol()/this.sevenDayHistoricalVol.get(underlyingid).get(temp1MVolArraySize-1)>2)
                && this.sevenDayHistoricalVol.get(underlyingid).get(temp7DVolArraySize-1)<this.oneMonthHistoricalVol.get(underlyingid).get(temp1MVolArraySize-1);
            
             {
                //Condition exists to take a position
            }
            //option (both call and put) vol/1 month vol>2 && option vol/3 month vol >2 and 1 month vol < historical 3 m vol 
            //buy sell option
            //buy or sell future
            //update combination position.
            //Exit
            //if market closing and position is in profit, exit
            //else write position to log file
            //
        }
        */
    }

    @Override
    public void bidaskChanged(BidAskEvent event) {
        //throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void barsReceived(HistoricalBarEvent event) {
        /*Dont need this code as bars are stored in symbol
        int id = event.getSymbol().getSerialno() - 1;
        if (event.ohlc().getPeriodicity() == EnumBarSize.Daily && tradeableSymbols.contains(event.getSymbol().getSerialno() - 1)) {
            //bars are thrown starting from first day in the range to the last day in range
            historicalData.get(id).put(event.ohlc().getOpenTime(), new BeanOHLC(event.ohlc()));
        }*/
    }
    
}
