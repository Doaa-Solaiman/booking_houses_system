package de.scheller.platform.network.rmi;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Serializable;
import java.io.Writer;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.net.SocketException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import de.scheller.platform.network.rmi.Failure.MarshallingException;
import de.scheller.platform.network.rmi.Failure.RemoteException;
import de.scheller.platform.network.rmi.Failure.SubstituteException;
import de.scheller.platform.network.rmi.InternalApi.ConnectionListener;
import de.scheller.platform.network.rmi.InternalApi.IObjectResolver;
import de.scheller.platform.network.rmi.InternalApi.InvocationListener;
import de.scheller.platform.network.rmi.InternalApi.Transformation;
import de.scheller.platform.network.rmi.MethodApi.Reference;
import de.scheller.platform.network.rmi.MethodApi.RestrictedServiceToken;
import de.scheller.platform.network.rmi.MethodApi.ReturnImmediately;
import de.scheller.platform.network.rmi.MethodApi.SerializableDataType;
import de.scheller.platform.network.rmi.RCommand.CommandID;
import de.scheller.platform.network.rmi.Stream.IInputStream;
import de.scheller.platform.network.rmi.Stream.IOutputStream;
import de.scheller.platform.network.rmi.Stream.InputStreamUnwrap;
import de.scheller.platform.network.rmi.Stream.InputStreamWrap;
import de.scheller.platform.network.rmi.Stream.OutputStreamUnwrap;
import de.scheller.platform.network.rmi.Stream.OutputStreamWrap;
import de.scheller.platform.network.rmi.Stream.ReaderInputStream;
import de.scheller.platform.network.rmi.Stream.WriterOutputStream;

/**
 * This class: tracks locally and remotely referenced objects, marshalls/unmarshalls objects,
 * tracks running invocations, does invocations, proxies remote interfaces etc.
 *
 * @author Joshua Tauberer (tauberer@for.net)
 * @author kandzia
 */
abstract class RCore implements InvocationHandler
{
	protected static final Logger logger = Logger.getLogger("rmi");

	public static final int MAX_CALL_STACK_SIZE = 50;

	/** default timeout for blocking transactions (in milliseconds) */
	public static long timeout = 60000;
	public static boolean ignoreNoClassDefFound = false;
//	public static ClassLoader baseclassloader = RCore.class.getClassLoader();
	public static ClassLoader baseclassloader = ClassLoader.getSystemClassLoader();
	public static boolean usethreadcontextclassloader = true;

	/** This point will terminate automatically when there are no more local proxies. */
	public boolean terminateWhenDone = false;

	protected String name; // for debugging
	@Override
	public String toString() { return name; }

	protected boolean noCompression;
	protected Map<String,Object> services = new HashMap(); // services by name
	protected boolean terminate = false; // on its way out of use

	protected Map<CommandID,MethodReturn> openTransactions = new HashMap(); // tracking blocking transactions

	// TRACKING LOCAL OBJECTS THAT ARE REFERENCED REMOTELY
	private final HashMap remotelyReferencedID = new HashMap(); // hashed by OBJID
	private final HashMap remotelyReferencedOBJ = new HashMap(); // hashed by OBJHASH
	private final HashMap remotelyReferencedCountID = new HashMap(); // refcounts hashed by OBJID

	// TRACKING LOCAL PROXIES THAT ARE REFERENCES FOR REMOTE OBJECTS
	private static HashMap localproxiesID = new HashMap(); // hashed by OBJID
	private static HashMap localproxiesHASH = new HashMap(); // hashed by OBJHASH
	private static ReferenceQueue localproxiesEnqued = new ReferenceQueue(); // gone out of scope
	protected Set<LocalProxyReference> localproxiesME = new HashSet(); // just my references

	public RCore(String name) {
		this.name = name;
		try { // default trasformations
			addTransformation(InputStream.class,IInputStream.class,true,InputStreamWrap.class,InputStreamUnwrap.class);
			addTransformation(OutputStream.class,IOutputStream.class,true,OutputStreamWrap.class,OutputStreamUnwrap.class);
			Charset cs = Charset.forName("utf-8");
			addTransformation(new Transformation(Reader.class,IInputStream.class,true) {
				@Override
				public Object toMedium(Object w) throws MarshallingException {
					if (w==null) return null;
					if (w instanceof Reader)
						return new InputStreamWrap(new ReaderInputStream((Reader)w,cs));
					throw new MarshallingException("cannot wrap "+w.getClass()+" with "+this);
				}
				@Override
				public Object fromMedium(Object w) throws MarshallingException {
					if (w==null) return null;
					if (w instanceof IInputStream)
						return new InputStreamReader(new InputStreamUnwrap((IInputStream)w),cs);
					throw new MarshallingException("cannot unwrap "+w.getClass()+" with "+this);
				}
			});
			addTransformation(new Transformation(Writer.class,IOutputStream.class,true) {
				@Override
				public Object toMedium(Object w) throws MarshallingException {
					if (w==null) return null;
					if (w instanceof Writer)
						return new OutputStreamWrap(new WriterOutputStream((Writer)w,cs.name()));
					throw new MarshallingException("cannot wrap "+w.getClass()+" with "+this);
				}
				@Override
				public Object fromMedium(Object w) throws MarshallingException {
					if (w==null) return null;
					if (w instanceof IOutputStream)
						return new OutputStreamWriter(new OutputStreamUnwrap((IOutputStream)w),cs);
					throw new MarshallingException("cannot unwrap "+w.getClass()+" with "+this);
				}
			});
		} catch (NoSuchMethodException e) {
			throw new Error(e);
		}
		rmicores.add(this);
	}

