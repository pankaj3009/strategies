/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package TurtleTrading;

import incurrframework.*;
import incurrframework.BeanOHLC;
import incurrframework.BeanSymbol;
import incurrframework.DateUtil;
import incurrframework.OrderSide;
import incurrframework.Parameters;
import incurrframework.TradeEvent;
import incurrframework.TradeListner;
import incurrframework.TradingUtil;
import java.beans.*;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Timer;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

/**
 *
 * @author pankaj
 */
public class BeanSwing implements Serializable, TradeListner {

    private ArrayList<ArrayList<BeanOHLC>>  ohlcv = new <ArrayList<BeanOHLC>> ArrayList();  //algo parameter 
    private ArrayList<ArrayList<Boolean>> swingHigh = new <ArrayList<Boolean>> ArrayList();  //algo parameter 
    private ArrayList<ArrayList<Boolean>> swingLow = new <ArrayList<Boolean>> ArrayList();  //algo parameter 
    private ArrayList<Integer> trend = new <Integer> ArrayList();  //algo parameter 
    private MainAlgorithm m;
    private Date algoStartDate;
    private Date marketOpenDate;
    private Date marketCloseDate;
    private String tickSize;
    private String exit;
    private OrderPlacement ordManagement;
    private final static Logger logger = Logger.getLogger(Algorithm.class.getName());
    Timer preopenProcessing;

    public BeanSwing(MainAlgorithm m) {
        this.m = m;
        Properties p = new Properties(System.getProperties());
        FileInputStream propFile;
        try {
            propFile = new FileInputStream("Swing.properties");
            try {
                p.load(propFile);
            } catch (IOException ex) {
                Logger.getLogger(BeanTurtle.class.getName()).log(Level.SEVERE, null, ex);
            }
        } catch (FileNotFoundException ex) {
            Logger.getLogger(BeanTurtle.class.getName()).log(Level.SEVERE, null, ex);
        }
        System.setProperties(p);
        String currDateStr = DateUtil.getFormatedDate("yyyyMMdd", Parameters.connection.get(0).getConnectionTime());
        String AlgoStartStr = currDateStr + " " + System.getProperty("AlgoStart");
        String MarketOpenStr = currDateStr + " " + System.getProperty("MarketOpen");
        String MarketCloseStr = currDateStr + " " + System.getProperty("MarketClose");
       
        tickSize = System.getProperty("TickSize");
        algoStartDate = DateUtil.parseDate("yyyyMMdd HH:mm:ss", AlgoStartStr);
        marketOpenDate = DateUtil.parseDate("yyyyMMdd HH:mm:ss", MarketOpenStr);
        marketCloseDate = DateUtil.parseDate("yyyyMMdd HH:mm:ss", MarketCloseStr);
        if (marketCloseDate.compareTo(marketOpenDate) < 0 && new Date().compareTo(marketOpenDate) > 0) {
            //increase enddate by one calendar day
            marketCloseDate = DateUtil.addDays(marketCloseDate, 1); //system date is > start date time. Therefore we have not crossed the 12:00 am barrier
        } else if (marketCloseDate.compareTo(marketOpenDate) < 0 && new Date().compareTo(marketOpenDate) < 0) {
            marketOpenDate = DateUtil.addDays(marketOpenDate, -1); // we have moved beyond 12:00 am . adjust startdate to previous date
        }
        exit = System.getProperty("Exit");
        
        for (int i = 0; i < Parameters.symbol.size(); i++) {
            
        }
        Parameters.addTradeListener(this);
        
        try {
            Class.forName("com.mysql.jdbc.Driver");
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(BeanSwing.class.getName()).log(Level.SEVERE, null, ex);
        }
          for (BeanSymbol s:Parameters.symbol){
              ArrayList<BeanOHLC> temp=TradingUtil.getDailyBarsFromOneMinCandle(90,s.getSymbol()+"_FUT");
              ohlcv.add(temp);
          }
        
        
        
        
        preopenProcessing=new Timer();
        long t=m.getPreopenDate().getTime();
        Date tempDate=new Date(t+1*60000);// process one minute after preopen time.
        //preopenProcessing.schedule(new BeanGudsPreOpen(this), tempDate);
    }

        
    private void calculateSD() {
        Connection connect = null;
        Statement statement = null;
        PreparedStatement preparedStatement = null;
        ResultSet rs = null;


        try {

            connect = DriverManager.getConnection("jdbc:mysql://localhost:3306/histdata", "root", "spark123");
            //statement = connect.createStatement();
            for (int j = 0; j < Parameters.symbol.size(); j++) {
                String name = Parameters.symbol.get(j).getSymbol() + "_FUT";
                preparedStatement = connect.prepareStatement("select * from dharasymb where name=? order by date asc");
                preparedStatement.setString(1, name);
                rs = preparedStatement.executeQuery();
                //parse and create one minute bars
                Date priorDate = null;
                Long volume = 0L;
                Double close = 0D;
                Double high = Double.MIN_VALUE;
                Double low = Double.MAX_VALUE;
                ArrayList<Double> returns = new ArrayList<Double>();
                ArrayList<Double> histclose = new ArrayList<Double>();
                ArrayList<Double> histlow = new ArrayList<Double>();
                ArrayList<Double> histhigh = new ArrayList<Double>();
                ArrayList<Long> histvolume = new ArrayList<Long>();
                System.out.println("Symbol:" + Parameters.symbol.get(j).getSymbol());
                while (rs.next()) {
                    priorDate = priorDate == null ? rs.getDate("date") : priorDate;
                    //String name = rs.getString("name");
                    Date date = rs.getDate("date");
                    Date datetime = rs.getTimestamp("date");
                    if (date.compareTo(priorDate) > 0 && date.compareTo(DateUtil.addDays(new Date(), -150)) > 0) {
                        //new bar has started
                        priorDate = date;
                        String formattedDate = DateUtil.getFormatedDate("yyyyMMdd hh:mm:ss", datetime.getTime());
                        histclose.add(close);
                        histlow.add(low);
                        histhigh.add(high);
                        if (histclose.size() > 1) {
                            returns.add((close - histclose.get(histclose.size() - 2)) / histclose.get(histclose.size() - 2));
                        }
                        histvolume.add(volume);
                        volume = rs.getLong("volume");

                    } else {
                        volume = volume + rs.getLong("volume");
                        close = rs.getDouble("tickclose");
                        high = rs.getDouble("high");
                        low = rs.getDouble("low");
                    }
                }
                rs.close();
                List<Double> sublist = returns.size()>90? returns.subList(returns.size() - 90, returns.size()):null;
                if(sublist!=null){
                double[] sample = new double[sublist.size()];
                int i = 0;
                DescriptiveStatistics stats = new DescriptiveStatistics();
                for (double value : sublist) {
                    sample[i] = value;
                    stats.addValue(value);
                    i = i + 1;
                }

            }
            }
        } catch (SQLException ex) {
            Logger.getLogger(BeanSwing.class.getName()).log(Level.SEVERE, null, ex);
        }


    }
    
    @Override
    public void tradeReceived(TradeEvent event) {
        int id = event.getSymbolID() - 1; //here symbolID is with zero base.

    }

 
}
