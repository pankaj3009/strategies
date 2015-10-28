/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.algorithms.valuation;

import com.incurrency.RatesClient.Subscribe;
import com.incurrency.framework.BeanConnection;
import com.incurrency.framework.BeanPosition;
import com.incurrency.framework.BeanSymbol;
import com.incurrency.framework.DateUtil;
import com.incurrency.framework.MainAlgorithm;
import com.incurrency.framework.Parameters;
import com.incurrency.framework.Strategy;
import com.incurrency.framework.TradeEvent;
import com.incurrency.framework.TradeListener;
import com.incurrency.framework.TradingUtil;
import com.incurrency.framework.Utilities;
import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.jsoup.Jsoup;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 *
 * @author pankaj
 */
public class Valuation implements TradeListener {

    private static final Logger logger = Logger.getLogger(Valuation.class.getName());
    private String path;
    private String estimateYear;
    private String outputFile;
    public static HashMap<Integer, FundamentalExtract> fundamentalsAnnual = new HashMap<>();
    public static HashMap<Integer, FundamentalExtract> fundamentalsInterim = new HashMap<>();

    public Valuation(String parameterFileName) {
        Properties p = Utilities.loadParameters(parameterFileName);
        loadParameters(p);
        for (BeanSymbol s : Parameters.symbol) {
            calculateValuation(s.getSerialno() - 1);
        }



    }

