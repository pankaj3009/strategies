/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.adr;

import com.espertech.esper.client.Configuration;
import com.espertech.esper.client.EPServiceProvider;
import com.espertech.esper.client.EPServiceProviderManager;
import com.espertech.esper.client.EPStatement;
/**
 *
 * @author Admin
 */
public class EventProcessor {

    private EPServiceProvider esperEngine;
    public  EventProcessor()
    {
        // Register event class alias for simplicity
        Configuration config = new Configuration();
        config.addEventType("TickPrice", TickPriceEvent.class.getName());

        // Get an engine instance
        esperEngine = EPServiceProviderManager.getDefaultProvider(config);
        esperEngine.initialize();
        System.out.println("Esper engine= " + esperEngine.toString());

        EPStatement statement = null;
        String stmt = null;

        //stmt = "select a.terminal.id as terminal from pattern [ every a=Checkin -> " +
        //        "      ( OutOfOrder(terminal.id=a.terminal.id) and not (Cancelled(terminal.id=a.terminal.id) or Completed(terminal.id=a.terminal.id)) )]";
       
        stmt = "create variable int LASTPRICE  = " + com.ib.client.TickType.LAST;
        esperEngine.getEPAdministrator().createEPL(stmt);
        stmt = "create variable int CLOSEPRICE  = " + com.ib.client.TickType.CLOSE;
        esperEngine.getEPAdministrator().createEPL(stmt);

        // Create a named window to get the last and close price for a ticker
        stmt = "create window PriceWin.std:unique(tickerID) as ";
        stmt += "(tickerID int, lastPrice double, closePrice double)";

        statement = esperEngine.getEPAdministrator().createEPL(stmt);

        stmt = "insert into PriceWin  ";
        stmt += "select  a.tickerID as tickerID, a.price as lastPrice, b.price as closePrice ";
        stmt += "from TickPrice(field = LASTPRICE, price > 0).std:unique(tickerID) as a, ";
        stmt += "TickPrice(field = CLOSEPRICE, price > 0).std:unique(tickerID) as b ";
        stmt += "where a.tickerID = b.tickerID";

        statement = esperEngine.getEPAdministrator().createEPL(stmt);
        // Create the statement to calculate adr as count(+symbs)/count(-symbs) since prv day close
        stmt = "select count(*, lastPrice > closePrice) as pTicks, ";
        stmt += "count(*, lastPrice < closePrice) as nTicks, count(*) as tTicks from PriceWin";
        statement = esperEngine.getEPAdministrator().createEPL(stmt);
        statement.addListener(new TickPriceListner());

        // Create a named window to get the last and previous last for each ticker
        stmt = "create window LastPriceWin.std:unique(tickerID) as ";
        stmt += "(tickerID int, price double, lPrice double)";
        statement = esperEngine.getEPAdministrator().createEPL(stmt);

        stmt = "insert into LastPriceWin  ";
        stmt += "select tickerID, prev(0, price) as price, prev(1, price) as lPrice ";
        stmt += "from TickPrice(field = LASTPRICE, price > 0).std:groupwin(tickerID).win:length(2) ";
        stmt += "where prev(0, price) != prev(1, price)";
        //stmt += "select tickerID, prior(0, price) as price, prior(1, price) as lPrice ";
        //stmt += "from TickPrice(field = LASTPRICE, price > 0) group by tickerID";
        statement = esperEngine.getEPAdministrator().createEPL(stmt);

        // Create the statement to calculate adr as count(+symbs)/count(-symbs) since last price for each ticker
        stmt = "select count(*, price > lPrice) as pTicks, count(*, price < lPrice) as nTicks, ";
        stmt += "count(*) as tTicks from LastPriceWin";
        statement = esperEngine.getEPAdministrator().createEPL(stmt);
        statement.addListener(new LastPriceListner());
    }
    
    public void sendEvent(Object event) {
        esperEngine.getEPRuntime().sendEvent(event);
    }

    public void destroy() {
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
