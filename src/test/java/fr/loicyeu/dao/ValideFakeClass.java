package fr.loicyeu.dao;

@DaoTable(tableName = "ValideFakeClass")
public class ValideFakeClass {

    @DaoField(name = "name", type = FieldType.VARCHAR)
    String name;

    public ValideFakeClass(String name) {
        this.name = name;
    }

    public ValideFakeClass() {
        this.name = "defaultName";
    }
}