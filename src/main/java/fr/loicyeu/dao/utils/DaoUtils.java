package fr.loicyeu.dao.utils;

import fr.loicyeu.dao.FieldType;
import fr.loicyeu.dao.annotations.DaoField;
import fr.loicyeu.dao.annotations.DaoSuper;
import fr.loicyeu.dao.annotations.DaoTable;
import fr.loicyeu.dao.annotations.PrimaryKey;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Classe util de {@link fr.loicyeu.dao.Dao}.
 *
 * @author Loïc HENRY
 * @author https://github.com/Loicyeu
 * @since 1.0
 */
public class DaoUtils {
    private DaoUtils() {

    }

    /**
     * Méthode permettant de vérifier que la classe fournie soit bien conforme aux exigences.
     *
     * @param clazz La classe a vérifier.
     * @param <T>   La classe du DAO.
     * @return Le constructeur vide de la classe dans le cas ou elle serait valide.
     * @throws IllegalArgumentException Si la classe ne possède pas l'annotation {@link DaoTable}, ou ne comporte pas
     *                                  de constructeur vide, ou alors est abstraite ou est une interface.
     */
    public static <T> Constructor<T> isValideClass(Class<T> clazz) throws IllegalArgumentException {
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

    public static <T> Map<String, Object> getFieldsValues(Map<Field, DaoField> fieldMap, T t) {
        Map<String, Object> fieldsValues = new HashMap<>();
        fieldMap.forEach((field, daoField) -> {
            field.setAccessible(true);
            try {
                fieldsValues.put(daoField.name(), field.get(t));
            } catch (IllegalAccessException err) {
                err.printStackTrace();
            }
        });
        return fieldsValues;
    }

    public static Map<Field, DaoField> getAnnotatedFields(Class<?> clazz, boolean fetchSuperFields) {
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

    public static Map<String, Field> getPK(Map<Field, DaoField> daoFieldMap) {
        Map<String, Field> primaryKeys = new HashMap<>();
        daoFieldMap.forEach((field, daoField) -> {
            if (field.isAnnotationPresent(PrimaryKey.class)) {
                primaryKeys.put(daoField.name(), field);
            }
        });
        return primaryKeys;
    }

    public static Map<String, FieldType> getFieldsDetails(Map<Field, DaoField> daoFieldMap) {
        Map<String, FieldType> map = new HashMap<>();
        daoFieldMap.forEach((field, daoField) -> map.put(daoField.name(), daoField.type()));
        return map;
    }

    public static Map<Field, DaoField> getPkDaoField(Map<String, Field> primaryKeys, Map<Field, DaoField> daoFieldMap) {
        Map<Field, DaoField> pkDaoFieldMap = new HashMap<>();
        primaryKeys.forEach((s, field) -> pkDaoFieldMap.put(field, daoFieldMap.get(field)));
        return pkDaoFieldMap;
    }

    public static boolean executeUpdate(Connection connection, String sql, List<Object> valuesList) {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (valuesList != null) {
                for (int i = 0; i < valuesList.size(); i++) {
                    statement.setObject(i + 1, valuesList.get(i));
                }
            }
            statement.executeUpdate();
            return true;
        } catch (SQLException err) {
            err.printStackTrace();
            return false;
        }
    }

}
