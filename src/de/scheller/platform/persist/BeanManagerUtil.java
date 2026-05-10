/* /////////////////////////////////////////////////////////////////////////////
 *
 * Created       :  14.02.2012 16:04:05 by kandzia
 * Project       :  EIP2-ModelPersistence-Server
 * CVS Ident     :  $Revision$, $Date$
 * Last Commiter :  $Author$
 *
 * Copyright (c) 2012 Scheller Systemtechnik GmbH
 *                    Poeler Strasse 85a, 23970 Wismar, Germany
 *
 *//////////////////////////////////////////////////////////////////////////////
package de.scheller.platform.persist;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.persistence.NoResultException;
import javax.persistence.NonUniqueResultException;
import javax.persistence.Query;

import de.scheller.common.HasId;
import de.scheller.common.HasVersion;
import de.scheller.platform.persist.util.ReflectHelper;
import de.scheller.transferobject.ITransferObject;

/**
 * Unterstützung bei Datenanfragen.
 * Es taucht immer wieder <tt>Object.. idAndValueAndExpr</tt> auf,
 * das folgendermaßen aufgebaut ist:
 * <ul>
 * <li> Query-Parameter:
 * <ol>
 * <li> Index - 0..n, <tt>null</tt> bricht die Parameterliste ab
 * <li> Argument - Parameterwert
 * <li> JPAQL-Bedingung - für Parameterwert <tt>!= null</tt>
 * <li> JPAQL-Bedingung - für Parameterwert <tt>== null || false</tt><br>
 *      (<tt>true/false</tt> sind erlaubt, <tt>null</tt> bedeutet: 3. übernehmen)
 * </ol>
 * <li> Query-Pattern: (optional)<br>{@link MessageFormat}-Pattern,
 *      das die JPAQL-Bedingungen zur Query zusammensetzt.
 *      Im Pattern werden die Index-Nummern der Bedingungen verwendet.
 *      Wird das Query-Pattern nicht angegeben, wird automatisch eines generiert,
 *      das alle Bedingungen mit <tt>and</tt> verknüpft.
 *      <br><br>
 * <li> QueryBuilder-Parameter: (optional)<br>Abwechselnd Schlüssel und Wert:
 * <ul>
 * <li> "limit" - Begrenzt die Anzahl der Ergebniszeilen. ({@link Query#setMaxResults(int)})
 * <li> "offset" - Ausgabe der Ergebniszeilen ab bestimmter Zeile. ({@link Query#setFirstResult(int)})
 * </ul>
 * </ul>
 *
 * @author kandzia
 */
public class BeanManagerUtil
{
	/** <code>update X x set ...</code>, see {@link BeanManagerUtil} */
	public static Query update(IServerBeanManager m,
			Class entityType, Object... idAndValueAndExpr) {
		String q = "update "+m.getEntityName(entityType)+" x set";
		return query(m,q,idAndValueAndExpr);
	}

	/** <code>update X x set whatToSet</code>, see {@link BeanManagerUtil} */
	public static Query update(IServerBeanManager m,
			Class entityType, String whatToSet, Object... idAndValueAndExpr) {
		String q = "update "+m.getEntityName(entityType)+" x set "+whatToSet;
		return query(m,q,idAndValueAndExpr);
	}

	/** <code>delete from X x </code>, see {@link BeanManagerUtil} */
	public static Query delete(IServerBeanManager m,
			Class entityType, Object... idAndValueAndExpr) {
		String q = "delete from "+m.getEntityName(entityType)+" x";
		return query(m,q,idAndValueAndExpr);
	}

	/** <code>from X x ...</code>, see {@link BeanManagerUtil} */
	public static Query select(IServerBeanManager m,
			Class entityType, Object... idAndValueAndExpr) {
		String q = "from "+m.getEntityName(entityType)+" x";
		return query(m,q,idAndValueAndExpr);
	}

	/** <code>select ... from X x ...</code>, see {@link BeanManagerUtil} */
	public static Query select(IServerBeanManager m, String fields,
			Class entityType, Object... idAndValueAndExpr) {
		String q = "select "+fields+" from "+m.getEntityName(entityType)+" x";
		return query(m,q,idAndValueAndExpr);
	}

