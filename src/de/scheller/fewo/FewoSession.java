package de.scheller.fewo;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.google.common.base.Objects;
import com.google.gson.Gson;

import de.scheller.fewo.FewoServer.WsConnection;
import de.scheller.fewo.util.DbUtils;
import de.scheller.fewo.util.LocalAccountContext;
import de.scheller.fewo.util.StringUtils;
import de.scheller.fsm.FSM;
import de.scheller.platform.apis.SessionContext;
import de.scheller.platform.common.FileUtils;
import de.scheller.platform.common.MapUtils;
import de.scheller.platform.org.IOrgManager;
import de.scheller.platform.org.model.IOrganization;
import de.scheller.platform.persist.DatabaseApi;
import de.scheller.platform.persist.IServerBeanManager;
import de.scheller.util.BCrypt;
import io.undertow.server.handlers.form.FormData;
import io.undertow.server.handlers.form.FormData.FileItem;
import io.undertow.server.handlers.form.FormData.FormValue;
import io.undertow.util.Headers;

public class FewoSession extends LocalAccountContext implements SessionContext.ActionListener
{
	@Override
	protected void serviceAvailable() {
		System.out.println("FewoSession.serviceAvailable()");
	}

	@Override
	protected void init() {
		System.out.println("FewoSession.init()");
	}

	@Override
	protected void contextChanged() {
		System.out.println("FewoSession.contextChanged()");
		for (WsConnection ui : uis) {
			ui.call("reload");
			ui.flushCommands();
		}
	}

	public Map<String,Object> action(String sessionId, String command, Object... data) {
		System.out.println(command + " " + Arrays.asList(data));
		return null;
	}

	ContextByOrg contextByOrg(IOrganization org) {
		return contextByOrg.computeIfAbsent(org,o -> {
			long init = System.currentTimeMillis();
			ContextByOrg c = new ContextByOrg();
			c.context = context;
			c.org = o;
			c.db = FSM.touch(c.context,DatabaseApi.class);
			c.bm = FSM.touch(c.context,IServerBeanManager.class);
			c.om = FSM.touch(c.context,IOrgManager.class);

			System.out.println("init output context for " + o + " took "
					+ (System.currentTimeMillis() - init) + "ms");
			return c;
		});
	}

	Map<IOrganization,ContextByOrg> contextByOrg = new ConcurrentHashMap();

	static class ContextByOrg
	{
		Object context;
		IOrganization org;

		// system apis/manager
		DatabaseApi db;
		IServerBeanManager bm;
		IOrgManager om;
	}

	final ThreadLocal<WsConnection> ui = new ThreadLocal();
	final Set<WsConnection> uis = new LinkedHashSet();

	public void activateUI(WsConnection ui) {
		uis.add(ui);
		System.out.println("activateUI " + ui.sessionId + ", now " + uis.size() + " active UIs");
		this.origin = ui.origin;
	}

	public void deactivateUI(WsConnection ui) {
		uis.remove(ui);
		System.out.println("deactivateUI " + ui.sessionId + ", still " + uis.size() + " active UIs");
	}

	public void asyncTest(String a, String b, String requestId) {
		WsConnection sl = ui.get();
		new Thread() {
			@Override
			public void run() {
				try {
					Thread.sleep(13250);
				} catch (InterruptedException ex) {
					// TODO Auto-generated catch block
					ex.printStackTrace();
				}
				sl.response(requestId,("" + a + "-" + b).toUpperCase());
				sl.flushCommands();
			}
		}.start();
	}

	// 2 styles of function impl: old style (but still needed for async stuff), new style (feels standard)
	public Map loadConfigs(String requestId) { // old style w/ requestId as last arg
		ui.get().response(requestId,Collections.EMPTY_MAP); // both works at same time: sending response(s)
		if (ui != null) throw new RuntimeException("too bad"); // "sending" exception
		return Collections.EMPTY_MAP; // returned value is sent as (another) response IF NOT NULL!
	}

	public Map loadConfigs() throws Exception {
		Map<String,Map> configs = new LinkedHashMap();
		return configs;
	}

	public List<Map> loadBookingStats() throws Exception {
		try (Connection conn = FewoServer.pds.getConnection()) {
			List<Map> rows = new ArrayList();
			String sql = "SELECT b.houseTitle, COUNT(b.id) as count "
					+ "FROM BookingDetails b GROUP BY b.houseTitle";
			PreparedStatement ps = conn.prepareStatement(sql);
			try (ResultSet rs = ps.executeQuery()) {
				rs.beforeFirst();
				while (rs.next()) {
					Map<String,Object> row = new LinkedHashMap();
					row.put("houseTitle",rs.getString("houseTitle"));
					row.put("count",rs.getInt("count"));
					rows.add(row);
				}
			}
			return rows;
		}
	}

	public List<Map> loadBookings() throws Exception {
		try (Connection conn = FewoServer.pds.getConnection()) {
			List<Map> rows = new ArrayList();
			String sql = "SELECT * FROM BookingDetails";
			PreparedStatement ps = conn.prepareStatement(sql);
			try (ResultSet rs = ps.executeQuery()) {
				rs.beforeFirst();
				while (rs.next()) {
					Map<String,Object> row = new LinkedHashMap();
					row.put("id",rs.getString("id"));
					row.put("name",rs.getString("name"));
					row.put("checkInDate",rs.getDate("checkInDate"));
					row.put("checkOutDate",rs.getDate("checkOutDate"));
					row.put("numberOfGuests",rs.getInt("numberOfGuests"));
					row.put("specialRequests",rs.getString("specialRequests"));
					row.put("houseTitle",rs.getString("houseTitle"));
					row.put("estimatedFund",rs.getDouble("estimatedFund"));
					row.put("dateOfReservation",rs.getDate("dateOfReservation"));
					rows.add(row);
				}
			}
			return rows;
		}
	}

	public List<Map> loadSitesOld() throws Exception {
		try (Connection conn = FewoServer.pds.getConnection()) {
			List<Map> rows = new ArrayList<>();
			String sql = "SELECT * FROM Sites";
			PreparedStatement ps = conn.prepareStatement(sql);
			try (ResultSet rs = ps.executeQuery()) {
				rs.beforeFirst();
				while (rs.next()) {
					Map<String,Object> row = new LinkedHashMap<>();
					row.put("id",rs.getString("id"));
					row.put("siteType",rs.getString("siteType"));
					row.put("siteTitle",rs.getString("siteTitle"));
					row.put("street",rs.getString("street"));
					row.put("city",rs.getString("city"));
					row.put("zipCode",rs.getString("zipCode"));
					row.put("width",rs.getDouble("width"));
					Double l = rs.getDouble("length");
					if (rs.wasNull()) {
						l = null;
					}
					row.put("length",l);
					Double tsf = rs.getDouble("totalSquareFootage");
					if (rs.wasNull()) {
						tsf = null;
					}
					row.put("totalSquareFootage",tsf);
					row.put("numberOfRooms",rs.getInt("numberOfRooms"));
					row.put("roomTypes",rs.getString("roomTypes"));
					row.put("numberOfBeds",rs.getInt("numberOfBeds"));
					row.put("bedTypes",rs.getString("bedTypes"));
					row.put("capacity",rs.getInt("capacity"));
					row.put("parkingAvailable",rs.getBoolean("parkingAvailable"));
					row.put("swimmingPoolAvailable",rs.getBoolean("swimmingPoolAvailable"));
					row.put("petsAllowed",rs.getBoolean("petsAllowed"));
					row.put("petDetails",rs.getString("petDetails"));
					row.put("smokingAllowed",rs.getBoolean("smokingAllowed"));
					row.put("smokingDetails",rs.getString("smokingDetails"));
					row.put("description",rs.getString("description"));
					row.put("fromDate",rs.getDate("fromDate"));
					row.put("toDate",rs.getDate("toDate"));
					row.put("pricing",rs.getDouble("pricing"));
					long time = rs.getDate("created_at").getTime();
					row.put("submissionDate",time);
					rows.add(row);
				}
				rs.close();
			}

			ps.close();
			conn.close();

			return rows;
		}
	}

	//////////////////////////////////////////////////////////////////////////////

	// User Authentication and Organization Management (Friday Nov15th)

	static Map<String,Object[]> sessionPool = new HashMap<>();

	String origin;
	String sessionId;
	String accountId;
	String loggedInOrgId;
	Map loggedIn;
	boolean isGuest = true;
	boolean isDeveloper;

	void onLoggedIn(String orgId, String accountId, String sessionId) throws Exception {
		Map org = loadOrganization(orgId);
		this.sessionId = sessionId;
		this.accountId = accountId;
		this.loggedInOrgId = orgId != null ? orgId : null;
		this.loggedIn = !isDeveloper ? org : null;
		this.isGuest = orgId==null;
		this.isDeveloper = "dev".equals(orgId) && !accountId.equals("ac-3");
	}

