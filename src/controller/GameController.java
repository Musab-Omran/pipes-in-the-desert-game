package controller;

import model.elements.FieldElement;
import model.elements.ActiveElement;
import model.elements.Pipe;
import model.elements.Pump;
import model.engine.Action;
import model.engine.GameEngine;
import model.player.Player;
import util.Trace;

/**
 * Central controller between the Swing GUI and the GameEngine.
 * <p>
 * The controller exposes user-intent methods and keeps GUI code out of
 * the model layer.
 */
public class GameController {

    private final GameEngine engine;
    private Player currentPlayer;
    private FieldElement selectedTarget;

    /**
     * Creates a new controller for the given engine.
     *
     * @param engine game engine
     */
    public GameController(GameEngine engine) {
        this.engine = engine;
    }

    /**
     * Returns the wrapped engine.
     *
     * @return game engine
     */
    public GameEngine getEngine() {
        return engine;
    }

    /**
     * Adds a model listener.
     *
     * @param listener listener to add
     */
    public void addListener(GameEventListener listener) {
        engine.addListener(listener);
    }

    /**
     * Removes a model listener.
     *
     * @param listener listener to remove
     */
    public void removeListener(GameEventListener listener) {
        engine.removeListener(listener);
    }

    /**
     * Returns the current player.
     *
     * @return current player
     */
    public Player getCurrentPlayer() {
        return currentPlayer;
    }

    /**
     * Sets the current player.
     *
     * @param currentPlayer player to select
     */
    public void setCurrentPlayer(Player currentPlayer) {
        this.currentPlayer = currentPlayer;
        // Clear stale target selection: PI-1 selected for PL-1 should not
        // remain highlighted when the user switches to SA-2.
        this.selectedTarget = null;
        // Notify all listeners so MapView, StatusPanel and ControlPanel refresh.
        engine.notifyStateChanged();
    }

    /**
     * Returns the currently selected target element.
     *
     * @return selected target
     */
    public FieldElement getSelectedTarget() {
        return selectedTarget;
    }

    /**
     * Sets the selected target.
     *
     * @param selectedTarget target element
     */
    public void setSelectedTarget(FieldElement selectedTarget) {
        this.selectedTarget = selectedTarget;
    }

    /**
     * Clears selection state.
     */
    public void clearSelection() {
        selectedTarget = null;
    }

    /**
     * Starts the game.
     */
    public void startGame() {
        engine.startGame();
        if (currentPlayer == null && !engine.getTeams().isEmpty() && !engine.getTeams().get(0).getPlayers().isEmpty()) {
            currentPlayer = engine.getTeams().get(0).getPlayers().get(0);
        }
    }

    /**
     * Ends the game.
     */
    public void endGame() {
        engine.endGame();
    }

    /**
     * Advances the game simulation by one tick.
     */
    public void tick() {
        engine.simulateWater();
    }

    /**
     * Moves the current player to the selected target.
     *
     * @return true if the movement succeeded
     */
    public boolean moveSelected() {
        if (currentPlayer == null || selectedTarget == null) {
            return false;
        }
        return engine.tryMove(currentPlayer, selectedTarget);
    }

    /**
     * Requests a move to the given target.
     *
     * @param target target element
     * @return true if success
     */
    public boolean move(FieldElement target) {
        if (currentPlayer == null) {
            return false;
        }
        return engine.tryMove(currentPlayer, target);
    }

    /**
     * Fixes the current pump.
     *
     * @return true if action executed
     */
    public boolean fixPump() {
        if (currentPlayer == null) {
            return false;
        }
        engine.performAction(currentPlayer, Action.FIX_PUMP);
        return true;
    }

    /**
     * Fixes the current pipe.
     *
     * @return true if action executed
     */
    public boolean fixPipe() {
        if (currentPlayer == null) {
            return false;
        }
        engine.performAction(currentPlayer, Action.FIX_PIPE);
        return true;
    }

    /**
     * Punctures the current pipe.
     *
     * @return true if action executed
     */
    public boolean puncturePipe() {
        if (currentPlayer == null) {
            return false;
        }
        engine.performAction(currentPlayer, Action.PUNCTURE_PIPE);
        return true;
    }

    /**
     * Picks up a pump from the current cistern.
     *
     * @return true if action executed
     */
    public boolean pickUpPump() {
        if (currentPlayer == null) {
            return false;
        }
        engine.performAction(currentPlayer, Action.PICK_UP_PUMP);
        return true;
    }

    /**
     * Inserts the carried pump into the current pipe.
     *
     * @return true if action executed
     */
    public boolean insertPump() {
        if (currentPlayer == null) {
            return false;
        }
        engine.performAction(currentPlayer, Action.INSERT_PUMP);
        return true;
    }

    /**
     * Connects a free end of the pipe the plumber stands on to the chosen active element.
     *
     * @param pipe  the pipe the plumber is currently standing on
     * @param end   which end of the pipe to connect ('A' or 'B') — must be free
     * @param target the active element to connect to — must have a free port
     * @return true if success
     */
    public boolean connectPipe(Pipe pipe, char end, ActiveElement target) {
        if (currentPlayer == null) {
            return false;
        }
        return engine.tryConnectPipe(currentPlayer, pipe, end, target);
    }

    /**
     * Disconnects a pipe end from the current active element.
     *
     * @param pipe pipe to disconnect
     * @param end selected end
     * @return true if success
     */
    public boolean disconnectPipe(Pipe pipe, char end) {
        if (currentPlayer == null) {
            return false;
        }
        return engine.tryDisconnectPipe(currentPlayer, pipe, end);
    }

    /**
     * Changes pump direction from the current player position.
     *
     * @param incoming incoming pipe
     * @param outgoing outgoing pipe
     * @return true if success
     */
    public boolean changeDirection(Pipe incoming, Pipe outgoing) {
        if (currentPlayer == null || !(currentPlayer.getCurrentPosition() instanceof Pump)) {
            return false;
        }
        return engine.tryChangeDirection(currentPlayer, (Pump) currentPlayer.getCurrentPosition(), incoming, outgoing);
    }

    /**
     * Marks a player as ready.
     *
     * @param player target player
     */
    public void ready(Player player) {
        if (player != null) {
            engine.performAction(player, Action.READY);
        }
    }

    /**
     * Returns the selected target or null.
     *
     * @return selected target
     */
    public FieldElement getTarget() {
        return selectedTarget;
    }

    /**
     * Logs a simple controller message.
     *
     * @param text message
     */
    public void log(String text) {
        Trace.log(text);
    }
}
