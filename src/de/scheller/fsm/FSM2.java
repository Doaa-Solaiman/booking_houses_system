/* /////////////////////////////////////////////////////////////////////////////
 *
 * Created       :  12.04.2008 13:55:33 by kandzia
 * Project       :  de.scheller-sfc
 * CVS Ident     :  $Revision$, $Date$
 * Last Commiter :  $Author$
 *
 * Copyright (c) 2008 Scheller Systemtechnik GmbH
 *                    Poeler Strasse 85a, 23970 Wismar, Germany
 *
 *//////////////////////////////////////////////////////////////////////////////
package de.scheller.fsm;

import java.awt.Component;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Resource;
import javax.swing.event.SwingPropertyChangeSupport;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;

import de.scheller.common.HasContext;
import de.scheller.fsm.FSM.IContext;
import de.scheller.fsm.FSM.Inherit;
import de.scheller.gui.Gui;
import de.scheller.gui.editor.ColorProvider;
import de.scheller.util.PostOrderIterator;
import de.scheller.util.ReflectHelper;
import de.scheller.util.TreeNodeChildrenIteratorOp;

/**
 * FSM2
 *
 * @author kandzia
 */
public class FSM2 implements FSM.IFlyingSpaghettiMonster
{
	public static DefaultTreeModel tm;
	public static ContextNode root;
	private final WeakHashMap known = new WeakHashMap();
	public static Runnable debug;
	private final Map<Object,WeakReference> noodles = Collections.synchronizedMap(new WeakHashMap());

	private final Object PROVIDER = new Object() {
		@Override
		public String toString() {
			return "FsmProvider";
		}
	};

	public FSM2() {
		ReflectHelper.write(FSM.class,"fsm",this);
		root = new ContextNode(this);
	}

	private final Map<Class,TargetObjectHandler> toh = new IdentityHashMap();
	private final Map<Class,TargetObjectHandler> tohCache = new ConcurrentHashMap();
	private static final TargetObjectHandler tohNull = new TargetObjectHandler() {
		public Class getTargetClass() { return null; }
		public Object getParent(Object target) { return null; }
		public boolean hasProperties(Object target) { return false; }
		public boolean hasProperty(Object target, Object key) { return false; }
		public Object getProperty(Object target, Object key) { return null; }
	};

	public void addTargetObjectHandler(TargetObjectHandler h) {
		toh.put(h.getTargetClass(),h);
		tohCache.clear();
	}

	public TargetObjectHandler getTargetObjectHandler(Object target) {
		Class c = target.getClass();

		// cache lookup
		TargetObjectHandler h = tohCache.get(c);
		if (h!=null)
			return h!=tohNull ? h : null;

		// cache miss
		Set<Class> classes =
			ReflectHelper.getClasses(c,ReflectHelper.ClassStrategy.all,Object.class);
		for (Class cc : classes)
			if ((h=toh.get(cc))!=null) break;
		tohCache.put(c,h!=null ? h : tohNull);
		return h;
	}

	private Object getParent(Object target) {
		Object context = noodles.get(target);
		if (context!=null) context = ((WeakReference)context).get();
		if (context==target) {
			noodles.remove(target);
			System.err.println("FSM: context of target is target, " +
				"context entry removed ("+target+")");
			return null;
		}
		if (context!=null)
			return context;

		if (target instanceof HasContext) {
			context = ((HasContext)target).getContext();
			if (context!=null)
				return context;
		}

		TargetObjectHandler h = getTargetObjectHandler(target);
		Object parent = h!=null ? h.getParent(target) : null;
		if (parent==target) {
			System.err.println("FSM: parent of target is target ("+target+")");
			return null;
		}
		return parent;
	}

