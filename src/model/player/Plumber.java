package model.player;

import model.elements.ActiveElement;
import model.elements.Cistern;
import model.elements.Pipe;
import model.elements.PipeStatus;
import model.elements.Pump;
import model.elements.PumpStatus;
import util.Trace;

/** Plumber player role. */
public class Plumber extends Player {
    private Pump carriedPump;

    public Plumber(String id, String name, Team team) {
        super(id, name, team);
        this.carriedPump = null;
    }

    public void fixBrokenPump(Pump pump) {
        Trace.log("S: " + id + ".fixBrokenPump(" + pump.getId() + ")");
        pump.setStatus(PumpStatus.WORKING);
        Trace.log("S: " + pump.getId() + ".status = WORKING");
        Trace.log("S: [OK] " + id + " fixed " + pump.getId() + ".");
    }

    public void fixLeakingPipe(Pipe pipe) {
        Trace.log("S: " + id + ".fixLeakingPipe(" + pipe.getId() + ")");
        pipe.setStatus(PipeStatus.NORMAL);
        Trace.log("S: " + pipe.getId() + ".status = NORMAL");
        Trace.log("S: [OK] " + id + " fixed " + pipe.getId() + ".");
    }

    public void pickUpPump(Cistern cistern) {
        Trace.log("S: " + id + ".pickUpPump(" + cistern.getId() + ")");
        if (carriedPump != null) {
            Trace.log("S: [FAIL] " + id + " is already carrying " + carriedPump.getId() + ".");
            return;
        }
        if (cistern.getPumpInventory().isEmpty()) {
            Trace.log("S: [FAIL] " + cistern.getId() + " has no pumps available.");
            return;
        }
        carriedPump = cistern.getPumpInventory().remove(0);
        Trace.log("S: " + id + ".carriedPump = " + carriedPump.getId());
        Trace.log("S: [OK] " + id + " picked up " + carriedPump.getId()
                + " from " + cistern.getId() + ".");
    }

    /**
     * Legacy method kept for compatibility. The engine's insertPumpIntoPipe()
     * is the authoritative insertion workflow.
     */
    public void insertPump(Pipe pipe, Pump pump) {
        Trace.log("S: " + id + ".insertPump(" + pipe.getId() + ", " + pump.getId() + ")");
        ActiveElement oldEndA = pipe.getEndA();
        ActiveElement oldEndB = pipe.getEndB();

        Pipe halfA = new Pipe(pipe.getId() + "-A");
        Pipe halfB = new Pipe(pipe.getId() + "-B");

        halfA.setEndA(oldEndA); halfA.setEndB(pump);
        if (oldEndA != null) { oldEndA.disconnectPipe(pipe); oldEndA.connectPipe(halfA); }
        pump.connectPipe(halfA);

        halfB.setEndA(pump); halfB.setEndB(oldEndB);
        pump.connectPipe(halfB);
        if (oldEndB != null) { oldEndB.disconnectPipe(pipe); oldEndB.connectPipe(halfB); }

        pump.setIncomingPipe(halfA);
        pump.setOutgoingPipe(halfB);

        if (currentPosition == pipe) { pipe.setOccupant(null); }
        pump.getOccupants().add(this);
        currentPosition = pump;
        carriedPump = null;
        Trace.log("S: [OK] " + id + " inserted " + pump.getId() + " into " + pipe.getId() + ".");
    }

    public void connectPipe(Pipe pipe, ActiveElement element) {
        Trace.log("S: " + id + ".connectPipe(" + pipe.getId() + ", " + element.getId() + ")");
        if (!element.isThereSpacePipe()) {
            Trace.log("S: [FAIL] " + element.getId() + " has no free port.");
            return;
        }
        if (pipe.getEndA() == null) {
            pipe.setEndA(element); element.connectPipe(pipe);
        } else if (pipe.getEndB() == null) {
            pipe.setEndB(element); element.connectPipe(pipe);
        } else {
            Trace.log("S: [FAIL] " + pipe.getId() + " has no free end.");
            return;
        }
        Trace.log("S: [OK] " + pipe.getId() + " connected to " + element.getId() + ".");
    }

    public void disconnectPipe(Pipe pipe, ActiveElement element) {
        Trace.log("S: " + id + ".disconnectPipe(" + pipe.getId() + ", " + element.getId() + ")");
        if (pipe.getEndA() == element) {
            pipe.setEndA(null);
        } else if (pipe.getEndB() == element) {
            pipe.setEndB(null);
        } else {
            Trace.log("S: [FAIL] " + pipe.getId() + " is not connected to " + element.getId() + ".");
            return;
        }
        element.disconnectPipe(pipe);
        Trace.log("S: [OK] " + pipe.getId() + " disconnected from " + element.getId() + ".");
    }

    public Pump getCarriedPump() { return carriedPump; }
    public void setCarriedPump(Pump p) { this.carriedPump = p; }
}
