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
package org.mobicents.protocols.sctp;

import java.util.Iterator;
import java.util.Map;

import javolution.xml.XMLBinding;
import javolution.xml.XMLFormat;
import javolution.xml.stream.XMLStreamException;

/**
 * @author amit bhayani
 * 
 */
public class SctpXMLBinding extends XMLBinding {

	protected static final XMLFormat<AssociationMap> ASSOCIATION_MAP = new XMLFormat<AssociationMap>(AssociationMap.class) {

		@Override
		public void write(AssociationMap obj, javolution.xml.XMLFormat.OutputElement xml) throws XMLStreamException {
			final Map map = (Map) obj;

			for (Iterator it = map.entrySet().iterator(); it.hasNext();) {
				Map.Entry entry = (Map.Entry) it.next();

				xml.add((String) entry.getKey(), "name", String.class);
				xml.add((AssociationImpl) entry.getValue(), "association", AssociationImpl.class);
			}
		}

		@Override
		public void read(javolution.xml.XMLFormat.InputElement xml, AssociationMap obj) throws XMLStreamException {
			while (xml.hasNext()) {
				String key = xml.get("name", String.class);
				AssociationImpl association = xml.get("association", AssociationImpl.class);
				obj.put(key, association);
			}
		}

	};

	protected XMLFormat getFormat(Class forClass) throws XMLStreamException {
		if (AssociationMap.class.equals(forClass)) {
			return ASSOCIATION_MAP;
		}
		return super.getFormat(forClass);
	}
}
