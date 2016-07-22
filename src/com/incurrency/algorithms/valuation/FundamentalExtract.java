/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.algorithms.valuation;


import com.incurrency.framework.ReaderWriterInterface;
import com.incurrency.framework.Utilities;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author pankaj
 */
public class FundamentalExtract implements ReaderWriterInterface{
    
    public String symbol; //IB Website
    public String exchange; //IB Website
    public String periodicity;//Annual or Interim
    public String currency; //IB Website
    public String estimateYear;// from parameter file
    public String ebitEstimate; //Estimates
    public String ebitEstimateSD="0"; //Estimates
    public String ebitEstimateCount="0";//Estimates
    public String analystEstimateAvailable;
    public String ebitActual;//finstat
    public String ebitActualYear;//finstat
    public String balanceSheetDuration; //finstat
    public String totalDebt; //finstat
    public String interestExpense; //finstat
    public String cashandstinvestments; //finstat
    public String minorityInterest; //finstat
    public String sharePrice; //snapshot
    public String commonShares; //snapshot
    public String accountsReceivable; //finstat
    public String inventories;//finstat
    public String accountsPayable;//finstat
    public String financialsDate;//finstat
    public String reportingCurrency;//finstat
    public String exchangeRate;//snapshot
    public String estimatesExchangeCurrency;// Estimates
    public String economicSector;
    public String beta;
    public String costOfEquity;
    public String costOfDebt;
    public String costOfCapital;
    public String ev;
    public String result;
    public String niftySD;
    public String sd;
    public String correlation;
    public ArrayList<Double> returns=new ArrayList<>();
    
    private static final Logger logger = Logger.getLogger(FundamentalExtract.class.getName());

   
    public FundamentalExtract(){
        
    }
    
    public FundamentalExtract(String ibSymbol, String exchange, String currency){
        this.symbol=ibSymbol;
        this.exchange=exchange;
        this.currency=currency;
    }
            
    public FundamentalExtract(String input[]){
     symbol = input[0];
     exchange = input[1];
     currency = input[2];
     estimateYear = input[3];
     ebitEstimate = input[4];
     ebitEstimateSD = input[5];
     ebitEstimateCount = input[6];
     ebitActual = input[7];
     ebitActualYear = input[8];
     balanceSheetDuration = input[9];
     totalDebt = input[10];
     interestExpense = input[11];
     cashandstinvestments = input[12];
     minorityInterest = input[13];
     sharePrice = input[14];
     commonShares = input[15];
     accountsReceivable = input[16];
     inventories = input[17];
     accountsPayable = input[18];
     financialsDate = input[19];
     reportingCurrency = input[20];
     exchangeRate = input[21];
     estimatesExchangeCurrency = input[22];
     economicSector = input[23];
     beta = input[24];
     costOfEquity = input[25];
     costOfDebt = input[26];
     costOfCapital=input[27];
     ev = input[28];
     result = input[29];
     niftySD=input[30];
     sd=input[31];
     correlation=input[32];
    }
    
    @Override
    public void reader(String inputfile, List target) {
        
    File inputFile=new File(inputfile);
         if(inputFile.exists() && !inputFile.isDirectory()){
        try {
            List<String> existingFundamentalLoad=Files.readAllLines(Paths.get(inputfile), StandardCharsets.UTF_8);
            for(String fundamentaldataline: existingFundamentalLoad){
                String[] input=fundamentaldataline.split(",");
                target.add(new FundamentalExtract(input));
            }
        } catch (IOException ex) {
            Logger.getLogger(FundamentalExtract.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    }

    @Override
    public void writer(String fileName) {
               File f = new File(fileName);
        try {
            if (!f.exists() || f.isDirectory()) {
                String header = "Symbol,Exchange,Currency,Estimate Year, EBIT Estimate,EBIT Estimate SD, EBIT Estimate Count, Latest Annual EBIT, EBIT Year, BS Duration,Total Debt,Interest Expense,Cash and Investments,Minority Interest,Share Price,Common Shares,Accounts Receivable,Inventories,Accounts Payable,Financials Date,Reporting Currency,ExchangeRate,EstimatesCurrency,Economic Sector,Beta,Cost of Equity,Cost of Debt,Cost of Capital,EV,Result,Nifty SD, Symbol SD, Correlation" ;
                PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(fileName, true)));
                out.println(header);
                out.close();
            } 
                DecimalFormat df = new DecimalFormat("0.00##");
            if(result!=null && Utilities.isDouble(result)){
                String data = symbol+","+exchange+","+currency+","+estimateYear+","+ebitEstimate+","+ebitEstimateSD+","+ebitEstimateCount+","+ebitActual+","+ebitActualYear+","+balanceSheetDuration+","+totalDebt+","+interestExpense+","+cashandstinvestments+","+minorityInterest+","+sharePrice+","+commonShares+","+accountsReceivable+","+inventories+","+accountsPayable+","+financialsDate+","+reportingCurrency+","+exchangeRate+","+estimatesExchangeCurrency+","+economicSector+","+beta+","+costOfEquity+","+costOfDebt+","+costOfCapital+","+ev+","+ df.format(Double.valueOf(result)*100)+"%"+","+niftySD+","+sd+","+correlation;
                PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(fileName, true)));
                out.println(data);
                out.close();   
               
            }else{
                String data = symbol+","+exchange+","+currency+","+estimateYear+","+ebitEstimate+","+ebitEstimateSD+","+ebitEstimateCount+","+ebitActual+","+ebitActualYear+","+balanceSheetDuration+","+totalDebt+","+interestExpense+","+cashandstinvestments+","+minorityInterest+","+sharePrice+","+commonShares+","+accountsReceivable+","+inventories+","+accountsPayable+","+financialsDate+","+reportingCurrency+","+exchangeRate+","+estimatesExchangeCurrency+","+economicSector+","+beta+","+costOfEquity+","+costOfDebt+","+costOfCapital+","+ev+","+result+","+niftySD+","+sd+","+correlation;
                PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(fileName, true)));
                out.println(data);
                out.close();
            }
                

            
        } catch (Exception e) {
            logger.log(Level.SEVERE, null, e);
        }
    }
}
