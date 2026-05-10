package de.scheller.platform.common;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * @author kandzia
 */
public class ItemStore
{
	public static final String DisplayName = "name";
	public static final String Created = "created";
	public static final String Updated = "updated";

	public static enum Get { IfExists, OrCreate, CreateOverwrite, CreateAlternativeName }

	private final ItemStore root;
	private final String base;
	private final Set<String> folders;
	private final Map<String,StoreItem> items;
	private final Map<String,StoreItem> itemsOld;
	private final Set<Listener> listeners = new LinkedHashSet();
	private boolean batch;
	protected boolean quiet;

	public ItemStore(ItemStore items) {
		this(items.root.items.values());
	}

	public ItemStore(Iterable<? extends StoreItem> items) {
		this.root = this;
		this.base = null;
		this.items = Collections.synchronizedMap(new TreeMap());
		this.itemsOld = new TreeMap();
		this.folders = new HashSet();
		setItems(items);
	}

	private ItemStore(ItemStore root, String base) {
		this.root = root;
		this.base = base;
		this.items = null; //failfast
		this.itemsOld = null; //failfast
		this.folders = null; //failfast
		root.folders.add(base);
	}

	protected void setItems(Iterable<? extends StoreItem> items) {
		synchronized (root.items) {
			root.items.clear();
			synchronized (items) {
				for (StoreItem i : items)
					root.items.put(i.path,new StoreItem(this,i));
			}
			root.itemPaths = null;
		}
	}

	public String getContent(String path) {
		StoreItem item = getItemMaybeVirtual(path);
		return item==null ? null : getContent(item);
	}

	public StoreItem setContent(String path, String content) {
		StoreItem item = getItem(path,Get.IfExists);
		if (item==null)
			item = getItem(path,Get.OrCreate);
		else item.meta(Updated,new Date());
		return setContent(item,content);
	}

	public <T> T getMeta(String path, String key, Class<T> type) {
		StoreItem item = getItemMaybeVirtual(path);
		return item==null ? null : getMeta(item,key,type);
	}

	public StoreItem setMeta(String path, String key, Object value) {
		StoreItem item = getItemMaybeVirtual(path);
		return item==null ? null : setMeta(item,key,value);
	}

	public StoreItem flush(String path) {
		StoreItem item = getItemMaybeVirtual(path);
		return item==null ? null : flush(item);
	}

	private int flushing;
	private int flushPending;
	private int flushNext = 1;
	private final Queue eventsPending = new LinkedList();

	public void flush() {
		if (flushing!=0) {
			flushPending = ++flushNext;
			return;
		}
		try {
			flushing = flushPending>0 ? flushPending : ++flushNext;
			flushPending = 0;

			// pending events (as created before)
			while (eventsPending.size()>0) {
				String event = (String)eventsPending.remove();
				if ("pathsChanged".equals(event))
					pathsChanged((List)eventsPending.remove(),(List)eventsPending.remove());
				else if ("metaChanged".equals(event))
					metaChanged((List)eventsPending.remove());
				else if ("contentsChanged".equals(event))
					contentsChanged((List)eventsPending.remove());
			}

			// dirty metadata events (merged)
			List<String> paths = new ArrayList(root.items.size());
			synchronized (root.items) {
				for (StoreItem item : root.items.values())
					if (item.metaDirty!=0) {
						if (item.metaDirty<=flushing)
							item.metaDirty = 0;
						paths.add(item.path);
					}
			}
			if (paths.size()>0)
				metaChanged(paths);

			// dirty content events (merged)
			paths.clear();
			synchronized (root.items) {
				for (StoreItem item : root.items.values())
					if (item.contentDirty!=0) {
						if (item.contentDirty<=flushing)
							item.contentDirty = 0;
						paths.add(item.path);
					}
			}
			if (paths.size()>0)
				contentsChanged(paths);
		} finally {
			flushing = 0;
			if (flushPending!=0)
				flush();
		}
	}

