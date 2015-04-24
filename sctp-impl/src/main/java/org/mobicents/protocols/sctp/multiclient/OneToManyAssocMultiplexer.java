package org.mobicents.protocols.sctp.multiclient;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javolution.util.FastList;

import org.apache.log4j.Logger;
import org.mobicents.protocols.api.PayloadData;
import org.mobicents.protocols.sctp.multiclient.OneToManyAssociationImpl.HostAddressInfo;

import com.sun.nio.sctp.MessageInfo;
import com.sun.nio.sctp.SctpMultiChannel;

public class OneToManyAssocMultiplexer {
	private static final Logger logger = Logger.getLogger(OneToManyAssocMultiplexer.class);
	
	private HostAddressInfo hostAddressInfo;
	private SctpMultiChannel socketMultiChannel;
	private MultiManagementImpl management;
	// The buffer into which we'll read data when it's available
	private ByteBuffer rxBuffer = ByteBuffer.allocateDirect(8192);
	
	// Is the multiplexer been started by management?
	private AtomicBoolean started = new AtomicBoolean(false);

		
	// Queue holds payloads to be transmitted
	private ConcurrentLinkedQueueSwapper<SctpMessage> txQueueSwapper = new ConcurrentLinkedQueueSwapper(new ConcurrentLinkedQueue<SctpMessage>());
	
	private CopyOnWriteArrayList<OneToManyAssociationImpl> pendingAssocs = new CopyOnWriteArrayList<OneToManyAssociationImpl>();
	private ConcurrentHashMap<Integer,OneToManyAssociationImpl> connectedAssocs = new ConcurrentHashMap<Integer, OneToManyAssociationImpl>();
	
	protected final MultiAssociationHandler associationHandler = new MultiAssociationHandler();
	
	/*
	 * Support fast and save queue operations like:
	 * 		
	 */
	static class ConcurrentLinkedQueueSwapper<T> {
		private ReadWriteLock lock = new ReentrantReadWriteLock();
		private ConcurrentLinkedQueue<T> queue;
		
		public ConcurrentLinkedQueueSwapper(ConcurrentLinkedQueue<T> queue) {
			this.queue = queue;
		}
		
		public void add(T e) {
			lock.readLock().lock();
			queue.add(e);
			lock.readLock().unlock();
		}
		
		public boolean isEmpty() {
			return queue.isEmpty();
		}
		
		public ConcurrentLinkedQueue<T> swap(ConcurrentLinkedQueue<T> newQueue) {
			if (newQueue == null) {
				throw new NullPointerException(this.getClass()+".swap(ConcurrentLinkedQueue<T> newQueue): newQueue parameter can not be null!");
			}
			ConcurrentLinkedQueue<T> newQueueCopy = new ConcurrentLinkedQueue<T>(newQueue);
			lock.writeLock().lock();
			ConcurrentLinkedQueue<T> oldQueue = this.queue;
			this.queue = newQueueCopy;
			lock.writeLock().unlock();
			return oldQueue;
		}	
		
		public void concatAsHead(ConcurrentLinkedQueue<T> newHead) {
			if (newHead == null) {
				throw new NullPointerException(this.getClass()+".concatAsHead(ConcurrentLinkedQueue<T> newHead): newHead parameter can not be null!");
			}
			ConcurrentLinkedQueue<T> newQueueCopy = new ConcurrentLinkedQueue<T>(newHead);
			lock.writeLock().lock();
			for (T e: this.queue) {
				newQueueCopy.add(e);
			}
			this.queue = newQueueCopy;
			lock.writeLock().unlock();
		}
		
	}
	public OneToManyAssocMultiplexer(HostAddressInfo hostAddressInfo, MultiManagementImpl management) throws IOException {
		super();
		if (hostAddressInfo == null || management == null) {
			throw new IllegalArgumentException("Constructor OneToManyAssocMultiplexer: hostAddressInfo and management parameters can not be null!");
		}
		this.hostAddressInfo = hostAddressInfo;
		this.management = management;
		// clean receiver buffer
		this.rxBuffer.clear();
		this.rxBuffer.rewind();
		this.rxBuffer.flip();
		initMultiChannel();
	}
	
