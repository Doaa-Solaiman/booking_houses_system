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

import org.nustaq.serialization.util.FSTObject2IntMap;
import org.nustaq.serialization.util.FSTUtil;

/**
 * Adapted from {@link FSTObject2IntMap}.
 */
public class FSTObject2LongMap<K> implements Serializable, Externalizable
{
	static int[] prim = {
		3, 5, 7, 11, 13, 17, 19, 23, 29, 37, 67, 97, 139,
		211, 331, 641, 1097, 1531, 2207, 3121, 5059, 7607, 10891,
		15901, 19993, 30223, 50077, 74231, 99991, 150001, 300017,
		1000033, 1500041, 200033, 3000077, 5000077, 10000019
	};

	private static final int GROFAC = 2;

	public static int adjustSize(int size) {
		for (int i = 0; i < prim.length - 1; i++)
			if (size < prim[i])
				return prim[i];
		return size;
	}

	public Object mKeys[];
	public long mValues[];
	public int mNumberOfElements;
	FSTObject2LongMap<K> next;
	boolean checkClazzOnEquals = false;

	public FSTObject2LongMap(int initialSize, boolean checkClassOnequals) {
		if (initialSize < 2)
			initialSize = 2;
		initialSize = adjustSize(initialSize * 2);

		mKeys = new Object[initialSize];
		mValues = new long[initialSize];
		mNumberOfElements = 0;
		this.checkClazzOnEquals = checkClassOnequals;
	}

	public int size() {
		return mNumberOfElements + (next != null ? next.size() : 0);
	}

	final public void put(K key, long value) {
		int hash = key.hashCode() & 0x7FFFFFFF;
		putHash(key, value, hash, this);
	}

	final private static <K> void putHash(K key, long value, int hash,
			FSTObject2LongMap<K> current, FSTObject2LongMap<K> parent, boolean checkClazzOnEquals) {
		int count = 0;
		while(true){
			if (current.mNumberOfElements * GROFAC > current.mKeys.length) {
				if (parent != null) {
					if ((parent.mNumberOfElements + current.mNumberOfElements) * GROFAC > parent.mKeys.length) {
						parent.resize(parent.mKeys.length * GROFAC);
						parent.put(key, value);
						return;
					} else {
						current.resize(current.mKeys.length * GROFAC);
					}
				} else {
					current.resize(current.mKeys.length * GROFAC);
				}
			}

			int idx = hash % current.mKeys.length;

			if (current.mKeys[idx] == null) // new
			{
				current.mNumberOfElements++;
				current.mValues[idx] = value;
				current.mKeys[idx] = key;
				return;
			} else if (current.mKeys[idx] == key && (!checkClazzOnEquals || current.mKeys[idx].getClass() == key.getClass()))  // overwrite
			{
				current.mValues[idx] = value;
				return;
			} else {
				if (current.next == null) {
					// try break edge cases leading to long chains of maps
					if ( count > 4 && current.mNumberOfElements < 5 ) {
						int newSiz = current.mNumberOfElements*2+1;
						current.next = new FSTObject2LongMap<K>(newSiz,checkClazzOnEquals);
						count = 0;
					} else {
						int newSiz = current.mNumberOfElements / 3;
						current.next = new FSTObject2LongMap<K>(newSiz,checkClazzOnEquals);
					}
				}
				parent = current;
				current = current.next;
				count++;
			}
		}
	}

	final void putHash(K key, long value, int hash, FSTObject2LongMap<K> parent) {
		putHash(key, value, hash,this, parent,checkClazzOnEquals);
	}

	final K removeHash(K key, int hash) {
		final int idx = hash % mKeys.length;

		final Object mKey = mKeys[idx];
		if (mKey == null) // not found
		{
//            hit++;
			return null;
		} else if (mKey.equals(key) && (!checkClazzOnEquals || mKeys[idx].getClass() == key.getClass()))  // found
		{
//            hit++;
			K val = (K) mKeys[idx];
			mValues[idx] = 0l; mKeys[idx] = null;
			mNumberOfElements--;
			return val;
		} else {
			if (next == null) {
				return null;
			}
//            miss++;
			return next.removeHash(key, hash);
		}
	}

