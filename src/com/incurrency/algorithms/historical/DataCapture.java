/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.algorithms.historical;

import com.incurrency.framework.HistoricalBarEvent;
import com.incurrency.framework.HistoricalBarListener;
import com.incurrency.framework.*;
import java.io.IOException;
import java.io.PrintStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 *
 * @author pankaj
 */
public class DataCapture implements HistoricalBarListener {

    public Connection mysqlConnection;
    public Socket cassandraConnection;
    public PreparedStatement mySQLInsert;
    public static String newline = System.getProperty("line.separator");
    static int batchSize=0;
    private String table;
    int batch;

    public DataCapture(String table,int batch) throws SQLException, UnknownHostException, IOException, ClassNotFoundException {
        this.table=table;
        this.batch=batch;
        MainAlgorithm.tes.addHistoricalListener(this);
        if (Historical.mysqlConnection!=null) {
            Class.forName("com.mysql.jdbc.Driver") ;
            mysqlConnection = DriverManager.getConnection(Historical.mysqlConnection, Historical.mysqlUserName, Historical.mysqlPassword);
            mysqlConnection.setAutoCommit(true);
            mySQLInsert = mysqlConnection.prepareStatement("insert into "+table+" values (?,?,?,?,?,?,?)");            
            
        }
        if (Historical.cassandraConnection!=null) {
            cassandraConnection = new Socket(Historical.cassandraConnection, Integer.valueOf(Historical.cassandraPort));
        }

    }

    @Override
    public synchronized void barsReceived(HistoricalBarEvent event) {
        try {
            String name=event.getSymbol().getDisplayname().trim();
            String expiry=event.getSymbol().getExpiry();
            switch (event.getOhlc().getPeriodicity()) {
                case DAILY:
                    if (mysqlConnection != null && Historical.mysqlBarSize.get("daily") != null) {
                        insertIntoMySQL(event.getOhlc(), Historical.mysqlBarSize.get("daily"),name);
                    }
                    if (cassandraConnection != null && Historical.cassandraBarSize.get("daily") != null) {
                        insertIntoCassandra(event.getOhlc(), Historical.cassandraBarSize.get("daily"),name,expiry);
                    }
                    break;
                case ONEMINUTE:
                    if (mysqlConnection != null && Historical.mysqlBarSize.get("1min") != null) {
                        insertIntoMySQL(event.getOhlc(), Historical.mysqlBarSize.get("1min"),name);
                    }
                    if (cassandraConnection != null && Historical.cassandraBarSize.get("1min") != null) {
                        insertIntoCassandra(event.getOhlc(), Historical.cassandraBarSize.get("1min"),name,expiry);
                    }
                    break;
                case ONESECOND:
                    if (mysqlConnection != null && Historical.mysqlBarSize.get("1sec") != null) {
                        insertIntoMySQL(event.getOhlc(), Historical.mysqlBarSize.get("1sec"),name);
                    }
                    if (cassandraConnection != null && Historical.cassandraBarSize.get("1sec") != null) {
                        insertIntoCassandra(event.getOhlc(), Historical.cassandraBarSize.get("1sec"),name,expiry);
                    }
                    break;
                default:
                    break;
            }
        } catch (SQLException | IOException e) {
        }
    }

    public void insertIntoMySQL(BeanOHLC ohlc, String table,String symbol) throws SQLException {
        if(table.equals(this.table)){
            //System.out.println("Sub:"+getFormattedDate("yyyy-MM-dd HH:mm:ss", ohlc.getOpenTime(), HistoricalData.timeZone)+":"+ohlc.getClose());
        mySQLInsert.setString(1,symbol );
        mySQLInsert.setString(2, getFormattedDate("yyyy-MM-dd HH:mm:ss", ohlc.getOpenTime(), Historical.timeZone));
        mySQLInsert.setString(3, String.valueOf(ohlc.getOpen()));
        mySQLInsert.setString(4, String.valueOf(ohlc.getHigh()));
        mySQLInsert.setString(5, String.valueOf(ohlc.getLow()));
        mySQLInsert.setString(6, String.valueOf(ohlc.getClose()));
        mySQLInsert.setString(7, String.valueOf(ohlc.getVolume()));
        mySQLInsert.addBatch();
        batchSize++;
        if(batchSize>=batch){
            mySQLInsert.executeBatch();
            System.out.println("SQLProcessed - Symbol: "+symbol+ "Date: "+getFormattedDate("yyyy-MM-dd HH:mm:ss", ohlc.getOpenTime(), Historical.timeZone));
            batchSize=0;
        }
        
        //mySQLInsert.executeUpdate();
        }else{
            mySQLInsert.executeBatch();
        }
        
    }
    
    public void insertIntoCassandra(BeanOHLC ohlc, String metric,String symbol,String expiry) throws IOException{
        if((ohlc.getVolume()>0 ||Historical.zerovolumeSymbols.contains(symbol))&&(Historical.lastUpdateDate.get(symbol).before(new Date(ohlc.getOpenTime())))){
        PrintStream output= new PrintStream(cassandraConnection.getOutputStream());
        if(expiry==null){
        output.print("put "+metric+".open "+ohlc.getOpenTime()+ " "+ohlc.getOpen()+" "+"symbol="+symbol.toLowerCase()+newline);
        output.print("put "+metric+".high "+ohlc.getOpenTime()+ " "+ohlc.getHigh()+" "+"symbol="+symbol.toLowerCase()+newline);
        output.print("put "+metric+".low "+ohlc.getOpenTime()+ " "+ohlc.getLow()+" "+"symbol="+symbol.toLowerCase()+newline);
        output.print("put "+metric+".close "+ohlc.getOpenTime()+ " "+ohlc.getClose()+" "+"symbol="+symbol.toLowerCase()+newline);
        output.print("put "+metric+".volume "+ohlc.getOpenTime()+ " "+ohlc.getVolume()+" "+"symbol="+symbol.toLowerCase()+newline);
        //} 
        }else{
        output.print("put "+metric+".open "+ohlc.getOpenTime()+ " "+ohlc.getOpen()+" "+"symbol="+symbol.toLowerCase()+" "+"expiry="+expiry+newline);
        output.print("put "+metric+".high "+ohlc.getOpenTime()+ " "+ohlc.getHigh()+" "+"symbol="+symbol.toLowerCase()+" "+"expiry="+expiry+newline);
        output.print("put "+metric+".low "+ohlc.getOpenTime()+ " "+ohlc.getLow()+" "+"symbol="+symbol.toLowerCase()+" "+"expiry="+expiry+newline);
        output.print("put "+metric+".close "+ohlc.getOpenTime()+ " "+ohlc.getClose()+" "+"symbol="+symbol.toLowerCase()+" "+"expiry="+expiry+newline);
        output.print("put "+metric+".volume "+ohlc.getOpenTime()+ " "+ohlc.getVolume()+" "+"symbol="+symbol.toLowerCase()+" "+"expiry="+expiry+newline);
            
        }
        }
    }

    public String getFormattedDate(String format, long timeMS, TimeZone tmz) {
        SimpleDateFormat sdf = new SimpleDateFormat(format);
        sdf.setTimeZone(tmz);
        String date = sdf.format(new Date(timeMS));
        return date;
    }
}
