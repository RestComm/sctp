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

package org.mobicents.protocols.sctp.multihome;

import static org.junit.Assert.assertTrue;
import javolution.util.FastList;

import org.apache.log4j.Logger;
import org.mobicents.protocols.api.Association;
import org.mobicents.protocols.api.AssociationListener;
import org.mobicents.protocols.api.IpChannelType;
import org.mobicents.protocols.api.PayloadData;
import org.mobicents.protocols.sctp.AssociationImpl;
import org.mobicents.protocols.sctp.ManagementImpl;
import org.mobicents.protocols.sctp.SctpTransferTest;
import org.mobicents.protocols.sctp.ServerImpl;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * <p>
 * This test is for SCTP Multihoming. Make sure you change SERVER_HOST1 and
 * CLIENT_HOST1 to match your current ip before you run this test.
 * <p>
 * <p>
 * Once this test is started you can randomly bring down loop back interface or
 * real interafce and see that traffic still continues.
 * </p>
 * <p>
 * This is not automated test. Please don't add in automation.
 * </p>
 * 
 * @author amit bhayani
 * 
 */
public class SctpMultiHomeTransferTest {
	private static final String SERVER_NAME = "testserver";
	private static final String SERVER_HOST = "127.0.0.1";
	private static final String SERVER_HOST1 = "10.2.50.194";

	private static final int SERVER_PORT = 2350;

	private static final String SERVER_ASSOCIATION_NAME = "serverAssociation";
	private static final String CLIENT_ASSOCIATION_NAME = "clientAssociation";

	private static final String CLIENT_HOST = "127.0.0.1";
	private static final String CLIENT_HOST1 = "10.2.50.194";

	private static final int CLIENT_PORT = 2351;

	private final String CLIENT_MESSAGE = "Client says Hi";
	private final String SERVER_MESSAGE = "Server says Hi";

	private ManagementImpl management = null;

	// private Management managementClient = null;
	private ServerImpl server = null;

	private AssociationImpl serverAssociation = null;
	private AssociationImpl clientAssociation = null;

	private volatile boolean clientAssocUp = false;
	private volatile boolean serverAssocUp = false;

	private volatile boolean clientAssocDown = false;
	private volatile boolean serverAssocDown = false;

	private FastList<String> clientMessage = null;
	private FastList<String> serverMessage = null;

	@BeforeClass
	public static void setUpClass() throws Exception {
	}

	@AfterClass
	public static void tearDownClass() throws Exception {
	}

