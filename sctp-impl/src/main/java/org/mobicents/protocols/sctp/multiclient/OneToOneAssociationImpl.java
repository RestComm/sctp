package org.mobicents.protocols.sctp.multiclient;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import javolution.util.FastList;
import javolution.xml.XMLFormat;
import javolution.xml.stream.XMLStreamException;

import org.apache.log4j.Logger;
import org.mobicents.protocols.api.AssociationListener;
import org.mobicents.protocols.api.AssociationType;
import org.mobicents.protocols.api.IpChannelType;
import org.mobicents.protocols.api.ManagementEventListener;
import org.mobicents.protocols.api.PayloadData;

import com.sun.nio.sctp.MessageInfo;
import com.sun.nio.sctp.SctpChannel;

/**
 * Implements a one-to-one type ManagableAssociation. Used when associations is peeled off the sctp multi channel sockets to a
 * separate sctp socket channel.
 * 
 * @author amit bhayani
 * @author balogh.gabor@alerant.hu
 * 
 */
@SuppressWarnings("restriction")
public class OneToOneAssociationImpl extends ManageableAssociation {

    protected static final Logger logger = Logger.getLogger(OneToOneAssociationImpl.class.getName());

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

    private AssociationType type;

    private AssociationListener associationListener = null;

    protected final OneToOneAssociationHandler associationHandler = new OneToOneAssociationHandler();

    // Is the Association been started by management?
    private AtomicBoolean started = new AtomicBoolean(false);
    // Is the Association up (connection is established)
    protected AtomicBoolean up = new AtomicBoolean(false);

    private int workerThreadTable[] = null;

    private ConcurrentLinkedQueue<PayloadData> txQueue = new ConcurrentLinkedQueue<PayloadData>();

    private SctpChannel socketChannelSctp;

    // The buffer into which we'll read data when it's available
    private ByteBuffer rxBuffer = ByteBuffer.allocateDirect(8192);
    private ByteBuffer txBuffer = ByteBuffer.allocateDirect(8192);

    private volatile MessageInfo msgInfo;

    private OneToManyAssocMultiplexer multiplexer;

    /**
     * Count of number of IO Errors occured. If this exceeds the maxIOErrors set in Management, socket will be closed and
     * request to reopen the cosket will be initiated
     */
    private volatile int ioErrors = 0;

