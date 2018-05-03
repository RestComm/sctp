/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2014, Telestax Inc and individual contributors
 * by the @authors tag.
 *
 * This program is free software: you can redistribute it and/or modify
 * under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 *
 */
package org.mobicents.protocols.sctp.netty;

import java.util.Iterator;
import java.util.Map;

import javolution.xml.XMLBinding;
import javolution.xml.XMLFormat;
import javolution.xml.stream.XMLStreamException;

/**
 * @author <a href="mailto:amit.bhayani@telestax.com">Amit Bhayani</a>
 * 
 */
public class NettySctpXMLBinding extends XMLBinding {

	protected static final XMLFormat<NettyAssociationMap> ASSOCIATION_MAP = new XMLFormat<NettyAssociationMap>(NettyAssociationMap.class) {

		@Override
		public void write(NettyAssociationMap obj, javolution.xml.XMLFormat.OutputElement xml) throws XMLStreamException {
			final Map map = (Map) obj;

			for (Iterator it = map.entrySet().iterator(); it.hasNext();) {
				Map.Entry entry = (Map.Entry) it.next();

				xml.add((String) entry.getKey(), "name", String.class);
				xml.add((NettyAssociationImpl) entry.getValue(), "association", NettyAssociationImpl.class);
			}
		}

		@Override
		public void read(javolution.xml.XMLFormat.InputElement xml, NettyAssociationMap obj) throws XMLStreamException {
			while (xml.hasNext()) {
				String key = xml.get("name", String.class);
				NettyAssociationImpl association = xml.get("association", NettyAssociationImpl.class);
				obj.put(key, association);
			}
		}

	};

	protected XMLFormat getFormat(Class forClass) throws XMLStreamException {
		if (NettyAssociationMap.class.equals(forClass)) {
			return ASSOCIATION_MAP;
		}
		return super.getFormat(forClass);
	}
}
