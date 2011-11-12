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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.Selector;
import java.nio.channels.spi.SelectorProvider;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javolution.text.TextBuilder;
import javolution.util.FastList;
import javolution.util.FastMap;
import javolution.xml.XMLBinding;
import javolution.xml.XMLObjectReader;
import javolution.xml.XMLObjectWriter;
import javolution.xml.stream.XMLStreamException;

import org.apache.log4j.Logger;
import org.mobicents.protocols.api.Association;
import org.mobicents.protocols.api.Management;
import org.mobicents.protocols.api.Server;

/**
 * @author amit bhayani
 * 
 */
public class ManagementImpl implements Management {

	private static final Logger logger = Logger.getLogger(ManagementImpl.class);

	private static final String SCTP_PERSIST_DIR_KEY = "sctp.persist.dir";
	private static final String USER_DIR_KEY = "user.dir";
	private static final String PERSIST_FILE_NAME = "sctp.xml";

	private static final String SERVERS = "servers";
	private static final String ASSOCIATIONS = "associations";

	private final TextBuilder persistFile = TextBuilder.newInstance();

	protected static final SctpXMLBinding binding = new SctpXMLBinding();
	protected static final String TAB_INDENT = "\t";
	private static final String CLASS_ATTRIBUTE = "type";

	private final String name;

	protected String persistDir = null;

	private FastList<Server> servers = new FastList<Server>();
	protected AssociationMap<String, Association> associations = new AssociationMap<String, Association>();

	private FastList<ChangeRequest> pendingChanges = new FastList<ChangeRequest>();

	// Create a new selector
	private Selector socketSelector = null;

	private SelectorThread selectorThread = null;

	static final int DEFAULT_IO_THREADS = Runtime.getRuntime().availableProcessors() * 2;

	private int workerThreads = DEFAULT_IO_THREADS;

	private boolean singleThread = false;

	private int workerThreadCount = 0;

	private int connectDelay = 30000;

	private ExecutorService[] executorServices = null;
	
	private volatile boolean started = false;

	public ManagementImpl(String name) throws IOException {
		this.name = name;
		binding.setClassAttribute(CLASS_ATTRIBUTE);
		binding.setAlias(ServerImpl.class, "server");
		binding.setAlias(AssociationImpl.class, "association");
		binding.setAlias(String.class, "string");
		this.socketSelector = SelectorProvider.provider().openSelector();
	}

	/**
	 * @return the name
	 */
	public String getName() {
		return name;
	}

	public String getPersistDir() {
		return persistDir;
	}

	public void setPersistDir(String persistDir) {
		this.persistDir = persistDir;
	}

	/**
	 * @return the connectDelay
	 */
	public int getConnectDelay() {
		return connectDelay;
	}

	/**
	 * @param connectDelay
	 *            the connectDelay to set
	 */
	public void setConnectDelay(int connectDelay) {
		this.connectDelay = connectDelay;
	}

	/**
	 * @return the workerThreads
	 */
	public int getWorkerThreads() {
		return workerThreads;
	}

	/**
	 * @param workerThreads
	 *            the workerThreads to set
	 */
	public void setWorkerThreads(int workerThreads) {
		if (workerThreads < 1) {
			workerThreads = DEFAULT_IO_THREADS;
		}
		this.workerThreads = workerThreads;

	}

	/**
	 * @return the singleThread
	 */
	public boolean isSingleThread() {
		return singleThread;
	}

	/**
	 * @param singleThread
	 *            the singleThread to set
	 */
	public void setSingleThread(boolean singleThread) {
		this.singleThread = singleThread;
	}

	public void start() throws Exception {
		this.persistFile.clear();

		if (persistDir != null) {
			this.persistFile.append(persistDir).append(File.separator).append(PERSIST_FILE_NAME);
		} else {
			persistFile.append(System.getProperty(SCTP_PERSIST_DIR_KEY, System.getProperty(USER_DIR_KEY))).append(File.separator).append(PERSIST_FILE_NAME);
		}

		logger.info(String.format("SCTP configuration file path %s", persistFile.toString()));

		try {
			this.load();
		} catch (FileNotFoundException e) {
			logger.warn(String.format("Failed to load the SCTP configuration file. \n%s", e.getMessage()));
		}

		if (!this.singleThread) {
			// If not single thread model we create worker threads
			this.executorServices = new ExecutorService[this.workerThreads];
			for (int i = 0; i < this.workerThreads; i++) {
				this.executorServices[i] = Executors.newSingleThreadExecutor();
			}
		}

		this.selectorThread = new SelectorThread(this.socketSelector, this);
		this.selectorThread.setStarted(true);

		(new Thread(this.selectorThread)).start();
		
		this.started = true;
		
		if (logger.isInfoEnabled()) {
			logger.info(String.format("Started SCTP Management=%s WorkerThreads=%d SingleThread=%s", this.name, (this.singleThread ? 0 : this.workerThreads),
					this.singleThread));
		}
	}

