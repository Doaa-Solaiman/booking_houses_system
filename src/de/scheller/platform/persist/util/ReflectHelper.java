/* /////////////////////////////////////////////////////////////////////////////
 *
 * Created       :  01.08.2007 15:22:22 by kandzia
 * Project       :  flexwf-server
 * CVS Ident     :  $Revision$, $Date$
 * Last Commiter :  $Author$
 *
 * Copyright (c) 2004-2007 Scheller Systemtechnik GmbH
 *                         Poeler Strasse 85a, 23970 Wismar, Germany
 *
 *//////////////////////////////////////////////////////////////////////////////
package de.scheller.platform.persist.util;

import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import de.scheller.common.HasId;
import de.scheller.platform.common.MapUtils;
import de.scheller.util.Pair;

public abstract class ReflectHelper implements Serializable
{
	public static final Logger logger = Logger.getLogger(
			ReflectHelper.class.getName());

	public static Serializable getId(Object bean) {
		try {
			Method m = bean.getClass().getMethod("getId",(Class[])null);
			return (Serializable)m.invoke(bean,(Object[])null);
		} catch (Exception ex) {
			logger.log(Level.SEVERE,ex.getLocalizedMessage(),ex);
			throw new RuntimeException(ex);
		}
	}

	public static Class getDataObjectClass(
			Class ownerClass, String field, Object value) {
		if (value==null || value instanceof Collection) {
			Field f = getField(ownerClass,field);
			if (f!=null) {
				Class t = f.getType();
				if (!Collection.class.isAssignableFrom(t)) return t;
				Type gt = f.getGenericType();
				return (Class)((ParameterizedType)gt).getActualTypeArguments()[0];
			}
		}
		return value==null ? null : value.getClass();
	}

	public static void set(Object o, String field, Object value) {
		Method g = getGetter(o.getClass(),field);
		if (g==null) return;
		Method m = getSetter(o.getClass(),field,g.getReturnType());
		if (m==null) return;
		boolean a = m.isAccessible();
		try {
			m.setAccessible(true);
			m.invoke(o,value);
		} catch (Exception ex) {
			logger.log(Level.SEVERE,ex.getLocalizedMessage()+
					", field: "+field+", target: "+o,ex);
			throw new RuntimeException(ex);
		} finally {
			m.setAccessible(a);
		}
	}

	public static Object get(Object o, String field) {
		Method m = getGetter(o.getClass(),field);
		if (m==null) return null;
		boolean a = m.isAccessible();
		try {
			m.setAccessible(true);
			return m.invoke(o);
		} catch (Exception ex) {
			logger.log(Level.SEVERE,ex.getLocalizedMessage(),ex);
			throw new RuntimeException(ex);
		} finally {
			m.setAccessible(a);
		}
	}

	public static void write(Object o, String field, Object value) {
		Field f = getField(o.getClass(),field);
		if (f==null) return;
		boolean a = f.isAccessible();
		try {
			f.setAccessible(true);
			f.set(o,value);
		} catch (Exception ex) {
			logger.log(Level.SEVERE,ex.getLocalizedMessage(),ex);
			throw new RuntimeException(ex);
		} finally {
			f.setAccessible(a);
		}
	}

	public static void write(Class c, String field, Object value) {
		Field f = getField(c,field);
		if (f==null) return;
		boolean a = f.isAccessible();
		try {
			f.setAccessible(true);
			f.set(null,value);
		} catch (Exception ex) {
			logger.log(Level.SEVERE,ex.getLocalizedMessage(),ex);
			throw new RuntimeException(ex);
		} finally {
			f.setAccessible(a);
		}
	}

	public static Object read(Object o, String field) {
		Field f = getField(o.getClass(),field);
		if (f==null) return null;
		boolean a = f.isAccessible();
		try {
			f.setAccessible(true);
			return f.get(o);
		} catch (Exception ex) {
			logger.log(Level.SEVERE,ex.getLocalizedMessage(),ex);
			throw new RuntimeException(ex);
		} finally {
			f.setAccessible(a);
		}
	}

	public static Object read(Class c, String field) {
		Field f = getField(c,field);
		if (f==null) return null;
		boolean a = f.isAccessible();
		try {
			f.setAccessible(true);
			return f.get(null);
		} catch (Exception ex) {
			logger.log(Level.SEVERE,ex.getLocalizedMessage(),ex);
			throw new RuntimeException(ex);
		} finally {
			f.setAccessible(a);
		}
	}

