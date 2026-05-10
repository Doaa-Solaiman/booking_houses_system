/* /////////////////////////////////////////////////////////////////////////////
 *
 * Created       :  14.12.2004 11:55:27 by kandzia
 * Project       :  de.scheller-sfc
 * CVS Ident     :  $Revision$, $Date$
 * Last Commiter :  $Author$
 *
 * Copyright (c) 2004-2009 Scheller Systemtechnik GmbH
 *                         Poeler Strasse 85a, 23970 Wismar, Germany
 *
 *//////////////////////////////////////////////////////////////////////////////
package de.scheller.platform.common;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.FileChannel;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.Stack;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * @author kandzia, lamprecht
 */
public class FileUtils
{
	/**
	 * Findet Dateien unterhalb (rekursiv) von <tt>path</tt>.
	 * Durchlaufene Verzeichnisse werden nicht im Ergebnis zur�ckgeliefert.
	 *
	 * @param path Startverzeichnis
	 * @return Array mit gefundenen <tt>{@link File}</tt>-Objekten
	 */
	public static File[] listFilesRecursive(File path) {
		LinkedList fileList = new LinkedList();
		listFilesRecursive(path,fileList,ALLFILES,false,false);
		return (File[])fileList.toArray(new File[fileList.size()]);
	}

	/**
	 * Findet Dateien unterhalb (rekursiv) von <tt>path</tt>, die
	 * vom Filter <tt>ff</tt> akzeptiert werden.
	 * Durchlaufene Verzeichnisse werden im Ergebnis zur�ckgeliefert, wenn sie
	 * vom Filter <tt>ff</tt> akzeptiert werden.
	 *
	 * @param path Startverzeichnis
	 * @param ff Dateifilter
	 * @param addDirs Verzeichnisse mit in Ergebnis aufnehmen?
	 * @return Array mit gefundenen <tt>{@link File}</tt>-Objekten
	 */
	public static File[] listFilesRecursive(
			File path, String pattern, boolean addDirs) {
		LinkedList fileList = new LinkedList();
		listFilesRecursive(path,fileList,new PatternFileFilter(pattern),addDirs,false);
		return (File[])fileList.toArray(new File[fileList.size()]);
	}

	/**
	 * Findet Dateien unterhalb (rekursiv) von <tt>path</tt>, die
	 * vom Filter <tt>ff</tt> akzeptiert werden.
	 * Durchlaufene Verzeichnisse werden im Ergebnis zur�ckgeliefert, wenn sie
	 * vom Filter <tt>ff</tt> akzeptiert werden.
	 *
	 * @param path Startverzeichnis
	 * @param ff Dateifilter
	 * @param addDirs Verzeichnisse mit in Ergebnis aufnehmen?
	 * @param stop Nicht tiefer in ein Verzeichnis laufen, das vom Dateifilter abgelehnt wurde?
	 * @return Array mit gefundenen <tt>{@link File}</tt>-Objekten
	 */
	public static File[] listFilesRecursive(
			File path, FileFilter ff, boolean addDirs, boolean stop) {
		LinkedList fileList = new LinkedList();
		listFilesRecursive(path,fileList,ff,addDirs,stop);
		return (File[])fileList.toArray(new File[fileList.size()]);
	}

	/**
	 * Findet Dateien unterhalb (rekursiv) von <tt>path</tt> und f�gt diese,
	 * wenn sie vom Filter <tt>ff</tt> akzeptiert werden, zur Liste
	 * <tt>fileList</tt> hinzu. Mittels <tt>addDirs</tt> kann bestimmt werden,
	 * ob die Verzeichnisse, durch die <tt>listFilesRecursive</tt> gelaufen
	 * ist, mit in die Ergebnisliste aufgenommen werden.
	 *
	 * @param path Startverzeichnis
	 * @param fileList Liste, in der <tt>{@link File}</tt>-Objekte hinzugef�gt werden
	 * @param ff Dateifilter
	 * @param addDirs Verzeichnisse mit in Ergebnis aufnehmen?
	 * @param stop Nicht tiefer in ein Verzeichnis laufen, das vom Dateifilter abgelehnt wurde?
	 */
	private static void listFilesRecursive(File f, List fileList,
			FileFilter ff, boolean addDirs, boolean stop) {
		if (ff.accept(f)) {
			if (!f.isDirectory() || addDirs) fileList.add(f);
		} else {
			if (stop) return;
		}
		File[] files = f.listFiles();
		if (files!=null)
			for (int i=0; i<files.length; i++)
				listFilesRecursive(files[i],fileList,ff,addDirs,stop);
	}

