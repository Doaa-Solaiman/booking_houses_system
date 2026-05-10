package de.scheller.platform.network.rmi;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.util.Objects;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * @author kandzia
 * @author Joshua Tauberer (tauberer@for.net)
 */
public interface Stream
{
	/**
	 * The interface equivelant of an OutputStream that can be remotely referenced.
	 * A java.io.OutputStream cannot be used remotely because its functionality is not
	 * encapsulated in an interface, and wrapping it in a DataOutput interface would
	 * provide poor performance, as only single items could be retreived at a time.
	 */
	interface IOutputStream {
		public void write(int b) throws IOException;
		public void write(byte[] b) throws IOException;
		public void write(byte[] b, int off, int len) throws IOException;
		public void flush() throws IOException;
		public void close() throws IOException;
	}

	/** Unwraps an IOutputStream back into a standard java.io.OutputStream. */
	class OutputStreamUnwrap extends OutputStream {
		IOutputStream s;
		Deflater deflater = new Deflater();
		long totalData = 0, totalTransfer = 0;

		public OutputStreamUnwrap(IOutputStream s) {
			this.s = s;
		}

		public void setCompressionNone() {
			deflater.setLevel(Deflater.NO_COMPRESSION);
		}
		public void setCompressionFast() {
			deflater.setLevel(Deflater.BEST_SPEED);
		}
		public void setCompressionHigh() {
			deflater.setLevel(Deflater.BEST_COMPRESSION);
		}

		public float getCompressionRatio() {
			if (totalTransfer==0) return 0;
			return 1-(float)totalTransfer/totalData;
		}
		public long getTotalData() {
			return totalData;
		}
		public long getTotalTransfer() {
			return totalTransfer;
		}

		@Override
		public void write(int b) throws IOException {
			s.write(b);
			totalData++;
			totalTransfer++;
		}

		@Override
		public void write(byte[] b) throws IOException {
			write(b,0,b.length);
		}

		@Override
		public void write(byte[] dd, int off, int len) throws IOException {
			if (len<=0) return;
			byte[] b = new byte[50 + len*2];
			deflater.setInput(dd,off,len);
			deflater.finish();
			int d = 0;
			while (!deflater.finished()) {
				if (d == b.length) throw new IOException("Deflation too large.");
				d += deflater.deflate(b, d, b.length - d);
			}
			deflater.reset();
			s.write(b,0,d);
			totalData += len;
			totalTransfer += d;
		}

		@Override public void flush() throws IOException { s.flush(); }
		@Override public void close() throws IOException { s.close(); }
	}

	/** Wraps java.io.OutputStream into a class that is remotely usable. */
	class OutputStreamWrap implements IOutputStream {
		OutputStream s;
		Inflater inflater = new Inflater();

		public OutputStreamWrap(OutputStream s) {
			this.s = new BufferedOutputStream(s);
		}

		public void write(int b) throws IOException { s.write(b); }
		public void write(byte[] b) throws IOException { write(b,0,b.length); }
		public void write(byte[] dd, int off, int len) throws IOException {
			byte b[] = new byte[len*2];
			int d;
			if (len==0) return;
			try {
				inflater.setInput(dd, off, len);
				if (inflater.needsInput() || inflater.needsDictionary()) throw new IOException("Needy!");
				while (!inflater.finished()) {
					d = inflater.inflate(b);
					s.write(b,0,d);
				}
				inflater.reset();
			} catch (Exception e) {
				throw new IOException(e.toString());
			}
		}

		public void flush() throws IOException { s.flush(); }
		public void close() throws IOException { s.close(); }
	}

	class WriterOutputStream extends OutputStream {
		protected Writer writer;
		protected Charset encoding;

		public WriterOutputStream(Writer writer, String encoding) {
			this(writer,Charset.forName(encoding));
		}

		public WriterOutputStream(Writer writer, Charset encoding) {
			this.writer = writer;
			this.encoding = encoding;
		}

		@Override
		public void write(int b) throws IOException {
			write(new byte[] { (byte)b });
		}
		@Override
		public void write(byte[] b) throws IOException {
			writer.write(new String(b,encoding));
		}
		@Override
		public void write(byte[] b, int off, int len) throws IOException {
			writer.write(new String(b,off,len,encoding));
		}
		@Override
		public void flush() throws IOException {
			writer.flush();
		}
		@Override
		public void close() throws IOException {
			writer.close();
			writer = null;
			encoding = null;
		}
	}

