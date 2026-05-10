package de.scheller.platform.persist;

import java.util.Collection;
import java.util.Iterator;

import javax.persistence.Query;

import de.scheller.model.persistence.IBeanManager;
import de.scheller.model.persistence.ITransactionData;

/**
 * @author kandzia
 */
public interface IServerBeanManager extends IBeanManager
{
	void addBeanHandler(BeanHandler l, Class[] classes);
	<T> Class<T> getEntityClass(Class<T> type);
	<T> Class<T> getEntityClass(String type);
	String getEntityName(Class entityType);
	String getIdentifierName(Class entityType);
	Query createQuery(String string);
	Object getUnmanaged(Object ref);
	Iterator stream(Query q);

	interface BeanHandler {
		String generateIdPrefix(Class objectClass);

		void preSave(Object o) throws VetoException, Exception;
		void afterSave(Object o, final Object orig) throws Exception;

		void preDelete(Object o) throws VetoException, Exception;
		void afterDelete(Object o) throws Exception;

		// TransactionData
		void saveTransactionData(Collection<ITransactionData> data) throws Exception;

		class VetoException extends Exception {}
		class VetoButCommitException extends Exception {}
	}

	interface ChangesListener {
		void changesCommited(Collection<BeanChange> changes);
	}

	class BeanChange<T extends Object> {
		public enum Type { ADD, UPDATE, DELETE, MAPPED };

		public static BeanChange ADD(Object o) { return new BeanChange(Type.ADD,o,null); }
		public static BeanChange DELETE(Object o) { return new BeanChange(Type.DELETE,null,o); }
		public static BeanChange UPDATE(Object o, Object orig) { return new BeanChange(Type.UPDATE,o,orig); }
		public static BeanChange MAPPED(Object o, String field) { return new BeanChange(Type.MAPPED,o,field); }

		private final Type typeOfChange;
		private T current;
		private final T previous;

		private BeanChange(Type typeOfChange, T current, T previous) {
			this.typeOfChange = typeOfChange;
			this.current = current;
			this.previous = previous;
		}

		public Type getTypeOfChange() { return typeOfChange; }
		public void setCurrent(T o) { current = o; }
		public T getCurrent() { return current; }
		public T getPrevious() { return previous; }
	}
}
