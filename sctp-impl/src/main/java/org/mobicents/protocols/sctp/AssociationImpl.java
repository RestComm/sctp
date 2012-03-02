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

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

import javolution.util.FastList;
import javolution.xml.XMLFormat;
import javolution.xml.stream.XMLStreamException;

import org.apache.log4j.Logger;
import org.mobicents.protocols.api.Association;
import org.mobicents.protocols.api.AssociationListener;
import org.mobicents.protocols.api.AssociationType;
import org.mobicents.protocols.api.IpChannelType;
import org.mobicents.protocols.api.PayloadData;

import com.sun.nio.sctp.MessageInfo;
import com.sun.nio.sctp.SctpChannel;

/**
 * @author amit bhayani
 * 
 */
public class AssociationImpl implements Association {

	protected static final Logger logger = Logger.getLogger(AssociationImpl.class.getName());

	private static final String NAME = "name";
	private static final String SERVER_NAME = "serverName";
	private static final String HOST_ADDRESS = "hostAddress";
	private static final String HOST_PORT = "hostPort";

	private static final String PEER_ADDRESS = "peerAddress";
	private static final String PEER_PORT = "peerPort";

	private static final String ASSOCIATION_TYPE = "assoctype";
	private static final String IPCHANNEL_TYPE = "ipChannelType";
	private static final String EXTRA_HOST_ADDRESSES = "extraHostAddresses";

	private String hostAddress;
	private int hostPort;
	private String peerAddress;
	private int peerPort;
	private String serverName;
	private String name;
	private IpChannelType ipChannelType;
	private FastList<String> extraHostAddresses;

	private AssociationType type;

	private AssociationListener associationListener = null;

	protected final AssociationHandler associationHandler = new AssociationHandler();

	// Is the Association been started by management?
	private volatile boolean started = false;

	private static final int MAX_SLS = 32;
	private int slsTable[] = new int[MAX_SLS];

	private int workerThreadTable[] = null;

	private ConcurrentLinkedQueue<PayloadData> txQueue = new ConcurrentLinkedQueue<PayloadData>();

	private ManagementImpl management;

	private SctpChannel socketChannelSctp;
	private SocketChannel socketChannelTcp;

	// The buffer into which we'll read data when it's available
	private ByteBuffer rxBuffer = ByteBuffer.allocateDirect(8192);
	private ByteBuffer txBuffer = ByteBuffer.allocateDirect(8192);

	private volatile MessageInfo msgInfo;

	/**
	 * Count of number of IO Errors occured. If this exceeds the maxIOErrors set
	 * in Management, socket will be closed and request to reopen the cosket
	 * will be initiated
	 */
	private volatile int ioErrors = 0;

	public AssociationImpl() {
		super();
		// clean transmission buffer
		txBuffer.clear();
		txBuffer.rewind();
		txBuffer.flip();

		// clean receiver buffer
		rxBuffer.clear();
		rxBuffer.rewind();
		rxBuffer.flip();
	}

	/**
	 * 
	 * 
	 * @param hostAddress
	 * @param hostport
	 * @param peerAddress
	 * @param peerPort
	 * @param assocName
	 * @throws IOException
	 */
	public AssociationImpl(String hostAddress, int hostport, String peerAddress, int peerPort, String assocName, IpChannelType ipChannelType,
			FastList<String> extraHostAddresses) throws IOException {
		this();
		this.hostAddress = hostAddress;
		this.hostPort = hostport;
		this.peerAddress = peerAddress;
		this.peerPort = peerPort;
		this.name = assocName;
		this.ipChannelType = ipChannelType;
		this.extraHostAddresses = extraHostAddresses;

		this.type = AssociationType.CLIENT;

	}

