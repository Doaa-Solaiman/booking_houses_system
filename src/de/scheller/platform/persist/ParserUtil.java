package de.scheller.platform.persist;

import java.util.Map;
import java.util.stream.Collectors;

import de.scheller.platform.persist.Parser.Statement;
import de.scheller.platform.persist.Parser.ValueExpr;
import de.scheller.platform.persist.Parser.VarDecl;

/**
 * @author kandzia
 */
public class ParserUtil
{
	public static String path(ValueExpr.PathExpr p) {
		if (p.access==null)
			return p.start;
		return p.access.stream().collect(Collectors.joining(".",p.start+".",""));
	}

	public static String joinspec(VarDecl.JoinDecl d) {
		if (d.inner) return "inner";
		if (d.full) return "full"; //"full outer";
		if (d.left) return "left"; // "left outer";
		if (d.right) return "right"; // "right outer";
		return "";
	}

	public static void print(Object x, StringBuilder sb) {
		print(x,sb,0,0);
	}

	private static void print(Object x, StringBuilder sb, int indent) {
		print(x,sb,indent,0);
	}

	private static void print(Object x, StringBuilder sb, int indent, int index) {
		indent(indent,sb);
		indent = Math.abs(indent);
		/****/ if (x instanceof Statement.Select) {
			Statement.Select s = (Statement.Select)x;
			sb.append("SELECT ");
			indent++;
			if (s.distinct)
				indent(indent,sb).append("DISTINCT");
			if (s.expr!=null) {
				int i = 0;
				for (Map.Entry<ValueExpr,String> e : s.expr.entrySet()) {
					indent(indent,sb);
					sb.append(e.getValue()!=null ? e.getValue()+": " : i+": ");
					print(e.getKey(),sb,-indent,i++);
				}
			}
			if (s.from!=null) {
				indent(indent,sb).append("FROM");
				int i = 0;
				for (VarDecl d : s.from)
					print(d,sb,indent+1,index++);
			}
			if (s.where!=null) {
				indent(indent,sb).append("WHERE");
				print(s.where,sb,indent+1);
			}
			if (s.group!=null) {
				indent(indent,sb).append("GROUP");
				for (ValueExpr g : s.group)
					print(g,sb,indent+1);
			}
			if (s.having!=null) {
				indent(indent,sb).append("HAVING");
				print(s.having,sb,indent+1);
			}
			if (s.order!=null) {
				indent(indent,sb).append("ORDER");
				int i = 0;
				for (Map.Entry<ValueExpr,String> e : s.order.entrySet()) {
					indent(indent+1,sb);
					if (e.getValue()!=null)
						sb.append(e.getValue().toUpperCase()+" ");
					print(e.getKey(),sb,-(indent+1),i++);
				}
			}
			if (s.limit>=0) {
				indent(indent,sb).append("LIMIT "+s.limit);
			}
		} else if (x instanceof Statement.Delete) {
			Statement.Delete s = (Statement.Delete)x;
			sb.append("DELETE ");
			indent++;
			if (s.from!=null) {
				indent(indent,sb).append("FROM");
				print(s.from,sb,indent+1);
			}
			if (s.where!=null) {
				indent(indent,sb).append("WHERE");
				print(s.where,sb,indent+1);
			}
		} else if (x instanceof Statement.Update) {
			Statement.Update s = (Statement.Update)x;
			sb.append("UPDATE ");
			indent++;
			if (s.range!=null) {
				indent(indent,sb).append("RANGE");
				print(s.range,sb,indent+1);
			}
			if (s.values!=null) {
				indent(indent,sb).append("SET");
				int i = 0;
				for (Map.Entry<ValueExpr.PathExpr,ValueExpr> e : s.values.entrySet()) {
					indent(indent+1,sb);
					print(e.getKey(),sb,-(indent+1),i);
					sb.append(" =");
					print(e.getValue(),sb,indent+2,i++);
				}
			}
			if (s.where!=null) {
				indent(indent,sb).append("WHERE");
				print(s.where,sb,indent+1);
			}
		} else if (x instanceof VarDecl.JoinDecl) { // FROM
			VarDecl.JoinDecl d = (VarDecl.JoinDecl)x;
			sb.append(d.alias!=null ? d.alias+": " : index+": ");
			if (d.left && d.right) sb.append("FULL OUTER JOIN ");
			else if (d.right) sb.append("RIGHT OUTER JOIN ");
			else if (d.left) sb.append("LEFT OUTER JOIN ");
			else sb.append("INNER JOIN ");
			if (d.fetch) sb.append("FETCH ");
			print(d.path,sb,-indent);
			if (d.on!=null)
				print(d.on,sb,indent+1);
		} else if (x instanceof VarDecl.RangeDecl) { // FROM
			VarDecl.RangeDecl d = (VarDecl.RangeDecl)x;
			sb.append(d.alias!=null ? d.alias+": " : index+": ");
			print(d.path,sb,-indent);
		} else if (x instanceof VarDecl.MemberDecl) { // FROM
			VarDecl.MemberDecl d = (VarDecl.MemberDecl)x;
			sb.append(d.alias!=null ? d.alias+": " : index+": ");
			sb.append("(LEFT INNER JOIN) collection ");
			print(d.path,sb,-indent);
		} else if (x instanceof ValueExpr.Condition.BoolExpr) { // WHERE/HAVING
			ValueExpr.Condition.BoolExpr c = (ValueExpr.Condition.BoolExpr)x;
			if (c.not) sb.append("NOT ");
			if (x instanceof ValueExpr.Condition.Compare)
				sb.append("COMPARE ");
			sb.append(c.op.toUpperCase());
			for (ValueExpr e : c.expr)
				print(e,sb,indent+1);
		} else if (x instanceof ValueExpr.Construct) {
			ValueExpr.Construct e = (ValueExpr.Construct)x;
			sb.append("NEW ");
			print(e.func,sb,-indent);
			sb.append("()");
			for (ValueExpr a : e.args)
				print(a,sb,indent+1);
		} else if (x instanceof ValueExpr.Function) {
			ValueExpr.Function e = (ValueExpr.Function)x;
			print(e.func,sb,-indent);
			sb.append("()");
			for (ValueExpr a : e.args)
				print(a,sb,indent+1);
		} else if (x instanceof ValueExpr.PathExpr) {
			ValueExpr.PathExpr e = (ValueExpr.PathExpr)x;
			sb.append(e.start);
			if (e.access!=null) {
				sb.append(".");
				sb.append(e.access.stream().collect(Collectors.joining(".")));
			}
		} else if (x instanceof ValueExpr.Operation) {
			ValueExpr.Operation e = (ValueExpr.Operation)x;
			sb.append(e.op);
			if (e.left!=null) { // left is null for unary/prefix op
				print(e.left,sb,indent+1);
				print(e.right,sb,indent+1);
			} else {
				sb.append(" ");
				print(e.right,sb,-indent);
			}
		} else if (x instanceof ValueExpr.Parameter) {
			ValueExpr.Parameter e = (ValueExpr.Parameter)x;
			sb.append(e.name);
		} else if (x instanceof ValueExpr.Literal) {
			ValueExpr.Literal e = (ValueExpr.Literal)x;
			sb.append(e.value);
		} else sb.append(x);
	}

	private static StringBuilder indent(int indent, StringBuilder sb) {
		if (indent<0)
			return sb;
		sb.append("\n");
		for (int i=0; i<indent; i++)
			sb.append("  ");
		return sb;
	}
}
