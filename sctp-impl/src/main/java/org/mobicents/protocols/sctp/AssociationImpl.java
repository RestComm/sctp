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
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

import javolution.util.FastList;
import javolution.xml.XMLFormat;
import javolution.xml.stream.XMLStreamException;

import org.apache.log4j.Logger;
import org.mobicents.protocols.api.Association;
import org.mobicents.protocols.api.AssociationListener;
import org.mobicents.protocols.api.PayloadData;

import com.sun.nio.sctp.MessageInfo;
import com.sun.nio.sctp.SctpChannel;

/**
 * @author amit bhayani
 * 
 */
public class AssociationImpl implements Association {

	private static final Logger logger = Logger.getLogger(AssociationImpl.class.getName());

	private static final String NAME = "name";
	private static final String SERVER_NAME = "serverName";
	private static final String HOST_ADDRESS = "hostAddress";
	private static final String HOST_PORT = "hostPort";

	private static final String PEER_ADDRESS = "peerAddress";
	private static final String PEER_PORT = "peerPort";

	private static final String ASSOCIATION_TYPE = "assoctype";

	private String hostAddress;
	private int hostPort;
	private String peerAddress;
	private int peerPort;
	private String serverName;
	private String name;

	private SctpChannel socketChannel;

	private AssociationType type;

	private AssociationListener associationListener = null;

	private final AssociationHandler associationHandler = new AssociationHandler();

	// Is the Association been started by management?
	private volatile boolean started = false;

	private static final int MAX_SLS = 32;
	private int slsTable[] = new int[MAX_SLS];

	private int workerThreadTable[] = null;

	private ConcurrentLinkedQueue<PayloadData> txQueue = new ConcurrentLinkedQueue<PayloadData>();

	private ManagementImpl management;

	// The buffer into which we'll read data when it's available
	private ByteBuffer rxBuffer = ByteBuffer.allocateDirect(8192);
	private ByteBuffer txBuffer = ByteBuffer.allocateDirect(8192);

	private volatile MessageInfo msgInfo;

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
	public AssociationImpl(String hostAddress, int hostport, String peerAddress, int peerPort, String assocName) throws IOException {
		this();
		this.hostAddress = hostAddress;
		this.hostPort = hostport;
		this.peerAddress = peerAddress;
		this.peerPort = peerPort;
		this.name = assocName;

		this.type = AssociationType.CLIENT;

	}

