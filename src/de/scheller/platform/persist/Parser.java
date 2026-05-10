package de.scheller.platform.persist;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.scheller.platform.persist.Parser.ValueExpr.Condition;
import de.scheller.platform.persist.Parser.ValueExpr.Operation;
import de.scheller.platform.persist.Parser.ValueExpr.PathExpr;

/**
 * @author kandzia
 */
public class Parser
{
	public Lexer lx;

	private void ex(String msg) {
		throw new RuntimeException(msg);
	}

	public void parse(String s) {
		this.lx = new Lexer(s,statementPatterns());
	}

	public static Statement statement(String statement) {
		Parser p = new Parser();
		p.parse(statement);
		return p.statement();
	}

	public Statement statement() {
		if (lx.check("select")) { lx.tokenIndex--; return select(); }
		if (lx.check("from"))   { lx.tokenIndex--; return select(); }
		if (lx.check("update")) { lx.tokenIndex--; return update(); }
		if (lx.check("delete")) { lx.tokenIndex--; return delete(); }
//		ex("unknown statement type");
		return null;
	}

	private Statement.Select select() {
		boolean select = lx.check("select");
		boolean distinct = lx.check("distinct");
		int limit,offset,number; // not JPA & not HQL
		limit = offset = number = -1;
		Map<ValueExpr,String> expr = new LinkedHashMap();
		if (select) { // JPA always expects "select"
			if (lx.check("top")) limit = Integer.parseInt(lx.stringOf("int"));
			expr.put(selectExpression(true),alias());
			while (lx.check(","))
				expr.put(selectExpression(true),alias());
		} else expr = null;
		List<VarDecl> from = fromClause(true);
		//if (from==null) ex("from clause expected"); // JPA is stricter here
		Condition.BoolExpr where = whereClause();
		List<ValueExpr> group = groupClause();
		Condition.BoolExpr having = havingClause();
		Map<ValueExpr,String> order = orderClause();
		try {
			if (lx.check("limit"))
				limit = number = Integer.parseInt(lx.stringOf("int"));
			if (lx.check(",")) {
				offset = number; limit = Integer.parseInt(lx.stringOf("int"));
			} else if (lx.check("offset")) offset = Integer.parseInt(lx.stringOf("int"));
		} catch (NumberFormatException ignore) {
			System.out.println("Parser.select() TODO support placeholder for limit/offset?");
		}
		return new Statement.Select(distinct,expr,from,where,group,having,order,limit,offset);
	}

	private Statement.Subquery subquery() {
		boolean select = lx.check("select");
		boolean distinct = lx.check("distinct");
		Map<ValueExpr,String> expr = new LinkedHashMap();
		if (select) // JPA always expects "select"
			expr.put(selectExpression(false),alias());
		else expr = null;
		List<VarDecl> from = fromClause(false);
		if (from==null) return null;
		Condition.BoolExpr where = whereClause();
		List<ValueExpr> group = groupClause();
		Condition.BoolExpr having = havingClause();
		return new Statement.Subquery(distinct,expr,from,where,group,having);
	}

	private Statement.Delete delete() {
		if (!lx.check("delete")) return null;
		lx.check("from");
		VarDecl from = rangeVariableDeclaration();
		if (from==null) ex("from clause expected");
		Condition.BoolExpr where = whereClause();
		return new Statement.Delete(from,where);
	}

	private Statement.Update update() {
		if (!lx.check("update")) return null;
		VarDecl range = rangeVariableDeclaration();
		if (range==null) ex("range declaration expected");
		if (!lx.check("set")) ex("'set' expected");
		Map<PathExpr,ValueExpr> values = new LinkedHashMap();
		updateItem(values);
		while (lx.check(","))
			updateItem(values);
		Condition.BoolExpr where = whereClause();
		return new Statement.Update(range,where,values);
	}

	private void updateItem(Map<PathExpr,ValueExpr> values) {
		PathExpr key = pathExpression();
		if (!lx.check("=")) ex("'=' expected");
		ValueExpr value = valueExpression();
		values.put(key,value);
	}

