package org.mobicents.protocols.sctp.multiclient;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import javolution.xml.XMLFormat;
import javolution.xml.stream.XMLStreamException;

import org.apache.log4j.Logger;
import org.mobicents.protocols.api.AssociationListener;
import org.mobicents.protocols.api.AssociationType;
import org.mobicents.protocols.api.IpChannelType;
import org.mobicents.protocols.api.ManagementEventListener;
import org.mobicents.protocols.api.PayloadData;

import com.sun.nio.sctp.AbstractNotificationHandler;
import com.sun.nio.sctp.MessageInfo;

/*
 * This Association implementation is limited to ONE-TO-MANY TYPE CLIENT SCTP association
 */

public class OneToManyAssociationImpl extends ManageableAssociation {

	protected static final Logger logger = Logger.getLogger(OneToManyAssociationImpl.class);
	
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
	private String name;
	private String[] extraHostAddresses;

	private AssociationListener associationListener = null;
	
	//TODO see dev notes
	private ByteBuffer txBuffer = ByteBuffer.allocateDirect(8192);

	protected final OneToManyAssociationHandler associationHandler = new OneToManyAssociationHandler();

	/**
	 * This is used only for SCTP This is the socket address for peer which will
	 * be null initially. If the Association has multihome support and if peer
	 * address changes, this variable is set to new value so new messages are
	 * now sent to changed peer address
	 */
	protected volatile SocketAddress peerSocketAddress = null;

	// Is the Association been started by management?
	private AtomicBoolean started = new AtomicBoolean(false);
	// Is the Association up (connection is established)
	protected AtomicBoolean up = new AtomicBoolean(false);

	private int workerThreadTable[] = null;

	private MultiManagementImpl management;

	private volatile MessageInfo msgInfo;
	
	private volatile com.sun.nio.sctp.Association sctpAssociation;
	private final IpChannelType ipChannelType = IpChannelType.SCTP;
	
	private AssociationInfo assocInfo;
	private OneToManyAssocMultiplexer multiplexer;

	/**
	 * Count of number of IO Errors occured. If this exceeds the maxIOErrors set
	 * in Management, socket will be closed and request to reopen the cosket
	 * will be initiated
	 */
	//TODO see dev notes
	private volatile int ioErrors = 0;
	
	static class PeerAddressInfo {
		protected SocketAddress peerSocketAddress;
		protected int sctpAssocId;
		
		public PeerAddressInfo(SocketAddress peerSocketAddress) {
			super();
			this.peerSocketAddress = peerSocketAddress;
		}

		public SocketAddress getPeerSocketAddress() {
			return peerSocketAddress;
		}

		public int getSctpAssocId() {
			return sctpAssocId;
		}

		protected void setPeerSocketAddress(SocketAddress peerSocketAddress) {
			this.peerSocketAddress = peerSocketAddress;
		}

		protected void setSctpAssocId(int sctpAssocId) {
			this.sctpAssocId = sctpAssocId;
		}

		@Override
		public String toString() {
			return "PeerAddressInfo [peerSocketAddress=" + peerSocketAddress
					+ ", sctpAssocId=" + sctpAssocId + "]";
		}
	}
	
	static class HostAddressInfo {
		private final String primaryHostAddress;
		private final String secondaryHostAddress;
		private final int hostPort;
		
			
		public HostAddressInfo(String primaryHostAddress,
				String secondaryHostAddress, int hostPort) {
			super();
			if (primaryHostAddress == null || primaryHostAddress.isEmpty()) {
				throw new IllegalArgumentException("Constructor HostAddressInfo: primaryHostAddress can not be null!");
			}
			this.primaryHostAddress = primaryHostAddress;
			this.secondaryHostAddress = secondaryHostAddress;
			this.hostPort = hostPort;
		}
		public String getPrimaryHostAddress() {
			return primaryHostAddress;
		}
		
		public String getSecondaryHostAddress() {
			return secondaryHostAddress;
		}
		
		public int getHostPort() {
			return hostPort;
		}
				
