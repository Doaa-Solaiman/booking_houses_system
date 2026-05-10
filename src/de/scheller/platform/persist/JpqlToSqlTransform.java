package de.scheller.platform.persist;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;

import de.scheller.platform.common.MapUtils;
import de.scheller.platform.persist.DatabaseOrm.OrmInfo;
import de.scheller.platform.persist.Parser.Statement;
import de.scheller.platform.persist.Parser.ValueExpr;
import de.scheller.platform.persist.Parser.ValueExpr.Condition;
import de.scheller.platform.persist.Parser.VarDecl;

/**
 * @author kandzia
 */
public class JpqlToSqlTransform extends ParserResultVisitorBase
{
	LinkedList<Parser.Part> stack = new LinkedList();
	DatabaseOrm dbo;
	Map<String,OrmInfo> declared = new LinkedHashMap();
	Map<String,Map<String,String>> inheritJoins = new LinkedHashMap();

	public JpqlToSqlTransform(DatabaseOrm dbo) {
		this.dbo = dbo;
	}

	@Override
	protected void visit(Statement.Select s) {
		stack.push(s);
		if (s.from!=null && s.from.size()>0)
			for (VarDecl d : new ArrayList<VarDecl>(s.from))
				visit(d);
		if (s.expr!=null && s.expr.size()>0)
			for (Map.Entry<ValueExpr,String> e : s.expr.entrySet())
				visit(e.getKey());
		if (s.where!=null)
			visit(s.where);
		stack.pop();
//		System.out.println();
//		for (String alias : declared.keySet()) {
//			OrmInfo info = declared.get(alias);
//			System.out.println(alias);
//			DatabaseOrm.dump(info);
//		}
	}

	@Override
	protected void visit(VarDecl.RangeDecl d) {
		String typename = ParserUtil.path(d.path);
		Class type = dbo.types.get(typename);
		OrmInfo info = dbo.info(type);

		String table = info.tables.get(0);
		String alias = d.alias!=null ? d.alias : "t"+declared.size();
		declared.put(alias,info);
		d.alias = alias;
		d.path = new ValueExpr.PathExpr(info.tables.get(0));
		MapUtils.groupT(inheritJoins,HashMap.class,alias,table,alias);

		Statement.Select s = (Statement.Select)stack.peek();
		for (int i=1; i<info.tables.size(); i++) {
			String jtable = info.tables.get(i);
			boolean negate = jtable.startsWith("!");
			if (negate) jtable = jtable.substring(1);
			Class jtype = dbo.types.get(jtable); // TODO path is tablename, not really classname!
			OrmInfo jinfo = dbo.info(jtype);
			String jalias = alias+"_"+jinfo.prefix;
			if (declared.containsKey(jalias))
				jalias = alias+declared.size();
			declared.put(jalias,jinfo);
			MapUtils.groupT(inheritJoins,HashMap.class,alias,jtable,jalias);
			ValueExpr.PathExpr path = new ValueExpr.PathExpr(jtable);
			Condition.BoolExpr on = new Condition.Compare(false,
					new ValueExpr.PathExpr(jalias,jinfo.pkColumn),
					new ValueExpr.PathExpr(alias,info.pkColumn),
					true,false,false);
			s.from.add(new VarDecl.JoinDecl(path,jalias,negate,false,false,on));
		}
	}

	@Override
	protected void visit(VarDecl.MemberDecl d) {
	}

	@Override
	protected void visit(VarDecl.JoinDecl d) {
		String typename = ParserUtil.path(d.path);
		Class type = dbo.types.get(typename);
		OrmInfo info = dbo.info(type);

		String alias = d.alias!=null ? d.alias : "t"+declared.size();
		declared.put(alias,info);
		d.alias = alias;

		if (d.on!=null)
			visit(d.on);
	}

	@Override
	protected void visit(Condition.BoolExpr c) {
		for (ValueExpr e : c.expr)
			visit(e);
	}

	@Override
	protected void visit(ValueExpr.PathExpr e) {
//		System.out.println(".maybeTableColumn "+e);
		String root = e.start;
		LinkedList<String> access = e.access;
		if (root.equals("*")) {
			if (declared.size()>0)
				root = declared.keySet().iterator().next();
		}
		if (!declared.containsKey(root)) {
			Map<String,LinkedList<String>> candidates = new LinkedHashMap();
			for (Map.Entry<String,OrmInfo> i : declared.entrySet()) {
				Map<String,String> propColumn = i.getValue().propColumn;
				Map<String,String> columnTable = i.getValue().columnTable;
				String column = propColumn.get(root);
				if (column==null)
					column = propColumn.get(root.replaceFirst("_id$",""));
				if (column!=null) {
					String table = columnTable.get(column);
					access = new LinkedList();
					Map<String,String> inheritJoin = inheritJoins.get(e.start);
					access.add(inheritJoin!=null ? inheritJoin.get(table) : e.start);
					if (e.access!=null) access.addAll(e.access);
					if (i.getValue().root)
						candidates.put(i.getKey(),access);
				}
			}
			if (candidates.size()==1) {
				root = candidates.keySet().iterator().next();
				access = candidates.get(root);
			}
			if (!declared.containsKey(root))
				System.out.println("ERROR path has no declared root "+e);
		} else if (e.access!=null) {
			String alias = e.start;
			OrmInfo info = declared.get(alias);
			String column = column(info,e.access.get(0));
			String table = info.columnTable.get(column);
			Map<String,String> inheritJoin = inheritJoins.get(alias);
			root = inheritJoin!=null ? inheritJoin.get(table) : alias;
			access = new LinkedList();
			Statement.Select s = (Statement.Select)stack.peek();
			for (int i=0; i<e.access.size(); i++) {
				String step = e.access.get(i);
				column = column(info,step);
				if (column==null)
					throw new RuntimeException("'"+step+"' not found in path "+e);
				String jtable = info.targetTable.get(column);

				if (i<e.access.size()-1) {
					table = info.columnTable.get(column);
					info = dbo.info(table);
					alias = alias+"_"+info.prefix;
					OrmInfo jinfo = dbo.info(jtable);
					String jalias = alias+"_"+jinfo.prefix;
					if (declared.containsKey(jalias))
						jalias = jalias+declared.size();
					ValueExpr.PathExpr path = new ValueExpr.PathExpr(jtable);
					Condition.BoolExpr on = new Condition.Compare(false,
							new ValueExpr.PathExpr(jalias,jinfo.pkColumn),
							new ValueExpr.PathExpr(alias,info.pkColumn),
							true,false,false);
					s.from.add(new VarDecl.JoinDecl(path,jalias,true,false,false,on));
					root = jalias;
				} else {
					access.add(jtable!=null ? column+"_id" : column);
				}
				info = dbo.info(info.targetTable.get(column));
			}
		}
		e.start = root;
		e.access = access;
	}

	private String column(OrmInfo info, String step) {
		String column = info.propColumn.get(step);
		if (column==null)
			column = info.propColumn.get(step.replaceFirst("_id$",""));
		return column;
	}
}