	private List<VarDecl> fromClause(boolean toplevel) {
		if (!lx.check("from")) return null;
		List<VarDecl> expr = new ArrayList();
		if (!toplevel) {
			VarDecl.RangeDecl decl = null;
			if ((decl = collectionMemberDeclaration())!=null) expr.add(decl);
			else expr.addAll(variableDeclaration());
		} else expr.addAll(variableDeclaration());
		if (expr.isEmpty()) return null;
		while (lx.check(",")) {
			VarDecl.RangeDecl decl = null;
			if ((decl = collectionMemberDeclaration())!=null) expr.add(decl);
			else expr.addAll(variableDeclaration());
		}
		if (expr.isEmpty()) return null;
		return expr;
	}

	public Condition.BoolExpr whereClause() {
		if (!lx.check("where")) return null;
		return conditionExpression();
	}

	public List<ValueExpr> groupClause() {
		if (lx.string("(?i)group.*")==null) return null;
		List<ValueExpr> expr = new ArrayList();
		expr.add(valueExpressionSimple());
		while (lx.check(","))
			expr.add(valueExpressionSimple());
		return expr;
	}

	public Condition.BoolExpr havingClause() {
		if (!lx.check("having")) return null;
		return conditionExpression();
	}

	public Map<ValueExpr,String> orderClause() {
		if (lx.string("(?i)order.*")==null) return null;
		Map<ValueExpr,String> order = new LinkedHashMap();
		orderItem(order);
		while (lx.check(","))
			orderItem(order);
		return order;
	}

	private void orderItem(Map<ValueExpr,String> order) {
		ValueExpr value = valueExpression();
		if (value==null) return;
		String m,mode = "";
		while ((m = lx.stringOf("keyword.order"))!=null)
			mode += m+" ";
		mode = mode.trim();
		if (mode.length()==0)
			mode = null;
		order.put(value,mode);
	}

	private Condition.BoolExpr conditionExpression() {
		List<Condition.BoolExpr> expr = new ArrayList();
		expr.add(conditionTerm());
		while (lx.check("or") || lx.check("||"))
			expr.add(conditionTerm());
		if (expr.size()==0) return null;
		if (expr.size()==1) return expr.get(0);
		return new Condition.Or(expr);
	}

	private Condition.BoolExpr conditionTerm() {
		List<Condition.BoolExpr> expr = new ArrayList();
		expr.add(conditionFactor());
		while (lx.check("and") || lx.check("&&"))
			expr.add(conditionFactor());
		if (expr.size()==0) return null;
		if (expr.size()==1) return expr.get(0);
		return new Condition.And(expr);
	}

	private Condition.BoolExpr conditionFactor() {
		if (lx.check("not"))
			return new Condition.Not(conditionExpression());
		return conditionPrimary();
	}

	private Condition.BoolExpr conditionPrimary() {
		if (lx.check("(")) {
			if (lx.check("select") || lx.check("from")) { // JPA always expects "select"
				lx.tokenIndex -= 2; // unsee "select" and "("
				return simpleCondExpression();
			}
			Condition.BoolExpr c = conditionExpression();
			if (!lx.check(")")) ex("')' expected");
			return c;
		}
		return simpleCondExpression();
	}

	private Condition.BoolExpr simpleCondExpression() {
		Condition.BoolExpr expr = null;
		if ((expr = existsExpression())!=null) return expr;
		if ((expr = collectionMember())!=null) return expr;
		if ((expr = emptyCollection())!=null) return expr;
		if ((expr = nullComparison())!=null) return expr;
		if ((expr = inExpression())!=null) return expr;
		if ((expr = likeExpression())!=null) return expr;
		if ((expr = betweenExpression())!=null) return expr;
		if ((expr = comparisonExpression())!=null) return expr;
		return new Condition.BoolExpr(false,pathExpression());
	}

	private Condition.Exists existsExpression() {
		String compare = keyword(lx.stringOf("compare.word"));
		if (compare==null || !compare.contains("exists"))
			return null;
		boolean not = compare.contains("not");
		if (!lx.check("(")) ex("'(' expected");
		Statement.Subquery subquery = subquery();
		if (!lx.check(")")) ex("')' expected");
		return new Condition.Exists(not,subquery);
	}

	private Condition.Member collectionMember() {
		int index = lx.tokenIndex;
		ValueExpr left = parameter();
		if (left==null) left = pathExpression();
		String compare = keyword(lx.stringOf("compare.word"));
		if (compare==null || !compare.contains("member")) {
			lx.tokenIndex = index;
			return null;
		}
		boolean not = compare.contains("not");
		ValueExpr right = pathExpression();
		if (right==null) ex("collection expected");
		return new Condition.Member(not,left,right);
	}

