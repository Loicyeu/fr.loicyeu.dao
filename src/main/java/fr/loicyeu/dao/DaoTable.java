package fr.loicyeu.dao;

import java.lang.annotation.*;

@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface DaoTable {

    String tableName();
    boolean fetchSuperFields() default false;

}