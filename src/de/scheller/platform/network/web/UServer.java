package de.scheller.platform.network.web;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URI;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import org.xnio.ChannelListener;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.XnioWorker;

import de.scheller.platform.common.MapUtils;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.Undertow.ListenerInfo;
import io.undertow.UndertowOptions;
import io.undertow.client.ClientStatistics;
import io.undertow.server.ConnectorStatistics;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.OpenListener;
import io.undertow.server.handlers.NameVirtualHostHandler;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.server.handlers.proxy.ExclusivityChecker;
import io.undertow.server.handlers.proxy.LoadBalancingProxyClient;
import io.undertow.server.handlers.proxy.LoadBalancingProxyClient.Host;
import io.undertow.server.handlers.proxy.ProxyCallback;
import io.undertow.server.handlers.proxy.ProxyConnection;
import io.undertow.server.handlers.proxy.ProxyConnectionPool;
import io.undertow.server.session.InMemorySessionManager;
import io.undertow.server.session.Session;
import io.undertow.server.session.SessionAttachmentHandler;
import io.undertow.server.session.SessionCookieConfig;
import io.undertow.server.session.SessionListener;
import io.undertow.server.session.SessionManager;
import io.undertow.server.session.SessionManagerStatistics;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;
import io.undertow.websockets.WebSocketConnectionCallback;
import io.undertow.websockets.core.AbstractReceiveListener;
import io.undertow.websockets.core.BufferedTextMessage;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSockets;
import io.undertow.websockets.spi.WebSocketHttpExchange;

/**
 * @author kandzia
 */
public class UServer
{
	public static int port = 80;
	public static int port2 = 0;
	public static int sslport = 443;
	public static String bind = "0.0.0.0";
	public static String sslbind = "0.0.0.0";
	public static boolean forceHttps = true;
	public static boolean useSessions = true;

	public static SessionCookieConfig sessionCookie;
	static {
		sessionCookie = new SessionCookieConfig();
		sessionCookie.setCookieName("SESSIONID");
	}

	public static UServer start(HttpHandler root) throws Exception {
		return userverLast = new UServer(root);
	}

	public int _port;
	public int _port2;
	public int _sslport;
	public String _bind;
	public String _sslbind;
	public boolean _forceHttps;
	public boolean _useSessions;
	SessionManager sessions;
	Undertow server;
	static SessionManager sessionsLast;
	static Undertow serverLast;
	static UServer userverLast;

