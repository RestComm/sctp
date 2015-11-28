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

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.sctp.SctpChannel;
import io.netty.channel.sctp.SctpChannelOption;
import io.netty.channel.sctp.nio.NioSctpChannel;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;

import javolution.xml.XMLFormat;
import javolution.xml.stream.XMLStreamException;

import org.apache.log4j.Logger;
import org.mobicents.protocols.api.Association;
import org.mobicents.protocols.api.AssociationListener;
import org.mobicents.protocols.api.AssociationType;
import org.mobicents.protocols.api.IpChannelType;
import org.mobicents.protocols.api.ManagementEventListener;
import org.mobicents.protocols.api.PayloadData;

/**
 * @author <a href="mailto:amit.bhayani@telestax.com">Amit Bhayani</a>
 * 
 */
public class NettyAssociationImpl implements Association {

    protected static final Logger logger = Logger.getLogger(NettyAssociationImpl.class.getName());

    private static final String NAME = "name";
    private static final String SERVER_NAME = "serverName";
    private static final String HOST_ADDRESS = "hostAddress";
    private static final String HOST_PORT = "hostPort";

    private static final String PEER_ADDRESS = "peerAddress";
    private static final String PEER_PORT = "peerPort";

    private static final String ASSOCIATION_TYPE = "assoctype";
    private static final String IPCHANNEL_TYPE = "ipChannelType";
    private static final String EXTRA_HOST_ADDRESS = "extraHostAddress";
    private static final String EXTRA_HOST_ADDRESS_SIZE = "extraHostAddresseSize";

    private String hostAddress;
    private int hostPort;
    private String peerAddress;
    private int peerPort;
    private String serverName;
    private String name;
    private IpChannelType ipChannelType;
    private String[] extraHostAddresses;
    private NettyServerImpl server; // this is filled only for anonymous Associations

    private AssociationType type;

    private AssociationListener associationListener = null;

    private NettySctpManagementImpl management;

    // Is the Association been started by management?
    private volatile boolean started = false;
    // Is the Association up (connection is established)
    protected volatile boolean up = false;

    private NettySctpServerHandler sctpServerHandler;
    private NettySctpClientHandler sctpClientHandler;

    private EventLoopGroup group;
    private ChannelFuture cahnnelFuture;

    public NettyAssociationImpl() {
        super();
    }

    /**
     * Creating a CLIENT Association
     * 
     * @param hostAddress
     * @param hostPort
     * @param peerAddress
     * @param peerPort
     * @param assocName
     * @param ipChannelType
     * @param extraHostAddresses
     * @throws IOException
     */
    public NettyAssociationImpl(String hostAddress, int hostPort, String peerAddress, int peerPort, String assocName,
            IpChannelType ipChannelType, String[] extraHostAddresses) throws IOException {
        this();
        this.hostAddress = hostAddress;
        this.hostPort = hostPort;
        this.peerAddress = peerAddress;
        this.peerPort = peerPort;
        this.name = assocName;
        this.ipChannelType = ipChannelType;
        this.extraHostAddresses = extraHostAddresses;

        this.type = AssociationType.CLIENT;

    }

    /**
     * Creating a SERVER Association
     * 
     * @param peerAddress
     * @param peerPort
     * @param serverName
     * @param assocName
     * @param ipChannelType
     */
    public NettyAssociationImpl(String peerAddress, int peerPort, String serverName, String assocName,
            IpChannelType ipChannelType) {
        this();
        this.peerAddress = peerAddress;
        this.peerPort = peerPort;
        this.serverName = serverName;
        this.name = assocName;
        this.ipChannelType = ipChannelType;

        this.type = AssociationType.SERVER;

    }

    /**
     * Creating an ANONYMOUS_SERVER Association
     * 
     * @param hostAddress
     * @param hostPort
     * @param peerAddress
     * @param peerPort
     * @param serverName
     * @param assocName
     * @param ipChannelType
     */
    protected NettyAssociationImpl(String peerAddress, int peerPort, String serverName, IpChannelType ipChannelType,
            NettyServerImpl server) {
        this();
        this.peerAddress = peerAddress;
        this.peerPort = peerPort;
        this.serverName = serverName;
        this.ipChannelType = ipChannelType;
        this.server = server;

        this.type = AssociationType.ANONYMOUS_SERVER;

    }

