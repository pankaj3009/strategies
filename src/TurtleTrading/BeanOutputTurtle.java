/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package TurtleTrading;

import java.beans.*;
import java.io.Serializable;

/**
 *
 * @author pasharma
 */
public class BeanOutputTurtle implements Serializable {

    private String symbolName;
    private int quantity;
    private String sideEntry;
    private double priceEntry;
    private String sideExit;
    private double priceExit;
    private double profit;
    private int upbar;
    private int downbar;
    private int strength;
    
    public BeanOutputTurtle() {
    }

    /**
     * @return the symbolName
     */
    public String getSymbolName() {
        return symbolName;
    }

    /**
     * @param symbolName the symbolName to set
     */
    public void setSymbolName(String symbolName) {
        this.symbolName = symbolName;
    }

    /**
     * @return the quantity
     */
    public int getQuantity() {
        return quantity;
    }

    /**
     * @param quantity the quantity to set
     */
    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    /**
     * @return the sideEntry
     */
    public String getSideEntry() {
        return sideEntry;
    }

    /**
     * @param sideEntry the sideEntry to set
     */
    public void setSideEntry(String sideEntry) {
        this.sideEntry = sideEntry;
    }

    /**
     * @return the priceEntry
     */
    public double getPriceEntry() {
        return priceEntry;
    }

    /**
     * @param priceEntry the priceEntry to set
     */
    public void setPriceEntry(double priceEntry) {
        this.priceEntry = priceEntry;
    }

    /**
     * @return the sideExit
     */
    public String getSideExit() {
        return sideExit;
    }

    /**
     * @param sideExit the sideExit to set
     */
    public void setSideExit(String sideExit) {
        this.sideExit = sideExit;
    }

    /**
     * @return the priceExit
     */
    public double getPriceExit() {
        return priceExit;
    }

    /**
     * @param priceExit the priceExit to set
     */
    public void setPriceExit(double priceExit) {
        this.priceExit = priceExit;
    }

    /**
     * @return the profit
     */
    public double getProfit() {
        return profit;
    }

    /**
     * @param profit the profit to set
     */
    public void setProfit(double profit) {
        this.profit = profit;
    }

    /**
     * @return the upbar
     */
    public int getUpbar() {
        return upbar;
    }

    /**
     * @param upbar the upbar to set
     */
    public void setUpbar(int upbar) {
        this.upbar = upbar;
    }

    /**
     * @return the downbar
     */
    public int getDownbar() {
        return downbar;
    }

    /**
     * @param downbar the downbar to set
     */
    public void setDownbar(int downbar) {
        this.downbar = downbar;
    }

    /**
     * @return the strength
     */
    public int getStrength() {
        return strength;
    }

    /**
     * @param strength the strength to set
     */
    public void setStrength(int strength) {
        this.strength = strength;
    }
    

}