	public static File[] listFilesWildcard(File path) {
		File dir = path.getParentFile();
		String name = path.getName();
		return dir.listFiles(new PatternFileFilter(name,false));
	}

	public static File[] listFilesWildcardRecursive(File path) {
		LinkedList fileList = new LinkedList();
		File dir = path.getParentFile();
		String name = path.getName();
		listFilesRecursive(dir,fileList,new PatternFileFilter(name),false,false);
		return (File[])fileList.toArray(new File[fileList.size()]);
	}

	public static final FileFilter ALLFILES = new AllFiles();
	private static class AllFiles implements FileFilter {
		public boolean accept(File pathname) { return true; }
	}

	public static class PatternFileFilter implements FileFilter {
		private final Pattern pattern;
		private final boolean acceptDirectories;
		public PatternFileFilter(String pattern) { this(pattern,true); }
		public PatternFileFilter(String pattern, boolean acceptDirectories) {
			pattern = pattern.replaceAll("\\s*,\\s*","|");
			pattern = pattern.replace(".","\\.");
			pattern = pattern.replace("*",".*");
			this.pattern = Pattern.compile(pattern);
			this.acceptDirectories = acceptDirectories;
		}
		public boolean accept(File pathname) {
			if (acceptDirectories && pathname.isDirectory()) return true;
			return pattern.matcher(pathname.getName()).matches();
		}
	}

	public static boolean fileCopy(File source, File target) {
		try (FileInputStream in = new FileInputStream(source);
				FileOutputStream out = new FileOutputStream(target)) {
			FileChannel sourceChannel = in.getChannel();
			FileChannel targetChannel = out.getChannel();
			long size = sourceChannel.size();
			for (long pos=0; pos<size;)
				pos += targetChannel.transferFrom(sourceChannel,pos,size-pos);
			return true;
		} catch (IOException ex) {
			ex.printStackTrace();
			return false;
		}
	}

	public static List<String> getLines(File f) {
		try {
			return getLines(new FileReader(f));
		} catch (IOException ex) {
			ex.printStackTrace();
			return null;
		}
	}

	public static List<String> getLines(URL url) {
		try {
			URLConnection c = url.openConnection();
			c.setUseCaches(false);
			return getLines(new InputStreamReader(c.getInputStream()));
		} catch (IOException ex) {
			ex.printStackTrace();
			return null;
		}
	}

	public static List<String> getLines(Reader r) {
		ArrayList lines = new ArrayList();
		try {
			BufferedReader br = new BufferedReader(r);
			for (String s; (s = br.readLine())!=null;) lines.add(s);
			br.close();
		} catch (IOException ex) {
			ex.printStackTrace();
			return null;
		}
		return lines;
	}

	private static String upcaseHexChars = "0123456789ABCDEF";
	private static String forbiddenChars = "\\/:*?|\"<>";

	public static boolean isValidFilename(String filename) {
		return filename.split("["+forbiddenChars+"]").length==1;
	}

	public static String makeValidFilename(String filename) {
		for (int i=0; i<forbiddenChars.length(); i++) {
			char forbiddenChar = forbiddenChars.charAt(i);
			int lo = forbiddenChar & 15;
			int hi = forbiddenChar >> 4;
			String replacement = "%"+upcaseHexChars.charAt(hi)+upcaseHexChars.charAt(lo);
			filename = filename.replace(Character.toString(forbiddenChar),replacement);
		}
		return filename;
	}

	public static String makeValidFilename(String filename, String forbidden, String replacements) {
		for (int i=0; i<forbidden.length(); i++) {
			char forbiddenChar = forbidden.charAt(i);
			char replacementChar = replacements.charAt(i);
			filename = filename.replace(forbiddenChar,replacementChar);
		}
		return filename;
	}

