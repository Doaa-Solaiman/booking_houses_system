package de.scheller.platform.persist.util;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Object-2-long map based on {@link IntIntMap3}.
 * Added functionality from {@link FSTObject2LongMap}.
 *
 * @see http://java-performance.info/implementing-world-fastest-java-int-to-int-hash-map/
 * @author kandzia
 */
public class ObjLongMap implements Serializable, Externalizable
{
	private static final Object FREE_KEY = new Object();
	private static final Object REMOVED_KEY = new Object();

	public static final int NO_VALUE = Integer.MIN_VALUE;

	/** Keys and values */
	private Object[] m_keys;
	private long[] m_values;

	/** Value for the null key (if inserted into a map) */
	private long m_nullValue;
	private boolean m_hasNull;

	/** Fill factor, must be between (0 and 1) */
	private final float m_fillFactor;
	/** We will resize a map once it reaches this size */
	private int m_threshold;
	/** Current map size */
	private int m_size;
	/** Mask to calculate the original position */
	private int m_mask;

	public ObjLongMap() {
		this(16,0.75f);
	}

	public ObjLongMap( final int size, final float fillFactor )
	{
		if ( fillFactor <= 0 || fillFactor >= 1 )
			throw new IllegalArgumentException( "FillFactor must be in (0, 1)" );
		if ( size <= 0 )
			throw new IllegalArgumentException( "Size must be positive!" );
		final int capacity = IntIntMapTools.arraySize( size, fillFactor );
		m_mask = capacity - 1;
		m_fillFactor = fillFactor;

		m_values = new long[capacity];
		m_keys = new Object[capacity];
		Arrays.fill(m_keys,FREE_KEY);
		m_threshold = (int) (capacity * fillFactor);
	}

	public long get( final Object key )
	{
		if ( key == null )
			return m_nullValue; //we null it on remove, so safe not to check a flag here

		if ( key == FREE_KEY)
			return m_hasNull ? m_nullValue : NO_VALUE;

		final int idx = getReadIndex( key );
		return idx != -1 ? m_values[ idx ] : NO_VALUE;
	}

	public long put( final Object key, final long value )
	{
		if ( key == null )
			return insertNullKey(value);

		if ( key == FREE_KEY )
		{
			final long ret = m_nullValue;
			if ( !m_hasNull )
				++m_size;
			m_hasNull = true;
			m_nullValue = value;
			return ret;
		}

		int idx = getPutIndex(key);
		if ( idx < 0 )
		{ //no insertion point? Should not happen...
			rehash( m_keys.length * 2 );
			idx = getPutIndex( key );
		}
		final long prev = m_values[ idx ];
		if ( !key.equals(m_keys[ idx ]) )
		{
			m_keys[ idx ] = key;
			m_values[ idx ] = value;
			++m_size;
			if ( m_size >= m_threshold )
				rehash( m_keys.length * 2 );
		}
		else //it means used cell with our key
		{
			assert m_keys[ idx ] == key;
			m_values[ idx ] = value;
		}
		return prev;
	}

	public long remove( final Object key )
	{
		if ( key == null )
			return removeNullKey();

		if ( key == FREE_KEY )
		{
			if ( !m_hasNull )
				return NO_VALUE;
			m_hasNull = false;
			final long ret = m_nullValue;
			m_nullValue = NO_VALUE;
			--m_size;
			return ret;
		}

		int idx = getReadIndex(key);
		if ( idx == -1 )
			return NO_VALUE;

		final long res = m_values[ idx ];
		m_values[ idx ] = NO_VALUE;
		shiftKeys(idx);
		--m_size;
		return res;
	}

	public void clear() {
		Arrays.fill(m_keys,FREE_KEY);
		Arrays.fill(m_values,NO_VALUE);
		m_nullValue = NO_VALUE;
		m_hasNull = false;
	}

	private int shiftKeys(int pos)
	{
		// Shift entries with the same hash.
		int last, slot;
		Object k;
		final Object[] keys = this.m_keys;
		while ( true )
		{
			last = pos;
			pos = getNextIndex(pos);
			while ( true )
			{
				if ((k = keys[pos]) == FREE_KEY)
				{
					keys[last] = FREE_KEY;
					m_values[ last ] = NO_VALUE;
					return last;
				}
				slot = getStartIndex(k); //calculate the starting slot for the current key
				if (last <= pos ? last >= slot || slot > pos : last >= slot && slot > pos) break;
				pos = getNextIndex(pos);
			}
			keys[last] = k;
			m_values[last] = m_values[pos];
		}
	}

