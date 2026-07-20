package controller;

/**
 * Observer interface for GUI components that need to react to model changes.
 */
public interface GameEventListener {

    /**
     * Called whenever a visible state change occurs.
     */
    void onStateChanged();

    /**
     * Called once per simulation tick.
     *
     * @param remainingSeconds remaining game time
     */
    void onTick(int remainingSeconds);

    /**
     * Called after an action succeeds or fails.
     *
     * @param success true if the action succeeded
     * @param message action result
     */
    void onActionResult(boolean success, String message);

    /**
     * Called when the game ends.
     *
     * @param result result message
     * @param collected collected water
     * @param lost lost water
     */
    void onGameEnded(String result, int collected, int lost);

    /**
     * Called when a random pump failure occurs.
     *
     * @param pumpId pump ID
     */
    void onPumpBroke(String pumpId);

    /**
     * Called when a pipe is manufactured.
     *
     * @param cisternId cistern ID
     * @param pipeId pipe ID
     */
    void onPipeManufactured(String cisternId, String pipeId);

    /**
     * Called when a pump is manufactured.
     *
     * @param cisternId cistern ID
     * @param pumpId pump ID
     */
    void onPumpManufactured(String cisternId, String pumpId);
}