	public static String getUniqueFilename() {
		return new Date().getTime() + "-" + (long)(1000000 * Math.random());
	}

//	public static void main(String[] args) {
//		System.out.println(forbiddenChars);
//		try {
//			System.out.println(URLEncoder.encode(forbiddenChars,"ISO-8859-1"));
//			System.out.println(makeValidFilename(forbiddenChars));
//			System.out.println(URLDecoder.decode(URLEncoder.encode(forbiddenChars,"ISO-8859-1"),"ISO-8859-1"));
//			System.out.println(URLDecoder.decode(makeValidFilename(forbiddenChars),"ISO-8859-1"));
//		} catch (UnsupportedEncodingException ex) {
//			ex.printStackTrace();
//		}
//	}

//	public static ArrayList<String> getFileList(File file) {
//		return getFileList(file,null,null,true);
//	}
//
//	public static ArrayList<String> getFileList(
//			File file, Pattern included, Pattern excluded) {
//		return getFileList(file,new Pattern[] { included },new Pattern[] { excluded },true);
//	}
//
//	public static ArrayList<String> getFileList(
//			File file, Pattern[] included, Pattern[] excluded) {
//		return getFileList(file,included,excluded,true);
//	}
//
//	private static ArrayList<String> getFileList(
//			File file, Pattern[] included, Pattern[] excluded, boolean root) {
//		if (file==null)
//			return new ArrayList();
//
//		ArrayList<String> filelist = new ArrayList();
//		if (file.isDirectory()) {
//			String[] names = file.list();
//			if (names!=null) {
//				for (String name : names) {
//					File nextFile = new File(file.getAbsolutePath() + File.separator + name);
//					ArrayList<String> dir = getFileList(nextFile,included,excluded,false);
//					for (String file_name : dir) {
//						if (root) {
//							// if the file is not accepted, don't process it further
//							if (!filter(file_name,included,excluded))
//								continue;
//						} else {
//							file_name = file.getName() + File.separator + file_name;
//						}
//						int filelist_size = filelist.size();
//						for (int i=0; i<filelist_size; i++) {
//							if (filelist.get(i).compareTo(file_name) > 0) {
//								filelist.add(i,file_name);
//								break;
//							}
//						}
//						if (filelist.size()==filelist_size)
//							filelist.add(file_name);
//					}
//				}
//			}
//		} else if (file.isFile()) {
//			String file_name = file.getName();
//			if (root) {
//				if (filter(file_name,included,excluded))
//					filelist.add(file_name);
//			} else filelist.add(file_name);
//		}
//		return filelist;
//	}

	public static boolean filter(String name, Pattern[] included, Pattern[] excluded) {
		if (name==null)
			return false;
		boolean accepted = false;
		if (included==null) {
			accepted = true;
		} else { // retain only the includes
			for (Pattern pattern : included) {
				if (pattern!=null && pattern.matcher(name).matches()) {
					accepted = true;
					break;
				}
			}
		}
		if (accepted && excluded != null) { // remove the excludes
			for (Pattern pattern : excluded) {
				if (pattern!=null && pattern.matcher(name).matches()) {
					accepted = false;
					break;
				}
			}
		}
		return accepted;
	}

	public static void moveFile(File source, File target) throws IOException {
		if (source==null) throw new IllegalArgumentException("source can't be null.");
		if (target==null) throw new IllegalArgumentException("target can't be null.");
		if (!source.exists()) throw new IOException(
				"The source file '" + source.getAbsolutePath() + "' does not exist.");
		copy(source,target);
		deleteFile(source);
	}

	/**
	 * Moves the content of <tt>source</tt> to <tt>target</tt>.
	 */
	public static void moveDirectory(File source, File target) throws IOException {
		if (source==null) throw new IllegalArgumentException("source can't be null.");
		if (target==null) throw new IllegalArgumentException("target can't be null.");
		if (!source.exists()) throw new IOException(
				"The source directory '" + source.getAbsolutePath() + "' does not exist.");
		// create target if it does not exist already
		if (!target.exists())
			target.mkdirs();
		for (String name : source.list()) {
			File sourceFile = new File(source,name);
			File targetFile = new File(target,name);
			if (sourceFile.isDirectory())
				moveDirectory(sourceFile,targetFile);
			else moveFile(sourceFile,targetFile);
		}
		deleteFile(source); // finished with this directory. delete it.
	}

