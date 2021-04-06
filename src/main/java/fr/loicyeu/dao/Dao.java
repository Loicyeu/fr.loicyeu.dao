package fr.loicyeu.dao;

import fr.loicyeu.dao.annotations.DaoField;
import fr.loicyeu.dao.annotations.DaoTable;
import fr.loicyeu.dao.exceptions.*;
import fr.loicyeu.dao.utils.DaoUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

import static fr.loicyeu.dao.utils.DaoUtils.*;

/**
 * Représente un DAO générique permettant la gestion d'une base de données pour une classe donnée.<br>
 * La classe du DAO ne doit pas être abstraite ou être une interface.
 * Elle doit également être annoter par {@link DaoTable}
 * et posséder une constructeur ne pennant aucun paramètre (peut importe l'encapsulation).
 *
 * @param <E> La classe du DAO.
 * @author Loïc HENRY
 * @author https://github.com/Loicyeu
 * @since 1.0
 */
public final class Dao<E> {

    private static final List<String> tablesName = new LinkedList<>();

    private final Connection connection;
    private final Constructor<E> constructor;

    private final String tableName;
    private final Map<Field, DaoField> daoFieldMap;
    private final Map<String, FieldType> fieldTypeMap;
    private final Map<String, Field> primaryKeys;

    private final Map<Dao<?>, String> hasMany;
    private final Map<Dao<?>, String> hasOne;


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
        this.daoFieldMap = getAnnotatedFields(e, daoTable.fetchSuperFields());
        this.fieldTypeMap = getFieldsDetails(daoFieldMap);
        this.primaryKeys = getPK(daoFieldMap);

        this.hasMany = new HashMap<>();
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
        this.hasOne.put(otherDao, relationName);
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
        this.hasMany.put(otherDao, relationName);
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

