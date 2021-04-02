package fr.loicyeu.dao;

import java.lang.annotation.*;

@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DaoField {

    String name();
    FieldType type();

}
