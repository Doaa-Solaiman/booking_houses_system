package de.scheller.platform.network;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.apache.logging.log4j.core.config.plugins.util.PluginManager;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.java_websocket.WebSocket;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.ServerHandshake;
import org.java_websocket.server.WebSocketServer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.ToNumberPolicy;

import de.scheller.platform.apis.ServiceListener;
import de.scheller.platform.common.ByName;
import de.scheller.platform.common.CollectionUtils;
import de.scheller.platform.common.EventBus;
import de.scheller.platform.common.IValue.ByNameWithDefaults;
import de.scheller.platform.common.ItemStore.StoreItem;
import de.scheller.platform.common.Logging;
import de.scheller.platform.common.MapUtils;
import de.scheller.platform.network.rmi.RmiClient;
import de.scheller.platform.network.rmi.RmiServer;

/**
 * messages over mqtt:<ul>
 * <li>&lt;prefixAll&gt;/&lt;host&gt;/node/&lt;nodeName&gt;/advertise</li>
 * <li>&lt;prefixAll&gt;/&lt;host&gt;/node/&lt;nodeName&gt;/heartbeat</li>
 * <li>&lt;prefixAll&gt;/&lt;host&gt;/proc/&lt;processId&gt;/configure</li>
 * <li>&lt;prefixAll&gt;/&lt;message&gt;/&lt;messageId&gt;</li>
 * <li>&lt;prefixAll&gt;/&lt;event&gt;/&lt;eventType&gt;</li>
 * </ul>
 *
 * @author kandzia
 */
public class Network
{
	private static Logger logger;
	private static Logger logger() {
		if (logger==null)
			logger = LoggerFactory.getLogger(Network.class.getPackage().getName());
		return logger;
	}

	public static class NodeFound {
		public interface Listener { void nodeFound(NodeFound event); }
	}
	public static class Connected {
		public interface Listener { void connected(NodeFound event); }
	}

	static enum Protocol { MQTT, WS, JNC };
	static long timeInit;
	static long timeConfigure;
	static String type;
	public static String name;
	public static String realm;
	public static String host;
	public static String hostip;
	public static String proc;
	static String cmdline;
	static String[] args;
	static String prefix = "de.scheller.platform/";
	static String prefixAll = prefix;
	static String prefixSelf;
	static Mqtt mqtt;
	static EventBus eventbus;
	static WsServer wss;
	static WsClient wsc;
	static CountDownLatch waitForConfig = new CountDownLatch(1);
	public static Function<String,String> sendRewrite;
	public static Function<String,String> receiveRewrite;
	public static Map<String,Consumer<ByName>> procActions = new HashMap();

	public static void init(String[] args, String nodeName) {
		Network.type = nodeName;
		Network.name = nodeName;
		Network.host = getHostName();
		Network.hostip = getHostAddr();
		Network.proc = getProcessId();
		Network.timeInit = System.currentTimeMillis();
		Network.realm = System.getProperty("PLATFORM_REALM");
		if (Network.realm==null)
			Network.realm = System.getenv("PLATFORM_REALM");
		if (Network.realm==null)
			Network.realm = "realm";
		Network.prefixAll += Network.realm + "/";
		Network.prefixSelf = prefixAll + host + "/node/" + name + "/";

		String cmdline = getJavaCommandLines().get(getProcessId());
		String jvmargs = "-agentlib:jdwp=transport=dt_socket,suspend=n,server=y,address=8105";
		cmdline = cmdline.replaceFirst("\"?-javaagent:\\S*\"?",""); // e.g. remove eclipse java agent
		cmdline = cmdline.replaceFirst("\"?-agentlib:\\S*\"?",""); // e.g. remove eclipse agent config
		cmdline = cmdline.replaceAll("\\s+"," ");
		Network.cmdline = cmdline;
		Network.args = args;
//		cmdline = cmdline.replaceFirst("(\"[^\"]+\"|\\S+)","$1 "+jvmargs); // add own jvm args
//		writeCmdLine(MapUtils.asMap("time",Network.timeInit,"cmdline",cmdline,"args",args));

		File conf = new File("logging.properties"); // TODO get rid of. go central (re)configuration!
		if (!conf.exists())
			conf = new File("conf.properties"); // TODO get rid of conf.properties (unspecific filename)
		if (conf.exists()) try {
			PluginManager.addPackage("de.scheller.platform.network.log");
			Properties loggingProps = new Properties();
			loggingProps.load(new FileReader(conf));
			Logging.props.putAll((Map)loggingProps);
			Logging.initLoggingSystem();
			logger();
		} catch (Exception ex) {
			logger().error("Failed to configure logging",ex);
		}
		Network.eventbus = new EventBus(false); // EventBus demands logger!
	}

