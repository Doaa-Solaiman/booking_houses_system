package de.scheller.fewo.util;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import de.scheller.fsm.FSM;
import de.scheller.platform.apis.SessionContext.Session;

public class LocalAccountContext
{
	private static final Logger logger = LoggerFactory.getLogger(LocalAccountContext.class);

	public static Supplier<LocalAccountContext> factory = () -> new LocalAccountContext();
	public static LocalAccountContext create(Object context, String sessionId, Supplier<LocalAccountContext> factory) {
		return factory.get().init(context,sessionId);
	}
	public static LocalAccountContext getOrCreate(Object context, String sessionId) {
		return getOrCreate(context,sessionId,factory);
	}
	public static LocalAccountContext getOrCreate(Object context, String sessionId, Supplier<LocalAccountContext> factory) {
		LocalAccountContext ac = get(sessionId);
		if (ac==null) ac = create(context,sessionId,factory);
		else ac.context = context;
		return ac;
	}
	public static LocalAccountContext get(String sessionId) {
		WeakReference<LocalAccountContext> ref = acs.get(sessionId);
		return ref!=null ? ref.get() : null;
	}
	public static boolean isValid(String subSessionId, String sessionId) {
		if (subSessionId==null)
			return false;
		if (get(subSessionId)==null)
			return false;
		if (sessions.get(subSessionId)==null)
			return false;
		return Objects.equals(sessions.get(subSessionId).parent,sessionId);
	}

	static final Map<String,WeakReference<LocalAccountContext>> acs = new ConcurrentHashMap();
	static final Map<String,Session> sessions = new ConcurrentHashMap();

	protected Object context;
	protected String sessionId;
	protected String userId;
	private boolean orgOverride;
	private boolean isOrgAdmin;
	private boolean isDeveloper;

	private LocalAccountContext init(Object context, String sessionId) {
		this.context = context;
		this.sessionId = sessionId;
		if (sessionId!=null)
			acs.put(sessionId,new WeakReference(this));

		FSM.noodle(this,context);
		init();

		return this;
	}

	protected void init() {}
	protected void serviceAvailable() {}
	protected void contextChanged() {}

	public String getSessionId() {
		return sessionId;
	}

	public String getUserId() {
		return userId;
	}

	public boolean isOrgAdmin() {
		return isOrgAdmin;
	}

	public boolean isDeveloper() {
		return isDeveloper;
	}

	public void updateLogContext() {
		MDC.put("session",sessionId);
		if (getUserId()!=null)
			MDC.put("user",getUserId());
		if (isDeveloper)
			MDC.put("role","dev");
		else if (isOrgAdmin)
			MDC.put("role","admin");
		else MDC.put("role","user");
	}
}
