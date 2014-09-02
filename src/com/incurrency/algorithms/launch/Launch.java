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

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);

        lblCaptionIBMessage.setText("IB Message:");

        lblIBMessage.setText("...");

        lblCaptionProgramMessage.setText("Program Message:");

        lblProgramMessage.setText("...");

        cmdTerminate.setText("Terminate ALL Algorithms");
        cmdTerminate.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cmdTerminateActionPerformed(evt);
            }
        });

        cmdOrderLogs.setText("Print Order & Trade logs");
        cmdOrderLogs.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cmdOrderLogsActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(29, 29, 29)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(lblCaptionIBMessage, javax.swing.GroupLayout.PREFERRED_SIZE, 117, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(lblIBMessage, javax.swing.GroupLayout.PREFERRED_SIZE, 480, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(lblCaptionProgramMessage)
                            .addComponent(lblProgramMessage, javax.swing.GroupLayout.PREFERRED_SIZE, 490, javax.swing.GroupLayout.PREFERRED_SIZE)))
                    .addGroup(layout.createSequentialGroup()
                        .addGap(39, 39, 39)
                        .addComponent(cmdTerminate, javax.swing.GroupLayout.PREFERRED_SIZE, 219, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(29, 29, 29)
                        .addComponent(cmdOrderLogs, javax.swing.GroupLayout.PREFERRED_SIZE, 173, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addGap(191, 191, 191))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(21, 21, 21)
                .addComponent(lblCaptionIBMessage)
                .addGap(6, 6, 6)
                .addComponent(lblIBMessage, javax.swing.GroupLayout.PREFERRED_SIZE, 49, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(lblCaptionProgramMessage)
                .addGap(11, 11, 11)
                .addComponent(lblProgramMessage, javax.swing.GroupLayout.PREFERRED_SIZE, 44, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(11, 11, 11)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(cmdTerminate)
                    .addComponent(cmdOrderLogs)))
        );

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
                    Strategy.printOrders("",s);
                    //logger.log(Level.INFO,"101",s.getClass().getName());
                }
            }
            System.exit(0);
            
        }

    }//GEN-LAST:event_cmdTerminateActionPerformed

    private void cmdOrderLogsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cmdOrderLogsActionPerformed
              
                    if(algo!=null){
                for(Strategy s:algo.getStrategyInstances()){
                    Strategy.printOrders("tmp",s);
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
            algo=MainAlgorithm.getInstance(input);

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
    private javax.swing.JButton cmdOrderLogs;
    private javax.swing.JButton cmdTerminate;
    private javax.swing.JLabel lblCaptionIBMessage;
    private javax.swing.JLabel lblCaptionProgramMessage;
    private static javax.swing.JLabel lblIBMessage;
    private static javax.swing.JLabel lblProgramMessage;
    // End of variables declaration//GEN-END:variables
}
