/* /////////////////////////////////////////////////////////////////////////////
 *
 * Created       :  19.11.2013 13:54:07 by kunze
 * Project       :  de.scheller-sfc
 * CVS Ident     :  $Revision$, $Date$
 * Last Commiter :  $Author$
 *
 * Copyright (c) 2013 Scheller Systemtechnik GmbH
 *                    Poeler Strasse 85a, 23970 Wismar, Germany
 *
 *//////////////////////////////////////////////////////////////////////////////
package de.scheller.platform.common;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.WeakHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author kandzia, kunze
 */
public class MapUtils
{
	MapUtils() {}

	private static Logger logger;
	private static Logger logger() {
		if (logger!=null) return logger;
		return logger = Logger.getLogger(MapUtils.class.getPackage().getName());
	}


	////////////////////////////////////////////////////////////////////////////
	// lambda

	public static <ID,T> Map<ID,T> index(Iterable<? extends T> objects, Function<T,ID> id) {
		if (objects==null)
			return null;
		return index(objects,id,new LinkedHashMap());
	}

	public static <ID,T> Map<ID,T> index(Iterable<? extends T> objects, Function<T,ID> id, Map<ID,T> index) {
		if (objects==null)
			return null;
		for (T o : objects)
			index.put(id.apply(o),o);
		return index;
	}

	public static <G,V,C extends Collection<V>> Map<G,C> group(Iterable<? extends V> objects, Function<V,G> group, Class<? extends Collection> type) {
		if (objects==null)
			return null;
		return group(objects,group,type,new LinkedHashMap());
	}

	public static <G,V,C extends Collection<V>> Map<G,C> group(Iterable<? extends V> objects, Function<V,G> group, Class<? extends Collection> type, Map<G,C> groupMap) {
		if (objects==null)
			return null;
		for (V o : objects)
			groupT(groupMap,type,group.apply(o),o);
		return groupMap;
	}

	public static <G,K,V,M extends Map<K,V>> Map<G,M> group(M objects, BiFunction<K,V,G> group, Class<? extends Map> type) {
		if (objects==null)
			return null;
		return group(objects,group,type,new LinkedHashMap());
	}

	public static <G,K,V,M extends Map<K,V>> Map<G,M> group(M objects, BiFunction<K,V,G> group, Class<? extends Map> type, Map<G,M> groupMap) {
		if (objects==null)
			return null;
		for (Map.Entry<K,V> e : objects.entrySet()) {
			K k = e.getKey();
			V v = e.getValue();
			groupT(groupMap,type,group.apply(k,v),k,v);
		}
		return groupMap;
	}

	////////////////////////////////////////////////////////////////////////////
	// grouping

	public static <G,K,V,M extends Map<K,V>> void groupT(Map<G,M> groupMap,
			Class<? extends Map> type, G groupKey, K key, V value) {
		M m = groupMap.get(groupKey);
		if (m==null) groupMap.put(groupKey,m = (M)MapUtils.createMap(type));
		m.put(key,value);
	}

	public static <G,K,V,M extends Map<K,V>> void groupT(Map<G,M> groupMap,
			Class<? extends Map> type, G groupKey, Map<? extends K,? extends V> values) {
		M m = groupMap.get(groupKey);
		if (m==null) groupMap.put(groupKey,m = (M)MapUtils.createMap(type));
		m.putAll(values);
	}

	public static <G,K,V,M extends Map<K,V>> void groupRT(Map<G,M> groupMap,
			Class<? extends Map> type, G groupKey, Map<? extends K,? extends V> values) {
		M m = groupMap.get(groupKey);
		if (m==null) groupMap.put(groupKey,m = (M)MapUtils.createMap(type));
		for (Map.Entry<? extends K,? extends V> e : values.entrySet()) {
			if (e.getValue() instanceof Map) {
				groupRT((Map)m,type,e.getKey(),(Map)e.getValue());
			} else {
				m.putAll(values);
			}
		}
	}

	public static <G,V,C extends Collection<V>> void groupT(Map<G,C> groupMap,
			Class<? extends Collection> type, G groupKey, Collection<? extends V> values) {
		C c = groupMap.get(groupKey);
		if (c==null) groupMap.put(groupKey,c = (C)CollectionUtils.createCollection(type));
		c.addAll(values);
	}

	public static <G,V,C extends Collection<V>> void groupT(Map<G,C> groupMap,
			Class<? extends Collection> type, G groupKey, V value) {
		C c = groupMap.get(groupKey);
		if (c==null) groupMap.put(groupKey,c = (C)CollectionUtils.createCollection(type));
		c.add(value);
	}

