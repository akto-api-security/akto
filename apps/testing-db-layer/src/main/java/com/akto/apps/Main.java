package com.akto.apps;

import com.akto.sql.SampleDataAltDb;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {

        try {
            com.akto.sql.Main.createSampleDataTable();
        } catch(Exception e){
            e.printStackTrace();
            logger.error("Error creating sample data table" + e.getMessage());
            System.exit(0);
        }

        try {
            SampleDataAltDb.createIndex();
        } catch(Exception e){
            e.printStackTrace();
            logger.error("Error creating index" + e.getMessage());
        }

    }
}