package fr.loicyeu.dao.annotations;

import fr.loicyeu.dao.FieldType;

import java.lang.annotation.*;

/**
 * Permet d'indiquer au {@link fr.loicyeu.dao.Dao} un champ à inclure dans la table de la classe annoter avec
 * {@link DaoTable}.
 *
 * @author Loïc HENRY
 * @author https://github.com/Loicyeu
 * @version 1.0
 * @since 1.0
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DaoField {

    /**
     * Le nom du champ dans la base de données. Peut être différent du nom de la variable.
     *
     * @return Le nom du champs dans la base de données.
     */
    String name();

    /**
     * Le type SQL du champ.
     *
     * @return Le type SQL du champ.
     */
    FieldType type();

}