	protected void registerAssociation(OneToManyAssociationImpl association) {
		if (!started.get()) {
			throw new IllegalStateException("OneToManyAssocMultiplexer is stoped!");
		}
		
		pendingAssocs.add(association);
	}
	
	protected void start() throws IOException {
		if (!started.compareAndSet(false, true)) {
			return;
		}
		socketMultiChannel = SctpMultiChannel.open();
		socketMultiChannel.configureBlocking(false);
		socketMultiChannel.bind(new InetSocketAddress(this.hostAddressInfo.getPrimaryHostAddress(), this.hostAddressInfo.getHostPort()));
		if (this.hostAddressInfo.getSecondaryHostAddress() != null && !this.hostAddressInfo.getSecondaryHostAddress().isEmpty()) {
			socketMultiChannel.bindAddress(InetAddress.getByName(this.hostAddressInfo.getSecondaryHostAddress()));
		}
		if (logger.isDebugEnabled()) {					
			logger.debug("New socketMultiChanel is created: "+socketMultiChannel+" supported options: "+socketMultiChannel.validOps()+":"+socketMultiChannel.supportedOptions());
		}
		FastList<MultiChangeRequest> pendingChanges = this.management.getPendingChanges();
		synchronized (pendingChanges) {
			pendingChanges.add(new MultiChangeRequest(this.socketMultiChannel, this, null, MultiChangeRequest.REGISTER,
					SelectionKey.OP_WRITE|SelectionKey.OP_READ));
		}	
	}
	
	protected void assignSctpAssocIdToAssociation(Integer id, OneToManyAssociationImpl association) {
		if (!started.get()) {
			throw new IllegalStateException("OneToManyAssocMultiplexer is stoped!");
		}
		if (id == null || association ==  null) {
			return;
		}
		logger.debug("BUG_TRACE - assignSctpAssocIdToAssociation - 1: pendingAssocs.size=" + pendingAssocs.size() + " connectedAssocs.size=" + connectedAssocs.size());
		connectedAssocs.put(id, association);
		pendingAssocs.remove(association);
		logger.debug("BUG_TRACE - assignSctpAssocIdToAssociation - 2: pendingAssocs.size=" + pendingAssocs.size() + " connectedAssocs.size=" + connectedAssocs.size());
		association.assignSctpAssociationId(id);
	}
	
	protected OneToManyAssociationImpl findConnectedAssociation(Integer sctpAssocId) {
		return connectedAssocs.get(sctpAssocId);
	}
	
	private String extractPeerAddresses(com.sun.nio.sctp.Association sctpAssociation) {
		String peerAddresses = "";
		try {
			for (SocketAddress sa : getSocketMultiChannel().getRemoteAddresses(sctpAssociation)) {
				peerAddresses += ", "+sa.toString();
			}
		} catch (IOException e) {	}
		return peerAddresses;
	}
	
	protected OneToManyAssociationImpl findPendingAssociation(com.sun.nio.sctp.Association sctpAssociation) {
		String peerAddresses = extractPeerAddresses(sctpAssociation);
		if (logger.isDebugEnabled()) {
			peerAddresses = peerAddresses.isEmpty() ? peerAddresses : peerAddresses.substring(2);
			logger.debug("Association("+sctpAssociation.associationID()+") connected to "+peerAddresses);
		}
		OneToManyAssociationImpl ret=null;
		for (OneToManyAssociationImpl assocImpl : pendingAssocs) {
			if (assocImpl.isConnectedToPeerAddresses(peerAddresses)) {
				ret = assocImpl;
				break;
			}
		}
		return ret;
	}
	
