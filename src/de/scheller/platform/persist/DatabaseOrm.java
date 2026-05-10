package de.scheller.platform.persist;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.Version;

import de.scheller.platform.persist.util.LambdaUtil;
import de.scheller.platform.persist.util.ReflectHelper;
import de.scheller.platform.persist.util.ReflectHelper.ClassStrategy;
import de.scheller.transferobject.ITransferObject;
import de.scheller.util.Pair;

/**
 * @author kandzia
 */
public class DatabaseOrm
{
	Map<Class,String> modelImplTypes;
	Map<Class,Class> modelIntfTypes;
	Set<Class> valueTypes;
	Map<Class,OrmInfo> infos = new LinkedHashMap();
	Map<String,Class> types;
	Map<Class,Class> eqclass;

	public DatabaseOrm(Iterable<Class> model, Map<?,String> prefixes) throws Exception {
		modelImplTypes = new LinkedHashMap();
		for (Class type : model)
			if (type.isAnnotationPresent(Entity.class))
				modelImplTypes.put(type,tableName(type));

		valueTypes = new LinkedHashSet();
		for (Class impl : modelImplTypes.keySet())
			for (Pair<String,Class> g : ReflectHelper.getGetters(impl).keySet())
				if (Serializable.class.isAssignableFrom(g.getSecond()))
					valueTypes.add(g.getSecond());

		modelIntfTypes = new LinkedHashMap();
		Set<Class> notOneToOne = new HashSet();
		Map<Class,Class> direct = new LinkedHashMap();
		for (Class impl : modelImplTypes.keySet())
			for (Class intf : ReflectHelper.getClasses(impl,ClassStrategy.interfaces,null)) {
				if (Arrays.asList(impl.getInterfaces()).contains(intf))
					direct.put(intf,impl);
				if (modelIntfTypes.containsKey(intf)) {
					notOneToOne.add(intf);
					modelIntfTypes.remove(intf);
				} else if (!notOneToOne.contains(intf)) {
					modelIntfTypes.put(intf,impl);
				} else {
//					Annotation entity = intf.getAnnotation(EntityPart.class);
//					if (entity!=null)
//						entityParts.add(intf);
				}
			}
		modelIntfTypes.putAll(direct);

		Set<Class> all = new LinkedHashSet();
		all.addAll(modelImplTypes.keySet());
		all.addAll(modelIntfTypes.keySet());
		for (Class type : all) {
			OrmInfo info = info(type);
			for (Class base : ReflectHelper.getClasses(type,ClassStrategy.all,null))
				if (base!=type && all.contains(base) && !info.basedOn.contains(base))
					info.basedOn.add(base);
		}

		for (Class type : modelImplTypes.keySet()) {
			OrmInfo info = info(type);
			info.tables.add(modelImplTypes.get(type));
			info.root = true;
			for (Class base : info.basedOn)
				if (modelImplTypes.containsKey(base)) {
					info.tables.add(modelImplTypes.get(base));
					info.root = false;
				}
		}

		for (Class type : modelImplTypes.keySet())
			for (Class base : info(type).basedOn)
				if (modelImplTypes.containsKey(base)) {
					OrmInfo info = info(base);
					info.subtypes.add(type);
					info.tables.add("!"+modelImplTypes.get(type));
				}

		for (Class type : modelImplTypes.keySet()) {
			OrmInfo info = info(type);
			Class<?> root = null;
			LinkedList<Class> bases = new LinkedList();
			for (Class<?> b=type; b!=null; b=b.getSuperclass())
				if (b.getAnnotation(Entity.class)!=null)
					bases.addFirst(root = b);

			Inheritance inherit = root.getAnnotation(Inheritance.class);
			InheritanceType strategy = inherit!=null ?
					inherit.strategy() : InheritanceType.SINGLE_TABLE;
			Set<String> columns = new HashSet();
			for (Class t : bases)
				for (Method m : t.getDeclaredMethods()) {
					if (m.isSynthetic())
						continue;
					Class ct = strategy==InheritanceType.SINGLE_TABLE ? root : t;
					Class rt = m.getReturnType();
					String mn = propertyName(m);
					String pn = ct+"."+mn;
					Column c = m.getAnnotation(Column.class);
					String cn = c!=null && c.name().length()>0 ? c.name() : mn;

					Id id = m.getAnnotation(Id.class);
					if (id!=null) {
						info.pkColumn = cn;
						GeneratedValue gv = m.getAnnotation(GeneratedValue.class);
						if (gv!=null && Number.class.isAssignableFrom(rt))
							info.autoIncrement = rt;
					}
					Version version = m.getAnnotation(Version.class);
					if (version!=null)
						info.versionColumn = cn;

					rt = modelIntfTypes.get(rt)!=null ? modelIntfTypes.get(rt) : rt;
					if (modelImplTypes.containsKey(rt)) {
						info.targetTable.put(cn,tableName(rt));
						OneToOne o2o = m.getAnnotation(OneToOne.class);
						if (o2o!=null && o2o.optional())
							info.optional.add(cn);
						ManyToOne m2o = m.getAnnotation(ManyToOne.class);
						if (m2o!=null && m2o.optional())
							info.optional.add(cn);
					} else if (Collection.class.isAssignableFrom(rt)) {
						OneToMany o2m = m.getAnnotation(OneToMany.class);
						if (o2m!=null) {
							OrmInfo.CollectionInfo ci = new OrmInfo.CollectionInfo();
							ci.mappedBy = o2m.mappedBy();
							ci.entityType = o2m.targetEntity();
							ci.tableName = null;
							info.collectionColumns.put(cn,ci);
						}
						ManyToMany m2m = m.getAnnotation(ManyToMany.class);
						if (m2m!=null) {
							OrmInfo.CollectionInfo ci = new OrmInfo.CollectionInfo();
							ci.mappedBy = m2m.mappedBy().length()==0 ? null : m2m.mappedBy();
							ci.entityType = m2m.targetEntity();

							String t1 = tableName(ct);
							String t2 = tableName(ci.entityType);
							String tn = ci.mappedBy==null ? t1+"_"+t2 : t2+"_"+t1;
							ci.tableName = tn;
							info.collectionColumns.put(cn,ci);

							JoinTable jt = m.getAnnotation(JoinTable.class);
							if (jt!=null) {
								JoinColumn jc = jt.joinColumns().length==1 ? jt.joinColumns()[0] : null;
								if (jc!=null) ci.altName = jc.name();
							}
							if (ci.mappedBy!=null) {
								OrmInfo im = info(ci.entityType);
								OrmInfo.CollectionInfo cim = im.collectionColumns.get(ci.mappedBy);
								if (cim!=null) cim.mappedBy = cn;
							} else {
								OrmInfo im = info(ci.entityType);
								for (Map.Entry<String,OrmInfo.CollectionInfo> e : im.collectionColumns.entrySet()) {
									OrmInfo.CollectionInfo cim = e.getValue();
									if (!cn.equals(cim.mappedBy)) continue;
									ci.mappedBy = e.getKey();
									break;
								}
							}
						}
					}

					if (columns.contains(pn) && mn.equals(cn))
						continue;
					columns.add(pn);
					info.propColumn.put(mn,cn);
					info.columnTable.put(cn,tableName(ct));
				}
		}

		for (Class type : modelImplTypes.keySet()) {
			OrmInfo info = info(type);
			String tablename = modelImplTypes.get(type);
			String prefix = prefixes.get(tablename);
			if (prefix==null) prefix = prefixes.get(type);
			if (prefix==null)
				for (Class t : info.basedOn) {
					prefix = prefixes.get(t);
					if (prefix!=null) break;
				}
			info.prefix = prefix;
		}

		types = new LinkedHashMap();
		eqclass = new LinkedHashMap();
		for (Class type : modelImplTypes.keySet()) {
			types.put(type.getName(),type);
			try {
				eqclass.put(type,(Class)((ITransferObject)type.newInstance()).getEqualsClass());
			} catch (Exception ignore) {}
		}
		for (Class type : modelIntfTypes.keySet())
			types.put(type.getName(),type);
		for (Class type : valueTypes)
			types.put(type.getName(),type);
		for (OrmInfo info : infos.values())
			for (OrmInfo.CollectionInfo ci : info.collectionColumns.values())
				if (ci.tableName!=null)
					types.put(ci.tableName,null);
		for (String typeName : types.keySet()) {
			Class type = types.get(typeName);
			if (modelIntfTypes.containsKey(type))
				types.put(typeName,modelIntfTypes.get(type));
		}
	}