	/**
	 * The interface equivelant of an InputStream that can be remotely referenced.
	 * A java.io.InputStream cannot be used remotely because its functionality is not
	 * encapsulated in an interface, and wrapping it in a DataInput interface would
	 * provide poor performance, as only single items could be retreived at a time.
	 * Furthermore, the input stream returns byte[] data by reference through
	 * the read method's arguments, which would not work for RMI.
	 */
	interface IInputStream {
		int available() throws IOException;
		int read() throws IOException;
		byte[] read(int length) throws IOException;
		long skip(long n) throws IOException;
		void close() throws IOException;

		boolean markSupported();
		void mark(int readlimit);
		void reset() throws IOException;

		void setCompression(int level);
	}

	/** Unwraps an IInputStream back into a standard java.io.InputStream. */
	class InputStreamUnwrap extends InputStream {
		IInputStream in;
		Inflater inflater = new Inflater();
		long totalData = 0, totalTransfer = 0;

		public InputStreamUnwrap(IInputStream in) {
			this.in = in;
		}

		public void setCompressionNone() {
			in.setCompression(Deflater.NO_COMPRESSION);
		}
		public void setCompressionFast() {
			in.setCompression(Deflater.BEST_SPEED);
		}
		public void setCompressionHigh() {
			in.setCompression(Deflater.BEST_COMPRESSION);
		}

		public float getCompressionRatio() {
			if (totalTransfer==0) return 0;
			return 1-(float)totalTransfer/totalData;
		}
		public long getTotalData() {
			return totalData;
		}
		public long getTotalTransfer() {
			return totalTransfer;
		}

		@Override
		public int read() throws IOException {
			totalData++;
			totalTransfer++;
			return in.read();
		}

		@Override
		public int read(byte[] b) throws IOException {
			return read(b,0,b.length);
		}

		@Override
		public int read(byte[] b, int off, int len) throws IOException {
			if (len==0) return 0;
			byte[] o = in.read(len);
			if (o==null) return -1;
			try {
				inflater.setInput(o);
				int d = 0;
				while (!inflater.finished()) {
					if (off+d == b.length) throw new IOException("Out of room for inflation.");
					d += inflater.inflate(b, off+d, b.length-off-d);
				}
				inflater.reset();
				totalData += d;
				totalTransfer += o.length;
				return d;
			} catch (Exception ex) {
				throw new IOException(ex);
			}
		}

		@Override public long skip(long n) throws IOException { return in.skip(n); }
		@Override public int available() throws IOException { return in.available(); }
		@Override public void close() throws IOException { in.close(); }

		@Override public boolean markSupported() { return in.markSupported(); }
		@Override public void mark(int readlimit) { in.mark(readlimit); }
		@Override public void reset() throws IOException { in.reset(); }
	}

	/** Wraps java.io.InputStream into a class that is remotely usable. */
	class InputStreamWrap implements IInputStream {
		InputStream s;
		Deflater deflater = new Deflater();

		public InputStreamWrap(InputStream s) {
			this.s = s;
		}

		public InputStreamWrap(Reader s) {
			this.s = new ReaderToInStream(s);
		}

		public int read() throws IOException { return s.read(); }
		public byte[] read(int length) throws IOException {
			byte dd[] = new byte[length];
			byte b[] = new byte[length*2];
			int r = s.read(dd);
			if (r==-1) return null;
			deflater.setInput(dd,0,r);
			deflater.finish();
			int d = 0;
			while (!deflater.finished()) {
				if (d == b.length) throw new IOException("Deflation too large.");
				d += deflater.deflate(b, d, b.length - d);
			}
			byte[] q = new byte[d];
			System.arraycopy(b,0,q,0,d);
			deflater.reset();
			return q;
		}

		public long skip(long n) throws IOException { return s.skip(n); }
		public int available() throws IOException { return s.available(); }
		public void close() throws IOException { s.close(); }

		public boolean markSupported() { return s.markSupported(); }
		public void mark(int readlimit) { s.mark(readlimit); }
		public void reset() throws IOException { s.reset(); }

		public void setCompression(int level) { deflater.setLevel(level); }
	}

	class ReaderToInStream extends InputStream {
		int next = -1;
		Reader r;

		ReaderToInStream(Reader r) {
			this.r = r;
		}