	public boolean isInContext(Object target, Object context) {
		if (context instanceof String) {
			Object parent = getParent(target);
			for(; parent!=null; parent=getParent(parent)) {
				if (parent.getClass().getName().equals(context))
					return true;
			}
			return false;
		}
		if (target==context) return true;
		Object o = getParent(target);
		if (context==null) return o==null;
		for (; o!=null; o=getParent(o))
			if (o==context) return true;
		return false;
	}

//	public boolean isInContext(Object target, Object context) {
//		kickStaleNodes();
//		// just a check
//		ContextNode tn = rememberOrGetToKnow(target);
//		ContextNode cn = rememberOrGetToKnow(context);
//		return false;
//	}

	public void noodle(Object it, Object context) {
		kickStaleNodes();
		if (it==null) return;
		if (context==null) {
			noodles.remove(it);
			ContextNode target = remember(it);
			if (target==null) return;
			ContextNode parent = target.parent;
			parent.children.remove(target);
			PostOrderIterator i = new PostOrderIterator(
					target,new TreeNodeChildrenIteratorOp());
			while (i.hasNext()) {
				ContextNode n = (ContextNode)i.next();
				n.children = null;
				n.contextData = null;
				n.localData = null;
				n.parent = null;
				known.remove(n.get());
			}
			kickStaleNodes();
			if (tm!=null)
				tm.nodeStructureChanged(parent);
			return;
		} else {
			for (Object p = getParent(context); p!=null; p=getParent(p))
				if (p.equals(it)) return;
			noodles.put(it,new WeakReference(context));
			ContextNode target = rememberOrGetToKnow(it);
			if (target==null) return;
			ContextNode cn = context!=null ? rememberOrGetToKnow(context) : null;
//			target.removeChildren(); // invalidate subtree under target node
//			ContextNode oldParent = (ContextNode)target.getParent();
			target.setParent(cn); // reparent target node to context node
			target.invalidate();
//			target.injection();
//			if (oldParent!=null) oldParent.removeFromTreeIfNotUseful();
		}
	}

	public void noodle(Object context, Class service, Object instance) {
		kickStaleNodes();
		if (Provider.class==service) {
			if (instance==null) {
				noodle(context,null);
				return;
			}
			ContextNode cn = rememberOrGetToKnow(context);
			if (cn!=null) cn.addData(PROVIDER,instance);
		} else {
			ContextNode cn = rememberOrGetToKnow(context);
			if (cn!=null) cn.addData(service,instance);
		}
//		touch(context,context);
	}

	public void tell(Object context, String property, Object something) {
		if (context==null) {
			System.err.println("FSM global property "+property+" = "+something);
			return;
		}
		if (context instanceof FSM.TheOne && something instanceof FSM.TheOne
				&& something==context && FSM.Sacrifice.equals(property))
			addPropertyChangeListener((FSM.TheOne)something);

		kickStaleNodes();
		ContextNode cn = rememberOrGetToKnow(context);
		if (cn!=null) cn.addData(property,something);
//		touch(context,context);
	}

	public void touch(Object it, Object context) {
		if (it==null) return;
		kickStaleNodes();
		// get data (dependency injection pattern)
		ContextNode cn = rememberOrGetToKnow(context);
		analyze(it.getClass());
		cn.injection(it);
	}

	public Object touch(final Object me, Class c) {
		if (IContext.class.equals(c)) {
			return new IContext() {
				public Object getContentContext(Object location) {
					ContextNode n = remember(location);
					return n!=null && n.rAmen!=null ? n.rAmen.get() : null;
				}
				public Object[] getSpaceContext() {
					LinkedList steps = new LinkedList();
					Object o = me;
					for (; o!=null; o=getParent(o))
						steps.addFirst(new WeakReference(o));
					return steps.toArray();
				}
				public Object[] getSpaceContext2() {
					LinkedList steps = new LinkedList();
					Object o = me;
					for (; o!=null; o=getParent(o))
						steps.addFirst(o);
					return steps.toArray();
				}
			};
		}

		kickStaleNodes();
		// get data (service locator pattern)
		ContextNode data = rememberOrGetToKnow(me);
		if (ContextNode.class.equals(c)) {
			return data;
		}
		Object service = data.getService(c);
		if (service==null)
			service = root.getService(c);
		return service;
	}