	String tableName(Class<?> type) {
		Entity e = type.getAnnotation(Entity.class);
		if (e!=null && e.name().length()>0)
			return e.name();
		return type.getSimpleName();
	}

	String propertyName(Method method) {
		String mn = method.getName();
		if (mn.startsWith("get") || mn.startsWith("set") || mn.startsWith("is")) {
			String s = mn.substring(mn.charAt(0)=='i' ? 2 : 3);
			char c = s.charAt(0);
			if (Character.isUpperCase(c) && Character.isUpperCase(s.charAt(1)))
				return s;
			if (Character.isUpperCase(c))
				return Character.toLowerCase(c) + s.substring(1);
		}
		return mn;
	}

	public void dump() {
		System.out.println("Model impl types ("+modelImplTypes.size()+")");
		for (Class type : modelImplTypes.keySet())
			System.out.println("- "+type);
		System.out.println("Value types ("+valueTypes.size()+")");
		for (Class type : valueTypes)
			System.out.println("- "+type);
		System.out.println("Model intf types ("+modelIntfTypes.size()+")");
		for (Map.Entry<Class,Class> e : modelIntfTypes.entrySet())
			System.out.println("- "+e.getKey()+" -> "+e.getValue());
		for (Map.Entry<Class,OrmInfo> e : infos.entrySet()) {
			System.out.println("Model type "+e.getKey().getSimpleName());
			dump(e.getValue());
		}
	}

