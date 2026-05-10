/* /////////////////////////////////////////////////////////////////////////////
 *
 * Created       :  19.11.2013 12:56:44 by kunze
 * Project       :  de.scheller-sfc
 * CVS Ident     :  $Revision$, $Date$
 * Last Commiter :  $Author$
 *
 * Copyright (c) 2013 Scheller Systemtechnik GmbH
 *                    Poeler Strasse 85a, 23970 Wismar, Germany
 *
 *//////////////////////////////////////////////////////////////////////////////
package de.scheller.platform.common;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.NavigableSet;
import java.util.RandomAccess;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.Vector;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author kandzia, kunze
 */
public class CollectionUtils
{
	CollectionUtils() {}

	private static final Logger logger =
		Logger.getLogger(CollectionUtils.class.getPackage().getName());

	public static <T> T getFirstT(Iterable<? extends T> i) {
		return (T)get(i,false);
	}

	public static Object getFirst(Iterable i) {
		return get(i,false);
	}

	public static <T> T getLastT(Iterable<? extends T> i) {
		return (T)getLast(i);
	}

	public static Object getLast(Iterable i) {
		if (i==null) return null;
		if (i.getClass().isArray()) {
			int n = Array.getLength(i);
			if (n==0) return null;
			return Array.get(i,n-1);
		}
		if (i instanceof List) {
			List l = (List)i;
			int n = l.size();
			if (n==0) return null;
			if (l instanceof LinkedList)
				return ((LinkedList)l).getLast();
			if (l instanceof RandomAccess)
				return l.get(n-1);
			ListIterator it = l.listIterator(n);
			return it.hasPrevious() ? it.previous() : null;
		}
		Object o = null;
		for (Iterator it = i.iterator(); it.hasNext(); o=it.next()) ;
		return o;
	}

	public static <T> T getSingleT(Iterable<? extends T> i) {
		return (T)get(i,true);
	}

	public static Object getSingle(Iterable i) {
		return get(i,true);
	}

	private static Object get(Iterable i, boolean single) {
		if (i==null) return null;
		if (i.getClass().isArray()) {
			int n = Array.getLength(i);
			if (n==0) return null;
			if (n>1 && single) return null;
			return Array.get(i,0);
		}
		if (i instanceof Collection) {
			Collection c = (Collection)i;
			int n = c.size();
			if (n==0) return null;
			if (n>1 && single) return null;
		}
		if (i instanceof LinkedList)
			return ((LinkedList)i).getFirst();
		if (i instanceof List && i instanceof RandomAccess)
			return ((List)i).get(0);
		Iterator it = i.iterator();
		Object o = it.hasNext() ? it.next() : null;
		return !single || !it.hasNext() ? o : null;
	}

	public static Iterator iterator(Object i) {
		if (i==null)
			return Arrays.asList((Object)null).iterator();
		if (i instanceof Object[]) // for primitives it would be inefficient
			return Arrays.asList((Object[])i).iterator();
		if (i instanceof Iterable)
			return ((Iterable)i).iterator();
		if (i instanceof Iterator)
			return (Iterator)i;
		return Arrays.asList(i).iterator();
	}

	public static Iterable iterable(Object i) {
		if (i==null)
			return Arrays.asList((Object)null);
		if (i instanceof Object[]) // for primitives it would be inefficient
			return Arrays.asList((Object[])i);
		if (i instanceof Iterable)
			return ((Iterable)i);
		if (i instanceof Iterator)
			return new IteratorIterable((Iterator)i);
		return Arrays.asList(i);
	}

	public static Boolean isEmpty(Object i) {
		if (i==null) return null;
		if (i.getClass().isArray())
			return Array.getLength(i)==0;
		if (i instanceof Collection)
			return ((Collection)i).isEmpty();
		if (i instanceof Iterable)
			return !((Iterable)i).iterator().hasNext();
		if (i instanceof Iterator)
			return !((Iterator)i).hasNext();
		return null;
	}

	public static HashSet asHashSet(Object... o) {
		return fill(new HashSet(),FillFlatsObjectArray,o);
	}

