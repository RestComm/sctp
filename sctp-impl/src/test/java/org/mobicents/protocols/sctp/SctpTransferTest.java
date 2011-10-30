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

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mobicents.protocols.api.Association;
import org.mobicents.protocols.api.AssociationListener;
import org.mobicents.protocols.api.PayloadData;

/**
 * @author amit bhayani
 * 
 */
public class SctpTransferTest {
	private static final String SERVER_NAME = "testserver";
	private static final String SERVER_HOST = "127.0.0.1";
	private static final int SERVER_PORT = 2345;

	private static final String SERVER_ASSOCIATION_NAME = "serverAsscoiation";
	private static final String CLIENT_ASSOCIATION_NAME = "clientAsscoiation";

	private static final String CLIENT_HOST = "127.0.0.1";
	private static final int CLIENT_PORT = 2346;

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

	@BeforeClass
	public static void setUpClass() throws Exception {
	}

	@AfterClass
	public static void tearDownClass() throws Exception {
	}

	@Before
	public void setUp() throws Exception {
		this.management = new ManagementImpl("server-management");
		this.management.setSingleThread(true);
		this.management.start();

		this.server = this.management.addServer(SERVER_NAME, SERVER_HOST, SERVER_PORT);
		this.serverAssociation = this.management.addServerAssociation(CLIENT_HOST, CLIENT_PORT, SERVER_NAME, SERVER_ASSOCIATION_NAME);
		this.clientAssociation = this.management.addAssociation(CLIENT_HOST, CLIENT_PORT, SERVER_HOST, SERVER_PORT, CLIENT_ASSOCIATION_NAME);

	}

	@After
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
	@Test
	@SuppressWarnings("static-access")
	public void testDataTransfer() throws Exception {

		this.management.startServer(SERVER_NAME);

		this.serverAssociation.setAssociationListener(new ServerAssociationListener());
		this.management.startAssociation(SERVER_ASSOCIATION_NAME);

		this.clientAssociation.setAssociationListener(new ClientAssociationListenerImpl());
		this.management.startAssociation(CLIENT_ASSOCIATION_NAME);

		Thread.sleep(1000 * 40);

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

		Runtime runtime = Runtime.getRuntime();
	}

	private class ClientAssociationListenerImpl implements AssociationListener {

		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * org.mobicents.protocols.sctp.AssociationListener#onCommunicationUp
		 * (org.mobicents.protocols.sctp.Association)
		 */
		@Override
		public void onCommunicationUp(Association association) {
			System.out.println(this + " onCommunicationUp");

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
			System.out.println(this + " onPayload");

			clientMessage = new byte[payloadData.getDataLength()];
			System.arraycopy(payloadData.getData(), 0, clientMessage, 0, payloadData.getDataLength());

			System.out.println(this + "received " + new String(clientMessage));

		}

	}

	private class ServerAssociationListener implements AssociationListener {

		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * org.mobicents.protocols.sctp.AssociationListener#onCommunicationUp
		 * (org.mobicents.protocols.sctp.Association)
		 */
		@Override
		public void onCommunicationUp(Association association) {
			System.out.println(this + " onCommunicationUp");

			serverAssocUp = true;

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
			System.out.println(this + " onPayload");

			serverMessage = new byte[payloadData.getDataLength()];
			System.arraycopy(payloadData.getData(), 0, serverMessage, 0, payloadData.getDataLength());

			System.out.println(this + "received " + new String(serverMessage));
		}

	}

}
