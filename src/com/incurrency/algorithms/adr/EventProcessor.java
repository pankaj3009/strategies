/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.incurrency.algorithms.adr;

import com.espertech.esper.client.Configuration;
import com.espertech.esper.client.EPOnDemandQueryResult;
import com.espertech.esper.client.EPRuntime;
import com.espertech.esper.client.EPServiceProvider;
import com.espertech.esper.client.EPServiceProviderManager;
import com.espertech.esper.client.EPStatement;
import com.espertech.esper.client.EventBean;
import com.incurrency.framework.TradingUtil;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.util.logging.Logger;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.WindowConstants;
/**
 *
 * @author Admin
 */
public class EventProcessor implements ActionListener {

    private EPServiceProvider esperEngine;
    private static final Logger logger = Logger.getLogger(EventProcessor.class.getName());
    public EPStatement ADRStatement=null;
    public ADR adrStrategy;
    public ADRListener adrListener;
    public TickListener tickListener;
    JFrame debugFrame;
    public  EventProcessor(ADR adr)
    {
        this.adrStrategy=adr;
        adrListener=new ADRListener(adrStrategy);
        tickListener=new TickListener(adrStrategy);
        
        // Register event class alias for simplicity
        Configuration config = new Configuration();
        config.getEngineDefaults().getThreading().setListenerDispatchPreserveOrder(false);
        config.getEngineDefaults().getThreading().setInsertIntoDispatchPreserveOrder(false); 
//        config.addEventType("TickPrice", "com.adrStrategy.TickPriceEvent");
       config.addEventType("TickPrice", com.incurrency.algorithms.adr.TickPriceEvent.class);
       config.addEventType("Flush", com.incurrency.algorithms.adr.FlushEvent.class);
       config.addEventType("ADRPrice", com.incurrency.algorithms.adr.ADREvent.class);
       config.getEngineDefaults().getThreading().setInternalTimerEnabled(false);
       
       

        // Get an engine instance
        esperEngine=EPServiceProviderManager.getProvider(adrStrategy.getStrategy(),config);
   //     String test=TickPriceEvent.class.getName();
  //      esperEngine.getEPAdministrator().getConfiguration().addEventType("TickPrice", TickPriceEvent.class);
        //Make sure the following jars are in classpath. 1.CGILIB, 2.ANTLR
        esperEngine.initialize();
        System.out.println("Esper engine= " + esperEngine.getURI());

        EPStatement statement = null;
        String stmt = null;
        
        //stmt = "select a.terminal.id as terminal from pattern [ every a=Checkin -> " +
        //        "      ( OutOfOrder(terminal.id=a.terminal.id) and not (Cancelled(terminal.id=a.terminal.id) or Completed(terminal.id=a.terminal.id)) )]";
       
        stmt = "create variable int LASTPRICE  = " + com.ib.client.TickType.LAST;
        esperEngine.getEPAdministrator().createEPL(stmt);
        stmt = "create variable int OPENPRICE  = " + com.ib.client.TickType.OPEN;
        esperEngine.getEPAdministrator().createEPL(stmt);
        stmt = "create variable int LASTSIZE  = " + com.ib.client.TickType.LAST_SIZE;
        esperEngine.getEPAdministrator().createEPL(stmt);
        stmt = "create variable int VOLUME = " + com.ib.client.TickType.VOLUME;
        esperEngine.getEPAdministrator().createEPL(stmt);
        stmt = "create variable int TRADEDVALUE = " + com.ib.client.TickType.TRADEDVALUE;
        esperEngine.getEPAdministrator().createEPL(stmt);

        // Create a named window to get the last and close price for a ticker
        stmt = "create window PriceWin.std:unique(tickerID) as ";
        stmt += "(tickerID int, lastPrice double, openPrice double, lastSize double, volume double, tradedvalue double)";

        statement = esperEngine.getEPAdministrator().createEPL(stmt);

        stmt = "insert into PriceWin  ";
        stmt += "select  a.tickerID as tickerID, a.price as lastPrice, b.price as openPrice, c.price as lastSize, d.price as volume,e.price as tradedvalue ";
        stmt += "from TickPrice(field=TRADEDVALUE, price> 0).std:unique(tickerID) as e, ";
        stmt += "TickPrice(field = LASTPRICE, price > 0).std:unique(tickerID) as a, ";
        stmt += "TickPrice(field = OPENPRICE, price > 0).std:unique(tickerID) as b, ";
        stmt += "TickPrice(field = LASTSIZE, price > 0).std:unique(tickerID) as c, ";
        stmt += "TickPrice(field = VOLUME, price > 0).std:unique(tickerID) as d ";
        stmt += "where a.tickerID = b.tickerID ";
        stmt += "and b.tickerID = c.tickerID ";
        stmt += "and c.tickerID = d.tickerID ";
        stmt += "and d.tickerID = e.tickerID";


        statement = esperEngine.getEPAdministrator().createEPL(stmt);
        // Create the statement to calculate adrStrategy as count(+symbs)/count(-symbs) since prv day close
        stmt = "select count(*, lastPrice > openPrice) as pTicks, ";
        stmt += "sum(tradedvalue, lastPrice > openPrice) as pValue, ";
        stmt += "sum(volume, lastPrice > openPrice) as pVolume, ";
        stmt += "count(*, lastPrice < openPrice) as nTicks, ";
        stmt += "sum(tradedvalue, lastPrice < openPrice) as nValue, ";
        stmt += "sum(volume, lastPrice < openPrice) as nVolume, ";
        stmt += "count(*) as tTicks, ";
        stmt += "sum(tradedvalue) as tradedValue, ";
        stmt += "sum(volume) as volume from PriceWin";
        statement = esperEngine.getEPAdministrator().createEPL(stmt);
        statement.addListener(adrListener);

        // Create a named window to get the last and previous last for each ticker
        stmt = "create window LastPriceWin.std:unique(tickerID) as ";
        stmt += "(tickerID int, price double, lPrice double, lastSize double, lLastSize double, lastvalue double)";
        statement = esperEngine.getEPAdministrator().createEPL(stmt);

        stmt = "insert into LastPriceWin  ";
        stmt += "select a.tickerID as tickerID, last(a.price,0) as price, last(a.price,1) as lPrice, last(b.price,0) as lastSize, last(b.price,1) as lLastSize,last(a.price,0)*last(b.price,0) as lastvalue ";
        stmt += "from TickPrice(field = LASTPRICE, price > 0).std:groupwin(tickerID).win:length(2) as a, ";
        stmt += "TickPrice(field = LASTSIZE, price > 0).std:groupwin(tickerID).win:length(2) as b ";
        stmt += " where a.tickerID = b.tickerID ";
        stmt += "group by a.tickerID, b.tickerID "; 
        statement = esperEngine.getEPAdministrator().createEPL(stmt);

        // Create the statement to calculate tick as count(+symbs)/count(-symbs) since last price for each ticker
        stmt = "select count(*, price > lPrice) as pTicks,sum(lastvalue, price > lPrice) as pLastSize, count(*, price < lPrice) as nTicks,sum(lastvalue, price < lPrice) as nLastSize, ";
        stmt += "count(*,price=lPrice) as uTicks, sum(lastvalue,price=lPrice) as uLastSize, count(*) as tTicks, sum(lastvalue) as tLastSize from LastPriceWin ";
        statement = esperEngine.getEPAdministrator().createEPL(stmt);
        statement.addListener(tickListener);
        
        
        //Once ADR volume, count data is available, we move to ADR processing
        stmt = "create variable int adr  = " + com.incurrency.algorithms.adr.ADRTickType.D_ADR;
        esperEngine.getEPAdministrator().createEPL(stmt);
        stmt = "create variable int adrTRINVOLUME= " + com.incurrency.algorithms.adr.ADRTickType.D_TRIN_VOLUME;
        esperEngine.getEPAdministrator().createEPL(stmt);
        stmt = "create variable int tick  = " + com.incurrency.algorithms.adr.ADRTickType.T_TICK;
        esperEngine.getEPAdministrator().createEPL(stmt);
        stmt = "create variable int tickTRIN = " + com.incurrency.algorithms.adr.ADRTickType.T_TRIN;
        esperEngine.getEPAdministrator().createEPL(stmt);
        
        
        stmt = "select field,max(price) as high ,min(price) as low, avg(price) as average "
                + "from ADRPrice.win:time("
                +adrStrategy.window
                +" minutes) "
                + "group by field";
        
        ADRStatement = esperEngine.getEPAdministrator().createEPL(stmt);
        //statement.addListener(TurtleMainUI.algo.getParamADR());

       stmt= "on Flush "+
             "delete from "+
              "LastPriceWin" ;
        
        esperEngine.getEPAdministrator().createEPL(stmt);
       stmt= "on Flush "+
             "delete from "+
              "ADRPrice.win:time("
               +adrStrategy.window
               +" minutes) " ;
       stmt= "on Flush "+
             "delete from "+
              "PriceWin" ;
               esperEngine.getEPAdministrator().createEPL(stmt);
        //create debug window
        //if(Launch.input.containsKey("debugscreen")){
        if(com.incurrency.algorithms.launch.Launch.input.containsKey("debugscreen")){
        debugFrame = new JFrame("Debug Window");
        debugFrame.setLayout( new FlowLayout() );
        debugFrame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        debugFrame.setSize(300,400);
        debugFrame.setTitle(adrStrategy.getStrategy());        
         JButton debugButton = new JButton("Debug");
        debugFrame.add(debugButton);
        debugFrame.setAlwaysOnTop (true);
         debugFrame.setVisible(true);
        debugButton.setVisible(true);
        debugButton.addActionListener(this);
        
        }
    
    }
    
    
  
