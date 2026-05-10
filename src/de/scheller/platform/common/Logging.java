package de.scheller.platform.common;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.math.BigInteger;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.LogManager;

import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.properties.PropertiesConfiguration;
import org.apache.logging.log4j.core.config.properties.PropertiesConfigurationFactory;
import org.apache.logging.log4j.core.impl.Log4jContextFactory;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.core.selector.BasicContextSelector;
import org.apache.logging.log4j.core.util.SystemNanoClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import com.google.gson.Gson;

/**
 * @author kandzia
 */
public class Logging
{
	public static final Map<String,Object> props = new LinkedHashMap();
	private static PropertiesConfiguration pc;
	private static Logger logger;
	private static Logger logger() {
		if (logger!=null) return logger;
		return logger = LoggerFactory.getLogger("");
	}

	public static final PrintStream sysout = System.out;
	public static final PrintStream syserr = System.err;

	public static final Marker STATS = MarkerFactory.getMarker("STATS");

	public static final Marker START = MarkerFactory.getMarker("START");
	public static final Marker STOP = MarkerFactory.getMarker("STOP");
	public static final Marker TIME = MarkerFactory.getMarker("TIME");

	public static final Marker HTTP = MarkerFactory.getMarker("HTTP");
	public static final Marker RPC = MarkerFactory.getMarker("RPC");
	public static final Marker WS = MarkerFactory.getMarker("WS");

	public static final Marker REF(String content, Object... markerAndMeta) {
		String ph = toSHA1(content);
		Long t = refsReported.get(ph);
		if (t==null || System.currentTimeMillis()-t>1000) {
			refsReported.put(ph,System.currentTimeMillis());
			Marker ref = MarkerFactory.getMarker("REF"); // we're reusing(!) this marker here
			if (ref.hasReferences()) {
				List<Marker> toRemove = new ArrayList();
				for (Iterator<Marker> it = ref.iterator(); it.hasNext();)
					toRemove.add(it.next());
				for (Marker p : toRemove) ref.remove(p);
			}
			List data = new ArrayList(markerAndMeta.length);
			for (Object o : markerAndMeta)
				if (o instanceof Marker) ref.add((Marker)o); else data.add(o);
			if (data.size()>0)
				logger().debug(ref,content,data.toArray());
			else logger().debug(ref,content); // writes the content we want to refer to
		}
		Marker pm = MarkerFactory.getMarker("REF/"+ph);
		for (Object o : markerAndMeta)
			if (o instanceof Marker) pm.add((Marker)o);
		return pm;
	};
	private static String toSHA1(String s) {
		try {
			MessageDigest md = MessageDigest.getInstance("sha-1");
			byte[] bytes = md.digest(s.getBytes("utf-8"));
			return new BigInteger(1,bytes).toString(36);
		} catch (Exception ex) {
			ex.printStackTrace();
			return null;
		}
	}
	public static final Map<String,Long> refsReported = new ConcurrentHashMap();

	public static void initLoggingSystem() {
		System.setProperty("java.util.logging.manager",LogManagerX.class.getName());
		try {
			Properties loggingProps = new Properties();
			loggingProps.putAll(props);
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			loggingProps.store(bos,"LOG4J2 Logging Properties");
			final ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());

//			System.setProperty("log4j2.debug","true");
//			System.setProperty("log4j.LoggerContext.stacktrace.on.start","true");
			System.setProperty("AsyncLogger.ThreadNameStrategy","UNCACHED");
			System.setProperty("log4j2.loggerContextFactory",Log4jContextFactory.class.getName());
			System.setProperty("Log4jContextSelector",BasicContextSelector.class.getName());
			System.setProperty("log4j2.messageFactory","de.scheller.platform.network.log.ParameterizedMessageFactory");
			Configurator.setRootLevel(org.apache.logging.log4j.Level.INFO);

			ConfigurationSource cs = new ConfigurationSource(new InputStream() {
				@Override public int available() { return 0; }
				@Override public int read() { return -1; }
			}) {
				@Override public String getLocation() { return null; }
				@Override public URL getURL() { return null; }
				@Override public URI getURI() { return null; }
				@Override public File getFile() { return null; }
				@Override public long getLastModified() { return System.currentTimeMillis(); }
				@Override public ConfigurationSource resetInputStream() throws IOException { return this; }
				@Override public InputStream getInputStream() {
					try {
						Properties p = new Properties();
						p.putAll(props);
						return ContentUSH.inputstream(p);
					} catch (IOException ex) {
						ex.printStackTrace();
						return null;
					}
				}
			};
			LoggerContext lc = LoggerContext.getContext();
			PropertiesConfigurationFactory pcf = new PropertiesConfigurationFactory();
			PropertiesConfiguration pc = pcf.getConfiguration(lc,cs);
			lc.setConfiguration(pc);
			Log4jLogEvent.setNanoClock(new SystemNanoClock());

			pc.reconfigure();
			pc.reconfigure();
			pc.reconfigure();

			boolean consoleRedirect = "true".equalsIgnoreCase(
					((String)props.getOrDefault("console.redirect","true")).trim());
			boolean consoleOutput = "true".equalsIgnoreCase(
					((String)props.getOrDefault("console.output","false")).trim());

			if (consoleRedirect) {
				org.slf4j.Logger syslog = LoggerFactory.getLogger("Console");
				if (consoleOutput) {
					System.setOut(Print.to(msg -> syslog.info(msg)).and(System.out));
					System.setErr(Print.to(msg -> syslog.error(msg)).and(System.err));
				} else {
					System.setOut(Print.to(msg -> syslog.info(msg)));
					System.setErr(Print.to(msg -> syslog.error(msg)));
				}
			}

			logger();
		} catch (Exception ex) {
			logger().error(ex.getLocalizedMessage(),ex);
		}

