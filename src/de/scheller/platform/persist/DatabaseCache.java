package de.scheller.platform.persist;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.zip.Deflater;

import org.nustaq.serialization.FSTClazzInfo;
import org.nustaq.serialization.FSTClazzInfo.FSTFieldInfo;
import org.nustaq.serialization.FSTConfiguration;
import org.nustaq.serialization.FSTObjectInput;
import org.nustaq.serialization.FSTObjectOutput;
import org.nustaq.serialization.FSTObjectSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.scheller.common.HasId;
import de.scheller.data.convert.Convert;
import de.scheller.platform.persist.DatabaseImpl.Fields;
import de.scheller.platform.persist.DatabaseOrm.OrmInfo;
import de.scheller.platform.persist.DatabaseOrm.RecordMapInfo;
import de.scheller.platform.persist.bgzf.BlockCompressedInputStream;
import de.scheller.platform.persist.bgzf.BlockCompressedOutputStream;
import de.scheller.platform.persist.bgzf.ByteArraySeekableStream;
import de.scheller.platform.persist.util.ObjLongMap;
import de.scheller.platform.persist.util.ReflectHelper;
import de.scheller.transferobject.ITransferObject;
import de.scheller.transferobject.TransferObject2;
import de.scheller.util.Pair;

/**
 * @author kandzia
 */
public class DatabaseCache
{
	private static final Logger logger = LoggerFactory.getLogger(
			DatabaseCache.class.getPackage().getName()+"");

	DatabaseOrm orm;
	int compression;
	FSTConfiguration fst;
	Map<String,Class> types;
	Map<Class,ObjectCache> caches = new HashMap();