    @Override
    	public void actionPerformed(ActionEvent e) {
        EPRuntime epRuntime = esperEngine.getEPRuntime();        
        System.out.println("PriceWin aggregation output");
        String query="select * from LastPriceWin";
        EPOnDemandQueryResult result = epRuntime.executeQuery(query);
        TradingUtil.writeToFile("DebugTick.csv", "tickerID,Price,lPrice,lastSize,lLastSize");
        for (EventBean row : result.getArray()) {
            //String timeStamp=row.get("current_timestamp")!=null?row.get("current_timestamp").toString():"";            
            String tickerID=row.get("tickerID")!=null?row.get("tickerID").toString():"";
            String price=row.get("price")!=null?row.get("price").toString():"";
            String lPrice=row.get("lPrice")!=null?row.get("lPrice").toString():"";
            String lastSize=row.get("lastSize")!=null?row.get("lastSize").toString():"";
            String lLastSize=row.get("lLastSize")!=null?row.get("lLastSize").toString():"";
            TradingUtil.writeToFile("DebugTick.csv", tickerID+","+price+","+lPrice+","+lastSize+","+lLastSize);
        }
        
        //write ADR
            query="select * from PriceWin";
            EPOnDemandQueryResult result2 = epRuntime.executeQuery(query);
             TradingUtil.writeToFile("DebugADR.csv", "tickerID,lastPrice,openPrice,lastSize,volume");
             for (EventBean ADRrow : result2.getArray()) {
            String tickerID=ADRrow.get("tickerID")!=null?ADRrow.get("tickerID").toString():"";
            String lastPrice=ADRrow.get("lastPrice")!=null?ADRrow.get("lastPrice").toString():"";
            String openPrice=ADRrow.get("openPrice")!=null?ADRrow.get("openPrice").toString():"";
            String lastSize=ADRrow.get("lastSize")!=null?ADRrow.get("lastSize").toString():"";
            String volume=ADRrow.get("volume")!=null?ADRrow.get("volume").toString():"";
            TradingUtil.writeToFile("DebugADR.csv", tickerID+","+lastPrice+","+openPrice+","+lastSize+","+volume);
                    
             }
       }
        
      
        public void debugFireTickQuery(){
        EPRuntime epRuntime = esperEngine.getEPRuntime();        
        System.out.println("PriceWin aggregation output");
        String query="select * from LastPriceWin";
        EPOnDemandQueryResult result = epRuntime.executeQuery(query);
        result = epRuntime.executeQuery(query);
        TradingUtil.writeToFile("Tick.csv", "tickerID,Price,lPrice,lastSize,lLastSize\n");
        for (EventBean row : result.getArray()) {
            TradingUtil.writeToFile("Tick.csv", row.get("tickerID").toString()+","+row.get("price").toString()+","+
                    row.get("lPrice").toString()+","+row.get("lastSize").toString()+","+row.get("lLastSize").toString()+"\n");
            //write ADR
            query="select * from LastPrice";
            result = epRuntime.executeQuery(query);
             TradingUtil.writeToFile("ADR.csv", "tickerID,lastPrice,openPrice,lastSize,volume\n");
             for (EventBean ADRrow : result.getArray()) {
                  TradingUtil.writeToFile("Tick.csv", ADRrow.get("tickerID").toString()+","+ADRrow.get("lastPrice").toString()+","+
                    ADRrow.get("openPrice").toString()+","+ADRrow.get("lastSize").toString()+","+ADRrow.get("volume").toString()+"\n");

             }
            /*            
            System.out.println("tickerID=" + row.get("tickerID"));
            System.out.println("Price=" + row.get("price"));
            System.out.println("lPrice=" + row.get("lPrice"));
            System.out.println("lastSize=" + row.get("lastSize"));
            System.out.println("lLastSize=" + row.get("lLastSize"));*/
	}
        }
        
