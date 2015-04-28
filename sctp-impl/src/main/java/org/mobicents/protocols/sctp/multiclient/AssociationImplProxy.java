package org.mobicents.protocols.sctp.multiclient;

import org.mobicents.protocols.api.AssociationListener;
import org.mobicents.protocols.api.AssociationType;
import org.mobicents.protocols.api.IpChannelType;
import org.mobicents.protocols.api.PayloadData;

public class AssociationImplProxy extends ManageableAssociation {
	private ManageableAssociation delegate;

	public AssociationImplProxy(ManageableAssociation delagate) {
		this.delegate = delagate;
	}
	
	public void hotSwapDelegate(ManageableAssociation newDelegate) {
		newDelegate.setAssociationListener(delegate.getAssociationListener());
		this.delegate = newDelegate;
	}
	
	/**
	 * @return the delagate
	 */
	protected ManageableAssociation getDelagate() {
		return delegate;
	}

	/**
	 * @param delagate the delagate to set
	 */
	protected void setDelagate(ManageableAssociation delagate) {
		this.delegate = delagate;
	}

	
	/**
	 * @param management
	 * @see org.mobicents.protocols.sctp.multiclient.ManageableAssociation#setManagement(org.mobicents.protocols.sctp.multiclient.MultiManagementImpl)
	 */
	protected void setManagement(MultiManagementImpl management) {
		delegate.setManagement(management);
	}



	/**
	 * @throws Exception
	 * @see org.mobicents.protocols.sctp.multiclient.ManageableAssociation#start()
	 */
	protected void start() throws Exception {
		delegate.start();
	}

	/**
	 * @throws Exception
	 * @see org.mobicents.protocols.sctp.multiclient.ManageableAssociation#stop()
	 */
	protected void stop() throws Exception {
		delegate.stop();
	}

	/**
	 * @return
	 * @see org.mobicents.protocols.api.Association#getIpChannelType()
	 */
	public IpChannelType getIpChannelType() {
		return delegate.getIpChannelType();
	}

	/**
	 * @return
	 * @see org.mobicents.protocols.api.Association#getAssociationType()
	 */
	public AssociationType getAssociationType() {
		return delegate.getAssociationType();
	}

	/**
	 * @return
	 * @see org.mobicents.protocols.api.Association#getName()
	 */
	public String getName() {
		return delegate.getName();
	}

	/**
	 * @return
	 * @see org.mobicents.protocols.api.Association#isStarted()
	 */
	public boolean isStarted() {
		return delegate.isStarted();
	}

	/**
	 * @return
	 * @see org.mobicents.protocols.api.Association#isConnected()
	 */
	public boolean isConnected() {
		return delegate.isConnected();
	}

	/**
	 * @return
	 * @see org.mobicents.protocols.api.Association#isUp()
	 */
	public boolean isUp() {
		return delegate.isUp();
	}

	/**
	 * @return
	 * @see org.mobicents.protocols.api.Association#getAssociationListener()
	 */
	public AssociationListener getAssociationListener() {
		return delegate.getAssociationListener();
	}

	/**
	 * @param associationListener
	 * @see org.mobicents.protocols.api.Association#setAssociationListener(org.mobicents.protocols.api.AssociationListener)
	 */
	public void setAssociationListener(AssociationListener associationListener) {
		delegate.setAssociationListener(associationListener);
	}

	/**
	 * @return
	 * @see org.mobicents.protocols.api.Association#getHostAddress()
	 */
	public String getHostAddress() {
		return delegate.getHostAddress();
	}

	/**
	 * @return
	 * @see org.mobicents.protocols.api.Association#getHostPort()
	 */
	public int getHostPort() {
		return delegate.getHostPort();
	}

	/**
	 * @return
	 * @see org.mobicents.protocols.api.Association#getPeerAddress()
	 */
	public String getPeerAddress() {
		return delegate.getPeerAddress();
	}

	/**
	 * @return
	 * @see org.mobicents.protocols.api.Association#getPeerPort()
	 */
	public int getPeerPort() {
		return delegate.getPeerPort();
	}

	/**
	 * @return
	 * @see org.mobicents.protocols.api.Association#getServerName()
	 */
	public String getServerName() {
		return delegate.getServerName();
	}

	/**
	 * @return
	 * @see org.mobicents.protocols.api.Association#getExtraHostAddresses()
	 */
	public String[] getExtraHostAddresses() {
		return delegate.getExtraHostAddresses();
	}

	/**
	 * @param payloadData
	 * @throws Exception
	 * @see org.mobicents.protocols.api.Association#send(org.mobicents.protocols.api.PayloadData)
	 */
	public void send(PayloadData payloadData) throws Exception {
		delegate.send(payloadData);
	}

	/**
	 * @param associationListener
	 * @throws Exception
	 * @see org.mobicents.protocols.api.Association#acceptAnonymousAssociation(org.mobicents.protocols.api.AssociationListener)
	 */
	public void acceptAnonymousAssociation(
			AssociationListener associationListener) throws Exception {
		delegate.acceptAnonymousAssociation(associationListener);
	}

	/**
	 * 
	 * @see org.mobicents.protocols.api.Association#rejectAnonymousAssociation()
	 */
	public void rejectAnonymousAssociation() {
		delegate.rejectAnonymousAssociation();
	}

	/**
	 * @throws Exception
	 * @see org.mobicents.protocols.api.Association#stopAnonymousAssociation()
	 */
	public void stopAnonymousAssociation() throws Exception {
		delegate.stopAnonymousAssociation();
	}
}
