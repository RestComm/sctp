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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.sctp.SctpMessage;

import java.net.InetSocketAddress;

import javolution.util.FastMap;

import org.apache.log4j.Logger;
import org.mobicents.protocols.api.Association;
import org.mobicents.protocols.api.AssociationType;
import org.mobicents.protocols.api.PayloadData;

/**
 * @author <a href="mailto:amit.bhayani@telestax.com">Amit Bhayani</a>
 * 
 */
public class NettySctpServerHandler extends NettySctpChannelInboundHandlerAdapter {

    Logger logger = Logger.getLogger(NettySctpServerHandler.class);

    private final NettyServerImpl serverImpl;
    private final NettySctpManagementImpl managementImpl;

    private Channel channel = null;
    private ChannelHandlerContext ctx = null;

    /**
     * 
     */
    public NettySctpServerHandler(NettyServerImpl serverImpl, NettySctpManagementImpl managementImpl) {
        this.serverImpl = serverImpl;
        this.managementImpl = managementImpl;
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        Channel channel = ctx.channel();
        InetSocketAddress sockAdd = ((InetSocketAddress) channel.remoteAddress());
        String host = sockAdd.getAddress().getHostAddress();
        int port = sockAdd.getPort();

        boolean provisioned = false;

        if (logger.isDebugEnabled()) {
            logger.debug("Received connect from peer host=" + host + " port=" + port);
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
                                "Received connect request for Association=%s but not started yet. Droping the connection! ",
                                association.getName()));
                        ChannelFuture channelFuture = channel.close();
                        break;
                    }

                    this.association = association;
                    this.channel = channel;
                    this.ctx = ctx;
                    this.association.setSctpServerHandler(this);
                    // ((AssociationImpl) association).setSocketChannel(socketChannel);

                    if (logger.isInfoEnabled()) {
                        logger.info(String.format("Connected %s", association));
                    }

                    // if (association.getIpChannelType() == IpChannelType.TCP) {
                    // AssocChangeEvent ace = AssocChangeEvent.COMM_UP;
                    // AssociationChangeNotification2 acn = new AssociationChangeNotification2(ace);
                    // association.associationHandler.handleNotification(acn, association);
                    // }

                    break;
                }
            }
        }// for loop

        // lets try to close the Channel
        // ChannelFuture channelFuture = channel.close();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        SctpMessage sctpMessage = (SctpMessage) msg;
        //MessageInfo messageInfo = sctpMessage.messageInfo();
        ByteBuf byteBuf = sctpMessage.content();

        byte[] array = new byte[byteBuf.readableBytes()];
        byteBuf.getBytes(0, array);
        

        PayloadData payload = new PayloadData(array.length, array, sctpMessage.isComplete(), sctpMessage.isUnordered(),
                sctpMessage.protocolIdentifier(), sctpMessage.streamIdentifier());

        this.association.read(payload);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // Close the connection when an exception is raised.
        logger.error(String.format("ExceptionCaught for Associtaion %s", this.association.getName()), cause);
        ctx.close();
    }

    protected void send(PayloadData payloadData) {
        byte[] data = payloadData.getData();
        // final ByteBuf byteBuf = ctx.alloc().buffer(data.length);
        final ByteBuf byteBuf = Unpooled.wrappedBuffer(data);
        SctpMessage sctpMessage = new SctpMessage(payloadData.getPayloadProtocolId(), payloadData.getStreamNumber(),
                payloadData.isUnordered(), byteBuf);

        try {
            this.channel.writeAndFlush(sctpMessage).sync();
        } catch (InterruptedException e) {
            logger.error(String.format("Sending of payload failed for Associtaion %s", this.association.getName()), e);
        }

    }

    protected void closeChannel() {
        if (this.channel != null) {
            try {
                channel.close().sync();
            } catch (InterruptedException e) {
                logger.error(
                        String.format("Error while trying to close Channel for Associtaion %s", this.association.getName()), e);
            }
        }
    }

}
