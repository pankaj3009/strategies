/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.eodmaintenance;

import com.incurrency.framework.Algorithm;
import com.incurrency.framework.BeanSymbol;
import com.incurrency.framework.MainAlgorithm;
import com.incurrency.framework.Utilities;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Properties;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 *
 * @author Pankaj
 */
public class EODMaintenance {

    private Properties properties;
    private ArrayList<BeanSymbol> symbols = new ArrayList<>();
    private static final Logger logger = Logger.getLogger(EODMaintenance.class.getName());

    /**
     * @param args the command line arguments
     */
    public EODMaintenance(String propertyFileName) throws Exception {
        properties = Utilities.loadParameters(propertyFileName);
        SimpleDateFormat sdf_yyyyMMdd = new SimpleDateFormat("yyyyMMdd");
        String fnolotsizeurl = properties.getProperty("fnolotsizeurl", "http://www.nseindia.com/content/fo/fo_mktlots.csv").toString().trim();
        String cnx500url = properties.getProperty("cnx500url", "http://www.nseindia.com/content/indices/ind_cnx500list.csv").toString().trim();
        String niftyurl=properties.getProperty("niftyurl", "http://www.nseindia.com/content/indices/ind_niftylist.csv").toString().trim();
        String currentDay = properties.getProperty("currentday", sdf_yyyyMMdd.format(Calendar.getInstance(TimeZone.getTimeZone(Algorithm.timeZone)).getTime()));
        String historicalfutures = properties.getProperty("historicalfutures");
        String historicalstocks = properties.getProperty("historicalstocks");
        String swinginr = properties.getProperty("swinginr");
        String rateserverinrmarket = properties.getProperty("rateserverinrmarket");
        String ibsymbolfile = properties.getProperty("ibsymbolfile");
        String ibsymbolurl = properties.getProperty("ibsymbolurl");
        extractSymbolsFromIB(ibsymbolurl, ibsymbolfile, symbols);       
        String nextExpiry = getNextExpiry(currentDay);
        if (historicalfutures != null) {
            rateServerFutures(fnolotsizeurl, nextExpiry, 11, historicalfutures);
        }
        if (historicalstocks != null) {
            rateServerStocks(cnx500url, 1, historicalstocks);
        }
        if (swinginr != null) {
            inrswing(fnolotsizeurl, nextExpiry, 1, swinginr);
        }
        if (rateserverinrmarket != null) {
            inrmarket(fnolotsizeurl, 11,cnx500url,1,niftyurl,1,nextExpiry, rateserverinrmarket);
        }
        MainAlgorithm.setCloseDate(new Date());
    }

