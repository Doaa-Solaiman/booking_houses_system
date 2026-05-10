/* /////////////////////////////////////////////////////////////////////////////
 *
 * Created       :  08.08.2007 13:39:30 by kandzia
 * Project       :  de.scheller-sfc
 * CVS Ident     :  $Revision$, $Date$
 * Last Commiter :  $Author$
 *
 * Copyright (c) 2007-2009 Scheller Systemtechnik GmbH
 *                         Poeler Strasse 85a, 23970 Wismar, Germany
 *
 *//////////////////////////////////////////////////////////////////////////////
package de.scheller.platform.common;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * @author kandzia
 */
public class TreeBuilder<T>
{
	private static final Object NULL = new Object() {
		@Override
		public String toString() { return "<null>"; }
	};

	protected Collection<T> objects;
	protected TreePathBuilder<T> builder;
	protected Object root;
	protected boolean useBuilderLeafs;

	public TreeBuilder(Collection<T> objects, TreePathBuilder<T> builder) {
		this.root = this;
		this.objects = objects!=null ? objects : Collections.EMPTY_LIST;

		parents = new IdentityHashMap();
		branches = new IdentityHashMap();
		nodeByPath = new LinkedHashMap();
		pathByNode = new IdentityHashMap();
		buildpathsByObj = new HashMap();

		setBuilder(builder);
	}

	public void setObjects(Collection<T> objects) {
		this.objects = objects!=null ? objects : Collections.EMPTY_LIST;
		setBuilder(builder);
	}

	public void setBuilder(TreePathBuilder<T> builder) {
		this.builder = builder;
		if (builder!=null) fireListChanged();
	}

	public void setUseBuilderLeafs(boolean useBuilderLeafs) {
		this.useBuilderLeafs = useBuilderLeafs;
		if (builder!=null) fireListChanged();
	}

	public void fireListChanged() {
		clear();
		buildLookupMaps();
		build(null);
	}

	protected final HashMap<BP,Object> nodeByPath;
	protected final IdentityHashMap<Object,BP> pathByNode;
	protected final HashMap<Object,BP[]> buildpathsByObj;

	public void clear() {
		parents.clear();
		branches.clear();
		nodeByPath.clear();
		pathByNode.clear();
		buildpathsByObj.clear();

		parents.put(root,null);
		nodeByPath.put(null,root);
		pathByNode.put(root,null);
		buildpathsByObj.put(root,null);
	}

	protected void buildLookupMaps() {
		for (Iterator it = objects.iterator(); it.hasNext();)
			addToLookupMaps(it.next());
	}

	private final ArrayList<Object[]> paths = new ArrayList();
	private final LinkedList<BP> buildpaths = new LinkedList();

	protected void addToLookupMaps(Object o) {
		if (o==null) return;
		if (buildpathsByObj.containsKey(o)) {
			System.out.println("WARNING: object "+o+" more than one time in source list " + objects.size());
			return;
		}

		if (builder instanceof SingleTreePathBuilder) {
			Object[] steps = ((SingleTreePathBuilder)builder).buildTreePath(o);
			if (steps==null || steps.length==0) { // no path
				buildpathsByObj.put(o,new BP[0]);
				return;
			}

			BP bp = includePath(steps,o);
			if (bp!=null) buildpathsByObj.put(o,new BP[] { bp });

		} else if (builder instanceof MultiTreePathBuilder) {
			paths.clear();
			((MultiTreePathBuilder)builder).buildTreePath(o,paths);
			if (paths.isEmpty()) { // no paths
				buildpathsByObj.put(o,new BP[0]);
				return;
			}

			buildpaths.clear();
			for (int n=paths.size(), i=0; i<n; i++) {
				Object[] steps = paths.get(i);
				if (steps==null || steps.length==0) continue;

				BP bp = includePath(steps,o);
				if (bp!=null) buildpaths.add(bp);
			}
			buildpathsByObj.put(o,buildpaths.toArray(new BP[buildpaths.size()]));
		}
	}

	protected BP includePath(Object[] steps, Object input) {
		BP bp = new BP(steps);
		if (nodeByPath.containsKey(bp)) return null;

		Object leaf = bp.getLastPathComponent();
		Object node = newNode(leaf,pathByNode.containsKey(leaf));
		if (node!=leaf) { // wrapped the leaf?
			Object[] path = bp.path;
			Class c = path.getClass();
			int length = bp.length;
			if (!c.equals(Object[].class) &&
					!c.getComponentType().isAssignableFrom(node.getClass())) {
				path = new Object[length];
				System.arraycopy(bp.path,0,path,0,length);
				bp.path = path;
			}
			path[length-1] = node;
		}
		updateMap(bp,node);
		return bp;
	}

