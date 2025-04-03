package com.akto.sql;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
// import org.postgresql.Driver;
import java.util.UUID;

import javax.sql.DataSource;

import org.postgresql.ds.PGSimpleDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.akto.dao.context.Context;
import com.akto.dto.sql.SampleDataAlt;

public class Main {

    final static String connectionUri = 
    System.getenv("POSTGRES_URL");
    // "jdbc:postgresql://localhost:5432/shivansh";
    final static String user = 
    System.getenv("POSTGRES_USER");
    // "shivansh";
    final static String password = 
    System.getenv("POSTGRES_PASSWORD");
    // "example";
    
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String args[]) {

        // DataSource ds = createDataSource();

        // // Connection c = null;
        // try {
        //     Connection conn = ds.getConnection();
        //     PreparedStatement stmt = conn.prepareStatement("SELECT * FROM birds");
        //     ResultSet rs = stmt.executeQuery();

        //     // Class.forName("org.postgresql.Driver");
        //     // c = DriverManager.getConnection(connectionUri, user, password);
        // } catch (Exception e) {
        //     e.printStackTrace();
        //     System.err.println(e.getClass().getName() + ": " + e.getMessage());
        //     System.exit(0);
        // }
        // System.out.println("Opened database successfully");

        // createSampleDataTable();
        try {
            // SampleDataAlt s1 = new SampleDataAlt(UUID.randomUUID(), "sample 2", 123, "GET", "https://qapi.mpl.live:443/12312/pending-invites", -1, Context.now(), 1);
            // SampleDataAlt s2 = new SampleDataAlt(UUID.randomUUID(), "sample 1", 123, "POST", "https://qapi.mpl.live:443/abc/pending-invites", -1, Context.now(), 1);

            // List<SampleDataAlt> list = new ArrayList<>();
            // list.add(s1);
            // list.add(s2);
            // SampleDataAltDb.bulkInsert(list);
        // int limit = 5;
        // int skip = 0;
        // List<String> ids = SampleDataAltDb.iterateAndGetIds(limit, skip);
        // while (ids != null && !ids.isEmpty()) {

        //     System.out.println(ids);
        //     skip += limit;
        //     ids = SampleDataAltDb.iterateAndGetIds(limit, skip);
        // }
        // ids = new ArrayList<>();
        // ids.add("f475af40-b533-409c-8def-f7d818be880f");
        // ids.add("0e83a826-369c-4a34-99ee-0c6bce494e1c");

        // SampleDataAltDb.delete(ids, 1718426392);
            // SampleDataAltDb.deleteOld();
            
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        

    }

    private static int lastPing = 0;
    private final static int PING_INTERVAL = 5 * 60;

    private static boolean postgresConnected = false;

    public static boolean isPostgresConnected() {
        int now = Context.now();
        try {
            if ((lastPing + PING_INTERVAL) <= now) {
                lastPing = now;
                getConnection();
                postgresConnected = true;
                logger.info("established postgres connection lastPing: " + lastPing + " now: " + now );
            }
            logger.info("reusing existing postgres connection isConnected: " + postgresConnected + " lastPing: " + lastPing + " now: " + now );
        } catch (Exception e) {
            e.printStackTrace();
            logger.error("error establishing postgres connection now: " + now + " error: " + e.getMessage());
        }
        return postgresConnected;
    }

    public static String extractDatabaseName() {
        String regex = "jdbc:postgresql://[^/]+:\\d+/(\\w+)";
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(regex);
        java.util.regex.Matcher matcher = pattern.matcher(connectionUri);

        if (matcher.find()) {
            return matcher.group(1);
        } else {
            throw new IllegalArgumentException("Database name not found in the connection URL.");
        }
    }

    private static DataSource createDataSource() {
        final String url = connectionUri;
        final PGSimpleDataSource dataSource = new PGSimpleDataSource();
        if (connectionUri == null || user == null || password == null) {
            logger.info("createDataSource values: " + connectionUri + " user: " + user + " password: " + password );
            return dataSource;
        }
        dataSource.setUrl(url);
        dataSource.setUser(user);
        dataSource.setPassword(password);
        return dataSource;
    }

    public static Connection getConnection() throws Exception {
        DataSource ds = createDataSource();
        return ds.getConnection();
    }

    public static void createSampleDataTable() {
        DataSource ds = createDataSource();

        try {
            Connection conn = ds.getConnection();
            PreparedStatement stmt = conn.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS SAMPLEDATA02 " +
                            "(ID UUID PRIMARY KEY NOT NULL," +
                            "SAMPLE TEXT NOT NULL," +
                            "API_COLLECTION_ID INT NOT NULL," +
                            " METHOD VARCHAR(50) NOT NULL," +
                            " URL VARCHAR(2083) NOT NULL," +
                            " RESPONSE_CODE INT NOT NULL," +
                            // add account id, if needed.
                            " TIMESTAMP INT NOT NULL)");
            stmt.executeUpdate();
            stmt.close();
            conn.close();

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println(e.getClass().getName() + ": " + e.getMessage());
        }
    }

    /*
     * 1. Create table
     * 2. Insert query for sample data.
     * 2. Read query for sample data.
     */

     /*
      * sample data table
       id uuid
       sample string
       apiCollectionId int
       method string
       url string 
       responseCode int
     */

}
