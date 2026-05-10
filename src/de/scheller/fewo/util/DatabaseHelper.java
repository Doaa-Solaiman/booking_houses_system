package de.scheller.fewo.util;

import java.io.File;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.zip.Deflater;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import de.scheller.common.HasId;
import de.scheller.platform.common.ClassUtils;
import de.scheller.platform.common.StringUtils;
import de.scheller.platform.network.Network;
import de.scheller.platform.persist.DatabaseCache;
import de.scheller.platform.persist.DatabaseImpl;
import de.scheller.platform.persist.DatabaseMeta;
import de.scheller.platform.persist.DatabaseOrm;
import de.scheller.platform.persist.IServerBeanManager.BeanChange;
import de.scheller.platform.persist.IServerBeanManager.ChangesListener;
import de.scheller.platform.persist.util.Id;

/**
 * @author kandzia
 */
public class DatabaseHelper
{
	private static Logger logger = LoggerFactory.getLogger(DatabaseHelper.class.getName());

	public static DatabaseImpl db() throws Exception {
		return emitUpdateEvents(db(ds()));
	}

	public static DatabaseImpl db(DataSource ds) throws Exception {
		// get database schema object model
		Path root = ClassUtils.getClassesRootPath();
		List<Class> model = ClassUtils.getClasses(root,"",false);
//		if (!"jar".equals(root.toUri().getScheme()))
//			model.addAll(ClassUtils.getClasses(Paths.get(
//					"/workspace-git/EIP4/platform-persistence/target/classes/"),"",false));

		// init ID generator
		Id.periodicallySaveIdState(new File("id.properties"));

		// init ORM database access
		Properties prefixes = new Properties();
//		prefixes.load(DatabaseHelper.class.getResourceAsStream("prefixes.properties"));
		DatabaseOrm dbo = new DatabaseOrm(model,(Map)prefixes);
		DatabaseCache dbc = new DatabaseCache(dbo,Deflater.BEST_SPEED);
//		DatabaseCache dbc = new DatabaseCache(dbo,Deflater.NO_COMPRESSION);
		Connection conn = ds.getConnection();
		DatabaseMeta dbm = new DatabaseMeta(conn,conn.getCatalog());
		return new DatabaseImpl(ds,dbo,dbc) {
			@Override
			public void link(Object it, Object to, String field) throws Exception {
				if (it==null || to==null)
					return;
				beginTransaction();
				try {
					super.link(it,to,field);
					addChangeEvent(BeanChange.MAPPED(it,field));
					addChangeEvent(BeanChange.MAPPED(to,field));
					commit();
				} catch (Exception ex) {
					rollback();
					throw ex;
				}
			}
			@Override
			public void unlink(Object it, Object from, String field) throws Exception {
				if (it==null || from==null)
					return;
				beginTransaction();
				try {
					super.unlink(it,from,field);
					addChangeEvent(BeanChange.MAPPED(it,field));
					addChangeEvent(BeanChange.MAPPED(from,field));
					commit();
				} catch (Exception ex) {
					rollback();
					throw ex;
				}
			}
		};
	}

	public static Map config() throws Exception {
		Map database = (Map)Network.getConfig().get("database");
		if (database==null)
			throw new Exception("Database access is not configured.");
		return database;
	}

	public static DataSource ds() throws Exception {
		// init database connection factory/pool
		return ds(config());
	}

	public static DataSource ds(Map database) throws Exception {
		// init database connection factory/pool
		if (database==null)
			throw new Exception("Database access is not configured.");
		if (database.get("driverClassName")!=null)
			Class.forName((String)database.get("driverClassName"));
		String jdbcUrl = (String)database.get("jdbc");
		if (!jdbcUrl.startsWith("jdbc:")) jdbcUrl = "jdbc:"+jdbcUrl;
		jdbcUrl = StringUtils.substitute(jdbcUrl,StringUtils.PropertyOnlyBraces,database);
		database.put("jdbc",jdbcUrl);
		Properties props = new Properties();
		props.put("jdbcUrl",database.get("jdbc"));
		props.put("username",database.get("user"));
		props.put("password",database.get("pass"));
		props.put("catalog",database.get("name"));
		Map pool = (Map)database.getOrDefault("pool",Collections.EMPTY_MAP);
		props.put("poolName",pool.getOrDefault("name","Hikari-"+Network.name+"-"+Network.proc));
		props.putAll(pool);
		HikariConfig dsc = new HikariConfig(props);
		HikariDataSource ds = new HikariDataSource(dsc);
		logger.info("Database: {}",ds.toString());
		Connection conn = ds.getConnection();
		showConnectionInfos(conn);
		conn.close();
		return ds;
	}

	public static void showConnectionInfos(Connection c) throws SQLException {
		DatabaseMetaData dbmd = c.getMetaData();
		if (dbmd==null) {
			logger.info("Connection metadata not supported");
			return;
		}
		logger.info("Database Version: {}",dbmd.getDatabaseProductVersion());
		logger.info("Driver Name: {}",dbmd.getDriverName());
		logger.info("Driver Version: {}",dbmd.getDriverVersion());
		logger.info("URL: {}",dbmd.getURL());
		logger.info("User Name: {}",dbmd.getUserName());
		logger.info("ANSI92FullSQL {}",(dbmd.supportsANSI92FullSQL() ? "supported." : "not supported."));
		logger.info("Transactions {}",(dbmd.supportsTransactions() ? "supported." : "not supported."));
	}

	public static DatabaseImpl emitUpdateEvents(DatabaseImpl db) throws Exception {
		db.setChangesListener(new ChangesListener() {
			public void changesCommited(Collection<BeanChange> changes) {
				Network.message(Network.host+"_"+Network.proc,"committed "+changes.size()+" changes");
				try {
					StringBuilder sb = new StringBuilder();
					for (BeanChange c : changes) {
						sb.append('|').append(c.getTypeOfChange().name().toLowerCase());
						if (c.getCurrent()!=null) {
							sb.append('|').append(c.getCurrent().getClass().getName());
							sb.append('|').append(String.valueOf(((HasId)c.getCurrent()).getId()));
						} else {
							sb.append('|').append(c.getPrevious().getClass().getName());
							sb.append('|').append(String.valueOf(((HasId)c.getPrevious()).getId()));
						}
					}
					Network.event("changesCommitted",sb.toString());
				} catch (Exception ex) {
					logger.error(ex.getMessage(),ex);
				}
			}
		});
		return db;
	}

	public static void receiveUpdateEvents(DatabaseImpl db) throws Exception {
		Network.onAction("clearCaches",info -> {
			db.clearCaches();
			Network.message(info.asString("id"),"DB caches cleared.");
		});
	}
}
