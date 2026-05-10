package de.scheller.platform.common;

import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author kandzia
 */
public class Database
{
	private final String uri;
	private final String username;
	private final String password;
	private final Properties connProps;

	public Database(String uri, String username, String password) {
		this(uri,username,password,null);
	}

	public Database(String uri, String username, String password, Properties connProps) {
		this.uri = !uri.startsWith("jdbc:") ? "jdbc:" + uri : uri;
		this.username = username;
		this.password = password;
		this.connProps = connProps;
	}

	public String getJdbcUrl() {
		return uri;
	}

	public URI getJdbcUri() {
		String u = uri.startsWith("jdbc:") ? uri.substring("jdbc:".length()) : uri;
		try {
			return new URI(u);
		} catch (URISyntaxException ex) {
			return null;
		}
	}

	public Set<String> getTableNames() throws SQLException {
		String catalog = getJdbcUri().getPath();
		if (catalog.startsWith("/")) catalog = catalog.substring(1);
		Connection c = getConnection("metadata");
		ResultSet rs = c.getMetaData().getTables(catalog,null,null,null);
		RowIterator it = new RowIterator(rs);
		Set<String> tables = new LinkedHashSet();
		while (it.hasNext()) tables.add(it.next().get("TABLE_NAME"));
		c.close();
		return tables;
	}

	public Table getTable(String name) throws SQLException {
		return new Table(this,name,false);
	}

	public Table getTable(String name, boolean readOnly) throws SQLException {
		return new Table(this,name,readOnly);
	}

	PreparedStatement ps(String ckey, String sql, Object... params) throws SQLException {
		Connection c = getConnection(ckey);
		PreparedStatement ps = c.prepareStatement(sql);
		if (params!=null)
			for (int i=0; i<params.length; i++)
				ps.setObject(i+1,params[i]);
		return ps;
	}

	public int execute(String sql, Object... params) throws SQLException {
		PreparedStatement ps = ps("execute",sql,params);
		try {
			if (params!=null)
				for (int i=0; i<params.length; i++)
					ps.setObject(i+1,params[i]);
			ps.execute();
			return ps.getUpdateCount();
		} finally {
			ps.getConnection().close();
		}
	}

	public RowSet query(String sql, Object... params) throws SQLException {
		return query("query",sql,params);
	}

	RowSet query(String ckey, String sql, Object... params) throws SQLException {
		PreparedStatement ps = ps(ckey,sql,params);
		ps.setFetchSize(512);
		ResultSet rs = ps.executeQuery();
		try {
			RowSet rows = new RowSet();
			for (Row r : new RowIterator(rs))
				rows.add(r);
			return rows;
		} catch (RuntimeException ex) {
			if (ex.getCause() instanceof SQLException)
				throw (SQLException)ex.getCause();
			throw ex;
		} finally {
			ps.getConnection().close();
		}
	}

	public RowIterator iterate(String sql, Object... params) throws SQLException {
		return iterate("iterate",sql,params);
	}

	RowIterator iterate(String ckey, String sql, Object... params) throws SQLException {
		PreparedStatement ps = ps(ckey,sql,params);
		try {
			ps.setFetchSize(Integer.MIN_VALUE);
		} catch (Exception ex) {} // not every db supports this
		ResultSet rs = ps.executeQuery();
		return new RowIterator(rs);
	}

	public static class RowIterator implements Iterator<Row>, Iterable<Row> {
		private final ResultSet rs;
		private final ResultSetMetaData rsmd;
		private final int cols;
		private boolean doNext = true;
		private boolean next;

		public RowIterator(ResultSet rs) throws SQLException {
			this.rs = rs;
			this.rsmd = rs.getMetaData();
			this.cols = rsmd.getColumnCount();
		}

		public ResultSet getResultSet() {
			return rs;
		}

		public ResultSetMetaData getMetaData() {
			return rsmd;
		}

		public boolean hasNext() {
			try {
				if (doNext) {
					next = rs.next(); // moves the cursor
					if (!next)
						rs.close();
				}
				doNext = false;
				return next;
			} catch (SQLException ex) {
				throw new RuntimeException(ex);
			}
		}

		public Row next() {
			if (doNext) hasNext();
			if (!next)
				throw new NoSuchElementException();
			Row row = new Row();
			for (int i=1; i<=cols; i++) {
				try {
					String name = rsmd.getColumnLabel(i);
					Object value = rs.getObject(i);
					row.pput(name,value);
				} catch (SQLException ex) {
					throw new RuntimeException(ex);
				}
			}
			doNext = true;
			return row;
		}

