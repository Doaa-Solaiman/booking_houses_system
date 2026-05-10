package de.scheller.platform.common;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * @author kandzia
 */
public class ClassUtils
{
	ClassUtils() {}

	public static URI getClassesRootURI() {
		try {
			String cwd = new File("").getAbsolutePath().replace('\\','/');
			if (!cwd.startsWith("/")) cwd = "/"+cwd;
			List<URL> urls = Collections.list(ClassLoader.getSystemClassLoader().getResources("META-INF"));
			for (URL u : urls) {
				String p = u.getPath();
				if (urls.size()==1 || p.startsWith(cwd) || p.startsWith("file:"+cwd))
					return URI.create(u.toString().replaceFirst("META-INF$",""));
			}
			return null;
		} catch (IOException ex) {
			return null;
		}
	}

	public static Path getClassesRootPath() throws IOException {
		URI root = getClassesRootURI();
		if (root==null) return null;
		if (root.toString().startsWith("jar:"))
			try {
				return FileSystems.getFileSystem(root).getPath("/");
			} catch (FileSystemNotFoundException ex) {
				return FileSystems.newFileSystem(root,Collections.emptyMap()).getPath("/");
			}
		return Paths.get(root);
	}

	public static List<Class> getClasses(Path root, String pkgName, boolean recursive) throws IOException {
		String p = pkgName.replace('.','/');
		Stream<Path> allPaths = recursive ? Files.walk(root.resolve(p)) : Files.list(root.resolve(p));
		try {
			List<Class> allClasses = new ArrayList();
			allPaths.filter(Files::isRegularFile).forEach(file -> {
				try {
					String name = root.relativize(file).toString();
					name = name.replace('/','.').replace('\\','.');
					name = name.substring(0,name.length()-".class".length());
					allClasses.add(Class.forName(name));
				} catch (ClassNotFoundException ignore) {}
			});
			return allClasses;
		} finally {
			allPaths.close();
		}
	}
}
