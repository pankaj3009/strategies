/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.algorithms.csv;

import com.incurrency.framework.EnumNotification;
import com.incurrency.framework.EnumOrderSide;
import com.incurrency.framework.EnumOrderStage;
import com.incurrency.framework.EnumOrderType;
import com.incurrency.framework.ReaderWriterInterface;
import com.incurrency.framework.TradingUtil;
import java.io.File;
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
public class CSVOrder implements ReaderWriterInterface {
    private String symbol;
    private String happyName;
    private String type;
    private String exchange;
    private String optionStrike;
    private String right;
    private EnumOrderSide side;
    private EnumOrderType orderType;
    private double limitPrice;
    private double triggerPrice;
    private String effectiveFrom;
    private int effectiveDuration;
    private int dynamicDuration;
    private EnumOrderStage stage;
    private boolean scaleIn;
    private int size;
    private String tif;
    private EnumNotification reason;
    private double slippage;
    private int id;
    private static final Logger logger = Logger.getLogger(CSVOrder.class.getName());
    public int test;
            
    public CSVOrder(){
        
    }

    public CSVOrder(String[] input) {
        this.symbol = input[0]==null?"":input[0];
        this.happyName=input[1]==null?input[0]:input[1];
        this.type = input[2]==null?"":input[2];
        this.exchange = input[3]==null?"":input[3];
        this.optionStrike = input[4]==null?"":input[4];
        this.right = input[5]==null?"":input[5];
        this.side = input[6]==null?EnumOrderSide.UNDEFINED: EnumOrderSide.valueOf(input[6].toUpperCase());
        this.orderType = input[7]==null?EnumOrderType.UNDEFINED: EnumOrderType.valueOf(input[7].toUpperCase());
        this.limitPrice = input[8]==null?0:Double.parseDouble(input[8]);
        this.triggerPrice = input[9]==null?0:Double.parseDouble(input[9]);
        this.effectiveFrom = input[10]==null?"":input[10];
        this.effectiveDuration = input[11]==null?0:Integer.parseInt(input[11]);
        this.dynamicDuration = input[12]==null?0:Integer.parseInt(input[12]);
        this.stage = input[13]==null?EnumOrderStage.UNDEFINED: EnumOrderStage.valueOf(input[13].toUpperCase());
        this.scaleIn = input[14]==null?Boolean.FALSE:Boolean.parseBoolean(input[14]);
        this.size=input[15]==null?0:Integer.parseInt(input[15]);
        this.tif=input[16]==null?"DAY":input[16];
        this.slippage=input[17]==null?0D:Double.parseDouble(input[17]);
        this.reason=input[18]==null?EnumNotification.UNDEFINED:EnumNotification.valueOf(input[18]);
        id=TradingUtil.getIDFromSymbol(symbol, type, type, right, optionStrike);
        
    }

    
    
    /**
     * @return the symbol
     */
    public String getSymbol() {
        return symbol;
    }

    /**
     * @param symbol the symbol to set
     */
    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    /**
     * @return the side
     */
    public EnumOrderSide getSide() {
        return side;
    }

    /**
     * @param side the side to set
     */
    public void setSide(EnumOrderSide side) {
        this.side = side;
    }

    /**
     * @return the orderType
     */
    public EnumOrderType getOrderType() {
        return orderType;
    }

    /**
     * @param orderType the orderType to set
     */
    public void setOrderType(EnumOrderType orderType) {
        this.orderType = orderType;
    }

    /**
     * @return the limitPrice
     */
    public double getLimitPrice() {
        return limitPrice;
    }

    /**
     * @param limitPrice the limitPrice to set
     */
    public void setLimitPrice(double limitPrice) {
        this.limitPrice = limitPrice;
    }

    /**
     * @return the triggerPrice
     */
    public double getTriggerPrice() {
        return triggerPrice;
    }

    /**
     * @param triggerPrice the triggerPrice to set
     */
    public void setTriggerPrice(double triggerPrice) {
        this.triggerPrice = triggerPrice;
    }

