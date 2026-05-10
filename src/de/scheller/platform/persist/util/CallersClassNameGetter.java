/* /////////////////////////////////////////////////////////////////////////////
 *
 * Created       :  15.12.2006 11:55:27 by kandzia
 * Project       :  de.scheller-sfc
 * CVS Ident     :  $Revision$, $Date$
 * Last Commiter :  $Author$
 *
 * Copyright (c) 2004-2007 Scheller Systemtechnik GmbH
 *                         Poeler Strasse 85a, 23970 Wismar, Germany
 *
 *//////////////////////////////////////////////////////////////////////////////
package de.scheller.platform.persist.util;

/**
 * @deprecated not reliable since java 7 - first two classes can be ignored
 *
 * @author kandzia
 */
@Deprecated
public class CallersClassNameGetter extends SecurityManager
{
	private static CallersClassNameGetter INSTANCE = new CallersClassNameGetter();
	public static Class getCallersClass() {
		return INSTANCE.getClassContext()[2];
	}

	public static Class getCallersClass(int i) {
		return INSTANCE.getClassContext()[i];
	}

	public static Class[] getCallersClassContext() {
		return INSTANCE.getClassContext();
	}
}
