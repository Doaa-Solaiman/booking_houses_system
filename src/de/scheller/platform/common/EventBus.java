/* /////////////////////////////////////////////////////////////////////////////
 *
 * Created       :  18.07.2011 11:40:48 by kunze
 * Project       :  de.scheller-blackboard
 * CVS Ident     :  $Revision$, $Date$
 * Last Commiter :  $Author$
 *
 * Copyright (c) 2011 Scheller Systemtechnik GmbH
 *                    Poeler Strasse 85a, 23970 Wismar, Germany
 *
 *//////////////////////////////////////////////////////////////////////////////
package de.scheller.platform.common;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.WeakHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author kandzia, kunze
 */
public class EventBus<L,E>
{
	private static final Logger logger = Logger.getLogger(EventBus.class.getName());

	private final boolean useExecutor;
	private final Map<Class<E>,Collection<Class<L>>> registration = new HashMap();

	private static final int defaultRunlevel = 8;

	public EventBus() { this(false); }
	public EventBus(boolean useExecutor) {
		this.useExecutor = useExecutor;
	}

	public void register(Class<L> listener, Class<E> event) {
		if (listener==null) {
			logger.severe("listener class must not be null");
			return;
		}
		if (event==null) {
			logger.severe("event class must not be null");
			return;
		}
		if (!listener.isInterface()) {
			logger.severe("listener class must be an interface");
			return;
		}

		Collection<Class<L>> ls = registration.get(event);
		if (ls==null) registration.put(event,ls = new LinkedHashSet());
		ls.add(listener);
	}

	private final Timer forgetTimer = new Timer();
	{
		forgetTimer.schedule(new TimerTask() {
			@Override
			public void run() {
				mayRemoveContexts();
			}
		},5000,5000);
	}
	private final ReferenceQueue toForget = new ReferenceQueue();
	private void mayRemoveContexts() {
		WeakReference<Object> stale = null;
		while ((stale = (WeakReference<Object>)toForget.poll())!=null)
			removeByContext(stale);
	}
	private Object wrap(L listener) {
		return new ListenerWrapper(listener);
	}
	private L unwrap(Object listener) {
		if (listener instanceof ListenerWrapper)
			return ((ListenerWrapper<L>)listener).ref.get();
		return (L)listener;
	}
	private Class getClass(Object listener) {
		if (listener instanceof ListenerWrapper)
			return ((ListenerWrapper<L>)listener).listenerClass;
		return listener.getClass();
	}
	private static class ListenerWrapper<L> {
		private final WeakReference<L> ref;
		private final Class listenerClass;
		private ListenerWrapper(L listener) {
			ref = new WeakReference<>(listener);
			listenerClass = listener.getClass();
		}
	}

	private final Map<Object,WeakReference<Object>> refByContext = new WeakHashMap();
	private final Map<Object,Object> refByListener = new WeakHashMap();
	private final Map<WeakReference<Object>,Collection<Object>> listenersByContext = new IdentityHashMap();
	private final Map<Object,WeakReference<Object>> contextByListeners = new HashMap();
	private final Map<Class<L>,Collection<Object>> listenersByClass = new HashMap();
	private final Map<Class<E>,Map<Class<L>,Method>> methodsByListenerByEvent = new HashMap();
	private final Map<Object,Object[]> criteriaByListener = Collections.synchronizedMap(new HashMap());
	private final Map<Object,Integer> runlevelByListener = Collections.synchronizedMap(new HashMap());
	private final Map<Class<L>,Boolean> runlevelUsedByClass = new HashMap();
	private final Map<Class<E>,Object> interceptByEventType = new HashMap();

	public void add(L listener) {
		add(null,listener);
	}

	public void add(Object context, L listener, Object... criteria) {
		add(context,null,listener,criteria);
	}

