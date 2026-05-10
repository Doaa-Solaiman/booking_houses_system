package de.scheller.platform.persist;

import java.sql.Connection;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.persistence.FlushModeType;
import javax.persistence.LockModeType;
import javax.persistence.NoResultException;
import javax.persistence.NonUniqueResultException;
import javax.persistence.Parameter;
import javax.persistence.Query;
import javax.persistence.TemporalType;
import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import de.scheller.common.HasId;
import de.scheller.platform.common.Logging;
import de.scheller.platform.persist.DatabaseCache.RowsIterator;
import de.scheller.platform.persist.util.JdbcHelper;

/**
 * @author kandzia
 */
public class QueryImpl implements Query
{
	private static final Logger logger = LoggerFactory.getLogger(
			QueryImpl.class.getPackage().getName()+"");

	DatabaseImpl db;
	DatabaseOrm dbo;
	DataSource ds;

	String jpaql;
	Parser.Statement statement;
	String sql;
	Class type;
	Class rtype;
	Map<String,Class> fieldTypes = new HashMap();

	int maxResult = -1;
	int startPosition;
	Map<String,Object> hints;
//	ResultSetMetaData rm;
	ParameterMetaData pm;
	List<String> paramNames;
	Object[] paramValues;
	TemporalType[] paramTT;

	public static final Marker QUERY = MarkerFactory.getMarker("QUERY");
	public static final Marker JPQL = MarkerFactory.getMarker("JPQL");
	public static final Marker SQL = MarkerFactory.getMarker("SQL");

	public QueryImpl(DatabaseImpl db, DatabaseOrm dbo, DataSource ds, String jpaql) {
		this.db = db;
		this.dbo = dbo;
		this.ds = ds;

		List<String> params = new ArrayList();
		this.jpaql = jpaql;
//		logger.debug(Logging.REF(jpaql,JPQL,QUERY,Logging.START),"creating query {REF}");
		this.statement = db.parseQuery(jpaql);
		if (this.statement instanceof Parser.Statement.Select)
			this.maxResult = ((Parser.Statement.Select)this.statement).limit;
		this.sql = db.generateSql(statement,fieldTypes,params);
//		logger.debug(Logging.REF(sql,fieldTypes,SQL,JPQL,QUERY,Logging.STOP),"created query {REF}");
		this.type = fieldTypes.get("_entityType");
		this.rtype = fieldTypes.get("_resultType");
		this.fieldTypes.putAll(dbo.types);
		try (Connection co = ds.getConnection()) {
			try (PreparedStatement ps = co.prepareStatement(sql)) {
//				this.rm = ps.getMetaData();
				this.pm = ps.getParameterMetaData();
				int pc = pm.getParameterCount();
				paramNames = new ArrayList(params);
				paramValues = new Object[pc];
				paramTT = new TemporalType[pc];
			}
		} catch (SQLException ex) {
			throw new RuntimeException(ex);
		}
	}

	public String getQueryString() {
		if (maxResult>=0 || startPosition>0) {
			statement = db.parseQuery(jpaql);
			Parser.Statement.Select select = (Parser.Statement.Select)statement;
			statement = new Parser.Statement.Select(
					select.distinct,select.expr,select.from,
					select.where,select.group,select.having,
					select.order,maxResult,startPosition);
			statement.meta.putAll(select.meta);
			sql = db.generateSql(statement);
		}
		return sql;
	}

	public Class getEntityType() {
		return type;
	}

	public Class getResultType() {
		return rtype;
	}

	public Query setMaxResults(int maxResult) {
		this.maxResult = maxResult;
		return this;
	}

	public int getMaxResults() {
		return maxResult;
	}

	public Query setFirstResult(int startPosition) {
		this.startPosition = startPosition;
		return this;
	}

	public int getFirstResult() {
		return startPosition;
	}

	public Query setHint(String hintName, Object value) {
		if (hints==null)
			hints = new HashMap();
		hints.put(hintName,value);
		return this;
	}

	public Map<String,Object> getHints() {
		return hints!=null ? hints : Collections.EMPTY_MAP;
	}

	public <T> Query setParameter(Parameter<T> param, T value) {
		return setParameter(param.getPosition(),value);
	}

	public Query setParameter(Parameter<Calendar> param, Calendar value, TemporalType temporalType) {
		return setParameter(param.getPosition(),value,temporalType);
	}

	public Query setParameter(Parameter<Date> param, Date value, TemporalType temporalType) {
		return setParameter(param.getPosition(),value,temporalType);
	}

	public Query setParameter(String name, Object value) {
		for (int i=0; i<paramNames.size(); i++)
			if (name.equals(paramNames.get(i)))
				setParameter(i,value);
		return this;
	}

