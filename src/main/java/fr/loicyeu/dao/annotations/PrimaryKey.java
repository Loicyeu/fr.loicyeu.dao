package fr.loicyeu.dao.annotations;

import java.lang.annotation.*;

/**
 * Permet d'indiquer au {@link fr.loicyeu.dao.Dao} que le champ annoter est une clé primaire de la table.
 * Une classe peut contenir plusieurs clés primaires.
 *
 * @author Loïc HENRY
 * @author https://github.com/Loicyeu
 * @version 1.0
 * @since 1.0
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PrimaryKey {

}
