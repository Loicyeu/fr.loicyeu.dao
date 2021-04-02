package fr.loicyeu.dao;

public enum FieldType {

    BOOLEAN("BOOLEAN"),
    INT("INT"),
    FLOAT("FLOAT"),
    CHAR("CHAR(1)"),
    VARCHAR("VARCHAR(255)"),
    LONG_VARCHAR("VARCHAR(1023)");

    private final String sql;

    FieldType(String sql) {
        this.sql = sql;
    }

    public String getSQL() {
        return sql;
    }
}