	public Query setParameter(String name, Calendar value, TemporalType temporalType) {
		for (int i=0; i<paramNames.size(); i++)
			if (name.equals(paramNames.get(i)))
				setParameter(i,value,temporalType);
		return this;
	}

	public Query setParameter(String name, Date value, TemporalType temporalType) {
		for (int i=0; i<paramNames.size(); i++)
			if (name.equals(paramNames.get(i)))
				setParameter(i,value,temporalType);
		return this;
	}

	public Query setParameter(int position, Object value) {
		paramValues[position] = value;
		paramTT[position] = TemporalType.DATE;
		return this;
	}

	public Query setParameter(int position, Calendar value, TemporalType temporalType) {
		if (temporalType==null)
			throw new IllegalArgumentException("temporalType is required");
		paramValues[position] = value;
		paramTT[position] = temporalType;
		return this;
	}

	public Query setParameter(int position, Date value, TemporalType temporalType) {
		if (temporalType==null)
			throw new IllegalArgumentException("temporalType is required");
		paramValues[position] = value;
		paramTT[position] = temporalType;
		return this;
	}

	public Set<Parameter<?>> getParameters() {
		Set<Parameter<?>> params = new LinkedHashSet();
		for (int i=0; i<paramValues.length; i++)
			params.add(getParameter(i));
		return params;
	}

	public Parameter<?> getParameter(String name) {
		return getParameter(name);
	}

	public <T> Parameter<T> getParameter(String name, Class<T> type) {
		return getParameter(paramNames.indexOf(name),type);
	}

	public Parameter<?> getParameter(int position) {
		return getParameter(position);
	}

	public <T> Parameter<T> getParameter(int position, Class<T> type) {
		try {
			ParameterImpl p = new ParameterImpl<T>();
			p.name = paramNames.get(position);
			p.position = position;
			p.parameterType = JdbcHelper.toJavaType(pm.getParameterType(position));
			return p;
		} catch (SQLException ex) {
			throw new RuntimeException(ex);
		}
	}

	public boolean isBound(Parameter<?> param) {
		return paramTT[param.getPosition()]!=null;
	}

	public <T> T getParameterValue(Parameter<T> param) {
		return (T)paramValues[param.getPosition()];
	}

	public Object getParameterValue(String name) {
		return paramValues[paramNames.indexOf(name)];
	}

	public Object getParameterValue(int position) {
		return paramValues[position];
	}

	private static class ParameterImpl<T> implements Parameter<T> {
		String name;
		Integer position;
		Class parameterType;

		public String getName() { return name; }
		public Integer getPosition() { return position; }
		public Class<T> getParameterType() { return parameterType; }
	}

	public PreparedStatement getStatement(Connection conn, boolean stream) throws SQLException {
		String sql = getQueryString();
		int collectionIndex = 0;
		for (int i=0; i<paramValues.length; i++)
			if (paramValues[i] instanceof Collection) {
				Collection c = (Collection)paramValues[i];
//				StringBuilder sb = new StringBuilder( // create comma-delimited list
//						new String(new char[c.size()]).replace("\0","?,"));
//				sb.setLength(Math.max(sb.length()-1,0)); // cut trailing comma
//				if (sb.length()>1) sql = sql.replaceFirst("\\(\\?\\)","("+sb+")");
				if (c.size()>1) {
					String s = (String)c.stream().map(o -> "?").collect(Collectors.joining(",","(",")"));
					sql = replaceNthCollection(sql,s,collectionIndex);
				} else collectionIndex++;
			}
		PreparedStatement ps;
		if (stream) {
			// http://javaquirks.blogspot.de/2007/12/mysql-streaming-result-set.html
			// https://dev.mysql.com/doc/connector-j/5.1/en/connector-j-reference-implementation-notes.html
			ps = conn.prepareStatement(sql,
					ResultSet.TYPE_FORWARD_ONLY,ResultSet.CONCUR_READ_ONLY);
			ps.setFetchSize(Integer.MIN_VALUE);
		} else {
			ps = conn.prepareStatement(sql);
		}
		for (int p=1,i=0; i<paramValues.length; i++)
			if (paramValues[i] instanceof Collection) {
				for (Object o : (Collection)paramValues[i])
					ps.setObject(p++,transform(o));
			} else ps.setObject(p++,transform(paramValues[i]));
//		int pc = psOrig.getParameterMetaData().getParameterCount();
//		for (int i=0; i<pc; i++)
//			ps.setObject(i+1,queryOrig.getParameterValue(i));
		return ps;
	}

