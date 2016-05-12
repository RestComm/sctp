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

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;

import java.net.InetSocketAddress;

import javolution.util.FastMap;

import org.apache.log4j.Logger;
import org.mobicents.protocols.api.Association;
import org.mobicents.protocols.api.AssociationType;
import org.mobicents.protocols.api.IpChannelType;

import com.sun.nio.sctp.SctpChannel;
import com.sun.nio.sctp.SctpStandardSocketOptions;

/**
 * @author <a href="mailto:amit.bhayani@telestax.com">Amit Bhayani</a>
 * 
 */
public class NettySctpServerHandler extends NettySctpChannelInboundHandlerAdapter {

    Logger logger = Logger.getLogger(NettySctpServerHandler.class);

    private final NettyServerImpl serverImpl;
    private final NettySctpManagementImpl managementImpl;

    /**
     * 
     */
    public NettySctpServerHandler(NettyServerImpl serverImpl, NettySctpManagementImpl managementImpl) {
        this.serverImpl = serverImpl;
        this.managementImpl = managementImpl;
    }

    @Override
    public void channelUnregistered(final ChannelHandlerContext ctx) throws Exception {
        logger.warn(String.format("ChannelUnregistered event: association=%s", association));
        if (association != null) {
            this.association.setChannelHandler(null);
        }
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        if (logger.isDebugEnabled()) {
            logger.debug(String.format("channelRegistered event: association=%s", this.association));
        }

        Channel channel = ctx.channel();
        InetSocketAddress sockAdd = ((InetSocketAddress) channel.remoteAddress());
        String host = sockAdd.getAddress().getHostAddress();
        int port = sockAdd.getPort();

        boolean provisioned = false;

        if (logger.isDebugEnabled()) {
            logger.debug(String.format("Received connect request from peer host=%s port=%d", host, port));
        }

        // Iterate through all corresponding associate to
        // check if incoming connection request matches with any provisioned
        // ip:port
        FastMap<String, Association> associations = this.managementImpl.associations;
        for (FastMap.Entry<String, Association> n = associations.head(), end = associations.tail(); (n = n.getNext()) != end
                && !provisioned;) {
            NettyAssociationImpl association = (NettyAssociationImpl) n.getValue();

            // check if an association binds to the found server
            if (serverImpl.getName().equals(association.getServerName())
                    && association.getAssociationType() == AssociationType.SERVER) {
                // compare port and ip of remote with provisioned
                if ((port == association.getPeerPort()) && (host.equals(association.getPeerAddress()))) {
                    provisioned = true;

                    if (!association.isStarted()) {
                        logger.error(String.format(
                                "Received connect request for Association=%s but not started yet. Droping the connection!",
                                association.getName()));
                        channel.close();
                        return;
                    }

                    this.association = association;
                    this.channel = channel;
                    this.ctx = ctx;
                    this.association.setChannelHandler(this);

                    if (logger.isInfoEnabled()) {
                        logger.info(String.format("Connected %s", association));
                    }

                    if (association.getIpChannelType() == IpChannelType.TCP) {
                        this.association.markAssociationUp(1, 1);
                    }

                    break;
                }
            }
        }// for loop

        if (!provisioned && serverImpl.isAcceptAnonymousConnections() && this.managementImpl.getServerListener() != null) {
            // the server accepts anonymous connections

            // checking for limit of concurrent connections
            if (serverImpl.getMaxConcurrentConnectionsCount() > 0
                    && serverImpl.anonymAssociations.size() >= serverImpl.getMaxConcurrentConnectionsCount()) {
                logger.warn(String.format(
                        "Incoming anonymous connection is rejected because of too many active connections to Server=%s",
                        serverImpl));
                channel.close();
                return;
            }

            provisioned = true;

            NettyAssociationImpl anonymAssociation = new NettyAssociationImpl(host, port, serverImpl.getName(),
                    serverImpl.getIpChannelType(), serverImpl);
            anonymAssociation.setManagement(this.managementImpl);

            try {
                this.managementImpl.getServerListener().onNewRemoteConnection(serverImpl, anonymAssociation);
            } catch (Throwable e) {
                logger.warn(String.format("Exception when invoking ServerListener.onNewRemoteConnection() Ass=%s",
                        anonymAssociation), e);
                channel.close();
                return;
            }

            if (!anonymAssociation.isStarted()) {
                // connection is rejected
                logger.info(String.format("Rejected anonymous %s", anonymAssociation));
                channel.close();
                return;
            }

            this.association = anonymAssociation;
            this.channel = channel;
            this.ctx = ctx;
            this.association.setChannelHandler(this);

            if (logger.isInfoEnabled()) {
                logger.info(String.format("Accepted anonymous %s", anonymAssociation));
            }

            if (association.getIpChannelType() == IpChannelType.TCP) {
                this.association.markAssociationUp(1, 1);
            }
        }

        if (!provisioned) {
            // There is no corresponding Associate provisioned. Lets close the
            // channel here
            logger.warn(String.format("Received connect request from non provisioned %s:%d address. Closing Channel", host,
                    port));
            ctx.close();
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        if (logger.isDebugEnabled()) {
            logger.debug("channelActive event: association=" + this.association);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // Close the connection when an exception is raised.
        logger.error("ExceptionCaught for Associtaion: " + this.association.getName() + "\n", cause);
        ctx.close();
    }

}
