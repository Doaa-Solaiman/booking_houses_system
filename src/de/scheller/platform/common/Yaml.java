package de.scheller.platform.common;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.Reader;
import java.util.Map;

/**
 * @author kandzia
 */
public class Yaml
{
	public static Map<String,Object> read(Class c, String resourceName) {
		return read(c.getClassLoader().getResourceAsStream(resourceName));
	}
	public static Map<String,Object> read(File file) throws FileNotFoundException {
		return read(new FileInputStream(file));
	}
	public static Map<String,Object> read(InputStream is) {
		return new org.yaml.snakeyaml.Yaml().load(is);
	}
	public static Map<String,Object> read(Reader r) {
		return new org.yaml.snakeyaml.Yaml().load(r);
	}
	public static Map<String,Object> read(String yaml) {
		return new org.yaml.snakeyaml.Yaml().load(yaml);
	}
}
