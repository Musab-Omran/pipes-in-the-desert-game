package model.elements;

import java.util.ArrayList;
import java.util.List;
import util.Trace;

/**
 * A spring continuously injects water into its connected pipes.
 *
 * Water-loss rule: if a connected pipe is already full (blocked downstream by
 * a broken pump, wrong direction, etc.) the spring cannot deliver water there.
 * That unit of water is lost to the desert — tracked via lastTickLoss so the
 * GameEngine can add it to the global lostWater counter each tick.
 *
 * This means: a broken pump causes "back-pressure" at the spring and makes the
 * spring water overflow, which IS counted as water loss even though the pump
 * itself is not leaking.
 */
public class Spring extends ActiveElement {
    private int maxPorts;
    private int transferRate;
    private int lastTickLoss;   // units lost this tick due to full downstream pipes

    public Spring(String id, int maxPorts) {
        super(id); this.maxPorts = maxPorts; this.transferRate = 1;
        Trace.log("S: Spring created: " + id);
    }
    public Spring(String id, int maxPorts, int transferRate) {
        super(id); this.maxPorts = maxPorts; this.transferRate = transferRate;
        Trace.log("S: Spring created: " + id + " (transferRate=" + transferRate + ")");
    }

    @Override public boolean isThereSpacePipe() {
        return connectedPipes.size() < maxPorts;
    }

    /**
     * Pushes water into connected pipes.
     * If a pipe is already full, the water has nowhere to go and is lost
     * (spring overflow due to blocked downstream network).
     */
    @Override public void moveWater() {
        Trace.log("S: " + id + ".moveWater()");
        lastTickLoss = 0;
        List<Pipe> snapshot = new ArrayList<>(connectedPipes);
        int remaining = transferRate;
        for (Pipe pipe : snapshot) {
            if (remaining <= 0) break;
            if (!pipe.getIsWaterInside()) {
                // Empty pipe — fill it
                pipe.setIsWaterInside(true);
                remaining--;
                Trace.log("S:   " + id + " → " + pipe.getId() + " filled");
            } else {
                // Pipe already full — downstream is blocked (e.g. broken pump)
                // Water produced by spring has nowhere to go → lost to desert
                lastTickLoss++;
                Trace.log("S:   " + id + " → " + pipe.getId()
                        + " BLOCKED/FULL — spring overflow → water lost");
            }
        }
    }

    /**
     * Returns spring overflow losses this tick and resets the counter.
     * Call from GameEngine after moveWater().
     */
    public int getAndResetLastTickLoss() {
        int v = lastTickLoss;
        lastTickLoss = 0;
        return v;
    }

    public int getTransferRate() { return transferRate; }
    public void setTransferRate(int rate) { this.transferRate = rate; }
    public int getMaxPorts() { return maxPorts; }
}