	public DatabaseCache(DatabaseOrm orm, int compression) throws Exception {
		this.orm = orm;
		this.compression = compression;
		this.types = orm.types;
//		this.fst = FSTConfiguration.createStructConfiguration(); // small, custom ser for Class
//		this.fst = FSTConfiguration.createUnsafeBinaryConfiguration(); // big
//		this.fst = FSTConfiguration.createAndroidDefaultConfiguration();
		this.fst = FSTConfiguration.createDefaultConfiguration();
//		for (Class type : types.values())
//			if (Serializable.class.isAssignableFrom(type))
//				this.fst.registerClass(type);

		this.types = new LinkedHashMap();
		for (Map.Entry<Class,String> e : orm.modelImplTypes.entrySet()) {
			this.types.put(e.getValue(),e.getKey());
		}
		for (Class impl : new ArrayList<Class>(types.values()))
			for (Pair<String,Class> g : ReflectHelper.getGetters(impl).keySet())
				if (Serializable.class.isAssignableFrom(g.getSecond()))
					fst.registerClass(g.getSecond());
		for (Class impl : new ArrayList<Class>(types.values()))
			for (Class intf : ReflectHelper.getClasses(impl,ReflectHelper.ClassStrategy.interfaces,null)) {
				Class known = types.get(intf.getName());
				if (known==null || impl.isAssignableFrom(known))
					types.put(intf.getName(),impl);
			}
		fst.registerClass(Class.class);
		fst.registerClass(Timestamp.class);
		fst.registerClass(types.values().toArray(new Class[0]));

		Map<Class,Integer> idByClass = new HashMap();
		Class[] typesById = new Class[types.size()];
		int id = 0;
		for (Class c : types.values()) {
			idByClass.put(c,id);
			typesById[id++] = c;
		}
		fst.registerSerializer(Class.class,new FSTObjectSerializer() {
			public void writeObject(
					FSTObjectOutput out, Object toWrite, FSTClazzInfo clzInfo,
					FSTFieldInfo referencedBy, int streamPosition) throws IOException {
				out.writeInt(idByClass.get(toWrite));
			}
			public boolean willHandleClass(Class cl) {
				return cl==Class.class;
			}
			public void readObject(
					FSTObjectInput in, Object toRead, FSTClazzInfo clzInfo,
					FSTFieldInfo referencedBy) throws Exception {
			}
			public Object instantiate(
					Class objectClass, FSTObjectInput fstObjectInput,
					FSTClazzInfo serializationInfo, FSTFieldInfo referencee, int streamPosition)
					throws Exception {
				return typesById[fstObjectInput.readInt()];
			}
			public boolean alwaysCopy() {
				return true;
			}
		},false);
		fst.registerSerializer(Timestamp.class,new FSTObjectSerializer() {
			public void writeObject(
					FSTObjectOutput out, Object toWrite, FSTClazzInfo clzInfo,
					FSTFieldInfo referencedBy, int streamPosition) throws IOException {
				out.writeLong(((Timestamp)toWrite).getTime());
			}
			public boolean willHandleClass(Class cl) {
				return cl==Timestamp.class;
			}
			public void readObject(
					FSTObjectInput in, Object toRead, FSTClazzInfo clzInfo,
					FSTFieldInfo referencedBy) throws Exception {
			}
			public Object instantiate(
					Class objectClass, FSTObjectInput fstObjectInput,
					FSTClazzInfo serializationInfo, FSTFieldInfo referencee, int streamPosition)
					throws Exception {
				return new Timestamp(fstObjectInput.readLong());
			}
			public boolean alwaysCopy() {
				return true;
			}
		},false);
		fst.registerSerializer(TransferObject2.class,new FSTObjectSerializer() {
			public void writeObject(
					FSTObjectOutput out, Object toWrite, FSTClazzInfo ci,
					FSTFieldInfo referencedBy, int streamPosition) throws IOException {
				if (referencedBy.getField()!=null) {
					TransferObject2 to = (TransferObject2)toWrite;
					out.writeInt(idByClass.get(to.getTargetClass()));
					out.writeStringUTF(to.getId()!=null ? String.valueOf(to.getId()) : null);
				} else out.defaultWriteObject(toWrite,ci);
			}
			public boolean willHandleClass(Class cl) {
				return cl==TransferObject2.class;
			}
			public void readObject(
					FSTObjectInput in, Object toRead, FSTClazzInfo ci,
					FSTFieldInfo referencedBy) throws Exception {
			}
			public Object instantiate(
					Class cl, FSTObjectInput in,
					FSTClazzInfo serializationInfo, FSTFieldInfo referencee, int streamPosition)
					throws Exception {
				if (referencee.getField()==null) {
					TransferObject2 to = (TransferObject2)serializationInfo.newInstance(false);
					in.defaultReadObject(referencee,serializationInfo,to);
					return to;
				}
				try {
					TransferObject2 to = (TransferObject2)typesById[in.readInt()].newInstance();
					to.setId(in.readStringUTF());
					return to;
				} catch (Exception ex) {
					throw new IllegalArgumentException("creating instance of class failed",ex);
				}
			}
			public boolean alwaysCopy() {
				return true;
			}
		},true);
	}

	public <T> Class<T> getEntityClass(Class<T> type) {
		if (orm.modelImplTypes.containsKey(type))
			return type;
		return orm.modelIntfTypes.get(type);
	}
	public <T> Class<T> getEqualsClass(Class type) {
		Class eqc = orm.eqclass.get(getEntityClass(type));
		return eqc!=null ? eqc : type;
	}
	public ObjectCache getObjectCache(Class c) {
		return caches.get(getEqualsClass(c));
	}

	public List<Serializable> getObjects(
			Class type, Map<Serializable,Object> byId, List<Serializable> ids)
	throws Exception {
		if (ids.isEmpty())
			return ids;
		ObjectCache tc = getObjectCache(type);
		if (tc==null)
			return ids;
		List<Serializable> remaining = tc.getObjects(byId,ids);
//		OrmInfo info = orm.info(type);
//		Fields fields = Fields.of(type);
		Map<Class,OrmInfo> im = new HashMap();
		Map<Class,Fields> fm = new HashMap();
		Function<Class,OrmInfo> info = t -> im.computeIfAbsent(t,tt -> orm.info(tt));
		Function<Class,Fields> fields = t -> fm.computeIfAbsent(t,tt -> Fields.of(tt));
		for (Object obj : byId.values()) {
			if (obj instanceof TransferObject2) {
				Class t = obj.getClass();
				TransferObject2 to = (TransferObject2)obj;
				to.setTransferState(ITransferObject.TransferState.unknown);
				for (String name : info.apply(t).targetTable.keySet()) {
					Object ref = fields.apply(t).read(to,name);
					if (ref instanceof TransferObject2) {
						TransferObject2 ro = (TransferObject2)ref;
						ro.setTransferState(ITransferObject.TransferState.blank);
					}
				}
			}
		}
		return remaining;
	}