    /**
     * prints out a symbol file for use in inr marketdata server.Handles requirements for ADR and Swing.
     * @param fnourl
     * @param rowsToSkipFNO
     * @param cnx500url
     * @param rowsToSkipCNX500Stocks
     * @param expiry
     * @param outputfilename
     * @throws MalformedURLException
     * @throws IOException
     * @throws ParseException 
     */
    public void inrmarket(String fnourl, int rowsToSkipFNO, String cnx500url, int rowsToSkipCNX500Stocks, String niftyurl,int rowsToSkipNifty,String expiry, String outputfilename) throws MalformedURLException, IOException, ParseException {
        //NSENIFTY, IND AND FUT is priority 1
        //FNO stocks are priority 2
        //Residual CNX500 stocks are priority 3
        ArrayList<BeanSymbol> stockSymbols = new ArrayList<>();
        BeanSymbol s = new BeanSymbol("NIFTY50", "NSENIFTY", "FUT", expiry, "", "");
        s.setCurrency("INR");
        s.setExchange("NSE");
        s.setStreamingpriority(1);
        s.setStrategy("DATA");
        s.setMinsize(75);
        stockSymbols.add(s);
        s = new BeanSymbol("NIFTY50", "NSENIFTY", "FUT", this.getNextExpiry(expiry), "", "");
        s.setCurrency("INR");
        s.setExchange("NSE");
        s.setStreamingpriority(1);
        s.setStrategy("DATA");
        s.setMinsize(75);
        stockSymbols.add(s);
        s = new BeanSymbol("NIFTY50", "NSENIFTY", "IND", "", "", "");
        s.setCurrency("INR");
        s.setExchange("NSE");
        s.setStreamingpriority(1);
        s.setStrategy("DATA");
        stockSymbols.add(s);
        
        //Add FNO Stocks. By default priority set to 3.
        //Unless they are pary of nifty. In which case priority set to 2.
        SimpleDateFormat formatInFile = new SimpleDateFormat("MMM-yy");
        SimpleDateFormat sdf_yyyyMMdd = new SimpleDateFormat("yyyyMMdd");
        String expiryFormattedAsInput = formatInFile.format(sdf_yyyyMMdd.parse(expiry));
        int columnNumber = -1;
        URL fnoURL = new URL(fnourl);
        if (getResponseCode(fnourl) != 404) {
            BufferedReader in = new BufferedReader(new InputStreamReader(fnoURL.openStream()));
            int j = 0;
            int i = 0;
            String line;
            while ((line = in.readLine()) != null) {
                j = j + 1;
                if (j > rowsToSkipFNO) {
                    String[] input = line.split(",");
                    if (j == rowsToSkipFNO + 1) {//we are on the first row containing expiration information
                        for (String inp : input) {
                            if (Utilities.isDate(inp, formatInFile)) {
                                String expiration = inp.trim().toUpperCase();
                                if (expiration.equalsIgnoreCase(expiryFormattedAsInput)) {
                                    columnNumber = i;
                                    break;
                                }
                            }
                            i = i + 1;
                        }
                    } else if (columnNumber >= 0) {
                        if (input[1].trim().length() > 0) {//not an empty row
                            String exchangesymbol = input[1].trim().toUpperCase();
                            String displayName = input[1].trim().toUpperCase().replaceAll("[^A-Za-z0-9]", "");
                            int minsize = Utilities.getInt(input[columnNumber], 0);
                            if (minsize > 0) {
                                int id = Utilities.getIDFromExchangeSymbol(symbols, exchangesymbol, "STK", "", "", "");
                                if(id>=0){
                                s = symbols.get(id);
                                BeanSymbol s1 = s.clone(s);
                                s1.setType("STK");
                                s1.setExpiry("");
                                s1.setMinsize(minsize);
                                s1.setStrategy("DATA");
                                s1.setStreamingpriority(3);
                                s1.setSerialno(stockSymbols.size()+1);
                                stockSymbols.add(s1);
                                }else{
                                    logger.log(Level.SEVERE,"Symbol not found in IB database corresponding to NSE Symbol {0}",new Object[]{exchangesymbol});
        
                                }
                            }
                        }
                    }
                }
            }
        }

        //Read nifty stocks. Add them with priority 3, if they are not in fno
        //Add current expiry future with priority 2, if nifty stock is in fno.
        //Add next expiry future with priority 5, if nifty stock is in fno
        //Non nifty stocks in fno, move priority to 3
        columnNumber = -1;
        URL niftyURL = new URL(niftyurl);
           if (getResponseCode(niftyurl) != 404) {
            BufferedReader in = new BufferedReader(new InputStreamReader(niftyURL.openStream()));
            int j = 0;
            int i = 0;
            String line;
            while ((line = in.readLine()) != null) {
                j = j + 1;
                if (j > rowsToSkipNifty) {
                    String[] input = line.split(",");
                    if (input[2].trim().length() > 0) {//not an empty row
                        String exchangesymbol = input[2].trim().toUpperCase();
                        //String displayName = input[2].trim().toUpperCase().replaceAll("[^A-Za-z0-9]", "");
                        int id = Utilities.getIDFromExchangeSymbol(symbols, exchangesymbol, "STK", "", "", "");
                        int existingID = Utilities.getIDFromExchangeSymbol(stockSymbols, exchangesymbol, "STK", "", "", "");
                        if (id >= 0 && existingID == -1) {//symbol not in existing file
                            s = symbols.get(id);
                            BeanSymbol s1 = s.clone(s);
                            s1.setType("STK");
                            s1.setExpiry("");
                            s1.setMinsize(1);
                            s1.setStrategy("DATA");
                            s1.setStreamingpriority(3);
                            stockSymbols.add(s1);
                        }else{//add future
                            s = stockSymbols.get(existingID);
                            s.setStreamingpriority(2);
                            BeanSymbol s1 = s.clone(s);
                            s1.setType("FUT");
                            s1.setExpiry(expiry);
                            s1.setStrategy("DATA");
                            s1.setStreamingpriority(2);
                            s1.setSerialno(stockSymbols.size()+1);
                            stockSymbols.add(s1);
                            
                            s = stockSymbols.get(existingID);
                            s1 = s.clone(s);
                            s1.setType("FUT");
                            String expiry1=this.getNextExpiry(expiry);
                            s1.setExpiry(expiry1);
                            s1.setStrategy("DATA");
                            s1.setStreamingpriority(5);
                            s1.setSerialno(stockSymbols.size()+1);
                            stockSymbols.add(s1);
                        }
                    }
                }
            }
        }
           
        //           //Non nifty stocks in fno, move priority to 3
                
        //Residual NIFTY,CNX500 stocks are priority 4
        columnNumber = -1;
        URL stockURL = new URL(cnx500url);
        if (getResponseCode(cnx500url) != 404) {
            BufferedReader in = new BufferedReader(new InputStreamReader(stockURL.openStream()));
            int j = 0;
            int i = 0;
            String line;
            while ((line = in.readLine()) != null) {
                j = j + 1;
                if (j > rowsToSkipCNX500Stocks) {
                    String[] input = line.split(",");
                    if (input[2].trim().length() > 0) {//not an empty row
                        String exchangesymbol = input[2].trim().toUpperCase();
                        String displayName = input[2].trim().toUpperCase().replaceAll("[^A-Za-z0-9]", "");
                        int minsize = 1;
                        int id = Utilities.getIDFromExchangeSymbol(symbols, exchangesymbol, "STK", "", "", "");
                        int existingID = Utilities.getIDFromExchangeSymbol(stockSymbols, exchangesymbol, "STK", "", "", "");
                        if (id >= 0 && existingID == -1) {//symbol not in existing file
                            s = symbols.get(id);
                            BeanSymbol s1 = s.clone(s);
                            s1.setType("STK");
                            s1.setExpiry("");
                            s1.setMinsize(minsize);
                            s1.setStrategy("DATA");
                            s1.setStreamingpriority(4);
                            s1.setSerialno(stockSymbols.size()+1);
                            stockSymbols.add(s1);
                        }
                    }
                }
            }
        }
        //Print symbols
        //update serial nos
        for (int k = 0; k < stockSymbols.size(); k++) {
            stockSymbols.get(k).setSerialno(k + 1);
        }
        //now write data to file
        File outputFile = new File("logs",outputfilename);
        if (stockSymbols.size() > 0) {
            //write header
            Utilities.deleteFile(outputFile);
            String header = "serialno,brokersymbol,exchangesymbol,displayname,type,exchange,primaryexchange,currency,expiry,option,right,minsize,barstarttime,streaming,strategy";
            Utilities.writeToFile(outputFile, header);
            String content = "";
            for (BeanSymbol s1 : stockSymbols) {
                content = s1.getSerialno() + "," + s1.getBrokerSymbol() + "," + (s1.getExchangeSymbol() == null ? "" : s1.getExchangeSymbol())
                        + "," + (s1.getDisplayname() == null ? "" : s1.getDisplayname())
                        + "," + (s1.getType() == null ? "" : s1.getType())
                        + "," + (s1.getExchange() == null ? "" : s1.getExchange())
                        + "," + (s1.getPrimaryexchange() == null ? "" : s1.getPrimaryexchange())
                        + "," + (s1.getCurrency() == null ? "" : s1.getCurrency())
                        + "," + (s1.getExpiry() == null ? "" : s1.getExpiry())
                        + "," + (s1.getOption() == null ? "" : s1.getOption())
                        + "," + (s1.getRight() == null ? "" : s1.getRight())
                        + "," + (s1.getMinsize() == 0 ? 1 : s1.getMinsize())
                        + "," + (s1.getBarsstarttime() == null ? "" : s1.getBarsstarttime())
                        + "," + (s1.getStreamingpriority() == 0 ? "10" : s1.getStreamingpriority())
                        + "," + (s1.getStrategy() == null ? "NONE" : s1.getStrategy());
                Utilities.writeToFile(outputFile, content);
            }
        }
    }

