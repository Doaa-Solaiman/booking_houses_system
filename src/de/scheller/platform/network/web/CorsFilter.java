package de.scheller.platform.network.web;

import java.util.logging.Level;
import java.util.logging.Logger;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderValues;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import io.undertow.util.StatusCodes;

public abstract class CorsFilter implements HttpHandler
{
	private static final Logger LOG = Logger.getLogger(CorsFilter.class.getName());
	static {
		LOG.setLevel(Level.OFF);
	}

	/**
	 * The main CORS header indicating if cross-origin access is allowed.
	 *
	 * <p>If it's value is equal to the requesting origin, cross-origin access from that origin is allowed.
	 * If it differs, cross-origin access is denied. "*" allows all resources, but is only valid for requests that
	 * do not include credentials (Authorization header, session cookie).</p>
	 *
	 * <p>This filter simply echoes the origin of the request if the request was allowed by the
	 * selected policy class, because this is valid under all circumstances.</p>
	 *
	 * @see https://developer.mozilla.org/en-US/docs/Web/HTTP/Access_control_CORS#Access-Control-Allow-Origin
	 */
	public static final String ACCESS_CONTROL_ALLOW_ORIGIN = "Access-Control-Allow-Origin";
	/**
	 * Indicates whether cross-origin access with credentials (Authorization header, cookies) is allowed.
	 * @see https://developer.mozilla.org/en-US/docs/Web/HTTP/Access_control_CORS#Access-Control-Allow-Credentials
	 */
	public static final String ACCESS_CONTROL_ALLOW_CREDENTIALS = "Access-Control-Allow-Credentials";
	/**
	 * Used in response to a preflight request to indicate which HTTP headers can be used when making the actual request.
	 * @see https://developer.mozilla.org/en-US/docs/Web/HTTP/Access_control_CORS#Access-Control-Allow-Headers
	 */
	public static final String ACCESS_CONTROL_ALLOW_HEADERS = "Access-Control-Allow-Headers";
	/**
	 * Used in response to a preflight request to indicate which HTTP methods can be used when making the actual request.
	 * @see https://developer.mozilla.org/en-US/docs/Web/HTTP/Access_control_CORS#Access-Control-Allow-Methods
	 */
	public static final String ACCESS_CONTROL_ALLOW_METHODS = "Access-Control-Allow-Methods";
	/**
	 * Lets a server whitelist headers that browsers are allowed to access.
	 *
	 * <p>Default headers allowed without needing to be exposed:
	 * Cache-Control, Content-Language, Content-Type, Expires, Last-Modified, Pragma</p>
	 *
	 * @see https://developer.mozilla.org/en-US/docs/Web/HTTP/Access_control_CORS#Access-Control-Expose-Headers
	 */
	public static final String ACCESS_CONTROL_EXPOSE_HEADERS = "Access-Control-Expose-Headers";
	/**
	 * The max age header determines how long browsers are allowed to cache the CORS responses.
	 * @see https://developer.mozilla.org/en-US/docs/Web/HTTP/Access_control_CORS#Access-Control-Max-Age
	 */
	public static final String ACCESS_CONTROL_MAX_AGE = "Access-Control-Max-Age";
	/** @see https://www.w3.org/TR/cors/#simple-response-header */
	public static final String SIMPLE_RESPONSE_HEADERS = "Cache-Control,Content-Language,Content-Type,Expires,Last-Modified,Pragma";

	public static final String DEFAULT_MAX_AGE = "864000"; // 10 days
	public static final String DEFAULT_ALLOW_METHODS = "DELETE,GET,HEAD,OPTIONS,PATCH,POST,PUT";
	public static final String DEFAULT_ALLOW_HEADERS = "Authorization,Content-Type,Link,X-Total-Count,Range";
	public static final String DEFAULT_EXPOSE_HEADERS = "Accept-Ranges,Content-Length,Content-Range,ETag,Link,Server,X-Total-Count";
	public static final String DEFAULT_ALLOW_CREDENTIALS = "true";

	private String maxAge;
	private String allowMethods;
	private String allowHeaders;
	private String exposeHeaders; // What headers should be set to what values?
	private String allowCredentials;

	private final HttpHandler next;

	public CorsFilter(HttpHandler next) {
		this.next = next;
	}