	/** <code>from X x ...</code>, see {@link BeanManagerUtil} */
	public static <T> List<T> getAll(IServerBeanManager m,
			Class<T> entityType, Object... idAndValueAndExpr) {
		String q = "from "+m.getEntityName(entityType)+" x";
		return query(m,q,idAndValueAndExpr).getResultList();
	}

	/** <code>select ... from X x ...</code>, see {@link BeanManagerUtil} */
	public static List getAll(IServerBeanManager m, String fields,
			Class entityType, Object... idAndValueAndExpr) {
		String q = "select "+fields+" from "+m.getEntityName(entityType)+" x";
		return query(m,q,idAndValueAndExpr).getResultList();
	}

	/** <code>from X x ...</code>, see {@link BeanManagerUtil} */
	public static <T> T getSingle(IServerBeanManager m,
			Class<T> entityType, Object... idAndValueAndExpr) {
		String q = "from "+m.getEntityName(entityType)+" x";
		// getSingleResult() could throw NoResultException|NonUniqueResultException|others
		// this sets (even catched) transaction state to rollbackOnly
		// return query(m,q,idAndValueAndExpr).getSingleResult();
		Query query = query(m,q,idAndValueAndExpr);
		if (query.getMaxResults()<=0) // respect limit from query
			query.setMaxResults(2);
		List<T> result = query.getResultList();
		if (result.size()==0)
			return null;
		if (result.size()>1)
			throw new NonUniqueResultException();
		return result.get(0);
	}

	/** <code>select ... from X x ...</code>, see {@link BeanManagerUtil} */
	public static Object getSingle(IServerBeanManager m, String fields,
			Class entityType, Object... idAndValueAndExpr) {
		String q = "select "+fields+" from "+m.getEntityName(entityType)+" x";
		try {
			return query(m,q,idAndValueAndExpr).getSingleResult();
		} catch (NoResultException ex) {
			return null;
		}
	}

	/** <code>select count(*) from X x ...</code>, see {@link BeanManagerUtil} */
	public static long count(IServerBeanManager m,
			Class entityType, Object... idAndValueAndExpr) {
		String q = "select count(x."+m.getIdentifierName(entityType)+")" +
				" from "+m.getEntityName(entityType)+" x";
		return (Long)query(m,q,idAndValueAndExpr).getSingleResult();
	}

	public static Query query(IServerBeanManager m,
			String prefix, Object... idAndValueAndExpr) {
		Map<String,Object> values = parseArguments(idAndValueAndExpr);
		String qs = (String)values.remove(null);
		int i = (Integer)values.remove("!");
//		System.out.println(qs);
//		System.out.println(values);
		Query q = m.createQuery(prefix + " " + qs);
		for (Map.Entry<String,Object> e : values.entrySet())
			q.setParameter(e.getKey(),pad(m,e.getValue()));
		while (i<idAndValueAndExpr.length) {
			String key = String.valueOf(idAndValueAndExpr[i++]);
			Object value = idAndValueAndExpr[i++];
			/**/ if ("limit".equals(key))
				q.setMaxResults(((Number)value).intValue());
			else if ("offset".equals(key))
				q.setFirstResult(((Number)value).intValue());
			else if ("cache".equals(key) && value instanceof Boolean)
				q.setHint("org.hibernate.cacheable",((Boolean)value).booleanValue());
			else if ("cache".equals(key) && value instanceof String) {
				q.setHint("org.hibernate.cacheable",true);
				q.setHint("org.hibernate.cacheRegion",value);
			}
			else q.setHint(key,value);
		}
		return q;
	}

	/** @see https://stackoverflow.com/questions/31557076#40017486 */
	private static final TreeSet<Integer> padLimits = new TreeSet();
	private static final Map<Class,Object> padValues = new HashMap();
	static {
		padLimits.addAll(Arrays.asList(1, 10, 50, 100, 200, 500, 1000, 2000, 5000, 10000, 50000));
		padValues.put(String.class,"#$!&");
		padValues.put(Integer.class,Integer.MIN_VALUE);
		padValues.put(Long.class,(long)Integer.MIN_VALUE);
	}

	public static Object pad(IServerBeanManager m, Object value) {
		if (value instanceof Collection==false)
			return value;
		return pad(m,(Collection)value);
	}

