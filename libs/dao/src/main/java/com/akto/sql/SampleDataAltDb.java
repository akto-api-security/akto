package com.akto.sql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;

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

}
