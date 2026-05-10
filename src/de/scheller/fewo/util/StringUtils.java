/* /////////////////////////////////////////////////////////////////////////////
 *
 * Created       :  06.12.2004 11:55:27 by kandzia
 * Project       :  de.scheller-sfc
 * CVS Ident     :  $Revision$, $Date$
 * Last Commiter :  $Author$
 *
 * Copyright (c) 2004-2007 Scheller Systemtechnik GmbH
 *                         Poeler Strasse 85a, 23970 Wismar, Germany
 *
 *//////////////////////////////////////////////////////////////////////////////
package de.scheller.fewo.util;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author kandzia, orfert
 */
public class StringUtils
{
	public static String replaceLast(String input, String regex, String replacement) {
		Pattern pattern = Pattern.compile(regex);
		Matcher matcher = pattern.matcher(input);
		if (!matcher.find())
		return input;
		int lastMatchStart = 0;
		do {
		lastMatchStart = matcher.start();
		} while (matcher.find());
		matcher.find(lastMatchStart);
		StringBuffer sb = new StringBuffer(input.length());
		matcher.appendReplacement(sb,replacement);
		matcher.appendTail(sb);
		return sb.toString();
	}

	public static int countChars(String str, char ch) {
		char[] s = str.toCharArray();
		int n = 0;
		for (int i=0; i<s.length; i++) if (ch==s[i]) n++;
		return n;
	}

	public static String[] regexSplit(String str, String regex, Integer... group) {
		List parts = new ArrayList();
		Pattern p = Pattern.compile(regex);
		Matcher m = p.matcher(str);
		while (m.find()) {
			for (int i=0; i<group.length; i++) {
				int g = group[i];
				String s = m.group(g);
				if (s!=null && s.length()!=0 || i+1==group.length) {
					parts.add(s);
					break;
				}
			}
		}
		return (String[])parts.toArray(new String[parts.size()]);
	}

	public static String[] smartSplit(String str) {
		List parts = smartSplitToList(str);
		return (String[])parts.toArray(new String[parts.size()]);
	}

	/**
	 * Splits a string with whitespace ({@link #isWhitespace(char)}) as
	 * delimiter. Quoted strings (',") will not be split.
	 */
	public static List smartSplitToList(String str) {
		ArrayList e = new ArrayList();
		StringBuilder t = new StringBuilder();
		boolean inString = false;
		char ss = ' '; // start/stop char (quotes)
		char[] s = str.toCharArray();
		for (int i=0; i<s.length; i++) {
			char c = s[i];
			if (inString) { if (c=='\\') c = s[++i]; // escape sequence
							else if (c==ss) { inString = false; continue; } // string end
			} else { if (c=='\'' || c=='"') { inString = true; ss = c; continue; } // string start
					if (isWhitespace(c)) { e.add(new String(t)); t = new StringBuilder(); continue; } }  // delimiter
			t.append(c);
		}
		e.add(new String(t));
		return e;
	}

	/**
	 * Returns <tt>true</tt> if <tt>c</tt> is whitespace (space, \t tab,
	 * \n newline, \r carriage return, \f formfeed).
	 */
	private static boolean isWhitespace(char c) {
		return c==' ' || c=='\t' || c=='\n' || c=='\r' || c=='\f';
	}

	public static String[] smartSplit(String str, char splitter) {
		return smartSplit(str,splitter,true);
	}

	public static String[] smartSplit(String str, char splitter, boolean ignoreWs) {
		ArrayList e = new ArrayList();
		StringBuilder t = new StringBuilder();
		boolean inString = false;
		char ss = ' '; // start/stop char (quotes)
		char[] s = str.toCharArray();
		for (int i=0; i<s.length; i++) {
			char c = s[i];
			if (inString) { if (c=='\\') c = s[++i]; // escape sequence
							else if (c==ss) { inString = false; continue; } // string end
			} else { if (c=='\'' || c=='"') { inString = true; ss = c; continue; } // string start
					if (c==splitter) { e.add(new String(t)); t = new StringBuilder(); continue; }
					if (ignoreWs && isWhitespace(c)) continue; // ignore whitespaces
			}
			t.append(c);
		}
		e.add(new String(t));
		return (String[])e.toArray(new String[e.size()]);
	}

