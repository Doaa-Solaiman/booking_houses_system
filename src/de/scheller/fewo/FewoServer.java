package de.scheller.fewo;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.PasswordAuthentication;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.IoUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import de.scheller.fewo.util.DatabaseHelper;
import de.scheller.fewo.util.DbUtils;
import de.scheller.fewo.util.LocalAccountContext;
import de.scheller.fewo.util.ServerHelper;
import de.scheller.fewo.util.SmallMessagesWsLogic;
import de.scheller.fsm.FSM;
import de.scheller.fsm.FSM2;
import de.scheller.platform.common.FileUtils;
import de.scheller.platform.common.Logging;
import de.scheller.platform.common.MapUtils;
import de.scheller.platform.common.X;
import de.scheller.platform.network.Network;
import de.scheller.platform.network.PlatformNode;
import de.scheller.platform.network.web.UServer;
import de.scheller.platform.network.web.UServer.WsLogic;
import de.scheller.platform.persist.DatabaseApi;
import de.scheller.platform.persist.DatabaseImpl;
import de.scheller.platform.persist.IServerBeanManager;
import de.scheller.platform.persist.util.ReflectHelper;
import io.undertow.Handlers;
import io.undertow.io.IoCallback;
import io.undertow.io.Sender;
import io.undertow.predicate.Predicates;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.encoding.ContentEncodingRepository;
import io.undertow.server.handlers.encoding.EncodingHandler;
import io.undertow.server.handlers.encoding.GzipEncodingProvider;
import io.undertow.server.handlers.form.FormData;
import io.undertow.server.handlers.form.FormData.FileItem;
import io.undertow.server.handlers.form.FormData.FormValue;
import io.undertow.server.handlers.form.FormParserFactory;
import io.undertow.server.handlers.resource.ClassPathResourceManager;
import io.undertow.server.handlers.resource.FileResourceManager;
import io.undertow.server.handlers.resource.Resource;
import io.undertow.server.session.Session;
import io.undertow.util.Headers;
import io.undertow.util.Methods;
import io.undertow.util.StatusCodes;

/**
 * @author kandzia
 */
public class FewoServer
{
	private static Logger logger;
	private static Logger logger() {
		if (logger==null)
			logger = LoggerFactory.getLogger(FewoServer.class.getPackage().getName());
		return logger;
	}

	// managed independently from communication channel, e.g. websocket, XHR etc.
	static Map<String,WsConnection> sessions = new ConcurrentHashMap();
	static class WsConnection implements WsLogic {
		String origin;
		String sessionId;
		long lastActivity;
		Consumer<Object> send;
		Map<String,Object> routing;
		FewoSession ac;

		Gson gson = new Gson();
		Gson gsonPretty = new GsonBuilder().setPrettyPrinting().create();
		Map<String,Object> state = new LinkedHashMap();
		Map<String,Object> sessionContext = new LinkedHashMap();

		public void activate(Consumer send) {
			this.send = send;
			System.out.println("MwfUiServer.SessionLogic.activate() "+sessionContext);
			ac.activateUI(this);
		}

		public void deactivate() {
			System.out.println("MwfUiServer.SessionLogic.deactivate() "+sessionContext);
			ac.deactivateUI(this);
		}

		public void receive(String messageJson) {
			Map<String,Object> message = gson.fromJson(messageJson,Map.class);
//			System.out.println("ws message: "+message);
			String rq = (String)message.get("rq");
			String fn = (String)message.get("fn");
			List args = (List)message.get("args");
			Object[] argsArray = new Object[args.size()];
			Object[] argsWithRq = new Object[args.size()+1];
			args.toArray(argsArray);
			args.toArray(argsWithRq);
			argsWithRq[argsWithRq.length-1] = rq;
			Runnable process = () -> {
				try {
					ac.ui.set(this);
					try { // 2 styles of function impl are supported:
						response(rq,ReflectHelper.callMethod(ac,fn,argsArray)); // new style (easy, feels standard)
					} catch (NoSuchMethodException ex) {
						Object rs = ReflectHelper.callMethod(ac,fn,argsWithRq); // old style (needed for async stuff)
						if (rs!=null) response(rq,rs);
					}
				} catch (Exception ex) {
					logger.error(Logging.WS,"calling {} failed, args {}",fn,args,ex);
					exception(rq,ex);
				} finally {
					flushCommands();
					ac.ui.set(null);
				}
			};
			new Thread(process,fn).start();
		}
		public void response(String requestId, Object result) {
			command("call",MapUtils.asMap("fn","response","args",Arrays.asList(requestId,result)));
		}
		public void exception(String requestId, Exception ex) {
			command("call",MapUtils.asMap("fn","exception","args",Arrays.asList(requestId,ex)));
		}
		public void call(String function, Object... args) {
			command("call",MapUtils.asMap("fn",function,"args",Arrays.asList(args)));
		}

