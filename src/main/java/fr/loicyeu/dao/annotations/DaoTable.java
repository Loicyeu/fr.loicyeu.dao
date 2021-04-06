package fr.loicyeu.dao.annotations;

import java.lang.annotation.*;

/**
 * Permet d'indiquer au {@link fr.loicyeu.dao.Dao} le nom unique de la table qui accueillera cette classe.<br>
 * L'annotation permet également de définir s'il faut remonter l'arborescence des classes
 * pour y trouver des champs a inclure dans la base de données. Par défaut cette option est désactivée.
 * Dans le cas ou elle serait activé, il faut annoter toutes les super classes avec {@link DaoSuper}.
 * Lorsqu'en remontant l'arborescence une classe sera trouvé sans l'annotation {@link DaoSuper}, cela stoppera
 * la remontée.
 *
 * @author Loïc HENRY
 * @author https://github.com/Loicyeu
 * @version 1.0
 * @since 1.0
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface DaoTable {

    /**
     * Le nom unique de la table.
     * @return Le nom unique de la table.
     */
    String tableName();

    /**
     * La remontée de l'arborescence pour chercher des champs à inclure dans la base de données.
     * @return Vrai s'il faut remonter l'arborescence, faux sinon (par défaut).
     */
    boolean fetchSuperFields() default false;

}