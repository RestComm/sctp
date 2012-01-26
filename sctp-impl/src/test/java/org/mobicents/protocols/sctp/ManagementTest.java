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

import static org.junit.Assert.*;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mobicents.protocols.api.Association;
import org.mobicents.protocols.api.Server;

/**
 * @author amit bhayani
 * 
 */
public class ManagementTest {

	private static final String SERVER_NAME = "testserver";
	private static final String SERVER_HOST = "127.0.0.1";
	private static final int SERVER_PORT = 2345;

	@BeforeClass
	public static void setUpClass() throws Exception {
	}

	@AfterClass
	public static void tearDownClass() throws Exception {
	}

	@Before
	public void setUp() throws Exception {

	}

	@After
	public void tearDown() throws Exception {

	}

	/**
	 * Test the creation of Server. Stop management and start, and Server should
	 * be started automatically
	 * 
	 * @throws Exception
	 */
	@Test
	public void testServer() throws Exception {
		ManagementImpl management = new ManagementImpl("ManagementTest");
		management.setSingleThread(true);
		management.start();

		Server server = management.addServer(SERVER_NAME, SERVER_HOST, SERVER_PORT);
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

		management.stopServer(SERVER_NAME);

		management.removeServer(SERVER_NAME);

		servers = management.getServers();
		assertEquals(0, servers.size());

		management.stop();

	}

	@Test
	public void testAssociation() throws Exception {
		ManagementImpl management = new ManagementImpl("ManagementTest");
		management.setSingleThread(true);
		management.start();

		// Add association
		Association clientAss1 = management.addAssociation("localhost", 2905, "localhost", 2906, "ClientAssoc1");
		assertNotNull(clientAss1);

		// Try to add assoc with same name
		try {
			clientAss1 = management.addAssociation("localhost", 2907, "localhost", 2908, "ClientAssoc1");
			fail("Expected Exception");
		} catch (Exception e) {
			assertEquals("Already has association=ClientAssoc1", e.getMessage());
		}

		// Try to add assoc with same peer add and port
		try {
			clientAss1 = management.addAssociation("localhost", 2907, "localhost", 2906, "ClientAssoc2");
			fail("Expected Exception");
		} catch (Exception e) {
			assertEquals("Already has association=ClientAssoc1 with same peer address=localhost and port=2906", e.getMessage());
		}

		// Try to add assoc with same host add and port
		try {
			clientAss1 = management.addAssociation("localhost", 2905, "localhost", 2908, "ClientAssoc2");
			fail("Expected Exception");
		} catch (Exception e) {
			assertEquals("Already has association=ClientAssoc1 with same host address=localhost and port=2905", e.getMessage());
		}

		// Remove Assoc
		management.removeAssociation("ClientAssoc1");

		management.stop();
	}

}
