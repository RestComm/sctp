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

import java.nio.channels.spi.AbstractSelectableChannel;

/**
 * @author amit bhayani
 * @author alerant appngin
 * 
 */
public final class MultiChangeRequest {
	public static final int REGISTER = 1;
	public static final int CHANGEOPS = 2;
	public static final int CONNECT = 3;
	public static final int CLOSE = 4;
	public static final int ADD_OPS = 5;

	private final int type;
	private final int ops;
	private final AbstractSelectableChannel socketChannel;
	private final OneToManyAssocMultiplexer assocMultiplexer;
	private final OneToOneAssociationImpl  oneToOneAssoc;
	
	private final boolean multiAssocRequest;

	private long executionTime;

	protected MultiChangeRequest(AbstractSelectableChannel socketChannel, OneToManyAssocMultiplexer assocMultiplexer, OneToOneAssociationImpl oneToOneAssoc, int type, int ops) {
		if (assocMultiplexer != null && oneToOneAssoc != null) {
			throw new IllegalArgumentException("MultiChangeRequest can not be instatiated because of ambiougos arguments: both assocMultiplexer and oneToOneAssoc are specified!");
		}
		if (assocMultiplexer == null && oneToOneAssoc == null) {
			throw new IllegalArgumentException("MultiChangeRequest can not be instatiated because of ambiougos arguments: nor assocMultiplexer nor oneToOneAssocc are specified!");
		}
		this.type = type;
		this.ops = ops;
		
		if (assocMultiplexer != null) {
			this.assocMultiplexer = assocMultiplexer;
			this.oneToOneAssoc = null;
			this.multiAssocRequest = true;
			if (socketChannel == null) {
				this.socketChannel = assocMultiplexer.getSocketMultiChannel();
			} else {
				this.socketChannel = socketChannel;
			}
		} else {
			this.oneToOneAssoc = oneToOneAssoc;
			this.assocMultiplexer = null;
			this.multiAssocRequest = false;
			if (socketChannel == null) {
				this.socketChannel = oneToOneAssoc.getSocketChannel();
			} else {
				this.socketChannel = socketChannel;
			}
		}
	}

	protected MultiChangeRequest(OneToManyAssocMultiplexer assocMultiplexer, OneToOneAssociationImpl oneToOneAssoc, int type, long executionTime) {
		this(null, assocMultiplexer, oneToOneAssoc, type, -1);
		this.executionTime = executionTime;
	}

	/**
	 * @return the type
	 */
	protected int getType() {
		return type;
	}

	/**
	 * @return the ops
	 */
	protected int getOps() {
		return ops;
	}

	/**
	 * @return the socketChannel
	 */
	protected AbstractSelectableChannel getSocketChannel() {
		return socketChannel;
	}

	/**
	 * @return the one-to-many multiplexer instance
	 */
	protected OneToManyAssocMultiplexer getAssocMultiplexer() {
		return assocMultiplexer;
	}

	/**
	 * @return the  one-to-one association
	 */
	protected OneToOneAssociationImpl getOneToOneAssociation() {
		return oneToOneAssoc;
	}
	
	protected boolean isMultiAssocRequest() {
		return multiAssocRequest;
	}
	
	/**
	 * @return the executionTime
	 */
	protected long getExecutionTime() {
		return executionTime;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "MultiChangeRequest [type=" + type + ", ops=" + ops
				+ ", socketChannel=" + socketChannel + ", assocMultiplexer="
				+ assocMultiplexer + ", oneToOneAssoc=" + oneToOneAssoc
				+ ", multiAssocRequest=" + multiAssocRequest
				+ ", executionTime=" + executionTime + "]";
	}
	
	
}
