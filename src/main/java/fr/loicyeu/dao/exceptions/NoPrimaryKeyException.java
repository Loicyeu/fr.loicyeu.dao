package fr.loicyeu.dao.exceptions;

/**
 * Représente une erreur de classe ne comportant aucune clés primaires.
 *
 * @author Loïc HENRY
 * @author https://github.com/Loicyeu
 * @since 1.0
 */
public final class NoPrimaryKeyException extends DaoException {

    /**
     * Constructs a new runtime exception with {@code null} as its
     * detail message.  The cause is not initialized, and may subsequently be
     * initialized by a call to {@link #initCause}.
     */
    public NoPrimaryKeyException() {
    }

    /**
     * Constructs a new runtime exception with the specified detail message.
     * The cause is not initialized, and may subsequently be initialized by a
     * call to {@link #initCause}.
     *
     * @param message the detail message. The detail message is saved for
     *                later retrieval by the {@link #getMessage()} method.
     */
    public NoPrimaryKeyException(String message) {
        super(message);
    }
}