        public void debugFireADRQuery(){
        EPRuntime epRuntime = esperEngine.getEPRuntime();        
        System.out.println("PriceWin aggregation output");
        String query="select * from PriceWin";
        EPOnDemandQueryResult result = epRuntime.executeQuery(query);
        result = epRuntime.executeQuery(query);
        for (EventBean row : result.getArray()) {
            System.out.println("tickerID=" + row.get("tickerID"));
            //System.out.println("field=" + row.get("field"));
            System.out.println("lastPrice=" + row.get("lastPrice"));
            System.out.println("openPrice=" + row.get("openPrice"));
            System.out.println("lastSize=" + row.get("lastSize"));
            System.out.println("Volume=" + row.get("volume"));
	}
        }
    public void sendEvent(Object event) {
        esperEngine.getEPRuntime().sendEvent(event);
    }
    public void initialize(){
        if(com.incurrency.algorithms.launch.Launch.input.containsKey("debugscreen") && debugFrame!=null){
            debugFrame.dispatchEvent(new WindowEvent(debugFrame, WindowEvent.WINDOW_CLOSING));
        }
        esperEngine.initialize();
        
    }
    
    public void destroy() {
        if(com.incurrency.algorithms.launch.Launch.input.containsKey("debugscreen") && debugFrame!=null){
            debugFrame.dispatchEvent(new WindowEvent(debugFrame, WindowEvent.WINDOW_CLOSING));
        }
        esperEngine.destroy();
    }

    public void stopAllStmt()
    {
        esperEngine.getEPAdministrator().stopAllStatements();
    }
    public void startAllStmt()
    {
        esperEngine.getEPAdministrator().startAllStatements();
    }
}