	private static Set<RCore> rmicores = new HashSet();

	protected static ThreadLocal<RCore> threadconnection = new ThreadLocal();
	protected static ThreadLocal<Integer> threadcallstack = new ThreadLocal();
	protected static ThreadLocal<Object> threadcallobject = new ThreadLocal();

	public static Set<RCore> invokers() { return rmicores; }
	public static RCore invoker() { return threadconnection.get(); }
	public static Integer getCallStackDepth() { return threadcallstack.get(); }
	public static Object getInvokeTarget() { return threadcallobject.get(); }

	protected static Set<InvocationListener> invkListeners = Collections.synchronizedSet(new LinkedHashSet());

	public static void addInvocationListener(InvocationListener l) {
		invkListeners.add(l);
	}

	public static void removeInvocationListener(InvocationListener l) {
		invkListeners.remove(l);
	}

	public static void fireBeforeInvocation() {
		for (InvocationListener l : invkListeners.toArray(new InvocationListener[0]))
			l.beforeInvocation();
	}

	public static void fireAfterInvocation() {
		for (InvocationListener l : invkListeners.toArray(new InvocationListener[0]))
			l.afterInvocation();
	}

	/**
	 * Returns a reference to the service on the remote machine.
	 *
	 * @param name the name of the remote service
	 * @param token a restricted service access token, or null if not needed
	 * @exception RemoteException if the service is not found, unavailable, or restricted
	 * @return the reference to the service
	 */
	public Object findService(String name, RestrictedServiceToken token) throws RemoteException {
		try {
			RCommand j = RCommand.SERVICE(name, token);
			MethodReturn ret = new MethodReturn(j);
			try {
				send(j);
			} catch (IOException e) {
				throw new RemoteException("transfer error", e);
			}
			ret.hold();
			try {
				ret.except();
			} catch (RemoteException re) {
				throw re;
			} catch (Throwable t) {
				throw new RemoteException(t.getMessage(),t);
			}
			Object[] rets = (Object[])ret.returnvalue;
			Object o = proxyRemoteObject((Class [])rets[0], (ObjectID)rets[1]);

			if (connectionListener!=null && o!=null && this instanceof RmiClient)
				connectionListener.connectionOpened((RmiClient)this,o);

			return o;
		} catch (RemoteException ex) {
			ex.printStackTrace();
			close();
			throw ex;
		} catch (Throwable ex) {
			ex.printStackTrace();
			close();
			return null;
		}
	}

	public Object findService(String name) throws  RemoteException {
		return findService(name, null);
	}

	private ClassLoader remoteClassLoader;
	private ClassLoader getRemoteClassLoader() {
		if (!usethreadcontextclassloader)
			return getClass().getClassLoader();
		if (remoteClassLoader!=null && remoteClassLoader.getParent()==baseclassloader)
			return remoteClassLoader;
		return remoteClassLoader = new ClassLoader(baseclassloader) {
			private final Map<String,Class> classCache = new HashMap();
			@Override
			public Class<?> loadClass(String name) throws ClassNotFoundException {
				Class c = classCache.get(name);
				if (c!=null) return c;
				boolean array = name.startsWith("[L") && name.endsWith(";");
				if (array)
					c = loadClass(name.substring(2,name.length()-1));
				else c = super.loadClass(name);
				if (array)
					c = Array.newInstance(c,0).getClass();
				classCache.put(name,c);
				return c;
			}
			@Override
			protected Class<?> findClass(String name) throws ClassNotFoundException {
				ClassLoader l = Thread.currentThread().getContextClassLoader();
				if (l!=null) return l.loadClass(name);
				throw new ClassNotFoundException(name);
			}
		};
	}

	private synchronized Object proxyRemoteObject(Class[] ifaces, ObjectID id) {
		// If we already have a proxy, use that.
		LocalProxyReference ref = getProxyRef(id);
		Object proxy = ref!=null ? ref.get() : null;
		if (proxy!=null)
			return proxy;

		if (ref!=null)
			forgetProxyRef(ref);

		Object o = Proxy.newProxyInstance(getRemoteClassLoader(),ifaces,this);
		ref = new LocalProxyReference(o,this,id,o.getClass(),localproxiesEnqued);
		localproxiesHASH.put(new Integer(System.identityHashCode(o)),ref);
		localproxiesID.put(id,ref);
		localproxiesME.add(ref);

		Object invkTarget = threadcallobject.get();
		if (invkTarget!=null)
			newProxyCreated(o,invkTarget);
		return o;
	}

	protected void newProxyCreated(Object proxy, Object invkContext) {}

	protected static LocalProxyReference getProxyRef(Object object) {
		return (LocalProxyReference)localproxiesHASH.get(new Integer(System.identityHashCode(object)));
	}
	protected static LocalProxyReference getProxyRef(ObjectID id) {
		return (LocalProxyReference)localproxiesID.get(id);
	}

