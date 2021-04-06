package fr.loicyeu.dao.annotations;

import java.lang.annotation.*;

/**
 * Permet d'indiquer au {@link fr.loicyeu.dao.Dao} que la classe annoter peut comporter des champs à inclure dans
 * la base de données pour des sous-classes. Elle indique également que sa super classe peut en contenir également.
 * Dans le cas ou une classe ne comporterais aucun champs mais que sa super classe en aurait il faut ajouter cette
 * annotation a cette classe ainsi que sa super classe.
 * Cette annotation peut être combiné avec {@link DaoTable} et peut être utilisé sur des classes abstraites ou sans
 * constructeurs vide.
 *
 * @author Loïc HENRY
 * @author https://github.com/Loicyeu
 * @version 1.0
 * @since 1.0
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface DaoSuper {
}