	private long insertNullKey(final long value)
	{
		if ( m_hasNull )
		{
			final long ret = m_nullValue;
			m_nullValue = value;
			return ret;
		}
		else
		{
			m_hasNull = true;
			m_nullValue = value;
			++m_size;
			return NO_VALUE;
		}
	}

	private long removeNullKey()
	{
		if ( m_hasNull )
		{
			final long ret = m_nullValue;
			m_nullValue = NO_VALUE;
			m_hasNull = false;
			--m_size;
			return ret;
		}
		else
		{
			return NO_VALUE;
		}
	}

	public int size()
	{
		return m_size;
	}

	private void rehash( final int newCapacity )
	{
		m_threshold = (int) (newCapacity * m_fillFactor);
		m_mask = newCapacity - 1;

		final int oldCapacity = m_keys.length;
		final Object[] oldKeys = m_keys;
		final long[] oldValues = m_values;

		m_values = new long[newCapacity];
		m_keys = new Object[newCapacity];
		Arrays.fill(m_keys,FREE_KEY);
		m_size = m_hasNull ? 1 : 0;

		for ( int i = oldCapacity; i-- > 0; ) {
			if( oldKeys[ i ] != FREE_KEY  )
				put( oldKeys[ i ], oldValues[ i ] );
		}
	}

	/**
	 * Find key position in the map.
	 * @param key Key to look for
	 * @return Key position or -1 if not found
	 */
	private int getReadIndex( final Object key )
	{
		int idx = getStartIndex( key );
		if ( key.equals(m_keys[ idx ]) ) //we check FREE prior to this call
			return idx;
		if ( m_keys[ idx ] == FREE_KEY ) //end of chain already
			return -1;
		final int startIdx = idx;
		while (( idx = getNextIndex( idx ) ) != startIdx )
		{
			if ( m_keys[ idx ] == FREE_KEY )
				return -1;
			if ( key.equals(m_keys[ idx ]) )
				return idx;
		}
		return -1;
	}

	/**
	 * Find an index of a cell which should be updated by 'put' operation.
	 * It can be:
	 * 1) a cell with a given key
	 * 2) first free cell in the chain
	 * @param key Key to look for
	 * @return Index of a cell to be updated by a 'put' operation
	 */
	private int getPutIndex( final Object key )
	{
		final int readIdx = getReadIndex( key );
		if ( readIdx >= 0 )
			return readIdx;
		//key not found, find insertion point
		final int startIdx = getStartIndex( key );
		if ( m_keys[ startIdx ] == FREE_KEY )
			return startIdx;
		int idx = startIdx;
		while ( m_keys[ idx ] != FREE_KEY )
		{
			idx = getNextIndex( idx );
			if ( idx == startIdx )
				return -1;
		}
		return idx;
	}


	public int getStartIndex( final Object key )
	{
		//key is not null here
		return key.hashCode() & m_mask;
	}

	private int getNextIndex( final int currentIndex )
	{
		return ( currentIndex + 1 ) & m_mask;
	}

	public Set keySet() {
		Set keys = new HashSet();
		if (m_hasNull)
			keys.add(null);
		for (int i = 0; i < m_keys.length; i++) {
			Object key = m_keys[i];
			if (key != FREE_KEY && key != REMOVED_KEY)
				keys.add(key);
		}
		return keys;
	}

	private void rePut(Map<Object,Long> javaMap) {
		for (int i = 0; i < m_keys.length; i++) {
			Object key = m_keys[i];
			if (key != FREE_KEY && key != REMOVED_KEY)
				javaMap.put(key, m_values[i]);
		}
	}

	public void writeExternal(ObjectOutput out) throws IOException {
		Map<Object,Long> javaMap = new HashMap();
		rePut(javaMap);
		out.writeObject(javaMap);
	}

	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		Map<Object,Long> javaMap = (Map<Object,Long>)in.readObject();
		for (Map.Entry<Object,Long> e : javaMap.entrySet())
			put(e.getKey(),e.getValue());
	}
}
