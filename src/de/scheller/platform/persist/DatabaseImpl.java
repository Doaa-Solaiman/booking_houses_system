package de.scheller.platform.persist;

import java.io.Closeable;
import java.io.IOException;
import java.io.Serializable;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.zip.Deflater;

import javax.persistence.OptimisticLockException;
import javax.persistence.Query;
import javax.persistence.Transient;
import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.esotericsoftware.reflectasm.FieldAccess;

import de.scheller.common.HasContext;
import de.scheller.common.HasId;
import de.scheller.common.HasLifecycleState;
import de.scheller.common.HasVersion;
import de.scheller.common.HasWorkflowState;
import de.scheller.common.LifecycleState;
import de.scheller.common.WorkflowState;
import de.scheller.fsm.FSM;
import de.scheller.model.persistence.CrudTimes;
import de.scheller.model.persistence.ITransactionData;
import de.scheller.platform.common.CollectionUtils;
import de.scheller.platform.common.MapUtils;
import de.scheller.platform.common.X;
import de.scheller.platform.persist.DatabaseCache.ResultSetIterator;
import de.scheller.platform.persist.DatabaseCache.RowsIterator;
import de.scheller.platform.persist.DatabaseOrm.OrmInfo;
import de.scheller.platform.persist.IServerBeanManager.BeanHandler.VetoButCommitException;
import de.scheller.platform.persist.IServerBeanManager.BeanHandler.VetoException;
import de.scheller.platform.persist.Parser.Statement;
import de.scheller.platform.persist.Parser.ValueExpr;
import de.scheller.platform.persist.Parser.ValueExpr.Condition;
import de.scheller.platform.persist.Parser.VarDecl;
import de.scheller.platform.persist.util.Id;
import de.scheller.platform.persist.util.LambdaUtil;
import de.scheller.platform.persist.util.ReflectHelper;
import de.scheller.transferobject.ITransferObject;
import de.scheller.transferobject.TransferObject2;
import de.scheller.transferobject.TransferObject2.ILazyLoader;
import de.scheller.util.Pair;

/**
 * - read access (DatabaseImpl)
 * -- is same for all
 * -- uses cache before direct db access
 * - write access (DatabaseImpl.WriteAccess)
 * -- encapsulates connection + transaction
 * -- is per thread or per thread-token
 * -- is direct db access
 * -- cache is updated and/or invalidated after successful write access
 * - read & write access have different connections
 *
 * @author kandzia
 */
public class DatabaseImpl implements DatabaseApi, IServerBeanManager
{
	private static final Logger logger = LoggerFactory.getLogger(
			DatabaseImpl.class.getPackage().getName()+"");

	DataSource ds;
	DatabaseOrm dbo;
	DatabaseCache dc;
	LazyLoader ll;

	public DatabaseImpl(DataSource ds, DatabaseOrm dbo, DatabaseCache dc) {
		this.ds = ds;
		this.dbo = dbo;
		this.dc = dc;
		this.ll = new LazyLoader();
		FSM.noodle(this,ILazyLoader.class,this.ll);
	}

	public void clearCaches() {
		dc.clearAll();
	}

	public DataSource getDataSource() throws SQLException {
		return ds;
	}

	public Connection getConnection() throws SQLException {
		return ds.getConnection();
	}

	public <T> T createInstance(Class<T> c) {
		return createInstance(c,true);
	}

	public <T> T createInstance(Class<T> c, boolean generateId) {
		T o = createImplInstance(c);
		// optimize away lots of find calls in save/assignManagedValues
		if (o instanceof TransferObject2)
			((TransferObject2)o).setTransferState(ITransferObject.TransferState.unknown);
		if (o instanceof HasId) {
			Class idClass = ReflectHelper.getClassArgumentFor(o.getClass(),TransferObject2.class);
			if (String.class.isAssignableFrom(idClass)) {
				String prefix = getPrefix(c);
				String id = !generateId ? prefix : (prefix!=null ? prefix+Id.next() : Id.next());
				((HasId)o).setId(id);
				return o;
			}
		}
		return o;
	}

	private <T> T createImplInstance(Class<T> c) {
		try {
			T instance = getEntityClass(c).newInstance();
			serializeOne(instance);
			return instance;
		} catch (Exception ex) {
			throw new IllegalArgumentException("creating instance of class failed",ex);
		}
	}

	public <T> List<T> createInstances(Class<T> c, int count, boolean generateId) {
		List<T> result = new ArrayList(count);
		String prefix = getPrefix(c);
		boolean setId = HasId.class.isAssignableFrom(c)
				|| createImplInstance(c) instanceof HasId;
		boolean checkSetId = true;
		for (int i=0; i<count; i++) {
			T o = createImplInstance(c);
			// optimize away lots of find calls in save/assignManagedValues
			if (o instanceof TransferObject2)
				((TransferObject2)o).setTransferState(ITransferObject.TransferState.unknown);
			if (o!=null && setId) {
				HasId oo = (HasId)o;
				String id = !generateId ? prefix : (prefix!=null ? prefix+Id.next() : Id.next());
				if (checkSetId) {
					checkSetId = false;
					try {
						oo.setId(id);
						oo.getId(); // return type may be different from setter param type
					} catch (ClassCastException ex) {
						setId = false;
						oo.setId(null);
					}
				} else {
					oo.setId(id);
				}
			}
			result.add(o);
		}
		return serialize(result);
	}

	public <T> String generateId(Class<T> c) {
		if (c==null) return null;
		String prefix = getPrefix(c);
		return prefix!=null ? prefix+Id.next() : Id.next();
	}

	private <T> String getPrefix2(Class<T> c) {
		if (c==null) return null;
		return dbo.info(c).prefix;
	}

	public <T> Class<T> getEntityClass(Class<T> type) {
		if (type==null) return null;
		if (dbo.modelImplTypes.containsKey(type))
			return type;
		return dbo.modelIntfTypes.get(type);
	}

	public <T> Class<T> getEntityClass(String type) {
		if (type==null) return null;
		return dbo.types.get(type);
	}

	public String getEntityName(Class type) {
		if (type==null) return null;
		if (dbo.modelImplTypes.containsKey(type))
			return dbo.modelImplTypes.get(type);
		return dbo.modelIntfTypes.get(type).getSimpleName();
	}

	public <T> Class<T> getEqualsClass(Class type) {
		if (type==null) return null;
		Class eqc = dbo.eqclass.get(getEntityClass(type));
		return eqc!=null ? eqc : type;
	}

	///////////////////////////////////////////////////////////////////////////
	// DB read access

	public long getCount(Class c) {
		Query q = createQuery("select count(*) from "+getEntityName(c));
		return ((Number)q.getSingleResult()).longValue();
	}

