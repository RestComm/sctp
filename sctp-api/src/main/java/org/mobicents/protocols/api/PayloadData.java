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
package org.mobicents.protocols.api;

import org.mobicents.commons.HexTools;

/**
 * The actual pay load data received or to be sent from/to underlying socket
 * 
 * @author amit bhayani
 * 
 */
public class PayloadData {
	private final int dataLength;
	private final byte[] data;
	private final boolean complete;
	private final boolean unordered;
	private final int payloadProtocolId;
	private final int streamNumber;

	/**
	 * @param dataLength
	 *            Length of byte[] data
	 * @param data
	 *            the payload data
	 * @param complete
	 *            if this data represents complete protocol data
	 * @param unordered
	 *            set to true if we don't care for oder
	 * @param payloadProtocolId
	 *            protocol ID of the data carried
	 * @param streamNumber
	 *            the SCTP stream number
	 */
	public PayloadData(int dataLength, byte[] data, boolean complete, boolean unordered, int payloadProtocolId, int streamNumber) {
		super();
		this.dataLength = dataLength;
		this.data = data;
		this.complete = complete;
		this.unordered = unordered;
		this.payloadProtocolId = payloadProtocolId;
		this.streamNumber = streamNumber;
	}

	/**
	 * @return the dataLength
	 */
	public int getDataLength() {
		return dataLength;
	}

	/**
	 * @return the data
	 */
	public byte[] getData() {
		return data;
	}

	/**
	 * @return the complete
	 */
	public boolean isComplete() {
		return complete;
	}

	/**
	 * @return the unordered
	 */
	public boolean isUnordered() {
		return unordered;
	}

	/**
	 * @return the payloadProtocolId
	 */
	public int getPayloadProtocolId() {
		return payloadProtocolId;
	}

	/**
	 * <p>
	 * This is SCTP Stream sequence identifier.
	 * </p>
	 * <p>
	 * While sending PayloadData to SCTP Association, this value should be set
	 * by SCTP user. If value greater than or equal to maxOutboundStreams or
	 * lesser than 0 is used, packet will be dropped and error message will be
	 * logged
	 * </p>
	 * </p> While PayloadData is received from underlying SCTP socket, this
	 * value indicates stream identifier on which data was received. Its
	 * guaranteed that this value will be greater than 0 and less than
	 * maxInboundStreams
	 * <p>
	 * 
	 * @return the streamNumber
	 */
	public int getStreamNumber() {
		return streamNumber;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append("PayloadData [dataLength=").append(dataLength).append(", complete=").append(complete).append(", unordered=").append(unordered)
				.append(", payloadProtocolId=").append(payloadProtocolId).append(", streamNumber=").append(streamNumber).append(", data=\n")
				.append(HexTools.dump(data, 0)).append("]");
		return sb.toString();
	}

}
