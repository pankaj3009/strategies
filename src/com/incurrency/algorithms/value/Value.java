/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.algorithms.value;

import com.incurrency.algorithms.contra.*;
import com.incurrency.algorithms.manager.Manager;
import com.incurrency.framework.MainAlgorithm;
import java.util.ArrayList;
import java.util.Properties;
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
public class Value extends Manager {
    private static final Logger logger = Logger.getLogger(Contra.class.getName());
    private final Object lockScan=new Object();
    
    public Value(MainAlgorithm m, Properties p, String parameterFile, ArrayList<String> accounts, Integer stratCount) {
        super(m, p, parameterFile, accounts, stratCount,"contra");
        Timer trigger = new Timer("Timer: " + this.getStrategy() + " RScriptProcessor");
        trigger.schedule(RScriptRunTask, RScriptRunTime);
    }

    private TimerTask RScriptRunTask = new TimerTask() {
        @Override
        public void run() {
            if (!RStrategyFile.equals("")) {
                synchronized (lockScan) {
                    logger.log(Level.INFO, "501,Scan,{0}", new Object[]{getStrategy()});
                    RConnection c = null;
                    try {
                        c = new RConnection(rServerIP);
                        c.eval("setwd(\"" + wd + "\")");
                        REXP wd = c.eval("getwd()");
                        System.out.println(wd.asString());
                        c.eval("options(encoding = \"UTF-8\")");
                        String[] args=new String[]{"1",getStrategy(), getRedisDatabaseID()};
                        c.assign("args", args);
                        c.eval("source(\"" + RStrategyFile + "\")");
                    } catch (Exception e) {
                        logger.log(Level.SEVERE, null, e);
                    }
                }
            }
        }
    };
}