	public static Collection pad(IServerBeanManager m, Collection c) {
		int size = c.size();
		if (size==0) return c;

		SortedSet<Integer> tail = padLimits.tailSet(size);
		int targetSize;
		if (tail.size()>0) {
			targetSize = tail.first();
		} else {
			int max = padLimits.last();
			targetSize = max*(size/max)+1;
		}
		if (size==targetSize) return c;

		Object padValue = padValue(m,c.iterator().next());
		if (padValue==null) return c;

		Collection padded = new ArrayList(targetSize);
		padded.addAll(c);
		for (int i=size; i<targetSize; i++)
			padded.add(padValue);
		return padded;
	}

	private static Object padValue(IServerBeanManager m, Object ref) {
		if (ref==null) return null;
		Class c = ref.getClass();
		if (c.isEnum()) return null;

		Object v = padValues.get(c);
		if (v!=null || padValues.containsKey(c))
			return v;

		if (!ITransferObject.class.isAssignableFrom(c))
			return null;

		ref = m.getUnmanaged(ref);
		c = ref.getClass();
		v = padValues.get(c);
		if (v!=null || padValues.containsKey(c))
			return v;

		v = m.createInstance(c);
		if (v==null)
			try {
				v = c.newInstance();
			} catch (Exception ignore) {}
		if (v instanceof HasId) {
			HasId o = (HasId)v;
			Object id = o.getId();
			if (id!=null)
				o.setId((Serializable)padValue(m,id));
			else ; // TODO type for id field + padValue access
		}
		if (v instanceof HasVersion) {
			// TODO use correct comparable type
			ReflectHelper.write(v,"version",0l);
		}
		padValues.put(c,v);
		return v;
	}

	private static final Pattern where = Pattern.compile("(?i)\\bwhere\\b");

	public static Map<String,Object> parseArguments(Object... idAndValueAndExpr) {
		Pattern jpaqlParam = Pattern.compile(":(\\w+)");
		Map<String,Object> values = new LinkedHashMap();
		Map<Integer,String> parts = new LinkedHashMap();
		int i = 0;
		Object o;
		while (i<idAndValueAndExpr.length) {
			// index
			Integer index = (Integer)idAndValueAndExpr[i++];
			if (index==null) break;
			// value
			o = idAndValueAndExpr[i++];
			Object value = o;
			// condition for not null
			o = idAndValueAndExpr[i++];
			String notnull = String.valueOf(o);
			// condition for null
			o = i<idAndValueAndExpr.length ? idAndValueAndExpr[i] : null;
			i++;
			String isnull = null;
			if (o instanceof Boolean)
				isnull = (Boolean)o ? "1=1" : "1=0";
			else if (o!=null) isnull = String.valueOf(o);

			String part = notnull;
			if (isnull!=null) {
				if (value instanceof Boolean)
					part = (Boolean)value ? notnull : isnull;
				else part = value!=null ? notnull : isnull;
			}
			if (part==null)
				throw new IllegalArgumentException("JPAQL condition must not be null");
			if (value!=null) {
				if (value instanceof Collection && ((Collection)value).isEmpty() && part.toLowerCase().contains(" in"))
					part = "1=0"; // false instead of "foo in ()"
				else if (value.getClass().isArray() && Array.getLength(value)==0 && part.toLowerCase().contains(" in"))
					part = "1=0"; // false instead of "foo in ()"
			}
			parts.put(index,part);
			Matcher matcher = jpaqlParam.matcher(part);
			if (matcher.find())
				values.put(matcher.group(1),value);
		}
		String pattern = i<idAndValueAndExpr.length ? (String)idAndValueAndExpr[i++] : null;
		if (pattern==null || !where.matcher(pattern).find()) {
			String ps = "";
			for (int p=0; p<parts.size(); p++) {
				ps += p==0 ? "where " : "and ";
				ps += "{"+p+"} ";
			}
			pattern = pattern!=null ? (ps+" "+pattern) : ps;
		}
		Object[] partsarray = new Object[parts.size()];
		for (int p=0; p<partsarray.length; p++)
			partsarray[p] = parts.get(p);
		String qs = MessageFormat.format(pattern,partsarray);
		values.put(null,qs);
		values.put("!",i);
		return values;
	}
}