	public static <G,V,C extends Collection<V>> void groupAll(Map<G,C> groupMap,
			Class<? extends Collection> type, G groupKey, Collection<? extends V> values) {
		C c = groupMap.get(groupKey);
		if (c==null) groupMap.put(groupKey,c = (C)CollectionUtils.createCollection(type));
		c.addAll(values);
	}

	public static <G,V> void group(Map<G,Collection<V>> groupMap,
			Class<? extends Collection> type, G groupKey, V value) {
		Collection<V> c = groupMap.get(groupKey);
		if (c==null) groupMap.put(groupKey,c = CollectionUtils.createCollection(type));
		c.add(value);
	}

	public static <G,C extends Collection> C ungroup(Map<G,C> groupMap,
			Class<? extends Collection> type) {
		C ungrouped = (C)CollectionUtils.createCollection(type);
		for (Collection c : groupMap.values())
			ungrouped.addAll(c);
		return ungrouped;
	}

	public static <G,V,C extends Collection<V>> void remove(Map<G,C> groupMap,
			G groupKey, V value, boolean removeEmptyGroup) {
		C c = groupMap.get(groupKey);
		if (c==null) return;
		c.remove(value);
		if (c.size()==0 && removeEmptyGroup)
			groupMap.remove(groupKey);
	}

	public static <G,V,C extends Collection<V>> void removeAll(Map<G,C> groupMap,
			G groupKey, Collection<? extends V> values, boolean removeEmptyGroup) {
		C c = groupMap.get(groupKey);
		if (c==null) return;
		c.removeAll(values);
		if (c.size()==0 && removeEmptyGroup)
			groupMap.remove(groupKey);
	}

	////////////////////////////////////////////////////////////////////////////

	public static <K,V> void groupMerge(Map<K,V> target, Map<? extends K,? extends V> source) {
		for (Map.Entry<? extends K,? extends V> e : source.entrySet()) {
			if (!target.containsKey(e.getKey()))
				target.put(e.getKey(),e.getValue());
			else {
				Object t = target.get(e.getKey());
				Object s = e.getValue();
				if (s instanceof Collection && t instanceof Collection)
					((Collection)t).addAll((Collection)s);
				else if (s instanceof Map && t instanceof Map)
					groupMerge((Map)t,(Map)s);
			}
		}
	}

	////////////////////////////////////////////////////////////////////////////
	// self map

	public static <T> Map<T,T> selfMap(Iterable<T> objects) {
		return selfMap(new HashMap(),objects);
	}

	public static <T> Map<T,T> selfMap(Map<T,T> m, Iterable<T> objects) {
		for (T o : objects) m.put(o,o);
		return m;
	}

	public static <T> T selfMapLookup(Map<T,T> selfMap, T o) {
		T self = selfMap.get(o);
		return self!=null ? self : o;
	}

	public static <T,C extends Collection<T>> C selfMap(Iterable<T> objects, C c) {
		Map selfMap = selfMap(objects);
		C selfMapped = (C)CollectionUtils.createCollection(c);
		for (T o : c) selfMapped.add((T)selfMapLookup(selfMap,o));
		return selfMapped;
	}

	public static <T> T merge(Map<T,T> m, T o) {
		T orig = m.remove(o);
		if (orig instanceof Mergeable)
			o = (T)((Mergeable)orig).merge(o);
		m.put(o,o);
		return o;
	}

	public static interface Mergeable<T> {
		T merge(T o);
	}

	////////////////////////////////////////////////////////////////////////////
	// map creation

	/** as LinkedHashMap */
	public static Map asMap(Object... keyValuePairs) {
		return putKeyValuePairs(new LinkedHashMap(),keyValuePairs);
	}

	public static Map asHashMap(Object... keyValuePairs) {
		return putKeyValuePairs(new HashMap(),keyValuePairs);
	}

	public static <M extends Map> M createMapT(M m) {
		return (M)createMap(m.getClass(),m.size());
	}

	public static <M extends Map> M createMapT(Class<M> c) {
		return (M)createMap(c,-1);
	}

	public static <M extends Map> M createMapT(Class<M> c, int size) {
		return (M)createMap(c,size);
	}

	public static Map createMap(Map m) {
		return createMap(m.getClass(),m.size());
	}

	public static Map createMap(Class<? extends Map> c) {
		return createMap(c,-1);
	}

