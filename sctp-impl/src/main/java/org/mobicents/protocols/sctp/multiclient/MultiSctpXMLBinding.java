/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011, Red Hat, Inc. and/or its affiliates, and individual
 * contributors as indicated by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a full listing
 * of individual contributors.
 * 
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU General Public License, v. 2.0.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of 
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU 
 * General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License,
 * v. 2.0 along with this distribution; if not, write to the Free 
 * Software Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301, USA.
 */
package org.mobicents.protocols.sctp.multiclient;

import java.util.Map.Entry;

import javolution.xml.XMLBinding;
import javolution.xml.XMLFormat;
import javolution.xml.stream.XMLStreamException;

import org.mobicents.protocols.sctp.AssociationMap;

/**
 * @author amit bhayani
 * @author balogh.gabor@alerant.hu
 */
@SuppressWarnings("serial")
public class MultiSctpXMLBinding extends XMLBinding {

    private final boolean isBranched;

    public MultiSctpXMLBinding(boolean isBranched) {
        this.isBranched = isBranched;
    }

    protected static final XMLFormat<AssociationMap<String, ManageableAssociation>> ASSOCIATION_MAP_ONE_TO_ONE = new XMLFormat<AssociationMap<String, ManageableAssociation>>(
            null) {

        @Override
        public void write(AssociationMap<String, ManageableAssociation> obj, javolution.xml.XMLFormat.OutputElement xml)
                throws XMLStreamException {
            for (Entry<String, ManageableAssociation> entry : obj.entrySet()) {
                xml.add((String) entry.getKey(), "name", String.class);
                xml.add((ManageableAssociation) entry.getValue(), "association", ManageableAssociation.class);
            }
        }

        @Override
        public void read(javolution.xml.XMLFormat.InputElement xml, AssociationMap<String, ManageableAssociation> obj)
                throws XMLStreamException {
            while (xml.hasNext()) {
                String key = xml.get("name", String.class);
                OneToOneAssociationImpl association = xml.get("association", OneToOneAssociationImpl.class);
                obj.put(key, association);
            }
        }
    };

    protected static final XMLFormat<AssociationMap<String, ManageableAssociation>> ASSOCIATION_MAP_ONE_TO_MANY = new XMLFormat<AssociationMap<String, ManageableAssociation>>(
            null) {

        @Override
        public void write(AssociationMap<String, ManageableAssociation> obj, javolution.xml.XMLFormat.OutputElement xml)
                throws XMLStreamException {
            for (Entry<String, ManageableAssociation> entry : obj.entrySet()) {
                xml.add((String) entry.getKey(), "name", String.class);
                xml.add((ManageableAssociation) entry.getValue(), "association", ManageableAssociation.class);
            }
        }

        @Override
        public void read(javolution.xml.XMLFormat.InputElement xml, AssociationMap<String, ManageableAssociation> obj)
                throws XMLStreamException {
            while (xml.hasNext()) {
                String key = xml.get("name", String.class);
                OneToManyAssociationImpl association = null;
                association = xml.get("association", OneToManyAssociationImpl.class);
                obj.put(key, association);
            }
        }
    };

    @SuppressWarnings("rawtypes")
    protected XMLFormat<?> getFormat(Class forClass) throws XMLStreamException {
        if (AssociationMap.class.equals(forClass)) {
            if (isBranched) {
                return ASSOCIATION_MAP_ONE_TO_ONE;
            }
            return ASSOCIATION_MAP_ONE_TO_MANY;
        }
        return super.getFormat(forClass);
    }
}
