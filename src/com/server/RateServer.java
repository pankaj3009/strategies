/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.server;
import org.zeromq.ZMQ;
/**
 *
 * @author pankaj
 */
public class RateServer {
     ZMQ.Context context = ZMQ.context(1);
     ZMQ.Socket publisher;

     
     public RateServer(int port){
        publisher = context.socket(ZMQ.PUB);
        publisher.bind("tcp://*:"+port);
        //publisher.bind("ipc://weather");
     }
     
     public void send(String topic, String message){
         publisher.sendMore(topic);
         publisher.send(message,0);
     }
     
}