    private void calculateValuation(int id) {
        //Read xml file
        String symbolName = Parameters.symbol.get(id).getBrokerSymbol();
        logger.log(Level.INFO, "Processing Symbol: {0}", new Object[]{Parameters.symbol.get(id).getBrokerSymbol()});
        try {
            //Generate Valuation
            //1. Read Snapshot
            File fXmlFile = new File(path + "//" + symbolName + "_" + "SNAPSHOT" + ".xml");
            Document doc = null;
            if (fXmlFile.exists()) {
                DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
                DocumentBuilder dBuilder;
                dBuilder = dbFactory.newDocumentBuilder();
                doc = dBuilder.parse(fXmlFile);
                doc.getDocumentElement().normalize();
                FundamentalExtract f = fundamentalsAnnual.get(id) == null ? new FundamentalExtract() : fundamentalsAnnual.get(id);
                Node ratios = doc.getElementsByTagName("Ratios").item(0);
                f.currency = ((Element) ratios).getAttribute("PriceCurrency");
                NodeList ratio = doc.getElementsByTagName("Ratio");
                for (int temp1 = 0; temp1 < ratio.getLength(); temp1++) {
                    switch (((Element) ratio.item(temp1)).getAttribute("FieldName")) {
                        case "NPRICE":
                            f.sharePrice = ((Element) ratio.item(temp1)).getTextContent();
                            break;
                        default:
                            break;
                    }
                }
                f.commonShares = doc.getElementsByTagName("SharesOut") != null ? String.valueOf(Double.parseDouble(doc.getElementsByTagName("SharesOut").item(0).getTextContent()) / 1000000) : "";
                if (doc.getElementsByTagName("IndustryInfo").getLength() > 0) {
                    Element industryInformation = (Element) doc.getElementsByTagName("IndustryInfo").item(0);
                    Element trbc = (Element) industryInformation.getElementsByTagName("Industry").item(0);
                    String code = trbc.getAttribute("code");
                    code = code.trim();
                    code = code.substring(0, 2);
                    switch (code) {
                        case "50":
                            f.economicSector = "Energy";
                            break;
                        case "51":
                            f.economicSector = "Basic Materials";
                            break;
                        case "52":
                            f.economicSector = "Industrials";
                            break;
                        case "53":
                            f.economicSector = "Cyclical Consumer Goods & Services";
                            break;
                        case "54":
                            f.economicSector = "Non-Cyclical Consumer Goods & Services";
                            break;
                        case "55":
                            f.economicSector = "Financials";
                            break;
                        case "56":
                            f.economicSector = "Healthcare";
                            break;
                        case "57":
                            f.economicSector = "Technology";
                            break;
                        case "58":
                            f.economicSector = "Telecommunications Services";
                            break;
                        case "59":
                            f.economicSector = "Utilities";
                            break;
                        default:
                            f.economicSector = "NA";
                            break;
                    }
                }
                fundamentalsAnnual.put(id, f);
            }
            //2. Read Estimates
            fXmlFile = new File(path + "//" + symbolName + "_" + "ESTIMATES" + ".xml");
            if (fXmlFile.exists()) {
                DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
                DocumentBuilder dBuilder;
                dBuilder = dbFactory.newDocumentBuilder();
                doc = dBuilder.parse(fXmlFile);
                doc.getDocumentElement().normalize();
                FundamentalExtract f = fundamentalsAnnual.get(id) == null ? new FundamentalExtract() : fundamentalsAnnual.get(id);
                NodeList fyEstimates = doc.getElementsByTagName("FYEstimate");
                outerloop:
                for (int temp1 = 0; temp1 < fyEstimates.getLength(); temp1++) {
                    Node fyEstimate = fyEstimates.item(temp1);
                    Element elFYEstimate = (Element) fyEstimate;
                    if (elFYEstimate.getAttribute("type").compareTo("EBIT") == 0) {
                        NodeList fyPeriods = elFYEstimate.getChildNodes();
                        for (int temp2 = 0; temp2 < fyPeriods.getLength(); temp2++) {
                            Node fyPeriod = fyPeriods.item(temp2);
                            Element elFYPeriod = (Element) fyPeriod;
                            if (elFYPeriod.getAttribute("fYear").compareTo(estimateYear) == 0 && elFYPeriod.getAttribute("periodType").compareTo("A") == 0) {
                                NodeList consEstimates = elFYPeriod.getChildNodes();
                                for (int temp3 = 0; temp3 < fyPeriods.getLength(); temp3++) {
                                    Node consEstimate = consEstimates.item(temp3);
                                    Element elConsEstimate = (Element) consEstimate;
                                    if (elConsEstimate.getAttribute("type").equals("Mean")) {
                                        NodeList consValues = elConsEstimate.getChildNodes();
                                        for (int temp4 = 0; temp4 < consValues.getLength(); temp4++) {
                                            Node consValue = consValues.item(temp4);
                                            Element elConsValue = (Element) consValue;
                                            if (elConsValue.getAttribute("dateType").equals("CURR")) {
                                                f.ebitEstimate = elConsValue.getTextContent();
                                                f.analystEstimateAvailable = "Yes";
                                                f.estimateYear=estimateYear;

                                            }
                                        }
                                    }
                                    if (elConsEstimate.getAttribute("type").equals("StdDev")) {
                                        NodeList consValues = elConsEstimate.getChildNodes();
                                        for (int temp4 = 0; temp4 < consValues.getLength(); temp4++) {
                                            Node consValue = consValues.item(temp4);
                                            Element elConsValue = (Element) consValue;
                                            if (elConsValue.getAttribute("dateType").equals("CURR")) {
                                                f.ebitEstimateSD = elConsValue.getTextContent();
                                                f.analystEstimateAvailable = "Yes";

                                            }
                                        }
                                    }
                                    if (elConsEstimate.getAttribute("type").equals("NumOfEst")) {
                                        NodeList consValues = elConsEstimate.getChildNodes();
                                        for (int temp4 = 0; temp4 < consValues.getLength(); temp4++) {
                                            Node consValue = consValues.item(temp4);
                                            Element elConsValue = (Element) consValue;
                                            if (elConsValue.getAttribute("dateType").equals("CURR")) {
                                                f.ebitEstimateCount = elConsValue.getTextContent();
                                                f.analystEstimateAvailable = "Yes";
                                                break outerloop;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Element currency = (Element) doc.getElementsByTagName("Currency").item(0);
                f.estimatesExchangeCurrency = currency.getAttribute("code");
                fundamentalsAnnual.put(id, f);
            }
            //3. Read FINSTAT
            fXmlFile = new File(path + "//" + symbolName + "_" + "FINSTAT" + ".xml");
            if (fXmlFile.exists()) {
                DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
                DocumentBuilder dBuilder;
                dBuilder = dbFactory.newDocumentBuilder();
                doc = dBuilder.parse(fXmlFile);
                doc.getDocumentElement().normalize();
                FundamentalExtract f = fundamentalsAnnual.get(id) == null ? new FundamentalExtract() : fundamentalsAnnual.get(id);
                //used latest available annual operating income as ebit estimate
                f.financialsDate = "1900-01-01";
                Node annualPeriod = doc.getElementsByTagName("AnnualPeriods").item(0);
                Node fiscalPeriod = ((Element) annualPeriod).getElementsByTagName("FiscalPeriod").item(0);
                NodeList fiscalPeriods = ((Element) annualPeriod).getElementsByTagName("FiscalPeriod");
                if (fiscalPeriods.getLength() > 0) {
                    fiscalPeriod = fiscalPeriods.item(0);
                    Element eFiscalPeriod = (Element) fiscalPeriod;
                    f.ebitActualYear = eFiscalPeriod.getAttribute("EndDate");
                }
                NodeList annualStatements = ((Element) annualPeriod).getElementsByTagName("Statement");
                ebitloop:
                for (int temp5 = 0; temp5 < annualStatements.getLength(); temp5++) {
                    Element elAnnualStatement = (Element) annualStatements.item(temp5);
                    if (elAnnualStatement.getAttribute("Type").equals("INC")) {
                        Element elAnnualHeader = (Element) elAnnualStatement.getElementsByTagName("FPHeader").item(0);
                        if (elAnnualHeader.getElementsByTagName("PeriodLength").item(0).getTextContent().compareTo("12") >= 0 && elAnnualHeader.getElementsByTagName("periodType").item(0).getTextContent().equals("Months") || elAnnualHeader.getElementsByTagName("PeriodLength").item(0).getTextContent().compareTo("52") >= 0 && elAnnualHeader.getElementsByTagName("periodType").item(0).getTextContent().equals("Weeks")) {
                            //we are in the latest annualreporting
                            f.balanceSheetDuration = elAnnualHeader.getElementsByTagName("PeriodLength").item(0).getTextContent();
                            NodeList lineItems = elAnnualStatement.getElementsByTagName("lineItem");
                            for (int temp6 = 0; temp6 < lineItems.getLength(); temp6++) {
                                Element elCOA = (Element) lineItems.item(temp6);
                                switch (elCOA.getAttribute("coaCode")) {
                                    case "SOPI":
                                        if (f.ebitEstimate == null) {
                                            f.ebitEstimate = elCOA.getTextContent();
                                            f.analystEstimateAvailable = "No";
                                        }
                                        f.ebitActual = elCOA.getTextContent();
                                        break;
                                    default:
                                        break;
                                }
                            }
                            break ebitloop;
                        }
                    }
                }

                boolean quarterlyDataReceived = false;
                boolean annualDataReceived = false;
                Node reportingCurrency = doc.getElementsByTagName("ReportingCurrency").item(0);
                Element elReportingCurrency = (Element) reportingCurrency;
                f.reportingCurrency = elReportingCurrency.getAttribute("Code");
                Node interimPeriod = doc.getElementsByTagName("InterimPeriods").item(0);
                fiscalPeriods = ((Element) interimPeriod).getElementsByTagName("FiscalPeriod");
                outerloop:
                for (int temp1 = 0; temp1 < fiscalPeriods.getLength(); temp1++) {
                    NodeList statements = ((Element) fiscalPeriods.item(temp1)).getElementsByTagName("Statement");
                    int fiscalPeriodIndex=0;
                    boolean incfound=false;
                    boolean bsfound=false;
                    boolean incbsfound=false;
                    for (int temp2 = 0; temp2 < statements.getLength(); temp2++) {
                        fiscalPeriodIndex=fiscalPeriodIndex+1;
                        //first check if the fiscal period has both income and balance sheet statement
                        Element elStatement = (Element) statements.item(temp2);
                        if (elStatement.getAttribute("Type").equals("INC")) {
                            incfound=true;
                        }else if(elStatement.getAttribute("Type").equals("BAL")){
                            bsfound=true;
                        }
                    }
                    incbsfound=incfound && bsfound;
                    if(incbsfound==true){
                        statements = ((Element) fiscalPeriods.item(fiscalPeriodIndex-1)).getElementsByTagName("Statement");
                        for (int temp2 = 0; temp2 < statements.getLength(); temp2++) {
                        Element elStatement=(Element)statements.item(temp2);
                           if (elStatement.getAttribute("Type").equals("INC")) {
                            Element elHeader = (Element) elStatement.getElementsByTagName("FPHeader").item(0);
                            NodeList lineItems = elStatement.getElementsByTagName("lineItem");
                            f.balanceSheetDuration = elHeader.getElementsByTagName("periodType").item(0).getTextContent().equals("Months") ? elHeader.getElementsByTagName("PeriodLength").item(0).getTextContent() : String.valueOf(Double.parseDouble(elHeader.getElementsByTagName("PeriodLength").item(0).getTextContent()) / 4);
                            for (int temp4 = 0; temp4 < lineItems.getLength(); temp4++) {
                                Element elCOA = (Element) lineItems.item(temp4);
                                switch (elCOA.getAttribute("coaCode")) {
                                    //case "SINN":
                                    case "SNIN":
                                        f.interestExpense = elCOA.getTextContent();
                                        break;
                                    default:
                                        break;

                                }
                            }
                        } else if (elStatement.getAttribute("Type").equals("BAL")) {
                            Element elHeader = (Element) elStatement.getElementsByTagName("FPHeader").item(0);
                            NodeList lineItems = elStatement.getElementsByTagName("lineItem");
                            f.financialsDate = elHeader.getElementsByTagName("StatementDate").item(0).getTextContent();
                            for (int temp4 = 0; temp4 < lineItems.getLength(); temp4++) {
                                Element elCOA = (Element) lineItems.item(temp4);
                                switch (elCOA.getAttribute("coaCode")) {
                                    case "STLD":
                                        f.totalDebt = elCOA.getTextContent();
                                        break;
                                    case "LAPB":
                                        f.accountsPayable = elCOA.getTextContent();
                                        break;
                                    case "AACR":
                                        f.accountsReceivable = elCOA.getTextContent();
                                        break;
                                    case "SCSI":
                                        f.cashandstinvestments = elCOA.getTextContent();
                                        break;
                                    case "QTCO":
                                        //f.commonShares = elCOA.getTextContent();
                                        break;
                                    case "AITL":
                                        f.inventories = elCOA.getTextContent();
                                        break;
                                    case "LMIN":
                                        f.minorityInterest = elCOA.getTextContent();
                                        break;
                                    default:
                                        break;
                                }
                            }
                            quarterlyDataReceived = true;
                            f.symbol = Parameters.symbol.get(id).getExchangeSymbol();
                            f.exchange = Parameters.symbol.get(id).getExchange();
                            f.estimateYear = this.estimateYear;
                            f.totalDebt = f.totalDebt == null ? "0" : f.totalDebt;
                            f.accountsPayable = f.accountsPayable == null ? "0" : f.accountsPayable;
                            f.accountsReceivable = f.accountsReceivable == null ? "0" : f.accountsReceivable;
                            f.cashandstinvestments = f.cashandstinvestments == null ? "0" : f.cashandstinvestments;
                            f.commonShares = f.commonShares == null ? "NA" : f.commonShares;
                            f.inventories = f.inventories == null ? "0" : f.inventories;
                            f.minorityInterest = f.minorityInterest == null ? "0" : f.minorityInterest;
                            f.ebitEstimate = f.ebitEstimate == null ? "NA" : f.ebitEstimate;
                            f.reportingCurrency = f.reportingCurrency == null ? "NA" : f.reportingCurrency;
                            f.estimatesExchangeCurrency = f.estimatesExchangeCurrency == null ? f.reportingCurrency : f.estimatesExchangeCurrency;
                            f.ebitActual = f.ebitActual == null ? "NA" : f.ebitActual;
                           //f.sharePrice=f.exchange.equals("LSE")?String.valueOf(Double.valueOf(f.sharePrice)/100):f.sharePrice;
                            fundamentalsAnnual.put(id, f);
                            break outerloop;
                        }
                        }
                    }
                }
                //Now get recent annual data and check if its reporting is greater than quarterly data
                annualPeriod = doc.getElementsByTagName("AnnualPeriods").item(0);
                fiscalPeriod = ((Element) annualPeriod).getElementsByTagName("FiscalPeriod").item(0);
                annualStatements = ((Element) annualPeriod).getElementsByTagName("Statement");
                balancesheetloop:
                for (int temp5 = 0; temp5 < annualStatements.getLength(); temp5++) {
                    Element elAnnualStatement = (Element) annualStatements.item(temp5);
                    Element elAnnualHeader = (Element) elAnnualStatement.getElementsByTagName("FPHeader").item(0);
                    String statementDate = elAnnualHeader.getElementsByTagName("StatementDate").item(0).getTextContent();
                    if (statementDate != null) {
                        if (elAnnualStatement.getAttribute("Type").equals("BAL")) {
                            if (DateUtil.parseDate("yyyy-MM-dd", f.financialsDate).before(DateUtil.parseDate("yyyy-MM-dd", statementDate))) {
                                //we are in the latest annualreporting which is greater than quarterly reporting date
                                f.financialsDate = elAnnualHeader.getElementsByTagName("StatementDate").item(0).getTextContent();
                                NodeList lineItems = elAnnualStatement.getElementsByTagName("lineItem");
                                for (int temp6 = 0; temp6 < lineItems.getLength(); temp6++) {
                                    Element elCOA = (Element) lineItems.item(temp6);
                                    //f.balanceSheetDuration = elAnnualHeader.getElementsByTagName("periodType").item(0).getTextContent().equals("Months") ? elAnnualHeader.getElementsByTagName("PeriodLength").item(0).getTextContent() : String.valueOf(Double.parseDouble(elAnnualHeader.getElementsByTagName("PeriodLength").item(0).getTextContent()) / 4);
                                    switch (elCOA.getAttribute("coaCode")) {
                                        case "STLD":
                                            f.totalDebt = elCOA.getTextContent();
                                            break;
                                        case "LAPB":
                                            f.accountsPayable = elCOA.getTextContent();
                                            break;
                                        case "AACR":
                                            f.accountsReceivable = elCOA.getTextContent();
                                            break;
                                        case "SCSI":
                                            f.cashandstinvestments = elCOA.getTextContent();
                                            break;
                                        case "QTCO":
                                            //f.commonShares = elCOA.getTextContent();
                                            break;
                                        case "AITL":
                                            f.inventories = elCOA.getTextContent();
                                            break;
                                        case "LMIN":
                                            f.minorityInterest = elCOA.getTextContent();
                                            break;
                                        default:
                                            break;
                                    }
                                }
                                annualDataReceived = true;
                                break;
                            }
                        } else if (elAnnualStatement.getAttribute("Type").equals("INC")) {
                            if ((elAnnualHeader.getElementsByTagName("PeriodLength").item(0).getTextContent().compareTo("12") >= 0 && elAnnualHeader.getElementsByTagName("periodType").item(0).getTextContent().equals("Months") || elAnnualHeader.getElementsByTagName("PeriodLength").item(0).getTextContent().compareTo("52") >= 0 && elAnnualHeader.getElementsByTagName("periodType").item(0).getTextContent().equals("Weeks"))
                                    && DateUtil.parseDate("yyyy-MM-dd", f.financialsDate).before(DateUtil.parseDate("yyyy-MM-dd", statementDate))) {
                                NodeList lineItems = elAnnualStatement.getElementsByTagName("lineItem");
                                for (int temp6 = 0; temp6 < lineItems.getLength(); temp6++) {
                                    Element elCOA = (Element) lineItems.item(temp6);
                                    f.balanceSheetDuration = elAnnualHeader.getElementsByTagName("periodType").item(0).getTextContent().equals("Months") ? elAnnualHeader.getElementsByTagName("PeriodLength").item(0).getTextContent() : String.valueOf(Double.parseDouble(elAnnualHeader.getElementsByTagName("PeriodLength").item(0).getTextContent()) / 4);
                                    switch (elCOA.getAttribute("coaCode")) {
                                        //case "SINN":
                                        case "SNIN":
                                            f.interestExpense = elCOA.getTextContent();
                                            break;
                                        default:
                                            break;
                                    }
                                }
                                f.balanceSheetDuration = elAnnualHeader.getElementsByTagName("periodType").item(0).getTextContent().equals("Months") ? elAnnualHeader.getElementsByTagName("PeriodLength").item(0).getTextContent() : String.valueOf(Double.parseDouble(elAnnualHeader.getElementsByTagName("PeriodLength").item(0).getTextContent()) / 4);

                            }
                        }
                    }
                }
                if (annualDataReceived) {
                    f.symbol=Parameters.symbol.get(id).getExchangeSymbol();
                    f.totalDebt = f.totalDebt == null ? "0" : f.totalDebt;
                    f.accountsPayable = f.accountsPayable == null ? "0" : f.accountsPayable;
                    f.accountsReceivable = f.accountsReceivable == null ? "0" : f.accountsReceivable;
                    f.cashandstinvestments = f.cashandstinvestments == null ? "0" : f.cashandstinvestments;
                    f.commonShares = f.commonShares == null ? "NA" : f.commonShares;
                    f.inventories = f.inventories == null ? "0" : f.inventories;
                    f.minorityInterest = f.minorityInterest == null ? "0" : f.minorityInterest;
                    f.ebitEstimate = f.ebitEstimate == null ? "NA" : f.ebitEstimate;
                    f.reportingCurrency = f.reportingCurrency == null ? "NA" : f.reportingCurrency;
                    f.estimatesExchangeCurrency = f.estimatesExchangeCurrency == null ? f.reportingCurrency : f.estimatesExchangeCurrency;
                    f.interestExpense = f.interestExpense == null ? "0" : f.interestExpense;
                    f.ebitActual = f.ebitActual == null ? "NA" : f.ebitActual;
                    //f.sharePrice=f.exchange.equals("LSE")?String.valueOf(Double.valueOf(f.sharePrice)/100):f.sharePrice;
                    fundamentalsAnnual.put(id, f);
                }
                //insert results here
                if (quarterlyDataReceived || annualDataReceived) {
                    if (!f.estimatesExchangeCurrency.equals(f.reportingCurrency) || f.ebitEstimate.equals("NA") && f.ebitActual.equals("NA")) {
                        f.result = "NA";
                        f.exchangeRate = "1";
                    } else {
                        double sharePrice = Utilities.isDouble(f.sharePrice) ? Double.parseDouble(f.sharePrice) : Double.MAX_VALUE;
                        double totalDebt = Utilities.isDouble(f.totalDebt) ? Double.parseDouble(f.totalDebt) : Double.MAX_VALUE;
                        double accountsPayable = Utilities.isDouble(f.accountsPayable) ? Double.parseDouble(f.accountsPayable) : Double.MAX_VALUE;
                        double accountsReceivable = Utilities.isDouble(f.accountsReceivable) ? Double.parseDouble(f.accountsReceivable) : Double.MAX_VALUE;
                        double cashandstinvestments = Utilities.isDouble(f.cashandstinvestments) ? Double.parseDouble(f.cashandstinvestments) : Double.MAX_VALUE;
                        double commonShares = Utilities.isDouble(f.commonShares) ? Double.parseDouble(f.commonShares) : Double.MAX_VALUE;
                        double inventories = Utilities.isDouble(f.inventories) ? Double.parseDouble(f.inventories) : Double.MAX_VALUE;
                        double minorityInterest = Utilities.isDouble(f.minorityInterest) ? Double.parseDouble(f.minorityInterest) : Double.MAX_VALUE;
                        double sharesOutstanding = Utilities.isDouble(f.commonShares) ? Double.parseDouble(f.commonShares) : Double.MAX_VALUE;
                        double ebit = Utilities.isDouble(f.ebitEstimate) ? Double.parseDouble(f.ebitEstimate) : Utilities.isDouble(f.ebitActual) ? Double.parseDouble(f.ebitActual) : 0D;
                        double interestexpense = Utilities.isDouble(f.interestExpense) ? -Double.parseDouble(f.interestExpense) : 0D;
                        f.costOfDebt = String.valueOf(f.balanceSheetDuration.equals("3") ? interestexpense * 4 / totalDebt : interestexpense / totalDebt);
                        //f.beta = "1";
                        double beta = Utilities.isDouble(f.beta) ? Double.parseDouble(f.beta) : 1D;
                        double costOfDebt = Utilities.isDouble(f.costOfDebt) ? Double.parseDouble(f.costOfDebt) : 0D;
                        double riskfreeRate = 0.08;
                        double marketReturn = 0.15;
                        f.costOfEquity = String.valueOf(riskfreeRate + beta * (marketReturn - riskfreeRate));
                        double costOfEquity = Utilities.isDouble(f.costOfEquity) ? Double.parseDouble(f.costOfEquity) : 0D;
                        f.exchange = Parameters.symbol.get(id).getExchange();

                        if (!f.exchange.equals(f.currency)) {//&& !f.exchange.equals(f.currency)) {
                            //make currency conversion. Change market price to currency of financials
                            String baseUrl = "http://query.yahooapis.com/v1/public/yql?q=";
                            String query = " select rate,name from csv where url='http://download.finance.yahoo.com/d/quotes?s=" + f.currency + f.reportingCurrency + "%3DX&f=l1n' and columns='rate,name'";
                            String fullUrlStr = baseUrl + URLEncoder.encode(query, "UTF-8") + "&format=xml";
                            org.jsoup.nodes.Document rate = Jsoup.connect(fullUrlStr).timeout(0).get();
                            f.exchangeRate = rate.getElementsByTag("rate").text();
                            double exchangeRate = Utilities.isDouble(f.exchangeRate) ? Double.parseDouble(f.exchangeRate) : 1;
                            sharePrice = sharePrice * exchangeRate;

                        } else {
                            f.exchangeRate = "1";
                        }
                        double result = 0;
                        double cellB29 = 1; //beta
                        double cellB30 = marketReturn - riskfreeRate;//CAPM excess market return
                        double cellB31 = riskfreeRate;
                        double cellB34 = costOfDebt;//cost of debt
                        double cellB35 = 0.3;//tax rate
                        double cellB38 = 0.05;//earnings growth rate
                        double cellB32 = cellB31 + cellB29 * cellB30;
                        double cellB6 = -ebit * cellB35;
                        double cellB7 = ebit + cellB6;
                        double cellB25 = accountsReceivable + inventories - accountsPayable;
                        double cellB8 = -cellB25 * cellB38;
                        double cellB9 = cellB8 + cellB7;
                        double cellB13 = totalDebt - cashandstinvestments;
                        double cellB14 = minorityInterest;
                        double cellB10 = cellB9;
                        double cellB15 = cellB9 - cellB13 - cellB14;

                        double seedValue = cellB9; //seed value for cell B9, equity value

                        for (int i = 0; i < 1000; i++) {
                            cellB15 = seedValue - cellB13 - cellB14;
                            double cellB36 = cellB15 / (cellB13 + cellB14 + cellB15) < 0.5 ? 0.5 : cellB15 / (cellB13 + cellB14 + cellB15) > 1 ? 1 : cellB15 / (cellB13 + cellB14 + cellB15);
                            double cellB37 = cellB32 * cellB36 + cellB34 * (1 - cellB36) * (1 - cellB35);
                            cellB10 = cellB9 / (cellB37 - cellB38);
                            if (Math.abs(cellB10 - seedValue) > 0.1) {
                                seedValue = cellB10;
                            } else {
                                break;
                            }

                        }

                        if (Math.abs(seedValue - cellB10) < 0.1) {
                            double cellB17 = cellB15 / sharesOutstanding;
                            f.costOfCapital = String.valueOf(cellB9 / seedValue) + cellB38;
                            f.ev = String.valueOf(cellB10);
                            f.result = Double.toString(cellB17 / sharePrice - 1);
                        } else {
                            f.result = "NA";
                        }
                        f.writer(outputFile);

                    }
                }

            }
            //Write to csv
//        FundamentalExtract f=new FundamentalExtract();

        } catch (ParserConfigurationException ex) {
            logger.log(Level.SEVERE, null, ex);
        } catch (SAXException ex) {
            logger.log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    private void loadParameters(Properties p) {
        path = p.getProperty("path", "logs");
        estimateYear = p.getProperty("estimateyear", "2016");
        outputFile = p.getProperty("outputfile", "logs\\output.csv");
    }

    @Override
    public void tradeReceived(TradeEvent event) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
}
