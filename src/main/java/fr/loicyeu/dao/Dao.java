package fr.loicyeu.dao;

import fr.loicyeu.dao.annotations.DaoField;
import fr.loicyeu.dao.annotations.DaoSuper;
import fr.loicyeu.dao.annotations.DaoTable;
import fr.loicyeu.dao.annotations.PrimaryKey;
import fr.loicyeu.dao.exceptions.DaoException;
import fr.loicyeu.dao.exceptions.MissingPrimaryKeyException;
import fr.loicyeu.dao.exceptions.NoPrimaryKeyException;
import fr.loicyeu.dao.exceptions.WrongPrimaryKeyException;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Représente un DAO générique permettant la gestion d'une base de données pour une classe donnée.<br>
 * La classe du DAO ne doit pas être abstraite ou être une interface.
 * Elle doit également être annoter par {@link DaoTable}
 * et posséder une constructeur ne pennant aucun paramètre (peut importe l'encapsulation).
 *
 * @param <E> La classe du DAO.
 * @author Loïc HENRY
 * @author https://github.com/Loicyeu
 * @version 1.0
 * @since 1.0
 */
public final class Dao<E> {

    protected static final List<String> tablesName = new LinkedList<>();

    private final Connection connection;
    private final Constructor<E> constructor;
    private final boolean fetchSuperFields;

    private final String tableName;
    private final Map<Field, DaoField> daoFieldMap;
    private final Map<String, FieldType> fieldTypeMap;
    private final Map<String, Field> primaryKeys;
    private final Map<String, Dao<?>> hasManyList;
    private final Map<String, Dao<?>> hasOne;

    /**
     * Constructeur permettant de fabriquer un Dao pour la classe fournie.
     *
     * @param connection La connexion SQL.
     * @param e          Le type du DAO.
     * @throws IllegalArgumentException Si la classe ne possède pas l'annotation {@link DaoTable}, ou ne comporte pas
     *                                  de constructeur vide, ou alors est abstraite ou est une interface.
     */
    public Dao(Connection connection, Class<E> e) throws IllegalArgumentException {
        this.connection = connection;
        this.constructor = isValideClass(e);
        DaoTable daoTable = e.getAnnotation(DaoTable.class);
        this.tableName = daoTable.tableName();
        this.fetchSuperFields = daoTable.fetchSuperFields();
        this.daoFieldMap = getAnnotatedFields(e);
        this.fieldTypeMap = getFieldsDetails();
        this.primaryKeys = getPrimaryKeys();

        this.hasManyList = new HashMap<>();
        this.hasOne = new HashMap<>();

        tablesName.add(tableName);
    }

    /**
     * Permet d'associer la classe du DAO a une classe par la relation 1-1.
     *
     * @param otherDao     L'autre DAO à associé.
     * @param relationName Le nom qui sera donnée à la relation.
     * @throws NoPrimaryKeyException    Si l'un des deux DAO ne contient pas de clé primaires.
     * @throws IllegalArgumentException Si le nom de la relation est déjà utilisé pour ce DAO.
     */
    public void hasOne(Dao<?> otherDao, String relationName) {
        verifyRelation(otherDao, relationName);
        tablesName.add(relationName);
        this.hasOne.put(relationName, otherDao);
    }

    /**
     * Permet d'associer la classe du DAO a une classe par la relation 1-N.
     *
     * @param otherDao     L'autre DAO à associé.
     * @param relationName Le nom qui sera donnée à la relation.
     * @throws NoPrimaryKeyException    Si l'un des deux DAO ne contient pas de clé primaires.
     * @throws IllegalArgumentException Si le nom de la relation est déjà utilisé pour ce DAO.
     */
    public void hasMany(Dao<?> otherDao, String relationName) {
        verifyRelation(otherDao, relationName);
        tablesName.add(relationName);
        this.hasManyList.put(relationName, otherDao);
    }


    /**
     * Permet de créer la table dans la base de donnée, ainsi que toutes les tables des relations.
     * Si l'option {@code force} est activé, force sa suppression puis sa re-création.
     *
     * @param force Permet de forcer la re-création de la table.
     * @return Vrai si la table ainsi que les tables des relations ont été créées sans problème, faux sinon.
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
            primaryKeys.keySet().forEach(pk -> sqlBuilder.append(pk).append(","));
            sqlBuilder.deleteCharAt(sqlBuilder.lastIndexOf(",")).append("),\n");
        }
        sqlBuilder.deleteCharAt(sqlBuilder.lastIndexOf(",")).append(")");

        try (PreparedStatement statement = connection.prepareStatement(sqlBuilder.toString())) {
            statement.executeUpdate();
            return createHasOneTables() && createHasManyTables();
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

    /**
     * Permet d'ajouter un objet dans la base de données.
     *
     * @param e L'objet à ajouter à la base de données.
     * @return Vrai si l'objet a bien été ajouté, faux sinon.
     */
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

    /**
     * Permet de récupérer tous les objets de la classe du DAO dans la base de données.
     *
     * @return Une liste de tous les objets ou {@code null} si une erreur est survenu.
     */
    public List<E> findAll() {
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
     * Permet de récupérer un object de la base de donnée a partir de sa/ses clé(s) primaire(s).
     *
     * @param primaryKeys Les clés primaires de l'objet a récupérer.
     * @return L'objet s'il est dans la base de données, {@code null} sinon.
     * @throws DaoException Si la classe du DAO ne comporte aucune clé primaire,
     *                      ou si il n'y a pas le bon nombre de clés primaires fournis,
     *                      ou si les champs passés ne sont pas des clés primaires.
     */
    public E findByPk(FieldData... primaryKeys) throws DaoException {
        if (this.primaryKeys.size() == 0) {
            throw new NoPrimaryKeyException("La classe du DAO ne comporte aucune clé primaire.");
        }
        if (primaryKeys.length != this.primaryKeys.size()) {
            throw new MissingPrimaryKeyException("Les clés primaires passées en paramètres (" + primaryKeys.length + ") " +
                    "ne sont pas égales au nombre de clé primaires de la classe (" + this.primaryKeys.size() + ").");
        }
        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("SELECT * FROM ").append(tableName).append(" WHERE ");
        for (FieldData pk : primaryKeys) {
            if (this.primaryKeys.containsKey(pk.getFieldName())) {
                sqlBuilder.append(pk.getFieldName()).append("=? AND");
            } else {
                throw new WrongPrimaryKeyException("La classe du DAO ne contient pas de clé primaire nommé : " + pk.getFieldName());
            }
        }
        sqlBuilder.delete(sqlBuilder.lastIndexOf("AND"), sqlBuilder.length());

        try (PreparedStatement statement = connection.prepareStatement(sqlBuilder.toString())) {
            for (int i = 0; i < primaryKeys.length; i++) {
                statement.setObject(i + 1, primaryKeys[i].getFieldValue());
            }
            ResultSet resultSet = statement.executeQuery();
            resultSet.next();
            return createInstance(resultSet);
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

    protected Map<Field, DaoField> getAnnotatedFields(Class<?> clazz) {
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

    private Map<String, Field> getPrimaryKeys() {
        Map<String, Field> primaryKeys = new HashMap<>();
        this.daoFieldMap.forEach((field, daoField) -> {
            if (field.isAnnotationPresent(PrimaryKey.class)) {
                primaryKeys.put(daoField.name(), field);
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

    /**
     * Permet de vérifier que la relation à ajouter au DAO est valide.
     *
     * @param otherDao     L'autre DAO à associé.
     * @param relationName Le nom qui sera donnée à la relation.
     * @throws NoPrimaryKeyException    Si l'un des deux DAO ne contient pas de clé primaires.
     * @throws IllegalArgumentException Si le nom de la relation est déjà utilisé pour ce DAO.
     */
    private void verifyRelation(Dao<?> otherDao, String relationName) {
        if (primaryKeys.isEmpty()) {
            throw new NoPrimaryKeyException("La classe du DAO ne contient pas de clé primaire.");
        }
        if (otherDao.primaryKeys.isEmpty()) {
            throw new NoPrimaryKeyException("La classe de l'autre DAO ne contient pas de clé primaire");
        }
        if (tablesName.contains(relationName)) {
            throw new IllegalArgumentException("Le DAO contient déjà une relation ou une table portant le nom : " + relationName);
        }
    }

    private boolean createHasOneTables() {
        try {
            this.hasOne.forEach((relationName, dao) -> {
                StringBuilder sqlBuilder = new StringBuilder();
                sqlBuilder.append("CREATE TABLE IF NOT EXISTS ").append(relationName).append(" (\n");
                StringBuilder fkBuilder = new StringBuilder();
                StringBuilder pkBuilder = new StringBuilder("\tPRIMARY KEY (");

                this.primaryKeys.forEach((name, field) -> {
                    DaoField daoField = this.daoFieldMap.get(field);
                    sqlBuilder.append("\t").append(this.tableName).append(name).append(" ")
                            .append(daoField.type().getSQL()).append(",\n");
                    pkBuilder.append(this.tableName).append(name).append(",");
                    fkBuilder.append("\tFOREIGN KEY (").append(this.tableName).append(name).append(") REFERENCES ")
                            .append(this.tableName).append("(").append(name).append("),\n");
                });

                dao.primaryKeys.forEach((name, field) -> {
                    DaoField daoField = dao.daoFieldMap.get(field);
                    sqlBuilder.append("\t").append(dao.tableName).append(name).append(" ")
                            .append(daoField.type().getSQL()).append(",\n");
                    fkBuilder.append("\tFOREIGN KEY (").append(dao.tableName).append(name).append(") REFERENCES ")
                            .append(dao.tableName).append("(").append(name).append("),\n");
                });

                pkBuilder.deleteCharAt(pkBuilder.lastIndexOf(",")).append("),\n");
                sqlBuilder.append(pkBuilder);
                sqlBuilder.append(fkBuilder);
                sqlBuilder.deleteCharAt(sqlBuilder.lastIndexOf(",")).append(")\n");

                try (PreparedStatement statement = connection.prepareStatement(sqlBuilder.toString())) {
                    statement.executeUpdate();
                } catch (SQLException e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            });
        } catch (RuntimeException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private boolean createHasManyTables() {
        try {
            this.hasManyList.forEach((relationName, dao) -> {
                StringBuilder sqlBuilder = new StringBuilder();
                sqlBuilder.append("CREATE TABLE IF NOT EXISTS ").append(relationName).append(" (\n");
                StringBuilder fkBuilder = new StringBuilder();
                StringBuilder pkBuilder = new StringBuilder("\tPRIMARY KEY (");

                this.primaryKeys.forEach((name, field) -> {
                    DaoField daoField = this.daoFieldMap.get(field);
                    sqlBuilder.append("\t").append(this.tableName).append(name).append(" ")
                            .append(daoField.type().getSQL()).append(",\n");
                    pkBuilder.append(this.tableName).append(name).append(",");
                    fkBuilder.append("\tFOREIGN KEY (").append(this.tableName).append(name).append(") REFERENCES ")
                            .append(this.tableName).append("(").append(name).append("),\n");
                });

                dao.primaryKeys.forEach((name, field) -> {
                    DaoField daoField = dao.daoFieldMap.get(field);
                    sqlBuilder.append("\t").append(dao.tableName).append(name).append(" ")
                            .append(daoField.type().getSQL()).append(",\n");
                    pkBuilder.append(dao.tableName).append(name).append(",");
                    fkBuilder.append("\tFOREIGN KEY (").append(dao.tableName).append(name).append(") REFERENCES ")
                            .append(dao.tableName).append("(").append(name).append("),\n");
                });

                pkBuilder.deleteCharAt(pkBuilder.lastIndexOf(",")).append("),\n");
                sqlBuilder.append(pkBuilder);
                sqlBuilder.append(fkBuilder);
                sqlBuilder.deleteCharAt(sqlBuilder.lastIndexOf(",")).append(")\n");

                try (PreparedStatement statement = connection.prepareStatement(sqlBuilder.toString())) {
                    statement.executeUpdate();
                } catch (SQLException e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            });
        } catch (RuntimeException e) {
            e.printStackTrace();
            return false;
        }
        return true;
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
        } catch (SQLException ignored) {
            return resultSet.getObject(name);
        }
    }
}