	public void stop() throws Exception {

		// We store the original state first
		this.store();

		// Stop all associations
		for (FastMap.Entry<String, Association> n = this.associations.head(), end = this.associations.tail(); (n = n.getNext()) != end;) {
			Association associationTemp = n.getValue();
			if (associationTemp.isStarted()) {
				((AssociationImpl) associationTemp).stop();
			}
		}

		for (FastList.Node<Server> n = this.servers.head(), end = this.servers.tail(); (n = n.getNext()) != end;) {
			Server serverTemp = n.getValue();
			if (serverTemp.isStarted()) {
				try {
					((ServerImpl) serverTemp).stop();
				} catch (Exception e) {
					logger.error(String.format("Exception while stopping the Server=%s", serverTemp.getName()), e);
				}
			}
		}

		if (this.executorServices != null) {
			for (int i = 0; i < this.executorServices.length; i++) {
				this.executorServices[i].shutdown();
			}
		}

		this.selectorThread.setStarted(false);
		this.socketSelector.wakeup(); // Wakeup selector so SelectorThread dies

		// Graceful shutdown for each of Executors
		if (this.executorServices != null) {
			for (int i = 0; i < this.executorServices.length; i++) {
				if (!this.executorServices[i].isTerminated()) {
					if (logger.isInfoEnabled()) {
						logger.info("Waiting for worker thread to die gracefully ....");
					}
					try {
						this.executorServices[i].awaitTermination(5000, TimeUnit.MILLISECONDS);
					} catch (InterruptedException e) {
						// Do we care?
					}
				}
			}
		}
		
		this.started = false;
	}

	public void load() throws FileNotFoundException {
		XMLObjectReader reader = null;
		try {
			reader = XMLObjectReader.newInstance(new FileInputStream(persistFile.toString()));
			reader.setBinding(binding);
			this.servers = reader.read(SERVERS, FastList.class);

			for (FastList.Node<Server> n = this.servers.head(), end = this.servers.tail(); (n = n.getNext()) != end;) {
				Server serverTemp = n.getValue();
				((ServerImpl) serverTemp).setManagement(this);
				if (serverTemp.isStarted()) {
					try {
						((ServerImpl) serverTemp).start();
					} catch (Exception e) {
						logger.error(String.format("Error while initiating Server=%s", serverTemp.getName()), e);
					}
				}
			}

			this.associations = reader.read(ASSOCIATIONS, AssociationMap.class);
			for (FastMap.Entry<String, Association> n = this.associations.head(), end = this.associations.tail(); (n = n.getNext()) != end;) {
				AssociationImpl associationTemp = (AssociationImpl) n.getValue();
				associationTemp.setManagement(this);
			}

		} catch (XMLStreamException ex) {
			// this.logger.info(
			// "Error while re-creating Linksets from persisted file", ex);
		}
	}

	public void store() {
		try {
			XMLObjectWriter writer = XMLObjectWriter.newInstance(new FileOutputStream(persistFile.toString()));
			writer.setBinding(binding);
			// Enables cross-references.
			// writer.setReferenceResolver(new XMLReferenceResolver());
			writer.setIndentation(TAB_INDENT);
			writer.write(this.servers, SERVERS, FastList.class);
			writer.write(this.associations, ASSOCIATIONS, AssociationMap.class);

			writer.close();
		} catch (Exception e) {
			logger.error("Error while persisting the Rule state in file", e);
		}
	}

	public ServerImpl addServer(String serverName, String hostAddress, int port) throws Exception {
		if (!this.started) {
			throw new Exception(String.format("Management=%s not started", this.name));
		}
		
		if (serverName == null) {
			throw new Exception("Server name cannot be null");
		}

		if (hostAddress == null) {
			throw new Exception("Server host address cannot be null");
		}

		if (port < 1) {
			throw new Exception("Server host port cannot be less than 1");
		}

		for (FastList.Node<Server> n = this.servers.head(), end = this.servers.tail(); (n = n.getNext()) != end;) {
			Server serverTemp = n.getValue();
			if (serverName.equals(serverTemp.getName())) {
				throw new Exception(String.format("Server name=%s already exist", serverName));
			}

			if (hostAddress.equals(serverTemp.getHostAddress()) && port == serverTemp.getHostport()) {
				throw new Exception(String.format("Server name=%s is already bound to %s:%d", serverTemp.getName(), serverTemp.getHostAddress(),
						serverTemp.getHostport()));
			}
		}

		ServerImpl server = new ServerImpl(serverName, hostAddress, port);
		server.setManagement(this);

		this.servers.add(server);

		this.store();
		if (logger.isInfoEnabled()) {
			logger.info(String.format("Created Server=%s", server.getName()));
		}

		return server;
	}

