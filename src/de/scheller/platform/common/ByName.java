package de.scheller.platform.common;

import java.text.DecimalFormat;
import java.util.Locale;
import java.util.Map;

import de.scheller.platform.common.IValue.AccessR;
import de.scheller.platform.common.IValue.Accessor;
import de.scheller.platform.common.IValue.ByNameWithDefaults;

/**
 * @author kandzia
 */
public class ByName implements IValue.ByName
{
	public static Accessor<AccessR<Map,Object>> MapAccess = k -> m -> m.get(k);

	public static Accessor<? extends AccessR> defaultAccess = MapAccess;

	private final Object data;
	private final Accessor<? extends AccessR> access;

	public static ByName get(Object data) {
		return new ByName(data,defaultAccess);
	}

	public static ByName get(Object data, Accessor<? extends AccessR> access) {
		return new ByName(data,access);
	}

	public static IValue.WithDefaults<String> withDefaults(Object data) {
		return new ByName(data,defaultAccess).withDefaults();
	}

	public static IValue.WithDefaults<String> withDefaults(Object data, Accessor<? extends AccessR> access) {
		return new ByName(data,access).withDefaults();
	}

	public ByName(Object data, Accessor<? extends AccessR> access) {
		this.data = data;
		this.access = access;
	}

	@Override
	public String toString() {
		return "ByName/"+data;
	}

	public ByNameWithDefaults withDefaults() {
		return new ByNameWithDefaults(this);
	}

	public Object value(String name) {
		if (data instanceof ByName)
			return ((ByName)data).value(name);
		if (data==null) return null;
		return access.get(name).getValue(data);
	}

	public String asString(String name) {
		if (data instanceof ByName)
			return ((ByName)data).asString(name);
		Object v = value(name);
		if (v==null)
			return null;
		if (v instanceof Double || v instanceof Float)
			return DecimalFormat.getNumberInstance(Locale.US).format(v);
		return String.valueOf(v);
	}

	public Boolean asBoolean(String name) {
		if (data instanceof ByName)
			return ((ByName)data).asBoolean(name);
		Object v = value(name);
		if (v==null) return null;
		if (v instanceof Boolean) return (Boolean)v;
		if (v instanceof Number) return ((Number)v).doubleValue()!=0;
		String s = v.toString().trim();
		return "true".equalsIgnoreCase(s) || "yes".equalsIgnoreCase(s) ||
			"on".equalsIgnoreCase(s) || "1".equals(s);
	}

	public Integer asInteger(String name) {
		if (data instanceof ByName)
			return ((ByName)data).asInteger(name);
		Object v = value(name);
		if (v==null) return null;
		if (v instanceof Integer) return (Integer)v;
		if (v instanceof Number) return ((Number)v).intValue();
		String s = v.toString().trim();
		if (s.length()==0) return null;
		return Integer.parseInt(s);
	}

	public Long asLong(String name) {
		if (data instanceof ByName)
			return ((ByName)data).asLong(name);
		Object v = value(name);
		if (v==null) return null;
		if (v instanceof Long) return (Long)v;
		if (v instanceof Number) return ((Number)v).longValue();
		String s = v.toString().trim();
		if (s.length()==0) return null;
		return Long.parseLong(s);
	}

	public Double asDouble(String name) {
		if (data instanceof ByName)
			return ((ByName)data).asDouble(name);
		Object v = value(name);
		if (v==null) return null;
		if (v instanceof Double) return (Double)v;
		if (v instanceof Number) return ((Number)v).doubleValue();
		String s = v.toString().trim();
		if (s.length()==0) return null;
		return Double.parseDouble(s);
	}

	public Float asFloat(String name) {
		if (data instanceof ByName)
			return ((ByName)data).asFloat(name);
		Object v = value(name);
		if (v==null) return null;
		if (v instanceof Float) return (Float)v;
		if (v instanceof Number) return ((Number)v).floatValue();
		String s = v.toString().trim();
		if (s.length()==0) return null;
		return Float.parseFloat(s);
	}
}
