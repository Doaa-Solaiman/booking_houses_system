package de.scheller.platform.persist;

import java.util.Map;
import java.util.stream.Collectors;

import de.scheller.platform.persist.Parser.Statement;
import de.scheller.platform.persist.Parser.ValueExpr;
import de.scheller.platform.persist.Parser.ValueExpr.Condition;
import de.scheller.platform.persist.Parser.ValueExpr.PathExpr;
import de.scheller.platform.persist.Parser.VarDecl;

/**
 * @author kandzia
 */
public class ParserSqlBuilder extends ParserResultVisitorBase
{
	int index = 0;

	@Override
	protected void visit(Statement.Select s) {
		print("SELECT ");
		if (s.distinct) print("DISTINCT ");
		if (s.expr!=null && s.expr.size()>0) {
			index = 0;
			for (Map.Entry<ValueExpr,String> e : s.expr.entrySet()) {
				if (index>0) print(", ");
				visit(e.getKey());
				if (e.getValue()!=null)
					print(" AS "+maybeQuote(e.getValue()));
				index++;
			}
		}
		if (s.from!=null && s.from.size()>0) {
			print("\nFROM ");
			index = 0;
			for (VarDecl d : s.from)
				visit(d);
		}
		if (s.where!=null) {
			print("\nWHERE ");
			visit(s.where);
		}
		if (s.group!=null) {
			print("\nGROUP BY ");
			int i = 0;
			for (ValueExpr e : s.group) {
				if (i>0) print(",");
				visit(e);
				i++;
			}
		}
		if (s.having!=null) {
			print("\nHAVING ");
			visit(s.having);
		}
		if (s.order!=null) {
			print("\nORDER BY ");
			int i = 0;
			for (Map.Entry<ValueExpr,String> e : s.order.entrySet()) {
				if (i>0) print(",");
				visit(e.getKey());
				if (e.getValue()!=null)
					print(" "+e.getValue());
				i++;
			}
		}
		if (s.limit>=0)
			print("\nLIMIT "+s.limit);
		if (s.offset>=0)
			print("\nOFFSET "+s.offset);
	}

	@Override
	protected void visit(Statement.Update s) {
		print("UPDATE ");
		visit(s.range);
		if (s.values!=null && s.values.size()>0) {
			print("\nSET ");
			index = 0;
			for (Map.Entry<PathExpr,ValueExpr> e : s.values.entrySet()) {
				if (index>0) print(", ");
				visit(e.getKey());
				print(" = ");
				visit(e.getValue());
				index++;
			}
		}
		if (s.where!=null) {
			print("\nWHERE ");
			visit(s.where);
		}
	}

	/** Subclass should handle visit(VarDecl.RangeDecl) etc for correct SQL. */
	protected void visitInsert(Statement.Update s) {
		print("INSERT INTO ");
		visit(s.range);
		if (s.values!=null && s.values.size()>0) {
			print(" (");
			index = 0;
			for (Map.Entry<PathExpr,ValueExpr> e : s.values.entrySet()) {
				if (index>0) print(", ");
				visit(e.getKey());
				index++;
			}
			print(")\nVALUES (");
			index = 0;
			for (Map.Entry<PathExpr,ValueExpr> e : s.values.entrySet()) {
				if (index>0) print(", ");
				visit(e.getValue());
				index++;
			}
			print(")");
		}
	}

	@Override
	protected void visit(Statement.Delete d) {
		print("DELETE");
		print("\nFROM ");
		visit(d.from);
		if (d.where!=null) {
			print("\nWHERE ");
			visit(d.where);
		}
	}

	@Override
	protected void visit(VarDecl.RangeDecl d) {
		if (index++>0) print(", ");
		print(ParserUtil.path(d.path));
		if (d.alias!=null)
			print(" AS "+maybeQuote(d.alias));
	}

	@Override
	protected void visit(VarDecl.MemberDecl d) {
		if (index++>0) print(", ");
		print("IN("+ParserUtil.path(d.path)+")");
		if (d.alias!=null)
			print(" AS "+maybeQuote(d.alias));
	}

	@Override
	protected void visit(VarDecl.JoinDecl d) {
		if (index++>0) print("\n");
		print(ParserUtil.joinspec(d).toUpperCase());
		if (d.outer) print(" OUTER");
		print(" JOIN ");
		if (d.fetch) print("FETCH ");
		print(ParserUtil.path(d.path));
		if (d.alias!=null)
			print(" AS "+maybeQuote(d.alias));
		if (d.on!=null) {
			print(" ON ");
			visit(d.on);
		}
	}