	public static void start() {
		String platformBus = System.getProperty("PLATFORM_BUS");
		if (platformBus==null)
			platformBus = System.getenv("PLATFORM_BUS");
		Network.mqtt = new Mqtt(platformBus,host+"_"+name,false);
		System.gc();

		Network.listenToAll("requestAdvertise","onRequestAdvertise");
		Network.listenToAll("+/node/+/advertise","onAdvertisement"); // host/name
		Network.listenToAll("+/proc/+/configure","onProcessConfigure"); // host/proc
		Network.listenToAll("+/proc/+/action","onProcessAction"); // host/proc
		Network.listener((topic,data) -> {
			if (topic.equals("onRequestAdvertise")) {
				new Thread(() -> {
					try { Thread.sleep(1000); } catch (InterruptedException ignore) {}
					advertise();
				},"advertise").start();
			}
			if (topic.equals("onAdvertisement")) {
				String host = (String)((Map)data.get("pathmatch")).get(1);
				String name = (String)((Map)data.get("pathmatch")).get(2);
				if (name.equals(Network.name)) return; // message from ourself
				Object body = data.get("body");
				if (body==null) return; // empty message
				ByNameWithDefaults info = ByName.get(body).withDefaults();
				int wsPort = info.asInt("wsport",0); // WS port
				if (wsPort!=0) {
					availWsServers.put(host+"/"+name,host+":"+wsPort);
					startWsClients();
				} else {
					availWsServers.remove(host+"/"+name);
				}
				int rmiPort = info.asInt("rmiport",0); // RMI port
				if (rmiPort!=0) {
					List<String> services = (List<String>)info.value("services");
					if (services==null || services.isEmpty())
						return; // no RMI services
					String givenServiceAddress = host+":"+rmiPort;
					List<String> avail = new ArrayList();
					for (String service : services) {
						try {
							Class.forName(service);
							String knownServiceAddress = availServiceServers.get(service);
							if (givenServiceAddress.equals(knownServiceAddress))
								continue;
							availServiceServers.put(service,givenServiceAddress);
							avail.add(service);
						} catch (ClassNotFoundException ex) {
							continue;
						}
					}
					notifyAvailServices(avail);
				}
			}
			if (topic.equals("onProcessConfigure")) {
				String host = (String)((Map)data.get("pathmatch")).get(1);
				String proc = (String)((Map)data.get("pathmatch")).get(2);
				if (!host.equals(Network.host)) return;
				if (!proc.equals(Network.proc)) return;
				try {
					message("apply configuration");
					timeConfigure = System.currentTimeMillis();
					PlatformNode.updateRootConfig((Map<String,Object>)data.get("body"));
					PlatformNode.loadConfigsFromDatabase();
					PlatformNode.configure();
					PlatformNode.configs.flush();
				} catch (SQLException ex) {
					ex.printStackTrace();
				}
				waitForConfig.countDown();
			}
			if (topic.equals("onProcessAction")) {
				String host = (String)((Map)data.get("pathmatch")).get(1);
				String proc = (String)((Map)data.get("pathmatch")).get(2);
				if (!host.equals(Network.host)) return;
				if (!proc.equals(Network.proc)) return;
				ByName info = ByName.get(data.get("body"));
				String action = info.asString("action");
				if ("stop".equals(action)) {
					System.exit(0);
				} else if ("gc".equals(action)) {
					System.gc();
					message(info.asString("id"),"GC done.");
				} else if (procActions.get(action)!=null) {
					procActions.get(action).accept(info);
				}
			}
		});
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				message("using shutdown hooks");
				logger().info("SOFT SHUTDOWN");
			}
		});

		// https://stackoverflow.com/questions/28568188/java-net-uri-get-host-with-underscores#44129608
		try {
			if (new URL("https://host_name_with_underscores.com").getHost()==null) {
				patchUriField(35184372088832L,"L_DASH");
				patchUriField(2147483648L,"H_DASH");
			}
		} catch (Exception ex) {
			logger().warn("JVM URI patch did not apply. URIs containing underscores may be problematic.");
		}
	}

	private static void patchUriField(Long maskValue, String fieldName) throws Exception {
		Field field = URI.class.getDeclaredField(fieldName);
		Field modifiers = Field.class.getDeclaredField("modifiers");
		modifiers.setAccessible(true);
		modifiers.setInt(field,field.getModifiers() & ~Modifier.FINAL);
		field.setAccessible(true);
		field.setLong(null,maskValue);
	}

	static String getHostName() {
		try { return exec("hostname").trim(); }
		catch (Exception ignore) {}
		try { return InetAddress.getLocalHost().getHostName(); }
		catch (UnknownHostException ex) { return "<unknown>"; }
	}
	static String getHostAddr() {
		try {
			StringBuilder sb = new StringBuilder();
			for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces()))
				if (!ni.isLoopback()) for (InetAddress a : Collections.list(ni.getInetAddresses()))
					sb.append(sb.length()>0 ? "," : "").append(a.getHostAddress());
			return sb.toString();
		} catch (SocketException ignore) {}
		try { return InetAddress.getLocalHost().getHostAddress(); }
		catch (UnknownHostException ex) { return "<unknown>"; }
	}
	static String getProcessId() {
		return ManagementFactory.getRuntimeMXBean().getName().replaceFirst("@.*","");
	}
	static long getProcessUsedMemory() {
		return Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
	}
	static long getProcessFreeMemory() {
		return Runtime.getRuntime().maxMemory() - getProcessUsedMemory();
	}

	static ObjectName mbNameOS;
	static MBeanServer mbs;
	static MBeanServer mbs() {
		if (mbs!=null) return mbs;
		try {
			mbNameOS = ObjectName.getInstance("java.lang:type=OperatingSystem");
			mbs = ManagementFactory.getPlatformMBeanServer();
		} catch (MalformedObjectNameException ex) {
			ex.printStackTrace();
		}
		return mbs;
	}
	static short getProcessCpuLoad() throws Exception {
		Double load = (Double)mbs().getAttribute(mbNameOS,"ProcessCpuLoad");
		if (load==-1.0) return -1;
		return (short)(load * 10000); // percentage with 2 decimal point precision
	}
	static short getSystemCpuLoad() throws Exception {
		Double load = (Double)mbs().getAttribute(mbNameOS,"SystemCpuLoad");
		if (load==-1.0) return -1;
		return (short)(load * 10000); // percentage with 2 decimal point precision
	}

	static boolean isWindows() {
		return System.getProperty("os.name").toLowerCase().contains("win");
	}
	static Map<String,String> getJavaCommandLines() {
		Map<String,String> cmdlines = new HashMap();
		try {
			if (isWindows()) {
				String command = "wmic path win32_process "	+
						"where \"Name like '%java%'\" get processid,commandline /format:rawxml";
				String result = exec(command);
				Document doc = Jsoup.parse(result);
				for (Element e : doc.select("instance[classname=Win32_Process]")) {
					String pid = e.select("property[name=ProcessId] value").text();
					String cmd = e.select("property[name=CommandLine] value").text();
					cmdlines.put(pid,cmd);
				}
			} else {
				String command = "ps x";
				String result = exec(command);
				Map<String,Integer> columns = new HashMap();
				for (String line : result.split("[\r\n|\r|\n]+")) {
					if (columns.isEmpty()) {
						String[] split = line.trim().split("\\s+");
						for (int i=0; i<split.length; i++)
							columns.put(split[i].toUpperCase(),i);
					} else {
						String[] split = line.trim().split("\\s+",columns.size());
						String pid = split[columns.get("PID")];
						String cmd = split[columns.get("COMMAND")];
						if (cmd.startsWith("java"))
							cmdlines.put(pid,cmd);
					}
				}
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return cmdlines;
	}
	static String exec(String command) throws Exception {
		StringBuilder sb = new StringBuilder();
		Process p = Runtime.getRuntime().exec(command);
		Thread t = new Thread(new Runnable() {
			public void run() {
				Reader r = new InputStreamReader(p.getInputStream());
				BufferedReader br = new BufferedReader(r);
				try {
					for (String line=null; (line = br.readLine())!=null;)
						sb.append(line).append(System.lineSeparator());
					sb.setLength(sb.length()-System.lineSeparator().length());
				} catch (IOException ex) {
					ex.printStackTrace();
				}
			}
		});
		t.start();
		p.waitFor();
		t.join();
		return sb.toString();
	}

	public static void event(String eventType, String message) {
		mqtt.publish(prefixAll+"event/"+eventType,message);
	}
	public static void message(String id, String message) {
		mqtt.publish(prefixAll+"message/"+id,message);
	}
	public static void message(String message) {
		message(host+"_"+proc,message);
	}
	public static void publish(String path, String message) {
		mqtt.publish(prefixAll+path,message);
	}
	public static void requestAdvertise() {
		mqtt.publish(prefixAll+"requestAdvertise","?!");
	}
	public static void advertise() {
//		mqtt.publishRetain(prefixSelf+"advertise",new Gson().toJson(writeNodeInfo()));
		mqtt.publishRetain(prefixSelf+"advertise",new Gson().toJson(nodeInfo()));
	}
	public static void deadvertise() {
		mqtt.publishRetain(prefixSelf+"advertise","");
	}
	static Map nodeInfo() {
		long since = timeInit;
		long uptime = System.currentTimeMillis()-timeInit;
		try {
			RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
			since = runtime.getStartTime();
			uptime = runtime.getUptime();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		Map nodeInfo = MapUtils.asMap(
				"realm",realm,"host",host,"type",type,"name",name,
				"proc",proc,"cmdline",cmdline,"args",args,
				"since",since,"uptime",uptime,"cfgtime",timeConfigure,
				"addr",hostip,"mqtt",mqtt.clientId,"services",localServices);
		if (rmiServer!=null) nodeInfo.put("rmiport",rmiServer.getBoundPort());
		if (wss!=null) nodeInfo.put("wsport",wss.getPort());
		return nodeInfo;
	}
//	static File nodeInfoFile() { return new File("supervisor/"+host+"."+name+".nodeinfo.json"); }
//	static Map readNodeInfo() { return readJson(nodeInfoFile(),Map.class); }
//	static Map writeNodeInfo() { return writeJson(nodeInfoFile(),nodeInfo()); }
//	static File cmdLineFile() { return new File("supervisor/"+host+"."+name+".cmdline.json"); }
//	static Map readCmdLine() { return readJson(cmdLineFile(),Map.class); }
//	static Map writeCmdLine(Map cmdline) { return writeJson(cmdLineFile(),cmdline); }
//	static <T> T readJson(File file, Class<T> type) {
//		try (FileReader reader = new FileReader(file)) {
//			return new Gson().fromJson(reader,type);
//		} catch (Exception ex) {
//			ex.printStackTrace();
//			return null;
//		}
//	}
//	static <T> T writeJson(File file, T data) {
//		try (FileWriter writer = new FileWriter(file)) {
//			file.getParentFile().mkdirs();
//			new GsonBuilder().setPrettyPrinting().create().toJson(data,writer);
//			return data;
//		} catch (Exception ex) {
//			ex.printStackTrace();
//			return null;
//		}
//	}

	public static boolean waitForConfiguration(int timeout) {
		try {
			return waitForConfig.await(timeout,TimeUnit.MILLISECONDS);
		} catch (InterruptedException ex) {
			return waitForConfig.getCount()==0;
		}
	}
	public static Map<String,Object> getConfig() {
		StoreItem config = PlatformNode.getConfig(".config");
		return config!=null ? config.access().get(Map.class) : Collections.EMPTY_MAP;
	}

	public static void listenTo(String pattern, String event) {
		mqtt.topics.put(prefixSelf+pattern,event);
		mqtt.resubscribe();
	}
	public static void listenToAll(String pattern, String event) {
		mqtt.topics.put(prefixAll+pattern,event);
		mqtt.resubscribe();
	}
	public static void listener(Listener listener) {
		listeners.add(listener);
	}
	public static void onAction(String actionName, Consumer<ByName> action) {
		procActions.put(actionName,action);
	}
	private static List<Listener> listeners = syncedList();
	public interface Listener {
		void onMessage(String topic, Map<String,Object> data);
	}

	static MqttClient mqttClient(String mqttUri, String mqttClientId, boolean persistent,
			Consumer<Map.Entry<String,MqttMessage>> incoming,
			Consumer<Throwable> connectionLost) throws MqttException {
		String mqttBroker = null;
		String mqttUser = null;
		String mqttPass = null;
		if (mqttUri!=null) {
			URI uri = URI.create(mqttUri);
			int port = uri.getPort();
			mqttBroker = uri.getScheme()+"://"+uri.getHost();
			if (port>=0) mqttBroker += ":"+port;
			String userInfo = uri.getUserInfo();
			if (userInfo!=null) {
				String[] userPass = userInfo.split(":",2);
				mqttUser = userPass[0];
				if (userPass.length==2)
					mqttPass = userPass[1];
			}
		}
		MemoryPersistence persistence = new MemoryPersistence();
		MqttClient mqttClient = new MqttClient(mqttBroker,mqttClientId,persistence);
		mqttClient.setCallback(new MqttCallback() {
			public void messageArrived(String topic, MqttMessage m) {
				incoming.accept(new AbstractMap.SimpleEntry(topic,m));
			}
			public void deliveryComplete(IMqttDeliveryToken token) {}
			public void connectionLost(Throwable cause) {
				connectionLost.accept(cause);
			}
		});
		MqttConnectOptions connOpts = new MqttConnectOptions();
		connOpts.setCleanSession(!persistent);
		if (mqttUser!=null)
			connOpts.setUserName(mqttUser);
		if (mqttPass!=null)
			connOpts.setPassword(mqttPass.toCharArray());
		connOpts.setWill(prefixSelf+"gone","".getBytes(),1,true);
		mqttClient.connect(connOpts);
		return mqttClient;
	}

	static class Mqtt {
		Runnable tryToConnect;
		Runnable heartbeat;
		Runnable processMessages;
		boolean connected;
		boolean connecting;
		String clientId;
		MqttClient mqtt;
		Map<String,String> topics = Collections.synchronizedMap(new LinkedHashMap());
		Set<String> subscribed = Collections.synchronizedSet(new LinkedHashSet());
		BlockingQueue<Map.Entry<String,MqttMessage>> mq = new LinkedBlockingQueue();

		public Mqtt(String mqttUri, String mqttClientId, boolean persistent) {
			clientId = mqttClientId;
			tryToConnect = new Runnable() {
				@Override
				public void run() {
					if (mqtt!=null) try {
						Thread.sleep(2000);
					} catch (InterruptedException ignore) {}
					connecting = true;
					MqttClient mqttClient = null;
					while (mqttClient==null || !mqttClient.isConnected()) {
						try {
							Mqtt.this.mqtt = mqttClient(mqttUri,mqttClientId,persistent,
									incoming -> mq.add(incoming),
									cause -> {
										cause.printStackTrace();
										if (connected && !connecting)
											new Thread(tryToConnect,"mqtt_reconnect").start();
										connected = false;
										Mqtt.this.connectionLost();
									});
							connected = true;
							resubscribe();
							new Thread(heartbeat,"mqtt_heartbeat").start();
							Mqtt.this.connected();
							break;
						} catch (MqttException ex) {
							// keep quiet at ConnectException, we're a (re)connect thread.
							Throwable cause = ex.getCause();
							if (cause instanceof ConnectException==false)
								logger().error(ex.getMessage(),ex);
							if (cause instanceof IOException==false)
								break;
						} catch (Exception ex) {
							if (ex instanceof ConnectException==false)
								logger().error(ex.getMessage(),ex);
							if (ex instanceof IOException==false)
								break;
						}
						try {
							Thread.sleep(2000);
						} catch (InterruptedException ignore) {}
					}
					connecting = false;
				}
			};
			heartbeat = new Runnable() {
				public void run() {
					long wait = 2000;
					while (connected) {
						try {
							ByteBuffer bb = ByteBuffer.allocate(8*3+2*2);
							bb.putLong(System.currentTimeMillis());
							bb.putLong(getProcessUsedMemory());
							bb.putLong(getProcessFreeMemory());
							bb.putShort(getProcessCpuLoad());
							bb.putShort(getSystemCpuLoad());
							if (mqtt.isConnected())
								mqtt.publish(prefixSelf+"heartbeat",bb.array(),0,true);
							wait = 2000;
						} catch (Exception ignore) {
							wait = 10000;
						}
						try {
							Thread.sleep(wait);
						} catch (InterruptedException ignore) {}
					}
					logger().warn("heartbeat stopped");
				}
			};
			processMessages = new Runnable() {
				public void run() {
					while (true) {
						try {
							Entry<String,MqttMessage> message = mq.take();
							String topic = message.getKey();
							MqttMessage m = message.getValue();
							try {
								if (Network.receiveRewrite!=null && topic!=null)
									topic = Network.receiveRewrite.apply(topic);
								if (topic!=null)
									handleMessage(topic,m);
							} catch (IllegalStateException ex) {
								logger().error(".messageArrived() failed ({}) on {}: {}",ex.getMessage(),topic,m);
							} catch (Throwable ex) {
								logger().error(".messageArrived() fail on {}: {}",topic,m,ex);
							} finally {
								MDC.clear();
							}
						} catch (InterruptedException ex) {}
					}
				}
			};
			new Thread(processMessages,"mqtt_process").start();
			new Thread(tryToConnect,"mqtt_connect").start();
		}
		void resubscribe() {
			if (mqtt==null || !mqtt.isConnected())
				return;
			try {
				Set<String> topicset = new LinkedHashSet();
				for (String topic : topics.keySet())
					topicset.add(topic.replaceFirst("#.*","#"));
				synchronized (subscribed) {
					for (String topic : subscribed)
						mqtt.unsubscribe(topic);
					subscribed.clear();
					for (String topic : topicset)
						mqtt.subscribe(topic);
					subscribed.addAll(topicset);
				}
			} catch (MqttException ex) {
				ex.printStackTrace();
			}
		}

		public void publish(String topic, byte[] message) {
			publish(topic,message,1,false);
		}
		public void publish(String topic, String message) {
			publish(topic,message.getBytes(StandardCharsets.UTF_8),1,false);
		}
		public void publishRetain(String topic, String message) {
			publish(topic,message.getBytes(StandardCharsets.UTF_8),1,true);
		}
		public void publish(String topic, byte[] message, int qos, boolean retain) {
			if (mqtt==null || !mqtt.isConnected())
				return;
			try {
				if (Network.sendRewrite!=null && topic!=null)
					topic = Network.sendRewrite.apply(topic);
				if (topic!=null)
					mqtt.publish(topic,message,qos,retain);
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}

		public void connected() {
			logger().info("MQTT connected");
			advertise();
		}

		public void connectionLost() {
			logger().info("MQTT connection lost");
		}

		public void handleMessage(String topic, MqttMessage msg) throws Exception {
			for (String pattern : subscribed.toArray(new String[0])) {
				String re = pattern;
				re = re.replaceAll("(?<=^|/)(\\+)(?=/|$)","([^/]+)");
				re = re.replaceAll("(?<=^|/)(#)(?=/|$)","(.*)");
				Pattern p = Pattern.compile(re);
				Matcher m = p.matcher(topic);
				if (m.matches()) {
					Map data = new HashMap();
					data.put("path",m.group());
					Map pathmatches = new LinkedHashMap();
					for (int i=1; i<=m.groupCount(); i++)
						pathmatches.put(i,m.group(i));
					data.put("pathmatch",pathmatches);
					data.put("retained",msg.isRetained());
					String type = topics.get(pattern);
					notifyListeners(type,data,msg.getPayload());
					break;
				}
			}
		}
	}

	private static void notifyListeners(String type, Map data, Object message) {
		String str = null;
		if (message instanceof String)
			str = ((String)message).trim();
		else if (message instanceof byte[])
			str = new String((byte[])message,StandardCharsets.UTF_8).trim();
		Object body = null;
		if (str!=null && str.trim().length()>0) {
			GsonBuilder gb = new GsonBuilder();
			gb.setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE);
			Gson gson = gb.create();
			char c0 = str.charAt(0);
			if (body==null && c0=='{') try {
				body = gson.fromJson(str,Map.class);
			} catch (Exception ignore) {}
			if (body==null && c0=='[') try {
				body = gson.fromJson(str,List.class);
			} catch (Exception ignore) {}
		}
		if (body==null)
			body = message;
		data.put("body",body);
		data.put("bodyAsString",str);
		for (Listener l : listeners.toArray(new Listener[listeners.size()]))
			l.onMessage(type,data);
	}

	public static void wsevent(String eventType, Object message) {
		if (wss==null) return;
//		if (logger().isDebugEnabled())
//			logger().debug("Network.wsevent() {}",Log.maxlen(100,message,true));
		wss.broadcast(eventType+"|"+host+"_"+proc+"|"+message);
	}

	public static void wssend(String service, String message) {
		for (Map.Entry<String,WsClient> e : wsClients.entrySet())
			if (service==null || e.getKey().equals(service) || e.getKey().endsWith("/"+service))
				e.getValue().send(message);
	}

	public static void startWsServer(int port) {
		new WsServer(port);
	}

	private static boolean wsConnect;
	private static Set<String> wsConnectHosts;
	private static Set<String> wsConnectNames;
	private static Map<String,String> availWsServers = syncedMap();
	private static Map<String,WsClient> wsClients = syncedMap();

	public static void startWsClients(String host, String name) {
		wsConnectHosts = host==null ? null : CollectionUtils.asSet((Object[])host.split("[,;\\s]+"));
		wsConnectNames = name==null ? null : CollectionUtils.asSet((Object[])name.split("[,;\\s]+"));
		wsConnect = true;
	}

	public static void startWsClients() {
		if (!wsConnect) return;
		for (Map.Entry<String,String> e : availWsServers.entrySet()) {
			String[] hostAndName = e.getKey().split("/",2);
			if (wsConnectHosts!=null && !wsConnectHosts.contains(hostAndName[0])) continue;
			if (wsConnectNames!=null && !wsConnectNames.contains(hostAndName[1])) continue;
			if (!wsClients.containsKey(e.getValue()))
				wsClients.put(e.getKey(),new WsClient(e.getValue()) {
					@Override
					public void onClose(int code, String reason, boolean remote) {
						super.onClose(code,reason,remote);
						wsClients.remove(e.getKey());
					}
				});
		}
	}

	private static class WsClient extends WebSocketClient {
		String wsUri;
		public WsClient(String wsUri) {
			super(URI.create("ws://" + wsUri));
			this.wsUri = wsUri;
			connect();
		}
		@Override
		public void onOpen(ServerHandshake handshakeData) {
			System.out.println("# Connection with SuperPeer open. Status: " + handshakeData.getHttpStatus());
		}
		@Override
		public void onMessage(String message) {
			System.out.println("Network.WsClient.onMessage() "+message);
		}
		@Override
		public void onClose(int code, String reason, boolean remote) {
			System.out.println("Connection closed. Code/reason/remote: " + code + "/" + reason + "/" + remote);
		}
		@Override
		public void onError(Exception ex) {
			System.out.println("Terrible fail: ");
			ex.printStackTrace();
		}
//		public void emit(String data) {
//			System.out.println("WsPeers.AsClient.emit() "+data);
//			this.send(data);
//		}
	}

	private static class WsServer extends WebSocketServer {
		public WsServer(int port) {
			super(new InetSocketAddress(port));
			setReuseAddr(true);
			setTcpNoDelay(true);
			start();
		}
		@Override
		public void onOpen(WebSocket conn, ClientHandshake handshake) {
			System.out.println("Connected new peer: " + conn.getRemoteSocketAddress().toString());
		}
		@Override
		public void onClose(WebSocket conn, int code, String reason, boolean remote) {
			if (conn!=null)
				System.out.println("Peer " + conn.getRemoteSocketAddress() + " closed the connection for reason (code): " + reason + " (" + code + ")");
		}
		@Override
		public void onMessage(WebSocket conn, String message) {
			notifyListeners("onWebsocket",new HashMap(),message);
		}
		@Override
		public void onError(WebSocket conn, Exception ex) {
			if (conn!=null)
				System.out.println("# Exception occured on connection: " + conn.getRemoteSocketAddress());
			ex.printStackTrace();
		}
		@Override
		public void onStart() {
			Network.wss = this;
			advertise();
		}
//		public void emit(String data) {
//			System.out.println("WsPeers.AsServer.emit() "+data);
//			for (WebSocket conn : this.getConnections())
//				conn.send(data);
//		}
	}

	/** Short-term usage of a service.
	 * Caller must be robust against service unavailability and method RemoteExceptions. */
	public static <S> S getService(Class<S> service) {
		logger().info("Network.getService() {}",service);
		return (S)service(service.getName());
	}
	/** Long-term usage of a service.
	 * Caller must be robust against service availability fluctuation. */
	public static <S> void getService(Class<S> service, ServiceListener<S> listener) {
		logger().info("Network.getService() {}",service);
		String serviceName = service.getName();
		List<ServiceListener> list = availServiceListeners.get(serviceName);
		if (list==null) availServiceListeners.put(serviceName,list = new ArrayList());
		list.add(listener);
		String server = availServiceServers.get(serviceName);
		if (server==null) return;
		S serviceIntf = (S)service(serviceName);
		if (serviceIntf!=null)
			listener.serviceAvailable(serviceIntf);
	}

	public static <S> void putService(Class<S> service, S serviceImpl) {
		rmiServer().export(service.getName(),serviceImpl);
		localServices.add(service.getName());
		advertise();
	}

	private static void notifyAvailServices(List<String> available) {
		for (String service : available) {
			List<ServiceListener> list = availServiceListeners.get(service);
			if (list==null) continue;
			Object serviceIntf = service(service);
			if (serviceIntf!=null)
				for (ServiceListener l : list)
					l.serviceAvailable(serviceIntf);
		}
	}
	private static Object service(String service) {
		String server = availServiceServers.get(service);
		if (server==null) return null;
		RmiClient client = rmiClients.get(server);
		if (client==null) {
			String[] hostAndPort = server.split(":");
			try {
				client = new RmiClient(hostAndPort[0],Integer.parseInt(hostAndPort[1]),false);
				rmiClients.put(server,client);
				logger().info("obtained service {} from {}",service,server);
			} catch (Exception ex) {
				logger().error("could not obtain service {}, reason: {}",service,ex.getMessage(),ex);
//				ex.printStackTrace();
				return null;
			}
		}
		return client.findService(service);
	}

	private static Map<String,List<ServiceListener>> availServiceListeners = syncedMap();
	private static Map<String,String> availServiceServers = syncedMap();
	private static List<String> localServices = syncedList();
	private static Map<String,RmiClient> rmiClients = syncedMap();
	private static RmiServer rmiServer;
	private static RmiServer rmiServer() {
		if (rmiServer!=null)
			return rmiServer;
		int port = 0;
		rmiServer = new RmiServer(port,false);
		rmiServer.start();
		while ((port = rmiServer.getBoundPort())==0)
			try { Thread.sleep(10); } catch (InterruptedException ex) {}
		return rmiServer;
	}

	private static Map syncedMap() { return Collections.synchronizedMap(new HashMap()); }
	private static List syncedList() { return Collections.synchronizedList(new ArrayList()); }
}
