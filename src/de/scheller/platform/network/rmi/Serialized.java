package de.scheller.platform.network.rmi;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.OutputStream;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import de.scheller.platform.network.rmi.Failure.Exceptions;
import de.scheller.platform.network.rmi.Failure.MarshallingException;
import de.scheller.platform.network.rmi.InternalApi.IObjectResolver;
import de.scheller.platform.network.rmi.RCore.ObjectID;

/**
 * A wrapper for objects as they are transferred between sockets.
 * MarshalledObjects may represent sender-side objects, receiver-side objects,
 * as well as objects serialized within the structure.
 *
 * @author Joshua Tauberer (tauberer@for.net)
 * @author kandzia
 */
class Serialized implements Serializable
{
	public static final long serialVersionUID = -459473945050272517l;

	public static final int SERVER_SENDER = 0;
	public static final int SERVER_RECEIVER = 1;
	public static final int VAR_SERIALIZED = 2;

	public int varstat;
	public ObjectID id;
	public Class cls;
	public byte[] data;

	private Serialized(int varstat, Class cls, ObjectID id) {
		this.varstat = varstat;
		this.cls = cls;
		this.id = id;
	}

	private Serialized(int varstat, byte[] data) {
		this.varstat = varstat;
		this.data = data;
	}

	public static Serialized SENDERSIDE(Class cls, ObjectID id) throws MarshallingException {
		return new Serialized(SERVER_SENDER,cls,id);
	}

	public static Serialized RECEIVERSIDE(ObjectID id) throws MarshallingException {
		return new Serialized(SERVER_RECEIVER,null,id);
	}

	public static Serialized SERIALIZED(RCore p, Serializable object) throws MarshallingException {
		try {
//			ByteArrayOutputStream uncompressed = new ByteArrayOutputStream();
//			ObjectOutputStream userializer = new ObjectOutputStream(uncompressed);
//			userializer.writeObject(object);
//			userializer.close();

			ByteArrayOutputStream serialized = new ByteArrayOutputStream();
			ObjectOutputStream serializer = null;
			if (p.noCompression) {
				// up to 10 times more data over network...
				serializer = new RObjectOutput(serialized,p.outgoingResolver);
			} else {
				serializer = new RObjectOutput(
						new CompressedBlockOutputStream(serialized,4096),p.outgoingResolver);
//				// slooooww...
//				serializer = new RObjectOutput(
//						new DeflaterOutputStream(serialized),p.outgoingResolver);
			}
			serializer.writeObject(object);
			serializer.close();

//			int uncompressedLength = uncompressed.size();
//			int compressedLength = serialized.size();
//			System.out.println(uncompressedLength+" -> "+compressedLength);

			return new Serialized(Serialized.VAR_SERIALIZED,serialized.toByteArray());
		} catch (StackOverflowError ex) {
			ex.printStackTrace();
			throw new MarshallingException("could not serialize "+object.getClass()+" "+object,ex);
		} catch (IOException ex) {
			ex.printStackTrace();
			throw new MarshallingException("could not serialize "+object.getClass()+" "+object,ex);
		} catch (Throwable t) {
			t.printStackTrace();
			throw new MarshallingException("could not serialize "+object.getClass()+" "+object,t);
		}
	}

	public Object deserialize(RCore p, ClassLoader classloader)
	throws MarshallingException, NoClassDefFoundError {
		RObjectInput deserializer = null;
		try {
			ByteArrayInputStream serialized = new ByteArrayInputStream(data);
			if (p.noCompression) {
				deserializer = new RObjectInput(serialized,classloader,p.incomingResolver);
			} else {
				deserializer = new RObjectInput(
						new CompressedBlockInputStream(serialized),classloader,p.incomingResolver);
//				deserializer = new RObjectInput(
//						new InflaterInputStream(serialized),classloader,p.incomingResolver);
			}
			return deserializer.readObject();
		} catch (Throwable ex) { // NoClassDefFound*Error*
			if (ex instanceof NoClassDefFoundError && RConnection.ignoreNoClassDefFound)
				throw (NoClassDefFoundError)ex;
			Exceptions exs = new Exceptions();
			exs.addCause(ex);
			if (deserializer!=null) {
				if (deserializer.lastException!=null)
					exs.addCause(deserializer.lastException);
				Exception[] xs = deserializer.getExceptions();
				if (xs==null)
					exs.addCause(new Exception("RObjectInput.getExceptions() failed"));
				else if (xs.length>0)
					exs.addCause(xs);
			}
			throw new MarshallingException("could not deserialize",exs);
		}
	}

