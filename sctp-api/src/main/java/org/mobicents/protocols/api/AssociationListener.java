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

/**
 * <p>
 * The listener interface for receiving the underlying socket status and
 * received payload from peer. The class that is interested in receiving data
 * must implement this interface, and the object created with that class is
 * registered with {@link Association}
 * </p>
 * 
 * @author amit bhayani
 * 
 */
public interface AssociationListener {

	/**
	 * Invoked when underlying socket is open and connection is established with
	 * peer. This is expected behavior when management start's the
	 * {@link Association}
	 * 
	 * @param association
	 * @param maxInboundStreams
	 *            Returns the maximum number of inbound streams that this
	 *            association supports. Data received on this association will
	 *            be on stream number s, where 0 <= s < maxInboundStreams(). For
	 *            TCP socket this value is always 1
	 * @param maxOutboundStreams
	 *            Returns the maximum number of outbound streams that this
	 *            association supports. Data sent on this association must be on
	 *            stream number s, where 0 <= s < maxOutboundStreams(). For TCP
	 *            socket this value is always 1
	 */
	public void onCommunicationUp(Association association, int maxInboundStreams, int maxOutboundStreams);

	/**
	 * Invoked when underlying socket is shutdown and connection is ended with
	 * peer. This is expected behavior when management stop's the
	 * {@link Association}
	 * 
	 * @param association
	 */
	public void onCommunicationShutdown(Association association);

	/**
	 * Invoked when underlying socket lost the connection with peer due to any
	 * reason like network between peer's died etc. This is unexpected behavior
	 * and the underlying {@link Association} should try to re-establish the
	 * connection
	 * 
	 * @param association
	 */
	public void onCommunicationLost(Association association);

	/**
	 * Invoked when the connection with the peer re-started. This is specific to
	 * SCTP protocol
	 * 
	 * @param association
	 */
	public void onCommunicationRestart(Association association);

	/**
	 * Invoked when the {@link PayloadData} is received from peer
	 * 
	 * @param association
	 * @param payloadData
	 */
	public void onPayload(Association association, PayloadData payloadData);

	/**
	 * <p>
	 * The stream id set in outgoing {@link PayloadData} is invalid. This packe
	 * will be dropped after calling the listener.
	 * </p>
	 * <p>
	 * This callback is on same Thread as {@link SelectorThread}. Do not delay
	 * the process here as it will hold all other IO.
	 * </p>
	 * 
	 * @param payloadData
	 */
	public void inValidStreamId(PayloadData payloadData);

}
