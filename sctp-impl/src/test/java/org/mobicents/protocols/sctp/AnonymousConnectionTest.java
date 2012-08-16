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

import static org.junit.Assert.*;
import java.util.Arrays;
import javolution.util.FastList;
import org.apache.log4j.Logger;
import org.mobicents.protocols.api.Association;
import org.mobicents.protocols.api.AssociationListener;
import org.mobicents.protocols.api.IpChannelType;
import org.mobicents.protocols.api.PayloadData;
import org.mobicents.protocols.api.Server;
import org.mobicents.protocols.api.ServerListener;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * 
 * @author sergey vetyutnev
 * 
 */
public class AnonymousConnectionTest implements ServerListener {
	private static final String SERVER_NAME = "testserver";
	private static final String SERVER_HOST = "127.0.0.1";
	private static final int SERVER_PORT = 2354;

	private static final String CLIENT_ASSOCIATION_NAME1 = "clientAssociation1";
	private static final String CLIENT_ASSOCIATION_NAME2 = "clientAssociation2";
	private static final String CLIENT_ASSOCIATION_NAME3 = "clientAssociation3";

	private static final String CLIENT_HOST = "127.0.0.1";
//	private static final int CLIENT_PORT1 = 2355;
	private static final int CLIENT_PORT1 = 0;
	private static final int CLIENT_PORT2 = 0;
	private static final int CLIENT_PORT3 = 0;

	private final byte[] CLIENT_MESSAGE = "Client says Hi".getBytes();
	private final byte[] CLIENT_MESSAGE2 = "Client says Hi ones moew".getBytes();
	private final byte[] SERVER_MESSAGE = "Server says Hi".getBytes();
	
	private final int CONNECT_DELAY = 4000;

	private ManagementImpl management = null;
	private ManagementImpl management2 = null;
	private ManagementImpl management3 = null;

	// private Management managementClient = null;
	private ServerImpl server = null;

	private AssociationImpl clientAssociation1 = null;
	private AssociationImpl clientAssociation2 = null;
	private AssociationImpl clientAssociation3 = null;

	private boolean rejectConn;
	
	private AssociationData assDataClt1; 
	private AssociationData assDataClt2; 
	private AssociationData assDataClt3; 
	private FastList<AssociationData> assDataSrv = new FastList<AssociationData>(); 

	@BeforeClass
	public static void setUpClass() throws Exception {
	}

	@AfterClass
	public static void tearDownClass() throws Exception {
	}

	public void setUp(IpChannelType ipChannelType) throws Exception {

		this.management = new ManagementImpl("server-management");
		this.management.setConnectDelay(CONNECT_DELAY);// Try connecting every x secs
		this.management.setSingleThread(true);
		this.management.start();
		this.management.removeAllResourses();

		this.management2 = new ManagementImpl("server-management2");
		this.management2.setConnectDelay(CONNECT_DELAY);// Try connecting every x secs
		this.management2.setSingleThread(true);
		this.management2.start();
		this.management2.removeAllResourses();

		this.management3 = new ManagementImpl("server-management3");
		this.management3.setConnectDelay(CONNECT_DELAY);// Try connecting every x secs
		this.management3.setSingleThread(true);
		this.management3.start();
		this.management3.removeAllResourses();

		this.server = this.management.addServer(SERVER_NAME, SERVER_HOST, SERVER_PORT, ipChannelType, true, 2, null);
		this.clientAssociation1 = this.management.addAssociation(CLIENT_HOST, CLIENT_PORT1, SERVER_HOST, SERVER_PORT, CLIENT_ASSOCIATION_NAME1, ipChannelType,
				null);
		this.clientAssociation2 = this.management2.addAssociation(CLIENT_HOST, CLIENT_PORT2, SERVER_HOST, SERVER_PORT, CLIENT_ASSOCIATION_NAME2, ipChannelType,
				null);
		this.clientAssociation3 = this.management3.addAssociation(CLIENT_HOST, CLIENT_PORT3, SERVER_HOST, SERVER_PORT, CLIENT_ASSOCIATION_NAME3, ipChannelType,
				null);
	}