	public static void copy(Object target, final Object source) {
		copy(target,source,null);
	}

	public static void copy(Object target, final Object source, Class lastSuperClass) {
		if (source==null) throw new IllegalArgumentException("no source");
		if (target==null) throw new IllegalArgumentException("no target");
		Class targetClass = target.getClass();
		Class sourceClass = source.getClass();
		if (targetClass!=sourceClass
				&& !(targetClass.isAssignableFrom(sourceClass)
						|| sourceClass.isAssignableFrom(targetClass))) {
			throw new IllegalArgumentException(
					"source class and target class must be the same");
		}
		for (;;) {
			Field[] fields = targetClass.getDeclaredFields();
			for (int i=0; i<fields.length; i++) {
				Field f = fields[i];
				if (Modifier.isFinal(f.getModifiers())) continue;
				f.setAccessible(true);
				Object obj = null;
				try {
					obj = f.get(source);
				} catch (Exception e) {}
				try {
					f.set(target,obj);
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			}
			if (targetClass==lastSuperClass) break;
			targetClass = targetClass.getSuperclass();
			if (targetClass==null) break;
		}
	}

	public static void copy2(Object target, final Object source) {
		if (source==null) throw new IllegalArgumentException("no source");
		if (target==null) throw new IllegalArgumentException("no target");
		Class targetClass = target.getClass();
		Class sourceClass = source.getClass();
		if (targetClass!=sourceClass
				&& !(targetClass.isAssignableFrom(sourceClass)
						|| sourceClass.isAssignableFrom(targetClass))) {
			for (sourceClass = sourceClass.getSuperclass(); sourceClass!=null; sourceClass = sourceClass.getSuperclass()) {
				if (targetClass.isAssignableFrom(sourceClass)
						|| sourceClass.isAssignableFrom(targetClass))
					break;
			}
			if (sourceClass == null)
				throw new IllegalArgumentException(
						"source class and target class must be the same");
		}
		for (;;) {
			try {
				Field[] fields = targetClass.getDeclaredFields();
				Set<String> sourceFields = null;
				if (!targetClass.equals(sourceClass)) {
					sourceFields = new HashSet();
					Field[] sFields = getDeclaredFields(sourceClass);
					for (int i = 0; i < sFields.length; i++) {
						sourceFields.add(sFields[i].getName());
					}
				}
				for (int i=0; i<fields.length; i++) {
					Field f = fields[i];
					if (Modifier.isFinal(f.getModifiers())) continue;
					if (sourceFields!=null && !sourceFields.contains(f.getName())) continue;
					f.setAccessible(true);
					Object obj = null;
					try {
						obj = f.get(source);
						if (obj==null && f.getType().isPrimitive())
							continue;
						f.set(target,obj);
					} catch (Exception ex) {
						ex.printStackTrace();
					}
				}
			} catch (Exception ex) {
				ex.printStackTrace();
			}
			targetClass = targetClass.getSuperclass();
			if (targetClass==null) break;
		}
	}

	public static void copyWithGetterInvoke(Object target, Object source) {
		copyWithGetterInvoke(target,source,null);
	}

	public static void copyWithGetterInvoke(Object target, Object source, Class lastSuperClass) {
		if (source==null) throw new IllegalArgumentException("no source");
		if (target==null) throw new IllegalArgumentException("no target");
		Class targetClass = target.getClass();
		Class sourceClass = source.getClass();
		if (targetClass!=sourceClass
				&& !(targetClass.isAssignableFrom(sourceClass)
						|| sourceClass.isAssignableFrom(targetClass))) {
			for (sourceClass = sourceClass.getSuperclass(); sourceClass!=null; sourceClass = sourceClass.getSuperclass()) {
				if (targetClass.isAssignableFrom(sourceClass)
						|| sourceClass.isAssignableFrom(targetClass))
					break;
			}
			if (sourceClass == null)
				throw new IllegalArgumentException(
						"source class and target class must be the same");
		}
		for (;;) {
			Field[] fields = targetClass.getDeclaredFields();
			for (int i=0; i<fields.length; i++) {
				Field f = fields[i];
				if (Modifier.isFinal(f.getModifiers())) continue;
				f.setAccessible(true);
				Object obj = null;
				try {
					String methodName =
						"get" +	f.getName().substring(0,1).toUpperCase() +
						f.getName().substring(1);
					Method m;
					try {
						m = sourceClass.getMethod(methodName);
					} catch (NoSuchMethodException e) {
						m = null;
					}
					if (m!=null && ! isTransient(m)) {
						obj = m.invoke(source);
					} else {
						methodName =
							"is" +	f.getName().substring(0,1).toUpperCase() +
							f.getName().substring(1);
						m = sourceClass.getMethod(methodName);
						if (m!=null && !isTransient(m))
							obj = m.invoke(source);
						else {
							obj = f.get(source);
						}
					}
				} catch (Exception e) {}
				try {
					if (obj!=null) {
						// Versions-Feld bei neuem Objekt auf null setzen
//						if ("version".equals(f.getName()))
//							f.set(target, null);
//						else
						if (f.getType()==WeakReference.class &&
								obj instanceof WeakReference==false &&
								"context".equals(f.getName()))
							f.set(target,new WeakReference(obj));
						else
							f.set(target,obj);
					}
				} catch (Exception ex) {
					System.err.println("set value at field '"+f.getName()+"' on " + targetClass.getSimpleName() + " failed.");
					ex.printStackTrace();
				}
			}
			if (targetClass==lastSuperClass) break;
			targetClass = targetClass.getSuperclass();
			if (targetClass==null) break;
		}
	}

	public static void copyMatchingWithGetterInvoke(Object target, Object source) {
		copyMatchingWithGetterInvoke(target,source,null);
	}

	public static void copyMatchingWithGetterInvoke(Object target, Object source, Class lastSuperClass) {
		if (source==null) throw new IllegalArgumentException("no source");
		if (target==null) throw new IllegalArgumentException("no target");
		Class targetClass = target.getClass();
		Class sourceClass = source.getClass();
		Map<Pair<String,Class>,Method> getters = getGetters(sourceClass);
		for (;;) {
			Field[] fields = targetClass.getDeclaredFields();
			for (int i=0; i<fields.length; i++) {
				Field f = fields[i];
				if (Modifier.isFinal(f.getModifiers())) continue;
				Method g = getters.get(new Pair(f.getName(),f.getType()));
				if (g==null)
					continue;
				f.setAccessible(true);
				try {
					f.set(target,g.invoke(source));
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			}
			if (targetClass==lastSuperClass) break;
			targetClass = targetClass.getSuperclass();
			if (targetClass==null) break;
		}
	}

	static Map<Pair<Class,String>,Field> fieldsCache = Collections.synchronizedMap(new HashMap());

	public static Field getField(Class c, String name) {
		Pair<Class,String> typeAndName = new Pair(c,name);
		Field field = fieldsCache.get(typeAndName);
		if (field!=null) return field;
		fieldsCache.put(typeAndName,field = getFieldNoCache(c,name));
		return field;
	}

	public static Field getFieldNoCache(Class c, String name) {
		for (;;) {
			try {
				Field f = c.getDeclaredField(name);
				return f;
			} catch (SecurityException ex) {
				logger.log(Level.SEVERE,ex.getLocalizedMessage(),ex);
				throw ex;
			} catch (NoSuchFieldException ex) {
				c = c.getSuperclass();
				if (c==null) return null;
			}
		}
	}

	private static Map<Class,Map<String,Method>> simpleGetters = new LinkedHashMap();
	private static Map<Class,Map<String,Method>> simpleSetters = new LinkedHashMap();

	public static Method getGetter(Class c, String field) {
		Map<String,Method> cache = simpleGetters.get(c);
		Method m = cache!=null ? cache.get(field) : null;
		boolean cn = cache!=null && cache.containsKey(field);
		if (m==null && !cn) {
			String n = Character.toUpperCase(field.charAt(0))+field.substring(1);
			m = getMethod(c,"get"+n);
			if (m==null)
				m = getMethod(c,"is"+n);
			MapUtils.groupT(simpleGetters,LinkedHashMap.class,c,field,m);
		}
		return m;
	}

	public static Method getSetter(Class c, String field, Class... parameters) {
		Map<String,Method> cache = simpleSetters.get(c);
		Method m = cache!=null ? cache.get(field) : null;
		boolean cn = cache!=null && cache.containsKey(field);
		if (m==null && !cn) {
			String n = Character.toUpperCase(field.charAt(0))+field.substring(1);
			m = getMethod(c,"set"+n,parameters);
			MapUtils.groupT(simpleSetters,LinkedHashMap.class,c,field,m);
		}
		return m;
	}

	public static Method getMethod(Class c, String field) {
		return getMethod(c,field,new Class[0]);
	}

	public static Method getMethod(Class c, String field, Class... parameters) {
		try {
			return c.getMethod(field,parameters);
		} catch (NoSuchMethodException ex) {
			if (parameters.length==1)
				return findBest(c,field,parameters[0]);
			return null;
		} catch (SecurityException ex) {
			throw ex;
		}
	}

	public static Object callMethod(Object on, String name, Object... args) throws Exception {
		boolean staticMethod = on instanceof Class;
		Method m = findMethod(staticMethod ? (Class)on : on.getClass(),
				MemberStrategy.declared,name,args);
		if (m==null)
			m = findMethod(staticMethod ? (Class)on : on.getClass(),
				MemberStrategy.allOver,name,args);
		if (m==null)
			throw new NoSuchMethodException("a suitable method "+name+" cannot be found");
		boolean a = m.isAccessible();
		try {
			m.setAccessible(true);
			return m.invoke(staticMethod ? null : on,args);
		} catch (InvocationTargetException ex) {
			if (ex.getCause() instanceof Exception)
				throw (Exception)ex.getCause();
			throw ex;
		} finally {
			m.setAccessible(a);
		}
	}

	public static Method findMethod(Class c, MemberStrategy s, String name, Object... args) {
		return findMethod(c,s,name,parameterTypes(args));
	}

	public static Method findMethod(Class c, MemberStrategy s, String name, Class... paramTypes) {
		if (name==null)
			return findUnambiguousMethod(c,s,name,paramTypes);
		try {
			return c.getMethod(name,paramTypes);
		} catch (NoSuchMethodException ex) {
			return findUnambiguousMethod(c,s,name,paramTypes);
		}
	}

	// TODO findBestMethod -> siehe setValue
//	public static Method findBestMethod(Class c, MemberStrategy s, String name, Class... paramTypes) {
//	public void setValue(Object value) {
//	public void setValue(String value) {
//	public void setValue(Serializable value) {

	public static Method findBestMethod(Collection<Method> methods, Class paramType) {
		Method bestMatch = null;
		Class bestType = null;
		for (Method m : methods) {
			Class<?>[] types = m.getParameterTypes();
			Class t = types.length==1 ? types[0] : null;
			if (t==null || !t.isAssignableFrom(paramType))
				continue;
			if (bestType!=null && !bestType.isAssignableFrom(t))
				continue;
			bestMatch = m;
			bestType = t;
		}
		return bestMatch;
	}

	public static Method findUnambiguousMethod(Class c, MemberStrategy s, String name, Class... paramTypes) {
		Set<Method> result = new HashSet();
		for (Method m : getMethods(c,s)) {
			if (name!=null && !name.equals(m.getName()))
				continue; // name doesn't match
			if (!parametersMatch(m.getParameterTypes(),paramTypes))
				continue; // method doesn't match
			result.add(m);
		}
		for (Iterator<Method> it = result.iterator(); it.hasNext();) {
			Method m = it.next();
			if (m.getDeclaringClass().isInterface()) it.remove();
			if (result.size()==1) return m;
		}
		if (result.size()>1)
			throw new IllegalStateException("ambiguous method: "+result);
		return null;
	}

	public static boolean parametersMatch(Class[] candidate, Class[] types) {
		if (candidate.length!=types.length)
			return false; // parameter count doesn't match
		for (int i=0; i<candidate.length; i++)
			if (types[i]!=null && !getObjectType(candidate[i]).isAssignableFrom(types[i]))
				return false;
		return true;
	}

	public static Class[] parameterTypes(Object... args) {
		Class[] pt = new Class[args.length];
		for (int i=0; i<args.length; i++) {
			Object v = args[i];
			pt[i] = v==null ? null : v.getClass();
		}
		return pt;
	}

	public static Class getGetterReturnType(Class c, String field) {
		Method g = getGetter(c,field);
		return g!=null ? g.getReturnType() : null;
	}

	private static final HashMap declaredFields = new HashMap();

	public static Field[] getDeclaredFields(Class c) {
		return getDeclaredFields(c,Object.class);
	}

	/**
	 * Searches fields for a class and its superclasses until stopClass.<br>
	 * Fields from stopClass are not in the result.
	 */
	public static Field[] getDeclaredFields(Class c, Class stopClass) {
		Field[] fields = (Field[])declaredFields.get(c);
		if (fields!=null) return fields;
		LinkedList f = new LinkedList();
		if (stopClass==null)
			stopClass = Object.class;
		for (; c!=null && !c.equals(stopClass); c=c.getSuperclass())
			f.addAll(Arrays.asList(c.getDeclaredFields()));
		fields = (Field[])f.toArray(new Field[f.size()]);
		declaredFields.put(c,fields);
		return fields;
	}

	public static Map<String,Object[]> compare(final Object o1, final Object o2, Class stopClass) {
		if ((o1==null && o2==null) ||
				(o1!=null && o2!=null && !o1.getClass().equals(o2.getClass())))
			return null;

		Map<String,Object[]> map = new LinkedHashMap();
		Field[] fields = null;
		if (o1!=null)
			fields = getDeclaredFields(o1.getClass(),stopClass);
		else
			fields = getDeclaredFields(o2.getClass(),stopClass);
		try {
			for (int i=0; i<fields.length; i++) {
				Field f = fields[i];
				int m = f.getModifiers();
				if (Modifier.isFinal(m) && Modifier.isStatic(m)) continue;
				String name = f.getName();

				if (Collection.class.isAssignableFrom(f.getType())) continue;
				f.setAccessible(true);

				Object v1 = o1==null ? null : f.get(o1);
				Object v2 = o2==null ? null : f.get(o2);
				if (v1 instanceof HasId || v2 instanceof HasId) {
					v1 = v1 instanceof HasId ? ((HasId)v1).getId() : null;
					v2 = v2 instanceof HasId ? ((HasId)v2).getId() : null;
				}

				boolean eq = v1==v2 || v1!=null && v1.equals(v2);

				// Sonderbehandlung Datumswerte (Date, Timestamp), 07.09.2009
				// Timestamp kann nur Sekunden -> t/1000*1000
				if (!eq && v1!=null && v2!=null
						&& v1 instanceof Date && v2 instanceof Date)
					eq = (((Date)v1).getTime()/1000*1000 == ((Date)v2).getTime()/1000*1000);

				if (!eq) map.put(name,new Object[] { v1, v2 });
			}
			return map;
		} catch (Exception ex) {
			logger.log(Level.SEVERE,ex.getLocalizedMessage(),ex);
			return null;
		}
	}

	public static Class getObjectType(Class p) {
		if (!p.isPrimitive()) return p;
		if (p==Boolean.TYPE) return Boolean.class;
		if (p==Byte.TYPE) return Byte.class;
		if (p==Character.TYPE) return Character.class;
		if (p==Short.TYPE) return Short.class;
		if (p==Integer.TYPE) return Integer.class;
		if (p==Long.TYPE) return Long.class;
		if (p==Float.TYPE) return Float.class;
		if (p==Double.TYPE) return Double.class;
		if (p==Void.TYPE) return Void.class;
		throw new UnsupportedOperationException("can't translate primitive class "+p);
	}

	public static Class getPrimitiveType(Class c) {
		if (c.isPrimitive()) return c;
		if (c==Boolean.class) return Boolean.TYPE;
		if (c==Byte.class) return Byte.TYPE;
		if (c==Character.class) return Character.TYPE;
		if (c==Short.class) return Short.TYPE;
		if (c==Integer.class) return Integer.TYPE;
		if (c==Long.class) return Long.TYPE;
		if (c==Float.class) return Float.TYPE;
		if (c==Double.class) return Double.TYPE;
		throw new IllegalArgumentException("can't translate "+c+" to primitive class");
	}

	public static Class getType(ClassLoader loader, String type) {
		if ("int".equals(type)) return int.class;
		if ("long".equals(type)) return long.class;
		if ("double".equals(type)) return double.class;
		if ("float".equals(type)) return float.class;
		if ("short".equals(type)) return short.class;
		if ("byte".equals(type)) return byte.class;
		if ("char".equals(type)) return char.class;
		if ("boolean".equals(type)) return boolean.class;
		if ("int[]".equals(type)) return int[].class;
		if ("long[]".equals(type)) return long[].class;
		if ("double[]".equals(type)) return double[].class;
		if ("float[]".equals(type)) return float[].class;
		if ("short[]".equals(type)) return short[].class;
		if ("byte[]".equals(type)) return byte[].class;
		if ("char[]".equals(type)) return char[].class;
		if ("boolean[]".equals(type)) return boolean[].class;
		try {
			return Class.forName(type,true,loader);
//			return loader!=null ? loader.loadClass(type) : Class.forName(type);
		} catch (ClassNotFoundException ex) {
			logger.log(Level.SEVERE,ex.getLocalizedMessage(),ex);
			return null;
		}
	}

	public static void getset(Object source, Object target) {
		getset(source,target,null);
	}

	public static void getset(Object source, Object target, String name) {
		if (source==null || target==null) return;
		Map<Pair<String,Class>,Method> getters = getGetters(source.getClass());
		Map<Pair<String,Class>,Method> setters = getSetters(target.getClass());
		Map<String,List<Method>> settersByName = new LinkedHashMap();
		for (Map.Entry<Pair<String,Class>,Method> e : setters.entrySet())
			MapUtils.groupT(settersByName,ArrayList.class,e.getKey().getFirst(),e.getValue());
		for (Map.Entry<Pair<String,Class>,Method> e : getters.entrySet()) {
			Pair<String,Class> p = e.getKey();
			String field = p.getFirst();
			if (name!=null && !name.equals(field)) continue;
			Method g = e.getValue();
			List<Method> sl = settersByName.get(field);
			if (sl==null || sl.isEmpty())
				continue;
			Method s = sl.size()==1 ? sl.get(0) : findBestMethod(sl,p.getSecond());
			if (s==null)
				continue;
//				throw new IllegalStateException("ambiguous methods: "+sl);
			try {
				s.invoke(target,g.invoke(source,(Object[])null));
			} catch (InvocationTargetException ex) {
				if (ex.getTargetException() instanceof UnsupportedOperationException==false)
					logger.log(Level.SEVERE,ex.getLocalizedMessage(),ex);
			} catch (Exception ex) {
				logger.log(Level.SEVERE,ex.getLocalizedMessage(),ex);
			}
		}
	}

	private static Map<Class,Map<Pair<String,Class>,Method>> getters = new LinkedHashMap();

	public static synchronized Map<Pair<String,Class>,Method> getGetters(Class c) {
		Map<Pair<String,Class>,Method> map = getters.get(c);
		if (map!=null)
			return map;

		map = new LinkedHashMap();
		Method[] methods = c.getMethods();
		for (int i=0; i<methods.length; i++) {
			Method m = methods[i];
			if (m.isSynthetic()) continue;

			Class rt = m.getReturnType();
			if (rt==null) continue;
			Class[] pt = m.getParameterTypes();
			if (pt.length>0) continue;

			String n = m.getName();
			int index = -1;
			if (n.startsWith("get")) index = 3;
			if (n.startsWith("is")) index = 2;
			if (index<0) continue;
			char ch = n.charAt(index);
			if (!Character.isUpperCase(ch)) continue;

			if (isTransient(m))
				continue;

			n = n.substring(index);
			n = Character.toLowerCase(ch) + n.substring(1);
			map.put(new Pair(n,rt),m);
		}
		getters.put(c,map = Collections.unmodifiableMap(map));
		return map;
	}

	private static Map<Class,Map<Pair<String,Class>,Method>> setters = new LinkedHashMap();

	public static Map<Pair<String,Class>,Method> getSetters(Class c) {
		Map<Pair<String,Class>,Method> map = setters.get(c);
		if (map!=null)
			return map;

		setters.put(c,map = new LinkedHashMap());
		Method[] methods = c.getMethods();
		for (int i=0; i<methods.length; i++) {
			Method m = methods[i];
			if (m.isSynthetic()) continue;

			// this filters "String Attribute.setValue(String)"
//			Class rt = m.getReturnType();
//			if (rt!=void.class) continue;
			Class[] pt = m.getParameterTypes();
			if (pt.length!=1) continue;

			String n = m.getName();
			int index = -1;
			if (n.startsWith("set")) index = 3;
			if (index<0) continue;
			char ch = n.charAt(index);
			if (!Character.isUpperCase(ch)) continue;

			if (isTransient(m))
				continue;

			n = n.substring(index);
			n = Character.toLowerCase(ch) + n.substring(1);
			map.put(new Pair(n,pt[0]),m);
		}
		return map;
	}

	private static boolean isTransient (Method m) {
		for (Annotation annotation :m.getAnnotations()) {
			if (annotation.getClass().getCanonicalName().endsWith("Transient"))
				return true;
		}
		return false;
	}

	public static Method findBest(Class c, String name, Class argc) {
		Method[] methods = c.getMethods();
		LinkedList<Method> matches = new LinkedList();
		for (Method m : methods) {
			if (m.isSynthetic()) continue;
			if (!name.equals(m.getName())) continue;
			Class[] t = m.getParameterTypes();
			if (t.length==1 && t[0].isAssignableFrom(argc)) matches.add(m);
		}
		return matches.size()==1 ? matches.getFirst() : null;
	}

	public static Method findBestDecl(Class c, String name, Class argc) {
		Method[] methods = c.getDeclaredMethods();
		LinkedList<Method> matches = new LinkedList();
		for (Method m : methods) {
			if (!name.equals(m.getName())) continue;
			Class[] t = m.getParameterTypes();
			if (t.length==1 && t[0].isAssignableFrom(argc)) matches.add(m);
		}
		return matches.size()==1 ? matches.getFirst() : null;
	}

	// TODO schick machen und findBest* gedöns ersetzen
	public static <T> T newInstance(Object e, Class<T> c, Object... args)
	throws Exception {
		if (c.isMemberClass() && !Modifier.isStatic(c.getModifiers())) {
			if (e==null)
				throw new IllegalArgumentException("enclosing instance is needed to instanciate "+c);
			Object[] a = new Object[1+args.length];
			a[0] = e;
			System.arraycopy(args,0,a,1,args.length);
			Class[] pt = parameterTypes(a);
			return c.getConstructor(pt).newInstance(a);
		} else {
			if (args.length==0)
				return c.newInstance();
			Class[] pt = parameterTypes(args);
			return c.getConstructor(pt).newInstance(args);
		}
	}

	/**
	 * <ul>
	 * <li>own - direct public members (without superclasses)</li>
	 * <li>normal - all public members but no overridden (incl. superclasses)</li>
	 * <li>normalOver - all public members and overridden (incl. superclasses)</li>
	 * <li>declared - direct members (without superclasses)</li>
	 * <li>all (TODO) - all members but no overridden (incl. superclasses)</li>
	 * <li>allOver - all members and overridden (incl. superclasses)</li>
	 * </ul>
	 */
	public static enum MemberStrategy {
		/** direct public members (without superclasses) */
		own,
		/** all public members but no overridden (incl. superclasses) */
		normal,
		/** all public members and overridden (incl. superclasses) */
		normalOver,
		/** direct members (without superclasses) */
		declared,
		/** TODO all members but no overridden (incl. superclasses) */
		all,
		/** all members and overridden (incl. superclasses) */
		allOver,
	}

	public static Set<Method> getMethods(Class c, MemberStrategy s) {
		return getMethods(c,s,null);
	}

	public static Set<Method> getMethods(Class c, MemberStrategy s, Class stop) {
		if (s==MemberStrategy.own) {
			Set<Method> methods = new HashSet();
			for (Method m : c.getMethods()) {
				if (m.isSynthetic()) continue;
				if (m.getDeclaringClass()==c) methods.add(m);
			}
			return Collections.unmodifiableSet(methods);
		}
		if (s==MemberStrategy.normal) {
			Set<Method> methods = new HashSet();
			for (Method m : c.getMethods()) {
				if (m.isSynthetic()) continue;
				if (stop!=null && m.getDeclaringClass().isAssignableFrom(stop)) continue;
				methods.add(m);
			}
			return Collections.unmodifiableSet(methods);
		}
		if (s==MemberStrategy.normalOver) {
			Set<Method> methods = new HashSet();
			for (Class cc : getClasses(c,ClassStrategy.all,stop))
				for (Method m : cc.getMethods()) {
					if (m.isSynthetic()) continue;
					if (!Modifier.isPublic(m.getModifiers())) continue;
					if (stop!=null && m.getDeclaringClass().isAssignableFrom(stop)) continue;
					methods.add(m);
				}
			return Collections.unmodifiableSet(methods);
		}
		if (s==MemberStrategy.declared) {
			return Collections.unmodifiableSet(
					new HashSet(Arrays.asList(c.getDeclaredMethods())));
		}
		if (s==MemberStrategy.all) {
			// TODO
//			LinkedList<Set<Method>> m = new LinkedList();
//			for (Class cc : getClasses(c,ClassStrategy.classes,stop))
//				m.addFirst(getMethods(cc,MemberStrategy.declared));
//			for (Class cc : getClasses(c,ClassStrategy.interfaces,stop))
//				m.addFirst(getMethods(cc,MemberStrategy.declared));
			Set<Method> methods = new HashSet();
//			for (Set<Method> mm : m) {
//				for (Method mmm : mm) {
//					methods.remove(findBestMethod(
//							mmm.getDeclaringClass(),MemberStrategy.allOver,
//							mmm.getName(),mmm.getParameterTypes()));
//					methods.add(mmm);
//				}
//			}
			return Collections.unmodifiableSet(methods);
		}
		if (s==MemberStrategy.allOver) {
			Set<Method> methods = new HashSet();
			for (Class cc : getClasses(c,ClassStrategy.all,stop))
				methods.addAll(Arrays.asList(cc.getDeclaredMethods()));
			return Collections.unmodifiableSet(methods);
		}
		return null;
	}

	public static Set<Method> getMethodsCallable(Class c, MemberStrategy s, Class stop) {
		// TODO
		return null;
	}

	public static enum ClassStrategy {
		classes, // classes only
		interfaces, // interfaces only
		all // classes and interfaces
	}

	public static Set<Class> getClasses(Class root, ClassStrategy s, Class stop) {
		Set<Class> classes = new LinkedHashSet();
		getClasses(root,classes,s,stop);
		return classes;
	}

	public static void getClasses(Class c, Collection<Class> classes, ClassStrategy s, Class stop) {
		if (c==null || c==stop) return;
		if (s!=ClassStrategy.interfaces || c.isInterface())
			classes.add(c);
		if (s!=ClassStrategy.classes)
			for (Class i : c.getInterfaces())
				getClasses(i,classes,s,stop);
		getClasses(c.getSuperclass(),classes,s,stop);
	}

	public static Type[] getTypeArgumentsFor(Class c, Class target) {
		while (c!=null) {
			Type t = c.getGenericSuperclass();
			if (t==null) {
				c = c.getSuperclass();
				continue;
			}
			if (t instanceof Class) {
				c = (Class)t;
				continue;
			}
			if (t instanceof ParameterizedType) {
				ParameterizedType pt = (ParameterizedType)t;
				c = (Class)pt.getRawType();
				if (c.equals(target))
					return pt.getActualTypeArguments();
				continue;
			}
		}
		return null;
	}

	public static Type getTypeArgumentFor(Class c, Class target) {
		Type[] ta = getTypeArgumentsFor(c,target);
		return ta!=null && ta.length>0 ? ta[0] : null;
	}

	public static Class getClassArgumentFor(Class c, Class target) {
		Type[] ta = getTypeArgumentsFor(c,target);
		return ta!=null && ta.length>0 && ta[0] instanceof Class ? (Class)ta[0] : null;
	}

	public static Method getCallerMethod(Class... paramTypes) {
		Class caller = CallersClassNameGetter.getCallersClass();
		return getCallerMethod_(caller,paramTypes);
	}

	public static Method getCallerMethod(Object caller, Class... paramTypes) {
		return getCallerMethod_(caller.getClass(),paramTypes);
	}

	private static Method getCallerMethod_(Class caller, Class... paramTypes) {
		String callerName = caller.getName();
		StackTraceElement c = null; // caller
		boolean otherClasses = false;
		for (StackTraceElement e : new Exception().getStackTrace()) {
			if (callerName.equals(e.getClassName()) || otherClasses)
				try {
					Class cc = caller.getClassLoader().loadClass(e.getClassName());
					return cc.getMethod(e.getMethodName(),paramTypes);
				} catch (NoSuchMethodException ignore) {
					otherClasses = true;
				} catch (Exception ex) {
					throw new RuntimeException(ex);
				}
		}
		return null;
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
