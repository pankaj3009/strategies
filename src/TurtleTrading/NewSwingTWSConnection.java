/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package TurtleTrading;

import incurrframework.*;

/**
 *
 * @author admin
 */
public class NewSwingTWSConnection extends incurrframework.TWSConnection {

    ConnectionBean c;

    NewSwingTWSConnection(ConnectionBean c) {
        super(c);
        this.c = c;
    }

    public void nextValidId(int orderId) {
        c.getIdmanager().initializeOrderId(orderId);
    }

    public void currentTime(long time) {
        c.setTimeDiff(System.currentTimeMillis() - time * 1000);
        System.out.println("Time Diff for:" + c.getIp() + ":" + c.getTimeDiff());
    }
}