	public static String smartWhitespaceReplace(String str) {
		StringBuilder t = new StringBuilder();
		char inString = ' ';
		boolean masked = false;
		boolean wasWhiteSpace=false;
		char[] s = str.toCharArray();

		for (int i=0; i<s.length; i++) {
			char c = s[i];
			if (inString == '\'' || inString== '"') {
				if (c=='\\') masked = !masked; // escape sequence
				else {
					if (!masked) {
						if (c==inString) { // end String
							inString = ' ';
						}
					} else masked=false;
				}
				wasWhiteSpace=false;
			} else {
				if (c=='\'' || c=='"') { // string start
					inString = c;
					wasWhiteSpace=false;
				} else {
					if (isWhitespace(c)) {
						if (!wasWhiteSpace) {
							t.append(' ');
							wasWhiteSpace = true;
							continue;
						}
					} else wasWhiteSpace=false;
				}
			}
			t.append(c);
		}
		return t.toString();
	}

	public static String mapAsString(Map m, boolean quoted) {
		if (m==null || m.size()==0) return null;
		StringBuilder sb = new StringBuilder();
		for (Iterator it = m.entrySet().iterator(); it.hasNext();) {
			Map.Entry e = (Map.Entry)it.next();

			if (sb.length()>0) sb.append(',');
			sb.append(e.getKey()).append('=');
			if (quoted) sb.append('\'');
			sb.append(e.getValue());
			if (quoted) sb.append('\'');
		}
		return sb.toString();
	}

	public static Map stringAsMap(String s, boolean isQuoted) {
		Map m = new HashMap();

		String[] as = isQuoted ? smartSplit(s,',') : s.split("\\,");

		for (int i = 0; i < as.length; i++) {
			String[] p = as[i].split("=",2);
			m.put(p[0], p.length==2 ? p[1]: null);
		}
		return m;
	}

	public static String defaultUrlCharEncoding = "ISO-8859-1";

	public static String toUrlQueryString(Map<String,Object> args) {
		return toUrlQueryString(args,defaultUrlCharEncoding);
	}
	public static String toUrlQueryString(Map<String,Object> args, String enc) {
		try {
			StringBuilder sb = new StringBuilder();
			for (Map.Entry<String,Object> e : args.entrySet()) {
				if (sb.length()>0)
					sb.append('&');
				sb.append(e.getKey());
				Object value = e.getValue();
				if (value!=null)
					sb.append('=').append(URLEncoder.encode(value.toString(),enc));
			}
			return sb.toString();
		} catch (UnsupportedEncodingException ex) {
			return null;
		}
	}

	public static Map<String,String> parseUrlQueryString(String qs) {
		return parseUrlQueryString(qs,defaultUrlCharEncoding);
	}
	public static Map<String,String> parseUrlQueryString(String qs, String enc) {
		if (qs==null)
			return Collections.EMPTY_MAP;
		try {
			if (qs.startsWith("?")) qs = qs.substring(1); // cut '?'
			String[] nameValuePairs = qs.split("&");
			Map params = new LinkedHashMap(nameValuePairs.length);
			for (int i=0; i<nameValuePairs.length; i++) {
				String[] p = nameValuePairs[i].split("=",2);
				params.put(p[0],p.length>1 ? URLDecoder.decode(p[1],enc) : null);
			}
			return params;
		} catch (UnsupportedEncodingException ex) {
			return null;
		}
	}

