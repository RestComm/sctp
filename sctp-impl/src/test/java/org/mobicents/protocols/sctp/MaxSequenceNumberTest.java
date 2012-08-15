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
import static org.junit.Assert.assertTrue;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;
import org.mobicents.protocols.api.Association;
import org.mobicents.protocols.api.AssociationListener;
import org.mobicents.protocols.api.IpChannelType;
import org.mobicents.protocols.api.PayloadData;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.sun.nio.sctp.SctpChannel;

/**
 * @author amit bhayani
 * 
 */
public class MaxSequenceNumberTest {

	public static final Logger logger = Logger.getLogger(MaxSequenceNumberTest.class);

	private static final String SERVER_NAME = "testserver";
	private static final String SERVER_HOST = "127.0.0.1";
	private static final int SERVER_PORT = 2349;

	private static final String SERVER_ASSOCIATION_NAME = "serverAssociation";
	private static final String CLIENT_ASSOCIATION_NAME = "clientAssociation";

	private static final String CLIENT_HOST = "127.0.0.1";
	private static final int CLIENT_PORT = 2350;

	private ManagementImpl management = null;

	// private Management managementClient = null;
	private ServerImpl server = null;

	private AssociationImpl serverAssociation = null;
	private AssociationImpl clientAssociation = null;

	private volatile boolean clientAssocUp = false;
	private volatile boolean serverAssocUp = false;

	private volatile boolean clientAssocDown = false;
	private volatile boolean serverAssocDown = false;

	private volatile int clientPacketsRx = 0;
	private volatile int serverPacketsRx = 0;

	private volatile int clientPacketsDropped = 0;
	private volatile int serverPacketsDropped = 0;

	private Semaphore semaphore = null;

	@BeforeClass
	public static void setUpClass() throws Exception {
	}

	@AfterClass
	public static void tearDownClass() throws Exception {
	}

	public void setUp(IpChannelType ipChannelType) throws Exception {
		this.semaphore = new Semaphore(0);

		this.clientAssocUp = false;
		this.serverAssocUp = false;

		this.clientAssocDown = false;
		this.serverAssocDown = false;

		this.clientPacketsRx = 0;
		this.serverPacketsRx = 0;
		
		this.clientPacketsDropped = 0;
		this.serverPacketsDropped = 0;

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
	 * Simple test that sends twice as many packets as maxInboundStreams
	 * registered for each client and server socket. In this case there
	 * shouldn't be any packets dropped.
	 */
	@Test(groups = { "functional", "noAdditionOnOutBoundStream" })
	public void testNoAdditionOnOutBoundStream() throws Exception {

		int additionOnMaxSeqno = 0;

		if (SctpTransferTest.checkSctpEnabled()) {
			this.setUp(IpChannelType.SCTP);

			this.management.startServer(SERVER_NAME);

			ServerAssociationListener serverAssociationListener = new ServerAssociationListener(additionOnMaxSeqno);
			this.serverAssociation.setAssociationListener(serverAssociationListener);
			this.management.startAssociation(SERVER_ASSOCIATION_NAME);

			ClientAssociationListener clientAssociationListener = new ClientAssociationListener(additionOnMaxSeqno);
			this.clientAssociation.setAssociationListener(clientAssociationListener);
			this.management.startAssociation(CLIENT_ASSOCIATION_NAME);

			for (int i1 = 0; i1 < 40; i1++) {
				if (serverAssocUp)
					break;
				Thread.sleep(1000 * 5); // was: 40
			}

			semaphore.tryAcquire(2, 3000, TimeUnit.MILLISECONDS);

			Thread.sleep(1000 * 2); // 2 seconds for read operation

			this.management.stopAssociation(CLIENT_ASSOCIATION_NAME);

			Thread.sleep(1000);

			this.management.stopAssociation(SERVER_ASSOCIATION_NAME);
			this.management.stopServer(SERVER_NAME);

			Thread.sleep(1000 * 2);

			assertTrue(clientAssocUp);
			assertTrue(serverAssocUp);

			assertTrue(clientAssocDown);
			assertTrue(serverAssocDown);

			assertEquals((serverAssociationListener.getMaxOutboundStreams() * 2), this.clientPacketsRx);
			assertEquals((clientAssociationListener.getMaxOutboundStreams() * 2), this.serverPacketsRx);

			// There should be no packet drops here
			assertEquals(0, this.clientPacketsDropped);
			assertEquals(0, this.serverPacketsDropped);

			this.tearDown();
		}
	}

	/**
	 * Simple test that sends ((maxInboundStreams + 2) * 2) packets as
	 * registered for each client and server socket. In this case there since
	 * sequence number is overshotting there will be drop in packets
	 */
	@Test(groups = { "functional", "additionOnOutBoundStream" })
	public void testAdditionOnOutBoundStream() throws Exception {

		int additionOnMaxSeqno = 2;

		if (SctpTransferTest.checkSctpEnabled()) {
			this.setUp(IpChannelType.SCTP);

			this.management.startServer(SERVER_NAME);

			ServerAssociationListener serverAssociationListener = new ServerAssociationListener(additionOnMaxSeqno);
			this.serverAssociation.setAssociationListener(serverAssociationListener);
			this.management.startAssociation(SERVER_ASSOCIATION_NAME);

			ClientAssociationListener clientAssociationListener = new ClientAssociationListener(additionOnMaxSeqno);
			this.clientAssociation.setAssociationListener(clientAssociationListener);
			this.management.startAssociation(CLIENT_ASSOCIATION_NAME);

			for (int i1 = 0; i1 < 40; i1++) {
				if (serverAssocUp)
					break;
				Thread.sleep(1000 * 5); // was: 40
			}

			semaphore.tryAcquire(2, 3000, TimeUnit.MILLISECONDS);

			Thread.sleep(1000 * 2); // 2 seconds for read operation

			this.management.stopAssociation(CLIENT_ASSOCIATION_NAME);

			Thread.sleep(1000);

			this.management.stopAssociation(SERVER_ASSOCIATION_NAME);
			this.management.stopServer(SERVER_NAME);

			Thread.sleep(1000 * 2);

			assertTrue(clientAssocUp);
			assertTrue(serverAssocUp);

			assertTrue(clientAssocDown);
			assertTrue(serverAssocDown);

			assertEquals((serverAssociationListener.getMaxOutboundStreams() * 2), this.clientPacketsRx);
			assertEquals((clientAssociationListener.getMaxOutboundStreams() * 2), this.serverPacketsRx);

			// We know 4 packets would have been dropped
			assertEquals(4, this.clientPacketsDropped);
			assertEquals(4, this.serverPacketsDropped);

			this.tearDown();
		}
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
		private int additionOnMaxSeqno;
		private int maxInboundStreams;
		private int maxOutboundStreams;

		ClientAssociationListener(int additionOnMaxSeqno) {
			this.additionOnMaxSeqno = additionOnMaxSeqno;

		}

		/**
		 * @return the maxInboundStreams
		 */
		public int getMaxInboundStreams() {
			return maxInboundStreams;
		}

		/**
		 * @return the maxOutboundStreams
		 */
		public int getMaxOutboundStreams() {
			return maxOutboundStreams;
		}

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
			clientAssocUp = true;
			this.maxInboundStreams = maxInboundStreams;
			this.maxOutboundStreams = maxOutboundStreams;
			(new Thread(new DataSender((this.additionOnMaxSeqno + maxOutboundStreams), "Client Hi", association))).start();
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
			byte[] clientMessage = new byte[payloadData.getDataLength()];
			System.arraycopy(payloadData.getData(), 0, clientMessage, 0, payloadData.getDataLength());
			logger.debug("CLIENT received " + new String(clientMessage));

			clientPacketsRx++;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * org.mobicents.protocols.api.AssociationListener#inValidStreamId(org
		 * .mobicents.protocols.api.PayloadData)
		 */
		@Override
		public void inValidStreamId(PayloadData payloadData) {
			clientPacketsDropped++;
			logger.error(String.format("Tx : PayloadData with streamNumber=%d which is greater than or equal to maxSequenceNumber=%d. Droping PayloadData=%s",
					payloadData.getStreamNumber(), this.getMaxOutboundStreams(), payloadData));
		}

	}

