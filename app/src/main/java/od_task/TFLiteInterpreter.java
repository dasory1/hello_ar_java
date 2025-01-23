package od_task;
import org.tensorflow.lite.Interpreter;

import java.nio.MappedByteBuffer;

public class TFLiteInterpreter {
    private Interpreter interpreter;

    public TFLiteInterpreter(MappedByteBuffer modelBuffer) {
        Interpreter.Options options = new Interpreter.Options();
        this.interpreter = new Interpreter(modelBuffer, options);
    }

    public void close() {
        interpreter.close();
    }

    public Interpreter getInterpreter() {
        return interpreter;
    }
}