	public static int smartIndexOf(String base, String search, boolean ignoreCase) {

		if (base==null || search==null) return -1;

		int sbLen = base.length();
		int swLen = search.length();

		if (swLen > sbLen) return -1;

		int searchEnd = sbLen - swLen;
		boolean isMasked = false;
		char lastChar = ' ';

		for (int i=0; i<searchEnd; i++) {
			if(lastChar !='\\' && base.charAt(i)=='\'' || base.charAt(i)=='"')
				isMasked = !isMasked;
			if (!isMasked) {
				if (ignoreCase) {
					if (search.equalsIgnoreCase(base.substring(i, i+swLen)))
						return i;
				} else {
					if (search.equals(base.substring(i, i+swLen)))
						return i;
				}
			}
		}
		return -1;
	}

	public static String replaceKeysInText(String text, Map m) {
		if (text==null) return null;
		if (m==null) return text;

		StringBuilder output = new StringBuilder();
		String open = "${";
		String close = "}";
		for(;;) {
			int idx = text.indexOf(open);
			int idxEnd = text.indexOf(close,idx);
			if (idx==-1 || idxEnd==-1) break;
			String key = text.substring(idx+2,idxEnd); // +2 skips '${'
			output.append(text.substring(0,idx));

			Object value = m.get(key);
			output.append(value!=null ? value.toString() : open+key+close);

			text = text.substring(idxEnd+1,text.length()); // +1 skips '}'
		}
		output.append(text);
		return output.toString();
	}

	public static String makeXmlNCName(String s) {
		if (s==null) return null;
		if (s.length()==0) return s;
		s = s.replace('\u00b2','2');
		s = s.replace('\u00b3','3');
		Pattern p = Pattern.compile("[\\s:;,.@/\\\\~#'\\\"+*?!=§$%&()\\[\\]{}<>\\|²³](\\w{0,1})");
		Matcher m = p.matcher(s);
		StringBuffer sb = new StringBuffer();
		while (m.find()) m.appendReplacement(sb,m.group(1).toUpperCase());
		m.appendTail(sb);
		s = sb.toString();
		if (s.length()==0) return s;
		if (s.charAt(0)=='.') s = s.substring(1);
		if (s.charAt(0)=='-') s = s.substring(1);
		return s;
	}

	public static String makeJavaIdentifier(String s) {
		if (s==null) return null;
		if (s.length()==0) return s;
		s = s.replace('\u00b2','2');
		s = s.replace('\u00b3','3');
		s = s.replaceAll("[:;,.@/\\\\~#'\\\"+\\-*?!=§$%&()\\[\\]{}<>\\|²³]"," ");
		s = s.replaceAll("[A-Z]"," $0");
		String[] p = s.trim().split("\\s+");
		StringBuilder sb = new StringBuilder();
		for (int i=0; i<p.length; i++) {
			if (i==0) sb.append(Character.toLowerCase(p[i].charAt(0)));
			else sb.append(Character.toUpperCase(p[i].charAt(0)));
			sb.append(p[i].substring(1).toLowerCase());
		}
		return sb.toString();
	}

	public static String unquote(String s) {
		if (s==null) return null;
		if (s.length()<3) return s;
		char q = s.charAt(0);
		if (q!='"' && q!='\'' && q!='`') return s;
		return s.substring(1,s.length()-1);
	}

	public static String replaceBackslash(String s) {
		if (s==null) return null;
		if (s.length()<2) return s;
		s = s.replaceAll("[\\x20]?\\\\[\\x20]?", " " + String.valueOf((char)Character.LINE_SEPARATOR));
		return s;
	}

	public static String addToken(String s, String token) {
		if (s==null) return token;
		List tokens = smartSplitToList(s);
		tokens.add(token);
		return join(tokens.iterator(),' ');
	}

	public static String removeToken(String s, String token) {
		List tokens = smartSplitToList(s);
		tokens.remove(s);
		if (tokens.isEmpty()) return null;
		return join(tokens.iterator(),' ');
	}

	public static boolean containsToken(String s, String token) {
		if (s==null || token==null) return false;
		if (s.length()==0 || token.length()==0) return false;
		List tokens = smartSplitToList(s);
		return tokens.contains(token);
	}

	public static String join(Iterator it, char delimiter) {
		StringBuilder sb = new StringBuilder();
		for (int i=0; it.hasNext(); i++) {
			if (i>0) sb.append(delimiter);
			sb.append(it.next());
		}
		return sb.toString();
	}

