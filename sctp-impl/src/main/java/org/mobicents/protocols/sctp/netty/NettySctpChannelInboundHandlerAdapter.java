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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import org.apache.log4j.Logger;
import org.apache.log4j.Priority;

import sun.nio.ch.SctpAssocChange;

import com.sun.nio.sctp.AssociationChangeNotification;
import com.sun.nio.sctp.PeerAddressChangeNotification;
import com.sun.nio.sctp.SendFailedNotification;
import com.sun.nio.sctp.ShutdownNotification;

/**
 * @author <a href="mailto:amit.bhayani@telestax.com">Amit Bhayani</a>
 * 
 */
public class NettySctpChannelInboundHandlerAdapter extends ChannelInboundHandlerAdapter {

    Logger logger = Logger.getLogger(NettySctpChannelInboundHandlerAdapter.class);

    // Default value is 1 for TCP
    private volatile int maxInboundStreams = 1;
    private volatile int maxOutboundStreams = 1;

    protected NettyAssociationImpl association = null;

    /**
     * 
     */
    public NettySctpChannelInboundHandlerAdapter() {
        // TODO Auto-generated constructor stub
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {

        if (evt instanceof AssociationChangeNotification) {
            SctpAssocChange not = (SctpAssocChange) evt;
            switch (not.event()) {
                case COMM_UP:
                    if (not.association() != null) {
                        this.maxOutboundStreams = not.association().maxOutboundStreams();
                        this.maxInboundStreams = not.association().maxInboundStreams();
                    }

                    if (logger.isInfoEnabled()) {
                        logger.info(String.format(
                                "New association setup for Association=%s with %d outbound streams, and %d inbound streams.\n",
                                association.getName(), this.maxOutboundStreams, this.maxInboundStreams));
                    }

                    try {
                        this.association.markAssociationUp();
                        this.association.getAssociationListener().onCommunicationUp(association, this.maxInboundStreams,
                                this.maxOutboundStreams);
                    } catch (Exception e) {
                        logger.error(String.format(
                                "Exception while calling onCommunicationUp on AssociationListener for Association=%s",
                                association.getName()), e);
                    }
                    break;
                case CANT_START:
                    logger.error(String.format("Can't start for Association=%s", association.getName()));
                    break;
                case COMM_LOST:
                    logger.warn(String.format("Communication lost for Association=%s", association.getName()));

                    // Close the Socket
                    association.close();
                    association.scheduleConnect();
                    break;
                case RESTART:
                    logger.warn(String.format("Restart for Association=%s", association.getName()));
                    try {
                        association.getAssociationListener().onCommunicationRestart(association);
                    } catch (Exception e) {
                        logger.error(String.format(
                                "Exception while calling onCommunicationRestart on AssociationListener for Association=%s",
                                association.getName()), e);
                    }
                    break;
                case SHUTDOWN:
                    if (logger.isInfoEnabled()) {
                        logger.info(String.format("Shutdown for Association=%s", association.getName()));
                    }
                    try {
                        association.markAssociationDown();
                    } catch (Exception e) {
                        logger.error(String.format(
                                "Exception while calling onCommunicationShutdown on AssociationListener for Association=%s",
                                association.getName()), e);
                    }
                    break;
                default:
                    logger.warn(String.format("Received unkown Event=%s for Association=%s", not.event(), association.getName()));
                    break;
            }

        } else if (evt instanceof PeerAddressChangeNotification) {
            PeerAddressChangeNotification notification = (PeerAddressChangeNotification) evt;

            if (logger.isEnabledFor(Priority.WARN)) {
                logger.warn(String.format("Peer Address changed to=%s for Association=%s", notification.address(),
                        association.getName()));
            }

        } else if (evt instanceof SendFailedNotification) {
            SendFailedNotification notification = (SendFailedNotification) evt;
            logger.error(String.format("Association=" + association.getName() + " SendFailedNotification, errorCode="
                    + notification.errorCode()));

        } else if (evt instanceof ShutdownNotification) {
            ShutdownNotification notification = (ShutdownNotification) evt;

            if (logger.isInfoEnabled()) {
                logger.info(String.format("Association=%s SHUTDOWN", association.getName()));
            }

            // TODO assign Thread's ?

            try {
                association.markAssociationDown();
                association.getAssociationListener().onCommunicationShutdown(association);
            } catch (Exception e) {
                logger.error(String.format(
                        "Exception while calling onCommunicationShutdown on AssociationListener for Association=%s",
                        association.getName()), e);
            }
        }// if (evt instanceof AssociationChangeNotification)

    }

}