	final void putNext(final int hash, final K key, final int value) {
		if (next == null) {
			int newSiz = mNumberOfElements / 3;
			next = new FSTObject2LongMap<K>(newSiz, checkClazzOnEquals);
		}
		next.putHash(key, value, hash, this);
	}

	/** @return Integer.MIN_VALUE if not found */
	final public long get(final K key) {
		final int hash = key.hashCode() & 0x7FFFFFFF;
		//return getHash(key,hash); inline =>
		final int idx = hash % mKeys.length;

		final Object mapsKey = mKeys[idx];
		if (mapsKey == null) // not found
		{
			return Integer.MIN_VALUE;
		} else if (mapsKey.equals(key) && (!checkClazzOnEquals || mapsKey.getClass() == key.getClass()))  // found
		{
			return mValues[idx];
		} else {
			if (next == null) {
				return Integer.MIN_VALUE;
			}
			long res = next.getHash(key, hash);
			return res;
		}
		// <== inline
	}

	static int miss = 0;
	static int hit = 0;

	final long getHash(final K key, final int hash) {
		final int idx = hash % mKeys.length;

		final Object mapsKey = mKeys[idx];
		if (mapsKey == null) // not found
		{
			return Integer.MIN_VALUE;
		} else if (mapsKey.equals(key) && (!checkClazzOnEquals || mapsKey.getClass() == key.getClass()))  // found
		{
			return mValues[idx];
		} else {
			if (next == null) {
				return Integer.MIN_VALUE;
			}
			long res = next.getHash(key, hash);
			return res;
		}
	}

	final void resize(int newSize) {
		newSize = adjustSize(newSize);
		Object[] oldTabKey = mKeys;
		long[] oldTabVal = mValues;

		mKeys = new Object[newSize];
		mValues = new long[newSize];
		mNumberOfElements = 0;

		for (int n = 0; n < oldTabKey.length; n++) {
			if (oldTabKey[n] != null) {
				put((K) oldTabKey[n], oldTabVal[n]);
			}
		}
		if (next != null) {
			FSTObject2LongMap oldNext = next;
			next = null;
			oldNext.rePut(this);
		}
	}

	private void rePut(FSTObject2LongMap<K> kfstObject2LongMap) {
		for (int i = 0; i < mKeys.length; i++) {
			Object mKey = mKeys[i];
			if (mKey != null) {
				kfstObject2LongMap.put((K) mKey, mValues[i]);
			}
		}
		if (next != null) {
			next.rePut(kfstObject2LongMap);
		}
	}

	public void clear() {
		if (size() == 0) {
			return;
		}
		FSTUtil.clear(mKeys);
		Arrays.fill(mValues, 0);
		mNumberOfElements = 0;
		if (next != null) {
			next.clear();
		}
	}

	private void keySet(Set<K> javaSet) {
		for (int i = 0; i < mKeys.length; i++) {
			Object mKey = mKeys[i];
			if (mKey != null) {
				javaSet.add((K) mKey);
			}
		}
		if (next != null) {
			next.keySet(javaSet);
		}
	}

	public Set<K> keySet() {
		Set<K> keys = new HashSet();
		keySet(keys);
		return keys;
	}

	private void rePut(Map<K,Long> javaMap) {
		for (int i = 0; i < mKeys.length; i++) {
			Object mKey = mKeys[i];
			if (mKey != null) {
				javaMap.put((K) mKey, mValues[i]);
			}
		}
		if (next != null) {
			next.rePut(javaMap);
		}
	}

	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeBoolean(checkClazzOnEquals);
		Map<K,Long> javaMap = new HashMap();
		rePut(javaMap);
		out.writeObject(javaMap);
	}

	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		checkClazzOnEquals = in.readBoolean();
		Map<K,Long> javaMap = (Map<K,Long>)in.readObject();
		for (Map.Entry<K,Long> e : javaMap.entrySet())
			put(e.getKey(),e.getValue());
	}
}
