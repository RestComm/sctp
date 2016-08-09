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
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;

import javolution.util.FastList;

import org.apache.log4j.Logger;

/**
 * This class controls the nio sockets and manages the I/O operations.
 * 
 * @author amit bhayani
 * @author balogh.gabo@alerant.hu
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
     * @param started the started to set
     */
    protected void setStarted(boolean started) {
        this.started = started;
    }

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

                        SelectionKey key = change.getSocketChannel() == null ? null
                                : change.getSocketChannel().keyFor(this.selector);
                        if (logger.isDebugEnabled()) {
                            if (key != null && key.isValid()) {
                                logger.debug(
                                        "change=" + change + ": key=" + key + " of socketChannel=" + change.getSocketChannel()
                                                + " for selector=" + this.selector + " key interesOps=" + key.interestOps());
                            }
                        }
                        switch (change.getType()) {
                            case MultiChangeRequest.CHANGEOPS:
                                pendingChanges.remove(change);
                                if (key == null) {
                                    logger.warn("change=" + change + ": key is null",
                                            new NullPointerException("Selection key is null"));
                                } else if (!key.isValid()) {
                                    logger.warn("change=" + change + ": key=" + key + " key is invalid",
                                            new InternalError("Selection key is invalid"));
                                } else {
                                    key.interestOps(change.getOps());
                                }
                                break;
                            case MultiChangeRequest.ADD_OPS:
                                pendingChanges.remove(change);
                                if (key == null) {
                                    logger.warn("change=" + change + ": key is null",
                                            new NullPointerException("Selection key is null"));
                                } else if (!key.isValid()) {
                                    logger.warn("change=" + change + ": key=" + key + " key is invalid",
                                            new InternalError("Selection key is invalid"));
                                } else {
                                    key.interestOps(key.interestOps() | change.getOps());
                                }
                                break;
                            case MultiChangeRequest.REGISTER:
                                pendingChanges.remove(change);

                                SelectionKey key1 = change.getSocketChannel().register(this.selector, change.getOps());

                                if (change.isMultiAssocRequest()) {
                                    key1.attach(change.getAssocMultiplexer());
                                    if (logger.isDebugEnabled()) {
                                        logger.debug("Key=" + key1 + "is registered to channel=" + change.getSocketChannel()
                                                + " of the association=" + change.getAssocMultiplexer());
                                    }
                                } else {
                                    key1.attach(change.getAssociation());
                                    if (logger.isDebugEnabled()) {
                                        logger.debug("Key=" + key1 + "is registered to channel=" + change.getSocketChannel()
                                                + " of the association=" + change.getAssociation());
                                    }
                                }
                                break;
                            case MultiChangeRequest.CONNECT:
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
                                break;
                        }
                    }
                }

                // Wait for an event one of the registered channels
                this.selector.select(500);

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
            } catch (CancelledKeyException cke) {
                // having this exception when closing a channel can be normal, but we log it on WARN level
                logger.warn("Selecting a cancelled ready key: " + cke.getMessage());
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

}