	/**
	 * Returns whether the object is a proxy for a remote object.
	 *
	 * @param object the object to test
	 * @return whether the object is a proxy for a remote object
	 */
	public static boolean isRemote(Object object) {
		LocalProxyReference ref = getProxyRef(object);
		return (ref != null);
	}

	/**
	 * Gets the socket that corresponds to the remote object.
	 *
	 * @param object the object to test
	 * @return the socket that this object is on
	 */
	public static RCore objectPoint(Object object) {
		LocalProxyReference ref = getProxyRef(object);
		if (ref == null) return null;
		return ref.server;
	}

	/**
	 * Returns a local copy of a remote object.
	 * The remote object must support serialization.
	 *
	 * @param object the object to clone
	 * @return the local copy of the object
	 */
	public static Object localClone(Object object) throws RemoteException, MarshallingException {
		LocalProxyReference ref = getProxyRef(object);
		if (ref == null) throw new MarshallingException("Object is not a remote object.");
		return ref.server.localCloneInternal(ref.id);
	}

	protected Object localCloneInternal(ObjectID objID) throws RemoteException, MarshallingException {
		RCommand j = RCommand.TRANSFER(objID);
		MethodReturn ret = new MethodReturn(j);
		try {
			send(j);
		} catch (IOException e) {
			throw new RemoteException("transfer error", e);
		}
		ret.hold();
		if (ret.exception != null) throw (MarshallingException)ret.exception.fillInStackTrace();
		return ret.returnvalue;
	}

	protected Object getLocal(ObjectID id) {
		return remotelyReferencedID.get(id);
	}

	protected synchronized ObjectID referenceLocalObject(Object object) {
		ObjectID id = (ObjectID)remotelyReferencedOBJ.get(object);
		if (id==null) {
			id = new ObjectID(name);
			remotelyReferencedID.put(id,object);
			remotelyReferencedOBJ.put(object,id);
		}

		Integer count = (Integer)remotelyReferencedCountID.get(id);
		count = new Integer(count==null ? 1 : count.intValue()+1);
		remotelyReferencedCountID.put(id,count);
//		System.out.println("referenceLocalObject() "+id+" ("+object+") referenced "+count+" times");

		return id;
	}
	protected synchronized Object unreferenceLocalObject(ObjectID id) {
		Object object = remotelyReferencedID.get(id);
		if (object==null) {
			logger.warning(name + "> release on unreferenced " + id);
			return null;
		}

		int newCount = -1;
		Integer count = (Integer)remotelyReferencedCountID.get(id);
		if (count!=null) {
			newCount = count.intValue()-1;
			if (newCount>0)
				remotelyReferencedCountID.put(id,new Integer(newCount));
			else remotelyReferencedCountID.remove(id);
//			System.out.println("unreferenceLocalObject() "+id+" ("+object+") referenced "+newCount+" times");
		}

		if (newCount<0) {
			System.err.println("RefCount: Something is strange :-/. Check that!");
		}
		if (newCount==0 && object!=null) {
			remotelyReferencedID.remove(id);
			remotelyReferencedOBJ.remove(object);
		}
		return object;
	}

	static Class[] makeClassArray(Object o[]) {
		int i;
		Class ca[] = new Class[o.length];
		for (i = 0; i < o.length; i++) {
			if (o[i] != null) ca[i] = o[i].getClass();
		}
		return ca;
	}