	void register(Map<String, Object> formData) throws Exception {
		String firstName = (String) formData.get("firstName");
		String lastName = (String) formData.get("lastName");
		String gender = (String) formData.get("gender");
		String email = (String) formData.get("email");
		String password = (String) formData.get("password");
		String orgName = (String)formData.get("orgName");
		boolean isHost = orgName!=null && orgName.trim().length()>0;

		//condition of email existing in java
		List<Map> existingAccount = DbUtils.loadMany(tables.get("Accounts"), "identity = ?", email);

		if (!existingAccount.isEmpty()) {
			Map acc = existingAccount.get(0);
			Date confirmDate = (Date) acc.get("confirmDate");
			if (confirmDate!=null) {
				// Email already used & confirmed
				throw new IllegalArgumentException("Diese E-Mail-Adresse ist bereits registriert");
			}
			Date registerDate = (Date) acc.get("registerDate");
			Date now = new Date();
			if (registerDate!=null && registerDate.getTime() + 60*60*1000 > now.getTime()) {
				// Email already used & waits for confirmation
				throw new IllegalArgumentException("Diese E-Mail-Adresse ist bereits registriert");
			}
			DbUtils.remove(tables.get("Accounts"), (String)acc.get("id"));
		}

		/*if (!existingAccount.isEmpty() && (confirmDate != null)) {
			throw new IllegalArgumentException("This Email is already taken");
		}*/

		String organizationId = null;
		if (isHost) {
			Map<String,Object> newHost = new LinkedHashMap();
			newHost.put("name",orgName);
			newHost.put("email",email);
			newHost.put("address",formData.get("address"));
			newHost.put("city", formData.get("city"));
			newHost.put("zipCode",formData.get("zipCode"));
			newHost.put("country",formData.get("country"));
			newHost.put("showOn", formData.get("showOn"));

			String privatePageToken = UUID.randomUUID().toString();
			newHost.put("privatePageToken",privatePageToken);

			organizationId = DbUtils.save(tables.get("Organization"),newHost);
		}

		String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());
		String confirmToken = UUID.randomUUID().toString();// we use UUID and not JWT to generate a long string of symbols in the database

		Map<String,Object> newAccount = new LinkedHashMap();
		newAccount.put("organization_id",organizationId);
		newAccount.put("firstName",firstName);
		newAccount.put("lastName",lastName);
		newAccount.put("gender",gender);
		newAccount.put("identity",email);
		newAccount.put("secret",hashedPassword);
		newAccount.put("registerDate",new Date());
		newAccount.put("confirmToken",confirmToken);

		DbUtils.save(tables.get("Accounts"),newAccount);

		//System.out.println("A new guest registered successfully");
		//sendConfirmationEmail(email,firstName,confirmToken);

		String confirmLink = origin+"#RegisterConfirm#"+confirmToken;
		Map customizeInfos = customizeInfos(null); // get customizeInfos by origin/domain
		Map infos = MapUtils.asMap( // compose infos that can be used in mail text template
				"firstName",firstName,"lastName",lastName,"gender",gender,
				"orgName",orgName,"isHost",isHost,
				"confirmLink",confirmLink,
				"pageTitle",customizeInfos.get("title"));
		sendEmail(FewoServer.senderEMail,email,"welcome",null,infos);
	}

