package fr.loicyeu.dao.annotations;

import fr.loicyeu.dao.FieldType;

import java.lang.annotation.*;

@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DaoField {

    String name();
    FieldType type();

}