		public void remove() {
			try {
				rs.deleteRow();
			} catch (SQLException ex) {
				throw new RuntimeException(ex);
			}
		}

		public Iterator<Row> iterator() {
			return this;
		}
	}

	public RowFetch fetch(String sql, Object... params) throws SQLException {
		return fetch("fetch",sql,params);
	}

	RowFetch fetch(String ckey, String sql, Object... params) throws SQLException {
		return new RowFetch(ckey,sql,params);
	}

	public class RowFetch {
		private final String ckey;
		private final String sql;
		private final Object[] params;

		public RowFetch(String ckey, String sql, Object... params) throws SQLException {
			this.ckey = ckey;
			this.sql = sql;
			this.params = params;
		}

		public List<Row> fetch(int from, int to) {
			try {
				int limit = to-from;
				PreparedStatement ps = ps(ckey,sql+" limit "+from+","+limit,params);
				ps.setMaxRows(limit);
				System.out.println(ps);
				ResultSet rs = ps.executeQuery();
				RowSet rows = new RowSet();
				for (Row r : new RowIterator(rs))
					rows.add(r);
				return rows;
			} catch (SQLException ex) {
				throw new RuntimeException(ex);
			}
		}
	}

	////////////////////////////////////////////////////////////////////////////
	// connections

	private final Map<String,Connection> connections = new HashMap();

	public Connection getConnection() throws SQLException {
		return getConnection(null);
	}

	public Connection getConnection(URL driverUrl, String driver) throws SQLException {
		if (driverUrl==null)
			return getConnection((String)null);

		try {
			Properties info = connProps!=null ? connProps : new Properties();
			if (username!=null) info.put("user",username);
			if (password!=null) info.put("password",password);

			URL[] urls = new URL[] { driverUrl };
			URLClassLoader callerCL = new URLClassLoader(urls);
			Class<Driver> d = (Class<Driver>)callerCL.loadClass(driver);
			DriverManager.registerDriver(d.newInstance());
			Method m = DriverManager.class.getDeclaredMethod("getConnection",
					new Class[] { String.class, Properties.class, ClassLoader.class });
			m.setAccessible(true);
			return (Connection)m.invoke(null,new Object[] { uri, info, callerCL });
		} catch (Exception ex) {
			ex.printStackTrace();
			return null;
		}
	}

	Connection getConnection(String key) throws SQLException {
		Connection c = connections.get(key);
		if (c!=null && !c.isClosed()) return c;

		loadJdbcDriver();
		Properties info = connProps!=null ? connProps : new Properties();
		if (username!=null) info.put("user",username);
		if (password!=null) info.put("password",password);
		connections.put(key,c = getConnection(uri,info));
		return c;
	}

	protected Connection getConnection(String uri, Properties info) throws SQLException {
		return DriverManager.getConnection(uri,info);
	}

	public void close() {
		for (Connection c : connections.values()) {
			try {
				c.close();
			} catch (SQLException ex) {
				ex.printStackTrace();
			}
		}
	}

	////////////////////////////////////////////////////////////////////////////
	// jdbc driver

	boolean driverLoaded;

	void loadJdbcDriver() {
		if (driverLoaded) return;
		Pattern p = Pattern.compile("^jdbc:([^/:]+):");
		Matcher m = p.matcher(uri);
		if (m.find()) {
			String[] classnames = driverClasses.get(m.group(1));
			if (classnames!=null)
				for (String cn : classnames)
					try { Class.forName(cn); break; }
					catch (Exception ex) {} // ignore
		}
		driverLoaded = true;
	}

