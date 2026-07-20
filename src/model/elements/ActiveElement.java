package model.elements;

import java.util.ArrayList;
import java.util.List;
import util.Trace;

/**
 * A field element that can connect pipes.
 */
public abstract class ActiveElement extends FieldElement {

    protected List<Pipe> connectedPipes;

    public ActiveElement(String id) {
        super(id);
        this.connectedPipes = new ArrayList<>();
    }

    public abstract boolean isThereSpacePipe();

    public void connectPipe(Pipe pipe) {
        Trace.log("S: " + id + ".connectPipe(" + (pipe != null ? pipe.getId() : "null") + ")");
        if (pipe == null) {
            Trace.log("S: [FAIL] Cannot connect null pipe to " + id + ".");
            return;
        }
        if (!isThereSpacePipe()) {
            Trace.log("S: [FAIL] " + id + " has no available ports.");
            return;
        }
        if (!connectedPipes.contains(pipe)) {
            connectedPipes.add(pipe);
            Trace.log("S: " + id + ".connectedPipes.add(" + pipe.getId() + ")");
        }
    }

    public void disconnectPipe(Pipe pipe) {
        Trace.log("S: " + id + ".disconnectPipe(" + (pipe != null ? pipe.getId() : "null") + ")");
        if (pipe == null || !connectedPipes.contains(pipe)) {
            Trace.log("S: [WARN] " + id + " is not connected to " + (pipe != null ? pipe.getId() : "null") + ".");
            return;
        }
        connectedPipes.remove(pipe);
        Trace.log("S: " + id + ".connectedPipes.remove(" + pipe.getId() + ")");
    }

    public List<Pipe> getConnectedPipes() {
        return connectedPipes;
    }
}