	public Object create(Object context, Class c) {
		kickStaleNodes();
		// get data (dependency injection pattern)
		ContextNode cn = rememberOrGetToKnow(context);
		Object justCreated = null;
		try {
			justCreated = c.newInstance();
		} catch (InstantiationException ex) {
			ex.printStackTrace();
		} catch (IllegalAccessException ex) {
			ex.printStackTrace();
		}
		getNode(justCreated,cn).injection(null);
		known.remove(justCreated);
		return justCreated;
	}

	private final ReferenceQueue toForget = new ReferenceQueue();

	private void kickStaleNodes() {
		try {
			ContextNode stale = null;
			while ((stale = (ContextNode)toForget.poll())!=null) {
//				System.out.println("FSM: stale "+stale);
				stale.removeChildren();
//				stale.setParent(getNode("stale",null));
				stale.setParent(null);
			}
		} catch (Exception ex) {
			System.err.println(ex.getLocalizedMessage()+" in kickStaleNodes()");
		}
	}

	private ContextNode remember(Object target) {
		if (target==null) return root;
		return (ContextNode)known.get(target);
	}

	private ContextNode rememberOrGetToKnow(Object target) {
		if (target==null) return root;
		LinkedList<ContextNode> steps = new LinkedList();
		for (Object p=target; p!=null; p=getParent(p))
			steps.addFirst(getNode(p,null));
		ContextNode parent = null;
		for (ContextNode n : steps) {
			n.setParent(parent!=null ? parent : root);
			parent = n;
		}
		return steps.getLast();
	}

	private ContextNode getNode(Object target, ContextNode parent) {
		if (target==null) return root;
		ContextNode t = (ContextNode)known.get(target);
		if (t==null) {
			t = new ContextNode(target);
			t.setParent(parent!=null ? parent : root);
			known.put(target,t);
		}
		return t;
	}

	private final HashMap<Class,Map> injectTargetsByClass = new HashMap();

	private void analyze(ContextNode target) {
		Object t = target.get();
		if (t==null) return;
		analyze(t.getClass());
	}

	private void analyze(Class targetType) {
		if (injectTargetsByClass.containsKey(targetType)) return; // already analyzed?
		if (!isInjectTarget(targetType)) return;

		Map injectTargets = new LinkedHashMap();
		Class type = targetType;
		if (type.isAnonymousClass())
			type = type.getSuperclass();

		Field[] fields = type.getDeclaredFields();
		for (int i=0; i<fields.length; i++) {
			Field f = fields[i];
			if (!f.isAnnotationPresent(Resource.class))
				continue;
			if (!injectTargets.containsKey(f.getName()))
				injectTargets.put(f.getName(),f);
			if (!injectTargets.containsKey(f.getType()))
				injectTargets.put(f.getType(),f);
		}

		Method[] methods = type.getMethods();
		for (int i=0; i<methods.length; i++) {
			Method m = methods[i];
			String name = m.getName();
			Class[] p = m.getParameterTypes();
			if (p.length==1) {
				if ((m.isAnnotationPresent(Resource.class) /*|| isInjectTarget(p[0])*/) &&
						(name.startsWith("inject") || name.startsWith("set"))) {
					if (name.startsWith("inject")) name = name.substring("inject".length());
					else if (name.startsWith("set")) name = name.substring("set".length());
					if (name.length()==0) continue;
					name = Character.toLowerCase(name.charAt(0)) + name.substring(1);
					Object s = injectTargets.get(name);
					if (s!=null) {
						if (s instanceof List==false) {
							List l = new ArrayList();
							l.add(s);
							l.add(m);
							injectTargets.put(name,l);
						} else ((List)s).add(m);
					} else injectTargets.put(name,m);

					s = injectTargets.get(p[0]);
					if (s!=null) {
						if (s instanceof List==false) {
							List l = new ArrayList();
							l.add(s);
							l.add(m);
							injectTargets.put(p[0],l);
						} else ((List)s).add(m);
					} else injectTargets.put(p[0],m);

					continue;
				}
			}
		}
//		System.out.println("FSM: touch "+injectTargets);
		injectTargetsByClass.put(targetType,injectTargets);
	}