	public void tearDown() throws Exception {

		this.management.removeAssociation(CLIENT_ASSOCIATION_NAME1);
		this.management2.removeAssociation(CLIENT_ASSOCIATION_NAME2);
		this.management3.removeAssociation(CLIENT_ASSOCIATION_NAME3);
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

		this.assDataClt1 = new AssociationData();
		this.assDataClt2 = new AssociationData();
		this.assDataClt3 = new AssociationData();
		this.assDataSrv.clear();
		this.clientAssociation1.setAssociationListener(new ClientAssociationListener(this.assDataClt1));
		this.clientAssociation2.setAssociationListener(new ClientAssociationListener(this.assDataClt2));
		this.clientAssociation3.setAssociationListener(new ClientAssociationListener(this.assDataClt3));


		// test1 - do not accept because of ServerListener is absent
		this.management.setServerListener(null);
		this.management.startAssociation(CLIENT_ASSOCIATION_NAME1);
		Thread.sleep(CONNECT_DELAY + 2000);
		assertEquals(this.server.anonymAssociations.size(), 0);
		assertEquals(this.assDataSrv.size(), 0);
		assertTrue(this.assDataClt1.assocUp);
		this.assDataClt1.assocUp = false;
		assertTrue(this.assDataClt1.assocDown);
		this.assDataClt1.assocDown = false;
		this.management.stopAssociation(CLIENT_ASSOCIATION_NAME1);
		assertFalse(this.clientAssociation1.isConnected());
		assertFalse(this.clientAssociation2.isConnected());
		assertFalse(this.clientAssociation3.isConnected());
		Thread.sleep(500);

		// test2 - a server reject the first connection
		this.management.setServerListener(this);
		this.rejectConn = true;
		this.management.startAssociation(CLIENT_ASSOCIATION_NAME1);
		Thread.sleep(CONNECT_DELAY + 2000);
		assertEquals(this.server.anonymAssociations.size(), 0);
		assertEquals(this.assDataSrv.size(), 0);
		assertTrue(this.assDataClt1.assocUp);
		this.assDataClt1.assocUp = false;
		assertTrue(this.assDataClt1.assocDown);
		this.assDataClt1.assocDown = false;
		assertNull(this.assDataClt1.rsvdMsg);
		this.management.stopAssociation(CLIENT_ASSOCIATION_NAME1);
		assertFalse(this.clientAssociation1.isConnected());
		assertFalse(this.clientAssociation2.isConnected());
		assertFalse(this.clientAssociation3.isConnected());
		Thread.sleep(500);

		// test3 - successfully establishing conn1 + transfer data
		this.rejectConn = false;
		this.management.startAssociation(CLIENT_ASSOCIATION_NAME1);
		for (int i1 = 0; i1 < 15; i1++) {
			if (this.server.anonymAssociations.size() > 0)
				break;
			Thread.sleep(1000 * 1);
		}
		Thread.sleep(1000 * 1);
		
		assertEquals(this.server.anonymAssociations.size(), 1);
		assertEquals(this.assDataSrv.size(), 1);
		assertTrue(this.assDataClt1.assocUp);
		assertFalse(this.assDataClt1.assocDown);
		assertFalse(this.assDataClt2.assocUp);
		assertFalse(this.assDataClt2.assocDown);
		assertTrue(this.assDataSrv.get(0).assocUp);
		assertFalse(this.assDataSrv.get(0).assocDown);
		assertTrue(this.clientAssociation1.isConnected());
		assertFalse(this.clientAssociation2.isConnected());
		assertFalse(this.clientAssociation3.isConnected());
		assertTrue(this.assDataSrv.get(0).ass.isConnected());
		assertTrue(this.assDataSrv.get(0).ass.isStarted());
		assertTrue(Arrays.equals(SERVER_MESSAGE, this.assDataClt1.rsvdMsg));
		assertTrue(Arrays.equals(CLIENT_MESSAGE, this.assDataSrv.get(0).rsvdMsg));

		// test4 - successfully establishing conn2 + transfer data
		this.management2.startAssociation(CLIENT_ASSOCIATION_NAME2);
		for (int i1 = 0; i1 < 15; i1++) {
			if (this.server.anonymAssociations.size() > 1)
				break;
			Thread.sleep(1000 * 1);
		}
		Thread.sleep(1000 * 1);

		assertEquals(this.server.anonymAssociations.size(), 2);
		assertEquals(this.assDataSrv.size(), 2);
		assertTrue(this.assDataClt1.assocUp);
		assertFalse(this.assDataClt1.assocDown);
		assertTrue(this.assDataClt2.assocUp);
		assertFalse(this.assDataClt2.assocDown);
		assertFalse(this.assDataClt3.assocUp);
		assertFalse(this.assDataClt3.assocDown);
		assertTrue(this.assDataSrv.get(0).assocUp);
		assertFalse(this.assDataSrv.get(0).assocDown);
		assertTrue(this.assDataSrv.get(1).assocUp);
		assertFalse(this.assDataSrv.get(1).assocDown);
		assertTrue(this.clientAssociation1.isConnected());
		assertTrue(this.clientAssociation2.isConnected());
		assertFalse(this.clientAssociation3.isConnected());
		assertTrue(this.assDataSrv.get(0).ass.isConnected());
		assertTrue(this.assDataSrv.get(0).ass.isStarted());
		assertTrue(this.assDataSrv.get(1).ass.isConnected());
		assertTrue(this.assDataSrv.get(1).ass.isStarted());
		assertTrue(Arrays.equals(SERVER_MESSAGE, this.assDataClt2.rsvdMsg));
		assertTrue(Arrays.equals(CLIENT_MESSAGE, this.assDataSrv.get(1).rsvdMsg));

		// test5 - rejecting the third connection if the connection limit is 2
		this.management3.startAssociation(CLIENT_ASSOCIATION_NAME3);
		Thread.sleep(CONNECT_DELAY + 2000);

		assertEquals(this.server.anonymAssociations.size(), 2);
		assertEquals(this.assDataSrv.size(), 2);
		assertTrue(this.assDataClt1.assocUp);
		assertFalse(this.assDataClt1.assocDown);
		assertTrue(this.assDataClt2.assocUp);
		assertFalse(this.assDataClt2.assocDown);
		assertTrue(this.assDataClt3.assocUp);
		assertTrue(this.assDataClt3.assocDown);
		assertTrue(this.assDataSrv.get(0).assocUp);
		assertFalse(this.assDataSrv.get(0).assocDown);
		assertTrue(this.assDataSrv.get(1).assocUp);
		assertFalse(this.assDataSrv.get(1).assocDown);
		assertTrue(this.clientAssociation1.isConnected());
		assertTrue(this.clientAssociation2.isConnected());
		assertFalse(this.clientAssociation3.isConnected());
		assertTrue(this.assDataSrv.get(0).ass.isConnected());
		assertTrue(this.assDataSrv.get(0).ass.isStarted());
		assertTrue(this.assDataSrv.get(1).ass.isConnected());
		assertTrue(this.assDataSrv.get(1).ass.isStarted());
		this.management3.stopAssociation(CLIENT_ASSOCIATION_NAME3);
		Thread.sleep(500);

		// test6 - close conn1 from client side
		this.management.stopAssociation(CLIENT_ASSOCIATION_NAME1);
		Thread.sleep(500);

		assertEquals(this.server.anonymAssociations.size(), 1);
		assertEquals(this.assDataSrv.size(), 2);
		assertTrue(this.assDataClt1.assocUp);
		assertTrue(this.assDataClt1.assocDown);
		assertTrue(this.assDataClt2.assocUp);
		assertFalse(this.assDataClt2.assocDown);
		assertTrue(this.assDataClt3.assocUp);
		assertTrue(this.assDataClt3.assocDown);
		assertTrue(this.assDataSrv.get(0).assocUp);
		assertTrue(this.assDataSrv.get(0).assocDown);
		assertTrue(this.assDataSrv.get(1).assocUp);
		assertFalse(this.assDataSrv.get(1).assocDown);
		assertFalse(this.clientAssociation1.isConnected());
		assertTrue(this.clientAssociation2.isConnected());
		assertFalse(this.clientAssociation3.isConnected());
		assertFalse(this.assDataSrv.get(0).ass.isConnected());
		assertTrue(this.assDataSrv.get(0).ass.isStarted()); // !!!
		assertTrue(this.assDataSrv.get(1).ass.isConnected());
		assertTrue(this.assDataSrv.get(1).ass.isStarted());

		// test7 - transfer data via conn2
		PayloadData pd = new PayloadData(CLIENT_MESSAGE2.length, CLIENT_MESSAGE2, true, false, 3, 1);
		this.clientAssociation2.send(pd);
		Thread.sleep(500);

		assertEquals(this.server.anonymAssociations.size(), 1);
		assertEquals(this.assDataSrv.size(), 2);
		assertTrue(this.assDataClt1.assocUp);
		assertTrue(this.assDataClt1.assocDown);
		assertTrue(this.assDataClt2.assocUp);
		assertFalse(this.assDataClt2.assocDown);
		assertTrue(this.assDataClt3.assocUp);
		assertTrue(this.assDataClt3.assocDown);
		assertTrue(this.assDataSrv.get(0).assocUp);
		assertTrue(this.assDataSrv.get(0).assocDown);
		assertTrue(this.assDataSrv.get(1).assocUp);
		assertFalse(this.assDataSrv.get(1).assocDown);
		assertFalse(this.clientAssociation1.isConnected());
		assertTrue(this.clientAssociation2.isConnected());
		assertFalse(this.clientAssociation3.isConnected());
		assertFalse(this.assDataSrv.get(0).ass.isConnected());
		assertTrue(this.assDataSrv.get(0).ass.isStarted()); // !!!
		assertTrue(this.assDataSrv.get(1).ass.isConnected());
		assertTrue(this.assDataSrv.get(1).ass.isStarted());
		assertTrue(Arrays.equals(SERVER_MESSAGE, this.assDataClt2.rsvdMsg));
		assertTrue(Arrays.equals(CLIENT_MESSAGE2, this.assDataSrv.get(1).rsvdMsg));

		// test8 - close conn2 from server side
		this.assDataSrv.get(1).ass.stopAnonymousAssociation();
		Thread.sleep(500);

		assertEquals(this.server.anonymAssociations.size(), 0);
		assertEquals(this.assDataSrv.size(), 2);
		assertTrue(this.assDataClt1.assocUp);
		assertTrue(this.assDataClt1.assocDown);
		assertTrue(this.assDataClt2.assocUp);
		assertTrue(this.assDataClt2.assocDown);
		assertTrue(this.assDataClt3.assocUp);
		assertTrue(this.assDataClt3.assocDown);
		assertTrue(this.assDataSrv.get(0).assocUp);
		assertTrue(this.assDataSrv.get(0).assocDown);
		assertTrue(this.assDataSrv.get(1).assocUp);
		assertTrue(this.assDataSrv.get(1).assocDown);
		assertFalse(this.clientAssociation1.isConnected());
		assertFalse(this.clientAssociation2.isConnected());
		assertFalse(this.clientAssociation3.isConnected());
		assertFalse(this.assDataSrv.get(0).ass.isConnected());
		assertTrue(this.assDataSrv.get(0).ass.isStarted()); // !!!
		assertFalse(this.assDataSrv.get(1).ass.isConnected());
		assertFalse(this.assDataSrv.get(1).ass.isStarted());
		this.management2.stopAssociation(CLIENT_ASSOCIATION_NAME2);

		this.management.stopServer(SERVER_NAME);

		this.tearDown();
	}

