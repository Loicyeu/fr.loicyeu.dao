/**
 * @author Loïc HENRY
 * @author https://github.com/Loicyeu
 * @since 1.0
 */
module fr.loicyeu.dao {

    requires java.compiler;
    requires java.sql;

    exports fr.loicyeu.dao;
    exports fr.loicyeu.dao.exceptions;
    exports fr.loicyeu.dao.annotations;
}