	protected Serializable marshall(Object object, Class marshallAs, Object[] args)
	throws MarshallingException {
		if (object==null) return null;
		if (object instanceof Serialized)
			throw new MarshallingException("Cannot marshall a MarshalledObject");

		LocalProxyReference ref = getProxyRef(object);
		if (ref!=null) {
			// Passing a remote object.
			if (ref.server==this) {
				// Object resides on remote server.
				return Serialized.RECEIVERSIDE(ref.id);
			}
			// Marshall the proxy itself. We know the proxy can be
			// unmarshalled later because we were able to make a proxy ourselves.
			// Pass by reference.
			return Serialized.SENDERSIDE(marshallAs, referenceLocalObject(object));
		}
		// Passing a local object.
		/*
		When the object (o) or parameter/return type (t) is...

		t = Reference				by reference
		o = primitive/java.lang.*	by value, no compressoin
		o = SerializableDataType	by value, with compression
		t = transformable			by reference
		t = interface				by reference
		o = Serializable			by value, with compression
		t = Object					by reference, if implements interfaces

		primatives and java.lang.* are always passed by value
		SerializableDataType's are always passed by value
		Serializable's are passed
			by reference, if the formal type is an interface (except the Serializable interface)
			by value, if the formal type is not an interface (or the Serializable interface)
		Nonserializables are passed
			by reference, if the formal type is an interface
			by reference, if the formal type is Object and the actual type implements interfaces
			otherwise it cannot be passed and an exception is thrown
		*/

		// If passing by reference...
		if (marshallAs==Reference.class) {
			if (object.getClass().getInterfaces().length==0)
				throw new MarshallingException("Cannot pass " + object.getClass() + " by reference");
			return Serialized.SENDERSIDE(object.getClass(),referenceLocalObject(object));
		}

		// Primitive objects and short strings get passed by value without compression.
		if (doPassByValue(marshallAs,object)) return (Serializable)object;

		// SerializableDataTypes get passed by value.
		if (object instanceof SerializableDataType) return Serialized.SERIALIZED(this,(Serializable)object);

		// Use transformations to coerce a reference _or_ to serialize/transmit within another medium
		if (marshallAs!=String.class) {
			Class c = marshallAs;
			if (c!=Object.class) {
				Transformation t = getTransformation(c);
				if (t!=null) {
					Object medium = t.toMedium(object);
					if (!t.asProxy) {
						if (!(object instanceof Serializable)) return (Serializable)medium;
						return doPassByValue(marshallAs,medium)
							? (Serializable)medium
							: Serialized.SERIALIZED(this,(Serializable)medium);
					}
					return Serialized.SENDERSIDE(t.mediumType,referenceLocalObject(medium));
				}
			}
			Transformation t = getTransformation(object.getClass());
			if (t!=null) {
				Object medium = t.toMedium(object);
				if (!t.asProxy) {
					if (!(object instanceof Serializable)) return (Serializable)medium;
					return doPassByValue(marshallAs,medium)
						? (Serializable)medium
						: Serialized.SERIALIZED(this,(Serializable)medium);
				}
				return Serialized.SENDERSIDE(t.mediumType,referenceLocalObject(medium));
			}
		}

		// When the server is expecting an interface, pass by reference,
		// except in the case...
		if (marshallAs.isInterface()) {
			// ...when it is expecting a java.util.Collection and that one is serializable
			if (Collection.class.isAssignableFrom(marshallAs) && object instanceof Serializable)
				return Serialized.SERIALIZED(this,(Serializable)object);
			if (Map.class.isAssignableFrom(marshallAs) && object instanceof Serializable)
				return Serialized.SERIALIZED(this,(Serializable)object);
			if (Serializable.class.isAssignableFrom(marshallAs) && object instanceof Serializable)
				return Serialized.SERIALIZED(this,(Serializable)object);
			// ...when server says it needs to get serialized
//			if ()
//				return MarshalledObject.SERIALIZED(this,(Serializable)object);
			// ...when it is expecting a Serializable object.
			if (marshallAs!=Serializable.class)
				return Serialized.SENDERSIDE(marshallAs,referenceLocalObject(object));
		}

		// Send in Serialized Form
		if (object instanceof Serializable)
			return Serialized.SERIALIZED(this,(Serializable)object);

		// If the server is expecting an object of type Object, and we can send
		// this object by reference (it implements interfaces), do that.
		Class cls = object.getClass();
		Class[] intf = cls.getInterfaces();
		if (marshallAs == Object.class && intf.length > 0) {
			if (intf.length==1) {
				cls = intf[0];
			} else if (args!=null) { // be smart
				HashSet s = new HashSet(Arrays.asList(intf));
				s.retainAll(Arrays.asList(args));
				if (s.size()==1) cls = (Class)s.iterator().next();
			}
			return Serialized.SENDERSIDE(cls,referenceLocalObject(object));
		}

		// Couldn't find a way to marshall the object.
		throw new MarshallingException("Object could not be marshalled: " + object.getClass() + ", expecting " + marshallAs);
	}

	private final boolean doPassByValue(Class marshallAs, Object object) {
		if (marshallAs.isPrimitive()) return true;
		Class type = object.getClass();
		if (type==String.class)
			return ((String)object).length() < 2000;
		if (type.isPrimitive() || type.getName().startsWith("java.lang"))
			return true;
		if (type.isArray()) {
			Class ctype = type.getComponentType();
			if (ctype.isPrimitive() || ctype.getName().startsWith("java.lang"))
				return true;
		}
		return false;
	}

	protected Object unmarshall(Object object, Class unmarshallAs) throws MarshallingException {
		if (object==null) return null;
		Object o = object;
		if (object instanceof Serialized) {
			Serialized mo = (Serialized)object;
			switch (mo.varstat) {

				case Serialized.SERVER_RECEIVER:
					// Object is on our side.
					o = remotelyReferencedID.get(mo.id);
					if (o==null) throw new MarshallingException("The object has disconnected from the system.");
					return o;

				case Serialized.SERVER_SENDER:
					// Object is on the other side.
					if (mo.cls.isInterface()) // Client expects this interface.
						o = proxyRemoteObject(new Class[] { mo.cls }, mo.id);
					else // Client expects Object.
						o = proxyRemoteObject(mo.cls.getInterfaces(), mo.id);
					break;

				case Serialized.VAR_SERIALIZED:
					// Object is serialized in data.
//					o = mo.deserialize(remoteclassloader);
					o = mo.deserialize(this,getRemoteClassLoader());
					break;
			}
		}
		Transformation t = getTransformation(unmarshallAs);
		return t==null ? o : t.fromMedium(o);
	}

//	protected Object marshallClass(Class cls) {
//		if (cls.isPrimitive())
//			return cls.getName();
//		else
//			return cls;
//	}

//	protected Class unmarshallClass(Object cls) {
//		// Reassign primitive types
//		if (cls instanceof Class)
//			return (Class)cls;
//		else if (cls.equals("boolean"))
//			return Boolean.TYPE;
//		else if (cls.equals("byte"))
//			return Byte.TYPE;
//		else if (cls.equals("char"))
//			return Character.TYPE;
//		else if (cls.equals("double"))
//			return Double.TYPE;
//		else if (cls.equals("float"))
//			return Float.TYPE;
//		else if (cls.equals("int"))
//			return Integer.TYPE;
//		else if (cls.equals("long"))
//			return Long.TYPE;
//		else if (cls.equals("short"))
//			return Short.TYPE;
//		else if (cls.equals("void"))
//			return Void.TYPE;
//		throw new Error("unknown primitive type encoding: " + cls);
//	}