    public HashMap<String,Integer> getContractSizes(String expiry,String url,int rowsToSkip) throws ParseException, MalformedURLException, IOException{
        HashMap<String,Integer> out=new HashMap<>();
        ArrayList<BeanSymbol> fnoSymbols = new ArrayList<>();
        SimpleDateFormat formatInFile = new SimpleDateFormat("MMM-yy");
        SimpleDateFormat sdf_yyyyMMdd = new SimpleDateFormat("yyyyMMdd");
        String expiryFormattedAsInput = formatInFile.format(sdf_yyyyMMdd.parse(expiry));
        int columnNumber = -1;
        URL fnoURL = new URL(url);
        if (getResponseCode(url) != 404) {
            BufferedReader in = new BufferedReader(new InputStreamReader(fnoURL.openStream()));
            int j = 0;
            int i = 0;
            String line;
            while ((line = in.readLine()) != null) {
                j = j + 1;
                if (j > rowsToSkip) {
                    String[] input = line.split(",");
                    if (j == rowsToSkip + 1) {//we are on the first row containing expiration information
                        for (String inp : input) {
                            if (Utilities.isDate(inp, formatInFile)) {
                                String expiration = inp.trim().toUpperCase();
                                if (expiration.equalsIgnoreCase(expiryFormattedAsInput)) {
                                    columnNumber = i;
                                    break;
                                }
                            }
                            i = i + 1;
                        }
                    } else if (columnNumber >= 0) {
                        if (input[1].trim().length() > 0) {//not an empty row
                            String exchangesymbol = input[1].trim().toUpperCase();
                            String displayName = input[1].trim().toUpperCase().replaceAll("[^A-Za-z0-9]", "");
                            int minsize = Utilities.getInt(input[columnNumber], 0);
                            if (minsize > 0) {
                                int id = Utilities.getIDFromExchangeSymbol(symbols, exchangesymbol, "STK", "", "", "");
                                if(id>=0){
                                BeanSymbol s = symbols.get(id);
                                BeanSymbol s1 = s.clone(s);
                                s1.setType("FUT");
                                s1.setExpiry(expiry);
                                s1.setMinsize(minsize);
                                s1.setStrategy("DATA");
                                s1.setDisplayname(displayName);
                                s1.setStreamingpriority(4);
                                fnoSymbols.add(s1);
                                }else{
                                    logger.log(Level.SEVERE,"Symbol not found in IB database corresponding to NSE Symbol {0}",new Object[]{exchangesymbol});
                                }
                            } else {
                                //do not add row as minsize was not available
                            }
                        } else {
                            //do nothing. Empty row
                        }
                    } else {
                        //do nothing
                    }
                }
            
    }
        }
    for(BeanSymbol s:fnoSymbols){
        out.put(s.getExchangeSymbol(), s.getMinsize());
    }
    return out;
    }
    