    /**
     * @return the effectiveFrom
     */
    public String getEffectiveFrom() {
        return effectiveFrom;
    }

    /**
     * @param effectiveFrom the effectiveFrom to set
     */
    public void setEffectiveFrom(String effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    /**
     * @return the effectiveDuration
     */
    public int getEffectiveDuration() {
        return effectiveDuration;
    }

    /**
     * @param effectiveDuration the effectiveDuration to set
     */
    public void setEffectiveDuration(int effectiveDuration) {
        this.effectiveDuration = effectiveDuration;
    }

    /**
     * @return the dynamicDuration
     */
    public int getDynamicDuration() {
        return dynamicDuration;
    }

    /**
     * @param dynamicDuration the dynamicDuration to set
     */
    public void setDynamicDuration(int dynamicDuration) {
        this.dynamicDuration = dynamicDuration;
    }

    /**
     * @return the stage
     */
    public EnumOrderStage getStage() {
        return stage;
    }

    /**
     * @param stage the stage to set
     */
    public void setStage(EnumOrderStage stage) {
        this.stage = stage;
    }

    /**
     * @return the scaleIn
     */
    public boolean isScaleIn() {
        return scaleIn;
    }

    /**
     * @param scaleIn the scaleIn to set
     */
    public void setScaleIn(boolean scaleIn) {
        this.scaleIn = scaleIn;
    }

    @Override
    public void reader(String inputfile, ArrayList target) {
        File inputFile = new File(inputfile);
        if (inputFile.exists() && !inputFile.isDirectory()) {
            try {
                List<String> ordersLoad = Files.readAllLines(Paths.get(inputfile), StandardCharsets.UTF_8);
                ordersLoad.remove(0);
                for (String order : ordersLoad) {
                    if(!order.equals("")){
                    String[] input = order.split(",");
                    target.add(new CSVOrder(input));
                    }
                    }                    
            } catch (Exception ex) {
                logger.log(Level.SEVERE, null, ex);
            }
        }    }

    @Override
    public void writer(String fileName) {
        
    }

    /**
     * @return the exchange
     */
    public String getExchange() {
        return exchange;
    }

    /**
     * @param exchange the exchange to set
     */
    public void setExchange(String exchange) {
        this.exchange = exchange;
    }

    /**
     * @return the optionStrike
     */
    public String getOptionStrike() {
        return optionStrike;
    }

    /**
     * @param optionStrike the optionStrike to set
     */
    public void setOptionStrike(String optionStrike) {
        this.optionStrike = optionStrike;
    }

    /**
     * @return the right
     */
    public String getRight() {
        return right;
    }

    /**
     * @param right the right to set
     */
    public void setRight(String right) {
        this.right = right;
    }

    /**
     * @return the type
     */
    public String getType() {
        return type;
    }

    /**
     * @param type the type to set
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * @return the size
     */
    public int getSize() {
        return size;
    }

    /**
     * @param size the size to set
     */
    public void setSize(int size) {
        this.size = size;
    }

    /**
     * @return the reason
     */
    public EnumNotification getReason() {
        return reason;
    }

    /**
     * @param reason the reason to set
     */
    public void setReason(EnumNotification reason) {
        this.reason = reason;
    }

    /**
     * @return the slippage
     */
    public double getSlippage() {
        return slippage;
    }

    /**
     * @param slippage the slippage to set
     */
    public void setSlippage(double slippage) {
        this.slippage = slippage;
    }

    /**
     * @return the tif
     */
    public String getTif() {
        return tif;
    }

    /**
     * @param tif the tif to set
     */
    public void setTif(String tif) {
        this.tif = tif;
    }

    /**
     * @return the id
     */
    public int getId() {
        return id;
    }

    /**
     * @param id the id to set
     */
    public void setId(int id) {
        this.id = id;
    }

    /**
     * @return the happyName
     */
    public String getHappyName() {
        return happyName;
    }

    /**
     * @param happyName the happyName to set
     */
    public void setHappyName(String happyName) {
        this.happyName = happyName;
    }
    
    
}