	public static void initGcTimer() {
		Timer t = new Timer();
		t.schedule(new TimerTask() {
			@Override
			public void run() {
				gc(false);
			}
		},0,1000);
	}

	/**
	 * Performs garbage collection on the local proxies for
	 * remote objects.  Out of scope proxies are cleared on
	 * the remote server.
	 *
	 * @param RuntimeGC if true, force the JVM runtime to
	 * perform garbage collection first.
	 */
	public static synchronized void gc(boolean RuntimeGC) {
		if (RuntimeGC) System.gc();
		boolean more = true;

		LocalProxyReference ref = null;

		while (more) {
			more = false;
			while ((ref = (LocalProxyReference)localproxiesEnqued.poll()) != null) {
//				try {
					if (!ref.server.terminate) {
						try {
							ref.server.send(RCommand.RELEASE(ref.id));
						} catch (Exception ex) {
							logger.log(Level.SEVERE,"gc() "+ex.getMessage(),ex);
						}
					}
					forgetProxyRef(ref);

					// If this was the last reference, close the point.
					if (ref.server.terminateWhenDone && ref.server.localproxiesME.size() == 0)
						ref.server.close();

					if (logger.isLoggable(Level.FINER)) {
						logger.finer(ref.server.name + " -- " + describeClass(ref.type) + " [" + ref.server.localproxiesME.size() + "]");
						LocalProxyReference[] lprs = ref.server.localproxiesME.toArray(new LocalProxyReference[ref.server.localproxiesME.size()]);
						for (LocalProxyReference lpr : lprs)
							logger.finer(describeClass(lpr.type) + "#" + lpr.id.id);
					}

					more = true;
//				} catch (Exception ex) {
//					if (RCore.trace) System.err.println("RCore GC " + ex);
//				}
			}
		}
	}

	private static void forgetProxyRef(LocalProxyReference ref) {
		localproxiesHASH.remove(new Integer(ref.hashcode));
		localproxiesID.remove(ref.id);
		ref.server.localproxiesME.remove(ref);
	}

	public ConnectionListener connectionListener;

	/**
	 * Closes the communications channel.
	 */
	public void close() {
		if (logger.isLoggable(Level.FINER))
			logger.finer("closing communication, sending CMD_GOODBYE");

		terminateWhenDone = false; // prevent recursion with gc()

		if (!terminate) {
			try {
				send(RCommand.GOODBYE());
				gc(true);
			} catch (SocketException ex) {
				// ignore the other socket being closed
			} catch (IOException ex) {
				logger.log(Level.WARNING,"Exception ignored in RConnection shutdown",ex);
			}
		}

		rmicores.remove(this);

		// unreference all objects
		remotelyReferencedID.clear();
		remotelyReferencedOBJ.clear();

		if (connectionListener!=null) connectionListener.connectionClosed((RConnection)this);

		logger.info("communication closed");
	}

	/**
	 * Invokes a method on a remote object.  This method is automatically
	 * called by proxy objects through the InvokationHandler interface.
	 */
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
////		if (getCallObject()==null)
//			return invoke2(proxy,method,args);
//
////		final MethodReturn ret = new MethodReturn();
////		Thread t = new Thread(new Runnable() {
////			public void run() {
////				try {
////					ret.returnvalue = invoke2(proxy,method,args);
////				} catch (Throwable ex) {
////					ret.exception = ex;
////				}
////			}
////		},"invoke "+method.getName());
////		t.start();
////		t.join();
////		if (ret.exception!=null) throw ret.exception;
////		return ret.returnvalue;
//	}
//
//	private Object invoke2(Object proxy, Method method, Object[] args) throws Throwable {

//		if ("load".equals(method.getName())) {
//			if ("de.scheller.platform.BeanManager".equals(method.getDeclaringClass().getName())) {
//				System.err.println(method+" "+Arrays.asList(args));
//			}
//		}

		String methodName = method.getName();
		char mn0 = methodName.charAt(0);
		if (mn0=='h' && (method.getModifiers()&Modifier.NATIVE)!=0 && "hashCode".equals(methodName))
			return System.identityHashCode(proxy);

		boolean isObjectMethod = method.getDeclaringClass()==Object.class;
		if (isObjectMethod) {
			if (mn0=='e' && "equals".equals(methodName)) {
				if (args[0]==null)
					return false;
				if (args[0]==proxy)
					return true;
				if (!Proxy.isProxyClass(args[0].getClass()))
					return false;
			}
			if (mn0=='t' && "toString".equals(methodName)) {
				if (terminate) return "toString()-remote-closed";
			}
		}

		if (terminate) throw new RemoteException("Remote connection closed.");

		LocalProxyReference ref = getProxyRef(proxy);
		if (ref==null) throw new Error("Invocation on non remote proxy.");
		ObjectID id = ref.id;