    public void extractSymbolsFromIB(String urlName, String fileName, ArrayList<BeanSymbol> symbols) throws IOException {
        String constant = "&sequence_idx=";
        if (urlName != null) {
            String exchange = urlName.split("&")[1].split("=")[1].toUpperCase();
            String type = urlName.split("&")[2].split("=")[1].toUpperCase();
            for (int pageno = 100; pageno < 10000; pageno = pageno + 100) {
                String url = urlName + constant + pageno;
                System.out.println("Parsing :" + pageno);
                org.jsoup.nodes.Document stockList = Jsoup.connect(url).timeout(0).get();
                if (stockList.getElementsMatchingText("No result for this combination").size() > 0) {
                    break;
                } else {
                    Element tbl = null;
                    Elements shortlist = stockList.getElementsByClass("table-responsive");
                    for (Element e : shortlist) {
                        if (e.getElementsMatchingOwnText("IB Symbol").size() > 0 && e.getElementsMatchingOwnText("Product Description").size() > 0) {
                            tbl = e;
                            break;
                        }
                    }
                    //tbl = stockList.getElementsByClass("comm_table_background").get(4); //Todo: Check for 404 error
                    Elements rows = tbl.select("tr");
                    int i = 0;
                    for (Element stockRow : rows) {
                        if (i >= 2) {
                            //if (stockRow.attr("class").equals("linebottom")) {
                            BeanSymbol tempContract = new BeanSymbol();
                            String tempIBSymbol = stockRow.getElementsByTag("td").get(0).text().toUpperCase().trim();
                            String tempLongName = stockRow.getElementsByTag("td").get(1).text().toUpperCase().trim();
                            String tempContractID = stockRow.getElementsByTag("td").get(1).getElementsByTag("a").get(0).attr("href").split("&")[2].split("conid=")[1].split("'")[0].toUpperCase().trim();
                            String tempCurrency = stockRow.getElementsByTag("td").get(3).text().toUpperCase().trim();
                            String tempExchangeSymbol = stockRow.getElementsByTag("td").get(2).text().toUpperCase().trim();
                            tempContract.setContractID(Utilities.getInt(tempContractID, 0));
                            tempContract.setCurrency(tempCurrency);
                            tempContract.setBrokerSymbol(tempIBSymbol);
                            tempContract.setLongName(tempLongName);
                            int index=tempExchangeSymbol.indexOf("_");
                            tempExchangeSymbol=index>0?tempExchangeSymbol.substring(0, index+1):tempExchangeSymbol;
                            tempContract.setExchangeSymbol(tempExchangeSymbol);
                            tempContract.setExchange(exchange);
                            tempContract.setType(type);
                            symbols.add(tempContract);
                            System.out.println(tempContract.getExchangeSymbol());
                        }
                        i++;
                    }
                }
            }
            //add serial nos
            //update serial nos
            for (int k = 0; k < symbols.size(); k++) {
                symbols.get(k).setSerialno(k + 1);
            }
            Utilities.deleteFile(fileName);
            //Save to filename, if provided
            if (fileName != null) {
                for (BeanSymbol c : symbols) {
                    c.writer(fileName);
                }
            }

        }
    }

    /**
     * Creates a brokerSymbol file that is used by historical data collector.
     *
     * @param url
     */
    public void rateServerFutures(String url, String expiry, int rowsToSkip, String outputfilename) throws MalformedURLException, IOException, ParseException {
        ArrayList<BeanSymbol> fnoSymbols = new ArrayList<>();
        SimpleDateFormat formatInFile = new SimpleDateFormat("MMM-yy");
        SimpleDateFormat sdf_yyyyMMdd = new SimpleDateFormat("yyyyMMdd");
        String expiryFormattedAsInput = formatInFile.format(sdf_yyyyMMdd.parse(expiry));
        int columnNumber = -1;
        URL fnoURL = new URL(url);
        if (getResponseCode(url) != 404) {
            BufferedReader in = new BufferedReader(new InputStreamReader(fnoURL.openStream()));
            int j = 0;
            int i = 0;
            String line;
            while ((line = in.readLine()) != null) {
                j = j + 1;
                if (j > rowsToSkip) {
                    String[] input = line.split(",");
                    if (j == rowsToSkip + 1) {//we are on the first row containing expiration information
                        for (String inp : input) {
                            if (Utilities.isDate(inp, formatInFile)) {
                                String expiration = inp.trim().toUpperCase();
                                if (expiration.equalsIgnoreCase(expiryFormattedAsInput)) {
                                    columnNumber = i;
                                    break;
                                }
                            }
                            i = i + 1;
                        }
                    } else if (columnNumber >= 0) {
                        if (input[1].trim().length() > 0) {//not an empty row
                            String exchangesymbol = input[1].trim().toUpperCase();
                            String displayName = input[1].trim().toUpperCase().replaceAll("[^A-Za-z0-9]", "");
                            int minsize = Utilities.getInt(input[columnNumber], 0);
                            if (minsize > 0) {
                                int id = Utilities.getIDFromExchangeSymbol(symbols, exchangesymbol, "STK", "", "", "");
                                if(id>=0){
                                BeanSymbol s = symbols.get(id);
                                BeanSymbol s1 = s.clone(s);
                                s1.setType("FUT");
                                s1.setExpiry(expiry);
                                s1.setMinsize(minsize);
                                s1.setStrategy("DATA");
                                s1.setDisplayname(displayName);
                                s1.setStreamingpriority(4);
                                fnoSymbols.add(s1);
                                }else{
                                    //System.out.println("Incorrect id for "+exchangesymbol);
                                   logger.log(Level.SEVERE,"Futures symbol not found in IB for exchange name {0}",new Object[]{exchangesymbol});
                                }
                            } else {
                                //do not add row as minsize was not available
                            }
                        } else {
                            //do nothing. Empty row
                        }
                    } else {
                        //do nothing
                    }
                }
            }
            //update serial nos
            for (int k = 0; k < fnoSymbols.size(); k++) {
                fnoSymbols.get(k).setSerialno(k + 2);//as the first row NIFTY50 is already K+1
            }
            //now write data to file
            File outputFile = new File("logs",outputfilename);
            if (fnoSymbols.size() > 0) {
                //write header
                Utilities.deleteFile(outputFile);
                String header = "serialno,brokersymbol,exchangesymbol,displayname,type,exchange,primaryexchange,currency,expiry,option,right,minsize,barstarttime,streaming,strategy";
                Utilities.writeToFile(outputFile, header);
                //Write Index row
                String content = 1 + "," + "NIFTY50" + "," + "NSENIFTY" + "," + "NSENIFTY" + "," + "FUT" + "," + "NSE" + "," + "" + "," + "INR" + "," + expiry + "," + "" + "," + "" + "," + 75 + "," + "" + "," + 4 + "," + "DATA";
                Utilities.writeToFile(outputFile, content);

                for (BeanSymbol s : fnoSymbols) {
                    content = s.getSerialno() + "," + s.getBrokerSymbol() + "," + (s.getExchangeSymbol() == null ? "" : s.getExchangeSymbol())
                            + "," + (s.getDisplayname() == null ? "" : s.getDisplayname())
                            + "," + (s.getType() == null ? "" : s.getType())
                            + "," + (s.getExchange() == null ? "" : s.getExchange())
                            + "," + (s.getPrimaryexchange() == null ? "" : s.getPrimaryexchange())
                            + "," + (s.getCurrency() == null ? "" : s.getCurrency())
                            + "," + (s.getExpiry() == null ? "" : s.getExpiry())
                            + "," + (s.getOption() == null ? "" : s.getOption())
                            + "," + (s.getRight() == null ? "" : s.getRight())
                            + "," + (s.getMinsize() == 0 ? 1 : s.getMinsize())
                            + "," + (s.getBarsstarttime() == null ? "" : s.getBarsstarttime())
                            + "," + (s.getStreamingpriority() == 0 ? "10" : s.getStreamingpriority())
                            + "," + (s.getStrategy() == null ? "NONE" : s.getStrategy());
                    Utilities.writeToFile(outputFile, content);
                }
                //write END row
                content = fnoSymbols.size() + 2 + "," + "END" + "," + "END" + "," + "END" + "," + "END" + "," + "END" + "," + "END" + "," + "" + "," + "" + "," + "" + "," + "" + "," + "1" + "," + "" + "," + 4 + "," + "DATA";
                Utilities.writeToFile(outputFile, content);

            }
        }
    }