	public void setUp(IpChannelType ipChannelType) throws Exception {
		this.clientAssocUp = false;
		this.serverAssocUp = false;

		this.clientAssocDown = false;
		this.serverAssocDown = false;

		this.clientMessage = new FastList<String>();
		this.serverMessage = new FastList<String>();

		this.management = new ManagementImpl("server-management");
		this.management.setConnectDelay(10000);// Try connecting every 10 secs
		this.management.setSingleThread(true);
		this.management.start();
		this.management.removeAllResourses();

		this.server = this.management.addServer(SERVER_NAME, SERVER_HOST, SERVER_PORT, ipChannelType, false, 0, new String[] { SERVER_HOST1 });
		this.serverAssociation = this.management.addServerAssociation(CLIENT_HOST, CLIENT_PORT, SERVER_NAME, SERVER_ASSOCIATION_NAME, ipChannelType);
		this.clientAssociation = this.management.addAssociation(CLIENT_HOST, CLIENT_PORT, SERVER_HOST, SERVER_PORT, CLIENT_ASSOCIATION_NAME, ipChannelType,
				new String[] { CLIENT_HOST1 });
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
	@Test(groups = { "functional", "sctp-multihome" })
	public void testDataTransferSctp() throws Exception {

		// Testing only is sctp is enabled
		if (!SctpTransferTest.checkSctpEnabled())
			return;
		
		this.setUp(IpChannelType.SCTP);

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
		Thread.sleep(1000 * 15000); // was: 40

		this.management.stopAssociation(CLIENT_ASSOCIATION_NAME);

		Thread.sleep(1000);

		this.management.stopAssociation(SERVER_ASSOCIATION_NAME);
		this.management.stopServer(SERVER_NAME);

		Thread.sleep(1000 * 2);

		// assertTrue(Arrays.equals(SERVER_MESSAGE, clientMessage));
		// assertTrue(Arrays.equals(CLIENT_MESSAGE, serverMessage));

		assertTrue(clientAssocUp);
		assertTrue(serverAssocUp);

		assertTrue(clientAssocDown);
		assertTrue(serverAssocDown);

		Runtime runtime = Runtime.getRuntime();

		this.tearDown();
	}

	private class ClientAssociationListener implements AssociationListener {
		private final Logger logger = Logger.getLogger(ClientAssociationListener.class);
		
		private LoadGenerator loadGenerator = null;

		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * org.mobicents.protocols.sctp.AssociationListener#onCommunicationUp
		 * (org.mobicents.protocols.sctp.Association)
		 */
		@Override
		public void onCommunicationUp(Association association, int maxInboundStreams, int maxOutboundStreams) {
			logger.info(" onCommunicationUp");

			clientAssocUp = true;
			loadGenerator = new LoadGenerator(association, CLIENT_MESSAGE);
			(new Thread(loadGenerator)).start();

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
			logger.warn( " onCommunicationShutdown");
			clientAssocDown = true;
			loadGenerator.stop();
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
			logger.warn(" onCommunicationLost");
			loadGenerator.stop();
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
			logger.warn(" onCommunicationRestart");
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
			byte[] data = new byte[payloadData.getDataLength()];
			System.arraycopy(payloadData.getData(), 0, data, 0, payloadData.getDataLength());
			String rxMssg = new String(data);
			logger.debug("CLIENT received " + rxMssg);
			clientMessage.add(rxMssg);

		}

		/* (non-Javadoc)
		 * @see org.mobicents.protocols.api.AssociationListener#inValidStreamId(org.mobicents.protocols.api.PayloadData)
		 */
		@Override
		public void inValidStreamId(PayloadData payloadData) {
			// TODO Auto-generated method stub
			
		}

	}

	private class LoadGenerator implements Runnable {

		private String message = null;
		private Association association;
		private volatile boolean started = true;

		LoadGenerator(Association association, String message) {
			this.association = association;
			this.message = message;
		}

		void stop() {
			this.started = false;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.lang.Runnable#run()
		 */
		@Override
		public void run() {
			for (int i = 0; i < 10000 && started; i++) {
				byte[] data = (this.message + i).getBytes();
				PayloadData payloadData = new PayloadData(data.length, data, true, false, 3, 1);

				try {
					this.association.send(payloadData);
				} catch (Exception e) {
					e.printStackTrace();
				}
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}

	}

	private class ServerAssociationListener implements AssociationListener {
		private final Logger logger = Logger.getLogger(ServerAssociationListener.class);
		private LoadGenerator loadGenerator = null;

		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * org.mobicents.protocols.sctp.AssociationListener#onCommunicationUp
		 * (org.mobicents.protocols.sctp.Association)
		 */
		@Override
		public void onCommunicationUp(Association association, int maxInboundStreams, int maxOutboundStreams) {
			logger.info(" onCommunicationUp");

			serverAssocUp = true;

			loadGenerator = new LoadGenerator(association, SERVER_MESSAGE);
			(new Thread(loadGenerator)).start();
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
			logger.warn(" onCommunicationShutdown");
			serverAssocDown = true;
			loadGenerator.stop();
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
			logger.warn(" onCommunicationLost");
			loadGenerator.stop();
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
			logger.warn(" onCommunicationRestart");
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
			byte[] data = new byte[payloadData.getDataLength()];
			System.arraycopy(payloadData.getData(), 0, data, 0, payloadData.getDataLength());
			String rxMssg = new String(data);
			logger.debug("SERVER received " + rxMssg);
			serverMessage.add(rxMssg);
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
