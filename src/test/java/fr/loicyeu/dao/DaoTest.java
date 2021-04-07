package fr.loicyeu.dao;

import fr.loicyeu.dao.exceptions.DaoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DaoTest {

    Connection connection;
    Dao<ValidFakeClass> dao;

    ValidFakeClass jean1 = new ValidFakeClass(1, 15, "Jean");
    ValidFakeClass jean2 = new ValidFakeClass(2, 15, "Jean");
    ValidFakeClass jacques1 = new ValidFakeClass(1, 25, "Jacques");
    ValidFakeClass jacques3 = new ValidFakeClass(3, 25, "Jacques");


    @BeforeEach
    void setUp() throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        dao = new Dao<>(connection, ValidFakeClass.class);
    }

    @Test
    void createTable() {
        assertTrue(dao.createTable(false), "Création de table valide");
        assertTrue(dao.createTable(true), "Création forcée de table valide");
        assertFalse(dao.createTable(false), "Création de table déjà présente");
    }

    @Test
    void dropTable() {
        assertFalse(dao.dropTable());
        assertTrue(dao.createTable(false));
        assertTrue(dao.dropTable());
    }

    @Test
    void insert() {
        assertFalse(dao.insert(jean1));
        dao.createTable(false);
        assertTrue(dao.insert(jean1));
        assertFalse(dao.insert(jean1));
        assertTrue(dao.insert(jean2));
        assertFalse(dao.insert(jacques1));
    }

    @Test
    void findByPk() {
        dao.createTable(false);
        dao.insert(jean1);
        dao.insert(jacques3);

        assertEquals(jean1, dao.findByPk(new FieldData("id", 1)));
        assertThrows(DaoException.class, () -> dao.findByPk());
        assertThrows(DaoException.class, () -> dao.findByPk(new FieldData("name", "Jean")));
        assertThrows(DaoException.class, () -> dao.findByPk(
                new FieldData("name", "Jean"),
                new FieldData("id", 0)
        ));
        assertNull(dao.findByPk(new FieldData("id", 5)));
        assertEquals(jacques3, dao.findByPk(new FieldData("id", 3)));
    }

    @Test
    void findAll() {
        assertTrue(dao.findAll().isEmpty());
        dao.createTable(false);
        assertTrue(dao.findAll().isEmpty());
        dao.insert(jean1);
        dao.insert(jean2);
        dao.insert(jacques3);

        List<ValidFakeClass> list = List.of(jean1, jacques3, jean2);
        List<ValidFakeClass> resList = dao.findAll();
        assertEquals(list.size(), resList.size());
        assertTrue(list.containsAll(resList));
        assertTrue(resList.containsAll(list));
    }

    @Test
    void findAllWhere() {
        dao.createTable(false);
        assertTrue(dao.findAllWhere().isEmpty());
        dao.insert(jean1);
        dao.insert(jean2);
        dao.insert(jacques3);

        List<ValidFakeClass> findAll = dao.findAll();
        List<ValidFakeClass> resList = dao.findAllWhere();
        assertEquals(findAll.size(), resList.size());
        assertTrue(findAll.containsAll(resList));
        assertTrue(resList.containsAll(findAll));

        List<ValidFakeClass> jeanList = List.of(jean1, jean2);
        List<ValidFakeClass> resListJean = dao.findAllWhere(new FieldData("name", "Jean"));
        assertEquals(jeanList.size(), resListJean.size());
        assertTrue(jeanList.containsAll(resListJean));
        assertTrue(resListJean.containsAll(jeanList));

        assertThrows(DaoException.class, () -> dao.findAllWhere(new FieldData("unknown", 0)));
        assertTrue(dao.findAllWhere(
                new FieldData("id", 1),
                new FieldData("name", "Jean"),
                new FieldData("age", 30)
        ).isEmpty());

    }

    @Test
    void edit() {
        dao.createTable(false);
        dao.insert(jean1);
        dao.insert(jacques3);

        assertEquals(jean1, dao.findByPk(new FieldData("id", 1)));
        assertTrue(dao.edit(jacques1));
        assertEquals(jacques1, dao.findByPk(new FieldData("id", 1)));
    }

    @Test
    void delete() {
        dao.createTable(false);
        dao.insert(jean1);

        assertEquals(jean1, dao.findByPk(new FieldData("id", 1)));
        assertTrue(dao.delete(jean1));
        assertNull(dao.findByPk(new FieldData("id", 1)));
    }

    @Test
    void purge() {
        dao.createTable(false);
        dao.insert(jean1);
        dao.insert(jean2);
        dao.insert(jacques3);

        assertEquals(3, dao.findAll().size());
        assertTrue(dao.purge());
        assertTrue(dao.findAll().isEmpty());
    }
}
