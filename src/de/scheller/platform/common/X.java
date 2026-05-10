package de.scheller.platform.common;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * TripleX. xXx -> Xxx.
 * @author kandzia
 */
public class X
{
	public static <K,V> MX<K,V> x(Map<K,V> m) { return new MX(m); }
	public static <T,C extends Collection<T>> CX<T,C> x(C c) { return new CX(c); }
	public static SX x(String s) { return new SX(s); }

	public static <K,V> MX<K,V> map() { return new MX(new LinkedHashMap()); }
	public static <T> CX<T,Set<T>> set() { return new CX(new LinkedHashSet()); }
	public static <T> CX<T,List<T>> list() { return new CX(new ArrayList()); }
	public static <K,V> MX<K,V> map(Object... kv) { return X.<K,V>map().put(kv); }
	public static <K,V> MX<K,V> map(K k, V v) { return X.<K,V>map().put(k,v); }
	public static <T> MX<T,T> mapT(T... kv) { return X.<T,T>map().put(kv); }
	public static <T> CX<T,Set<T>> set(T... o) { return X.<T>set().add(o); }
	public static <T> CX<T,List<T>> list(T... o) { return X.<T>list().add(o); }

	public static <K,V> Map<K,V> mapx() { return new LinkedHashMap(); }
	public static <T> Set<T> setx() { return new LinkedHashSet(); }
	public static <T> List<T> listx() { return new ArrayList(); }
	public static <K,V> Map<K,V> mapx(Object... kv) { return X.<K,V>map().put(kv).x(); }
	public static <K,V> Map<K,V> mapx(K k, V v) { return X.<K,V>map().put(k,v).x(); }
	public static <T> Map<T,T> mapxT(T... kv) { return X.<T,T>map().put(kv).x(); }
	public static <T> Set<T> setx(T... o) { return X.<T>set().add(o).x(); }
	public static <T> List<T> listx(T... o) { return X.<T>list().add(o).x(); }

	public static class MU extends MapUtils {}
	public static class MX<K,V> {
		Map m;
		MX(Map m) { this.m = m; }
		// result
		public Map<K,V> x() { return m; }
		public Map<K,V> map() { return m; }
		// MapUtils
		public MX<K,V> put(Object... kvPairs) { MU.putKeyValuePairs(m,kvPairs); return this; }
		public MX<K,V> putAll(Map<K,V>... maps) { for (Map<K,V> kv : maps) m.putAll(kv); return this; }
		public MX<K,V> putAll(Iterable<Map<K,V>> maps) { for (Map<K,V> kv : maps) m.putAll(kv); return this; }
		public <G,C extends Collection> MX<G,C> group(Class<C> t, G g, Object v) { MU.groupT(m,t,g,v); return (MX<G,C>)this; }
		public <G,C extends Collection> MX<G,C> group(Class<C> t, G g, Collection v) { MU.groupAll(m,t,g,v); return (MX<G,C>)this; }
		public <G,C extends Collection,T> MX<G,C> group(Class<C> t, Function<T,G> g, Collection<T> v) { MU.group(v,g,t,m); return (MX<G,C>)this; }
		public <G,M extends Map> MX<G,M> group(Class<M> t, G g, Object k, Object v) { MU.groupT(m,t,g,k,v); return (MX<G,M>)this; }
		public <G,M extends Map> MX<G,M> group(Class<M> t, G g, Map kv) { MU.groupT(m,t,g,kv); return (MX<G,M>)this; }
		public <G,M extends Map> MX<G,M> group(Class<M> t, BiFunction<?,?,G> g, Map kv) { MU.group(kv,g,t,m); return (MX<G,M>)this; }
		public <G,M extends Map> MX<G,M> group(Class<M> t, BiFunction<?,?,G> g) { return X.map().group(t,g,m); }
		public <G,M extends Map> MX<G,M> group(Class t, G g, Object... v) {
			if (Collection.class.isAssignableFrom(t)) MU.groupAll(m,t,g,Arrays.asList(v));
			else if (Map.class.isAssignableFrom(t)) MU.groupT(m,(Class<Map>)t,g,X.map().put(v).map());
			return (MX<G,M>)this;
		}
		public <ID,T> MX<ID,T> index(Iterable<T> it, Function<T,ID> id) { MU.index(it,id,m); return (MX<ID,T>)this; }
		public MX<K,V> keyStartsWith(String s) { return X.x(MU.getKeyStartsWith(m,s)); }
		public K firstKey() { return (K)MU.getFirstKey(m); }
		public K nextKey(Object k) { return (K)MU.getNext((SortedMap)m,k); }
		public K prevKey(Object k) { return (K)MU.getPrevious((SortedMap)m,k); }
		public CX<K,Set<K>> keys() { return X.x(new LinkedHashSet(m.keySet())); }
		public CX<V,List<V>> values() { return X.x(new ArrayList(m.values())); }
		public <E extends Map.Entry<K,V>> CX<E,Set<E>> entries() { return X.x(new LinkedHashSet(m.entrySet())); }
		public Object get(Object... access) { return X.get(m,access); }
		public <T> T get(Class<T> type, Object... access) { return get(type,LinkedHashMap.class,access); }
		public <T> T get(Class<T> type, Class<? extends Map> mt, Object... access) {
			Map m = this.m; for (int i=0; i<access.length-1; i++) m = X.get(m,mt,access[i]);
			return (T)X.get(m,type,access[access.length-1]); }
		// TODO
	}

