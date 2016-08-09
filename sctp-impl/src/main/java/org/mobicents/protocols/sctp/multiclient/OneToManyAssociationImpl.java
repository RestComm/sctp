package org.mobicents.protocols.sctp.multiclient;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.util.Arrays;
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

/**
 * Implements a one-to-many type ManagableAssociation. Used when associations is NOT peeled off the sctp multi channel sockets
 * to a separate sctp socket channel.
 * 
 * @author balogh.gabor@alerant.hu
 */
@SuppressWarnings("restriction")
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

    private AssociationListener associationListener = null;

    private ByteBuffer txBuffer = ByteBuffer.allocateDirect(8192);

    protected final OneToManyAssociationHandler associationHandler = new OneToManyAssociationHandler();

    // Is the Association been started by management?
    private AtomicBoolean started = new AtomicBoolean(false);
    // Is the Association up (connection is established)
    protected AtomicBoolean up = new AtomicBoolean(false);

    private int workerThreadTable[] = null;

    private volatile MessageInfo msgInfo;

    private volatile com.sun.nio.sctp.Association sctpAssociation;
    private final IpChannelType ipChannelType = IpChannelType.SCTP;

    private OneToManyAssocMultiplexer multiplexer;

    /**
     * Count of number of IO Errors occured.
     */
    private volatile int ioErrors = 0;

    public OneToManyAssociationImpl() {
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
        this(hostAddress, hostPort, peerAddress, peerPort, assocName, extraHostAddresses, null);
    }

    public OneToManyAssociationImpl(String hostAddress, int hostPort, String peerAddress, int peerPort, String assocName,
            String[] extraHostAddresses, String secondaryPeerAddress) throws IOException {
        super(hostAddress, hostPort, peerAddress, peerPort, assocName, extraHostAddresses);
        // clean transmission buffer
        txBuffer.clear();
        txBuffer.rewind();
        txBuffer.flip();
    }

    public void start() throws Exception {

        if (this.associationListener == null) {
            throw new NullPointerException(String.format("AssociationListener is null for Associatoion=%s", this.name));
        }

        if (started.getAndSet(true)) {
            logger.warn("Association: " + this + " has been already STARTED");
            return;
        }
        for (ManagementEventListener lstr : this.management.getManagementEventListeners()) {
            try {
                lstr.onAssociationStarted(this);
            } catch (Throwable ee) {
                logger.error("Exception while invoking onAssociationStarted", ee);
            }
        }
        scheduleConnect();
    }

    public void stop() throws Exception {
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

        try {
            this.associationListener.onCommunicationShutdown(this);
        } catch (Exception e) {
            logger.error(String.format(
                    "Exception while calling onCommunicationShutdown on AssociationListener for Association=%s", this.name), e);
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
            if (logger.isDebugEnabled()) {
                logger.debug("Association: " + this + " has been already marked UP");
            }
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
            if (logger.isDebugEnabled()) {
                logger.debug("Association: " + this + " has been already marked DOWN");
            }
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
    public void setManagement(MultiManagementImpl management) {
        this.management = management;
    }

    /**
     * @param socketChannel the socketChannel to set
     */
    protected void setSocketChannel(AbstractSelectableChannel socketChannel) {
        //
    }

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

    public void send(PayloadData payloadData) throws Exception {
        if (!started.get()) {
            throw new Exception("send failed: Association is not started");
        }
        multiplexer.send(payloadData, this.msgInfo, this);
    }

    protected boolean writePayload(PayloadData payloadData, boolean initMsg) {
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

                if (initMsg) {
                    if (this.sctpAssociation != null) {
                        msgInfo = MessageInfo.createOutgoing(sctpAssociation, initSocketAddress, seqControl);
                    } else {
                        msgInfo = MessageInfo.createOutgoing(this.initSocketAddress, seqControl);
                    }

                } else {
                    if (this.sctpAssociation != null) {
                        msgInfo = MessageInfo.createOutgoing(sctpAssociation, peerSocketAddress, seqControl);
                    } else {
                        msgInfo = MessageInfo.createOutgoing(this.peerSocketAddress, seqControl);
                    }
                }
                msgInfo.payloadProtocolID(payloadData.getPayloadProtocolId());
                msgInfo.complete(payloadData.isComplete());
                msgInfo.unordered(payloadData.isUnordered());

                logger.debug("write() - msgInfo: " + msgInfo);
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

    private int doSend() throws IOException {
        return multiplexer.getSocketMultiChannel().send(txBuffer, msgInfo);
    }

    protected void reconnect() {
        try {
            doInitiateConnectionSctp();
        } catch (Exception ex) {
            logger.warn("Error while trying to reconnect association[" + this.getName() + "]: " + ex.getMessage(), ex);
            scheduleConnect();
        }
    }

    protected void close() {
        if (multiplexer != null) {
            multiplexer.unregisterAssociation(this);
            if (logger.isDebugEnabled()) {
                logger.debug("close() - association=" + getName() + " is unregistered from the multiplexer");
            }
        }
        try {
            this.markAssociationDown();
            this.associationListener.onCommunicationShutdown(this);
        } catch (Exception e) {
            logger.error(String.format(
                    "Exception while calling onCommunicationShutdown on AssociationListener for Association=%s", this.name), e);
        }
    }

    protected AbstractSelectableChannel getSocketChannel() {
        if (this.multiplexer == null) {
            return null;
        }
        return this.multiplexer.getSocketMultiChannel();
    }

    protected void scheduleConnect() {
        if (!started.get()) {
            logger.info("Association " + name + " is not started, no need to reconnect");
            return;
        }
        if (up.get()) {
            logger.info("Associoation " + name + " is up, no need to reconnect");
        } else {
            FastList<MultiChangeRequest> pendingChanges = this.management.getPendingChanges();
            synchronized (pendingChanges) {
                pendingChanges.add(new MultiChangeRequest(null, this, MultiChangeRequest.CONNECT,
                        System.currentTimeMillis() + this.management.getConnectDelay()));
            }
        }
    }

    private void doInitiateConnectionSctp() throws IOException {
        this.multiplexer = management.getMultiChannelController().register(this);
        this.multiplexer.send(getInitPayloadData(), null, this, true);
    }

    protected void createworkerThreadTable(int maximumBooundStream) {
        this.workerThreadTable = new int[maximumBooundStream];
        this.management.populateWorkerThread(this.workerThreadTable);
    }

    @Override
    public String toString() {
        return "OneToManyAssociationImpl [hostAddress=" + hostAddress + ", hostPort=" + hostPort + ", peerAddress="
                + peerAddress + ", peerPort=" + peerPort + ", name=" + name + ", extraHostAddresses="
                + Arrays.toString(extraHostAddresses) + ", secondaryPeerAddress=" + this.secondaryPeerAddress + ", type="
                + AssociationType.CLIENT + ", started=" + started + ", up=" + up + ", management=" + management + ", msgInfo="
                + msgInfo + ", sctpAssociation=" + sctpAssociation + ", ipChannelType=" + ipChannelType + ", assocInfo="
                + assocInfo + ", multiplexer=" + multiplexer + ", ioErrors=" + ioErrors + "]";
    }

    /**
     * XML Serialization/Deserialization
     */
    protected static final XMLFormat<OneToManyAssociationImpl> ASSOCIATION_XML = new XMLFormat<OneToManyAssociationImpl>(
            OneToManyAssociationImpl.class) {

        @Override
        public void read(javolution.xml.XMLFormat.InputElement xml, OneToManyAssociationImpl association)
                throws XMLStreamException {
            association.name = xml.getAttribute(NAME, "");

            association.hostAddress = xml.getAttribute(HOST_ADDRESS, "");
            association.hostPort = xml.getAttribute(HOST_PORT, 0);

            association.peerAddress = xml.getAttribute(PEER_ADDRESS, "");
            association.peerPort = xml.getAttribute(PEER_PORT, 0);

            // association.serverName = xml.getAttribute(SERVER_NAME, "");

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

    protected void onSendFailed() {
        // if started and down then it means it is a CANT_START event and scheduleConnect must be called.
        if (started.get() && !up.get()) {
            logger.warn("Association=" + getName() + " CANT_START, trying to reconnect...");
            switchInitSocketAddress();
            scheduleConnect();
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
}
