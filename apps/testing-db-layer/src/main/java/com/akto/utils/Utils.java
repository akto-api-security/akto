package com.akto.utils;

import com.akto.sql.Main;
import org.apache.commons.lang3.function.FailableFunction;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class Utils {

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



}
