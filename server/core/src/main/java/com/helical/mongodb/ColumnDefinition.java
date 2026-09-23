package com.helical.mongodb;

public class ColumnDefinition {
    private final String name;
    private final int sqlType;
    private final String typeName;
    private final int columnDisplaySize;

    public ColumnDefinition(String name, int sqlType, String typeName, int columnDisplaySize) {
        this.name = name;
        this.sqlType = sqlType;
        this.typeName = typeName;
        this.columnDisplaySize = columnDisplaySize;
    }

    public ColumnDefinition(String name, int sqlType, String typeName) {
        this(name, sqlType, typeName, 255);
    }

    public String getName() {
        return name;
    }

    public int getSqlType() {
        return sqlType;
    }

    public String getTypeName() {
        return typeName;
    }

    public int getColumnDisplaySize() {
        return columnDisplaySize;
    }
}
