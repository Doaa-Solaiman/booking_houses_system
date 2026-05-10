package de.scheller.platform.network.rmi;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.Serializable;
import java.lang.reflect.Method;

import de.scheller.platform.network.rmi.MethodApi.RestrictedServiceToken;
import de.scheller.platform.network.rmi.RCore.ObjectID;

/**
 * @author Joshua Tauberer (tauberer@for.net)
 */
class RCommand implements Serializable
{
	public static final long serialVersionUID = 0x523244324333504fl; // R2D2C3PO

	public int command = -1;
	public CommandID transaction;
	public int callstacksize;

	public ObjectID obj;
	public String method;
	public CommandID tid;
	public Class[] argsc;
	public Serializable[] args;

	public static final int CMD_HELLO		= 0;
	public static final int CMD_GOODBYE		= 1;
	public static final int CMD_SERVICE		= 101;
	public static final int CMD_RELEASE		= 110;
	public static final int CMD_TRANSFER	= 150;
	public static final int CMD_INVOKE		= 200;
	public static final int CMD_RETURN		= 300;
	public static final int CMD_EXCEPTION	= 301;

	RCommand() {
		transaction = new CommandID(-1);
	}

	private RCommand(int cmd) {
		transaction = new CommandID(true);
		command = cmd;
	}

	public static RCommand HELLO(String name) {
		RCommand j = new RCommand(CMD_HELLO);
		j.args = new Serializable[] { name };
		return j;
	}
	public static RCommand GOODBYE() {
		RCommand j = new RCommand(CMD_GOODBYE);
		return j;
	}
	public static RCommand SERVICE(String name, RestrictedServiceToken token) {
		RCommand j = new RCommand(CMD_SERVICE);
		j.args = new Serializable[] { name, token };
		return j;
	}
	public static RCommand RELEASE(ObjectID obj) {
		RCommand j = new RCommand(CMD_RELEASE);
		j.obj = obj;
		return j;
	}
	public static RCommand TRANSFER(ObjectID obj) {
		RCommand j = new RCommand(CMD_TRANSFER);
		j.obj = obj;
		return j;
	}
	public static RCommand INVOKE(ObjectID obj, Method method, Class[] argsc, Serializable[] args, int stack) {
		RCommand j = new RCommand(CMD_INVOKE);
		j.obj = obj;
		j.method = method.getName();
		j.argsc = argsc;
		j.args = args;
		j.callstacksize = stack;
		return j;
	}
	public static RCommand RETURN(CommandID transid, Serializable value, Class valueClass) {
		RCommand j = new RCommand(CMD_RETURN);
		j.tid = transid;
		j.args = new Serializable[] { value, valueClass };
		return j;
	}
	public static RCommand EXCEPTION(CommandID transid, Throwable exception) {
		RCommand j = new RCommand(CMD_EXCEPTION);
		j.tid = transid;
		j.args = new Serializable[] { exception };
		return j;
	}

	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
		switch (command) {
			case CMD_HELLO:
				sb.append(transaction);
				sb.append(" hello"); break;
			case CMD_GOODBYE:
				sb.append(transaction);
				sb.append(" goodbye"); break;
			case CMD_SERVICE:
				sb.append(transaction);
				sb.append(" find service "+args[0]); break;
			case CMD_RELEASE:
				sb.append(transaction);
				sb.append(" release instance "+obj); break;
			case CMD_INVOKE:
				sb.append(transaction);
				sb.append(" invoke ");
				sb.append(obj);
				sb.append("::");
				sb.append(method);
				sb.append('(');
				if (args!=null)
					for (int i=0; i<args.length; i++) {
						Object a = args[i];
						sb.append(' ');
						sb.append(a!=null ? a.toString() : "null");
						if (a instanceof Serialized) {
							Serialized mo = (Serialized)a;
							sb.append('(');
							if (mo.varstat==Serialized.VAR_SERIALIZED) {
								sb.append(mo.data.length);
								sb.append(" bytes (serialized)");
							} else {
								sb.append(mo.cls);
								sb.append('#');
								sb.append(mo.id);
								if (mo.varstat==Serialized.SERVER_SENDER)
									sb.append(" (ref to remote obj)");
								if (mo.varstat==Serialized.SERVER_RECEIVER)
									sb.append(" (remote side is recv)");
							}
							break;
						}
						sb.append(',');
					}
				if (sb.charAt(sb.length()-1)==',') sb.deleteCharAt(sb.length()-1);
				if (args!=null) sb.append(' ');
				sb.append(')');
				break;
			case CMD_RETURN:
				sb.append(tid);
				sb.append(" return ");
				if (args[0]==null) { sb.append("null"); break; }
				if (args[0] instanceof Serialized) {
					Serialized mo = (Serialized)args[0];
					if (mo.varstat==Serialized.VAR_SERIALIZED) {
						sb.append(mo.data.length);
						sb.append(" bytes (serialized)");
					} else {
						sb.append(mo.cls);
						sb.append('#');
						sb.append(mo.id);
						if (mo.varstat==Serialized.SERVER_SENDER)
							sb.append(" (ref to remote obj)");
						if (mo.varstat==Serialized.SERVER_RECEIVER)
							sb.append(" (remote side is recv)");
					}
					break;
				}
				sb.append(args[0].toString());
				sb.append(" (direct)");
				break;
			case CMD_EXCEPTION:
				sb.append(tid);
				sb.append(" return exception "+(args!=null && args[0]!=null ? args[0].getClass() : "n/a"));
				break;
			default:
				sb.append(transaction);
				sb.append(" unknown, command id = "+command);
		}
		return sb.toString();
	}

	public void write(ObjectOutput out) throws IOException {
		out.writeInt(command);
		out.writeLong(transaction.transaction);
		out.writeInt(callstacksize);
		out.writeObject(obj);
		out.writeObject(method);
		out.writeObject(tid);
		out.writeObject(argsc);
		out.writeObject(args);
	}

	public void read(ObjectInput in) throws IOException, ClassNotFoundException {
		command = in.readInt();
		transaction.transaction = in.readLong();
		callstacksize = in.readInt();
		obj = (ObjectID)in.readObject();
		method = (String)in.readObject();
		tid = (CommandID)in.readObject();
		argsc = (Class[])in.readObject();
		args = (Serializable[])in.readObject();
	}

	static class CommandID implements Serializable {
		public static final long serialVersionUID = 0x523244322d434d44l; // R2D2-CMD

		private static long tcounter = 0;
		private static synchronized long next() { return tcounter++; }
		long transaction;

		CommandID(long id) { transaction = id; }
		CommandID(boolean b) { transaction = next(); }

		@Override public String toString() { return Long.toString(transaction); }
		@Override public int hashCode() { return (int)(transaction & 0xFFFF); }
		@Override public boolean equals(Object o) {
			if (o==this) return true;
			if (o instanceof CommandID==false) return false;
			return transaction==((CommandID)o).transaction;
		}
	}
}
