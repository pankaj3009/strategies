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

import incurrframework.Parameters;
import incurrframework.BeanSymbol;
import java.beans.XMLEncoder;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import javax.swing.*;

/**
 *
 * @author admin
 */
public class MainAlgorithmUI extends javax.swing.JFrame {
   static List<String> myList = new ArrayList<String>();
   public JList list;
   static public MainAlgorithm algo;
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

        Start = new javax.swing.JButton();
        OutputTable = new javax.swing.JScrollPane();
        DataTable = new javax.swing.JTable();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("Swing");

        Start.setText("Start Swing");
        Start.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                StartActionPerformed(evt);
            }
        });

        DataTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null}
            },
            new String [] {
                "Title 1", "Title 2", "Title 3", "Title 4"
            }
        ));
        OutputTable.setViewportView(DataTable);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap(270, Short.MAX_VALUE)
                .addComponent(Start)
                .addGap(409, 409, 409))
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(OutputTable, javax.swing.GroupLayout.PREFERRED_SIZE, 700, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(56, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(24, 24, 24)
                .addComponent(Start)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(OutputTable, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

private void StartActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_StartActionPerformed
        //algo.run();
        //DataTable.setModel(MainAlgorithm.model1);
    XMLEncoder encoder1;
    XMLEncoder encoder2;
       try {
       encoder1 = new XMLEncoder(new BufferedOutputStream(new FileOutputStream("Beanarchive.xml")));
       encoder1.writeObject(Parameters.symbol);
       encoder1.close();
       encoder2 = new XMLEncoder(new BufferedOutputStream(new FileOutputStream("Connectionarchive.xml")));
       encoder2.writeObject(Parameters.connection);
       encoder2.close();
       } catch (FileNotFoundException ex) {
           Logger.getLogger(MainAlgorithmUI.class.getName()).log(Level.SEVERE, null, ex);
       }

 
        
}//GEN-LAST:event_StartActionPerformed

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
        myList.add("symbols.csv");
        myList.add("connection.csv");
        loadParam("NewSwing.properties");
        FileInputStream configFile = null;
        configFile = new FileInputStream("logging.properties");
        LogManager.getLogManager().readConfiguration(configFile);
        algo=new MainAlgorithm(myList);
        /* Create and display the form */
//2. Load properties file for any global parameters like start time, end time, blah blah
        
        java.awt.EventQueue.invokeLater(new Runnable() {
           
            public void run() {
                new MainAlgorithmUI().setVisible(true);
            }
        });
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
    private javax.swing.JTable DataTable;
    private javax.swing.JScrollPane OutputTable;
    private javax.swing.JButton Start;
    // End of variables declaration//GEN-END:variables
}