	static final String[] drivers = new String[] {
			"cloudscape","COM.cloudscape.core.JDBCDriver",
			"db2","COM.ibm.db2.jdbc.net.DB2Driver",
			"derby","org.apache.derby.jdbc.ClientDriver,org.apache.derby.jdbc.EmbeddedDriver",
			"easysoft","easysoft.sql.jobDriver",
			"firebird","org.firebirdsql.jdbc.FBDriver",
			"frontbase","jdbc.FrontBase.FBJDriver",
			"h2","org.h2.Driver",
			"hsqldb","org.hsqldb.jdbcDriver",
			"idb","org.enhydra.instantdb.jdbc.idbDriver",
			"informix-sqli","com.informix.jdbc.IfxDriver",
			"ingres","com.ingres.jdbc.IngresDriver",
			"interbase","interbase.interclient.Driver",
			"jdbcprogress","com.progress.sql.jdbc.JdbcProgressDriver",
			"jtds","net.sourceforge.jtds.jdbc.Driver",
			"mariadb","org.mariadb.jdbc.Driver",
			"mckoi","com.mckoi.JDBCDriver",
			"microsoft","com.microsoft.jdbc.sqlserver.SQLServerDriver",
			"msql","com.imaginary.sql.msql.MsqlDriver",
			"mysql","com.mysql.cj.jdbc.Driver,com.mysql.jdbc.Driver,org.gjt.mm.mysql.Driver",
			"nilostep","com.nilostep.xlsql.jdbc.xlDriver",
			"odbc","sun.jdbc.odbc.JdbcOdbcDriver",
			"openbase","com.openbase.jdbc.ObDriver",
			"oracle","oracle.jdbc.driver.OracleDriver",
			"pointbase","com.pointbase.jdbc.jdbcUniversalDriver",
			"postgres95","postgres95.PGDriver",
			"postgresql","org.postgresql.Driver,postgresql.Driver",
			"sapdb","com.sap.dbtech.jdbc.DriverSapDB",
			"solid","solid.jdbc.SolidDriver",
			"sqlbase","centura.java.sqlbase.SqlbaseDriver",
			"sybase","com.sybase.jdbc2.jdbc.SybDriver",
			"sybase:tds","com.sybase.jdbc.SybDriver",
			"timesten:client","com.timesten.jdbc.TimesTenClientDriver",
			"timesten:direct","com.timesten.jdbc.TimesTenDriver",
	};
	static Map<String,String[]> driverClasses;
	static {
		driverClasses = new HashMap();
		for (int i=0; i<drivers.length;)
			driverClasses.put(drivers[i++],drivers[i++].split(","));
	}

	public static class Table {
		private final Database db;
		private final String name;
		private final Set<String> pks;
		private final Set<String> columns;

		private final boolean readOnly;
		private final String sqlSelect;
		private final String sqlRowCount;

		public Table(Database db, String name, boolean readOnly) throws SQLException {
			this.db = db;
			this.name = name;
			this.readOnly = readOnly;

			Connection c = db.getConnection(name);

			ResultSet rs = c.getMetaData().getPrimaryKeys(null,null,name);
			RowIterator it = new RowIterator(rs);
			pks = new LinkedHashSet();
			while (it.hasNext()) pks.add(it.next().get("COLUMN_NAME"));

			rs = c.getMetaData().getColumns(null,null,name,null);
			it = new RowIterator(rs);
			columns = new LinkedHashSet();
			while (it.hasNext()) columns.add(it.next().get("COLUMN_NAME"));
			if (columns.isEmpty()) {
				it = db.iterate("show columns from "+name);
				while (it.hasNext()) columns.add(it.next().get("COLUMN_NAME"));
			}
			c.close();

			sqlSelect = "select * from " + name;
			sqlRowCount = "select count(*) from " + name;
		}

		public String getName() {
			return name;
		}

		public int getColumnCount() {
			return columns.size();
		}

		public String[] getColumns() {
			return columns.toArray(new String[columns.size()]);
		}

		public String[] getPks() {
			return pks.toArray(new String[pks.size()]);
		}

		public String getPk() {
			return pks.size()==1 ? pks.iterator().next() : null;
		}

		public Table addPK(String pk) {
			pks.add(pk);
			return this;
		}

		private String sqlSelect(String criteria) {
			if (criteria!=null && criteria.trim().toLowerCase().startsWith("select "))
				return criteria.trim();
			return sqlSelect + (criteria==null ? "" : (" where "+criteria));
		}

		public RowSet getRows(String criteria, Object... params) throws SQLException {
			return db.query(name+"/getRows",sqlSelect(criteria),params);
		}

		public RowSet getRows() throws SQLException {
			return getRows(null);
		}

		public RowIterator iterateRows(String criteria, Object... params) throws SQLException {
			return db.iterate(null,sqlSelect(criteria),params);
		}

		public RowIterator iterateRows() throws SQLException {
			return iterateRows(null);
		}

		public RowFetch fetchRows(String criteria, Object... params) throws SQLException {
			return db.fetch(null,sqlSelect(criteria),params);
		}

		public RowFetch fetchRows() throws SQLException {
			return fetchRows(null);
		}

		public int getRowCount() throws SQLException {
			Connection c = db.getConnection(name+"/getRowCount");
			ResultSet rs = c.prepareStatement(sqlRowCount).executeQuery();
			return rs.first() ? rs.getInt(1) : 0;
		}

