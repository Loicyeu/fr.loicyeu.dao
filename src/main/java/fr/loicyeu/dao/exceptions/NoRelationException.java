package fr.loicyeu.dao.exceptions;

/**
 * Représente une erreur de relation inexistante entre deux DAO.
 *
 * @author Loïc HENRY
 * @author https://github.com/Loicyeu
 * @since 1.0
 */
public final class NoRelationException extends DaoException {
    /**
     * Constructs a new runtime exception with {@code null} as its
     * detail message.  The cause is not initialized, and may subsequently be
     * initialized by a call to {@link #initCause}.
     */
    public NoRelationException() {
    }

    /**
     * Constructs a new runtime exception with the specified detail message.
     * The cause is not initialized, and may subsequently be initialized by a
     * call to {@link #initCause}.
     *
     * @param message the detail message. The detail message is saved for
     *                later retrieval by the {@link #getMessage()} method.
     */
    public NoRelationException(String message) {
        super(message);
    }
}