	public List<Serializable> putObjects(Class type, Iterable<HasId> objects)
	throws Exception {
		Class eqc = getEqualsClass(type);
		ObjectCache tc = caches.get(eqc);
		if (tc==null)
			caches.put(eqc,tc = new ObjectCache(eqc,fst,compression));
		return tc.putObjects(objects);
	}

	public List<Serializable> putObjects(Class type, ResultSet rs) throws Exception {
		try {
			return putObjects(type,new RowsIterator(rs,type,type,types,orm));
		} finally {
			rs.close();
		}
	}

	public List<Serializable> putObjects(Class type, Connection conn, String sql)
	throws Exception {
		PreparedStatement ps = conn.prepareStatement(sql,
				ResultSet.TYPE_FORWARD_ONLY,
				ResultSet.CONCUR_READ_ONLY);
		ps.setFetchSize(Integer.MIN_VALUE);
		ResultSetMetaData rm = ps.getMetaData();
		ResultSet rs = ps.executeQuery();
		try {
			return putObjects(type,new RowsIterator(rs,type,type,types,orm));
		} finally {
			rs.close();
			conn.close();
		}
	}

	public void clearAll() {
		for (ObjectCache c : new ArrayList<>(caches.values()))
			c.clear();
	}

	public void clear(Class type) {
		Class eqc = getEqualsClass(type);
		ObjectCache tc = caches.get(eqc);
		if (tc!=null) tc.clear();
	}

	public static class ObjectCache {
		Class type;
		FSTConfiguration fst;
		int compression;
		ByteArrayOS baos = new ByteArrayOS();
//		Map<Serializable,Long> pos = new HashMap();
		ObjLongMap pos = new ObjLongMap(10000,0.75f);
		int baosIdxSize;
		int baosUnpSize;
		FSTConfiguration dfst = FSTConfiguration.createDefaultConfiguration();

		public ObjectCache(Class type, FSTConfiguration fst, int compression) {
			if (type==null)
				throw new IllegalArgumentException("require entity java type");
			this.type = type;
			this.fst = fst;
			this.compression = compression;
		}

		public synchronized void clear() {
			baos.reset();
			pos.clear();
			baosIdxSize = 0;
			baosUnpSize = 0;
		}