		// list log4j2 plugins
//		LoggerContext ctx = LoggerContext.getContext();
//		org.apache.logging.log4j.core.config.plugins.util.PluginManager pm =
//				((AbstractConfiguration)ctx.getConfiguration()).getPluginManager();
//		for (Map.Entry<?,?> e : ((Map<?,?>)new TreeMap(pm.getPlugins())).entrySet()) {
//			System.out.format("%-30s --> %s\n",e.getKey(),e.getValue());
//		}

		reconfigureJavaUtilLogging();
		// https://bugs.openjdk.java.net/browse/JDK-8043306
//		// see LogManagerX below, TODO implement logging configuration related stuff
//		LogManager.getLogManager().addPropertyChangeListener(new PropertyChangeListener() {
//			@Override
//			public void propertyChange(PropertyChangeEvent evt) {
//				reconfigureLogging();
//			}
//		});

		SLF4JBridgeHandler.removeHandlersForRootLogger(); // remove existing handlers
		SLF4JBridgeHandler.install(); // add SLF4JBridgeHandler to j.u.l's root logger

		if (logger.isInfoEnabled()) {
			logger.info("Configuring Logging APIs");
			Object javaVersion = System.getProperty("java.version");
			Object javaVendor = System.getProperty("java.vendor");
			logger.info("Java Version: "+javaVersion+" by "+javaVendor);
			logger.info("Start time: "+new Date());
		}

