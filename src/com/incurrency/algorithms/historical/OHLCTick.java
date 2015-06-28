package com.incurrency.algorithms.historical;

import java.util.Date;

public class OHLCTick
{
    private String ticker;
    private String price;
    private long timestamp;
    private String type;

    public OHLCTick(String ticker, String price, long timestamp,String type)
    {
        this.ticker = ticker;
        this.price = price;
        this.timestamp = timestamp;
        this.type=type;
    }

    public String getTicker()
    {
        return ticker;
    }

    public String getPrice()
    {
        return price;
    }

    public long getTimestamp()
    {
        return timestamp;
    }

    public String toString()
    {
        return "ticker " + ticker +
               " price " + price +
               " timestamp " + printTime(timestamp);
    }

    private String printTime(long timestamp)
    {
        return new Date(timestamp).toString();
    }

    /**
     * @return the type
     */
    public String getType() {
        return type;
    }
}
