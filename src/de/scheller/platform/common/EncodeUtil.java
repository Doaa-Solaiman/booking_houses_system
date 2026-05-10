package de.scheller.platform.common;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * @author kandzia, kunze
 */
public class EncodeUtil
{
	public static String getEncodedString(String string, boolean gzip) {
		try {
			if (string==null)
				return null;
			if (string.length()<100)
				return string;
			String encoded = getEncodedString(string.getBytes("utf8"),gzip);
			return encoded!=null ? encoded : string;
		} catch (UnsupportedEncodingException ignore) {
			return null;
		}
	}

	public static String getEncodedString(byte[] data, boolean gzip) {
		String type = null;
		if (gzip) type = "base64-gzip";
		else type = "base64";
		String[] steps = type.split("-");
		if (steps.length==0)
			return null;
		try {
			int originalLength = data.length;
			for (int i=steps.length-1; i>=0; i--) {
				String step = steps[i];
				if ("base64".equals(step)) {
					data = Base64.encodeToByte(data,true);
				} else if ("gzip".equals(step)) {
					ByteArrayOutputStream os = new ByteArrayOutputStream();
					GZIPOutputStream zos = new GZIPOutputStream(os);
					ByteArrayInputStream is = new ByteArrayInputStream(data);
					copy(is,zos);
					zos.close();
					data = os.toByteArray();
				}
			}
			byte[] t = ("#!"+type+"\r\n").getBytes("utf8");
			byte[] result = new byte[t.length+data.length];
			System.arraycopy(t,0,result,0,t.length);
			System.arraycopy(data,0,result,t.length,data.length);
			if (gzip && result.length>=originalLength)
				return null;
			return new String(result,"utf8");
		} catch (IOException ignore) {
			return null;
		}
	}

	public static String getDecodedString(String string) {
		try {
			byte[] decoded = getDecodedBytes(string);
			return decoded!=null ? new String(decoded,"utf8") : string;
		} catch (UnsupportedEncodingException ignore) {
			return null;
		}
	}

	private static final List<String> SupportedDecodeKeywords = Arrays.asList("base64","gzip","file");

	public static byte[] getDecodedBytes(String string) {
		return getDecodedBytes(string,null);
	}

	public static byte[] getDecodedBytes(String string, String type) {
		if (string==null)
			return null;
		if (type==null)
			type = getEncodingType(string);
		if (type==null)
			return null;
		try {
			String orig = string;
			byte[] data = removeEncodingType(string).getBytes("utf8");
			String[] steps = type.split("-");
			for (int i=0; i<steps.length; i++) {
				String step = steps[i];
				if (!SupportedDecodeKeywords.contains(step))
					return orig.getBytes("utf8");
			}
			for (int i=0; i<steps.length; i++) {
				String step = steps[i];
				if ("base64".equals(step)) {
					data = Base64.decode(data);
				} else if ("gzip".equals(step)) {
					GZIPInputStream zis = new GZIPInputStream(new ByteArrayInputStream(data));
					ByteArrayOutputStream os = new ByteArrayOutputStream(data.length*2);
					copy(zis,os);
					zis.close();
					os.close();
					data = os.toByteArray();
				}
			}
			return data;
		} catch (IOException ignore) {
			return null;
		}
	}

	public static String removeEncodingType(String dataStr) {
		if (dataStr.startsWith("#!"))
			return dataStr.replaceFirst("(?ms)[^\r\n]*","").trim();
		return dataStr;
	}

	public static final Pattern AfterFirstLinePattern = Pattern.compile("(?ms)(\r\n|\r|\n).*");

	public static String getEncodingType(String dataStr) {
		dataStr = dataStr.substring(0,Math.min(100,dataStr.length()));
		String firstLine = AfterFirstLinePattern.matcher(dataStr).replaceFirst("");
		if (firstLine.startsWith("#!"))
			return firstLine.substring(2);
		return null;
	}

	public static void copy(InputStream is, OutputStream os) throws IOException {
		byte[] bytes = new byte[4096];
		for (int r=is.read(bytes); r!=-1; r=is.read(bytes)) os.write(bytes,0,r);
	}
}