	private void initMultiChannel() throws IOException {
		socketMultiChannel = SctpMultiChannel.open();
		socketMultiChannel.configureBlocking(false);
		socketMultiChannel.bind(new InetSocketAddress(this.hostAddressInfo.getPrimaryHostAddress(), this.hostAddressInfo.getHostPort()));
		if (this.hostAddressInfo.getSecondaryHostAddress() != null && !this.hostAddressInfo.getSecondaryHostAddress().isEmpty()) {
			socketMultiChannel.bindAddress(InetAddress.getByName(this.hostAddressInfo.getSecondaryHostAddress()));
		}
		started.set(true);	
		if (logger.isDebugEnabled()) {					
			logger.debug("New socketMultiChanel is created: "+socketMultiChannel+" supported options: "+socketMultiChannel.validOps()+":"+socketMultiChannel.supportedOptions());
		}
		FastList<MultiChangeRequest> pendingChanges = this.management.getPendingChanges();
		synchronized (pendingChanges) {
			pendingChanges.add(new MultiChangeRequest(this.socketMultiChannel, this, null, MultiChangeRequest.REGISTER,
					SelectionKey.OP_WRITE|SelectionKey.OP_READ));
		}		
	}
	
	public HostAddressInfo getHostAddressInfo() {
		return hostAddressInfo;
	}
	public SctpMultiChannel getSocketMultiChannel() {
		return socketMultiChannel;
	}
	
	private OneToManyAssociationImpl getAssociationByMessageInfo(MessageInfo msgInfo) {
		OneToManyAssociationImpl ret = null;
		//find connected assoc
		if (msgInfo.association() != null) {
			ret = findConnectedAssociation(msgInfo.association().associationID());
		}
		//find in pending assoc
		if (ret == null) {
			ret = findPendingAssociation(msgInfo.association());
		}
		return ret;
	}
	
	protected void send(PayloadData payloadData, MessageInfo messageInfo, OneToManyAssociationImpl sender) throws IOException {
		if (!started.get()) {
			return;
		}
		FastList<MultiChangeRequest> pendingChanges = this.management.getPendingChanges();
		synchronized (pendingChanges) {

			// Indicate we want the interest ops set changed
			pendingChanges.add(new MultiChangeRequest(this.getSocketMultiChannel(), this, null, MultiChangeRequest.ADD_OPS,
					SelectionKey.OP_WRITE));
			
			this.txQueueSwapper.add(new SctpMessage(payloadData, messageInfo, sender));
		}

		// Finally, wake up our selecting thread so it can make the required
		// changes
		this.management.getSocketSelector().wakeup();
	}
	
	protected void write(SelectionKey key) {
		if (!started.get()) {
			return;
		}
		ConcurrentLinkedQueue<SctpMessage> txQueueTmp = txQueueSwapper.swap(new ConcurrentLinkedQueue<SctpMessage>());
		HashSet<String> skipList = new HashSet<String>();
		ConcurrentLinkedQueue<SctpMessage> retransmitQueue = new ConcurrentLinkedQueue<SctpMessage>();
		
		if (txQueueTmp.isEmpty()) {
			// We wrote away all data, so we're no longer interested
			// in writing on this socket. Switch back to waiting for
			// data.
			key.interestOps(SelectionKey.OP_READ);
			if (logger.isDebugEnabled()) {
				logger.debug("write: txQueue was empty");
			}
			return;
		}
		
		while (!txQueueTmp.isEmpty()) {
			SctpMessage msg = txQueueTmp.poll();
			if (skipList.contains(msg.getSenderAssoc().getName())) {
				retransmitQueue.add(msg);
			} else {
				if (!msg.getSenderAssoc().write(msg.getPayloadData())) {
					skipList.add(msg.getSenderAssoc().getName());
					retransmitQueue.add(msg);
				}
			}
		}
		
		if (!retransmitQueue.isEmpty()) {
			txQueueSwapper.concatAsHead(retransmitQueue);
		}
		
		//TODO see dev notes
		if (txQueueTmp.isEmpty()) {
			// We wrote away all data, so we're no longer interested
			// in writing on this socket. Switch back to waiting for
			// data.
			key.interestOps(SelectionKey.OP_READ);
		}
	}

	
	private void doReadSctp() throws IOException {

		rxBuffer.clear();
		MessageInfo messageInfo = null;
		messageInfo = this.socketMultiChannel.receive(rxBuffer, this, this.associationHandler);
		
		if (messageInfo == null) {
			if (logger.isDebugEnabled()) {
				logger.debug(String.format(" messageInfo is null for AssociationMultiplexer=%s", this));
			}
			return;
		}

		int len = messageInfo.bytes();
		if (len == -1) {
			logger.error(String.format("Rx -1 while trying to read from underlying socket for AssociationMultiplexer=%s ",
					this));
			return;
		}

		rxBuffer.flip();
		byte[] data = new byte[len];
		rxBuffer.get(data);
		rxBuffer.clear();

		PayloadData payload = new PayloadData(len, data, messageInfo.isComplete(), messageInfo.isUnordered(),
				messageInfo.payloadProtocolID(), messageInfo.streamNumber());

		OneToManyAssociationImpl assoc = getAssociationByMessageInfo(messageInfo);
		if (assoc != null) {
			assoc.read(payload);
		}
	
	}

	
	protected void read() {
		if (!started.get()) {
			return;
		}
		try {
			doReadSctp();
		} catch (IOException e) {
				logger.error("Unable to read from socketMultiChannek, hostAddressInfo: "+this.hostAddressInfo, e);
		} catch (Exception ex) {
			logger.error("Unexpected exception: unnable to read from socketMultiChannek, hostAddressInfo: "+this.hostAddressInfo, ex);
		}
	}
	