		if (isObjectMethod && !ref.callRemote) {
			// the checks above make sure that args[0] is a proxy too
			if (mn0=='e' && "equals".equals(methodName)) {
				LocalProxyReference otherRef = getProxyRef(args[0]);
				if (otherRef==null)
					return false;
				ObjectID otherId = otherRef.id;
				return id.equals(otherId);
			}
			if (mn0=='t' && "toString".equals(methodName)) {
				return ref.getToString();
			}
		}

		Class argsc[] = method.getParameterTypes();
//		Class[] argscv[] = null;
		Serializable[] argsv = null;

		if (args != null) {
			argsv = new Serializable[args.length];
//			argscv = new Class[args.length];
			for (int i = 0; i < args.length; i++) {
//				argscv[i] = marshallClass(argsc[i]);
				argsv[i] = marshall(args[i],argsc[i],null);
			}
		}

		StringBuffer sb = null;
		if (logger.isLoggable(Level.FINER)) {
			sb = new StringBuffer();
			sb.append("Invoked ");
			sb.append(method.getDeclaringClass().getName());
			sb.append('#');
			sb.append(id.id);
			sb.append("::");
			sb.append(methodName);
			sb.append("( ");
			if (argsc!=null) {
				for (int i=0; i<argsc.length; i++) {
					sb.append(argsc[i].getName());
					sb.append('/');
					Object v = argsv[i];
					if (v!=null) {
						if (v.getClass() == Serialized.class) {
							Serialized mo = (Serialized)argsv[i];
							if (mo.varstat == Serialized.VAR_SERIALIZED)
								sb.append("byval(serialized)");
							else if (mo.varstat == Serialized.SERVER_RECEIVER)
								sb.append("ref-recv");
							else if (mo.varstat == Serialized.SERVER_SENDER)
								sb.append("byref");
						} else {
							sb.append("byval(direct)");
						}
					} else {
						sb.append("byval(null)");
					}
					if (i!=argsc.length-1) sb.append(", ");
				}
			}
			sb.append(" )");
		}

		int stack = 0;
		Integer istack = getCallStackDepth();
		if (istack != null) stack = istack.intValue();
		if (stack >= MAX_CALL_STACK_SIZE) throw new RemoteException("Remote call stack overflow.");

		long t = System.currentTimeMillis();
		RCommand j = RCommand.INVOKE(id,method,argsc,argsv,stack);
		Class returnType = method.getReturnType();
		if (returnType==ReturnImmediately.class) {
			send(j);
			return null;
		}
		MethodReturn ret = new MethodReturn(j);
		send(j);
		ret.hold();
		t = System.currentTimeMillis() - t;

		if (sb!=null) {
			sb.insert(0,"ms, ");
			sb.insert(0,t);
			sb.insert(0,"TIME rmi"+j.transaction+" rmi/invoke | ");
			sb.append(" => result ");
			if (ret.exception!=null) {
				sb.append(ret.exception);
			} else if (ret.returnvalue==null) {
				sb.append("null");
			} else if (isRemote(ret.returnvalue)) {
				sb.append(ret.returnvalue.getClass().getInterfaces()[0].getName());
				sb.append('#');
				sb.append(getProxyRef(ret.returnvalue).id.id);
				sb.append("/byref");
			} else {
				sb.append(ret.returnvalue.getClass().getName());
				sb.append("/byval");
			}
			sb.append('(');
			sb.append(returnType);
			sb.append(")");
			logger.finer(sb.toString());
		}

