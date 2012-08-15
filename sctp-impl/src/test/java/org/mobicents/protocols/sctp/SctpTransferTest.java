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

import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.apache.log4j.Logger;
import org.mobicents.protocols.api.Association;
import org.mobicents.protocols.api.AssociationListener;
import org.mobicents.protocols.api.IpChannelType;
import org.mobicents.protocols.api.PayloadData;
import org.testng.annotations.*;

import com.sun.nio.sctp.SctpChannel;

/**
 * @author amit bhayani
 * 
 */
public class SctpTransferTest {
	private static final String SERVER_NAME = "testserver";
	private static final String SERVER_HOST = "127.0.0.1";
	private static final int SERVER_PORT = 2347;

	private static final String SERVER_ASSOCIATION_NAME = "serverAssociation";
	private static final String CLIENT_ASSOCIATION_NAME = "clientAssociation";

	private static final String CLIENT_HOST = "127.0.0.1";
	private static final int CLIENT_PORT = 2348;

	private final byte[] CLIENT_MESSAGE = "Client says Hi".getBytes();
	private final byte[] SERVER_MESSAGE = "Server says Hi".getBytes();

	private ManagementImpl management = null;

	// private Management managementClient = null;
	private ServerImpl server = null;

	private AssociationImpl serverAssociation = null;
	private AssociationImpl clientAssociation = null;

	private volatile boolean clientAssocUp = false;
	private volatile boolean serverAssocUp = false;

	private volatile boolean clientAssocDown = false;
	private volatile boolean serverAssocDown = false;

	private byte[] clientMessage;
	private byte[] serverMessage;
	
	private volatile int clientMaxInboundStreams = 0;
	private volatile int clientMaxOutboundStreams = 0;
	
	private volatile int serverMaxInboundStreams = 0;
	private volatile int serverMaxOutboundStreams = 0;

	@BeforeClass
	public static void setUpClass() throws Exception {
	}

	@AfterClass
	public static void tearDownClass() throws Exception {
	}

	public void setUp(IpChannelType ipChannelType) throws Exception {
		this.clientMaxInboundStreams = 0;
		this.serverMaxOutboundStreams = 0;
		
		this.clientAssocUp = false;
		this.serverAssocUp = false;

		this.clientAssocDown = false;
		this.serverAssocDown = false;

		this.clientMessage = null;
		this.serverMessage = null;

		this.management = new ManagementImpl("server-management");
		this.management.setConnectDelay(10000);// Try connecting every 10 secs
		this.management.setSingleThread(true);
		this.management.start();
		this.management.removeAllResourses();

		this.server = this.management.addServer(SERVER_NAME, SERVER_HOST, SERVER_PORT, ipChannelType, false, 0, null);
		this.serverAssociation = this.management.addServerAssociation(CLIENT_HOST, CLIENT_PORT, SERVER_NAME, SERVER_ASSOCIATION_NAME, ipChannelType);
		this.clientAssociation = this.management.addAssociation(CLIENT_HOST, CLIENT_PORT, SERVER_HOST, SERVER_PORT, CLIENT_ASSOCIATION_NAME, ipChannelType,
				null);
	}

	public void tearDown() throws Exception {

		this.management.removeAssociation(CLIENT_ASSOCIATION_NAME);
		this.management.removeAssociation(SERVER_ASSOCIATION_NAME);
		this.management.removeServer(SERVER_NAME);

		this.management.stop();
	}

	/**
	 * Simple test that creates Client and Server Association, exchanges data
	 * and brings down association. Finally removes the Associations and Server
	 */
	@Test(groups = { "functional", "sctp" })
	public void testDataTransferSctp() throws Exception {

		if (SctpTransferTest.checkSctpEnabled())
			this.testDataTransferByProtocol(IpChannelType.SCTP);
	}

	/**
	 * Simple test that creates Client and Server Association, exchanges data
	 * and brings down association. Finally removes the Associations and Server
	 */
	@Test(groups = { "functional", "tcp" })
	public void testDataTransferTcp() throws Exception {

		// BasicConfigurator.configure();
		// Logger logger = Logger.getLogger(ServerImpl.class.getName());
		// logger.setLevel(Level.ALL);

		this.testDataTransferByProtocol(IpChannelType.TCP);
	}

	private void testDataTransferByProtocol(IpChannelType ipChannelType) throws Exception {

		this.setUp(ipChannelType);

		this.management.startServer(SERVER_NAME);

		this.serverAssociation.setAssociationListener(new ServerAssociationListener());
		this.management.startAssociation(SERVER_ASSOCIATION_NAME);

		this.clientAssociation.setAssociationListener(new ClientAssociationListener());
		this.management.startAssociation(CLIENT_ASSOCIATION_NAME);

		for (int i1 = 0; i1 < 40; i1++) {
			if (serverAssocUp)
				break;
			Thread.sleep(1000 * 5); // was: 40
		}
		Thread.sleep(1000 * 1); // was: 40

		this.management.stopAssociation(CLIENT_ASSOCIATION_NAME);

		Thread.sleep(1000);

		this.management.stopAssociation(SERVER_ASSOCIATION_NAME);
		this.management.stopServer(SERVER_NAME);

		Thread.sleep(1000 * 2);

		assertTrue(Arrays.equals(SERVER_MESSAGE, clientMessage));
		assertTrue(Arrays.equals(CLIENT_MESSAGE, serverMessage));

		assertTrue(clientAssocUp);
		assertTrue(serverAssocUp);

		assertTrue(clientAssocDown);
		assertTrue(serverAssocDown);
		
		assertTrue(this.clientMaxInboundStreams> 0 );
		assertTrue(this.clientMaxOutboundStreams > 0);
		
		assertTrue(this.serverMaxInboundStreams> 0 );
		assertTrue(this.serverMaxOutboundStreams > 0);

		this.tearDown();
	}

