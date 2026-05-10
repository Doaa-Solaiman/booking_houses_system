package de.scheller.platform.network.rmi;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author kandzia
 * @author Joshua Tauberer (tauberer@for.net)
 */
public interface Failure
{
	class RemoteException extends RuntimeException {
		public RemoteException(String message) { super(message); }
		public RemoteException(String message, Throwable cause) { super(message,cause); }
	}

	class MarshallingException extends RemoteException {
		public MarshallingException(String message) { super(message); }
		public MarshallingException(String message, Throwable cause) { super(message,cause); }
	}

	class Exceptions extends Exception {
		protected Throwable[] nested;

		public Exceptions() { super(); }
		public Exceptions(String message) { super(message); }
		public Exceptions(String message, Throwable... ex) { super(message); nested = ex; }
		public Exceptions(Throwable... ex) { super(); nested = ex; }

		public void addCause(Throwable... ex) {
			int length = nested!=null ? nested.length : 0;
			Throwable[] n = new Throwable[length + ex.length];
			if (nested!=null) System.arraycopy(nested,0,n,0,nested.length);
			System.arraycopy(ex,0,n,length,ex.length);
			nested = n;
		}

		public Throwable[] getCauses() {
			return nested;
		}

		@Override
		public void printStackTrace() {
			super.printStackTrace();
			for (Throwable c : getCauses())
				c.printStackTrace();
		}
	}

	class SubstituteException extends RuntimeException {
		//                                            SUBSTITUTEEXCEPTION
		private static final long serialVersionUID = -5085717073387397105L;

		private String className;
		private String localizedMessage;
		private byte[] original;

		public SubstituteException(Throwable t) {
			super(t==null ? null : t.getMessage());
			boolean subclassed = SubstituteException.class.equals(getClass().getSuperclass());
			if (t!=null) init(t,subclassed);
		}

		private SubstituteException(Throwable t, boolean root) {
			super(t==null ? null : t.getMessage());
			if (t!=null) init(t,root);
		}

		private void init(Throwable t, boolean root) {
			if (t==null) return;

			// save exception data (classname, message, stacktrace, cause)
			Throwable cause = t.getCause();
			if (cause!=null) initCause(new SubstituteException(cause,false));
			if (root) {
				StackTraceElement[] trace0 = t.getStackTrace();
				StackTraceElement[] trace1 = new StackTraceElement[trace0.length-1];
				System.arraycopy(trace0,1,trace1,0,trace1.length);
				setStackTrace(trace1);
				className = this.getClass().getName();
			} else {
				setStackTrace(t.getStackTrace());
				className = t.getClass().getName();
			}
			localizedMessage = t.getLocalizedMessage();

			// save original
			try {
				ByteArrayOutputStream bos = new ByteArrayOutputStream();
				ObjectOutputStream oos = new ObjectOutputStream(bos);
				oos.writeObject(t);
				oos.close();
				original = bos.toByteArray();
				//System.out.println("SubstituteException.init() "+original.length);
			} catch (Exception ex) {}
		}

		public Exception restoreException() {
			return restoreException(getCause(),null);
		}

		public Exception restoreException(ClassLoader loader) {
			return restoreException(getCause(),loader);
		}

		private Exception restoreException(Throwable cause, final ClassLoader loader) {
			// try to restore original
			if (original!=null) try {
				ByteArrayInputStream bis = new ByteArrayInputStream(original);
				ObjectInputStream ois = new ObjectInputStream(bis) {
					@Override
					protected Class resolveClass(ObjectStreamClass desc)
						throws IOException, ClassNotFoundException {
						if (loader==null)
							return super.resolveClass(desc);
						try {
							return loader.loadClass(desc.getName());
						} catch (ClassNotFoundException ex) {
							return super.resolveClass(desc);
						}
					}
				};
				Exception t = (Exception)ois.readObject();
				ois.close();
				return t;
			} catch (Throwable t) {}

			// try to restore by saved data
			try {
				Class c = Class.forName(className);
				Throwable t = null;
				try {
					Constructor m = c.getConstructor(new Class[] { String.class });
					t = (Throwable)m.newInstance(new Object[] { getMessage() });
				} catch (Exception ex) {
					t = (Throwable)c.newInstance();
					try {
						Field f = Throwable.class.getDeclaredField("detailMessage");
						f.setAccessible(true);
						f.set(t,localizedMessage);
					} catch (Exception ex2) {}
				}
				t.setStackTrace(getStackTrace());
				if (cause!=null) {
					if (cause instanceof SubstituteException)
						t.initCause(((SubstituteException)cause).restoreException());
					else t.initCause(cause);
				}
				return (Exception)t;
			} catch (Exception ex) {
				return this;
			}
		}

		public String getClassName() {
			return className;
		}

		@Override
		public String getLocalizedMessage() {
			return localizedMessage!=null ? localizedMessage : getMessage();
		}

		@Override
		public String toString() {
			String message = getLocalizedMessage();
			if (message==null) message = getMessage();
			return message!=null ? (className + ": " + message) : className;
		}
	}

	class ExceptionUtil {
		public static void renderText(StringBuilder sb, Throwable t, String msg, int depth, boolean stacktrace) {
			if (t instanceof InvocationTargetException)
				t = t.getCause();

			if (msg!=null) {
				for (int i=0; i<depth; i++) sb.append("  ");
				sb.append(msg);
				sb.append("\n");
			}
			for (int i=0; i<depth; i++) sb.append("  ");
			if (depth>0)
				sb.append("Caused by: ");
			sb.append(t.toString());
			sb.append("\n");
			if (stacktrace)
				renderText(sb,t.getStackTrace(),depth);

			List<Throwable> causes = new ArrayList();
			if (t instanceof Exceptions)
				causes.addAll(Arrays.asList(((Exceptions)t).getCauses()));
			else if (t.getCause()!=null) causes.add(t.getCause());
			for (Throwable c : causes)
				renderText(sb,c,null,depth+1,stacktrace);
		}

		public static void renderText(StringBuilder sb, StackTraceElement[] t, int depth) {
			for (int i=0; i<t.length; i++)
				renderText(sb,t[i],depth);
		}

		public static void renderText(StringBuilder sb, StackTraceElement t, int depth) {
			String cn = t.getClassName();
			if (cn.startsWith(Failure.class.getPackage().getName()))
				return;
			for (int j=0; j<depth; j++) sb.append("  ");
			sb.append("    at ");
			sb.append(t.toString());
			sb.append("\n");
		}
	}
}
