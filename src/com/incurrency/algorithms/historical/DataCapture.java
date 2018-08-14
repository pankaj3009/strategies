/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.algorithms.historical;

import com.incurrency.framework.*;
import java.io.IOException;
import java.io.PrintStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.rosuda.REngine.Rserve.RserveException;

/**
 *
 * @author pankaj
 */
public class DataCapture implements HistoricalBarListener {

    public Connection mysqlConnection;
    public Socket cassandraConnection;
    public PreparedStatement mySQLInsert;
    public static String newline = System.getProperty("line.separator");
    static int batchSize = 0;
    private String table;
    int batch;
    private HashMap<String, TreeMap<Long, BeanOHLC>> rdata = new HashMap<>();
    private HashMap<String, Long> lastData = new HashMap<>();

    public DataCapture(String table, int batch) throws SQLException, UnknownHostException, IOException, ClassNotFoundException {
        this.table = table;
        this.batch = batch;
        MainAlgorithm.tes.addHistoricalListener(this);
        if (Historical.mysqlConnection != null) {
            Class.forName("com.mysql.jdbc.Driver");
            mysqlConnection = DriverManager.getConnection(Historical.mysqlConnection, Historical.mysqlUserName, Historical.mysqlPassword);
            mysqlConnection.setAutoCommit(true);
            mySQLInsert = mysqlConnection.prepareStatement("insert into " + table + " values (?,?,?,?,?,?,?)");
        }
        if (Historical.cassandraConnection != null) {
            cassandraConnection = new Socket(Historical.cassandraConnection, Integer.valueOf(Historical.cassandraPort));
        }
    }

    @Override
    public synchronized void barsReceived(HistoricalBarEvent event) {
        try {
            String name = event.getSymbol().getDisplayname().trim();
            String expiry = event.getSymbol().getExpiry();
            switch (event.getOhlc().getPeriodicity()) {
                case DAILY:
                    if (mysqlConnection != null && Historical.mysqlBarDestination.get("daily") != null) {
                        insertIntoMySQL(event.getOhlc(), Historical.mysqlBarDestination.get("daily"), name);
                    }
                    if (cassandraConnection != null && Historical.cassandraBarDestination.get("daily") != null) {
                        insertIntoCassandra(event.getOhlc(), Historical.cassandraBarDestination.get("daily"), name, expiry);
                    }
                    if (Historical.rBarRequestDuration.get("daily") != null) {
                        insertIntoRDB(event.getOhlc(), event.getSymbol().getDisplayname());
                    }
                    break;
                case ONEMINUTE:
                    if (mysqlConnection != null && Historical.mysqlBarDestination.get("1min") != null) {
                        insertIntoMySQL(event.getOhlc(), Historical.mysqlBarDestination.get("1min"), name);
                    }
                    if (cassandraConnection != null && Historical.cassandraBarDestination.get("1min") != null) {
                        insertIntoCassandra(event.getOhlc(), Historical.cassandraBarDestination.get("1min"), name, expiry);
                    }
                    if (Historical.rBarRequestDuration.get("1min") != null) {
                        insertIntoRDB(event.getOhlc(), event.getSymbol().getDisplayname());
                    }
                    break;
                case ONESECOND:
                    if (mysqlConnection != null && Historical.mysqlBarDestination.get("1sec") != null) {
                        insertIntoMySQL(event.getOhlc(), Historical.mysqlBarDestination.get("1sec"), name);
                    }
                    if (cassandraConnection != null && Historical.cassandraBarDestination.get("1sec") != null) {
                        insertIntoCassandra(event.getOhlc(), Historical.cassandraBarDestination.get("1sec"), name, expiry);
                    }
                    if (Historical.rBarRequestDuration.get("1sec") != null) {
                        insertIntoRDB(event.getOhlc(), event.getSymbol().getDisplayname());
                    }
                    break;
                default:
                    break;
            }
        } catch (SQLException | IOException e) {
        }
    }

    public void insertIntoMySQL(BeanOHLC ohlc, String table, String symbol) throws SQLException {
        if (table.equals(this.table)) {
            //System.out.println("Sub:"+getFormattedDate("yyyy-MM-dd HH:mm:ss", ohlc.getOpenTime(), HistoricalData.timeZone)+":"+ohlc.getClose());
            mySQLInsert.setString(1, symbol);
            mySQLInsert.setString(2, getFormattedDate("yyyy-MM-dd HH:mm:ss", ohlc.getOpenTime(), Historical.timeZone));
            mySQLInsert.setString(3, String.valueOf(ohlc.getOpen()));
            mySQLInsert.setString(4, String.valueOf(ohlc.getHigh()));
            mySQLInsert.setString(5, String.valueOf(ohlc.getLow()));
            mySQLInsert.setString(6, String.valueOf(ohlc.getClose()));
            mySQLInsert.setString(7, String.valueOf(ohlc.getVolume()));
            mySQLInsert.addBatch();
            batchSize++;
            if (batchSize >= batch) {
                mySQLInsert.executeBatch();
                System.out.println("SQLProcessed - Symbol: " + symbol + "Date: " + getFormattedDate("yyyy-MM-dd HH:mm:ss", ohlc.getOpenTime(), Historical.timeZone));
                batchSize = 0;
            }

            //mySQLInsert.executeUpdate();
        } else {
            mySQLInsert.executeBatch();
        }
    }

