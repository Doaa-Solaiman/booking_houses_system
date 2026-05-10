/* /////////////////////////////////////////////////////////////////////////////
 *
 * Created       :  10.10.2007 13:48:09 by kandzia
 * Project       :  de.scheller-sfc-minus
 * CVS Ident     :  $Revision$, $Date$
 * Last Commiter :  $Author$
 *
 * Copyright (c) 2007 Scheller Systemtechnik GmbH
 *                    Poeler Strasse 85a, 23970 Wismar, Germany
 *
 *//////////////////////////////////////////////////////////////////////////////
package de.scheller.fsm;

import java.beans.PropertyChangeListener;

/**
 * <p>
 * The Flying Spaghetti Monster.
 * This class makes "Touched by His Noodly Appendage" reality.
 * </p>
 *
 * <p>
 * In fact, this is a blend between the service locator pattern ("Touch me!")
 * and dependency injection ("Touch it!") plus some
 * <a href="http://en.wikipedia.org/wiki/Intelligent_design">
 * intelligent design</a> ;-).
 * This framework is intended to be as simple-to-use as possible. You have to
 * provide {@link Provider}s ...because even the FSM isn't all-knowing...
 * only at those places where they're naturally provided.
 * Then ...wherever you are... you can ask the FSM to touch you/something
 * to provide you/it with information. If you are a believer, in no time the
 * FSM will kindly satisfy your desire. HE finds a path from you to the
 * appropriate provider. HE knows the answer. You're a believer if you are
 * part of a virtual context tree. The FSM finds treepaths from your location
 * to provider locations. How does HE do that? Part of a secret implementation.
 * But HE knows the answer.
 * </p>
 *
 * "Believe your noodly master!"
 *
 * @see <a href="http://www.venganza.org/touched.htm">http://www.venganza.org/touched.htm</a>
 * @see <a href="http://en.wikipedia.org/wiki/Flying_Spaghetti_Monster">http://en.wikipedia.org/wiki/Flying_Spaghetti_Monster</a>
 *
 * @author kandzia
 */
public abstract class FSM
{
	/**
	 * Use this with tell() to attach content to context ("to make sacrifices" ;-))
	 */
	public static final String Sacrifice = "RAmen.";

	/**
	 * Create something incl. dependency injection
	 */
	public static final Object create(Object context, Class c) {
		return fsm!=null ? fsm.create(context,c) : null;
	}

	/**
	 * Dependency injection ("FSM, please touch it!")
	 */
	public static final void touch(Object it, Object context) {
		if (fsm!=null) fsm.touch(it,context);
	}

	/**
	 * Service Provider Request ("Please, FSM, touch me!")
	 */
	public static final <T extends Object> T touch(Object me, Class<T> c) {
		return fsm!=null ? fsm.touch(me,c) : null;
	}

	/**
	 * Provide something in context (for the service provider pattern).
	 * This is replaced by {@link #noodle(Object, Class, Object)}.
	 */
	@Deprecated
	public static final void noodle(Object context, Provider p) {
		if (fsm!=null) fsm.noodle(context,Provider.class,p);
	}

	/**
	 * Provide something in context (for dependency injection and/or
	 * service provider pattern). One can use {@link Provider} as service class
	 * and the given {@link Provider} instance is used to handle requests.
	 */
	public static final void noodle(Object context, Class service, Object sth) {
		if (fsm!=null) fsm.noodle(context,service,sth);
	}

	/**
	 * Leave a breadcrumb trail (add a shortcut that overrides the context tree)
	 */
	public static final void noodle(Object it, Object context) {
		if (fsm!=null) fsm.noodle(it,context);
	}

	/**
	 * Tell something (calls setter methods in known (sub)context objects)
	 */
	public static final void tell(Object context, String property, Object sth) {
		if (fsm!=null) fsm.tell(context,property,sth);
	}

	/**
	 * @return true if <tt>target</tt> is in context of <tt>context</tt>
	 * OR true if <tt>target</tt> is unknown and <tt>context</tt> is null
	 */
	public static final boolean isInContext(Object target, Object context) {
		if (fsm!=null) return fsm.isInContext(target,context);
		return false;
	}

	private static IFlyingSpaghettiMonster fsm;

	public interface IFlyingSpaghettiMonster {
		Object create(Object context, Class c);
		void touch(Object target, Object context);
		<T extends Object> T touch(Object target, Class<T> c);
		void noodle(Object context, Class service, Object instance);
		void noodle(Object target, Object context);
		void tell(Object context, String property, Object something);
		boolean isInContext(Object target, Object context);
	}

	public interface Inherit {};

	public interface TheOne extends PropertyChangeListener {};

	public interface IContext {
		Object[] getSpaceContext();
		Object[] getSpaceContext2();
		Object getContentContext(Object location);
	}
}
