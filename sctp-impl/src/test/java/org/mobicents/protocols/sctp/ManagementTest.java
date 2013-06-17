/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011, Red Hat, Inc. and/or its affiliates, and individual
 * contributors as indicated by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a full listing
 * of individual contributors.
 * 
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU General Public License, v. 2.0.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of 
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU 
 * General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License,
 * v. 2.0 along with this distribution; if not, write to the Free 
 * Software Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301, USA.
 */
package org.mobicents.protocols.sctp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import java.util.List;
import java.util.Map;

import org.mobicents.protocols.api.Association;
import org.mobicents.protocols.api.AssociationListener;
import org.mobicents.protocols.api.IpChannelType;
import org.mobicents.protocols.api.PayloadData;
import org.mobicents.protocols.api.Server;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * @author amit bhayani
 * 
 */
public class ManagementTest {

	private static final String SERVER_NAME = "testserver";
	private static final String SERVER_HOST = "127.0.0.1";
	private static final int SERVER_PORT = 2349;
	private static final String CLIENT_HOST = "127.0.0.1";
	private static final int CLIENT_PORT = 2352;
	private static final String SERVER_ASSOCIATION_NAME = "serverAssociation";
	private static final String CLIENT_ASSOCIATION_NAME = "clientAssociation";

	@BeforeClass
	public static void setUpClass() throws Exception {
	}

	@AfterClass
	public static void tearDownClass() throws Exception {
	}

	public void setUp() throws Exception {

	}

	public void tearDown() throws Exception {

	}

	/**
	 * Test the creation of Server. Stop management and start, and Server should
	 * be started automatically
	 * 
	 * @throws Exception
	 */
	@Test(groups = { "functional", "sctp" })
	public void testServerSctp() throws Exception {
		
		if (SctpTransferTest.checkSctpEnabled())
			this.testServerByProtocol(IpChannelType.SCTP);
	}

	/**
	 * Test the creation of Server. Stop management and start, and Server should
	 * be started automatically
	 * 
	 * @throws Exception
	 */
	@Test(groups = { "functional", "tcp" })
	public void testServerTcp() throws Exception {

		this.testServerByProtocol(IpChannelType.TCP);
	}

	private void testServerByProtocol(IpChannelType ipChannelType) throws Exception {
		ManagementImpl management = new ManagementImpl("ManagementTest");
		management.setSingleThread(true);
		management.start();
		management.removeAllResourses();

		String[] arr = new String[]{"127.0.0.2", "127.0.0.3"};
		Server server = management.addServer(SERVER_NAME, SERVER_HOST, SERVER_PORT, ipChannelType, true, 5, arr);

		management.startServer(SERVER_NAME);

		assertTrue(server.isStarted());

		management.stop();

		management = new ManagementImpl("ManagementTest");
		// start again
		management.start();

		List<Server> servers = management.getServers();
		assertEquals(1, servers.size());

		server = servers.get(0);
		assertTrue(server.isStarted());

		// Add association
		management.addServerAssociation(CLIENT_HOST, CLIENT_PORT, SERVER_NAME, SERVER_ASSOCIATION_NAME, ipChannelType);

		assertEquals(management.getAssociations().size(), 1);
		
		management.stopServer(SERVER_NAME);
		
		// Try to delete and it should throw error
		try {
			management.removeServer(SERVER_NAME);
			fail("Expected Exception");
		} catch (Exception e) {
			assertEquals("Server=testserver has Associations. Remove all those Associations before removing Server", e.getMessage());
		}
		
		//Try removing Association now
		// Remove Assoc
		management.removeAssociation(SERVER_ASSOCIATION_NAME);		

		management.removeServer(SERVER_NAME);

		servers = management.getServers();
		assertEquals(0, servers.size());

		management.stop();

	}

	@Test(groups = { "functional", "sctp" })
	public void testAssociationSctp() throws Exception {
		
		if (SctpTransferTest.checkSctpEnabled())
			this.testAssociationByProtocol(IpChannelType.SCTP);
	}

	@Test(groups = { "functional", "tcp" })
	public void testAssociationTcp() throws Exception {

		this.testAssociationByProtocol(IpChannelType.TCP);
	}

