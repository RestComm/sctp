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

import javolution.util.FastList;

import org.apache.log4j.Logger;
import org.mobicents.protocols.api.Association;

/**
 * @author <a href="mailto:amit.bhayani@telestax.com">Amit Bhayani</a>
 * 
 */
public class NettyClientOpsThread implements Runnable {

    private static final Logger logger = Logger.getLogger(NettyClientOpsThread.class);

    protected volatile boolean started = true;

    private FastList<ChangeRequest> pendingChanges = new FastList<ChangeRequest>();

    private final NettySctpManagementImpl management;

    private Object waitObject = new Object();

    /**
	 * 
	 */
    public NettyClientOpsThread(NettySctpManagementImpl management) {
        this.management = management;
    }

    /**
     * @param started the started to set
     */
    protected void setStarted(boolean started) {
        this.started = started;

        synchronized (this.waitObject) {
            this.waitObject.notify();
        }
    }

    protected void scheduleConnect(Association association) {
        synchronized (this.pendingChanges) {
            this.pendingChanges.add(new ChangeRequest(association, ChangeRequest.CONNECT, System.currentTimeMillis()
                    + this.management.getConnectDelay()));
        }

        synchronized (this.waitObject) {
            this.waitObject.notify();
        }

    }

    @Override
    public void run() {
        if (logger.isInfoEnabled()) {
            logger.info("NettyClientOpsThread started.");
        }

        while (this.started) {

            try {
                synchronized (this.pendingChanges) {
                    Iterator<ChangeRequest> changes = pendingChanges.iterator();
                    while (changes.hasNext()) {
                        ChangeRequest change = changes.next();
                        switch (change.getType()) {
                            case ChangeRequest.CONNECT:
                                if (!change.getAssociation().isStarted()) {
                                    pendingChanges.remove(change);
                                } else {
                                    if (change.getExecutionTime() <= System.currentTimeMillis()) {
                                        pendingChanges.remove(change);
                                        // TODO may be create abstract AssociationInpl that has conncet() method
                                        Association association = change.getAssociation();
                                        NettyAssociationImpl nettyAssociationImpl = (NettyAssociationImpl) association;
                                        nettyAssociationImpl.connect();
                                    }
                                }
                                break;

                        }// switch
                    }
                }

                synchronized (this.waitObject) {
                    this.waitObject.wait(5000);
                }

            } catch (InterruptedException e) {
                logger.error("Error while looping SmppClientOpsThread thread", e);
            }
        }// while

        if (logger.isInfoEnabled()) {
            logger.info("SmppClientOpsThread for stopped.");
        }
    }

}