	private Condition.Empty emptyCollection() {
		int index = lx.tokenIndex;
		ValueExpr left = pathExpression();
		String compare = keyword(lx.stringOf("compare.word"));
		if (compare==null || !compare.contains("empty")) {
			lx.tokenIndex = index;
			return null;
		}
		boolean not = compare.contains("not");
		return new Condition.Empty(not,left);
	}

	private Condition.Null nullComparison() {
		int index = lx.tokenIndex;
		ValueExpr left = valueExpression();//pathExpression(); // JPA is stricter here
		String compare = keyword(lx.stringOf("compare.word"));
		if (compare==null || !compare.contains("null")) {
			lx.tokenIndex = index;
			return null;
		}
		boolean not = compare.contains("not");
		return new Condition.Null(not,left);
	}

	private Condition.In inExpression() {
		int index = lx.tokenIndex;
		ValueExpr left = valueExpression();//inItem(); // JPA is stricter here
		String compare = keyword(lx.stringOf("compare.word"));
		if (compare==null || !compare.contains("in")) {
			lx.tokenIndex = index;
			return null;
		}
		boolean not = compare.contains("not");
		boolean paren = lx.check("(");
		Statement.Subquery subquery = subquery();
		if (subquery!=null) {
			if (!paren) ex("'(' expected");
			if (!lx.check(")")) ex("')' expected");
			return new Condition.In(not,Arrays.asList(left,subquery));
		}
		List<ValueExpr> expr = new ArrayList();
		expr.add(left);
		expr.add(inItem());
		while (lx.check(",")) {
			if (!paren) ex("'(' expected");
			expr.add(inItem());
		}
		if (paren && !lx.check(")")) ex("')' expected");
		return new Condition.In(not,expr);
	}

	private ValueExpr inItem() {
		ValueExpr item = parameter();
		if (item==null) item = literal(); // number, string, datetime, bool
		if (item==null) item = pathExpression(); // enum literal
		return item;
	}

	private Condition.Like likeExpression() {
		int index = lx.tokenIndex;
		ValueExpr left = valueExpression();
		String compare = keyword(lx.stringOf("compare.word"));
		if (compare==null || !compare.contains("like")) {
			lx.tokenIndex = index;
			return null;
		}
		boolean not = compare.contains("not");
		boolean paren = lx.check("(");
		String pattern = lx.stringOf("string");
		ValueExpr right = pattern!=null ?
				new ValueExpr.Literal(pattern) : parameter();
		if (right==null) ex("pattern expected");
		char escapeChar = '0';
		if (lx.check("escape"))
			escapeChar = lx.string().charAt(0);
		if (paren && !lx.check(")")) ex("')' expected");
		return new Condition.Like(not,left,right,escapeChar);
	}

	private Condition.Between betweenExpression() {
		int index = lx.tokenIndex;
		ValueExpr left = valueExpression();
		String compare = keyword(lx.stringOf("compare.word"));
		if (compare==null || !compare.contains("between")) {
			lx.tokenIndex = index;
			return null;
		}
		boolean not = compare.contains("not");
		ValueExpr min = valueExpression();
		if (!lx.check("and")) ex("'and' expected");
		ValueExpr max = valueExpression();
		return new Condition.Between(not,left,min,max);
	}

	private Condition.Compare comparisonExpression() {
		int index = lx.tokenIndex;
		ValueExpr left = valueExpression();
		String compare = lx.stringOf("compare.op");
		if (compare==null) {
			lx.tokenIndex = index;
			return null;
		}
		if (compare.equals("<>")) compare = "!=";
		boolean not = compare.contains("!");
		boolean equals = compare.contains("=");
		boolean less = compare.contains("<");
		boolean greater = compare.contains(">");
		ValueExpr right = allOrAnyExpression();
		if (right==null) right = valueExpression();
		if (right==null) ex("right hand expression expected");
		return new Condition.Compare(not,left,right,equals,less,greater);
	}

	private Condition.AllAny allOrAnyExpression() {
		String func = keyword(lx.string("(?i)all|any|some"));
		if (func==null) return null;
		if (!lx.check("(")) ex("'(' expected");
		Statement.Subquery subquery = subquery();
		if (!lx.check(")")) ex("')' expected");
		return new Condition.AllAny(func,subquery);
	}