	protected OneToManyAssociationImpl resolveAssociationImpl(com.sun.nio.sctp.Association sctpAssociation) {
		if (!started.get()) {
			return null;
		}
		OneToManyAssociationImpl association = findConnectedAssociation(sctpAssociation.associationID());
		if (association == null) {
			association = findPendingAssociation(sctpAssociation);
			assignSctpAssocIdToAssociation(sctpAssociation.associationID(), association);
		}
		if (logger.isDebugEnabled()) {
			logger.debug("resolveAssociationImpl result for sctpAssocId: "+sctpAssociation.associationID()+" is "+association);
		}
		return association;
	}
	
	protected void stop() throws IOException {
		if (!started.compareAndSet(true, false)) {
			return;
		}
		
		for (OneToManyAssociationImpl assocImpl: connectedAssocs.values()) {
			try {
				assocImpl.stop();
			} catch (Exception ex) {
				logger.warn(ex);
			}
		}
		connectedAssocs.clear();
		for (OneToManyAssociationImpl assocImpl: pendingAssocs) {
			try {
				assocImpl.stop();
			} catch (Exception e) {
				logger.warn(e);;
			}
		}
		pendingAssocs.clear();
		this.socketMultiChannel.close();
	}
	
	protected synchronized void stopAssociation(OneToManyAssociationImpl assocImpl) throws IOException {
		logger.debug("BUG_TRACE 1");
		if (!started.get()) {
			logger.debug("BUG_TRACE 2_1");
			return;
		}
		logger.debug("BUG_TRACE 2_2");
		if (connectedAssocs.remove(assocImpl.getAssocInfo().getPeerInfo().getSctpAssocId()) == null) {
			logger.debug("BUG_TRACE 3_1");
			pendingAssocs.remove(assocImpl);
		} else {
			logger.debug("BUG_TRACE 3_2");
		}
		
		logger.debug("BUG_TRACE 4: pendingAssocs.size=" + pendingAssocs.size() + " connectedAssocs.size=" + connectedAssocs.size());
		
		if (pendingAssocs.isEmpty() && connectedAssocs.isEmpty()) {
			started.set(false);
			logger.debug("All associations of the multiplexer instance is stopped");
			this.socketMultiChannel.close();
		}
	}
	
	static class SctpMessage {
		private final PayloadData payloadData;
		private final MessageInfo messageInfo;
		private final OneToManyAssociationImpl senderAssoc;
		private SctpMessage(PayloadData payloadData, MessageInfo messageInfo,
				OneToManyAssociationImpl senderAssoc) {
			super();
			this.payloadData = payloadData;
			this.messageInfo = messageInfo;
			this.senderAssoc = senderAssoc;
		}
		private PayloadData getPayloadData() {
			return payloadData;
		}
		private MessageInfo getMessageInfo() {
			return messageInfo;
		}
		private OneToManyAssociationImpl getSenderAssoc() {
			return senderAssoc;
		}
		@Override
		public String toString() {
			return "SctpMessage [payloadData=" + payloadData + ", messageInfo="
					+ messageInfo + ", senderAssoc=" + senderAssoc + "]";
		}
	}

}
