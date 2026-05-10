package de.scheller.platform.persist.util;

import java.lang.invoke.CallSite;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * @author kunze
 */
public class LambdaUtil
{
	public static <T> Supplier<T> getDefaultConstructor(Class<T> clazz)
	throws Throwable {
		MethodHandles.Lookup lookup = MethodHandles.lookup();
		MethodHandle handle = lookup.findConstructor(
				clazz,MethodType.methodType(void.class));
		CallSite site = LambdaMetafactory.metafactory(
				lookup,"get",MethodType.methodType(Supplier.class),
				handle.type().erase(),handle,handle.type());
		return (Supplier)site.getTarget().invokeExact();
	}

	static Map<Method,Function> getters = new HashMap();

	public static Function getGetter(Method getter)
	throws Throwable {
		Function f = getters.get(getter);
		if (f!=null) return f;
		MethodHandles.Lookup lookup = MethodHandles.lookup();
		MethodHandle handle = lookup.unreflect(getter);
		MethodType mtype = handle.type();
		if (mtype.returnType().isPrimitive())
			mtype = mtype.changeReturnType(mtype.wrap().returnType());
		CallSite site = LambdaMetafactory.metafactory(
				lookup,"apply",MethodType.methodType(Function.class),
				mtype.erase(),handle,handle.type());
		f = (Function)site.getTarget().invokeExact();
		getters.put(getter,f);
		return f;
	}

	public static <T,R> Function<T,R> getGetter(Class<T> clazz, String name, Class<R> type)
	throws Throwable {
		MethodHandles.Lookup lookup = MethodHandles.lookup();
		MethodHandle handle = lookup.findVirtual(clazz,name,MethodType.methodType(type));
		MethodType mtype = handle.type();
		if (type.isPrimitive())
			mtype = mtype.changeReturnType(mtype.wrap().returnType());
		CallSite site = LambdaMetafactory.metafactory(
				lookup,"apply",MethodType.methodType(Function.class),
				mtype.erase(),handle,handle.type());
		return (Function)site.getTarget().invokeExact();
	}

	static Map<Method,BiConsumer> setters = new HashMap();

	public static BiConsumer getSetter(Method setter)
	throws Throwable {
		BiConsumer c = setters.get(setter);
		if (c!=null) return c;
		MethodHandles.Lookup lookup = MethodHandles.lookup();
		MethodHandle handle = lookup.unreflect(setter);
		MethodType mtype = handle.type();
		if (mtype.parameterType(1).isPrimitive())
			mtype = mtype.changeParameterType(1,mtype.wrap().parameterType(1));
		CallSite site = LambdaMetafactory.metafactory(
				lookup,"accept",MethodType.methodType(BiConsumer.class),
				mtype.erase(),handle,methodType(handle.type()));
		c = (BiConsumer)site.getTarget().invokeExact();
		setters.put(setter,c);
		return c;
	}

	public static <T,U> BiConsumer<T,U> getSetter(Class<T> clazz, String name, Class<U> type)
	throws Throwable {
		MethodHandles.Lookup lookup = MethodHandles.lookup();
		MethodHandle handle = lookup.findVirtual(clazz,name,MethodType.methodType(void.class,type));
		MethodType mtype = handle.type();
		if (type.isPrimitive())
			mtype = mtype.changeParameterType(1,mtype.wrap().parameterType(1));
		CallSite site = LambdaMetafactory.metafactory(
				lookup,"accept",MethodType.methodType(BiConsumer.class),
				mtype.erase(),handle,methodType(handle.type()));
		return (BiConsumer)site.getTarget().invokeExact();
	}

	private static MethodType methodType(MethodType type) {
		List<Class<?>> params = type.parameterList();
		params = params.stream().map(
				c -> c.isPrimitive() ? primitiveWrapperMap.get(c) : c).collect(Collectors.toList());
		return MethodType.methodType(type.returnType(),params);
	}

	private static Map<Class<?>,Class<?>> primitiveWrapperMap = new HashMap();
	static {
		primitiveWrapperMap.put(Boolean.TYPE, Boolean.class);
		primitiveWrapperMap.put(Byte.TYPE, Byte.class);
		primitiveWrapperMap.put(Character.TYPE, Character.class);
		primitiveWrapperMap.put(Short.TYPE, Short.class);
		primitiveWrapperMap.put(Integer.TYPE, Integer.class);
		primitiveWrapperMap.put(Long.TYPE, Long.class);
		primitiveWrapperMap.put(Double.TYPE, Double.class);
		primitiveWrapperMap.put(Float.TYPE, Float.class);
		primitiveWrapperMap.put(Void.TYPE, Void.TYPE);
	}
}
