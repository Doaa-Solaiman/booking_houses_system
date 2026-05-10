package de.scheller.platform.common;

import java.util.function.Supplier;

/**
 * @author kandzia
 */
public class Log
{
	public static Object lazy(Supplier s) {
		return new Object() {
			@Override public String toString() {
				return String.valueOf(s.get());
			}
		};
	}

	public static String maxlen(int max, Object o, boolean prefixLength) {
		String s = String.valueOf(o);
		int len = s.length();
		s = s.substring(0,Math.min(max,len));
		if (max<len) s += "...";
		if (prefixLength) s = len+" "+s;
		return s;
	}
}
