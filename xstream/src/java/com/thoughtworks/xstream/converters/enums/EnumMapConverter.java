/*
 * Copyright (C) 2005 Joe Walnes.
 * Copyright (C) 2006, 2007, 2008, 2009, 2013, 2020 XStream Committers.
 * All rights reserved.
 *
 * The software in this package is published under the terms of the BSD
 * style license a copy of which has been included with this distribution in
 * the LICENSE.txt file.
 * 
 * Created on 06. April 2005 by Joe Walnes
 */

// ***** READ THIS *****
// This class will only compile with JDK 1.5.0 or above as it test Java enums.
// If you are using an earlier version of Java, just don't try to build this class. XStream should work fine without it.

package com.thoughtworks.xstream.converters.enums;

import com.thoughtworks.xstream.converters.collections.MapConverter;
import com.thoughtworks.xstream.converters.ConversionException;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.mapper.Mapper;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.core.util.Fields;

import java.util.EnumMap;
import java.lang.reflect.Field;

/**
 * Serializes an Java 5 EnumMap, including the type of Enum it's for. If a SecurityManager is set, the converter will only work with permissions
 * for SecurityManager.checkPackageAccess, SecurityManager.checkMemberAccess(this, EnumSet.MEMBER)
 * and ReflectPermission("suppressAccessChecks").
 *
 * @author Joe Walnes
 */
public class EnumMapConverter extends MapConverter {

    public EnumMapConverter(Mapper mapper) {
        super(mapper);
    }

    public boolean canConvert(Class type) {
        return type == EnumMap.class && Reflections.typeField != null;
    }

    public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
        Class type = (Class) Fields.read(Reflections.typeField, source);
        String attributeName = mapper().aliasForSystemAttribute("enum-type");
        if (attributeName != null) {
            writer.addAttribute(attributeName, mapper().serializedClass(type));
        }
        super.marshal(source, writer, context);
    }

    @SuppressWarnings("unchecked")
    public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
        String attributeName = mapper().aliasForSystemAttribute("enum-type");
        if (attributeName == null) {
            throw new ConversionException("No EnumType specified for EnumMap");
        }
        Class type = mapper().realClass(reader.getAttribute(attributeName));
        EnumMap map = new EnumMap(type);
        populateMap(reader, context, map);
        return map;
    }

    private static class Reflections {
        private final static Field typeField = Fields.locate(EnumMap.class, Class.class, false);
    }
}