		public synchronized List<Serializable> putObjects(Iterable<HasId> objects) throws Exception {
			long t = System.currentTimeMillis();
			int count = 0;
			List<Serializable> ids = new ArrayList();
			Map<Serializable,Long> pos = new HashMap();
			int baosUnpSize = 0;
			int before = baos.size();
			try {
				BlockCompressedOutputStream bcos = compression!=Deflater.NO_COMPRESSION ?
						new BlockCompressedOutputStream(baos,compression) : null;
				if (bcos!=null)
					bcos.setIncremental(true,baos.size());
				OutputStream os = bcos!=null ? bcos : baos;
				for (HasId obj : objects) {
					Serializable id = obj.getId();
					if (bcos!=null)
						pos.put(id,bcos.getFilePointer()); // virtual pointer, see javadoc
					else pos.put(id,(long)baos.size());
					ids.add(id);
					byte[] bytes = fst.asByteArray(obj);
					int written = bytes.length;
					os.write((written >>> 0) & 0xff);
					os.write((written >>> 8) & 0xff);
					os.write((written >>> 16) & 0xff);
					os.write((written >>> 24) & 0xff);
					os.write(bytes);
					// fst.encodeToStream(compress,obj); flushes -> makes poor compression rate
					baosUnpSize += written;
					count++;
					if ((count%10000)==0) System.out.print(".");
				}
				os.close();
				//int len = baos.bytes().length;
				baos.trim();
				//System.out.println("trim "+len+" -> "+baos.bytes().length);
				for (Map.Entry<Serializable,Long> e : pos.entrySet())
					this.pos.put(e.getKey(),e.getValue());
				this.baosUnpSize += baosUnpSize;
			} catch (Exception ex) {
				throw ex;
			} catch (Throwable th) {
				throw new Exception(th);
			}
			if (count>0) {
				int total = pos.size();
				int after = baos.size();
				if (after/5000>before/5000) {
					CountBytesOS cbos = new CountBytesOS();
//					BlockCompressedOutputStream bcos = new BlockCompressedOutputStream(cbos,Deflater.BEST_SPEED);
//					FSTObjectOutput oos = dfst.getObjectOutput(bcos);
					FSTObjectOutput oos = dfst.getObjectOutput(cbos);
					oos.writeObject(pos);
					oos.flush();
					baosIdxSize = cbos.size();
				}
				logger.debug("{} {}ms for {}/{} records :: " +
						"added {}->{} bytes, avg {}->{}, " +
						"total {}->{} bytes, avg {}->{} for records :: " +
						"{} bytes, avg {} for index",
						type,(System.currentTimeMillis()-t),count,total,
						baosUnpSize,(after-before),
						(count>0 ? baosUnpSize/count : -1),
						(count>0 ? (after-before)/count : -1),
						this.baosUnpSize,after,
						(total>0 ? (before)/total : -1),
						(total>0 ? (after)/total : -1),
						baosIdxSize,
						(total>0 ? baosIdxSize/total : -1));
//				System.out.format(
//						"%s %dms for %d/%d records :: " +
//						"added %-4d->%4d bytes, avg %-4d->%4d, " +
//						"total %-4d->%4d bytes, avg %-4d->%4d for records :: " +
//						"%d bytes, avg %d for index\n",
//						type,(System.currentTimeMillis()-t),count,total,
//						baosUnpSize,(after-before),
//						(count>0 ? baosUnpSize/count : -1),
//						(count>0 ? (after-before)/count : -1),
//						this.baosUnpSize,after,
//						(total>0 ? (before)/total : -1),
//						(total>0 ? (after)/total : -1),
//						baosIdxSize,
//						(total>0 ? baosIdxSize/total : -1));
			}
			return ids;
		}

		public List<Serializable> getObjects(Map<Serializable,Object> byId, List<Serializable> ids)
		throws Exception {
			int count = 0;
			long[] vpos = new long[ids.size()];
			Map<Long,Serializable> idByPos = new HashMap();
			List<Serializable> remaining = new ArrayList();
			for (Serializable id : ids) {
				long p = pos.get(id); // returns Integer.MIN_VALUE if not found
				if (p!=Integer.MIN_VALUE && p!=Integer.MAX_VALUE) {
//				Long p = pos.get(id);
//				if (p!=null) {
					idByPos.put(p,id);
					vpos[count++] = p;
				} else remaining.add(id);
			}
			if (count==0)
				return remaining;
			Arrays.sort(vpos,0,count);

			ByteArraySeekableStream bass = new ByteArraySeekableStream(baos.bytes());
			BlockCompressedInputStream bcis = compression!=Deflater.NO_COMPRESSION ?
					new BlockCompressedInputStream(bass) : null;
			InputStream is = bcis!=null ? bcis : bass;
			byte[] buffer = new byte[10000];
			FSTObjectInput oi = fst.getObjectInput(buffer);
			for (int i=0; i<count; i++) {
				long pos = vpos[i];
				if (bcis!=null)
					bcis.seek(pos); // virtual pointer, see javadoc
				else bass.seek(pos);
				int b1 = (is.read() + 256) & 0xff;
				int b2 = (is.read() + 256) & 0xff;
				int b3 = (is.read() + 256) & 0xff;
				int b4 = (is.read() + 256) & 0xff;
				int len = (b4<<24) + (b3<<16) + (b2<<8) + (b1<<0);
				if (len<=0)
					throw new EOFException("stream is corrupted");
				if (len - buffer.length > 0) {
					int oldCapacity = buffer.length;
					int newCapacity = oldCapacity << 1;
					if (newCapacity - len < 0)
						newCapacity = len;
					buffer = new byte[newCapacity];
					oi.resetForReuseUseArray(buffer);
				}
//				buffer = new byte[len];
//				oi.resetForReuseUseArray(buffer);
				for (int off=0, read=len; read>0; off+=read)
					read -= is.read(buffer,off,read);
//				Arrays.fill(buffer,len,Math.min(len+16,buffer.length),(byte)0);
				oi.getCodec().moveTo(0);
				byId.put(idByPos.get(pos),oi.readObject());
//				System.out.println(result.get(result.size()-1));
			}
			is.close();
			oi.close();
			return remaining;
		}

