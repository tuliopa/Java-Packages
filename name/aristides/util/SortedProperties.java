/*
 * Copyright (c) 1995, 2010, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package name.aristides.util;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.*;

public class SortedProperties extends TreeMap<String, String> {

	/**
	 * 
	 */
	private static final long serialVersionUID = 742538447736749320L;

	/**
	 * The default values for this SortedProperties.
	 */
	protected SortedProperties defaults;

	private ArrayList<String> orderedKeys;

	/**
	 * Constructs a new SortedProperties object.
	 */
	public SortedProperties() {
		super();
		orderedKeys = new ArrayList<String>();
	}

	/**
	 * Searches for the property with the specified name. If the property is not
	 * found, look in the default properties. If the property is not found in
	 * the default properties, answer null.
	 * 
	 * @param name
	 *            the name of the property to find
	 * @return the named property value
	 */
	public String getProperty(String name) {
		Object result = super.get(name);
		String property = result instanceof String ? (String) result : null;
		if (property == null && defaults != null) {
			property = defaults.getProperty(name);
		}
		return property;
	}

	public synchronized void load(Reader reader) throws IOException {
		load0(new LineReader(reader));
	}

	public synchronized void load(InputStream inStream) throws IOException {
		load0(new LineReader(inStream));
	}

	private void load0(LineReader lr) throws IOException {
		char[] convtBuf = new char[1024];
		int limit;
		int keyLen;
		int valueStart;
		char c;
		boolean hasSep;
		boolean precedingBackslash;

		while ((limit = lr.readLine()) >= 0) {
			c = 0;
			keyLen = 0;
			valueStart = limit;
			hasSep = false;

			// System.out.println("line=<" + new String(lineBuf, 0, limit) +
			// ">");
			precedingBackslash = false;
			while (keyLen < limit) {
				c = lr.lineBuf[keyLen];
				// need check if escaped.
				if ((c == '=' || c == ':') && !precedingBackslash) {
					valueStart = keyLen + 1;
					hasSep = true;
					break;
				} else if ((c == ' ' || c == '\t' || c == '\f')
						&& !precedingBackslash) {
					valueStart = keyLen + 1;
					break;
				}
				if (c == '\\') {
					precedingBackslash = !precedingBackslash;
				} else {
					precedingBackslash = false;
				}
				keyLen++;
			}
			while (valueStart < limit) {
				c = lr.lineBuf[valueStart];
				if (c != ' ' && c != '\t' && c != '\f') {
					if (!hasSep && (c == '=' || c == ':')) {
						hasSep = true;
					} else {
						break;
					}
				}
				valueStart++;
			}
			String key = loadConvert(lr.lineBuf, 0, keyLen, convtBuf);
			String value = loadConvert(lr.lineBuf, valueStart, limit - valueStart, convtBuf);
			put(key, value);
		}
	}

	/*
	 * Read in a "logical line" from an InputStream/Reader, skip all comment and
	 * blank lines and filter out those leading whitespace characters ( , and )
	 * from the beginning of a "natural line". Method returns the char length of
	 * the "logical line" and stores the line in "lineBuf".
	 */
	class LineReader {
		public LineReader(InputStream inStream) {
			this.inStream = inStream;
			inByteBuf = new byte[8192];
		}

		public LineReader(Reader reader) {
			this.reader = reader;
			inCharBuf = new char[8192];
		}

		byte[] inByteBuf;
		char[] inCharBuf;
		char[] lineBuf = new char[1024];
		int inLimit = 0;
		int inOff = 0;
		InputStream inStream;
		Reader reader;

		int readLine() throws IOException {
			int len = 0;
			char c = 0;

			boolean skipWhiteSpace = true;
			boolean isCommentLine = false;
			boolean isNewLine = true;
			boolean appendedLineBegin = false;
			boolean precedingBackslash = false;
			boolean skipLF = false;

			while (true) {
				if (inOff >= inLimit) {
					inLimit = (inStream == null) ? reader.read(inCharBuf)
							: inStream.read(inByteBuf);
					inOff = 0;
					if (inLimit <= 0) {
						if(isCommentLine){
							//Todo
							//orderedKeys.add("comentario");
						}
						if (len == 0 || isCommentLine) {
							return -1;
						}
						return len;
					}
				}
				if (inStream != null) {
					// The line below is equivalent to calling a
					// ISO8859-1 decoder.
					c = (char) (0xff & inByteBuf[inOff++]);
				} else {
					c = inCharBuf[inOff++];
				}
				if (skipLF) {
					skipLF = false;
					if (c == '\n') {
						continue;
					}
				}
				if (skipWhiteSpace) {
					if (c == ' ' || c == '\t' || c == '\f') {
						continue;
					}
					if (!appendedLineBegin && (c == '\r' || c == '\n')) {
						continue;
					}
					skipWhiteSpace = false;
					appendedLineBegin = false;
				}
				if (isNewLine) {
					isNewLine = false;
					if (c == '#' || c == '!') {
						isCommentLine = true;
						continue;
					}
				}

				if (c != '\n' && c != '\r') {
					lineBuf[len++] = c;
					if (len == lineBuf.length) {
						int newLength = lineBuf.length * 2;
						if (newLength < 0) {
							newLength = Integer.MAX_VALUE;
						}
						char[] buf = new char[newLength];
						System.arraycopy(lineBuf, 0, buf, 0, lineBuf.length);
						lineBuf = buf;
					}
					// flip the preceding backslash flag
					if (c == '\\') {
						precedingBackslash = !precedingBackslash;
					} else {
						precedingBackslash = false;
					}
				} else {
					// reached EOL
					if (isCommentLine || len == 0) {
						isCommentLine = false;
						isNewLine = true;
						skipWhiteSpace = true;
						len = 0;
						continue;
					}
					if (inOff >= inLimit) {
						inLimit = (inStream == null) ? reader.read(inCharBuf)
								: inStream.read(inByteBuf);
						inOff = 0;
						if (inLimit <= 0) {
							return len;
						}
					}
					if (precedingBackslash) {
						len -= 1;
						// skip the leading whitespace characters in following
						// line
						skipWhiteSpace = true;
						appendedLineBegin = true;
						precedingBackslash = false;
						if (c == '\r') {
							skipLF = true;
						}
					} else {
						return len;
					}
				}
			}
		}
	}

	/*
	 * Converts encoded &#92;uxxxx to unicode chars and changes special saved
	 * chars to their original forms
	 */
	private String loadConvert(char[] in, int off, int len, char[] convtBuf) {
		if (convtBuf.length < len) {
			int newLen = len * 2;
			if (newLen < 0) {
				newLen = Integer.MAX_VALUE;
			}
			convtBuf = new char[newLen];
		}
		char aChar;
		char[] out = convtBuf;
		int outLen = 0;
		int end = off + len;

		while (off < end) {
			aChar = in[off++];
			if (aChar == '\\') {
				aChar = in[off++];
				if (aChar == 'u') {
					// Read the xxxx
					int value = 0;
					for (int i = 0; i < 4; i++) {
						aChar = in[off++];
						switch (aChar) {
						case '0':
						case '1':
						case '2':
						case '3':
						case '4':
						case '5':
						case '6':
						case '7':
						case '8':
						case '9':
							value = (value << 4) + aChar - '0';
							break;
						case 'a':
						case 'b':
						case 'c':
						case 'd':
						case 'e':
						case 'f':
							value = (value << 4) + 10 + aChar - 'a';
							break;
						case 'A':
						case 'B':
						case 'C':
						case 'D':
						case 'E':
						case 'F':
							value = (value << 4) + 10 + aChar - 'A';
							break;
						default:
							throw new IllegalArgumentException(
									"Malformed \\uxxxx encoding.");
						}
					}
					out[outLen++] = (char) value;
				} else {
					if (aChar == 't')
						aChar = '\t';
					else if (aChar == 'r')
						aChar = '\r';
					else if (aChar == 'n')
						aChar = '\n';
					else if (aChar == 'f')
						aChar = '\f';
					out[outLen++] = aChar;
				}
			} else {
				out[outLen++] = (char) aChar;
			}
		}
		return new String(out, 0, outLen);
	}


	/**
	 * Maps the specified key to the specified value. If the key already exists,
	 * the old value is replaced. The key and value cannot be null.
	 * 
	 * @param name
	 *            the key
	 * @param value
	 *            the value
	 * @return the old value mapped to the key, or null
	 */
	public Object setProperty(String name, String value) {
		return put(name, value);
	}

	public void store(Writer writer, String comments) throws IOException {
		store0((writer instanceof BufferedWriter) ? (BufferedWriter) writer
				: new BufferedWriter(writer), comments, false);
	}

	/**
	 * Writes this property list (key and element pairs) in this
	 * <code>Properties</code> table to the output stream in a format suitable
	 * for loading into a <code>Properties</code> table using the
	 * {@link #load(InputStream) load(InputStream)} method.
	 * <p>
	 * Properties from the defaults table of this <code>Properties</code> table
	 * (if any) are <i>not</i> written out by this method.
	 * <p>
	 * This method outputs the comments, properties keys and values in the same
	 * format as specified in {@link #store(java.io.Writer, java.lang.String)
	 * store(Writer)}, with the following differences:
	 * <ul>
	 * <li>The stream is written using the ISO 8859-1 character encoding.
	 * 
	 * <li>Characters not in Latin-1 in the comments are written as
	 * <code>&#92;u</code><i>xxxx</i> for their appropriate unicode hexadecimal
	 * value <i>xxxx</i>.
	 * 
	 * <li>Characters less than <code>&#92;u0020</code> and characters greater
	 * than <code>&#92;u007E</code> in property keys or values are written as
	 * <code>&#92;u</code><i>xxxx</i> for the appropriate hexadecimal value
	 * <i>xxxx</i>.
	 * </ul>
	 * <p>
	 * After the entries have been written, the output stream is flushed. The
	 * output stream remains open after this method returns.
	 * <p>
	 * 
	 * @param out
	 *            an output stream.
	 * @param comments
	 *            a description of the property list.
	 * @exception IOException
	 *                if writing this property list to the specified output
	 *                stream throws an <tt>IOException</tt>.
	 * @exception ClassCastException
	 *                if this <code>Properties</code> object contains any keys
	 *                or values that are not <code>Strings</code>.
	 * @exception NullPointerException
	 *                if <code>out</code> is null.
	 * @since 1.2
	 */
	public void store(OutputStream out, String comments) throws IOException {
		store0(new BufferedWriter(new OutputStreamWriter(out, "8859_1")),
				comments, true);
	}

	private void store0(BufferedWriter bw, String comments, boolean escUnicode)
			throws IOException {
		if (comments != null) {
			writeComments(bw, comments);
		}
		bw.write("#" + new Date().toString());
		bw.newLine();
		synchronized (this) {
			for (String key : propertyNames()) {
				String val = (String) get(key);
				key = saveConvert(key, true, escUnicode);
				/*
				 * No need to escape embedded and trailing spaces for value,
				 * hence pass false to flag.
				 */
				val = saveConvert(val, false, escUnicode);
				bw.write(key + "=" + val);
				bw.newLine();
			}
		}
		bw.flush();
	}

	/*
	 * Converts unicodes to encoded &#92;uxxxx and escapes special characters
	 * with a preceding slash
	 */
	private String saveConvert(String theString, boolean escapeSpace,
			boolean escapeUnicode) {
		int len = theString.length();
		int bufLen = len * 2;
		if (bufLen < 0) {
			bufLen = Integer.MAX_VALUE;
		}
		StringBuffer outBuffer = new StringBuffer(bufLen);

		for (int x = 0; x < len; x++) {
			char aChar = theString.charAt(x);
			// Handle common case first, selecting largest block that
			// avoids the specials below
			if ((aChar > 61) && (aChar < 127)) {
				if (aChar == '\\') {
					outBuffer.append('\\');
					outBuffer.append('\\');
					continue;
				}
				outBuffer.append(aChar);
				continue;
			}
			switch (aChar) {
			case ' ':
				if (x == 0 || escapeSpace)
					outBuffer.append('\\');
				outBuffer.append(' ');
				break;
			case '\t':
				outBuffer.append('\\');
				outBuffer.append('t');
				break;
			case '\n':
				outBuffer.append('\\');
				outBuffer.append('n');
				break;
			case '\r':
				outBuffer.append('\\');
				outBuffer.append('r');
				break;
			case '\f':
				outBuffer.append('\\');
				outBuffer.append('f');
				break;
			case '=': // Fall through
			case ':': // Fall through
			case '#': // Fall through
			case '!':
				outBuffer.append('\\');
				outBuffer.append(aChar);
				break;
			default:
				if (((aChar < 0x0020) || (aChar > 0x007e)) & escapeUnicode) {
					outBuffer.append('\\');
					outBuffer.append('u');
					outBuffer.append(toHex((aChar >> 12) & 0xF));
					outBuffer.append(toHex((aChar >> 8) & 0xF));
					outBuffer.append(toHex((aChar >> 4) & 0xF));
					outBuffer.append(toHex(aChar & 0xF));
				} else {
					outBuffer.append(aChar);
				}
			}
		}
		return outBuffer.toString();
	}

	private static void writeComments(BufferedWriter bw, String comments)
			throws IOException {
		bw.write("#");
		int len = comments.length();
		int current = 0;
		int last = 0;
		char[] uu = new char[6];
		uu[0] = '\\';
		uu[1] = 'u';
		while (current < len) {
			char c = comments.charAt(current);
			if (c > '\u00ff' || c == '\n' || c == '\r') {
				if (last != current)
					bw.write(comments.substring(last, current));
				if (c > '\u00ff') {
					uu[2] = toHex((c >> 12) & 0xf);
					uu[3] = toHex((c >> 8) & 0xf);
					uu[4] = toHex((c >> 4) & 0xf);
					uu[5] = toHex(c & 0xf);
					bw.write(new String(uu));
				} else {
					bw.newLine();
					if (c == '\r' && current != len - 1
							&& comments.charAt(current + 1) == '\n') {
						current++;
					}
					if (current == len - 1
							|| (comments.charAt(current + 1) != '#' && comments
									.charAt(current + 1) != '!'))
						bw.write("#");
				}
				last = current + 1;
			}
			current++;
		}
		if (last != current)
			bw.write(comments.substring(last, current));
		bw.newLine();
	}

	private static char toHex(int nibble) {
		return hexDigit[(nibble & 0xF)];
	}

	/** A table of hex digits */
	private static final char[] hexDigit = { '0', '1', '2', '3', '4', '5', '6',
			'7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };
	
	@Override
	public String put(String key, String value) {
		orderedKeys.add(key);
		return super.put(key,value);
	}
	
	public ArrayList<String> propertyNames(){
		return this.orderedKeys;
	}

}