	private ValueExpr selectExpression(boolean toplevel) {
		ValueExpr expr = null;
		if (toplevel) {
			if (lx.check("*")) return new PathExpr("*"); // JPA has no "*"
			if ((expr = constructExpression())!=null) return expr;
			if ((expr = caseExpression())!=null) return expr;
			if (lx.check("object")) {
				if (!lx.check("(")) ex("'(' expected");
				String value = lx.stringOf("word");
				if (value==null) ex("identifier expected");
				if (!lx.check(")")) ex("')' expected");
				return new PathExpr(value,null);
			}
		}
		return valueExpression(); // JPA is stricter, lines below
		//if ((expr = aggregateExpression())!=null) return expr;
		//if ((expr = pathExpression())!=null) return expr;
		//String value = lx.stringOf("word");
		//if (value==null) ex("identifier expected");
		//return new PathExpr(value,null);
	}

	private ValueExpr.Construct constructExpression() {
		if (!lx.check("new")) return null;
		PathExpr type = pathExpression();
		if (type==null) ex("object type expected");
		if (!lx.check("(")) ex("'(' expected");
		List<ValueExpr> args = new ArrayList();
		args.add(selectExpression(false));
		while (lx.check(","))
			args.add(selectExpression(false));
		if (!lx.check(")")) ex("')' expected");
		return new ValueExpr.Construct(type,args);
	}

	// supported:
	// CASE WHEN condition THEN result [WHEN condition THEN result ...] [ELSE result] END
	// TODO not supported:
	// CASE value WHEN compare_value THEN result [WHEN compare_value THEN result ...] [ELSE result] END
	private ValueExpr.Case caseExpression() {
		boolean paren = lx.check("(");
		if (!lx.check("case")) {
			if (paren)
				lx.tokenIndex--; // unsee '('
			return null;
		}
		List<ValueExpr> args = new ArrayList();
		args.add(whenThenExpression());
		while (!lx.check("end")) {
			ValueExpr expr = null;
			if (expr==null) expr = whenThenExpression();
			if (expr==null) expr = elseExpression();
			if (expr==null) ex("'when' or 'else' expected");
			args.add(expr);
			if (lx.check(",")) ex("'end' expected");
		}
		if (paren && !lx.check(")")) ex("')' expected");
		return new ValueExpr.Case(args);
	}

	private Condition.WhenThen whenThenExpression() {
		if (!lx.check("when")) return null;
		Condition.BoolExpr whenExpr = conditionExpression();
		if (!lx.check("then")) ex("'then' expected");
		ValueExpr thenExpr = valueExpression();
		return new Condition.WhenThen(whenExpr,thenExpr);
	}

	private Condition.Else elseExpression() {
		if (!lx.check("else")) return null;
		ValueExpr elseExpr = valueExpression();
		return new Condition.Else(elseExpr);
	}

	private ValueExpr valueExpression() {
		if (lx.check("(")) {
			Statement.Subquery subquery = subquery();
			if (subquery!=null) {
				if (!lx.check(")")) ex("')' expected");
				return subquery;
			} else lx.tokenIndex--; // unsee "("
		}
		return valueExpressionSimple();
	}

	private ValueExpr valueExpressionSimple() {
		ValueExpr term = valueTerm();
		String op = lx.string("\\+|-");
		return op==null ? term : new Operation(op,term,valueExpressionSimple());
	}

	private ValueExpr valueTerm() {
		ValueExpr factor = valueFactor();
		String op = lx.string("\\*|\\/");
		return op==null ? factor : new Operation(op,factor,valueTerm());
	}

	private ValueExpr valueFactor() {
		String op = lx.string("\\+|-");
		return op==null ? valuePrimary() : new Operation(op,null,valuePrimary());
	}

	private ValueExpr valuePrimary() {
		ValueExpr expr = null;
		if ((expr = functionExpression())!=null) return expr; // aggregate funcs too
		if ((expr = parameter())!=null) return expr;
		if (lx.check("(")) {
			ValueExpr c = valueExpressionSimple();
			if (!lx.check(")")) ex("')' expected");
			return c;
		}
		if ((expr = literal())!=null) return expr;
		if ((expr = pathExpression())!=null) return expr;
		return null;
	}