		// return a value _or_ throw an exception
		ret.except();
		return ret.returnvalue;
	}

	/**
	 * Used to hold execution until a remote method invokation returns.
	 */
	class MethodReturn {
		ObjectID invkTarget;
		CommandID transaction;
		RCommand cmd;

		Object returnvalue;
		Throwable exception;

		boolean wait = true;

		MethodReturn() {}

		MethodReturn(RCommand cmd) {
			this.invkTarget = cmd.obj;
			this.transaction = cmd.transaction;
			this.cmd = cmd;
			openTransactions.put(transaction,this);
		}

		public synchronized void hold() throws RemoteException {
			if (wait) returnvalue = null;
			if (wait) exception = null;

			try {
				if (wait) {
					long t = timeout;
					String timeoutOverride = System.getProperty("rmi.timeout");
					if (timeoutOverride!=null)
						t = Long.parseLong(timeoutOverride);
					wait(t);
				}
			} catch (InterruptedException ex) {}

			openTransactions.remove(transaction);

			if (exception==null && returnvalue==null)
				throw new RemoteException("Timeout on " + cmd);

			if (returnvalue == Void.TYPE) returnvalue = null;
		}

		public synchronized void methodReturn(Object o) {
			returnvalue = o;
			if (returnvalue == null) returnvalue = Void.TYPE;
			notifyAll();
			wait = false;
		}
		public synchronized void methodException(Throwable t) {
			exception = t;
			notifyAll();
			wait = false;
		}

		public void except() throws Throwable {
			if (exception == null) return;

			if (exception instanceof RemoteException) {
				exception.printStackTrace();
				// lose the remote stack trace
				throw (RemoteException)exception.fillInStackTrace();

			} else if (exception instanceof Error) {
				// chain remote stack trace
				throw (Error)new Error("Remote error", exception).fillInStackTrace();

			} else {
//				// chain remote stack trace
//				wipermistack(exception);
//				Exception mid = new Exception("exception on " + getRemoteName(), exception);
//				mid.setStackTrace(new StackTraceElement[] { });
//				RemoteException r = (RemoteException)new RemoteException(exception.toString(), mid).fillInStackTrace();
//				wipermistack(r);
//				throw r;
				StackTraceElement[] remoteStacktrace = exception.getStackTrace();
				StackTraceElement[] localStacktrace = new Exception().getStackTrace();
				StackTraceElement[] fullStacktrace =
					new StackTraceElement[remoteStacktrace.length + localStacktrace.length];
				System.arraycopy(remoteStacktrace,0,fullStacktrace,0,remoteStacktrace.length);
				System.arraycopy(localStacktrace,0,fullStacktrace,remoteStacktrace.length,localStacktrace.length);
				exception.setStackTrace(fullStacktrace);
				throw exception;
			}
		}

		@Override
		public String toString() {
			if (exception != null)
				return transaction + ":" + exception.getClass();
			else if (returnvalue != null)
				return transaction + ":" + returnvalue.getClass();
			else
				return transaction + ":not yet returned";
		}

//		private void wipermistack(Throwable e) {
//			int c = 0, i;
//			StackTraceElement[] st = e.getStackTrace();
//			for (i = 0; i < st.length; i++) {
//				if (!st[i].getClassName().startsWith(RCore.class.getPackage().getName())) c++;
//			}
//
//			StackTraceElement[] st2 = new StackTraceElement[c];
//			c = 0;
//			for (i = 0; i < st.length; i++) {
//				if (!st[i].getClassName().startsWith(RCore.class.getPackage().getName()))
//					st2[c++] = st[i];
//			}
//
//			e.setStackTrace(st2);
//		}
	}

	/**
	 * Invokes a method on the REMOTE side of a connection.
	 */
	protected void localinvoke(Object o, String method, Object[] args, Class[] methc, CommandID transaction, int callstack) throws IOException {
		if (callstack >= MAX_CALL_STACK_SIZE) throw new IOException("Remote call stack overflow.");

		threadcallstack.set(new Integer(callstack+1));
		threadcallobject.set(o);
		fireBeforeInvocation();

		Object[] argsv = null;
		Class[] argsc = null;
		try {
			if (args!=null) {
				argsv = new Object[args.length];
				argsc = new Class[methc.length];
				for (int i=0; i<args.length; i++) {
					argsc[i] = methc[i];//unmarshallClass(methc[i]);
					// Unmarshall Objects
					argsv[i] = unmarshall(args[i],argsc[i]);
				}
			}

			// Execute Method
			Method m = o.getClass().getMethod(method,argsc);
			Thread.currentThread().setContextClassLoader(o.getClass().getClassLoader());

			if (m.getReturnType() != ReturnImmediately.class) {
				Object ret = m.invoke(o,argsv);
				send(RCommand.RETURN(transaction,
						marshall(ret,m.getReturnType(),argsv),m.getReturnType()));
			} else {
				try {
					m.invoke(o,argsv);
				} catch (Exception e) {
					// Ignore all exceptions.
				}
			}
		} catch (InvocationTargetException ex) {
			logger.log(Level.SEVERE,method+" "+Arrays.asList(argsv),ex.getTargetException());
			send(RCommand.EXCEPTION(transaction,new SubstituteException(ex.getTargetException())));
		} catch (Exception ex) {
			logger.log(Level.SEVERE,method+" "+Arrays.asList(argsv),ex);
			send(RCommand.EXCEPTION(transaction,new SubstituteException(ex)));
		} catch (NoClassDefFoundError ex) {
			if (RConnection.ignoreNoClassDefFound) {
				logger.log(Level.SEVERE,ex.getMessage(),ex);
				send(RCommand.EXCEPTION(transaction,new SubstituteException(ex)));
			}
		} catch (Throwable ex) {
			logger.log(Level.SEVERE,method+" "+Arrays.asList(argsv),ex);
			send(RCommand.EXCEPTION(transaction,new SubstituteException(ex)));
		} finally {
			fireAfterInvocation();
			threadcallobject.set(null);
			threadcallstack.set(null);
		}
	}

	protected static String describeClass(Object o) {
		Class c;

		if (o.getClass() == Class.class)
			c = (Class)o;
		else
			c = o.getClass();

		if (c.isInterface() || c.getInterfaces().length == 0) return c.getName();

		String s = "";
		for (int i = 0; i < c.getInterfaces().length; i++) {
			s += c.getInterfaces()[i];
			if (i < c.getInterfaces().length - 1) s += "/";
		}
		return s;
	}


	/**
	 * Exports an object as a service.
	 */
	public void export(String name, Object object) {
		services.put(name, object);
	}
	/**
	 * Removes the service of the specified name.
	 */
	public void unexport(String name) {
		services.remove(name);
	}
	/**
	 * Disconnects an object from remote connections.
	 */
	public void disconnect(Object o) {
		ObjectID id = (ObjectID)remotelyReferencedOBJ.get(o);
		if (id == null) return;
		remotelyReferencedOBJ.remove(o);
		remotelyReferencedID.remove(id);
	}

	///////////////////////////////
	/*     ABSTRACT METHODS      */
	///////////////////////////////

	protected abstract void send(RCommand j) throws IOException;

	////////////////////////////////////////////////////////////////////////////
	// transformations

	protected Map<Class,Transformation> transformations = new ConcurrentHashMap();
	protected static final Transformation nullTransformation =
			new Transformation(Object.class,Object.class,false);

	public void addTransformation(Class parameter, Class medium, boolean asProxy, Class wrapper, Class unwrapper) throws NoSuchMethodException {
		Transformation t = new Transformation(parameter,medium,asProxy,wrapper,unwrapper);
		transformations.put(t.objectType,t);
	}
	public void addTransformation(Transformation t) {
		transformations.put(t.objectType,t);
	}
	protected Transformation getTransformation(Class parameter) {
		if (transformations.containsKey(parameter)) {
			Transformation t = transformations.get(parameter);
			if (t==nullTransformation) t = null;
			return t;
		}
		if (logger.isLoggable(Level.FINE))
			logger.fine("Lookup transformation for: "+parameter+" "+transformations.size());
		for (Transformation t : new ArrayList<Transformation>(transformations.values())) {
			if (t==nullTransformation) continue;
			if (t.objectType.isAssignableFrom(parameter) && t.canHandle(parameter)) {
				transformations.put(parameter,t);
				if (logger.isLoggable(Level.FINE))
					logger.fine("Found transformation for: "+parameter);
				return t;
			}
		}
		transformations.put(parameter,nullTransformation);
		return null;
	}

	////////////////////////////////////////////////////////////////////////////
	// IObjectResolver for objects going to the network

	protected List<IObjectResolver> outgoingResolver = new ArrayList();

	public void addOutgoingObjectResolver(IObjectResolver r) {
		outgoingResolver.add(r);
	}

	////////////////////////////////////////////////////////////////////////////
	// IObjectResolver for objects coming from the network

	protected List<IObjectResolver> incomingResolver = new ArrayList();

	public void addIncomingObjectResolver(IObjectResolver r) {
		incomingResolver.add(r);
	}

