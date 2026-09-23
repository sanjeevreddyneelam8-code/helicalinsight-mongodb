package com.helical.mongodb;

import com.mongodb.MongoClient;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoIterable;
import org.bson.Document;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.RowIdLifetime;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MongoDatabaseMetaData implements DatabaseMetaData {

    private final MongoConnection connection;

    public MongoDatabaseMetaData(MongoConnection connection) {
        this.connection = connection;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return connection;
    }

    @Override
    public String getDatabaseProductName() throws SQLException {
        return "MongoDB";
    }

    @Override
    public String getDatabaseProductVersion() throws SQLException {
        return "6.0";
    }

    @Override
    public String getDriverName() throws SQLException {
        return MongoJdbcDriver.DRIVER_NAME;
    }

    @Override
    public String getDriverVersion() throws SQLException {
        return MongoJdbcDriver.MAJOR_VERSION + "." + MongoJdbcDriver.MINOR_VERSION;
    }

    @Override
    public int getDriverMajorVersion() {
        return MongoJdbcDriver.MAJOR_VERSION;
    }

    @Override
    public int getDriverMinorVersion() {
        return MongoJdbcDriver.MINOR_VERSION;
    }

    @Override
    public ResultSet getCatalogs() throws SQLException {
        List<ColumnDefinition> cols = Collections.singletonList(
                new ColumnDefinition("TABLE_CAT", Types.VARCHAR, "VARCHAR")
        );
        List<List<Object>> rows = new ArrayList<>();
        MongoClient client = connection.getMongoClient();
        if (client != null) {
            try {
                MongoIterable<String> dbNames = client.listDatabaseNames();
                for (String dbName : dbNames) {
                    rows.add(Collections.singletonList(dbName));
                }
            } catch (Exception e) {
                String currentDb = connection.getCatalog();
                if (currentDb != null && !currentDb.isEmpty()) {
                    rows.add(Collections.singletonList(currentDb));
                }
            }
        } else {
            String currentDb = connection.getCatalog();
            rows.add(Collections.singletonList(currentDb != null ? currentDb : "database"));
        }
        return new MongoResultSet(cols, rows);
    }

    @Override
    public ResultSet getSchemas() throws SQLException {
        return getSchemas(null, null);
    }

    @Override
    public ResultSet getSchemas(String catalog, String schemaPattern) throws SQLException {
        List<ColumnDefinition> cols = Arrays.asList(
                new ColumnDefinition("TABLE_SCHEM", Types.VARCHAR, "VARCHAR"),
                new ColumnDefinition("TABLE_CATALOG", Types.VARCHAR, "VARCHAR")
        );
        List<List<Object>> rows = new ArrayList<>();
        String currentDb = catalog != null ? catalog : connection.getCatalog();
        rows.add(Arrays.asList(currentDb, currentDb));
        return new MongoResultSet(cols, rows);
    }

    @Override
    public ResultSet getTables(String catalog, String schemaPattern, String tableNamePattern, String[] types) throws SQLException {
        List<ColumnDefinition> cols = Arrays.asList(
                new ColumnDefinition("TABLE_CAT", Types.VARCHAR, "VARCHAR"),
                new ColumnDefinition("TABLE_SCHEM", Types.VARCHAR, "VARCHAR"),
                new ColumnDefinition("TABLE_NAME", Types.VARCHAR, "VARCHAR"),
                new ColumnDefinition("TABLE_TYPE", Types.VARCHAR, "VARCHAR"),
                new ColumnDefinition("REMARKS", Types.VARCHAR, "VARCHAR"),
                new ColumnDefinition("TYPE_CAT", Types.VARCHAR, "VARCHAR"),
                new ColumnDefinition("TYPE_SCHEM", Types.VARCHAR, "VARCHAR"),
                new ColumnDefinition("TYPE_NAME", Types.VARCHAR, "VARCHAR"),
                new ColumnDefinition("SELF_REFERENCING_COL_NAME", Types.VARCHAR, "VARCHAR"),
                new ColumnDefinition("REF_GENERATION", Types.VARCHAR, "VARCHAR")
        );
        List<List<Object>> rows = new ArrayList<>();
        String targetDbName = catalog != null && !catalog.isEmpty() ? catalog : connection.getCatalog();
        MongoClient client = connection.getMongoClient();
        if (client != null && targetDbName != null && !targetDbName.isEmpty()) {
            try {
                MongoDatabase db = client.getDatabase(targetDbName);
                for (String collName : db.listCollectionNames()) {
                    if (tableNamePattern != null && !tableNamePattern.isEmpty() && !tableNamePattern.equals("%")) {
                        if (!matchesPattern(collName, tableNamePattern)) {
                            continue;
                        }
                    }
                    rows.add(Arrays.asList(
                            targetDbName,
                            null,
                            collName,
                            "TABLE",
                            "MongoDB Collection",
                            null,
                            null,
                            null,
                            null,
                            null
                    ));
                }
            } catch (Exception ignored) {
            }
        }
        return new MongoResultSet(cols, rows);
    }

    @Override
    public ResultSet getColumns(String catalog, String schemaPattern, String tableNamePattern, String columnNamePattern) throws SQLException {
        List<ColumnDefinition> cols = Arrays.asList(
                new ColumnDefinition("TABLE_CAT", Types.VARCHAR, "VARCHAR"),
                new ColumnDefinition("TABLE_SCHEM", Types.VARCHAR, "VARCHAR"),
                new ColumnDefinition("TABLE_NAME", Types.VARCHAR, "VARCHAR"),
                new ColumnDefinition("COLUMN_NAME", Types.VARCHAR, "VARCHAR"),
                new ColumnDefinition("DATA_TYPE", Types.INTEGER, "INTEGER"),
                new ColumnDefinition("TYPE_NAME", Types.VARCHAR, "VARCHAR"),
                new ColumnDefinition("COLUMN_SIZE", Types.INTEGER, "INTEGER"),
                new ColumnDefinition("BUFFER_LENGTH", Types.INTEGER, "INTEGER"),
                new ColumnDefinition("DECIMAL_DIGITS", Types.INTEGER, "INTEGER"),
                new ColumnDefinition("NUM_PREC_RADIX", Types.INTEGER, "INTEGER"),
                new ColumnDefinition("NULLABLE", Types.INTEGER, "INTEGER"),
                new ColumnDefinition("REMARKS", Types.VARCHAR, "VARCHAR"),
                new ColumnDefinition("COLUMN_DEF", Types.VARCHAR, "VARCHAR"),
                new ColumnDefinition("SQL_DATA_TYPE", Types.INTEGER, "INTEGER"),
                new ColumnDefinition("SQL_DATETIME_SUB", Types.INTEGER, "INTEGER"),
                new ColumnDefinition("CHAR_OCTET_LENGTH", Types.INTEGER, "INTEGER"),
                new ColumnDefinition("ORDINAL_POSITION", Types.INTEGER, "INTEGER"),
                new ColumnDefinition("IS_NULLABLE", Types.VARCHAR, "VARCHAR"),
                new ColumnDefinition("SCOPE_CATALOG", Types.VARCHAR, "VARCHAR"),
                new ColumnDefinition("SCOPE_SCHEMA", Types.VARCHAR, "VARCHAR"),
                new ColumnDefinition("SCOPE_TABLE", Types.VARCHAR, "VARCHAR"),
                new ColumnDefinition("SOURCE_DATA_TYPE", Types.SMALLINT, "SMALLINT"),
                new ColumnDefinition("IS_AUTOINCREMENT", Types.VARCHAR, "VARCHAR"),
                new ColumnDefinition("IS_GENERATEDCOLUMN", Types.VARCHAR, "VARCHAR")
        );
        List<List<Object>> rows = new ArrayList<>();
        String targetDbName = catalog != null && !catalog.isEmpty() ? catalog : connection.getCatalog();
        MongoClient client = connection.getMongoClient();

        if (client != null && targetDbName != null && !targetDbName.isEmpty()) {
            try {
                MongoDatabase db = client.getDatabase(targetDbName);
                List<String> targetCollections = new ArrayList<>();
                if (tableNamePattern != null && !tableNamePattern.isEmpty() && !tableNamePattern.equals("%")) {
                    targetCollections.add(tableNamePattern);
                } else {
                    for (String name : db.listCollectionNames()) {
                        targetCollections.add(name);
                    }
                }

                for (String collName : targetCollections) {
                    Set<String> seenFields = new HashSet<>();
                    List<ColumnInfo> fields = new ArrayList<>();

                    // Always add _id column first
                    seenFields.add("_id");
                    fields.add(new ColumnInfo("_id", Types.VARCHAR, "OBJECTID"));

                    // Sample documents to infer fields and types
                    for (Document doc : db.getCollection(collName).find().limit(50)) {
                        for (String key : doc.keySet()) {
                            if (!seenFields.contains(key)) {
                                seenFields.add(key);
                                Object val = doc.get(key);
                                int sqlType = inferSqlType(val);
                                String typeName = inferTypeName(val);
                                fields.add(new ColumnInfo(key, sqlType, typeName));
                            }
                        }
                    }

                    int ordinal = 1;
                    for (ColumnInfo info : fields) {
                        if (columnNamePattern != null && !columnNamePattern.isEmpty() && !columnNamePattern.equals("%")) {
                            if (!matchesPattern(info.name, columnNamePattern)) {
                                continue;
                            }
                        }
                        rows.add(Arrays.asList(
                                targetDbName,
                                null,
                                collName,
                                info.name,
                                info.sqlType,
                                info.typeName,
                                255,
                                0,
                                0,
                                10,
                                DatabaseMetaData.columnNullable,
                                null,
                                null,
                                info.sqlType,
                                0,
                                255,
                                ordinal++,
                                "YES",
                                null,
                                null,
                                null,
                                (short) 0,
                                "NO",
                                "NO"
                        ));
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return new MongoResultSet(cols, rows);
    }

    @Override
    public ResultSet getTableTypes() throws SQLException {
        List<ColumnDefinition> cols = Collections.singletonList(
                new ColumnDefinition("TABLE_TYPE", Types.VARCHAR, "VARCHAR")
        );
        List<List<Object>> rows = Arrays.asList(
                Collections.singletonList("TABLE"),
                Collections.singletonList("VIEW"),
                Collections.singletonList("COLLECTION")
        );
        return new MongoResultSet(cols, rows);
    }

    private static class ColumnInfo {
        final String name;
        final int sqlType;
        final String typeName;

        ColumnInfo(String name, int sqlType, String typeName) {
            this.name = name;
            this.sqlType = sqlType;
            this.typeName = typeName;
        }
    }

    private static int inferSqlType(Object value) {
        if (value == null) return Types.VARCHAR;
        if (value instanceof String) return Types.VARCHAR;
        if (value instanceof Integer) return Types.INTEGER;
        if (value instanceof Long) return Types.BIGINT;
        if (value instanceof Double || value instanceof Float) return Types.DOUBLE;
        if (value instanceof Boolean) return Types.BOOLEAN;
        if (value instanceof java.util.Date) return Types.TIMESTAMP;
        if (value instanceof byte[]) return Types.VARBINARY;
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

    private static boolean matchesPattern(String text, String pattern) {
        if (pattern == null || pattern.equals("%")) return true;
        String regex = pattern.replace(".", "\\.").replace("%", ".*").replace("_", ".");
        return text.matches("(?i)" + regex);
    }

    // Default standard metadata capabilities
    @Override public boolean allProceduresAreCallable() { return true; }
    @Override public boolean allTablesAreSelectable() { return true; }
    @Override public String getURL() { return connection.getUrl(); }
    @Override public String getUserName() { return ""; }
    @Override public boolean isReadOnly() { return false; }
    @Override public boolean nullsAreSortedHigh() { return false; }
    @Override public boolean nullsAreSortedLow() { return true; }
    @Override public boolean nullsAreSortedAtStart() { return false; }
    @Override public boolean nullsAreSortedAtEnd() { return false; }
    @Override public boolean usesLocalFiles() { return false; }
    @Override public boolean usesLocalFilePerTable() { return false; }
    @Override public boolean supportsMixedCaseIdentifiers() { return true; }
    @Override public boolean storesUpperCaseIdentifiers() { return false; }
    @Override public boolean storesLowerCaseIdentifiers() { return false; }
    @Override public boolean storesMixedCaseIdentifiers() { return true; }
    @Override public boolean supportsMixedCaseQuotedIdentifiers() { return true; }
    @Override public boolean storesUpperCaseQuotedIdentifiers() { return false; }
    @Override public boolean storesLowerCaseQuotedIdentifiers() { return false; }
    @Override public boolean storesMixedCaseQuotedIdentifiers() { return true; }
    @Override public String getIdentifierQuoteString() { return "\""; }
    @Override public String getSQLKeywords() { return ""; }
    @Override public String getNumericFunctions() { return ""; }
    @Override public String getStringFunctions() { return ""; }
    @Override public String getSystemFunctions() { return ""; }
    @Override public String getTimeDateFunctions() { return ""; }
    @Override public String getSearchStringEscape() { return "\\"; }
    @Override public String getExtraNameCharacters() { return ""; }
    @Override public boolean supportsAlterTableWithAddColumn() { return false; }
    @Override public boolean supportsAlterTableWithDropColumn() { return false; }
    @Override public boolean supportsColumnAliasing() { return true; }
    @Override public boolean nullPlusNonNullIsNull() { return true; }
    @Override public boolean supportsConvert() { return false; }
    @Override public boolean supportsConvert(int fromType, int toType) { return false; }
    @Override public boolean supportsTableCorrelationNames() { return true; }
    @Override public boolean supportsDifferentTableCorrelationNames() { return false; }
    @Override public boolean supportsExpressionsInOrderBy() { return true; }
    @Override public boolean supportsOrderByUnrelated() { return true; }
    @Override public boolean supportsGroupBy() { return true; }
    @Override public boolean supportsGroupByUnrelated() { return true; }
    @Override public boolean supportsGroupByBeyondSelect() { return true; }
    @Override public boolean supportsLikeEscapeClause() { return true; }
    @Override public boolean supportsMultipleResultSets() { return false; }
    @Override public boolean supportsMultipleTransactions() { return false; }
    @Override public boolean supportsNonNullableColumns() { return true; }
    @Override public boolean supportsMinimumSQLGrammar() { return true; }
    @Override public boolean supportsCoreSQLGrammar() { return true; }
    @Override public boolean supportsExtendedSQLGrammar() { return false; }
    @Override public boolean supportsANSI92EntryLevelSQL() { return true; }
    @Override public boolean supportsANSI92IntermediateSQL() { return false; }
    @Override public boolean supportsANSI92FullSQL() { return false; }
    @Override public boolean supportsIntegrityEnhancementFacility() { return false; }
    @Override public boolean supportsOuterJoins() { return true; }
    @Override public boolean supportsFullOuterJoins() { return false; }
    @Override public boolean supportsLimitedOuterJoins() { return true; }
    @Override public String getSchemaTerm() { return "schema"; }
    @Override public String getProcedureTerm() { return "procedure"; }
    @Override public String getCatalogTerm() { return "database"; }
    @Override public boolean isCatalogAtStart() { return true; }
    @Override public String getCatalogSeparator() { return "."; }
    @Override public boolean supportsSchemasInDataManipulation() { return false; }
    @Override public boolean supportsSchemasInProcedureCalls() { return false; }
    @Override public boolean supportsSchemasInTableDefinitions() { return false; }
    @Override public boolean supportsSchemasInIndexDefinitions() { return false; }
    @Override public boolean supportsSchemasInPrivilegeDefinitions() { return false; }
    @Override public boolean supportsCatalogsInDataManipulation() { return true; }
    @Override public boolean supportsCatalogsInProcedureCalls() { return false; }
    @Override public boolean supportsCatalogsInTableDefinitions() { return true; }
    @Override public boolean supportsCatalogsInIndexDefinitions() { return true; }
    @Override public boolean supportsCatalogsInPrivilegeDefinitions() { return false; }
    @Override public boolean supportsPositionedDelete() { return false; }
    @Override public boolean supportsPositionedUpdate() { return false; }
    @Override public boolean supportsSelectForUpdate() { return false; }
    @Override public boolean supportsStoredProcedures() { return false; }
    @Override public boolean supportsSubqueriesInComparisons() { return false; }
    @Override public boolean supportsSubqueriesInExists() { return false; }
    @Override public boolean supportsSubqueriesInIns() { return false; }
    @Override public boolean supportsSubqueriesInQuantifieds() { return false; }
    @Override public boolean supportsCorrelatedSubqueries() { return false; }
    @Override public boolean supportsUnion() { return true; }
    @Override public boolean supportsUnionAll() { return true; }
    @Override public boolean supportsOpenCursorsAcrossCommit() { return false; }
    @Override public boolean supportsOpenCursorsAcrossRollback() { return false; }
    @Override public boolean supportsOpenStatementsAcrossCommit() { return false; }
    @Override public boolean supportsOpenStatementsAcrossRollback() { return false; }
    @Override public int getMaxBinaryLiteralLength() { return 0; }
    @Override public int getMaxCharLiteralLength() { return 0; }
    @Override public int getMaxColumnNameLength() { return 128; }
    @Override public int getMaxColumnsInGroupBy() { return 0; }
    @Override public int getMaxColumnsInIndex() { return 0; }
    @Override public int getMaxColumnsInOrderBy() { return 0; }
    @Override public int getMaxColumnsInSelect() { return 0; }
    @Override public int getMaxColumnsInTable() { return 0; }
    @Override public int getMaxConnections() { return 0; }
    @Override public int getMaxCursorNameLength() { return 0; }
    @Override public int getMaxIndexLength() { return 0; }
    @Override public int getMaxSchemaNameLength() { return 0; }
    @Override public int getMaxProcedureNameLength() { return 0; }
    @Override public int getMaxCatalogNameLength() { return 128; }
    @Override public int getMaxRowSize() { return 16777216; } // 16MB BSON limit
    @Override public boolean doesMaxRowSizeIncludeBlobs() { return true; }
    @Override public int getMaxStatementLength() { return 0; }
    @Override public int getMaxStatements() { return 0; }
    @Override public int getMaxTableNameLength() { return 128; }
    @Override public int getMaxTablesInSelect() { return 0; }
    @Override public int getMaxUserNameLength() { return 128; }
    @Override public int getDefaultTransactionIsolation() { return Connection.TRANSACTION_NONE; }
    @Override public boolean supportsTransactions() { return false; }
    @Override public boolean supportsTransactionIsolationLevel(int level) { return level == Connection.TRANSACTION_NONE; }
    @Override public boolean supportsDataDefinitionAndDataManipulationTransactions() { return false; }
    @Override public boolean supportsDataManipulationTransactionsOnly() { return false; }
    @Override public boolean dataDefinitionCausesTransactionCommit() { return false; }
    @Override public boolean dataDefinitionIgnoredInTransactions() { return false; }

    @Override public ResultSet getProcedures(String catalog, String schemaPattern, String procedureNamePattern) { return emptyResultSet(); }
    @Override public ResultSet getProcedureColumns(String catalog, String schemaPattern, String procedureNamePattern, String columnNamePattern) { return emptyResultSet(); }
    @Override public ResultSet getColumnPrivileges(String catalog, String schema, String table, String columnNamePattern) { return emptyResultSet(); }
    @Override public ResultSet getTablePrivileges(String catalog, String schemaPattern, String tableNamePattern) { return emptyResultSet(); }
    @Override public ResultSet getBestRowIdentifier(String catalog, String schema, String table, int scope, boolean nullable) { return emptyResultSet(); }
    @Override public ResultSet getVersionColumns(String catalog, String schema, String table) { return emptyResultSet(); }
    @Override public ResultSet getPrimaryKeys(String catalog, String schema, String table) {
        List<ColumnDefinition> cols = Arrays.asList(
                new ColumnDefinition("TABLE_CAT", Types.VARCHAR, "VARCHAR"),
                new ColumnDefinition("TABLE_SCHEM", Types.VARCHAR, "VARCHAR"),
                new ColumnDefinition("TABLE_NAME", Types.VARCHAR, "VARCHAR"),
                new ColumnDefinition("COLUMN_NAME", Types.VARCHAR, "VARCHAR"),
                new ColumnDefinition("KEY_SEQ", Types.SMALLINT, "SMALLINT"),
                new ColumnDefinition("PK_NAME", Types.VARCHAR, "VARCHAR")
        );
        List<List<Object>> rows = Collections.singletonList(
                Arrays.asList(catalog, null, table, "_id", (short) 1, "PRIMARY")
        );
        return new MongoResultSet(cols, rows);
    }
    @Override public ResultSet getImportedKeys(String catalog, String schema, String table) { return emptyResultSet(); }
    @Override public ResultSet getExportedKeys(String catalog, String schema, String table) { return emptyResultSet(); }
    @Override public ResultSet getCrossReference(String parentCatalog, String parentSchema, String parentTable, String foreignCatalog, String foreignSchema, String foreignTable) { return emptyResultSet(); }
    @Override public ResultSet getTypeInfo() { return emptyResultSet(); }
    @Override public ResultSet getIndexInfo(String catalog, String schema, String table, boolean unique, boolean approximate) { return emptyResultSet(); }
    @Override public boolean supportsResultSetType(int type) { return type == ResultSet.TYPE_FORWARD_ONLY; }
    @Override public boolean supportsResultSetConcurrency(int type, int concurrency) { return concurrency == ResultSet.CONCUR_READ_ONLY; }
    @Override public boolean ownUpdatesAreVisible(int type) { return false; }
    @Override public boolean ownDeletesAreVisible(int type) { return false; }
    @Override public boolean ownInsertsAreVisible(int type) { return false; }
    @Override public boolean othersUpdatesAreVisible(int type) { return false; }
    @Override public boolean othersDeletesAreVisible(int type) { return false; }
    @Override public boolean othersInsertsAreVisible(int type) { return false; }
    @Override public boolean updatesAreDetected(int type) { return false; }
    @Override public boolean deletesAreDetected(int type) { return false; }
    @Override public boolean insertsAreDetected(int type) { return false; }
    @Override public boolean supportsBatchUpdates() { return false; }
    @Override public ResultSet getUDTs(String catalog, String schemaPattern, String typeNamePattern, int[] types) { return emptyResultSet(); }
    @Override public boolean supportsSavepoints() { return false; }
    @Override public boolean supportsNamedParameters() { return false; }
    @Override public boolean supportsMultipleOpenResults() { return false; }
    @Override public boolean supportsGetGeneratedKeys() { return false; }
    @Override public ResultSet getSuperTypes(String catalog, String schemaPattern, String typeNamePattern) { return emptyResultSet(); }
    @Override public ResultSet getSuperTables(String catalog, String schemaPattern, String tableNamePattern) { return emptyResultSet(); }
    @Override public ResultSet getAttributes(String catalog, String schemaPattern, String typeNamePattern, String attributeNamePattern) { return emptyResultSet(); }
    @Override public boolean supportsResultSetHoldability(int holdability) { return false; }
    @Override public int getResultSetHoldability() { return ResultSet.CLOSE_CURSORS_AT_COMMIT; }
    @Override public int getDatabaseMajorVersion() { return 6; }
    @Override public int getDatabaseMinorVersion() { return 0; }
    @Override public int getJDBCMajorVersion() { return 4; }
    @Override public int getJDBCMinorVersion() { return 2; }
    @Override public int getSQLStateType() { return DatabaseMetaData.sqlStateSQL; }
    @Override public boolean locatorsUpdateCopy() { return false; }
    @Override public boolean supportsStatementPooling() { return false; }
    @Override public RowIdLifetime getRowIdLifetime() { return RowIdLifetime.ROWID_UNSUPPORTED; }
    @Override public boolean supportsStoredFunctionsUsingCallSyntax() { return false; }
    @Override public boolean autoCommitFailureClosesAllResultSets() { return false; }
    @Override public ResultSet getClientInfoProperties() { return emptyResultSet(); }
    @Override public ResultSet getFunctions(String catalog, String schemaPattern, String functionNamePattern) { return emptyResultSet(); }
    @Override public ResultSet getFunctionColumns(String catalog, String schemaPattern, String functionNamePattern, String columnNamePattern) { return emptyResultSet(); }
    @Override public ResultSet getPseudoColumns(String catalog, String schemaPattern, String tableNamePattern, String columnNamePattern) { return emptyResultSet(); }
    @Override public boolean generatedKeyAlwaysReturned() { return false; }

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

    private static ResultSet emptyResultSet() {
        return new MongoResultSet(Collections.emptyList(), Collections.emptyList());
    }
}