    /**
     * Creates data file for collection of 1 second stock data for cnx500.Supremeind is an outlier that is added manually
     * @param url
     * @param rowsToSkip
     * @param outputfilename
     * @throws MalformedURLException
     * @throws IOException
     * @throws ParseException 
     */
    public void rateServerStocks(String url, int rowsToSkip, String outputfilename) throws MalformedURLException, IOException, ParseException {
        ArrayList<BeanSymbol> stockSymbols = new ArrayList<>();
        //load existing file, if exists
        File f = new File("logs",outputfilename);
        if (f.exists() && !f.isDirectory()) {
            new BeanSymbol().reader(outputfilename, stockSymbols);
        }
        SimpleDateFormat formatInFile = new SimpleDateFormat("MMM-yy");
        SimpleDateFormat sdf_yyyyMMdd = new SimpleDateFormat("yyyyMMdd");
        int columnNumber = -1;
        URL stockURL = new URL(url);
        if (getResponseCode(url) != 404) {
            BufferedReader in = new BufferedReader(new InputStreamReader(stockURL.openStream()));
            int j = 0;
            int i = 0;
            String line;
            while ((line = in.readLine()) != null) {
                j = j + 1;
                if (j > rowsToSkip) {
                    String[] input = line.split(",");
                    if (input[2].trim().length() > 0) {//not an empty row
                        String exchangesymbol = input[2].trim().toUpperCase();
                        String displayName = input[2].trim().toUpperCase().replaceAll("[^A-Za-z0-9]", "");
                        int minsize = 1;
                        int id = Utilities.getIDFromExchangeSymbol(symbols, exchangesymbol, "STK", "", "", "");
                        int existingID = Utilities.getIDFromExchangeSymbol(stockSymbols, exchangesymbol, "STK", "", "", "");
                        if (id >= 0 && existingID == -1) {//symbol not in existing file
                            BeanSymbol s = symbols.get(id);
                            BeanSymbol s1 = s.clone(s);
                            s1.setType("STK");
                            s1.setExpiry("");
                            s1.setMinsize(minsize);
                            s1.setStrategy("DATA");
                            s1.setDisplayname(displayName);
                            s1.setStreamingpriority(4);
                            stockSymbols.add(s1);
                        } else if (id > 0 && existingID > 0) {
                            BeanSymbol s = stockSymbols.get(existingID);
                            s.setType("STK");
                            s.setExpiry("");
                            s.setMinsize(minsize);
                            s.setStrategy("DATA");
                            s.setDisplayname(displayName);
                            s.setStreamingpriority(4);

                        } else {
                            System.out.println("Exchange Symbol " + exchangesymbol + " not found in IB data");
                        }
                    }
                }
            }
        }

        //now write data to file
        File outputFile = new File("logs",outputfilename);
        if (stockSymbols.size() > 0) {
            if (outputFile.exists()) {
                stockSymbols.remove(0);//remove NIFTY
                int last = stockSymbols.size() - 1;
                stockSymbols.remove(last); //remove END

            }
            Utilities.deleteFile(outputFile);

            //update serial nos
            for (int k = 0; k < stockSymbols.size(); k++) {
                stockSymbols.get(k).setSerialno(k + 2);
            }

            String header = "serialno,brokersymbol,exchangesymbol,displayname,type,exchange,primaryexchange,currency,expiry,option,right,minsize,barstarttime,streaming,strategy";
            Utilities.writeToFile(outputFile, header);
            //Write Index row

            String content = 1 + "," + "NIFTY50" + "," + "NSENIFTY" + "," + "NSENIFTY" + "," + "IND" + "," + "NSE" + "," + "" + "," + "INR" + "," + "" + "," + "" + "," + "" + "," + 1 + "," + "" + "," + 4 + "," + "DATA";
            Utilities.writeToFile(outputFile, content);

            for (BeanSymbol s : stockSymbols) {
                content = s.getSerialno() + "," + s.getBrokerSymbol() + "," + (s.getExchangeSymbol() == null ? "" : s.getExchangeSymbol())
                        + "," + (s.getDisplayname() == null ? "" : s.getDisplayname())
                        + "," + (s.getType() == null ? "" : s.getType())
                        + "," + (s.getExchange() == null ? "" : s.getExchange())
                        + "," + (s.getPrimaryexchange() == null ? "" : s.getPrimaryexchange())
                        + "," + (s.getCurrency() == null ? "" : s.getCurrency())
                        + "," + (s.getExpiry() == null ? "" : s.getExpiry())
                        + "," + (s.getOption() == null ? "" : s.getOption())
                        + "," + (s.getRight() == null ? "" : s.getRight())
                        + "," + (s.getMinsize() == 0 ? 1 : s.getMinsize())
                        + "," + (s.getBarsstarttime() == null ? "" : s.getBarsstarttime())
                        + "," + (s.getStreamingpriority() == 0 ? "10" : s.getStreamingpriority())
                        + "," + (s.getStrategy() == null ? "NONE" : s.getStrategy());
                Utilities.writeToFile(outputFile, content);
            }
            //write END row
            content = stockSymbols.size() + 2 + "," + "END" + "," + "END" + "," + "END" + "," + "END" + "," + "END" + "," + "END" + "," + "" + "," + "" + "," + "" + "," + "" + "," + "1" + "," + "" + "," + 4 + "," + "DATA";
            Utilities.writeToFile(outputFile, content);

        }
    }

