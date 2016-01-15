/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.scanneradr;

import java.util.Comparator;

/**
 *
 * @author admin
 */
public class ArrayIndexComparator implements Comparator<Integer>
{
    private final double[] array;

    public ArrayIndexComparator(double[] array)
    {
        this.array = array;
    }

    public Integer[] createIndexArray()
    {
        Integer[] indexes = new Integer[array.length];
        for (int i = 0; i < array.length; i++)
        {
            indexes[i] = i; // Autoboxing
        }
        return indexes;
    }

    @Override
    public int compare(Integer index1, Integer index2)
    {
         // Autounbox from Integer to int to use as array indexes
        if(array[index1]>array[index2]){
            return 1;
        }else if(array[index1]<array[index2]){
            return -1;
        }else{
            return 0;
        }
    }
}