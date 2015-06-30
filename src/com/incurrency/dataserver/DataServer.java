/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.dataserver;

import com.incurrency.framework.rateserver.ServerPubSub;
import com.incurrency.framework.rateserver.ServerResponse;
import com.incurrency.framework.Algorithm;
import com.incurrency.framework.BeanConnection;
import com.incurrency.framework.DateUtil;
import com.incurrency.framework.MainAlgorithm;
import com.incurrency.framework.Parameters;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.incurrency.framework.Utilities;
import java.io.PrintStream;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Properties;

/**
 * Generates ServerPubSub, Tick , TRIN initialise ServerPubSub passing the adrSymbols that
 * need to be tracked. ServerPubSub will immediately initiate polling market data in
 * snapshot mode. Streaming mode is currently not supported output is available
 * via static fields Advances, Declines, Tick Advances, Tick Declines, Advancing
 * Volume, Declining Volume, Tick Advancing Volume, Tick Declining Volume
 *
 * @author pankaj
 */
public class DataServer extends com.incurrency.framework.rateserver.Rates {

    public DataServer(String parameterFile) {
        super(parameterFile);
    }

   


}