    /**
     * Generates symbol file for adr
     * @param url
     * @param expiry
     * @param rowsToSkip
     * @param outputfilename
     * @throws MalformedURLException
     * @throws IOException
     * @throws ParseException 
     */
    public void inradr(String url, String expiry, int rowsToSkip, String outputfilename) throws MalformedURLException, IOException, ParseException {
        ArrayList<BeanSymbol> adrSymbols = new ArrayList<>();
        SimpleDateFormat formatInFile = new SimpleDateFormat("MMM-yy");
        SimpleDateFormat sdf_yyyyMMdd = new SimpleDateFormat("yyyyMMdd");
        int columnNumber = -1;
        URL stockURL = new URL(url);
        if (getResponseCode(url) != 404) {
            BufferedReader in = new BufferedReader(new InputStreamReader(stockURL.openStream()));
            int j = 0;
            int i = 0;
            String line;
            while ((line = in.readLine()) != null) {
                j = j + 1;
                if (j > rowsToSkip) {
                    String[] input = line.split(",");
                    if (input[2].trim().length() > 0) {//not an empty row
                        String exchangesymbol = input[2].trim().toUpperCase();
                        String displayName = input[2].trim().toUpperCase().replaceAll("[^A-Za-z0-9]", "");
                        int minsize = 1;
                        int id = Utilities.getIDFromExchangeSymbol(symbols, exchangesymbol, "STK", "", "", "");
                        if (id >= 0) {//symbol not in existing file
                            BeanSymbol s = symbols.get(id);
                            BeanSymbol s1 = s.clone(s);
                            s1.setType("STK");
                            s1.setExpiry("");
                            s1.setMinsize(minsize);
                            s1.setStrategy("ADR");
                            s1.setStreamingpriority(4);
                            adrSymbols.add(s1);
                        } else {
                            System.out.println("Exchange Symbol " + exchangesymbol + " not found in IB data");
                        }
                    }
                }
            }
        }

        //now write data to file
        File outputFile = new File("logs",outputfilename);
        if (adrSymbols.size() > 0) {
            Utilities.deleteFile(outputFile);

            //update serial nos
            for (int k = 0; k < adrSymbols.size(); k++) {
                adrSymbols.get(k).setSerialno(k + 2);
            }

            String header = "serialno,brokersymbol,exchangesymbol,displayname,type,exchange,primaryexchange,currency,expiry,option,right,minsize,barstarttime,streaming,strategy";
            Utilities.writeToFile(outputFile, header);
            //Write Index row

            String content = 1 + "," + "NIFTY50" + "," + "NSENIFTY" + "," + "" + "," + "FUT" + "," + "NSE" + "," + "" + "," + "INR" + "," + expiry + "," + "" + "," + "" + "," + 75 + "," + "" + "," + 0 + "," + "ADR";
            Utilities.writeToFile(outputFile, content);

            for (BeanSymbol s : adrSymbols) {
                content = s.getSerialno() + "," + s.getBrokerSymbol() + "," + (s.getExchangeSymbol() == null ? "" : s.getExchangeSymbol())
                        + "," + (s.getDisplayname() == null ? "" : s.getDisplayname())
                        + "," + (s.getType() == null ? "" : s.getType())
                        + "," + (s.getExchange() == null ? "" : s.getExchange())
                        + "," + (s.getPrimaryexchange() == null ? "" : s.getPrimaryexchange())
                        + "," + (s.getCurrency() == null ? "" : s.getCurrency())
                        + "," + (s.getExpiry() == null ? "" : s.getExpiry())
                        + "," + (s.getOption() == null ? "" : s.getOption())
                        + "," + (s.getRight() == null ? "" : s.getRight())
                        + "," + (s.getMinsize() == 0 ? 1 : s.getMinsize())
                        + "," + (s.getBarsstarttime() == null ? "" : s.getBarsstarttime())
                        + "," + (s.getStreamingpriority() == 0 ? "1" : s.getStreamingpriority())
                        + "," + (s.getStrategy() == null ? "ADR" : s.getStrategy());
                Utilities.writeToFile(outputFile, content);
            }
        }
    }

