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

import com.sun.nio.sctp.AbstractNotificationHandler;
import com.sun.nio.sctp.AssociationChangeNotification;
import com.sun.nio.sctp.HandlerResult;
import com.sun.nio.sctp.ShutdownNotification;

/**
 * // TODO Override all methods?
 * 
 * @author amit bhayani
 * 
 */
class AssociationHandler extends AbstractNotificationHandler<Association> {

	private static final Logger logger = Logger.getLogger(AssociationHandler.class);

	/**
	 * @param asscoitaion
	 */
	public AssociationHandler() {

	}

	@Override
	public HandlerResult handleNotification(AssociationChangeNotification not, Association asscoitaion) {
		switch (not.event()) {
		case COMM_UP:
			int outbound = not.association().maxOutboundStreams();
			int inbound = not.association().maxInboundStreams();

			if (logger.isInfoEnabled()) {
				logger.info(String.format("New association setup for Association=%s with %d outbound streams, and %d inbound streams.\n",
						asscoitaion.getName(), outbound, inbound));
			}

			// Recreate SLS table. Minimum of two is correct?
			asscoitaion.createSLSTable(Math.min(inbound, outbound) - 1);

			asscoitaion.createworkerThreadTable(Math.max(inbound, outbound));

			// TODO assign Thread's ?
			asscoitaion.getAssociationListener().onCommunicationUp(asscoitaion);
			break;

		case CANT_START:
			// TODO
			break;
		case COMM_LOST:
			// TODO
			break;
		case RESTART:
			// TODO
			break;
		case SHUTDOWN:
			// TODO
			break;
		}

		return HandlerResult.CONTINUE;
	}

	@Override
	public HandlerResult handleNotification(ShutdownNotification not, Association asscoitaion) {
		if (logger.isInfoEnabled()) {
			logger.info(String.format("Association=%s SHUTDOWN", asscoitaion.getName()));
		}

		// TODO assign Thread's ?

		asscoitaion.getAssociationListener().onCommunicationShutdown(asscoitaion);

		return HandlerResult.RETURN;
	}
}
