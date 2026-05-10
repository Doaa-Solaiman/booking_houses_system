package de.scheller.platform.persist;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import de.scheller.platform.persist.DatabaseOrm.OrmInfo;
import de.scheller.platform.persist.Parser.Statement;
import de.scheller.platform.persist.Parser.ValueExpr;
import de.scheller.platform.persist.Parser.ValueExpr.Condition;
import de.scheller.platform.persist.Parser.ValueExpr.Condition.BoolExpr;
import de.scheller.platform.persist.Parser.ValueExpr.PathExpr;
import de.scheller.platform.persist.Parser.VarDecl;

/**
 * @author kandzia
 */
public class QueryAnalyze extends ParserResultVisitorBase
{
	LinkedList<Parser.Part> stack = new LinkedList();
	DatabaseOrm dbo;
	Map<String,OrmInfo> declared = new LinkedHashMap();
//	Map<String,Map<String,String>> inheritJoins = new LinkedHashMap();

	Map<String,List<VarDecl.JoinDecl>> givenJoins = new LinkedHashMap();
	Map<String,List<VarDecl.JoinDecl>> inheritJoins = new LinkedHashMap();
	Map<String,List<VarDecl.JoinDecl>> accessJoins = new LinkedHashMap();

	public QueryAnalyze(DatabaseOrm dbo) {
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

//		System.out.println();
//		for (String alias : declared.keySet()) {
//			OrmInfo info = declared.get(alias);
//			System.out.println(alias+" "+info.tables);
//		}
		analyzePaths();
	}

	public List<RangeInfo> getRanges() {
		return new ArrayList(ranges.values());
	}

	@Override
	protected void visit(VarDecl.RangeDecl d) {
		path(d.path,d.alias);
//		declare(d.path,d.alias);
	}

	@Override
	protected void visit(VarDecl.MemberDecl d) {
		visit(d.path);
	}

	@Override
	protected void visit(VarDecl.JoinDecl d) {
		path(d.path,d.alias);
//		String typename = ParserUtil.path(d.path);
//		Class type = dbo.types.get(typename);
//		if (type!=null) {
//			declare(d.path,d.alias);
//			if (d.on!=null)
//				visit(d.on);
//		} else {
//			join(d.path,d.alias);
//		}
	}

	@Override
	protected void visit(BoolExpr c) {
		for (ValueExpr e : c.expr)
			visit(e);
	}

	@Override
	protected void visit(PathExpr e) {
		path(e,null);
//		join(e,null);
	}

	private void declare(PathExpr path, String alias) {
		String typename = ParserUtil.path(path);
		Class type = dbo.types.get(typename);
		if (type==null)
			throw new RuntimeException(typename+" is not a model type");
		OrmInfo info = dbo.info(type);
//		DatabaseOrm.dump(info);
		String table = info.tables.get(0);
		if (alias==null)
			alias = "t"+declared.size();
		declared.put(alias,info);
	}

	private void path(PathExpr path, String alias) {
//		System.out.println("QueryAnalyze.path() "+path+" -> "+alias);
		String typename = ParserUtil.path(path);
		Class type = dbo.types.get(typename);
		if (alias==null)
			alias = path.start;
		RangeInfo r = ranges.get(alias);
		if (r==null) { // declare range via FROM or (LEFT or INNER) JOIN
			ranges.put(alias,r = new RangeInfo());
			r.alias = alias;
			r.from = type!=null ? typename : null;
			r.fromPath = type==null ? path : null;
		} else { // just collect that path as this is just range usage
			r.paths.put(path,null);
		}
	}

