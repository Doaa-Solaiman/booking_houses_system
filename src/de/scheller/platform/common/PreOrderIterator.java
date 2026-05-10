/* /////////////////////////////////////////////////////////////////////////////
 *
 * Created       :  27.02.2009 19:21:00 by kandzia
 * Project       :  de.scheller-sfc
 * CVS Ident     :  $Revision$, $Date$
 * Last Commiter :  $Author$
 *
 * Copyright (c) 2009 Scheller Systemtechnik GmbH
 *                    Poeler Strasse 85a, 23970 Wismar, Germany
 *
 *//////////////////////////////////////////////////////////////////////////////
package de.scheller.platform.common;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Stack;

/**
 * Iteriert durch den Baum (A(B(DE)C(F(HI)G))) in der Pre-order-Folge:
 * A B D E C F H I G.
 *
 * @author kandzia
 */
public class PreOrderIterator<N> implements Iterator<N>, Iterable<N>
{
	private final UnaryOp<Iterator,Object> ce;
	private final Stack s; // iterator queue (iterators over children)
	private boolean branch;
	private boolean last;

	public PreOrderIterator(Object root, UnaryOp<Iterator,Object> ce) {
		this.ce = ce;
		ArrayList l = new ArrayList(1);
		l.add(root);
		s = new Stack();
		s.push(l.iterator());
	}

	public boolean hasNext() {
		return !s.empty() && ((Iterator)s.peek()).hasNext();
	}

	public N next() {
		Iterator it = (Iterator)s.peek();
		Object node = it.next();
		Iterator ch = ce.eval(node);

		if (last = !it.hasNext()) s.pop();
		if (branch = ch.hasNext()) s.push(ch);
		return (N)node;
	}

	public boolean isBranch() {
		return branch;
	}

	public boolean isLast() {
		return last;
	}

	public void remove() {}

	public Iterator<N> iterator() {
		return this;
	}
}
