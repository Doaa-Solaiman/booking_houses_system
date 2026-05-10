package de.scheller.platform.persist;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import de.scheller.platform.persist.util.JdbcHelper;

/**
 * @author kandzia
 */
public class DatabaseMeta
{
	String name;
	Map<String,DatabaseMeta.TableMeta> tables = new LinkedHashMap();

	public static class TableMeta {
		String name;
		String pk;
		Set<String> pks = new LinkedHashSet();
		Map<String,DatabaseMeta.ColumnMeta> columns = new LinkedHashMap();
		Map<String,DatabaseMeta.IndexMeta> indexes = new LinkedHashMap();
		Map<String,DatabaseMeta.FKMeta> foreign = new LinkedHashMap();
	}

	public static class ColumnMeta {
		String name;
		Class type;
		boolean nullable;
	}

	public static class IndexMeta {
		String name;
		String column;
		Set<String> columns = new LinkedHashSet();
		boolean primary;
		boolean unique;
	}

	public static class FKMeta {
		String name;
		String table;
		String pk;
	}

	public DatabaseMeta(Connection c, String name) throws SQLException {
		this.name = name;
		DatabaseMetaData dbmd = c.getMetaData();
		ResultSet rst = dbmd.getTables(name,null,"%",null);
		while (rst.next()) {
			String cat = rst.getString("TABLE_CAT");
			String table = rst.getString("TABLE_NAME");
			DatabaseMeta.TableMeta tm = new TableMeta();
			tables.put(table,tm);
			tm.name = table;

			ResultSet rsc = dbmd.getColumns(cat,null,table,"%");
			while (rsc.next()) {
				name = rsc.getString("COLUMN_NAME");
				int typ = rsc.getInt("DATA_TYPE");
				DatabaseMeta.ColumnMeta m = new ColumnMeta();
				tm.columns.put(name,m);
				m.name = name;
				m.type = JdbcHelper.toJavaType(typ);
				m.nullable = rsc.getInt("NULLABLE")>0;
			}
			rsc.close();
			ResultSet rsi = dbmd.getIndexInfo(cat,null,table,false,true);
			while (rsi.next()) {
				name = rsi.getString("INDEX_NAME");
				String col = rsi.getString("COLUMN_NAME");
				DatabaseMeta.IndexMeta m = tm.indexes.get(name);
				if (m==null) tm.indexes.put(name,m = new IndexMeta());
				m.name = name;
				m.columns.add(col);
				if (m.column==null)
					m.column = col;
				m.primary = name.toUpperCase().equals("PRIMARY");
				if (m.primary)
					tm.pks.addAll(m.columns);
				m.unique = !rsi.getBoolean("NON_UNIQUE");
			}
			rsi.close();
			ResultSet rsr = dbmd.getImportedKeys(cat,null,table);
			while (rsr.next()) {
				name = rsr.getString("FKCOLUMN_NAME");
				DatabaseMeta.FKMeta m = new FKMeta();
				tm.foreign.put(name,m);
				m.name = name;
				m.table = rsr.getString("PKTABLE_NAME");
				m.pk = rsr.getString("PKCOLUMN_NAME");
			}
			rsr.close();
			if (tm.pks.size()==1)
				tm.pk = tm.pks.iterator().next();
		}
		rst.close();
		c.close();
	}
}
