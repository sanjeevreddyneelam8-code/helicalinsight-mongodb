package com.mongodb.jdbc;

import com.helical.mongodb.MongoJdbcDriver;

import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Compatibility alias for com.mongodb.jdbc.MongoDriver delegating to Helical MongoJdbcDriver.
 */
public class MongoDriver extends MongoJdbcDriver {

    static {
        try {
            DriverManager.registerDriver(new MongoDriver());
        } catch (SQLException e) {
            throw new RuntimeException("Failed to register com.mongodb.jdbc.MongoDriver", e);
        }
    }
}