		public boolean matches(HostAddressInfo hostAddressInfo) {
			if (hostAddressInfo == null) {
				return false;
			}
			if (this.hostPort != hostAddressInfo.getHostPort()) {
				return false;
			}
			if (this.equals(hostAddressInfo)) {
				return true;
			}
			if (this.getPrimaryHostAddress().equals(hostAddressInfo.getPrimaryHostAddress())
					|| this.getPrimaryHostAddress().equals(hostAddressInfo.getSecondaryHostAddress())) {
				return true;
			}
			if (this.getSecondaryHostAddress() != null && !this.getSecondaryHostAddress().isEmpty()) {
				if (this.getSecondaryHostAddress().equals(hostAddressInfo.getPrimaryHostAddress()) 
					|| this.getSecondaryHostAddress().equals(hostAddressInfo.getSecondaryHostAddress())) {
					return true;
				}
			}
			return false;
		}
		
		@Override
		public String toString() {
			return "HostAddressInfo [primaryHostAddress=" + primaryHostAddress
					+ ", secondaryHostAddress=" + secondaryHostAddress
					+ ", hostPort=" + hostPort + "]";
		}
		
	}
	static class AssociationInfo {		
		protected PeerAddressInfo peerInfo;
		protected HostAddressInfo hostInfo;
		public PeerAddressInfo getPeerInfo() {
			return peerInfo;
		}
		public HostAddressInfo getHostInfo() {
			return hostInfo;
		}
		@Override
		public String toString() {
			return "AssociationInfo [peerInfo=" + peerInfo + ", hostInfo="
					+ hostInfo + "]";
		}
		public AssociationInfo(PeerAddressInfo peerInfo,
				HostAddressInfo hostInfo) {
			super();
			this.peerInfo = peerInfo;
			this.hostInfo = hostInfo;
		}
		protected void setPeerInfo(PeerAddressInfo peerInfo) {
			this.peerInfo = peerInfo;
		}
		protected void setHostInfo(HostAddressInfo hostInfo) {
			this.hostInfo = hostInfo;
		}

	}
	
