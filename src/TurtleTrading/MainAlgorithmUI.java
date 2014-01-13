/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/*
 * NewSwingGUI.java
 *
 * Created on 09-Aug-2013, 10:05:09
 */
package TurtleTrading;


import incurrframework.BeanConnection;
import incurrframework.Registration;
import incurrframework.Parameters;
import incurrframework.BeanSymbol;
import incurrframework.OrderBean;
import java.beans.XMLEncoder;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import javax.swing.*;
import org.supercsv.io.CsvBeanWriter;
import org.supercsv.prefs.CsvPreference;

/**
 *
 * @author admin
 */
public class MainAlgorithmUI extends javax.swing.JFrame {
   static List<String> myList = new ArrayList<String>();
   public JList list;
   static public MainAlgorithm algo;
   static String parameterFileName;
    /** Creates new form NewSwingGUI */
    public MainAlgorithmUI() {
        initComponents();
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        cmdLong = new javax.swing.JButton();
        cmdShort = new javax.swing.JButton();
        cmdBoth = new javax.swing.JButton();
        cmdPause = new javax.swing.JButton();
        cmdSquareAll = new javax.swing.JButton();
        cmdStart = new javax.swing.JButton();
        cmdAggressionDisable = new javax.swing.JButton();
        cmdAggressionEnable = new javax.swing.JButton();
        jLabel1 = new javax.swing.JLabel();
        lblMessage = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        lblIBMessage = new javax.swing.JLabel();
        cmdRegister = new javax.swing.JButton();
        cmdExitLongs = new javax.swing.JButton();
        cmdExitShorts = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("Intra-Day Turtle (IDT)");
        setPreferredSize(new java.awt.Dimension(800, 250));

        cmdLong.setText("Long only");
        cmdLong.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cmdLongActionPerformed(evt);
            }
        });

        cmdShort.setText("Short Only");
        cmdShort.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cmdShortActionPerformed(evt);
            }
        });

        cmdBoth.setText("Long and Short Trading");
        cmdBoth.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cmdBothActionPerformed(evt);
            }
        });

        cmdPause.setText("Pause Program");
        cmdPause.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cmdPauseActionPerformed(evt);
            }
        });

        cmdSquareAll.setText("Square All");
        cmdSquareAll.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cmdSquareAllActionPerformed(evt);
            }
        });

        cmdStart.setText("Start Program");
        cmdStart.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cmdStartActionPerformed(evt);
            }
        });

        cmdAggressionDisable.setText("Disable Aggro");
        cmdAggressionDisable.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cmdAggressionDisableActionPerformed(evt);
            }
        });

        cmdAggressionEnable.setText("Enable Aggro");
        cmdAggressionEnable.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cmdAggressionEnableActionPerformed(evt);
            }
        });

        jLabel1.setText("Program message:");

        lblMessage.setText("Please ensure TWS/Gateway is running before starting this program");

        jLabel2.setText("IB API message:");

        lblIBMessage.setText("...");

        cmdRegister.setText("Register");
        cmdRegister.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cmdRegisterActionPerformed(evt);
            }
        });

        cmdExitLongs.setText("Exit Longs");
        cmdExitLongs.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cmdExitLongsActionPerformed(evt);
            }
        });

        cmdExitShorts.setText("Exit Shorts");
        cmdExitShorts.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cmdExitShortsActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(javax.swing.GroupLayout.Alignment.LEADING, layout.createSequentialGroup()
                        .addGap(59, 59, 59)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel1, javax.swing.GroupLayout.PREFERRED_SIZE, 93, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jLabel2))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(lblIBMessage, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(lblMessage)))
                    .addGroup(javax.swing.GroupLayout.Alignment.LEADING, layout.createSequentialGroup()
                        .addContainerGap()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(cmdLong, javax.swing.GroupLayout.PREFERRED_SIZE, 98, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(5, 5, 5)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(cmdExitShorts, javax.swing.GroupLayout.PREFERRED_SIZE, 95, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(cmdShort, javax.swing.GroupLayout.PREFERRED_SIZE, 95, javax.swing.GroupLayout.PREFERRED_SIZE)))
                            .addComponent(cmdStart, javax.swing.GroupLayout.PREFERRED_SIZE, 201, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(cmdExitLongs, javax.swing.GroupLayout.PREFERRED_SIZE, 98, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(cmdAggressionDisable, javax.swing.GroupLayout.PREFERRED_SIZE, 198, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(18, 18, 18)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(cmdSquareAll, javax.swing.GroupLayout.PREFERRED_SIZE, 174, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(cmdBoth, javax.swing.GroupLayout.PREFERRED_SIZE, 174, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(cmdPause, javax.swing.GroupLayout.PREFERRED_SIZE, 174, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(cmdAggressionEnable, javax.swing.GroupLayout.PREFERRED_SIZE, 174, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(18, 18, 18)
                        .addComponent(cmdRegister, javax.swing.GroupLayout.PREFERRED_SIZE, 133, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addGap(230, 230, 230))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel1)
                    .addComponent(lblMessage))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(lblIBMessage)
                    .addComponent(jLabel2, javax.swing.GroupLayout.Alignment.TRAILING))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(1, 1, 1)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(cmdStart)
                            .addComponent(cmdPause))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(cmdLong)
                            .addComponent(cmdShort)
                            .addComponent(cmdBoth))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(cmdSquareAll)
                            .addComponent(cmdExitLongs)
                            .addComponent(cmdExitShorts))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(cmdAggressionDisable)
                            .addComponent(cmdAggressionEnable)))
                    .addComponent(cmdRegister, javax.swing.GroupLayout.PREFERRED_SIZE, 134, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(306, 306, 306))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents
/**/
    /**/
    
    private void cmdLongActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cmdLongActionPerformed
        algo.getParamTurtle().setLongOnly(true);
        algo.getParamTurtle().setShortOnly(false);
         Logger.getLogger(MainAlgorithmUI.class.getName()).log(Level.INFO, "Set to Long Only");
    }//GEN-LAST:event_cmdLongActionPerformed

    private void cmdShortActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cmdShortActionPerformed
        algo.getParamTurtle().setLongOnly(false);
        algo.getParamTurtle().setShortOnly(true);
        Logger.getLogger(MainAlgorithmUI.class.getName()).log(Level.INFO, "Set to Short Only");
    }//GEN-LAST:event_cmdShortActionPerformed

    private void cmdBothActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cmdBothActionPerformed
        algo.getParamTurtle().setLongOnly(true);
        algo.getParamTurtle().setShortOnly(true);
        Logger.getLogger(MainAlgorithmUI.class.getName()).log(Level.INFO, "Set to Long and Short");
    }//GEN-LAST:event_cmdBothActionPerformed

    private void cmdPauseActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cmdPauseActionPerformed
        algo.getParamTurtle().setLongOnly(false);
        algo.getParamTurtle().setShortOnly(false);
    }//GEN-LAST:event_cmdPauseActionPerformed

    private void cmdSquareAllActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cmdSquareAllActionPerformed
        algo.getParamTurtle().setLongOnly(false);
        algo.getParamTurtle().setShortOnly(false);
        for (BeanConnection c : Parameters.connection) {
            if ("Trading".equals(c.getPurpose()) && c.getStrategy().contains("TurtleTrading")) {
                for (int id = 0; id < Parameters.symbol.size(); id++) {
                    algo.ordManagement.cancelOpenOrders(c, id, "TurtleTrading");
                    algo.ordManagement.squareAllPositions(c, id, "TurtleTrading");
                }
            }
    }//GEN-LAST:event_cmdSquareAllActionPerformed
    }
    
    public static void disableStartButton(){
        cmdStart.setEnabled(false);
    }
    
    private void cmdStartActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cmdStartActionPerformed
        algo.startDataCollection(algo.getHistoricalData(), algo.getRealTimeBars(),MainAlgorithm.getStartDate());
    }//GEN-LAST:event_cmdStartActionPerformed

    private void cmdAggressionDisableActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cmdAggressionDisableActionPerformed
        algo.getParamTurtle().setAggression(false);
    }//GEN-LAST:event_cmdAggressionDisableActionPerformed

    private void cmdAggressionEnableActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cmdAggressionEnableActionPerformed
        algo.getParamTurtle().setAggression(true);
    }//GEN-LAST:event_cmdAggressionEnableActionPerformed

    private void cmdRegisterActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cmdRegisterActionPerformed
        Registration register=new Registration();
                register.setLocation(0,0);
                register.setVisible(true);
    }//GEN-LAST:event_cmdRegisterActionPerformed

    private void cmdExitLongsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cmdExitLongsActionPerformed
        algo.getParamTurtle().setLongOnly(false);
        for (BeanConnection c : Parameters.connection) {
            if ("Trading".equals(c.getPurpose()) && c.getStrategy().contains("TurtleTrading")) {
                for (int id = 0; id < Parameters.symbol.size(); id++) {
                    if(c.getPositions().get(id)!=null){
                        if(c.getPositions().get(id).getPosition()>0){
                            algo.ordManagement.cancelOpenOrders(c, id, "TurtleTrading");
                            algo.ordManagement.squareAllPositions(c, id, "TurtleTrading");
                        }
                    }
                }
            }
        } 
    }//GEN-LAST:event_cmdExitLongsActionPerformed

    private void cmdExitShortsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cmdExitShortsActionPerformed
        algo.getParamTurtle().setShortOnly(false);
        for (BeanConnection c : Parameters.connection) {
            if ("Trading".equals(c.getPurpose()) && c.getStrategy().contains("TurtleTrading")) {
                for (int id = 0; id < Parameters.symbol.size(); id++) {
                    if(c.getPositions().get(id)!=null){
                        if(c.getPositions().get(id).getPosition()<0){
                            algo.ordManagement.cancelOpenOrders(c, id, "TurtleTrading");
                            algo.ordManagement.squareAllPositions(c, id, "TurtleTrading");
                        }
                    }
                }
            }
        } 
    }//GEN-LAST:event_cmdExitShortsActionPerformed
    
    public static synchronized void setMessage(String message) {
        lblMessage.setText(message);
    }
    
   public static synchronized void setIBMessage(String message) {
        lblIBMessage.setText("<html>"+message+"</html>");
    }
   
   public static synchronized void displayRegistration(boolean display){
       cmdRegister.setVisible(display);
   }
    
    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) throws Exception {
        /* Set the Nimbus look and feel */
        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html 
         */
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ClassNotFoundException ex) {
            java.util.logging.Logger.getLogger(MainAlgorithmUI.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (InstantiationException ex) {
            java.util.logging.Logger.getLogger(MainAlgorithmUI.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            java.util.logging.Logger.getLogger(MainAlgorithmUI.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (javax.swing.UnsupportedLookAndFeelException ex) {
            java.util.logging.Logger.getLogger(MainAlgorithmUI.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        //</editor-fold>
//1. Add the parameters files to myList variable  
        if(args.length==3){
        myList.add(args[0]);
        myList.add(args[1]);
        myList.add(args[2]);
        parameterFileName=args[0];
        
        }else{
        myList.add("symbols.csv");
        myList.add("connection.csv");}
//        loadParam("Algo.properties");
        FileInputStream configFile = null;
        configFile = new FileInputStream("logging.properties");
        LogManager.getLogManager().readConfiguration(configFile);
        java.awt.EventQueue.invokeLater(new Runnable() {
           
            public void run() {
                
                MainAlgorithmUI startup=new MainAlgorithmUI();
                startup.setLocation(0,465);
                MainAlgorithmUI.displayRegistration(false);
                MainAlgorithmUI.disableStartButton();
                startup.setVisible(true);
            }
            });
            algo=new MainAlgorithm(myList);
        /* Create and display the form */
    }
    
    static private void loadParam(String param){
        Properties props = new Properties();
        try{
            props.load(new FileInputStream(param));
         
        }
        catch(IOException e){
             e.printStackTrace();
        }
    }
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton cmdAggressionDisable;
    private javax.swing.JButton cmdAggressionEnable;
    private javax.swing.JButton cmdBoth;
    private javax.swing.JButton cmdExitLongs;
    private javax.swing.JButton cmdExitShorts;
    private javax.swing.JButton cmdLong;
    private javax.swing.JButton cmdPause;
    private static javax.swing.JButton cmdRegister;
    private javax.swing.JButton cmdShort;
    private javax.swing.JButton cmdSquareAll;
    private static javax.swing.JButton cmdStart;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private static javax.swing.JLabel lblIBMessage;
    private static javax.swing.JLabel lblMessage;
    // End of variables declaration//GEN-END:variables
}