	public void add(Object context, Integer runlevel, L listener, Object... criteria) {
		if (listener==null) {
			logger.severe("listener must not be null");
			return;
		}
		if (criteria==null) {
			logger.severe("criteria must not be null");
			return;
		}

		Class c = listener.getClass();
		Collection<Class> interfaces = new LinkedHashSet();
		getInterfaces(c,interfaces);
		boolean b = findMethods(c,interfaces);
		if (!b && listener instanceof EventIntercept==false) {
			logger.fine("no listener methods found for listener class "+c);
			return;
		}

		Object l = listener;
		WeakReference<Object> ref = null;
		if (context!=null) {
			ref = refByContext.get(context);
			if (ref==null || ref.get()==null)
				refByContext.put(context,ref = new WeakReference<>(context,toForget));
			if (context==listener)
				refByListener.put(listener,l = wrap(listener));
		}

		Collection listeners = listenersByContext.get(ref);
		if (listeners==null) listenersByContext.put(ref,listeners = new HashSet());
		listeners.add(l);
		contextByListeners.put(l,ref);

		for (Class i : interfaces) {
			Collection ls = listenersByClass.get(i);
			if (ls==null) listenersByClass.put(i,
					ls = Collections.synchronizedCollection(new LinkedHashSet()));
			ls.add(l);
		}
		if (listener instanceof EventIntercept) {
			EventIntercept intercept = (EventIntercept)listener;
			interceptByEventType.put(intercept.getEventClass(),l);
		}

		if (criteria.length>0)
			criteriaByListener.put(l,criteria);
		if (runlevel!=null) {
			runlevelByListener.put(l,runlevel);
			for (Class i : interfaces)
				runlevelUsedByClass.put(i,true);
		}
	}

	private boolean findMethods(Class<L> c, Collection<Class> interfaces) {
		boolean b = false;

		Method[] methods = c.getMethods();
		for (Method m : methods) {
			Class[] ts = m.getParameterTypes();
			if (ts.length!=1) continue;

			Class type = ts[0];
			if (Collection.class.isAssignableFrom(type)) continue; // could not support collections
			Class t = type.isArray() ? type.getComponentType() : type;

			Class cc = m.getDeclaringClass();
			if (!cc.isInterface()) {
				Class i = null;
				for (Class ii : interfaces) {
					try {
						Method mm = ii.getMethod(m.getName(),type);
						if (!registration.isEmpty()) {
							Collection<Class<L>> ls = registration.get(t);
							if (ls==null || !ls.contains(mm.getDeclaringClass()))
								continue;
						}
						if (i==null || mm.getDeclaringClass().isAssignableFrom(i)) {
							m = mm;
							i = mm.getDeclaringClass();
						}
//						break;
					} catch (Exception ex) {}
				}
				if (i==null) continue;

				cc = i;
			} else {
				if (!registration.isEmpty()) {
					Collection<Class<L>> ls = registration.get(t);
					if (ls==null || !ls.contains(cc))
						continue;
				}
			}

			m.setAccessible(true);

			Map<Class<L>,Method> mm = methodsByListenerByEvent.get(t);
			if (mm==null) methodsByListenerByEvent.put(t,mm = new HashMap());
			mm.put(cc,m);
			b = true;
		}

		return b;
	}

	private void getInterfaces(Class c, Collection<Class> interfaces) {
		if (c==null) return;

		if (c.isInterface())
			interfaces.add(c);
		else getInterfaces(c.getSuperclass(),interfaces);

		Class[] is = c.getInterfaces();
		for (Class i : is)
			getInterfaces(i,interfaces);
	}

	public void removeByContext(Object context) {
		if (refByContext.containsKey(context))
			context = refByContext.remove(context);
		Collection listeners = listenersByContext.remove(context);
		if (listeners==null)
			return;
		for (Object l : listeners)
			remove(l);
	}

	public void remove(Object listener) {
		if (listener==null) {
			logger.severe("listener must not be null");
			return;
		}
		if (refByListener.containsKey(listener))
			listener = refByListener.remove(listener);
		criteriaByListener.remove(listener);
		runlevelByListener.remove(listener);
		matchers.remove(listener);
		Object context = contextByListeners.remove(listener);
		matchers.remove(context);

		Collection<Class> interfaces = new LinkedHashSet();
		getInterfaces(getClass(listener),interfaces);
		for (Class i : interfaces) {
			Collection listeners = listenersByClass.get(i);
			if (listeners==null) continue;
			if (listeners.remove(listener))
				if (listeners.isEmpty())
					listenersByClass.remove(i);
		}
	}

	public void fire(E... events) {
		fire(useExecutor,events!=null ? Arrays.asList(events) : null,true);
	}
	public void queue(E... events) {
		fire(true,events!=null ? Arrays.asList(events) : null,true);
	}
	public void fire(Collection<E> events) {
		fire(useExecutor,events,true);
	}
	public void queue(Collection<E> events) {
		fire(true,events,true);
	}

