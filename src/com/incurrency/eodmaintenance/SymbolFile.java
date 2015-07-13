/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.eodmaintenance;

import java.util.List;

/**
 *
 * @author Pankaj
 */
public class SymbolFile {
    int serialno;
    String brokerSymbol;
    String exchangeSymbol;
    String displayName;
    String type;
    String exchange;
    String primaryExchange;
    String currency;
    String expiry;
    String option;
    String right;
    int minSize;
    String barsstarttime;
    int streaming;
    String strategy;

    public SymbolFile(List<SymbolFile> symbols,String symbol, String servicename, String type, String exchange, String primaryExchange, String currency, String expiry, String option, String right, int minSize, String barsstarttime, int streaming, String strategy) {
        this.serialno=symbols.size()+1;
        this.brokerSymbol = symbol.trim().toUpperCase();
        this.displayName = servicename.trim().toUpperCase();
        this.type = type.trim().toUpperCase();
        this.exchange = exchange.trim().toUpperCase();
        this.primaryExchange = primaryExchange.trim().toUpperCase();
        this.currency = currency.trim().toUpperCase();
        this.expiry = expiry.trim().toUpperCase();
        this.option = option.trim().toUpperCase();
        this.right = right.trim().toUpperCase();
        this.minSize = minSize;
        this.barsstarttime = barsstarttime.trim().toUpperCase();
        this.streaming = streaming;
        this.strategy = strategy.trim().toUpperCase();
    }
            
}