	private final ArrayList qnamePrefixes = new ArrayList();

	public void addInjectTargets(String qnamePrefix) {
		qnamePrefixes.add(qnamePrefix);
	}

	private boolean isInjectTarget(Class c) {
		if (c==null) return false;
		String cn = c.getName();
		for (int i=0; i<qnamePrefixes.size(); i++)
			if (cn.startsWith((String)qnamePrefixes.get(i)))
				return true;
		return false;
	}

	public static String getObjectName(Object o) {
		if (o==null) return "[NULL]";
		String s = o.getClass().getSimpleName();
		String n = null;
		if (o instanceof String)
			n = (String)o;
		if (o instanceof Component)
			n = ((Component)o).getName();
		if (n!=null) s += "["+n+"]";
		return s + "#" + System.identityHashCode(o);
	}

	public class ContextNode extends WeakReference implements TreeNode
	{
		public Map<Object,Object> contextData;
		public Map localData;
		public Map<Object,WeakReference> dataAtTarget = new HashMap();
		public boolean valid; // location in context tree has been validated
		public SoftReference<Object> rAmen;
		public ArrayList hits = new ArrayList();

		ContextNode(Object target) {
			super(target,toForget);
		}

		synchronized void injection(Object target) {
			validate();
			if (localData==null) return;
			if (localData.isEmpty()) {
				localData = null;
				return;
			}

			if (target==null) target = get();
			if (target==null) return;
			Map<?,?> t = injectTargetsByClass.get(target.getClass());
			if (t==null) {
				removeFromTreeIfNotUseful();
				return;
			}
//			Set injectTargets = new HashSet(t.values());

			hits.clear();
//			for (Iterator it = injectTargets.iterator(); it.hasNext(); ) {
//				Object injectTarget = it.next();
//				// collection
//				if (injectTarget instanceof Collection) {
//					for (Object mOrF : (Collection)injectTarget) {
//						// method injection
//						if (mOrF instanceof Method) {
//							Class key = ((Method)mOrF).getParameterTypes()[0];
//							Object service = getService(key);
//							if (service!=null && inject((Method)mOrF,key,target,service)) {
//								it.remove();
//								break;
//							}
//							continue;
//						}
//						// field injection
//						if (mOrF instanceof Field) {
//							Class key = ((Field)mOrF).getType();
//							Object service = getService(key);
//							if (service!=null && inject((Field)mOrF,key,target,service)) {
//								it.remove();
//								break;
//							}
//							continue;
//						}
//					}
//					continue;
//				}
//				// method injection
//				if (injectTarget instanceof Method) {
//					Class key = ((Method)injectTarget).getParameterTypes()[0];
//					Object service = getService(key);
//					if (service!=null && inject((Method)injectTarget,key,target,service))
//						it.remove();
//					continue;
//				}
//				// field injection
//				if (injectTarget instanceof Field) {
//					Class key = ((Field)injectTarget).getType();
//					Object service = getService(key);
//					if (service!=null && inject((Field)injectTarget,key,target,service))
//						it.remove();
//					continue;
//				}
//			}
//
//			if (!injectTargets.isEmpty()) {
				Map localDataCopy;
				synchronized (localData) {
					localDataCopy = new LinkedHashMap(localData);
				}
				for (Map.Entry e : t.entrySet()) {
					Object key = e.getKey();
					Object value = localDataCopy.get(key);
					if (value instanceof WeakReference) // for java.awt.Component
						value = ((WeakReference)value).get();
					if (value==null && key instanceof Class)
						value = getService((Class)key);
					if (value==null) continue;

					Object methodOrMethodsOrField = e.getValue();

					// collection
					if (methodOrMethodsOrField instanceof Collection) {
						for (Object mOrF : (Collection)methodOrMethodsOrField) {
							// method injection
							if (mOrF instanceof Method) {
								if (inject((Method)mOrF,key,target,value))
									break;
								continue;
							}
							// field injection
							if (mOrF instanceof Field) {
								if (inject((Field)mOrF,key,target,value))
									break;
								continue;
							}
						}
						continue;
					}

					// method injection
					if (methodOrMethodsOrField instanceof Method) {
						inject((Method)methodOrMethodsOrField,key,target,value);
						continue;
					}

					// field injection
					if (methodOrMethodsOrField instanceof Field) {
						inject((Field)methodOrMethodsOrField,key,target,value);
						continue;
					}
				}
//			}
//			if (hits.isEmpty())
//				invalidate();

//			System.out.println("FSM: inject into "+this+": "+localData.size()+" -> "+t.size()+" ->\n"+hits);
		}