	private void fire(boolean queue, Collection<E> events, boolean allowIntercept) {
		if (events==null) {
			logger.severe("events to fire must not be null");
			return;
		}
		if (events.isEmpty()) {
			logger.severe("no events to fire");
			return;
		}

		Map<Class,List> eventsByClass = new LinkedHashMap();
		for (E e : events)
			addRecursive(e.getClass(),e,eventsByClass);

		for (Map.Entry<Class,List> e : eventsByClass.entrySet()) {
			Map<Class<L>,Method> ms = methodsByListenerByEvent.get(e.getKey());
			if (ms==null) continue;
			if (allowIntercept) {
				Object ref = interceptByEventType.get(e.getKey());
				EventIntercept<E> intercept = (EventIntercept<E>)unwrap(ref);
				if (intercept!=null) {
					intercept.intercept(e.getValue(),fireWithoutIntercept);
					continue;
				}
			}

			for (Map.Entry<Class<L>,Method> ee : ms.entrySet()) {
				Class<L> listenerClass = ee.getKey();
				Collection ls = listenersByClass.get(listenerClass);
				if (ls==null) continue;
				if (runlevelUsedByClass.containsKey(listenerClass)) {
					List l = new ArrayList(ls);
					Collections.sort(l,new Comparator<L>() {
						public int compare(L l1, L l2) {
							Integer level1 = runlevelByListener.get(l1);
							Integer level2 = runlevelByListener.get(l2);
							int i1 = level1!=null ? level1 : defaultRunlevel;
							int i2 = level2!=null ? level2 : defaultRunlevel;
							return i1-i2;
						}
					});
					ls = l;
				}

				if (queue) {
					for (Object l : ls.toArray())
						executor().execute(new Fire(l,e.getValue(),ee.getValue()));
				} else {
					for (Object l : ls.toArray())
						fire(l,e.getValue(),ee.getValue());
				}
			}
		}
		mayRemoveContexts();
	}

	private void addRecursive(Class c, E event, Map<Class,List> map) {
		if (c==null) return;

		List events = map.get(c);
		if (events==null) map.put(c,events = new ArrayList());
		events.add(event);

		if (!c.isInterface())
			addRecursive(c.getSuperclass(),event,map);

		Class[] is = c.getInterfaces();
		for (Class i : is)
			addRecursive(i,event,map);
	}

	private Executor executor;
	private Executor executor() {
		if (executor==null)
			executor = Executors.newSingleThreadExecutor(); // keep event order
		return executor;
	}

	private class Fire implements Runnable
	{
		private final Object listener;
		private final List<E> events;
		private final Method method;

		private Fire(Object listener, List<E> events, Method method) {
			this.listener = listener;
			this.events = events;
			this.method = method;
		}

		public void run() {
			fire(listener,events,method);
		}
	}

	private void fire(Object listener, List<E> events, Method method) {
		L l = unwrap(listener);
		if (l==null)
			return;

		Object[] criteria = criteriaByListener.get(listener);
		boolean useGlobalMatchers = criteria!=null && !globalMatchers.isEmpty(); // global event matchers
		Map<EventMatcher,Long> lm = matchers.get(listener);
		boolean useListenerMatchers = lm!=null && !lm.isEmpty(); // listener-specific event matchers
		WeakReference<Object> context = contextByListeners.get(listener);
		Map<EventMatcher,Long> cm = matchers.get(context);
		boolean useContextMatchers = cm!=null && !cm.isEmpty(); // context-specific event matchers
		Object ctx = context!=null ? context.get() : null;

		List<EventMatcher> matcherList = null;
		if (useGlobalMatchers || useListenerMatchers || useContextMatchers) {
			matcherList = new LinkedList();
			if (useGlobalMatchers) {
				matcherList.addAll(globalMatchers);
			}
			if (useListenerMatchers) {
				removeExpired(lm);
				matcherList.addAll(lm.keySet());
			}
			if (useContextMatchers) {
				removeExpired(cm);
				matcherList.addAll(cm.keySet());
			}
		}

		events = new ArrayList(events);
		for (Iterator it = events.iterator(); it.hasNext();) {
			Object e = it.next();
			boolean match = true;
//			if (match && e instanceof ItemMatcher)
//				match = ((ItemMatcher)e).match(ctx!=null ? new Object[] { ctx } : null);
//			if (match && e instanceof ItemMatcher)
//				match = ((ItemMatcher)e).match(criteria);
			if (match && matcherList!=null) {
				for (EventMatcher m : matcherList) {
					if (!m.getEventClass().isAssignableFrom(e.getClass()))
						continue;
					if (!m.matches(e,criteria)) {
						match = false;
						break;
					}
				}
			}
			if (!match)
				it.remove();
		}
		if (events.isEmpty()) return;

		Class t = method.getParameterTypes()[0];
		if (t.isArray()) {
			t = t.getComponentType();

			Object array = Array.newInstance(t,events.size());
			for (int i=0; i<events.size(); i++)
				Array.set(array,i,events.get(i));
			try {
				invokeListener(method,l,array);
			} catch (Exception ex) {
				String msg = "Method " + toStringSimple(method) +
						" on " + toStringSimple(l) +
						" with event(s) " + Arrays.toString((Object[])array)+" failed." +
						" Exception catched in EventBus: "+ex.getMessage();
				logger.log(Level.SEVERE,msg,ex);
			}
		} else {
			for (Object e : events) {
				try {
					invokeListener(method,l,e);
				} catch (Exception ex) {
					String msg = "Method " + toStringSimple(method) +
							" on " + toStringSimple(l) +
							" with event " + e + " failed." +
							" Exception catched in EventBus: "+ex.getMessage();
					logger.log(Level.SEVERE,msg,ex);
				}
			}
		}
	}

