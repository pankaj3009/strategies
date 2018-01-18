/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.algorithms.dataserver;


/**
 * Generates ServerPubSub, Tick , TRIN initialise ServerPubSub passing the
 * adrSymbols that need to be tracked. ServerPubSub will immediately initiate
 * polling market data in snapshot mode. Streaming mode is currently not
 * supported output is available via static fields Advances, Declines, Tick
 * Advances, Tick Declines, Advancing Volume, Declining Volume, Tick Advancing
 * Volume, Tick Declining Volume
 *
 * @author pankaj
 */
public class Dataserver extends Rates {

    public Dataserver(String parameterFile) {
        super(parameterFile);
    }

}