		private boolean inject(Method m, Object key, Object target, Object value) {
			if (key instanceof Class && value instanceof Provider)
				value = ((Provider)value).get((Class)key,get());
			if (value instanceof Collection)
				System.err.println("there should be no more Collection");
			if (value instanceof CollectionValues)
				value = ((CollectionValues)value).c;

			Class[] types = m.getParameterTypes();
			if (types.length!=1) return false;
			if (value!=null) {
				Class c = types[0];
				if (c.isPrimitive())
					c = ReflectHelper.getObjectType(c);
				if (!c.isAssignableFrom(value.getClass()))
					return false;
			}

			boolean a = m.isAccessible();
			try {
				WeakReference oldvalue = dataAtTarget.get(m);
				if (true || oldvalue==null || value!=oldvalue.get()) {
					m.setAccessible(true);
					m.invoke(target,new Object[] { value });
//					System.err.println("injection: "+value+" -> "+m.getName()+" @ "+getObjectName(target));
					dataAtTarget.put(m,new WeakReference(value));
					hits.add(key);
				}
				return true;
			} catch (Exception ex) {
				System.err.println("injection failed: "+
						(value!=null ? value.getClass() : "null")+" -> "+m);
				System.err.println("injection value: "+value);
				System.err.print("injection exception: ");
				ex.printStackTrace();
			} finally {
				m.setAccessible(a);
			}
			return false;
		}

		private boolean inject(Field f, Object key, Object target, Object value) {
			if (key instanceof Class && value instanceof Provider)
				value = ((Provider)value).get((Class)key,get());
			if (value instanceof Collection)
				System.err.println("there should be no more Collection");
			if (value instanceof CollectionValues)
				value = ((CollectionValues)value).c;

			boolean a = f.isAccessible();
			try {
				WeakReference oldvalue = dataAtTarget.get(f);
				if (true || oldvalue==null || value!=oldvalue.get()) {
					f.setAccessible(true);
					f.set(target,value);
//					System.err.println("injection: "+value+" -> "+f.getName()+" @ "+getObjectName(target));
					dataAtTarget.put(f,new WeakReference(value));
					hits.add(key);
				}
				return true;
			} catch (Exception ex) {
				System.err.println("injection failed: "+
						(value!=null ? value.getClass() : "null")+" -> "+f);
				System.err.println("injection value: "+value);
				System.err.print("injection exception: ");
				ex.printStackTrace();
			} finally {
				f.setAccessible(a);
			}
			return false;
		}

		synchronized Object getService(Class service) {
			validate();
			if (localData!=null && localData.isEmpty())
				localData = null;

			Object instance = getService(contextData,service);
			return instance!=null ? instance : getService(localData,service);
		}