	public static class CU extends CollectionUtils {}
	public static class CX<T,CT extends Collection<T>> {
		CT c;
		CX(CT c) { this.c = c; }
		// result
		public CT x() { return c instanceof Set ? (CT)set() : (CT)list(); }
		public Set<T> set() { return c instanceof Set ? (Set)c : new LinkedHashSet(c); }
		public List<T> list() { return c instanceof List ? (List)c : new ArrayList(c); }
		public CX<T,Set<T>> setx() { return X.x(set()); }
		public CX<T,List<T>> listx() { return X.x(list()); }
		public Object[] array() { return list().toArray(); }
		public T[] array(Class<T> t) { return list().toArray((T[])Array.newInstance(t,0)); }
		public CX<T,List<T>> reverse() { List<T> l = list(); Collections.reverse(l); return X.x(l); }
		public CXS<T,CT> stream() { return new CXS(c); }
		public <K,V> MX<K,V> mapx() { return X.x(map()); }
		public <K,V> Map<K,V> map() {
			List l = list(); // fixed: first() consumed the stream, so map->list->collect failed
			if (CU.getFirst(l) instanceof Map.Entry)
				return X.x((List<Map.Entry<K,V>>)l).map(Map.Entry::getKey,Map.Entry::getValue);
			return X.mapx(l.toArray()); }
		public <K,V> MX<K,V> mapx(Function<T,K> key, Function<T,V> value) { return X.x(map(key,value)); }
		public <K,V> Map<K,V> map(Function<T,K> key, Function<T,V> value) {
			return list().stream().collect(Collectors.toMap(key,value,(u,v)->v,LinkedHashMap::new)); }
		// CollectionUtils
		public CX<T,CT> add(T o) { c.add(o); return this; }
		public CX<T,CT> add(T... o) { c.addAll(Arrays.asList(o)); return this; }
		public CX<T,CT> addAll(Collection<? extends T> o) { c.addAll(o); return this; }
		public CX<T,CT> addAll(Iterable<? extends T> o) { for (T e : o) c.add(e); return this; }
		public CX<T,CT> removeNull() { return c instanceof Set ? remove((T)null) : filter(o -> o!=null); }
		public CX<T,CT> remove(T o) { c.remove(o); return this; }
		public CX<T,CT> remove(T... o) { c.removeAll(Arrays.asList(o)); return this; }
		public CX<T,CT> removeAll(Object o) { c.removeAll(CU.asList(o)); return this; }
		public CX<T,CT> removeAll(Collection<? extends T> o) { c.removeAll(o); return this; }
		public CX<T,CT> removeAll(Iterable<? extends T> o) { for (T e : o) c.remove(e); return this; }
		public <G,C extends Collection> MX<G,C> group(Class<C> t, Function<T,G> g) { return X.map().group(t,g,c); }
		public <ID> MX<ID,T> index(Function<T,ID> id) { return X.map().index(c,id); }
		public MX<T,T> selfMap() { return X.x(MU.selfMap(new HashMap(),c)); }
		public <R> CX<R,List<R>> flat() {
			List<R> l = create(List.class); CU.fill(l,CU.FillFlatsAll&~CU.FillFlatsRecursive,x()); return X.x(l); }
		public T first() { return CU.getFirstT(c); }
		public T next(T o) { return MU.getNext((SortedSet)c,o); }
		public T prev(T o) { return MU.getPrevious((SortedSet)c,o); }
		public T last() { return CU.getLastT(c); }
		public <U extends Comparable> CX<T,CT> sort(Object... c) {
			return sort(X.Comparators.cmp(c)); }
		public <U extends Comparable, F extends Function<T,U>> CX<T,CT> sort(F c, Object... more) {
			return sort(X.Comparators.cmp(c,more)); }
		public <U extends Comparable, F extends Function<T,U>> CX<T,CT> sort(F c1, F c2, Object... more) {
			return sort(X.Comparators.cmp(c1,c2,more)); }
		public <U extends Comparable, F extends Function<T,U>> CX<T,CT> sort(F c1, F c2, F c3, Object... more) {
			return sort(X.Comparators.cmp(c1,c2,c3,more)); }
		public CX<T,CT> sort(Comparator<T> c) {
			if (this.c instanceof List==false) return X.x(new TreeSet(c)).addAll(this.c);
			Collections.sort((List)this.c,c); return this;
		}
		public CX<T,CT> forEach(Consumer<T> c) {
			Object[] a=array(); for (int n=a.length,i=0; i<n; i++) c.accept((T)a[i]); return this; }
		public CX<T,CT> forEach(BiConsumer<T,Integer> c) {
			Object[] a=array(); for (int n=a.length,i=0; i<n; i++) c.accept((T)a[i],i); return this; }
		public CX<T,CT> forEach(TriConsumer<T,Integer,Object[]> c) {
			Object[] a=array(); for (int n=a.length,i=0; i<n; i++) c.accept((T)a[i],i,a); return this; }
		public <R> R reduce(R init, BiFunction<R,T,R> c) {
			Object[] a=array(); R r=init; for (int n=a.length,i=0; i<n; i++) r=c.apply(r,(T)a[i]); return r; }
		public <R> R reduce(R init, TriFunction<R,T,Integer,R> c) {
			Object[] a=array(); R r=init; for (int n=a.length,i=0; i<n; i++) r=c.apply(r,(T)a[i],i); return r; }
		public <R> R reduce(R init, QuadFunction<R,T,Integer,Object[],R> c) {
			Object[] a=array(); R r=init; for (int n=a.length,i=0; i<n; i++) r=c.apply(r,(T)a[i],i,a); return r; }
		// stream
		public CX<T,CT> filter(Predicate<T> p) { return stream().filter(p); }
		public <R> CX<R,Collection<R>> map(Function<T,R> m) { return stream().map(m); }
		public boolean anyMatch(Predicate<T> p) { return stream().anyMatch(p); }
		public boolean allMatch(Predicate<T> p) { return stream().allMatch(p); }
		public boolean noneMatch(Predicate<T> p) { return stream().noneMatch(p); }
		public <R> CX<R,Collection<R>> cast() { return (CX<R,Collection<R>>)this; }
		// TODO
	}
	public static class CXS<T,CT extends Collection<T>> extends CX<T,CT> { // stream mode
		Stream<T> s; // type-compatible stream! if type changes, construct new CX!
		CXS(CT c) { super(c); this.s = c.stream(); }
		CXS(Stream<T> s) { super(null); this.s = s; }
		// result
		@Override public Set<T> set() { return new LinkedHashSet(list()); }
		@Override public List<T> list() { return s.collect(Collectors.toList()); }
		@Override public CXS<T,CT> stream() { return this; }
		// "CollectionUtils"
		@Override public CXS<T,CT> add(T o) { throw new IllegalStateException("not possible in stream mode"); }
		@Override public CXS<T,CT> add(T... o) { throw new IllegalStateException("not possible in stream mode"); }
		@Override public CXS<T,CT> addAll(Collection<? extends T> o) { throw new IllegalStateException("not possible in stream mode"); }
		@Override public CXS<T,CT> addAll(Iterable<? extends T> o) { throw new IllegalStateException("not possible in stream mode"); }
		@Override public CX<T,CT> remove(T o) { return removeAll(Arrays.asList(o)); }
		@Override public CX<T,CT> remove(T... o) { return removeAll(Arrays.asList(o)); }
		@Override public CX<T,CT> removeAll(Collection<? extends T> o) { return remove(new HashSet(o)); }
		@Override public CX<T,CT> removeAll(Iterable<? extends T> o) { return remove(CU.asSet(o)); }
		private CX<T,CT> remove(Set<T> o) { return filter(e -> !o.contains(e)); }
		@Override public <G,C extends Collection> MX<G,C> group(Class<C> t, Function<T,G> g) { return X.map().group(t,g,list()); }
		@Override public <ID> MX<ID,T> index(Function<T,ID> id) { return X.map().index(list(),id); }
		@Override public MX<T,T> selfMap() { return X.x(MU.selfMap(new HashMap(),list())); }
		@Override public T first() { return s.findFirst().orElse(null); }
		@Override public T last() { return CU.getLastT(list()); }
		// stream
		@Override public CXS<T,CT> sort(Comparator<T> c) { s = s.sorted(c); return this; }
		@Override public CXS<T,CT> filter(Predicate<T> p) { s = s.filter(p); return this; }
		@Override public <R> CXS<R,Collection<R>> map(Function<T,R> m) { return new CXS(s.map(m)); }
		@Override public boolean anyMatch(Predicate<T> p) { return s.anyMatch(p); }
		@Override public boolean allMatch(Predicate<T> p) { return s.allMatch(p); }
		@Override public boolean noneMatch(Predicate<T> p) { return s.noneMatch(p); }
		// TODO
	}

