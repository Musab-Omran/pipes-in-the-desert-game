package model.player;

import model.elements.ActiveElement;
import model.elements.FieldElement;
import model.elements.Pipe;
import model.elements.Pump;
import util.Trace;

/**
 * Abstract player base class.
 */
public abstract class Player {

    protected String id;
    protected String name;
    protected Team team;
    protected FieldElement currentPosition;
    protected boolean ready;

    public Player(String id, String name, Team team) {
        this.id = id;
        this.name = name;
        this.team = team;
        this.ready = false;
        Trace.log("S: Player created: " + id + " (" + name + ")");
    }

    /**
     * Move to an adjacent field element.
     *
     * @param target target field element
     */
    public void moveTo(FieldElement target) {
        if (target == null) {
            Trace.log("S: [FAIL] target is null.");
            return;
        }

        Trace.log("S: " + id + ".moveTo(" + target.getId() + ")");

        if (currentPosition != null && !isAdjacent(target)) {
            Trace.log("S: [FAIL] " + target.getId() + " is not adjacent to " + id + "'s position.");
            return;
        }

        if (target instanceof Pipe) {
            Pipe pipe = (Pipe) target;
            if (pipe.isOccupied()) {
                Trace.log("S: [FAIL] " + target.getId() + " is already occupied.");
                return;
            }
            leaveCurrentPosition();
            pipe.setOccupant(this);
            currentPosition = target;
            Trace.log("S: " + id + ".currentPosition = " + target.getId());
            Trace.log("S: [OK] " + id + " moved to " + target.getId() + ".");
            return;
        }

        if (target instanceof Pump) {
            Pump pump = (Pump) target;
            if (!pump.isThereSpaceOccupant()) {
                Trace.log("S: [FAIL] " + target.getId() + " is full (max occupants reached).");
                return;
            }
            leaveCurrentPosition();
            pump.getOccupants().add(this);
            currentPosition = target;
            Trace.log("S: " + id + ".currentPosition = " + target.getId());
            Trace.log("S: [OK] " + id + " moved to " + target.getId() + ".");
            return;
        }

        leaveCurrentPosition();
        currentPosition = target;
        Trace.log("S: " + id + ".currentPosition = " + target.getId());
        Trace.log("S: [OK] " + id + " moved to " + target.getId() + ".");
    }

    /**
     * Changes the direction of a pump.
     *
     * @param pump pump to change
     * @param inPipe incoming pipe
     * @param outPipe outgoing pipe
     */
    public void changePumpDirection(Pump pump, Pipe inPipe, Pipe outPipe) {
        if (pump == null) {
            Trace.log("S: [FAIL] pump is null.");
            return;
        }

        Trace.log("S: " + id + ".changePumpDirection(" + pump.getId() + ", "
                + (inPipe != null ? inPipe.getId() : "null") + ", "
                + (outPipe != null ? outPipe.getId() : "null") + ")");

        if (inPipe != null && !pump.getConnectedPipes().contains(inPipe)) {
            Trace.log("S: [FAIL] " + inPipe.getId() + " is not connected to " + pump.getId() + ".");
            return;
        }
        if (outPipe != null && !pump.getConnectedPipes().contains(outPipe)) {
            Trace.log("S: [FAIL] " + outPipe.getId() + " is not connected to " + pump.getId() + ".");
            return;
        }

        if (inPipe != null) {
            pump.setIncomingPipe(inPipe);
        }
        if (outPipe != null) {
            pump.setOutgoingPipe(outPipe);
        }

        Trace.log("S: " + pump.getId() + ".incomingPipe = "
                + (pump.getIncomingPipe() != null ? pump.getIncomingPipe().getId() : "null"));
        Trace.log("S: " + pump.getId() + ".outgoingPipe = "
                + (pump.getOutgoingPipe() != null ? pump.getOutgoingPipe().getId() : "null"));
        Trace.log("S: [OK] " + id + " changed direction of " + pump.getId() + ".");
    }

    public void setReady() {
        ready = true;
        Trace.log("S: " + id + ".ready = true");
    }

    protected boolean isAdjacent(FieldElement target) {
        if (currentPosition instanceof ActiveElement) {
            ActiveElement ae = (ActiveElement) currentPosition;
            if (target instanceof Pipe) {
                return ae.getConnectedPipes().contains(target);
            }
            for (Pipe p : ae.getConnectedPipes()) {
                if (p.getEndA() == target || p.getEndB() == target) {
                    return true;
                }
            }
        } else if (currentPosition instanceof Pipe) {
            Pipe pipe = (Pipe) currentPosition;
            return target == pipe.getEndA() || target == pipe.getEndB();
        }
        return false;
    }

    private void leaveCurrentPosition() {
        if (currentPosition == null) {
            return;
        }
        if (currentPosition instanceof Pipe) {
            ((Pipe) currentPosition).setOccupant(null);
            Trace.log("S: " + currentPosition.getId() + ".occupant = null");
        } else if (currentPosition instanceof Pump) {
            ((Pump) currentPosition).getOccupants().remove(this);
            Trace.log("S: " + currentPosition.getId() + ".occupants.remove(" + id + ")");
        }
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Team getTeam() {
        return team;
    }

    public FieldElement getCurrentPosition() {
        return currentPosition;
    }

    public void setCurrentPosition(FieldElement p) {
        leaveCurrentPosition();
        currentPosition = p;
        if (p instanceof Pipe) {
            ((Pipe) p).setOccupant(this);
        } else if (p instanceof Pump) {
            Pump pump = (Pump) p;
            if (!pump.getOccupants().contains(this)) {
                pump.getOccupants().add(this);
            }
        }
    }

    public boolean isReady() {
        return ready;
    }

    @Override
    public String toString() {
        return id + " (" + name + ")";
    }
}
