package de.scheller.fewo.util;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import de.scheller.fewo.FewoServer;
import de.scheller.fewo.FewoSession.TableInfo;

/**
 * created 2024-11-15 in fewo-buchung while teaching doaa DB/SQL/JDBC and how to make things easier
 *
 * @author kandzia
 */
public class DbUtils
{
	public static boolean readonly = false; // e.g. for debugging/testing

	// to loads all records from the specified table.
	public static List<Map> loadAll(TableInfo table) throws Exception {
		return loadMany(table,null);
	}

	// To load multiple records from the specified table based on the provided WHERE caluse.
	// where: The WHERE clause to filter results (can be null).
	// values: The values to set in the prepared statement for the WHERE clause.
	// Returns a list of maps representing the rows returned by the query.

	public static List<Map> loadMany(TableInfo table, String where, String... values)
	throws Exception {
		String sql = "SELECT * FROM "+table.name;
		if (where!=null)
			sql += " WHERE ("+where+")";
		return loadManySql(table,sql,values);
	}

	public static List<Map> loadManySql(TableInfo table, String sql, String... values)
	throws Exception {
		try (Connection conn = FewoServer.pds.getConnection()) {
			PreparedStatement ps = conn.prepareStatement(sql);
			int i = 1;
			for (String value : values)
				ps.setString(i++,value);
			List<Map> rows = new ArrayList();
			try (ResultSet rs = ps.executeQuery()) {
				rs.beforeFirst();
				while (rs.next())
					rows.add(fromResultSetToMap(rs,table));
			}
			return rows;
		}
	}

	// to loads one record from the specified table.
	public static Map loadOne(TableInfo table, String id) throws Exception {
		return loadOne(table,id,null);
	}

	public static Map loadOne(TableInfo table, String id, String where, String... values) throws Exception {
		try (Connection conn = FewoServer.pds.getConnection()) {
			List<Map> rows = new ArrayList();
			String sql = "SELECT * FROM "+table.name+" WHERE id = ?";
			if (where!=null)
				sql += " AND ("+where+")";
			PreparedStatement ps = conn.prepareStatement(sql);
			ps.setString(1,id);
			int i = 2;
			for (String value : values)
				ps.setString(i++,value);
			try (ResultSet rs = ps.executeQuery()) {
				rs.beforeFirst();
				if (rs.next())
					return fromResultSetToMap(rs,table);
			}
			return null;
		}
	}

	private static Map fromResultSetToMap(ResultSet rs, TableInfo table) throws SQLException {
		Map<String,Object> row = new LinkedHashMap();
		for (Map.Entry<String,String> e : table.columns.entrySet()) {
			String field = e.getKey();
			String fieldType = e.getValue();
			if ("string".equals(fieldType))
				row.put(field,rs.getString(field));
			else if ("double".equals(fieldType))
				row.put(field,rs.getDouble(field));
			else if ("integer".equals(fieldType))
				row.put(field,rs.getInt(field));
			else if ("boolean".equals(fieldType))
				row.put(field,rs.getBoolean(field));
			else if ("date".equals(fieldType))
				row.put(field,rs.getDate(field));
			else if ("timestamp".equals(fieldType))
				row.put(field,rs.getTimestamp(field));
		}
		return row;
	}

	private static int fromMapToStatement(Map row, PreparedStatement ps, TableInfo table)
	throws SQLException {
		int index = 1;
		for (Map.Entry<String,String> e : table.columns.entrySet()) {
			String field = e.getKey();
			if ("id".equals(field))
				continue;
			String fieldType = e.getValue();
			if ("string".equals(fieldType)) {
				ps.setString(index++,Utils.asString(row.get(field)));
			} else if ("double".equals(fieldType)) {
				if (row.get(field)==null)
					ps.setNull(index++,java.sql.Types.DOUBLE);
				else ps.setDouble(index++,Utils.asDouble(row.get(field)));
			} else if ("integer".equals(fieldType)) {
				if (row.get(field)==null)
					ps.setNull(index++,java.sql.Types.INTEGER);
				else ps.setInt(index++,Utils.asInteger(row.get(field)));
			} else if ("boolean".equals(fieldType)) {
				if (row.get(field)==null)
					ps.setNull(index++,java.sql.Types.BOOLEAN);
				else ps.setBoolean(index++,Utils.asBoolean(row.get(field)));
			} else if ("date".equals(fieldType)) {
				ps.setDate(index++,Utils.asSqlDate(row.get(field)));
			} else if ("timestamp".equals(fieldType)) {
				Object t = row.get(field);
				if (t instanceof Date || t instanceof Number) { // normal
					ps.setTimestamp(index++,Utils.asSqlTimestamp(t));
				} else { // that old hack where we don't remember where this was used
					String fromIsoUtc = (row.get(field)+"").replace("Z","").replace("T"," "); // hacky
					ps.setTimestamp(index++,Utils.asSqlTimestamp(fromIsoUtc));
				}
			}
		}
		return index;
	}

	public static String save(TableInfo table, Map item) throws Exception {
		Set<String> columns = new LinkedHashSet(table.columns.keySet());
		table.columns.remove("children");
		columns.remove("id"); // column names without "id"
		try (Connection conn = FewoServer.pds.getConnection()) {
			String sql;
			boolean exists = loadOne(table,(String)item.get("id"))!=null;
			if (exists) {
//				sql = "UPDATE Site SET ";
//				sql += "name=?, address=?, city=?, state=?, country=?, ";
//				sql += "phoneNumber=?, email=? WHERE id=?";

				sql = "UPDATE "+table.name+" SET ";
				for (String c : columns)
					sql += "`"+c+"`=?, ";
				sql = sql.substring(0,sql.length()-2); // cut last ", "
				sql += " WHERE id=?";
			} else {
//				sql = "INSERT INTO Site";
//				sql += "(name, address, city, state, country, phonenumber, email, id) ";
//				sql += "VALUES (?,?,?,?,?,?,?,?)";

				sql = "INSERT INTO "+table.name;
				sql += "(";
				for (String c : columns)
					sql += "`"+c+"`, ";
				sql += "id";
				sql += ") ";
				sql += "VALUES (";
				for (String c : columns)
					sql += "?,";
				sql += "?";
				sql += ")";

				if (item.get("id")==null)
					item.put("id",table.prefix+UUID.randomUUID().toString().substring(0,8));
			}
			if (readonly) {
				System.out.println("READONLY MODE");
				System.out.println("SQL: "+sql);
				System.out.println("ARGS: "+item);
				return (String)item.get("id");
			}

			PreparedStatement ps = conn.prepareStatement(sql);
			// simple naive solution: not datatype-aware
//			int index = 1;
//			for (String c : columns)
//				ps.setString(index++,(String)item.get(c));
			// datatype-aware solution
			int index = fromMapToStatement(item,ps,table);
			ps.setString(index,(String)item.get("id"));
			ps.executeUpdate();
			ps.close();

			return (String)item.get("id");
		}
	}

	public static void remove(TableInfo table, String id) throws Exception {
		try (Connection conn = FewoServer.pds.getConnection()) {
			String sql = "DELETE FROM " + table.name + " WHERE id = ?";
			if (readonly) {
				System.out.println("READONLY MODE");
				System.out.println("SQL: "+sql);
				System.out.println("ARGS: "+id);
				return;
			}
			try (PreparedStatement ps = conn.prepareStatement(sql)) {
				ps.setString(1, id);
				ps.executeUpdate();
			}
		}
	}
}
