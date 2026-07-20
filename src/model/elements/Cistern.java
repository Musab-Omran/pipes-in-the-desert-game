package model.elements;

import java.util.ArrayList;
import java.util.List;
import util.Trace;

/** A cistern collects delivered water and stores manufactured parts. */
public class Cistern extends ActiveElement {
    private int waterCollected;
    private List<Pipe> pipeInventory;
    private List<Pump> pumpInventory;

    public Cistern(String id) {
        super(id);
        this.waterCollected = 0;
        this.pipeInventory  = new ArrayList<>();
        this.pumpInventory  = new ArrayList<>();
        Trace.log("S: Cistern created: " + id);
    }

    @Override public boolean isThereSpacePipe() { return true; }

    /**
     * Collects water from every connected pipe that contains water.
     * Uses a snapshot to prevent ConcurrentModificationException.
     */
    @Override public void moveWater() {
        Trace.log("S: " + id + ".moveWater()");
        List<Pipe> snapshot = new ArrayList<>(connectedPipes);
        for (Pipe pipe : snapshot) {
            if (pipe.getIsWaterInside()) {
                pipe.setIsWaterInside(false);
                waterCollected++;
                Trace.log("S:   " + id + ".waterCollected += 1 → " + waterCollected);
            }
        }
    }

    public void receiveWater(int amount) {
        Trace.log("S: " + id + ".receiveWater(" + amount + ")");
        waterCollected += amount;
    }

    /** Add a pipe to the cistern's staging inventory (not yet connected). */
    public void receivePipe(Pipe pipe) {
        if (pipe != null && !pipeInventory.contains(pipe)) {
            Trace.log("S: " + id + ".receivePipe(" + pipe.getId() + ")");
            pipeInventory.add(pipe);
        }
    }

    /** Remove a pipe from the staging inventory (called when it is fully connected). */
    public void removePipeFromInventory(Pipe pipe) {
        pipeInventory.remove(pipe);
    }

    public void receivePump(Pump pump) {
        if (pump != null) {
            Trace.log("S: " + id + ".receivePump(" + pump.getId() + ")");
            pumpInventory.add(pump);
        }
    }

    public int getWaterCollected() { return waterCollected; }
    public void setWaterCollected(int value) { this.waterCollected = Math.max(0, value); }
    public List<Pipe> getPipeInventory() { return pipeInventory; }
    public List<Pump> getPumpInventory() { return pumpInventory; }
}
