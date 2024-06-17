package com.akto.sql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

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

    final static String INSERT_QUERY = "INSERT INTO sampledata(id, sample, api_collection_id,method,url, response_code, timestamp) "
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

    final static String DELETE_QUERY = "DELETE from sampledata WHERE timestamp < ? AND id IN (?";

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

    final static String ITERATE_QUERY = "SELECT id FROM sampledata ORDER BY id limit ? offset ?";

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

    final static String FIND_QUERY = "SELECT * FROM sampledata where id=?";

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
}