	public void invalidate(Class c, String id) {
		try {
			if (id==null) return;
			c = getEntityClass(c);
			DatabaseCache.ObjectCache oc = dc.getObjectCache(c);
			if (oc==null) return;
			oc.pos.remove(id);
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	public <T> T getBlank(Class<T> type, String id) {
		T o = createImplInstance(type);
		((HasId)o).setId(id);
		((TransferObject2)o).setTransferState(ITransferObject.TransferState.blank);
		return o;
	}

	private List<Serializable> getObjects(
			Class type, Map<Serializable,Object> byId, List<Serializable> ids)
	throws Exception {
		WriteAccess wa = shared(false,false);
		if (wa!=null) ids = wa.getTransactionObjects(type,byId,ids);
		return dc.getObjects(type,byId,ids);
	}

	private Map<Serializable,Object> byId(Class c, List ids) throws SQLException, Exception {
		c = getEntityClass(c);
		DatabaseCache.ObjectCache oc = dc.getObjectCache(c);
		if (oc==null) {
			Query q = ids!=null ?
					BeanManagerUtil.select(this,"*",c,0,ids,"x.id in (:id)",false) :
					BeanManagerUtil.select(this,"*",c);
			Connection co = null;
			PreparedStatement ps = null;
			ResultSet rs = null;
			try {
				co = ds.getConnection();
				ps = ((QueryImpl)q).getStatement(co,true);
				rs = ps.executeQuery();
				dc.putObjects(c,rs);
			} finally {
				QueryImpl.close(rs,ps,co);
			}
			oc = dc.getObjectCache(c);
		}
		if (ids==null) {
			// TODO add all ids of c in transaction cache
			long count = BeanManagerUtil.count(this,c);
			if (oc.pos.keySet().size()==count)
				ids = new ArrayList(oc.pos.keySet());
			else ids = BeanManagerUtil.getAll(this,"x.id",c);
		}
		Map<Serializable,Object> byId = new LinkedHashMap();
		List<Serializable> remaining = getObjects(c,byId,ids);
		if (remaining.size()>0) {
			ids = remaining;
			Query q = ids!=null ?
					BeanManagerUtil.select(this,"*",c,0,ids,"x.id in (:id)",false) :
					BeanManagerUtil.select(this,"*",c);
			Connection co = null;
			PreparedStatement ps = null;
			ResultSet rs = null;
			try {
				co = ds.getConnection();
				ps = ((QueryImpl)q).getStatement(co,true);
				rs = ps.executeQuery();
				dc.putObjects(c,rs);
			} finally {
				QueryImpl.close(rs,ps,co);
			}
			remaining = dc.getObjects(c,byId,ids);
		}
		if (remaining.size()>0) {
			logger.debug("not found {}",remaining);
			oc.markNotFound(remaining);
//			for (Serializable id : remaining)
//				byId.put(id,null);
		}
		for (Object o : byId.values())
			serializeOne(o);
//		Map<Serializable,Serializable> cache = llcache(c);
//		for (Map.Entry<Serializable,Object> e : byId.entrySet())
//			cache.put(e.getKey(),(Serializable)e.getValue());
		return byId;
	}

	public <T> List<T> getAll(Class<T> c) {
		try {
//			Query q = createQuery("from "+getEntityName(c));
//			return serialize(q.getResultList());
			return serialize(new ArrayList(byId(c,null).values()));
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	public List getAllValues(Class c, String field) {
		if (field==null)
			return null;
		try {
//			Query q = createQuery("select distinct "+field+" from "+getEntityName(c));
//			return serialize(q.getResultList());
			Map<Serializable,Object> byId = byId(c,null);
			c = getEntityClass(c);
			Method m = ReflectHelper.getGetter(c,field);
			Function getter = LambdaUtil.getGetter(m);
			Set result = new LinkedHashSet(byId.size()); // distinct
			for (Object o : byId.values())
				result.add(getter.apply(o));
			return new ArrayList(result);
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}
	}

	public <T> T load(T o) {
		if (o==null)
			return null;
		Class oc = o.getClass();
		if (oc.isArray()) {
			for (int n=Array.getLength(o), i=0; i<n; i++)
				Array.set(o,i,load(Array.get(o,i)));
			return o;
		}
		if (o instanceof Collection) {
			Collection c = (Collection)o;
			if (c.isEmpty()) return o;
			try {
				Map<Class,List<Serializable>> idsByClass = new HashMap();
				for (Object oo : c) {
					if (oo instanceof HasId) {
						MapUtils.groupT(idsByClass,ArrayList.class,oo.getClass(),((HasId)oo).getId());
					} else if (oo!=null && oo.getClass().isArray()) {
						for (int i=0; i<Array.getLength(oo); i++) {
							Object ooo = Array.get(oo,i);
							if (ooo instanceof HasId)
								MapUtils.groupT(idsByClass,ArrayList.class,ooo.getClass(),((HasId)ooo).getId());
						}
					}
				}
				Map<Serializable,Object> loadedById = new HashMap();
				for (Map.Entry<Class,List<Serializable>> e : idsByClass.entrySet())
					loadedById.putAll(byId(e.getKey(),e.getValue()));
				Collection result = CollectionUtils.createCollectionT(c);
				for (Object oo : c)
					if (oo instanceof HasId) {
						result.add(loadedById.get(((HasId)oo).getId()));
					} else {
						if (oo!=null && oo.getClass().isArray()) {
							for (int i=0; i<Array.getLength(oo); i++) {
								Object ooo = Array.get(oo,i);
								if (ooo instanceof HasId)
									Array.set(oo,i,loadedById.get(((HasId)ooo).getId()));
							}
						}
						result.add(oo);
					}
				return (T)result;
			} catch (Exception ex) {
				throw new RuntimeException(ex);
			}
		}
		if (o instanceof HasId)
			return (T)load(oc,((HasId)o).getId());
		return o;
	}

	public <T extends Serializable> T load(Class<T> c, Serializable id) {
		try {
			if (id==null) return null;
			Map<Serializable,Object> byId = byId(c,Arrays.asList(id));
			return serialize(byId.size()>0 ? (T)byId.values().iterator().next() : null);
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

//	public List query(Query q, String runKey, Collection runValues) {
//		// TODO performance prüfen
//		int rvsize = 10000;
//		int size = runValues.size();
//		if (size==0)
//			return Collections.EMPTY_LIST;
//		if (size<=rvsize) {
//			q.setParameter(runKey,runValues);
//			return q.getResultList();
//		}
//		List rv = new ArrayList(Math.min(rvsize,size));
//		List result = new ArrayList(runValues.size());
//		for (Object v : runValues) {
//			rv.add(v);
//			if (--size==0 || rv.size()==rvsize) {
//				q.setParameter(runKey,rv);
//				result.addAll(q.getResultList());
//				if (size>0) rv.clear();
//			}
//		}
//		return result;
//	}
//	public <T,S extends Serializable> Collection<T> loadBulk(Class<T> c, Collection<S> ids) {
//		StringBuilder sb = new StringBuilder();
//		sb.append("FROM ").append(getEntityName(c));
//		sb.append(" WHERE ").append(getIdentifierName(c));
//		sb.append(" in (:ids)");
//		return query(createQuery(sb.toString()),"ids",BeanManagerUtil.pad(bm,ids));
//	}
	public <T,S extends Serializable> Collection<T> loadBulk(Class<T> c, Collection<S> ids) {
		try {
			if (ids==null)
				return Collections.EMPTY_LIST;
			return serialize(new ArrayList(byId(c,new ArrayList(ids)).values()));
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	public <S extends Serializable> Map<S,?> load(Class c, Collection<S> ids, String field) {
		try {
			if (ids==null)
				return Collections.EMPTY_MAP;
			Map<Serializable,Object> byId = byId(c,new ArrayList(ids));
			if (field==null)
				return serialize((Map<S,?>)byId);
			return serialize(extract(c,byId,field));
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}
	}

	public <S extends Serializable> Map<S,?> extract(Class c, Map<Serializable,Object> byId, String field) {
		try {
			c = getEntityClass(c);
			Method m = ReflectHelper.getGetter(c,field);
			Function getter = LambdaUtil.getGetter(m);
			Map result = new LinkedHashMap();
			for (Map.Entry<Serializable,Object> e : byId.entrySet())
				result.put(e.getKey(),getter.apply(e.getValue()));
			return result;
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}
	}

	// BeanManager variant that supports multiple fields
//	public <S extends Serializable> Map<S,Object> load(Class c, Collection<S> ids, String field) {
//		if (ids==null || ids.isEmpty())
//			return Collections.EMPTY_MAP;
//		boolean multipleFields = field!=null && field.contains(",");
//		if (ids.size()==1 && !multipleFields) { // one ID, one field, use cache
//			Map<S,Object> result = new HashMap();
//			S id = ids.iterator().next();
//			Object o = find(c,id);
//			if (o==null)
//				return result;
//			if (field!=null)
//				o = ReflectHelper.get(o,field);
//			result.put(id,o);
//			return result;
//		}
//		if (field==null) { // no fields, return objects-by-ID
//			Map<S,Object> result = new HashMap();
//			Collection<HasId> os = loadBulk(c,ids);
//			for (HasId o : os)
//				result.put((S)o.getId(),o);
//			return result;
//		}
//		if (!multipleFields) { // field is collection reference? use getter on objects
//			// check for collection reference, e.g. many-to-many collection
//			Reference cref = null;
//			for (Reference r : bm.getCollectionRefs()) {
//				if (c.getName().equals(r.referrerClass) && field.equals(r.referrerField)) {
//					cref = r;
//					break;
//				}
//			}
//			if (cref!=null) {
//				Map<S,Object> result = new HashMap();
//				Collection<HasId> os = loadBulk(c,ids);
//				for (HasId o : os)
//					result.put((S)o.getId(),ReflectHelper.get(o,field));
//				return result;
//			}
//		}
//		// finally, only load whats requested, avoid object creation
//		if (multipleFields)
//			field = field.replace(",",",x.");
//		// TODO performance prüfen
//		StringBuilder sb = new StringBuilder();
//		sb.append("SELECT x.").append(getIdentifierName(c)).append(",x.").append(field);
//		sb.append(" FROM ").append(getEntityName(c)).append(" x");
//		sb.append(" WHERE x.").append(getIdentifierName(c)).append(" in (:ids)");
//		List<Object[]> os = query(createQuery(sb.toString()),"ids",BeanManagerUtil.pad(bm,ids));
//		Map<S,Object> result = new HashMap();
//		if (multipleFields) {
//			for (Object[] o : os)
//				result.put((S)o[0],o);
//		} else {
//			for (Object[] o : os)
//				result.put((S)o[0],o[1]);
//		}
//		return result;
//	}

	///////////////////////////////////////////////////////////////////////////

	public <T> IResult<T> queryIds(
			int blockSize, Class<T> entityType, Object... idAndValueAndExpr) {
		return new Result(this,blockSize,null,entityType,idAndValueAndExpr);
	}

	public <T> IResult<T> query(
			int blockSize, Class<T> entityType, Object... idAndValueAndExpr) {
		return new Result(this,blockSize,"*",entityType,idAndValueAndExpr);
	}

	public IResult query(
			int blockSize, String fields, Class entityType, Object... idAndValueAndExpr) {
		return new Result(this,blockSize,fields,entityType,idAndValueAndExpr);
	}

	public class Result<T> implements IResult<T> {
		private final IServerBeanManager sbm;
		private final int blockSize;
		private final String fields;
		private final Class<T> entityType;
		private final Object[] idAndValueAndExpr;
		private Iterator it;

		public Result(IServerBeanManager sbm, int blockSize,
				String fields, Class<T> entityType, Object... idAndValueAndExpr) {
			this.sbm = sbm;
			this.blockSize = blockSize;
			this.fields = fields;
			this.entityType = entityType;
			this.idAndValueAndExpr = idAndValueAndExpr;
			// replace alternative class with entity class, e.g. if ModelBuilder is used
			for (int n=idAndValueAndExpr.length, i=0; i<n; i+=4)
				if (i+1<n && idAndValueAndExpr[i+1] instanceof Class) {
					Class c = getEntityClass((Class)idAndValueAndExpr[i+1]);
					if (c!=null) idAndValueAndExpr[i+1] = c;
				}
		}

		public Iterator<List<T>> iterator() {
			Query q = fields!=null ?
					BeanManagerUtil.select(sbm,fields,entityType,idAndValueAndExpr) :
					BeanManagerUtil.select(sbm,entityType,idAndValueAndExpr);
			return new ResultIterator<T>(it = sbm.stream(q),blockSize);
		}

		public int count() {
			Query q = BeanManagerUtil.select(sbm,"count(x)",entityType,idAndValueAndExpr);
			return ((Number)q.getSingleResult()).intValue();
		}

		public void close() {
			if (it==null) return;
			// it.close() can take time...
			// https://stackoverflow.com/questions/2447324/streaming-large-result-sets-with-mysql
			// https://bugs.mysql.com/bug.php?id=42929
			Iterator itToclose = it;
			it = null;
			closer.execute(() -> {
				Thread.currentThread().setName("close database resultset");
				try {
					((Closeable)itToclose).close();
				} catch (IOException ex) {
					ex.printStackTrace();
				}
			});
		}
	}

	private static Executor closer = executor(0,4,1000,0);

	static ThreadPoolExecutor executor(int coreThreads, int maxThreads, int maxIdleMs, int queueLimit) {
		BlockingQueue queue = queueLimit<=0 ?
				new LinkedBlockingQueue<Runnable>() : new ArrayBlockingQueue<Runnable>(queueLimit);
		ThreadPoolExecutor threadPool = new ThreadPoolExecutor(
				coreThreads,maxThreads,maxIdleMs,TimeUnit.MILLISECONDS,queue);
		if (queueLimit>0)
			threadPool.setRejectedExecutionHandler(new RejectedExecutionHandler() {
				public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
					try {
						executor.getQueue().put(r); // blocks if the queue is full
					} catch (InterruptedException ignore) {}
				}
			});
		return threadPool;
	}

	public class ResultIterator<T> implements Iterator<List<T>> {
		private final Iterator it;
		private final int blockSize;

		public ResultIterator(Iterator it, int blockSize) {
			this.it = it;
			this.blockSize = blockSize;
		}

		public boolean hasNext() {
			return it.hasNext();
		}

		public List<T> next() {
			List<T> block = new ArrayList(blockSize);
			for (int i=0; it.hasNext() && i<blockSize; i++)
				block.add((T)it.next());
			return serialize(load(block));
		}
	}

	public Iterator stream(Query q) {
		if (q instanceof QueryImpl==false)
			throw new IllegalArgumentException();
		Connection co = null;
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			co = ds.getConnection();
			ps = ((QueryImpl)q).getStatement(co,true);
			Class type = ((QueryImpl)q).getEntityType();
			Class rtype = ((QueryImpl)q).getResultType();
			Map<String,Class> types = ((QueryImpl)q).fieldTypes;
			rs = ps.executeQuery();
			ResultSetMetaData rm = rs.getMetaData();
			int columnCount = rm.getColumnCount();
			if (columnCount>1
					|| (types.get("0")!=null && ITransferObject.class.isAssignableFrom(types.get("0")))
					|| rtype!=null && ITransferObject.class.isAssignableFrom(rtype))
				return new RowsIterator(rs,type,rtype,types,dbo) {
					@Override
					protected Object readRow() throws Exception {
						return serialize(super.readRow());
					}
				};
			return new ResultSetIterator(rs) {
				@Override
				protected Object readRow() throws Exception {
					return serialize(rs.getObject(1));
				}
			};
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	///////////////////////////////////////////////////////////////////////////

	public List<Reference> getObjectRefs() {
		return null;
	}

	public List<Reference> getCollectionRefs() {
		return null;
	}

	public String getIdentifierName(Class entityType) {
		entityType = getEntityClass(entityType);
		OrmInfo ormInfo = dbo.infos.get(entityType);
		return ormInfo!=null ? ormInfo.pkColumn : null;
	}

	public Query createQuery(String jpaql) {
		return new QueryImpl(this,dbo,ds,jpaql);
	}

	static Statement transformStatement(DatabaseOrm dbo, Statement statement, boolean insert) {
//		StringBuilder sql = new StringBuilder();
//		new ParserSqlBuilder() {
//			@Override
//			protected void print(String s) {
//				sql.append(s);
//			}
//		}.visit(statement);

		Map<String,Class> fieldTypes = new LinkedHashMap();
		boolean aliasOnly = false;
		boolean implicitXId = false;
		boolean containsWildcard = false;
		if (statement instanceof Statement.Select) {
			Statement.Select s = (Statement.Select)statement;

			class Util {
				void checkAlias(List<VarDecl.RangeDecl> ranges, Set<String> aliases) {
					if (ranges.size()==0)
						return;
					if (ranges.size()>1)
						throw new UnsupportedOperationException("multiple ranges without aliases (cross product)");

					checkAlias(ranges.get(0),aliases);
				}
				void checkAlias(VarDecl.RangeDecl range, Set<String> aliases) {
					String[] defaultAliases = { "x","_","alias" };
					String defaultAlias = null;
					for (String a : defaultAliases)
						if (!aliases.contains(a)) {
							defaultAlias = a;
							break;
						}
					if (defaultAlias==null)
						throw new RuntimeException("could not decide on default alias");
					range.alias = defaultAlias;
				}
				void checkAlias(List<ValueExpr.PathExpr> paths, Set<String> aliases, Map<String,Class> entities) {
					for (ValueExpr.PathExpr path : paths)
						checkAlias(path,aliases,entities);
				}
				void checkAlias(ValueExpr.PathExpr path, Set<String> aliases, Map<String,Class> entities) {
					if (aliases.contains(path.start))
						return;
					if ("*".equals(path.start))
						return;

					String alias = null;
					if (entities.size()==1) {
						alias = entities.keySet().iterator().next();
					} else {
						String field = path.start;
						for (Map.Entry<String,Class> e : entities.entrySet()) {
							OrmInfo info = dbo.info(e.getValue());
							if (info==null) continue;
							if (!info.columnTable.containsKey(field)) {
								int cut = field.lastIndexOf('_');
								if (cut>0) {
									String f = field.substring(0,cut);
									if (!info.targetTable.containsKey(f)) continue;
								} else continue;
							}
							if (alias!=null) { // ambigious
								alias = null;
								break;
							}
							alias = e.getKey();
						}
					}
					if (alias==null)
						throw new RuntimeException("could not decide on alias for path "+path);
					if (path.access==null)
						path.access = new LinkedList();
					path.access.addFirst(path.start);
					path.start = alias;
				}
				String table(Class type, String field) {
					OrmInfo info = dbo.info(type);
					if (info==null) return null;
					String table = info.targetTable.get(field);
					if (table==null) {
						int cut = field.lastIndexOf('_');
						if (cut>0) {
							String f = field.substring(0,cut);
							table = info.targetTable.get(f);
						}
					}
					return table;
				}
				boolean optional(Class type, String field) {
					OrmInfo info = dbo.info(type);
					if (info==null) return false;
					boolean optional = info.optional.contains(field);
					if (!optional) {
						int cut = field.lastIndexOf('_');
						if (cut>0) {
							String f = field.substring(0,cut);
							optional = info.optional.contains(f);
						}
					}
					return optional;
				}
				String dummyAlias(Set<String> aliases) {
					String a = "";
					for (int i=0; i<100; i++) {
						a += "_";
						if (!aliases.contains(a))
							return a;
					}
					throw new RuntimeException("could not find dummy alias");
				}
				String nextAlias(String alias, Set<String> aliases) {
					for (int i=0; i<100; i++) {
						String a = alias+i;
						if (!aliases.contains(a))
							return a;
					}
					throw new RuntimeException("could not find next alias for "+alias);
				}
				String tableName(String tableAndHints) {
					return tableAndHints.replaceFirst("\\|.*$","");
				}
			}
			Util util = new Util();

			class Stats {
				Set<String> aliases = new LinkedHashSet();
				Map<ValueExpr.PathExpr,Integer> selectedFields = new LinkedHashMap();
				List<VarDecl.RangeDecl> rangesWoAlias = new ArrayList();
				List<VarDecl.RangeDecl> ranges = new ArrayList();
				List<ValueExpr.PathExpr> paths = new ArrayList();
			}
			Stats stats = new Stats();
			new ParserResultVisitorBase() {
				@Override
				protected void visit(Statement.Select s) {
					if (s.expr!=null) {
						int index = 0;
						for (Map.Entry<ValueExpr,String> e : s.expr.entrySet()) {
							ValueExpr expr = e.getKey();
							visit(expr);
							if (expr instanceof ValueExpr.PathExpr)
								stats.selectedFields.put((ValueExpr.PathExpr)expr,index);
							index++;
						}
					}
					if (s.from!=null)
						for (VarDecl d : s.from)
							visit(d);
					if (s.where!=null)
						visit(s.where);
					if (s.group!=null)
						for (ValueExpr e : s.group)
							visit(e);
					if (s.order!=null)
						for (Map.Entry<ValueExpr,String> e : s.order.entrySet())
							visit(e.getKey());
					if (s.having!=null)
						visit(s.having);
				}
				@Override
				protected void visit(Statement.Subquery s) {
					throw new UnsupportedOperationException("subquery not supported");
				}
				@Override
				protected void visit(Condition.BoolExpr c) {
					for (int i=0; i<c.expr.size(); i++)
						visit(c.expr.get(i));
				}
				@Override
				protected void visit(ValueExpr.Case f) {
					for (int i=0; i<f.args.size(); i++)
						visit(f.args.get(i));
				}
				@Override
				protected void visit(ValueExpr.Function f) {
					for (int i=0; i<f.args.size(); i++)
						visit(f.args.get(i));
				}
				@Override
				protected void visit(ValueExpr.Operation o) {
					visit(o.left);
					visit(o.right);
				}
				@Override
				protected void visit(VarDecl.JoinDecl j) {
					visit((VarDecl.RangeDecl)j);
					if (j.on!=null)
						visit(j.on);
				}
				/////////////////////
				@Override
				protected void visit(VarDecl.RangeDecl r) {
					if (r.alias==null) stats.rangesWoAlias.add(r);
					else stats.aliases.add(r.alias);
					stats.ranges.add(r);
				}
				@Override
				protected void visit(ValueExpr.PathExpr p) {
					stats.paths.add(p);
				}
			}.visit(statement);
			// check ranges and joins for missing aliases
			util.checkAlias(stats.rangesWoAlias,stats.aliases);
			for (VarDecl.RangeDecl r : stats.rangesWoAlias)
				stats.aliases.add(r.alias);

			// collect types and names from ranges and joins
			Map<String,Class> entities = new LinkedHashMap(); // K=alias, V=entity
			Map<String,String> tables = new LinkedHashMap(); // K=alias, V=tableName
			for (VarDecl.RangeDecl r : stats.ranges) {
				// assume table name (e.g. MDC_Item)
				String table = r.path.start;
				Class type = dbo.types.get(table);
				if (type==null && !dbo.types.containsKey(table)) {
					// r.path.start is alias, so check path to get type and name (e.g. i.itemType)
					util.checkAlias(r.path,stats.aliases,entities);
					type = entities.get(r.path.start);
					for (String field : r.path.access) {
						table = util.table(type,field);
						type = dbo.types.get(table);
						if (type==null)
							throw new RuntimeException("could not determine type of "+field+" in path "+r.path);
					}
				}
				entities.put(r.alias,type);
				tables.put(r.alias,table);
			}
			// check aliases for pathes, set from existing ranges + joins (no type hierarchy)
			util.checkAlias(stats.paths,stats.aliases,entities);
			for (ValueExpr.PathExpr p : stats.paths)
				if (!"*".equals(p.start))
					stats.aliases.add(p.start);

			List<VarDecl.RangeDecl> ranges = new ArrayList();
			Map<Entry<String,String>,VarDecl.JoinDecl> joinByPath = new LinkedHashMap();
			List<ValueExpr.PathExpr> paths = new ArrayList();
			Map<ValueExpr.PathExpr,VarDecl.RangeDecl> rangeByPath = new HashMap();
			// collect all paths, also from ranges (ParserSqlBuilder doesn't collect them)
			for (VarDecl.RangeDecl r : stats.ranges) {
				if (stats.aliases.contains(r.path.start)) { // modify
					paths.add(r.path);
					rangeByPath.put(r.path,r);
				} else ranges.add(r); // keep
			}
			paths.addAll(stats.paths);
			// search necessary joins for ranges (type hierarchy)
			// only for explicit wildcard (with and without alias)
			// TODO check if join already exists...
			Set<String> expandRanges = new HashSet();
			for (ValueExpr.PathExpr p : stats.selectedFields.keySet()) {
				if ("*".equals(p.start)) {
					containsWildcard = true;
					for (VarDecl.RangeDecl r : ranges)
						expandRanges.add(r.alias);
				} else if (p.access!=null && "*".equals(p.access.get(0))) {
					containsWildcard = true;
					expandRanges.add(p.start);
				}
			}
			// collect joins for type hierarchy (if exists)
			// collect type column for type hierarchy with outer join (e.g. ORG_Resource, !ORG_OrgPerson)
			Map<ValueExpr,String> classCase = new LinkedHashMap();
			for (String a : expandRanges) {
				String tableName = tables.get(a);
				Class type = entities.get(a);
				OrmInfo info = dbo.info(type);
				if (info==null) continue; // exception?

				List<ValueExpr> whenThans = new ArrayList();
				for (String table : info.tables) {
					if (tableName.equals(table))
						continue;
					table = util.tableName(table);
					boolean negate = table.startsWith("!");
					if (negate) table = table.substring(1);
					String alias = util.nextAlias(a,stats.aliases);
					stats.aliases.add(alias);
//					OrmInfo tinfo = dbo.info(table);
					Entry key = new SimpleEntry(table,a+"."+info.pkColumn);
					joinByPath.put(key,new VarDecl.JoinDecl(
							new ValueExpr.PathExpr(table),alias,
							negate,false,false,
							new ValueExpr.Condition.Compare(false,
//									new ValueExpr.PathExpr(alias,tinfo.pkColumn),
									new ValueExpr.PathExpr(alias,info.pkColumn),
									new ValueExpr.PathExpr(a,info.pkColumn),
									true,false,false)));
					if (negate)
						whenThans.add(new Condition.WhenThen(
								new Condition.Null(true,new ValueExpr.PathExpr(alias,info.pkColumn)),
								new ValueExpr.Literal("'"+table+"'")));
				}
				if (whenThans.size()>0) {
					whenThans.add(new Condition.WhenThen(
							new Condition.Null(true,new ValueExpr.PathExpr(a,info.pkColumn)),
							new ValueExpr.Literal("'"+tableName+"'")));
					classCase.put(new ValueExpr.Case(whenThans),"_table_"+a);
				}
			}
			// adapt paths, e.g.
			//   x -> x.id
			//   x.parent.id -> x.parent_id
			//   x.parent -> x.parent_id (only if x.parent targets table)
			// fieldTypes: store user decision, e.g.
			//   x.parent -> user want's bean
			//   x.parent.id -> user want's ID value
			for (ValueExpr.PathExpr p : paths) {
				Class type = entities.get(p.start);
				if (p.access==null) {
					if (type!=null) {
						OrmInfo info = dbo.info(type);
						String pk = info.pkColumn;
						p.access = new LinkedList(Arrays.asList(pk));
						if (stats.selectedFields.containsKey(p)) {
							aliasOnly = true;
							fieldTypes.put(String.valueOf(stats.selectedFields.get(p)),type);
						}
					}
					continue;
				}
				boolean lastIsPk = false;
				for (int i=0, size=p.access.size(); i<size; i++) {
					String field = p.access.get(i);
					OrmInfo info = dbo.info(type);
					if (info!=null) { // TODO support many-to-many link tables
						String f = info.propColumn.get(field);
						if (f!=null && !f.equals(field))
							p.access.set(i,field = f);
					}
					String table = util.table(type,field);
					if (table==null) {
						if (i!=size-1)
							throw new RuntimeException("path could not be resolved "+p);
						lastIsPk = type!=null && dbo.info(type).pkColumn.equals(field);
					}
					type = dbo.types.get(table);
				}
				if (lastIsPk && p.access.size()>1) {
					String pk = p.access.removeLast();
					String field = p.access.getLast();
					if (!field.endsWith("_"+pk))
						p.access.set(p.access.size()-1,field+"_"+pk);
					if (stats.selectedFields.containsKey(p))
						fieldTypes.put(String.valueOf(stats.selectedFields.get(p)),Object.class);
				} else if (!lastIsPk && type!=null) {
					OrmInfo info = dbo.info(type);
					String pk = info.pkColumn;
					String field = p.access.getLast();
					if (!field.endsWith("_"+pk)) {
						p.access.set(p.access.size()-1,field+"_"+pk);
						if (stats.selectedFields.containsKey(p))
							fieldTypes.put(String.valueOf(stats.selectedFields.get(p)),type);
					} else {
						if (stats.selectedFields.containsKey(p))
							fieldTypes.put(String.valueOf(stats.selectedFields.get(p)),Object.class);
					}
				}
			}
			// search necessary joins for paths
			// simplePaths: alias + field
			List<ValueExpr.PathExpr> simplePaths = new ArrayList();
			// pathsForJoin: alias + fields
			List<ValueExpr.PathExpr> pathsForJoin = new ArrayList();
			for (ValueExpr.PathExpr p : paths) {
				if (p.access==null) // no checks needed
					continue;
				VarDecl.RangeDecl r = rangeByPath.get(p);
				if (r==null && p.access.size()<=1)
					simplePaths.add(p);
				else pathsForJoin.add(p);
			}
			// step through path + check type of field owner + check if join is needed + adapt path and alias
			for (ValueExpr.PathExpr p : pathsForJoin) {
				VarDecl.RangeDecl r = rangeByPath.get(p);
				VarDecl.JoinDecl j = r instanceof VarDecl.JoinDecl ? (VarDecl.JoinDecl)r : null;
				String a = p.start;
				Class type = entities.get(a);
				String table = tables.get(a);
				OrmInfo info = dbo.info(type);
				int size = p.access.size();
				if (r==null) size = size-1;
				String dummyAlias = null;
				for (int i=0; i<size; i++) {
					String field = p.access.get(i);
					boolean optional = util.optional(type,field);
					String t = info.columnTable.get(field);
					if (t==null) {
						int cut = field.lastIndexOf('_');
						if (cut>0) {
							String f = field.substring(0,cut);
							t = info.columnTable.get(f);
						}
					}
					if (t!=null && !table.equals(t)) {
						Entry key = new SimpleEntry(t,a+"."+info.pkColumn);
						VarDecl.JoinDecl join = joinByPath.get(key);
						String alias;
						if (join!=null) {
							alias = join.alias;
						} else {
							alias = util.nextAlias(a,stats.aliases);
							stats.aliases.add(alias);
							joinByPath.put(key,new VarDecl.JoinDecl(
									new ValueExpr.PathExpr(t),alias,
									false,false,false,
									new ValueExpr.Condition.Compare(false,
											new ValueExpr.PathExpr(alias,info.pkColumn),
											new ValueExpr.PathExpr(a,info.pkColumn),
											true,false,false)));
						}
						p.start = a = alias;
					}
					t = util.table(type,field);
					type = dbo.types.get(t);
					String pk = dbo.info(type).pkColumn;
					if (!field.endsWith("_"+pk))
						field = field+"_"+pk;
					Entry key = new SimpleEntry(t,a+"."+field);
					VarDecl.JoinDecl join = joinByPath.get(key);
					String alias;
					if (join!=null) {
						alias = join.alias;
					} else {
						if (r!=null)
							alias = i==size-1 ? r.alias : util.nextAlias(r.alias,stats.aliases);
						else {
							alias = i==size-1
									? dummyAlias = util.dummyAlias(stats.aliases)
									: util.nextAlias(dummyAlias,stats.aliases); // need alias here
						}
						stats.aliases.add(alias);
						joinByPath.put(key,new VarDecl.JoinDecl(
								new ValueExpr.PathExpr(t),alias,
								j!=null ? j.left : optional,j!=null ? j.right : false,
								j!=null ? j.fetch : false,
								new ValueExpr.Condition.Compare(false,
										new ValueExpr.PathExpr(alias,pk),
										new ValueExpr.PathExpr(a,field),
										true,false,false)));
					}
					a = alias;
				}
				p.start = a;
				p.access = new LinkedList(Arrays.asList(p.access.getLast()));
			}
			// check type of field owner + check if join is needed + may adapt alias
			for (ValueExpr.PathExpr p : simplePaths) {
				String a = p.start;
				Class type = entities.get(a);
				String table = tables.get(a);
				OrmInfo info = dbo.info(type);
				if (info==null) continue;

				String field = p.access.get(0);
				String t = info.columnTable.get(field);
				if (t==null) {
					int cut = field.lastIndexOf('_');
					if (cut>0) {
						String f = field.substring(0,cut);
						t = info.columnTable.get(f);
					}
				}
				if (t==null) continue;
				if (!table.equals(t) && !info.pkColumn.equals(field)) {
					Entry key = new SimpleEntry(t,a+"."+info.pkColumn);
					VarDecl.JoinDecl join = joinByPath.get(key);
					String alias;
					if (join!=null) {
						alias = join.alias;
					} else {
						alias = util.nextAlias(a,stats.aliases);
						stats.aliases.add(alias);
						joinByPath.put(key,new VarDecl.JoinDecl(
								new ValueExpr.PathExpr(t),alias,
								false,false,false,
								new ValueExpr.Condition.Compare(false,
										new ValueExpr.PathExpr(alias,info.pkColumn),
										new ValueExpr.PathExpr(a,info.pkColumn),
										true,false,false)));
					}
					p.start = alias;
				}
			}
			Map<ValueExpr,String> expr = s.expr;
			if (expr==null) {
				// select fields empty, hql: from ...
				expr = new LinkedHashMap();
				expr.put(new ValueExpr.PathExpr(stats.aliases.iterator().next(),"id"),null);
				implicitXId = true;
			}
			if (classCase.size()>0)
				expr.putAll(classCase);
			// combine existing ranges and additional joins
			List<VarDecl> from = new ArrayList(ranges.size()+joinByPath.size());
			from.addAll(ranges);
			from.addAll(joinByPath.values());
			statement = new Statement.Select(s.distinct,
					expr,from,s.where,s.group,s.having,s.order,s.limit,s.offset);
			// now statement modification should be completed
		} else {
			VarDecl.RangeDecl range = null;
			Collection<ValueExpr.PathExpr> expr;
			boolean useAlias = false;
			if (statement instanceof Statement.Update) {
				Statement.Update update = (Statement.Update)statement;
				range = (VarDecl.RangeDecl)update.range;
				expr = update.values.keySet();
				useAlias = !insert;
			} else if (statement instanceof Statement.Delete) {
				Statement.Delete delete = (Statement.Delete)statement;
				range = (VarDecl.RangeDecl)delete.from;
				expr = new ArrayList();
				new ParserResultVisitorBase() {
					@Override
					protected void visit(Statement.Subquery s) {
						throw new UnsupportedOperationException("subquery not supported");
					}
					@Override
					protected void visit(Condition.BoolExpr c) {
						for (int i=0; i<c.expr.size(); i++)
							visit(c.expr.get(i));
					}
					@Override
					protected void visit(ValueExpr.Function f) {
						for (int i=0; i<f.args.size(); i++)
							visit(f.args.get(i));
					}
					/////////////////////
					@Override
					protected void visit(ValueExpr.PathExpr p) {
						expr.add(p);
					}
				}.visit(delete.where);
				useAlias = false;
			} else expr = Collections.EMPTY_LIST;
			if (range!=null) {
				String alias = range.alias;
				if (useAlias && alias==null) alias = "x";
				range.alias = useAlias ? alias : null;
				if (useAlias || alias!=null) {
					for (ValueExpr.PathExpr p : expr) {
						if (useAlias && !alias.equals(p.start)) {
							if (p.access==null)
								p.access = new LinkedList();
							p.access.addFirst(p.start);
							p.start = alias;
						} else if (!useAlias && alias.equals(p.start)) {
							p.start = p.access.removeFirst();
							if (p.access.size()==0)
								p.access = null;
						}
					}
				}
				String table = range.path.start;
				OrmInfo info = dbo.info(table);
				if (info!=null) {
					for (ValueExpr.PathExpr p : expr) {
						String field = useAlias ? p.access.getFirst() : p.start;
						String column = info.propColumn.get(field);
						if (column!=null && !column.equals(field)) {
							if (useAlias) p.access.set(0,column);
							else p.start = column;
						}
						String ttable = info.targetTable.get(field);
						if (ttable!=null) {
							field += "_"+dbo.info(ttable).pkColumn;
							if (useAlias) p.access.set(0,field);
							else p.start = field;
						}
					}
				}
			}
		}
		statement.meta.put("insert",insert);
		statement.meta.put("aliasOnly",aliasOnly);
		statement.meta.put("implicitXId",implicitXId);
		statement.meta.put("containsWildcard",containsWildcard);
		statement.meta.put("fieldTypes",fieldTypes);
		return statement;
	}

	final Map<String,Parser.Statement> qcache = new LinkedHashMap();

	Parser.Statement parseQuery(String jpaql) {
		return parseQuery(jpaql,false);
	}
	Parser.Statement parseQuery(String jpaql, boolean insert) {
		String key = insert+"|"+jpaql; // we know.. the insert part is a bit hacky currently
		Parser.Statement s = qcache.get(key);
		if (s==null) {
			s = Parser.statement(jpaql);
			s = transformStatement(dbo,s,insert);
			qcache.put(key,s);
			logger.debug("query cache {}",qcache.size());
		}
		return s;
	}

	String generateSql(Statement statement) {
		return generateSql_(dbo,statement,null,null);
	}
	String generateSql(Statement statement, Map<String,Class> fieldTypes, List<String> params) {
		return generateSql_(dbo,statement,fieldTypes,params);
	}
	private static String generateSql_(DatabaseOrm dbo, Statement statement,
			Map<String,Class> fieldTypes, List<String> params) {
		Map<String,Class> fieldTypesMeta = (Map<String,Class>)statement.meta.get("fieldTypes");
		final boolean insert = (boolean)statement.meta.get("insert");
		final boolean aliasOnly = (boolean)statement.meta.get("aliasOnly");
		final boolean implicitXId = (boolean)statement.meta.get("implicitXId");
		final boolean containsWildcard = (boolean)statement.meta.get("containsWildcard");
		class Stats {
			final Map<String,Class> entities = new LinkedHashMap(); // K=alias, V=entity
			Class entity;
			boolean returnBeans = false;
		}
		Stats stats = new Stats();
		new ParserResultVisitorBase() {
			@Override
			protected void visit(Statement.Select s) {
				if (s.expr!=null) {
					ValueExpr single = null;
					for (Map.Entry<ValueExpr,String> e : s.expr.entrySet()) {
						String alias = e.getValue();
						if (alias!=null && alias.startsWith("_table_")) // filter "type" columns
							continue;
						if (single!=null) { // no single expr
							single = null;
							break;
						}
						single = e.getKey();
					}
					if (single instanceof ValueExpr.PathExpr) {
						ValueExpr.PathExpr p = (ValueExpr.PathExpr)single;
						stats.returnBeans = aliasOnly || implicitXId ||
								p.access==null || (p.access.size()==1 && "*".equals(p.access.get(0)));
					}
					for (Map.Entry<ValueExpr,String> e : s.expr.entrySet())
						visit(e.getKey());
				}
				if (s.from!=null)
					for (VarDecl d : s.from)
						visit(d);
				if (s.where!=null)
					visit(s.where);
				if (s.order!=null)
					for (Map.Entry<ValueExpr,String> e : s.order.entrySet())
						visit(e.getKey());
			}
			@Override
			protected void visit(Statement.Subquery s) {
				throw new UnsupportedOperationException("subquery not supported");
			}
			@Override
			protected void visit(Condition.BoolExpr c) {
				for (int i=0; i<c.expr.size(); i++)
					visit(c.expr.get(i));
			}
			@Override
			protected void visit(ValueExpr.Function f) {
				for (int i=0; i<f.args.size(); i++)
					visit(f.args.get(i));
			}
			@Override
			protected void visit(VarDecl.JoinDecl j) {
				visit((VarDecl.RangeDecl)j);
				if (j.on!=null)
					visit(j.on);
			}
			/////////////////////
			@Override
			protected void visit(VarDecl.RangeDecl r) {
				String table = r.path.start;
				Class type = dbo.types.get(table);
				stats.entities.put(r.alias,type);
				if (r instanceof VarDecl.JoinDecl==false) {
					if (stats.entity==null)
						stats.entity = type;
					else stats.entity = Object.class;
				}
			}
			@Override
			protected void visit(ValueExpr.Parameter p) {
				if (params!=null)
					params.add(p.name.substring(1)); // cut ':'
			}
		}.visit(statement);
		StringBuilder sb = new StringBuilder();
		ParserSqlBuilder b = new ParserSqlBuilder() {
			@Override
			protected void print(String s) {
				sb.append(s);
			}
		};
		if (insert)
			b.visitInsert((Statement.Update)statement);
		else b.visit(statement);
		if (fieldTypes!=null) {
			fieldTypes.putAll(fieldTypesMeta);
			if (stats.returnBeans && aliasOnly)
				fieldTypes.clear();
			if (containsWildcard) {
				fieldTypes.clear();
				fieldTypes.put("_wildcard",null);
			}
			fieldTypes.put("_entityType",stats.entity);
			fieldTypes.put("_resultType",stats.returnBeans ? stats.entity : Object.class);
		}
		return sb.toString();
	}

	public Object getUnmanaged(Object bean) { // no more "managed" beans
		return bean;
	}

	///////////////////////////////////////////////////////////////////////////
	// DB read access, lazy loading

	private void serializeOne(Object o) { // this provides lazy-loading to our beans
		if (o instanceof HasContext)
			((HasContext)o).setContext(this);
	}
	public <T> T serialize(T o) {
		if (o==null) return o;
		serializeOne(o);
		if (o.getClass().isArray() && o.getClass().getComponentType()==Object.class) {
			for (Object oo : (Object[])o)
				serialize(oo);
		} else if (o instanceof Collection) {
			for (Object oo : (Collection)o)
				serialize(oo);
		} else if (o instanceof Map) {
			for (Map.Entry e : ((Map<Object,Object>)o).entrySet()) {
				serialize(e.getKey());
				serialize(e.getValue());
			}
		}
		return o;
	}

	static Map<Class,Map<Serializable,Serializable>> llcaches = new HashMap();
	Map<Serializable,Serializable> llcache(Class type) {
		Map cache = llcaches.get(type);
		if (cache==null) llcaches.put(type,cache = new LinkedHashMap() {
			@Override
			protected boolean removeEldestEntry(java.util.Map.Entry eldest) {
				return size()>1000;
			}
		});
		return cache;
	}
	public <T extends Serializable> T loadCached(Class<T> c, Serializable id) {
		Map<Serializable,Serializable> cache = llcache(c);
		Serializable o = cache.get(id);
		if (o==null) cache.put(id,o = load(c,id));
		return (T)o;
	}

	<T extends Serializable> Serializable llload(Class<T> clazz, Serializable id, String field, boolean useCache) throws Exception {
//		if (field==null)
//			return load(clazz,id);
//		Class collection = Fields.of(clazz).type(field);
//		if (!Collection.class.isAssignableFrom(collection))
//			return (Serializable)load(clazz,Arrays.asList(id),field).get(id);

		if (field==null)
			return useCache ? loadCached(clazz,id) : load(clazz,id);
		Class collection = Fields.of(clazz).type(field);
		if (!Collection.class.isAssignableFrom(collection)) {
			try {
				Object o = loadCached(clazz,id);
				Method m = ReflectHelper.getGetter(clazz,field);
				Function getter = LambdaUtil.getGetter(m);
				return (Serializable)getter.apply(o);
			} catch (Exception ex) {
				throw ex;
			} catch (Throwable ex) {
				throw new Exception(ex);
			}
		}

		OrmInfo i = dbo.infos.get(clazz);
		OrmInfo.CollectionInfo ci = i.collectionColumns.get(field);
		Collection objects;
		if (ci.tableName==null) {
			objects = BeanManagerUtil.getAll(this,ci.entityType,
					0,id,ci.mappedBy+"."+i.pkColumn+" = :id");
		} else {
			OrmInfo im = dbo.infos.get(ci.entityType);
			OrmInfo.CollectionInfo cim = im.collectionColumns.get(ci.mappedBy);
			String fn = collectionColumnName(im,cim);
			String cn = collectionColumnName(i,ci);
			List<String> ids = BeanManagerUtil.query(this,
					"select "+fn+" from "+ci.tableName+" x",
					0,id,cn+" = :id").getResultList();
			objects = byId(ci.entityType,ids).values();
		}
		if (collection==List.class) collection = ArrayList.class;
		else if (collection==Set.class) collection = LinkedHashSet.class;
		Collection c = CollectionUtils.createCollectionT(collection,objects.size());
		c.addAll(objects);
		return (Serializable)CollectionUtils.unmodifiableCollection(c);
	}

	private String collectionColumnName(OrmInfo info, OrmInfo.CollectionInfo ci) {
		return ci.altName!=null ? ci.altName : ci.mappedBy+"_"+info.pkColumn;
	}

	class LazyLoader implements ILazyLoader {
		public void load(TransferObject2 owner, Object value, String field) {
			if (value instanceof TransferObject2) {
				TransferObject2 to = (TransferObject2)value;
				if (ITransferObject.Util.isOk(to.getTransferState())) return;
				if (to.getTransferState()==ITransferObject.TransferState.error) return;
			}

			if (value instanceof Collection)
				return;

			Class<? extends TransferObject2> oc = owner.getClass();
			Serializable oid = owner.getId();
			if (value==null && ITransferObject.Util.isOk(owner.getTransferState())) {
				Class collection = Fields.of(oc).type(field);
				if (!Collection.class.isAssignableFrom(collection) &&
						!Map.class.isAssignableFrom(collection)) return;
			}
			try {
				Object r;
				if (value instanceof TransferObject2) {
					Class vc = value.getClass();
					Serializable vid = ((TransferObject2)value).getId();
					r = llload(vc,vid,null,owner!=value || field!=null);
				} else {
					r = llload(oc,oid,field,true);
				}
				if (r!=null) {
					Class rc = r.getClass();
					if (field==null) {
						if (oc!=rc)
							logger.error("ClientSide.load() owner validate: class mismatch (loaded {} vs. owner {})",rc,oc);
					} else if (value instanceof TransferObject2) {
						Class vc = value.getClass();
						if (vc!=rc) {
							if (!vc.isAssignableFrom(rc))
								logger.error("ClientSide.load() value read: class mismatch (loaded {} vs. owner {})",rc,vc);
							else {
								Fields.of(oc).write(owner,field,r);
								return;
							}
						}
					}
				}
				if (value==null && r==null) {
					Class collection = Fields.of(oc).type(field);
					if (List.class.isAssignableFrom(collection)) r = Collections.EMPTY_LIST;
					if (Set.class.isAssignableFrom(collection)) r = Collections.EMPTY_SET;
				}
				if (r==null) {
					if (value instanceof TransferObject2)
						((TransferObject2)value).setTransferState(ITransferObject.TransferState.error);
					return;
				}
				if (value==null || r instanceof Collection) {
					Fields.of(oc).write(owner,field,r);
					return;
				}

				// handle transferobject -> copy its content
				if (r instanceof TransferObject2) {
					Class vc = value.getClass();
					Fields.of(vc).copy(value,r);
					((TransferObject2)value).setTransferState(ITransferObject.TransferState.unknown);
					serializeOne(value);
					return;
				}

				logger.warn("ClientSide.load() could not handle return value {}",r);
			} catch (Exception ex) {
				logger.error(ex.getMessage(),ex);
			}
		}
	}

	///////////////////////////////////////////////////////////////////////////
	// DB write access

	private IHistoryLogger historyLogger;

	public void setHistoryLogger(IHistoryLogger historyLogger) {
		this.historyLogger = historyLogger;
	}

	private ChangesListener changesListener;

	public void setChangesListener(ChangesListener l) {
		this.changesListener = l;
	}

	public ChangesListener getChangesListener() {
		return changesListener;
	}

	private final Map<Class,BeanHandler> beanHandlers = new HashMap();

	public void addBeanHandler(BeanHandler l, Class[] classes) {
		for (Class c : classes) {
			BeanHandler known = beanHandlers.get(c);
			if (known!=null)
				throw new RuntimeException(MessageFormat.format(
						"handler {0} for {1} already registered",l,c));
			beanHandlers.put(c,l);
		}
	}

	public Map<Class,BeanHandler> getBeanHandlers() {
		return beanHandlers;
	}

	private final Map<Class,LinkedList<BeanHandler>> beanHandlersCache = new HashMap();

	private List<BeanHandler> getBeanHandler(Class c) {
		if (beanHandlers==null)
			return Collections.EMPTY_LIST;
		LinkedList<BeanHandler> handlers = beanHandlersCache.get(c);
		if (handlers!=null)
			return Collections.unmodifiableList(handlers);
		handlers = new LinkedList();
		for (; c!=null; c = c.getSuperclass()) {
			BeanHandler l = beanHandlers.get(c);
			if (l!=null && !handlers.contains(l))
				handlers.addFirst(l);
			addInterfaceHandler(c,handlers);
		}
		beanHandlersCache.put(c,handlers);
		return Collections.unmodifiableList(handlers);
	}

	public <T> String getPrefix(Class<T> c) {
		String prefix = getPrefix1(c); // from BeanHandler
		if (prefix==null) prefix = getPrefix2(c); // from central configuration
		return prefix;
	}

	private <T> String getPrefix1(Class<T> c) {
		List<BeanHandler> handlers = new ArrayList(getBeanHandler(c));
		Collections.reverse(handlers);
		String prefix = null;
		for (BeanHandler h : handlers) {
			prefix = h.generateIdPrefix(c);
			if (prefix!=null) return prefix;
		}
		return null;
	}

	private void addInterfaceHandler(Class c, LinkedList<BeanHandler> handlers) {
		for (Class i : c.getInterfaces()) {
			BeanHandler l = beanHandlers.get(i);
			if (l!=null && !handlers.contains(l))
				handlers.addFirst(l);
			addInterfaceHandler(i,handlers);
		}
	}

	public boolean inTransaction() {
		return shared().transactionCounter>0;
	}

	public void beginTransaction() {
		shared().beginTransaction();
	}

	public void commit() throws Exception {
		shared().commit();
	}

	public void rollback() {
		shared().rollback();
	}

	public String getTransactionId() {
		return shared().getTransactionId();
	}

	public <T> T save(T o) throws Exception {
		return serialize(shared().save(o));
	}

	public Collection saveAll(Collection c) throws Exception {
		return serialize(shared().saveAll(c));
	}

	public void saveData(ITransactionData... data) throws Exception {
		shared().saveData(data);
	}

	public void saveData(Collection<ITransactionData> data) throws Exception {
		shared().saveData(data);
	}

	public void delete(Object o) throws Exception {
		shared().delete(o);
	}

	public void delete(Object o, boolean force) throws Exception {
		shared().delete(o,force);
	}

	public void deleteAll(Collection o) throws Exception {
		shared().deleteAll(o);
	}

	public void deleteAll(Collection o, boolean force) throws Exception {
		shared().deleteAll(o,force);
	}

	public void link(Object it, Object to, String field) throws Exception {
		shared().link(it,to,field);
	}

	public void unlink(Object it, Object from, String field) throws Exception {
		shared().unlink(it,from,field);
	}

	public void beforeInvocation() {
//		shared(true).beforeInvocation();
		shared().beforeInvocation();
	}

	public void afterInvocation() {
//		if (instanceEntityManager()!=null) return;
//		shared(true).afterInvocation();
		shared().afterInvocation();
	}

	public Connection getCurrentConnection() {
		return shared().getCurrentConnection();
	}

	public void closeCurrentConnection() {
//		if (shared()==instanceEntityManager()) return;
		shared().closeCurrentConnection();
	}

	public void close() throws IOException {
		closeCurrentConnection();
	}

	public void addChangeEvent(BeanChange change) {
		shared().addChangeEvent(change);
	}

	public List<BeanChange> getChangeEvents() {
		return Collections.unmodifiableList(shared().changesInTransaction());
	}

	public void addIgnoreChangeEvent(Class type) {
		shared().addIgnoreChangeEvent(type);
	}

	public void removeIgnoreChangeEvent(Class type) {
		shared().removeIgnoreChangeEvent(type);
	}

	////////////////////////////////////////////////////////////////////////////

	private final ThreadLocal<WriteAccess> threadEntityManagers = new ThreadLocal();
	private final ThreadLocal<Object> threadTokens = new ThreadLocal();
	private final Map<Object,WriteAccess> instanceEntityManager = new HashMap();
	private final Map<Object,Thread> instanceEntityManagerCreator = new HashMap();

	private WriteAccess shared() {
		return shared(false,true);
	}

	private WriteAccess shared(boolean forceThreadLocal, boolean mayCreate) {
		if (!forceThreadLocal) {
			WriteAccess threadShare = instanceEntityManager();
			if (threadShare!=null)
				return threadShare;
		}

		WriteAccess em = threadEntityManagers.get();
		if (em!=null && em.isOpen())
			return em;

		if (!mayCreate)
			return null;

		EntityManagerCloser.closeOrphanedEntityManagers();
		DatabaseCache transactionCache = null;
		if (em!=null) {
			transactionCache = em.transactionCache;
		} else {
			try {
				transactionCache = new DatabaseCache(dbo,Deflater.NO_COMPRESSION);
			} catch (Exception ex) {
				logger.warn(ex.getMessage(),ex);
			}
		}
		em = new WriteAccess(this,historyLogger,transactionCache);
//		if (logger.isLoggable(Level.FINER))
//			logger.finer("open "+em+" for thread "+Thread.currentThread());
		threadEntityManagers.set(em);
		new EntityManagerCloser(Thread.currentThread(),em,threadEntityManagers);
		return em;
	}

	private WriteAccess instanceEntityManager() {
		return instanceEntityManager.get(threadTokens.get());
	}

	public void setShareMode(Object token) {
		if (token!=null) {
			threadTokens.set(token);
			if (!instanceEntityManager.containsKey(token)) {
				instanceEntityManager.put(token,shared());
				instanceEntityManagerCreator.put(token,Thread.currentThread());
			}
		} else {
			if (Thread.currentThread()==instanceEntityManagerCreator.get(token))
				instanceEntityManager.remove(threadTokens.get());
			threadTokens.remove();
		}
	}

	/** This is a wrapper around JDBC Connection that is shared threadlocal over BeanManager
	 * instances. This is the standard usecase in our platform. But an instance can also be
	 * bound to a BeanManager instance by setting a "share mode". If it is bound, the BeanManager
	 * sticks to it even if BeanManager calls happen on a different thread.
	 * Per instance one can use one transaction after the other, in a serial manner. */
	private static class WriteAccess
	{
		private final DatabaseImpl bm;
		private final IHistoryLogger historyLogger;
		private Connection co;

		public WriteAccess(DatabaseImpl bm, IHistoryLogger historyLogger, DatabaseCache transactionCache) {
			this.bm = bm;
			this.historyLogger = historyLogger;
			this.transactionCache = transactionCache;
		}

		private WriteAccess em() {
			try {
				if (co!=null && !co.isClosed())
					return this;
				co = bm.ds.getConnection();
			} catch (SQLException ex) {
				ex.printStackTrace();
			}
			return this;
		}

		private boolean isOpen() {
			return co!=null;
		}

		public Object getUnmanaged(Object o) {
			return bm.getUnmanaged(o);
		}

		public String getIdentifierName(Class c) {
			return bm.getIdentifierName(c);
		}

		public String getEntityName(Class c) {
			return bm.getEntityName(c);
		}

		public <T> Class<T> getEntityClass(Class<T> c) {
			return bm.getEntityClass(c);
		}

		private List<BeanHandler> getBeanHandler(Class c) {
			return bm.getBeanHandler(c);
		}

		private <T extends Serializable> T find(Object o) {
			Object merged = inTransaction!=null ? inTransaction.get(o) : null;
			if (merged!=null) return (T)merged;
			return bm.load((Class<T>)o.getClass(),((HasId)o).getId());
		}
		private <T> T find(Class<T> c, Serializable id) {
			return null;
		}
		private <T> T merge(Object o) throws Exception {
			return merge(o,false);
		}
		private <T> T merge(Object o, boolean forceNoTransaction) throws Exception {
			Serializable current = find((T)o);
			Class type = o.getClass();
			OrmInfo ormInfo = bm.dbo.infos.get(type);
			if (ormInfo==null) {
				logger.error("merge failed, missing OrmInfo for {}",o);
				return (T)o;
			}
			Map<String,Object> valuesOrig;
			if (valuesInTransaction!=null && valuesInTransaction.containsKey(o)) {
				valuesOrig = valuesInTransaction.get(o);
			} else {
				valuesOrig = new HashMap();
				if (current!=null) {
					for (Map.Entry<String,String> e : ormInfo.propColumn.entrySet()) {
						String prop = e.getKey();
						Object value = ReflectHelper.getGetter(type,prop).invoke(current);
						valuesOrig.put(prop,value);
					}
				}
			}
			Map<String,Object> valuesToSave = new HashMap();
			for (Map.Entry<String,String> e : ormInfo.propColumn.entrySet()) {
				String prop = e.getKey();
				String column = e.getValue();
				if (ormInfo.collectionColumns.containsKey(column))
					continue;
				Object value = ReflectHelper.getGetter(type,prop).invoke(o);
				Object orig = valuesOrig.get(prop);
				if (!Objects.equals(value,orig))
					valuesToSave.put(prop,value);
			}
			if (valuesToSave.size()==0) // nothing to save
				return (T)o;

			Serializable id = ((HasId)o).getId();
			Long version = o instanceof HasVersion ? ((HasVersion<Long>)o).getVersion() : null;

//			SqlBuilder sb = new SqlBuilder();
//			sb.values(valuesToSave.keySet());
//			sb.table(bm.dbo.info(type).tables);
//			sb.allTypes(true);
//			sb.where("t0.id = ?");
//			String sql = sb.sql();

			// the parser does not support INSERT, so for INSERT we render an "UPDATE" here
			// generateSql can render INSERTs for that
			boolean insert = id==null || valuesToSave.keySet().contains("id");
			boolean hasVersion = ormInfo.versionColumn!=null;
			boolean incrementVersion = !forceNoTransaction;//true;
			if (incrementVersion && hasVersion) {
				long v = version!=null ? version : 0L; // e.g. after adding version column to existing table
				valuesToSave.put(ormInfo.versionColumn,insert ? 0 : v+1);
			}

			Map<String,Map<String,Object>> valuesToSaveByTable =
					MapUtils.group(valuesToSave,(k,v) -> ormInfo.columnTable.get(ormInfo.propColumn.get(k)),HashMap.class);
			if (insert) {
				for (String t : ormInfo.tables) {
					if (t.startsWith("!"))
						continue;
					Map<String,Object> m = valuesToSaveByTable.get(t);
					if (m==null) valuesToSaveByTable.put(t,MapUtils.asMap(ormInfo.pkColumn,id));
					else if (!m.containsKey(ormInfo.pkColumn)) m.put(ormInfo.pkColumn,id);
				}
			}
			boolean needsTransaction = !forceNoTransaction && valuesToSaveByTable.size()>1;
			if (needsTransaction)
				beginTransaction();
			Deque<String> tables = ormInfo.tables.stream().filter(t -> !t.startsWith("!")).collect(Collector.of(
					ArrayDeque::new,(deq, t) -> deq.addFirst(t),(d1, d2) -> { d2.addAll(d1); return d2; }));
			for (String table : tables) {
				Map<String,Object> values = valuesToSaveByTable.get(table);
				if (values==null) continue;

				if (insert && !values.containsKey(ormInfo.pkColumn))
					values.put(ormInfo.pkColumn,id);

				boolean addVersion = hasVersion && table.equals(ormInfo.columnTable.get(ormInfo.versionColumn));
				String sql = "update "+table+" x set\n";
				sql += values.keySet().stream().collect(Collectors.joining(" = ?, x.","x."," = ?"));
				if (!insert) {
					sql += "\nwhere x.id = ?";
					if (addVersion) {
						if (version!=null)
							sql += " and x."+ormInfo.versionColumn+" = ?";
						else sql += " and x."+ormInfo.versionColumn+" is null";
					}
				}

				sql = bm.generateSql(bm.parseQuery(sql,insert));

				logger.debug("{}",sql);
				PreparedStatement ps = null;
				try {
					if (ormInfo.autoIncrement!=null)
						ps = co.prepareStatement(sql,PreparedStatement.RETURN_GENERATED_KEYS);
					else ps = co.prepareStatement(sql);
					int index = 1;
					for (Object value : values.values())
						ps.setObject(index++,QueryImpl.transform(value));
					if (!insert) {
						ps.setObject(index++,id);
						if (addVersion && version!=null)
							ps.setObject(index++,version);
					}
					int affected = ps.executeUpdate();
					if (affected==0)
						throw new OptimisticLockException();
					else if (affected!=1) ; // TODO ?
					if (ormInfo.autoIncrement!=null && ((HasId)o).getId()==null) {
						ResultSet rs = ps.getGeneratedKeys();
						rs.next();
						if (ormInfo.autoIncrement==Long.class) id = rs.getLong(1);
						else if (ormInfo.autoIncrement==Integer.class) id = rs.getInt(1);
						((HasId)o).setId(id);
					}
					logger.debug("affected {} rows",affected);
				} finally {
					QueryImpl.close(ps);
				}
			}
			if (needsTransaction)
				commit();
			if (incrementVersion && hasVersion) {
				long v = version!=null ? version : 0L; // e.g. after adding version column to existing table
				v = insert ? 0 : v+1;
				Method setter = ReflectHelper.getSetter(type,ormInfo.versionColumn);
				if (setter!=null)
					setter.invoke(o,v);
				else ReflectHelper.write(o,ormInfo.versionColumn,v);
			}
			if (inTransaction!=null)
				inTransaction.put(o,o);
			if (valuesInTransaction!=null) {
				Map<String,Object> values = new HashMap();
				values.putAll(valuesOrig);
				values.putAll(valuesToSave);
				valuesInTransaction.put(o,values);
			}
			return (T)o;
		}
		private void remove(Object o) throws Exception {
			o = find(o);
			if (o==null) return;
			Class type = o.getClass();
			OrmInfo ormInfo = bm.dbo.infos.get(type);
			if (ormInfo==null) {
				logger.error("delete failed, missing OrmInfo for {}",o);
				return;
			}
			Serializable id = ((HasId)o).getId();
			Long version = o instanceof HasVersion ? ((HasVersion<Long>)o).getVersion() : null;
			boolean hasVersion = ormInfo.versionColumn!=null;
			List<String> tables = X.x(ormInfo.tables).filter(t -> !t.startsWith("!")).x();
			boolean needsTransaction = tables.size()>1;
			if (needsTransaction)
				beginTransaction();
			for (String table : tables) {
				boolean addVersion = hasVersion && table.equals(ormInfo.columnTable.get(ormInfo.versionColumn));
				String sql = "delete x from "+table+" x"+
						"\nwhere x.id = ?";
				if (addVersion) {
					if (version!=null)
						sql += " and x."+ormInfo.versionColumn+" = ?";
					else sql += " and x."+ormInfo.versionColumn+" is null";
				}

				logger.debug("{}",sql);
				PreparedStatement ps = null;
				try {
					ps = co.prepareStatement(sql);
					int index = 1;
					ps.setObject(index++,id);
					if (addVersion && version!=null)
						ps.setObject(index++,version);
					int affected = ps.executeUpdate();
					if (affected==0)
						throw new OptimisticLockException();
					else if (affected!=1) ; // TODO ?
					logger.debug("affected {} rows",affected);
				} finally {
					QueryImpl.close(ps);
				}
			}
			if (needsTransaction)
				commit();
		}

		////////////////////////////////////////////////////////////////////////////

		public <T> T save(T o) throws Exception {
			if (o==null)
				throw new NullPointerException("save(null) was called");

			// TODO transferobject2 loswerden!?
			TransferObject2 toSave = (TransferObject2)o;
			TransferObject2 original = find(toSave);

//			if (toSave instanceof HasVersion && original instanceof HasVersion) {
//				HasVersion vs = (HasVersion)toSave;
//				HasVersion vo = (HasVersion)original;
//				if (vs.getVersion()==null && vo.getVersion()!=null) {
//					throw new PlatformException("object with id "+toSave.getId()+
//							" already exists in database;" +
//							" please change the id of your new object.");
//				}
//			}

			BeanChange prevChange = null;
//			Serializable id = toSave.getId();
//			if (original==toSave && em().contains(toSave)) {
//				// jpa managed object
//
//				toSave = (TransferObject2) original.clone();
//				toSave.setId(null);
//
//				TransferObject2 previouslySavedInTransaction = null;
//				prevChange = getPreviousChangeInTransaction(original);
//				if (prevChange!=null)
//					previouslySavedInTransaction = (TransferObject2) prevChange.getCurrent();
//
//				if (previouslySavedInTransaction!=null)
//					original = previouslySavedInTransaction;
//				else
//					em().refresh(original);
//
//				toSave.setId(id);
//				original = (TransferObject2) original.clone();
//			}
//			else
//			{
//				// this is better than nothing! if this line is missing we have NO
//				// update history entries 'cause em.merge (below) refreshes the entity
//				// instance we've got from em.find (above).
//				original = original!=null ? (TransferObject2)original.clone() : null;
//			}

//			if (original!=null)
//				original.setId(null);

			Class c = toSave.getClass();

			boolean autoCommit = !isTransactionActive();
			if (autoCommit) beginTransaction();
			try {
				collectTransientRefs(toSave);

				if (original==null && toSave instanceof CrudTimes.C) {
					if (((CrudTimes.C)toSave).getTimeCreate()==null)
						((CrudTimes.C)toSave).setTimeCreate(new Date());
				} else if (original!=null && toSave instanceof CrudTimes.U) {
					((CrudTimes.U)toSave).setTimeUpdate(new Date());
				}

				List<BeanHandler> handlers = getBeanHandler(c);
				for (BeanHandler h : handlers)
					h.preSave(toSave);

				TransferObject2 saved = em().merge(toSave);
				// optimize away find calls here in save
				toSave.setTransferState(null); // see .createInstance + .find
				toSave = saved;
				putTransactionObject(c,saved);

				afterSave(prevChange,toSave,original);

				for (BeanHandler h : handlers)
					h.afterSave(toSave,original);

				if (historyLogger!=null) {
					Object orig = getUnmanaged(original);
					Object changed = getUnmanaged(toSave);
					historyLogger.change(transactionId,orig,changed);
				}

				if (autoCommit) commit();
			} catch (VetoButCommitException ex) {
				if (autoCommit) commit();
			} catch (VetoException ex) {
				if (autoCommit) rollback();
			} catch (Exception ex) {
				if (autoCommit) rollback();
				throw ex;
			}
			return (T)find(toSave);
		}

		public Collection saveAll(Collection c) throws Exception {
			boolean autoCommit = !isTransactionActive();
			if (autoCommit) beginTransaction();
			try {
				Collection result = new ArrayList(c.size());
				for (Object bean : c)
					result.add(save(bean));
				if (autoCommit) commit();
				return result;
			} catch (Exception ex) {
				if (autoCommit) rollback();
				throw ex;
			}
		}

		public void saveData(ITransactionData... data) throws Exception {
			saveData(Arrays.asList(data));
		}

		public void saveData(Collection<ITransactionData> data) throws Exception {
			boolean autoCommit = !isTransactionActive();
			if (autoCommit) beginTransaction();
			try {
				List<BeanHandler> handlers;
				Map<BeanHandler,Collection<ITransactionData>> splitted = new LinkedHashMap();
				for (ITransactionData d : data) {
					handlers = getBeanHandler(d.getClass());
					for (BeanHandler h : handlers) {
						Collection<ITransactionData> c = splitted.get(h);
						if (c==null) splitted.put(h,c = new LinkedHashSet());
						c.add(d);
					}
					handlers = getBeanHandler(d.getEntity().getClass());
					for (BeanHandler h : handlers) {
						Collection<ITransactionData> c = splitted.get(h);
						if (c==null) splitted.put(h,c = new LinkedHashSet());
						c.add(d);
					}
				}
				for (Map.Entry<BeanHandler,Collection<ITransactionData>> e : splitted.entrySet())
					e.getKey().saveTransactionData(e.getValue());
				if (autoCommit) commit();
			} catch (Exception ex) {
				if (autoCommit) rollback();
				throw ex;
			}
		}

		public void delete(Object o) throws Exception {
			delete(o,false);
		}

		public void delete(Object o, boolean force) throws Exception {
			Class c = o.getClass();

			boolean autoCommit = !isTransactionActive();
			if (autoCommit) beginTransaction();
			try {
				List<BeanHandler> handlers = getBeanHandler(c);
				for (BeanHandler h : handlers)
					h.preDelete(o);

				o = em().find(o);
				if (o==null) return;

				if (force) {
					em().remove(o);
				} else {
					boolean wasMarkedAsDeleted = false;
					// TODO #496
					if (o instanceof HasLifecycleState) {
						wasMarkedAsDeleted = ((HasLifecycleState)o).getState()==LifecycleState.Deleted;
						if (!wasMarkedAsDeleted) {
							((HasLifecycleState)o).setState(LifecycleState.Deleted);
							if (o instanceof CrudTimes.D)
								((CrudTimes.D)o).setTimeDelete(new Date());
							// TODO oder save()? (events, handlers etc)
							em().merge(o);
						}
//							if (wasMarkedAsDeleted)
//								em().remove(o);
					} else if (o instanceof HasWorkflowState) {
						wasMarkedAsDeleted = ((HasWorkflowState)o).getState()==WorkflowState.ResolvedDeleted;
						if (!wasMarkedAsDeleted) {
							((HasWorkflowState)o).setState(WorkflowState.ResolvedDeleted);
							if (o instanceof CrudTimes.D)
								((CrudTimes.D)o).setTimeDelete(new Date());
							// TODO oder save()? (events, handlers etc)
							em().merge(o);
						}
//							if (wasMarkedAsDeleted)
//								em().remove(o);
					} else {
						Map<Pair<String,Class>,Method> setters = ReflectHelper.getSetters(o.getClass());
						boolean markedAsDeleted = false;
						wasMarkedAsDeleted = true; // for the case that no state-setter is found
						for (Map.Entry<Pair<String,Class>,Method> e : setters.entrySet()) {
							Class cc = e.getKey().getSecond();
							Method m = e.getValue();

							String setterName = m.getName();
							String getterName = setterName.replaceFirst("^set","get");
							Object state = ReflectHelper.get(o,getterName);
							if (state instanceof LifecycleState)
								wasMarkedAsDeleted = state==LifecycleState.Deleted;
							else if (state instanceof WorkflowState)
								wasMarkedAsDeleted = state==WorkflowState.ResolvedDeleted;

							if (cc==LifecycleState.class && !wasMarkedAsDeleted) {
								try {
									m.invoke(o,LifecycleState.Deleted);
									markedAsDeleted = true;
									break;
								} catch (Exception ex) {}
							}
							else if (cc==WorkflowState.class && !wasMarkedAsDeleted) {
								try {
									m.invoke(o,WorkflowState.ResolvedDeleted);
									markedAsDeleted = true;
									break;
								} catch (Exception ex) {}
							}
						}
						if (markedAsDeleted) {
							if (o instanceof CrudTimes.D) {
								CrudTimes.D d = (CrudTimes.D)o;
								if (d.getTimeDelete()==null)
									d.setTimeDelete(new Date());
							}
						}
						if (markedAsDeleted)
							// TODO oder save()? (events, handlers etc)
							o = em().merge(o);
						else
//							if (wasMarkedAsDeleted)
							em().remove(o);
					}
				}
				afterDelete(o);

				for (BeanHandler h : handlers)
					h.afterDelete(o);

				if (historyLogger!=null) {
					Object orig = getUnmanaged(o);
					historyLogger.change(transactionId,orig,null);
				}

				if (autoCommit) commit();
			} catch (VetoButCommitException ex) {
				if (autoCommit) commit();
			} catch (VetoException ex) {
				if (autoCommit) rollback();
			} catch (Exception ex) {
				if (autoCommit) rollback();
				throw ex;
			}
		}

		public void deleteAll(Collection c) throws Exception {
			deleteAll(c,false);
		}

		public void deleteAll(Collection c, boolean force) throws Exception {
			if (c==null || c.isEmpty()) return;
			boolean autoCommit = !isTransactionActive();
			if (autoCommit) beginTransaction();
			try {
				for (Object bean : c)
					delete(bean,force);
				if (autoCommit) commit();
			} catch (Exception ex) {
				if (autoCommit) rollback();
				throw ex;
			}
		}

		public void link(Object it, Object to, String field) throws Exception {
			if (it==null || to==null)
				return;
			if (it instanceof Collection) {
				for (Object o : (Collection)it)
					link(o,to,field);
				return;
			}
			if (to instanceof Collection) {
				for (Object o : (Collection)to)
					link(it,o,field);
				return;
			}
			if (!linkUnlink(to,it,field,true))
				throw new UnsupportedOperationException("link table "+it+" to "+to+" not found");
		}

		public void unlink(Object it, Object from, String field) throws Exception {
			if (it==null || from==null)
				return;
			if (it instanceof Collection) {
				for (Object o : (Collection)it)
					unlink(o,from,field);
				return;
			}
			if (from instanceof Collection) {
				for (Object o : (Collection)from)
					unlink(it,o,field);
				return;
			}
			if (!linkUnlink(from,it,field,false))
				throw new UnsupportedOperationException("unlink table "+it+" from "+from+" not found");
		}

		private boolean linkUnlink(Object o1, Object o2, String field, boolean add) throws Exception {
//			if (o2 instanceof Class) {
//				Object o = o1;
//				o1 = o2;
//				o2 = o;
//			}
			boolean removeAll = false;
			Class type = o1.getClass();
			Class other = o2.getClass();
			if (o1 instanceof Class || o2 instanceof Class) {
				if (add)
					return false;
				throw new UnsupportedOperationException("unlink all "+o2+" from "+o1+" not supported yet");
//				type = (Class)o1;
//				removeAll = true;
			}
			if (field==null)
				field = findCollectionField(type,other); // could be ambigious when type==other

			OrmInfo info = bm.dbo.info(type);
			OrmInfo infoOther = bm.dbo.info(other);
			OrmInfo.CollectionInfo ci = info.collectionColumns.get(field);
			OrmInfo.CollectionInfo ciOther = infoOther.collectionColumns.get(ci.mappedBy);

			String cn = bm.collectionColumnName(info,ci);
			String cnOther = bm.collectionColumnName(infoOther,ciOther);

			LinkedList<String> fields = new LinkedList(Arrays.asList(cn,cnOther));
			String sql;
			if (add) {
				sql = "update "+ci.tableName+" x set\n";
				sql += fields.stream().collect(Collectors.joining(" = ?, x.","x."," = ?"));
			} else {
				if (removeAll)
					fields.removeFirst();
				sql = "delete from "+ci.tableName+" x where\n";
				sql += fields.stream().collect(Collectors.joining(" = ? and x.","x."," = ?"));
			}

			sql = bm.generateSql(bm.parseQuery(sql,add));
			sql = sql.replace("INSERT INTO","INSERT IGNORE INTO");

			logger.debug("{}",sql);
			PreparedStatement ps = null;
			try {
				em(); // TODO add beginTransaction + commit to link/unlink methods?
				ps = co.prepareStatement(sql);
				int index = 1;
				if (!removeAll)
					ps.setObject(index++,QueryImpl.transform(o1));
				ps.setObject(index++,QueryImpl.transform(o2));
				int affected = ps.executeUpdate();
				logger.debug("affected {} rows",affected);
			} finally {
				QueryImpl.close(ps);
			}
			return true;
		}

		private String findCollectionField(Class type, Class other) {
			OrmInfo info = bm.dbo.info(type);
			OrmInfo infoOther = bm.dbo.info(other);
			Map<String,OrmInfo.CollectionInfo> cc = info.collectionColumns;
			for (Map.Entry<String,OrmInfo.CollectionInfo> e : cc.entrySet()) {
				OrmInfo.CollectionInfo ci = e.getValue();
				if (ci.tableName==null)
					continue;
				if (!ci.entityType.isAssignableFrom(other))
					continue;
				OrmInfo.CollectionInfo ciOther = infoOther.collectionColumns.get(ci.mappedBy);
				if (ciOther==null)
					continue;
				return e.getKey();
			}
			return null;
		}

		////////////////////////////////////////////////////////////////////////////

		private int transactionCounter;
		private String transactionId;
		private Map<Object,Object> inTransaction;
		private Map<Object,Map<String,Object>> valuesInTransaction;
		private Map<Object,Map<Method,Object>> refsToSet;
		private final DatabaseCache transactionCache;

		private List<Serializable> putTransactionObject(Class type, HasId object)
		throws Exception {
			if (transactionCounter==0) return null;
			if (transactionCache==null) return null;
			return transactionCache.putObjects(type,Arrays.asList(object));
		}

		public List<Serializable> getTransactionObjects(
				Class type, Map<Serializable,Object> byId, List<Serializable> ids)
		throws Exception {
			if (transactionCounter==0) return ids;
			if (transactionCache==null) return ids;
			return transactionCache.getObjects(type,byId,ids);
		}

		private void clearTransactionCache() {
			if (transactionCache!=null)
				transactionCache.clearAll();
		}

		public void beginTransaction() {
			if (transactionCounter==0) {
				discardUpdateEventsOfTransaction();
				inTransaction = new HashMap();
				valuesInTransaction = new HashMap();
				refsToSet = null;
				clearTransactionCache();
			}
			try {
				if (co==null || co.getAutoCommit()) {
					em();
					co.setAutoCommit(false);
				}
				transactionCounter++;
				transactionId = "dt"+Id.next();
				if (historyLogger!=null)
					historyLogger.beginTransaction(transactionId);
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}

		public void commit() throws Exception {
			transactionCounter--;
			if (transactionCounter>0)
				return;
			if (co!=null && !co.getAutoCommit()) {
				try {
					Map<Object,Map<Method,Object>> map = refsToSet;
					if (map!=null) {
						for (Map.Entry<Object,Map<Method,Object>> e : map.entrySet()) {
							Object o = find(e.getKey());
							for (Map.Entry<Method,Object> ee : e.getValue().entrySet()) {
								Object ov = ee.getValue();
								Object oo = find(ov);
								if (oo==null)
									throw new Exception(MessageFormat.format(
											"referenced object not found: {0} for {1}",
											HasId.Util.simpleName((HasId)ov),ee.getKey()));
//									throw new LocalizedException("Objekt nicht gefunden: Typ: {0}, Id: '{1}'",
//											new Object[] { ov.getClass(), ov });
								ee.getKey().invoke(o,oo);
							}
							merge(o,true);
						}
					}
					co.commit();
					if (historyLogger!=null)
						historyLogger.endTransaction(transactionId,
								IHistoryLogger.TransactionState.commit);
				} catch (SQLException ex) {
					rollback();
					logger.error("commit failed",ex);
					throw new Exception("commit failed",ex);
				} finally {
					transactionCounter = 0;
					inTransaction = null;
					valuesInTransaction = null;
					clearTransactionCache();
				}
				try {
					closeCurrentConnection();
					fireUpdateEventsOfTransaction();
				} catch (SQLException ex) {
					discardUpdateEventsOfTransaction();
					logger.error("fireUpdateEventsOfTransaction failed",ex);
				}
			} else {
				transactionCounter = 0;
				logger.error("commit but no transaction",new Exception());
			}
		}

		public void rollback() {
			discardUpdateEventsOfTransaction();
			try {
				if (co!=null && !co.getAutoCommit()) {
					co.rollback();
					if (historyLogger!=null)
						historyLogger.endTransaction(transactionId,
								IHistoryLogger.TransactionState.rollback);
				}
			} catch (SQLException ex) {
				ex.printStackTrace();
			} finally {
				clearTransactionCache();
				closeCurrentConnection();
				transactionCounter = 0;
			}
		}

		public String getTransactionId() {
			return transactionId;
		}

		public Connection getCurrentConnection() {
			return co;
		}

		public void closeCurrentConnection() {
			if (co==null) return;
//			if (logger.isLoggable(Level.FINER))
//				logger.finer("close "+m);
			try {
				if (!co.getAutoCommit() && transactionCounter>0) {
					logger.warn("closeCurrentConnection() with open transaction, force rollback");
					co.rollback();
				}
				if (!co.isClosed())
					co.close();
				co = null;
			} catch (SQLException ex) {
				ex.printStackTrace();
			}
		}

		public void beforeInvocation() {
			transactionCounter = 0;
			transactionId = null;
			clearTransactionCache();
		}

		public void afterInvocation() {
			closeCurrentConnection();
		}

		////////////////////////////////////////////////////////////////////////////

		private final List<BeanChange> changesInTransaction = new LinkedList();
		private final Set<Class> ignoreChangeEvents = new HashSet();

		private List<BeanChange> changesInTransaction() {
			return changesInTransaction;
		}

		public void addChangeEvent(BeanChange change) {
			changesInTransaction().add(change);
		}

		public void addIgnoreChangeEvent(Class type) {
			type = getEntityClass(type);
			ignoreChangeEvents.add(type);
		}

		public void removeIgnoreChangeEvent(Class type) {
			type = getEntityClass(type);
			ignoreChangeEvents.remove(type);
		}

		////////////////////////////////////////////////////////////////////////////

		// FIXME find() liefert jetzt immer hibernate managed objekte
//		private Object find(Object o, Class c, Serializable id) {
//			if (o==null && (c==null || id==null)) return null;
//			if (o!=null && c==null) c = getBeanClass(o);
//			TransferObject t = (TransferObject)o;
//			o = null;
//			if (id!=null) {
//				if (jpaProvider.isManaged(t))
//					return t;
//				o = em().find(c,id);
//			}
//			if (o==null && t!=null) {
////				if (t.__state!=TransferObject.OK) {
//					id = t.getId();
//					o = id==null ? null : em().find(c,id);
////				}
//			}
//			if (o!=null && !(o.getClass().equals(c))) {
//				if (jpaProvider.isManaged(t))
//					o = jpaProvider.getUnmanaged(o);
//			}
//			if (o==null) {
////				new Exception("BeanManager.find() did not find "+
////						(bean!=null ? bean.getClass() : "null") + "("+c+") "+id).printStackTrace();
//			}
//			return o;
//		}

		private boolean isTransactionActive() {
			try {
				em();
				return co!=null && !co.getAutoCommit();
			} catch (SQLException ex) {
				ex.printStackTrace();
				return false;
			}
		}

		private void collectTransientRefs(Object o) throws Exception {
			Map<Object,Map<Method,Object>> map = refsToSet;
			if (map==null) map = refsToSet = new LinkedHashMap();

			Class t = o.getClass();
			Map<Pair<String,Class>,Method> getters = ReflectHelper.getGetters(t);
			for (Map.Entry<Pair<String,Class>,Method> e : getters.entrySet()) {
				Method getter = e.getValue();
				if (getter.getAnnotation(Transient.class)!=null) continue;
				if (getter.getDeclaringClass()==TransferObject2.class) continue;

				Pair<String,Class> pair = e.getKey();
				String name = pair.getFirst();
				Class type = pair.getSecond();
				if (Collection.class.isAssignableFrom(type)) continue;

				Object value = getter.invoke(o);
				if (value==null) continue;
				if (!HasId.class.isAssignableFrom(value.getClass())) continue;

				// TODO better solution, handle IEntry and subclasses lookup-or-create
				Serializable id = ((HasId)value).getId();
				if (id instanceof Number && ((Number)id).intValue()<0) continue;

				if (find(value)==null) {
					Method setter = ReflectHelper.getSetter(t,name,type);
					setter.invoke(o,(Object)null);
					Map<Method,Object> m = map.get(o);
					if (m==null) map.put(o,m = new HashMap());
					m.put(setter,value);
				}
			}
		}

		private void afterSave(BeanChange prevChange, Object o, Object orig) {
			if (o!=null && ignoreChangeEvents.contains(o.getClass()))
				return;

			// wenn ein Objekt innerhalb derselben Transaktion mehrfach gespeichert wird
			// wird nach Aussen nur ein Event mit dem aktuellen Stand weitergeleitet!!!
			// TODO: eventuell noch die Reihenfolge der Events ändern???
			if (prevChange!=null)
				prevChange.setCurrent(o);
			else {
				changesInTransaction().add(
					orig==null ? BeanChange.ADD(o) : BeanChange.UPDATE(o,orig));
//				addCollectionEvents(o);
			}
		}

		private void afterDelete(Object o) {
			if (o!=null && ignoreChangeEvents.contains(o.getClass()))
				return;

			changesInTransaction().add(BeanChange.DELETE(o));
//			addCollectionEvents(o);
		}

//		private void addCollectionEvents(Object o) {
//			Map<Class,List<Reference>> collectionRefs = bm.collectionRefs;
//			if (collectionRefs==null) return;
//			if (o instanceof TransferObject2==false) return;
//			TransferObject2 to = (TransferObject2)o;
//
//			// adviceclearing -> get stockchange instance -> clear sc.clearings
//			//                -> get advicechange instance -> clear ac.clearings
//			// TODO getTargetClass or getEqualsClass? type hierarchy (from targetclass to equalsclass)?
//			// TODO collect references
//			List<IBeanMetadata.Reference> refs = null;
//			if (refs==null) refs = collectionRefs.get(to.getTargetClass());
//			if (refs==null) refs = collectionRefs.get(to.getEqualsClass());
//			if (refs==null) return;
//			for (IBeanMetadata.Reference r : refs) {
//				Object to2 = r.referentField!=null ? ReflectHelper.read(to,r.referentField) : null;
//				if (to2 instanceof Collection) {
//					// TODO many-to-many case
//					// TODO probably ReflectHelper.read won't work everytime (hibernate collections/proxies)
////					for (Object to3 : ((Collection)to2)) {
////						if (to3!=null)
////							changesInTransaction.add(BeanChange.MAPPED(to3,r.referrerField));
////					}
//				} else if (to2!=null)
//					changesInTransaction().add(BeanChange.MAPPED(to2,r.referrerField));
//			}
//		}

		/** Liefert ein eventuell in dieser Transaktion bereits vorhandenes Insert/Update Event
		 * @param o
		 * @return
		 */
		private BeanChange getPreviousChangeInTransaction(TransferObject2 o) {
			if (o==null)
				return null;
			for (BeanChange beanChange : changesInTransaction()) {
				if (BeanChange.Type.UPDATE.equals(beanChange.getTypeOfChange()) ||
						BeanChange.Type.ADD.equals(beanChange.getTypeOfChange())) {
					if (beanChange.getCurrent()!=null
							&& beanChange.getCurrent() instanceof TransferObject2) {
						TransferObject2 toSaved = (TransferObject2)beanChange.getCurrent();
						if (toSaved.getId().equals(o.getId())) {
							Class cO = o.getClass();
							Class cOSaved = toSaved.getClass();
							if (cO.equals(cOSaved))
								return beanChange;
						}
					}
				}
			}
			return null;
		}

		private void fireUpdateEventsOfTransaction() throws Exception {
			if (changesInTransaction().isEmpty()) return;

			// summarize changes
			Map<BeanChange.Type,Map<Serializable,BeanChange>> changes = new LinkedHashMap();
			List<BeanChange> changesNoId = new LinkedList();
			Map<Class,List<HasId>> putCache = new HashMap();
			for (BeanChange c : changesInTransaction()) {
				Object bean = c.getCurrent();
				if (c.getTypeOfChange()==BeanChange.Type.DELETE)
					bean = c.getPrevious();
				if (bean instanceof HasId) {
					Serializable id = ((HasId)bean).getId();

					Class type = bean.getClass();
					OrmInfo info = bm.dbo.info(type);
					for (String cn : info.collectionColumns.keySet()) {
						String mn = cn;
						if (!info.propColumn.containsKey(mn)) {
							for (Map.Entry<String,String> e : info.propColumn.entrySet())
								if (cn.equals(e.getValue())) {
									mn = e.getKey();
									break;
								}
						}
						ReflectHelper.set(bean,mn,null);
					}
					MapUtils.groupT(putCache,ArrayList.class,bean.getClass(),(HasId)bean);

					Map<Serializable,BeanChange> changesByType = changes.get(c.getTypeOfChange());
					if (changesByType==null)
						changes.put(c.getTypeOfChange(),changesByType = new LinkedHashMap());
					changesByType.put(id,c);
				} else changesNoId.add(c);
			}
			for (Map.Entry<Class,List<HasId>> e : putCache.entrySet())
				bm.dc.putObjects(e.getKey(),e.getValue());

			if (bm.changesListener==null) {
				changesInTransaction().clear();
				return;
			}

			// delete > add > update > mapped
			Map mdelete = changes.get(BeanChange.Type.DELETE);
			Map madd = changes.get(BeanChange.Type.ADD);
			Map mupdate = changes.get(BeanChange.Type.UPDATE);
			Map mmapped = changes.get(BeanChange.Type.MAPPED);
			if (mupdate!=null) {
				if (mmapped!=null) mmapped.keySet().removeAll(mupdate.keySet());
			}
			if (madd!=null) {
				if (mupdate!=null) mupdate.keySet().removeAll(madd.keySet());
				if (mmapped!=null) mmapped.keySet().removeAll(madd.keySet());
			}
			if (mdelete!=null) {
				if (madd!=null) madd.keySet().removeAll(mdelete.keySet());
				if (mupdate!=null) mupdate.keySet().removeAll(mdelete.keySet());
				if (mmapped!=null) mmapped.keySet().removeAll(mdelete.keySet());
			}
			int before = changesInTransaction().size();
			changesInTransaction().clear();
			if (mdelete!=null) changesInTransaction().addAll(mdelete.values());
			if (madd!=null) changesInTransaction().addAll(madd.values());
			if (mupdate!=null) changesInTransaction().addAll(mupdate.values());
			if (mmapped!=null) changesInTransaction().addAll(mmapped.values());
			changesInTransaction().addAll(changesNoId);
			logger.debug("BeanManager.fireUpdateEventsOfTransaction() {} -> {}",before,changesInTransaction().size());

//			for (Iterator<BeanChange> it = changesInTransaction.iterator(); it.hasNext();) {
//				BeanChange c = it.next();
//				if (BeanChange.Type.UPDATE.equals(c.getTypeOfChange())) {
//					Object old = c.getPrevious();
//					Object nju = c.getCurrent();
//					Long oldVersion = (Long)ReflectHelper.get(old,"version");
//					Long njuVersion = (Long)ReflectHelper.get(nju,"version");
//					if (njuVersion!=null && njuVersion.equals(oldVersion)) it.remove();
//				}
//			}
			List<BeanChange> events = new ArrayList(changesInTransaction());
//				fireUpdateEvents.execute(new Runnable() {
//				public void run() {
					bm.changesListener.changesCommited(events);
//				}
//			});
			changesInTransaction().clear();
		}

		private void discardUpdateEventsOfTransaction() {
			changesInTransaction().clear();
		}
	}

//	private static final Executor fireUpdateEvents = Executors.newSingleThreadExecutor();

	private static class EntityManagerCloser extends WeakReference<Thread>
	{
		private static final ReferenceQueue<Thread> q = new ReferenceQueue();
		private final WriteAccess em;
		private final ThreadLocal<WriteAccess> threadEntityManagers;

		EntityManagerCloser(Thread t, WriteAccess em, ThreadLocal<WriteAccess> threadEntityManagers) {
			super(t,q);
			this.em = em;
			this.threadEntityManagers = threadEntityManagers;
		}

		static {
			new Timer("EntityManagerCloser",true).schedule(new TimerTask() {
				@Override
				public void run() {
					closeOrphanedEntityManagers();
				}
			},0,1000);
		}

		static void closeOrphanedEntityManagers() {
			EntityManagerCloser e;
			while ((e = (EntityManagerCloser)q.poll())!=null)
				closeEntityManager(e.em,e.threadEntityManagers);
		}

		static void closeEntityManager(WriteAccess m, ThreadLocal<WriteAccess> threadEntityManagers) {
			if (m==null) return;
			m.closeCurrentConnection();
			threadEntityManagers.remove();
		}
	}

	public interface IHistoryLogger
	{
		String FIELD_ROOT = "history.logger";
		String FIELD_URL = "url";
		String FIELD_USERNAME = "username";
		String FIELD_PASSWORD = "password";

		void init() throws Exception;
		void shutdown();

		void exclude(Class... types);

		enum TransactionState { open, commit, rollback }
		void beginTransaction(String transactionId);
		void endTransaction(String transactionId, TransactionState state);

		void change(String transactionId, Object orig, Object changed);
//		void log(String transactionId, String message, Throwable ex);
	}

	public static class Fields {
		private static final Map<Class,Fields> cached = new ConcurrentHashMap();
		static Fields of(Class c) {
			Fields f = cached.get(c);
			if (f==null) cached.put(c,f = new Fields(c));
			return f;
		}

		private final Class type;
		private final Field[] fields;
		private final Map<String,FieldInfo> fieldmap;

		private static class FieldInfo {
			Field field;
			Class vtype;
			Class dtype;
			FieldAccess access;
			int index;
		}

		public Fields(Class type) {
			this.type = type;
			this.fieldmap = new LinkedHashMap();
			ArrayList<Field> relevantFields = new ArrayList();
			Class stop = TransferObject2.class;
			for (Class c=type; c!=null && c!=stop; c=c.getSuperclass()) {
				Field[] fields = c.getDeclaredFields();
				for (int i=0; i<fields.length; i++) {
					Field f = fields[i];
					if (Modifier.isFinal(f.getModifiers())) continue;
					f.setAccessible(true);
					relevantFields.add(f);
					if (!fieldmap.containsKey(f.getName())) {
						FieldInfo fi = new FieldInfo();
						fi.field = f;
						fi.vtype = f.getType();
						fi.dtype = f.getDeclaringClass();
						fi.access = FieldAccess.get(fi.dtype);
						fi.index = fi.access.getIndex(f.getName());
						fieldmap.put(f.getName(),fi);
					}
				}
			}
			this.fields = relevantFields.toArray(new Field[relevantFields.size()]);
		}

		public Class type(String name) {
			return fieldmap.get(name).vtype;
		}

		public Object read(Object source, String name) {
			FieldInfo fi = fieldmap.get(name);
			if (fi==null) return null;
			return fi.access.get(source,fi.index);
		}

		public void write(Object target, String name, Object value) {
			FieldInfo fi = fieldmap.get(name);
			if (fi==null) return;
			fi.access.set(target,fi.index,value);
		}

		public void copy(Object target, Object source) {
			for (FieldInfo fi : fieldmap.values()) {
//				if (Modifier.isFinal(fi.field.getModifiers()))
//					continue;
				/**/ if (fi.vtype==boolean.class)
					fi.access.setBoolean(target,fi.index,fi.access.getBoolean(source,fi.index));
				else if (fi.vtype==long.class)
					fi.access.setLong(target,fi.index,fi.access.getLong(source,fi.index));
				else if (fi.vtype==int.class)
					fi.access.setInt(target,fi.index,fi.access.getInt(source,fi.index));
				else if (fi.vtype==double.class)
					fi.access.setDouble(target,fi.index,fi.access.getDouble(source,fi.index));
				else if (fi.vtype==float.class)
					fi.access.setFloat(target,fi.index,fi.access.getFloat(source,fi.index));
				else
					fi.access.set(target,fi.index,fi.access.get(source,fi.index));
			}
		}

//		public void read(Object target, String name) {
//			try {
//				Field f = fieldmap.get(name);
//				if (f==null) return null;
//				return f.get(target);
//			} catch (Exception ex) {
//				throw new RuntimeException(ex);
//			}
//		}
//
//		public void write(Object target, String name, Object value) {
//			try {
//				Field f = fieldmap.get(name);
//				if (f==null) return;
//				f.set(target,value);
//			} catch (Exception ex) {
//				throw new RuntimeException(ex);
//			}
//		}
//
//		public void copy(Object target, Object source) {
//			for (Field f : fields) {
//				if (Modifier.isFinal(f.getModifiers()))
//					continue;
//				Object obj = null;
//				try {
//					obj = f.get(source);
//				} catch (Exception ex) {
//					ex.printStackTrace();
//				}
//				try {
//					f.set(target,obj);
//				} catch (Exception ex) {
//					ex.printStackTrace();
//				}
//			}
//		}
	}
}