    /*
     * (non-Javadoc)
     * 
     * @see org.mobicents.protocols.api.Association#getIpChannelType()
     */
    @Override
    public IpChannelType getIpChannelType() {
        return this.ipChannelType;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.mobicents.protocols.api.Association#getAssociationType()
     */
    @Override
    public AssociationType getAssociationType() {
        return this.type;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.mobicents.protocols.api.Association#getName()
     */
    @Override
    public String getName() {
        return this.name;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.mobicents.protocols.api.Association#isStarted()
     */
    @Override
    public boolean isStarted() {
        return this.started;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.mobicents.protocols.api.Association#isConnected()
     */
    @Override
    public boolean isConnected() {
        return started && up;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.mobicents.protocols.api.Association#isUp()
     */
    @Override
    public boolean isUp() {
        return up;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.mobicents.protocols.api.Association#getAssociationListener()
     */
    @Override
    public AssociationListener getAssociationListener() {
        return this.associationListener;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.mobicents.protocols.api.Association#setAssociationListener(org.mobicents.protocols.api.AssociationListener)
     */
    @Override
    public void setAssociationListener(AssociationListener associationListener) {
        this.associationListener = associationListener;

    }

    /*
     * (non-Javadoc)
     * 
     * @see org.mobicents.protocols.api.Association#getHostAddress()
     */
    @Override
    public String getHostAddress() {
        return hostAddress;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.mobicents.protocols.api.Association#getHostPort()
     */
    @Override
    public int getHostPort() {
        return hostPort;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.mobicents.protocols.api.Association#getPeerAddress()
     */
    @Override
    public String getPeerAddress() {
        return peerAddress;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.mobicents.protocols.api.Association#getPeerPort()
     */
    @Override
    public int getPeerPort() {
        return peerPort;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.mobicents.protocols.api.Association#getServerName()
     */
    @Override
    public String getServerName() {
        return serverName;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.mobicents.protocols.api.Association#getExtraHostAddresses()
     */
    @Override
    public String[] getExtraHostAddresses() {
        return extraHostAddresses;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.mobicents.protocols.api.Association#send(org.mobicents.protocols.api.PayloadData)
     */
    @Override
    public void send(PayloadData payloadData) throws Exception {
        if (this.type == AssociationType.SERVER) {
            this.sctpServerHandler.send(payloadData);
        } else {
            this.sctpClientHandler.send(payloadData);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.mobicents.protocols.api.Association#acceptAnonymousAssociation(org.mobicents.protocols.api.AssociationListener)
     */
    @Override
    public void acceptAnonymousAssociation(AssociationListener associationListener) throws Exception {
        this.associationListener = associationListener;

        if (this.getAssociationType() != AssociationType.ANONYMOUS_SERVER) {
            throw new UnsupportedOperationException(
                    "Association.acceptAnonymousAssociation() can be applied only for anonymous associations");
        }

        this.start();

    }

    /*
     * (non-Javadoc)
     * 
     * @see org.mobicents.protocols.api.Association#rejectAnonymousAssociation()
     */
    @Override
    public void rejectAnonymousAssociation() {
        // TODO Auto-generated method stub

    }

    /*
     * (non-Javadoc)
     * 
     * @see org.mobicents.protocols.api.Association#stopAnonymousAssociation()
     */
    @Override
    public void stopAnonymousAssociation() throws Exception {
        if (this.getAssociationType() != AssociationType.ANONYMOUS_SERVER) {
            throw new UnsupportedOperationException(
                    "Association.stopAnonymousAssociation() can be applied only for anonymous associations");
        }

        this.stop();

    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder();
        sb.append("Association [name=").append(this.name).append(", associationType=").append(this.type)
                .append(", ipChannelType=").append(this.ipChannelType).append(", hostAddress=").append(this.hostAddress)
                .append(", hostPort=").append(this.hostPort).append(", peerAddress=").append(this.peerAddress)
                .append(", peerPort=").append(this.peerPort).append(", serverName=").append(this.serverName);

        sb.append(", extraHostAddress=[");

        if (this.extraHostAddresses != null) {
            for (int i = 0; i < this.extraHostAddresses.length; i++) {
                String extraHostAddress = this.extraHostAddresses[i];
                sb.append(extraHostAddress);
                sb.append(", ");
            }
        }

        sb.append("]]");

        return sb.toString();
    }

    /**
     * @param management the management to set
     */
    protected void setManagement(NettySctpManagementImpl management) {
        this.management = management;
    }

    protected void start() throws Exception {
        if (this.associationListener == null) {
            throw new NullPointerException(String.format("AssociationListener is null for Associatoion=%s", this.name));
        }

        if (this.type == AssociationType.CLIENT) {
            this.scheduleConnect();
        }

        this.started = true;

        if (logger.isInfoEnabled()) {
            if (this.type != AssociationType.ANONYMOUS_SERVER) {
                logger.info(String.format("Started Association=%s", this));
            }
        }

        for (ManagementEventListener lstr : this.management.getManagementEventListeners()) {
            try {
                lstr.onAssociationStarted(this);
            } catch (Throwable ee) {
                logger.error("Exception while invoking onAssociationStarted", ee);
            }
        }
    }

    protected void stop() throws Exception {
        if (logger.isInfoEnabled()) {
            logger.info(String.format("Management requested to stop %s", this.toString()));
        }
        this.started = false;
        for (ManagementEventListener lstr : this.management.getManagementEventListeners()) {
            try {
                lstr.onAssociationStopped(this);
            } catch (Throwable ee) {
                logger.error("Exception while invoking onAssociationStopped", ee);
            }
        }

        try {
            this.markAssociationDown();
            this.associationListener.onCommunicationShutdown(this);
        } catch (Exception e) {
            logger.error(String.format(
                    "Exception while calling onCommunicationShutdown on AssociationListener for Association=%s", this.name), e);
        }

        if (this.type == AssociationType.SERVER) {
            if (this.sctpServerHandler != null) {
                this.sctpServerHandler.closeChannel();
            }
        } else {

            if (this.cahnnelFuture != null) {
                this.cahnnelFuture.channel().close().sync();
            }

            if (this.group != null) {
                this.group.shutdownGracefully();
            }
        }

    }

    protected void read(PayloadData payload) {
        try {
            this.associationListener.onPayload(this, payload);
        } catch (Exception e) {
            logger.error(String.format("Error while calling Listener for Association=%s.Payload=%s", this.name, payload), e);
        }
    }

    protected void markAssociationUp() {
        if (this.server != null) {
            synchronized (this.server.anonymAssociations) {
                this.server.anonymAssociations.add(this);
            }
        }

        this.up = true;
        for (ManagementEventListener lstr : this.management.getManagementEventListeners()) {
            try {
                lstr.onAssociationUp(this);
            } catch (Throwable ee) {
                logger.error("Exception while invoking onAssociationUp", ee);
            }
        }
    }

    protected void markAssociationDown() {
        if (this.up) {
            // To avoid calling Listener again and again
            this.up = false;
            for (ManagementEventListener lstr : this.management.getManagementEventListeners()) {
                try {
                    lstr.onAssociationDown(this);
                } catch (Throwable ee) {
                    logger.error("Exception while invoking onAssociationDown", ee);
                }
            }

            if (this.server != null) {
                synchronized (this.server.anonymAssociations) {
                    this.server.anonymAssociations.remove(this);
                }
            }
            
            this.getAssociationListener().onCommunicationShutdown(this);
        }
    }

    protected void close() {

        // channel.close();

        try {
            this.markAssociationDown();
        } catch (Exception e) {
            logger.error(String.format(
                    "Exception while calling onCommunicationShutdown on AssociationListener for Association=%s", this.name), e);
        }
    }

    protected void scheduleConnect() {
        if (this.getAssociationType() == AssociationType.CLIENT) {
            this.management.getNettyClientOpsThread().scheduleConnect(this);
        }
    }

    protected void setSctpServerHandler(NettySctpServerHandler sctpEchoServerHandler) {
        this.sctpServerHandler = sctpEchoServerHandler;
    }

    protected void setSctpClientHandler(NettySctpClientHandler sctpClientHandler) {
        this.sctpClientHandler = sctpClientHandler;
    }

    protected void connect() {
        try {
            this.group = new NioEventLoopGroup();
            Bootstrap b = new Bootstrap();

            NettySctpClientChannelInitializer nettySctpClientChannelInitializer = new NettySctpClientChannelInitializer(this);
            b.group(group).channel(NioSctpChannel.class).option(SctpChannelOption.SCTP_NODELAY, true)
                    .handler(nettySctpClientChannelInitializer);

            InetSocketAddress localAddress = new InetSocketAddress(this.hostAddress, this.hostPort);

            // Bind the client channel.
            ChannelFuture bindFuture = b.bind(localAddress).sync();

            // Get the underlying sctp channel
            SctpChannel channel = (SctpChannel) bindFuture.channel();

            // Bind the secondary address.
            // Please note that, bindAddress in the client channel should be done before connecting if you have not
            // enable Dynamic Address Configuration. See net.sctp.addip_enable kernel param
            if (this.extraHostAddresses != null) {
                for (int count = 0; count < this.extraHostAddresses.length; count++) {
                    String localSecondaryAddress = this.extraHostAddresses[count];
                    InetAddress localSecondaryInetAddress = InetAddress.getByName(localSecondaryAddress);

                    channel.bindAddress(localSecondaryInetAddress).sync();
                }
            }

            InetSocketAddress remoteAddress = new InetSocketAddress(this.peerAddress, this.peerPort);

            // Finish connect
            this.cahnnelFuture = channel.connect(remoteAddress).sync();

            System.out.println(this.getName() + " Client Channel at connect = " + this.cahnnelFuture.channel());
        } catch (Exception e) {
            logger.error(String.format("Exception while finishing connection for Association=%s", this.getName()), e);
            // TODO check if channel is up and close it?
            this.scheduleConnect();
        }
    }

    /**
     * XML Serialization/Deserialization
     */
    protected static final XMLFormat<NettyAssociationImpl> ASSOCIATION_XML = new XMLFormat<NettyAssociationImpl>(
            NettyAssociationImpl.class) {

        @SuppressWarnings("unchecked")
        @Override
        public void read(javolution.xml.XMLFormat.InputElement xml, NettyAssociationImpl association) throws XMLStreamException {
            association.name = xml.getAttribute(NAME, "");
            association.type = AssociationType.getAssociationType(xml.getAttribute(ASSOCIATION_TYPE, ""));
            association.hostAddress = xml.getAttribute(HOST_ADDRESS, "");
            association.hostPort = xml.getAttribute(HOST_PORT, 0);

            association.peerAddress = xml.getAttribute(PEER_ADDRESS, "");
            association.peerPort = xml.getAttribute(PEER_PORT, 0);

            association.serverName = xml.getAttribute(SERVER_NAME, "");
            association.ipChannelType = IpChannelType
                    .getInstance(xml.getAttribute(IPCHANNEL_TYPE, IpChannelType.SCTP.getCode()));
            if (association.ipChannelType == null)
                association.ipChannelType = IpChannelType.SCTP;

            int extraHostAddressesSize = xml.getAttribute(EXTRA_HOST_ADDRESS_SIZE, 0);
            association.extraHostAddresses = new String[extraHostAddressesSize];

            for (int i = 0; i < extraHostAddressesSize; i++) {
                association.extraHostAddresses[i] = xml.get(EXTRA_HOST_ADDRESS, String.class);
            }

        }

        @Override
        public void write(NettyAssociationImpl association, javolution.xml.XMLFormat.OutputElement xml)
                throws XMLStreamException {
            xml.setAttribute(NAME, association.name);
            xml.setAttribute(ASSOCIATION_TYPE, association.type.getType());
            xml.setAttribute(HOST_ADDRESS, association.hostAddress);
            xml.setAttribute(HOST_PORT, association.hostPort);

            xml.setAttribute(PEER_ADDRESS, association.peerAddress);
            xml.setAttribute(PEER_PORT, association.peerPort);

            xml.setAttribute(SERVER_NAME, association.serverName);
            xml.setAttribute(IPCHANNEL_TYPE, association.ipChannelType.getCode());

            xml.setAttribute(EXTRA_HOST_ADDRESS_SIZE,
                    association.extraHostAddresses != null ? association.extraHostAddresses.length : 0);
            if (association.extraHostAddresses != null) {
                for (String s : association.extraHostAddresses) {
                    xml.add(s, EXTRA_HOST_ADDRESS, String.class);
                }
            }
        }
    };
}