	public UServer(HttpHandler root) throws Exception {
		_port = port;
		_port2 = port2;
		_sslport = sslport;
		_bind = bind;
		_sslbind = sslbind;
		_forceHttps = forceHttps;
		_useSessions = useSessions;

		if (useSessions) {
			sessions = new InMemorySessionManager("SESSIONS");
			sessions.start();
			root = new SessionAttachmentHandler(root,sessions,sessionCookie);
			sessions.registerSessionListener(new SessionListener() {
				public void sessionCreated(Session session, HttpServerExchange exchange) {
					if (statsOutput!=null)
						output(session);
				}
				public void sessionDestroyed(Session session, HttpServerExchange exchange, SessionDestroyedReason reason) {
					if (statsOutput!=null)
						output(session,"se",System.currentTimeMillis(),"sr",reason.name());
				}
				public void sessionIdChanged(Session session, String oldSessionId) {
					if (statsOutput!=null)
						output(session,"oid",oldSessionId);
				}
				public void attributeAdded(Session session, String name, Object value) {
					if (statsOutput!=null)
						output(session,"aa","add","ak",name,"av",value);
				}
				public void attributeRemoved(Session session, String name, Object oldValue) {
					if (statsOutput!=null)
						output(session,"aa","remove","ak",name,"ov",oldValue);
				}
				public void attributeUpdated(Session session, String name, Object newValue, Object oldValue) {
					if (statsOutput!=null)
						output(session,"aa","update","ak",name,"av",newValue,"ov",oldValue);
				}
				private void output(Session session, Object... kvPairs) {
					if (statsOutput==null) return;
					Map s = new LinkedHashMap();
					s.putAll(MapUtils.asMap(
							"sid",session.getId(),"st",session.getCreationTime(),
							"smi",session.getMaxInactiveInterval(),"sa",session.getLastAccessedTime()));
					s.putAll(MapUtils.asMap(kvPairs));
					List<Map> stats = new ArrayList();
					stats.add(s);
					statsOutput.accept(stats);
				}
			});
		}
		HttpHandler rootWithoutForceHttps = root;
		if (forceHttps)
			root = forceHttps(root);
		Undertow.Builder builder = Undertow.builder();
		builder.addHttpListener(port,bind);
		if (port2>0)
			builder.addHttpListener(port2,bind,rootWithoutForceHttps);
		if (ssl!=null)
			builder.addHttpsListener(sslport,sslbind,ssl);
		builder.setServerOption(UndertowOptions.ENABLE_HTTP2,true);
		builder.setServerOption(UndertowOptions.ALWAYS_SET_KEEP_ALIVE,true);
		builder.setServerOption(UndertowOptions.ENABLE_STATISTICS,true);
//		builder.setSocketOption(UndertowOptions.ENABLE_STATISTICS,true);
//		builder.setWorkerOption(UndertowOptions.ENABLE_STATISTICS,true);
		builder.setWorkerOption(Options.WORKER_TASK_CORE_THREADS,0);
//		builder.setIoThreads(UServer.ioThreads);
//		builder.setWorkerThreads(UServer.workerThreads);
//		builder.setDirectBuffers(UServer.directBuffers);
//		builder.setBufferSize(UServer.bufferSize);
		builder.setHandler(root);
		server = builder.build();
		server.start();
		XnioWorker w = server.getWorker();
		System.out.println("IO threads = "+w.getIoThreadCount());
		System.out.println("Worker threads = "+w.getOption(Options.WORKER_TASK_MAX_THREADS));
		for (ListenerInfo l : server.getListenerInfo()) {
			Field f = ListenerInfo.class.getDeclaredField("openListener");
			f.setAccessible(true);
			OpenListener ol = (OpenListener)f.get(l);
			System.out.format("%s -> directBuffers %s bufferSize %d\n",l.getProtcol(),
					ol.getBufferPool().isDirect(),ol.getBufferPool().getBufferSize());
		}
		serverLast = server;
		sessionsLast = sessions;
	}

	public static HttpHandler forceHttps(HttpHandler next) {
		return http -> {
			if (!http.isSecure()) {
				http.setStatusCode(StatusCodes.MOVED_PERMANENTLY);
				http.getResponseHeaders().put(Headers.LOCATION,
						http.getRequestURL().replaceFirst("^http:","https:"));
				http.endExchange();
				return;
			}
			next.handleRequest(http);
		};
	}

	// come and go as websocket connections come and go
	Set<WsSession> wsSessions = ConcurrentHashMap.newKeySet();
	static class WsSession implements Consumer<String> {
		WebSocketChannel channel;
		BlockingQueue<String> wsIn = new LinkedBlockingQueue();
		BlockingQueue<String> wsOut = new LinkedBlockingQueue();
		WsLogic receiver;

		void processMessages() {
			if (channel==null) return;
			while (wsIn.peek()!=null) {
				try {
					receiver.receive(wsIn.take());
				} catch (InterruptedException ex) {
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			}
		}

		public void accept(String output) {
			if (channel==null) return;
			wsOut.add(output);
		}
	}

	public interface WsLogic {
		void receive(String message);
		void activate(Consumer send);
		void deactivate();
	}

	public static HttpHandler ws(Function<WebSocketHttpExchange,WsLogic> onConnect) {
		return userverLast.wshandler(onConnect);
	}

