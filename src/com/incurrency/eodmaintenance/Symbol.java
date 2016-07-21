/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.eodmaintenance;


import com.incurrency.framework.BeanSymbol;
import com.incurrency.framework.ReaderWriterInterface;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author pankaj
 */
public class Symbol implements ReaderWriterInterface {

    public String symbolLongName;
    public String ibSymbol;
    public String exchangeSymbol;
    public String currency;
    public String contractId;
    public String exchange;
    public String type;
    
    private static final Logger logger = Logger.getLogger(Symbol.class.getName());

    public Symbol() {
    }

    public Symbol(String symbolLongName, String ibSymbol, String exchangeSymbol, String currency, String contractId, String exchange, String type) {
        this.symbolLongName = symbolLongName;
        this.ibSymbol = ibSymbol;
        this.exchangeSymbol = exchangeSymbol;
        this.currency = currency;
        this.contractId = contractId;
        this.exchange = exchange;
        this.type = type;
    }

    public Symbol(String input[]) {
        this.symbolLongName = input[0];
        this.ibSymbol = input[1];
        this.exchangeSymbol = input[2];
        this.currency = input[3];
        this.contractId = input[4];
        this.exchange = input[5];
        this.type = input[6];
    }

    
    @Override
    public void reader(String inputfile, ArrayList target) {
        
    File inputFile=new File(inputfile);
         if(inputFile.exists() && !inputFile.isDirectory()){
        try {
            List<String> existingContractLoad=Files.readAllLines(Paths.get(inputfile), StandardCharsets.UTF_8);
            for(String Contractline: existingContractLoad){
                String[] input=Contractline.split(",");
                target.add(new BeanSymbol(input,7));
            }
            int i=0;
            for(Object s:target){
                BeanSymbol bs=(BeanSymbol)s;
                bs.setSerialno(i+1);
                i++;
            }
        } catch (IOException ex) {
           logger.log(Level.SEVERE, null, ex);
        }
    }
    }

    @Override
    public void writer(String fileName) {
        File f = new File(fileName);
        try {
            if (!f.exists() || f.isDirectory()) {
                String header = "Long Name" + ",IB Symbol" + ",Exchange Symbol" + ",Currency" + ",Contract ID" + ",Exchange" + ",Type";
                PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(fileName, true)));
                out.println(header);
                out.close();
            } 
                String data = symbolLongName + "," + ibSymbol + "," + exchangeSymbol + "," + currency + "," + contractId + "," + exchange + "," + type;
                PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(fileName, true)));
                out.println(data);
                out.close();
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, null, e);
        }
    }
    
    @Override
    public boolean equals(Object object) {
    boolean result = false;
    if (object == null || object.getClass() != getClass()) {
      result = false;
    } else {
      Symbol symbol = (Symbol) object;
      if ( this.ibSymbol.equals(symbol.ibSymbol)
          && this.exchange.equals(symbol.exchange)
              && this.currency.equals(symbol.currency)) {
        result = true;
      }
    }
    return result;
  }
    
    @Override
    public int hashCode() {
    int hash = 5;
    hash = 7 * hash + ibSymbol.hashCode()+exchange.hashCode()+currency.hashCode();
    return hash;
  }    
    
}