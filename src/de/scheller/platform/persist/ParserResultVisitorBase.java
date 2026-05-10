package de.scheller.platform.persist;

import de.scheller.platform.persist.Parser.Statement;
import de.scheller.platform.persist.Parser.ValueExpr;
import de.scheller.platform.persist.Parser.ValueExpr.Condition;
import de.scheller.platform.persist.Parser.VarDecl;

/**
 * @author kandzia
 */
public class ParserResultVisitorBase
{
	protected boolean debug;

	protected void visit(Statement s) {
		/**/ if (s instanceof Statement.Subquery) visit((Statement.Subquery)s);
		else if (s instanceof Statement.Select) visit((Statement.Select)s);
		else if (s instanceof Statement.Delete) visit((Statement.Delete)s);
		else if (s instanceof Statement.Update) visit((Statement.Update)s);
	}

	protected void visit(VarDecl d) {
		/**/ if (d instanceof VarDecl.MemberDecl) visit((VarDecl.MemberDecl)d);
		else if (d instanceof VarDecl.JoinDecl) visit((VarDecl.JoinDecl)d);
		else if (d instanceof VarDecl.RangeDecl) visit((VarDecl.RangeDecl)d);
	}

	protected void visit(ValueExpr e) {
		/**/ if (e instanceof ValueExpr.PathExpr) visit((ValueExpr.PathExpr)e);
		else if (e instanceof ValueExpr.Literal) visit((ValueExpr.Literal)e);
		else if (e instanceof ValueExpr.Parameter) visit((ValueExpr.Parameter)e);
		else if (e instanceof ValueExpr.Construct) visit((ValueExpr.Construct)e);
		else if (e instanceof ValueExpr.Case) visit((ValueExpr.Case)e);
		else if (e instanceof ValueExpr.Function) visit((ValueExpr.Function)e);
		else if (e instanceof ValueExpr.Operation) visit((ValueExpr.Operation)e);
		else if (e instanceof Condition.BoolExpr) visit((Condition.BoolExpr)e);
	}

	protected void visit(Condition.BoolExpr c) {
		/**/ if (c instanceof Condition.Compare) visit((Condition.Compare)c);
		else if (c instanceof Condition.Null) visit((Condition.Null)c);
		else if (c instanceof Condition.Not) visit((Condition.Not)c);
		else if (c instanceof Condition.And) visit((Condition.And)c);
		else if (c instanceof Condition.Or) visit((Condition.Or)c);
		else if (c instanceof Condition.In) visit((Condition.In)c);
		else if (c instanceof Condition.Like) visit((Condition.Like)c);
		else if (c instanceof Condition.Empty) visit((Condition.Empty)c);
		else if (c instanceof Condition.Between) visit((Condition.Between)c);
		else if (c instanceof Condition.Member) visit((Condition.Member)c);
		else if (c instanceof Condition.Exists) visit((Condition.Exists)c);
		else if (c instanceof Condition.AllAny) visit((Condition.AllAny)c);
		else if (c instanceof Condition.WhenThen) visit((Condition.WhenThen)c);
		else if (c instanceof Condition.Else) visit((Condition.Else)c);
	}

	protected void visit(Statement.Subquery s) {
		if (debug) visitLog(s);
	}
	protected void visit(Statement.Select s) {
		if (debug) visitLog(s);
	}
	protected void visit(Statement.Delete s) {
		if (debug) visitLog(s);
	}
	protected void visit(Statement.Update s) {
		if (debug) visitLog(s);
	}

	protected void visit(VarDecl.MemberDecl d) {
		if (debug) visitLog(d);
	}
	protected void visit(VarDecl.JoinDecl d) {
		if (debug) visitLog(d);
	}
	protected void visit(VarDecl.RangeDecl d) {
		if (debug) visitLog(d);
	}

	protected void visit(ValueExpr.PathExpr e) {
		if (debug) visitLog(e);
	}
	protected void visit(ValueExpr.Literal e) {
		if (debug) visitLog(e);
	}
	protected void visit(ValueExpr.Parameter e) {
		if (debug) visitLog(e);
	}
	protected void visit(ValueExpr.Construct e) {
		if (debug) visitLog(e);
	}
	protected void visit(ValueExpr.Function e) {
		if (debug) visitLog(e);
	}
	protected void visit(ValueExpr.Operation e) {
		if (debug) visitLog(e);
	}
	protected void visit(ValueExpr.Case e) {
		if (debug) visitLog(e);
	}

	protected void visit(Condition.Compare c) {
		if (debug) visitLog(c);
	}
	protected void visit(Condition.Null c) {
		if (debug) visitLog(c);
	}
	protected void visit(Condition.Not c) {
		if (debug) visitLog(c);
	}
	protected void visit(Condition.And c) {
		if (debug) visitLog(c);
	}
	protected void visit(Condition.Or c) {
		if (debug) visitLog(c);
	}
	protected void visit(Condition.In c) {
		if (debug) visitLog(c);
	}
	protected void visit(Condition.Like c) {
		if (debug) visitLog(c);
	}
	protected void visit(Condition.Empty c) {
		if (debug) visitLog(c);
	}
	protected void visit(Condition.Between c) {
		if (debug) visitLog(c);
	}
	protected void visit(Condition.Member c) {
		if (debug) visitLog(c);
	}
	protected void visit(Condition.Exists c) {
		if (debug) visitLog(c);
	}
	protected void visit(Condition.AllAny c) {
		if (debug) visitLog(c);
	}
	protected void visit(Condition.WhenThen c) {
		if (debug) visitLog(c);
	}
	protected void visit(Condition.Else c) {
		if (debug) visitLog(c);
	}

	protected void visitLog(Object o) {
		System.out.println(".visit("+o.getClass().getSimpleName()+") "+o);
	}
}