	protected Access getAccess(StoreItem item) {
		return null;
	}

	protected String getContent(StoreItem item) {
		return item.content;
	}

	protected StoreItem setContent(StoreItem item, String content) {
		boolean change = !Objects.equals(item.content,content);
		item.content = content;
		if (change) {
			item.contentDirty = quiet ? 0 : flushNext;
			Date now = new Date();
			Date created = item.meta(Created,Date.class);
			if (created==null || now.getTime()-created.getTime()>10*1000)
				item.meta(Updated,now);
		}
		return item;
	}

	protected <T> T getMeta(StoreItem item, String key, Class<T> type) {
		return item.meta!=null ? (T)item.meta.get(key) : null;
	}

	protected StoreItem setMeta(StoreItem item, String key, Object value) {
		if (item.meta==null) item.meta = new LinkedHashMap();
		Object old = item.meta.get(key);
		item.meta.put(key,value);
		if (!Objects.equals(value,old))
			item.metaDirty = quiet ? 0 : flushNext;
		return item;
	}

	protected StoreItem flush(StoreItem item) {
		if (flushing!=0) {
			flushPending = ++flushNext;
			return item;
		}
		try {
			flushing = flushPending>0 ? flushPending : ++flushNext;
			flushPending = 0;

			List<String> paths = item.contentDirty!=0 || item.metaDirty!=0 ?
					new ArrayList(Arrays.asList(item.path)) : null;
			if (item.metaDirty!=0) {
				if (item.metaDirty<=flushing)
					item.metaDirty = 0;
				metaChanged(paths);
			}
			if (item.contentDirty!=0) {
				if (item.contentDirty<=flushing)
					item.contentDirty = 0;
				contentsChanged(paths);
			}
			return item;
		} finally {
			flushing = 0;
			if (flushPending!=0)
				flush();
		}
	}

	private StoreItem getItemMaybeVirtual(String path) {
		StoreItem item = getItem(path,Get.IfExists);
		if (item==null && exists(path,false)) {
			item = new StoreItem();
			item.owner = this;
			item.path = path;
		}
		return item;
	}

	public StoreItem getOldItem(String path) {
		return root.itemsOld.get(path);
	}

	public ItemStore getFolder(String path, Get strategy) {
		path = normalize(path);
		if (exists(path,true))
			throw new RuntimeException("could not get folder (file w/ same path exists)");
		if (exists(path,false)) {
			if (strategy==Get.IfExists || strategy==Get.OrCreate)
				return new ItemStore(root,path);
			if (strategy==Get.CreateAlternativeName)
				return createFolder(path,true);
			if (strategy==Get.CreateOverwrite) {
				delete(path,true,false);
				return createFolder(path,false);
			}
			return null;
		} else {
			if (strategy==Get.IfExists)
				return null;
			return createFolder(path,false);
		}
	}

	private ItemStore createFolder(String path, boolean alternativeName) {
		if (alternativeName)
			path = makeAlternative(path,null);
		ItemStore folder = new ItemStore(root,path);
		pathsChanged(new ArrayList(Arrays.asList(path)),null);
		return folder;
	}

	/** @return all items including temporary in-memory folder items */
	public List<StoreItem> getItems() {
		ArrayList items = new ArrayList();
		for (String path : root.folders)
			items.add(getItemMaybeVirtual(path));
		synchronized (root.items) {
			items.addAll(root.items.values());
		}
		return items;
	}

	/** @return items including temporary in-memory folder items that match the pattern */
	public List<StoreItem> getItems(String pathPattern) {
		String[] patterns = pathPattern.split("\\s*\\|\\s*");
		List<StoreItem> items = new ArrayList();
		for (String ptn : patterns) {
			ptn = normalize(ptn);
			Function<String,Boolean> matcher = matcher(ptn);
			for (String p : root.folders)
				if (matcher.apply(p+"/"))
					items.add(getItemMaybeVirtual(p));
			synchronized (root.items) {
				for (String p : root.items.keySet())
					if (matcher.apply(p))
						items.add(root.items.get(p));
			}
		}
		return items;
	}