	public static void deleteDirectory(File source) throws IOException {
		if (source==null) throw new IllegalArgumentException("source can't be null.");
		if (!source.exists()) throw new IOException(
				"The directory '" + source.getAbsolutePath() + "' does not exist");
		for (String name : source.list()) {
			File f = new File(source.getAbsolutePath() + File.separator + name);
			if (f.isDirectory())
				deleteDirectory(f);
			else deleteFile(f);
		}
		deleteFile(source); // finished with this directory. delete it.
	}

	private static final int BUFFERSIZE = 4*1024;

	public static void copy_(InputStream is, OutputStream os) throws IOException {
		byte[] bytes = new byte[BUFFERSIZE];
		for (int r=is.read(bytes); r!=-1; r=is.read(bytes)) os.write(bytes,0,r);
	}

	public static void copy_(Reader in, Writer out) throws IOException {
		char[] chars = new char[BUFFERSIZE];
		for (int r=in.read(chars); r!=-1; r=in.read(chars)) out.write(chars,0,r);
	}

	public static void copy(InputStream is, OutputStream os) throws IOException {
		if (is==null) throw new IllegalArgumentException("inputstream can't be null.");
		if (os==null) throw new IllegalArgumentException("outputstream can't be null.");
		try {
			copy_(is,os);
			os.close();
			is.close();
		} catch (IOException ex) {
			throw (IOException)new IOException(
					"Error during the copying of streams.").initCause(ex);
		}
	}

	public static void copy(InputStream is, File target) throws IOException {
		if (is==null) throw new IllegalArgumentException("inputstream can't be null.");
		if (target==null) throw new IllegalArgumentException("target can't be null.");
		try {
			FileOutputStream os = new FileOutputStream(target);
			copy_(is,os);
			os.close();
			is.close();
		} catch (IOException ex) {
			throw (IOException)new IOException(
					"Error while copying an input stream to file '" +
					target.getAbsolutePath() + "'.").initCause(ex);
		}
	}

	public static void copy(File source, OutputStream os) throws IOException {
		if (source==null) throw new IllegalArgumentException("source can't be null.");
		if (os==null) throw new IllegalArgumentException("outputstream can't be null.");
		try {
			FileInputStream is = new FileInputStream(source);
			copy_(is,os);
			os.close();
			is.close();
		} catch (IOException ex) {
			throw (IOException)new IOException(
					"Error while copying file '" + source.getAbsolutePath() +
					"' to an output stream.").initCause(ex);
		}
	}

	public static void copy(File source, File target) throws IOException {
		if (source==null) throw new IllegalArgumentException("source can't be null.");
		if (target==null) throw new IllegalArgumentException("target can't be null.");
		try {
			FileInputStream is = new FileInputStream(source);
			FileOutputStream os = new FileOutputStream(target);
			copy_(is,os);
			os.close();
			is.close();
		} catch (IOException ex) {
			throw (IOException)new IOException(
					"Error while copying file '" + source.getAbsolutePath() +
					"' to file '" + target.getAbsolutePath() + "'.").initCause(ex);
		}
	}

	public static void copy(Reader r, Writer w) throws IOException {
		if (r==null) throw new IllegalArgumentException("reader can't be null.");
		if (w==null) throw new IllegalArgumentException("writer can't be null.");
		try {
			copy_(r,w);
		} catch (IOException ex) {
			throw (IOException)new IOException(
					"Error during the copying of streams.").initCause(ex);
		}
	}

	public static ByteArrayOutputStream readStream(InputStream is, int bufsize) throws IOException {
		if (is==null) throw new IllegalArgumentException("inputstream can't be null.");
		try {
			byte[] b = new byte[bufsize];
			ByteArrayOutputStream os = new ByteArrayOutputStream(b.length);
			for (int r=is.read(b); r!=-1; r=is.read(b)) os.write(b,0,r);
			os.close();
			is.close();
			return os;
		} catch (IOException ex) {
			throw (IOException)new IOException(
					"Error while reading the complete contents of an input stream.").initCause(ex);
		}
	}

	public static byte[] readBytes(InputStream is) throws IOException {
		if (is==null) throw new IllegalArgumentException("inputstream can't be null.");
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		copy(is,os);
		return os.toByteArray();
	}

