package model.elements;

import model.player.Player;
import util.Trace;

/** A pipe segment connecting two active elements. */
public class Pipe extends FieldElement {
    private static int counter = 0;

    private ActiveElement endA;
    private ActiveElement endB;
    private PipeStatus status;
    private Player occupant;
    private boolean isWaterInside;
    private int lastTickLoss;   // 1 if water was lost this tick, else 0

    public Pipe() {
        super("PI-" + (++counter));
        this.status = PipeStatus.NORMAL;
        this.occupant = null;
        this.isWaterInside = false;
        this.lastTickLoss = 0;
    }

    public Pipe(String id) {
        super(id);
        this.status = PipeStatus.NORMAL;
        this.occupant = null;
        this.isWaterInside = false;
        this.lastTickLoss = 0;
    }

    public static void resetCounter() { counter = 0; }

    /**
     * Handles water loss for leaking or free-ended pipes.
     * Water is lost if:
     * - The pipe is leaking (punctured), OR
     * - Either end is null and the other end is not a Cistern
     *   (water can only stay if it is about to be collected by a Cistern).
     */
    @Override
    public void moveWater() {
        Trace.log("S: " + id + ".moveWater()");
        Trace.log("S:   " + id + ".status → " + status);
        lastTickLoss = 0;

        if (!isWaterInside) {
            Trace.log("S:   " + id + " has no water — nothing to do.");
            return;
        }

        // Leaking pipe — water spills into the desert
        if (status == PipeStatus.LEAKING) {
            isWaterInside = false;
            lastTickLoss = 1;
            Trace.log("S:   " + id + ".isWaterInside = false  (leaking)");
            return;
        }

        // Free end A — water cannot enter from a source, so it is lost
        // UNLESS end B is a Cistern that will collect it in this same tick.
        if (endA == null && !(endB instanceof Cistern)) {
            isWaterInside = false;
            lastTickLoss = 1;
            Trace.log("S:   " + id + ".isWaterInside = false  (free end A)");
            return;
        }

        // Free end B — water cannot leave to a destination, so it is lost
        // UNLESS end A is a Cistern (symmetric case).
        if (endB == null && !(endA instanceof Cistern)) {
            isWaterInside = false;
            lastTickLoss = 1;
            Trace.log("S:   " + id + ".isWaterInside = false  (free end B)");
            return;
        }

        Trace.log("S:   [INFO] " + id + ": water flowing normally.");
    }

    /** Returns units of water lost this tick (call after moveWater). */
    public int getAndResetLastTickLoss() {
        int v = lastTickLoss; lastTickLoss = 0; return v;
    }

    public boolean isOccupied() {
        boolean result = (occupant != null);
        Trace.log("S: " + id + ".isOccupied() → " + result);
        return result;
    }

    public boolean isWaterFlowing() {
        return isWaterInside && status == PipeStatus.NORMAL;
    }

    public ActiveElement getEndA() { return endA; }
    public ActiveElement getEndB() { return endB; }
    public void setEndA(ActiveElement endA) { this.endA = endA; }
    public void setEndB(ActiveElement endB) { this.endB = endB; }
    public PipeStatus getStatus() { return status; }
    public void setStatus(PipeStatus status) { this.status = status; }
    public Player getOccupant() { return occupant; }
    public void setOccupant(Player occupant) { this.occupant = occupant; }
    public boolean getIsWaterInside() { return isWaterInside; }
    public void setIsWaterInside(boolean w) { this.isWaterInside = w; }

    @Override public String toString() { return id; }
}