//	protected ObjectOutputStream getSerializer(Class c, OutputStream s)
//	throws IOException {
//		Class o = (Class)outgoingResolver.get(c);
//		if (outgoingResolver.containsKey(c)) return buildOos(o,s);
//
//		if (logger.isLoggable(Level.FINE))
//			logger.fine("Lookup serializer for: "+c+" "+outgoingResolver.size());
//		for (Iterator it = outgoingResolver.entrySet().iterator(); it.hasNext();) {
//			Map.Entry e = (Map.Entry)it.next();
//			Class oc = (Class)e.getKey();
//			o = (Class)e.getValue();
//			if (o==null) continue;
//			if (oc.isAssignableFrom(c)) {
//				outgoingResolver.put(c,o);
//				if (logger.isLoggable(Level.FINE))
//					logger.fine("Found serializer for: "+c);
//				return buildOos(o,s);
//			}
//		}
//		outgoingResolver.put(c,null);
//		return buildOos(null,s);
//	}
//
//	private ObjectOutputStream buildOos(Class c, OutputStream s)
//	throws IOException {
//		if (c==null) return new ObjectOutputStream(s);
//		try {
//			Constructor cc = c.getConstructor(new Class[] { OutputStream.class });
//			return (ObjectOutputStream)cc.newInstance(new Object[] { s });
//		} catch (Exception ex) {
//			System.err.println("Failed to build custom ObjectOutputStream: "+ex.getMessage());
//			return new ObjectOutputStream(s);
//		}
//	}
//
//	////////////////////////////////////////////////////////////////////////////
//	// object resolver
//
//	protected HashMap deserializer = new HashMap();
//
//	public IObjectResolver getObjectResolver(Class c) {
//		return (IObjectResolver)deserializer.get(c);
//	}
//
//	public void addIncomingObjectResolver(Class c, IObjectResolver r) {
//		deserializer.put(c,r);
//	}

	static class ObjectID implements Serializable {
		public static final long serialVersionUID = 0x4333504f2d4f424al; // C3PO-OBJ

		private static long idcounter = 0;
		private static synchronized long next() { return idcounter++; }

		private final long id;
		private final String server;

		ObjectID(String server) {
			this.id = next();
			this.server = server;
		}

		@Override public String toString() { return id+"@"+server; }
		@Override public int hashCode() { return (int)(id & 0xFFFF); }
		@Override public boolean equals(Object obj) {
			if (obj==this) return true;
			if (obj instanceof ObjectID==false) return false;
			ObjectID other = (ObjectID)obj;
			return id==other.id && server.equals(other.server);
		}
	}

	/**
	 * Holds on to proxy objects as a weak reference, so that the RMI connection
	 * can be notified when the object has been released by the VM,
	 * so it can be unreferenced remotely.
	 */
	static class LocalProxyReference extends WeakReference {
		RCore server;
		ObjectID id;
		Class type;
		int hashcode;
		String toString;
		boolean callRemote;

		LocalProxyReference(Object o, RCore server, ObjectID id, Class type, ReferenceQueue queue) {
			super(o,queue);
			this.server = server;
			this.id = id;
			this.type = type;
			this.hashcode = System.identityHashCode(o);
		}

		String getToString() {
			if (toString==null) {
				try {
					callRemote = true;
					Object o = get();
					toString = o.toString();
				} finally {
					callRemote = false;
				}
			}
			return toString;
		}

		void clearToString() {
			toString = null;
		}
	}
}