        if (executeUpdate(connection, sqlBuilder.toString(), null)) {
            return createHasOneTables() && createHasManyTables();
        } else {
            return false;
        }
    }

    /**
     * Permet de supprimer la table de la base de donnée.
     *
     * @return Vrai si la table a bien été supprimé, faux sinon.
     */
    public boolean dropTable() {
        return executeUpdate(connection, "DROP TABLE " + tableName, null);
    }


    /**
     * Permet d'ajouter un objet dans la base de données.
     *
     * @param e L'objet à ajouter à la base de données.
     * @return Vrai si l'objet a bien été ajouté, faux sinon.
     */
    public boolean insert(E e) {
        Map<String, Object> fieldsValues = getFieldsValues(this.daoFieldMap, e);
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

        return executeUpdate(connection, sqlBuilder.toString(), valuesList);
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
                throw new NoPrimaryKeyException("La classe du DAO ne contient pas de clé primaire nommé : " + pk.getFieldName());
            }
        }
        sqlBuilder.delete(sqlBuilder.lastIndexOf("AND"), sqlBuilder.length());

        try (PreparedStatement statement = connection.prepareStatement(sqlBuilder.toString())) {
            for (int i = 0; i < primaryKeys.length; i++) {
                statement.setObject(i + 1, primaryKeys[i].getFieldValue());
            }
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                return createInstance(resultSet);
            } else {
                return null;
            }
        } catch (SQLException err) {
            err.printStackTrace();
            return null;
        }
    }

    /**
     * Permet de récupérer tous les objets de la classe du DAO dans la base de données.
     *
     * @return Une liste de tous les objets ou une liste vide si une erreur est survenu.
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
            return List.of();
        }
    }

    /**
     * Permet de récupérer tous les objets de la base de données coïncidant avec tous les champs passés en paramètres.
     * L'opérateur {@code AND} est utilisé entre tous les champs dans la requête. Dans le cas ou aucun champs ne serait
     * donnés, la méthode se comporte comme {@link #findAll()}.
     *
     * @param fields La liste de tous les champs recherchés dans les objets.
     * @return La liste des objets de la base de données coïncidant.
     * @throws DaoException Si le nombre de champs passés en paramètres sont supérieurs au nombre de champs du DAO,
     *                      ou si un champ n'existe pas dans le DAO.
     */
    public List<E> findAllWhere(FieldData... fields) throws DaoException {
        if (fields.length == 0) {
            return findAll();
        }
        List<E> eList = new ArrayList<>();
        if (fields.length > daoFieldMap.size()) {
            throw new TooManyFieldsException("Le nombre de champs passés (" + fields.length +
                    ") est supérieur au nombre de champs que comporte le DAO (" + daoFieldMap.size() + ").");
        }

        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("SELECT * FROM ").append(tableName).append(" WHERE ");
        for (FieldData field : fields) {
            if (this.fieldTypeMap.containsKey(field.getFieldName())) {
                sqlBuilder.append(field.getFieldName()).append("=? AND ");
            } else {
                throw new NoFieldException("Le DAO ne contient pas de champ nommé : " + field.getFieldName());
            }
        }
        sqlBuilder.delete(sqlBuilder.lastIndexOf("AND"), sqlBuilder.length());

        try (PreparedStatement statement = connection.prepareStatement(sqlBuilder.toString())) {
            for (int i = 0; i < fields.length; i++) {
                statement.setObject(i + 1, fields[i].getFieldValue());
            }
            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                eList.add(createInstance(resultSet));
            }
            return eList;
        } catch (SQLException err) {
            err.printStackTrace();
            return List.of();
        }

    }

    /**
     * Permet de modifier un objet de la base de donnée a condition que ses clés primaires n'ai pas changé.
     *
     * @param e L'élément a mettre a jour dans la base de donnée.
     * @return Vrai si la mise a jour s'est bien déroulé, faux sinon.
     */
    public boolean edit(E e) {
        Map<String, Object> fieldValues = getFieldsValues(daoFieldMap, e);
        List<Object> valuesList = new ArrayList<>();

        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("UPDATE ").append(tableName).append(" SET ");
        fieldValues.forEach((name, value) -> {
            if (!primaryKeys.containsKey(name)) {
                sqlBuilder.append(name).append("=?, ");
                valuesList.add(fieldValues.get(name));
            }
        });
        sqlBuilder.deleteCharAt(sqlBuilder.lastIndexOf(",")).append(" WHERE ");
        primaryKeys.forEach((name, field) -> {
            sqlBuilder.append(name).append("=? AND ");
            valuesList.add(fieldValues.get(name));
        });
        sqlBuilder.delete(sqlBuilder.lastIndexOf("AND"), sqlBuilder.length());

        return executeUpdate(connection, sqlBuilder.toString(), valuesList);
    }

    /**
     * Permet de supprimer un objet dans la base de donnée à partir de ses clés primaires.
     * Seules les clés primaires de l'objet ne seront prisent en compte lors de la recherche.
     *
     * @param e L'objet a supprimer de la base de données.
     * @return Vrai si l'objet a bien été supprimer, faux sinon.
     */
    public boolean delete(E e) {
        Map<String, Object> fieldValues = getFieldsValues(daoFieldMap, e);
        List<Object> valuesList = new ArrayList<>();

        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("DELETE FROM ").append(tableName).append(" WHERE ");
        primaryKeys.forEach((name, field) -> {
            sqlBuilder.append(name).append("=? AND ");
            valuesList.add(fieldValues.get(name));
        });
        sqlBuilder.delete(sqlBuilder.lastIndexOf("AND"), sqlBuilder.length());

        return executeUpdate(connection, sqlBuilder.toString(), valuesList);
    }

    /**
     * Permet de purger la table de toutes les données qu'elle contient.
     *
     * @return Vrai si la table a bien été purger, faux sinon.
     */
    public boolean purge() {
        return executeUpdate(connection, "DROP FROM " + tableName, null);
    }


    /**
     * Permet d'ajouter un lien entre deux objet dans la relation 1-N impliquant le DAO passé en paramètre.
     *
     * @param otherDao Le DAO impliqué dans le lien.
     * @param e        L'objet de ce DAO impliqué dans le lien.
     * @param t        L'objet de l'autre DAO impliqué dans le lien.
     * @param <T>      La classe de l'autre DAO.
     * @return Vrai si l'ajout du lien s'est correctement passé, faux sinon.
     * @throws DaoException Si aucune relation n'implique le DAO passé en paramètre et ce DAO.
     */
    public <T> boolean insert1NRelation(Dao<T> otherDao, E e, T t) throws DaoException {
        return insertInRelation(hasMany, otherDao, e, t);
    }

    /**
     * Permet d'ajouter un lien entre deux objet dans la relation 1-1 impliquant le DAO passé en paramètre.
     *
     * @param otherDao Le DAO impliqué dans le lien.
     * @param e        L'objet de ce DAO impliqué dans le lien.
     * @param t        L'objet de l'autre DAO impliqué dans le lien.
     * @param <T>      La classe de l'autre DAO.
     * @return Vrai si l'ajout du lien s'est correctement passé, faux sinon.
     * @throws DaoException Si aucune relation n'implique le DAO passé en paramètre et ce DAO.
     */
    public <T> boolean insert11Relation(Dao<T> otherDao, E e, T t) throws DaoException {
        return insertInRelation(hasOne, otherDao, e, t);
    }

    /**
     * Permet de récupérer tous les objets de la base de données correspondant à {@code e} via la relation impliquant le
     * DAO {@code otherDao}.
     *
     * @param otherDao Le DAO de la relation.
     * @param e        L'élément dont on veut les objets de la relation.
     * @param <T>      Le type du DAO de la relation.
     * @return La liste des objets correspondant ou null si une erreur s'est produite.
     * @throws DaoException Si aucune relation ne comporte le DAO.
     */
    public <T> List<T> findFrom1NRelation(Dao<T> otherDao, E e) throws DaoException {
        return findFromRelation(hasMany, otherDao, e);
    }

    /**
     * Permet de récupérer l'objets de la base de données correspondant à {@code e} via la relation impliquant le
     * DAO {@code otherDao}.
     *
     * @param otherDao Le DAO de la relation.
     * @param e        L'élément dont on veut l'objet de la relation.
     * @param <T>      Le type du DAO de la relation.
     * @return L'objet correspondant ou null s'il n'y en a pas ou si une erreur s'est produite.
     * @throws DaoException Si aucune relation ne comporte le DAO.
     */
    public <T> T findFrom11Relation(Dao<T> otherDao, E e) throws DaoException {
        List<T> tList = findFromRelation(hasOne, otherDao, e);
        if (tList != null && !tList.isEmpty()) {
            return tList.get(0);
        } else {
            return null;
        }
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

    private boolean createRelationTable(Map<Dao<?>, String> map, boolean is1N) {
        try {
            map.forEach((dao, relationName) -> {
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
                            .append(this.tableName).append("(").append(name).append(") ON DELETE CASCADE,\n");
                });

                dao.primaryKeys.forEach((name, field) -> {
                    DaoField daoField = dao.daoFieldMap.get(field);
                    sqlBuilder.append("\t").append(dao.tableName).append(name).append(" ")
                            .append(daoField.type().getSQL()).append(",\n");
                    if (is1N) {
                        pkBuilder.append(dao.tableName).append(name).append(",");
                    }
                    fkBuilder.append("\tFOREIGN KEY (").append(dao.tableName).append(name).append(") REFERENCES ")
                            .append(dao.tableName).append("(").append(name).append(") ON DELETE CASCADE,\n");
                });

                pkBuilder.deleteCharAt(pkBuilder.lastIndexOf(",")).append("),\n");
                sqlBuilder.append(pkBuilder);
                sqlBuilder.append(fkBuilder);
                sqlBuilder.deleteCharAt(sqlBuilder.lastIndexOf(",")).append(")\n");

                try (PreparedStatement statement = connection.prepareStatement(sqlBuilder.toString())) {
                    statement.executeUpdate();
                } catch (SQLException e) {
                    e.printStackTrace();
                    throw new UnexpectedDaoException(e);
                }
            });
        } catch (RuntimeException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private boolean createHasOneTables() {
        return createRelationTable(this.hasOne, false);
    }

    private boolean createHasManyTables() {
        return createRelationTable(this.hasMany, true);
    }

    private E createInstance(ResultSet resultSet) {
        try {
            constructor.setAccessible(true);
            E e = constructor.newInstance();
            for (Map.Entry<Field, DaoField> field : daoFieldMap.entrySet()) {
                field.getKey().setAccessible(true);
                field.getKey().set(e, getValueFromResultSet(resultSet, field.getValue().name()));
            }
            return e;
        } catch (Exception err) {
            err.printStackTrace();
            return null;
        }
    }

    private <T> List<T> createInstances(ResultSet resultSet, Dao<T> otherDao) {
        try {
            List<T> tList = new ArrayList<>();
            while (resultSet.next()) {
                List<FieldData> fieldDataList = new ArrayList<>();
                for (String s : otherDao.primaryKeys.keySet()) {
                    Object obj = otherDao.getValueFromResultSet(resultSet, s + otherDao.tableName);
                    fieldDataList.add(new FieldData(s, obj));
                }
                List<T> res = otherDao.findAllWhere(fieldDataList.toArray(new FieldData[0]));
                if (res != null) {
                    tList.addAll(res);
                }
            }
            return tList;
        } catch (SQLException e) {
            e.printStackTrace();
            return List.of();
        }
    }

    private <T> List<T> findFromRelation(Map<Dao<?>, String> hasMap, Dao<T> otherDao, E e) {
        if (!hasMap.containsKey(otherDao)) {
            throw new NoRelationException("Aucune relation n'a été trouvé avec le DAO fournit.");
        }
        String relationName = hasMap.get(otherDao);

        Map<String, Object> fieldsValues = getFieldsValues(getPkDaoField(primaryKeys, daoFieldMap), e);
        List<Object> valuesList = new ArrayList<>();

        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("SELECT * FROM ").append(relationName).append(" WHERE ");
        fieldsValues.forEach((field, value) -> {
            sqlBuilder.append(tableName).append(field).append("=? AND ");
            valuesList.add(value);
        });
        sqlBuilder.delete(sqlBuilder.lastIndexOf("AND"), sqlBuilder.length());

        try (PreparedStatement statement = connection.prepareStatement(sqlBuilder.toString())) {
            for (int i = 0; i < valuesList.size(); i++) {
                statement.setObject(i + 1, valuesList.get(i));
            }
            ResultSet resultSet = statement.executeQuery();
            return createInstances(resultSet, otherDao);
        } catch (SQLException err) {
            err.printStackTrace();
            return List.of();
        }
    }

    private <T> boolean insertInRelation(Map<Dao<?>, String> hasMap, Dao<T> otherDao, E e, T t) throws DaoException {
        if (!hasMap.containsKey(otherDao)) {
            throw new NoRelationException("Aucune relation n'a été trouvée avec le DAO fournit.");
        }
        String relationName = hasMap.get(otherDao);
        Map<String, Object> eMap = getFieldsValues(DaoUtils.getPkDaoField(primaryKeys, daoFieldMap), e);
        Map<String, Object> tMap = getFieldsValues(getPkDaoField(otherDao.primaryKeys, otherDao.daoFieldMap), t);
        List<Object> valuesList = new ArrayList<>();

        StringBuilder sqlBuilder = new StringBuilder("INSERT INTO ");
        sqlBuilder.append(relationName).append("(");
        eMap.forEach((name, value) -> {
            sqlBuilder.append(relationName).append(name).append(",");
            valuesList.add(value);
        });
        tMap.forEach((name, value) -> {
            sqlBuilder.append(relationName).append(name).append(",");
            valuesList.add(value);
        });
        sqlBuilder.deleteCharAt(sqlBuilder.lastIndexOf(",")).append(") VALUES (");
        sqlBuilder.append("?,".repeat(valuesList.size())).deleteCharAt(sqlBuilder.lastIndexOf(",")).append(")");

        return executeUpdate(connection, sqlBuilder.toString(), valuesList);
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