	public static void dump(OrmInfo info) {
		System.out.println("- root     "+info.root);
		System.out.println("- primaryk "+info.pkColumn);
		System.out.println("- tables   "+info.tables);
//		System.out.println("- tables + "+info.include+" - "+info.exclude);
		System.out.println("- inherits "+info.basedOn);
		System.out.println("- subtypes "+info.subtypes);
		for (String prop : info.propColumn.keySet()) {
			String column = info.propColumn.get(prop);
			String table = info.columnTable.get(column);
			System.out.println("- property "+prop+" -- "+table+"."+column);
		}
	}

	public OrmInfo info(String type) {
		return info(types.get(type));
	}

	public OrmInfo info(Class type) {
		if (type==null)
			return null;
		if (modelIntfTypes.containsKey(type))
			type = modelIntfTypes.get(type);
		OrmInfo info = infos.get(type);
		if (info==null) infos.put(type,info = new OrmInfo());
		return info;
	}

	public static class OrmInfo {
		boolean root;
		Class autoIncrement;
		String pkColumn;
		String versionColumn;
		List<Class> basedOn = new ArrayList(2);
		List<Class> subtypes = new ArrayList(8);
		List<String> tables = new ArrayList(8);
//		List<String> include = new ArrayList(2);
//		List<String> exclude = new ArrayList(8);
		String prefix;
		Map<String,String> propColumn = new LinkedHashMap();
		Map<String,String> columnTable = new LinkedHashMap();
		Map<String,String> targetTable = new LinkedHashMap();
		Set<String> optional = new LinkedHashSet();
		Map<String,CollectionInfo> collectionColumns = new LinkedHashMap();

		@Override
		public String toString() {
			return "OrmInfo"+tables;
		}

		public static class CollectionInfo {
			String mappedBy;
			String altName;
			Class entityType;
			String tableName;
		}
	}

	public static class SqlBuilder {
		private final Map<String,String> table = new LinkedHashMap(); // K=alias, V=table+hint
		private final Map<String,String> select = new LinkedHashMap(); // K=expression, V=alias
		private final Map<String,String> values = new LinkedHashMap(); // K=column, V=value
		private final List<String> where = new ArrayList();
		private final List<String> order = new ArrayList();
		private boolean allTypes;
		private boolean distinct;
		private int limit;

		public SqlBuilder allTypes(boolean allTypes) {
			this.allTypes = allTypes;
			return this;
		}

		public SqlBuilder table(Iterable<String> tables) {
			for (String t : tables)
				this.table.put("t"+this.table.size(),t);
			return this;
		}

		public SqlBuilder tableAs(String alias, String table) {
			this.table.put(alias,table);
			return this;
		}

		public SqlBuilder tableHint(String alias, String hint) {
			this.table.put(alias,this.table.get(alias)+"|"+table);
			return this;
		}

		public SqlBuilder values(Set<String> columns) {
			for (String e : columns)
				this.values.put(e,null);
			return this;
		}

		public SqlBuilder select(Iterable<String> expressions) {
			for (String e : expressions)
				this.select.put(e,null);
			return this;
		}

		public SqlBuilder selectAs(String alias, String expression) {
			this.select.put(expression,alias);
			return this;
		}

		public SqlBuilder where(String... expressions) {
			for (String e : expressions)
				if (e.trim().length()>0)
					this.where.add(e);
			return this;
		}

		public SqlBuilder sortAsc(String... fields) {
			for (String f : fields)
				this.order.add(f);
			return this;
		}

		public SqlBuilder sortDesc(String... fields) {
			for (String f : fields)
				this.order.add("!"+f);
			return this;
		}

		public SqlBuilder distinct() {
			this.distinct = true;
			return this;
		}

		public SqlBuilder limit(int limit) {
			this.limit = limit;
			return this;
		}