	protected void invokeListener(Method method, L listener, Object event) throws Exception {
		method.invoke(listener,event);
	}

	private final List<EventMatcher> globalMatchers = new ArrayList();

	public void addEventMatcher(EventMatcher m) {
		globalMatchers.add(m);
	}

	public void removeEventMatcher(EventMatcher m) {
		globalMatchers.remove(m);
	}

	private final Map<Object,Map<EventMatcher,Long>> matchers = Collections.synchronizedMap(new IdentityHashMap());

	public void addEventMatcher(Object contextOrListener, EventMatcher matcher) {
		addEventMatcher(contextOrListener,matcher,0);
	}

	public void addEventMatcher(Object contextOrListener, EventMatcher matcher, int timeout) {
		if (refByContext.containsKey(contextOrListener))
			contextOrListener = refByContext.get(contextOrListener);
		Map<EventMatcher,Long> mm = matchers.get(contextOrListener);
		if (mm==null) {
			mm = Collections.synchronizedMap(new HashMap());
			matchers.put(contextOrListener,mm);
		}
		mm.put(matcher,timeout>0 ? System.currentTimeMillis()+timeout : null);
	}

	private void removeExpired(Map<EventMatcher,Long> mm) {
		Set<Entry<EventMatcher,Long>> entries = mm.entrySet();
		synchronized (mm) {
			Iterator<Map.Entry<EventMatcher,Long>> it = entries.iterator();
			while (it.hasNext()) {
				long now = System.currentTimeMillis();
				Map.Entry<EventMatcher,Long> e = it.next();
				if (e.getValue()!=null && e.getValue()<now)
					it.remove();
			}
		}
	}

	public static interface EventMatcher<E>
	{
		Class<E> getEventClass();
		boolean matches(E event, Object... criteria);
	}

	private final FireWithoutIntercept fireWithoutIntercept = new FireWithoutIntercept();
	private class FireWithoutIntercept implements EventIntercept.Forward<E> {
		public void fire(E... events) {
			EventBus.this.fire(useExecutor,events!=null ? Arrays.asList(events) : null,false);
		}
		public void fire(Collection<E> events) {
			EventBus.this.fire(useExecutor,events,false);
		}
	}

	public static interface EventIntercept<E>
	{
		Class<E> getEventClass();
		void intercept(List<E> events, Forward forward);

		interface Forward<E> {
			void fire(E... events);
			void fire(Collection<E> events);
		}
	}

	public static String toStringSimple(Method m) {
		StringBuilder sb = new StringBuilder();
		try {
			sb.append(m.getDeclaringClass().getSimpleName());
			sb.append('.').append(m.getName()).append('(');
			Class<?>[] pts = m.getParameterTypes();
			for (Class pt : pts)
				sb.append(pt.getSimpleName()).append(',');
			if (pts.length>0) sb.setLength(sb.length()-1); // cut ','
			sb.append(')');
		} catch (Throwable ex) {
			sb.append(m.toString());
		}
		return sb.toString();
	}

	public static String toStringSimple(Object o) {
		if (o==null)
			return "null";
		String toString = o.toString();
		String hexHashcode = Integer.toHexString(o.hashCode());
		String java = o.getClass().getName() + "@" + hexHashcode;
		if (toString==null || toString.equals(java))
			return o.getClass().getSimpleName() + "@" + hexHashcode;
		return toString;
	}
}
