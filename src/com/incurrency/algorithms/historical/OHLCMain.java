
package com.incurrency.algorithms.historical;
import com.espertech.esper.client.Configuration;
import com.espertech.esper.client.EPServiceProvider;
import com.espertech.esper.client.EPServiceProviderManager;
import com.espertech.esper.client.EPStatement;
import com.espertech.esper.client.time.CurrentTimeEvent;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.Calendar;
import java.util.GregorianCalendar;

public class OHLCMain
{
    private static final Log log = LogFactory.getLog(OHLCMain.class);
    EPServiceProvider epService;
    public static int summary;

    public OHLCMain(){
        run("OHLCEngineURI");
    }
    
    public OHLCMain(int summary)
    {
        OHLCMain.summary=summary;
        try
        {
            //run("OHLCEngineURI");
        }
        catch (RuntimeException ex)
        {
            //log.error("Unexpected exception :" + ex.getMessage(), ex);
        }
    }

    public void run(String engineURI)
    {
        log.info("Setting up EPL");

        Configuration config = new Configuration();
        config.getEngineDefaults().getThreading().setInternalTimerEnabled(false);   // external timer for testing
        config.addEventType("OHLCTick", OHLCTick.class);
        config.addPlugInView("examples", "ohlcbarminute", OHLCBarPlugInViewFactory.class.getName());

        EPServiceProvider epService = EPServiceProviderManager.getProvider(engineURI, config);
        epService.initialize();     // Since running in a unit test may use the same engine many times

        // set time as an arbitrary start time
        sendTimer(epService, toTime("9:01:50"));

        Object[][] statements = new Object[][] {
            {"S1",    "select * from OHLCTick.std:groupwin(ticker).examples:ohlcbarminute(timestamp,price,type,summary)"},
            };

        for (Object[] statement : statements)
        {
            String stmtName = (String) statement[0];
            String expression = (String) statement[1];
            log.info("Creating statement: " + expression);
            EPStatement stmt = epService.getEPAdministrator().createEPL(expression, stmtName);

            if (stmtName.equals("S1"))
            {
                OHLCUpdateListener listener = new OHLCUpdateListener();
                stmt.addListener(listener);
            }
        }
    }

    private void sendTimer(EPServiceProvider epService, long timestamp)
    {
        epService.getEPRuntime().sendEvent(new CurrentTimeEvent(timestamp));
    }

    private static long toTime(String time)
    {
        String[] fields = time.split(":");
        int hour = Integer.parseInt(fields[0]);
        int min = Integer.parseInt(fields[1]);
        int sec = Integer.parseInt(fields[2]);
        Calendar cal = GregorianCalendar.getInstance();
        cal.set(2008, 1, 1, hour, min, sec);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }
}

