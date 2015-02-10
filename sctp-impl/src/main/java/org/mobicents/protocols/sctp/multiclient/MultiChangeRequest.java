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

	private int type;
	private int ops;
	private AbstractSelectableChannel socketChannel;
	private OneToManyAssocMultiplexer assocMultiplexer;

	private long executionTime;

	protected MultiChangeRequest(AbstractSelectableChannel socketChannel, OneToManyAssocMultiplexer assocMultiplexer, int type, int ops) {
		this.type = type;
		this.ops = ops;
		this.socketChannel = socketChannel;
		this.assocMultiplexer = assocMultiplexer;
		if (socketChannel == null && assocMultiplexer != null) {
			this.socketChannel = assocMultiplexer.getSocketMultiChannel();
		}
	}

	protected MultiChangeRequest(OneToManyAssocMultiplexer assocMultiplexer, int type, long executionTime) {
		this(null, assocMultiplexer, type, -1);
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
	 * @return the association
	 */
	protected OneToManyAssocMultiplexer getAssocMultiplexer() {
		return assocMultiplexer;
	}

	/**
	 * @return the executionTime
	 */
	protected long getExecutionTime() {
		return executionTime;
	}
}