	private static Pattern collectionPattern = Pattern.compile("\\(\\?\\)");
	private static String replaceNthCollection(String sql, String placeholders, int ocurrence) {
		Matcher m = collectionPattern.matcher(sql);
		StringBuffer sb = new StringBuffer(sql);
		int i = 0;
		while (m.find()) {
			if (i++ == ocurrence) { sb.replace(m.start(), m.end(), placeholders); break; }
		}
		return sb.toString();
	}

	static Object transform(Object value) {
		if (value instanceof HasId) // in JPA world this would use the getter with @Id
			return ((HasId)value).getId();
		if (value instanceof Enum)
			return ((Enum)value).name();
		return value;
	}

	public List getResultList() {
//		logger.debug(Logging.REF(jpaql,JPQL,QUERY,Logging.START),"getResultList()");
		try (Connection co = ds.getConnection()) {
			try (PreparedStatement ps = getStatement(co,false)) {
				try (ResultSet rs = ps.executeQuery()) {
					try (RowsIterator it = new RowsIterator(rs,type,rtype,fieldTypes,dbo)) {
						ArrayList result = new ArrayList();
						for (Object o : it)
							result.add(o);
						return db.load(result);
					}
				}
			}
		} catch (SQLException ex) {
			throw new RuntimeException(ex);
		} finally {
//			logger.debug(Logging.REF(jpaql,JPQL,QUERY,Logging.STOP),"getResultList() done");
		}
	}

	public Object getSingleResult() {
		logger.debug(Logging.REF(jpaql,JPQL,QUERY,Logging.START),"getSingleResult()");
		try (Connection co = ds.getConnection()) {
			try (PreparedStatement ps = getStatement(co,false)) {
				try (ResultSet rs = ps.executeQuery()) {
					try (RowsIterator it = new RowsIterator(rs,type,rtype,fieldTypes,dbo)) {
						if (!it.hasNext())
							throw new NoResultException();
						Object single = it.next();
						if (it.hasNext())
							throw new NonUniqueResultException();
						return db.load(single);
					}
				}
			}
		} catch (SQLException ex) {
			throw new RuntimeException(ex);
		} finally {
			logger.debug(Logging.REF(jpaql,JPQL,QUERY,Logging.STOP),"getSingleResult() done");
		}
	}

	public int executeUpdate() {
		logger.debug(Logging.REF(jpaql,JPQL,QUERY,Logging.START),"executeUpdate()");
		try (Connection co = ds.getConnection()) {
			try (PreparedStatement ps = getStatement(co,false)) {
				int affected = ps.executeUpdate();
				return affected;
			}
		} catch (SQLException ex) {
			throw new RuntimeException(ex);
		} finally {
			logger.debug(Logging.REF(jpaql,JPQL,QUERY,Logging.STOP),"executeUpdate() done");
		}
	}

	public Query setFlushMode(FlushModeType flushMode) {
		return null;
	}

	public FlushModeType getFlushMode() {
		return null;
	}

	public Query setLockMode(LockModeType lockMode) {
		return null;
	}

	public LockModeType getLockMode() {
		return null;
	}

	public <T> T unwrap(Class<T> cls) {
		try (Connection co = ds.getConnection()) {
			try (PreparedStatement ps = getStatement(co,false)) {
				return ps.unwrap(cls);
			}
		} catch (SQLException ex) {
			throw new RuntimeException(ex);
		}
	}

	public static void close(Object... o) {
		if (o==null) return;
		for (int i=0; i<o.length; i++)
			close(o[i]);
	}
	public static void closeChain(Object o) {
		if (o==null) return;
		Object[] chain = new Object[3];
		int i = 0;
		if (o instanceof ResultSet) { chain[i++] = o;
			try { o = ((ResultSet)o).getStatement(); }
			catch (SQLException ex) { ex.printStackTrace(); }
		}
		if (o instanceof Statement) { chain[i++] = o;
			try { o = ((Statement)o).getConnection(); }
			catch (SQLException ex) { ex.printStackTrace(); }
		}
		if (o instanceof Connection) { chain[i++] = o; }
		close(chain);
	}
	public static void close(Object o) {
		if (o==null) return;
		if (o instanceof ResultSet) {
			try { ((ResultSet)o).close(); }
			catch (SQLException ex) { ex.printStackTrace(); }
		}
		if (o instanceof Statement) {
			try { ((Statement)o).close(); }
			catch (SQLException ex) { ex.printStackTrace(); }
		}
		if (o instanceof Connection) {
			try { ((Connection)o).close(); }
			catch (SQLException ex) { ex.printStackTrace(); }
		}
	}
}
