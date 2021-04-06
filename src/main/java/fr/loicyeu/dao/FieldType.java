package fr.loicyeu.dao;

/**
 * Représente les types SQL d'une base de donnée.
 *
 * @author Loïc HENRY
 * @author https://github.com/Loicyeu
 * @since 1.0
 */
public enum FieldType {

    /**
     * Type {@code boolean}.
     */
    BOOLEAN("BOOLEAN"),
    /**
     * Type {@code int}
     */
    INT("INT"),
    /**
     * Type {@code float}
     */
    FLOAT("FLOAT"),
    /**
     * Type {@code double}
     */
    DOUBLE("DOUBLE"),
    /**
     * Type {@code char}
     */
    CHAR("INT"),
    /**
     * Type {@code String} de moins de 255 caractères
     */
    VARCHAR("VARCHAR(255)"),
    /**
     * Type {@code String} de moins de 1023 caractères
     */
    LONG_VARCHAR("VARCHAR(1023)");

    private final String sql;

    FieldType(String sql) {
        this.sql = sql;
    }

    /**
     * Permet de récupérer la version SQL du type.
     *
     * @return La version SQL du type.
     */
    public String getSQL() {
        return sql;
    }
}
