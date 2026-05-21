package interpret

public interface Interpreter {
    public fun fold(parameters: List<InterpreterNode>): InterpreterNode
}
