package interpret

/**
 * Meta-annotation for annotations that indicate reification
 *
 * @contract InterpreterNode construction is fully controlled by the backend, so it
 *           is safe to cast InterpreterNode to internal type that implements it.
 */
public annotation class Interpret
