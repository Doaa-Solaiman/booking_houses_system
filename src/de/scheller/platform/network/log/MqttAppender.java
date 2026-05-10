package de.scheller.platform.network.log;

import java.io.Serializable;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderFactory;
import org.apache.logging.log4j.core.config.plugins.validation.constraints.Required;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttPersistenceException;
import org.eclipse.paho.client.mqttv3.MqttSecurityException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.scheller.platform.network.Network;

@Plugin(name = "MqttAppender", category = "Core", elementType = "appender", printObject = true)
public class MqttAppender extends AbstractAppender implements MqttCallback {


	public static final String PLUGIN_NAME = "File";

	public static class Builder<B extends Builder<B>> extends AbstractAppender.Builder<B>
			implements org.apache.logging.log4j.core.util.Builder<MqttAppender> {

		@PluginBuilderAttribute
		@Required
		private String broker;

		@PluginBuilderAttribute
		@Required
		private String clientId;

		@PluginBuilderAttribute
		@Required
		private String topic;

		@Override
		public MqttAppender build() {
//			final Layout<? extends Serializable> layout = getOrCreateLayout();
//			final Layout<? extends Serializable> layout = JsonLayout.createLayout(null,
//					true, // locationInfo
//					true, // properties
//					false,// propertiesAsList
//					true, // complete
//					false,// compact
//					true, // eventEol
//					null,null,StandardCharsets.UTF_8,
//					true  // includeStacktrace
//			);
			final Layout<? extends Serializable> layout = GsonLayout.newBuilder()
					.setComplete(true)
					.setProperties(true)
					.setLocationInfo(true)
					.setIncludeStacktrace(true)
					.setIncludeTimeMillis(true)
					.setCharset(StandardCharsets.UTF_8)
					.setObjectMessageAsJsonObject(true)
					.setEventEol(true)
//					.setCompact(true)
					.build();
			return new MqttAppender(getName(), layout, getFilter(), broker, clientId, topic, getPropertyArray());
		}

		public String getBroker() {
			return broker;
		}
		public String getClientId() {
			return clientId;
		}
		public String getTopic() {
			return topic;
		}

		public B withBroker(final String broker) {
			this.broker = broker;
			return asBuilder();
		}
		public B withClientId(final String clientId) {
			this.clientId = clientId;
			return asBuilder();
		}
		public B withTopic(final String topic) {
			this.topic = topic;
			return asBuilder();
		}

	}

	@PluginBuilderFactory
	public static <B extends Builder<B>> B newBuilder() {
		return new Builder<B>().asBuilder();
	}

	public MqttAppender(String name, Layout<? extends Serializable> layout, Filter filter,
			String broker, String clientId, String topic, Property[] properties) {
		super(name, filter, layout, true, properties);
		this.broker = broker;
		this.clientid = clientId;
		this.topic = topic;
		initialize();
	}

	@Override
	public void start() {
		super.start();
	}

	@Override
	public boolean stop(final long timeout, final TimeUnit timeUnit) {
		setStopping();
		super.stop(timeout, timeUnit, false);
		setStopped();
		return true;
	}

	private MqttClient mqtt;
	private final int RECONNECT_MIN = 1000;
	private final int RECONNECT_MAX = 32000;
	private int currentReconnect = 1000;
	private boolean connected = false;
	private final LinkedBlockingQueue<LogEvent> queue = new LinkedBlockingQueue<LogEvent>(10000);

	private String broker;
	private String clientid;
	private String username;
	private String password;
	private int connectionTimeout = 2000;
	private int keepAliveInterval = 60000;
	private String topic;
	private int qos = 0;
	private boolean retain = false;
	ErrorHandler errorHandler = new ErrorHandler();

	private static final Logger logger = LoggerFactory.getLogger(
			MqttAppender.class.getPackage().getName()+"");
	class ErrorHandler {
		public void error(String msg) {
			logger.error(msg);
		}
	}

	public String getBroker() {
		return broker;
	}

	public void setBroker(String broker) {
		this.broker = broker;
	}

	public String getClientid() {
		return clientid;
	}

