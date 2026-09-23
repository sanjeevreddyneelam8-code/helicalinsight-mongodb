package com.helical.mongodb;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientOptions;
import com.mongodb.MongoClientURI;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;
import org.bson.Document;

import java.net.URI;
import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLClientInfoException;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Struct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;

public class MongoConnection implements Connection {

    private final String rawUrl;
    private final Properties info;
    private MongoClient mongoClient;
    private String databaseName;
    private boolean closed = false;
    private boolean autoCommit = true;

    public MongoConnection(String url, Properties info) throws SQLException {
        this.rawUrl = url;
        this.info = info != null ? info : new Properties();
        connect();
    }

    private void connect() throws SQLException {
        try {
            String cleanUrl = rawUrl.trim();
            if (cleanUrl.toLowerCase().startsWith("jdbc:")) {
                cleanUrl = cleanUrl.substring(5).trim();
            }
            if (cleanUrl.toLowerCase().startsWith("mongo://")) {
                cleanUrl = "mongodb://" + cleanUrl.substring(8);
            }

            // Extract credentials from properties if not in URL
            String user = info.getProperty("user");
            if (user == null || user.isEmpty()) {
                user = info.getProperty("userName");
            }
            if (user == null || user.isEmpty()) {
                user = info.getProperty("username");
            }
            String password = info.getProperty("password");

            // Extract database name from properties or URL
            this.databaseName = info.getProperty("database");
            if (this.databaseName == null || this.databaseName.isEmpty()) {
                this.databaseName = info.getProperty("DatabaseName");
            }

            // Check if credentials should be injected into URI
            if (user != null && !user.isEmpty() && password != null && !cleanUrl.contains("@")) {
                int schemeEnd = cleanUrl.indexOf("://");
                if (schemeEnd != -1) {
                    String scheme = cleanUrl.substring(0, schemeEnd + 3);
                    String remainder = cleanUrl.substring(schemeEnd + 3);
                    cleanUrl = scheme + user + ":" + password + "@" + remainder;
                }
            }

            MongoClientURI uri = new MongoClientURI(cleanUrl);
            this.mongoClient = new MongoClient(uri);

            if ((this.databaseName == null || this.databaseName.isEmpty()) && uri.getDatabase() != null) {
                this.databaseName = uri.getDatabase();
            }
            if (this.databaseName == null || this.databaseName.isEmpty()) {
                this.databaseName = "test";
            }

            // Verify connection
            isValid(5);
        } catch (Exception e) {
            closeQuietly();
            throw new SQLException("Failed to connect to MongoDB: " + e.getMessage(), e);
        }
    }

    public MongoClient getMongoClient() {
        return mongoClient;
    }

    public String getUrl() {
        return rawUrl;
    }

    @Override
    public Statement createStatement() throws SQLException {
        checkClosed();
        return new MongoStatement(this);
    }

    @Override
    public PreparedStatement prepareStatement(String sql) throws SQLException {
        checkClosed();
        return new MongoPreparedStatement(this, sql);
    }

    @Override
    public CallableStatement prepareCall(String sql) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public String nativeSQL(String sql) throws SQLException {
        return sql;
    }

    @Override
    public void setAutoCommit(boolean autoCommit) throws SQLException {
        this.autoCommit = autoCommit;
    }

    @Override
    public boolean getAutoCommit() throws SQLException {
        return autoCommit;
    }

    @Override
    public void commit() throws SQLException {
    }

    @Override
    public void rollback() throws SQLException {
    }

    @Override
    public void close() throws SQLException {
        this.closed = true;
        closeQuietly();
    }

    private void closeQuietly() {
        if (mongoClient != null) {
            try {
                mongoClient.close();
            } catch (Exception ignored) {
            }
            mongoClient = null;
        }
    }

    @Override
    public boolean isClosed() throws SQLException {
        return closed;
    }

    @Override
    public DatabaseMetaData getMetaData() throws SQLException {
        checkClosed();
        return new MongoDatabaseMetaData(this);
    }

    @Override
    public void setReadOnly(boolean readOnly) throws SQLException {
    }

    @Override
    public boolean isReadOnly() throws SQLException {
        return false;
    }

    @Override
    public void setCatalog(String catalog) throws SQLException {
        this.databaseName = catalog;
    }

    @Override
    public String getCatalog() throws SQLException {
        return databaseName;
    }

    @Override
    public void setTransactionIsolation(int level) throws SQLException {
    }

    @Override
    public int getTransactionIsolation() throws SQLException {
        return TRANSACTION_NONE;
    }

    @Override
    public SQLWarning getWarnings() throws SQLException {
        return null;
    }

    @Override
    public void clearWarnings() throws SQLException {
    }

    @Override
    public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
        return createStatement();
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        return prepareStatement(sql);
    }

    @Override
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public Map<String, Class<?>> getTypeMap() throws SQLException {
        return Collections.emptyMap();
    }

    @Override
    public void setTypeMap(Map<String, Class<?>> map) throws SQLException {
    }

    @Override
    public void setHoldability(int holdability) throws SQLException {
    }

    @Override
    public int getHoldability() throws SQLException {
        return ResultSet.CLOSE_CURSORS_AT_COMMIT;
    }

    @Override
    public Savepoint setSavepoint() throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public Savepoint setSavepoint(String name) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void rollback(Savepoint savepoint) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void releaseSavepoint(Savepoint savepoint) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        return createStatement();
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        return prepareStatement(sql);
    }

    @Override
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
        return prepareStatement(sql);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
        return prepareStatement(sql);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
        return prepareStatement(sql);
    }

    @Override
    public Clob createClob() throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public Blob createBlob() throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public NClob createNClob() throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public SQLXML createSQLXML() throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public boolean isValid(int timeout) throws SQLException {
        if (closed || mongoClient == null) {
            return false;
        }
        try {
            // Heartbeat via getAddress or ping command
            return mongoClient.getAddress() != null;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void setClientInfo(String name, String value) throws SQLClientInfoException {
        info.setProperty(name, value);
    }

    @Override
    public void setClientInfo(Properties properties) throws SQLClientInfoException {
        info.putAll(properties);
    }

    @Override
    public String getClientInfo(String name) throws SQLException {
        return info.getProperty(name);
    }

    @Override
    public Properties getClientInfo() throws SQLException {
        return info;
    }

    @Override
    public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void setSchema(String schema) throws SQLException {
        this.databaseName = schema;
    }

    @Override
    public String getSchema() throws SQLException {
        return databaseName;
    }

    @Override
    public void abort(Executor executor) throws SQLException {
        close();
    }

    @Override
    public void setNetworkTimeout(Executor executor, int milliseconds) throws SQLException {
    }

    @Override
    public int getNetworkTimeout() throws SQLException {
        return 0;
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return iface.cast(this);
        }
        throw new SQLException("Cannot unwrap to " + iface.getName());
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return iface.isInstance(this);
    }

    private void checkClosed() throws SQLException {
        if (closed) {
            throw new SQLException("Connection is closed");
        }
    }
}
