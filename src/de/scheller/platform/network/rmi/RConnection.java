package de.scheller.platform.network.rmi;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.SocketException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

import de.scheller.platform.network.rmi.Failure.ExceptionUtil;
import de.scheller.platform.network.rmi.Failure.MarshallingException;
import de.scheller.platform.network.rmi.Failure.RemoteException;
import de.scheller.platform.network.rmi.Failure.SubstituteException;
import de.scheller.platform.network.rmi.InternalApi.ActivityListener;
import de.scheller.platform.network.rmi.InternalApi.ConnectionListener2;
import de.scheller.platform.network.rmi.MethodApi.RestrictedService;
import de.scheller.platform.network.rmi.MethodApi.RestrictedServiceToken;

/**
 * This layer adds the connection message loop.
 *
 * @author Joshua Tauberer (tauberer@for.net)
 */
class RConnection extends RCore
{
	private InputStream sin;
	private OutputStream sout;
	private ObjectOutputStream out;
	private ObjectInputStream in;

	private Thread handleMessages;

	public static boolean useThreadPool = false;
	private static ExecutorService executor;

	public RConnection(String name) {
		super(name);
	}

	public static ActivityListener activityListener;

	void connect(InputStream in, OutputStream out) throws IOException {
		this.sin = new BufferedInputStream(in);
		this.sout = new BufferedOutputStream(out);
		this.out = new ObjectOutputStream(sout);
		this.handleMessages = new Thread(new Runnable() {
			public void run() {
				messageLoop();
				try { sin.close(); } catch (IOException ex) {}
				try { sout.close(); } catch (IOException ex) {}
				close();
				terminate = true;
				handleMessages = null;
			}
		},name);
		this.handleMessages.start();
	}

	@Override
	protected synchronized void send(RCommand cmd) throws IOException {
		if (terminate)
			throw new IOException("Cannot transmit data after socket has terminated.");
		if (logger.isLoggable(Level.FINEST))
			logger.finest("--> "+cmd);
		if (activityListener!=null)
			activityListener.commandRequest(cmd.transaction.transaction,cmd.command);
		out.reset();
		cmd.write(out);
		out.flush();
	}

	// kto: alternative ObjectInputStream to use an alternative class loader
	private class MyObjectInputStream extends ObjectInputStream {
		Map<String,Class> classCache = new HashMap();

		public MyObjectInputStream(InputStream in) throws IOException {
			super(in);
		}