	public static String join(Object[] parts, char delimiter) {
		StringBuilder sb = new StringBuilder();
		for (int i=0; i<parts.length; i++) {
			if (i>0) sb.append(delimiter);
			sb.append(parts[i]);
		}
		return sb.toString();
	}

	public static String join(int[] parts, char delimiter) {
		StringBuilder sb = new StringBuilder();
		for (int i=0; i<parts.length; i++) {
			if (i>0) sb.append(delimiter);
			sb.append(parts[i]);
		}
		return sb.toString();
	}

	/**
	 * Answers true if the pattern matches the filepath using the pathSepatator, false otherwise.
	 *
	 * Path char[] pattern matching, accepting wild-cards '**', '*' and '?' (using Ant directory tasks
	 * conventions, also see "http://jakarta.apache.org/ant/manual/dirtasks.html#defaultexcludes").
	 * Path pattern matching is enhancing regular pattern matching in supporting extra rule where '**' represent
	 * any folder combination.
	 * Special rules:
	 * - foo\  is equivalent to foo\**
	 * - *.java is equivalent to **\*.java
	 * When not case sensitive, the pattern is assumed to already be lowercased, the
	 * name will be lowercased character per character as comparing.
	 *
	 * @param pattern the given pattern
	 * @param filepath the given path
	 * @param isCaseSensitive to find out whether or not the matching should be case sensitive
	 * @param pathSeparator the given path separator
	 * @return true if the pattern matches the filepath using the pathSepatator, false otherwise
	 */
	public static final boolean pathMatch(
			char[] pattern,
			char[] filepath,
			boolean isCaseSensitive,
			char pathSeparator) {

		if (filepath == null)
			return false; // null name cannot match
		if (pattern == null)
			return true; // null pattern is equivalent to '*'

		// special case: pattern foo is equivalent to **\foo (not absolute)
		boolean freeLeadingDoubleStar;

		// offsets inside pattern
		int pSegmentStart, pLength = pattern.length;

		if (freeLeadingDoubleStar = pattern[0] != pathSeparator){
			pSegmentStart = 0;
		} else {
			pSegmentStart = 1;
		}
		int pSegmentEnd = indexOf(pathSeparator, pattern, pSegmentStart+1);
		if (pSegmentEnd < 0) pSegmentEnd = pLength;

		// special case: pattern foo\ is equivalent to foo\**
		boolean freeTrailingDoubleStar = pattern[pLength - 1] == pathSeparator;

		// offsets inside filepath
		int fSegmentStart, fLength = filepath.length;
		if (filepath[0] != pathSeparator){
			fSegmentStart = 0;
		} else {
			fSegmentStart = 1;
		}
		if (fSegmentStart != pSegmentStart) {
			return false; // both must start with a separator or none.
		}
		int fSegmentEnd = indexOf(pathSeparator, filepath, fSegmentStart+1);
		if (fSegmentEnd < 0) fSegmentEnd = fLength;

		// first segments
		while (pSegmentStart < pLength
				&& !freeLeadingDoubleStar
				&& !(pSegmentEnd == pLength && freeTrailingDoubleStar
						|| (pSegmentEnd == pSegmentStart + 2
								&& pattern[pSegmentStart] == '*'
									&& pattern[pSegmentStart + 1] == '*'))) {

			if (fSegmentStart >= fLength)
				return false;
			if (!match(
					pattern,
					pSegmentStart,
					pSegmentEnd,
					filepath,
					fSegmentStart,
					fSegmentEnd,
					isCaseSensitive)) {
				return false;
			}

			// jump to next segment
			pSegmentEnd =
				indexOf(
						pathSeparator,
						pattern,
						pSegmentStart = pSegmentEnd + 1);
			// skip separator
			if (pSegmentEnd < 0)
				pSegmentEnd = pLength;

			fSegmentEnd =
				indexOf(
						pathSeparator,
						filepath,
						fSegmentStart = fSegmentEnd + 1);
			// skip separator
			if (fSegmentEnd < 0) fSegmentEnd = fLength;
		}

		/* check sequence of doubleStar+segment */
		int pSegmentRestart;
		if ((pSegmentStart >= pLength && freeTrailingDoubleStar)
				|| (pSegmentEnd == pSegmentStart + 2
						&& pattern[pSegmentStart] == '*'
							&& pattern[pSegmentStart + 1] == '*')) {
			pSegmentEnd =
				indexOf(
						pathSeparator,
						pattern,
						pSegmentStart = pSegmentEnd + 1);
			// skip separator
			if (pSegmentEnd < 0) pSegmentEnd = pLength;
			pSegmentRestart = pSegmentStart;
		} else {
			if (pSegmentStart >= pLength) return fSegmentStart >= fLength; // true if filepath is done too.
			pSegmentRestart = 0; // force fSegmentStart check
		}
		int fSegmentRestart = fSegmentStart;
		checkSegment : while (fSegmentStart < fLength) {

			if (pSegmentStart >= pLength) {
				if (freeTrailingDoubleStar) return true;
				// mismatch - restart current path segment
				pSegmentEnd =
					indexOf(pathSeparator, pattern, pSegmentStart = pSegmentRestart);
				if (pSegmentEnd < 0) pSegmentEnd = pLength;

				fSegmentRestart =
					indexOf(pathSeparator, filepath, fSegmentRestart + 1);
				// skip separator
				if (fSegmentRestart < 0) {
					fSegmentRestart = fLength;
				} else {
					fSegmentRestart++;
				}
				fSegmentEnd =
					indexOf(pathSeparator, filepath, fSegmentStart = fSegmentRestart);
				if (fSegmentEnd < 0) fSegmentEnd = fLength;
				continue checkSegment;
			}

			/* path segment is ending */
			if (pSegmentEnd == pSegmentStart + 2
					&& pattern[pSegmentStart] == '*'
						&& pattern[pSegmentStart + 1] == '*') {
				pSegmentEnd =
					indexOf(pathSeparator, pattern, pSegmentStart = pSegmentEnd + 1);
				// skip separator
				if (pSegmentEnd < 0) pSegmentEnd = pLength;
				pSegmentRestart = pSegmentStart;
				fSegmentRestart = fSegmentStart;
				if (pSegmentStart >= pLength) return true;
				continue checkSegment;
			}
			/* chech current path segment */
			if (!match(
					pattern,pSegmentStart,pSegmentEnd,
					filepath,fSegmentStart,fSegmentEnd,
					isCaseSensitive)) {
				// mismatch - restart current path segment
				pSegmentEnd =
					indexOf(pathSeparator, pattern, pSegmentStart = pSegmentRestart);
				if (pSegmentEnd < 0) pSegmentEnd = pLength;

				fSegmentRestart =
					indexOf(pathSeparator, filepath, fSegmentRestart + 1);
				// skip separator
				if (fSegmentRestart < 0) {
					fSegmentRestart = fLength;
				} else {
					fSegmentRestart++;
				}
				fSegmentEnd =
					indexOf(pathSeparator, filepath, fSegmentStart = fSegmentRestart);
				if (fSegmentEnd < 0) fSegmentEnd = fLength;
				continue checkSegment;
			}
			// jump to next segment
			pSegmentEnd =
				indexOf(
						pathSeparator,
						pattern,
						pSegmentStart = pSegmentEnd + 1);
			// skip separator
			if (pSegmentEnd < 0)
				pSegmentEnd = pLength;

			fSegmentEnd =
				indexOf(
						pathSeparator,
						filepath,
						fSegmentStart = fSegmentEnd + 1);
			// skip separator
			if (fSegmentEnd < 0)
				fSegmentEnd = fLength;
		}

		return (pSegmentRestart >= pSegmentEnd)
			|| (fSegmentStart >= fLength && pSegmentStart >= pLength)
			|| (pSegmentStart == pLength - 2
				&& pattern[pSegmentStart] == '*'
				&& pattern[pSegmentStart + 1] == '*')
			|| (pSegmentStart == pLength && freeTrailingDoubleStar);
	}