	private List<VarDecl.RangeDecl> variableDeclaration() {
		VarDecl.RangeDecl decl = null;
		List<VarDecl.RangeDecl> expr = new ArrayList();
		if ((decl = rangeVariableDeclaration())!=null)
			expr.add(decl);
		if (expr.isEmpty())
			return expr;
		while ((decl = joinDeclaration())!=null)
			expr.add(decl);
		return expr;
	}

	private VarDecl.RangeDecl joinDeclaration() {
		String join = keyword(lx.stringOf("keyword.join"));
		if (join==null) return null;
		boolean left = join.contains("left"); // left = outer, !left = inner
		boolean right = join.contains("right"); // JPA has no right join
		boolean full = join.contains("full"); // JPA has no full join
		if (full) left = right = true;
		boolean fetch = lx.check("fetch");
		PathExpr path = pathExpression();
		if (path==null) ex("path expected");
		//String alias = !fetch ? alias() : null;
		String alias = alias(); // JPA is stricter here
		Condition.BoolExpr on = lx.check("on") ? conditionExpression() : null; // JPA has no "on"
		return new VarDecl.JoinDecl(path,alias,left,right,fetch,on);
	}

	private VarDecl.RangeDecl rangeVariableDeclaration() {
		PathExpr range = pathExpression();
		if (range==null) ex("range expected");
		return new VarDecl.RangeDecl(range,alias());
	}

	private VarDecl.RangeDecl collectionMemberDeclaration() {
		if (!lx.check("in")) return null;
		if (!lx.check("(")) ex("'(' expected");
		PathExpr path = pathExpression();
		if (path==null) ex("path expected");
		if (!lx.check(")")) ex("')' expected");
		return new VarDecl.MemberDecl(path,alias());
	}

	private ValueExpr.Function functionExpression() {
		PathExpr func = pathExpression();
		if (func==null) return null;
		if (!lx.check("(")) {
			lx.tokenIndex--; // unsee func word
			return null;
		}
		boolean distinct = lx.check("distinct"); // for aggregate functions, "*" too
		List<ValueExpr> args = new ArrayList();
		ValueExpr arg = null;
		if (lx.check("*")) args.add(new PathExpr("*")); // JPA has no "*"
		if (args.isEmpty()) {
			if ((arg = valueExpression())!=null) args.add(arg);
			while (lx.check(","))
				if ((arg = valueExpression())!=null) args.add(arg);
		}
		if (!lx.check(")")) ex("')' expected");
		return new ValueExpr.Function(func,args,distinct);
	}

	private String alias() {
		lx.check("as");
		int index = lx.tokenIndex;
		String alias = lx.string();
		if (lx.checkType("string",-1)) return alias;
		if (lx.checkType("word",-1)) return alias;
		lx.tokenIndex = index;
		return null;
	}

	private PathExpr pathExpression() {
		int index = lx.tokenIndex;
		String value = lx.string();
		if (lx.checkType("path",-1)) return new PathExpr(value.split("\\."));
		if (lx.checkType("word",-1)) return new PathExpr(value);
		lx.tokenIndex = index;
		return null;
	}

	private ValueExpr.Parameter parameter() {
		String name = lx.stringOf("parameter");
		return name!=null ? new ValueExpr.Parameter(name) : null;
	}

	private ValueExpr.Literal literal() {
		String value = lx.stringOf("literal");
		return value!=null ? new ValueExpr.Literal(value) : null;
	}

	private String keyword(String keyword) {
		return keyword!=null ? keyword.toLowerCase() : null;
	}

