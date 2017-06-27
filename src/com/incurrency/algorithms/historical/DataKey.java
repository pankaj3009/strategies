/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.algorithms.historical;

/**
 *
 * @author pankaj
 */
public class DataKey implements Comparable {

    private String symbol;
    private long timeStamp;
    private String type;

    public DataKey(String symbol, long timeStamp, String type) {
        this.symbol = symbol;
        this.timeStamp = timeStamp;
        this.type = type;
    }

    @Override
    public int compareTo(Object obj) {
        DataKey o = (DataKey) obj;
        if (this.timeStamp > o.getTimeStamp()) {
            return 1;
        } else if (this.getTimeStamp() < o.getTimeStamp()) {
            return -1;
        } else if (this.type.equals("open")) {
            return 1;
        } else if (this.type.equals("close")) {
            return -1;
        } else {
            return 0;
        }

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
     * @return the timeStamp
     */
    public long getTimeStamp() {
        return timeStamp;
    }

    /**
     * @param timeStamp the timeStamp to set
     */
    public void setTimeStamp(long timeStamp) {
        this.timeStamp = timeStamp;
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

}
