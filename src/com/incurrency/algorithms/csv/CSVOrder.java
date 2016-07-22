/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.algorithms.csv;

import com.incurrency.framework.EnumOrderReason;
import com.incurrency.framework.EnumOrderSide;
import com.incurrency.framework.EnumOrderStage;
import com.incurrency.framework.EnumOrderType;
import com.incurrency.framework.Parameters;
import com.incurrency.framework.ReaderWriterInterface;
import com.incurrency.framework.TradingUtil;
import com.incurrency.framework.Utilities;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author pankaj
 */
public class CSVOrder implements ReaderWriterInterface {
    private HashMap<String,Object> order=new HashMap<>();;
    private ArrayList<String> headers=new ArrayList<>();
    private CSV csv;
    private static final Logger logger = Logger.getLogger(CSVOrder.class.getName());
            
    public CSVOrder(CSV csv){
        this.csv=csv;
    }
    
    public CSVOrder(){
        
    }


    public CSVOrder(ArrayList<String> header,String[] input) {
        this.headers=header;
        for(int i=0;i<input.length;i++){
            order.put(headers.get(i), input[i]);
        }
        if(headers.indexOf("specifiedclass")>0){
        //if value is a class, handle that seperately.
        }
        order.put("id", Utilities.getIDFromDisplayName(Parameters.symbol, order.get("symbol").toString()));
/*
        this.symbol = input[0]==null?"":input[0].trim();
        this.happyName=input[1]==null?input[0]:input[1].trim();
        this.type = input[2]==null?"":input[2].trim();
        this.expiry = input[3]==null?"":input[3].trim();
        this.exchange = input[4]==null?"":input[4].trim();
        this.optionStrike = input[5]==null?"":input[5].trim();
        this.right = input[6]==null?"":input[6].trim();
        this.side = input[7]==null?EnumOrderSide.UNDEFINED: EnumOrderSide.valueOf(input[7].toUpperCase().trim());
        this.orderType = input[8]==null?EnumOrderType.UNDEFINED: EnumOrderType.valueOf(input[8].toUpperCase().trim());
        this.limitPrice = input[9]==null?0:Double.parseDouble(input[9].trim());
        this.triggerPrice = input[10]==null?0:Double.parseDouble(input[10].trim());
        this.effectiveFrom = input[11].trim().matches("\\d{4}\\d{2}\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}")?input[11].trim():"";
        this.effectiveDuration = input[12]==null?0:Integer.parseInt(input[12].trim());
        this.dynamicDuration = input[13]==null?0:Integer.parseInt(input[13].trim());
        this.stage = input[14]==null?EnumOrderStage.UNDEFINED: EnumOrderStage.valueOf(input[14].toUpperCase().trim());
        this.scaleIn = input[15]==null?Boolean.FALSE:Boolean.parseBoolean(input[15].trim());
        this.size=input[16]==null?0:Integer.parseInt(input[16].trim());
        this.tif=input[17]==null?"DAY":input[17].trim();
        this.slippage=input[18]==null?0D:Double.parseDouble(input[18].trim());
        this.rowreference=input[19]==null?"":input[19].trim();
        this.reason=input[20]==null?EnumOrderReason.UNDEFINED:EnumOrderReason.valueOf(input[20].toUpperCase().trim());
        if(this.type.equals("COMBO")){//update Parameters.Symbols if first time

        }else{
        id=Utilities.getIDFromBrokerSymbol(Parameters.symbol,symbol, type, expiry, right, optionStrike);
        }
    */
    }
  
    
    
    @Override
    public void reader(String inputfile, List target) {
        File inputFile = new File(inputfile);
        if (inputFile.exists() && !inputFile.isDirectory()) {
            try {
                List<String> ordersLoad = Files.readAllLines(Paths.get(inputfile), StandardCharsets.UTF_8);
                if(ordersLoad.size()>0){
                //logger.log(Level.INFO,"{0},{1},CSV Order Reader,Finished Reading CSV,inputFile:{2},Lines Read:{3}",new Object[]{"All","ALL",inputfile,ordersLoad.size()});
                headers.addAll(Arrays.asList(ordersLoad.get(0).split(",")));
                ordersLoad.remove(0);
                for (String order : ordersLoad) {
                    if(!order.trim().equals("")){
                    String[] input = order.split(",");
                    target.add(new CSVOrder(headers,input));
                    }
                    }    
                }
            } catch (Exception ex) {
                logger.log(Level.INFO, "101", ex);
            }
        }    
    }

    @Override
    public void writer(String fileName) {
        
    }        

    /**
     * @return the order
     */
    public HashMap<String,Object> getOrder() {
        return order;
    }

    /**
     * @param order the order to set
     */
    public void setOrder(HashMap<String,Object> order) {
        this.order = order;
    }
    
}