	public static class SX {
		String s;
		SX(String s) { this.s = s; }
		// TODO
	}

	public interface Converter<T> extends Function<Object,T> {}
	public interface Converters<T> extends Function<Class<T>,Converter<T>> {}

	public static <T> T get(Converters<T> as, Class<T> type, Object o, Object... access) {
		o = get(o,access);
		if (o==null) return null;
		if (type.isInstance(o)) return (T)o;
		if (as==null) return (T)get(o,access);
		Function<Object,T> converter = as.apply(type);
		if (converter==null) return (T)get(o,access);
		return converter.apply(get(o,access));
	}

	public static Object get(Object o, Object... access) {
		for (Object a : access)
			if (a instanceof Function) o = ((Function)a).apply(o);
			else if (o instanceof List) o = ((List)o).get((Integer)a);
			else o = o instanceof Map ? ((Map)o).get(a) : o;
		return o;
	}
	public static <K,V> V get(Map<K,V> m, Class<V> type, K key) {
		V v = m.get(key);
		if (v==null) m.put(key,v = create(type));
		return v;
	}
	public static <T> T create(Class<T> type) {
		if (Map.class.isAssignableFrom(type))
			return (T)MU.createMap((Class<? extends Map>)type);
		if (Collection.class.isAssignableFrom(type))
			return (T)CU.createCollection((Class<? extends Collection>)type);
		return null;
	}

