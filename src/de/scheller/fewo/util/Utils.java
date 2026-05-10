package de.scheller.fewo.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Utils
{
	public static String asString(Object v) {
		if (v==null)
			return null;
		return String.valueOf(v);
	}

	public static Double asDouble(Object v) {
		if (v==null)
			return null;
		if (v instanceof Double)
			return (Double)v;
		if (v instanceof Number)
			return ((Number)v).doubleValue();
		try {
			return Double.parseDouble(String.valueOf(v));
		} catch (NumberFormatException ex) {
			return null;
		}
	}

	public static Integer asInteger(Object v) {
		if (v==null)
			return null;
		if (v instanceof Integer)
			return (Integer)v;
		if (v instanceof Number)
			return ((Number)v).intValue();
		try {
			return Integer.parseInt(String.valueOf(v));
		} catch (NumberFormatException ex) {
			return null;
		}
	}

	public static Boolean asBoolean(Object v) {
		if (v==null)
			return null;
		if (v instanceof Boolean)
			return (Boolean)v;
		if (v instanceof Number)
			return ((Number)v).intValue()>0;
		return Boolean.parseBoolean(String.valueOf(v));
	}

	public static final String[] DatetimePatterns = new String[] {
		"yyyy-MM-dd HH:mm:ssZ",
		"yyyy-MM-dd HH:mm:ss",
		"yyyy-MM-dd",
	};

	public static Date asDate(Object v) {
		if (v==null)
			return null;
		if (v instanceof Date)
			return (Date)v;
		if (v instanceof String) {
			try {
				v = (long)Double.parseDouble((String)v);
			} catch (NumberFormatException ex) {}
		}
		if (v instanceof Number)
			return new Date(((Number)v).longValue());
		for (String p : DatetimePatterns) {
			try {
				SimpleDateFormat sdf = new SimpleDateFormat(p);
				return sdf.parse(String.valueOf(v));
			} catch (ParseException ignore) {}
		}
		return null;
	}

	public static java.sql.Date asSqlDate(Object v) {
		if (v==null)
			return null;
		if (v instanceof java.sql.Date)
			return (java.sql.Date)v;
		v = asDate(v);
		if (v==null)
			return null;
		return new java.sql.Date(((Date)v).getTime());
	}

	public static java.sql.Timestamp asSqlTimestamp(Object v) {
		if (v==null)
			return null;
		if (v instanceof java.sql.Timestamp)
			return (java.sql.Timestamp)v;
		v = asDate(v);
		if (v==null)
			return null;
		return new java.sql.Timestamp(((Date)v).getTime());
	}
}
