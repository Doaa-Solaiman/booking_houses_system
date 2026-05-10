package de.scheller.platform.network;

import java.io.IOException;
import java.io.StringReader;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.ToNumberPolicy;

import de.scheller.platform.common.ByName;
import de.scheller.platform.common.Database;
import de.scheller.platform.common.Database.Row;
import de.scheller.platform.common.Database.RowSet;
import de.scheller.platform.common.Database.Table;
import de.scheller.platform.common.IValue.WithDefaults;
import de.scheller.platform.common.ItemStore;
import de.scheller.platform.common.ItemStore.StoreItem;
import de.scheller.platform.common.StringUtils;

/**
 * @author kandzia
 */
public class PlatformNode
{
	private static final Logger logger = LoggerFactory.getLogger(
			PlatformNode.class.getPackage().getName()+"");

	public static String ConfigTable;
	public static String ConfigId;
	public static String ConfigVersion;

	public static final ItemStore configs = new ItemStore(Collections.EMPTY_LIST) {
		private final Gson gson = gsonBuilder().create();
		@Override
		protected Access getAccess(StoreItem item) {
			String content = item.content();
			Map m = tryParsing(item,Map.class);
			return new Access() {
				public <T> T get(Class<T> type) {
					if (type==String.class)
						return (T)content;
					if (type==Map.class)
						return (T)m;
					return tryParsing(item,type);
				}
				public <T> T get(String key, Class<T> type) {
					Object value = m.get(key);
					if (value==null)
						return null;
					if (type==StoreItem.class && value instanceof String)
						return (T)getItem(ItemStore.resolve((String)value,item.path()),Get.IfExists);
					if (type.isInstance(value))
						return (T)value;
					if (type==String.class)
						return (T)gson.toJson(value);
					return null;
				}
				public <T> T get(String key, T defaultValue) {
					Object value = m.get(key);
					return value!=null ? (T)value : defaultValue;
				}
				public <T> void set(String key, T value) {
					m.put(key,value);
					// TODO
				}
			};
		}
		<T> T tryParsing(StoreItem item, Class<T> type) {
			String content = item.content();
			if (content==null) return null;
			if (type==String.class) return (T)content;
			if (item.path().endsWith(".properties"))
				return tryConvert(tryConvert(content,Properties.class),type);
			if (content.trim().startsWith("{"))
				return tryConvert(tryConvert(content,Map.class),type);
			return null;
		}
		<T> T tryConvert(Object o, Class<T> type) {
			if (type==Map.class && o instanceof Properties)
				return (T)new LinkedHashMap((Map)o);
			if (type==Map.class && o instanceof String && ((String)o).trim().startsWith("{"))
				return (T)gson.fromJson((String)o,Map.class);
			if (type==Properties.class && o instanceof Map) {
				Properties props = new Properties();
				props.putAll((Map)o);
				return (T)props;
			}
			if (type==Properties.class && o instanceof String) {
				Properties props = new Properties();
				try {
					props.load(new StringReader((String)o));
				} catch (IOException ex) {
					ex.printStackTrace();
				}
				return (T)props;
			}
			return type.isInstance(o) ? (T)o : null;
		}
	};

	public static StoreItem getRootConfig() {
		return configs.getItem(".config",ItemStore.Get.IfExists);
	}

	public static StoreItem getConfig(String path) {
		return configs.getItem(path,ItemStore.Get.IfExists);
	}

	/** See {@link #persistConfigs(String...)}. */
	public static StoreItem getOrCreateConfig(String path) {
		return configs.getItem(path,ItemStore.Get.OrCreate);
	}

	static void updateRootConfig(Map<String,Object> config) {
		StoreItem item = configs.getItem(".config",ItemStore.Get.OrCreate);
		item.content(gsonBuilder().create().toJson(config));
		item.meta(ItemStore.DisplayName,"Root Config");
		item.meta(ItemStore.Updated,new Date());
	}

	public static Database getDatabase() {
		StoreItem configItem = getRootConfig();
		if (configItem==null)
			return null;
		Map database = configItem.access().get("database",Map.class);
		if (database==null)
			return null;
		String dbUri = (String)database.get("jdbc");
		String dbUser = (String)database.get("user");
		String dbPass = (String)database.get("pass");
		if (!dbUri.startsWith("jdbc:")) dbUri = "jdbc:"+dbUri;
		dbUri = StringUtils.substitute(dbUri,StringUtils.PropertyOnlyBraces,database);
		return new Database(dbUri,dbUser,dbPass);
	}

