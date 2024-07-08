package com.akto.sql;

import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import com.akto.dto.ApiInfo;
import com.akto.dto.sql.SampleDataAlt;

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

        try (Connection conn = Main.getConnection();
                PreparedStatement stmt = conn.prepareStatement(INSERT_QUERY, Statement.RETURN_GENERATED_KEYS)) {

            // bind the values
            for (SampleDataAlt data : list) {
                bindValues(stmt, data);
            }

            // execute the INSERT statement and get the inserted id
            int[] updateCounts = stmt.executeBatch();
            System.out.println(Arrays.toString(updateCounts));

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    final static String DELETE_QUERY = "DELETE from sampledata02 WHERE timestamp < ? AND id IN (?";

    public static void delete(List<String> uuidList, int timestamp) throws Exception {

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
            System.out.println("Deleted " + count);

        } catch (SQLException e) {
            e.printStackTrace();
        }

    }

    final static String ITERATE_QUERY = "SELECT id FROM sampledata02 ORDER BY id limit ? offset ?";

    public static List<String> iterateAndGetIds(int limit, int offset) throws Exception {
        List<String> idList = new ArrayList<>();

        try (Connection conn = Main.getConnection();
                PreparedStatement stmt = conn.prepareStatement(ITERATE_QUERY, Statement.RETURN_GENERATED_KEYS)) {

            // bind the values
            stmt.setInt(1, limit);
            stmt.setInt(2, offset);

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                idList.add(rs.getString(1));
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return idList;
    }

    final static String FIND_QUERY = "SELECT * FROM sampledata02 where id=?";

    public static String find(String id) throws Exception {
        String result = null;

        try (Connection conn = Main.getConnection();
                PreparedStatement stmt = conn.prepareStatement(FIND_QUERY, Statement.RETURN_GENERATED_KEYS)) {

                    stmt.setObject(1, UUID.fromString(id));

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                result = rs.getString(2);
                // System.out.println(result);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return result;
    }

    public static long getDbSizeInMb() throws Exception{
        String query = "SELECT pg_database_size('" + Main.extractDatabaseName() +  "')/1024/1024 AS size;";
        long size = 0;
        try (Connection conn = Main.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                size = rs.getLong(1);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return size;
    }

    public static String findLatestSampleByApiInfoKey(ApiInfo.ApiInfoKey key) throws Exception{
        String result = null;
        String query = "SELECT * FROM sampledata where api_collection_id=? and method=? and url=? order by timestamp desc limit 1;";
        try (Connection conn = Main.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setObject(1, key.getApiCollectionId());
            stmt.setObject(2, key.getMethod().toString());
            stmt.setObject(3, key.getUrl());

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                result = rs.getString(2);
                // System.out.println(result);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return result;
    }

    public static List<String> findSamplesByApiInfoKey(ApiInfo.ApiInfoKey key) throws Exception{
        List<String> result = new ArrayList<>();
        String query = "SELECT * FROM sampledata where api_collection_id=? and method=? and url=? order by timestamp desc limit 10;";
        try (Connection conn = Main.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setObject(1, key.getApiCollectionId());
            stmt.setObject(2, key.getMethod().toString());
            stmt.setObject(3, key.getUrl());

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                result.add(rs.getString(2));
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return result;
    }
    final static String ITERATE_QUERY_ALL = "SELECT id, api_collection_id, method, url FROM sampledata where api_collection_id=? ORDER BY id limit ? offset ?";

    public static List<SampleDataAlt> iterateAndGetAll(int apiCollectionId, int limit, int offset) throws Exception {
        List<SampleDataAlt> data = new ArrayList<>();

        try (Connection conn = Main.getConnection();
                PreparedStatement stmt = conn.prepareStatement(ITERATE_QUERY_ALL, Statement.RETURN_GENERATED_KEYS)) {

            // bind the values
            stmt.setInt(1, apiCollectionId);
            stmt.setInt(2, limit);
            stmt.setInt(3, offset);

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String id = rs.getString(1);
                apiCollectionId = rs.getInt(2);
                String method = rs.getString(3);
                String url = rs.getString(4);
                UUID uuid = UUID.fromString(id);
                SampleDataAlt sampleDataAlt = new SampleDataAlt(uuid, "", apiCollectionId, method, url, 0, 0, 0);
                data.add(sampleDataAlt);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return data;
    }

    final static String DELETE_OLD_QUERY = "DELETE FROM sampledata\n" + //
            "WHERE id IN (\n" + //
            "    SELECT id\n" + //
            "    FROM (\n" + //
            "        SELECT \n" + //
            "            id,\n" + //
            "            ROW_NUMBER() OVER (PARTITION BY api_collection_id, method, url ORDER BY timestamp DESC) AS row_num\n"
            + //
            "        FROM \n" + //
            "            sampledata\n" + //
            "    ) ranked\n" + //
            "    WHERE ranked.row_num > 10\n" + //
            ")\n" + //
            "";

    public static int deleteOld() throws Exception {

        try (Connection conn = Main.getConnection();
                PreparedStatement stmt = conn.prepareStatement(DELETE_OLD_QUERY, Statement.RETURN_GENERATED_KEYS)) {

            int count = stmt.executeUpdate();
            System.out.println("Deleted " + count);
            return count;
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }

    }

    public static int totalNumberOfRecords() throws Exception {
        int count = 0;
        try (Connection conn = Main.getConnection();
                PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(1) FROM sampledata", Statement.RETURN_GENERATED_KEYS)) {

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                count = rs.getInt(1);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return count;
    }

    final static String CREATE_INDEX_QUERY = "CREATE INDEX IF NOT EXISTS idx_sampledata_composite ON sampledata(api_collection_id, method, url, timestamp DESC)";

    public static void createIndex() throws Exception {
        try (Connection conn = Main.getConnection();
                PreparedStatement stmt = conn.prepareStatement(CREATE_INDEX_QUERY, Statement.RETURN_GENERATED_KEYS)) {

            stmt.execute();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    final static String UPDATE_URL_QUERY = "update sampledata set url=? where id in (?";

    public static void updateUrl(List<String> uuidList, String newUrl) throws Exception {

        if (uuidList == null || uuidList.isEmpty()) {
            return;
        }

        String query = UPDATE_URL_QUERY;
        for (int i = 1; i < uuidList.size(); i++) {
            query += ",?";
        }
        query += ")";

        try (Connection conn = Main.getConnection();
                PreparedStatement stmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, newUrl);
            // bind the values
            for (int i = 0; i < uuidList.size(); i++) {
                stmt.setObject(i + 2, UUID.fromString(uuidList.get(i)));
            }

            int count = stmt.executeUpdate();
            System.out.println("Updated " + count);

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

}
