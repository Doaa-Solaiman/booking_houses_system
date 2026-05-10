/* /////////////////////////////////////////////////////////////////////////////
 *
 * Created       :  05.03.2009 19:43:05 by kunze
 * Project       :  de.scheller-sfc-minus
 * CVS Ident     :  $Revision$, $Date$
 * Last Commiter :  $Author$
 *
 * Copyright (c) 2009 Scheller Systemtechnik GmbH
 *                    Poeler Strasse 85a, 23970 Wismar, Germany
 *
 *//////////////////////////////////////////////////////////////////////////////
package de.scheller.platform.common;

/**
 * A generic interface of an unary operation, an operation with a single input.
 * There are type variables <tt>R</tt> for result and <tt>A</tt> for argument.
 *
 * @author kandzia
 */
public interface UnaryOp<R,A>
{
	R eval(A arg);
}
