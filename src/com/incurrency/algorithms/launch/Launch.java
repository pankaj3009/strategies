/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.algorithms.launch;

import com.incurrency.framework.MainAlgorithm;
import com.incurrency.framework.Strategy;
import java.awt.Font;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.io.File;
import java.io.FileInputStream;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import javax.swing.JFrame;
import javax.swing.JOptionPane;

/**
 *
 * @author pankaj
 */
public class Launch extends javax.swing.JFrame {

   static public MainAlgorithm algo;
   static public boolean headless=false;
   private static final Logger logger=Logger.getLogger(Launch.class.getName());
   public static HashMap <String, String> input =new HashMap();
    /**
     * Creates new form Launch
     */
    public Launch() {
        initComponents();
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        buttonGroup1 = new javax.swing.ButtonGroup();
        lblCaptionIBMessage = new javax.swing.JLabel();
        Font font = new Font("Courier", Font.BOLD,12);
        lblCaptionIBMessage.setFont(font);
        lblIBMessage = new javax.swing.JLabel();
        lblCaptionProgramMessage = new javax.swing.JLabel();
        font = new Font("Courier", Font.BOLD,12);
 lblCaptionProgramMessage.setFont(font);
        lblProgramMessage = new javax.swing.JLabel();
        cmdTerminate = new javax.swing.JButton();
        cmdOrderLogs = new javax.swing.JButton();
        jPanel1 = new javax.swing.JPanel();
        radioFiner = new javax.swing.JRadioButton();
        radioFine = new javax.swing.JRadioButton();
        radioInfo = new javax.swing.JRadioButton();
        radioError = new javax.swing.JRadioButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        getContentPane().setLayout(new java.awt.GridBagLayout());

        lblCaptionIBMessage.setText("IB Message:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 0);
        getContentPane().add(lblCaptionIBMessage, gridBagConstraints);

        lblIBMessage.setText("...");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 5;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 0);
        getContentPane().add(lblIBMessage, gridBagConstraints);

        lblCaptionProgramMessage.setText("Program Message:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 0);
        getContentPane().add(lblCaptionProgramMessage, gridBagConstraints);

        lblProgramMessage.setText("...");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.gridwidth = 5;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 0);
        getContentPane().add(lblProgramMessage, gridBagConstraints);

        cmdTerminate.setText("Terminate ALL Algorithms");
        cmdTerminate.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cmdTerminateActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 0);
        getContentPane().add(cmdTerminate, gridBagConstraints);