		@Override
		public int read() throws IOException {
			if (next != -1) return next;
			int i = r.read();
			next = (i & 0xFF);
			return i >> 8;
		}

		@Override
		public long skip(long n) throws IOException {
			return r.skip(n);
		}

		@Override
		public int available() throws IOException {
			if (next==-1)
				return r.ready() ? 2 : 0;
			return r.ready() ? 3 : 1;
		}

		@Override
		public void close() throws IOException {
			r.close();
		}

		@Override
		public boolean markSupported() {
			return r.markSupported();
		}

		@Override
		public void mark(int readlimit) {
			try {
				r.mark(readlimit/2);
			} catch (IOException ex) {
				throw new RuntimeException(ex);
			}
		}

		@Override
		public void reset() throws IOException {
			r.reset();
		}
	}

	// previously we used a version of Ant's ReaderInputStream:
	// https://github.com/apache/ant/blob/3a4980e3c4be56745964442abc01360b618e3a85/src/main/org/apache/tools/ant/util/ReaderInputStream.java
	// but 14 years later: https://bz.apache.org/bugzilla/show_bug.cgi?id=40455
	// so we also switched: https://github.com/apache/ant/blob/master/src/main/org/apache/tools/ant/util/ReaderInputStream.java
	class ReaderInputStream extends InputStream {
		private static final int EOF = -1;
		private static final int DEFAULT_BUFFER_SIZE = 1024;
		private final Reader reader;
		private final CharsetEncoder encoder;
		private final CharBuffer encoderIn;
		private final ByteBuffer encoderOut;
		private CoderResult lastCoderResult;
		private boolean endOfInput;

		public ReaderInputStream(Reader reader, String encoding) {
			this(reader,Charset.forName(encoding));
		}

		public ReaderInputStream(Reader reader, Charset charset) {
			this(reader,charset.newEncoder()
					.onMalformedInput(CodingErrorAction.REPLACE)
					.onUnmappableCharacter(CodingErrorAction.REPLACE));
		}

		public ReaderInputStream(Reader reader, CharsetEncoder encoder) {
			this(reader,encoder,DEFAULT_BUFFER_SIZE);
		}

		public ReaderInputStream(Reader reader, CharsetEncoder encoder, int bufferSize) {
			this.reader = reader;
			this.encoder = encoder;
			this.encoderIn = CharBuffer.allocate(bufferSize);
			this.encoderIn.flip();
			this.encoderOut = ByteBuffer.allocate(128);
			this.encoderOut.flip();
		}

		private void fillBuffer() throws IOException {
			if (!endOfInput && (lastCoderResult==null || lastCoderResult.isUnderflow())) {
				encoderIn.compact();
				final int position = encoderIn.position();
				// We don't use Reader#read(CharBuffer) here because it is more efficient
				// to write directly to the underlying char array (the default implementation
				// copies data to a temporary char array).
				final int c = reader.read(encoderIn.array(), position, encoderIn.remaining());
				if (c == EOF) {
					endOfInput = true;
				} else {
					encoderIn.position(position+c);
				}
				encoderIn.flip();
			}
			encoderOut.compact();
			lastCoderResult = encoder.encode(encoderIn, encoderOut, endOfInput);
			encoderOut.flip();
		}

		@Override
		public int read(byte[] array, int off, int len) throws IOException {
			Objects.requireNonNull(array, "array");
			if (len<0 || off<0 || (off+len) > array.length)
				throw new IndexOutOfBoundsException(
						"Array Size=" + array.length + ", offset=" + off + ", length=" + len);
			int read = 0;
			if (len==0)
				return 0; // Always return 0 if len == 0
			while (len>0) {
				if (encoderOut.hasRemaining()) {
					final int c = Math.min(encoderOut.remaining(), len);
					encoderOut.get(array, off, c);
					off += c;
					len -= c;
					read += c;
				} else {
					fillBuffer();
					if (endOfInput && !encoderOut.hasRemaining())
						break;
				}
			}
			return read==0 && endOfInput ? EOF : read;
		}

		@Override
		public int read(byte[] b) throws IOException {
			return read(b,0,b.length);
		}

		@Override
		public int read() throws IOException {
			for (;;) {
				if (encoderOut.hasRemaining())
					return encoderOut.get() & 0xFF;
				fillBuffer();
				if (endOfInput && !encoderOut.hasRemaining())
					return EOF;
			}
		}

		@Override
		public void close() throws IOException {
			reader.close();
		}
	}
}