	public void setExposeHeaders(String value) {
		exposeHeaders = value;
		LOG.config("CorsFilter: exposeHeaders=" + getExposeHeaders());
	}
	public String getExposeHeaders() {
		return exposeHeaders!=null ? exposeHeaders : DEFAULT_EXPOSE_HEADERS;
	}

	public void setMaxAge(String value) {
		maxAge = value;
		LOG.config("CorsFilter: maxAge=" + getMaxAge());
	}
	public String getMaxAge() {
		return maxAge!=null ? maxAge : DEFAULT_MAX_AGE;
	}

	public void setAllowCredentials(String value) {
		allowCredentials = value;
		LOG.config("CorsFilter: allowCredentials=" + getAllowCredentials());
	}
	public String getAllowCredentials() {
		return allowCredentials!=null ? allowCredentials : DEFAULT_ALLOW_CREDENTIALS;
	}

	public void setAllowMethods(String value) {
		allowMethods = value;
		LOG.config("CorsFilter: allowMethods=" + getAllowMethods());
	}
	public String getAllowMethods() {
		return allowMethods!=null ? allowMethods : DEFAULT_ALLOW_METHODS;
	}

	public void setAllowHeaders(String value) {
		allowHeaders = value;
		LOG.config("CorsFilter: allowHeaders=" + getAllowHeaders());
	}
	public String getAllowHeaders() {
		return allowHeaders!=null ? allowHeaders : DEFAULT_ALLOW_HEADERS;
	}

	@Override
	public void handleRequest(HttpServerExchange http) throws Exception {
		if (http.isInIoThread()) {
			// This code is executed by one of the XNIO I/O threads.
			// It is very important NOT to run anything that could block the thread.
			http.dispatch(this);
			return;
		}
		// This code is executed by a worker thread. It's save to do blocking I/O here.
		boolean allowed = mayApplyCorsHeaders(http);
		if (LOG.isLoggable(Level.INFO))
			LOG.info("CorsFilter: CORS headers " + (allowed ? "" : "NOT ") + "added for origin " + origin(http));
		if (http.getRequestMethod().equals(Methods.OPTIONS)) {
			http.setStatusCode(StatusCodes.NO_CONTENT);
			return;
		}
		next.handleRequest(http);
	}

	public boolean mayApplyCorsHeaders(HttpServerExchange http) {
		String url = url(http);
		String origin = origin(http);
		if (origin!=null && isAllowed(origin,url)) {
			if (!hasHeader(http,ACCESS_CONTROL_ALLOW_ORIGIN))
				addHeader(http, ACCESS_CONTROL_ALLOW_ORIGIN, origin);
			if (!hasHeader(http,ACCESS_CONTROL_ALLOW_HEADERS))
				addHeader(http, ACCESS_CONTROL_ALLOW_HEADERS, getAllowHeaders());
			if (!hasHeader(http,ACCESS_CONTROL_ALLOW_CREDENTIALS))
				addHeader(http, ACCESS_CONTROL_ALLOW_CREDENTIALS, getAllowCredentials());
			if (!hasHeader(http,ACCESS_CONTROL_ALLOW_METHODS))
				addHeader(http, ACCESS_CONTROL_ALLOW_METHODS, getAllowMethods());
			if (!hasHeader(http,ACCESS_CONTROL_EXPOSE_HEADERS))
				addHeader(http, ACCESS_CONTROL_EXPOSE_HEADERS, getExposeHeaders());
			if (!hasHeader(http,ACCESS_CONTROL_MAX_AGE))
				addHeader(http, ACCESS_CONTROL_MAX_AGE, getMaxAge());
			return true;
		}
		return false;
	}

	protected String origin(HttpServerExchange http) {
		HeaderValues headers = http.getRequestHeaders().get("Origin");
		return headers==null ? null : headers.peekFirst();
	}
	protected String url(HttpServerExchange http) {
		return http.getRequestURL() +
				(http.getQueryString()==null || http.getQueryString().isEmpty() ? "" : "?" + http.getQueryString());
	}
	protected boolean hasHeader(HttpServerExchange http, String name) {
		return http.getResponseHeaders().get(name)!=null;
	}
	protected void addHeader(HttpServerExchange http, String name, String value) {
		http.getResponseHeaders().add(HttpString.tryFromString(name),value);
	}

	protected abstract boolean isAllowed(String origin, String url);
}
