package de.scheller.platform.network.rmi;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;

import de.scheller.platform.network.rmi.Failure.RemoteException;
import de.scheller.platform.network.rmi.InternalApi.ConnectionListener;

/**
 * @author kandzia
 * @author Joshua Tauberer (tauberer@for.net)
 */
public class RmiClient extends RConnection
{
	public static boolean noCompression = false;

	public final static int VERSION = 1;
	Socket s;

	public RmiClient(String host, int port, boolean ssl) throws UnknownHostException, IOException {
		super("rmi-client@"+host+":"+port);

		// TODO kandzia: allow connections over proxy / possibly tunnel through http
//		if ("true".equals(System.getProperty("rmi.useHttpProxy")) &&
//				"true".equals(System.getProperty("http.proxySet"))) {
//			String proxyHost = System.getProperty("http.proxyHost");
//			String proxyPort = System.getProperty("http.proxyPort");
//			String proxyUser = System.getProperty("http.proxyUser");
//			String proxyPass = System.getProperty("http.proxyPassword");
//
//			Proxy.Type proxyType = Proxy.Type.HTTP;
//			Proxy proxy = proxyType==Proxy.Type.DIRECT ? Proxy.NO_PROXY :
//				new Proxy(proxyType,new InetSocketAddress(proxyHost,Integer.parseInt(proxyPort)));
//			s = new Socket(proxy);
//			s.connect(new InetSocketAddress(host,port));
//		} else {
//			s = new Socket(host,port);
//		}

		s = new Socket(host,port);
		OutputStream o = s.getOutputStream();
		InputStream i = s.getInputStream();

		// Send Header
		o.write(VERSION);
		o.write(ssl ? 1 : 0);
		o.write(noCompression ? 1 : 0);
		super.noCompression = RmiClient.noCompression;

		// Get Header
		int sversion = i.read(); // version
		int applyssl = i.read(); // use SSL?

		connect(i,o);
	}

	public static Object GetService(String host, String service)
		throws UnknownHostException, IOException, RemoteException {
		return GetService(host, RmiServer.DEFAULT_PORT, service, null);
	}
	public static Object GetService(String host, int port, String service)
		throws UnknownHostException, IOException, RemoteException {
		return GetService(host, port, service, null);
	}
	public static Object GetService(String host, int port, String service, ConnectionListener l)
		throws UnknownHostException, IOException, RemoteException {
		RmiClient client = new RmiClient(host,port,false);
		client.terminateWhenDone = true;
		client.connectionListener = l;
		return client.findService(service);
	}

	public static RmiClient createClient(String host, int port, boolean terminateWhenDone)
	throws UnknownHostException, IOException, RemoteException {
		RmiClient client = new RmiClient(host,port,false);
		client.terminateWhenDone = terminateWhenDone;
		return client;
	}

	public static RmiClient createClient(String host, int port, ConnectionListener l)
	throws UnknownHostException, IOException, RemoteException {
		RmiClient client = new RmiClient(host,port,false);
		client.terminateWhenDone = true;
		client.connectionListener = l;
		return client;
	}
}