	public static HashSet asHashSet(Iterator it) {
		return fill(new HashSet(),FillFlatsIterable,it);
	}

	public static HashSet asHashSet(Iterable it) {
		return fill(new HashSet(),FillFlatsIterable,it);
	}

	/** as LinkedHashSet */
	public static LinkedHashSet asSet(Object... o) {
		return fill(new LinkedHashSet(),FillFlatsObjectArray,o);
	}

	/** as LinkedHashSet */
	public static LinkedHashSet asSet(Iterator it) {
		return fill(new LinkedHashSet(),FillFlatsIterable,it);
	}

	/** as LinkedHashSet */
	public static LinkedHashSet asSet(Iterable it) {
		return fill(new LinkedHashSet(),FillFlatsIterable,it);
	}

	/**
	 * {@link Arrays#asList(Object...)} funktioniert nicht mit Arrays von
	 * primitiven Typen.
	 * @see Arrays#asList(Object...)
	 */
	public static List asList(Object o) {
		if (o==null) return null;
		Class c = o.getClass();
		Class t = c.getComponentType();
		if (c.isArray() && t!=null && t.isPrimitive()) {
			int l = Array.getLength(o);
			List r = new ArrayList(l);
			for (int i=0; i<l; i++)
				r.add(Array.get(o,i));
			return r;
		}
		return Arrays.asList((Object[])o);
	}

	public static <T> T toArray(List l, T array) {
		if (l==null) return null;
		if (array==null) return (T)l.toArray();
		Class c = array.getClass();
		if (!c.isArray()) return null;
		Class t = c.getComponentType();
		if (t!=null && t.isPrimitive()) {
			int size = l.size();
			int len = Array.getLength(array);
			array = len==size ? array : (T)Array.newInstance(t,size);
			for (int i=0; i<size; i++)
				Array.set(array,i,l.get(i));
			return array;
		}
		return (T)l.toArray((Object[])array);
	}

	public static ArrayList asArrayList(Iterator it) {
		return fill(new ArrayList(),0,it);
	}

	public static ArrayList asArrayList(Iterable it) {
		return fill(new ArrayList(),0,it);
	}

	public static LinkedList asLinkedList(Iterator it) {
		return fill(new LinkedList(),0,it);
	}

	public static LinkedList asLinkedList(Iterable it) {
		return fill(new LinkedList(),0,it);
	}

	public static Object[] asArray(Iterable it) {
		return asLinkedList(it).toArray();
	}

	public static <C extends Collection> C cloneCollectionT(C c) {
		return (C)fill(createCollection(c.getClass(),c.size()),FillFlatsCollection,c);
	}

	public static Collection cloneCollection(Collection c) {
		return fill(createCollection(c.getClass(),c.size()),FillFlatsCollection,c);
	}

	public static <C extends Collection> C createCollectionT(C c) {
		return (C)createCollection(c.getClass(),c.size());
	}

	public static <C extends Collection> C createCollectionT(Class<C> c) {
		return (C)createCollection(c,-1);
	}

	public static <C extends Collection> C createCollectionT(Class<C> c, int size) {
		return (C)createCollection(c,size);
	}

	public static Collection createCollection(Collection c) {
		return createCollection(c.getClass(),c.size());
	}

	public static Collection createCollection(Class<? extends Collection> c) {
		return createCollection(c,-1);
	}