	public static byte[] readBytes(URL source) throws IOException {
		if (source==null) throw new IllegalArgumentException("source can't be null.");
		InputStream is = null;
		try {
			URLConnection c = source.openConnection();
			c.setUseCaches(false);
			is = c.getInputStream();
			return readBytes(is);
		} catch (IOException ex) {
			throw (IOException)new IOException(
					"Error while reading url '" + source.toString() + ".").initCause(ex);
		} finally {
			if (is!=null) is.close();
		}
	}

	public static byte[] readBytes(File source) throws IOException {
		if (source==null) throw new IllegalArgumentException("source can't be null.");
		InputStream is = null;
		try {
			is = new FileInputStream(source);
			return readBytes(is);
		} catch (IOException ex) {
			throw (IOException)new IOException(
					"Error while reading file '" + source.getAbsolutePath() + ".").initCause(ex);
		} finally {
			if (is!=null) is.close();
		}
	}

	public static void writeBytes(byte[] content, File target) throws IOException {
		writeBytes(content,target,false);
	}

	public static void writeBytes(byte[] content, File target, boolean append) throws IOException {
		if (content==null) throw new IllegalArgumentException("content can't be null.");
		if (target==null) throw new IllegalArgumentException("target can't be null.");
		try {
			FileOutputStream os = new FileOutputStream(target,append);
			os.write(content);
			os.flush();
			os.close();
		} catch (IOException ex) {
			throw (IOException)new IOException(
					"Error while write a string to '" +
					target.getAbsolutePath() + ".").initCause(ex);
		}
	}

	public static String readString(Reader r) throws IOException {
		if (r==null) throw new IllegalArgumentException("reader can't be null.");
		StringWriter w = new StringWriter();
		copy_(r,w);
		return w.toString();
	}

	public static String readString(InputStream is) throws IOException {
		if (is==null) throw new IllegalArgumentException("inputstream can't be null.");
		try {
			ByteArrayOutputStream os = new ByteArrayOutputStream(BUFFERSIZE*2);
			copy_(is,os);
			return os.toString();
		} catch (FileNotFoundException ex) {
			throw ex;
		} catch (IOException ex) {
			throw (IOException)new IOException(
					"Error while reading the complete contents of an reader.").initCause(ex);
		}
	}

	public static String readString(InputStream is, String encoding) throws IOException {
		if (is==null) throw new IllegalArgumentException("inputstream can't be null.");
		try {
			ByteArrayOutputStream os = new ByteArrayOutputStream(BUFFERSIZE*2);
			copy_(is,os);
			return os.toString(encoding);
		} catch (UnsupportedEncodingException ex) {
			throw (IOException)new IOException(
					"Encoding '" + encoding + "' is not supported.").initCause(ex);
		} catch (IOException ex) {
			throw (IOException)new IOException(
					"Error while reading the complete contents of an reader.").initCause(ex);
		}
	}

	public static String readString(URL source) throws IOException {
		return readString(source,null);
	}

	public static String readString(URL source, String encoding) throws IOException {
		if (source==null) throw new IllegalArgumentException("source can't be null.");
		try {
			URLConnection c = source.openConnection();
			c.setUseCaches(false);
			InputStream is = c.getInputStream();
			String content = encoding==null ? readString(is) : readString(is,encoding);
			is.close();
			return content;
		} catch (FileNotFoundException ex) {
			throw ex;
		} catch (IOException ex) {
			throw (IOException)new IOException(
					"Error while reading url '" + source.toString() + ".").initCause(ex);
		}
	}

	public static String readString(File source) throws IOException {
		return readString(source,null);
	}

	public static String readString(File source, String encoding) throws IOException {
		if (source==null) throw new IllegalArgumentException("source can't be null.");
		try {
			FileInputStream is = new FileInputStream(source);
			String content = encoding==null ? readString(is) : readString(is,encoding);
			is.close();
			return content;
		} catch (IOException ex) {
			throw (IOException)new IOException(
					"Error while reading url '" + source.getAbsolutePath() + ".").initCause(ex);
		}
	}

	public static void writeString(String content, File target) throws IOException {
		writeString(content,target,false);
	}

	public static void writeString(String content, File target, boolean append) throws IOException {
		if (content==null) throw new IllegalArgumentException("content can't be null.");
		if (target==null) throw new IllegalArgumentException("target can't be null.");
		try {
			FileWriter w = new FileWriter(target,append);
			w.write(content);
			w.flush();
			w.close();
		} catch (IOException ex) {
			throw (IOException)new IOException(
					"Error while write a string to '" +
					target.getAbsolutePath() + ".").initCause(ex);
		}
	}