	public static final int indexOf(char toBeFound, char[] array, int start) {
		for (int i=start; i<array.length; i++)
			if (toBeFound==array[i]) return i;
		return -1;
	}

	public static final boolean match(char[] pattern, char[] name, boolean isCaseSensitive) {
		if (name==null) return false; // null name cannot match
		if (pattern==null) return true; // null pattern is equivalent to '*'
		return match(
				pattern,0,pattern.length,
				name,0,name.length,
				isCaseSensitive,true);
	}

	/**
	 * Answers true if the a sub-pattern matches the subpart of the given name, false otherwise.
	 * char[] pattern matching, accepting wild-cards '*' and '?'. Can match only subset of name/pattern.
	 * end positions are non-inclusive.
	 * The subpattern is defined by the patternStart and pattternEnd positions.
	 * When not case sensitive, the pattern is assumed to already be lowercased, the
	 * name will be lowercased character per character as comparing.
	 * <br>
	 * <br>
	 * For example:
	 * <ol>
	 * <li><pre>
	 *    pattern = { '?', 'b', '*' }
	 *    patternStart = 1
	 *    patternEnd = 3
	 *    name = { 'a', 'b', 'c' , 'd' }
	 *    nameStart = 1
	 *    nameEnd = 4
	 *    isCaseSensitive = true
	 *    result => true
	 * </pre>
	 * </li>
	 * <li><pre>
	 *    pattern = { '?', 'b', '*' }
	 *    patternStart = 1
	 *    patternEnd = 2
	 *    name = { 'a', 'b', 'c' , 'd' }
	 *    nameStart = 1
	 *    nameEnd = 2
	 *    isCaseSensitive = true
	 *    result => false
	 * </pre>
	 * </li>
	 * </ol>
	 *
	 * @param pattern the given pattern
	 * @param patternStart the given pattern start
	 * @param patternEnd the given pattern end
	 * @param name the given name
	 * @param nameStart the given name start
	 * @param nameEnd the given name end
	 * @param isCaseSensitive flag to know if the matching should be case sensitive
	 * @return true if the a sub-pattern matches the subpart of the given name, false otherwise
	 */
	public static final boolean match(
			char[] pattern,
			int patternStart,
			int patternEnd,
			char[] name,
			int nameStart,
			int nameEnd,
			boolean isCaseSensitive){

		return match( pattern, patternStart, patternEnd, name, nameStart, nameEnd, isCaseSensitive, false );
	}