		private final List<String> keep = Arrays.asList("jsx","hast","html");
		private final Map<String,Object> commands = new HashMap();
		private synchronized void command(String cmd, Object data) {
			if (!keep.contains(cmd)) {
				List once = (List)commands.get("once");
				if (once==null) commands.put("once",once = new ArrayList());
				once.add(MapUtils.asMap(cmd,data));
			} else commands.put(cmd,data);
		}
		synchronized void flushCommands() {
			if (send!=null)
				send.accept(new LinkedHashMap(commands));
			commands.remove("once");
		}
	}

//	static Database pdb;
	public static DataSource pds;
	static DatabaseImpl odb;
	static Object context = new Object(); // currently I need only: FSM2, LocalBeanManager
	static Gson gson = new Gson();
	static Gson gsonPretty = new GsonBuilder().setPrettyPrinting().create();
	static File dataroot = new File("./data-fewo");
	static String privatePages;
	static String publicPages;

	public static void main(String[] args) throws Exception {
		Network.init(args,FewoServer.class.getSimpleName());
//		Network.start();
		logger(); // activate this
//		logger.info("Wait for configuration");
//		if (!Network.waitForConfiguration(20000)) {
//			logger.error("Wait for configuration -> timeout.");
//			System.exit(-1);
//		}

		Properties config = new Properties();
		config.load(new FileReader("app.properties"));

		dataroot = new File(config.getProperty("dataroot"));

		privatePages = config.getProperty("privatePages");
		publicPages = config.getProperty("publicPages");

		smtpHost = config.getProperty("smtpHost");
		if (config.getProperty("smtpPort")!=null)
			smtpPort = Integer.parseInt(config.getProperty("smtpPort"));
		if (config.getProperty("smtpAuth")!=null)
			smtpAuth = Boolean.parseBoolean(config.getProperty("smtpAuth"));
		if (config.getProperty("smtpTLS")!=null)
			smtpTLS = Boolean.parseBoolean(config.getProperty("smtpTLS"));
		smtpUser = config.getProperty("smtpUser");
		smtpPass = config.getProperty("smtpPass");
		senderEMail = config.getProperty("senderEMail");
		receiverEMail = config.getProperty("receiverEMail");

		PlatformNode.getOrCreateConfig(".config").content(new Gson().toJson(X.mapx(
				"database",X.mapx(
						"name",config.getProperty("name"),
						"host",config.getProperty("host"),
						"user",config.getProperty("user"),
						"pass",config.getProperty("pass"),
						"jdbc",config.getProperty("jdbc")
				)
		)));

		DbUtils.readonly = config.getProperty("readonly")==null ? false :
			Boolean.parseBoolean(config.getProperty("readonly"));

		boolean outputSourceMaps = config.getProperty("outputSourceMaps")==null ? false :
			Boolean.parseBoolean(config.getProperty("outputSourceMaps"));

		int httpPort = 80;
		if (config.getProperty("httpPort")!=null)
			httpPort = Integer.parseInt(config.getProperty("httpPort"));

//		Map sslConfig = PlatformNode.getRootConfig().access().get("ssl",Map.class);
//		if (sslConfig!=null) {
//			ByName c = ByName.get(sslConfig);
//			KeyStore k = UServer.loadKeyStore(c.asString("keystore"),c.asString("keystorePass"));
//			KeyStore t = UServer.loadKeyStore(c.asString("truststore"),c.asString("truststorePass"));
//			UServer.initSSLContext(k,t,c.asString("keyPass"));
//			logger.info("SSL context configured.");
//		} else logger.warn("SSL NOT configured.");

		PathHandler pathhandler;
		HttpHandler h;
		h = Handlers.resource(
//				new FileResourceManager(new File("./app-host/target/"),1024,true,true,new String[0]));
				new ClassPathResourceManager(FewoServer.class.getClassLoader(),"app-host/") {
					@Override
					public Resource getResource(String path) throws IOException {
						if (!outputSourceMaps && path.endsWith(".map"))
							return null;
						return super.getResource(path);
					}
				});
		h = pathhandler = Handlers.path(h);
		h = new FewoHandler(h);
		h = new EncodingHandler(new ContentEncodingRepository()
				.addEncodingHandler("gzip",
						new GzipEncodingProvider(),50,
						Predicates.parse("max-content-size(5)")))
				.setNext(h);


		pathhandler.addPrefixPath("/lib/data",Handlers.resource(
				new FileResourceManager(dataroot,1024,true,true,new String[0])));

		// that was minimal init() before I added init()
//		pdb = PlatformNode.getDatabase(); // thin JDBC DB access layer
//		pds = DatabaseHelper.ds(); // plain/platform DB access (JDBC connection pool)
//		odb = DatabaseHelper.db(pds); // ORM DB access
//
//		FSM2 fsm = new FSM2();
//		fsm.addInjectTargets("de.scheller.");
//		LocalBeanManager bm = new LocalBeanManager(odb);
//		FSM.noodle(context,DatabaseApi.class,odb);
//		FSM.noodle(context,IServerBeanManager.class,odb);
//		FSM.noodle(context,IBeanManager.class,bm);

		context = init();
		odb = (DatabaseImpl)FSM.touch(context,DatabaseApi.class);
		pds = odb.getDataSource();

		LocalAccountContext.factory = () -> new FewoSession();

		UServer.port = httpPort;
		UServer.forceHttps = false;
		UServer.start(h);

		pathhandler.addPrefixPath("ds",UServer.ws(http -> {
			if (http.getRequestParameters().isEmpty())
				return null;
			Session session = (Session)http.getSession();
			if (session==null)
				return null;
//			String sessionId = session.getId();
			String windowId = http.getRequestParameters().keySet().iterator().next();

			ServerHelper.getLocalAccountContext(session,windowId,context,() -> new FewoSession());

			WsConnection s = sessions.get(windowId);
			if (s==null) sessions.put(windowId,s = new WsConnection());
			s.sessionId = windowId;
			s.origin = http.getRequestHeader("origin");
			s.ac = (FewoSession)session.getAttribute("ac");
			return new SmallMessagesWsLogic(s);
		}));

		// 1. java itself
		//    - https://naveen-metta.medium.com/anatomy-of-java-programs-a-comprehensive-guide-38ef4d0a9e9b
		// 1.1. java main method
		// 1.2. java collections
		//    - list: ArrayList, LinkedList
		//    - set: HashSet, LinkedHashSet, TreeSet
		//    - map: HashMap, LinkedHashMap, TreeMap
		// 1.3. generics
		// 1.4. try-with-resources block
		// 2. java.sql package (Connection, Statement, PreparedStatement, ResultSet)
		//    - https://www.baeldung.com/java-jdbc
		//      - 4. Executing SQL Statements
		//      - 5. Parsing Query Results
		//      - 8. Closing the Resources
		//    - https://www.baeldung.com/sql/sql-statements-queries
		//    - https://www.baeldung.com/sql-joins
		// 3. SQL
		// 3.1. PRIMARY KEY vs FOREIGN KEY
		// 3.2. CRUD: Create, Read, Update, Delete
		//    - CRUD is about data records or rows
		//    - Create -> INSERT INTO
		//    - Read   -> SELECT
		//    - Update -> UPDATE
		//    - Delete -> DELETE FROM

//		System.out.println(new HashSet(Arrays.asList("d","b","a","c","1")));
//		System.out.println(new LinkedHashSet(Arrays.asList("d","b","a","c","1")));
//		System.out.println(new TreeSet(Arrays.asList("d","b","a","c","1")));

//		// wrong try-with-resources block
//		try (Statement stmt = pds.getConnection().createStatement()) {
//			// do stuff.
//			// oh oops, connection leaks (not closed)!
//		}
//
//		// solution 1: try-with-resources blocks for each resource (Connection,Statement)
//		try (Connection conn = pds.getConnection()) {
//			try (Statement stmt = conn.createStatement()) {
//			}
//		}
//
//		// solution 2: try-with-resources block with multiple resources
//		try (Connection conn = pds.getConnection();
//				Statement stmt = conn.createStatement()) {
//		}

		try (Connection conn = pds.getConnection()) {
			try (Statement stmt = conn.createStatement()) {
				// Create tables
				String sql = "CREATE TABLE IF NOT EXISTS BookingDetails (\n"
						+ "    id VARCHAR(255) NOT NULL,\n"
						+ "    name VARCHAR(255) NOT NULL,\n"
						+ "    checkInDate DATE NOT NULL,\n"
						+ "    checkOutDate DATE NOT NULL,\n"
						+ "    numberOfGuests INT NOT NULL,\n"
						+ "    specialRequests TEXT,\n"
						+ "    houseTitle VARCHAR(255) NOT NULL,\n"
						+ "    estimatedFund DECIMAL,\n"
						+ "    dateOfReservation DATETIME NOT NULL,\n"
						+ "    PRIMARY KEY (id)\n"
						+ ");";
				stmt.execute(sql);

				sql = "CREATE TABLE IF NOT EXISTS Messages (\n"
						+ "    id VARCHAR(255) NOT NULL,\n"
						+ "    name VARCHAR(255) NOT NULL,\n"
						+ "    email VARCHAR(255) NOT NULL,\n"
						+ "    message TEXT NOT NULL,\n"
						+ "    dateSent DATETIME NOT NULL,\n"
						+ "    PRIMARY KEY (id)\n"
						+ ");";
				stmt.execute(sql);

				sql = "CREATE TABLE IF NOT EXISTS SitesOld (\n"
						+ "    id VARCHAR(255) NOT NULL,\n"
						+ "    siteType VARCHAR(255) NOT NULL,\n"
						+ "    siteTitle VARCHAR(255) NOT NULL,\n"
						+ "    street VARCHAR(255) NOT NULL,\n"
						+ "    city VARCHAR(255) NOT NULL,\n"
						+ "    zipCode VARCHAR(20) NOT NULL,\n"
						+ "    width DECIMAL(10, 2) NOT NULL,\n"
						+ "    length DECIMAL(10, 2),\n"
						+ "    totalSquareFootage DECIMAL(10, 2),\n"
						+ "    numberOfRooms INT NOT NULL,\n"
						+ "    roomTypes TEXT,\n"
						+ "    numberOfBeds INT NOT NULL,\n"
						+ "    bedTypes TEXT,\n"
						+ "    capacity INT NOT NULL,\n"
						+ "    parkingAvailable BOOLEAN,\n"
						+ "    swimmingPoolAvailable BOOLEAN,\n"
						+ "    petsAllowed BOOLEAN,\n"
						+ "    petDetails TEXT,\n"
						+ "    smokingAllowed BOOLEAN,"
						+ "	   smokingDetails TEXT,\n"
						+ "    description TEXT,\n"
						+ "    fromDate DATE NOT NULL,\n"
						+ "    toDate DATE NOT NULL,\n"
						+ "    pricing DECIMAL(10, 2),\n"
						+ "    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,\n"
						+ "	   PRIMARY KEY (id)\n"
						+ ");";
				stmt.execute(sql);

//				sql = "DROP TABLE IF EXISTS Site_Photos";
//				stmt.execute(sql);

				sql = "CREATE TABLE IF NOT EXISTS Site_Photos (\n"
						+ "    id VARCHAR(255) NOT NULL,\n"
						+ "    name VARCHAR(255),\n"
						+ "    origName VARCHAR(255),\n"
						+ "    image_data LONGBLOB NOT NULL,\n"
						+ "    extension VARCHAR(4) NOT NULL,\n"
						+ "    mimeType VARCHAR(255) NOT NULL,\n"
						+ "    site_id VARCHAR(255) NOT NULL,\n"
						+ "    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,\n"
						+ "    PRIMARY KEY (id)\n,"
						+ "    FOREIGN KEY (site_id) REFERENCES Sites(id)\n"
						+ ");";
				stmt.execute(sql);


				// for booking requests status
				sql = "CREATE TABLE IF NOT EXISTS Status (\n"
						+ "   id VARCHAR(50) NOT NULL PRIMARY KEY,\n"
						+ "   name VARCHAR(50)"
						+ ");";
				stmt.execute(sql);

				sql = "CREATE TABLE IF NOT EXISTS RequestStatus (\n"
						+ "	  id VARCHAR(255) NOT NULL PRIMARY KEY,\n"
						+ "   BookingDataID VARCHAR(50),\n"
						+ "	  StatusID VARCHAR(50),\n"
						+ "   time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,\n"
						+ "   FOREIGN KEY (BookingDataID) REFERENCES BookingData(id),\n"
						+ "   FOREIGN KEY (StatusID) REFERENCES Status(id)\n"
						+ ");";
				stmt.execute(sql);


				// Insert data
//		        sql = "INSERT INTO ContactUsData " +
//		                "(id, dateSent, email, message, name) " +
//		                "VALUES (?, ?, ?, ?, ?)";
//		        PreparedStatement ps = conn.prepareStatement(sql);
//		        ps.setString(1, "msg1");
//		        ps.setDate(2, new java.sql.Date(System.currentTimeMillis()));
//		        ps.setString(3, "doaa@scheller.de");
//		        ps.setString(4, "Hey house owners, I would like to...");
//		        ps.setString(5, "Doaa");
//		        ps.execute();

				// Select data
//		        List<Map> rows = new ArrayList<>();
//		        sql = "SELECT b.houseTitle, COUNT(b.id) as count " +
//		                "FROM BookingDetails b GROUP BY b.houseTitle";
//		        PreparedStatement ps = conn.prepareStatement(sql);
//		        try (ResultSet rs = ps.executeQuery()) {
//		            rs.beforeFirst();
//		            while (rs.next()) {
//		                Map<String, Object> row = new LinkedHashMap<>();
//		                row.put("houseTitle", rs.getString("houseTitle"));
//		                row.put("count", rs.getInt("count"));
//		                rows.add(row);
//		            }
//		        }
//		        System.out.println(rows);
			} catch (Exception e) {
				e.printStackTrace();
			}
			conn.close();
			logger.info("FewoServer started");
		}
	}