        /**
     * Generates symbol file for swing
     * @param url
     * @param expiry
     * @param rowsToSkip
     * @param outputfilename
     * @throws MalformedURLException
     * @throws IOException
     * @throws ParseException 
     */
    public void inrswing(String url, String expiry, int rowsToSkip, String outputfilename) throws MalformedURLException, IOException, ParseException {
        ArrayList<BeanSymbol> swingSymbols = new ArrayList<>();
        
        //update index
            BeanSymbol s = new BeanSymbol("NIFTY50", "NSENIFTY", "IND", "", "", "");
            s.setCurrency("INR");
            s.setExchange("NSE");
            s.setStreamingpriority(1);
            s.setStrategy("SWING");
            s.setMinsize(75);
            swingSymbols.add(s);
            s = new BeanSymbol("NIFTY50", "NSENIFTY", "FUT", expiry, "", "");
            s.setCurrency("INR");
            s.setExchange("NSE");
            s.setStreamingpriority(1);
            s.setStrategy("SWING");
            s.setMinsize(75);
            swingSymbols.add(s);
            s = new BeanSymbol("NIFTY50", "NSENIFTY", "FUT", this.getNextExpiry(expiry), "", "");
            s.setCurrency("INR");
            s.setExchange("NSE");
            s.setStreamingpriority(1);
            s.setStrategy("SWING");
            s.setMinsize(75);
            swingSymbols.add(s);
            
        String strategySymbols[]=new String[]{"ACC","AMBUJACEM","ASIANPAINT","AXISBANK","BANKBARODA","BHARTIARTL","BHEL","BPCL","CAIRN","CIPLA","DRREDDY","GAIL","GRASIM","HCLTECH","HDFC","HDFCBANK","HINDALCO","HINDUNILVR","ICICIBANK","IDEA","INDUSINDBK","ITC","KOTAKBANK","LT","LUPIN","MARUTI","M&M","NTPC","ONGC","PNB","POWERGRID","RELIANCE","SBIN","SUNPHARMA","TATAMOTORS","TATAPOWER","TATASTEEL","TCS","TECHM","ULTRACEMCO","WIPRO","YESBANK","ZEEL"};
        SimpleDateFormat formatInFile = new SimpleDateFormat("MMM-yy");
        SimpleDateFormat sdf_yyyyMMdd = new SimpleDateFormat("yyyyMMdd");
        int columnNumber = -1;
                for(String sym:strategySymbols){
                        String exchangesymbol = sym;
                        String displayName = sym.trim().toUpperCase().replaceAll("[^A-Za-z0-9]", "");
                        int minsize = 1;
                        int id = Utilities.getIDFromExchangeSymbol(symbols, exchangesymbol, "STK", "", "", "");
                        if (id >= 0) {//symbol not in existing file
                            s = symbols.get(id);
                            BeanSymbol s1 = s.clone(s);
                            s1.setType("FUT");
                            s1.setExpiry("");
                            s1.setMinsize(minsize);
                            s1.setStrategy("SWING");
                            s1.setStreamingpriority(2);
                            swingSymbols.add(s1);
            } else {
                System.out.println("Exchange Symbol " + exchangesymbol + " not found in IB data");
            }
        }
        HashMap<String, Integer> nearExpiry = getContractSizes(expiry, url, 11);
        String nextExpiry = getNextExpiry(expiry);
        HashMap<String, Integer> farExpiry = getContractSizes(nextExpiry, url, 11);


        //now write data to file
        File outputFile = new File("logs",outputfilename);
        if (swingSymbols.size() > 0) {
            Utilities.deleteFile(outputFile);

       
            //update future expiry 1
            for(BeanSymbol s1:swingSymbols){
                if(s1.getType()!="IND"){
                Integer size=nearExpiry.get(s1.getExchangeSymbol());
                if(size!=null){
                s1.setMinsize(size);
                s1.setExpiry(expiry);
                }
                }
            }
            //create next month expiry
            ArrayList<BeanSymbol>second=new ArrayList<BeanSymbol>();
            String nextExipiry=getNextExpiry(expiry);
            for(BeanSymbol s1:swingSymbols){
                if(s1.getType()!="IND" && !s1.getExchangeSymbol().equals("NSENIFTY")){
                int size=farExpiry.get(s1.getExchangeSymbol());
                BeanSymbol s2=s1.clone(s1);
                s2.setMinsize(size);
                s2.setExpiry(nextExpiry);
                second.add(s2);
                }
            }
            
            swingSymbols.addAll(second);
            ArrayList<BeanSymbol>third=new ArrayList<BeanSymbol>();
            
            for(BeanSymbol s1:second){
                 if(s1.getType()!="IND" && !s1.getExchangeSymbol().equals("NSENIFTY")){
                     BeanSymbol s2=s1.clone(s1);
                     s2.setType("STK");
                     s2.setExpiry("");
                     third.add(s2);
                 }
            }
            swingSymbols.addAll(third);
            //update serial nos
            for (int k = 0; k < swingSymbols.size(); k++) {
                swingSymbols.get(k).setSerialno(k+1);
            }

            String header = "serialno,brokersymbol,exchangesymbol,displayname,type,exchange,primaryexchange,currency,expiry,option,right,minsize,barstarttime,streaming,strategy";
            Utilities.writeToFile(outputFile, header);
            //Write Index row

            String content = 1 + "," + "NIFTY50" + "," + "NSENIFTY" + "," + "" + "," + "FUT" + "," + "NSE" + "," + "" + "," + "INR" + "," + expiry + "," + "" + "," + "" + "," + 75 + "," + "" + "," + 0 + "," + "ADR";

            for (BeanSymbol s1 : swingSymbols) {
                content = s1.getSerialno() + "," + s1.getBrokerSymbol() + "," + (s1.getExchangeSymbol() == null ? "" : s1.getExchangeSymbol())
                        + "," + (s1.getDisplayname() == null ? "" : s1.getDisplayname())
                        + "," + (s1.getType() == null ? "" : s1.getType())
                        + "," + (s1.getExchange() == null ? "" : s1.getExchange())
                        + "," + (s1.getPrimaryexchange() == null ? "" : s1.getPrimaryexchange())
                        + "," + (s1.getCurrency() == null ? "" : s1.getCurrency())
                        + "," + (s1.getExpiry() == null ? "" : s1.getExpiry())
                        + "," + (s1.getOption() == null ? "" : s1.getOption())
                        + "," + (s1.getRight() == null ? "" : s1.getRight())
                        + "," + (s1.getMinsize() == 0 ? 1 : s1.getMinsize())
                        + "," + (s1.getBarsstarttime() == null ? "" : s1.getBarsstarttime())
                        + "," + (s1.getStreamingpriority() == 0 ? "1" : s1.getStreamingpriority())
                        + "," + (s1.getStrategy() == null ? "SWING" : s1.getStrategy());
                Utilities.writeToFile(outputFile, content);
            }
        }
    }

    
    public int getResponseCode(String urlString) throws MalformedURLException, IOException {
        URL u = new URL(urlString);
        HttpURLConnection huc = (HttpURLConnection) u.openConnection();
        huc.setRequestMethod("GET");
        huc.connect();
        return huc.getResponseCode();
    }

