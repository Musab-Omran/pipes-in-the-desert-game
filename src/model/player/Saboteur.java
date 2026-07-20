package model.player;

import model.elements.Pipe;
import model.elements.PipeStatus;
import util.Trace;

/**
 * Saboteur player role.
 */
public class Saboteur extends Player {

    public Saboteur(String id, String name, Team team) {
        super(id, name, team);
    }

    /**
     * Punctures a pipe.
     *
     * @param pipe pipe to puncture
     */
    public void puncturePipe(Pipe pipe) {
        Trace.log("S: " + id + ".puncturePipe(" + pipe.getId() + ")");
        pipe.setStatus(PipeStatus.LEAKING);
        Trace.log("S: " + pipe.getId() + ".status = LEAKING");
        Trace.log("S: [OK] " + id + " punctured " + pipe.getId() + ".");
    }
}