	public static Map<String,String> statementPatterns() {
		Map<String,String> ptn = new LinkedHashMap();
		String ptnIdent = "[a-zA-Z$_][a-zA-Z$_0-9]*";
		String ptnMod = "(?i)";
		ptn.put("keyword.join",ptnMod+"(X)\\b".replace("X",
				"(?:left\\s+|left\\s+outer\\s+|inner\\s+" +
				"|right\\s+|right\\s+outer\\s+|full\\s+" + // JPA has no right/full join
				")?join|fetch"));
		ptn.put("keyword.group",ptnMod+"(X)\\b".replace("X",
				"group\\s+by|having"));
		ptn.put("keyword.order",ptnMod+"(X)\\b".replace("X",
				"order\\s+by|descending|ascending|desc|asc|" +
				"nulls\\s+first|nulls\\s+last"));
		ptn.put("keyword.type",ptnMod+"(X)\\b".replace("X",
				"select|update|insert|delete"));
		ptn.put("keyword",ptnMod+"(X)\\b".replace("X",
//				"distinct|from|as|on|where|new|object|escape|set"));
				"distinct|from|as|on|where|new|escape|set"));
		ptn.put("literal.string.double", "\"(.*?(?<!\\\\)(?:\"\".*?(?<!\\\\))*)\"");
		ptn.put("literal.string.single",  "'(.*?(?<!\\\\)(?:''.*?(?<!\\\\))*)'");
		ptn.put("literal.string.backtick","`(.*?(?<!\\\\)(?:``.*?(?<!\\\\))*)`");
		ptn.put("compare.word",ptnMod+"(X)\\b".replace("X",
				("NOT?between|NOT?like|is\\s+NOT?null|NOT?in|is\\s+NOT?empty" +
				"|NOT?member(?:\\s+of)?|NOT?exists").replace("NOT?","(?:not\\s+)?")));
		ptn.put("literal.null",ptnMod+"(null)\\b");
		ptn.put("literal.bool",ptnMod+"(true|false)\\b");
		ptn.put("literal.dec","(\\d*\\.\\d+|\\d+(?=[eEfFdD]))([eE][+-]?\\d+)?[fFdD]?");
		ptn.put("literal.hex","0x[0-9A-Fa-f]+[lL]?");
		ptn.put("literal.oct","0[0-7]+[lL]?");
		ptn.put("literal.int","\\d+[lL]?");
		ptn.put("logical.op",ptnMod+"(not|and|or)\\s+|&&|\\|\\|");
		ptn.put("compare.op","<=|>=|!=|==?|<>|<|>");
		ptn.put("arithmetic.op","\\+|-|\\*|\\/");
		ptn.put("parenthesis","\\(|\\)");
		ptn.put("item.delimiter",",");
		ptn.put("parameter","[?:]"+ptnIdent+"|\\?\\d*");
		ptn.put("path.expr",ptnIdent+"(\\.\\*|(\\."+ptnIdent+")+)");
		ptn.put("word","\\w+|(?i)(?<=escape\\s?)'[^']+'");
		return ptn;
	}

	public static class Lexer {
		public final String input;
		public Token[] tokens;
		public int tokenIndex;

		public static class Token {
			public Set<String> type = new LinkedHashSet();
			public String t; public int s,e;
			public Token(String t) { this.t = t; }
			Token(String t, int s, int e) { this.t = t; this.s = s; this.e = e; }
			@Override public String toString() { return t; }
		}

		public Lexer(String input, Map<String,String> patterns) {
			this.input = input;
			List<Token> tokens = new ArrayList();
			Map<String,Pattern> compiled = new LinkedHashMap();
			for (Map.Entry<String,String> p : patterns.entrySet())
				compiled.put(p.getKey(),Pattern.compile(p.getValue()));
			Matcher m = Pattern.compile("").matcher(input+" ");
			m.useTransparentBounds(true).useAnchoringBounds(false);
			for (int n=m.regionEnd(), i=0; i<n; i++) {
				m.region(i,n);
				for (Map.Entry<String,Pattern> p : compiled.entrySet()) {
					if (m.usePattern(p.getValue()).lookingAt()) {
						int e = m.end();
						if (e==0) continue;
						//System.out.format("%30s %s\n",p.getKey(),m.group());
						Token t = new Token(m.group().trim(),m.start(),e-1);
						t.type.add(p.getKey());
						t.type.addAll(Arrays.asList(p.getKey().split("\\.")));
						tokens.add(t);
						i = e-1;
						break;
					}
				}
			}
			this.tokens = tokens.toArray(new Token[tokens.size()]);
		}

		public void init(Map<String,String> ptn) {}

		public boolean checkType(String type, int off) {
			if (tokenIndex+off<0 || tokenIndex+off>=tokens.length)
				return false;
			return tokens[tokenIndex+off].type.contains(type);
		}

		public boolean check(String s) {
			if (tokenIndex>=tokens.length)
				return false;
			if (tokens[tokenIndex++].t.equalsIgnoreCase(s))
				return true;
			tokenIndex--;
			return false;
		}

		public String stringOf(String type) {
			tokenIndex++;
			if (checkType(type,-1))
				return tokens[tokenIndex-1].t;
			tokenIndex--;
			return null;
		}