//	// the function of sending the con-mail after the host registeration
//	private void sendConfirmationEmail(String email, String firstName, String confirmToken) throws Exception{
//		String confirmLink = origin+"#RegisterConfirm#"+confirmToken;
//		//String confirmLink = "http://localhost:8380/#RegisterConfirm#<confirmToken>" falsch
//		String subject = "Registrierung Bestätigung";
//		String plainText = "Hallo " + firstName + ",\n\n"+
//				"Herzlich Willkommen!\n"+
//				"Vielen Dank für Ihre Registrierung. Ihr Konto wurde erfolgreich erstellt.\n\n"+
//				"Bitte aktivieren Sie Ihr Konto mit folgendem Link:\n"+
//				confirmLink+"\n\n"+
//				"Mit freundlichen Grüßen,\nFewoBuchung Team";
//		String htmlText = "<div style='font-family: Arial, sans-serif; color: #333; padding: 20px;'>" +
//				"<h2 style='color: #2C3E50;'>Registrierung Bestätigung</h2>" +
//				"<p style='font-size: 16px;'>Hallo " + firstName + ",</p>" +
//				"<p style='font-size: 16px;'>Herzlich Willkommen!<br>"+
//				"Vielen Dank für Ihre Registrierung. Ihr Konto wurde erfolgreich erstellt.</p>" +
//				"<p style='font-size: 16px;'>Bitte aktivieren Sie Ihr Konto mit folgendem Link:<br>"+
//				"<a href='"+confirmLink+"'>"+confirmLink+"</a></p>" +
//				"<p style='font-weight: bold;'>Mit freundlichen Grüßen,<br>FewoBuchung Team</p>" +
//				"</div>";
//
//		FewoServer.sendMail(email, subject, plainText, htmlText);
//	}

	/** sending a mail using template strings */
	private void sendEmail(String from, String to, String templateId, String orgId, Map infos) throws Exception{
		Map<String,String> strings = loadStringsByKey(templateId,orgId); // get mail strings
		String subject = strings.get("subject");
		String plainText = strings.get("text");
		String htmlText = strings.get("html");
		subject = StringUtils.substitute(subject,StringUtils.PropertyDoubleBraces,infos); // apply infos
		plainText = StringUtils.substitute(plainText,StringUtils.PropertyDoubleBraces,infos); // to
		htmlText = StringUtils.substitute(htmlText,StringUtils.PropertyDoubleBraces,infos); // templates
		FewoServer.sendMail(from,to,subject,plainText,htmlText);
	}

	void registerConfirm(String confirmToken) throws Exception {
		List<Map> accs = DbUtils.loadMany(tables.get("Accounts"), "confirmToken = ?", confirmToken);
		if (accs.size()!=1)
			throw new IllegalArgumentException("Ungültiger Bestätigungscode.");

		Map account = accs.get(0);
		if (account.get("confirmDate")!=null)
			throw new IllegalArgumentException("Das Konto wurde bereits bestätigt.");

		Date now = new Date();
		Date expirationTime = (Date) account.get("registerDate");
		long validDuration = 24 * 60 * 60 * 1000;
		if (now.getTime() - expirationTime.getTime() > validDuration)
			throw new IllegalArgumentException("Der Bestätigungslink ist abgelaufen.");

		account.put("confirmDate", new Date());
		DbUtils.save(tables.get("Accounts"), account);
	}

	// to check the login info according to the registration inputs
	Object[] login(String user, String pass) throws Exception {
		List<Map> accs = DbUtils.loadMany(tables.get("Accounts"),"identity = ?",user);
		if (accs.size()!=1)
			throw new IllegalArgumentException("Ungültige E-Mail oder ungültiges Passwort");

		Date confirmDate = (Date)accs.get(0).get("confirmDate");
		if (confirmDate==null)
			throw new IllegalArgumentException("Ungültige E-Mail oder ungültiges Passwort");

		String hashed = (String)accs.get(0).get("secret");
		if (!BCrypt.checkpw(pass,hashed))
			throw new IllegalArgumentException("Ungültiges Passwort");

		String sessionId = UUID.randomUUID().toString();
		String accountId = (String)accs.get(0).get("id");
		String orgId = (String)accs.get(0).get("organization_id");
		boolean isHost = orgId!=null;

		onLoggedIn(orgId,accountId,sessionId); // switch to private state
		// TODO add session to session pool
		sessionPool.put(sessionId, new Object[] {accountId, orgId, isHost});

		return new Object[] { sessionId, orgId, isHost };
	}

	void logout() throws Exception {
		// TODO remove session from session pool
		sessionPool.remove(sessionId);

		// switch to public state
		onLoggedIn(null,null,null);
	}

	Object[] session(String sessionId) {
		if (sessionId==null)
			return null;

		if (sessionId.equals(this.sessionId))
			return new Object[] { sessionId, loggedInOrgId, !isGuest, isDeveloper };

		// TODO find session in session pool
		Object[] session = sessionPool.get(sessionId);
		if (session!=null)
			return session;

		return null;
	}

	Map getLoggedInUser() throws Exception {
		if (accountId==null)
			return null;
		return DbUtils.loadOne(tables.get("Accounts"),accountId);
	}
	Map getLoggedInOrg() throws Exception {
		if (loggedInOrgId==null)
			return null;
		return loadOrganization(loggedInOrgId);
	}

	String defaultCustomizeInfos = "{\r\n"
			+ "	\"title\": \"Fewo Buchung\",\r\n"
			+ "	\"logo\": true,\r\n"
			+ "	\"logoImage\": \"img/fewosegel-logo.png\",\r\n"
			+ "	\"login\": true,\r\n"
			+ "	\"filter\": true,\r\n"
			+ "	\"bannerHeight\": 300,\r\n"
			+ "	\"bannerImage\": \"img/banner.jpg\",\r\n"
			+ "	\"#bannerImage\": \"https://static.vecteezy.com/system/resources/thumbnails/021/885/308/small_2x/miniature-house-with-keys-on-wooden-background-real-estate-concept-ai-generated-artwork-photo.jpg\"\r\n"
			+ "}\r\n"
			+ "";

	Map customizeInfos(String privatePageToken) throws Exception {
		String json = defaultCustomizeInfos;
		if (privatePageToken!=null) {
			List<Map> orgs = DbUtils.loadMany(tables.get("Organization"),"privatePageToken = ?",privatePageToken);
			if (orgs.size()==1)
				json = (String)orgs.get(0).get("customizeInfos");

			// try privatePageToken as orgId
			Map org = DbUtils.loadOne(tables.get("Organization"),privatePageToken);
			if (org!=null && org.get("customizeInfos")!=null)
				json = (String)org.get("customizeInfos");
		} else {
			List<Map> orgs = DbUtils.loadMany(tables.get("Organization"),"showOn is not null");
			for (Map org : orgs) {
				String showOn = (String)org.get("showOn");
				String orgDomain = showOn.trim().split("[\r\n]",2)[0];
				if (isOneOfDomains(origin,orgDomain)) {
					json = (String)org.get("customizeInfos");
					break;
				}
			}
		}
		return new Gson().fromJson(json,Map.class);
	}

	void security(Map item) {
		if (!isDeveloper) {
			String ownerOrgId = (String)item.get("organization_id");
			if (!Objects.equal(ownerOrgId,loggedInOrgId))
				throw new IllegalArgumentException("owner mismatch. attempt logged.");
		}
	}

	// reset password section later

	/*public void forgotPassword(String email) throws Exception {
		List<Map> accs = DbUtils.loadMany(tables.get("Accounts"), "identity = ?", email);
		if (accs.size() != 1) {
			throw new IllegalArgumentException("Diese E-Mail-Adresse ist nicht registriert.");
		}

		// to generate reset token
		String resetToken = UUID.randomUUID().toString();

		Map account = accs.get(0);
		account.put("resetToken", resetToken);
		DbUtils.save(tables.get("Accounts"), account);


		String resetLink = origin + "#ResetPassword#" + resetToken;
		sendResetPasswordEmail(email, resetLink);
	}

	private void sendResetPasswordEmail(String email, String resetLink) throws Exception {
		String subject = "Passwort zurücksetzen";
		String plainText = "Bitte klicken Sie auf den folgenden Link, um Ihr Passwort zurückzusetzen:\n" + resetLink;
		String htmlText = "<div>Bitte klicken Sie auf den folgenden Link, um Ihr Passwort zurückzusetzen:</div><a href='" + resetLink + "'>" + resetLink + "</a>";

		FewoServer.sendMail(email, subject, plainText, htmlText);
	}


	public void resetPassword(String resetToken, String newPassword) throws Exception {
		List<Map> accs = DbUtils.loadMany(tables.get("Accounts"), "resetToken = ?", resetToken);
		if (accs.size() != 1) {
			throw new IllegalArgumentException("Ungültiger Reset Token.");
		}

		Map account = accs.get(0);
		String hashedPassword = BCrypt.hashpw(newPassword, BCrypt.gensalt());
		account.put("secret", hashedPassword);
		account.put("resetToken", null);  // to remove the reset token
		DbUtils.save(tables.get("Accounts"), account);
	} */


	/*void security (Map item) {
		if (!isDeveloper) {
			if (item == null || !item.containsKey("organization_id")) {
				throw new IllegalArgumentException ("no organization_id found");
			}
			String ownerOrgId = (String) item.get("organization_id");
			if (ownerOrgId == null || loggedInOrgId == null || !Objects.equals(ownerOrgId, loggedInOrgId)) {
				throw new IllegalArgumentException ("owner mismatch. attempt logged.");
			}
		}
	} */

	Map<String,TableInfo> tables = createTableInfos();

	Map<String,TableInfo> createTableInfos() {
		Map<String,TableInfo> infos = new LinkedHashMap();
		Map<String,String> columns;

		columns = new LinkedHashMap();
		columns.put("id","string");
		columns.put("organization_id","string");
		columns.put("firstName","string");
		columns.put("lastName","string");
		columns.put("gender","string");
		columns.put("identity","string");
		columns.put("secret","string");
		columns.put("registerDate","timestamp");
		columns.put("confirmToken","string");
		columns.put("confirmDate","timestamp");
		//columns.put("resetToken", "string");

		infos.put("Accounts",new TableInfo("Accounts","ac-",columns));

		columns = new LinkedHashMap();
		columns.put("id","string");
		columns.put("name","string");
		columns.put("email","string");
		columns.put("address","string");
		columns.put("city","string");
		columns.put("zipCode","string");
		columns.put("country","string");
		columns.put("showOn","string");
		columns.put("privatePageToken","string");
		columns.put("customizeInfos","string");
		infos.put("Organization",new TableInfo("Organization","org-",columns));

		columns = new LinkedHashMap();
		columns.put("id","string");
		columns.put("organization_id","string");
		columns.put("previous_id","string");
		columns.put("context","string");
		columns.put("purpose","string");
		columns.put("skey","string");
		columns.put("svalue","string");
		columns.put("lang","string");
		columns.put("comment","string");
		columns.put("version","integer");
		columns.put("timeCreate","timestamp");
		columns.put("timeUpdate","timestamp");
		infos.put("Strings",new TableInfo("Strings","str-",columns));

		columns = new LinkedHashMap();
		columns.put("id","string");
		columns.put("organization_id","string");
		columns.put("name","string");
		columns.put("shortName","string");
		columns.put("presentationType","string");
		columns.put("availabilityType","string");
		columns.put("unitCount","integer");
		//columns.put("roomType_ids", "string");
		columns.put("teaser","string");
		columns.put("description","string");
		columns.put("address","string");
		columns.put("city","string");
		columns.put("state","string");
		columns.put("country","string");
		columns.put("phoneNumber","string");
		columns.put("email","string");
		columns.put("roomtypeLabel","string");
		columns.put("showRoomtypePhotos","boolean");
		infos.put("Site",new TableInfo("Site","st",columns));

		columns = new LinkedHashMap();
		columns.put("id","string");
		columns.put("organization_id","string");
		columns.put("site_id","string");
		columns.put("name","string");
		columns.put("shortName","string");
		columns.put("adults","integer");
		columns.put("supertype_ids","string");
		columns.put("teaser","string");
		columns.put("description","string");
		columns.put("price","double");
		columns.put("cleanService","double");
		columns.put("showSitePhotos","boolean");
		infos.put("RoomType",new TableInfo("RoomType","rt-",columns));

		columns = new LinkedHashMap();
		columns.put("id","string");
		columns.put("organization_id","string");
		columns.put("roomType_id","string");
		columns.put("site_id","string");
		columns.put("name","string");
		columns.put("shortName","string");
		//columns.put("children","integer");
		columns.put("bedSizes","string");
		columns.put("rate","double");
		columns.put("view","string");
		columns.put("available","boolean");
		infos.put("Room",new TableInfo("Room","ro-",columns));

		columns = new LinkedHashMap();
		columns.put("id","string");
		columns.put("site_id","string");
		columns.put("roomType_id","string");
		columns.put("room_id","string");
		columns.put("belongsTo","string");
		columns.put("name","string");
		columns.put("active","boolean");
		columns.put("startDate","date");
		columns.put("endDate","date");
		columns.put("conditions","string");
		columns.put("adjustType","string");
		columns.put("adjustValue","double");
		infos.put("PricingRule",new TableInfo("PricingRule","pr-",columns));

		columns = new LinkedHashMap();
		columns.put("id","string");
		columns.put("file","string");
		columns.put("order", "integer");
		columns.put("caption","string");
		columns.put("origName","string");
		columns.put("organization_id","string");
		columns.put("site_id","string");
		columns.put("roomType_id","string");
		infos.put("Photos",new TableInfo("Photos","ph-",columns));

		columns = new LinkedHashMap();
		columns.put("id","string"); // this is for BookingRoomType
		columns.put("booking_id", "string");  // this is for BookingData.id
		columns.put("roomType_id","string");
		columns.put("room_ids","string");
		columns.put("name","string"); //roomtype name
		columns.put("count","integer"); // how many booked rooms of this type
		columns.put("guests","integer");
		columns.put("pricePerNight","double");
		infos.put("BookingRoomType", new TableInfo("BookingRoomType","bookedRT-",columns));

		columns = new LinkedHashMap();
		columns.put("id","string");
		columns.put("site_id","string");
		columns.put("siteName","string");
		columns.put("roomType_id","string"); //for a single roomtype bookings.
		columns.put("room_id","string"); //for a single room  bookings.
		columns.put("startDate","date");
		columns.put("endDate","date");
		columns.put("firstName","string");
		columns.put("lastName","string");
		columns.put("email","string");
		columns.put("address","string");
		columns.put("telephone","string");
		columns.put("additionalWishes","string");
		columns.put("totalGuests","integer");
		columns.put("totalPrice","double"); // the whole amount the customer paid
		columns.put("price","double");
		columns.put("priceType","string"); // "total", "perNight" | to determine where the total price or the price perNight
		columns.put("dateSent","timestamp");
		columns.put("status","string"); // pending, accepted, rejected, canceled
		columns.put("statusDecisionTime","timestamp");
		columns.put("source","string");
		infos.put("BookingData",new TableInfo("BookingData","bd-",columns));

		columns = new LinkedHashMap();
		columns.put("id","string");
		columns.put("roomType_id","string");
		columns.put("name", "string");
		infos.put("Amenities",new TableInfo("Amenities","Amen-",columns));

		return infos;
	}

	public static class TableInfo
	{
		public String name;
		public String prefix;
		public Map<String,String> columns;

		public TableInfo(String name, String prefix, Map columns) {
			this.name = name;
			this.prefix = prefix;
			this.columns = columns;
		}
	}

	// Organization section
	public List<Map> loadOrganizations() throws Exception {
		return DbUtils.loadAll(tables.get("Organization"));
	}

	public Map loadOrganization(String itemId) throws Exception {
		Map <String, Object> org = DbUtils.loadOne(tables.get("Organization"),itemId,null);
		if (org==null)
			return org;

		String privatePageToken = (String)org.get("privatePageToken");
		if (privatePageToken==null || privatePageToken.toString().trim().isEmpty() ) {
			privatePageToken = UUID.randomUUID().toString();
			org.put("privatePageToken",privatePageToken);
			DbUtils.save(tables.get("Organization"),org);
		}

		String shareLink = origin + "#Presentation#" + privatePageToken;
		org.put("shareLink",shareLink);
		return org;
	}

	public void saveOrganization(Map item) throws Exception {
		DbUtils.save(tables.get("Organization"),item);
	}

	public void removeOrganization(String itemId) throws Exception {
		DbUtils.remove(tables.get("Organization"),itemId);
	}

	// Strings section
	public List<Map> loadStrings() throws Exception {
		if (isDeveloper) return loadStringsByPurpose(null,"*");
		return loadStringsByPurpose("request:%",loggedInOrgId);
	}

	public Map loadString(String itemId) throws Exception {
		if (isDeveloper) return DbUtils.loadOne(tables.get("Strings"),itemId);
		return DbUtils.loadOne(tables.get("Strings"),itemId,
				"organization_id = ? or organization_id is null",loggedInOrgId);
	}

	public Map<String,String> loadStringsByKey(String purpose, String orgId) throws Exception {
		List<Map> strings = loadStringsByPurpose(purpose,orgId);
		Map byKey = new LinkedHashMap();
		for (Map s : strings)
			byKey.put(s.get("skey"),s.get("svalue"));
		return byKey;
	}

	public String saveString(Map item) throws Exception {
		boolean raw = Boolean.TRUE==item.remove("raw");
		boolean force = Boolean.TRUE==item.remove("force");
		boolean amend = Boolean.TRUE==item.remove("amend");

		if (force && !isDeveloper) return null; // not allowed for users
		if (raw && !isDeveloper) return null; // not allowed for users
		TableInfo table = tables.get("Strings");
		if (raw) {
			DbUtils.save(table,item);
			return (String)item.get("id");
		}

		String strId = (String)item.remove("id");
		String orgId = (String)item.remove("organization_id");
		if (!isDeveloper) orgId = loggedInOrgId;
		Map current = loadString(strId);
		int version = ((Number)current.get("version")).intValue();
		if (!force) { // context, purpose, lang, skey should be stable for a string instance
			if (item.get("context")==null) item.put("context",current.get("context"));
			if (item.get("purpose")==null) item.put("purpose",current.get("purpose"));
			if (item.get("lang")==null) item.put("lang",current.get("lang"));
			if (item.get("skey")==null) item.put("skey",current.get("skey"));
			if (!Objects.equal(current.get("context"),item.get("context"))) return null;
			if (!Objects.equal(current.get("purpose"),item.get("purpose"))) return null;
			if (!Objects.equal(current.get("lang"),item.get("lang"))) return null;
			if (!Objects.equal(current.get("skey"),item.get("skey"))) return null;
			if (Objects.equal(current.get("svalue"),item.get("svalue"))) return null;
			item.remove("timeCreate"); // no force, no modification of timestamps
			item.remove("timeUpdate"); // no force, no modification of timestamps
			item.remove("previous_id"); // no force, no modification of history / branching
		}

		int newVersion = item.get("version")!=null ? ((Number)item.get("version")).intValue() : version;
		while (newVersion<=version)
			newVersion++;
		String previousId = null; // decide for new record (continue history) vs. updating the recent (amend)
		if (!amend && item.get("previous_id")==null) {
			previousId = strId;
			strId = table.prefix+UUID.randomUUID().toString().substring(0,8);
			// try to continue some manual ID scheme
			Pattern numberAtEnd = Pattern.compile("-(\\d)$");
			Matcher m = numberAtEnd.matcher(previousId);
			if (m.find()) {
				if (Integer.parseInt(m.group(1))==version) {
					String id = previousId.substring(0,previousId.length()-m.group().length());
					id += "-" + newVersion;
					if (loadString(id)==null)
						strId = id;
				}
			}
			item.put("previous_id",previousId);
			item.put("id",strId);
			// try to create some meaningful ID
			// ID from purpose/skey/version? regex: (?m)^(\w{1,8}).*?(\w{0,8})$
			// examples: request:accept, thingwithlongname:somelittledetail, fd34, fdsfssfsdffsd
			// length: 4 (prefix) + 8+1+8 (purpose parts) + 8+1+8 (skey parts) + 2 (version) = max 40
		}
		item.put("version",newVersion);
		if (!isDeveloper)
			item.put("organization_id",loggedInOrgId);
		DbUtils.save(tables.get("Strings"),item);
		return (String)item.get("id");
	}

	List<Map> loadStringsByPurpose(String purpose, String orgId) throws Exception {
		String sql = "select * from Strings as s, "+
				"(select organization_id,context,purpose,skey,lang,max(version) as version from Strings "+
				(purpose!=null ? "\nwhere (purpose like ?) " : "\nwhere (purpose is not null or purpose = ?) ")+
				(orgId!=null && !"*".equals(orgId) ? "and (organization_id = ? or organization_id is null)" :
					("*".equals(orgId) ? "or ('nomatch'=?)" : "or ('nomatch'=?) and (organization_id is null)"))+
				"\ngroup by organization_id,context,purpose,skey,lang) r "+
				"\nwhere " + (orgId!=null && !"*".equals(orgId) ? "(s.organization_id = ? or s.organization_id is null)" :
					("*".equals(orgId) ? "('nomatch'=? or 1=1)" : "('nomatch'=? or 1=1) and (s.organization_id is null)")) +
				"\nand s.context = r.context and s.purpose = r.purpose and s.skey = r.skey and s.lang = r.lang and s.version = r.version"+
				"\norder by s.version";
		return DbUtils.loadManySql(tables.get("Strings"),sql,""+purpose,""+orgId,""+orgId);
	}

	// Sites section