	public StoreItem getItem(String path, Get strategy) {
		path = normalize(path);
		StoreItem item = root.items.get(path);
		if (item!=null) {
			if (strategy==Get.IfExists || strategy==Get.OrCreate)
				return item;
			if (strategy==Get.CreateAlternativeName)
				return createItem(path,true);
			if (strategy==Get.CreateOverwrite) {
				item.content = null;
				item.meta(Updated,null);
				return item;
			}
			return null;
		} else {
			if (strategy==Get.IfExists)
				return null;
			return createItem(path,false);
		}
	}

	private StoreItem createItem(String path, boolean alternativeName) {
		StoreItem item = new StoreItem();
		item.owner = this;
		if (alternativeName) {
			StoreItem existing = root.items.get(path);
			String name = existing.meta(DisplayName,String.class);
			Integer[] suffix = new Integer[1];
			item.path = makeAlternative(existing.path,suffix);
			item.meta(DisplayName,name+" ("+suffix[0]+")");
		} else {
			item.path = path;
			item.meta(DisplayName,new File(path).getName());
		}
		item.meta(Created,new Date());
		root.items.put(item.path,item);
		root.itemPaths = null;
		pathsChanged(new ArrayList(Arrays.asList(item.path)),null);
		return item;
	}

	private String makeAlternative(String path, Integer[] suffix) {
		File f = new File(path);
		File folder = f.getParentFile();
		String name = f.getName();
		String ext = "";
		int cut = name.lastIndexOf('.');
		if (cut>0) {
			ext = name.substring(cut);
			name = name.substring(0,cut);
		}
		for (int i=2; root.items.containsKey(normalize(f.getPath())); i++) {
			f = new File(folder,name+"-"+i+ext);
			if (suffix!=null) suffix[0] = i;
		}
		return f.getPath();
	}

	public boolean delete(String path, boolean recursive) {
		return delete(normalize(path),recursive,true);
	}

	private boolean delete(String path, boolean recursive, boolean self) {
		if (exists(path,true)) {
			root.itemsOld.put(path,root.items.remove(path));
			root.itemPaths = null;
			pathsChanged(null,new ArrayList(Arrays.asList(path)));
			return true;
		}
		if (exists(path,false)) {
			List<String> paths = new ArrayList();
			Set<String> items = root.itemPaths().get(path);
			if (!recursive && items!=null && items.size()>0)
				throw new RuntimeException("could not delete folder (not empty)");
			if (recursive && items!=null && items.size()>0) {
				for (String p : items)
					root.itemsOld.put(p,root.items.remove(p));
				paths.addAll(items);
			}
			if (self) {
				root.itemsOld.put(path,getItemMaybeVirtual(path));
				root.folders.remove(path);
				paths.add(path);
			}
			root.itemPaths = null;
			pathsChanged(null,paths);
			return true;
		}
		return false;
	}

	public boolean rename(String path, String name) {
		return move(path,resolve(name,path));
	}

