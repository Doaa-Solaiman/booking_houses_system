package de.scheller.platform.network.rmi;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import de.scheller.platform.network.rmi.InternalApi.ConnectionListener;
import de.scheller.platform.network.rmi.InternalApi.IObjectResolver;
import de.scheller.platform.network.rmi.InternalApi.Transformation;

/**
 * @author kandzia
 * @author Joshua Tauberer (tauberer@for.net)
 */
public class RmiServer
{
	private static final Logger logger =
		Logger.getLogger(RCore.class.getPackage().getName());

	public static final int VERSION = 1;
	public static final int DEFAULT_PORT = 1301;

	protected int port;
	protected boolean requiressl;
	protected Set<String> accept;
	protected Set<String> deny;
	protected AcceptConnections t = new AcceptConnections();

	protected Map<String,Object> services = new HashMap();
	protected Map<Class,Transformation> transformations = new HashMap();
	protected List<IObjectResolver> incomingResolver = new ArrayList();
	protected List<IObjectResolver> outgoingResolver = new ArrayList();
	protected ConnectionListener connectionListener;

	public RmiServer() { this(DEFAULT_PORT, false); }
	public RmiServer(int port, boolean requiressl) {
		this.port = port;
		t.setDaemon(true);
		this.requiressl = requiressl;
	}

	/** @param addresses hostnames or IP addresses to accept connections from */
	public void accept(Set<String> addresses) {
		this.accept = addresses;
	}
	/** @param addresses hostnames or IP addresses to deny connections from */
	public void deny(Set<String> addresses) {
		this.deny = addresses;
	}
	/** Exports an object as a service. */
	public void export(String name, Object object) {
		services.put(name,object);
	}
	/** Removes the service of the specified name. */
	public void unexport(String name) {
		services.remove(name);
	}
	public void addTransformation(Class parameter, Class medium, boolean asProxy, Class wrapper, Class unwrapper) throws NoSuchMethodException {
		Transformation t = new Transformation(parameter,medium,asProxy,wrapper,unwrapper);
		transformations.put(t.objectType,t);
	}
	public void addTransformation(Transformation t) {
		transformations.put(t.objectType,t);
	}
	public void addIncomingResolver(IObjectResolver r) {
		incomingResolver.add(r);
	}
	public void addOutgoingResolver(IObjectResolver r) {
		outgoingResolver.add(r);
	}
	public void setConnectionListener(ConnectionListener l) {
		this.connectionListener = l;
	}
	/** Starts listening for incoming connections. */
	public void start() {
		t.start();
	}
	public int getPort() {
		return port;
	}
	public int getBoundPort() {
		return t.bound;
	}

	private class AcceptConnections extends Thread {
		int bound = 0;

		private void close(Socket client) throws IOException {
			client.close();
		}
		@Override
		public void run() {
			setName("rmi-server-listener");
			logger.info("Server listener thread started");
			try {
				ServerSocket serv = new ServerSocket(port);
				bound = serv.getLocalPort();
				while (true) {
					try {
						Socket s = serv.accept();
						int port = s.getPort();
						InetAddress addr = s.getInetAddress();
						String hostname = addr.getHostName();
						String hostaddr = addr.getHostAddress();

						logger.info("New socket connection "+addr+":"+port);
						if (accept!=null && !(accept.contains(hostname) || accept.contains(hostaddr))) {
							logger.info("Explicitly accept "+addr+":"+port);
							close(s);
							continue;
						}
						if (deny!=null && (deny.contains(hostname) || deny.contains(hostaddr))) {
							logger.info("Denied "+addr+":"+port);
							close(s);
							continue;
						}
						OutputStream o = s.getOutputStream();
						InputStream i = s.getInputStream();
						// READ HEADER
						int cversion = i.read();
						if (cversion != 1) {
							close(s);
							logger.info("Socket closed (cversion!=1) "+addr+":"+port);
							continue;
						}

						boolean applyssl = i.read()!=0 || requiressl;
						boolean nocompression = i.read()!=0;

						// SEND HEADER
						o.write(VERSION);
						o.write(applyssl ? 1 : 0);

						RConnection j = new RConnection("rmi-server@"+hostname+":"+port);

						logger.info("Socket created "+addr+":"+port+" - " +j.toString());

						// overrides
						j.noCompression = nocompression;
						j.services = services;
						transformations.putAll(j.transformations);
						j.transformations = transformations;
						j.outgoingResolver = outgoingResolver;
						j.incomingResolver = incomingResolver;
						// init
						j.connectionListener = connectionListener;
						j.connect(i,o);
						logger.info("Socket connected "+addr+":"+port+" - " +j.toString());
						if (connectionListener!=null)
							connectionListener.connectionOpened(null,j);
					} catch (ThreadDeath ex) {
						System.err.println("ThreadDeath");
						throw ex;
					} catch (Throwable ex) {
						System.err.println("problem in connection-accept loop");
						ex.printStackTrace();
					}
				}
			} catch (IOException ex) {
				System.err.println("ServerSocket creation failed");
				ex.printStackTrace();
				bound = 0;
			}
		}
	}
}