	public static Object init() throws Exception {
		// pray to the flying spaghetti monster
		FSM2 fsm = new FSM2();
		fsm.addInjectTargets("de.scheller.");

		DatabaseImpl db = DatabaseHelper.db();
		DatabaseHelper.receiveUpdateEvents(db);

		Object context = new Object();
		FSM.noodle(context,DatabaseApi.class,db);
		FSM.noodle(context,IServerBeanManager.class,db);
		FSM.noodle(db,context);

		return context;
	}

	static class FewoHandler implements HttpHandler {
		HttpHandler next;
		static FormParserFactory FPF = FormParserFactory.builder().build();

		public FewoHandler(HttpHandler next) {
			this.next = next;
		}

		public void handleRequest(HttpServerExchange http) throws Exception {
			if (http.isInIoThread()) {
				http.dispatch(this);
				return;
			}
			// ensure http session (bound to cookie) & app session (determined by platform)
			Session hs = ServerHelper.mayCreateHttpSession(http);
			String ui = X.x(Arrays.asList(http.getRequestPath().split("/"))).filter(s -> s.matches("UI0\\d+")).first();
			boolean uiValid = LocalAccountContext.isValid(ui,hs.getId());
			FewoSession as = uiValid ?
					(FewoSession)LocalAccountContext.get(ui) :
					(FewoSession)ServerHelper.getLocalAccountContext(hs,context);
			// app session state/data could be shared over multiple processes that contain
			// webservers having http sessions by themselves (yes, I tried to share a single cookie
			// over multiple servers to get same session ID everywhere but didn't work
			// with multiple vaadin servers)

			String path = http.getRequestPath();

			if (path.startsWith("/config/")) {
				path = path.substring("/config/".length());
				String pathFinal = path.replace('/','-');
				File configDir = new File(dataroot,"config");
				File configFile = new File(configDir,pathFinal);
				if (Methods.POST.equals(http.getRequestMethod())) {
					http.getRequestReceiver().receiveFullBytes((e,m) -> {
						try {
							FileUtils.writeBytes(m,configFile);
						} catch (Exception ex) {
							ex.printStackTrace();
							http.setStatusCode(StatusCodes.INTERNAL_SERVER_ERROR);
						}
					});
				} else {
					if (!configFile.exists() || !configFile.isFile())
						http.setStatusCode(StatusCodes.NOT_FOUND);
					else try {
						http.getResponseSender().send(ByteBuffer.wrap(FileUtils.readBytes(configFile)));
					} catch (Exception ex) {
						ex.printStackTrace();
						http.setStatusCode(StatusCodes.INTERNAL_SERVER_ERROR);
					}
				}
				return;
			}

			// Here should be the place, where you want to sent your data.
//			// Create site
//			if (path.startsWith("/createSite") && http.getRequestMethod().equals(Methods.POST)) {
////				Map data = http.getQueryParameters();
//				http.startBlocking();
//				FormData formData = FPF.createParser(http).parseBlocking();
//				try {
//					formData.put("id",UUID.randomUUID().toString(),null);
//					as.createSite(formData);
//					as.savePhotos(formData);
//					http.setStatusCode(StatusCodes.ACCEPTED);
//				} catch (Exception ex) {
//					ex.printStackTrace();
//					http.setStatusCode(StatusCodes.INTERNAL_SERVER_ERROR);
//				}
//				return;
//			}
//			// Update site
//			if (path.startsWith("/updateSite") && http.getRequestMethod().equals(Methods.POST)) {
////				Map data = http.getQueryParameters();
//				http.startBlocking();
//				FormData formData = FPF.createParser(http).parseBlocking();
//				try {
//					as.updateSite(formData);
//					as.savePhotos(formData);
//					http.setStatusCode(StatusCodes.ACCEPTED);
//				} catch (Exception ex) {
//					ex.printStackTrace();
//					http.setStatusCode(StatusCodes.INTERNAL_SERVER_ERROR);
//				}
//				return;
//			}
			// Upload photos
			if (path.startsWith("/uploadPhotos") && http.getRequestMethod().equals(Methods.POST)) {
//				Map data = http.getQueryParameters();
				MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
				http.startBlocking();
				FormData formData = FPF.createParser(http).parseBlocking();
				try {
					List<String> names = new ArrayList();
					Deque<FormValue> values = formData.get("photos");
					for (FormValue value : values) {
						if (!value.isFileItem())
							continue;
						FileItem fi = value.getFileItem();
						Path p = fi.getFile();
						File f = p.toFile();
						String name = value.getFileName();
						int cut = name.lastIndexOf('.');
						String ext = cut<0 ? "" : name.substring(cut);
						byte[] data = Files.readAllBytes(p);
						byte[] fingerprint = sha256.digest(data);
						name = new BigInteger(1,fingerprint).toString(16);
						name += ext;
						f.renameTo(new File(dataroot,name));
						names.add(name);
					}
					http.getResponseSender().send(gson.toJson(names));
					http.setStatusCode(StatusCodes.OK);
				} catch (Exception ex) {
					ex.printStackTrace();
					http.setStatusCode(StatusCodes.INTERNAL_SERVER_ERROR);
				}
				return;
			}
			// Load photos
			if (path.startsWith("/photos/") && http.getRequestMethod().equals(Methods.GET)) {
				// Map data = http.getQueryParameters();
				// old: load whole DB table with all photo blobs. dead end road.
				//sendJson(http,as.loadPhotos());
				// new: load photo from filesystem as requested by the browser
				String name = path.substring("/photos/".length());
				File f = new File(dataroot,name);
				logger.info("access "+f);
				if (!f.exists()) {
					http.setStatusCode(StatusCodes.NOT_FOUND);
					return;
				}
				FileInputStream fis = new FileInputStream(f);
				FileChannel fc = fis.getChannel();
				http.getResponseSender().transferFrom(fc,new IoCallback() {
					public void onException(HttpServerExchange exchange, Sender sender, IOException exception) {
						IoCallback.END_EXCHANGE.onException(exchange,sender,exception);
					}
					public void onComplete(HttpServerExchange exchange, Sender sender) {
						IoCallback.END_EXCHANGE.onComplete(exchange,sender);
						IoUtils.safeClose(fc);
						IoUtils.safeClose(fis);
					}
				});
				return;
			}
//			// Load sites
//			if (path.startsWith("/sites") && http.getRequestMethod().equals(Methods.GET)) {
////				Map data = http.getQueryParameters();
//				sendJson(http,as.loadSites());
//				return;
//			}
//			// Delete site/s
//			if (path.startsWith("/deleteSite") && http.getRequestMethod().equals(Methods.POST)) {
//				http.getRequestReceiver().receiveFullString((e,m) -> {
//					System.out.println("m" + m);
//					Map body = gson.fromJson(m,Map.class);
//					List ids = (List)body.get("ids");
//					try {
//						as.deleteSite(ids);
//						http.setStatusCode(StatusCodes.ACCEPTED);
//					} catch (Exception ex) {
//						ex.printStackTrace();
//						http.setStatusCode(StatusCodes.INTERNAL_SERVER_ERROR);
//					}
//				});
////				http.startBlocking();
////				FormData formData = FPF.createParser(http).parseBlocking();
////				try {
////					as.deleteSite(formData);
////					http.setStatusCode(StatusCodes.ACCEPTED);
////				} catch (Exception ex) {
////					ex.printStackTrace();
////					http.setStatusCode(StatusCodes.INTERNAL_SERVER_ERROR);
////				}
//				return;
//			}

			next.handleRequest(http);
		}

//		Gson gson = new Gson();
		ObjectMapper jom = new ObjectMapper();