	static class RObjectInput extends ObjectInputStream {
		ClassLoader loader;
		List<IObjectResolver> resolver;
		ClassNotFoundException lastException;

		RObjectInput(InputStream in, ClassLoader loader, List<IObjectResolver> resolver)
		throws IOException {
			super(in);
			this.loader = loader;
			this.resolver = resolver;
			enableResolveObject(true);
			for (IObjectResolver r : resolver)
				r.init();
		}

		@Override
		protected Class resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
			try {
				return loader.loadClass(desc.getName());
			} catch (ClassNotFoundException ex) {
				lastException = ex;
//				lastException = new ClassNotFoundException("could not resolve "+desc+" using "+loader,ex);
				return super.resolveClass(desc);
//				throw lastException;
			}
		}

		@Override
		protected Object resolveObject(Object obj) throws IOException {
			for (IObjectResolver r : resolver)
				obj = r.resolveObject(obj);
			return obj;
		}

		@Override
		public void close() throws IOException {
			super.close();
			for (IObjectResolver r : resolver)
				r.shutdown();
		}

		public Exception[] getExceptions() {
			Object handles = read(this,"handles");
			byte[] status = (byte[])read(handles,"status");
			Object[] entries = (Object[])read(handles,"entries");
			if (status==null || entries==null) return null;
			Collection<Exception> l = new LinkedHashSet();
			for (int i=0; i<status.length; i++) {
				if (status[i]==3) // ObjectInputStream$HandleTable.STATUS_EXCEPTION
					l.add((Exception)entries[i]);
			}
			return l.toArray(new Exception[l.size()]);
		}

		private Object read(Object o, String field) {
			if (o==null) return null;
			try {
				Field f = getField(o.getClass(),field);
				f.setAccessible(true);
				return f.get(o);
			} catch (Exception ex) {
				return null;
			}
		}

