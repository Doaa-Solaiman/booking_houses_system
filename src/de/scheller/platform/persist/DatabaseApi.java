package de.scheller.platform.persist;

import java.io.Closeable;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.scheller.model.persistence.ITransactionData;

/**
 * @author kandzia
 */
public interface DatabaseApi
{
	<T> T createInstance(Class<T> c);
	<T> List<T> createInstances(Class<T> c, int count, boolean generateId);
	<T> String generateId(Class<T> c);

	long getCount(Class c);
	<T> List<T> getAll(Class<T> c);
	List getAllValues(Class c, String field);
	<T extends Serializable> T load(Class<T> c, Serializable id);
	<T,S extends Serializable> Collection<T> loadBulk(Class<T> c, Collection<S> ids);

	/**
	 * Bulk-query field values or objects and mapping them to object IDs.
	 * If an object for an ID was not found, then the ID is not contained in the
	 * key set of the returned map. Using {@link Map#containsKey(Object)} you can
	 * check for the existence of objects. With getting <tt>null</tt> from
	 * {@link Map#get(Object)} only, you don't know if the field value is
	 * <tt>null</tt> OR the object was not found.
	 *
	 * @param c object type for which the query is executed
	 * @param ids IDs for which the query is executed
	 * @param field for which the values are returned or <tt>null</tt> to get the full objects
	 * @return field values (or objects) mapped to IDs
	 */
	<S extends Serializable> Map<S,?> load(Class c, Collection<S> ids, String field);

	<T> T save(T o) throws Exception;
	Collection saveAll(Collection o) throws Exception;
	void saveData(ITransactionData... data) throws Exception;
	void saveData(Collection<ITransactionData> data) throws Exception;

	void delete(Object o) throws Exception;
	void delete(Object o, boolean force) throws Exception;
	void deleteAll(Collection o) throws Exception;
	void deleteAll(Collection o, boolean force) throws Exception;

//	interface ITransactionData<T> extends Serializable {
//		enum Type { save, replace, delete }
//
//		Type getType();
//		ITransactionData getReference();
//		T getEntity();
//	}

	<T> IResult<T> query(int blockSize, Class<T> entityType, Object... idAndValueAndExpr);
	IResult query(int blockSize, String fields, Class entityType, Object... idAndValueAndExpr);

	interface IResult<T> extends Iterable<List<T>>, Closeable {
		void close();
		int count();

		default Set<T> asSet() {
			try {
				Set set = new LinkedHashSet();
				for (List l : this) set.addAll(l);
				return set;
			} finally {
				close();
			}
		}

		default List<T> asList() {
			try {
				List list = new ArrayList();
				for (List l : this) list.addAll(l);
				return list;
			} finally {
				close();
			}
		}
	}

	List<Reference> getObjectRefs();
	List<Reference> getCollectionRefs();

	class Reference implements Serializable {
		public String referrerClass;
		public String referrerField;

		/** not null for collection fields */
		public Boolean referrerMapped;

		public String referentClass;
		public String referentField;

		@Override
		public String toString() {
			return referrerClass+"."+referrerField+" -> "+referentClass+"."+referentField;
		}

		@Override
		public int hashCode() {
			int h = 0;
			if (referrerClass!=null) h += referrerClass.hashCode();
			if (referrerField!=null) h += referrerField.hashCode();
			if (referentClass!=null) h += referentClass.hashCode();
			if (referentField!=null) h += referentField.hashCode();
			return h;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj==this) return true;
			if (obj instanceof Reference==false) return false;
			Reference other = (Reference)obj;
			if (referrerClass!=null && !referrerClass.equals(other.referrerClass)) return false;
			if (referrerField!=null && !referrerField.equals(other.referrerField)) return false;
			if (referentClass!=null && !referentClass.equals(other.referentClass)) return false;
			if (referentField!=null && !referentField.equals(other.referentField)) return false;
			return true;
		}
	}
}