		public Row getRow(String criteria, Object... params) throws SQLException {
			RowIterator it = iterateRows(criteria,params);
			Row first = it.hasNext() ? it.next() : null;
			it.getResultSet().close();
			return first;
		}

		public boolean putRow(Row row) throws SQLException {
			return putRow(row,null);
		}

		public boolean putRow(Row row, String criteria) throws SQLException {
			if (row.getDirtyKeys().isEmpty()) return false;
			StringBuilder sb = new StringBuilder();
			boolean usercriteria = criteria!=null;
			boolean update = true;
			Connection c = db.getConnection(name+"/putRow");
			if (criteria==null) {
				sb.append(" where ");
				int i = 0;
				for (String pk : pks) {
					if (i++>0) sb.append(" and ");
					sb.append(pk).append("=?");
				}
				// check if row exists
				PreparedStatement ps = c.prepareStatement(sqlSelect+sb);
				i = 0;
				for (String pk : pks)
					ps.setObject(++i,row.geto(pk));
				update = ps.executeQuery().next();
				ps.close();
			} else {
				criteria = criteria.trim();
				sb.append(" ");
				if (!criteria.startsWith("where "))
					sb.append("where ");
				sb.append(criteria);
			}
			criteria = sb.toString();

			Set<String> dirty = row.getDirtyKeys();
			if (columns!=null && !columns.isEmpty()) {
				Map<String,String> lc = new LinkedHashMap();
				for (String dd : dirty) lc.put(dd.toLowerCase(),dd);
				if (!update) // pks required for insert
					for (String pk : pks) lc.put(pk.toLowerCase(),pk);
				dirty = new LinkedHashSet();
				for (String cc : columns) {
					String key = cc.toLowerCase();
					if (lc.containsKey(key))
						dirty.add(lc.get(key));
				}
			} else {
				if (!update) // pks required for insert
					dirty.addAll(pks);
			}
			List<String> keys = new ArrayList();
			sb.setLength(0);
			if (update) {
				sb.append("update ").append(name).append(" set ");
				for (String d : dirty)
					if (!pks.contains(d)) {
						sb.append(d).append("=?,");
						keys.add(d);
					}
				if (!usercriteria)
					for (String pk : pks)
						keys.add(pk);
				sb.setLength(sb.length()-1);
				sb.append(criteria);
			} else {
				sb.append("insert into ").append(name).append(" (");
				for (String d : dirty)
					sb.append(d).append(",");
				sb.setLength(sb.length()-1);
				sb.append(") values (");
				for (String d : dirty) {
					sb.append("?,");
					keys.add(d);
				}
				sb.setLength(sb.length()-1);
				sb.append(")");
			}
			PreparedStatement ps = c.prepareStatement(sb.toString());
			for (int i=0; i<keys.size(); i++)
				ps.setObject(i+1,row.geto(keys.get(i)));
			if (readOnly) {
				System.out.println(ps);
				return true; // or false?
			}
			ps.execute();
			return update ? ps.getUpdateCount()>0 : ps.getUpdateCount()==1;
		}
	}

	public static class Row {
		private final List<String> columns = new ArrayList();
		private final Map<String,Object> data = new LinkedHashMap();
		private final Set<String> dirty = new LinkedHashSet();

		void pput(String name, Object value) {
			if (!data.containsKey(name))
				columns.add(name);
			data.put(name,value);
		}

		public void put(String name, Object value) {
			if (data.containsKey(name)) {
				Object old = data.get(name);
				boolean eq = value==old || value!=null && value.equals(old);
				if (!eq) dirty.add(name);
			} else dirty.add(name);
			pput(name,value);
		}

		public int length() {
			return data.size();
		}

		public Object geto(String name) {
			return data.get(name);
		}

		public String get(String name) {
			Object v = data.get(name);
			return v!=null ? String.valueOf(v) : null;
		}

		public Object geto(int column) {
			return data.get(columns.get(column));
		}

		public String get(int column) {
			Object v = data.get(columns.get(column));
			return v!=null ? String.valueOf(v) : null;
		}

		public String getKey(int column) {
			return columns.get(column);
		}

		public Map<String,Object> getData() {
			return Collections.unmodifiableMap(data);
		}

		public Set<String> getDirtyKeys() {
			return dirty;
		}

		public void dump() {
			for (Map.Entry<String,Object> e : data.entrySet())
				System.out.print(e.getKey() + "=" + e.getValue() + ", ");
			System.out.println("");
		}
	}

	public static class RowSet extends ArrayList<Row> {
		public void dump() { for (Row r : this) r.dump(); }
	}
}