	public static final boolean match(
			char[] pattern,
			int patternStart,
			int patternEnd,
			char[] name,
			int nameStart,
			int nameEnd,
			boolean isCaseSensitive,
			boolean allowEscaping) {

		if (name == null)
			return false; // null name cannot match
		if (pattern == null)
			return true; // null pattern is equivalent to '*'
		int iPattern = patternStart;
		int iName = nameStart;

		if (patternEnd < 0)
			patternEnd = pattern.length;
		if (nameEnd < 0)
			nameEnd = name.length;

		/* check first segment */
		char patternChar = 0;
		boolean isEscaped = false;
		while ((iPattern < patternEnd) &&
				( (patternChar = pattern[iPattern]) != '*' ||
						(patternChar == '*' && isEscaped) ) ) {

			if( allowEscaping && pattern[iPattern] == '\\' && !isEscaped ){
				iPattern++;
				isEscaped = true;
				continue;
			} else isEscaped = false;

			if (iName == nameEnd)
				return false;
			if (patternChar
					!= (isCaseSensitive
							? name[iName]
								: Character.toLowerCase(name[iName]))
								&& patternChar != '?') {
				return false;
			}
			iName++;
			iPattern++;
			patternChar = 0;
		}
		/* check sequence of star+segment */
		int segmentStart;
		if (patternChar == '*') {
			segmentStart = ++iPattern; // skip star
		} else {
			segmentStart = 0; // force iName check
		}
		int prefixStart = iName;
		checkSegment : while (iName < nameEnd) {
			if (iPattern == patternEnd) {
				iPattern = segmentStart; // mismatch - restart current segment
				iName = ++prefixStart;
				continue checkSegment;
			}
			/* segment is ending */
			if ((patternChar = pattern[iPattern]) == '*') {
				segmentStart = ++iPattern; // skip start
				if (segmentStart == patternEnd) {
					return true;
				}
				prefixStart = iName;
				continue checkSegment;
			}
			/* check current name character */
			if ((isCaseSensitive ? name[iName] : Character.toLowerCase(name[iName]))
					!= patternChar
					&& patternChar != '?') {
				iPattern = segmentStart; // mismatch - restart current segment
				iName = ++prefixStart;
				continue checkSegment;
			}
			iName++;
			iPattern++;
		}

		return (segmentStart == patternEnd)
		|| (iName == nameEnd && iPattern == patternEnd)
		|| (iPattern == patternEnd - 1 && pattern[iPattern] == '*');
	}

