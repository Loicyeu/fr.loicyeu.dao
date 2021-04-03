package fr.loicyeu.dao;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

public class DaoTest {

    private static Connection connection;

    @BeforeAll
    static void beforeAll() throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
    }

    @Test
    public void testConstructorNoAnnotation() {
        class InvalidFakeClass {

            @DaoField(name = "name", type = FieldType.VARCHAR)
            String name;

            public InvalidFakeClass(String name) {
                this.name = name;
            }
        }
        assertThrows(IllegalArgumentException.class, () -> new Dao<>(connection, InvalidFakeClass.class));
    }

    @Test
    public void testConstructorNoConstructor() {
        @DaoTable(tableName = "InvalidFakeClass", fetchSuperFields = true)
        class InvalidFakeClass {

            @DaoField(name = "name", type = FieldType.VARCHAR)
            String name;

            public InvalidFakeClass(String name) {
                this.name = name;
            }
        }
        assertThrows(IllegalArgumentException.class, () -> new Dao<>(connection, InvalidFakeClass.class));
    }

    @Test
    public void testConstructorAbstract() {
        @DaoTable(tableName = "InvalidFakeClass", fetchSuperFields = true)
        abstract class InvalidFakeClass {

            @DaoField(name = "name", type = FieldType.VARCHAR)
            String name;

            public InvalidFakeClass(String name) {
                this.name = name;
            }
        }
        assertThrows(IllegalArgumentException.class, () -> new Dao<>(connection, InvalidFakeClass.class));
    }

    @Test
    public void testConstructor() {
        assertDoesNotThrow(() -> new Dao<>(connection, ValideFakeClass.class));
    }

}