	public static Map createMap(Class<? extends Map> c, int size) {
		if (SortedMap.class.isAssignableFrom(c))
			return new TreeMap();
		if (LinkedHashMap.class.isAssignableFrom(c))
			return size>=0 ? new LinkedHashMap(size) : new LinkedHashMap();
		if (IdentityHashMap.class.isAssignableFrom(c))
			return size>=0 ? new IdentityHashMap(size) : new IdentityHashMap();
		if (WeakHashMap.class.isAssignableFrom(c))
			return size>=0 ? new WeakHashMap(size) : new WeakHashMap();
		if (HashMap.class.isAssignableFrom(c))
			return size>=0 ? new HashMap(size) : new HashMap();
		if (logger().isLoggable(Level.WARNING))
			logger().warning("collection type not supported: "+c);
		if (Map.class.isAssignableFrom(c))
			return size>=0 ? new LinkedHashMap(size) : new LinkedHashMap();
		return size>=0 ? new LinkedHashMap(size) : new LinkedHashMap();
	}

	public static Map putKeyValuePairs(Map m, Object... keyValuePairs) {
		for (int n=keyValuePairs.length, i=0; i<n;)
			m.put(keyValuePairs[i++],i<keyValuePairs.length ? keyValuePairs[i++] : null);
		return m;
	}

	////////////////////////////////////////////////////////////////////////////
	// sorted map

	public static <K extends Object> K getFirstKey(Map<K,?> m) {
		if (m.isEmpty()) return null;
		if (m instanceof SortedMap)
			return ((SortedMap<K,?>)m).firstKey();
		return m.keySet().iterator().next();
	}

	public static <K extends Object> K getPrevious(SortedSet s, K key) {
		SortedSet<K> sub = s.headSet(key);
		return sub.isEmpty() ? null : sub.last();
	}

	public static <K extends Object> K getNext(SortedSet s, K key) {
		SortedSet<K> sub = s.tailSet(key);
		if (sub.isEmpty()) return null;
		Iterator<K> it = sub.iterator();
		K o = it.next(); // first
		Comparator c = s.comparator();
		if (c!=null ? c.compare(o,key)!=0 : ((Comparable)key).compareTo(o)!=0)
			return o;
		if (!it.hasNext()) return null;
		return it.next(); // first that is not equal key
	}

	public static <K extends Object> K getPrevious(SortedMap m, K key) {
		SortedMap<K,?> sub = m.headMap(key);
		return sub.isEmpty() ? null : sub.lastKey();
	}

	public static <K extends Object> K getNext(SortedMap m, K key) {
		SortedMap<K,?> sub = m.tailMap(key);
		if (sub.isEmpty()) return null;
		Iterator<K> it = sub.keySet().iterator();
		K o = it.next(); // first
		Comparator c = m.comparator();
		if (c!=null ? c.compare(o,key)!=0 : ((Comparable)key).compareTo(o)!=0)
			return o;
		if (!it.hasNext()) return null;
		return it.next(); // first that is not equal key
	}

	////////////////////////////////////////////////////////////////////////////
	// convenient access

	public static int getInt(Map m, String key, int def) {
		Object v = m.get(key);
		if (v==null) return def;
		if (v instanceof Number)
			return ((Number)v).intValue();
		try {
			return Integer.parseInt(v.toString());
		} catch (NumberFormatException ex) {
			return def;
		}
	}

	public static Integer getInteger(Map m, String key) {
		Object v = m.get(key);
		if (v==null) return null;
		if (v instanceof Integer)
			return (Integer)v;
		return Integer.valueOf(v.toString());
	}

	public static double getDouble(Map m, String key, double def) {
		Object v = m.get(key);
		if (v==null) return def;
		if (v instanceof Number)
			return ((Number)v).doubleValue();
		try {
			return Double.parseDouble(v.toString());
		} catch (NumberFormatException ex) {
			return def;
		}
	}

	public static Double getDouble(Map m, String key) {
		Object v = m.get(key);
		if (v==null) return null;
		if (v instanceof Double)
			return (Double)v;
		return Double.valueOf(v.toString());
	}

	public static boolean getBoolean(Map m, String key, boolean def) {
		Object v = m.get(key);
		if (v==null) return def;
		if (v instanceof Boolean)
			return (Boolean)v;
		return Boolean.parseBoolean(v.toString());
	}

	public static Boolean getBoolean(Map m, String key) {
		Object v = m.get(key);
		if (v==null) return null;
		if (v instanceof Boolean)
			return (Boolean)v;
		return Boolean.valueOf(v.toString());
	}

	public static <V> Map<String,V> getKeyStartsWith(Map<String,V> m, String startsWith) {
		Map<String,V> result = new HashMap();
		for (Map.Entry<String,V> e : m.entrySet()) {
			String key = e.getKey();
			if (key!=null && key.startsWith(startsWith))
				result.put(key,e.getValue());
		}
		return result;
	}
}