	public static Collection createCollection(Class<? extends Collection> c, int size) {
		if (Vector.class.isAssignableFrom(c))
			return size>=0 ? new Vector(size) : new Vector();
		if (ArrayList.class.isAssignableFrom(c))
			return size>=0 ? new ArrayList(size) : new ArrayList();
		if (LinkedList.class.isAssignableFrom(c))
			return new LinkedList();
		if (TreeSet.class.isAssignableFrom(c))
			return new TreeSet();
		if (ConcurrentSkipListSet.class.isAssignableFrom(c))
			return new ConcurrentSkipListSet();
		if (LinkedHashSet.class.isAssignableFrom(c))
			return size>=0 ? new LinkedHashSet(size) : new LinkedHashSet();
		if (HashSet.class.isAssignableFrom(c))
			return size>=0 ? new HashSet(size) : new HashSet();
//		if (IdentityHashSet.class.isAssignableFrom(c))
//			return size>=0 ? new IdentityHashSet(size) : new IdentityHashSet();
		if (logger.isLoggable(Level.WARNING))
			logger.warning("collection type not supported: "+c);
		if (List.class.isAssignableFrom(c))
			return size>=0 ? new ArrayList(size) : new ArrayList();
		if (Set.class.isAssignableFrom(c))
			return size>=0 ? new LinkedHashSet(size) : new LinkedHashSet();
		return size>=0 ? new ArrayList(size) : new ArrayList();
	}

	public static int FillFlatsCollection = 1;
	public static int FillFlatsObjectArray = 2;
	public static int FillFlatsIterable = 4;
	public static int FillFlatsRecursive = 8;
	public static int FillFlatsAll = 1|2|4|8;

	public static <C extends Collection> C fill(C c, int flags, Object... o) {
		if (c==null) return null;
		boolean flatC = (flags & FillFlatsCollection)!=0;
		boolean flatA = (flags & FillFlatsObjectArray)!=0;
		boolean flatI = (flags & FillFlatsIterable)!=0;
		boolean flatR = (flags & FillFlatsRecursive)!=0;
		for (Object e : iterable(o.length==1 ? o[0] : o)) {
			if (flatR) {
				/**/ if (flatC && e instanceof Collection) fill(c,flags,e);
				else if (flatA && e instanceof Object[]) fill(c,flags,e);
				else if (flatI && e instanceof Iterable) fill(c,flags,e);
				else if (flatI && e instanceof Iterator) fill(c,flags,e);
				else c.add(e);
			} else {
				/**/ if (flatC && e instanceof Collection) c.addAll((Collection)e);
				else if (flatA && e instanceof Object[]) c.addAll(Arrays.asList((Object[])e));
				else if (flatI && e instanceof Iterable) for (Object ee : (Iterable)e) c.add(ee);
				else if (flatI && e instanceof Iterator) while (((Iterator)e).hasNext()) c.add(((Iterator)e).next());
				else c.add(e);
			}
		}
		return c;
	}

	public static class IteratorIterable<T> implements Iterable<T>
	{
		private final Iterator<T> it;

		public IteratorIterable(Iterator<T> it) {
			this.it = it;
		}

		@Override
		public Iterator<T> iterator() {
			return it;
		}
	}

	/**
	 * Moves a non-contiguous selection of elements inside a list by offset.
	 *
	 * @param l list of elements to work on
	 * @param m set of elements to move
	 * @param o offset/distance to move
	 * @return diff/distance that would fail (given distance - fail = would work then)
	 */
	public static int move(List l, Set m, int o) {
		if (o==0) return 0;
		if (o>0) for (int n=l.size()-1, i=n; i>=0; i--) { // o>0
			if (!m.contains(l.get(i))) continue;
			if (i+o>n) return (i+o)-n;
			Collections.rotate(l.subList(i,i+o+1),o);
		} else for (int n=l.size()-1, i=0; i<=n; i++) { // o<0
			if (!m.contains(l.get(i))) continue;
			if (i+o<0) return (i+o)-0;
			Collections.rotate(l.subList(i+o,i+1),o);
		}
		return 0;
	}

	public static <C extends Collection> C unmodifiableCollection(C c) {
		if (c instanceof List)
			return (C)Collections.unmodifiableList((List)c);
		if (c instanceof NavigableSet)
			return (C)Collections.unmodifiableNavigableSet((NavigableSet)c);
		if (c instanceof SortedSet)
			return (C)Collections.unmodifiableSortedSet((SortedSet)c);
		if (c instanceof Set)
			return (C)Collections.unmodifiableSet((Set)c);
		return (C)Collections.unmodifiableCollection(c);
	}
}
