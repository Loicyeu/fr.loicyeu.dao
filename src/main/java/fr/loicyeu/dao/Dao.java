package fr.loicyeu.dao;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Dao<E> {

    private final Connection connection;
    private final Class<E> clazz;

    private final String tableName;
    private final Map<Field, DaoField> daoFieldMap;
    private final Map<String, FieldType> fieldTypeMap;
    private final List<String> primaryKeys;

    /**
     * Constructeur permettant de fabriquer un Dao pour la classe fournie.
     *
     * @param connection La connexion SQL.
     * @param e          Le type du DAO.
     */
    public Dao(Connection connection, Class<E> e) throws IllegalArgumentException {
        if (!e.isAnnotationPresent(DaoTable.class)) {
            throw new IllegalArgumentException("La classe '" + e.getSimpleName() + "' ne porte pas l'annotation" +
                    "'DaoTable' et ne peut donc pas être utilisé par le DAO.");
        }
        this.connection = connection;
        this.clazz = e;
        this.tableName = clazz.getSimpleName();
        this.daoFieldMap = getAnnotatedFields(this.clazz);
        this.fieldTypeMap = getFieldsDetails();
        this.primaryKeys = getPrimaryKeys();
    }

    /**
     * Permet de créer la table dans la base de donnée.
     * Si l'option {@code force} est activé, force sa suppression puis sa re-création.
     *
     * @param force Permet de forcer la re-création de la table.
     * @return Vrai si la table a été créer, faux sinon.
     */
    public boolean createTable(boolean force) {
        if (force) {
            dropTable();
        }
        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("CREATE TABLE IF NOT EXISTS ").append(this.tableName).append(" (\n");
        fieldTypeMap.forEach((name, fieldType) ->
                sqlBuilder.append("\t").append(name).append(" ").append(fieldType.getSQL()).append(",\n"));

        if (!primaryKeys.isEmpty()) {
            sqlBuilder.append("\tPRIMARY KEY (");
            primaryKeys.forEach(pk -> sqlBuilder.append(pk).append(","));
            sqlBuilder.deleteCharAt(sqlBuilder.lastIndexOf(",")).append("),\n");
        }
        sqlBuilder.deleteCharAt(sqlBuilder.lastIndexOf(",")).append(")");

        System.out.println(sqlBuilder.toString());

        try (PreparedStatement statement = connection.prepareStatement(sqlBuilder.toString())) {
            System.out.println("CREATE TABLE : " + statement.executeUpdate());
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Permet de supprimer la table de la base de donnée.
     *
     * @return Vrai si la table a bien été supprimé, faux sinon.
     */
    public boolean dropTable() {
        try (PreparedStatement statement = connection.prepareStatement("DROP TABLE " + tableName)) {
            statement.executeUpdate();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean insert(E e) {
        Map<String, Object> fieldsValues = getFieldsValues(e);
        StringBuilder sqlBuilder = new StringBuilder();
        List<Object> valuesList = new ArrayList<>();

        sqlBuilder.append("INSERT INTO ").append(tableName).append(" (");
        fieldsValues.forEach((field, value) -> {
            sqlBuilder.append(field).append(",");
            valuesList.add(value);
        });
        sqlBuilder.deleteCharAt(sqlBuilder.lastIndexOf(",")).append(") VALUES (");
        sqlBuilder.append("?,".repeat(valuesList.size())).deleteCharAt(sqlBuilder.lastIndexOf(","));
        sqlBuilder.append(")");

        try (PreparedStatement statement = connection.prepareStatement(sqlBuilder.toString())) {
            for (int i = 0; i < valuesList.size(); i++) {
                statement.setObject(i + 1, valuesList.get(i));
            }
            statement.executeUpdate();
            return true;
        } catch (SQLException err) {
            err.printStackTrace();
            return false;
        }
    }

    public List<E> findAll() throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM " + tableName)) {
            ResultSet resultSet = statement.executeQuery();
            List<E> eList = new ArrayList<>();
            while (resultSet.next()) {
                E e = createInstance(resultSet);
                if (e != null) {
                    eList.add(e);
                }
            }
            return eList;
        } catch (SQLException err) {
            err.printStackTrace();
            return null;
        }
    }


    private Map<Field, DaoField> getAnnotatedFields(Class<?> clazz) {
        Map<Field, DaoField> daoFieldMap = new HashMap<>();
        for (Field field : clazz.getDeclaredFields()) {
            if (field.isAnnotationPresent(DaoField.class)) {
                daoFieldMap.put(field, field.getAnnotation(DaoField.class));
            }
        }
        return daoFieldMap;
    }

    private Map<String, FieldType> getFieldsDetails() {
        Map<String, FieldType> map = new HashMap<>();
        for (Field field : this.daoFieldMap.keySet()) {
            if (field.isAnnotationPresent(DaoField.class)) {
                DaoField daoField = field.getAnnotation(DaoField.class);
                map.put(daoField.name(), daoField.type());
            }
        }
        return map;
    }

    private List<String> getPrimaryKeys() {
        List<String> primaryKeys = new ArrayList<>();
        for (Field field : this.daoFieldMap.keySet()) {
            if (field.isAnnotationPresent(DaoField.class) && field.isAnnotationPresent(PrimaryKey.class)) {
                DaoField daoField = field.getAnnotation(DaoField.class);
                primaryKeys.add(daoField.name());
            }
        }
        return primaryKeys;
    }

    private Map<String, Object> getFieldsValues(E e) {
        Map<String, Object> fieldsValues = new HashMap<>();
        daoFieldMap.forEach((field, daoField) -> {
            field.setAccessible(true);
            try {
                fieldsValues.put(daoField.name(), field.get(e));
            } catch (IllegalAccessException err) {
                err.printStackTrace();
            }
        });
        return fieldsValues;
    }

    private E createInstance(ResultSet resultSet) {
        try {
            E e = clazz.getDeclaredConstructor().newInstance();
            for (Field field : daoFieldMap.keySet()) {
                field.setAccessible(true);
                field.set(e, resultSet.getObject(daoFieldMap.get(field).name()));
            }
            return e;
        } catch (Exception err) {
            err.printStackTrace();
            return null;
        }
    }

}