	public static String alignLeft(Object o, int len) {
		String s = o.toString();
		int pad = len-s.length();
		if (pad<0) return s;
		char[] c = new char[pad];
		for (int i=0; i<c.length; i++) c[i] = ' ';
		return s + new String(c);
	}

	public static String alignRight(Object o, int len) {
		String s = o.toString();
		int pad = len-s.length();
		if (pad<0) return s;
		char[] c = new char[pad];
		for (int i=0; i<c.length; i++) c[i] = ' ';
		return new String(c) + s;
	}

	public static boolean hasContent(String s) {
		return s!=null && s.trim().length()>0;
	}

	public static String unescapeJava(String s) {
		if (s==null) return null;
		StringBuilder sb = new StringBuilder(s.length());
		unescapeJava(sb,s);
		return sb.toString();
	}

	public static void unescapeJava(StringBuilder out, String s) {
		StringBuilder unicode = new StringBuilder(4);
		boolean hadSlash = false;
		boolean inUnicode = false;
		for (int i=0; i<s.length(); i++) {
			char c = s.charAt(i);
			if (inUnicode) { // reading unicode values
				unicode.append(c);
				if (unicode.length()==4) { // four hex digits
					try {
						int value = Integer.parseInt(unicode.toString(),16);
						out.append((char)value);
						unicode.setLength(0);
						inUnicode = false;
						hadSlash = false;
					} catch (NumberFormatException ex) {
						throw new RuntimeException("Unable to parse unicode value: "+unicode,ex);
					}
				}
				continue;
			}
			if (hadSlash) {
				hadSlash = false;
				switch (c) {
					case '\\': out.append('\\'); break;
					case '\'': out.append('\''); break;
					case '\"': out.append('"'); break;
					case 't': out.append('\t'); break;
					case 'r': out.append('\r'); break;
					case 'n': out.append('\n'); break;
					case 'f': out.append('\f'); break;
					case 'b': out.append('\b'); break;
					case 'u': inUnicode = true; break;
					default: out.append(c); break;
				}
				continue;
			} else if (c=='\\') {
				hadSlash = true;
				continue;
			}
			out.append(c);
		}
		if (hadSlash) // weird case of a \ at the end of the string, output it anyway
			out.append('\\');
	}

	/**
	 * Decode a URI string (according to RFC 2396).
	 */
	public static String uridecode(String s) throws MalformedURLException {
		try {
			return uridecode(s,"8859_1");
		} catch (UnsupportedEncodingException e) {
			// ISO-Latin-1 should always be available?
			throw new MalformedURLException("ISO-Latin-1 decoder unavailable");
		}
	}