	public boolean move(String pathFrom, String pathTo) {
		boolean targetIsFolder = pathTo.endsWith("/") || pathTo.endsWith("\\");
		pathFrom = normalize(pathFrom);
		pathTo = normalize(pathTo);
		if (!exists(pathFrom,false))
			return false;
		if (!targetIsFolder && exists(pathTo,false))
			return false;
		if (targetIsFolder && !exists(pathTo,false))
			return false;
		boolean selfItem = exists(pathFrom,true);
		boolean selfFolder = !selfItem && exists(pathFrom,false);
		List<StoreItem> descendants = Collections.EMPTY_LIST;
		Set<String> pathsOld = new LinkedHashSet();
		Set<String> pathsNew = new LinkedHashSet();
		if (selfItem) {
			StoreItem item = getItem(pathFrom,Get.IfExists); // self
			pathsOld.add(item.path);
			root.itemsOld.put(item.path,root.items.remove(item.path));
			if (targetIsFolder) {
				String rel = relativize(item.path,pathFrom);
				item.path = resolve(rel,pathTo+"/");
			} else item.path = pathTo;
			root.items.put(item.path,item);
			pathsNew.add(item.path);
		} else if (selfFolder) {
			for (String path : new ArrayList<String>(root.folders))
				if (path.equals(pathFrom) || path.startsWith(pathFrom+"/")) {
					root.folders.remove(path);
					root.folders.add(path.replace(pathFrom,pathTo));
				}
			pathsOld.add(pathFrom);
			pathsNew.add(pathTo);
			descendants = getItems(pathFrom+"/**");
		} else {
			descendants = getItems(pathFrom+"/**");
		}
		if (descendants.size()>0) {
			pathsOld.add(pathFrom);
			pathsNew.add(pathTo);
			String nameFrom = new File(pathFrom).getName();
			pathFrom = pathFrom+"/"; // resolve to a folder
			pathTo = pathTo+"/"; // resolve to a folder
			for (StoreItem item : descendants) {
				pathsOld.add(item.path);
				root.itemsOld.put(item.path,root.items.remove(item.path));
				String rel = relativize(item.path,pathFrom);
				if (selfFolder && targetIsFolder)
					rel = nameFrom+"/"+rel;
				item.path = resolve(rel,pathTo);
				root.items.put(item.path,item);
				pathsNew.add(item.path);
			}
		}
		if (pathsNew.size()>0) {
			root.itemPaths = null;
			pathsChanged(new ArrayList(pathsNew),new ArrayList(pathsOld));
			return true;
		} else return false;
	}

	public boolean exists(String path) {
		return exists(normalize(path),false);
	}

	protected boolean exists(String path, boolean file) {
		if (file)
			return root.items.containsKey(path);
		if (path.endsWith("/"))
			path = path.substring(0,path.length()-1);
		if (root.folders.contains(path))
			return true;
		return root.itemPaths().keySet().contains(path);
	}

	private Map<String,Set<String>> itemPaths;
	private Map<String,Set<String>> itemPaths() { // call at root!
		if (itemPaths!=null)
			return itemPaths;
		Map<String,Set<String>> paths = new LinkedHashMap();
		synchronized (items) {
			for (String i : items.keySet())
				for (String p=i; p.length()>0; p=p.substring(0,Math.max(0,p.lastIndexOf('/'))))
					MapUtils.groupT(paths,LinkedHashSet.class,p,i);
		}
		return itemPaths = paths;
	}

	/** Pass every user-given path through this!
	 * Result is resolved to base if relative, uses '/' and NOT starts/ends with '/' */
	protected String normalize(String path) {
		path = path.trim().replace('\\','/').replace("//","/");
		if (base!=null && base.length()>0 && !path.startsWith("/"))
			path = base + "/" + path;
		while (path.startsWith("/"))
			path = path.substring(1);
		while (path.endsWith("/"))
			path = path.substring(0,path.length()-1);
		return path;
	}

	public void setQuiet(boolean quiet) {
		this.quiet = quiet;
	}
	public void setBatch(boolean batch) {
		this.batch = batch;
	}

	protected void pathsChanged(List<String> pathsNew, List<String> pathsOld) {
		if (quiet)
			return;
		if (batch) {
			root.eventsPending.add("pathsChanged");
			root.eventsPending.add(pathsNew);
			root.eventsPending.add(pathsOld);
			return;
		}
		for (Listener l : listeners)
			l.pathsChanged(pathsNew,pathsOld);
	}

	protected void metaChanged(List<String> paths) {
		if (quiet)
			return;
		if (batch) {
			root.eventsPending.add("metaChanged");
			root.eventsPending.add(paths);
			return;
		}
		for (Listener l : listeners)
			l.metaChanged(paths);
	}