	public void removeServer(String serverName) throws Exception {
		if(!this.started){
			throw new Exception(String.format("Management=%s not started", this.name));
		}
		
		if (serverName == null) {
			throw new Exception("Server name cannot be null");
		}

		Server removeServer = null;
		for (FastList.Node<Server> n = this.servers.head(), end = this.servers.tail(); (n = n.getNext()) != end;) {
			Server serverTemp = n.getValue();

			if (serverName.equals(serverTemp.getName())) {
				if (serverTemp.isStarted()) {
					throw new Exception(String.format("Server=%s is started. Stop the server before removing", serverName));
				}
				removeServer = serverTemp;
				break;
			}
		}

		if (removeServer == null) {
			throw new Exception(String.format("No Server found with name=%s", serverName));
		}

		this.servers.remove(removeServer);
		this.store();
	}

	public void startServer(String serverName) throws Exception {
		if(!this.started){
			throw new Exception(String.format("Management=%s not started", this.name));
		}
		
		if (name == null) {
			throw new Exception("Server name cannot be null");
		}

		for (FastList.Node<Server> n = this.servers.head(), end = this.servers.tail(); (n = n.getNext()) != end;) {
			Server serverTemp = n.getValue();

			if (serverName.equals(serverTemp.getName())) {
				if (serverTemp.isStarted()) {
					throw new Exception(String.format("Server=%s is already started", serverName));
				}
				((ServerImpl) serverTemp).start();
				this.store();
				return;
			}
		}

		throw new Exception(String.format("No Server foubd with name=%s", serverName));
	}

	public void stopServer(String serverName) throws Exception {
		if(!this.started){
			throw new Exception(String.format("Management=%s not started", this.name));
		}
		
		if (serverName == null) {
			throw new Exception("Server name cannot be null");
		}

		for (FastList.Node<Server> n = this.servers.head(), end = this.servers.tail(); (n = n.getNext()) != end;) {
			Server serverTemp = n.getValue();

			if (serverName.equals(serverTemp.getName())) {
				((ServerImpl) serverTemp).stop();
				this.store();
				return;
			}
		}

		throw new Exception(String.format("No Server found with name=%s", serverName));
	}

	public AssociationImpl addServerAssociation(String peerAddress, int peerPort, String serverName, String assocName) throws Exception {
		
		if(!this.started){
			throw new Exception(String.format("Management=%s not started", this.name));
		}
		
		if (peerAddress == null) {
			throw new Exception("Peer address cannot be null");
		}

		if (peerPort < 1) {
			throw new Exception("Peer port cannot be less than 1");
		}

		if (serverName == null) {
			throw new Exception("Server name cannot be null");
		}

		if (assocName == null) {
			throw new Exception("Association name cannot be null");
		}

		if (this.associations.get(assocName) != null) {
			throw new Exception(String.format("Already has association=%s", assocName));
		}

		Server server = null;

		for (FastList.Node<Server> n = this.servers.head(), end = this.servers.tail(); (n = n.getNext()) != end;) {
			Server serverTemp = n.getValue();
			if (serverTemp.getName().equals(serverName)) {
				server = serverTemp;
			}
		}

		if (server == null) {
			throw new Exception(String.format("No Server found for name=%s", serverName));
		}

		for (FastMap.Entry<String, Association> n = this.associations.head(), end = this.associations.tail(); (n = n.getNext()) != end;) {
			Association associationTemp = n.getValue();

			if (peerAddress.equals(associationTemp.getPeerAddress()) && associationTemp.getPeerPort() == peerPort) {
				throw new Exception(String.format("Already has association=%s with same peer address=% and port=%d", associationTemp.getName(), peerAddress,
						peerPort));
			}
		}

		AssociationImpl association = new AssociationImpl(peerAddress, peerPort, serverName, assocName);
		association.setManagement(this);
		this.associations.put(assocName, association);
		((ServerImpl)server).associations.add(assocName);

		this.store();

		if (logger.isInfoEnabled()) {
			logger.info(String.format("Added Associoation=%s of type=%s", association.getName(), association.getType()));
		}

		return association;
	}

