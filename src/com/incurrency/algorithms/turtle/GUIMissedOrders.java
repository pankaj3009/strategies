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
public class GUIMissedOrders extends JPanel {
    private boolean DEBUG = false;
    JTable table1;
 
    public GUIMissedOrders(MainAlgorithm m) {
        super(new GridLayout(1,0));
 
        table1 = new JTable(new TableModelMissedOrders(m));
        table1.setPreferredScrollableViewportSize(new Dimension(500, 200));
        table1.setFillsViewportHeight(true);
        table1.setAutoCreateRowSorter(true);
         //Create the scroll pane and add the table to it.
        JScrollPane scrollPane = new JScrollPane(table1);
         //Add the scroll pane to this panel.
        add(scrollPane);

        }
}
