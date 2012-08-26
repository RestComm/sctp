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

package org.mobicents.protocols.api;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * {@link Management} class manages the underlying {@link Association} and
 * {@link Server}.
 * </p>
 * <p>
 * Management should persist the state of {@link Server} and {@link Association}
 * </p>
 * <p>
 * Management when {@link #start() started} looks for file <tt>XXX_sctp.xml</tt> containing serialized state of underlying
 * {@link Association} and {@link Server}. Set the directory path by calling {@link #setPersistDir(String)} to direct Management to look at specified
 * directory for underlying serialized file.
 * </p>
 * <p>
 * If directory path is not set, Management searches for system property
 * <tt>sctp.persist.dir</tt> to get the path for directory
 * </p>
 * <p>
 * Even if <tt>sctp.persist.dir</tt> system property is not set,
 * Management will look at System set property <tt>user.dir</tt>
 * </p>
 * 
 * @author amit bhayani
 * 
 */
public interface Management {

	/**
	 * Returns the name of this {@link Management} instance
	 * 
	 * @return
	 */
	public String getName();
	
	/**
	 * Get persist dir
	 * @return
	 */
	public String getPersistDir();
	
	/**
	 * Directory where the XXX.xml will be searched
	 * @param persistDir
	 */
	public void setPersistDir(String persistDir);

	/**
	* The AssociationListener set for this Association
	* 
	* @return
	*/
	public ServerListener getServerListener();

	/**
	 * The {@link AssociationListener} to be registered for this Association
	 * 
	 * @param associationListener
	 */
	public void setServerListener(ServerListener serverListener);

	/**
	 * Adding ManagementEventListener.
	 * This listener is notified when adding/removing servers and associations 
	 * @param listener
	 */
	public void addManagementEventListener(ManagementEventListener listener);

	/**
	 * Removing ManagementEventListener.
	 * This listener is notified when adding/removing servers and associations 
	 * @param listener
	 */
	public void removeManagementEventListener(ManagementEventListener listener);

	/**
	 * Start the management. No management operation can be executed unless
	 * {@link Management} is started. If {@link Server} and {@link Association}
	 * were defined previously, Management should recreate those {@link Server}
	 * and {@link Association}.
	 * 
	 * @throws Exception
	 */
	public void start() throws Exception;

	/**
	 * Stop the management. It should persist the state of {@link Server} and
	 * {@link Associtaion}.
	 * 
	 * @throws Exception
	 */
	public void stop() throws Exception;
	
	/**
	 * returns true if Management is started
	 * @return
	 */
	public boolean isStarted();

	/**
	 * This method stops and removes all registered servers and associations
	 * Management should be started
	 * Use this method only for test purposes or after total crashes 
	 * 
	 * @throws Exception
	 */
	public void removeAllResourses() throws Exception;
	
	/**
	 * Add new {@link Server}.
	 * 
	 * @param serverName
	 *            name of the Server. Should be unique name
	 * @param hostAddress
	 *            IP address that this server will bind to
	 * @param port
	 *            port that this server will bind to
	 * @param ipChannelType
	 *            IP channel type: SCTP or TCP
	 * @param acceptAnonymousConnections
	 *            true: this Server accepts Anonymous connections, false: no
	 * @param maxConcurrentConnectionsCount
	 *            A count of concurrent connections that can accept a Server. 0 means an unlimited count.
	 * @param extraHostAddresses
	 *            When SCTP multi-homing configuration extra IP addresses can be put here
	 *            If multi-homing absence this parameter can be null 
	 * @return new Server instance
	 * @throws Exception
	 *             Exception if management not started or server name already
	 *             taken or some other server already has same ip:port
	 */
	public Server addServer(String serverName, String hostAddress, int port, IpChannelType ipChannelType, boolean acceptAnonymousConnections,
			int maxConcurrentConnectionsCount, String[] extraHostAddresses) throws Exception;

	/**
	 * Add new {@link Server}. Server can not accept anonymous connections.
	 * 
	 * @param serverName
	 *            name of the Server. Should be unique name
	 * @param hostAddress
	 *            IP address that this server will bind to
	 * @param port
	 *            port that this server will bind to
	 * @param ipChannelType
	 *            IP channel type: SCTP or TCP
	 * @param extraHostAddresses
	 *            When SCTP multi-homing configuration extra IP addresses can be put here
	 *            If multi-homing absence this parameter can be null 
	 * @return new Server instance
	 * @throws Exception
	 *             Exception if management not started or server name already
	 *             taken or some other server already has same ip:port
	 */
	public Server addServer(String serverName, String hostAddress, int port, IpChannelType ipChannelType, String[] extraHostAddresses) throws Exception;

	/**
	 * Add new {@link Server}. IP channel type is SCTP. Server can not accept anonymous connections.
	 * 
	 * @param serverName
	 *            name of the Server. Should be unique name
	 * @param hostAddress
	 *            IP address that this server will bind to
	 * @param port
	 *            port that this server will bind to
	 * @return new Server instance
	 * @throws Exception
	 *             Exception if management not started or server name already
	 *             taken or some other server already has same ip:port
	 */
	public Server addServer(String serverName, String hostAddress, int port) throws Exception;
	
	/**
	 * Remove existing {@link Server}
	 * 
	 * @param serverName
	 * @throws Exception
	 *             Exception if no Server with the passed name exist or Server
	 *             is started. Before removing server, it should be stopped
	 */
	public void removeServer(String serverName) throws Exception;

	/**
	 * Start the existing Server
	 * 
	 * @param serverName
	 *            name of the Server to be started
	 * @throws Exception
	 *             Exception if no Server found for given name or Server already
	 *             started
	 */
	public void startServer(String serverName) throws Exception;

	/**
	 * Stop the Server.
	 * 
	 * @param serverName
	 *            name of the Server to be stopped
	 * @throws Exception
	 *             Exception if no Server found for given name or any of the
	 *             {@link Association} within Server still started. All the
	 *             Association's must be stopped before stopping Server
	 */
	public void stopServer(String serverName) throws Exception;

	/**
	 * Get the list of Servers configured
	 * 
	 * @return
	 */
	public List<Server> getServers();

	/**
	 * Add server Association.
	 * 
	 * @param peerAddress
	 *            the peer IP address that this association will accept
	 *            connection from
	 * @param peerPort
	 *            the peer port that this association will accept connection
	 *            from
	 * @param serverName
	 *            the Server that this association belongs to
	 * @param assocName
	 *            unique name of Association
	 * @return
	 * @throws Exception
	 */
	public Association addServerAssociation(String peerAddress, int peerPort, String serverName, String assocName) throws Exception;

	/**
	 * Add server Association. IP channel type is SCTP.
	 * 
	 * @param peerAddress
	 *            the peer IP address that this association will accept
	 *            connection from
	 * @param peerPort
	 *            the peer port that this association will accept connection
	 *            from
	 * @param serverName
	 *            the Server that this association belongs to
	 * @param assocName
	 *            unique name of Association
	 * @param ipChannelType
	 *            IP channel type: SCTP or TCP
	 * @return
	 * @throws Exception
	 */
	public Association addServerAssociation(String peerAddress, int peerPort, String serverName, String assocName, IpChannelType ipChannelType)
			throws Exception;

	/**
	 * Add Association. IP channel type is SCTP.
	 * 
	 * @param hostAddress
	 * @param hostPort
	 * 		If hostPort==0 this mean the local port will be any vacant port
	 * @param peerAddress
	 * @param peerPort
	 * @param assocName
	 * @return
	 * @throws Exception
	 */
	public Association addAssociation(String hostAddress, int hostPort, String peerAddress, int peerPort, String assocName)
			throws Exception;

	/**
	 * Add Association
	 * 
	 * @param hostAddress
	 * @param hostPort
	 * 		If hostPort==0 this mean the local port will be any vacant port
	 * @param peerAddress
	 * @param peerPort
	 * @param assocName
	 * @param ipChannelType
	 *            IP channel type: SCTP or TCP
	 * @param extraHostAddresses
	 *            When SCTP multi-homing configuration extra IP addresses can be put here
	 *            If multi-homing absence this parameter can be null 
	 * @return
	 * @throws Exception
	 */
	public Association addAssociation(String hostAddress, int hostPort, String peerAddress, int peerPort, String assocName, IpChannelType ipChannelType,
			String[] extraHostAddresses) throws Exception;

	/**
	 * Remove existing Association. Association should be stopped before
	 * removing
	 * 
	 * @param assocName
	 * @throws Exception
	 */
	public void removeAssociation(String assocName) throws Exception;

	/**
	 * Get existing Association for passed name
	 * 
	 * @param assocName
	 * @return
	 * @throws Exception
	 */
	public Association getAssociation(String assocName) throws Exception;

	/**
	 * Get configured Association map with name as key and Association instance
	 * as value
	 * 
	 * @return
	 */
	public Map<String, Association> getAssociations();

	/**
	 * Start the existing Association
	 * 
	 * @param assocName
	 * @throws Exception
	 */
	public void startAssociation(String assocName) throws Exception;

	/**
	 * Stop the existing Association
	 * 
	 * @param assocName
	 * @throws Exception
	 */
	public void stopAssociation(String assocName) throws Exception;

	/**
	 * Get connection delay. If the client side {@link Association} dies due to
	 * network failure or any other reason, it should attempt to reconnect after
	 * connectDelay interval
	 * 
	 * @return
	 */
	public int getConnectDelay();

	/**
	 * Set the connection delay for client side {@link Association}
	 * 
	 * @param connectDelay
	 */
	public void setConnectDelay(int connectDelay);

	/**
	 * Number of threads configured to callback {@link AssociationListener}
	 * methods.
	 * 
	 * @return
	 */
	public int getWorkerThreads();

	/**
	 * Number of threads configured for callback {@link AssociationListener}
	 * 
	 * @param workerThreads
	 */
	public void setWorkerThreads(int workerThreads);

	/**
	 * If set as single thread, number of workers thread set has no effect and
	 * entire protocol stack runs on single thread
	 * 
	 * @return
	 */
	public boolean isSingleThread();

	/**
	 * Set protocol stack as single thread
	 * 
	 * @param singleThread
	 */
	public void setSingleThread(boolean singleThread);
}