	static void loadConfigsFromDatabase() throws SQLException {
		Database db = getDatabase();
		if (db==null)
			return;
		StoreItem configItem = getRootConfig();
		if (configItem==null)
			return;
		WithDefaults config = ByName.withDefaults(configItem.access().get(Map.class));
		String dbConfigs = config.asString("configsFromDb");
		if (dbConfigs==null)
			return;
		// TODO decide to remove defaults
		String dbPlatformOrg = config.asString("platformOrg","org-system-init");
		ConfigTable = config.asString("configDbTable","SYS_Config");
		WithDefaults schema = ByName.withDefaults(config.value("configDbTableSchema"));
		ConfigId = schema.asString("id","id");
		ConfigVersion = schema.asString("version","version");
		String dbTableOrgField = schema.asString("org","organization_id");
		String dbTablePathField = schema.asString("path","path");
		DbTableNameField = schema.asString("name","name");
		DbTableContentField = schema.asString("content","content");
		DbTableCreatedField = schema.asString("created","timeCreate");
		DbTableUpdatedField = schema.asString("updated","timeUpdate");
		String sql = "";
		if (dbTableOrgField!=null && dbPlatformOrg!=null)
			sql += dbTableOrgField+"='"+dbPlatformOrg+"' AND "; // must match
		sql += dbTablePathField+" like '"+dbConfigs+"%'"; // prefix search
		try {
			Table table = db.getTable(ConfigTable);
			RowSet rows = table.getRows(sql);
			configs.setBatch(true);
			for (Row r : rows) {
				String path = r.get(dbTablePathField).substring(dbConfigs.length());
				if (path.startsWith("/")) path = path.substring(1);
				StoreItem item = configs.getItem(path,ItemStore.Get.OrCreate);
				updateItem(item,r.getData());
			}
		} finally {
			db.close();
			configs.setBatch(false);
			configs.flush();
		}
	}

	public static boolean updateItem(StoreItem item) {
		if (item==null)
			return false;
		String id = item.meta(ConfigId,String.class);
		if (id==null)
			return false;
		Database db = getDatabase();
		if (db==null)
			return false;
		try {
			Row r = db.getTable(ConfigTable).getRow(ConfigId+" = '"+id+"'");
			if (r!=null)
				updateItem(item,r.getData());
			return true;
		} catch (SQLException ex) {
			throw new RuntimeException(ex);
		} finally {
			db.close();
		}
	}

	static void updateItem(StoreItem item, Map data) {
		item.content((String)data.get(DbTableContentField));
		if (DbTableNameField!=null && data.containsKey(DbTableNameField))
			item.meta(ItemStore.DisplayName,data.get(DbTableNameField));
		if (DbTableCreatedField!=null && data.containsKey(DbTableCreatedField))
			item.meta(ItemStore.Created,date(data.get(DbTableCreatedField)));
		Object updated = data.get(DbTableUpdatedField);
		if (updated==null) updated = new Date();
		item.meta(ItemStore.Updated,date(updated));
		if (ConfigId!=null && data.containsKey(ConfigId))
			item.meta(ConfigId,data.get(ConfigId));
		if (ConfigVersion!=null && data.containsKey(ConfigVersion))
			item.meta(ConfigVersion,data.get(ConfigVersion));
		// if this is an alternative format of the root configuration...
		if (item.path().startsWith(".config.")) {
			StoreItem root = getRootConfig();
			Map<String,Object> content = root.access().get(Map.class);
			content.putAll(item.access().get(Map.class));
			root.content(gsonBuilder().create().toJson(content));
			root.meta(ItemStore.Updated,date(updated));
		}
	}