	HttpHandler ws;
	public HttpHandler wshandler(Function<WebSocketHttpExchange,WsLogic> onConnect) {
		if (ws!=null) return ws;
		ws = Handlers.websocket(new WebSocketConnectionCallback() {
			public void onConnect(WebSocketHttpExchange http, WebSocketChannel channel) {
				WsLogic logic = onConnect.apply(http);
				if (logic==null) {
					System.out.println("onConnect() no WsLogic found");
					return;
				}
				WsSession ws = new WsSession();
				ws.channel = channel;
				ws.channel.getReceiveSetter().set(new AbstractReceiveListener() {
					@Override
					protected void onFullTextMessage(WebSocketChannel channel, BufferedTextMessage message) {
						ws.wsIn.add(message.getData());
					}
				});
				ws.channel.addCloseTask(new ChannelListener<WebSocketChannel>() {
					public void handleEvent(WebSocketChannel channel) {
						if (ws.channel!=null) try {
							ws.channel.close();
						} catch (IOException ex) {
							ex.printStackTrace();
						}
						ws.channel = null;
						logic.deactivate();
						wsSessions.remove(ws);
					}
				});
				ws.channel.resumeReceives();
				ws.receiver = logic;
				wsSessions.add(ws);
				logic.activate(ws);
			}
		});
		new Thread(new Runnable() {
			public void run() {
				while (true) {
					for (WsSession s : wsSessions) {
						if (s.channel==null) continue;
						if (s.wsOut.size()==0) continue;
//						s.logic.lastActivity = System.currentTimeMillis();
						try {
							String message = s.wsOut.take();
							WebSockets.sendText(message,s.channel,null);
						} catch (InterruptedException ex) {
							break;
						}
					}
					try {
						Thread.sleep(10);
					} catch (InterruptedException ignore) {}
				}
			}
		},"ws send").start();
		new Thread(new Runnable() {
			public void run() {
				while (true) {
					for (WsSession s : wsSessions) {
						if (s.channel==null) continue;
						if (s.wsIn.size()==0) continue;
//						s.logic.lastActivity = System.currentTimeMillis();
						try {
							s.processMessages();
						} catch (Exception ex) {
							ex.printStackTrace();
						}
					}
					try {
						Thread.sleep(10);
					} catch (InterruptedException ignore) {}
				}
			}
		},"ws process").start();
		return ws;
	}

	private static Consumer<List<Map>> statsOutput;
	public static void stats(long period, Consumer<List<Map>> output) {
		statsOutput = output;
		new Thread(() -> {
			while (true) {
				List<Map> stats = new ArrayList();
				for (Proxy pp : proxies.values()) {
					if (pp.cps==null) continue;
					for (Map.Entry<String,ProxyConnectionPool> e : pp.cps.entrySet()) {
						ProxyConnectionPool cp = e.getValue();
						ClientStatistics cs = cp.getClientStatistics();
						stats.add(MapUtils.asMap(
								"pid",pp.id,"pp",pp.path,"h",e.getKey(),
								"oc",cp.getOpenConnections(),
								"rq",cs.getRequests(),
								"r",cs.getRead(),"w",cs.getWritten()));
					}
				}
				for (ListenerInfo l : serverLast.getListenerInfo()) {
					ConnectorStatistics s = l.getConnectorStatistics();
					stats.add(MapUtils.asMap(
							"p",l.getProtcol(),
							"ac",s.getActiveConnections(),"acMax",s.getMaxActiveConnections(),
							"ar",s.getActiveRequests(),"arMax",s.getMaxActiveRequests(),
							"rq",s.getRequestCount(),"rqErr",s.getErrorCount(),
							"t",s.getProcessingTime(),"tMax",s.getMaxProcessingTime(),
							"s",s.getBytesSent(),"r",s.getBytesReceived()));
				}
				if (sessionsLast!=null) {
					SessionManagerStatistics s = sessionsLast.getStatistics();
					stats.add(MapUtils.asMap(
							"sm",sessionsLast.getDeploymentName(),
							"as",s.getActiveSessionCount(),"asMax",s.getHighestSessionCount(),
							"at",s.getAverageSessionAliveTime(),"atMax",s.getMaxSessionAliveTime(),
							"sc",s.getCreatedSessionCount(),"se",s.getExpiredSessionCount(),
							"sr",s.getRejectedSessions(),"slimit",s.getMaxActiveSessions(),
							"st",s.getStartTime()));
				}
				output.accept(stats);
				try {
					Thread.sleep(period);
				} catch (InterruptedException ex) {}
			}
		},"stats").start();
	}