    /**
     * Returns the next expiration date, given today's date.It assumes that the
     * program is run EOD, so the next expiration date is calculated after the
     * completion of today.
     *
     * @param currentDay
     * @return
     */
    public String getNextExpiry(String currentDay) throws IOException, ParseException {
        SimpleDateFormat sdf_yyyMMdd = new SimpleDateFormat("yyyyMMdd");
        Date today = sdf_yyyMMdd.parse(currentDay);
        Calendar cal_today = Calendar.getInstance(TimeZone.getTimeZone(Algorithm.timeZone));
        cal_today.setTime(today);
        int year = Utilities.getInt(currentDay.substring(0, 4), 0);
        int month = Utilities.getInt(currentDay.substring(4, 6), 0) - 1;//calendar month starts at 0
        Date expiry = getLastThursday(month, year);
        expiry = Utilities.nextGoodDay(expiry, 0, Algorithm.timeZone, Algorithm.openHour, Algorithm.openMinute, Algorithm.closeHour, Algorithm.closeMinute, null, true);
        Calendar cal_expiry = Calendar.getInstance(TimeZone.getTimeZone(Algorithm.timeZone));
        cal_expiry.setTime(expiry);
        if (cal_expiry.get(Calendar.DAY_OF_MONTH) > cal_today.get(Calendar.DAY_OF_MONTH)) {
            return sdf_yyyMMdd.format(expiry);
        } else {
            if (month == 11) {//we are in decemeber
                //expiry will be at BOD, so we get the next month, till new month==0
                while(month!=0){
                expiry = Utilities.nextGoodDay(expiry, 24*60, Algorithm.timeZone, Algorithm.openHour, Algorithm.openMinute, Algorithm.closeHour, Algorithm.closeMinute, null, true);
                year = Utilities.getInt(sdf_yyyMMdd.format(expiry).substring(0, 4), 0);
                month = Utilities.getInt(sdf_yyyMMdd.format(expiry).substring(4, 6), 0) - 1;//calendar month starts at 0
                }
                expiry = getLastThursday(month, year);
                expiry = Utilities.nextGoodDay(expiry, 0, Algorithm.timeZone, Algorithm.openHour, Algorithm.openMinute, Algorithm.closeHour, Algorithm.closeMinute, null, true);
                return sdf_yyyMMdd.format(expiry);
            } else {
                expiry = getLastThursday(month + 1, year);
                expiry = Utilities.nextGoodDay(expiry, 0, Algorithm.timeZone, Algorithm.openHour, Algorithm.openMinute, Algorithm.closeHour, Algorithm.closeMinute, null, true);
                return sdf_yyyMMdd.format(expiry);
            }
        }
    }

    public Date getLastThursday(int month, int year) {
        //http://stackoverflow.com/questions/76223/get-last-friday-of-month-in-java
        Calendar cal = Calendar.getInstance();
        cal.set(year, month, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        cal.set(GregorianCalendar.DAY_OF_WEEK, Calendar.THURSDAY);
        cal.set(GregorianCalendar.DAY_OF_WEEK_IN_MONTH, -1);
        return cal.getTime();
    }
}
