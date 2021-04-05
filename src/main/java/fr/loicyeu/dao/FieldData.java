package fr.loicyeu.dao;

/**
 * Représente un champ dans la base de donnée. <br>
 * Comporte le nom du champ ainsi que sa valeur.
 *
 * @author Loïc HENRY
 * @author https://github.com/Loicyeu
 * @version 1.0
 * @since 1.0
 */
public final class FieldData {
    private final String fieldName;
    private final Object fieldValue;

    public FieldData(String fieldName, Object fieldValue) {
        this.fieldName = fieldName;
        this.fieldValue = fieldValue;
    }

    public String getFieldName() {
        return fieldName;
    }

    public Object getFieldValue() {
        return fieldValue;
    }
}