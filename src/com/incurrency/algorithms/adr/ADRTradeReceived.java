/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.algorithms.adr;

import com.incurrency.framework.EnumOrderReason;
import com.incurrency.framework.EnumOrderSide;
import com.incurrency.framework.EnumOrderType;
import com.incurrency.framework.Parameters;
import com.incurrency.framework.TradeEvent;
import com.incurrency.framework.TradingUtil;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author pankaj
 */
public class ADRTradeReceived implements Runnable {

    ADR s;
    TradeEvent event;
    private static final Logger logger = Logger.getLogger(ADRTradeReceived.class.getName());
    
    public ADRTradeReceived(ADR s,TradeEvent event){
        this.s=s;
        this.event=event;
    }
    
    @Override
    public void run() {
        s.processTradeReceived(event);

/*        
                int id = event.getSymbolID(); //zero based id
        if (s.getStrategySymbols().contains(id) && Parameters.symbol.get(id).getType().compareTo("STK") == 0) {
            switch (event.getTickType()) {
                case com.ib.client.TickType.LAST_SIZE:
                    //System.out.println("LASTSIZE, Symbol:"+Parameters.symbol.get(id).getSymbol()+" Value: "+Parameters.symbol.get(id).getLastSize()+" tickerID: "+id);
                    s.mEsperEvtProcessor.sendEvent(new TickPriceEvent(id, com.ib.client.TickType.LAST_SIZE, Parameters.symbol.get(id).getLastSize()));
                    //mEsperEvtProcessor.debugFireTickQuery();
                    break;
                case com.ib.client.TickType.VOLUME:
                    //System.out.println("VOLUME, Symbol:"+Parameters.symbol.get(id).getSymbol()+" Value: "+Parameters.symbol.get(id).getVolume()+" tickerID: "+id);
                    s.mEsperEvtProcessor.sendEvent(new TickPriceEvent(id, com.ib.client.TickType.VOLUME, Parameters.symbol.get(id).getVolume()));
                    //mEsperEvtProcessor.debugFireADRQuery();
                    break;
                case com.ib.client.TickType.LAST:
                    s.mEsperEvtProcessor.sendEvent(new TickPriceEvent(id, com.ib.client.TickType.LAST, Parameters.symbol.get(id).getLastPrice()));
                    if (Parameters.symbol.get(id).getClosePrice() > 0 && !s.closePriceReceived.get(id)) {
                        s.mEsperEvtProcessor.sendEvent(new TickPriceEvent(id, com.ib.client.TickType.CLOSE, Parameters.symbol.get(id).getClosePrice()));
                        s.closePriceReceived.put(id, Boolean.TRUE);
                    }
                    //mEsperEvtProcessor.debugFireTickQuery();
                    //mEsperEvtProcessor.debugFireADRQuery();
                    break;
                case com.ib.client.TickType.CLOSE:
                    s.mEsperEvtProcessor.sendEvent(new TickPriceEvent(id, com.ib.client.TickType.CLOSE, Parameters.symbol.get(id).getClosePrice()));
                    //mEsperEvtProcessor.debugFireADRQuery();
                    break;
                default:
                    break;
            }
        }
        String symbolexpiry = Parameters.symbol.get(id).getExpiry() == null ? "" : Parameters.symbol.get(id).getExpiry();
        if (s.getTrading() && Parameters.symbol.get(id).getSymbol().equals(s.getIndex()) && Parameters.symbol.get(id).getType().equals(s.getType()) && symbolexpiry.equals(s.getExpiry()) && event.getTickType() == com.ib.client.TickType.LAST) {
            double price = Parameters.symbol.get(id).getLastPrice();
            if (s.adr > 0) { //calculate high low only after minimum ticks have been received.
                s.mEsperEvtProcessor.sendEvent(new ADREvent(ADRTickType.INDEX, price));
                if (price > s.indexDayHigh) {
                    s.indexDayHigh = price;
                } else if (price < s.indexDayLow) {
                    s.indexDayLow = price;
                }
            }
//            boolean buyZone1 = ((adrHigh - adrLow > 5 && adr > adrLow + 0.75 * (adrHigh - adrLow) && adr > adrAvg)
//                    || (adrDayHigh - adrDayLow > 10 && adr > adrDayLow + 0.75 * (adrDayHigh - adrDayLow) && adr > adrAvg));// && adrTRIN < 90;
            boolean buyZone1 = ((s.adrHigh - s.adrLow > 5 && s.adr > s.adrLow + 0.75 * (s.adrHigh - s.adrLow) && s.adr > s.adrAvg)
                    || (s.adrDayHigh - s.adrDayLow > 10 && s.adr > s.adrDayLow + 0.75 * (s.adrDayHigh - s.adrDayLow) && s.adr > s.adrAvg));// && adrTRIN < 90;
            boolean buyZone2 = ((s.indexHigh - s.indexLow > s.getWindowHurdle() && price > s.indexLow + 0.75 * (s.indexHigh - s.indexLow) && price > s.indexAvg)
                    || (s.indexDayHigh - s.indexDayLow > s.getDayHurdle() && price > s.indexDayLow + 0.75 * (s.indexDayHigh - s.indexDayLow) && price > s.indexAvg));// && adrTRIN < 90;
            //boolean buyZone3 = this.adrTRINAvg < 90 && this.adrTRINAvg > 0;
            boolean buyZone3 = (s.adrTRIN < s.adrTRINAvg - 5 && s.adrTRIN > 90 && s.adrTRIN < 110) || (s.adrTRIN < 90 && s.adrTRINAvg < 90);

//            boolean shortZone1 = ((adrHigh - adrLow > 5 && adr < adrHigh - 0.75 * (adrHigh - adrLow) && adr < adrAvg)
//                    || (adrDayHigh - adrDayLow > 10 && adr < adrDayHigh - 0.75 * (adrDayHigh - adrDayLow) && adr < adrAvg));// && adrTRIN > 95;
            boolean shortZone1 = ((s.adrHigh - s.adrLow > 5 && s.adr < s.adrHigh - 0.75 * (s.adrHigh - s.adrLow) && s.adr < s.adrAvg)
                    || (s.adrDayHigh - s.adrDayLow > 10 && s.adr < s.adrDayHigh - 0.75 * (s.adrDayHigh - s.adrDayLow) && s.adr < s.adrAvg));// && adrTRIN > 95;
            boolean shortZone2 = ((s.indexHigh - s.indexLow > s.getWindowHurdle() && price < s.indexHigh - 0.75 * (s.indexHigh - s.indexLow) && price < s.indexAvg)
                    || (s.indexDayHigh - s.indexDayLow > s.getDayHurdle() && price < s.indexDayHigh - 0.75 * (s.indexDayHigh - s.indexDayLow) && price < s.indexAvg));// && adrTRIN > 95;
            //boolean shortZone3 = this.adrTRINAvg > 95;
            boolean shortZone3 = (s.adrTRIN > s.adrTRINAvg + 5 && s.adrTRIN > 90 && s.adrTRIN < 110) || (s.adrTRIN > 110 && s.adrTRINAvg > 110);

            Boolean buyZone = false;
            Boolean shortZone = false;

            buyZone = atLeastTwo(buyZone1, buyZone2, buyZone3);
            shortZone = atLeastTwo(shortZone1, shortZone2, shortZone3);
            
            TradingUtil.writeToFile(s.getStrategy() + ".csv", buyZone + "," + shortZone + ","+s.tradingSide+"," + s.adr + "," + s.adrHigh + "," + s.adrLow + "," + s.adrDayHigh + "," + s.adrDayLow + "," + s.adrAvg + "," + buyZone1 + "," + shortZone1 + "," + price + "," + s.indexHigh + "," + s.indexLow + "," + s.indexDayHigh + "," + s.indexDayLow + "," + s.indexAvg + "," + buyZone2 + "," + shortZone2 + "," + s.adrTRIN + "," + s.adrTRINAvg + "," + buyZone3 + "," + shortZone3 + "," + s.tick + "," + s.tickTRIN + "," + s.adrTRINHigh + "," + s.adrTRINLow + "," + "SCAN");
            if ((!buyZone && s.tradingSide == 1 && s.getPosition().get(id).getPosition() == 0) || (!shortZone && s.tradingSide == -1 && s.getPosition().get(id).getPosition() == 0)) {
                logger.log(Level.INFO, "Trading Side Reset. New Trading Side: {0}, Earlier trading Side: {1}", new Object[]{0, s.tradingSide});
                s.tradingSide = 0;
            TradingUtil.writeToFile(s.getStrategy() + ".csv", buyZone + "," + shortZone + ","+s.tradingSide+"," + s.adr + "," + s.adrHigh + "," + s.adrLow + "," + s.adrDayHigh + "," + s.adrDayLow + "," + s.adrAvg + "," + buyZone1 + "," + shortZone1 + "," + price + "," + s.indexHigh + "," + s.indexLow + "," + s.indexDayHigh + "," + s.indexDayLow + "," + s.indexAvg + "," + buyZone2 + "," + shortZone2 + "," + s.adrTRIN + "," + s.adrTRINAvg + "," + buyZone3 + "," + shortZone3 + "," + s.tick + "," + s.tickTRIN + "," + s.adrTRINHigh + "," + s.adrTRINLow + "," + "TRADING SIDE RESET");
            }

            if (s.getPosition().get(id).getPosition()  == 0 && new Date().compareTo(s.getEndDate()) < 0) {
                if (s.tradingSide == 0 && buyZone && (s.tick < 45 || s.tickTRIN > 120) && s.getLongOnly() && price > s.indexHigh - 0.75 * s.getStopLoss()) {
                    s.setEntryPrice(price);
                    logger.log(Level.INFO, " ALL,{0}, Buy, adrHigh: {1},adrLow: {2},adrAvg: {3},adrTRINHigh: {4},adrTRINLow: {5},adrTRINAvg: {6},indexHigh :{7},indexLow :{8},indexAvg: {9}, buyZone1: {10}, buyZone2: {11}, buyZone 3: {12}, shortZone1: {13}, shortZone2: {14}, ShortZone3:{15}, ADR: {16}, ADRTrin: {17}, Tick: {18}, TickTrin: {19}, adrDayHigh: {20}, adrDayLow: {21}, IndexDayHigh: {22}, IndexDayLow: {23}, Price: {24}", new Object[]{s.getStrategy(), s.adrHigh, s.adrLow, s.adrAvg, s.adrTRINHigh, s.adrTRINLow, s.adrTRINAvg, s.indexHigh, s.indexLow, s.indexAvg, buyZone1, buyZone2, buyZone3, shortZone1, shortZone2, shortZone3, s.adr, s.adrTRIN, s.tick, s.tickTRIN, s.adrDayHigh, s.adrDayLow, s.indexDayHigh, s.indexDayLow, price});
                    s.entry(id, EnumOrderSide.BUY,EnumOrderType.LMT, s.getEntryPrice(), 0, false,EnumOrderReason.REGULARENTRY,"");
                    s.tradingSide = 1;
            TradingUtil.writeToFile(s.getStrategy() + ".csv", buyZone + "," + shortZone + ","+s.tradingSide+"," + s.adr + "," + s.adrHigh + "," + s.adrLow + "," + s.adrDayHigh + "," + s.adrDayLow + "," + s.adrAvg + "," + buyZone1 + "," + shortZone1 + "," + price + "," + s.indexHigh + "," + s.indexLow + "," + s.indexDayHigh + "," + s.indexDayLow + "," + s.indexAvg + "," + buyZone2 + "," + shortZone2 + "," + s.adrTRIN + "," + s.adrTRINAvg + "," + buyZone3 + "," + shortZone3 + "," + s.tick + "," + s.tickTRIN + "," + s.adrTRINHigh + "," + s.adrTRINLow + "," + "BUY");
                } else if (s.tradingSide == 0 && shortZone && (s.tick > 55 || s.tickTRIN < 80) && s.getShortOnly() && price < s.indexLow + 0.75 * s.getStopLoss()) {
                    s.setEntryPrice(price);
                    logger.log(Level.INFO, " ALL,{0}, Short, adrHigh: {1},adrLow: {2},adrAvg: {3},adrTRINHigh: {4},adrTRINLow: {5},adrTRINAvg: {6},indexHigh :{7},indexLow :{8},indexAvg: {9}, buyZone1: {10}, buyZone2: {11}, buyZone 3: {12}, shortZone1: {13}, shortZone2: {14}, ShortZone3:{15}, ADR: {16}, ADRTrin: {17}, Tick: {18}, TickTrin: {19}, adrDayHigh: {20}, adrDayLow: {21}, IndexDayHigh: {22}, IndexDayLow: {23}, Price: {24}", new Object[]{s.getStrategy(), s.adrHigh, s.adrLow, s.adrAvg, s.adrTRINHigh, s.adrTRINLow, s.adrTRINAvg, s.indexHigh, s.indexLow, s.indexAvg, buyZone1, buyZone2, buyZone3, shortZone1, shortZone2, shortZone3, s.adr, s.adrTRIN, s.tick, s.tickTRIN, s.adrDayHigh, s.adrDayLow, s.indexDayHigh, s.indexDayLow, price});
                    s.entry(id, EnumOrderSide.SHORT, EnumOrderType.LMT,s.getEntryPrice(), 0, false,EnumOrderReason.REGULARENTRY,"");
                    s.tradingSide = -1;
                    TradingUtil.writeToFile(s.getStrategy() + ".csv", buyZone + "," + shortZone + ","+s.tradingSide+"," + s.adr + "," + s.adrHigh + "," + s.adrLow + "," + s.adrDayHigh + "," + s.adrDayLow + "," + s.adrAvg + "," + buyZone1 + "," + shortZone1 + "," + price + "," + s.indexHigh + "," + s.indexLow + "," + s.indexDayHigh + "," + s.indexDayLow + "," + s.indexAvg + "," + buyZone2 + "," + shortZone2 + "," + s.adrTRIN + "," + s.adrTRINAvg + "," + buyZone3 + "," + shortZone3 + "," + s.tick + "," + s.tickTRIN + "," + s.adrTRINHigh + "," + s.adrTRINLow + "," + "SHORT");
                    } else if (s.tradingSide == 1 && price < s.getLastLongExit() - s.reentryMinimumMove && s.scalpingMode && s.getLastLongExit() > 0) { //used in scalping mode
                    s.setEntryPrice(price);
                    logger.log(Level.INFO, " ALL,{0}, Scalping Buy, adrHigh: {1},adrLow: {2},adrAvg: {3},adrTRINHigh: {4},adrTRINLow: {5},adrTRINAvg: {6},indexHigh :{7},indexLow :{8},indexAvg: {9}, buyZone1: {10}, buyZone2: {11}, buyZone 3: {12}, shortZone1: {13}, shortZone2: {14}, ShortZone3:{15}, ADR: {16}, ADRTrin: {17}, Tick: {18}, TickTrin: {19}, adrDayHigh: {20}, adrDayLow: {21}, IndexDayHigh: {22}, IndexDayLow: {23}, Price: {24}", new Object[]{s.getStrategy(), s.adrHigh, s.adrLow, s.adrAvg, s.adrTRINHigh, s.adrTRINLow, s.adrTRINAvg, s.indexHigh, s.indexLow, s.indexAvg, buyZone1, buyZone2, buyZone3, shortZone1, shortZone2, shortZone3, s.adr, s.adrTRIN, s.tick, s.tickTRIN, s.adrDayHigh, s.adrDayLow, s.indexDayHigh, s.indexDayLow, price});
                    s.entry(id, EnumOrderSide.BUY,EnumOrderType.LMT, s.getEntryPrice(), 0, false,EnumOrderReason.REGULARENTRY,"");;
                    TradingUtil.writeToFile(s.getStrategy() + ".csv", buyZone + "," + shortZone + ","+s.tradingSide+"," + s.adr + "," + s.adrHigh + "," + s.adrLow + "," + s.adrDayHigh + "," + s.adrDayLow + "," + s.adrAvg + "," + buyZone1 + "," + shortZone1 + "," + price + "," + s.indexHigh + "," + s.indexLow + "," + s.indexDayHigh + "," + s.indexDayLow + "," + s.indexAvg + "," + buyZone2 + "," + shortZone2 + "," + s.adrTRIN + "," + s.adrTRINAvg + "," + buyZone3 + "," + shortZone3 + "," + s.tick + "," + s.tickTRIN + "," + s.adrTRINHigh + "," + s.adrTRINLow + "," + "SCALPING BUY");
                } else if (s.tradingSide == -1 && price > s.getLastShortExit() + s.reentryMinimumMove && s.scalpingMode && s.getLastShortExit() > 0) {
                    s.setEntryPrice(price);
                    logger.log(Level.INFO, " ALL,{0}, Scalping Short, adrHigh: {1},adrLow: {2},adrAvg: {3},adrTRINHigh: {4},adrTRINLow: {5},adrTRINAvg: {6},indexHigh :{7},indexLow :{8},indexAvg: {9}, buyZone1: {10}, buyZone2: {11}, buyZone 3: {12}, shortZone1: {13}, shortZone2: {14}, ShortZone3:{15}, ADR: {16}, ADRTrin: {17}, Tick: {18}, TickTrin: {19}, adrDayHigh: {20}, adrDayLow: {21}, IndexDayHigh: {22}, IndexDayLow: {23}, Price: {24}", new Object[]{s.getStrategy(), s.adrHigh, s.adrLow, s.adrAvg, s.adrTRINHigh, s.adrTRINLow, s.adrTRINAvg, s.indexHigh, s.indexLow, s.indexAvg, buyZone1, buyZone2, buyZone3, shortZone1, shortZone2, shortZone3, s.adr, s.adrTRIN, s.tick, s.tickTRIN, s.adrDayHigh, s.adrDayLow, s.indexDayHigh, s.indexDayLow, price});
                    s.entry(id, EnumOrderSide.SHORT,EnumOrderType.LMT, s.getEntryPrice(), 0, false,EnumOrderReason.REGULARENTRY,"");
                    TradingUtil.writeToFile(s.getStrategy() + ".csv", buyZone + "," + shortZone + ","+s.tradingSide+"," + s.adr + "," + s.adrHigh + "," + s.adrLow + "," + s.adrDayHigh + "," + s.adrDayLow + "," + s.adrAvg + "," + buyZone1 + "," + shortZone1 + "," + price + "," + s.indexHigh + "," + s.indexLow + "," + s.indexDayHigh + "," + s.indexDayLow + "," + s.indexAvg + "," + buyZone2 + "," + shortZone2 + "," + s.adrTRIN + "," + s.adrTRINAvg + "," + buyZone3 + "," + shortZone3 + "," + s.tick + "," + s.tickTRIN + "," + s.adrTRINHigh + "," + s.adrTRINLow + "," + "SCALPING SHORT");
                }
            } else if (s.getPosition().get(id).getPosition()  <0) {
                if (buyZone || ((price > s.indexLow + s.stopLoss && !shortZone) || (price > s.getEntryPrice() + s.stopLoss)) || new Date().compareTo(s.getEndDate()) > 0) { //stop loss
                    logger.log(Level.INFO, " ALL,{0}, StopLoss Cover, adrHigh: {1},adrLow: {2},adrAvg: {3},adrTRINHigh: {4},adrTRINLow: {5},adrTRINAvg: {6},indexHigh :{7},indexLow :{8},indexAvg: {9}, buyZone1: {10}, buyZone2: {11}, buyZone 3: {12}, shortZone1: {13}, shortZone2: {14}, ShortZone3:{15}, ADR: {16}, ADRTrin: {17}, Tick: {18}, TickTrin: {19}, adrDayHigh: {20}, adrDayLow: {21}, IndexDayHigh: {22}, IndexDayLow: {23}, Price: {24}", new Object[]{s.getStrategy(), s.adrHigh, s.adrLow, s.adrAvg, s.adrTRINHigh, s.adrTRINLow, s.adrTRINAvg, s.indexHigh, s.indexLow, s.indexAvg, buyZone1, buyZone2, buyZone3, shortZone1, shortZone2, shortZone3, s.adr, s.adrTRIN, s.tick, s.tickTRIN, s.adrDayHigh, s.adrDayLow, s.indexDayHigh, s.indexDayLow, price});
                    s.exit(id, EnumOrderSide.COVER,EnumOrderType.LMT, price, 0, "", true, "DAY", false,EnumOrderReason.REGULAREXIT,"");
                    s.tradingSide = 0;
                    TradingUtil.writeToFile(s.getStrategy() + ".csv", buyZone + "," + shortZone + ","+s.tradingSide+"," + s.adr + "," + s.adrHigh + "," + s.adrLow + "," + s.adrDayHigh + "," + s.adrDayLow + "," + s.adrAvg + "," + buyZone1 + "," + shortZone1 + "," + price + "," + s.indexHigh + "," + s.indexLow + "," + s.indexDayHigh + "," + s.indexDayLow + "," + s.indexAvg + "," + buyZone2 + "," + shortZone2 + "," + s.adrTRIN + "," + s.adrTRINAvg + "," + buyZone3 + "," + shortZone3 + "," + s.tick + "," + s.tickTRIN + "," + s.adrTRINHigh + "," + s.adrTRINLow + "," + "STOPLOSS COVER");

                } else if ((s.scalpingMode || !shortZone) && (price < s.getEntryPrice() - s.takeProfit) && (price > s.indexLow + 0.5 * s.takeProfit)) {
                    logger.log(Level.INFO, " ALL,{0}, TakeProfit Cover, adrHigh: {1},adrLow: {2},adrAvg: {3},adrTRINHigh: {4},adrTRINLow: {5},adrTRINAvg: {6},indexHigh :{7},indexLow :{8},indexAvg: {9}, buyZone1: {10}, buyZone2: {11}, buyZone 3: {12}, shortZone1: {13}, shortZone2: {14}, ShortZone3:{15}, ADR: {16}, ADRTrin: {17}, Tick: {18}, TickTrin: {19}, adrDayHigh: {20}, adrDayLow: {21}, IndexDayHigh: {22}, IndexDayLow: {23}, Price: {24}", new Object[]{s.getStrategy(), s.adrHigh, s.adrLow, s.adrAvg, s.adrTRINHigh, s.adrTRINLow, s.adrTRINAvg, s.indexHigh, s.indexLow, s.indexAvg, buyZone1, buyZone2, buyZone3, shortZone1, shortZone2, shortZone3, s.adr, s.adrTRIN, s.tick, s.tickTRIN, s.adrDayHigh, s.adrDayLow, s.indexDayHigh, s.indexDayLow, price});
                    s.exit(id, EnumOrderSide.COVER,EnumOrderType.LMT, price, 0, "", true, "DAY", false,EnumOrderReason.REGULAREXIT,"");
                    s.setLastShortExit(price);
                    TradingUtil.writeToFile(s.getStrategy() + ".csv", buyZone + "," + shortZone + ","+s.tradingSide+"," + s.adr + "," + s.adrHigh + "," + s.adrLow + "," + s.adrDayHigh + "," + s.adrDayLow + "," + s.adrAvg + "," + buyZone1 + "," + shortZone1 + "," + price + "," + s.indexHigh + "," + s.indexLow + "," + s.indexDayHigh + "," + s.indexDayLow + "," + s.indexAvg + "," + buyZone2 + "," + shortZone2 + "," + s.adrTRIN + "," + s.adrTRINAvg + "," + buyZone3 + "," + shortZone3 + "," + s.tick + "," + s.tickTRIN + "," + s.adrTRINHigh + "," + s.adrTRINLow + "," + "TAKEPROFIT COVER");
                }
            } else if (s.getPosition().get(id).getPosition()  >0) {
                if (shortZone || ((price < s.indexHigh - s.stopLoss && !buyZone) || (price < s.getEntryPrice() - s.stopLoss)) || new Date().compareTo(s.getEndDate()) > 0) {
                    logger.log(Level.INFO, " ALL,{0}, StopLoss Sell, adrHigh: {1},adrLow: {2},adrAvg: {3},adrTRINHigh: {4},adrTRINLow: {5},adrTRINAvg: {6},indexHigh :{7},indexLow :{8},indexAvg: {9}, buyZone1: {10}, buyZone2: {11}, buyZone 3: {12}, shortZone1: {13}, shortZone2: {14}, ShortZone3:{15}, ADR: {16}, ADRTrin: {17}, Tick: {18}, TickTrin: {19}, adrDayHigh: {20}, adrDayLow: {21}, IndexDayHigh: {22}, IndexDayLow: {23}, Price: {24}", new Object[]{s.getStrategy(), s.adrHigh, s.adrLow, s.adrAvg, s.adrTRINHigh, s.adrTRINLow, s.adrTRINAvg, s.indexHigh, s.indexLow, s.indexAvg, buyZone1, buyZone2, buyZone3, shortZone1, shortZone2, shortZone3, s.adr, s.adrTRIN, s.tick, s.tickTRIN, s.adrDayHigh, s.adrDayLow, s.indexDayHigh, s.indexDayLow, price});
                    s.exit(id, EnumOrderSide.SELL,EnumOrderType.LMT, price, 0, "", true, "DAY", false,EnumOrderReason.REGULAREXIT,"");
                    TradingUtil.writeToFile(s.getStrategy() + ".csv", buyZone + "," + shortZone + ","+s.tradingSide+"," + s.adr + "," + s.adrHigh + "," + s.adrLow + "," + s.adrDayHigh + "," + s.adrDayLow + "," + s.adrAvg + "," + buyZone1 + "," + shortZone1 + "," + price + "," + s.indexHigh + "," + s.indexLow + "," + s.indexDayHigh + "," + s.indexDayLow + "," + s.indexAvg + "," + buyZone2 + "," + shortZone2 + "," + s.adrTRIN + "," + s.adrTRINAvg + "," + buyZone3 + "," + shortZone3 + "," + s.tick + "," + s.tickTRIN + "," + s.adrTRINHigh + "," + s.adrTRINLow + "," + "STOPLOSS SELL");
                } else if ((s.scalpingMode || !buyZone) && (price > s.getEntryPrice() + s.takeProfit) && (price < s.indexHigh - 0.5 * s.takeProfit)) {
                    logger.log(Level.INFO, " ALL,{0}, TakeProfit Sell, adrHigh: {1},adrLow: {2},adrAvg: {3},adrTRINHigh: {4},adrTRINLow: {5},adrTRINAvg: {6},indexHigh :{7},indexLow :{8},indexAvg: {9}, buyZone1: {10}, buyZone2: {11}, buyZone 3: {12}, shortZone1: {13}, shortZone2: {14}, ShortZone3:{15}, ADR: {16}, ADRTrin: {17}, Tick: {18}, TickTrin: {19}, adrDayHigh: {20}, adrDayLow: {21}, IndexDayHigh: {22}, IndexDayLow: {23}, Price: {24}", new Object[]{s.getStrategy(), s.adrHigh, s.adrLow, s.adrAvg, s.adrTRINHigh, s.adrTRINLow, s.adrTRINAvg, s.indexHigh, s.indexLow, s.indexAvg, buyZone1, buyZone2, buyZone3, shortZone1, shortZone2, shortZone3, s.adr, s.adrTRIN, s.tick, s.tickTRIN, s.adrDayHigh, s.adrDayLow, s.indexDayHigh, s.indexDayLow, price});
                    s.exit(id, EnumOrderSide.SELL,EnumOrderType.LMT, price, 0, "", true, "DAY", false,EnumOrderReason.REGULAREXIT,"");
                    s.setLastLongExit(price);
                    TradingUtil.writeToFile(s.getStrategy() + ".csv", buyZone + "," + shortZone + ","+s.tradingSide+"," + s.adr + "," + s.adrHigh + "," + s.adrLow + "," + s.adrDayHigh + "," + s.adrDayLow + "," + s.adrAvg + "," + buyZone1 + "," + shortZone1 + "," + price + "," + s.indexHigh + "," + s.indexLow + "," + s.indexDayHigh + "," + s.indexDayLow + "," + s.indexAvg + "," + buyZone2 + "," + shortZone2 + "," + s.adrTRIN + "," + s.adrTRINAvg + "," + buyZone3 + "," + shortZone3 + "," + s.tick + "," + s.tickTRIN + "," + s.adrTRINHigh + "," + s.adrTRINLow + "," + "TAKEPROFIT SELL");
                }
            }
        }
    }
    
        boolean atLeastTwo(boolean a, boolean b, boolean c) {
        return a && (b || c) || (b && c);
*/
}

}