		void markNotFound(List ids) {
			for (Object id : ids)
				pos.put(id,Integer.MAX_VALUE);
		}

		@Override
		public String toString() {
			return "ObjectCache["+type.getSimpleName()+", " +
					pos.size()+" entries, "+baos.size()+" bytes]";
		}
	}

	public static class RowsIterator<T> extends ResultSetIterator<T> {
		Class rtype;
		Map<String,Class> types;
		DatabaseOrm orm;
		Map<Class,RecordMapInfo> infos = new HashMap();
		Set<Integer> metaFields = new HashSet();
		String[] columnNames;
		String[] tableNames;
		// TODO support more than one type column?
		int typeColumn = -1;

		public RowsIterator(ResultSet rs, Class type, Class rtype, Map<String,Class> types, DatabaseOrm orm) throws SQLException {
			super(rs);
			this.rtype = rtype;
			this.types = types;
			this.orm = orm;
			infos.put(null,info(type)); // default
			if (this.rtype==Object.class && columnCount>1)
				this.rtype = Object[].class;
			columnNames = new String[columnCount];
			for (int c=0; c<columnCount; c++) {
				String cn = rm.getColumnName(c+1);
				columnNames[c] = cn;
				if (cn.startsWith("_table_")) {
					metaFields.add(c);
					if (typeColumn<0)
						typeColumn = c;
				}
			}
			if (typeColumn>=0) {
				tableNames = new String[columnCount];
				for (int c=0; c<columnCount; c++)
					tableNames[c] = rm.getTableName(c+1);
			}
		}

		RecordMapInfo info(Class type) throws SQLException {
			RecordMapInfo m = infos.get(type);
			if (m!=null) return m;
			if (type==null) return null;
			try {
				infos.put(type,m = orm.getMapInfo(type,rm));
				return m;
			} catch (Exception ex) {
				throw new SQLException(ex);
			}
		}

		@Override
		protected T readRow() throws Exception {
			Class type = null;
			if (typeColumn>=0)
				type = orm.types.get(rs.getObject(typeColumn+1));
			RecordMapInfo m = info(type);
			Object obj = null;
			/**/ if (rtype==Object[].class) obj = Array.newInstance(Object.class,columnCount);
			else if (rtype==Map.class) obj = new LinkedHashMap();
			else if (rtype!=Object.class) obj = (type!=null ? type : rtype).newInstance();

			Set<String> relevantTables = null;
			if (typeColumn>=0)
				relevantTables = new LinkedHashSet(orm.info(type).tables);
			for (int c=0; c<columnCount; c++) {
				if (metaFields.contains(c))
					continue;
				if (relevantTables!=null && !relevantTables.contains(tableNames[c]))
					continue;
				String cn = columnNames[c];
				Object value = rs.getObject(c+1);
				Class vtype = types.get(String.valueOf(c));
				if (vtype==null)
					vtype = m!=null ? m.fieldtypes[c] : null;
				if (value!=null && vtype!=null) {
					if (ITransferObject.class.isAssignableFrom(vtype) && types.containsKey(vtype.getName())) {
						vtype = types.get(vtype.getName());
						ITransferObject to = (ITransferObject)vtype.newInstance();
						((TransferObject2)to).setTransferState(ITransferObject.TransferState.blank);
						to.setId((Serializable)value);
						value = to;
					} else {
						value = Convert.get(value,vtype);
					}
				}
				if (rtype==Object.class)
					return (T)value;
				if (rtype==Object[].class) {
					Array.set(obj,c,value);
				} else if (rtype==Map.class) {
					((Map)obj).put(cn,value);
				} else {
					BiConsumer setter = m.setters[c];
					if (setter!=null)
						setter.accept(obj,value);
				}
			}
			return (T)obj;
		}
	}