	protected void contentsChanged(List<String> paths) {
		if (quiet)
			return;
		if (batch) {
			root.eventsPending.add("contentsChanged");
			root.eventsPending.add(paths);
			return;
		}
		for (Listener l : listeners)
			l.contentsChanged(paths);
	}

	public void addListener(Listener l) {
		listeners.add(l);
	}

	public void removeListener(Listener l) {
		listeners.remove(l);
	}

	public static interface Listener {
		/** Lists are synchronous, if both are not null. */
		void pathsChanged(List<String> pathsNew, List<String> pathsOld);
		void metaChanged(List<String> paths);
		void contentsChanged(List<String> paths);
	}

	public static interface Access {
		<T> T get(Class<T> type);
		<T> T get(String key, Class<T> type);
		<T> T get(String key, T defaultValue);
		<T> void set(String key, T value);
	}

	private static EmptyAccess emptyAccess = new EmptyAccess();
	private static class EmptyAccess implements Access {
		public <T> T get(Class<T> type) { return null; }
		public <T> T get(String key, Class<T> type) { return null; }
		public <T> T get(String key, T defaultValue) { return null; }
		public <T> void set(String key, T value) {}
	}

	public static class StoreItem {
		private ItemStore owner;
		private String path;
		private String content;
		private Map<String,Object> meta;
		private int contentDirty;
		private int metaDirty;

		public StoreItem() {}
		private StoreItem(ItemStore owner, StoreItem item) {
			this.owner = owner;
			this.path = item.path;
			this.content = item.content;
			if (item.meta!=null && item.meta.size()>0)
				this.meta = new LinkedHashMap(item.meta);
		}

		public String path() { return path; }
		public void path(String path) { this.path = path; }

		public Access access() {
			Access access = owner!=null ? owner.getAccess(this) : null;
			return access!=null ? access : emptyAccess;
		}
		public String content() {
			return owner!=null ? owner.getContent(this) : content;
		}
		public void content(String content) {
			if (owner!=null) owner.setContent(this,content);
			else this.content = content;
		}

		public <T> T meta(String key, Class<T> type) {
			if (key==null && type==Map.class)
				return meta!=null ? (T)new LinkedHashMap(meta) : (T)new LinkedHashMap();
			if (owner!=null) return owner.getMeta(this,key,type);
			else return meta!=null ? (T)meta.get(key) : null;
		}
		public void meta(String key, Object value) {
			if (owner!=null) owner.setMeta(this,key,value);
			else (meta!=null ? meta : (meta = new LinkedHashMap())).put(key,value);
		}

		public void flush() {
			if (owner!=null) owner.flush(this);
		}

		@Override
		public String toString() {
			return path + " ("+meta+")";
		}
	}

	public static String relativize(String target, String base) {
		return relativize(target,base,"/");
	}
	public static String relativize(String target, String base, String pathsep) {
		String b = base.replace('\\','/');
		String t = target.replace('\\','/');
		String authority = null;
		if (t.startsWith("//")) {
			t = t.substring(2);
			int cut = t.indexOf('/');
			if (cut<0)
				return target; // authority only, nothing to relativize
			authority = t.substring(0,cut);
			t = t.substring(cut+1);
		}
		if (b.startsWith("//")) {
			b = b.substring(2);
			int cut = b.indexOf('/');
			String baseAuthority = cut<0 ? b : b.substring(0,cut);
			if (authority!=null && !authority.equals(baseAuthority))
				return target; // authorities don't match, do NOT relativize
			b = cut<0 ? "/" : b.substring(cut+1);
		}
		return relativizePath(t,b,pathsep);
	}
	public static String relativizePath(String target, String base, String pathsep) {
		boolean tAbsolute = target.startsWith("/");
		if (tAbsolute) return target;
		boolean bAbsolute = base.startsWith("/");
		boolean tFile = !target.endsWith("/");
		boolean bFile = !base.endsWith("/");
		if (bAbsolute) base = base.substring(1);
		String[] bc = base.split("/");
		String[] tc = target.split("/");
		int ups,downs = ups = 0;
		int index = 0;
		for (; index<tc.length && index<bc.length; index++)
			if (!tc[index].equals(bc[index]))
				break;
		StringBuilder result = new StringBuilder();
		for (int i=index; i<bc.length-(bFile ? 1 : 0); i++, ups++)
			result.append("..").append(pathsep);
		if (ups>=bc.length-(bFile ? 1 : 0))
			return pathsep+target;
		for (int i=index; i<tc.length-(tFile ? 1 : 0); i++, downs++)
			result.append(tc[i]).append(pathsep);
//		if (ups==downs)
//			return target;
		if (tFile)
			result.append(tc[tc.length-1]);
		String relativized = result.toString();
		return relativized.length()>target.length() ? target : relativized;
	}

