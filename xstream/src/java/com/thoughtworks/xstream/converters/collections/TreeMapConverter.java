/*
 * Copyright (C) 2004, 2005 Joe Walnes.
 * Copyright (C) 2006, 2007, 2010, 2011, 2013, 2016, 2018, 2020 XStream Committers.
 * All rights reserved.
 *
 * The software in this package is published under the terms of the BSD
 * style license a copy of which has been included with this distribution in
 * the LICENSE.txt file.
 * 
 * Created on 08. May 2004 by Joe Walnes
 */
package com.thoughtworks.xstream.converters.collections;

import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.converters.reflection.ObjectAccessException;
import com.thoughtworks.xstream.core.JVM;
import com.thoughtworks.xstream.core.util.Fields;
import com.thoughtworks.xstream.core.util.HierarchicalStreams;
import com.thoughtworks.xstream.core.util.PresortedMap;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.mapper.Mapper;

import java.lang.reflect.Field;
import java.util.Comparator;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Converts a java.util.TreeMap to XML, and serializes
 * the associated java.util.Comparator. The converter
 * assumes that the entries in the XML are already sorted 
 * according the comparator.
 *
 * @author Joe Walnes
 * @author J&ouml;rg Schaible
 */
public class TreeMapConverter extends MapConverter {
    
    private static final class NullComparator extends Mapper.Null implements Comparator {
        public int compare(Object o1, Object o2) {
            Comparable c1 = (Comparable)o1;
            Comparable c2 = (Comparable)o2;
            return c1.compareTo(o2);
        }
    }

    private final static Comparator NULL_MARKER = new NullComparator();

    public TreeMapConverter(Mapper mapper) {
        super(mapper, TreeMap.class);
    }

    public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
        SortedMap sortedMap = (SortedMap) source;
        marshalComparator(sortedMap.comparator(), writer, context);
        super.marshal(source, writer, context);
    }

    protected void marshalComparator(Comparator comparator, HierarchicalStreamWriter writer,
        MarshallingContext context) {
        if (comparator != null) {
            writer.startNode("comparator");
            writer.addAttribute(mapper().aliasForSystemAttribute("class"), 
                mapper().serializedClass(comparator.getClass()));
            context.convertAnother(comparator);
            writer.endNode();
        }
    }

    public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
        TreeMap result = Reflections.comparatorField != null ? new TreeMap() :  null;
        final Comparator comparator = unmarshalComparator(reader, context, result);
        if (result == null) {
            result = comparator == null || comparator == NULL_MARKER ? new TreeMap() : new TreeMap(comparator);
        }
        populateTreeMap(reader, context, result, comparator);
        return result;
    }

    protected Comparator unmarshalComparator(HierarchicalStreamReader reader,
        UnmarshallingContext context, TreeMap result) {
        final Comparator comparator;
        if (reader.hasMoreChildren()) {
            reader.moveDown();
            if (reader.getNodeName().equals("comparator")) {
                Class comparatorClass = HierarchicalStreams.readClassType(reader, mapper());
                comparator = (Comparator) context.convertAnother(result, comparatorClass);
            } else if (reader.getNodeName().equals("no-comparator")) { // pre 1.4 format
                comparator = null;
            } else {
                // we are already within the first entry
                return NULL_MARKER;
            }
            reader.moveUp();
        } else {
            comparator = null;
        }
        return comparator;
    }

    protected void populateTreeMap(HierarchicalStreamReader reader, UnmarshallingContext context,
        TreeMap result, Comparator comparator) {
        boolean inFirstElement = comparator == NULL_MARKER;
        if (inFirstElement) {
            comparator = null;
        }
        SortedMap sortedMap = new PresortedMap(comparator != null && JVM.hasOptimizedTreeMapPutAll() ? comparator : null);
        if (inFirstElement) {
            // we are already within the first entry
            putCurrentEntryIntoMap(reader, context, result, sortedMap);
            reader.moveUp();
        }
        populateMap(reader, context, result, sortedMap);
        try {
            if (JVM.hasOptimizedTreeMapPutAll()) {
                if (comparator != null && Reflections.comparatorField != null) {
                    Reflections.comparatorField.set(result, comparator);
                }
                result.putAll(sortedMap); // internal optimization will not call comparator
            } else if (Reflections.comparatorField != null) {
                Reflections.comparatorField.set(result, sortedMap.comparator());
                result.putAll(sortedMap); // "sort" by index
                Reflections.comparatorField.set(result, comparator);
            } else {
                result.putAll(sortedMap); // will use comparator for already sorted map
            }
        } catch (final IllegalAccessException e) {
            throw new ObjectAccessException("Cannot set comparator of TreeMap", e);
        }
    }

    private static class Reflections {
        private final static Field comparatorField = Fields.locate(TreeMap.class, Comparator.class, false);
    }
}