    public void insertIntoCassandra(BeanOHLC ohlc, String metric, String symbol, String expiry) throws IOException {
        if ((ohlc.getVolume() > 0 || Historical.zerovolumeSymbols.contains(symbol)) && (Historical.lastUpdateDate.get(symbol).before(new Date(ohlc.getOpenTime())))) {
            PrintStream output = new PrintStream(cassandraConnection.getOutputStream());
            if (expiry == null) {
                output.print("put " + metric + ".open " + ohlc.getOpenTime() + " " + ohlc.getOpen() + " " + "symbol=" + symbol.toLowerCase() + newline);
                output.print("put " + metric + ".high " + ohlc.getOpenTime() + " " + ohlc.getHigh() + " " + "symbol=" + symbol.toLowerCase() + newline);
                output.print("put " + metric + ".low " + ohlc.getOpenTime() + " " + ohlc.getLow() + " " + "symbol=" + symbol.toLowerCase() + newline);
                output.print("put " + metric + ".close " + ohlc.getOpenTime() + " " + ohlc.getClose() + " " + "symbol=" + symbol.toLowerCase() + newline);
                output.print("put " + metric + ".volume " + ohlc.getOpenTime() + " " + ohlc.getVolume() + " " + "symbol=" + symbol.toLowerCase() + newline);
                //} 
            } else {
                output.print("put " + metric + ".open " + ohlc.getOpenTime() + " " + ohlc.getOpen() + " " + "symbol=" + symbol.toLowerCase() + " " + "expiry=" + expiry + newline);
                output.print("put " + metric + ".high " + ohlc.getOpenTime() + " " + ohlc.getHigh() + " " + "symbol=" + symbol.toLowerCase() + " " + "expiry=" + expiry + newline);
                output.print("put " + metric + ".low " + ohlc.getOpenTime() + " " + ohlc.getLow() + " " + "symbol=" + symbol.toLowerCase() + " " + "expiry=" + expiry + newline);
                output.print("put " + metric + ".close " + ohlc.getOpenTime() + " " + ohlc.getClose() + " " + "symbol=" + symbol.toLowerCase() + " " + "expiry=" + expiry + newline);
                output.print("put " + metric + ".volume " + ohlc.getOpenTime() + " " + ohlc.getVolume() + " " + "symbol=" + symbol.toLowerCase() + " " + "expiry=" + expiry + newline);

            }
        }
    }

    public void insertIntoRDB(BeanOHLC ohlc, String symbol) {
        String file = "NA";
        String type = symbol.split("_")[1].toLowerCase();
        if (ohlc.getOpenTime() > 0) {
            if (Historical.rnewfileperday > 0) {
                String formattedDate = getFormattedDate("yyyy-MM-dd", ohlc.getOpenTime(), TimeZone.getTimeZone(Algorithm.timeZone));
                file = Historical.rfolder + type + "/" + formattedDate + "/" + symbol + "_" + formattedDate + ".rds";
            } else {
                file = Historical.rfolder + type + "/" + symbol + ".rds";
            }
        }
        //String keyfile = Historical.rfolder + "/" + symbol + ".rds";
        TreeMap<Long, BeanOHLC> data = rdata.get(file);
        if (data == null) {
            data = new TreeMap<Long, BeanOHLC>();
        }
        if (ohlc.getOpenTime() == 0) {
//            if (Historical.rnewfileperday > 0) {
//                String formattedDate = getFormattedDate("yyyy-MM-dd", data.firstKey(), TimeZone.getTimeZone(Algorithm.timeZone));
//                file = Historical.rfolder + formattedDate + "/" + symbol + "_" + formattedDate + ".rds";
//            } else {
//                file = Historical.rfolder + "/" + symbol + ".rds";
//            }
            //write record to R
            for (String f : rdata.keySet()) {
                writeToR(f);
            }
            rdata.clear();
        }
        if (ohlc.getOpenTime() != 0) {
            data.put(ohlc.getOpenTime(), ohlc);
            rdata.put(file, data);
        }
    }

    public String getFormattedDate(String format, long timeMS, TimeZone tmz) {
        SimpleDateFormat sdf = new SimpleDateFormat(format);
        sdf.setTimeZone(tmz);
        String date = sdf.format(new Date(timeMS));
        return date;
    }

    public void writeToR(String file) {
        synchronized (Historical.lockRcon) {
            try {
                TreeMap<Long, BeanOHLC> data = rdata.get(file);
                int n = data.size();
                double[] open = new double[n];
                double[] high = new double[n];
                double[] low = new double[n];
                double[] close = new double[n];
                String[] volume = new String[n];
                String[] date = new String[n];
                int i = -1;
                for (BeanOHLC d : data.values()) {
                    i = i + 1;
                    open[i] = d.getOpen();
                    high[i] = d.getHigh();
                    low[i] = d.getLow();
                    close[i] = d.getClose();
                    volume[i] = String.valueOf(d.getVolume());
                    date[i] = String.valueOf(d.getOpenTime() / 1000);
                }
                Historical.rcon.assign("date", date);
                Historical.rcon.assign("open", open);
                Historical.rcon.assign("high", high);
                Historical.rcon.assign("low", low);
                Historical.rcon.assign("close", close);
                Historical.rcon.assign("volume", volume);

                String command = "insertIntoRDB(\"" + file + "\",\"" + date + "\",\"" + open + "\",\"" + high + "\",\"" + low + "\",\"" + close + "\",\"" + volume + "\")";
                command = "insertIntoRDB(\"" + file + "\"" + ",date,open,high,low,close,volume)";
                Historical.rcon.eval(command);
            } catch (Exception ex) {
                Logger.getLogger(DataCapture.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
}
