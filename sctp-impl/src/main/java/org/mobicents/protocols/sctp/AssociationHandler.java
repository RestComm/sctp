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

import org.apache.log4j.Logger;
import org.apache.log4j.Priority;

import com.sun.nio.sctp.AbstractNotificationHandler;
import com.sun.nio.sctp.AssociationChangeNotification;
import com.sun.nio.sctp.HandlerResult;
import com.sun.nio.sctp.PeerAddressChangeNotification;
import com.sun.nio.sctp.SendFailedNotification;
import com.sun.nio.sctp.ShutdownNotification;

/**
 * // TODO Override all methods? // TODO Add Association name for logging
 * 
 * @author amit bhayani
 * 
 */
class AssociationHandler extends AbstractNotificationHandler<AssociationImpl> {

	private static final Logger logger = Logger.getLogger(AssociationHandler.class);
	
	//Default value is 1 for TCP
	private volatile int maxInboundStreams = 1;
	private volatile int maxOutboundStreams = 1;

	/**
	 * @param asscoitaion
	 */
	public AssociationHandler() {

	}

	/**
	 * @return the maxInboundStreams
	 */
	public int getMaxInboundStreams() {
		return maxInboundStreams;
	}

	/**
	 * @return the maxOutboundStreams
	 */
	public int getMaxOutboundStreams() {
		return maxOutboundStreams;
	}

	@Override
	public HandlerResult handleNotification(AssociationChangeNotification not, AssociationImpl associtaion) {
		switch (not.event()) {
		case COMM_UP:
			if (not.association() != null) {
				this.maxOutboundStreams = not.association().maxOutboundStreams();
				this.maxInboundStreams = not.association().maxInboundStreams();
			}

			if (logger.isInfoEnabled()) {
				logger.info(String.format("New association setup for Association=%s with %d outbound streams, and %d inbound streams.\n",
						associtaion.getName(), this.maxOutboundStreams, this.maxInboundStreams));
			}

			associtaion.createworkerThreadTable(Math.max(this.maxInboundStreams, this.maxOutboundStreams));

			// TODO assign Thread's ?
			try {
				associtaion.markAssociationUp();
				associtaion.getAssociationListener().onCommunicationUp(associtaion, this.maxInboundStreams, this.maxOutboundStreams);
			} catch (Exception e) {
				logger.error(String.format("Exception while calling onCommunicationUp on AssociationListener for Association=%s", associtaion.getName()), e);
			}
			return HandlerResult.CONTINUE;

		case CANT_START:
			logger.error(String.format("Can't start for Association=%s", associtaion.getName()));
			return HandlerResult.CONTINUE;
		case COMM_LOST:
			logger.warn(String.format("Communication lost for Association=%s", associtaion.getName()));

			// Close the Socket
			associtaion.close();

			associtaion.scheduleConnect();
			try {
				associtaion.markAssociationDown();
				associtaion.getAssociationListener().onCommunicationLost(associtaion);
			} catch (Exception e) {
				logger.error(String.format("Exception while calling onCommunicationLost on AssociationListener for Association=%s", associtaion.getName()), e);
			}
			return HandlerResult.RETURN;
		case RESTART:
			logger.warn(String.format("Restart for Association=%s", associtaion.getName()));
			try {
				associtaion.getAssociationListener().onCommunicationRestart(associtaion);
			} catch (Exception e) {
				logger.error(String.format("Exception while calling onCommunicationRestart on AssociationListener for Association=%s", associtaion.getName()),
						e);
			}
			return HandlerResult.CONTINUE;
		case SHUTDOWN:
			if (logger.isInfoEnabled()) {
				logger.info(String.format("Shutdown for Association=%s", associtaion.getName()));
			}
			try {
				associtaion.markAssociationDown();
				associtaion.getAssociationListener().onCommunicationShutdown(associtaion);
			} catch (Exception e) {
				logger.error(String.format("Exception while calling onCommunicationShutdown on AssociationListener for Association=%s", associtaion.getName()),
						e);
			}
			return HandlerResult.RETURN;
		default:
			logger.warn(String.format("Received unkown Event=%s for Association=%s", not.event(), associtaion.getName()));
			break;
		}

		return HandlerResult.CONTINUE;
	}

	@Override
	public HandlerResult handleNotification(ShutdownNotification not, AssociationImpl associtaion) {
		if (logger.isInfoEnabled()) {
			logger.info(String.format("Association=%s SHUTDOWN", associtaion.getName()));
		}

		// TODO assign Thread's ?

		try {
			associtaion.markAssociationDown();
			associtaion.getAssociationListener().onCommunicationShutdown(associtaion);
		} catch (Exception e) {
			logger.error(String.format("Exception while calling onCommunicationShutdown on AssociationListener for Association=%s", associtaion.getName()), e);
		}

		return HandlerResult.RETURN;
	}

	@Override
	public HandlerResult handleNotification(SendFailedNotification notification, AssociationImpl associtaion) {
//        logger.error(String.format("Association=%s SendFailedNotification", associtaion.getName()));
        logger.error(String.format("Association=" + associtaion.getName() + " SendFailedNotification, errorCode=" + notification.errorCode()));
		return HandlerResult.RETURN;
	}

	@Override
	public  HandlerResult handleNotification(PeerAddressChangeNotification notification, AssociationImpl associtaion) {
		//associtaion.peerSocketAddress = notification.address();
		if(logger.isEnabledFor(Priority.WARN)){
			logger.warn(String.format("Peer Address changed to=%s for Association=%s", notification.address(), associtaion.getName()));
		}
		return HandlerResult.CONTINUE;
	}
}