    public OneToOneAssociationImpl() {
        this.type = AssociationType.CLIENT;
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
    public OneToOneAssociationImpl(String hostAddress, int hostPort, String peerAddress, int peerPort, String assocName,
            String[] extraHostAddresses) throws IOException {
        this(hostAddress, hostPort, peerAddress, peerPort, assocName, extraHostAddresses, null);
    }

    public OneToOneAssociationImpl(String hostAddress, int hostPort, String peerAddress, int peerPort, String assocName,
            String[] extraHostAddresses, String secondaryPeerAddress) throws IOException {
        super(hostAddress, hostPort, peerAddress, peerPort, assocName, extraHostAddresses, secondaryPeerAddress);
        this.type = AssociationType.CLIENT;
        // clean transmission buffer
        txBuffer.clear();
        txBuffer.rewind();
        txBuffer.flip();

        // clean receiver buffer
        rxBuffer.clear();
        rxBuffer.rewind();
        rxBuffer.flip();
    }

    protected void start() throws Exception {

        if (this.associationListener == null) {
            throw new NullPointerException(String.format("AssociationListener is null for Associatoion=%s", this.name));
        }

        if (started.getAndSet(true)) {
            logger.warn("Association: " + this + " has been already STARTED");
            return;
        }

        scheduleConnect();

        for (ManagementEventListener lstr : this.management.getManagementEventListeners()) {
            try {
                lstr.onAssociationStarted(this);
            } catch (Throwable ee) {
                logger.error("Exception while invoking onAssociationStarted", ee);
            }
        }
    }

    /**
     * Stops this Association. If the underlying SctpChannel is open, marks the channel for close
     */
    protected void stop() throws Exception {
        if (logger.isDebugEnabled()) {
            logger.debug("stopped called on association=" + this);
        }
        if (!started.getAndSet(false)) {
            logger.warn("Association: " + this + " has been already STOPPED");
            return;
        }
        for (ManagementEventListener lstr : this.management.getManagementEventListeners()) {
            try {
                lstr.onAssociationStopped(this);
            } catch (Throwable ee) {
                logger.error("Exception while invoking onAssociationStopped", ee);
            }
        }

        if (this.getSocketChannel() != null && this.getSocketChannel().isOpen()) {
            FastList<MultiChangeRequest> pendingChanges = this.management.getPendingChanges();
            synchronized (pendingChanges) {
                // Indicate we want the interest ops set changed
                pendingChanges.add(new MultiChangeRequest(getSocketChannel(), null, this, MultiChangeRequest.CLOSE, -1));
            }

            // Finally, wake up our selecting thread so it can make the required
            // changes
            this.management.getSocketSelector().wakeup();
        } else if (multiplexer != null) {
            multiplexer.unregisterAssociation(this);
            if (logger.isDebugEnabled()) {
                logger.debug("close() - association=" + getName() + " is unregistered from the multiplexer");
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
     * @param associationListener the associationListener to set
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
            logger.debug("Association: " + this + " has been already marked UP");
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
            logger.debug("Association: " + this + " has been already marked DOWN");
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
     * @param management the management to set
     */
    protected void setManagement(MultiManagementImpl management) {
        this.management = management;
    }

    protected AbstractSelectableChannel getSocketChannel() {
        return this.socketChannelSctp;
    }

    protected void reconnect() {
        try {
            doInitiateConnectionSctp();
        } catch (Exception ex) {
            logger.warn("Error while trying to reconnect association[" + this.getName() + "]: " + ex.getMessage(), ex);
            scheduleConnect();
        }
    }

    /**
     * @param socketChannel the socketChannel to set
     */
    protected void setSocketChannel(AbstractSelectableChannel socketChannel) {
        this.socketChannelSctp = (SctpChannel) socketChannel;
    }

    public void send(PayloadData payloadData) throws Exception {
        try {
            this.checkSocketIsOpen();

            FastList<MultiChangeRequest> pendingChanges = this.management.getPendingChanges();
            synchronized (pendingChanges) {
                // Indicate we want the interest ops set changed
                pendingChanges.add(new MultiChangeRequest(this.getSocketChannel(), null, this, MultiChangeRequest.CHANGEOPS,
                        SelectionKey.OP_WRITE));
                // And queue the data we want written
                // TODO Do we need to synchronize ConcurrentLinkedQueue ?
                // synchronized (this.txQueue) {
                this.txQueue.add(payloadData);
            }
            // Finally, wake up our selecting thread so it can make the required
            // changes
            this.management.getSocketSelector().wakeup();
        } catch (Exception ex) {
            logger.error("Error while sending payload data: " + ex.getMessage(), ex);
        }
    }

    private void checkSocketIsOpen() throws Exception {
        if (!started.get() || this.socketChannelSctp == null || !this.socketChannelSctp.isOpen()
                || this.socketChannelSctp.association() == null) {
            logger.warn(String.format("Underlying sctp channel doesn't open or doesn't have association for Association=%s",
                    this.name));
            throw new Exception(String
                    .format("Underlying sctp channel doesn't open or doesn't have association for Association=%s", this.name));
        }
    }

    protected void read() {
        try {
            PayloadData payload;

            payload = this.doReadSctp();

            if (payload == null)
                return;

            if (logger.isDebugEnabled()) {
                logger.debug(String.format("Rx : Ass=%s %s", this.name, payload));
            }

            if (this.management.isSingleThread()) {
                // If single thread model the listener should be called in the
                // selector thread itself
                try {
                    this.associationListener.onPayload(this, payload);
                } catch (Exception e) {
                    logger.error(
                            String.format("Error while calling Listener for Association=%s.Payload=%s", this.name, payload), e);
                }
            } else {

                MultiWorker worker = new MultiWorker(this, this.associationListener, payload);

                ExecutorService executorService = this.management
                        .getExecutorService(this.workerThreadTable[payload.getStreamNumber()]);
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
                    String.format("IOException while trying to read from underlying socket for Association=%s IOError count=%d",
                            this.name, this.ioErrors),
                    e);

            if (this.ioErrors > this.management.getMaxIOErrors()) {
                // Close this socket
                this.close();

                // retry to connect after delay
                this.scheduleConnect();
            }
        }
    }

    private PayloadData doReadSctp() throws IOException {
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

        PayloadData payload = new PayloadData(len, data, messageInfo.isComplete(), messageInfo.isUnordered(),
                messageInfo.payloadProtocolID(), messageInfo.streamNumber());

        return payload;
    }

    protected void write(SelectionKey key) {
        try {

            if (txBuffer.hasRemaining()) {
                // All data wasn't sent in last doWrite. Try to send it now
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

                    int seqControl = payloadData.getStreamNumber();

                    if (seqControl < 0 || seqControl >= this.associationHandler.getMaxOutboundStreams()) {
                        try {
                            // TODO : calling in same Thread. Is this ok? or
                            // dangerous?
                            this.associationListener.inValidStreamId(payloadData);
                        } catch (Exception e) {

                        }
                        txBuffer.clear();
                        txBuffer.flip();
                        continue;
                    }

                    msgInfo = MessageInfo.createOutgoing(this.peerSocketAddress, seqControl);
                    msgInfo.payloadProtocolID(payloadData.getPayloadProtocolId());
                    msgInfo.complete(payloadData.isComplete());
                    msgInfo.unordered(payloadData.isUnordered());

                    txBuffer.flip();

                    this.doSend();

                    if (txBuffer.hasRemaining()) {
                        // Couldn't send all data. Lets return now and try to
                        // send
                        // this message in next cycle
                        return;
                    }

                } // end of while
            }

            if (txQueue.isEmpty()) {
                // We wrote away all data, so we're no longer interested
                // in writing on this socket. Switch back to waiting for
                // data.
                key.interestOps(SelectionKey.OP_READ);
            }

        } catch (IOException e) {
            this.ioErrors++;
            logger.error(
                    String.format("IOException while trying to write to underlying socket for Association=%s IOError count=%d",
                            this.name, this.ioErrors),
                    e);

            if (this.ioErrors > this.management.getMaxIOErrors()) {
                // Close this socket
                this.close();

                // retry to connect after delay
                this.scheduleConnect();
            }
        } // try-catch
    }

    private int doSend() throws IOException {
        return this.socketChannelSctp.send(txBuffer, msgInfo);
    }

    protected boolean isOpen() {
        return this.getSocketChannel() != null && this.getSocketChannel().isOpen();
    }

    protected void close() {
        if (this.getSocketChannel() != null) {
            try {
                this.getSocketChannel().close();
                if (logger.isDebugEnabled()) {
                    logger.debug("close() - socketChannel is closed for association=" + getName());
                }
            } catch (Exception e) {
                logger.error(String.format("Exception while closing the SctpScoket for Association=%s", this.name), e);
            }
        }
        if (this.up.get()) {
            try {
                this.markAssociationDown();
                this.associationListener.onCommunicationShutdown(this);
            } catch (Exception e) {
                logger.error(String.format(
                        "Exception while calling onCommunicationShutdown on AssociationListener for Association=%s", this.name),
                        e);
            }
        }
        if (multiplexer != null) {
            multiplexer.unregisterAssociation(this);
            if (logger.isDebugEnabled()) {
                logger.debug("close() - association=" + getName() + " is unregistered from the multiplexer");
            }
        }
        // Finally clear the txQueue
        if (this.txQueue.size() > 0) {
            logger.warn(String.format("Clearig txQueue for Association=%s. %d messages still pending will be cleared",
                    this.name, this.txQueue.size()));
        }
        this.txQueue.clear();
    }

    protected void scheduleConnect() {
        if (this.getAssociationType() == AssociationType.CLIENT) {
            FastList<MultiChangeRequest> pendingChanges = this.management.getPendingChanges();
            synchronized (pendingChanges) {
                pendingChanges.add(new MultiChangeRequest(null, this, MultiChangeRequest.CONNECT,
                        System.currentTimeMillis() + this.management.getConnectDelay()));
            }
        }
    }

    protected void branch(SctpChannel sctpChannel, MultiManagementImpl management) {
        // if association is stopped, channel wont be registered.
        if (!started.get()) {
            if (logger.isInfoEnabled()) {
                logger.info("Branching a stopped association, channel wont be registered to the selector.");
            }
            // set channel to able to close later
            this.socketChannelSctp = sctpChannel;
            this.management = management;
        } else {
            FastList<MultiChangeRequest> pendingChanges = this.management.getPendingChanges();
            synchronized (pendingChanges) {
                // setting the channel must be synchronized
                this.socketChannelSctp = sctpChannel;
                this.management = management;
                pendingChanges.add(new MultiChangeRequest(sctpChannel, null, this, MultiChangeRequest.REGISTER,
                        SelectionKey.OP_WRITE | SelectionKey.OP_READ));
            }
        }
        ;
    }

    private void doInitiateConnectionSctp() throws IOException {
        // reset the ioErrors
        this.ioErrors = 0;
        this.multiplexer = management.getMultiChannelController().register(this);
        this.multiplexer.send(getInitPayloadData(), null, this, true);
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

        StringBuilder sb = new StringBuilder();
        sb.append("Association [name=").append(this.name).append(", started=").append(started.get()).append(", up=").append(up)
                .append(", associationType=").append(this.type).append(", ipChannelType=").append("SCTP")
                .append(", hostAddress=").append(this.hostAddress).append(", hostPort=").append(this.hostPort)
                .append(", peerAddress=").append(this.peerAddress).append(", peerPort=").append(this.peerPort)
                .append(", serverName=").append("");

        sb.append(", extraHostAddress=[");

        if (this.extraHostAddresses != null) {
            for (int i = 0; i < this.extraHostAddresses.length; i++) {
                String extraHostAddress = this.extraHostAddresses[i];
                sb.append(extraHostAddress);
                sb.append(", ");
            }
        }
        sb.append(" secondaryPeerAddress=").append(this.secondaryPeerAddress);
        sb.append("]]");

        return sb.toString();
    }

    /**
     * XML Serialization/Deserialization
     */
    protected static final XMLFormat<OneToOneAssociationImpl> ASSOCIATION_XML = new XMLFormat<OneToOneAssociationImpl>(
            OneToOneAssociationImpl.class) {

        @Override
        public void read(javolution.xml.XMLFormat.InputElement xml, OneToOneAssociationImpl association)
                throws XMLStreamException {
            association.name = xml.getAttribute(NAME, "");
            association.hostAddress = xml.getAttribute(HOST_ADDRESS, "");
            association.hostPort = xml.getAttribute(HOST_PORT, 0);

            association.peerAddress = xml.getAttribute(PEER_ADDRESS, "");
            association.peerPort = xml.getAttribute(PEER_PORT, 0);

            int extraHostAddressesSize = xml.getAttribute(EXTRA_HOST_ADDRESS_SIZE, 0);
            association.extraHostAddresses = new String[extraHostAddressesSize];

            for (int i = 0; i < extraHostAddressesSize; i++) {
                association.extraHostAddresses[i] = xml.get(EXTRA_HOST_ADDRESS, String.class);
            }
            try {
                association.initDerivedFields();
            } catch (IOException e) {
                logger.error("Unable to load association from XML: error while calculating derived fields", e);
            }
        }

        @Override
        public void write(OneToOneAssociationImpl association, javolution.xml.XMLFormat.OutputElement xml)
                throws XMLStreamException {
            xml.setAttribute(NAME, association.name);
            xml.setAttribute(ASSOCIATION_TYPE, association.type.getType());
            xml.setAttribute(HOST_ADDRESS, association.hostAddress);
            xml.setAttribute(HOST_PORT, association.hostPort);

            xml.setAttribute(PEER_ADDRESS, association.peerAddress);
            xml.setAttribute(PEER_PORT, association.peerPort);

            xml.setAttribute(SERVER_NAME, "");
            xml.setAttribute(IPCHANNEL_TYPE, IpChannelType.SCTP);

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
    protected void readPayload(PayloadData payload) {
        if (payload == null) {
            return;
        }

        if (logger.isDebugEnabled()) {
            logger.debug(String.format("Rx : Ass=%s %s", this.name, payload));
        }

        if (this.management.isSingleThread()) {
            try {
                this.associationListener.onPayload(this, payload);
            } catch (Exception e) {
                logger.error(String.format("Error while calling Listener for Association=%s.Payload=%s", this.name, payload),
                        e);
            }
        } else {
            MultiWorker worker = new MultiWorker(this, this.associationListener, payload);
            ExecutorService executorService = this.management
                    .getExecutorService(this.workerThreadTable[payload.getStreamNumber()]);
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

    @Override
    protected boolean writePayload(PayloadData payloadData, boolean initMsg) {
        try {

            if (txBuffer.hasRemaining()) {
                multiplexer.getSocketMultiChannel().send(txBuffer, msgInfo);
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
                
                if (initMsg) {
                    msgInfo = MessageInfo.createOutgoing(this.initSocketAddress, seqControl);
                } else {
                    msgInfo = MessageInfo.createOutgoing(this.peerSocketAddress, seqControl);
                }

                msgInfo.payloadProtocolID(payloadData.getPayloadProtocolId());
                msgInfo.complete(payloadData.isComplete());
                msgInfo.unordered(payloadData.isUnordered());

                logger.debug("write() - msgInfo: " + msgInfo);
                txBuffer.flip();

                multiplexer.getSocketMultiChannel().send(txBuffer, msgInfo);

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
            logger.error(
                    String.format("IOException while trying to write to underlying socket for Association=%s IOError count=%d",
                            this.name, this.ioErrors),
                    e);
            logger.error("Internal send failed, retrying.");
            this.close();
            onSendFailed();
            return false;
        } catch (Exception ex) {
            logger.error(String.format(
                    "Unexpected exception has been caught while trying to write SCTP socketChanel for Association=%s: %s",
                    this.name, ex.getMessage()), ex);
            return false;
        }
    }

    protected void onSendFailed() {
        // if started and down then it means it is a CANT_START event and scheduleConnect must be called.
        if (started.get() && !up.get()) {
            logger.warn("Association=" + getName() + " CANT_START, trying to reconnect...");
            switchInitSocketAddress();
            scheduleConnect();
        }
    }

    // called when COMM_UP event arrived after association was stopped.
    protected void silentlyShutdown() {
        if (!started.get()) {
            if (logger.isInfoEnabled()) {
                logger.info("Association=" + getName()
                        + " has been already stopped when COMM_UP event arrived, closing sctp association without notifying any listeners.");
            }
            if (this.getSocketChannel() != null) {
                try {
                    this.getSocketChannel().close();
                    if (logger.isDebugEnabled()) {
                        logger.debug("close() - socketChannel is closed for association=" + getName());
                    }
                } catch (Exception e) {
                    logger.error(String.format("Exception while closing the SctpScoket for Association=%s", this.name), e);
                }
            }
        }
    }

    @Override
    public void acceptAnonymousAssociation(AssociationListener associationListener) throws Exception {
        throw new UnsupportedOperationException(this.getClass() + " class does not implement SERVER type Associations!");
    }

    @Override
    public void rejectAnonymousAssociation() {
        throw new UnsupportedOperationException(this.getClass() + " class does not implement SERVER type Associations!");
    }

    @Override
    public void stopAnonymousAssociation() throws Exception {
        throw new UnsupportedOperationException(this.getClass() + " class does not implement SERVER type Associations!");
    }

    void onCantStart() {
        this.close();

    }
}