		void sendJson(HttpServerExchange http, Object o) throws Exception {
//			String json = gson.toJson(o);
			String json = jom.writeValueAsString(o);
//			json = DebugHelper.prettyPrintJSON(json);
			http.getResponseHeaders().put(Headers.CONTENT_TYPE,"application/json; charset=utf-8");
			http.getResponseSender().send(json,StandardCharsets.UTF_8);
		}
	}

	static String smtpHost = "mx.chalets-in-nature.de";
	static int smtpPort = 587;
	static boolean smtpAuth = true;
	static boolean smtpTLS = true;
	static String smtpUser = "request";
	static String smtpPass = "Chalets32948";
	static String senderEMail = "request@chalets-in-nature.de";
	static String receiverEMail = null; // used to ensure receiver while development

	static void sendMail(String from, String to, String subject, String text, String html) {
		if (senderEMail!=null)
			from = senderEMail;
		if (receiverEMail!=null)
			to = receiverEMail;
		if (smtpHost==null) {
			System.out.println("ERROR: Mail server not configured.");
			System.out.println("MAILTO: "+to);
			System.out.println("SUBJECT: "+subject);
			System.out.println("TEXT: "+text);
			return;
		}

		Properties props = new Properties();
		props.put("mail.smtp.host",smtpHost);
		props.put("mail.smtp.port",String.valueOf(smtpPort));
		props.put("mail.smtp.auth",String.valueOf(smtpAuth));
		props.put("mail.smtp.starttls.enable",String.valueOf(smtpTLS));
		props.put("mail.smtps.ssl.protocols","TLSv1.2");

		javax.mail.Session session = javax.mail.Session.getDefaultInstance(props,new Authenticator() {
			@Override
			protected PasswordAuthentication getPasswordAuthentication() {
				return new PasswordAuthentication(smtpUser,smtpPass);
			}
		});
		try {
			MimeBodyPart textPart = new MimeBodyPart();
			textPart.setText(text, "utf-8");

			MimeBodyPart htmlPart = new MimeBodyPart();
			htmlPart.setContent(html, "text/html; charset=utf-8");

			// multipart alternative example:
			// https://stackoverflow.com/questions/14744197/best-practices-sending-javamail-mime-multipart-emails-and-gmail
			Multipart multiPart = new MimeMultipart("alternative");
			multiPart.addBodyPart(textPart);
			multiPart.addBodyPart(htmlPart);

			Message msg = new MimeMessage(session);
			msg.setFrom(new InternetAddress(from!=null ? from : senderEMail));
			msg.setRecipient(Message.RecipientType.TO,new InternetAddress(to));
			msg.setSubject(subject);
//			msg.setText(text);
			msg.setContent(multiPart);

			Transport.send(msg);
		} catch (MessagingException ex) {
			ex.printStackTrace();
			System.out.println("ERROR: "+ex.getMessage());
			System.out.println("MAILTO: "+to);
			System.out.println("SUBJECT: "+subject);
			System.out.println("TEXT: "+text);
		}
	}
}