	private void analyzePaths() {
		for (String alias : ranges.keySet()) {
			RangeInfo r = ranges.get(alias);
//			for (PathExpr path : new ArrayList<PathExpr>(r.paths.keySet())) {
////				System.out.println(alias+" "+path);
//				String typename = ranges.get(path.start).from;
//				Class type = dbo.types.get(typename);
//				// TODO build access path w/ targetTable and value types
////				dbo.infos.get(type).targetTable.get(path.access.get(0));
//				if (type!=null) {
//					OrmInfo info = dbo.info(typename);
//					String firstStep = path.access.get(0);
//					String table = info.columnTable.get(firstStep);
//					PathExpr pathFromType = new PathExpr(table);
//					pathFromType.access = path.access;
//					r.paths.put(path,pathFromType);
//				} else { // assume path starts from alias
//					PathExpr fromPath = ranges.get(path.start).fromPath;
//					RangeInfo fromRange = ranges.get(fromPath.start);
//					OrmInfo info = dbo.info(fromRange.from);
//					String firstStep = fromPath.access.get(0);
//					String table = info.columnTable.get(firstStep);
//					PathExpr pathFromType = new PathExpr(table);
//					pathFromType.access = new ArrayList();
//					pathFromType.access.addAll(fromPath.access);
//					pathFromType.access.addAll(path.access);
//					fromRange.paths.put(path,pathFromType);
//					// TODO maybe r.paths.put(path,...); too
//				}
//			}

			PathExpr p = r.fromPath;
			if (r.from==null && p!=null) {
				String typename = ranges.get(p.start).from;
				if (typename==null) continue;
				OrmInfo info = dbo.info(typename);
				for (int i=0; p.access!=null && i<p.access.size(); i++) {
					String step = p.access.get(i);
					String column = column(info,step);
					String table = info.targetTable.get(column);
					info = dbo.info(table);
				}
				r.from = info.tables.get(0);
			}
		}
	}

	private void join(PathExpr path, String finalAlias) {
		String alias = path.start;
		OrmInfo info = declared.get(alias);
		if (path.access!=null) {
			String table = info.tables.get(0);
			List<String> access = new LinkedList();
			for (int i=0; i<path.access.size(); i++) {
				String step = path.access.get(i);
				String column = column(info,step);
				if (column==null)
					throw new RuntimeException("'"+step+"' not found in path "+path);
				String joinTable = info.targetTable.get(column);

				if (i<path.access.size()-1 || finalAlias!=null) {
					String fromAlias = alias;
					String fromTable = table;
//					if (finalAlias!=null && finalAlias.length()>0)
					{
						// adjust table to join from (maybe a different one, eg basetype)
						table = info.columnTable.get(column);
						info = dbo.info(table);
						alias = alias+"_"+info.prefix;
						if (!table.equals(fromTable)) {
							declare(new PathExpr(table),alias);
							System.out.println(join(table,alias,fromTable,fromAlias));
						}
					}
					OrmInfo joinInfo = dbo.info(joinTable);
					String joinAlias = alias+"_"+joinInfo.prefix;
					if (finalAlias!=null && finalAlias.length()>0 &&
							i==path.access.size()-1)
						joinAlias = finalAlias;
					if (!declared.containsKey(joinAlias))
						declare(new PathExpr(joinTable),joinAlias);
					System.out.println(join(joinTable,joinAlias,table,alias));
//					if (i==path.access.size()-1)
//						declared.put(joinAlias,joinInfo);
					table = joinTable;
					info = joinInfo;
				} else {
					access.add(joinTable!=null ? column+"_id" : column);
				}
			}
		}
	}

	private final Map<String,RangeInfo> ranges = new LinkedHashMap();

	public static class RangeInfo {
		String alias;
		String from;
		PathExpr fromPath;
		Map<PathExpr,PathExpr> paths = new LinkedHashMap();
	}

	private VarDecl.JoinDecl join(
			String table1, String alias1, String table0, String alias0) {
		OrmInfo info1 = dbo.info(table1);
		OrmInfo info0 = dbo.info(table0);
		PathExpr jp = new PathExpr(table1);
		BoolExpr on = new Condition.Compare(false,
				new PathExpr(alias1,info1.pkColumn),
				new PathExpr(alias0,info0.pkColumn),
				true,false,false);
		return new VarDecl.JoinDecl(jp,alias1,true,false,false,on);
	}

	private String column(OrmInfo info, String step) {
		String column = info.propColumn.get(step);
		if (column==null)
			column = info.propColumn.get(step.replaceFirst("_id$",""));
		return column;
	}
}