	private class ServerAssociationListener implements AssociationListener {

		private final Logger logger = Logger.getLogger(ClientAssociationListener.class);
		AssociationData assData;

		ServerAssociationListener(AssociationData assData) {
			this.assData = assData;
		}

		@Override
		public void onCommunicationUp(Association association, int maxInboundStreams, int maxOutboundStreams) {
			System.out.println(this + " onCommunicationUp");
			
			assData.assocUp = true;

			PayloadData payloadData = new PayloadData(SERVER_MESSAGE.length, SERVER_MESSAGE, true, false, 3, 1);

			try {
				association.send(payloadData);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		@Override
		public void onCommunicationShutdown(Association association) {
			System.out.println(this + " onCommunicationShutdown");
			assData.assocDown = true;
		}

		@Override
		public void onCommunicationLost(Association association) {
			System.out.println(this + " onCommunicationLost");
		}

		@Override
		public void onCommunicationRestart(Association association) {
			System.out.println(this + " onCommunicationRestart");
		}

		@Override
		public void onPayload(Association association, PayloadData payloadData) {
			assData.rsvdMsg = new byte[payloadData.getDataLength()];
			System.arraycopy(payloadData.getData(), 0, assData.rsvdMsg, 0, payloadData.getDataLength());
			logger.debug("SERVER received " + new String(assData.rsvdMsg));
		}

		@Override
		public void inValidStreamId(PayloadData payloadData) {
			// TODO Auto-generated method stub
			
		}
	}

	private class ClientAssociationListener implements AssociationListener {

		AssociationData assData;

		ClientAssociationListener(AssociationData assData) {
			this.assData = assData;
		}

		private final Logger logger = Logger.getLogger(ClientAssociationListener.class);

		@Override
		public void onCommunicationUp(Association association, int maxInboundStreams, int maxOutboundStreams) {
			System.out.println(this + " onCommunicationUp");
			
			assData.assocUp = true;

			PayloadData payloadData = new PayloadData(CLIENT_MESSAGE.length, CLIENT_MESSAGE, true, false, 3, 1);

			try {
				association.send(payloadData);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		@Override
		public void onCommunicationShutdown(Association association) {
			System.out.println(this + " onCommunicationShutdown");
			assData.assocDown = true;
		}

		@Override
		public void onCommunicationLost(Association association) {
			System.out.println(this + " onCommunicationLost");
		}

		@Override
		public void onCommunicationRestart(Association association) {
			System.out.println(this + " onCommunicationRestart");
		}

		@Override
		public void onPayload(Association association, PayloadData payloadData) {
			assData.rsvdMsg = new byte[payloadData.getDataLength()];
			System.arraycopy(payloadData.getData(), 0, assData.rsvdMsg, 0, payloadData.getDataLength());
			logger.debug("CLIENT received " + new String(assData.rsvdMsg));
		}

		@Override
		public void inValidStreamId(PayloadData payloadData) {
			// TODO Auto-generated method stub
			
		}
	}

	private class AssociationData {
		public boolean assocUp = false;
		public boolean assocDown = false;
		public byte[] rsvdMsg;
		public Association ass;
	}
	
	@Override
	public void onNewRemoteConnection(Server server, Association association) {
		if (this.rejectConn) {
			association.rejectAnonymousAssociation();
		} else {
			try {
				AssociationData ad = new AssociationData();
				this.assDataSrv.add(ad);
				ad.ass = association;
				association.acceptAnonymousAssociation(new ServerAssociationListener(ad));
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
}
