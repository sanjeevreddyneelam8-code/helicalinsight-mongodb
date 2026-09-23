package com.helical.mongodb;

import com.mongodb.client.FindIterable;
import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MongoStatement implements Statement {

    private final MongoConnection connection;
    private ResultSet currentResultSet;
    private boolean closed = false;
    private int maxRows = 0;
    private int queryTimeout = 0;

    public MongoStatement(MongoConnection connection) {
        this.connection = connection;
    }

    @Override
    public ResultSet executeQuery(String sql) throws SQLException {
        checkClosed();
        if (sql == null || sql.trim().isEmpty()) {
            throw new SQLException("SQL query cannot be null or empty");
        }

        String trimmed = sql.trim().replaceAll(";$", "").trim();

        // Handle SELECT 1 heartbeat query (used by Helical Insight connection pool & tests)
        if (isHeartbeatQuery(trimmed)) {
            List<ColumnDefinition> cols = Collections.singletonList(
                    new ColumnDefinition("1", Types.INTEGER, "INTEGER")
            );
            List<List<Object>> rows = Collections.singletonList(
                    Collections.singletonList(1)
            );
            this.currentResultSet = new MongoResultSet(cols, rows, this);
            return currentResultSet;
        }

        // Handle SQL SELECT queries
        if (trimmed.toUpperCase().startsWith("SELECT")) {
            return executeSqlSelect(trimmed);
        }

        // Handle JSON / BSON queries
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return executeJsonQuery(trimmed);
        }

        throw new SQLException("Unsupported query format: " + sql);
    }

    private boolean isHeartbeatQuery(String sql) {
        String upper = sql.toUpperCase().replaceAll("\\s+", " ").trim();
        return upper.equals("SELECT 1")
                || upper.equals("SELECT 1 FROM DUAL")
                || upper.equals("SELECT 1 FROM \"DUAL\"")
                || upper.equals("SELECT 1 AS TEST")
                || upper.startsWith("SELECT 1 WHERE")
                || upper.startsWith("/* PING */");
    }

    private ResultSet executeSqlSelect(String sql) throws SQLException {
        MongoClient client = connection.getMongoClient();
        if (client == null) {
            throw new SQLException("MongoDB connection is not established");
        }

        // Regex to parse SELECT <columns> FROM <collection> [LIMIT <limit>]
        Pattern pattern = Pattern.compile("(?i)SELECT\\s+(.+?)\\s+FROM\\s+[`\"\\[]?([a-zA-Z0-9_.-]+)[`\"\\]]?(?:\\s+LIMIT\\s+(\\d+))?");
        Matcher matcher = pattern.matcher(sql);

        if (!matcher.find()) {
            // Fallback for complex queries or metadata queries
            return new MongoResultSet(Collections.emptyList(), Collections.emptyList(), this);
        }

        String columnsPart = matcher.group(1).trim();
        String collectionName = matcher.group(2).trim();
        String limitStr = matcher.group(3);

        int limit = maxRows > 0 ? maxRows : 1000;
        if (limitStr != null) {
            try {
                int parsedLimit = Integer.parseInt(limitStr);
                if (limit == 0 || parsedLimit < limit) {
                    limit = parsedLimit;
                }
            } catch (NumberFormatException ignored) {
            }
        }

        String targetDb = connection.getCatalog();
        if (targetDb == null || targetDb.isEmpty()) {
            targetDb = "test";
        }

        MongoDatabase db = client.getDatabase(targetDb);
        MongoCollection<Document> coll = db.getCollection(collectionName);
        FindIterable<Document> docs = coll.find().limit(limit);

        boolean selectAll = columnsPart.equals("*");
        List<String> requestedColumns = new ArrayList<>();
        if (!selectAll) {
            for (String col : columnsPart.split(",")) {
                String cleanCol = col.trim().replaceAll("^[`\"']|[`\"']$", "");
                if (!cleanCol.isEmpty()) {
                    requestedColumns.add(cleanCol);
                }
            }
        }

        Map<String, ColumnDefinition> discoveredColumns = new LinkedHashMap<>();
        List<List<Object>> rows = new ArrayList<>();

        for (Document doc : docs) {
            if (selectAll) {
                for (String key : doc.keySet()) {
                    if (!discoveredColumns.containsKey(key)) {
                        Object val = doc.get(key);
                        discoveredColumns.put(key, new ColumnDefinition(key, inferSqlType(val), inferTypeName(val)));
                    }
                }
            }
        }

        if (!selectAll) {
            for (String col : requestedColumns) {
                discoveredColumns.put(col, new ColumnDefinition(col, Types.VARCHAR, "VARCHAR"));
            }
        }

        List<ColumnDefinition> colsList = new ArrayList<>(discoveredColumns.values());

        // Re-read documents for rows
        for (Document doc : coll.find().limit(limit)) {
            List<Object> row = new ArrayList<>();
            for (ColumnDefinition colDef : colsList) {
                Object val = doc.get(colDef.getName());
                row.add(val != null ? val : null);
            }
            rows.add(row);
        }

        this.currentResultSet = new MongoResultSet(colsList, rows, this);
        return currentResultSet;
    }

    private ResultSet executeJsonQuery(String json) throws SQLException {
        MongoClient client = connection.getMongoClient();
        if (client == null) {
            throw new SQLException("MongoDB connection is not established");
        }
        String targetDb = connection.getCatalog();
        if (targetDb == null || targetDb.isEmpty()) {
            targetDb = "test";
        }
        MongoDatabase db = client.getDatabase(targetDb);
        Document command = Document.parse(json);
        Document result = db.runCommand(command);

        List<ColumnDefinition> cols = Collections.singletonList(
                new ColumnDefinition("result", Types.VARCHAR, "VARCHAR")
        );
        List<List<Object>> rows = Collections.singletonList(
                Collections.singletonList(result.toJson())
        );
        this.currentResultSet = new MongoResultSet(cols, rows, this);
        return currentResultSet;
    }

    private static int inferSqlType(Object value) {
        if (value == null) return Types.VARCHAR;
        if (value instanceof String) return Types.VARCHAR;
        if (value instanceof Integer) return Types.INTEGER;
        if (value instanceof Long) return Types.BIGINT;
        if (value instanceof Double || value instanceof Float) return Types.DOUBLE;
        if (value instanceof Boolean) return Types.BOOLEAN;
        if (value instanceof java.util.Date) return Types.TIMESTAMP;
        return Types.VARCHAR;
    }

    private static String inferTypeName(Object value) {
        if (value == null) return "VARCHAR";
        if (value instanceof String) return "VARCHAR";
        if (value instanceof Integer) return "INTEGER";
        if (value instanceof Long) return "BIGINT";
        if (value instanceof Double || value instanceof Float) return "DOUBLE";
        if (value instanceof Boolean) return "BOOLEAN";
        if (value instanceof java.util.Date) return "TIMESTAMP";
        return "VARCHAR";
    }

    @Override
    public int executeUpdate(String sql) throws SQLException {
        return 0;
    }

    @Override
    public void close() throws SQLException {
        if (currentResultSet != null) {
            currentResultSet.close();
        }
        this.closed = true;
    }

    @Override
    public int getMaxFieldSize() throws SQLException {
        return 0;
    }

    @Override
    public void setMaxFieldSize(int max) throws SQLException {
    }

    @Override
    public int getMaxRows() throws SQLException {
        return maxRows;
    }

    @Override
    public void setMaxRows(int max) throws SQLException {
        this.maxRows = max;
    }

    @Override
    public void setEscapeProcessing(boolean enable) throws SQLException {
    }

    @Override
    public int getQueryTimeout() throws SQLException {
        return queryTimeout;
    }

    @Override
    public void setQueryTimeout(int seconds) throws SQLException {
        this.queryTimeout = seconds;
    }

    @Override
    public void cancel() throws SQLException {
    }

    @Override
    public SQLWarning getWarnings() throws SQLException {
        return null;
    }

    @Override
    public void clearWarnings() throws SQLException {
    }

    @Override
    public void setCursorName(String name) throws SQLException {
    }

    @Override
    public boolean execute(String sql) throws SQLException {
        this.currentResultSet = executeQuery(sql);
        return currentResultSet != null;
    }

    @Override
    public ResultSet getResultSet() throws SQLException {
        return currentResultSet;
    }

    @Override
    public int getUpdateCount() throws SQLException {
        return -1;
    }

    @Override
    public boolean getMoreResults() throws SQLException {
        return false;
    }

    @Override
    public void setFetchDirection(int direction) throws SQLException {
    }

    @Override
    public int getFetchDirection() throws SQLException {
        return ResultSet.FETCH_FORWARD;
    }

    @Override
    public void setFetchSize(int rows) throws SQLException {
    }

    @Override
    public int getFetchSize() throws SQLException {
        return 0;
    }

    @Override
    public int getResultSetConcurrency() throws SQLException {
        return ResultSet.CONCUR_READ_ONLY;
    }

    @Override
    public int getResultSetType() throws SQLException {
        return ResultSet.TYPE_FORWARD_ONLY;
    }

    @Override
    public void addBatch(String sql) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void clearBatch() throws SQLException {
    }

    @Override
    public int[] executeBatch() throws SQLException {
        return new int[0];
    }

    @Override
    public Connection getConnection() throws SQLException {
        return connection;
    }

    @Override
    public boolean getMoreResults(int current) throws SQLException {
        return false;
    }

    @Override
    public ResultSet getGeneratedKeys() throws SQLException {
        return new MongoResultSet(Collections.emptyList(), Collections.emptyList(), this);
    }

    @Override
    public int executeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
        return 0;
    }

    @Override
    public int executeUpdate(String sql, int[] columnIndexes) throws SQLException {
        return 0;
    }

    @Override
    public int executeUpdate(String sql, String[] columnNames) throws SQLException {
        return 0;
    }

    @Override
    public boolean execute(String sql, int autoGeneratedKeys) throws SQLException {
        return execute(sql);
    }

    @Override
    public boolean execute(String sql, int[] columnIndexes) throws SQLException {
        return execute(sql);
    }

    @Override
    public boolean execute(String sql, String[] columnNames) throws SQLException {
        return execute(sql);
    }

    @Override
    public int getResultSetHoldability() throws SQLException {
        return ResultSet.CLOSE_CURSORS_AT_COMMIT;
    }

    @Override
    public boolean isClosed() throws SQLException {
        return closed;
    }

    @Override
    public void setPoolable(boolean poolable) throws SQLException {
    }

    @Override
    public boolean isPoolable() throws SQLException {
        return false;
    }

    @Override
    public void closeOnCompletion() throws SQLException {
    }

    @Override
    public boolean isCloseOnCompletion() throws SQLException {
        return false;
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
            throw new SQLException("Statement is closed");
        }
    }
}
