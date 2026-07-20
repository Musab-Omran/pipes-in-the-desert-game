package model.elements;

import java.util.ArrayList;
import java.util.List;
import model.player.Player;
import util.Trace;

/** A pump controls water direction and stores water in an internal tank. */
public class Pump extends ActiveElement {
    private static int counter = 0;

    private int maxPorts;
    private List<Player> occupants;
    private int maxOccupants;
    private int tankCapacity;
    private int tankCurrentWater;
    private Pipe incomingPipe;
    private Pipe outgoingPipe;
    private PumpStatus status;
    private int lastTickLoss;   // 1 if water overflow occurred this tick

    public Pump() {
        super("PU-" + (++counter));
        this.maxPorts = 4;
        init();
    }

    public Pump(String id, int maxPorts) {
        super(id);
        this.maxPorts = maxPorts;
        init();
    }

    public static void resetCounter() { counter = 0; }

    private void init() {
        this.occupants        = new ArrayList<>();
        this.maxOccupants     = 4;
        this.tankCapacity     = 10;
        this.tankCurrentWater = 0;
        this.incomingPipe     = null;
        this.outgoingPipe     = null;
        this.status           = PumpStatus.WORKING;
        this.lastTickLoss     = 0;
        Trace.log("S: Pump created: " + id);
    }

    @Override public boolean isThereSpacePipe() {
        boolean space = connectedPipes.size() < maxPorts;
        Trace.log("S: " + id + ".isThereSpacePipe() → " + space);
        return space;
    }

    /**
     * Water simulation for one tick:
     * <ol>
     *   <li>If WORKING and the outgoing pipe is empty and the tank has water,
     *       push one unit out.</li>
      *   <li>If WORKING and the incoming pipe has water, pull one unit into the
     *       tank (or lose it if the tank is full).</li>
     *   <li>If BROKEN, outgoing flow is blocked, but the incoming pipe may still
     *       fill the tank until it is full.</li>
     * </ol>
     */
    /**
     * Documented simulation API — Phase A: commit water from tank to outgoing pipe.
     * Called BEFORE prepareWater() so water progresses one stage per tick.
     */
    public void commitWater() {
        if (status == PumpStatus.BROKEN) return;
        if (outgoingPipe != null && !outgoingPipe.getIsWaterInside() && tankCurrentWater > 0) {
            tankCurrentWater--;
            outgoingPipe.setIsWaterInside(true);
            Trace.log("S:   " + id + " commit: tank→" + outgoingPipe.getId()
                    + " tank=" + tankCurrentWater);
        }
    }

    /**
     * Documented simulation API — Phase B: pull water from incoming pipe into tank.
     * Overflow (tank full) is tracked as lost water.
     */
    public void prepareWater() {
        lastTickLoss = 0;
        if (incomingPipe != null && incomingPipe.getIsWaterInside()) {
            if (!isTankFull()) {
                tankCurrentWater++;
                incomingPipe.setIsWaterInside(false);
                Trace.log("S:   " + id + " prepare: " + incomingPipe.getId()
                        + "→tank tank=" + tankCurrentWater);
            } else {
                incomingPipe.setIsWaterInside(false);
                lastTickLoss = 1;
                Trace.log("S:   " + id + " OVERFLOW: tank full, water lost.");
            }
        }
    }

    /**
     * Legacy convenience — delegates to commitWater() then prepareWater().
     * Kept for backward compatibility; new simulation uses the two-phase API.
     */
    @Override
    public void moveWater() {
        commitWater();
        prepareWater();
    }

    /** Returns units of water lost to overflow this tick (call after moveWater). */
    public int getAndResetLastTickLoss() {
        int v = lastTickLoss; lastTickLoss = 0; return v;
    }

    public boolean isTankFull() {
        boolean full = tankCurrentWater >= tankCapacity;
        Trace.log("S: " + id + ".isTankFull() → " + full);
        return full;
    }

    public boolean isThereSpaceOccupant() {
        boolean space = occupants.size() < maxOccupants;
        Trace.log("S: " + id + ".isThereSpaceOccupant() → " + space);
        return space;
    }

    public PumpStatus getStatus() { return status; }
    public void setStatus(PumpStatus s) { this.status = s; }

    public Pipe getIncomingPipe() { return incomingPipe; }
    public void setIncomingPipe(Pipe p) { this.incomingPipe = p; }

    public Pipe getOutgoingPipe() { return outgoingPipe; }
    public void setOutgoingPipe(Pipe p) { this.outgoingPipe = p; }

    public int getTankCapacity() { return tankCapacity; }
    public int getTankCurrentWater() { return tankCurrentWater; }
    public void setTankCurrentWater(int w) { this.tankCurrentWater = w; }

    public List<Player> getOccupants() { return occupants; }
    public int getMaxOccupants() { return maxOccupants; }
    public int getMaxPorts() { return maxPorts; }

    @Override public String toString() { return id; }
}