//	public List <Map> loadSites(String organization_id) throws Exception {
//		String query;
//		if (organization_id.equals("dev")) {
//			query = "SELECT * FROM Site";
//			return DbUtils.executeQuery(query, organization_id);
//		}
//	}

	public List<Map> loadSites() throws Exception {
		return loadSites(true,null);
	}
	public List<Map> loadSites(boolean ignoreDomain) throws Exception {
		return loadSites(ignoreDomain,null);
	}
	public List<Map> loadSites(boolean ignoreDomain, String privatePageToken) throws Exception {
		if (privatePageToken!=null) {
			List<Map> orgs = DbUtils.loadMany(tables.get("Organization"),"privatePageToken = ?",privatePageToken);
			if (orgs.size()!=1)
				return Collections.EMPTY_LIST;
			String orgId = (String)orgs.get(0).get("id");
			return DbUtils.loadMany(tables.get("Site"),"organization_id = ?",orgId);
		}

		List<Map> loaded;
		if (isGuest) loaded = DbUtils.loadAll(tables.get("Site"));
		else if (isDeveloper) loaded = DbUtils.loadAll(tables.get("Site"));
		else loaded = DbUtils.loadMany(tables.get("Site"),"organization_id = ?",loggedInOrgId);

		if (ignoreDomain)
			return loaded;

//		String allowedOnDomains = "\n\n  chalets.local  \nfewobuchung.local  \n";
//		String[] domains = allowedOnDomains.split("\n");
//		boolean allow = false;
//		for (String d : domains)
//			allow |= d.trim().length()>0 && origin.contains("://"+d.trim());
//		if (!allow)
//			return Collections.EMPTY_LIST;

//		boolean originIsPrivatePage = isOneOfDomains(origin,FewoServer.privatePages);
		boolean originIsPublicPage = isOneOfDomains(origin,FewoServer.publicPages);
		List<Map> filtered = new ArrayList();
		for (Map site : loaded) {
			Map org = loadOrganization((String)site.get("organization_id"));
			String showOnDomains = org!=null ? (String)org.get("showOn") : null;
			/*String showOnDomains =
				site.get("organization_id").equals("org-pi") ?
						"\n\n  premier.local  \nhost2.local  \n" :
				site.get("organization_id").equals("org-ms") ?
//						"chalets.local" : null;
						"\n\n  chalets.local  \nfewobuchung.local  \n" : null; */
			boolean show = false;
			if (showOnDomains!=null && showOnDomains.trim().length()>0) {
				show = isOneOfDomains(origin,showOnDomains);
//			} else show = !originIsPrivatePage;
			} else show = originIsPublicPage;
			if (show)
				filtered.add(site);
		}
		return filtered;
	}

	static boolean isOneOfDomains(String domain, String domainPerLine) {
		String[] domains = domainPerLine.split("\\s");
		boolean isOneOf = false;
		for (String d : domains)
			isOneOf |= d.trim().length()>0 && domain.contains("://"+d.trim());
		return isOneOf;
	}

	public Map loadSite(String itemId) throws Exception {
		if (isDeveloper) return DbUtils.loadOne(tables.get("Site"),itemId);
		// TODO if (isHost) DbUtils.loadOne(tables.get("Site"),itemId,"organization_id = ?",loggedInOrgId);
		return DbUtils.loadOne(tables.get("Site"),itemId); // temporary
	}

	public void saveSite(Map item) throws Exception {
		security(item);
		System.out.println("Saving site with fields: " + item.keySet());
		if (item.get("unitCount")==null)
			item.put("unitCount",0);
		if (item.get("presentationType")==null)
			item.put("presentationType","roomtypes");
		DbUtils.save(tables.get("Site"),item);
	}

	public void removeSite(String itemId) throws Exception {

		security(loadSite(itemId));
		DbUtils.remove(tables.get("Site"),itemId);
	}

	public List<Map> loadPhotos(String siteOrRoomtypeId) throws Exception {
		return DbUtils.loadMany(tables.get("Photos"),
				"site_id = ? OR roomType_id = ?",siteOrRoomtypeId,siteOrRoomtypeId);
	}

	// Save photos method

	// applying load images function to fetch the old uploaded images

	public List<Map> savePhotos(String siteOrRoomtypeId, List<Map<String, Object>> photos) throws Exception {
		if (siteOrRoomtypeId==null) {
			throw new IllegalArgumentException("No site or room type.");
		}
		if (photos == null || photos.isEmpty()) {
			throw new IllegalArgumentException("Photo list is empty or null.");
		}

		List<Map> inDb = loadPhotos(siteOrRoomtypeId);
		List<String> idsInDb = new ArrayList();
		List<String> idsGiven = new ArrayList();
		for (Map photo : inDb)
			idsInDb.add((String)photo.get("id"));
		for (Map photo : photos)
			idsGiven.add((String)photo.get("id"));

		List<String> toDeleteIds = new ArrayList(idsInDb);
		toDeleteIds.removeAll(idsGiven);
		for (String id : toDeleteIds)
			DbUtils.remove(tables.get("Photos"),id);

		String organization_id = null;
		if (organization_id==null) {
			Map site = loadSite(siteOrRoomtypeId);
			organization_id = site != null ? (String)site.get("organization_id") : null;
			if (site!=null)
				security(site);
		}
		if (organization_id==null) {
			Map rt = loadRoomType(siteOrRoomtypeId);
			organization_id = rt != null ? (String)rt.get("organization_id") : null;
			if (rt!=null)
				security(rt);
		}
		if (organization_id == null) {
			throw new IllegalArgumentException("No organization found.");
		}

		for (Map<String, Object> photo : photos) {
			// to validate and process photo
			if (photo.get("file") == null) {
				throw new IllegalArgumentException("Missing file name for photo: " + photo);
			}

			if (photo.get("site_id") != null && !photo.get("site_id").equals(siteOrRoomtypeId)) {
				throw new IllegalArgumentException("Sites in photos are mixed up.");
			}

			if (photo.get("roomType_id") != null && !photo.get("roomType_id").equals(siteOrRoomtypeId)) {
				throw new IllegalArgumentException("Room types in photos are mixed up.");
			}

			if (photo.get("caption") == null) {
				System.err.println("Caption is missing for photo: " + photo);
			}

			// Prepare photo record
			Map<String, Object> photoRecord = new HashMap<>();
			photoRecord.put("id", photo.get("id"));
			photoRecord.put("file", photo.get("file"));
			photoRecord.put("order", photo.get("order"));
			photoRecord.put("caption", photo.get("caption"));
			photoRecord.put("origName", photo.get("origName"));
			photoRecord.put("organization_id", organization_id);
			photoRecord.put("site_id", photo.get("site_id"));
			photoRecord.put("roomType_id", photo.get("roomType_id"));

			// to save photo
			try {
				DbUtils.save(tables.get("Photos"), photoRecord);
			} catch (Exception e) {
				System.err.println("Saving photo failed: " + photoRecord);
				throw e;
			}
		}
		return loadPhotos(siteOrRoomtypeId);
	}

	// Room Types section
	public List<Map> loadRoomTypes() throws Exception {
		if (isGuest) return DbUtils.loadAll(tables.get("RoomType"));
		if (isDeveloper) return DbUtils.loadAll(tables.get("RoomType"));
		return DbUtils.loadMany(tables.get("RoomType"),"organization_id = ?",loggedInOrgId);
	}

	public Map loadRoomType(String itemId) throws Exception {
		if (isDeveloper) return DbUtils.loadOne(tables.get("RoomType"),itemId);
		return DbUtils.loadOne(tables.get("RoomType"),itemId,"organization_id = ?",loggedInOrgId);
	}

	public void saveRoomType(Map item) throws Exception {
		if (item.get("showSitePhotos")==null)
			item.put("showSitePhotos",false);
		DbUtils.save(tables.get("RoomType"),item);
	}

	public void removeRoomType(String itemId) throws Exception {
		DbUtils.remove(tables.get("RoomType"),itemId);
	}

	// Rooms section
	public List<Map> loadRooms() throws Exception {
		if (isGuest) return DbUtils.loadAll(tables.get("Room"));
		if (isDeveloper) return DbUtils.loadAll(tables.get("Room"));
		return DbUtils.loadMany(tables.get("Room"),"organization_id = ?",loggedInOrgId);
	}

	public Map loadRoom(String itemId) throws Exception {
		if (isDeveloper) return DbUtils.loadOne(tables.get("Room"),itemId);
		return DbUtils.loadOne(tables.get("Room"),itemId,"organization_id = ?",loggedInOrgId);
	}

	public void saveRoom(Map item) throws Exception {
		if (item.get("rate")==null)
			item.put("rate",0);
		if (item.get("available")==null)
			item.put("available",false);
		DbUtils.save(tables.get("Room"),item);
	}

	public void removeRoom(String itemId) throws Exception {
		DbUtils.remove(tables.get("Room"),itemId);
	}

	// PricingRules section
	public List<Map> loadPricingRules() throws Exception {
		if (isDeveloper) return DbUtils.loadAll(tables.get("PricingRule"));

		String sql = "SELECT p.* FROM PricingRule p " +
				"LEFT JOIN Room r ON p.room_id = r.id " +
				// "LEFT JOIN RoomType t ON t.id = p.roomType_id " +
				"LEFT JOIN RoomType t ON p.roomType_id = t.id OR r.roomType_id = t.id " +
				//"LEFT JOIN Site a ON a.id = p.site_id" +
				//"WHERE (t.organization_id = ? OR a.organization_id = ? )";
				"WHERE t.organization_id = ?";
		List<Map> pricingRules = DbUtils.loadManySql(tables.get("PricingRule"),sql,loggedInOrgId);
		return pricingRules;
	}
	public List<Map> loadPricingRule(String siteId) throws Exception {
		if (isDeveloper) return DbUtils.loadAll(tables.get("PricingRule"));

		String sql = "SELECT p.* FROM PricingRule p " +
				"LEFT JOIN Room r ON p.room_id = r.id " +
				"LEFT JOIN RoomType t ON p.roomType_id = t.id OR r.roomType_id = t.id " +
				//"LEFT JOIN Site a ON a.id = p.site_id" +
				//"WHERE (t.organization_id = ? OR a.organization_id = ? )";
				"WHERE p.site_id = ?";
		List values = new ArrayList();
		values.add(loggedInOrgId);
		values.add(siteId);
		List<Map> pricingRules = DbUtils.loadManySql(tables.get("PricingRule"),sql,siteId);
		return pricingRules;
	}

	public void savePricingRule(Map item) throws Exception {
		DbUtils.save(tables.get("PricingRule"),item);
	}

	public void removePricingRule(String itemId) throws Exception {
		DbUtils.remove(tables.get("PricingRule"),itemId);
	}

	// Amenticies section

	public List<String> loadAmenities(String roomTypeId) throws Exception {
		List<Map> amenities = DbUtils.loadMany(tables.get("Amenities"),"roomType_id = ?",roomTypeId);
		List<String> names = new ArrayList();
		for (Map a : amenities)
			names.add((String)a.get("name"));
		return names;
	}

	public void saveAmenities(String roomTypeId, List<String> amenities) throws Exception {
		List<Map> dbAmenities = DbUtils.loadMany(tables.get("Amenities"),"roomType_id = ?",roomTypeId);
		for (Map a : dbAmenities)
			removeAmenity((String)a.get("id"));
		for (String a : amenities) {
			Map asMap = new LinkedHashMap();
			asMap.put("id","amen-"+Long.toString(new Random().nextLong(),36));
			asMap.put("roomType_id",roomTypeId);
			asMap.put("name",a);
			DbUtils.save(tables.get("Amenities"),asMap);
		}
	}

	public void removeAmenity(String itemId) throws Exception {
		DbUtils.remove(tables.get("Amenities"),itemId);
	}

	// BookingData section

	public void guestRequest(Map bookingData) throws Exception {
		bookingData.put("source",origin);

		String guestEmail = (String) bookingData.get("email");
		if (guestEmail == null || guestEmail.isEmpty()){
			throw new IllegalArgumentException ("Guest email is invalid or missing");
		}

		String siteId = (String)bookingData.get("site_id");
		String contactEmail = getContactEmail(siteId,false);
		if (contactEmail == null || contactEmail.isEmpty()) {
			throw new IllegalArgumentException("Contact email is missing");
		}

		String hostEmail = getContactEmail(siteId,true);
		if (hostEmail == null || hostEmail.isEmpty()) {
			throw new IllegalArgumentException("Host email is missing");
		}

		saveBookingData(bookingData);

//		// Email content to the guest
//		String guestSubject = "Buchungsanfrage erhalten";
//		String guestPlainText = "Vielen Dank für Ihre Buchungsanfrage! Wir werden diese prüfen und uns in Kürze bei Ihnen melden.";
//		String guestHtmlText = "<div style='font-family: Arial, sans-serif; color: #333; padding: 20px;'>" +
//				"<h2 style='color: #2C3E50;'>Buchungsanfrage erhalten</h2>" +
//				"<p style='font-size: 16px;'>Vielen Dank für Ihre Buchungsanfrage! Wir werden diese sorgfältig prüfen und uns in Kürze bei Ihnen melden.</p>" +
//				"<p style='font-weight: bold;'>Mit freundlichen Grüßen,<br> Ihr Buchungsteam</p>" +
//				"</div>";
//
//		// Email content to the host
//		String hostSubject = "Neue Buchungsanfrage!";
//		String hostPlainText = "Eine neue Buchungsanfrage wurde erhalten. Bitte überprüfen Sie die ausstehenden Anfragen.";
//		String hostHtmlText = "<div style='font-family: Arial, sans-serif; color: #333; padding: 20px;'>" +
//				"<h2 style='color: #2C3E50;'>Neue Buchungsanfrage</h2>" +
//				"<p style='font-size: 16px;'>Eine neue Buchungsanfrage wurde erhalten. Bitte überprüfen Sie die ausstehenden Anfragen in Ihrem System.</p>" +
//				"<p style='font-weight: bold;'>Mit freundlichen Grüßen </p>" +
//				"</div>";
//
//		FewoServer.sendMail(guestEmail, guestSubject, guestPlainText, guestHtmlText);
//		FewoServer.sendMail(hostEmail, hostSubject, hostPlainText, hostHtmlText);

		Map customizeInfos = customizeInfos(loggedInOrgId); // get customizeInfos by logged in host org
		Map orgInfos = getSiteOrg(siteId);
		Map site = loadSite(siteId);
		Map infos = new LinkedHashMap();
		infos.putAll(bookingData);
		infos.put("orgName",orgInfos.get("name"));
		infos.put("siteName",site.get("name"));
		infos.put("siteCity",site.get("city"));
		infos.put("siteAddress",site.get("address"));
		infos.put("siteEmail",site.get("email"));
		infos.put("sitePhone",site.get("phoneNumber"));
		infos.put("siteTeaser",site.get("teaser"));
		infos.put("pageTitle",customizeInfos.get("title"));
		String siteOrgId = (String)orgInfos.get("id");

		sendEmail(contactEmail,guestEmail,"request:guest",siteOrgId,infos);
		sendEmail(FewoServer.senderEMail,hostEmail,"request:host",siteOrgId,infos);

		// TODO guestEMailAdr from bookingData
		// TODO hostEMailAdr from site org
		// TODO FewoServer.sendMail(guestEMailAdr,"Booking - Thank you","plain text","html text");
		// TODO FewoServer.sendMail(hostEMailAdr,"New Booking Request!","plain text","html text");
	}


	public void requestStatusUpdate(Map bookingData) throws Exception {
		saveBookingData(bookingData);

		String guestEmail = (String) bookingData.get("email");
		if (guestEmail == null || guestEmail.isEmpty()) {
			throw new IllegalArgumentException("Guest email is invalid or missing");
		}

		String status = (String) bookingData.get("status");
		if (status == null) {
			throw new IllegalArgumentException ("Booking status is missing");
		}

		String siteId = (String)bookingData.get("site_id");
		String contactEmail = getContactEmail(siteId,false);
		if (contactEmail == null || contactEmail.isEmpty()) {
			throw new IllegalArgumentException("Contact email is missing");
		}

		/*String roomTypeId = (String) bookingData.get("roomType_id");
		if (roomTypeId == null || roomTypeId.isEmpty()) {
			throw new IllegalArgumentException("Room Type Id is missing");
		}*/

//		String subject;
//		String plainText;
//		String htmlText;

		Map customizeInfos = customizeInfos(loggedInOrgId); // get customizeInfos by logged in host org
		Map orgInfos = getLoggedInOrg();
		Map site = loadSite(siteId);
		Map infos = new LinkedHashMap();
		infos.putAll(bookingData);
		infos.put("orgName",orgInfos.get("name"));
		infos.put("siteName",site.get("name"));
		infos.put("siteCity",site.get("city"));
		infos.put("siteAddress",site.get("address"));
		infos.put("siteEmail",site.get("email"));
		infos.put("sitePhone",site.get("phoneNumber"));
		infos.put("siteTeaser",site.get("teaser"));
		infos.put("pageTitle",customizeInfos.get("title"));
		String siteOrgId = (String)orgInfos.get("id");

		if ("akzeptiert".equals(status)) {
//			String subject = "Buchung bestätigt!";
//			String plainText = "Ihre Buchung wurde akzeptiert. Vielen Dank!";
//			String htmlText = "<div style='font-family: Arial, sans-serif; color: #333; padding: 20px;'>" +
//					"<h2 style='color: #2C3E50;'>Buchung bestätigt!</h2>" +
//					"<p style='font-size: 16px;'>Ihre Buchung wurde erfolgreich akzeptiert. Vielen Dank für Ihr Vertrauen!</p>" +
//					"<p style='font-weight: bold;'>Mit freundlichen Grüßen,<br> Ihr Buchungsteam</p>" +
//					"</div>";
//			FewoServer.sendMail(guestEmail,subject,plainText,htmlText);

			sendEmail(contactEmail,guestEmail,"request:accept",siteOrgId,infos);
		} else if ("abgelehnt".equals(status)) {
//			String subject = "Buchung abgelehnt!";
//			String plainText = "Leider können wir Ihre Buchungsanfrage nicht bearbeiten.";
//			String htmlText = "<div style='font-family: Arial, sans-serif; color: #333; padding: 20px;'>" +
//					"<h2 style='color: #C0392B;'>Buchung abgelehnt</h2>" +
//					"<p style='font-size: 16px;'>Es tut uns leid, aber wir können Ihre Buchungsanfrage nicht bearbeiten.</p>" +
//					"<p style='font-weight: bold;'>Mit freundlichen Grüßen,<br> Ihr Buchungsteam</p>" +
//					"</div>";
//			FewoServer.sendMail(guestEmail,subject,plainText,htmlText);

			sendEmail(contactEmail,guestEmail,"request:reject",siteOrgId,infos);
		} else {
			throw new IllegalArgumentException ("Invalid booking status: " + status);
		}
//		// mail to guest (sorry -OR- you're welcome)
//		// TODO FewoServer.sendMail
//		FewoServer.sendMail(guestEmail,subject,plainText,htmlText);
	}

	/** getting a contact email address for an site */
	private String getContactEmail(String siteId, boolean forHostOrg) throws Exception {
		if (siteId == null) return null;

		Map siteData = DbUtils.loadOne(tables.get("Site"), siteId);
		if (siteData == null) {
			System.out.println("Site not found! ID: " + siteId);
			throw new IllegalArgumentException("Site not found");
		}
		if (!forHostOrg) {
			String email = (String)siteData.get("email");
			if (email!=null && email.trim().length()>0)
				return email.trim();
		}

		String organizationId = (String) siteData.get("organization_id");
		System.out.println("Organization ID: " + organizationId);

		Map organizationData = DbUtils.loadOne(tables.get("Organization"), organizationId);
		if (organizationData == null) {
			System.out.println("Organization not found! ID: " + organizationId);
			throw new IllegalArgumentException("Organization not found");
		}
		String email = (String) organizationData.get("email");
		System.out.println("Host Email Found: " + email);
		return (String) organizationData.get("email");
	}
	private Map getSiteOrg(String siteId) throws Exception {
		if (siteId == null) return null;

		Map siteData = DbUtils.loadOne(tables.get("Site"), siteId);
		if (siteData == null) {
			System.out.println("Site not found! ID: " + siteId);
			throw new IllegalArgumentException("Site not found");
		}
		String organizationId = (String) siteData.get("organization_id");
		System.out.println("Organization ID: " + organizationId);

		Map organizationData = DbUtils.loadOne(tables.get("Organization"), organizationId);
		if (organizationData == null) {
			System.out.println("Organization not found! ID: " + organizationId);
			throw new IllegalArgumentException("Organization not found");
		}
		return organizationData;
	}

	// Firstly the function of saving the form data
	public void saveBookingData(Map bookingData) throws Exception {
		// to validate the incoming data
		if (bookingData.get("firstName") == null || bookingData.get("email") == null) {
			throw new IllegalArgumentException("Required fields are missing");
		}

		/*if (bookingData.get("roomType_id") == null || ((String) bookingData.get("roomType_id")).isEmpty()) {
			throw new IllegalArgumentException("Room Type ID is missing");
		}*/

		//String roomTypeId = (String)bookingData.get("roomType_id");
		List<Map> roomTypesList = (List<Map>)bookingData.get("roomTypes");

		if (roomTypesList==null || roomTypesList.isEmpty()) {
			String roomTypeId = (String) bookingData.get("roomType_id");
			if (roomTypeId != null && !roomTypeId.isEmpty()) {
				Map<String, Object> defaultRoomType = new LinkedHashMap<>();
				defaultRoomType.put("roomType_id", roomTypeId);
				defaultRoomType.put("count", 1);
				defaultRoomType.put("guests", bookingData.getOrDefault("guests", 1));
				defaultRoomType.put("name", "Standard");
				defaultRoomType.put("pricePerNight", bookingData.getOrDefault("pricePerNight", 0));

				List<Map> tmpList = new ArrayList<>();
				tmpList.add(defaultRoomType);
				roomTypesList = tmpList;

				bookingData.put("roomTypes", roomTypesList);
			} else {
				throw new IllegalArgumentException("Booking must include a single roomtype or roomTypes list.");
			}
		}

		if (roomTypesList==null || roomTypesList.isEmpty()) {
			throw new IllegalArgumentException("Booking must include a single roomtype or roomTypes list.");
		}

		boolean hasMultipleRoomType = roomTypesList.stream().map(
				rt -> rt.get("roomType_id")).collect(Collectors.toSet()).size() > 1;
		boolean hasCountGreaterOne = roomTypesList.size() > 1 ||
				roomTypesList.stream()
				.map(rt -> ((Number)rt.get("count")).intValue())
				.reduce(0,(total,count) -> total += count) > 1;

		//if the booking already exists, it keeps the same id, otherwise a fresh unique id is get.
		if (bookingData.get("id") == null || ((String) bookingData.get("id")).isEmpty()) {
			bookingData.put("id", "booking_" + UUID.randomUUID().toString().substring(0, 8));
		}
		//save or update main booking entry record
		String bookingId = DbUtils.save(tables.get("BookingData"), bookingData);

		//to remove old room types for this booking before re-adding
		try (Connection conn = FewoServer.pds.getConnection()) {
			PreparedStatement ps = conn.prepareStatement(
				"DELETE FROM " + tables.get("BookingRoomType").name + " WHERE booking_id = ?"
			);
			ps.setString(1, bookingId);
			ps.executeUpdate();
			ps.close();
		}
		// save extra booking data
		if (hasMultipleRoomType || hasCountGreaterOne) {
			for (Map rtEntry : roomTypesList) {
				Map<String, Object> bookingRoomType = new LinkedHashMap<>();
				bookingRoomType.put("booking_id", bookingId);
				bookingRoomType.put("roomType_id", rtEntry.get("roomType_id"));
				bookingRoomType.put("room_ids",rtEntry.get("room_ids"));
				bookingRoomType.put("name", rtEntry.get("name"));
				bookingRoomType.put("count", rtEntry.get("count"));
				bookingRoomType.put("guests", rtEntry.get("guests"));
				bookingRoomType.put("pricePerNight", rtEntry.get("pricePerNight"));

				DbUtils.save(tables.get("BookingRoomType"), bookingRoomType);
			}
		}
	}

	public void removeBookingData(String itemId) throws Exception {
		DbUtils.remove(tables.get("BookingData"),itemId);
	}

	// Load all booking data