		@Override
		protected Class resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
			String name = desc.getName();
			Class c = classCache.get(name);
			if (c!=null) return c;
			try {
				c = super.resolveClass(desc);
				classCache.put(name,c);
				return c;
			} catch (ClassNotFoundException ex) {
				c = baseclassloader.loadClass(name);
				classCache.put(name,c);
				return c;
			}
		}
	}

	private void messageLoop() {
		// say hello
		try {
			in = new MyObjectInputStream(sin);
			if (logger.isLoggable(Level.FINER))
				logger.finer("Thread started for "+name);
			send(RCommand.HELLO(name));
		} catch (IOException ex) {
			logger.log(Level.SEVERE,ex.getClass().getSimpleName() + ": " +
					ex.getMessage(),ex);
//			try {
//				ByteArrayOutputStream os = new ByteArrayOutputStream();
//				byte[] bytes = new byte[10000];
////				for (int r=sin.read(bytes); r!=-1; r=sin.read(bytes)) os.write(bytes,0,r);
//				int r=sin.read(bytes); os.write(bytes,0,r);
//				System.out.println(os.toString());
//			} catch (Exception ex2) {
//				ex2.printStackTrace();
//			}
		}

		if (useThreadPool)
			executor = Executors.newCachedThreadPool();

		// communicate
		RCommand cmd;
		Exception cmdReadEx = null;
		while (in!=null && !terminate) {
			try {
				gc(false);

				// process the next command
				cmdReadEx = null;
				cmd = new RCommand();
				try {
					cmd.read(in);
				} catch (EOFException ex) {
					logger.log(Level.WARNING,"eof @ " + cmd,ex);
					cmdReadEx = ex;
					if (cmd.command<0) {
						terminate = true;
						break;
					}
//				} catch (ClassNotFoundException ex) {
//					logger.log(Level.SEVERE,"command contains an unknown class",ex);
//					cmdReadEx = new MarshallingException("command contains an unknown class",ex);
				} catch (SocketException ex) {
					terminate = true;
					throw ex;
				} catch (Exception ex) {
					String name = ex.getClass().getSimpleName();
					logger.log(Level.SEVERE,name+" while reading command "+cmd.command,ex);
					cmdReadEx = new MarshallingException(name+" while reading command "+cmd.command,ex);
					ex.printStackTrace();
				}
				if (cmdReadEx!=null) // just for log
					cmd.command = RCommand.CMD_EXCEPTION;
				if (logger.isLoggable(Level.FINEST))
					logger.finest("<-- "+cmd);
				if (activityListener!=null)
					activityListener.commandResponse(
							cmd.tid!=null ? cmd.tid.transaction : 0,cmd.command);
				if (cmdReadEx!=null) {
					send(RCommand.EXCEPTION(cmd.transaction,cmdReadEx));
					continue;
				}

				switch (cmd.command) {
					case RCommand.CMD_HELLO: {
						name = name.replaceFirst("@",cmd.transaction+"@");
						handleMessages.setName("rmi-hello-"+name);
						break;
					}
					case RCommand.CMD_GOODBYE: {
						send(RCommand.GOODBYE());
						if (logger.isLoggable(Level.FINER))
							logger.finer("CMD_GOODBYE received");
						terminate = true;
						break;
					}
					case RCommand.CMD_SERVICE: {
						try {
							String name = (String)cmd.args[0];
							Object o = services.get(name);
							if (o==null)
								throw new RemoteException("service not found");
							if (o instanceof RestrictedService) {
								RestrictedService ro = (RestrictedService)o;
								ro.verifyAccess((RestrictedServiceToken)cmd.args[1]);
							}
							ObjectID id = referenceLocalObject(o);
							Set<Class> intfs = new HashSet();
							for (Class c=o.getClass(); c!=null; c=c.getSuperclass())
								intfs.addAll(Arrays.asList(c.getInterfaces()));
							send(RCommand.RETURN(cmd.transaction,new Object[] {
									intfs.toArray(new Class[intfs.size()]), id },Object[].class));
						} catch (RemoteException ex) {
							send(RCommand.EXCEPTION(cmd.transaction,ex));
						} catch (Exception ex) {
							send(RCommand.EXCEPTION(cmd.transaction,new RemoteException("data format error",ex)));
						}
						break;
					}
					case RCommand.CMD_RELEASE: {
						Object o = unreferenceLocalObject(cmd.obj);
						if (o instanceof RestrictedService) {
							RestrictedService ro = (RestrictedService)o;
							ro.releaseAccess();
						}
						break;
					}
					case RCommand.CMD_TRANSFER: {
						Object o = getLocal(cmd.obj);
						if (o==null)
							send(RCommand.EXCEPTION(cmd.transaction, new MarshallingException("the object has disconnected from the system")));
						if (isRemote(o))
							o = localClone(o);
						if (o instanceof Serializable)
							send(RCommand.RETURN(cmd.transaction, (Serializable)o, /*marshallClass*/(o.getClass())));
						else send(RCommand.EXCEPTION(cmd.transaction, new MarshallingException("cannot transfer class which does not support serialization: " + o.getClass())));
						break;
					}
					case RCommand.CMD_INVOKE: {
						Object o = getLocal(cmd.obj);
						if (o==null) {
							send(RCommand.EXCEPTION(cmd.transaction, new MarshallingException("the object has disconnected from the system "+cmd.obj+" "+cmd.method)));
							break;
						}
						if (true) { // test if method can be run in-thread
							if (useThreadPool && executor!=null) {
								executor.execute(new MsgProcess(cmd));
							} else {
								new Thread(new MsgProcess(cmd)).start();
							}
						} else {
							localinvoke(o,cmd.method,cmd.args,cmd.argsc,cmd.transaction,cmd.callstacksize);
						}
						break;
					}
					case RCommand.CMD_RETURN: {
						if (useThreadPool && executor!=null) {
							executor.execute(new MsgProcess(cmd));
						} else {
							new Thread(new MsgProcess(cmd)).start();
						}
						break;
					}
					case RCommand.CMD_EXCEPTION: {
						MethodReturn mret = openTransactions.get(cmd.tid);
						if (mret!=null) {
							Throwable t = (Throwable)cmd.args[0];
							if (t instanceof SubstituteException)
								t = ((SubstituteException)t).restoreException(baseclassloader);
							mret.methodException(t);
						} else
							logger.log(Level.SEVERE,"no waiting transaction for " + cmd.tid);
						break;
					}
					default:
						throw new Exception(String.valueOf(cmd));
				}
			} catch (SocketException ex) {
				logger.log(Level.SEVERE,"SocketException: " + ex.getMessage());
			} catch (IOException ex) {
				logger.log(Level.SEVERE,"IOException: " + ex.getMessage(),ex);
			} catch (Exception ex) {
				logger.log(Level.SEVERE,"Exception: " + ex.getMessage(),ex);
			} catch (Throwable ex) {
				logger.log(Level.SEVERE,"Throwable: " + ex.getMessage(),ex);
			}
		}
		if (cmdReadEx!=null) {
			logger.log(Level.SEVERE,"closing with exception: " + cmdReadEx.getMessage(),cmdReadEx);
		}
		if (terminate && connectionListener instanceof ConnectionListener2)
			((ConnectionListener2)connectionListener).connectionTerminated(this);
	}

	private class MsgProcess implements Runnable {
		private final RCommand cmd;

		private MsgProcess(RCommand cmd) {
			this.cmd = cmd;
		}

		public void run() {
			String threadid = (cmd.tid!=null ? cmd.tid : "") +
					"/" + cmd.transaction.transaction + "." + cmd.method;
			boolean setThreadId = logger.isLoggable(Level.FINE);
			String threadidOrig = setThreadId ? Thread.currentThread().getName() : null;
			try {
				threadconnection.set(RConnection.this);
				switch (cmd.command) {
					case RCommand.CMD_RETURN:
						if (setThreadId)
							Thread.currentThread().setName("rmi-return-"+threadid);
						MethodReturn mret = openTransactions.get(cmd.tid);
						if (mret!=null) {
							try {
								LocalProxyReference ref = getProxyRef(mret.invkTarget);
								Object invkTarget = ref!=null ? ref.get() : null;
								threadcallobject.set(invkTarget);

								Object r = unmarshall(cmd.args[0], /*unmarshallClass*/(Class)cmd.args[1]);
								mret.methodReturn(r);
							} catch (MarshallingException ex) {
								mret.methodException(ex);
								StringBuilder sb = new StringBuilder();
								ExceptionUtil.renderText(sb,ex,ex.getMessage(),0,false);
								System.err.println(sb.toString());
							} catch (Throwable t) {
								mret.methodException(new MarshallingException("unmarshall failed",t));
							} finally {
								threadcallobject.set(null);
							}
						} else logger.log(Level.SEVERE,name + ": no waiting transaction for " + cmd);
						break;

					case RCommand.CMD_INVOKE:
						if (setThreadId)
							Thread.currentThread().setName("rmi-invoke-"+threadid);
						Object o = getLocal(cmd.obj);
						if (o==null) {
							send(RCommand.EXCEPTION(cmd.transaction,new MarshallingException("the object "+cmd.obj+" is not available. was it garbage collected?")));
							break;
						}
						localinvoke(o,cmd.method,cmd.args,cmd.argsc,cmd.transaction,cmd.callstacksize);
						break;

					default:
						logger.log(Level.SEVERE,"unknown message");
				}
			} catch (Exception ex) {
				logger.log(Level.SEVERE,"Exception in MsgProcess " + name + ": " + ex.getMessage(),ex);
			} catch (Throwable ex) {
				if (ex instanceof NoClassDefFoundError && RConnection.ignoreNoClassDefFound)
					return;
				logger.log(Level.SEVERE,"Throwable in MsgProcess " + name + ": " + ex.getMessage(),ex);
			} finally {
				if (setThreadId)
					Thread.currentThread().setName(threadidOrig);
				threadconnection.set(null);
			}
		}
	}
}
