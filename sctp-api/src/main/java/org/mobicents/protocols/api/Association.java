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
 * A protocol relationship between endpoints
 * </p>
 * <p>
 * The implementation of this interface is actual wrapper over Socket that
 * know's how to communicate with peer. The user of Association shouldn't care
 * if the underlying Socket is client or server side
 * </p>
 * <p>
 * 
 * </p>
 * 
 * @author amit bhayani
 * 
 */
public interface Association {

	/**
	 * Return the Association channel type TCP or SCTP 
	 * 
	 * @return
	 */
	public IpChannelType getIpChannelType();
	
	/**
	 * Each association has unique name
	 * 
	 * @return name of association
	 */
	public String getName();

	/**
	 * If this association is started by management
	 * 
	 * @return
	 */
	public boolean isStarted();

	/**
	 * The AssociationListener set for this Association
	 * 
	 * @return
	 */
	public AssociationListener getAssociationListener();

	/**
	 * The {@link AssociationListener} to be registered for this Association
	 * 
	 * @param associationListener
	 */
	public void setAssociationListener(AssociationListener associationListener);

	/**
	 * The host address that underlying socket is bound to
	 * 
	 * @return
	 */
	public String getHostAddress();

	/**
	 * The host port that underlying socket is bound to
	 * 
	 * @return
	 */
	public int getHostPort();

	/**
	 * The peer address that the underlying socket connects to
	 * 
	 * @return
	 */
	public String getPeerAddress();

	/**
	 * The peer port that the underlying socket is connected to
	 * 
	 * @return
	 */
	public int getPeerPort();

	/**
	 * Server name if the association is for {@link Server}
	 * 
	 * @return
	 */
	public String getServerName();

	/**
	 * Send the {@link PayloadData} to the peer
	 * 
	 * @param payloadData
	 * @throws Exception
	 */
	public void send(PayloadData payloadData) throws Exception;

}