	private void updateMap(BP bp, Object o) {
		if (nodeByPath.containsKey(bp) || pathByNode.containsKey(o)) return;
		nodeByPath.put(bp,o);
		pathByNode.put(o,bp);
	}

	protected final Map<Object,Object> parents;
	protected final Map<Object,List> branches;

	private void build(Object oneLevel) {
		long t = System.nanoTime();
		// remove treestructure infos for start node
		BP bpParent = null;
		if (oneLevel!=null) {
			branches.put(oneLevel,null);
			bpParent = pathByNode.get(oneLevel);
		}
		// build treestructure infos
		for (Iterator it = objects.iterator(); it.hasNext();) {
			Object o = it.next();
			build(o,bpParent,false);
		}
	}

	protected void build(Object o, BP bpParent, boolean fireEvents) {
		if (o==null) return;
		BP[] buildpaths = buildpathsByObj.get(o);
		if (buildpaths==null) {
			addToLookupMaps(o);
			buildpaths = buildpathsByObj.get(o);
			if (buildpaths==null) return;
		}

		for (int n=buildpaths.length, p=0; p<n; p++) {
			BP bp = buildpaths[p];
			if (bp==null)
				continue;
			if (bpParent!=null && !bpParent.equals(bp.getParentPath()))
				continue;

			if (useBuilderLeafs)
				o = bp.getLastPathComponent();
			BP pp = bp.getParentPath();
			Object oo = o;
			// attach that to a parent
			for(;;) {
				// try to find a parent (rückwartsadoptieren ;-))
				BP parentPath = bp.getParentPath();
				Object parent = nodeByPath.get(parentPath);
				if (parent==null) { // no! :-(
					// build a virtual parent (ichhörestimmen)
					parent = parentPath.getLastPathComponent();
					if (pathByNode.containsKey(parent)) {
						// unwrap if node is already wrapped
						// to ensure that the node is wrapped once
						if (TbNode.class!=parent.getClass())
							parent = newNode(parent,true); // wrap the branch
					} else {
						if (parent==null) {
							parent = newNode(parent,false);
						} else if (TbNode.class!=parent.getClass())
							parent = newNode(parent,false);
					}
					updateMap(parentPath,parent);
					int i = attach(parent,o);
					o = parent;
					bp = parentPath;
				} else { // i has a parent! :-)
					int i = attach(parent,o);
					break;
				}
			}
			attached(pp,oo);
		}
	}

	protected int attach(Object parent, Object o) {
		List children = branches.get(parent);
		if (children==null) {
			branches.put(parent,children = newBranchCollection(parent));
		}
		// TODO in children.contains() geht die meiste zeit drauf :-(
		if (children.contains(o)) {
			System.err.println("TreeBuilderTreeModel.attach() "+o);
			return -1;
		}
		parents.put(o,parent);
		children.add(o);
		int n = children.size();
		return children.lastIndexOf(o);
	}

	protected List newBranchCollection(Object parent) {
		return new ArrayList();
	}

	protected Object newNode(Object o, boolean wrap) {
		if (!wrap) return o;
		throw new RuntimeException("please override TreeBuilder.newNode()");
	}

	protected void attached(BP parentPath, Object o) {}

	public Object getRoot() {
		return root;
	}

	public Object getChild(Object parent, int index) {
		List children = branches.get(parent);
		if (children==null) return null;

		Object node = children.get(index);
		if (node==null) node = NULL;
		return node;
	}

	public int getChildCount(Object parent) {
		List children = branches.get(parent);
		if (children==null) return 0;

		return children.size();
	}

	public int getIndexOfChild(Object parent, Object child) {
		List children = branches.get(parent);
		if (children==null) return -1;
		int index = children.indexOf(child);
		if (index>=0) return index;
		int i = 0;
		for (Object c : children) {        // try the equals check from the side
			if (c.equals(child)) return i; // of the eventually wrapped object
			i++;
		}
		return -1;
	}

	public boolean isLeaf(Object node) {
		if (branches==null) return true;
		List children = branches.get(node);
		return children!=null ? children.isEmpty() : true;
	}