//	public List<Map> loadAllBookingData() throws Exception {
//		if (isGuest) return Collections.EMPTY_LIST;
//		if (isDeveloper) return DbUtils.loadAll(tables.get("BookingData"));
//		String sql = "SELECT * FROM BookingData b \n" +
//				"LEFT JOIN Site a ON a.id = b.site_id \n" +
//				"WHERE a.organization_id = ?";
//		return DbUtils.loadManySql(tables.get("BookingData"),sql,loggedInOrgId);
//	}
	// for loading multi-room bookings.
	public List<Map> loadAllBookingData() throws Exception {
		if (isGuest) return Collections.EMPTY_LIST;

		List<Map> bookings;
		if (isDeveloper) {
			bookings = DbUtils.loadAll(tables.get("BookingData"));
		} else {
			String sql = "SELECT b.* FROM BookingData b " +
			"LEFT JOIN Site a ON a.id = b.site_id " +
			"WHERE a.organization_id = ?";

			bookings= DbUtils.loadManySql(tables.get("BookingData"), sql, loggedInOrgId);
		}

		for (Map booking : bookings) {
			String bookingId = (String) booking.get("id");
			String rtSql = "SELECT * FROM BookingRoomType WHERE booking_id = ?";
			List<Map> roomTypes = DbUtils.loadManySql(tables.get("BookingRoomType"), rtSql, bookingId);
			booking.put("roomTypes", roomTypes);
		}

		return bookings;

	}

	// Load a single booking record by ID
