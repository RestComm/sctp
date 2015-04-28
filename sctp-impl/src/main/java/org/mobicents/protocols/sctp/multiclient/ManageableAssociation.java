package org.mobicents.protocols.sctp.multiclient;

import org.mobicents.protocols.api.Association;

import com.sun.nio.sctp.AbstractNotificationHandler;

public abstract class ManageableAssociation implements Association {
	protected abstract void setManagement(MultiManagementImpl management);
	protected abstract void start() throws Exception;
	protected abstract void stop() throws Exception;
}