		public String string(String re) {
			if (tokenIndex>=tokens.length)
				return null;
			if (tokens[tokenIndex].t.matches(re))
				return tokens[tokenIndex++].t;
			return null;
		}

		public String string() {
			if (tokenIndex>=tokens.length)
				return null;
			return tokens[tokenIndex++].t;
		}
	}

	public interface Part {}

	public static class Statement implements Part {
		public final Map<String,Object> meta = new HashMap();

		static class Select extends Statement {
			public final boolean distinct;
			public final Map<ValueExpr,String> expr;
			public final List<VarDecl> from;
			public final Condition.BoolExpr where;
			public final List<ValueExpr> group;
			public final Condition.BoolExpr having;
			public final Map<ValueExpr,String> order;
			public final int limit;
			public final int offset;
			public boolean toplevel;

			public Select(boolean distinct, Map<ValueExpr,String> expr,
					List<VarDecl> from, Condition.BoolExpr where,
					List<ValueExpr> group, Condition.BoolExpr having,
					Map<ValueExpr,String> order, int limit, int offset) {
				this.distinct = distinct;
				this.expr = expr;
				this.from = from;
				this.where = where;
				this.group = group;
				this.having = having;
				this.order = order;
				this.limit = limit;
				this.offset = offset;
				this.toplevel = true;
			}

			@Override
			public String toString() {
				return "select"+(distinct ? " distinct " : " ")+expr+" from "+from+
						(where!=null ? " where "+where : "")+
						(group!=null ? " group-by "+group : "")+
						(having!=null ? " "+having : "")+
						(order!=null ? " order-by "+order : "");
			}
		}

		static class Subquery extends Select implements ValueExpr {
			public Subquery(boolean distinct, Map<ValueExpr,String> expr,
					List<VarDecl> from, Condition.BoolExpr where,
					List<ValueExpr> group, Condition.BoolExpr having) {
				super(distinct,expr,from,where,group,having,null,-1,-1);
				this.toplevel = false;
			}
		}

		static class Delete extends Statement {
			public final VarDecl from;
			public final Condition.BoolExpr where;

			public Delete(VarDecl from, Condition.BoolExpr where) {
				this.from = from;
				this.where = where;
			}

			@Override
			public String toString() {
				return "delete from "+from+
						(where!=null ? " where "+where : "");
			}
		}

		static class Update extends Statement {
			public final VarDecl range;
			public final Condition.BoolExpr where;
			public final Map<PathExpr,ValueExpr> values;

			public Update(VarDecl range, Condition.BoolExpr where,
					Map<PathExpr,ValueExpr> values) {
				this.range = range;
				this.where = where;
				this.values = values;
			}

			@Override
			public String toString() {
				return "update "+range+" set "+values+" "+
						(where!=null ? " where "+where : "");
			}
		}
	}

	public interface VarDecl extends Part {
		class RangeDecl implements VarDecl {
			public PathExpr path;
			public String alias;

			public RangeDecl(PathExpr path, String alias) {
				this.path = path;
				this.alias = alias;
			}

			@Override
			public String toString() {
				return path+(alias!=null ? " as "+alias : "");
			}
		}

		class MemberDecl extends RangeDecl {
			public MemberDecl(PathExpr path, String alias) {
				super(path,alias);
			}

			@Override
			public String toString() {
				return "in("+path+")"+(alias!=null ? " as "+alias : "");
			}
		}

		class JoinDecl extends RangeDecl {
			public final boolean inner;
			public final boolean outer;
			public final boolean left;
			public final boolean right;
			public final boolean full;
			public final boolean fetch;
			public final Condition.BoolExpr on;

			public JoinDecl(PathExpr path, String alias, boolean left, boolean right,
					boolean fetch, Condition.BoolExpr on) {
				super(path,alias);
				this.inner = !left && !right;
				this.outer = !inner;
				this.full = left && right;
				this.left = !full && left;
				this.right = !full && right;
				this.fetch = fetch;
				this.on = on;
			}

			@Override
			public String toString() {
				return ParserUtil.joinspec(this)+" join "+
						(fetch ? "fetch " : "") + path +
						(alias!=null ? " as "+alias : "") +
						(on!=null ? " on "+on : "");
			}
		}
	}

