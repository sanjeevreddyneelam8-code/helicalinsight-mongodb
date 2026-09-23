package com.helical.mongodb;

import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Helical MongoDB JDBC Driver
 * Provides JDBC connectivity to MongoDB databases for Helical Insight.
 * 
 * Supports URLs:
 * - mongodb://[username:password@]host[:port]/database[?options]
 * - mongodb+srv://[username:password@]host/database[?options]
 * - jdbc:mongodb://[username:password@]host[:port]/database[?options]
 * - jdbc:mongo://[username:password@]host[:port]/database[?options]
 */
public class MongoJdbcDriver implements Driver {

    public static final String DRIVER_NAME = "Helical MongoDB JDBC Driver";
    public static final int MAJOR_VERSION = 1;
    public static final int MINOR_VERSION = 0;

    static {
        try {
            DriverManager.registerDriver(new MongoJdbcDriver());
        } catch (SQLException e) {
            throw new RuntimeException("Failed to register MongoJdbcDriver with DriverManager", e);
        }
    }

    @Override
    public boolean acceptsURL(String url) throws SQLException {
        if (url == null) {
            return false;
        }
        String cleanUrl = url.trim().toLowerCase();
        return cleanUrl.startsWith("mongodb://")
                || cleanUrl.startsWith("mongodb+srv://")
                || cleanUrl.startsWith("jdbc:mongodb://")
                || cleanUrl.startsWith("jdbc:mongodb+srv://")
                || cleanUrl.startsWith("jdbc:mongo://");
    }

    @Override
    public java.sql.Connection connect(String url, Properties info) throws SQLException {
        if (!acceptsURL(url)) {
            return null;
        }
        return new MongoConnection(url, info);
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
        DriverPropertyInfo hostProp = new DriverPropertyInfo("host", "localhost");
        hostProp.description = "MongoDB Host";
        hostProp.required = true;

        DriverPropertyInfo portProp = new DriverPropertyInfo("port", "27017");
        portProp.description = "MongoDB Port";
        portProp.required = false;

        DriverPropertyInfo dbProp = new DriverPropertyInfo("database", "");
        dbProp.description = "MongoDB Database Name";
        dbProp.required = false;

        DriverPropertyInfo userProp = new DriverPropertyInfo("user", "");
        userProp.description = "Username";
        userProp.required = false;

        DriverPropertyInfo passwordProp = new DriverPropertyInfo("password", "");
        passwordProp.description = "Password";
        passwordProp.required = false;

        return new DriverPropertyInfo[] { hostProp, portProp, dbProp, userProp, passwordProp };
    }

    @Override
    public int getMajorVersion() {
        return MAJOR_VERSION;
    }

    @Override
    public int getMinorVersion() {
        return MINOR_VERSION;
    }

    @Override
    public boolean jdbcCompliant() {
        return false;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return Logger.getLogger(MongoJdbcDriver.class.getName());
    }
}