		private String tableExpr(Map.Entry<String,String> t) {
			return "`"+tableName(t).replaceFirst("^!","")+"` as `"+t.getKey()+"`";
		}
		private String tableName(Map.Entry<String,String> t) {
			return t.getValue().replaceFirst("\\|.*$","");
		}
		private String tableHint(Map.Entry<String,String> t) {
			return t.getValue().replaceFirst("^.*\\|?","");
		}

		public String sql() {
			if (table.isEmpty())
				throw new RuntimeException("no table");

			List<String> select = new ArrayList();
			for (Map.Entry<String,String> e : this.select.entrySet())
				if (e.getValue()!=null)
					select.add(e.getKey() + " as `"+e.getValue()+"`");
				else select.add(e.getKey());

			List<String> tables = new ArrayList();
			List<String> where = new ArrayList();
			Iterator<Map.Entry<String,String>> it = this.table.entrySet().iterator();
			Map.Entry<String,String> t0 = it.next();
			tables.add(tableExpr(t0)+" "+tableHint(t0));
			for (int i=1; i<table.size(); i++) {
				Map.Entry<String,String> tn = it.next();
				boolean negate = tableName(tn).startsWith("!");
				tables.add("left join "+tableExpr(tn)+
						" on `"+tn.getKey()+"`.id = `"+t0.getKey()+"`.id");
				if (!allTypes || !negate)
					where.add("t"+i+".id is "+(negate ? "" : "not ")+"null");
			}
			where.addAll(this.where);

			String sql = "";
			if (values.size()>0) {
				sql += tables.stream().collect(Collectors.joining("\n","update ",""));
				sql += "set "+values.keySet().stream().collect(Collectors.joining(" = ?, ","\n"," = ?"));
			} else {
				if (select.size()>0)
					sql += select.stream().collect(Collectors.joining(", ","select ",""));
				else sql += "select *";
				sql += tables.stream().collect(Collectors.joining("\n","\nfrom ",""));
			}
			if (where.size()>0)
				sql += where.stream().collect(Collectors.joining("\nand ","\nwhere ",""));
			return sql;
		}
	}

	static class RecordMapInfo {
		String[] fields = null;
		Class[] fieldtypes = null;
		BiConsumer[] setters = null;
	}

	RecordMapInfo getMapInfo(Class type, ResultSetMetaData rm) throws Exception {
		int cc = rm.getColumnCount();
		Map<String,String> xfields = new HashMap();
		Map<String,Class> xfieldtypes = new HashMap();
		Map<String,Method> xsetters = new HashMap();
		for (Pair<String,Class> g : ReflectHelper.getGetters(type).keySet()) {
			xfields.put(g.getFirst(),g.getFirst());
			xfieldtypes.put(g.getFirst(),g.getSecond());
			xsetters.put(g.getFirst(),ReflectHelper.getSetter(type,g.getFirst(),g.getSecond()));
		}
		OrmInfo info = info(type);
		for (int c=1; c<=cc; c++) {
			String cn = rm.getColumnName(c);
			String field = xfields.get(cn);
			if (field==null) {
				field = xfields.get(cn.replace("_id",""));
				if (field==null && Character.isUpperCase(cn.charAt(0)))
					field = xfields.get(Character.toLowerCase(cn.charAt(0))+cn.substring(1));
				if (field==null)
					for (Map.Entry<String,String> e : info.propColumn.entrySet()) {
						String prop = e.getKey();
						String column = e.getValue();
						if (column.equals(cn)) {
							field = prop;
							break;
						}
					}
				if (field!=null) {
					xfields.put(cn,field);
					xfieldtypes.put(cn,xfieldtypes.get(field));
					xsetters.put(cn,xsetters.get(field));
				} else {
					xfields.put(cn,cn);
				}
			}
		}
		RecordMapInfo m = new RecordMapInfo();
		m.fields = new String[cc];
		m.fieldtypes = new Class[cc];
		m.setters = new BiConsumer[cc];
		// TODO better use table names too and/or selects with explicit column names
		Set<String> hasSetter = new HashSet();
		for (int c=0; c<cc; c++) {
			String cn = rm.getColumnName(c+1);
			m.fields[c] = xfields.get(cn);
			m.fieldtypes[c] = xfieldtypes.get(cn);
			if (xsetters.get(cn)==null) continue;
			if (hasSetter.contains(cn)) continue;
			try {
				m.setters[c] = LambdaUtil.getSetter(xsetters.get(cn));
				hasSetter.add(cn);
			} catch (Exception ex) {
				throw ex;
			} catch (Throwable ex) {
				throw new Exception(ex);
			}
		}
		return m;
	}
}