	public static void persistConfigs(String... paths) throws SQLException {
		Database db = getDatabase();
		if (db==null)
			return;
		StoreItem configItem = getRootConfig();
		if (configItem==null)
			return;
		WithDefaults config = ByName.withDefaults(configItem.access().get(Map.class));
		String dbConfigs = config.asString("configsFromDb");
		if (dbConfigs==null)
			return;
		// TODO decide to remove defaults
		String dbPlatformOrg = config.asString("platformOrg","org-system-init");
		WithDefaults schema = ByName.withDefaults(config.value("configDbTableSchema"));
		String dbTableOrgField = schema.asString("org","organization_id");
		String dbTablePathField = schema.asString("path","path");
		try {
			Set<String> inserted = new LinkedHashSet();
			Set<String> updated = new LinkedHashSet();
			Table table = db.getTable(ConfigTable);
			for (String path : paths) {
				StoreItem item = getConfig(path);
				if (item==null) continue;
				String sql = "";
				if (dbTableOrgField!=null && dbPlatformOrg!=null)
					sql += dbTableOrgField+"='"+dbPlatformOrg+"' AND "; // must match
				sql += dbTablePathField+"='"+dbConfigs+path+"'"; // must match
				Row row = table.getRow(sql);
				if (row==null) {
					String id = "mcc-"+Long.toString(System.currentTimeMillis(),36)+"-"+Network.name;
					row = new Row();
					row.put(dbTableOrgField,dbPlatformOrg);
					row.put(dbTablePathField,dbConfigs+item.path());
					row.put(ConfigId,id);
					row.put(ConfigVersion,0);
					row.put(DbTableCreatedField,item.meta(ItemStore.Created,Date.class));
					row.put(DbTableContentField,item.content());
					if (table.putRow(row,null))
						inserted.add(row.get(ConfigId));
				} else {
					String ver = row.get(ConfigVersion);
					row.put(ConfigVersion,Integer.valueOf(ver!=null ? ver : "0")+1);
					row.put(DbTableUpdatedField,item.meta(ItemStore.Updated,Date.class));
					row.put(DbTableContentField,item.content());
					if (table.putRow(row,sql))
						updated.add(row.get(ConfigId));
				}
			}
			StringBuilder sb = new StringBuilder();
			for (String id : inserted)
				sb.append("|add|").append(ConfigTable).append('|').append(id);
			for (String id : updated)
				sb.append("|update|").append(ConfigTable).append('|').append(id);
			if (sb.length()>0)
				Network.event("changesCommitted",sb.toString());
		} finally {
			db.close();
		}
	}

	static Date date(Object o) {
		if (o instanceof Date)
			return (Date)o;
		if (o instanceof Long)
			return new Date((Long)o);
		if (o instanceof LocalDateTime)
			return Date.from(((LocalDateTime)o).atZone(ZoneId.systemDefault()).toInstant());
		if (o instanceof Instant)
			return Date.from((Instant)o);
		return null;
	}

	static String DbTableContentField;
	static String DbTableNameField;
	static String DbTableCreatedField;
	static String DbTableUpdatedField;

	static GsonBuilder gsonBuilder() {
		GsonBuilder gb = new GsonBuilder();
		gb.setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE);
		gb.setDateFormat("yyyy-MM-dd HH:mm:ss");
		gb.serializeSpecialFloatingPointValues();
		gb.disableHtmlEscaping();
		gb.setPrettyPrinting();
		return gb;
	}

	public static void configure() {
		StoreItem item = getRootConfig();
		List<List<String>> tsr = item.access().get("topicSendRewrite",List.class);
		List<List<String>> trr = item.access().get("topicReceiveRewrite",List.class);
		if (tsr!=null || trr!=null) {
			Function<List<List<String>>,Function<String,String>> createRewrite = rl -> {
				Map<Pattern,List<String>> rewrite = new LinkedHashMap();
				for (List<String> l : rl) {
					String re = l.get(0);
					re = re.replaceAll("(?<=^|/)(\\+)(?=/|$)","([^/]+)");
					re = re.replaceAll("(?<=^|/)(#)(?=/|$)","(.*)");
					Pattern p = Pattern.compile(re);
					rewrite.put(p,Arrays.asList(re,l.get(1)));
				}
				boolean sendRewrite = rl==tsr;
				return topic -> {
					String orig = topic;
					for (Map.Entry<Pattern,List<String>> e : rewrite.entrySet()) {
						Pattern p = e.getKey();
						Matcher m = p.matcher(topic);
						if (!m.matches()) continue;
						List<String> l = e.getValue();
						String re = l.get(0);
						String t = l.get(1);
						topic = topic.replaceAll(re,t);
						topic = topic.replace("{ip}",Network.hostip);
						topic = topic.replace("{realm}",Network.realm);
						topic = topic.replace("{host}",Network.host);
						topic = topic.replace("{node}",Network.name);
						topic = topic.replace("{proc}",Network.proc);
//						logger.debug("{} topic {} to {}",
//								sendRewrite ? "sendRewrite" : "receiveRewrite",orig,topic);
						return topic;
					}
					return topic;
				};
			};
			if (tsr!=null) Network.sendRewrite = createRewrite.apply(tsr);
			if (trr!=null) Network.receiveRewrite = createRewrite.apply(trr);
			if (trr!=null)
				for (List<String> l : trr) {
					String t = l.get(0);
					if (t.startsWith("+/"))
						t = t.substring(2);
					Network.mqtt.topics.put(Network.prefix+t,"to rewrite");
					Network.mqtt.resubscribe();
				}
		}
	}
}
