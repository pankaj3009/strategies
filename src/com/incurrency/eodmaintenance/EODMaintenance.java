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
import java.util.Properties;
import java.util.TimeZone;
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

    /**
     * @param args the command line arguments
     */
    public EODMaintenance(String propertyFileName) throws Exception {
        properties = Utilities.loadParameters(propertyFileName);
        SimpleDateFormat sdf_yyyyMMdd = new SimpleDateFormat("yyyyMMdd");
        String fnolotsizeurl = properties.getProperty("fnolotsizeurl", "http://www.nseindia.com/content/fo/fo_mktlots.csv").toString().trim();
        String cnx500url = properties.getProperty("fnolotsizeurl", "http://www.nseindia.com/content/indices/ind_cnx500list.csv").toString().trim();
        String currentDay = properties.getProperty("currentday", sdf_yyyyMMdd.format(Calendar.getInstance(TimeZone.getTimeZone(Algorithm.timeZone)).getTime()));
        String historicalfutures = properties.getProperty("historicalfutures");
        String historicalstocks = properties.getProperty("historicalstocks");
        String adrinrstocks = properties.getProperty("adrinrstocks");
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
        if (adrinrstocks != null) {
            inradr(cnx500url, nextExpiry, 1, adrinrstocks);
        }
        if (rateserverinrmarket != null) {
            inrmarket(fnolotsizeurl, 11,cnx500url,1,nextExpiry, rateserverinrmarket);
        }
        MainAlgorithm.setCloseDate(new Date());
    }

    public void inrmarket(String fnourl, int rowsToSkipFNO, String stockurl, int rowsToSkipStocks, String expiry, String outputfilename) throws MalformedURLException, IOException, ParseException {
        //NSENIFTY, IND AND FUT is priority 1
        //FNO stocks are priority 2
        //Residual CNX500 stocks are priority 3
        ArrayList<BeanSymbol> stockSymbols = new ArrayList<>();
        BeanSymbol s = new BeanSymbol("NIFTY50", "NSENIFTY", "FUT", expiry, "", "");
        s.setCurrency("INR");
        s.setExchange("NSE");
        s.setStreamingpriority(1);
        s.setStrategy("DATA");
        stockSymbols.add(s);
        s = new BeanSymbol("NIFTY50", "NSENIFTY", "IND", "", "", "");
        s.setCurrency("INR");
        s.setExchange("NSE");
        s.setStreamingpriority(1);
        s.setStrategy("DATA");
        stockSymbols.add(s);

        //Add FNO Stocks
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
                                s = symbols.get(id);
                                BeanSymbol s1 = s.clone(s);
                                s1.setType("STK");
                                s1.setExpiry("");
                                s1.setMinsize(minsize);
                                s1.setStrategy("DATA");
                                s1.setStreamingpriority(2);
                                stockSymbols.add(s1);
                            }
                        }
                    }
                }
            }
        }

        //Residual CNX500 stocks are priority 2
        columnNumber = -1;
        URL stockURL = new URL(stockurl);
        if (getResponseCode(stockurl) != 404) {
            BufferedReader in = new BufferedReader(new InputStreamReader(stockURL.openStream()));
            int j = 0;
            int i = 0;
            String line;
            while ((line = in.readLine()) != null) {
                j = j + 1;
                if (j > rowsToSkipStocks) {
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
                            s1.setStreamingpriority(3);
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
        File outputFile = new File(outputfilename);
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

    public void extractSymbolsFromIB(String urlName, String fileName, ArrayList<BeanSymbol> symbols) throws IOException {
        String constant = "&sequence_idx=";
        if (urlName != null) {
            String exchange = urlName.split("&")[0].split("=")[1].toUpperCase();
            String type = urlName.split("&")[1].split("=")[1].toUpperCase();
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
                            tempContract.setExchangeSymbol(tempExchangeSymbol.replaceAll("_BE", ""));
                            tempContract.setExchange(exchange);
                            tempContract.setType(type);
                            symbols.add(tempContract);
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
                                BeanSymbol s = symbols.get(id);
                                BeanSymbol s1 = s.clone(s);
                                s1.setType("FUT");
                                s1.setExpiry(expiry);
                                s1.setMinsize(minsize);
                                s1.setStrategy("DATA");
                                s1.setStreamingpriority(4);
                                s1.setDisplayname(displayName);
                                fnoSymbols.add(s1);
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
            File outputFile = new File(outputfilename);
            if (fnoSymbols.size() > 0) {
                //write header
                Utilities.deleteFile(outputFile);
                String header = "serialno,brokersymbol,exchangesymbol,displayname,type,exchange,primaryexchange,currency,expiry,option,right,minsize,barstarttime,streaming,strategy";
                Utilities.writeToFile(outputFile, header);
                //Write Index row
                String content = 1 + "," + "NIFTY50" + "," + "NSENIFTY" + "," + "NSENIFTY" + "," + "FUT" + "," + "NSE" + "," + "" + "," + "INR" + "," + expiry + "," + "" + "," + "" + "," + 25 + "," + "" + "," + 4 + "," + "DATA";
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
                content = symbols.size() + 2 + "," + "END" + "," + "END" + "," + "END" + "," + "END" + "," + "END" + "," + "END" + "," + "" + "," + "" + "," + "" + "," + "" + "," + "" + "," + "" + "," + 4 + "," + "DATA";
                Utilities.writeToFile(outputFile, content);

            }
        }
    }

    public void rateServerStocks(String url, int rowsToSkip, String outputfilename) throws MalformedURLException, IOException, ParseException {
        ArrayList<BeanSymbol> stockSymbols = new ArrayList<>();
        //load existing file, if exists
        File f = new File(outputfilename);
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
                            s1.setStreamingpriority(4);
                            s1.setDisplayname(displayName);
                            stockSymbols.add(s1);
                        } else if (id > 0 && existingID > 0) {
                            BeanSymbol s = stockSymbols.get(existingID);
                            s.setType("STK");
                            s.setExpiry("");
                            s.setMinsize(minsize);
                            s.setStrategy("DATA");
                            s.setStreamingpriority(4);
                            s.setDisplayname(displayName);

                        } else {
                            System.out.println("Exchange Symbol " + exchangesymbol + " not found in IB data");
                        }
                    }
                }
            }
        }

        //now write data to file
        File outputFile = new File(outputfilename);
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
            content = stockSymbols.size() + 2 + "," + "END" + "," + "END" + "," + "END" + "," + "END" + "," + "END" + "," + "END" + "," + "" + "," + "" + "," + "" + "," + "" + "," + "" + "," + "" + "," + 4 + "," + "DATA";
            Utilities.writeToFile(outputFile, content);

        }
    }

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
                            s1.setDisplayname(displayName);
                            adrSymbols.add(s1);
                        } else {
                            System.out.println("Exchange Symbol " + exchangesymbol + " not found in IB data");
                        }
                    }
                }
            }
        }

        //now write data to file
        File outputFile = new File(outputfilename);
        if (adrSymbols.size() > 0) {
            Utilities.deleteFile(outputFile);

            //update serial nos
            for (int k = 0; k < adrSymbols.size(); k++) {
                adrSymbols.get(k).setSerialno(k + 2);
            }

            String header = "serialno,brokersymbol,exchangesymbol,displayname,type,exchange,primaryexchange,currency,expiry,option,right,minsize,barstarttime,streaming,strategy";
            Utilities.writeToFile(outputFile, header);
            //Write Index row

            String content = 1 + "," + "NIFTY50" + "," + "NSENIFTY" + "," + "NSENIFTY" + "," + "FUT" + "," + "NSE" + "," + "" + "," + "INR" + "," + expiry + "," + "" + "," + "" + "," + 25 + "," + "" + "," + 0 + "," + "ADR";
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
            if (cal_today.get(Calendar.MONTH) == 11) {//we are in decemeber
                expiry = getLastThursday(month, year + 1);
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
