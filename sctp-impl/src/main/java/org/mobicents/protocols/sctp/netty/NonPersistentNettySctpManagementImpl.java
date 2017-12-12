package org.mobicents.protocols.sctp.netty;

import java.io.IOException;

public class NonPersistentNettySctpManagementImpl extends NettySctpManagementImpl {

	public NonPersistentNettySctpManagementImpl(String name) throws IOException {
		super(name);

	}
	
	@Override
	protected void load() {
	
	}
	
	@Override
	public void store() {
	
	}

}