		private Object getService(Map m, Class service) {
			if (m==null) return null;
			Object instance = m.get(service);
			if (instance instanceof WeakReference)
				instance = ((WeakReference)instance).get();
			if (instance instanceof Provider)
				instance = ((Provider)instance).get(service,get());
			if (instance!=null && service.isAssignableFrom(instance.getClass()))
				return instance;

			instance = null;
			Object provider = m.get(PROVIDER);
			if (service==Provider.class) {
				if (provider==null) return null;
				if (provider instanceof Provider) return provider;
				if (provider instanceof Collection) {
					List<Provider> providers = new ArrayList((Collection)provider);
					Collections.reverse(providers);
					return new JoinedProvider(providers);
				}
			}

			if (provider instanceof Provider)
				instance = ((Provider)provider).get(service,get());
			if (instance!=null) return instance;

			if (provider instanceof Collection) {
				List<Provider> providers = new ArrayList((Collection)provider);
				Collections.reverse(providers);
				for (Provider p : providers) {
					instance = p.get(service,get());
					if (instance!=null) return instance;
				}
			}

			if (instance instanceof Provider)
				instance = ((Provider)instance).get(service,get());
			return instance;
		}

		synchronized void addData(Object key, Object value) {
			if (FSM.Sacrifice.equals(key)) {
				Object old = rAmen;
				rAmen = new SoftReference(value);
				if (changeSupport!=null)
					changeSupport.firePropertyChange(FSM.Sacrifice,old,value);
				return;
			}
			if (PROVIDER.equals(key) && value==null) {
				if (contextData!=null)
					contextData.remove(key);
				PostOrderIterator i = new PostOrderIterator(this,new TreeNodeChildrenIteratorOp());
				while (i.hasNext()) {
					ContextNode n = (ContextNode)i.next();
					n.contextData = null;
					n.localData = null;
					n.valid = false;
				}
				return;
			}
			if (contextData==null)
				contextData = Collections.synchronizedMap(new LinkedHashMap());
			boolean eq = contextData.containsKey(key);
			if (PROVIDER.equals(key) && eq) {
				Object old = contextData.get(key);
				Collection ps;
				if (old instanceof Collection)
					ps = (Collection)old;
				else {
					ps = new LinkedHashSet();
					ps.add(old);
					contextData.put(key,ps);
				}
				ps.add(value);
				return;
			}
			if (eq) { // check new vs. old value
				Object old = contextData.get(key);
				if (old instanceof WeakReference) // for java.awt.Component
					old = ((WeakReference)old).get();
				if (old instanceof CollectionValues && value instanceof Collection) {
					Object[] o = ((CollectionValues)old).values;
					Object[] v = ((Collection)value).toArray();
					eq = Arrays.equals(v,o);
				} else {
					eq = value==old || value!=null && value.equals(old);
				}
			}
			if (!eq) {
				if (value instanceof Collection)
					value = new CollectionValues((Collection)value);
				if (value instanceof Component)
					value = new WeakReference(value);
				if (value!=null && value.getClass().getName().startsWith("de.scheller.aface") &&
						value instanceof Provider==false)
					value = new WeakReference(value);
				contextData.put(key,value);
				invalidate();
				validateSubtreeData(key,value);
			}
		}

		void validate() {
			if (valid) return;
//			System.out.println("FSM: find all data for "+this);
			if (localData==null)
				localData = Collections.synchronizedMap(new LinkedHashMap());

			// parents from root to leaf
			LinkedList<ContextNode> steps = new LinkedList();
			for (ContextNode n=this; n!=null; n=(ContextNode)n.getParent())
				steps.addFirst(n);

			for (ContextNode n : steps) {
				if (n.contextData==null) continue;
				synchronized (n.contextData) {
					for (Map.Entry e : n.contextData.entrySet()) {
						Object key = e.getKey();
						if (key.equals(PROVIDER)) {
							List providers = (List)localData.get(key);
							if (providers==null) localData.put(key,providers = new ArrayList());
							Object o = e.getValue();
							if (o instanceof Collection)
								providers.addAll((Collection)o);
							else providers.add(o);
						} else {
//							if (!localData.containsKey(key))
							if (ColorProvider.class.equals(key)) {
								if (e.getValue() instanceof Inherit)
									localData.put(key,e.getValue());
							} else localData.put(key,e.getValue());
						}
					}
				}
			}
			if (localData.isEmpty()) {
				localData = null;
				return;
			}
			valid = true;
		}