	public static String resolve(String target, String base) {
		return resolve(target,base,"/");
	}
	public static String resolve(String target, String base, String pathsep) {
		String b = base.replace('\\','/');
		String t = target.replace('\\','/');
		String authority = null;
		if (t.startsWith("//"))
			return target; // authority implies absolute path
		if (b.startsWith("//")) {
			b = b.substring(2);
			int cut = b.indexOf('/');
			authority = cut<0 ? b : b.substring(0,cut);
			b = cut<0 ? "/" : b.substring(cut);
		}
		if (authority!=null) {
			String resolved = resolvePath(t,b,pathsep);
			if (resolved.startsWith(pathsep))
				resolved = resolved.substring(1);
			return pathsep+pathsep+authority+pathsep+resolved;
		} else return resolvePath(t,b,pathsep);
	}
	public static String resolvePath(String target, String base, String pathsep) {
		boolean tAbsolute = target.startsWith("/");
		if (tAbsolute) return target;
		boolean bAbsolute = base.startsWith("/");
		boolean tFile = !target.endsWith("/");
		boolean bFile = !base.endsWith("/");
		LinkedList<String> rc = new LinkedList();
		String[] bc = base.split("/");
		String[] tc = target.split("/");
		if (!tAbsolute)
			for (int i=0; i<bc.length-(bFile ? 1 : 0); i++)
				if (".".equals(bc[i])) continue;
				else if ("..".equals(bc[i]) && rc.size()>0) rc.removeLast();
				else rc.add(bc[i]);
		for (int i=0; i<tc.length-(tFile ? 1 : 0); i++)
			if (".".equals(tc[i])) continue;
			else if ("..".equals(tc[i]) && rc.size()>0) rc.removeLast();
			else rc.add(tc[i]);
		return String.join(pathsep,rc) + (tFile ? pathsep+tc[tc.length-1] : pathsep);
	}

	public static Function<String,Boolean> matcher(String pathPattern) {
		Pattern pp = from(pathPattern);
		return path -> pp.matcher(path).matches();
	}

	public static Pattern from(String pathPattern) {
		String key = pathPattern;
		Pattern ptn = ptncache.get(key);
		if (ptn==null) {
			pathPattern = pathPattern.replace(".","\\.");
			pathPattern = pathPattern.replace("**",".+?");
			pathPattern = pathPattern.replace("*","[^/]+");
			pathPattern = "(?i)^"+pathPattern+"$";
			try {
				ptn = Pattern.compile(pathPattern);
				ptncache.put(key,ptn);
			} catch (PatternSyntaxException ex) {
				return Pattern.compile(""); // match nothing
			}
		}
		return ptn;
	}

	private static Map<String,Pattern> ptncache = new LinkedHashMap() {
		@Override
		protected boolean removeEldestEntry(java.util.Map.Entry eldest) {
			return size() > 1000;
		}
	};
}