//	public Map loadBookingData(String bookingId) throws Exception {
//		if (isDeveloper) return DbUtils.loadOne(tables.get("BookingData"), bookingId);
//		return DbUtils.loadOne(tables.get("BookingData"), bookingId, "organization_id = ?", loggedInOrgId);
//	}
	public Map loadBookingData(String bookingId) throws Exception {
		Map booking;
		if (isDeveloper) {
			booking = DbUtils.loadOne(tables.get("BookingData"), bookingId);
		} else {
			booking = DbUtils.loadOne(tables.get("BookingData"), bookingId, "organization_id = ?", loggedInOrgId);
		}

		if (booking !=null) {
			String rtSql = "SELECT * FROM BookingRoomType WHERE booking_id = ?";
			List<Map> roomTypes = DbUtils.loadManySql(tables.get("BookingRoomType"), rtSql, bookingId);
			booking.put("roomTypes", roomTypes);
		}

		return booking;
	}

	// Messages sections
	public List<Map> loadMessages() throws Exception {
		try (Connection conn = FewoServer.pds.getConnection()) {
			List<Map> rows = new ArrayList();
			String sql = "SELECT * FROM Messages";
			PreparedStatement ps = conn.prepareStatement(sql);
			try (ResultSet rs = ps.executeQuery()) {
				rs.beforeFirst();
				while (rs.next()) {
					Map<String,Object> row = new LinkedHashMap();
					row.put("id",rs.getString("id"));
					row.put("name",rs.getString("name"));
					row.put("email",rs.getString("email"));
					row.put("message",rs.getString("message"));
					row.put("dateSent",rs.getTimestamp("dateSent"));
					rows.add(row);
				}
			}
			return rows;
		}
	}

	public void saveMessage(String id, String name, String email, String message, Date dateSent)
			throws Exception {
		try (Connection conn = FewoServer.pds.getConnection()) {
			String sql =
					"INSERT INTO Messages (id, name, email, message, dateSent) VALUES (?, ?, ?, ?, ?)";
			try (PreparedStatement ps = conn.prepareStatement(sql)) {
				ps.setString(1,id);
				ps.setString(2,name);
				ps.setString(3,email);
				ps.setString(4,message);
				ps.setTimestamp(5,new java.sql.Timestamp(dateSent.getTime()));
				ps.executeUpdate();
			}
		}
	}

	////////////////////////////////////////////////////////////////////////////////////////////////

	public static byte[] convert(InputStream inputStream) throws IOException {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		int nRead;
		byte[] data = new byte[1024];

		while ((nRead = inputStream.read(data,0,data.length)) != -1) {
			buffer.write(data,0,nRead);
		}

		buffer.flush();
		return buffer.toByteArray();
	}

	public void savePhotosOld(FormData formData) throws Exception {
		String site_id = extractStringValueFromForm(formData,"id");
		expect(site_id,"Site ID (Foreign Key)");

		int count = 0;
		Deque<FormValue> values = formData.get("photos");
		for (FormValue value : values) {
			if (value.isFileItem()) {
				count++;
				FileItem phItem = value.getFileItem();
				if (phItem.getFileSize() == 0) {
					continue;
				}
				File assets = new File("./assets");
				if (!assets.exists()) {
					assets.mkdir();
				}
				String photoUniqueName = System.currentTimeMillis() + "_" + count;
				String mimeType = value.getHeaders().getFirst(Headers.CONTENT_TYPE);
				String origPhotoName = value.getFileName();
				String extension = null;
				int i = origPhotoName.lastIndexOf('.');
				if (i > 0) {
					extension = origPhotoName.substring(i + 1);
				}
				File photo = new File(assets,photoUniqueName + "." + extension);
				byte[] photoBytes = convert(phItem.getInputStream());
				FileUtils.writeBytes(photoBytes,photo);

				try (Connection conn = FewoServer.pds.getConnection()) {
					;
					String sql =
							"INSERT INTO Site_Photos SET id=?, name=?, origName=?, image_data=?, "
									+ "extension=?, mimeType=?, site_id=?";
					PreparedStatement ps = conn.prepareStatement(sql);
					ps.setString(1,UUID.randomUUID().toString());
					ps.setString(2,photoUniqueName);
					ps.setString(3,origPhotoName);
					ps.setBlob(4,phItem.getInputStream());
					ps.setString(5,extension);
					ps.setString(6,mimeType);
					ps.setString(7,site_id);

					ps.executeUpdate();

					ps.close();
					conn.close();
				}
			}
		}
	}

	public List loadPhotos() throws Exception {
		try (Connection conn = FewoServer.pds.getConnection()) {
			List<Map> rows = new ArrayList<>();
			String sql = "SELECT * FROM Site_Photos";
			PreparedStatement ps = conn.prepareStatement(sql);
			try (ResultSet rs = ps.executeQuery()) {
				rs.beforeFirst();
				while (rs.next()) {
					Map<String,Object> row = new LinkedHashMap<>();
					row.put("id",rs.getString("id"));
					String photoName = rs.getString("name");
					row.put("name",photoName);
					// Distinguish when the reference photo already exists in file system or not
					if (!(new File("./assets/" + photoName).exists())) {
						Blob b = rs.getBlob("image_data");
						byte[] bb = b.getBytes(1,(int)b.length()); // blob bytes (bb)
						String data = Base64.getEncoder().encodeToString(bb);
						row.put("data",data);
					}
					row.put("extension",rs.getString("extension"));
					row.put("mimeType",rs.getString("mimeType"));
					row.put("site_id",rs.getString("site_id"));
					rows.add(row);
				}
				rs.close();
			}
			return rows;
		}
	}