	/**
	 * @param peerAddress
	 * @param peerPort
	 * @param serverName
	 * @param assocName
	 */
	public AssociationImpl(String peerAddress, int peerPort, String serverName, String assocName, IpChannelType ipChannelType) {
		this();
		this.peerAddress = peerAddress;
		this.peerPort = peerPort;
		this.serverName = serverName;
		this.name = assocName;
		this.ipChannelType = ipChannelType;

		this.type = AssociationType.SERVER;

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
			logger.info(String.format("Started Association=%s", this));
		}
	}

	/**
	 * Stops this Association. If the underlying SctpChannel is open, marks the
	 * channel for close
	 */
	protected void stop() throws Exception {
		this.started = false;

		if (this.getSocketChannel() != null && this.getSocketChannel().isOpen()) {
			FastList<ChangeRequest> pendingChanges = this.management.getPendingChanges();
			synchronized (pendingChanges) {
				// Indicate we want the interest ops set changed
				pendingChanges.add(new ChangeRequest(getSocketChannel(), this, ChangeRequest.CLOSE, -1));
			}

			// Finally, wake up our selecting thread so it can make the required
			// changes
			this.management.getSocketSelector().wakeup();
		}
	}

	public IpChannelType getIpChannelType() {
		return this.ipChannelType;
	}

	/**
	 * @return the associationListener
	 */
	public AssociationListener getAssociationListener() {
		return associationListener;
	}

	/**
	 * @param associationListener
	 *            the associationListener to set
	 */
	public void setAssociationListener(AssociationListener associationListener) {
		this.associationListener = associationListener;
	}

	/**
	 * @return the assocName
	 */
	public String getName() {
		return name;
	}

	/**
	 * @return the associationType
	 */
	public AssociationType getAssociationType() {
		return type;
	}

	/**
	 * @return the started
	 */
	public boolean isStarted() {
		return started;
	}

	/**
	 * @return the hostAddress
	 */
	public String getHostAddress() {
		return hostAddress;
	}

	/**
	 * @return the hostPort
	 */
	public int getHostPort() {
		return hostPort;
	}

	/**
	 * @return the peerAddress
	 */
	public String getPeerAddress() {
		return peerAddress;
	}

	/**
	 * @return the peerPort
	 */
	public int getPeerPort() {
		return peerPort;
	}

	/**
	 * @return the serverName
	 */
	public String getServerName() {
		return serverName;
	}

	@Override
	public List<String> getExtraHostAddresses() {
		return extraHostAddresses.unmodifiable();
	}
	
	/**
	 * @param management
	 *            the management to set
	 */
	protected void setManagement(ManagementImpl management) {
		this.management = management;
	}

	private AbstractSelectableChannel getSocketChannel(){
		if (this.ipChannelType == IpChannelType.SCTP)
			return this.socketChannelSctp;
		else
			return this.socketChannelTcp;
	}

	/**
	 * @param socketChannel
	 *            the socketChannel to set
	 */
	protected void setSocketChannel(AbstractSelectableChannel socketChannel) {
		if (this.ipChannelType == IpChannelType.SCTP)
			this.socketChannelSctp = (SctpChannel) socketChannel;
		else
			this.socketChannelTcp = (SocketChannel) socketChannel;
	}

	public void send(PayloadData payloadData) throws Exception {
		this.checkSocketIsOpen();

		FastList<ChangeRequest> pendingChanges = this.management.getPendingChanges();
		synchronized (pendingChanges) {

			// Indicate we want the interest ops set changed
			pendingChanges.add(new ChangeRequest(this.getSocketChannel(), this, ChangeRequest.CHANGEOPS, SelectionKey.OP_WRITE));

			// And queue the data we want written
			// TODO Do we need to synchronize ConcurrentLinkedQueue ?
			// synchronized (this.txQueue) {
			this.txQueue.add(payloadData);
		}

		// Finally, wake up our selecting thread so it can make the required
		// changes
		this.management.getSocketSelector().wakeup();
	}

	private void checkSocketIsOpen() throws Exception {
		if (this.ipChannelType == IpChannelType.SCTP) {
			if (!this.started || this.socketChannelSctp == null || !this.socketChannelSctp.isOpen() || this.socketChannelSctp.association() == null)
				throw new Exception(String.format("Underlying sctp channel doesn't open or doesn't have association for Association=%s", this.name));
		} else {
			if (!this.started || this.socketChannelTcp == null || !this.socketChannelTcp.isOpen() || !this.socketChannelTcp.isConnected())
				throw new Exception(String.format("Underlying tcp channel doesn't open for Association=%s", this.name));
		}
	}

	protected void read() {

		try {
			PayloadData payload;
			if (this.ipChannelType == IpChannelType.SCTP)
				payload = this.doReadSctp();
			else
				payload = this.doReadTcp();
			if (payload == null)
				return;			
			
			if(logger.isDebugEnabled()){
				logger.debug(String.format("Rx : Ass=%s %s", this.name, payload));
			}

			if (this.management.isSingleThread()) {
				// If single thread model the listener should be called in the
				// selector thread itself
				try {
					this.associationListener.onPayload(this, payload);
				} catch (Exception e) {
					logger.error(String.format("Error while calling Listener for Association=%s.Payload=%s", this.name, payload), e);
				}
			} else {
				Worker worker = new Worker(this, this.associationListener, payload);

				System.out.println("payload.getStreamNumber()=" + payload.getStreamNumber() + " this.workerThreadTable[payload.getStreamNumber()]"
						+ this.workerThreadTable[payload.getStreamNumber()]);

				ExecutorService executorService = this.management.getExecutorService(this.workerThreadTable[payload.getStreamNumber()]);
				try {
					executorService.execute(worker);
				} catch (RejectedExecutionException e) {
					logger.error(String.format("Rejected %s as Executors is shutdown", payload), e);
				} catch (NullPointerException e) {
					logger.error(String.format("NullPointerException while submitting %s", payload), e);
				} catch (Exception e) {
					logger.error(String.format("Exception while submitting %s", payload), e);
				}
			}
		} catch (IOException e) {
			this.ioErrors++;
			logger.error(
					String.format("IOException while trying to read from underlying socket for Association=%s IOError count=%d", this.name, this.ioErrors), e);

			if (this.ioErrors > this.management.getMaxIOErrors()) {
				// Close this socket
				this.close();

				// retry to connect after delay
				this.scheduleConnect();
			}
		}
	}

	private PayloadData doReadSctp() throws IOException{

		rxBuffer.clear();
		MessageInfo messageInfo = this.socketChannelSctp.receive(rxBuffer, this, this.associationHandler);

		if (messageInfo == null) {
			if (logger.isDebugEnabled()) {
				logger.debug(String.format(" messageInfo is null for Association=%s", this.name));
			}
			return null;
		}

		int len = messageInfo.bytes();
		if (len == -1) {
			logger.error(String.format("Rx -1 while trying to read from underlying socket for Association=%s ", this.name));
			this.close();
			this.scheduleConnect();
			return null;
		}

		rxBuffer.flip();
		byte[] data = new byte[len];
		rxBuffer.get(data);
		rxBuffer.clear();

		PayloadData payload = new PayloadData(len, data, messageInfo.isComplete(), messageInfo.isUnordered(), messageInfo.payloadProtocolID(),
				messageInfo.streamNumber());
	
		return payload;
	}

	private PayloadData doReadTcp() throws IOException{

		rxBuffer.clear();
		int len = this.socketChannelTcp.read(rxBuffer);
		if (len == -1) {
			logger.warn(String.format("Rx -1 while trying to read from underlying socket for Association=%s ", this.name));
			this.close();
			this.scheduleConnect();
			return null;
		}

		rxBuffer.flip();
		byte[] data = new byte[len];
		rxBuffer.get(data);
		rxBuffer.clear();

		PayloadData payload = new PayloadData(len, data, true, false, 0, 0);

		return payload;
	}

	protected void write(SelectionKey key) {

		try {

			if (txBuffer.hasRemaining()) {
				// All data wasn't sent in last doWrite. Try to send it now
				// this.socketChannel.send(txBuffer, msgInfo);
				this.doSend();
			}

			// TODO Do we need to synchronize ConcurrentLinkedQueue?
			// synchronized (this.txQueue) {
			if (!txQueue.isEmpty() && !txBuffer.hasRemaining()) {
				while (!txQueue.isEmpty()) {
					// Lets read all the messages in txQueue and send

					txBuffer.clear();
					PayloadData payloadData = txQueue.poll();

					if (logger.isDebugEnabled()) {
						logger.debug(String.format("Tx : Ass=%s %s", this.name, payloadData));
					}

					// load ByteBuffer
					// TODO: BufferOverflowException ?
					txBuffer.put(payloadData.getData());

					if (this.ipChannelType == IpChannelType.SCTP) {
						int seqControl = payloadData.getStreamNumber();
						// we use max 32 streams
						seqControl = seqControl & 0x1F;

						msgInfo = MessageInfo.createOutgoing(null, this.slsTable[seqControl]);
						msgInfo.payloadProtocolID(payloadData.getPayloadProtocolId());
						msgInfo.complete(payloadData.isComplete());
						msgInfo.unordered(payloadData.isUnordered());
					}

					txBuffer.flip();

					this.doSend();

					if (txBuffer.hasRemaining()) {
						// Couldn't send all data. Lets return now and try to
						// send
						// this message in next cycle
						return;
					}

				}// end of while
			}

			if (txQueue.isEmpty()) {
				// We wrote away all data, so we're no longer interested
				// in writing on this socket. Switch back to waiting for
				// data.
				key.interestOps(SelectionKey.OP_READ);
			}

		} catch (IOException e) {
			this.ioErrors++;
			logger.error(String.format("IOException while trying to write to underlying socket for Association=%s IOError count=%d", this.name, this.ioErrors),
					e);

			if (this.ioErrors > this.management.getMaxIOErrors()) {
				// Close this socket
				this.close();

				// retry to connect after delay
				this.scheduleConnect();
			}
		}// try-catch
	}

	private int doSend() throws IOException {
		if (this.ipChannelType == IpChannelType.SCTP)
			return this.doSendSctp();
		else
			return this.doSendTcp();
	}

	private int doSendSctp() throws IOException {
		return this.socketChannelSctp.send(txBuffer, msgInfo);
	}

	private int doSendTcp() throws IOException {
		return this.socketChannelTcp.write(txBuffer);
	}

	protected void close() {
		if (this.getSocketChannel() != null) {
			try {
				this.getSocketChannel().close();
			} catch (Exception e) {
				logger.error(String.format("Exception while closing the SctpScoket for Association=%s", this.name), e);
			}
		}

		try {
			this.associationListener.onCommunicationShutdown(this);
		} catch (Exception e) {
			logger.error(String.format("Exception while calling onCommunicationShutdown on AssociationListener for Association=%s", this.name), e);
		}
	}

	protected void scheduleConnect() {
		if (this.getAssociationType() == AssociationType.CLIENT) {
			// If Associtaion is of Client type, reinitiate the connection
			// procedure
			FastList<ChangeRequest> pendingChanges = this.management.getPendingChanges();
			synchronized (pendingChanges) {
				pendingChanges.add(new ChangeRequest(this, ChangeRequest.CONNECT, System.currentTimeMillis() + this.management.getConnectDelay()));
			}
		}
	}

	protected void initiateConnection() throws IOException {

		// If Association is stopped, don't try to initiate connect
		if (!this.started) {
			return;
		}

		if (this.getSocketChannel() != null) {
			try {
				this.getSocketChannel().close();
			} catch (Exception e) {
				logger.error(String.format("Exception while trying to close existing sctp socket and initiate new socket for Association=%s", this.name), e);
			}
		}

		if (this.ipChannelType == IpChannelType.SCTP)
			this.doInitiateConnectionSctp();
		else
			this.doInitiateConnectionTcp();

		// reset the ioErrors
		this.ioErrors = 0;

		// Queue a channel registration since the caller is not the
		// selecting thread. As part of the registration we'll register
		// an interest in connection events. These are raised when a channel
		// is ready to complete connection establishment.
		FastList<ChangeRequest> pendingChanges = this.management.getPendingChanges();
		synchronized (pendingChanges) {
			pendingChanges.add(new ChangeRequest(this.getSocketChannel(), this, ChangeRequest.REGISTER, SelectionKey.OP_CONNECT));
		}

		// Finally, wake up our selecting thread so it can make the required
		// changes
		this.management.getSocketSelector().wakeup();

	}

	private void doInitiateConnectionSctp() throws IOException{
		// Create a non-blocking socket channel
		this.socketChannelSctp = SctpChannel.open();
		this.socketChannelSctp.configureBlocking(false);

		// bind to host address:port
		this.socketChannelSctp.bind(new InetSocketAddress(this.hostAddress, this.hostPort));
		if (this.extraHostAddresses != null) {
			for (String s : extraHostAddresses) {
				this.socketChannelSctp.bindAddress(InetAddress.getByName(s));
			}
		}

		// Kick off connection establishment
		this.socketChannelSctp.connect(new InetSocketAddress(this.peerAddress, this.peerPort), 32, 32);
	}

	private void doInitiateConnectionTcp() throws IOException{

		// Create a non-blocking socket channel
		this.socketChannelTcp = SocketChannel.open();
		this.socketChannelTcp.configureBlocking(false);

		// bind to host address:port
		this.socketChannelTcp.bind(new InetSocketAddress(this.hostAddress, this.hostPort));

		// Kick off connection establishment
		this.socketChannelTcp.connect(new InetSocketAddress(this.peerAddress, this.peerPort));
	}

	protected void createSLSTable(int minimumBoundStream) {

		if (minimumBoundStream == 1) { // special case - only 1 stream
			for (int i = 0; i < MAX_SLS; i++) {
				slsTable[i] = 0;
			}
		} else {
			slsTable[0] = 0;
			// Stream 0 is for management messages, we start from 1
			int stream = 1;
			for (int i = 1; i < MAX_SLS; i++) {
				if (stream > minimumBoundStream) {
					stream = 1;
				}
				slsTable[i] = stream++;
			}
		}
	}

	protected void createworkerThreadTable(int maximumBooundStream) {
		this.workerThreadTable = new int[maximumBooundStream];
		this.management.populateWorkerThread(this.workerThreadTable);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {

		StringBuilder sb2 = new StringBuilder();
		if (this.extraHostAddresses != null) {
			for (FastList.Node<String> n = this.extraHostAddresses.head(), end = this.extraHostAddresses.tail(); (n = n.getNext()) != end;) {
				if (sb2.length() > 0)
					sb2.append(", ");
				sb2.append(n.getValue());
			}
		}
		return "Association [name=" + name + ",hostAddress=" + hostAddress + ", hostPort=" + hostPort + ", peerAddress=" + peerAddress + ", peerPort="
				+ peerPort + ", serverName=" + serverName + ", associationType=" + type + ", ipChannelType=" + ipChannelType + ", extraHostAddresses=["+sb2+"], started=" + started + "]";
	}

	/**
	 * XML Serialization/Deserialization
	 */
	protected static final XMLFormat<AssociationImpl> ASSOCIATION_XML = new XMLFormat<AssociationImpl>(AssociationImpl.class) {

		@SuppressWarnings("unchecked")
		@Override
		public void read(javolution.xml.XMLFormat.InputElement xml, AssociationImpl association) throws XMLStreamException {
			association.name = xml.getAttribute(NAME, "");
			association.type = AssociationType.getAssociationType(xml.getAttribute(ASSOCIATION_TYPE, ""));
			association.hostAddress = xml.getAttribute(HOST_ADDRESS, "");
			association.hostPort = xml.getAttribute(HOST_PORT, 0);

			association.peerAddress = xml.getAttribute(PEER_ADDRESS, "");
			association.peerPort = xml.getAttribute(PEER_PORT, 0);

			association.serverName = xml.getAttribute(SERVER_NAME, "");
			association.ipChannelType = IpChannelType.getInstance(xml.getAttribute(IPCHANNEL_TYPE, IpChannelType.SCTP.getCode()));
			if (association.ipChannelType == null)
				association.ipChannelType = IpChannelType.SCTP;

			association.extraHostAddresses = xml.get(EXTRA_HOST_ADDRESSES, FastList.class);

		}

		@Override
		public void write(AssociationImpl association, javolution.xml.XMLFormat.OutputElement xml) throws XMLStreamException {
			xml.setAttribute(NAME, association.name);
			xml.setAttribute(ASSOCIATION_TYPE, association.type.getType());
			xml.setAttribute(HOST_ADDRESS, association.hostAddress);
			xml.setAttribute(HOST_PORT, association.hostPort);

			xml.setAttribute(PEER_ADDRESS, association.peerAddress);
			xml.setAttribute(PEER_PORT, association.peerPort);

			xml.setAttribute(SERVER_NAME, association.serverName);
			xml.setAttribute(IPCHANNEL_TYPE, association.ipChannelType.getCode());

			xml.add(association.extraHostAddresses, EXTRA_HOST_ADDRESSES, FastList.class);
		}
	};

}