	/**
	 * Decode a URI string (according to RFC 2396).
	 *
	 * Three-character sequences '%xy', where 'xy' is the two-digit
	 * hexadecimal representation of the lower 8-bits of a character,
	 * are decoded into the character itself.
	 *
	 * The string is subsequently converted using the specified encoding
	 */
	public static String uridecode(String s, String enc)
	throws MalformedURLException, UnsupportedEncodingException {
		int length = s.length();
		byte[] bytes = new byte[length];
		int j = 0;
		for (int i=0; i<length; i++) {
			if (s.charAt(i)=='%') {
				i++; // skip %
				try {
					bytes[j++] = (byte)Integer.parseInt(s.substring(i,i+2),16);
				} catch (Exception ex) {
					throw new MalformedURLException("Invalid URI encoding: " + s);
				}
				i++; // skip first hex char; for loop will skip second one
			} else {
				bytes[j++] = (byte)s.charAt(i);
			}
		}
		return new String(bytes,0,j,enc);
	}

	/*
	 * From RFC 2396:
	 *
	 *     mark = "-" | "_" | "." | "!" | "~" | "*" | "'" | "(" | ")"
	 * reserved = ";" | "/" | ":" | "?" | "@" | "&" | "=" | "+" | "$" | ","
	 */
	public static String UriAllowedRfc2396 = "-_.!~*'()"; // mark (see above)

	// see source of java.net.URLEncoder
	public static String UriAllowedJava = " -_.*";

	// disallowed \/:*?"<>|
	public static String UriAllowedFilename = " -_.,!~`'´();@&=+$#²³€{}[]%^°";

	/**
	 * Encode a string for inclusion in a URI (according to RFC 2396).
	 *
	 * Unsafe characters are escaped by encoding them in three-character
	 * sequences '%xy', where 'xy' is the two-digit hexadecimal representation
	 * of the lower 8-bits of the character.
	 *
	 * The question mark '?' character is also escaped, as required by RFC 2255.
	 *
	 * The string is first converted to the specified encoding.
	 * For LDAP (2255), the encoding must be UTF-8.
	 */
	public static String uriencode(String s, String enc)
	throws UnsupportedEncodingException {
		return uriencode(s,enc,UriAllowedRfc2396);
	}

	public static String uriencode(String s, String enc, String allowed)
	throws UnsupportedEncodingException {
		return uriencode(s,Charset.forName(enc),allowed);
	}

	public static String uriencode(String s, Charset cs, String allowed) {
		char[] chars = s.toCharArray();
		int count = chars.length;
		char[] buf = new char[12*count]; // 1 char can take up to 4 bytes encoded to 4x3 chars (%00)
		int j = 0;
		for (int i=0; i<count; i++) {
			char c = chars[i];
			byte b = (byte)c;
			if ((b>=0x61 && b<=0x7A) || // a..z
					(b>=0x41 && b<=0x5A) || // A..Z
					(b>=0x30 && b<=0x39) || // 0..9
					(allowed.indexOf(c)>=0)) {
				buf[j++] = c;
			} else {
				ByteBuffer encoded = cs.encode(CharBuffer.wrap(chars,i,1));
				for (int e=0; e<encoded.limit(); e++) {
					b = encoded.get(e);
					buf[j++] = '%';
					buf[j++] = Character.forDigit(0xF & (b >>> 4),16);
					buf[j++] = Character.forDigit(0xF & b,16);
				}
			}
		}
		return new String(buf,0,j);
	}

	public static final Pattern PropertyDollarBraces = Pattern.compile("\\$\\{(.+?)(?:=(.+?))?\\}");
	public static final Pattern PropertyOnlyBraces = Pattern.compile("\\{(.+?)(?:=(.+?))?\\}");
	public static final Pattern PropertyDoubleBraces = Pattern.compile("\\{\\{(.+?)(?:=(.+?))?\\}\\}");

	public static String substitute(String s, Pattern propertyPattern, Map props) {
		Matcher m = propertyPattern.matcher(s);
		StringBuffer sb = new StringBuffer();
		while (m.find()) {
			Object value = props.get(m.group(1));
			if (value==null && m.groupCount()>1)
				value = m.group(2);
			m.appendReplacement(sb,String.valueOf(value));
		}
		m.appendTail(sb);
		return sb.toString();
	}
}
