package de.scheller.platform.network.rmi;

import java.lang.reflect.Constructor;

import de.scheller.platform.network.rmi.Failure.MarshallingException;

/**
 * @author kandzia
 * @author Joshua Tauberer (tauberer@for.net)
 */
public interface InternalApi
{
	interface ConnectionListener {
		void connectionOpened(RmiClient client, Object service);
		void connectionClosed(RConnection socket);
	}

	interface ConnectionListener2 extends ConnectionListener {
		void connectionTerminated(RConnection socket);
	}

	interface ActivityListener {
		void commandRequest(long id, long rq);
		void commandResponse(long id, long rs);
	}

	interface InvocationListener {
		void beforeInvocation();
		void afterInvocation();
	}

	interface IObjectResolver {
		void init();
		Object resolveObject(Object obj);
		void shutdown();
	}

	class Transformation<T,M> {
		final Class<T> objectType;
		final Class<M> mediumType;
		final boolean asProxy;
		final Class wrapper;
		final Class unwrapper;
		final Constructor cwrap;
		final Constructor cunwrap;

		public Transformation(Class<T> objectType, Class<M> mediumType, boolean asProxy) {
			this.objectType = objectType;
			this.mediumType = mediumType;
			this.asProxy = asProxy;
			this.wrapper = null;
			this.unwrapper = null;
			this.cwrap = null;
			this.cunwrap = null;
		}

		public Transformation(Class<T> objectType, Class<M> mediumType, boolean asProxy, Class wrapper, Class unwrapper) throws IllegalArgumentException, NoSuchMethodException {
			this.objectType = objectType;
			this.mediumType = mediumType;
			this.asProxy = asProxy;
			this.wrapper = wrapper;
			this.unwrapper = unwrapper;

			if (!mediumType.isAssignableFrom(wrapper))
				throw new IllegalArgumentException("Wrapper class " + wrapper + " is not a " + mediumType);
			if (!objectType.isAssignableFrom(unwrapper))
				throw new IllegalArgumentException("Unwrapper class " + unwrapper + " is not a " + objectType);

			cwrap = wrapper.getConstructor(new Class[] { objectType });
			cunwrap = unwrapper.getConstructor(new Class[] { mediumType });
		}

		public M toMedium(T object) throws MarshallingException {
			if (wrapper==null)
				throw new MarshallingException("using template marshall wrapper to marshall.");
			if (!objectType.isAssignableFrom(object.getClass()))
				throw new MarshallingException("cannot wrap "+object.getClass()+" with "+this);
			try {
				return (M)cwrap.newInstance(new Object[] { object });
			} catch (Exception ex) {
				throw new MarshallingException(ex.getMessage(),ex);
			}
		}

		public T fromMedium(M medium) throws MarshallingException {
			if (unwrapper==null)
				throw new MarshallingException("using template marshall wrapper to unmarshall.");
			if (!mediumType.isAssignableFrom(medium.getClass()))
				throw new MarshallingException("cannot unwrap "+medium.getClass()+" with "+this);
			try {
				return (T)cunwrap.newInstance(new Object[] { medium });
			} catch (Exception ex) {
				throw new MarshallingException(ex.getMessage(),ex);
			}
		}

		@Override
		public String toString() {
			if (wrapper==null || unwrapper==null)
				return getClass().getName()+" marshalls "+objectType+" into "+mediumType;
			return getClass().getName()+" marshalls "+objectType+" into "+mediumType+", using "+wrapper+" and "+unwrapper;
		}

		@Override
		public int hashCode() {
			return objectType.hashCode();
		}

		@Override
		public boolean equals(Object o) {
			if (o==this) return true;
			if (o instanceof Transformation==false) return false;
			Transformation other = (Transformation)o;
			return objectType==other.objectType;
		}

		public boolean canHandle(Class<T> parameter) {
			return true;
		}
	}
}
