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

	protected static final XMLFormat<AssociationMap<String, OneToManyAssociationImpl>> ASSOCIATION_MAP = new XMLFormat<AssociationMap<String, OneToManyAssociationImpl>>(null) {

		@Override
		public void write(AssociationMap<String, OneToManyAssociationImpl> obj, javolution.xml.XMLFormat.OutputElement xml) throws XMLStreamException {
			for (Entry<String, OneToManyAssociationImpl> entry:  obj.entrySet()) {				
				xml.add((String) entry.getKey(), "name", String.class);
				xml.add((OneToManyAssociationImpl) entry.getValue(), "association", OneToManyAssociationImpl.class);
			}
		}

		@Override
		public void read(javolution.xml.XMLFormat.InputElement xml, AssociationMap<String, OneToManyAssociationImpl> obj) throws XMLStreamException {
			while (xml.hasNext()) {
				String key = xml.get("name", String.class);
				OneToManyAssociationImpl association = xml.get("association", OneToManyAssociationImpl.class);
				obj.put(key, association);
			}
		}
	};
	
	
	@SuppressWarnings("rawtypes")
	protected XMLFormat<?> getFormat( Class forClass) throws XMLStreamException {
		if (AssociationMap.class.equals(forClass)) {
			return ASSOCIATION_MAP;
		}
		return super.getFormat(forClass);
	}
}