		org.slf4j.LoggerFactory.getLogger("Hello").debug("Hello from SLF4J API");
		org.apache.log4j.LogManager.getLogger("Hello").debug("Hello from LOG4J v1 API");
		org.apache.logging.log4j.LogManager.getLogger("Hello").debug("Hello from LOG4J v2 API");
		org.apache.commons.logging.LogFactory.getLog("Hello").debug("Hello from JCL API (commons logging)");
		java.util.logging.Logger.getLogger("Hello").fine("Hello from JUL API (java.util logging)");
		System.out.println("Hello from System.out (java.lang \"logging\")");
		System.err.println("Hello from System.err (java.lang \"logging\")");
	}

	public void reconfigure(Map<String,Object> props) {
		Logging.props.putAll(props);
		pc.reconfigure();
		reconfigureJavaUtilLogging();
	}

	private static boolean isConfigLogging;
	private static void reconfigureJavaUtilLogging() {
		if (isConfigLogging) return;
		try {
			isConfigLogging = true;
			try {
				Properties p = new Properties();
				p.putAll(X.x(props).entries()
						.filter(e -> !e.getKey().startsWith("appender.") &&
								!e.getKey().startsWith("logger.")).map());
				LogManager.getLogManager().readConfiguration(ContentUSH.inputstream(p));
			} catch (Exception ex) {
				logger().error(ex.getLocalizedMessage(),ex);
			}
		} finally {
			isConfigLogging = false;
		}
	}

	/**
	 * Force/Apply our configuration everytime anyone tries to reconfigure JUL.
	 * Note: This will not work if anybody uses JUL before {@link Logging#initLoggingSystem()}.
	 * - Old method: listen with LogManager.addPropertyChangeListener
	 * - New method: install LogManagerX
	 * From LogManager.addPropertyChangeListener (deprecated in Java8, removed in Java9):
	 * "@deprecated The dependency on {@code PropertyChangeListener} creates a
	 *             significant impediment to future modularization of the Java
	 *             platform. This method will be removed in a future release.
	 *             The global {@code LogManager} can detect changes to the
	 *             logging configuration by overridding the {@link
	 *             #readConfiguration readConfiguration} method."
	 */
	public static class LogManagerX extends LogManager {
		@Override
		public void readConfiguration() throws IOException, SecurityException {
			super.readConfiguration();
			reconfigureJavaUtilLogging();
		}
		@Override
		public void readConfiguration(InputStream ins) throws IOException, SecurityException {
			super.readConfiguration(ins);
			reconfigureJavaUtilLogging();
		}
	}

	public static class ContentUSH extends URLStreamHandler {
		private final Function<URL,Object> content;
		public ContentUSH(Function<URL,Object> content) { this.content = content; }
		@Override
		protected URLConnection openConnection(URL u) throws IOException {
			return new URLConnection(u) {
				@Override
				public void connect() throws IOException {}
				@Override
				public Object getContent() throws IOException { return content.apply(url); }
				@Override
				public InputStream getInputStream() throws IOException {
					return inputstream(content.apply(url));
				}
			};
		}
		public static InputStream inputstream(Object content) throws IOException {
			if (content instanceof Properties) {
				ByteArrayOutputStream bos = new ByteArrayOutputStream();
				((Properties)content).store(bos,null);
				content = bos.toByteArray();
			}
			if (content instanceof Map || content instanceof Collection)
				content = new Gson().toJson(content);
			if (content instanceof String)
				content = ((String)content).getBytes(StandardCharsets.UTF_8);
			if (content instanceof byte[])
				return new ByteArrayInputStream((byte[])content);
			return null;
		}
	}

	@SuppressWarnings("resource") // not meant to be closed
	public static class Print extends PrintStream {
		private Consumer<String> logger;
		private PrintStream out;

		private Print() {
			super(new PrintBaos());
			((PrintBaos)super.out).print = this;
		}

		public static Print to(Consumer<String> logger) { return new Print().and(logger); }
		public static Print to(PrintStream out) { return new Print().and(out); }
		public static Print to(OutputStream out) { return new Print().and(out); }
		public Print and(Consumer<String> logger) { Print.this.logger = logger; return this; }
		public Print and(PrintStream out) { Print.this.out = out; return this; }
		public Print and(OutputStream out) { Print.this.out = new PrintStream(out); return this; }

		private static class PrintBaos extends ByteArrayOutputStream {
			private Print print;
			private final StringBuilder sb = new StringBuilder();
			@Override
			public void write(int b) {
				super.write(b);
				if (b=='\n') {
					String line = new String(toByteArray());
					reset();
					if (print.logger!=null)
						print.logger.accept(line.trim());
					if (print.out!=null)
						print.out.print(line);
				}
			}
			@Override
			public void write(byte b[], int off, int len) {
				if (len<0) throw new ArrayIndexOutOfBoundsException(len);
				for (int i=0; i<len; i++) write(b[off+i]);
			}
		}
	}

	public static class ProgressInputStream extends InputStream {
		private final InputStream in;
		private final Consumer<Double> progress;
		private final long total;
		private final int every;
		private long pos;
		private long mark;

		public ProgressInputStream(InputStream in, long total, Consumer<Double> progress) {
			this(in,total,1000,progress);
		}
		public ProgressInputStream(InputStream in, long total, int every, Consumer<Double> progress) {
			this.in = in;
			this.total = total;
			this.every = every;
			this.progress = progress;
			progress.accept(0d);
		}

		@Override public void close() throws IOException { in.close(); }
		@Override public int available() throws IOException { return in.available(); }
		@Override public boolean markSupported() { return in.markSupported(); }
		@Override public synchronized void mark(int readlimit) { in.mark(readlimit); mark = pos; }
		@Override public synchronized void reset() throws IOException { in.reset(); pos = mark; }
		@Override public long skip(long n) throws IOException {
			return progress(in.skip(n)); }
		@Override public int read(byte[] b) throws IOException {
			return progress(in.read(b)); }
		@Override public int read(byte[] b, int off, int len) throws IOException {
			return progress(in.read(b,off,len)); }
		@Override public int read() throws IOException {
			return progress(in.read()); }

		private int progress(int bytes) { if (bytes>=0) update(bytes); return bytes; }
		private long progress(long bytes) { if (bytes>=0) update(bytes); return bytes; }
		private void update(long read) {
			this.pos += read;
			if (pos>=total) progress.accept(1d);
			else if (pos%every==0) progress.accept(pos*1d/total);
		}
	}

	public static class ProgressOutputStream extends OutputStream {
		private final OutputStream out;
		private final Consumer<Double> progress;
		private final long total;
		private final int every;
		private long pos;

		public ProgressOutputStream(OutputStream out, long total, Consumer<Double> progress) {
			this(out,total,1000,progress);
		}
		public ProgressOutputStream(OutputStream out, long total, int every, Consumer<Double> progress) {
			this.out = out;
			this.total = total;
			this.every = every;
			this.progress = progress;
			progress.accept(0d);
		}

		@Override public void close() throws IOException { out.close(); }
		@Override public void flush() throws IOException { out.flush(); }
		@Override public void write(byte[] b) throws IOException {
			out.write(b); progress(b.length); }
		@Override public void write(byte[] b, int off, int len) throws IOException {
			out.write(b,off,len); progress(len); }
		@Override public void write(int b) throws IOException {
			out.write(b); progress(1); }

		private void progress(int read) {
			this.pos += read;
			if (pos>=total) progress.accept(1d);
			else if (pos%every==0) progress.accept(pos*1d/total);
		}
	}
}