	public static abstract class ResultSetIterator<T>
	implements Iterator<T>, Iterable<T>, Closeable, AutoCloseable {
		protected final ResultSet rs;
		protected final ResultSetMetaData rm;
		protected final int columnCount;
		private boolean doNext = true;
		private boolean next;

		public ResultSetIterator(ResultSet rs) throws SQLException {
			this.rs = rs;
			this.rm = rs.getMetaData();
			this.columnCount = rm.getColumnCount();
		}

		protected abstract T readRow() throws Exception;

		public ResultSet getResultSet() { return rs; }
		public ResultSetMetaData getMetaData() { return rm; }
		public Iterator<T> iterator() { return this; }

		public void close() {
			QueryImpl.closeChain(rs);
		}

		public boolean hasNext() {
			if (doNext) try {
				next = rs.next(); // moves the cursor
				if (!next)
					QueryImpl.closeChain(rs);
			} catch (SQLException ex) {
				throw new RuntimeException(ex);
			}
			doNext = false;
			return next;
		}

		public T next() {
			if (doNext) hasNext();
			if (!next)
				throw new NoSuchElementException();
			doNext = true; // in any condition to avoid infinite looping
			try {
				return readRow();
			} catch (Exception ex) {
				throw new RuntimeException(ex);
			}
		}

		public void remove() {
			try {
				rs.deleteRow();
			} catch (SQLException ex) {
				throw new RuntimeException(ex);
			}
		}
	}

	/** direct byte array access, trimmable, not synchronized */
	static class ByteArrayOS extends ByteArrayOutputStream  {
		public ByteArrayOS() { super(10000); }
		public ByteArrayOS(byte[] bytes) { buf = bytes; count = buf.length; }
		byte[] bytes() { return buf; }
		void trim() { buf = toByteArray(); count = buf.length; }

		private void ensureCapacity(int minCapacity) {
			if (minCapacity - buf.length > 0) // overflow-conscious code
				grow(minCapacity);
		}
		private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;
		private void grow(int minCapacity) {
			int oldCapacity = buf.length;
			int newCapacity = oldCapacity << 1;
			if (newCapacity - minCapacity < 0) // overflow-conscious code
				newCapacity = minCapacity;
			if (newCapacity - MAX_ARRAY_SIZE > 0)
				newCapacity = hugeCapacity(minCapacity);
			buf = Arrays.copyOf(buf, newCapacity);
		}
		private int hugeCapacity(int minCapacity) {
			if (minCapacity < 0) // overflow
				throw new OutOfMemoryError();
			return (minCapacity > MAX_ARRAY_SIZE) ? Integer.MAX_VALUE : MAX_ARRAY_SIZE;
		}
		@Override
		public void write(int b) {
			ensureCapacity(count+1);
			buf[count] = (byte)b;
			count += 1;
		}
		@Override
		public void write(byte b[], int off, int len) {
			if (off < 0 || off > b.length || len < 0 || off+len-b.length > 0)
				throw new IndexOutOfBoundsException();
			ensureCapacity(count+len);
			System.arraycopy(b,off,buf,count,len);
			count += len;
		}
	}

	/** allocates no memory, just count bytes */
	static class CountBytesOS extends ByteArrayOutputStream  {
		public CountBytesOS() {}
		@Override public void write(int b) { count += 1; }
		@Override public void write(byte b[], int off, int len) { count += len; }
	}
}