	protected OneToManyAssociationImpl() {
		super();
		// clean transmission buffer
		txBuffer.clear();
		txBuffer.rewind();
		txBuffer.flip();
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
	public OneToManyAssociationImpl(String hostAddress, int hostPort, String peerAddress, int peerPort, String assocName,
			String[] extraHostAddresses) throws IOException {
		this();		
		this.hostAddress = hostAddress;
		this.hostPort = hostPort;
		this.peerAddress = peerAddress;
		this.peerPort = peerPort;
		this.name = assocName;
		this.extraHostAddresses = extraHostAddresses;
		this.peerSocketAddress =  new InetSocketAddress(InetAddress.getByName(peerAddress), peerPort);
		String secondaryHostAddress = null;
		if (extraHostAddresses != null && extraHostAddresses.length >= 1) {
			secondaryHostAddress = extraHostAddresses[0];
		}
		this.assocInfo = new AssociationInfo(new PeerAddressInfo(peerSocketAddress),
											 new HostAddressInfo(hostAddress, secondaryHostAddress, hostPort));		
	}
	
	public AssociationInfo getAssocInfo() {
		return assocInfo;
	}

	public void setAssocInfo(AssociationInfo assocInfo) {
		this.assocInfo = assocInfo;
	}	
	
	protected void assignSctpAssociationId(int id) {
		this.assocInfo.getPeerInfo().setSctpAssocId(id);
	}
	
	protected boolean isConnectedToPeerAddresses(String peerAddresses) {
		if (logger.isDebugEnabled()) {
			logger.debug("OneToManyAssociationImpl.isConnectedToPeerAddresses - ownPeerAddress: "+getAssocInfo().getPeerInfo().getPeerSocketAddress().toString()
					+ "parameter peerAddresses: "+peerAddresses);
		}
		return peerAddresses.contains(getAssocInfo().getPeerInfo().getPeerSocketAddress().toString());
	}
	
	public void start() throws Exception {		

		if (this.associationListener == null) {
			throw new NullPointerException(String.format("AssociationListener is null for Associatoion=%s", this.name));
		}
		
		if (started.getAndSet(true)) {
			logger.warn("Association: "+this+" has been already STARTED");
			return;
		}

		doInitiateConnectionSctp();

		for (ManagementEventListener lstr : this.management.getManagementEventListeners()) {
			try {
				lstr.onAssociationStarted(this);
			} catch (Throwable ee) {
				logger.error("Exception while invoking onAssociationStarted", ee);
			}
		}
	}

	public void stop() throws Exception {
		if (!started.getAndSet(false)) {
			logger.warn("Association: "+this+" has been already STOPPED");
			return;
		}
		logger.debug("BUG_TRACE_1");
		this.multiplexer.stopAssociation(this);
		for (ManagementEventListener lstr : this.management.getManagementEventListeners()) {
			try {
				lstr.onAssociationStopped(this);
			} catch (Throwable ee) {
				logger.error("Exception while invoking onAssociationStopped", ee);
			}
		}
		
	}

	public IpChannelType getIpChannelType() {
		return IpChannelType.SCTP;
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
		return AssociationType.CLIENT;
	}

	/**
	 * @return the started
	 */
	@Override
	public boolean isStarted() {
		return started.get();
	}

	@Override
	public boolean isConnected() {
		return started.get() && up.get();
	}

	@Override
	public boolean isUp() {
		return up.get();
	}

	protected void markAssociationUp() {
		if (up.getAndSet(true)) {
			logger.debug("Association: "+this+" has been already marked UP");
			return;
		}
		for (ManagementEventListener lstr : this.management.getManagementEventListeners()) {
			try {
				lstr.onAssociationUp(this);
			} catch (Throwable ee) {
				logger.error("Exception while invoking onAssociationUp", ee);
			}
		}
	}

	protected void markAssociationDown() {
		if (!up.getAndSet(false)) {
			logger.debug("Association: "+this+" has been already marked DOWN");
			return;
		}
		
		for (ManagementEventListener lstr : this.management.getManagementEventListeners()) {
			try {
				lstr.onAssociationDown(this);
			} catch (Throwable ee) {
				logger.error("Exception while invoking onAssociationDown", ee);
			}
		}
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
		return null;
	}

	@Override
	public String[] getExtraHostAddresses() {
		return extraHostAddresses;
	}

	/**
	 * @param management
	 *            the management to set
	 */
	public void setManagement(MultiManagementImpl management) {
		this.management = management;
	}


	/**
	 * @param socketChannel
	 *            the socketChannel to set
	 */
	protected void setSocketChannel(AbstractSelectableChannel socketChannel) {
		//
	}

	public void read(PayloadData payload) {
		if (payload == null) {
			return;
		}
		
		if (logger.isDebugEnabled()) {
			logger.debug(String.format("Rx : Ass=%s %s", this.name, payload));
		}

		if (this.management.isSingleThread()) {
			// If single thread model the listener should be called in the
			// selector thread itself
			try {
				this.associationListener.onPayload(this, payload);
			} catch (Exception e) {
				logger.error(String.format("Error while calling Listener for Association=%s.Payload=%s", this.name,
						payload), e);
			}
		} else {
			MultiWorker worker = new MultiWorker(this, this.associationListener, payload);

			ExecutorService executorService = this.management.getExecutorService(this.workerThreadTable[payload
					.getStreamNumber()]);
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
	
	public void send(PayloadData payloadData) throws Exception {
		if (!started.get()) {
			throw new Exception("send failed: Association is not started");			
		}
		multiplexer.send(payloadData, this.msgInfo, this);
	}

	protected boolean write(PayloadData payloadData) {
		try {

			if (txBuffer.hasRemaining()) {
				// All data wasn't sent in last doWrite. Try to send it now
				this.doSend();
			}
			// TODO Do we need to synchronize ConcurrentLinkedQueue?
			// synchronized (this.txQueue) {
			if (!txBuffer.hasRemaining()) {
				txBuffer.clear();
				if (logger.isDebugEnabled()) {
					logger.debug(String.format("Tx : Ass=%s %s", this.name, payloadData));
				}

				// load ByteBuffer
				// TODO: BufferOverflowException ?
				txBuffer.put(payloadData.getData());
					
				int seqControl = payloadData.getStreamNumber();

				if (seqControl < 0 || seqControl >= this.associationHandler.getMaxOutboundStreams()) {
					try {
						// TODO : calling in same Thread. Is this ok? or
						// dangerous?
						this.associationListener.inValidStreamId(payloadData);
					} catch (Exception e) {
						logger.warn(e);
					}
					txBuffer.clear();
					txBuffer.flip();
					return false;
				}

				if (this.sctpAssociation != null) {
					msgInfo =MessageInfo.createOutgoing(sctpAssociation, peerSocketAddress, seqControl);
				} else {
					msgInfo = MessageInfo.createOutgoing(this.peerSocketAddress, seqControl);
				}
				msgInfo.payloadProtocolID(payloadData.getPayloadProtocolId());
				msgInfo.complete(payloadData.isComplete());
				msgInfo.unordered(payloadData.isUnordered());

				logger.debug("write() - msgInfo: "+msgInfo);
				txBuffer.flip();

				this.doSend();

				if (txBuffer.hasRemaining()) {
					// Couldn't send all data. Lets return now and try to
					// send
					// this message in next cycle
					return true;
				}
				return true;
			}
			return false;
		} catch (IOException e) {
			this.ioErrors++;
			logger.error(String.format(
					"IOException while trying to write to underlying socket for Association=%s IOError count=%d",
					this.name, this.ioErrors), e);
			return false;
		} catch (Exception ex) {
			logger.error(String.format("Unexpected exception has been caught while trying to write SCTP socketChanel for Association=%s: %s",
					this.name, ex.getMessage()), ex);
			return false;
		}
	}
	
	private int doSend() throws IOException {		
		return multiplexer.getSocketMultiChannel().send(txBuffer, msgInfo);
	}

		
	private void checkSocketIsOpen() throws Exception {
		if (!started.get()) {
				throw new Exception(String.format(
						"Association is not open (started) Association=%s",	this.name));
		}	
	}


	
	//TODO proper lifecycle management of multiplexed associations
	protected void close() {/*
		/*
		if (this.sctpAssociation != null && this.socketMultiChannel !=null) {
			try {
				this.socketMultiChannel.shutdown(this.sctpAssociation);
			} catch (Exception e) {
				logger.error(String.format("Exception while closing the SctpScoket for Association=%s", this.name), e);
			}
		}

		try {
			this.markAssociationDown();
			this.associationListener.onCommunicationShutdown(this);
		} catch (Exception e) {
			logger.error(String.format(
					"Exception while calling onCommunicationShutdown on AssociationListener for Association=%s",
					this.name), e);
		}

		// Finally clear the txQueue
		if (this.txQueue.size() > 0) {
			logger.warn(String.format("Clearig txQueue for Association=%s. %d messages still pending will be cleared",
					this.name, this.txQueue.size()));
		}
		this.txQueue.clear();
		*/
	}
/*
	protected void scheduleConnect() {
		if (this.getAssociationType() == AssociationType.CLIENT && !useOneToManyConnection) {
			// If Associtaion is of Client type, reinitiate the connection
			// procedure
			FastList<MultiChangeRequest> pendingChanges = this.management.getPendingChanges();
			synchronized (pendingChanges) {
				pendingChanges.add(new MultiChangeRequest(this, MultiChangeRequest.CONNECT, System.currentTimeMillis()
						+ this.management.getConnectDelay()));
			}
		} else if (this.getAssociationType() == AssociationType.CLIENT && useOneToManyConnection) {
			FastList<MultiChangeRequest> pendingChanges = this.management.getPendingChanges();
			synchronized (pendingChanges) {
				pendingChanges.add(new MultiChangeRequest(this.socketMultiChannel, this, MultiChangeRequest.REGISTER, SelectionKey.OP_WRITE));
			}
		}
	}*/

	protected void initiateConnection() throws IOException {

	/*	// If Association is stopped, don't try to initiate connect
		if (!this.started) {
			return;
		}

		if (this.getSocketChannel() != null) {
			try {
				this.getSocketChannel().close();
			} catch (Exception e) {
				logger.error(
						String.format(
								"Exception while trying to close existing sctp socket and initiate new socket for Association=%s",
								this.name), e);
			}
		}

		try {
			if (this.ipChannelType == IpChannelType.SCTP)
				this.doInitiateConnectionSctp();
			else
				this.doInitiateConnectionTcp();
		} catch (Exception e) {
			logger.error("Error while initiating a connection", e);
			this.scheduleConnect();
			return;
		}

		// reset the ioErrors
		this.ioErrors = 0;

		// Queue a channel registration since the caller is not the
		// selecting thread. As part of the registration we'll register
		// an interest in connection events. These are raised when a channel
		// is ready to complete connection establishment.
		FastList<MultiChangeRequest> pendingChanges = this.management.getPendingChanges();
		synchronized (pendingChanges) {
			pendingChanges.add(new MultiChangeRequest(this.getSocketChannel(), this, MultiChangeRequest.REGISTER,
					SelectionKey.OP_WRITE));
		}

		sendInit();
		this.connecting = true;
		// Finally, wake up our selecting thread so it can make the required
		// changes
		this.management.getSocketSelector().wakeup();
*/
	}


	private void doInitiateConnectionSctp() throws IOException {
		this.multiplexer =  management.getMultiChannelController().register(this);
		//send init msg
		byte[] spaceTrash = new byte[]{0x01, 0x00, 0x02, 0x03, 0x00, 0x00, 0x00, 0x18, 0x00, 0x06, 0x00, 0x08, 0x00, 0x00, 0x00, 0x05, 0x00, 0x12, 0x00, 0x08, 0x00, 0x00, 0x00, 0x00};
		PayloadData payloadData = new PayloadData(spaceTrash.length, spaceTrash, true, false, 0, 0);
		this.multiplexer.send(payloadData, null, this);
	}
	
	protected void createworkerThreadTable(int maximumBooundStream) {
		this.workerThreadTable = new int[maximumBooundStream];
		this.management.populateWorkerThread(this.workerThreadTable);
	}

	@Override
	public String toString() {
		return "OneToManyAssociationImpl [hostAddress=" + hostAddress
				+ ", hostPort=" + hostPort + ", peerAddress=" + peerAddress
				+ ", peerPort=" + peerPort + ", name=" + name
				+ ", extraHostAddresses=" + Arrays.toString(extraHostAddresses)
				+ ", type=" + AssociationType.CLIENT + ", started=" + started + ", up=" + up
				+ ", management=" + management + ", msgInfo=" + msgInfo
				+ ", sctpAssociation=" + sctpAssociation + ", ipChannelType="
				+ ipChannelType + ", assocInfo=" + assocInfo + ", multiplexer="
				+ multiplexer + ", ioErrors=" + ioErrors + "]";
	}

	/**
	 * XML Serialization/Deserialization
	 */
	protected static final XMLFormat<OneToManyAssociationImpl> ASSOCIATION_XML = new XMLFormat<OneToManyAssociationImpl>(
			OneToManyAssociationImpl.class) {

		@SuppressWarnings("unchecked")
		@Override
		public void read(javolution.xml.XMLFormat.InputElement xml, OneToManyAssociationImpl association)
				throws XMLStreamException {
			association.name = xml.getAttribute(NAME, "");
			//association.type = AssociationType.getAssociationType(xml.getAttribute(ASSOCIATION_TYPE, ""));
			association.hostAddress = xml.getAttribute(HOST_ADDRESS, "");
			association.hostPort = xml.getAttribute(HOST_PORT, 0);

			association.peerAddress = xml.getAttribute(PEER_ADDRESS, "");
			association.peerPort = xml.getAttribute(PEER_PORT, 0);

			//association.serverName = xml.getAttribute(SERVER_NAME, "");

			int extraHostAddressesSize = xml.getAttribute(EXTRA_HOST_ADDRESS_SIZE, 0);
			association.extraHostAddresses = new String[extraHostAddressesSize];

			for (int i = 0; i < extraHostAddressesSize; i++) {
				association.extraHostAddresses[i] = xml.get(EXTRA_HOST_ADDRESS, String.class);
			}

		}

		@Override
		public void write(OneToManyAssociationImpl association, javolution.xml.XMLFormat.OutputElement xml)
				throws XMLStreamException {
			xml.setAttribute(NAME, association.name);
			xml.setAttribute(ASSOCIATION_TYPE, AssociationType.CLIENT.getType());
			xml.setAttribute(HOST_ADDRESS, association.hostAddress);
			xml.setAttribute(HOST_PORT, association.hostPort);

			xml.setAttribute(PEER_ADDRESS, association.peerAddress);
			xml.setAttribute(PEER_PORT, association.peerPort);

			xml.setAttribute(SERVER_NAME, null);
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
	
	@Override
	public void acceptAnonymousAssociation(
			AssociationListener associationListener) throws Exception {
		throw new UnsupportedOperationException(this.getClass()+" class does not implement SERVER type Associations!");
	}
	
	@Override
	public void rejectAnonymousAssociation() {
		throw new UnsupportedOperationException(this.getClass()+" class does not implement SERVER type Associations!");
	}
	
	@Override
	public void stopAnonymousAssociation() throws Exception {
		throw new UnsupportedOperationException(this.getClass()+" class does not implement SERVER type Associations!");
	}
}
