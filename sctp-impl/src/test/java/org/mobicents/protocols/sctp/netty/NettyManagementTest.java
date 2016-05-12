/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2014, Telestax Inc and individual contributors
 * by the @authors tag.
 *
 * This program is free software: you can redistribute it and/or modify
 * under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 *
 */

package org.mobicents.protocols.sctp.netty;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;
import java.util.Map;

import org.mobicents.protocols.api.Association;
import org.mobicents.protocols.api.AssociationListener;
import org.mobicents.protocols.api.IpChannelType;
import org.mobicents.protocols.api.PayloadData;
import org.mobicents.protocols.api.Server;
import org.mobicents.protocols.sctp.SctpTransferTest;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * @author amit bhayani
 * 
 */
public class NettyManagementTest {

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
        NettySctpManagementImpl management = new NettySctpManagementImpl("ManagementTest");
//        management.setSingleThread(true);
        management.start();
        management.removeAllResourses();

        String[] arr = new String[]{"127.0.0.2", "127.0.0.3"};
        Server server = management.addServer(SERVER_NAME, SERVER_HOST, SERVER_PORT, ipChannelType, true, 5, arr);

        management.startServer(SERVER_NAME);

        assertTrue(server.isStarted());

        management.stop();

        management = new NettySctpManagementImpl("ManagementTest");
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
        NettySctpManagementImpl management = new NettySctpManagementImpl("ManagementTest");
//        management.setSingleThread(true);
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
        
        management = new NettySctpManagementImpl("ManagementTest");
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

        NettySctpManagementImpl management = new NettySctpManagementImpl("ManagementTest");
//        management.setSingleThread(true);
        management.start();
        management.setConnectDelay(10000);// Try connecting every 10 secs
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

    @Test(groups = { "functional", "sctp" })
    public void testSctpStackParameters() throws Exception {

        // TODO: revive this test when we introduce of parameters persistense 
//        NettySctpManagementImpl management = new NettySctpManagementImpl("ManagementTestParam");
//        management.start();
//        management.removeAllResourses();
//
//        management.stop();
//        management.start();
//
//        management.setOptionSctpDisableFragments(true);
//        management.setOptionSctpNodelay(false);
//        management.setOptionSctpFragmentInterleave(1);
//        management.setOptionSoLinger(2);
//        management.setOptionSoRcvbuf(3);
//        management.setOptionSoSndbuf(4);
//        management.setOptionSctpInitMaxstreams(SctpStandardSocketOptions.InitMaxStreams.create(11, 12));
//
//        management.stop();
//        management.start();
//
//        assertTrue(management.getOptionSctpDisableFragments());
//        assertFalse(management.getOptionSctpNodelay());
//        assertEquals(management.getOptionSctpFragmentInterleave(), 1);
//        assertEquals(management.getOptionSoLinger(), 2);
//        assertEquals(management.getOptionSoRcvbuf(), 3);
//        assertEquals(management.getOptionSoSndbuf(), 4);
//        assertEquals(management.getOptionSctpInitMaxstreams().maxInStreams(), 11);
//        assertEquals(management.getOptionSctpInitMaxstreams().maxOutStreams(), 12);
//
//        management.setOptionSctpDisableFragments(null);
//        management.setOptionSctpNodelay(null);
//        management.setOptionSctpFragmentInterleave(null);
//        management.setOptionSoLinger(null);
//        management.setOptionSoRcvbuf(null);
//        management.setOptionSoSndbuf(null);
//        management.setOptionSctpInitMaxstreams(null);
//
//        management.stop();
//        management.start();
//
//        assertNull(management.getOptionSctpDisableFragments());
//        assertNull(management.getOptionSctpNodelay());
//        assertNull(management.getOptionSctpFragmentInterleave());
//        assertNull(management.getOptionSoLinger());
//        assertNull(management.getOptionSoRcvbuf());
//        assertNull(management.getOptionSoSndbuf());
//        assertNull(management.getOptionSctpInitMaxstreams());

    }
}