	/**
	 * @return true if sctp is supported by this OS and false in not
	 */
	public static boolean checkSctpEnabled() {
		try {
			SctpChannel socketChannel = SctpChannel.open();
			socketChannel.close();
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	private class ClientAssociationListener implements AssociationListener {
		
		private final Logger logger = Logger.getLogger(ClientAssociationListener.class);

		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * org.mobicents.protocols.sctp.AssociationListener#onCommunicationUp
		 * (org.mobicents.protocols.sctp.Association)
		 */
		@Override
		public void onCommunicationUp(Association association, int maxInboundStreams, int maxOutboundStreams) {
			System.out.println(this + " onCommunicationUp");
			
			clientMaxInboundStreams = maxInboundStreams;
			clientMaxOutboundStreams = maxOutboundStreams;
			clientAssocUp = true;

			PayloadData payloadData = new PayloadData(CLIENT_MESSAGE.length, CLIENT_MESSAGE, true, false, 3, 1);

			try {
				association.send(payloadData);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * org.mobicents.protocols.sctp.AssociationListener#onCommunicationShutdown
		 * (org.mobicents.protocols.sctp.Association)
		 */
		@Override
		public void onCommunicationShutdown(Association association) {
			System.out.println(this + " onCommunicationShutdown");
			clientAssocDown = true;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * org.mobicents.protocols.sctp.AssociationListener#onCommunicationLost
		 * (org.mobicents.protocols.sctp.Association)
		 */
		@Override
		public void onCommunicationLost(Association association) {
			System.out.println(this + " onCommunicationLost");
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * org.mobicents.protocols.sctp.AssociationListener#onCommunicationRestart
		 * (org.mobicents.protocols.sctp.Association)
		 */
		@Override
		public void onCommunicationRestart(Association association) {
			System.out.println(this + " onCommunicationRestart");
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * org.mobicents.protocols.sctp.AssociationListener#onPayload(org.mobicents
		 * .protocols.sctp.Association,
		 * org.mobicents.protocols.sctp.PayloadData)
		 */
		@Override
		public void onPayload(Association association, PayloadData payloadData) {
			clientMessage = new byte[payloadData.getDataLength()];
			System.arraycopy(payloadData.getData(), 0, clientMessage, 0, payloadData.getDataLength());
			logger.debug("CLIENT received " + new String(clientMessage));
		}

		/* (non-Javadoc)
		 * @see org.mobicents.protocols.api.AssociationListener#inValidStreamId(org.mobicents.protocols.api.PayloadData)
		 */
		@Override
		public void inValidStreamId(PayloadData payloadData) {
			// TODO Auto-generated method stub
			
		}

	}

	private class ServerAssociationListener implements AssociationListener {

		private final Logger logger = Logger.getLogger(ServerAssociationListener.class);

		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * org.mobicents.protocols.sctp.AssociationListener#onCommunicationUp
		 * (org.mobicents.protocols.sctp.Association)
		 */
		@Override
		public void onCommunicationUp(Association association, int maxInboundStreams, int maxOutboundStreams) {
			System.out.println(this + " onCommunicationUp");

			serverAssocUp = true;
			serverMaxInboundStreams = maxInboundStreams;
			serverMaxOutboundStreams = maxOutboundStreams;
					

			PayloadData payloadData = new PayloadData(SERVER_MESSAGE.length, SERVER_MESSAGE, true, false, 3, 1);

			try {
				association.send(payloadData);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * org.mobicents.protocols.sctp.AssociationListener#onCommunicationShutdown
		 * (org.mobicents.protocols.sctp.Association)
		 */
		@Override
		public void onCommunicationShutdown(Association association) {
			System.out.println(this + " onCommunicationShutdown");
			serverAssocDown = true;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * org.mobicents.protocols.sctp.AssociationListener#onCommunicationLost
		 * (org.mobicents.protocols.sctp.Association)
		 */
		@Override
		public void onCommunicationLost(Association association) {
			System.out.println(this + " onCommunicationLost");
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * org.mobicents.protocols.sctp.AssociationListener#onCommunicationRestart
		 * (org.mobicents.protocols.sctp.Association)
		 */
		@Override
		public void onCommunicationRestart(Association association) {
			System.out.println(this + " onCommunicationRestart");
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * org.mobicents.protocols.sctp.AssociationListener#onPayload(org.mobicents
		 * .protocols.sctp.Association,
		 * org.mobicents.protocols.sctp.PayloadData)
		 */
		@Override
		public void onPayload(Association association, PayloadData payloadData) {
			serverMessage = new byte[payloadData.getDataLength()];
			System.arraycopy(payloadData.getData(), 0, serverMessage, 0, payloadData.getDataLength());
			logger.debug("SERVER received " + new String(serverMessage));
		}

		/* (non-Javadoc)
		 * @see org.mobicents.protocols.api.AssociationListener#inValidStreamId(org.mobicents.protocols.api.PayloadData)
		 */
		@Override
		public void inValidStreamId(PayloadData payloadData) {
			// TODO Auto-generated method stub
			
		}

	}

}
