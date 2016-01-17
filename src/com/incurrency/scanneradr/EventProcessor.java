/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.scanneradr;

import com.espertech.esper.client.Configuration;
import com.espertech.esper.client.EPOnDemandQueryResult;
import com.espertech.esper.client.EPRuntime;
import com.espertech.esper.client.EPServiceProvider;
import com.espertech.esper.client.EPServiceProviderManager;
import com.espertech.esper.client.EPStatement;
import com.espertech.esper.client.EventBean;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
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
    public ADRListener adrListener;
    public TickListener tickListener;
    public RangeListener rangeListener;
    public ADRManager adrManager;
    int[] periods;
    JFrame myFrame;

    public EventProcessor(ADRManager adrManager, int[] periods) {
        this.adrManager = adrManager;
        this.periods = periods;
        adrListener = new ADRListener(adrManager);
        tickListener = new TickListener(adrManager);
        rangeListener = new RangeListener();

        // Register event class alias for simplicity
        Configuration config = new Configuration();
        config.getEngineDefaults().getThreading().setListenerDispatchPreserveOrder(false);
        config.getEngineDefaults().getThreading().setInsertIntoDispatchPreserveOrder(false);
        config.addEventType("TickPrice", TickPriceEvent.class);
        config.addEventType("Flush", FlushEvent.class);
        config.addEventType("ADR", ADREvent.class);
        config.getEngineDefaults().getThreading().setInternalTimerEnabled(false);



        // Get an engine instance
        esperEngine = EPServiceProviderManager.getProvider("adr", config);
        //     String test=TickPriceEvent.class.getName();
        //      esperEngine.getEPAdministrator().getConfiguration().addEventType("TickPrice", TickPriceEvent.class);
        //Make sure the following jars are in classpath. 1.CGILIB, 2.ANTLR
        esperEngine.initialize();
        System.out.println("Esper engine= " + esperEngine.getURI());

        EPStatement statement = null;
        String stmt = null;

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

        esperEngine.getEPAdministrator().createEPL(stmt);

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


        esperEngine.getEPAdministrator().createEPL(stmt);
        // Create the statement to calculate adrStrategy as count(+symbs)/count(-symbs) since prv day close
        stmt = "select count(*, lastPrice > openPrice) as pTicks, ";
        stmt += "sum(tradedvalue, lastPrice > openPrice) as pValue, ";
        stmt += "sum(volume, lastPrice > openPrice) as pVolume, ";
        stmt += "count(*, lastPrice < openPrice) as nTicks, ";
        stmt += "sum(tradedvalue, lastPrice < openPrice) as nValue, ";
        stmt += "sum(volume, lastPrice < openPrice) as nVolume, ";
        stmt += "count(*) as tTicks, ";
        stmt += "current_timestamp() as ts, ";
        stmt += "sum(tradedvalue) as tradedValue, ";
        stmt += "sum(volume) as volume from PriceWin";
        statement = esperEngine.getEPAdministrator().createEPL(stmt);
        statement.addListener(adrListener);

        // Create a named window to get the last and previous last for each ticker
        stmt = "create window LastPriceWin.std:unique(tickerID) as ";
        stmt += "(tickerID int, price double, lPrice double, lastSize double, lLastSize double, lastvalue double)";
        esperEngine.getEPAdministrator().createEPL(stmt);

        stmt = "insert into LastPriceWin  ";
        stmt += "select a.tickerID as tickerID, last(a.price,0) as price, last(a.price,1) as lPrice, last(b.price,0) as lastSize, last(b.price,1) as lLastSize,last(a.price,0)*last(b.price,0) as lastvalue ";
        stmt += "from TickPrice(field = LASTPRICE, price > 0).std:groupwin(tickerID).win:length(2) as a, ";
        stmt += "TickPrice(field = LASTSIZE, price > 0).std:groupwin(tickerID).win:length(2) as b ";
        stmt += " where a.tickerID = b.tickerID ";
        stmt += "group by a.tickerID, b.tickerID "; 
        esperEngine.getEPAdministrator().createEPL(stmt);

        // Create the statement to calculate tick as count(+symbs)/count(-symbs) since last price for each ticker
        stmt = "select count(*, price > lPrice) as pTicks,sum(lastvalue, price > lPrice) as pLastSize, count(*, price < lPrice) as nTicks,sum(lastvalue, price < lPrice) as nLastSize, ";
        stmt += "count(*,price=lPrice) as uTicks, sum(lastvalue,price=lPrice) as uLastSize, count(*) as tTicks, sum(lastvalue) as tLastSize from LastPriceWin ";
        statement = esperEngine.getEPAdministrator().createEPL(stmt);
        statement.addListener(tickListener);


        //Once ADR volume, count data is available, we move to ADR processing
        stmt = "create variable double adr  = " + ADRTickType.D_ADR;
        esperEngine.getEPAdministrator().createEPL(stmt);
        stmt = "create variable double adrTRINVOLUME= " + ADRTickType.D_TRIN_VOLUME;
        esperEngine.getEPAdministrator().createEPL(stmt);
        stmt = "create variable double adrTRINVALUE= " + ADRTickType.D_TRIN_VALUE;
        esperEngine.getEPAdministrator().createEPL(stmt);
        stmt = "create variable double move  = " + ADRTickType.T_MOVE;
        esperEngine.getEPAdministrator().createEPL(stmt);
        stmt = "create variable int time = " + ADRTickType.T_TIME;
        esperEngine.getEPAdministrator().createEPL(stmt);
 
        
        for (int i = 0; i < periods.length; i++) {
            stmt = "select current_timestamp() as ts, " + periods[i] + " as period, field,max(price) as high ,min(price) as low , avg(price) as average "
                    + "from ADR.win:time("
                    + periods[i]
                    + " minutes)"
                    + "group by field";
            statement = esperEngine.getEPAdministrator().createEPL(stmt);
            statement.addListener(rangeListener);
        }



        stmt = "on Flush "
                + "delete from "
                + "LastPriceWin";
        esperEngine.getEPAdministrator().createEPL(stmt);

        stmt = "on Flush "
                + "delete from "
                + "PriceWin";
        esperEngine.getEPAdministrator().createEPL(stmt);

        //create debug window/


        myFrame = new JFrame("Debug Window");
        myFrame.setLayout(new FlowLayout());
        myFrame.setSize(300, 400);
        myFrame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        JButton debugButton = new JButton("Debug");
        myFrame.add(debugButton);
        myFrame.setAlwaysOnTop(true);
        myFrame.setVisible(true);
        debugButton.setVisible(true);
        debugButton.addActionListener(this);



    }

    public void sendEvent(Object event) {
        esperEngine.getEPRuntime().sendEvent(event);
    }

    public void initialize() {
        esperEngine.initialize();

    }

    public void destroy() {
        if (myFrame != null) {
            myFrame.dispose();
        }
        esperEngine.destroy();

    }

    public void stopAllStmt() {
        esperEngine.getEPAdministrator().stopAllStatements();
    }

    public void startAllStmt() {
        esperEngine.getEPAdministrator().startAllStatements();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        EPRuntime epRuntime = esperEngine.getEPRuntime();
        System.out.println("PriceWin aggregation output");
        String query = "select * from LastPriceWin";
        EPOnDemandQueryResult result = epRuntime.executeQuery(query);
        com.incurrency.framework.Utilities.writeToFile(new File("Tick.csv"), "tickerID,Price,lPrice,lastSize,lLastSize\n");
        for (EventBean row : result.getArray()) {
            String tickerID = row.get("tickerID") != null ? row.get("tickerID").toString() : "";
            String price = row.get("price") != null ? row.get("price").toString() : "";
            String lPrice = row.get("lPrice") != null ? row.get("lPrice").toString() : "";
            String lastSize = row.get("lastSize") != null ? row.get("lastSize").toString() : "";
            String lLastSize = row.get("lLastSize") != null ? row.get("lLastSize").toString() : "";
            com.incurrency.framework.Utilities.writeToFile("logs","Tick.csv", tickerID + "," + price + "," + lPrice + "," + lastSize + "," + lLastSize + "\n");
        }

        //write ADR
        query = "select * from PriceWin";
        EPOnDemandQueryResult result2 = epRuntime.executeQuery(query);
        com.incurrency.framework.Utilities.writeToFile(new File("ADR.csv"), "tickerID,lastPrice,openPrice,lastSize,volume\n");
        for (EventBean ADRrow : result2.getArray()) {
            String tickerID = ADRrow.get("tickerID") != null ? ADRrow.get("tickerID").toString() : "";
            String lastPrice = ADRrow.get("lastPrice") != null ? ADRrow.get("lastPrice").toString() : "";
            String openPrice = ADRrow.get("openPrice") != null ? ADRrow.get("openPrice").toString() : "";
            String lastSize = ADRrow.get("lastSize") != null ? ADRrow.get("lastSize").toString() : "";
            String volume = ADRrow.get("volume") != null ? ADRrow.get("volume").toString() : "";
            com.incurrency.framework.Utilities.writeToFile("logs","ADR.csv", tickerID + "," + lastPrice + "," + openPrice + "," + lastSize + "," + volume + "\n");

        }
    }
}