	public AssociationImpl addAssociation(String hostAddress, int hostPort, String peerAddress, int peerPort, String assocName) throws Exception {
		if(!this.started){
			throw new Exception(String.format("Management=%s not started", this.name));
		}
		
		if (hostAddress == null) {
			throw new Exception("Host address cannot be null");
		}

		if (hostPort < 1) {
			throw new Exception("Host port cannot be less than 1");
		}

		if (peerAddress == null) {
			throw new Exception("Peer address cannot be null");
		}

		if (peerPort < 1) {
			throw new Exception("Peer port cannot be less than 1");
		}

		if (assocName == null) {
			throw new Exception("Association name cannot be null");
		}

		for (FastMap.Entry<String, Association> n = this.associations.head(), end = this.associations.tail(); (n = n.getNext()) != end;) {
			Association associationTemp = n.getValue();

			if (assocName.equals(associationTemp.getName())) {
				throw new Exception(String.format("Already has association=%s", associationTemp.getName()));
			}

			if (peerAddress.equals(associationTemp.getPeerAddress()) && associationTemp.getPeerPort() == peerPort) {
				throw new Exception(String.format("Already has association=%s with same peer address=% and port=%d", associationTemp.getName(), peerAddress,
						peerPort));
			}

			if (hostAddress.equals(associationTemp.getHostAddress()) && associationTemp.getHostPort() == hostPort) {
				throw new Exception(String.format("Already has association=%s with same host address=% and port=%d", associationTemp.getName(), hostAddress,
						hostPort));
			}

		}

		AssociationImpl association = new AssociationImpl(hostAddress, hostPort, peerAddress, peerPort, assocName);
		association.setManagement(this);
		associations.put(assocName, association);

		this.store();

		if (logger.isInfoEnabled()) {
			logger.info(String.format("Added Associoation=%s of type=%s", association.getName(), association.getType()));
		}

		return association;
	}

	public Association getAssociation(String assocName) throws Exception {
		if (assocName == null) {
			throw new Exception("Association name cannot be null");
		}
		Association associationTemp = this.associations.get(assocName);

		if (associationTemp == null) {
			throw new Exception(String.format("No Association found for name=%s", assocName));
		}
		return associationTemp;
	}

	/**
	 * @return the associations
	 */
	public Map<String, Association> getAssociations() {
		return associations.unmodifiable();
	}

	public void startAssociation(String assocName) throws Exception {
		if(!this.started){
			throw new Exception(String.format("Management=%s not started", this.name));
		}

		if (assocName == null) {
			throw new Exception("Association name cannot be null");
		}

		Association associationTemp = this.associations.get(assocName);

		if (associationTemp == null) {
			throw new Exception(String.format("No Association found for name=%s", assocName));
		}

		if (associationTemp.isStarted()) {
			throw new Exception(String.format("Association=%s is already started", assocName));
		}

		((AssociationImpl) associationTemp).start();
		this.store();
	}

	public void stopAssociation(String assocName) throws Exception {
		if(!this.started){
			throw new Exception(String.format("Management=%s not started", this.name));
		}

		if (assocName == null) {
			throw new Exception("Association name cannot be null");
		}

		Association association = this.associations.get(assocName);

		if (association == null) {
			throw new Exception(String.format("No Association found for name=%s", assocName));
		}

		((AssociationImpl) association).stop();
		this.store();
	}

	public void removeAssociation(String assocName) throws Exception {
		if(!this.started){
			throw new Exception(String.format("Management=%s not started", this.name));
		}
		
		if (assocName == null) {
			throw new Exception("Association name cannot be null");
		}

		Association association = this.associations.get(assocName);

		if (association == null) {
			throw new Exception(String.format("No Association found for name=%s", assocName));
		}

		if (association.isStarted()) {
			throw new Exception(String.format("Association name=%s is started. Stop before removing", assocName));
		}

		this.associations.remove(assocName);

		if (((AssociationImpl) association).getType() == AssociationType.SERVER) {
			for (FastList.Node<Server> n = this.servers.head(), end = this.servers.tail(); (n = n.getNext()) != end;) {
				Server serverTemp = n.getValue();
				if (serverTemp.getName().equals(association.getServerName())) {
					((ServerImpl) serverTemp).associations.remove(assocName);
					break;
				}
			}
		}

		this.store();
	}

	/**
	 * @return the servers
	 */
	public List<Server> getServers() {
		return servers.unmodifiable();
	}

	/**
	 * @return the pendingChanges
	 */
	protected FastList<ChangeRequest> getPendingChanges() {
		return pendingChanges;
	}

	/**
	 * @return the socketSelector
	 */
	protected Selector getSocketSelector() {
		return socketSelector;
	}

	protected void populateWorkerThread(int workerThreadTable[]) {
		for (int count = 0; count < workerThreadTable.length; count++) {
			if (this.workerThreadCount == this.workerThreads) {
				this.workerThreadCount = 0;
			}

			workerThreadTable[count] = this.workerThreadCount;
			this.workerThreadCount++;
		}
	}

	protected ExecutorService getExecutorService(int index) {
		return this.executorServices[index];
	}

}