//	public void updateSite(FormData formData) throws Exception {
//		String id = extractStringValueFromForm(formData,"id");
//		expect(id,"Site ID");
//
//		try (Connection conn = FewoServer.pds.getConnection()) {
//			String sql =
//					"UPDATE Sites SET siteType=?, siteTitle=?, street=?, city=?, "
//							+ "zipCode=?, width=?, length=?, totalSquareFootage=?, numberOfRooms=?, roomTypes=?, "
//							+ "numberOfBeds=?, bedTypes=?, capacity=?, parkingAvailable=?, swimmingPoolAvailable=?, "
//							+ "petsAllowed=?, petDetails=?, smokingAllowed=?, smokingDetails=?, description=?, fromDate=?, toDate=?, pricing=? "
//							+ "WHERE id=?";
//			PreparedStatement ps = conn.prepareStatement(sql);
//
//			ps.setString(1,formData.getFirst("siteType").getValue());
//			ps.setString(2,formData.getFirst("siteTitle").getValue());
//			ps.setString(3,formData.getFirst("street").getValue());
//			ps.setString(4,formData.getFirst("city").getValue());
//			ps.setString(5,formData.getFirst("zipCode").getValue());
//			ps.setDouble(6,Double.parseDouble(formData.getFirst("width").getValue()));
//			String l = extractStringValueFromForm(formData,"length");
//			if (l == null) {
//				ps.setNull(7,Types.DOUBLE);
//			} else {
//				ps.setDouble(7,Double.parseDouble(l));
//			}
//			String tsf = extractStringValueFromForm(formData,"totalSquareFootage");
//			if (tsf == null) {
//				ps.setNull(8,Types.DOUBLE);
//			} else {
//				ps.setDouble(8,Double.parseDouble(tsf));
//			}
//			ps.setInt(9,Integer.parseInt(formData.getFirst("numberOfRooms").getValue()));
//			ps.setString(10,formData.getFirst("roomTypes").getValue());
//			ps.setInt(11,Integer.parseInt(formData.getFirst("numberOfBeds").getValue()));
//			ps.setString(12,formData.getFirst("bedTypes").getValue());
//			ps.setInt(13,Integer.parseInt(formData.getFirst("capacity").getValue()));
//
//			String pa = extractStringValueFromForm(formData,"parkingAvailable");
//			pa = pa == null ? "false" : pa;
//			ps.setBoolean(14,Boolean.valueOf(pa));
//
//			String spa = extractStringValueFromForm(formData,"swimmingPoolAvailable");
//			spa = spa == null ? "false" : spa;
//			ps.setBoolean(15,Boolean.valueOf(spa));
//
//			String peta = extractStringValueFromForm(formData,"petsAllowed");
//			peta = peta == null ? "false" : peta;
//			ps.setBoolean(16,Boolean.valueOf(peta));
//
//			String pd = extractStringValueFromForm(formData,"petDetails");
//			if (pd == null) {
//				ps.setNull(17,Types.VARCHAR);
//			} else {
//				ps.setString(17,pd);
//			}
//
//			String sa = extractStringValueFromForm(formData,"smokingAllowed");
//			sa = sa == null ? "false" : sa;
//			ps.setBoolean(18,Boolean.valueOf(sa));
//
//			String sd = extractStringValueFromForm(formData,"smokingDetails");
//			if (sd == null) {
//				ps.setNull(19,Types.VARCHAR);
//			} else {
//				ps.setString(19,sd);
//			}
//
//			ps.setString(20,formData.getFirst("description").getValue());
//			ps.setDate(21,Date.valueOf(formData.getFirst("fromDate").getValue()));
//			ps.setDate(22,Date.valueOf(formData.getFirst("toDate").getValue()));
//			ps.setDouble(23,Double.parseDouble(formData.getFirst("pricing").getValue()));
//			ps.setString(24,formData.getFirst("id").getValue());
//
//			ps.executeUpdate();
//
//			ps.close();
//			conn.close();
//		}
//	}

	private String extractStringValueFromForm(FormData fd, String key) {
		FormValue fv = fd.getFirst(key);
		String v = fv != null ? fv.getValue() : null;
		v = v != null ? (v.isEmpty() || "null".equals(v)) ? null : v : null;
		return v;
	}

	public void deletePhoto(List<String> ids) throws Exception {
		try (Connection conn = FewoServer.pds.getConnection()) {
			int l = ids.size();
			if (l > 0) {
				String placeholders = ids.stream().map(v -> "?").collect(Collectors.joining(", "));
				String sql = "DELETE FROM Site_Photos WHERE id IN (" + placeholders + ")";
				PreparedStatement ps = conn.prepareStatement(sql);
				for (int i = 0; i < l; i++) {
					ps.setString(i + 1,ids.get(i));
				}
				ps.executeUpdate();

				ps.close();
				conn.close();
			}
		}
	}

	public void deleteSite(List<String> ids) throws Exception {
		try (Connection conn = FewoServer.pds.getConnection()) {
			int l = ids.size();
			if (l > 0) {
				String placeholders = ids.stream().map(v -> "?").collect(Collectors.joining(", "));
				// 1. First delete related photos
				// 1.1. Delete from File System
				String sql = "SELECT * FROM Site_Photos WHERE site_id IN ("
						+ placeholders + ")";
				PreparedStatement ps = conn.prepareStatement(sql);
				for (int i = 0; i < l; i++) {
					ps.setString(i + 1,ids.get(i));
				}
				ResultSet rs = ps.executeQuery(); // Photo records
				while (rs.next()) {
					String name = rs.getString("name");
					String extension = rs.getString("extension");
					File file = new File("./assets/" + name + "." + extension);
					if (file.exists()) file.delete();
				}
				rs.close();
				// 1.2. Delete from DB
				sql = "DELETE FROM Site_Photos WHERE site_id IN (" + placeholders
						+ ")";
				ps = conn.prepareStatement(sql);
				for (int i = 0; i < l; i++) {
					ps.setString(i + 1,ids.get(i));
				}
				ps.executeUpdate();
				ps.close();
				// 2. Delete the selected sites
				sql = "DELETE FROM Sites WHERE id IN (" + placeholders + ")";
				ps = conn.prepareStatement(sql);
				for (int i = 0; i < l; i++) {
					ps.setString(i + 1,ids.get(i));
				}
				ps.executeUpdate();

				ps.close();
				conn.close();
			}
		}
	}