	private class ServerAssociationListener implements AssociationListener {

		private final Logger logger = Logger.getLogger(ServerAssociationListener.class);
		private int maxInboundStreams;
		private int maxOutboundStreams;

		private int additionOnMaxSeqno;

		ServerAssociationListener(int additionOnMaxSeqno) {
			this.additionOnMaxSeqno = additionOnMaxSeqno;
		}

		/**
		 * @return the maxInboundStreams
		 */
		public int getMaxInboundStreams() {
			return maxInboundStreams;
		}

		/**
		 * @return the maxOutboundStreams
		 */
		public int getMaxOutboundStreams() {
			return maxOutboundStreams;
		}

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

			this.maxOutboundStreams = maxOutboundStreams;
			this.maxInboundStreams = maxInboundStreams;

			(new Thread(new DataSender((this.additionOnMaxSeqno + maxOutboundStreams), "Server Hi", association))).start();
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
			byte[] serverMessage = new byte[payloadData.getDataLength()];
			System.arraycopy(payloadData.getData(), 0, serverMessage, 0, payloadData.getDataLength());
			// logger.debug("SERVER received " new String(serverMessage));

			serverPacketsRx++;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * org.mobicents.protocols.api.AssociationListener#inValidStreamId(org
		 * .mobicents.protocols.api.PayloadData)
		 */
		@Override
		public void inValidStreamId(PayloadData payloadData) {
			serverPacketsDropped++;
			logger.error(String.format("Tx : PayloadData with streamNumber=%d which is greater than or equal to maxSequenceNumber=%d. Droping PayloadData=%s",
					payloadData.getStreamNumber(), this.getMaxOutboundStreams(), payloadData));
		}

	}

	private class DataSender implements Runnable {

		private int maxSequenceNumber;
		private String message;
		private Association association;

		DataSender(int maxSequenceNumber, String message, Association association) {
			this.maxSequenceNumber = maxSequenceNumber;
			this.message = message;
			this.association = association;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.lang.Runnable#run()
		 */
		@Override
		public void run() {

			logger.info("Set maxSequenceNumber = " + this.maxSequenceNumber);

			int sequenceNumber = 0;
			// We will send packets twice the this.maxSequenceNumber
			for (int count = 0; count < (this.maxSequenceNumber * 2); count++) {
				byte[] data = (message + " " + count).getBytes();

				if (sequenceNumber >= this.maxSequenceNumber) {
					sequenceNumber = 0;
				}

				PayloadData payloadData = new PayloadData(data.length, data, true, false, 3, sequenceNumber);

				sequenceNumber++;
				try {
					association.send(payloadData);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

			semaphore.release();
		}

	}
}