	public static class Comparators {
		public static <T,U extends Comparable, F extends Function<T,U>> Comparator cmp(F c, Object... more) {
			Object[] args = new Object[1+more.length];
			System.arraycopy(more,0,args,1,more.length);
			args[0] = c;
			return cmp(args);
		}
		public static <T,U extends Comparable, F extends Function<T,U>> Comparator cmp(F c1, F c2, Object... more) {
			Object[] args = new Object[2+more.length];
			System.arraycopy(more,0,args,2,more.length);
			args[0] = c1; args[1] = c2;
			return cmp(args);
		}
		public static <T,U extends Comparable, F extends Function<T,U>> Comparator cmp(F c1, F c2, F c3, Object... more) {
			Object[] args = new Object[3+more.length];
			System.arraycopy(more,0,args,3,more.length);
			args[0] = c1; args[1] = c2; args[2] = c3;
			return cmp(args);
		}
		public static Comparator cmp(Object... c) {
			Comparator r = null;
			for (Object o : c)
				if (r==null) r = cmp(o);
				else if (o instanceof Boolean && (Boolean)o) r = r.reversed();
				else if (o instanceof Boolean && !(Boolean)o); // keep comparator
				else if (o instanceof Double && Double.doubleToLongBits(((Double)o).doubleValue())==Double.doubleToLongBits(-0d)) r = Comparator.nullsFirst(r);
				else if (o instanceof Double && Double.doubleToLongBits(((Double)o).doubleValue())==Double.doubleToLongBits(+0d)) r = Comparator.nullsLast(r);
				else if (o instanceof Number && ((Number)o).intValue()<0) r = r.reversed();
				else if (o instanceof Number); // keep comparator
				else r = r.thenComparing(cmp(o));
			return r;
		}
		public static <T,U extends Comparable> Comparator<T> cmp(Object c) {
			if (c instanceof Comparator) return (Comparator<T>)c;
			if (c instanceof Function) return Comparator.comparing((Function<T,U>)c);
			if (c instanceof ToIntFunction) return Comparator.comparingInt((ToIntFunction<T>)c);
			if (c instanceof ToLongFunction) return Comparator.comparingLong((ToLongFunction<T>)c);
			if (c instanceof ToDoubleFunction) return Comparator.comparingDouble((ToDoubleFunction<T>)c);
			return null;
		}
	}

	@FunctionalInterface
	public interface XFunction<T,R> {
		R apply(T t) throws Exception;
	}
	public static <T,R> Function<T,R> xx(XFunction<T,R> throwingFunction) {
		return t -> {
			try { return throwingFunction.apply(t); }
			catch (Exception ex) { throw new RuntimeException(ex); }
		};
	}
	@FunctionalInterface
	public interface XConsumer<T> {
		void apply(T t) throws Exception;
	}
	public static <T> Consumer<T> xx(XConsumer<T> throwingConsumer) {
		return t -> {
			try { throwingConsumer.apply(t); }
			catch (Exception ex) { throw new RuntimeException(ex); }
		};
	}

	@FunctionalInterface public interface TriConsumer<A,B,C> { void accept(A a, B b, C c); }
	@FunctionalInterface public interface TriFunction<A,B,C,R> { R apply(A a, B b, C c); }
	@FunctionalInterface public interface QuadFunction<A,B,C,D,R> { R apply(A a, B b, C c, D d); }
}
