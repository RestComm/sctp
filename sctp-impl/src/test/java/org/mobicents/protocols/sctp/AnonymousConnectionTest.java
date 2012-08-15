/*
 * TeleStax, Open Source Cloud Communications  Copyright 2012.
 * and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.mobicents.protocols.sctp;

import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.mobicents.protocols.api.IpChannelType;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * 
 * @author sergey vetyutnev
 * 
 */
public class AnonymousConnectionTest {
	private static final String SERVER_NAME = "testserver";
	private static final String SERVER_HOST = "127.0.0.1";
	private static final int SERVER_PORT = 2354;

//	private static final String SERVER_ASSOCIATION_NAME = "serverAssociation";
	private static final String CLIENT_ASSOCIATION_NAME1 = "clientAssociation1";
	private static final String CLIENT_ASSOCIATION_NAME2 = "clientAssociation1";

	private static final String CLIENT_HOST = "127.0.0.1";
	private static final int CLIENT_PORT1 = 2355;
	private static final int CLIENT_PORT2 = 2356;

	private final byte[] CLIENT_MESSAGE = "Client says Hi".getBytes();
	private final byte[] SERVER_MESSAGE = "Server says Hi".getBytes();

	private ManagementImpl management = null;

	// private Management managementClient = null;
	private ServerImpl server = null;

//	private AssociationImpl serverAssociation = null;
	private AssociationImpl clientAssociation1 = null;
	private AssociationImpl clientAssociation2 = null;

//	private volatile boolean clientAssocUp = false;
//	private volatile boolean serverAssocUp = false;
//
//	private volatile boolean clientAssocDown = false;
//	private volatile boolean serverAssocDown = false;

//	private byte[] clientMessage;
//	private byte[] serverMessage;
//	
//	private volatile int clientMaxInboundStreams = 0;
//	private volatile int clientMaxOutboundStreams = 0;
//	
//	private volatile int serverMaxInboundStreams = 0;
//	private volatile int serverMaxOutboundStreams = 0;

	@BeforeClass
	public static void setUpClass() throws Exception {
	}

	@AfterClass
	public static void tearDownClass() throws Exception {
	}

	public void setUp(IpChannelType ipChannelType) throws Exception {
//		this.clientMaxInboundStreams = 0;
//		this.serverMaxOutboundStreams = 0;
//		
//		this.clientAssocUp = false;
//		this.serverAssocUp = false;
//
//		this.clientAssocDown = false;
//		this.serverAssocDown = false;
//
//		this.clientMessage = null;
//		this.serverMessage = null;

		this.management = new ManagementImpl("server-management");
		this.management.setConnectDelay(10000);// Try connecting every 10 secs
		this.management.setSingleThread(true);
		this.management.start();
		this.management.removeAllResourses();

		this.server = this.management.addServer(SERVER_NAME, SERVER_HOST, SERVER_PORT, ipChannelType, true, 2, null);
//		this.serverAssociation = this.management.addServerAssociation(CLIENT_HOST, CLIENT_PORT, SERVER_NAME, SERVER_ASSOCIATION_NAME, ipChannelType);
		this.clientAssociation1 = this.management.addAssociation(CLIENT_HOST, CLIENT_PORT1, SERVER_HOST, SERVER_PORT, CLIENT_ASSOCIATION_NAME1, ipChannelType,
				null);
		this.clientAssociation2 = this.management.addAssociation(CLIENT_HOST, CLIENT_PORT2, SERVER_HOST, SERVER_PORT, CLIENT_ASSOCIATION_NAME2, ipChannelType,
				null);
	}

	public void tearDown() throws Exception {

		this.management.removeAssociation(CLIENT_ASSOCIATION_NAME1);
		this.management.removeAssociation(CLIENT_ASSOCIATION_NAME2);
		this.management.removeServer(SERVER_NAME);

		this.management.stop();
	}

	/**
	 * Simple test that creates Client and Server Association, exchanges data
	 * and brings down association. Finally removes the Associations and Server
	 */
	@Test(groups = { "functional", "sctp" })
	public void testAnonymousSctp() throws Exception {

		if (SctpTransferTest.checkSctpEnabled())
			this.testAnonymousByProtocol(IpChannelType.SCTP);
	}

	/**
	 * Simple test that creates Client and Server Association, exchanges data
	 * and brings down association. Finally removes the Associations and Server
	 */
	@Test(groups = { "functional", "tcp" })
	public void testAnonymousTcp() throws Exception {

		// BasicConfigurator.configure();
		// Logger logger = Logger.getLogger(ServerImpl.class.getName());
		// logger.setLevel(Level.ALL);

		this.testAnonymousByProtocol(IpChannelType.TCP);
	}

	private void testAnonymousByProtocol(IpChannelType ipChannelType) throws Exception {

		this.setUp(ipChannelType);

		this.management.startServer(SERVER_NAME);

//		this.clientAssociation1.setAssociationListener(new ClientAssociationListener());
//		this.management.startAssociation(CLIENT_ASSOCIATION_NAME1);
//
//		this.clientAssociation2.setAssociationListener(new ClientAssociationListener());
//		this.management.startAssociation(CLIENT_ASSOCIATION_NAME2);

		// test1 - do not accept because of ServerListener is absent

		// test2 - a server reject the first connection

		// test3 - successfully establishing conn1 + transfer data

		// test4 - successfully establishing conn2 + transfer data

		// test5 - close conn1 from client side

		// test6 - transfer data via conn2

		// test7 - close conn2 from client side
		
		
		
		
		
		
		
		
//		for (int i1 = 0; i1 < 40; i1++) {
//			if (serverAssocUp)
//				break;
//			Thread.sleep(1000 * 5); // was: 40
//		}
//		Thread.sleep(1000 * 1); // was: 40
//
//		this.management.stopAssociation(CLIENT_ASSOCIATION_NAME);
//
//		Thread.sleep(1000);
//
//		this.management.stopAssociation(SERVER_ASSOCIATION_NAME);
//		this.management.stopServer(SERVER_NAME);
//
//		Thread.sleep(1000 * 2);
//
//		assertTrue(Arrays.equals(SERVER_MESSAGE, clientMessage));
//		assertTrue(Arrays.equals(CLIENT_MESSAGE, serverMessage));
//
//		assertTrue(clientAssocUp);
//		assertTrue(serverAssocUp);
//
//		assertTrue(clientAssocDown);
//		assertTrue(serverAssocDown);
//		
//		assertTrue(this.clientMaxInboundStreams> 0 );
//		assertTrue(this.clientMaxOutboundStreams > 0);
//		
//		assertTrue(this.serverMaxInboundStreams> 0 );
//		assertTrue(this.serverMaxOutboundStreams > 0);

		this.tearDown();
	}

}
