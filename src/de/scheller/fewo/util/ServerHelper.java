package de.scheller.fewo.util;

import java.net.URI;
import java.util.Deque;
import java.util.Map;
import java.util.function.Supplier;

import io.undertow.server.HttpServerExchange;
import io.undertow.server.session.Session;
import io.undertow.server.session.SessionConfig;
import io.undertow.server.session.SessionManager;
import io.undertow.util.QueryParameterUtils;

/**
 * @author kandzia
 */
public class ServerHelper
{
	/**
	 * Ensure that a session exist. Session ID comes from URL query parameter ?session
	 * from request URL or referer URL (from HTTP header).
	 */
	public static Session mayCreateHttpSession(HttpServerExchange http) {
		Deque<String> sessionQP = http.getQueryParameters().get("session");
		String sessionId = sessionQP!=null ? sessionQP.peekFirst() : null;
		if (sessionId==null) {
			String referer = http.getRequestHeaders().getFirst("referer");
			String rquery = referer!=null ? URI.create(referer).getQuery() : null;
			if (rquery!=null) {
				String enc = QueryParameterUtils.getQueryParamEncoding(http);
				Map<String,Deque<String>> qp = QueryParameterUtils.parseQueryString(rquery,enc);
				sessionQP = qp.get("session");
				sessionId = sessionQP!=null ? sessionQP.peekFirst() : null;
			}
		}
		SessionManager sm = http.getAttachment(SessionManager.ATTACHMENT_KEY);
		SessionConfig sc = http.getAttachment(SessionConfig.ATTACHMENT_KEY);
		Session session = sm.getSession(http,sc);
		if (session==null)
			session = sm.createSession(http,sc);
		if (sessionId==null)
			sessionId = session.getId();
		session.setAttribute("id",sessionId); // given or generated session ID
		return session;
	}

	public static LocalAccountContext getLocalAccountContext(Session s, Object context) {
		return getLocalAccountContext(s,context,LocalAccountContext.factory);
	}
	public static LocalAccountContext getLocalAccountContext(Session s, Object context, Supplier<LocalAccountContext> factory) {
		String sessionId = (String)s.getAttribute("id"); // given or generated session ID
		if (sessionId!=null) {
			LocalAccountContext ac = LocalAccountContext.getOrCreate(context,sessionId,factory);
			s.setAttribute("ac",ac);
			return ac;
		}
		return null;
	}
	public static LocalAccountContext getLocalAccountContext(Session s, String sessionId, Object context, Supplier<LocalAccountContext> factory) {
		if (sessionId!=null) {
			LocalAccountContext ac = LocalAccountContext.getOrCreate(context,sessionId,factory);
			s.setAttribute("ac",ac);
			return ac;
		}
		return null;
	}
}
