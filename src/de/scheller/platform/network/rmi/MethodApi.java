package de.scheller.platform.network.rmi;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

import de.scheller.platform.network.rmi.Failure.RemoteException;

/**
 * @author kandzia
 * @author Joshua Tauberer (tauberer@for.net)
 */
public interface MethodApi
{
	/**
	 * Use as method return type to mark methods which will return to the calling
	 * process immediately, without a chance to return a value or throw an exception.
	 */
	interface ReturnImmediately {}

	/**
	 * Use to mark classes/types which shall be passed by value (serialized).
	 */
	interface SerializableDataType extends Serializable {}

	/**
	 * Use as method return type or method parameter type to indicate that the object
	 * must be passed by reference (interface proxy).
	 */
	interface Reference {}

	/**
	 * Use to restricted access to a service/interface.
	 */
	interface RestrictedService {
		/**
		 * Before a restricted service is attached to,
		 * this method is called to verify the access.
		 *
		 * @exception RemoteException throw if the connection is refused, otherwise return silently.
		 */
		void verifyAccess(RestrictedServiceToken token) throws RemoteException;
		void releaseAccess();
	}

	class RestrictedServiceToken implements SerializableDataType {
		private final Map<String,String> tokens = new LinkedHashMap();
		public void put(String key, String value) { tokens.put(key,value); }
		public String get(String key) { return tokens.get(key); }
	}
}