	public static void writeString(String content, File target, String encoding) throws IOException {
		writeString(content,target,encoding,false);
	}

	public static void writeString(String content, File target, String encoding, boolean append) throws IOException {
		if (content==null) throw new IllegalArgumentException("content can't be null.");
		if (target==null) throw new IllegalArgumentException("target can't be null.");
		try {
			FileOutputStream os = new FileOutputStream(target,append);
			OutputStreamWriter w = new OutputStreamWriter(os,encoding);
			w.write(content);
			w.flush();
			w.close();
		} catch (UnsupportedEncodingException ex) {
			throw (IOException)new IOException(
					"Encoding '" + encoding + "' is not supported.").initCause(ex);
		} catch (IOException ex) {
			throw (IOException)new IOException(
					"Error while write a string to '" +
					target.getAbsolutePath() + ".").initCause(ex);
		}
	}

	public static void deleteFile(File f) {
		if (f==null) throw new IllegalArgumentException("file can't be null.");
		if (!f.delete())
			f.deleteOnExit();
	}

	public static void unzipFile(File source, File target) throws IOException {
		if (source==null) throw new IllegalArgumentException("source can't be null.");
		if (target==null) throw new IllegalArgumentException("target can't be null.");
		ZipFile zipFile = null;
		try {
			zipFile = new ZipFile(source);
		} catch (IOException ex) {
			throw (IOException)new IOException(
					"Error while creating the zipfile '" +
					source.getAbsolutePath() + "'.").initCause(ex);
		}
		Enumeration entries = zipFile.entries();
		while (entries.hasMoreElements()) {
			ZipEntry entry = (ZipEntry)entries.nextElement();

			InputStream is;
			try {
				is = zipFile.getInputStream(entry);
			} catch (IOException ex) {
				throw (IOException)new IOException(
						"Error while obtaining the inputstream for entry '" +
						entry.getName() + "'.").initCause(ex);
			}

			String filename = target.getAbsolutePath() + File.separator +
					entry.getName().replace('/',File.separatorChar);
			File f = new File(filename);
			StringBuilder dirname = new StringBuilder(f.getPath());
			dirname.setLength(dirname.length() -
					f.getName().length() - File.separator.length());
			File dir = new File(dirname.toString());

			if (!dir.exists()) {
				if (!dir.mkdirs())
					throw new IOException("Couldn't create directory '" +
							dir.getAbsolutePath() + "' and its parents.");
			} else {
				if (!dir.isDirectory())
					throw new IOException("Destination '" +
							dir.getAbsolutePath() + "' exists and is not a directory.");
			}

			FileOutputStream os = null;
			try {
				os = new FileOutputStream(filename);
			} catch (IOException ex) {
				throw (IOException)new IOException(
						"Error while creating the output stream for file '" +
						filename + "'.").initCause(ex);
			}
			try {
				byte[] b = new byte[BUFFERSIZE];
				for (int r=is.read(b); r!=-1; r=is.read(b)) os.write(b,0,r);
			} catch (IOException ex) {
				throw (IOException)new IOException(
						"Error while uncompressing entry '" +
						filename + "'.").initCause(ex);
			} finally {
				try {
					os.close();
				} catch (IOException ex) {
					throw (IOException)new IOException(
							"Error while closing the output stream for file '" +
							filename + "'.").initCause(ex);
				} finally {
					try {
						is.close();
					} catch (IOException ex) {
						throw (IOException)new IOException(
								"Error while closing the input stream for entry '" +
								entry.getName() + "'.").initCause(ex);
					}
				}
			}
		}
		try {
			zipFile.close();
		} catch (IOException ex) {
			throw (IOException)new IOException(
					"Error while closing the zipfile '" +
					source.getAbsolutePath() + "'.").initCause(ex);
		}
	}

	public static String getBaseName(File f) {
		return getBaseName(f.getName());
	}

	public static String getBaseName(String filename) {
		if (filename==null)
			throw new IllegalArgumentException("filename can't be null.");
		int i = filename.lastIndexOf('.');
		if (i>=0 && i<filename.length()-1)
			return filename.substring(0,i);
		return null;
	}

