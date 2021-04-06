package fr.loicyeu.dao;

/**
 * Représente un champ dans la base de donnée. <br>
 * Comporte le nom du champ ainsi que sa valeur.
 *
 * @author Loïc HENRY
 * @author https://github.com/Loicyeu
 * @since 1.0
 */
public final class FieldData {
    private final String fieldName;
    private final Object fieldValue;

    /**
     * @param fieldName  Le nom du champ a représenter.
     * @param fieldValue La valeur du champ à représenter.
     */
    public FieldData(String fieldName, Object fieldValue) {
        this.fieldName = fieldName;
        this.fieldValue = fieldValue;
    }

    /**
     * Permet de récupérer le nom du champ.
     *
     * @return Le nom du champ.
     */
    public String getFieldName() {
        return fieldName;
    }

    /**
     * Permet de récupérer la valeur du champ.
     *
     * @return La valeur du champ.
     */
    public Object getFieldValue() {
        return fieldValue;
    }
}