	@Override
	protected void visit(Condition.Compare c) {
		visit(c.expr.get(0));
		print(" "+c.op+" ");
		visit(c.expr.get(1));
	}

	@Override
	protected void visit(Condition.Null c) {
		visit(c.expr.get(0));
		if (c.not)
			print(" IS NOT NULL");
		else print(" IS NULL");
	}

	@Override
	protected void visit(Condition.Not c) {
		print("NOT ");
		visit(c.expr.get(0));
	}

	@Override
	protected void visit(Condition.And c) {
		boolean b = c.expr.size()>1;
		if (b) print("(");
		visit(c.expr.get(0));
		for (int i=1; i<c.expr.size(); i++) {
			print(" AND ");
			visit(c.expr.get(i));
		}
		if (b) print(")");
	}

	@Override
	protected void visit(Condition.Or c) {
		boolean b = c.expr.size()>1;
		if (b) print("(");
		visit(c.expr.get(0));
		for (int i=1; i<c.expr.size(); i++) {
			print(" OR ");
			visit(c.expr.get(i));
		}
		if (b) print(")");
	}

	@Override
	protected void visit(Condition.In c) {
		visit(c.expr.get(0));
		if (c.not) print(" NOT");
		print(" IN (");
		for (int i=1; i<c.expr.size(); i++) {
			if (i>1) print(",");
			visit(c.expr.get(i));
		}
		print(")");
	}

	@Override
	protected void visit(Condition.Like c) {
		visit(c.expr.get(0));
		if (c.not) print(" NOT");
		print(" LIKE ");
		visit(c.expr.get(1));
	}

	@Override
	protected void visit(Condition.WhenThen c) {
		print("WHEN ");
		visit(c.expr.get(0));
		print(" THEN ");
		visit(c.expr.get(1));
	}

	@Override
	protected void visit(Condition.Else c) {
		print("ELSE ");
		visit(c.expr.get(0));
	}

	@Override
	protected void visit(ValueExpr.PathExpr e) {
		StringBuilder sb = new StringBuilder();
		sb.append(maybeQuote(e.start));
		if (e.access!=null)
			for (String step : e.access)
				sb.append(".").append(maybeQuote(step));
		print(sb.toString());
	}

	private String maybeQuote(String s) {
		if (s==null)
			return null;
		if (s.length()==1 || "id".equalsIgnoreCase(s))
			return s;
		if (s.matches("(?i)[A-Z_]+")) // e.g. AUTO_INCREMENT
			return "`"+s+"`";
		return s;
	}

	public static String path(ValueExpr.PathExpr p) {
		if (p.access==null)
			return p.start;
		return p.access.stream().collect(Collectors.joining(".",p.start+".",""));
	}

	@Override
	protected void visit(ValueExpr.Literal e) {
		print(e.value);
	}

	@Override
	protected void visit(ValueExpr.Parameter e) {
		print("?");
	}

	@Override
	protected void visit(ValueExpr.Function e) {
		ValueExpr lastArg = e.args.get(e.args.size()-1);
		boolean appendLast = lastArg instanceof ValueExpr.Literal &&
				((ValueExpr.Literal)lastArg).value.matches("['\"`]\\+\\+.*['\"`]");
		print(e.func.start.toUpperCase());
		print("(");
		if (e.distinct) print("DISTINCT ");
		for (int i=0; i<e.args.size()-(appendLast?1:0); i++) {
			if (i>0) print(",");
			visit(e.args.get(i));
		}
		if (appendLast) {
			String a = ((ValueExpr.Literal)lastArg).value;
			print(" "+a.substring(3,a.length()-1)); // cut quotes & prefix
		}
		print(")");
	}

	@Override
	protected void visit(ValueExpr.Operation o) {
		print("(");
		visit(o.left);
		print(o.op);
		visit(o.right);
		print(")");
	}

	@Override
	protected void visit(ValueExpr.Case e) {
		print(e.func.start.toUpperCase());
		print(" ");
		for (int i=0; i<e.args.size(); i++) {
			if (i>0) print(" ");
			visit(e.args.get(i));
		}
		print(" END");
	}

	protected void print(String s) {
		System.out.print(s);
	}
}