	private void testAssociationByProtocol(IpChannelType ipChannelType) throws Exception {
		ManagementImpl management = new ManagementImpl("ManagementTest");
		management.setSingleThread(true);
		management.start();
		management.removeAllResourses();

		// Add association
		String[] arr = new String[]{"127.0.0.2", "127.0.0.3"};
		Association clientAss1 = management.addAssociation("localhost", 2905, "localhost", 2906, "ClientAssoc1", ipChannelType, arr);
		assertNotNull(clientAss1);

		// Try to add assoc with same name
		try {
			clientAss1 = management.addAssociation("localhost", 2907, "localhost", 2908, "ClientAssoc1", ipChannelType, null);
			fail("Expected Exception");
		} catch (Exception e) {
			assertEquals("Already has association=ClientAssoc1", e.getMessage());
		}

		// Try to add assoc with same peer add and port
		try {
			clientAss1 = management.addAssociation("localhost", 2907, "localhost", 2906, "ClientAssoc2", ipChannelType, null);
			fail("Expected Exception");
		} catch (Exception e) {
			assertEquals("Already has association=ClientAssoc1 with same peer address=localhost and port=2906", e.getMessage());
		}

		// Try to add assoc with same host add and port
		try {
			clientAss1 = management.addAssociation("localhost", 2905, "localhost", 2908, "ClientAssoc2", ipChannelType, null);
			fail("Expected Exception");
		} catch (Exception e) {
			assertEquals("Already has association=ClientAssoc1 with same host address=localhost and port=2905", e.getMessage());
		}
		
		//Test Serialization.
		management.stop();
		
		management = new ManagementImpl("ManagementTest");
		// start again
		management.start();
		
		Map<String, Association> associations  = management.getAssociations();
		
		assertEquals(associations.size(), 1);

		// Remove Assoc
		management.removeAssociation("ClientAssoc1");

		management.stop();
	}
	

	@Test(groups = { "functional", "sctp" })
	public void testStopAssociationSctp() throws Exception {
		
		if (SctpTransferTest.checkSctpEnabled())
			this.testStopAssociationByProtocol(IpChannelType.SCTP);
	}

	@Test(groups = { "functional", "tcp" })
	public void testStopAssociationTcp() throws Exception {

		this.testStopAssociationByProtocol(IpChannelType.TCP);
	}

	private void testStopAssociationByProtocol(IpChannelType ipChannelType) throws Exception {

		ManagementImpl management = new ManagementImpl("ManagementTest");
		management.setSingleThread(true);
		management.setConnectDelay(10000);// Try connecting every 10 secs
		management.start();
		management.removeAllResourses();

		management.addServer(SERVER_NAME, SERVER_HOST, SERVER_PORT, ipChannelType, false, 0, null);
		Association serverAssociation = management.addServerAssociation(CLIENT_HOST, CLIENT_PORT, SERVER_NAME, SERVER_ASSOCIATION_NAME, ipChannelType);
		Association clientAssociation = management.addAssociation(CLIENT_HOST, CLIENT_PORT, SERVER_HOST, SERVER_PORT, CLIENT_ASSOCIATION_NAME, ipChannelType, null);

		management.startServer(SERVER_NAME);


		serverAssociation.setAssociationListener(new ServerAssociationListener());
		management.startAssociation(SERVER_ASSOCIATION_NAME);
		clientAssociation.setAssociationListener(new ClientAssociationListener());
		management.startAssociation(CLIENT_ASSOCIATION_NAME);

		for (int i1 = 0; i1 < 40; i1++) {
			if (serverAssociation.isConnected())
				break;
			Thread.sleep(1000 * 5);
		}
		Thread.sleep(1000 * 1);

		assertTrue(serverAssociation.isConnected());
		assertTrue(clientAssociation.isConnected());

		management.stop();

		assertFalse(serverAssociation.isConnected());
		assertFalse(clientAssociation.isConnected());

	}

	private class ClientAssociationListener implements AssociationListener {

		@Override
		public void onCommunicationUp(Association association, int maxInboundStreams, int maxOutboundStreams) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void onCommunicationShutdown(Association association) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void onCommunicationLost(Association association) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void onCommunicationRestart(Association association) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void onPayload(Association association, PayloadData payloadData) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void inValidStreamId(PayloadData payloadData) {
			// TODO Auto-generated method stub
			
		}
	}

	private class ServerAssociationListener implements AssociationListener {

		@Override
		public void onCommunicationUp(Association association, int maxInboundStreams, int maxOutboundStreams) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void onCommunicationShutdown(Association association) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void onCommunicationLost(Association association) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void onCommunicationRestart(Association association) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void onPayload(Association association, PayloadData payloadData) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void inValidStreamId(PayloadData payloadData) {
			// TODO Auto-generated method stub
			
		}
	}
}

