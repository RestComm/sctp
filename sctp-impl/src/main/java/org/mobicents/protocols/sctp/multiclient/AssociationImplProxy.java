package org.mobicents.protocols.sctp.multiclient;

import org.mobicents.protocols.api.AssociationListener;
import org.mobicents.protocols.api.AssociationType;
import org.mobicents.protocols.api.IpChannelType;
import org.mobicents.protocols.api.PayloadData;

public class AssociationImplProxy extends ManageableAssociation {
	private ManageableAssociation delagate;

	public AssociationImplProxy(ManageableAssociation delagate) {
		this.delagate = delagate;
	}
	
	/**
	 * @return the delagate
	 */
	protected ManageableAssociation getDelagate() {
		return delagate;
	}

	/**
	 * @param delagate the delagate to set
	 */
	protected void setDelagate(ManageableAssociation delagate) {
		this.delagate = delagate;
	}

	
	/**
	 * @param management
	 * @see org.mobicents.protocols.sctp.multiclient.ManageableAssociation#setManagement(org.mobicents.protocols.sctp.multiclient.MultiManagementImpl)
	 */
	protected void setManagement(MultiManagementImpl management) {
		delagate.setManagement(management);
	}



	/**
	 * @throws Exception
	 * @see org.mobicents.protocols.sctp.multiclient.ManageableAssociation#start()
	 */
	protected void start() throws Exception {
		delagate.start();
	}

	/**
	 * @throws Exception
	 * @see org.mobicents.protocols.sctp.multiclient.ManageableAssociation#stop()
	 */
	protected void stop() throws Exception {
		delagate.stop();
	}

	/**
	 * @return
	 * @see org.mobicents.protocols.api.Association#getIpChannelType()
	 */
	public IpChannelType getIpChannelType() {
		return delagate.getIpChannelType();
	}

	/**
	 * @return
	 * @see org.mobicents.protocols.api.Association#getAssociationType()
	 */
	public AssociationType getAssociationType() {
		return delagate.getAssociationType();
	}

	/**
	 * @return
	 * @see org.mobicents.protocols.api.Association#getName()
	 */
	public String getName() {
		return delagate.getName();
	}

	/**
	 * @return
	 * @see org.mobicents.protocols.api.Association#isStarted()
	 */
	public boolean isStarted() {
		return delagate.isStarted();
	}

	/**
	 * @return
	 * @see org.mobicents.protocols.api.Association#isConnected()
	 */
	public boolean isConnected() {
		return delagate.isConnected();
	}

	/**
	 * @return
	 * @see org.mobicents.protocols.api.Association#isUp()
	 */
	public boolean isUp() {
		return delagate.isUp();
	}

	/**
	 * @return
	 * @see org.mobicents.protocols.api.Association#getAssociationListener()
	 */
	public AssociationListener getAssociationListener() {
		return delagate.getAssociationListener();
	}

	/**
	 * @param associationListener
	 * @see org.mobicents.protocols.api.Association#setAssociationListener(org.mobicents.protocols.api.AssociationListener)
	 */
	public void setAssociationListener(AssociationListener associationListener) {
		delagate.setAssociationListener(associationListener);
	}

	/**
	 * @return
	 * @see org.mobicents.protocols.api.Association#getHostAddress()
	 */
	public String getHostAddress() {
		return delagate.getHostAddress();
	}

	/**
	 * @return
	 * @see org.mobicents.protocols.api.Association#getHostPort()
	 */
	public int getHostPort() {
		return delagate.getHostPort();
	}

	/**
	 * @return
	 * @see org.mobicents.protocols.api.Association#getPeerAddress()
	 */
	public String getPeerAddress() {
		return delagate.getPeerAddress();
	}

	/**
	 * @return
	 * @see org.mobicents.protocols.api.Association#getPeerPort()
	 */
	public int getPeerPort() {
		return delagate.getPeerPort();
	}

	/**
	 * @return
	 * @see org.mobicents.protocols.api.Association#getServerName()
	 */
	public String getServerName() {
		return delagate.getServerName();
	}

	/**
	 * @return
	 * @see org.mobicents.protocols.api.Association#getExtraHostAddresses()
	 */
	public String[] getExtraHostAddresses() {
		return delagate.getExtraHostAddresses();
	}

	/**
	 * @param payloadData
	 * @throws Exception
	 * @see org.mobicents.protocols.api.Association#send(org.mobicents.protocols.api.PayloadData)
	 */
	public void send(PayloadData payloadData) throws Exception {
		delagate.send(payloadData);
	}

	/**
	 * @param associationListener
	 * @throws Exception
	 * @see org.mobicents.protocols.api.Association#acceptAnonymousAssociation(org.mobicents.protocols.api.AssociationListener)
	 */
	public void acceptAnonymousAssociation(
			AssociationListener associationListener) throws Exception {
		delagate.acceptAnonymousAssociation(associationListener);
	}

	/**
	 * 
	 * @see org.mobicents.protocols.api.Association#rejectAnonymousAssociation()
	 */
	public void rejectAnonymousAssociation() {
		delagate.rejectAnonymousAssociation();
	}

	/**
	 * @throws Exception
	 * @see org.mobicents.protocols.api.Association#stopAnonymousAssociation()
	 */
	public void stopAnonymousAssociation() throws Exception {
		delagate.stopAnonymousAssociation();
	}
}
