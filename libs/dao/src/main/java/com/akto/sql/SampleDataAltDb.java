package com.akto.sql;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.akto.dto.ApiInfo;
import com.akto.dto.sql.SampleDataAlt;
import com.akto.types.BasicDBListL;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import org.apache.commons.lang3.function.FailableFunction;

public class SampleDataAltDb {

    private static void bindValues(PreparedStatement stmt, SampleDataAlt sampleData) throws SQLException {
        stmt.setObject(1, sampleData.getId());
        stmt.setString(2, sampleData.getSample());
        stmt.setInt(3, sampleData.getApiCollectionId());
        stmt.setString(4, sampleData.getMethod());
        stmt.setString(5, sampleData.getUrl());
        stmt.setInt(6, sampleData.getResponseCode());
        stmt.setInt(7, sampleData.getTimestamp());
        stmt.addBatch();
    }

    final static String INSERT_QUERY = "INSERT INTO sampledata02(id, sample, api_collection_id,method,url, response_code, timestamp) "
            + "VALUES(?,?,?,?,?,?,?)";

    public static void bulkInsert(List<SampleDataAlt> list) throws Exception {

        if (!Main.isPostgresConnected()) {
            return;
        }

        try (Connection conn = Main.getConnection();
             PreparedStatement stmt = conn.prepareStatement(INSERT_QUERY, Statement.RETURN_GENERATED_KEYS)) {

            // bind the values
            for (SampleDataAlt data : list) {
                bindValues(stmt, data);
            }

            // execute the INSERT statement and get the inserted id
            int[] updateCounts = stmt.executeBatch();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    final static String DELETE_QUERY = "DELETE from sampledata02 WHERE timestamp < ? AND id IN (?";

    public static<T> T executeQuery(FailableFunction<Connection, PreparedStatement, SQLException> prepareQueryFunc, FailableFunction<ResultSet, T, SQLException> extractResultFunc) {

        if (!Main.isPostgresConnected()) {
            return null;
        }

        Connection conn = null;
        try {
            conn = Main.getConnection();
            PreparedStatement stmt = prepareQueryFunc.apply(conn);
            boolean hasResultSet = stmt.execute();

            if (hasResultSet) {
                ResultSet rs = stmt.getResultSet();
                return extractResultFunc.apply(rs);
            } else {
                return null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    public static int executeUpdateQuery(FailableFunction<Connection, PreparedStatement, SQLException> prepareQueryFunc) {

        if (!Main.isPostgresConnected()) {
            return 0;
        }

        Connection conn = null;
        try {
            conn = Main.getConnection();
            PreparedStatement stmt = prepareQueryFunc.apply(conn);
            return stmt.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static BasicDBList runCommand(String command) throws Exception {

        if (!Main.isPostgresConnected()) {
            return new BasicDBListL("Exception in runCommand(" + command + ") Postgres not connected");
        }

        try (Connection conn = Main.getConnection();
             Statement stmt = conn.createStatement()) {

            BasicDBList ret = new BasicDBList();
            boolean hasResultSet = stmt.execute(command);


            if (hasResultSet) {
                ResultSet rs =  stmt.getResultSet();
                while (rs.next()) {
                    int cols = rs.getMetaData().getColumnCount();
                    BasicDBObject retEntry = new BasicDBObject();
                    for (int i = 0; i < cols; i++) {
                        retEntry.put(rs.getMetaData().getColumnLabel(i + 1).toLowerCase(), rs.getObject(i + 1));
                    }
                    ret.add(retEntry);
                }
            } else {
                int counter = stmt.getUpdateCount();
                ret.add("update: " + counter);
            }

            return ret;
        } catch (SQLException e) {
            return new BasicDBListL("Exception in runCommand(" + command + ") " + e.getMessage());
        }
    }

    public static void delete(List<String> uuidList, int timestamp) throws Exception {

        if (!Main.isPostgresConnected()) {
            return;
        }

        if (uuidList == null || uuidList.isEmpty()) {
            return;
        }

        String query = DELETE_QUERY;
        for (int i = 1; i < uuidList.size(); i++) {
            query += ",?";
        }
        query += ")";

        try (Connection conn = Main.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setInt(1, timestamp);
            // bind the values
            for (int i = 0; i < uuidList.size(); i++) {
                stmt.setObject(i + 2, UUID.fromString(uuidList.get(i)));
            }

            int count = stmt.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }

    }

    final static String ITERATE_QUERY = "SELECT id FROM sampledata02 ORDER BY id limit ? offset ?";

    final static String FIND_QUERY = "SELECT * FROM sampledata02 where id=?";

    public static String find(String id) throws Exception {
        FailableFunction<Connection, PreparedStatement, SQLException> prepareStmt = (conn) -> {
            PreparedStatement stmt = conn.prepareStatement(FIND_QUERY, Statement.RETURN_GENERATED_KEYS);
            stmt.setObject(1, UUID.fromString(id));
            return stmt;
        };

        FailableFunction<ResultSet, String, SQLException> extractResultFunc = (rs) -> {
            String result = null;
            while (rs.next()) {
                result = rs.getString(2);
            }
            return result;
        };

        return executeQuery(prepareStmt, extractResultFunc);
    }

    public static long getDbSizeInMb() throws Exception{
        String query = "SELECT pg_database_size('" + Main.extractDatabaseName() +  "')/1024/1024 AS size;";

        FailableFunction<Connection, PreparedStatement, SQLException> prepareStmt = (conn) -> conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);

        FailableFunction<ResultSet, Long, SQLException> extractResultFunc = (rs) -> {
            long size = 0;
            while (rs.next()) {
                size = rs.getLong(1);
            }
            return size;
        };

        return executeQuery(prepareStmt, extractResultFunc);
    }

    public static String findLatestSampleByApiInfoKey(ApiInfo.ApiInfoKey key) throws Exception{
        String query = "SELECT * FROM sampledata02 where api_collection_id=? and method=? and url=? order by timestamp desc limit 1;";
        FailableFunction<Connection, PreparedStatement, SQLException> preparedStmt = (conn) -> {
            PreparedStatement stmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);
            stmt.setObject(1, key.getApiCollectionId());
            stmt.setObject(2, key.getMethod().toString());
            stmt.setObject(3, key.getUrl());
            return stmt;
        };

        FailableFunction<ResultSet, String, SQLException> extractResultFunc = (rs) -> {
            String result = null;
            while (rs.next()) {
                result = rs.getString(2);
            }
            return result;
        };

        return executeQuery(preparedStmt, extractResultFunc);
    }

    public static List<String> findSamplesByApiInfoKey(ApiInfo.ApiInfoKey key) throws Exception{

        String query = "SELECT * FROM sampledata02 where api_collection_id=? and method=? and url=? order by timestamp desc limit 10;";
        FailableFunction<Connection, PreparedStatement, SQLException> preparedStmt = (conn) -> {
            PreparedStatement stmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);
            stmt.setObject(1, key.getApiCollectionId());
            stmt.setObject(2, key.getMethod().toString());
            stmt.setObject(3, key.getUrl());
            return stmt;
        };

        FailableFunction<ResultSet, List<String>, SQLException> extractResultFunc = (rs) -> {
            List<String> result = new ArrayList<>();
            while (rs.next()) {
                result.add(rs.getString(2));
            }
            return result;
        };

        return executeQuery(preparedStmt, extractResultFunc);
    }

    final static String ITERATE_QUERY_ALL = "SELECT id, api_collection_id, method, url FROM sampledata02 where api_collection_id=? ORDER BY id limit ? offset ?";

    public static List<SampleDataAlt> iterateAndGetAll(int apiCollectionId, int limit, int offset) throws Exception {
        FailableFunction<Connection, PreparedStatement, SQLException> prepareStmt = (conn) -> {
            PreparedStatement stmt = conn.prepareStatement(ITERATE_QUERY_ALL, Statement.RETURN_GENERATED_KEYS);
            stmt.setInt(1, apiCollectionId);
            stmt.setInt(2, limit);
            stmt.setInt(3, offset);
            return stmt;
        };

        FailableFunction<ResultSet, List<SampleDataAlt>, SQLException> extractResultFunc = (rs) -> {
            List<SampleDataAlt> data = new ArrayList<>();
            while (rs.next()) {
                String id = rs.getString(1);
                int collectionId = rs.getInt(2);
                String method = rs.getString(3);
                String url = rs.getString(4);
                UUID uuid = UUID.fromString(id);
                SampleDataAlt sampleDataAlt = new SampleDataAlt(uuid, "", collectionId, method, url, 0, 0, 0);
                data.add(sampleDataAlt);
            }
            return data;
        };

        return executeQuery(prepareStmt, extractResultFunc);
    }

    final static String DELETE_OLD_QUERY = "DELETE FROM sampledata02\n" + //
            "WHERE id IN (\n" + //
            "    SELECT id\n" + //
            "    FROM (\n" + //
            "        SELECT \n" + //
            "            id,\n" + //
            "            ROW_NUMBER() OVER (PARTITION BY api_collection_id, method, url ORDER BY timestamp DESC) AS row_num\n"
            + //
            "        FROM \n" + //
            "            sampledata02\n" + //
            "    ) ranked\n" + //
            "    WHERE ranked.row_num > 10\n" + //
            ")\n" + //
            "";

    public static int deleteOld() throws Exception {
        FailableFunction<Connection, PreparedStatement, SQLException> prepareStmt = (conn) -> {
            return conn.prepareStatement(DELETE_OLD_QUERY, Statement.RETURN_GENERATED_KEYS);
        };
        return executeUpdateQuery(prepareStmt);
    }

    public static int totalNumberOfRecords() throws Exception {
        FailableFunction<Connection, PreparedStatement, SQLException> prepareStmt = (conn) -> {
            return conn.prepareStatement("SELECT COUNT(1) FROM sampledata02", Statement.RETURN_GENERATED_KEYS);
        };

        FailableFunction<ResultSet, Integer, SQLException> extractResultFunc = (rs) -> {
            int count = 0;
            while (rs.next()) {
                count = rs.getInt(1);
            }
            return count;
        };

        return executeQuery(prepareStmt, extractResultFunc);
    }

    final static String CREATE_INDEX_QUERY = "CREATE INDEX IF NOT EXISTS idx_sampledata_composite ON sampledata02(api_collection_id, method, url, timestamp DESC)";

    public static void createIndex() throws Exception {
        FailableFunction<Connection, PreparedStatement, SQLException> prepareStmt = (conn) -> {
            return conn.prepareStatement(CREATE_INDEX_QUERY, Statement.RETURN_GENERATED_KEYS);
        };

        executeQuery(prepareStmt, (rs) -> null);
    }

    final static String UPDATE_URL_QUERY = "update sampledata02 set url=? where id in (?";

    public static void updateUrl(List<String> uuidList, String newUrl) throws Exception {

        if (uuidList == null || uuidList.isEmpty()) {
            return;
        }

        String query = UPDATE_URL_QUERY;
        for (int i = 1; i < uuidList.size(); i++) {
            query += ",?";
        }
        query += ")";

        String finalQuery = query;
        FailableFunction<Connection, PreparedStatement, SQLException> prepareStmt = (conn) -> {
            PreparedStatement stmt = conn.prepareStatement(finalQuery, Statement.RETURN_GENERATED_KEYS);
            stmt.setString(1, newUrl);
            for (int i = 0; i < uuidList.size(); i++) {
                stmt.setObject(i + 2, UUID.fromString(uuidList.get(i)));
            }
            return stmt;
        };

        executeQuery(prepareStmt, (rs) -> null);
    }

    // TODO: close all connections or create some connections for different use cases, and use them.
}