//	public void createSite(FormData formData) throws Exception {
//		String id = extractStringValueFromForm(formData,"id");
//		expect(id,"Site ID");
//
//		try (Connection conn = FewoServer.pds.getConnection()) {
//			String sql =
//					"INSERT INTO Sites SET id=?, siteType=?, siteTitle=?, street=?, city=?, "
//							+ "zipCode=?, width=?, length=?, totalSquareFootage=?, numberOfRooms=?, roomTypes=?, "
//							+ "numberOfBeds=?, bedTypes=?, capacity=?, parkingAvailable=?, swimmingPoolAvailable=?, "
//							+ "petsAllowed=?, petDetails=?, smokingAllowed=?, smokingDetails=?, description=?, fromDate=?, toDate=?, pricing=?";
//			PreparedStatement ps = conn.prepareStatement(sql);
//
//			ps.setString(1,formData.getFirst("id").getValue());
//			ps.setString(2,formData.getFirst("siteType").getValue());
//			ps.setString(3,formData.getFirst("siteTitle").getValue());
//			ps.setString(4,formData.getFirst("street").getValue());
//			ps.setString(5,formData.getFirst("city").getValue());
//			ps.setString(6,formData.getFirst("zipCode").getValue());
//			ps.setDouble(7,Double.parseDouble(formData.getFirst("width").getValue()));
//			String l = extractStringValueFromForm(formData,"length");
//			if (l == null) {
//				ps.setNull(8,Types.DOUBLE);
//			} else {
//				ps.setDouble(8,Double.parseDouble(l));
//			}
//			String tsf = extractStringValueFromForm(formData,"totalSquareFootage");
//			if (tsf == null) {
//				ps.setNull(9,Types.DOUBLE);
//			} else {
//				ps.setDouble(9,Double.parseDouble(tsf));
//			}
//			ps.setInt(10,Integer.parseInt(formData.getFirst("numberOfRooms").getValue()));
//			ps.setString(11,formData.getFirst("roomTypes").getValue());
//			ps.setInt(12,Integer.parseInt(formData.getFirst("numberOfBeds").getValue()));
//			ps.setString(13,formData.getFirst("bedTypes").getValue());
//			ps.setInt(14,Integer.parseInt(formData.getFirst("capacity").getValue()));
//
//			String pa = extractStringValueFromForm(formData,"parkingAvailable");
//			pa = pa == null ? "false" : pa;
//			ps.setBoolean(15,Boolean.valueOf(pa));
//
//			String spa = extractStringValueFromForm(formData,"swimmingPoolAvailable");
//			spa = spa == null ? "false" : spa;
//			ps.setBoolean(16,Boolean.valueOf(spa));
//
//			String peta = extractStringValueFromForm(formData,"petsAllowed");
//			peta = peta == null ? "false" : peta;
//			ps.setBoolean(17,Boolean.valueOf(peta));
//
//			String pd = extractStringValueFromForm(formData,"petDetails");
//			if (pd == null) {
//				ps.setNull(18,Types.VARCHAR);
//			} else {
//				ps.setString(18,pd);
//			}
//
//			String sa = extractStringValueFromForm(formData,"smokingAllowed");
//			sa = sa == null ? "false" : sa;
//			ps.setBoolean(19,Boolean.valueOf(sa));
//
//			String sd = extractStringValueFromForm(formData,"smokingDetails");
//			if (sd == null) {
//				ps.setNull(20,Types.VARCHAR);
//			} else {
//				ps.setString(20,sd);
//			}
//
//			ps.setString(21,formData.getFirst("description").getValue());
//			ps.setDate(22,Date.valueOf(formData.getFirst("fromDate").getValue()));
//			ps.setDate(23,Date.valueOf(formData.getFirst("toDate").getValue()));
//			ps.setDouble(24,Double.parseDouble(formData.getFirst("pricing").getValue()));
//
//			ps.executeUpdate();
//
//			ps.close();
//			conn.close();
//		}
//	}

	static void expect(String value, String name) {
		if (value == null || value.trim().isEmpty())
			throw new IllegalArgumentException(name + " expected");
	}
}
