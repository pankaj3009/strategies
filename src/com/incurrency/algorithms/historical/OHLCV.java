/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.algorithms.historical;

/**
 *
 * @author pankaj
 */
public class OHLCV {

    private Long time;
    private String open;
    private String high;
    private String low;
    private String close;
    private String volume;

    /**
     * @return the time
     */
    public Long getTime() {
        return time;
    }

    /**
     * @param time the time to set
     */
    public void setTime(Long time) {
        this.time = time;
    }

    /**
     * @return the open
     */
    public String getOpen() {
        return open;
    }

    /**
     * @param open the open to set
     */
    public void setOpen(String open) {
        this.open = open;
    }

    /**
     * @return the high
     */
    public String getHigh() {
        return high;
    }

    /**
     * @param high the high to set
     */
    public void setHigh(String high) {
        this.high = high;
    }

    /**
     * @return the low
     */
    public String getLow() {
        return low;
    }

    /**
     * @param low the low to set
     */
    public void setLow(String low) {
        this.low = low;
    }

    /**
     * @return the close
     */
    public String getClose() {
        return close;
    }

    /**
     * @param close the close to set
     */
    public void setClose(String close) {
        this.close = close;
    }

    /**
     * @return the volume
     */
    public String getVolume() {
        return volume;
    }

    /**
     * @param volume the volume to set
     */
    public void setVolume(String volume) {
        this.volume = volume;
    }

   
}
