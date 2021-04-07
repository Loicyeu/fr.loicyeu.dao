package fr.loicyeu.dao;

import fr.loicyeu.dao.annotations.DaoField;
import fr.loicyeu.dao.annotations.DaoTable;
import fr.loicyeu.dao.annotations.PrimaryKey;

import java.util.Objects;

@DaoTable(tableName = "valid")
class ValidFakeClass {

    @DaoField(name = "id", type = FieldType.INT)
    @PrimaryKey
    private final int id;

    @DaoField(name = "age", type = FieldType.INT)
    private final int age;

    @DaoField(name = "name", type = FieldType.VARCHAR)
    private final String name;

    public ValidFakeClass() {
        this.id = 0;
        this.age = 0;
        this.name = "";
    }

    public ValidFakeClass(int id, int age, String name) {
        this.id = id;
        this.age = age;
        this.name = name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ValidFakeClass that = (ValidFakeClass) o;
        return id == that.id && age == that.age && Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, age, name);
    }
}