	public interface ValueExpr extends Part {
		class PathExpr implements ValueExpr {
			public String start;
			public LinkedList<String> access;

			public PathExpr(String... access) {
				LinkedList<String> steps = new LinkedList(Arrays.asList(access));
				this.start = steps.removeFirst();
				this.access = steps!=null & steps.size()>0 ? steps : null;
			}

			@Override
			public String toString() {
				return start+"."+(access!=null ? access : "");
			}
		}

		class Parameter implements ValueExpr {
			public final String name;
			public Parameter(String name) { this.name = name; }
			@Override
			public String toString() { return name; } // starts with [?:]
		}

		class Literal implements ValueExpr {
			public final String value;
			public Literal(String value) { this.value = value; }
			@Override
			public String toString() { return "#"+value; }
		}

		class Function implements ValueExpr {
			public final PathExpr func;
			public final List<ValueExpr> args;
			public final boolean distinct; // for aggregate functions

			public Function(PathExpr func, List<ValueExpr> args, boolean distinct) {
				this.func = func;
				this.args = args;
				this.distinct = distinct;
			}
			@Override
			public String toString() { return ""+func+args; }
		}

		class Case extends Function {
			public Case(List<ValueExpr> args) {
				super(new PathExpr("case"),args,false);
			}
		}

		class Construct extends Function {
			public Construct(PathExpr type, List<ValueExpr> args) {
				super(type,args,false);
			}
			@Override
			public String toString() { return "new "+func+args; }
		}

		class Operation implements ValueExpr {
			public final String op;
			public final ValueExpr left;
			public final ValueExpr right;

			public Operation(String op, ValueExpr left, ValueExpr right) {
				this.op = op;
				this.left = left;
				this.right = right;
			}
			@Override
			public String toString() { return "("+left+" "+op+" "+right+")"; }
		}

		interface Condition {
			class BoolExpr implements ValueExpr {
				public boolean not;
				public String op = getClass().getSimpleName().toLowerCase();
				public List<? extends ValueExpr> expr;

				public BoolExpr(boolean not, Object... expr) {
					this.not = not;
					this.expr = (List)(expr[0] instanceof List ? expr[0] : Arrays.asList(expr));
				}

				@Override
				public String toString() {
					return (not ? "Not-" : "")+getClass().getSimpleName()+expr;
				}
			}
			class And extends BoolExpr {
				public And(List<? extends BoolExpr> expr) { super(false,expr); }
			}
			class Or extends BoolExpr {
				public Or(List<? extends BoolExpr> expr) { super(false,expr); }
			}
			class Not extends BoolExpr {
				public Not(BoolExpr expr) { super(true,expr); }
			}
			class Exists extends BoolExpr {
				public Exists(boolean not, Statement.Subquery subquery) {
					super(not,subquery);
				}
			}
			class Member extends BoolExpr {
				public Member(boolean not, ValueExpr member, ValueExpr collection) {
					super(not,member,collection);
				}
			}
			class Empty extends BoolExpr {
				public Empty(boolean not, ValueExpr collection) { super(not,collection); }
			}
			class Null extends BoolExpr {
				public Null(boolean not, ValueExpr value) { super(not,value); }
			}
			class In extends BoolExpr {
				public In(boolean not, List<ValueExpr> values) { super(not,values); }
			}
			class Like extends BoolExpr {
				public Like(boolean not, ValueExpr value,
						ValueExpr pattern, char escapeChar) { super(not,value,pattern); }
			}
			class Between extends BoolExpr {
				public Between(boolean not, ValueExpr value,
						ValueExpr min, ValueExpr max) { super(not,value,min,max); }
			}
			class Compare extends BoolExpr {
				public Compare(boolean not, ValueExpr left, ValueExpr right,
						boolean equals, boolean less, boolean greater) {
					super(not,left,right);
					this.op = (not?"!":"")+(less?"<":"")+(greater?">":"")+(equals?"=":"");
				}
			}
			class AllAny extends BoolExpr {
				public AllAny(String func, Statement.Subquery subquery) {
					super(false,subquery);
					this.op = func;
				}
			}
			class WhenThen extends BoolExpr {
				public WhenThen(BoolExpr when, ValueExpr then) {
					super(false,when,then);
				}
			}
			class Else extends BoolExpr {
				public Else(ValueExpr elseExpr) {
					super(false,elseExpr);
				}
			}
		}
	}
}
