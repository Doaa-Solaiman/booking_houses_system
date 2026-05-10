package de.scheller.platform.common;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * @author kandzia
 */
public interface IValue
{
	interface Accessor<A extends Access> { A get(String name); }
	interface Access {}

	/** Deprecates {@link de.scheller.common.IAccess}.
	 * Whats new? Removing {@link de.scheller.common.IAccess#getType()} makes this variant a
	 * single method interface usable as {@link FunctionalInterface}. */
	@FunctionalInterface interface AccessR<D,V> extends Access { V getValue(D data); }
	@FunctionalInterface interface AccessW<D,V> extends Access { void setValue(D data, V value); }
	@FunctionalInterface interface AccessT extends Access { Type getType(); }
	@FunctionalInterface interface AccessC<D> extends Access { D create(); }

	interface AccessRT<D,V> extends AccessR<D,V>, AccessT {}
	interface AccessRW<D,V> extends AccessR<D,V>, AccessW<D,V> {}
	interface AccessRWT<D,V> extends AccessRT<D,V>, AccessRW<D,V> {}

	@FunctionalInterface interface Read<V> extends Access { V getValue(); }
	@FunctionalInterface interface Write<V> extends Access { void setValue(V value); }

	interface ReadWrite<V> extends Read<V>, Write<V> {}

	// technically similar to Write but with the semantic of a *reference* to data
	@FunctionalInterface interface Data<T> { void setData(T data); }

	/** Similar design as {@link ValueDisplay}. Can be built using {@link AccessRW}. */
	interface ReadWriteAccess<D,V> extends Data<D>, ReadWrite<V> {}

	static class Immutable<V> implements Read<V> {
		private final V value;
		public Immutable(V value) { this.value = value; }
		public V getValue() { return value; }
	}

	static class Value<V> implements ReadWrite<V> {
		protected V value;
		public Value(V value) { this.value = value; }
		public V getValue() { return value; }
		public void setValue(V value) { this.value = value; }
	}

	interface As {
		String asString();
		Boolean asBoolean();
		Integer asInteger();
		Long asLong();
		Float asFloat();
		Double asDouble();
	}

	// stick to String keys and
	// override parameter names (recognizable when e.g. eclipse inserts methods)
	interface ByName extends ByKey<String> {
		Object value(String name);
		String asString(String name);
		Boolean asBoolean(String name);
		Integer asInteger(String name);
		Long asLong(String name);
		Float asFloat(String name);
		Double asDouble(String name);
	}

	interface ByKey<K> {
		Object value(K key);
		String asString(K key);
		Boolean asBoolean(K key);
		Integer asInteger(K key);
		Long asLong(K key);
		Float asFloat(K key);
		Double asDouble(K key);
	}

	static class ByNameWithDefaults extends WithDefaults<String> implements ByName {
		public ByNameWithDefaults(ByKey<String> values) {
			super(values);
		}
	}

	static class WithDefaults<K> implements ByKey<K>
	{
		private final ByKey<K> values;

		public WithDefaults(ByKey<K> values) {
			this.values = values;
		}

		@Override
		public String toString() {
			return values.toString();
		}

		public Object value(K key) {
			return values.value(key);
		}

		public String asString(K key) {
			return values.asString(key);
		}

		public Boolean asBoolean(K key) {
			return values.asBoolean(key);
		}

		public Integer asInteger(K key) {
			return values.asInteger(key);
		}

		public Long asLong(K key) {
			return values.asLong(key);
		}

		public Float asFloat(K key) {
			return values.asFloat(key);
		}

		public Double asDouble(K key) {
			return values.asDouble(key);
		}

		////////////////////////////////////////////////////////////////////////
		// with defaults

		public <T> T value(K key, T dfault) {
			Object value = values.value(key);
			return value!=null ? (T)value : dfault;
		}

		public String asString(K key, String dfault) {
			String value = values.asString(key);
			return value!=null ? value : dfault;
		}

		public Boolean asBoolean(K key, Boolean dfault) {
			Boolean value = values.asBoolean(key);
			return value!=null ? value : dfault;
		}

		public Integer asInteger(K key, Integer dfault) {
			Integer value = values.asInteger(key);
			return value!=null ? value : dfault;
		}

		public Long asLong(K key, Long dfault) {
			Long value = values.asLong(key);
			return value!=null ? value : dfault;
		}

		public Float asFloat(K key, Float dfault) {
			Float value = values.asFloat(key);
			return value!=null ? value : dfault;
		}

		public Double asDouble(K key, Double dfault) {
			Double value = values.asDouble(key);
			return value!=null ? value : dfault;
		}

		////////////////////////////////////////////////////////////////////////
		// primitives

		public boolean asBoolean(K key, boolean dfault) {
			Boolean value = values.asBoolean(key);
			return value!=null ? value : dfault;
		}

		public byte asByte(K key, byte dfault) {
			Integer value = values.asInteger(key);
			return value!=null ? (byte)(value&0xff) : dfault;
		}

		public short asShort(K key, short dfault) {
			Integer value = values.asInteger(key);
			return value!=null ? (short)(value&0xffff) : dfault;
		}

		public int asInt(K key, int dfault) {
			Integer value = values.asInteger(key);
			return value!=null ? value : dfault;
		}

		public long asLong(K key, long dfault) {
			Long value = values.asLong(key);
			return value!=null ? value : dfault;
		}

		public float asFloat(K key, float dfault) {
			Float value = values.asFloat(key);
			return value!=null ? value : dfault;
		}

		public double asDouble(K key, double dfault) {
			Double value = values.asDouble(key);
			return value!=null ? value : dfault;
		}
	}

	interface ConverterPart<S,T> {
		class FromTo {
			private final Class from;
			private final Class to;
			public FromTo(Class from, Class to) { this.from = from; this.to = to; }
			public Class getFrom() { return from; }
			public Class getTo() { return to; }

			public static FromTo of(Class from, Class to) {
				return new FromTo(from,to);
			}
		}
		FromTo[] getSupported();
		T toTarget(S value, Class<? extends T> type);
		S toSource(T value, Class<? extends S> type);
	}

	interface SupportsInnerConversion {
		void setConverter(Converter converter);
		void setInnerType(Class type);
	}

	interface SupportsHint {
		void setHint(String hint);
	}

	interface Converter {
		<V> V convert(Object value, Class<V> type);
		<V> V convert(Object value, TT<V> type);

		<V> V convert(Object value, Class<V> type, String hint);
		<V> V convert(Object value, TT<V> type, String hint);

		<T extends Type,V> V convert(Object value, T type, String hint);
	}

	interface ConverterRegistry extends ConverterPart, Converter {
		void register(ConverterPart converter);
		void unregister(ConverterPart converter);
	}

	interface TT<T> {
		default Type type() {
			Type[] generics = this.getClass().getGenericInterfaces();
			return ((ParameterizedType)generics[0]).getActualTypeArguments()[0];
		}
	}
	interface In<T> extends TT<T> {}
	interface InOut<T> extends TT<T> {}
	interface Out<T> extends TT<T> {}
}