	public static String getExtension(File f) {
		return getExtension(f.getName());
	}

	public static String getExtension(String filename) {
		if (filename==null)
			throw new IllegalArgumentException("filename can't be null.");
		int i = filename.lastIndexOf('.');
		if (i>=0 && i<filename.length()-1) {
			String ext = filename.substring(i+1).toLowerCase();
			i = ext.lastIndexOf(File.separatorChar);
			return i<0 ? ext : null;
		}
		return null;
	}

	public static File getFile(Class owner, String filename) {
		CodeSource s = owner.getProtectionDomain().getCodeSource();
		URL location = s.getLocation();
		boolean devmode = "file".equals(location.getProtocol()) &&
				location.getPath().endsWith("/bin/");
		File p = devmode ? new File(location.getPath()).getParentFile() : null;
		return new File(p,filename);
	}

	public static File relocate(File f, File from, File to) {
		Stack<String> s = new Stack();
		if (f!=null && !f.equals(from)) do {
			s.push(f.getName());
			f = f.getParentFile();
		} while (f!=null && !f.equals(from));
		while (!s.isEmpty())
			to = new File(to,s.pop());
		return to!=null ? to : new File("");
	}

	public static File base(File f1, File f2) {
		Stack<String> s1 = stack(f1);
		Stack<String> s2 = stack(f2);
		File base = null;
		while (!s1.isEmpty() && !s2.isEmpty() && s1.peek().equals(s2.peek())) {
			base = new File(base,s1.pop()); s2.pop();
		}
		return base;
	}

	public static Stack<String> stack(File f) {
		Stack<String> s = new Stack();
		for (; f!=null; f=f.getParentFile())
			s.push(f.getParentFile()!=null ? f.getName() : f.getPath());
		return s;
	}

	private static HashMap<String,Set<String>> MIME_TYPES;

	public static String getMimeType(String filename) {
		if (filename==null)
			return null;
		initMimeTypes();
		String ext = getExtension(filename);
		Set<String> mimeTypes = MIME_TYPES.get(ext);
		if (mimeTypes==null || mimeTypes.isEmpty())
			return null;
		return mimeTypes.iterator().next();
	}

	public static Set<String> getMimeTypes(String filename) {
		if (filename==null)
			return null;
		initMimeTypes();
		String ext = getExtension(filename);
		Set<String> mimeTypes = MIME_TYPES.get(ext);
		if (mimeTypes==null || mimeTypes.isEmpty())
			return null;
		return mimeTypes;
	}

	public static Set<String> getMainTypes(String filename) {
		if (filename==null)
			return null;
		initMimeTypes();
		Set<String> fileMainTypes = new HashSet();
		Set<String> fileMimeTypes = getMimeTypes(filename);
		for (String mt : fileMimeTypes) {
			int cut = mt.indexOf('/');
			if (cut<0) fileMainTypes.add(mt);
			else fileMainTypes.add(mt.substring(0,cut));
		}
		return fileMainTypes;
	}

	public static boolean matchesMimeType(String filename, String mimetype) {
		if (filename==null)
			return false;
		Set<String> fileMimeTypes = getMimeTypes(filename);
		if (mimetype.endsWith("/*")) {
			String match = mimetype.replaceFirst("\\*$","");
			for (String mt : fileMimeTypes)
				if (mt.startsWith(match)) return true;
		}
		return fileMimeTypes.contains(mimetype);
	}

	public static void initMimeTypes() {
		if (MIME_TYPES==null) {
			MIME_TYPES = new HashMap();
			try {
				URL url = FileUtils.class.getResource("mimetypes.txt");
				BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream()));
				for (String line; (line=in.readLine())!=null;) {
					if (line.trim().length()==0) continue;
					if (line.trim().startsWith("#")) continue;
					String[] parts = line.split("\\s+",2);
					String mimetype = parts[0].toString();
					String[] exts = parts[1].split("\\s+");
					for (String ext : exts) {
						Set<String> mimetypes = MIME_TYPES.get(ext);
						if (mimetypes==null) MIME_TYPES.put(ext,mimetypes = new LinkedHashSet());
						mimetypes.add(mimetype);
					}
				}
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		}
	}
}
