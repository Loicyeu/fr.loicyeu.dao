package fr.loicyeu.dao;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
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
    private final Constructor<E> constructor;
    private final boolean fetchSuperFields;

    private final String tableName;
    private final Map<Field, DaoField> daoFieldMap;
    private final Map<String, FieldType> fieldTypeMap;
    private final List<String> primaryKeys;

    /**
     * Constructeur permettant de fabriquer un Dao pour la classe fournie.
     *
     * @param connection La connexion SQL.
     * @param e          Le type du DAO.
     * @throws IllegalArgumentException Si la classe ne possède pas l'annotation {@link DaoTable}, ou ne comporte pas
     *                                  de constructeur vide, ou alors est abstraite ou est une interface.
     */
    public Dao(Connection connection, Class<E> e) throws IllegalArgumentException {
        this.constructor = isValideClass(e);
        DaoTable daoTable = e.getAnnotation(DaoTable.class);
        this.tableName = daoTable.tableName();
        this.fetchSuperFields = daoTable.fetchSuperFields();

        this.connection = connection;
        this.clazz = e;
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


    /**
     * Méthode permettant de vérifier que la classe fournie soit bien conforme aux exigences.
     *
     * @param clazz La classe a vérifier.
     * @return Le constructeur vide de la classe dans le cas ou elle serait valide.
     * @throws IllegalArgumentException Si la classe ne possède pas l'annotation {@link DaoTable}, ou ne comporte pas
     *                                  de constructeur vide, ou alors est abstraite ou est une interface.
     */
    private Constructor<E> isValideClass(Class<E> clazz) throws IllegalArgumentException {
        if (!clazz.isAnnotationPresent(DaoTable.class)) {
            throw new IllegalArgumentException("La classe '" + clazz.getSimpleName() + "' ne porte pas l'annotation" +
                    "'DaoTable' et ne peut donc pas être utilisé par le DAO.");
        }

        if (Modifier.isAbstract(clazz.getModifiers()) || Modifier.isInterface(clazz.getModifiers())) {
            throw new IllegalArgumentException("La classe '" + clazz.getSimpleName() + "'" +
                    "est abstraite ou est une interface et ne peut donc pas être utilisé par le DAO.");
        }

        try {
            return clazz.getDeclaredConstructor();
        } catch (NoSuchMethodException err) {
            throw new IllegalArgumentException("La classe '" + clazz.getSimpleName() +
                    "' ne comporte pas de constructeur vide et ne peut donc pas être utilisé par le DAO.");
        }
    }

    private Map<Field, DaoField> getAnnotatedFields(Class<?> clazz) {
        Map<Field, DaoField> daoFieldMap = new HashMap<>();
        for (Field field : clazz.getDeclaredFields()) {
            if (field.isAnnotationPresent(DaoField.class)) {
                daoFieldMap.put(field, field.getAnnotation(DaoField.class));
            }
        }
        if (fetchSuperFields) {
            Class<?> cl = clazz.getSuperclass();
            while (cl != null && cl.isAnnotationPresent(DaoSuper.class)) {
                for (Field field : cl.getDeclaredFields()) {
                    if (field.isAnnotationPresent(DaoField.class)) {
                        daoFieldMap.put(field, field.getAnnotation(DaoField.class));
                    }
                }
                cl = cl.getSuperclass();
            }
        }
        return daoFieldMap;
    }

    private Map<String, FieldType> getFieldsDetails() {
        Map<String, FieldType> map = new HashMap<>();
        this.daoFieldMap.forEach((field, daoField) -> map.put(daoField.name(), daoField.type()));
        return map;
    }

    private List<String> getPrimaryKeys() {
        List<String> primaryKeys = new ArrayList<>();
        this.daoFieldMap.forEach((field, daoField) -> {
            if (field.isAnnotationPresent(PrimaryKey.class)) {
                primaryKeys.add(daoField.name());
            }
        });
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
            constructor.setAccessible(true);
            E e = constructor.newInstance();
            for (Field field : daoFieldMap.keySet()) {
                field.setAccessible(true);
                field.set(e, getValueFromResultSet(resultSet, daoFieldMap.get(field).name()));
            }
            return e;
        } catch (Exception err) {
            err.printStackTrace();
            return null;
        }
    }

    private Object getValueFromResultSet(ResultSet resultSet, String name) throws SQLException {
        try {
            return switch (fieldTypeMap.get(name)) {
                case BOOLEAN -> resultSet.getBoolean(name);
                case INT -> resultSet.getInt(name);
                case FLOAT -> resultSet.getFloat(name);
                case DOUBLE -> resultSet.getDouble(name);
                case CHAR, VARCHAR, LONG_VARCHAR -> resultSet.getString(name);
            };
        }catch (SQLException ignored) {
            return resultSet.getObject(name);
        }
    }

}