	public boolean isFirstChild(Object parent, Object child) {
		List children = branches.get(parent);
		if (children==null || children.isEmpty()) return false;
		return children.get(0)==child;
	}

	public boolean isLastChild(Object parent, Object child) {
		List children = branches.get(parent);
		if (children==null || children.isEmpty()) return false;
		return children.get(children.size()-1)==child;
	}

	public Object[] getFirstBuildPath(Object o) {
		BP[] bps = buildpathsByObj.get(o);
		return bps!=null && bps.length>0 ? bps[0].path : null;
	}

	public Object getParent(Object node) {
		return parents.get(node);
	}

	public int getDepth(Object node) {
		if (node==root) return 0;

		Object p = parents.get(node);
		if (p==null) return -1;
		int depth = 0;
		for (; p!=null; p=parents.get(p), depth++);
		return depth;
	}

	public int getNodeCount() {
		return nodeByPath.size();
	}

	public Object getNode(Object... path) {
		return nodeByPath.get(new BP(path));
	}

	private Object[] realPath(BP bp) {
		if (bp==null) return new Object[] { root };

		int length = bp.getPathCount();
		Object[] p = new Object[length+1];
		p[0] = root;
		for (int i=0; i<length; i++, bp=bp.getParentPath()) {
			Object step = nodeByPath.get(bp);
			p[length-i] = step!=null ? step : bp.getLastPathComponent();
		}
		return p;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(objects!=null ? objects.size() : "X");
		sb.append(" => ");
		sb.append(buildpathsByObj!=null ? buildpathsByObj.size() : "X");
		sb.append(" buildpaths, ");
		sb.append(branches!=null ? branches.size() : "X");
		sb.append(" branches, ");
		sb.append(nodeByPath!=null ? nodeByPath.size() : "X");
		sb.append(" nodesByPath, ");
		sb.append(pathByNode!=null ? pathByNode.size() : "X");
		sb.append(" pathsByNode");
		return sb.toString();
	}

	public List children(Object parent) {
		List children = branches.get(parent);
		return children!=null ? children : Collections.EMPTY_LIST;
	}

	protected static class BP {
		private Object[] path;
		private int length;

		private BP(Object[] path, int length) {
			this.path = path;
			this.length = length;
		}

		public BP(Object[] path) {
			this.path = path;
			if (path!=null)
				this.length = path.length;
		}

		public BP getParentPath() {
			return length>1 ? new BP(path,length-1) : null;
		}

		public Object getParent() {
			return length>1 ? path[length-2] : null;
		}

		public Object getLastPathComponent() {
			return path[length-1];
		}

		public int getPathCount() {
			return length;
		}

		@Override
		public int hashCode() {
			int h = 0;
			for (int i=0; i<length; i++) {
				Object o = path[i];
				if (o!=null) {
					if (o instanceof TbNode) {
//						System.out.println("BP.hashCode() "+o);
						o = ((TbNode)o).unwrap();
					}
					h += o.hashCode();
				}
			}
			return h;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj==this) return true;
			if (obj==null) return false;
			BP other = (BP)obj;
			if (length!=other.length) return false;
			if (path==other.path) return true;
//			int h = 0;
			for (int i=0; i<length; i++) {
				Object o1 = path[i];
				if (o1 instanceof TbNode) {
//					System.out.println("BP.equals() "+obj+" vs. "+other);
					o1 = ((TbNode)o1).unwrap();
				}
				Object o2 = other.path[i];
				if (o2 instanceof TbNode) {
//					System.out.println("BP.equals() "+obj+" vs. "+other);
					o2 = ((TbNode)o2).unwrap();
				}
				if (o1!=null && o2!=null)
					if (!o1.equals(o2)) return false;
				else
					if ((o1!=null && o2==null) || (o2!=null && o1==null))
						return false;

			}
			return true;
		}

		@Override
		public String toString() {
			StringBuffer sb = new StringBuffer("[");
			for (int i=0; i<length; i++) {
				if (i>0) sb.append(", ");
				sb.append(path[i]);
			}
			sb.append("]");
			return sb.toString();
		}
	}

	public static interface TbNode<T> {
		T unwrap();
	}

	public interface TreePathBuilder<T> {}

	public static interface SingleTreePathBuilder<T> extends TreePathBuilder<T> {
		Object[] buildTreePath(T item);
	}

	public interface MultiTreePathBuilder<T> extends TreePathBuilder<T> {
		void buildTreePath(T item, List<Object[]> result);
	}
}