	/**
	 * @param peerAddress
	 * @param peerPort
	 * @param serverName
	 * @param assocName
	 */
	public AssociationImpl(String peerAddress, int peerPort, String serverName, String assocName) {
		this();
		this.peerAddress = peerAddress;
		this.peerPort = peerPort;
		this.serverName = serverName;
		this.name = assocName;

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
	 * chanel for close
	 */
	protected void stop() throws Exception {
		this.started = false;

		if (this.socketChannel != null && this.socketChannel.isOpen()) {
			FastList<ChangeRequest> pendingChanges = this.management.getPendingChanges();
			synchronized (pendingChanges) {
				// Indicate we want the interest ops set changed
				pendingChanges.add(new ChangeRequest(socketChannel, this, ChangeRequest.CLOSE, -1));
			}

			// Finally, wake up our selecting thread so it can make the required
			// changes
			this.management.getSocketSelector().wakeup();
		}
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
	public AssociationType getType() {
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

	/**
	 * @param socketChannel
	 *            the socketChannel to set
	 */
	protected void setSocketChannel(SctpChannel socketChannel) {
		this.socketChannel = socketChannel;
	}

	/**
	 * @param management
	 *            the management to set
	 */
	protected void setManagement(ManagementImpl management) {
		this.management = management;
	}

	public void send(PayloadData payloadData) throws Exception {
		if (!this.started || this.socketChannel == null || !this.socketChannel.isOpen() || this.socketChannel.association() == null) {
			// Throw Exception if management not started the Association or
			// underlying socket is closed
			throw new Exception(String.format("Underlying sctp channel doesn't have association for Association=%s", this.name));
		}

		FastList<ChangeRequest> pendingChanges = this.management.getPendingChanges();
		synchronized (pendingChanges) {

			// Indicate we want the interest ops set changed
			pendingChanges.add(new ChangeRequest(socketChannel, this, ChangeRequest.CHANGEOPS, SelectionKey.OP_WRITE));

			// And queue the data we want written
			// TODO Do we need to synchronize ConcurrentLinkedQueue ?
			// synchronized (this.txQueue) {
			this.txQueue.add(payloadData);
			// }
		}

		// Finally, wake up our selecting thread so it can make the required
		// changes
		this.management.getSocketSelector().wakeup();
	}

	protected void read() throws IOException {

		rxBuffer.clear();
		MessageInfo messageInfo = this.socketChannel.receive(rxBuffer, this, this.associationHandler);

		if (messageInfo == null) {
			System.err.println(this.name + " messageInfo is null");
			return;
		}

		int len = messageInfo.bytes();
		if (len == -1) {
			// TODO close the channel?
			System.err.println("messageInfo.bytes() == -1");
			this.socketChannel.close();
			return;
		}

		rxBuffer.flip();
		byte[] data = new byte[len];
		rxBuffer.get(data);
		rxBuffer.clear();

		PayloadData payload = new PayloadData(len, data, messageInfo.isComplete(), messageInfo.isUnordered(), messageInfo.payloadProtocolID(),
				messageInfo.streamNumber());

		System.out.println(payload);

		if (this.management.isSingleThread()) {
			// If single thread model the listener should be called in the
			// selector thread itself
			try {
				this.associationListener.onPayload(this, payload);
			} catch (Exception e) {
				logger.error(String.format("Error while calling Listener. %s", payload), e);
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
	}

	protected void write(SelectionKey key) throws IOException {

		if (txBuffer.hasRemaining()) {
			// All data wasn't sent in last doWrite. Try to send it now
			this.socketChannel.send(txBuffer, msgInfo);
		}

		// TODO Do we need to synchronize ConcurrentLinkedQueue?
		// synchronized (this.txQueue) {
		if (!txQueue.isEmpty() && !txBuffer.hasRemaining()) {
			while (!txQueue.isEmpty()) {
				// Lets read all the messages in txQueue and send

				txBuffer.clear();
				PayloadData payloadData = txQueue.poll();

				// load ByteBuffer
				txBuffer.put(payloadData.getData());

				int seqControl = payloadData.getStreamNumber();
				// we use max 32 streams
				seqControl = seqControl & 0x1F;

				msgInfo = MessageInfo.createOutgoing(null, this.slsTable[seqControl]);
				msgInfo.payloadProtocolID(payloadData.getPayloadProtocolId());
				msgInfo.complete(payloadData.isComplete());
				msgInfo.unordered(payloadData.isUnordered());

				txBuffer.flip();

				int sent = this.socketChannel.send(txBuffer, msgInfo);

				if (txBuffer.hasRemaining()) {
					// Couldn't send all data. Lets return now and try to send
					// this message in next cycle
					return;
				}

			}// end of while
		}
		// }

		if (txQueue.isEmpty()) {
			// We wrote away all data, so we're no longer interested
			// in writing on this socket. Switch back to waiting for
			// data.
			key.interestOps(SelectionKey.OP_READ);
		}
	}

	protected void close() throws IOException {
		if (this.socketChannel != null) {
			try {
				this.socketChannel.close();
			} catch (IOException e) {
				logger.error("Error while closing the SctpScoket", e);
			}
		}

		this.associationListener.onCommunicationShutdown(this);
	}

	protected void scheduleConnect() {
		FastList<ChangeRequest> pendingChanges = this.management.getPendingChanges();
		synchronized (pendingChanges) {
			pendingChanges.add(new ChangeRequest(this, ChangeRequest.CONNECT, System.currentTimeMillis() + this.management.getConnectDelay()));
		}
	}

	protected void initiateConnection() throws IOException {

		// If Association is stopped, don't try to initiate connect
		if (!this.started) {
			return;
		}

		if (this.socketChannel != null) {
			try {
				this.socketChannel.close();
			} catch (Exception e) {
				logger.error(String.format("Exception while trying to close existing sctp socket and initiate new socket for Association=%s", this.name), e);
			}
		}

		// Create a non-blocking socket channel
		this.socketChannel = SctpChannel.open();
		this.socketChannel.configureBlocking(false);

		// bind to host address:port
		this.socketChannel.bind(new InetSocketAddress(this.hostAddress, this.hostPort));

		// Kick off connection establishment
		this.socketChannel.connect(new InetSocketAddress(this.peerAddress, this.peerPort), 32, 32);

		// Queue a channel registration since the caller is not the
		// selecting thread. As part of the registration we'll register
		// an interest in connection events. These are raised when a channel
		// is ready to complete connection establishment.
		FastList<ChangeRequest> pendingChanges = this.management.getPendingChanges();
		synchronized (pendingChanges) {
			pendingChanges.add(new ChangeRequest(this.socketChannel, this, ChangeRequest.REGISTER, SelectionKey.OP_CONNECT));
		}

		// Finally, wake up our selecting thread so it can make the required
		// changes
		this.management.getSocketSelector().wakeup();

	}

	protected void createSLSTable(int minimumBoundStream) {

		// Stream 0 is for management messages, we start from 1
		int stream = 1;
		for (int i = 0; i < MAX_SLS; i++) {
			if (stream > minimumBoundStream) {
				stream = 1;
			}
			slsTable[i] = stream++;
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
		return "Association [hostAddress=" + hostAddress + ", hostPort=" + hostPort + ", peerAddress=" + peerAddress + ", peerPort=" + peerPort
				+ ", serverName=" + serverName + ", assocName=" + name + ", associationType=" + type + ", started=" + started + "]";
	}

	/**
	 * XML Serialization/Deserialization
	 */
	protected static final XMLFormat<AssociationImpl> ASSOCIATION_XML = new XMLFormat<AssociationImpl>(AssociationImpl.class) {

		@Override
		public void read(javolution.xml.XMLFormat.InputElement xml, AssociationImpl association) throws XMLStreamException {
			association.name = xml.getAttribute(NAME, "");
			association.type = AssociationType.getAssociationType(xml.getAttribute(ASSOCIATION_TYPE, ""));
			association.hostAddress = xml.getAttribute(HOST_ADDRESS, "");
			association.hostPort = xml.getAttribute(HOST_PORT, 0);

			association.peerAddress = xml.getAttribute(PEER_ADDRESS, "");
			association.peerPort = xml.getAttribute(PEER_PORT, 0);

			association.serverName = xml.getAttribute(SERVER_NAME, "");

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

		}
	};

}