		void invalidate() {
//			if (!valid) return;
			if (localData!=null) localData.clear();
			valid = false;
//			removeFromTreeIfNotUseful();
			if (children!=null)
				synchronized (children) {
					for (ContextNode c : children)
						c.invalidate();
				}
		}

		void removeFromTreeIfNotUseful() {
			if (isUseful()) return;
			ContextNode n = this;
			ContextNode p = parent;
			for (; p!=null; p=p.parent) {
				if (p.isUseful()) break;
				// FIXME diese Stelle könnte der Grund für ConcurrentModificationException sein (l.710)
				p.removeChild(n);
				n = p;
			}
			if (p==null) p = root;
			p.removeChild(n);
		}

		boolean isUseful() {
			if (get()==null) return false;
			if (valid) return true;
			if (localData!=null && !localData.isEmpty()) return true;
			if (contextData!=null && !contextData.isEmpty()) return true;
//			if (getNoodledParent()!=null) return true;
			return false;
		}

		void validateSubtreeData(Object key, Object value) {
			if (!valid)
				validate();
			if (localData!=null && !PROVIDER.equals(key)) {
//				System.out.println("FSM: update data at "+this);
				localData.put(key,value);
			}
			if (localData!=null) {
				injection(null);
			}
			if (this==root)
				return;
			Object parent = get();
			synchronized (noodles) {
				for (Map.Entry<Object,WeakReference> e : noodles.entrySet()) {
					WeakReference r = e.getValue();
					if (r!=null && r.get()==parent)
						rememberOrGetToKnow(e.getKey());
				}
			}
			if (children!=null)
				for (ContextNode n : children.toArray(new ContextNode[children.size()]))
					n.validateSubtreeData(key,value);
		}

		@Override
		public String toString() {
			Object target = get();
			String state = null;
			state  = rAmen!=null && rAmen.get()!=null ? "R" : "_";
			state += contextData!=null && !contextData.isEmpty() ? "P" : "_";
			state += localData!=null && !localData.isEmpty() ? "L" : "_";
			state += valid ? "V" : "_";
			state += getNoodledParent()!=null ? "N" : "_";
			if (!hits.isEmpty()) state += " " + hits;
			return getObjectName(target) + " " + state;
		}

		public String getToolTipText() {
			return Gui.toHtml(
					((valid ? "Valid: " : "Invalid: ") +
						(get()!=null ? toString(get()) : "<weakref died>")) +
					(contextData==null ? "" :
						("\n\nProvided: \n"+toString(contextData.entrySet()))) +
					(localData==null ? "" :
						("\n\nLocal: \n"+toString(localData.entrySet()))) +
					(getNoodledParent()==null ? "" :
						("\n\nNoodled to: "+getObjectName(getNoodledParent())))
					);
		}

		public String toString(Object o) {
			if (o instanceof Map.Entry) {
				Map.Entry e = (Map.Entry)o;
				return "\t"+toString(e.getKey())+" = "+toString(e.getValue());
			}
			if (o instanceof Iterable) {
				StringBuilder sb = new StringBuilder();
				for (Object e : (Iterable)o)
					sb.append(toString(e)).append("\n");
				if (sb.length()>0) sb.setLength(sb.length()-1); // cut \n
				return sb.toString();
			}
			String s = String.valueOf(o); // if o.toString return null,
			s = String.valueOf(s);        // we resolve null here
			if (s.length()>200)
				s = s.substring(0,200)+"...";
			return s;
		}

