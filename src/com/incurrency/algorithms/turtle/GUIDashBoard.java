/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.algorithms.turtle;

import com.incurrency.framework.MainAlgorithm;
import java.awt.Dimension;
import java.awt.GridLayout;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;

/**
 *
 * @author pankaj
 */
public class GUIDashBoard extends JPanel{
    private boolean DEBUG = false;
 
    public GUIDashBoard(MainAlgorithm m) {
        super(new GridLayout(1,0));
       // JFrame frame=new JFrame();
        //frame.setLayout(null);
        //this.setBounds(1000, 1000, 800, 500);
        //frame.add(this);
        JTable table1 = new JTable(new TableModelDashBoard(m));
        table1.setPreferredScrollableViewportSize(new Dimension(800, 400));
        table1.setFillsViewportHeight(true);
        table1.setAutoCreateRowSorter(true);
         //Create the scroll pane and add the table to it.
        JScrollPane scrollPane = new JScrollPane(table1);
         //Add the scroll pane to this panel.
        add(scrollPane);
        scrollPane.getViewport().setViewPosition(new java.awt.Point(1000,1000));

        }
}
