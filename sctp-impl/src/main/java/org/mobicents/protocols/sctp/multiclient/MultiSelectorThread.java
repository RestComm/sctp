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

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;

import javolution.util.FastList;

import org.apache.log4j.Logger;

import com.sun.nio.sctp.AssociationChangeNotification;
import com.sun.nio.sctp.AssociationChangeNotification.AssocChangeEvent;

/**
 * @author amit bhayani
 * @author alerant appngin
 * 
 */
public class MultiSelectorThread implements Runnable {

	protected static final Logger logger = Logger.getLogger(MultiSelectorThread.class);

	protected Selector selector;

	protected MultiManagementImpl management = null;

	protected volatile boolean started = true;

	/**
	 * Creates the MultiSelector instance for the given MultiManagementImpl (SCTP stack) and Selector
	 * 
	 * @param selector
	 * @param management
	 */
	protected MultiSelectorThread(Selector selector, MultiManagementImpl management) {
		super();
		this.selector = selector;
		this.management = management;
	}

	/**
	 * @param started
	 *            the started to set
	 */
	protected void setStarted(boolean started) {
		this.started = started;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run() {
		if (logger.isInfoEnabled()) {
			logger.info(String.format("SelectorThread for Management=%s started.", this.management.getName()));
		}
		while (this.started) {
			try {
				FastList<MultiChangeRequest> pendingChanges = this.management.getPendingChanges();

				// Process any pending changes
				synchronized (pendingChanges) {
					Iterator<MultiChangeRequest> changes = pendingChanges.iterator();
					while (changes.hasNext()) {
						MultiChangeRequest change = changes.next();
						SelectionKey key = change.getSocketChannel() == null ? null :  change.getSocketChannel().keyFor(this.selector);
						logger.debug("change=" + change + ": key=" + key + " of socketChannel=" + change.getSocketChannel() + " for selector=" + this.selector );
						switch (change.getType()) {
						case MultiChangeRequest.CHANGEOPS:
							pendingChanges.remove(change);							
							key.interestOps(change.getOps());
							break;
						case MultiChangeRequest.ADD_OPS  :
							pendingChanges.remove(change);								
							key.interestOps(key.interestOps() | change.getOps());
							break;
						case MultiChangeRequest.REGISTER:
							pendingChanges.remove(change);
							SelectionKey key1 = change.getSocketChannel().register(this.selector, change.getOps());
							AssocChangeEvent ace = AssocChangeEvent.COMM_UP;
							AssociationChangeNotification2 acn = new AssociationChangeNotification2(ace);
							if (change.isMultiAssocRequest()) {
								key1.attach(change.getAssocMultiplexer());
								change.getAssocMultiplexer().associationHandler.handleNotification(acn, change.getAssocMultiplexer());
							} else {
								key1.attach(change.getAssociation());
								//change.getOneToOneAssociation().associationHandler.handleNotification(acn, change.getOneToOneAssociation());							
							}
							break;
						case MultiChangeRequest.CONNECT:
							//in CONNECT request assocociation is filled in both OneToOne and OneToMany cases
							if (!change.getAssociation().isStarted()) {
								pendingChanges.remove(change);
							} else {
								if (change.getExecutionTime() <= System.currentTimeMillis()) {
									pendingChanges.remove(change);
									change.getAssociation().reconnect();
								}
							}
							break;
						case MultiChangeRequest.CLOSE:
							pendingChanges.remove(change);
							if (!change.isMultiAssocRequest()) {
								change.getAssociation().close();
							}
						}
					}
				}

				// Wait for an event one of the registered channels
				this.selector.select(500);

				//logger.debug("Done selecting, selected keys size: " + this.selector.selectedKeys().size());

				// Iterate over the set of keys for which events are available
				Iterator<SelectionKey> selectedKeys = this.selector.selectedKeys().iterator();

				while (selectedKeys.hasNext()) {
					SelectionKey key = selectedKeys.next();
					selectedKeys.remove();

					if (!key.isValid()) {
						continue;
					}

					// Check what event is available and deal with it
					if (key.isConnectable()) {
						logger.error("Illegal selectionKey state: connectable");
					} 
					if (key.isAcceptable()) {
						logger.error("Illegal selectionKey state: acceptable");
					} 
					if (key.isReadable()) {
						this.read(key);						
					} 
					if (key.isWritable()) {
						this.write(key);
					}
				}

			} catch (Exception e) {
				logger.error("Error while selecting the ready keys", e);
				e.printStackTrace();
			}
		}

		try {
			this.selector.close();
		} catch (IOException e) {
			logger.error(String.format("Error while closing Selector for SCTP Management=%s", this.management.getName()));
		}

		if (logger.isInfoEnabled()) {
			logger.info(String.format("SelectorThread for Management=%s stopped.", this.management.getName()));
		}
	}
/*
	private void finishConnection(SelectionKey key) throws IOException{		
		this.finishConnectionMultiSctp(key);		
	}
/*	
	private void finishConnectionMultiSctp(SelectionKey key) throws IOException {
		OneToManyAssociationImpl association = (OneToManyAssociationImpl) key.attachment();
		if (logger.isInfoEnabled()) {
			logger.info(String.format("Association=%s connected to=%s", association.getName(), "TODO"));
		}
		this.read(key);
		// Register an interest in writing on this channel
		key.interestOps(SelectionKey.OP_READ);
		

		AssocChangeEvent ace = AssocChangeEvent.COMM_UP;
		AssociationChangeNotification2 acn = new AssociationChangeNotification2(ace);
		association.associationHandler.handleNotification(acn, association);
	}
/*
	private void finishConnectionSctp(SelectionKey key) throws IOException {
		
		OneToManyAssociationImpl association = (OneToManyAssociationImpl) key.attachment();
		try {
			
			SctpChannel socketChannel = (SctpChannel) key.channel();

			if (socketChannel.isConnectionPending()) {

				// TODO Loop? Or may be sleep for while?
				while (socketChannel.isConnectionPending()) {
					socketChannel.finishConnect();
				}
			}

			if (logger.isInfoEnabled()) {
				logger.info(String.format("Association=%s connected to=%s", association.getName(), socketChannel.getRemoteAddresses()));
			}

			// Register an interest in writing on this channel
			key.interestOps(SelectionKey.OP_READ);
		} catch (Exception e) {
			logger.error(String.format("Exception while finishing connection for Association=%s", association.getName()), e);
			association.scheduleConnect();
		}
	}*/

	private void read(SelectionKey key) throws IOException {
		if (key.attachment() instanceof OneToManyAssocMultiplexer) {
			OneToManyAssocMultiplexer multiplexer = (OneToManyAssocMultiplexer) key.attachment();
			multiplexer.read();
		} else if (key.attachment() instanceof OneToOneAssociationImpl) {
			OneToOneAssociationImpl association = (OneToOneAssociationImpl) key.attachment();
			association.read();
		}
	}

	private void write(SelectionKey key) throws IOException {
		if (key.attachment() instanceof OneToManyAssocMultiplexer) {
			OneToManyAssocMultiplexer multiplexer = (OneToManyAssocMultiplexer) key.attachment();
			multiplexer.write(key);
		} else if (key.attachment() instanceof OneToOneAssociationImpl) {
			OneToOneAssociationImpl association = (OneToOneAssociationImpl) key.attachment();
			association.write(key);
		}	
	}

	class AssociationChangeNotification2 extends AssociationChangeNotification {
		
		private AssocChangeEvent assocChangeEvent;

		public AssociationChangeNotification2(AssocChangeEvent assocChangeEvent) {
			this.assocChangeEvent = assocChangeEvent;
		}

		@Override
		public com.sun.nio.sctp.Association association() {
			return null;
		}

		@Override
		public AssocChangeEvent event() {
			return this.assocChangeEvent;
		}
	}
}

