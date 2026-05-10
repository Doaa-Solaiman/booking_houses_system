package de.scheller.platform.persist.util;

import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Struct;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author kandzia
 */
public class JdbcHelper
{
	private static final Logger logger = LoggerFactory.getLogger(
			JdbcHelper.class.getPackage().getName()+"");

	public static void showConnectionInfos(Connection c) throws SQLException {
		DatabaseMetaData dbmd = c.getMetaData();
		if (dbmd==null) {
			logger.info("Metadata not supported");
			return;
		}
		logger.info("Database Version: {}",dbmd.getDatabaseProductVersion());
		logger.info("Driver Name: {}",dbmd.getDriverName());
		logger.info("Driver Version: {}",dbmd.getDriverVersion());
		logger.info("URL: {}",dbmd.getURL());
		logger.info("User Name: {}",dbmd.getUserName());
		logger.info("ANSI92FullSQL {}",dbmd.supportsANSI92FullSQL() ? "supported." : "not supported.");
		logger.info("Transactions {}",dbmd.supportsTransactions() ? "supported." : "not supported.");
	}

	public static List<Serializable> ids(PreparedStatement ps) throws SQLException {
		ResultSet rs = ps.executeQuery();
		try {
			List<Serializable> ids = new ArrayList(1000);
			while (rs.next()) ids.add((Serializable)rs.getObject(1));
			return ids;
		} finally {
			rs.close();
		}
	}

	// for test purposes
	public static int readEverything(PreparedStatement ps, Map<Serializable,Object[]> byId)
	throws SQLException {
		ResultSet rs = ps.executeQuery();
		try {
			ResultSetMetaData rm = rs.getMetaData();
			int cc = rm.getColumnCount();
			int rc = 0;
			List values = new ArrayList();
			while (rs.next()) {
				values.clear();
				for (int c=1; c<=cc; c++)
					values.add(rs.getObject(c));
				byId.put((Serializable)rs.getObject(1),values.toArray());
				rc++;
			}
			return rc;
		} finally {
			rs.close();
		}
	}

	public static Class<?> toJavaType(int sqlType) {
		switch (sqlType) {
			case Types.BIT: case Types.BOOLEAN: return boolean.class;
			case Types.TINYINT: return byte.class;
			case Types.SMALLINT: return short.class;
			case Types.INTEGER: return int.class;
			case Types.BIGINT: return long.class;
			case Types.REAL: return float.class;
			case Types.FLOAT: case Types.DOUBLE: return double.class;
			case Types.NUMERIC: case Types.DECIMAL: return BigDecimal.class;

			case Types.CHAR: case Types.VARCHAR: case Types.LONGNVARCHAR:
				return String.class;
			case Types.CLOB: return Clob.class;
			case Types.BLOB: return Blob.class;
			case Types.BINARY: case Types.VARBINARY: case Types.LONGVARBINARY:
				return byte[].class;

			case Types.DATE: return Date.class;
			case Types.TIME: return Time.class;
			case Types.TIMESTAMP: return Timestamp.class;
			case Types.TIME_WITH_TIMEZONE: return OffsetTime.class;
			case Types.TIMESTAMP_WITH_TIMEZONE: return OffsetDateTime.class;

			case Types.ARRAY: return Array.class;
			case Types.STRUCT: return Struct.class;
			case Types.REF: return Ref.class;
		}
		return Object.class;
	}

	private static final Map<String, Class<?>> javaTypeToSqlType = new HashMap<String, Class<?>>();

	static {
		javaTypeToSqlType.put("java.lang.Number",BigDecimal.class);
		javaTypeToSqlType.put("java.util.Date",Timestamp.class);
		javaTypeToSqlType.put("java.util.Calendar",Timestamp.class);
		javaTypeToSqlType.put("java.sql.Timestamp",Timestamp.class);
		javaTypeToSqlType.put("java.time.Instant",Timestamp.class);
		javaTypeToSqlType.put("java.time.LocalDateTime",Timestamp.class);
		javaTypeToSqlType.put("java.time.ZonedDateTime",Timestamp.class);
		javaTypeToSqlType.put("java.time.OffsetDateTime",Timestamp.class);
		javaTypeToSqlType.put("java.sql.Time",Time.class);
		javaTypeToSqlType.put("java.time.LocalTime",Time.class);
		javaTypeToSqlType.put("java.time.OffsetTime",Time.class);
		javaTypeToSqlType.put("java.sql.Date",Date.class);
		javaTypeToSqlType.put("java.time.LocalDate",Date.class);
		javaTypeToSqlType.put("java.time.YearMonth",Date.class);
		javaTypeToSqlType.put("java.time.Year",Date.class);

		javaTypeToSqlType.put("org.joda.time.Instant",Timestamp.class);
		javaTypeToSqlType.put("org.joda.time.LocalDateTime",Timestamp.class);
		javaTypeToSqlType.put("org.joda.time.DateTime",Timestamp.class);
		javaTypeToSqlType.put("org.joda.time.LocalTime",Time.class);
		javaTypeToSqlType.put("org.joda.time.LocalDate",Date.class);
	}

//	boolean driverLoaded;
//
//	void loadJdbcDriver() {
//		if (driverLoaded) return;
//		Pattern p = Pattern.compile("^jdbc:([^/:]+):");
//		Matcher m = p.matcher(uri);
//		if (m.find()) {
//			String[] classnames = driverClasses.get(m.group(1));
//			if (classnames!=null)
//				for (String cn : classnames)
//					try { Class.forName(cn); break; }
//					catch (Exception ex) {} // ignore
//		}
//		driverLoaded = true;
//	}

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
			"mckoi","com.mckoi.JDBCDriver",
			"microsoft","com.microsoft.jdbc.sqlserver.SQLServerDriver",
			"msql","com.imaginary.sql.msql.MsqlDriver",
			"mysql","com.mysql.jdbc.Driver,org.gjt.mm.mysql.Driver",
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
}