		////////////////////////////////////////////////////////////////////////
		//  parents & children

		ContextNode parent;
		List<ContextNode> children;

		void addChild(ContextNode n) {
			if (children==null) children = Collections.synchronizedList(new LinkedList());
			children.add(n);
			if (tm!=null)
				tm.nodesWereInserted(this,new int[] { children.size()-1 });
		}

		void removeChild(ContextNode n) {
			if (children==null || n==null) return;
			if (children.remove(n) && tm!=null)
				tm.nodesWereRemoved(this,new int[] { children.size() }, new Object[] { n });
		}

		void removeChildren() {
			children = null;
			if (tm!=null)
				tm.nodeStructureChanged(this);
		}

		void setParent(ContextNode parent) {
			if (this.parent==parent) return; // no need to set parent
			if (parent==this) { // cycle
				System.err.println("ContextNode.setParent() this as parent -> cycle");
				return;
			}
//			System.out.print("ContextNode.setParent() "+this+" -> "+parent);
			if (this.parent!=null) this.parent.removeChild(this);
			this.parent = parent;
			invalidate();
			if (this.parent!=null) {
				this.parent.addChild(this);
				analyze(this);
				injection(null);
			}
//			System.out.println(" /done");
		}

		private Object getNoodledParent() {
			WeakReference np = noodles.get(get()); // noodled parent
			if (np==null) return null;
			if (np.get()==null)
				removeFromTreeIfNotUseful();
			return np.get();
		}

		////////////////////////////////////////////////////////////////////////
		// TreeNode

		public TreeNode getParent() {
			return parent;
		}

		public Enumeration children() {
			return Collections.enumeration(children!=null ?
					Arrays.asList(children.toArray()) : Collections.EMPTY_LIST);
		}

		public boolean isLeaf() {
			return children==null || children.isEmpty();
		}

		public int getChildCount() {
			return children!=null ? children.size() : 0;
		}

		public TreeNode getChildAt(int childIndex) {
			if (children==null) return null;
			synchronized (children) {
				Iterator it = children.iterator();
				for (int i=0; i<childIndex; i++) it.next();
				return it.hasNext() ? (TreeNode)it.next() : null;
			}
		}

		public int getIndex(TreeNode node) {
			if (node==null) return -1;
			if (children==null || !children.contains(node)) return -1;
			synchronized (children) {
				Iterator it = children.iterator();
				for (int i=0; it.hasNext(); i++) {
					Object n = it.next();
					if (node==n || node.equals(n)) return i;
				}
				return -1;
			}
		}

		public boolean getAllowsChildren() {
			return false;
		}
	}

	private static class CollectionValues {
		Collection c;
		Object[] values;

		public CollectionValues(Collection c) {
			this.c = c;
			this.values = c.toArray();
		}

		@Override
		public String toString() {
			return Arrays.toString(values);
		}
	}

	protected PropertyChangeSupport changeSupport;

	public synchronized void addPropertyChangeListener(PropertyChangeListener pcl) {
		if (changeSupport==null)
			changeSupport = new SwingPropertyChangeSupport(this);
		changeSupport.addPropertyChangeListener(pcl);
	}

	public synchronized void removePropertyChangeListener(PropertyChangeListener pcl) {
		if (changeSupport!=null)
			changeSupport.removePropertyChangeListener(pcl);
	}

	public synchronized PropertyChangeListener[] getPropertyChangeListeners() {
		if (changeSupport==null) return new PropertyChangeListener[0];
		return changeSupport.getPropertyChangeListeners();
	}

	private static class JoinedProvider implements Provider
	{
		private final Collection providers;

		public JoinedProvider(Collection providers) {
			this.providers = providers;
		}

		public <T> T get(Class<T> c, Object rq) {
			for (Object p : providers) {
				if (p instanceof Provider==false)
					continue;
				Object o = ((Provider)p).get(c,rq);
				if (o!=null) return (T)o;
			}
			return null;
		}

	}
}