	public static void installProxy(Proxy p, PathHandler ph, NameVirtualHostHandler vh) throws Exception {
		if (p.pc==null) {
			ExclusivityChecker exclusivityCheck = new ExclusivityChecker() {
				@Override
				public boolean isExclusivityRequired(HttpServerExchange exchange) {
					if (exchange.getRequestPath().contains("/HEARTBEAT/"))
						return true;
					if ("Upgrade".equals(exchange.getRequestHeaders().getFirst("Connection")))
						return true;
					return false;
				}
			};
			p.pc = new LoadBalancingProxyClient(exclusivityCheck) {
				@Override
				public void getConnection(ProxyTarget target, HttpServerExchange exchange,
						ProxyCallback<ProxyConnection> callback, long timeout, TimeUnit timeUnit) {
//					System.out.println("getConnection "+exchange);
					super.getConnection(target,exchange,callback,timeout,timeUnit);
				}
			};
//			p.pc.setMaxQueueSize(1)
//			p.pc.setSoftMaxConnectionsPerThread(1)
//			p.pc.setConnectionsPerThread(1);
			p.pc.setConnectionsPerThread(p.connectionsPerThread);
			p.pc.setSoftMaxConnectionsPerThread(p.softMaxConnectionsPerThread);
			p.pc.setTtl(10000);

			OptionMap om = OptionMap.builder() // for client connection
					.set(UndertowOptions.ENABLE_STATISTICS,true)
					.getMap();
//			for (String h : p.hosts)
//				p.pc.addHost(new URI(h),p.cookie,null,om);
			for (String h : p.hosts)
				p.pc.addHost(new URI(h),null,null,om);
//			pc.addHost(new URI("https://localhost:8887"),"VSESSION-cs",xnioSsl)
			p.pc.addSessionCookieName(p.cookie);
		}
		if (p.cps==null) {
			Map<String,ProxyConnectionPool> pools = new HashMap();
			Field r = LoadBalancingProxyClient.class.getDeclaredField("hosts");
			r.setAccessible(true);
			LoadBalancingProxyClient.Host[] hosts = (LoadBalancingProxyClient.Host[])r.get(p.pc);
			for (LoadBalancingProxyClient.Host h : hosts) {
				r = Host.class.getDeclaredField("connectionPool");
				r.setAccessible(true);
				pools.put(h.getUri().toString(),(ProxyConnectionPool)r.get(h));
			}
			p.cps = pools;
		}
		if (p.h==null) {
			p.h = Handlers.proxyHandler(p.pc,p.maxRequestTime,handler(p.fallbackHandler));
//			p.h = new EncodingHandler(p.h,new ContentEncodingRepository()
//					.addEncodingHandler("gzip",
//							new GzipEncodingProvider(),50,
//							Predicates.parse("max-content-size[5]")));
		}
		if (p.names!=null && p.names.length>0)
			for (String name : p.names)
				vh.addHost(name,new CorsFilter(p.h) {
					@Override
					protected boolean isAllowed(String origin, String url) {
						return true;
					}
				});
		if (p.path!=null)
			ph.addPrefixPath(p.path,p.h);
	}
	static HttpHandler handler(String name) {
		if ("200".equals(name)) return ResponseCodeHandler.HANDLE_200;
		if ("403".equals(name)) return ResponseCodeHandler.HANDLE_403;
		if ("404".equals(name)) return ResponseCodeHandler.HANDLE_404;
		if ("405".equals(name)) return ResponseCodeHandler.HANDLE_405;
		if ("406".equals(name)) return ResponseCodeHandler.HANDLE_406;
		if ("500".equals(name)) return ResponseCodeHandler.HANDLE_500;
		return null;
	}

	public static Map<String,Proxy> proxies = new LinkedHashMap();
	public static class Proxy {
		public String id;
		public String[] names;
		public String path;
		public String cookie;
		public Set<String> hosts = new LinkedHashSet();
		public int maxRequestTime = -1;
		public int connectionsPerThread = 50;
		public int softMaxConnectionsPerThread = 30;
		public String fallbackHandler;

		public LoadBalancingProxyClient pc;
		public HttpHandler h;
		public Map<String,ProxyConnectionPool> cps;
	}

	public static SSLContext ssl;
	public static SSLContext initSSLContext(KeyStore keyStore, KeyStore trustStore, String keyStorePassword) throws IOException {
		try {
			KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
			kmf.init(keyStore,keyStorePassword!=null ? keyStorePassword.toCharArray() : null);
			TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
			tmf.init(trustStore);
			ssl = SSLContext.getInstance("TLS");
			ssl.init(kmf.getKeyManagers(),tmf.getTrustManagers(),null);
			return ssl;
		} catch (Exception ex) {
			throw new IOException("Unable to create and initialize the SSLContext",ex);
		}
	}

	public static KeyStore loadKeyStore(String name, String password) throws IOException {
		if (name==null) return null;
		try (InputStream is = new FileInputStream(name)) {
			KeyStore ks = KeyStore.getInstance("JKS");
			ks.load(is,password.toCharArray());
			return ks;
		} catch (Exception ex) {
			throw new IOException(String.format("Unable to load KeyStore %s",name),ex);
		}
	}
}