        cmdOrderLogs.setText("Print Order & Trade logs");
        cmdOrderLogs.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cmdOrderLogsActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 0);
        getContentPane().add(cmdOrderLogs, gridBagConstraints);

        jPanel1.setBorder(javax.swing.BorderFactory.createTitledBorder("Logging Level"));
        jPanel1.setLayout(new java.awt.GridBagLayout());

        buttonGroup1.add(radioFiner);
        radioFiner.setText("Finer");
        radioFiner.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                radioFinerActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(23, 6, 0, 99);
        jPanel1.add(radioFiner, gridBagConstraints);

        buttonGroup1.add(radioFine);
        radioFine.setText("Fine");
        radioFine.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                radioFineActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(3, 6, 0, 99);
        jPanel1.add(radioFine, gridBagConstraints);

        buttonGroup1.add(radioInfo);
        radioInfo.setText("Info");
        radioInfo.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                radioInfoActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 6, 0, 99);
        jPanel1.add(radioInfo, gridBagConstraints);

        buttonGroup1.add(radioError);
        radioError.setText("Error");
        radioError.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                radioErrorActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(3, 6, 90, 99);
        jPanel1.add(radioError, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 4;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.RELATIVE;
        gridBagConstraints.gridheight = 5;
        gridBagConstraints.fill = java.awt.GridBagConstraints.VERTICAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weighty = 0.5;
        getContentPane().add(jPanel1, gridBagConstraints);

        setBounds(0, 0, 726, 254);
    }// </editor-fold>//GEN-END:initComponents

    private void cmdTerminateActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cmdTerminateActionPerformed

        if (input.containsKey("datasource")) { //use jeromq connector
            
            MainAlgorithm.socketListener.getSubs().getSubscriber().disconnect("tcp://" + input.get("datasource")+":"+"5556");
            MainAlgorithm.socketListener.getSubs().close();
        }

        int dialogResult = JOptionPane.showConfirmDialog(null, "Do you want to terminate all running algorithms?", "Warning", JOptionPane.YES_NO_OPTION);
        if (dialogResult == JOptionPane.YES_OPTION) {
            if(algo!=null && !algo.getStrategies().contains("nostrategy")){
                for(Strategy s:algo.getStrategyInstances()){
                    s.printOrders("",s);
                    //logger.log(Level.INFO,"101",s.getClass().getName());
                }
            }
            System.exit(0);
            
        }

    }//GEN-LAST:event_cmdTerminateActionPerformed

    private void cmdOrderLogsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cmdOrderLogsActionPerformed
              
                    if(algo!=null){
                for(Strategy s:algo.getStrategyInstances()){
                    s.printOrders("tmp",s);
                }
            }
               /* commented out after reflection change     
        if(algo!=null && algo.getParamADR()!=null){
                algo.getParamADR().printOrders("tmp");
            }
            if(algo !=null && algo.getParamTurtle()!=null){
                algo.getParamTurtle().printOrders("tmp");
            }
            if(algo !=null && algo.getParamSwing()!=null){
                algo.getParamSwing().printOrders("tmp");
            }*/
    }//GEN-LAST:event_cmdOrderLogsActionPerformed

    private void radioFinerActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_radioFinerActionPerformed
        Logger incurrency=Logger.getLogger("com.incurrency");
        Logger console=Logger.getLogger("java.util.logging.ConsoleHandler");
        console.setLevel(Level.FINER);
        incurrency.setLevel(Level.FINER);
    }//GEN-LAST:event_radioFinerActionPerformed

    private void radioFineActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_radioFineActionPerformed
        Logger incurrency=Logger.getLogger("com.incurrency");
        Logger console=Logger.getLogger("java.util.logging.ConsoleHandler");
        console.setLevel(Level.FINE);
        incurrency.setLevel(Level.FINE);
    }//GEN-LAST:event_radioFineActionPerformed

    private void radioInfoActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_radioInfoActionPerformed
        Logger incurrency=Logger.getLogger("com.incurrency");
        Logger console=Logger.getLogger("java.util.logging.ConsoleHandler");
        console.setLevel(Level.INFO);
        incurrency.setLevel(Level.INFO);
        
    }//GEN-LAST:event_radioInfoActionPerformed

    private void radioErrorActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_radioErrorActionPerformed
        Logger incurrency=Logger.getLogger("com.incurrency");
        Logger console=Logger.getLogger("java.util.logging.ConsoleHandler");
        console.setLevel(Level.SEVERE);
        incurrency.setLevel(Level.SEVERE);
    }//GEN-LAST:event_radioErrorActionPerformed

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
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | javax.swing.UnsupportedLookAndFeelException ex) {
            logger.log(java.util.logging.Level.INFO, "101", ex);
        }
        //</editor-fold>
         for(int i=0;i<args.length;i++){
            input.put(args[i].split("=")[0].toLowerCase(), args[i].split("=")[1].toLowerCase());
        }
         headless=(input.get("headless")==null||input.get("headless").compareTo("false")==0)?false:true;
            FileInputStream configFile;
            if(new File("logging.properties").exists()){
            configFile = new FileInputStream("logging.properties");
            LogManager.getLogManager().readConfiguration(configFile);
            }
        /* Create and display the form */
        java.awt.EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
                if(!headless){
                JFrame f=new Launch();
                f.pack();
                f.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
                GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
                GraphicsDevice defaultScreen = ge.getDefaultScreenDevice();
                Rectangle rect = defaultScreen.getDefaultConfiguration().getBounds();
                //int x = (int) rect.getMaxX() - f.getWidth();
                int x=0;
                int y = (int) rect.getMaxY() - f.getHeight()-50;
                f.setLocation(x, y);
                f.setVisible(true);
                f.setVisible(true);                
                }
            }
        });
            Thread.sleep(3000);
            boolean trading=false;
            if(input.containsKey("backtest")){
                trading=false;
            }else{
                trading=true;
            }
            algo=MainAlgorithm.getInstance(input,trading);
            //register strategy
            while(MainAlgorithm.getInstance()==null){
             Thread.yield();
            }
            algo=MainAlgorithm.getInstance();
            
            if(input.get("adr")!=null){
                algo.registerStrategy("com.incurrency.algorithms.adr.ADR",trading);
            }
            if(input.get("csv")!=null){
                algo.registerStrategy("com.incurrency.algorithms.csv.CSV",trading);//
            }
            if(input.get("idt")!=null){
                algo.registerStrategy("com.incurrency.algorithms.turtle.IDT",trading);
            }
            algo.postInit();
            
            

                //JFrame d=new com.incurrency.framework.display.DashBoardNew();

    }
    
    public static synchronized void setIBMessage(String message) {
        if(!headless){
        lblIBMessage.setText("<html>" + message + "</html>");
        }
    }

    public static synchronized void setMessage(String message) {
        if(!headless){
            lblProgramMessage.setText(message);
        }
    }    

    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.ButtonGroup buttonGroup1;
    private javax.swing.JButton cmdOrderLogs;
    private javax.swing.JButton cmdTerminate;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JLabel lblCaptionIBMessage;
    private javax.swing.JLabel lblCaptionProgramMessage;
    private static javax.swing.JLabel lblIBMessage;
    private static javax.swing.JLabel lblProgramMessage;
    private javax.swing.JRadioButton radioError;
    private javax.swing.JRadioButton radioFine;
    private javax.swing.JRadioButton radioFiner;
    private javax.swing.JRadioButton radioInfo;
    // End of variables declaration//GEN-END:variables
}