		public static Field getField(Class c, String name) {
			for (;;) {
				try {
					Field f = c.getDeclaredField(name);
					return f;
				} catch (SecurityException ex) {
					return null;
				} catch (NoSuchFieldException ex) {
					c = c.getSuperclass();
					if (c==null) return null;
				}
			}
		}
	}

	static class RObjectOutput extends ObjectOutputStream {
		List<IObjectResolver> resolver;

		RObjectOutput(OutputStream out, List<IObjectResolver> resolver) throws IOException {
			super(out);
			this.resolver = resolver;
			enableReplaceObject(true);
			for (IObjectResolver r : resolver)
				r.init();
		}

		@Override
		protected Object replaceObject(Object obj) throws IOException {
			for (IObjectResolver r : resolver)
				obj = r.resolveObject(obj);
			return obj;
		}

		@Override
		public void close() throws IOException {
			super.close();
			for (IObjectResolver r : resolver)
				r.shutdown();
		}
	}

	static class CompressedBlockInputStream extends FilterInputStream {
		private byte[] inBuf = null;
		private int inLength = 0;
		private byte[] outBuf = null;
		private int outOffs = 0;
		private int outLength = 0;
		private Inflater inflater = null;

		public CompressedBlockInputStream(InputStream is) throws IOException {
			super(is);
			inflater = new Inflater();
		}

		private void readAndDecompress() throws IOException {
			// read the length of the compressed block
			int b1 = in.read();
			int b2 = in.read();
			int b3 = in.read();
			int b4 = in.read();
			if ((b1 | b2 | b3 | b4) < 0)
				throw new EOFException();
			inLength = (b1<<24) + (b2<<16) + (b3<<8) + (b4<<0);

			// read the length of the uncompressed block
			b1 = in.read();
			b2 = in.read();
			b3 = in.read();
			b4 = in.read();
			if ((b1 | b2 | b3 | b4) < 0)
				throw new EOFException();
			outLength = (b1<<24) + (b2<<16) + (b3<<8) + (b4<<0);

			// make sure we've got enough space to read the block
			if (inBuf==null || inLength>inBuf.length)
				inBuf = new byte[inLength];
			if (outBuf==null || outLength>outBuf.length)
				outBuf = new byte[outLength];

			// read until we're got the entire compressed buffer.
			// read(...) will not necessarily block until all
			// requested data has been read, so we loop until we're done.
			int inOffs = 0;
			while (inOffs<inLength) {
				int inCount = in.read(inBuf,inOffs,inLength-inOffs);
				if (inCount==-1)
					throw new EOFException();
				inOffs += inCount;
			}
			inflater.setInput(inBuf,0,inLength);
			try {
				inflater.inflate(outBuf);
			} catch (DataFormatException ex) {
				throw new IOException("Data format exception: " + ex.getMessage());
			}
			// reset the inflator so we can re-use it for the next block
			inflater.reset();
			outOffs = 0;
		}

		@Override
		public int read() throws IOException {
			if (outOffs>=outLength) {
				try {
					readAndDecompress();
				} catch (EOFException ex) {
					return -1;
				}
			}
			return outBuf[outOffs++] & 0xff;
		}

		@Override
		public int read(byte[] b, int off, int len) throws IOException {
			int count = 0;
			while (count<len) {
				if (outOffs>=outLength) {
					try {
						// if we've read at least one decompressed byte and further
						// decompression would require blocking, return the count.
						if (count>0 && in.available()==0)
							return count;
						else readAndDecompress();
					} catch (EOFException ex) {
						if (count==0)
							count = -1;
						return count;
					}
				}
				int toCopy = Math.min(outLength-outOffs,len-count);
				System.arraycopy(outBuf,outOffs,b,off+count,toCopy);
				outOffs += toCopy;
				count += toCopy;
			}
			return count;
		}

		@Override
		public int available() throws IOException {
			// this isn't precise, but should be an adequate
			// lower bound on the actual amount of available data
			return (outLength-outOffs) + in.available();
		}
	}

	/**
	 * An OutputStream that writes to the given underlying output stream 'os' and
	 * sends a compressed block once 'size' byte have been written.
	 * The compression level and strategy should be specified using the constants
	 * defined in java.util.zip.Deflator or its defaults are used.
	 */
	static class CompressedBlockOutputStream extends FilterOutputStream {
		private byte[] inBuf = null;
		private byte[] outBuf = null;
		private int len = 0;
		private Deflater deflater = null;

		public CompressedBlockOutputStream(OutputStream os, int size) throws IOException {
			this(os,size,Deflater.DEFAULT_COMPRESSION,Deflater.DEFAULT_STRATEGY);
		}

		public CompressedBlockOutputStream(OutputStream os, int size, int level, int strategy)
		throws IOException {
			super(os);
			this.inBuf = new byte[size];
			this.outBuf = new byte[size + 64];
			this.deflater = new Deflater(level);
			this.deflater.setStrategy(strategy);
		}

		protected void compressAndSend() throws IOException {
			if (len>0) {
				deflater.setInput(inBuf,0,len);
				deflater.finish();
				int size = deflater.deflate(outBuf);
				// write the size of the compressed data
				out.write((size >> 24) & 0xFF);
				out.write((size >> 16) & 0xFF);
				out.write((size >>  8) & 0xFF);
				out.write((size >>  0) & 0xFF);
				// write the size of the uncompressed data
				out.write((len >> 24) & 0xFF);
				out.write((len >> 16) & 0xFF);
				out.write((len >>  8) & 0xFF);
				out.write((len >>  0) & 0xFF);
				// write the compressed data
				out.write(outBuf,0,size);
				out.flush();
				len = 0;
				deflater.reset();
			}
		}

		@Override
		public void write(int b) throws IOException {
			inBuf[len++] = (byte)b;
			if (len==inBuf.length)
				compressAndSend();
		}

		@Override
		public void write(byte[] b, int boff, int blen)
			throws IOException {
			while (len+blen>inBuf.length) {
				int toCopy = inBuf.length-len;
				System.arraycopy(b,boff,inBuf,len,toCopy);
				len += toCopy;
				compressAndSend();
				boff += toCopy;
				blen -= toCopy;
			}
			System.arraycopy(b,boff,inBuf,len,blen);
			len += blen;
		}

		@Override
		public void flush() throws IOException {
			compressAndSend();
			out.flush();
		}

		@Override
		public void close() throws IOException {
			compressAndSend();
			out.close();
		}
	}
}
