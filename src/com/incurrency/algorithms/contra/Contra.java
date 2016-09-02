/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.algorithms.contra;

import com.incurrency.algorithms.manager.Manager;
import com.incurrency.framework.Algorithm;
import com.incurrency.framework.DateUtil;
import com.incurrency.framework.MainAlgorithm;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Properties;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.rosuda.REngine.REXP;
import org.rosuda.REngine.Rserve.RConnection;

/**
 *
 * @author psharma
 */
public class Contra extends Manager {
    private static final Logger logger = Logger.getLogger(Contra.class.getName());
    
    
    public Contra(MainAlgorithm m, Properties p, String parameterFile, ArrayList<String> accounts, Integer stratCount) {
        super(m, p, parameterFile, accounts, stratCount);
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());
        cal.setTimeZone(TimeZone.getTimeZone(Algorithm.timeZone));
        cal.add(Calendar.DATE, -1);
        Date priorEndDate = cal.getTime();
        
        if (new Date().before(this.getEndDate()) && new Date().after(priorEndDate)) {
            Timer trigger = new Timer("Timer: " + this.getStrategy() + " RScriptProcessor");
            trigger.schedule(RScriptRunTask, RScriptRunTime);
        }
    }

    private TimerTask RScriptRunTask = new TimerTask() {
        @Override
        public void run() {
            if (!RStrategyFile.equals("")) {
                logger.log(Level.INFO, "501,Scan,{0}", new Object[]{getStrategy()});
                RConnection c = null;
                try {
                    c = new RConnection(rServerIP);
                    c.eval("setwd(\"" + wd + "\")");
                    REXP wd = c.eval("getwd()");
                    System.out.println(wd.asString());
                    c.eval("options(encoding = \"UTF-8\")");
                    c.eval("source(\"" + RStrategyFile + "\")");
                } catch (Exception e) {
                    logger.log(Level.SEVERE, null, e);
                }

            }
        }
    };
}
