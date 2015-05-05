package org.mobicents.protocols.sctp.multiclient;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.spi.AbstractSelectableChannel;

import org.mobicents.protocols.api.Association;
import org.mobicents.protocols.api.PayloadData;

import com.sun.nio.sctp.MessageInfo;

public abstract class ManageableAssociation implements Association {
	
	protected MultiManagementImpl management;
	protected String hostAddress;
	protected int hostPort;
	protected String peerAddress;
	protected int peerPort;
	protected String name;
	protected String[] extraHostAddresses;
	protected AssociationInfo assocInfo;
	
	/**
	 * This is used only for SCTP This is the socket address for peer which will
	 * be null initially. If the Association has multihome support and if peer
	 * address changes, this variable is set to new value so new messages are
	 * now sent to changed peer address
	 */
	protected volatile SocketAddress peerSocketAddress = null;
	
	protected abstract void start() throws Exception;
	protected abstract void stop() throws Exception;
	protected abstract AbstractSelectableChannel getSocketChannel();
	protected abstract void close();
	protected abstract void reconnect();
	protected abstract boolean writePayload(PayloadData payloadData);
	protected abstract void readPayload(PayloadData payloadData);
	
	
	
	protected ManageableAssociation(String hostAddress, int hostPort, String peerAddress, int peerPort, String assocName,
			String[] extraHostAddresses) throws IOException {
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
	
	protected void setManagement(MultiManagementImpl management) {
		this.management = management;
	}
	
	protected AssociationInfo getAssocInfo() {
		return assocInfo;
	}
	
	protected void setAssocInfo(AssociationInfo assocInfo) {
		this.assocInfo = assocInfo;
	}	
	
	protected void assignSctpAssociationId(int id) {
		this.assocInfo.getPeerInfo().setSctpAssocId(id);
	}
	
	protected boolean isConnectedToPeerAddresses(String peerAddresses) {
		return peerAddresses.contains(getAssocInfo().getPeerInfo().getPeerSocketAddress().toString());
	}
	
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
	
	static class SctpMessage {
		private final PayloadData payloadData;
		private final MessageInfo messageInfo;
		private final ManageableAssociation senderAssoc;
		protected SctpMessage(PayloadData payloadData, MessageInfo messageInfo, ManageableAssociation senderAssoc) {
			super();
			this.payloadData = payloadData;
			this.messageInfo = messageInfo;
			this.senderAssoc = senderAssoc;
		}
		protected PayloadData getPayloadData() {
			return payloadData;
		}
		protected MessageInfo getMessageInfo() {
			return messageInfo;
		}
		protected ManageableAssociation getSenderAssoc() {
			return senderAssoc;
		}
		@Override
		public String toString() {
			return "SctpMessage [payloadData=" + payloadData + ", messageInfo="
					+ messageInfo + ", senderAssoc=" + senderAssoc + "]";
		}
	}
}