	public void setClientid(String clientid) {
		this.clientid = clientid;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public int getConnectionTimeout() {
		return connectionTimeout;
	}

	public void setConnectionTimeout(int connectionTimeout) {
		this.connectionTimeout = connectionTimeout;
	}

	public int getKeepAliveInterval() {
		return keepAliveInterval;
	}

	public void setKeepAliveInterval(int keepAliveInterval) {
		this.keepAliveInterval = keepAliveInterval;
	}

	public String getTopic() {
		return topic;
	}

	public void setTopic(String topic) {
		this.topic = topic;
	}

	public int getQos() {
		return qos;
	}

	public void setQos(int qos) {
		this.qos = qos;
	}

	public boolean isRetain() {
		return retain;
	}

	public void setRetain(boolean retain) {
		this.retain = retain;
	}

	private void connectMqtt() {
		String mqttBroker = null;
		String mqttUser = null;
		String mqttPass = null;
		if (broker!=null) {
			URI uri = URI.create(broker);
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
		if (mqttBroker!=null)
			broker = mqttBroker;
		if (mqttUser!=null)
			username = mqttUser;
		if (mqttPass!=null)
			password = mqttPass;
		MqttConnectOptions opts = new MqttConnectOptions();
		opts.setConnectionTimeout(connectionTimeout);
		opts.setKeepAliveInterval(keepAliveInterval);
		if (username!=null)
			opts.setUserName(username);
		if (password!=null)
			opts.setPassword(password.toCharArray());
		try {
			mqtt = new MqttClient(broker,clientid,null);
			mqtt.connect(opts);
			mqtt.setCallback(this);
			currentReconnect = RECONNECT_MIN;
			synchronized (this) {
				connected = true;
			}
			emptyQueue();
		} catch (MqttSecurityException ex1) {
			errorHandler.error("MQTT Security error: " + ex1);
		} catch (MqttException ex2) {
			int code = ex2.getReasonCode();
			switch (code) {
				case 0: // connection successful, am I ever going here?
					// while testing I've noticed I am as I was receiving code 0 here, weird...
					currentReconnect = RECONNECT_MIN;
					synchronized (this) {
						connected = true;
					}
					emptyQueue();
					break;
				case 1:
					errorHandler.error("MQTT connection error: Connection Refused: unacceptable protocol version");
					break;
				case 2:
					errorHandler.error("MQTT connection error: Connection Refused: identifier ("+clientid+") rejected");
					break;
				case 3:
					errorHandler.error("MQTT connection error: Connection Refused: server unavailable");
					break;
				case 4:
					errorHandler.error("MQTT connection error: Connection Refused: bad user name or password");
					break;
				case 5:
					errorHandler.error("MQTT connection error: Connection Refused: not authorized");
					break;
				default:
					errorHandler.error("MQTT connection error: Unknown response -> " + code + ", reconnecting...");
					reconnectMqtt();
			}
		}
	}

	private synchronized void emptyQueue() {
		while (!queue.isEmpty()) {
			append(queue.poll());
		}
	}

	private void reconnectMqtt() {
		if ( currentReconnect < RECONNECT_MAX ) {
			currentReconnect += currentReconnect;
		}
		Thread t = new Thread() {
			@Override
			public void run() {
				try {
					Thread.sleep(currentReconnect);
					connectMqtt();
				} catch (InterruptedException ex) {}
			}
		};
		t.start();
	}

	@Override
	public void initialize() {
		setState(State.INITIALIZING);
		clientid = clientid.replace("{ip}",Network.hostip);
		clientid = clientid.replace("{realm}",Network.realm);
		clientid = clientid.replace("{host}",Network.host);
		clientid = clientid.replace("{node}",Network.name);
		clientid = clientid.replace("{proc}",Network.proc);
		topic = topic.replace("{ip}",Network.hostip);
		topic = topic.replace("{realm}",Network.realm);
		topic = topic.replace("{host}",Network.host);
		topic = topic.replace("{node}",Network.name);
		topic = topic.replace("{proc}",Network.proc);
		if (!"Logwriter".equals(Network.name))
			connectMqtt();
		setState(State.INITIALIZED);
	}

	@Override
	public void finalize() {
		this.close();
	}

	public void append(LogEvent event) {
		if ("Logwriter".equals(Network.name))
			return;
		if ( connected ) {
			MqttMessage msg = new MqttMessage();
			msg.setPayload( getLayout().toByteArray(event) );
			msg.setQos( qos );
			msg.setRetained( retain );
			try {
				if ( mqtt != null ) {
					mqtt.getTopic(this.topic).publish(msg);
				}
			} catch (MqttPersistenceException ex1) {
				errorHandler.error("MQTT Could not send a message: " + ex1);
				if ( !queue.offer(event) ) {
					errorHandler.error("MQTT offer queue is full. Messages will be lost.");
				}
			} catch (MqttException ex2) {
				errorHandler.error("MQTT Could not send a message: " + ex2);
				if ( !queue.offer(event) ) {
					errorHandler.error("MQTT offer queue is full. Messages will be lost.");
				}
			}
		} else {
			if ( !queue.offer(event) ) {
				errorHandler.error("MQTT offer queue is full. Messages will be lost.");
			}
		}
	}

	public synchronized void close() {
		synchronized (this) {
			connected = false;
		}
		try {
			mqtt.disconnect();
		} catch (MqttException ex) {
			errorHandler.error("Could not disconnect the MQTT client: " + ex);
		} finally {
			mqtt = null;
		}
	}



	@Override
	public void connectionLost(Throwable cause) {
		this.close();
		errorHandler.error("Connection to the MQTT broker lost, reconnecting: " + cause);
		reconnectMqtt();
	}

	public void messageArrived(String topic, MqttMessage message) throws Exception {
	}
	public void deliveryComplete(IMqttDeliveryToken token) {
	}
}
