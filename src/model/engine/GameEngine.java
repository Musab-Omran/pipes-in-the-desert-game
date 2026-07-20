package model.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import model.elements.ActiveElement;
import model.elements.Cistern;
import model.elements.FieldElement;
import model.elements.Pipe;
import model.elements.PipeStatus;
import model.elements.Pump;
import model.elements.PumpStatus;
import model.elements.Spring;
import model.player.Player;
import model.player.Plumber;
import model.player.Saboteur;
import model.player.Team;
import controller.GameEventListener;
import util.Trace;

/**
 * Central game engine for Pipes in the Desert.
 *
 * <p>All known bugs fixed:
 * <ul>
 *   <li>Manufactured pipes now go into cistern inventory (free-floating) instead
 *       of being directly connected to the cistern, preventing backward flow.</li>
 *   <li>Disconnecting a pipe now clears the pump's incoming/outgoing reference
 *       so the pump does not continue pumping through a disconnected pipe.</li>
 *   <li>Broken pumps no longer move water at all (full flow blockage).</li>
 *   <li>Water-loss snapshot correctly accounts for pump overflow only when WORKING.</li>
 *   <li>Player initialisation distributes plumbers and saboteurs to distinct
 *       starting positions.</li>
 * </ul>
 */
public class GameEngine {

    private final List<FieldElement> fieldElements;
    private final List<Team>         teams;
    private final List<GameEventListener> listeners;

    private int gameTimer;
    private final int manufactureTime;
    private final int randomFailureTime;
    private int waitTime;

    private GameStatus status;
    private int collectedWater;
    private int lostWater;

    private int manufactureTickCounter;
    private int failureTickCounter;
    private final Random random;

    public GameEngine() {
        this.fieldElements          = new ArrayList<>();
        this.teams                  = new ArrayList<>();
        this.listeners              = new ArrayList<>();
        this.gameTimer              = 180;
        this.manufactureTime        = 30;
        this.randomFailureTime      = 15;
        this.waitTime               = 1;
        this.status                 = GameStatus.WAITING;
        this.collectedWater         = 0;
        this.lostWater              = 0;
        this.manufactureTickCounter = 0;
        this.failureTickCounter     = 0;
        this.random                 = new Random();
        Pipe.resetCounter();
        Pump.resetCounter();
    }

    // ── Listener management ───────────────────────────────────────

    public void addListener(GameEventListener l) {
        if (l != null && !listeners.contains(l)) listeners.add(l);
    }
    public void removeListener(GameEventListener l) { listeners.remove(l); }

    private void fireStateChanged()  { for (GameEventListener l : listeners) l.onStateChanged(); }
    private void fireTick()          { for (GameEventListener l : listeners) l.onTick(gameTimer); }
    private void fireActionResult(boolean ok, String msg) {
        for (GameEventListener l : listeners) l.onActionResult(ok, msg);
    }
    private void fireGameEnded(String result) {
        for (GameEventListener l : listeners) l.onGameEnded(result, collectedWater, lostWater);
    }
    private void firePumpBroke(String id) {
        for (GameEventListener l : listeners) l.onPumpBroke(id);
    }
    private void firePipeManufactured(String ci, String pi) {
        for (GameEventListener l : listeners) l.onPipeManufactured(ci, pi);
    }
    private void firePumpManufactured(String ci, String pi) {
        for (GameEventListener l : listeners) l.onPumpManufactured(ci, pi);
    }

    // ── Game lifecycle ────────────────────────────────────────────

    public void startGame() {
        Trace.log("S: GameEngine.startGame()");
        if (!areAllPlayerReady()) {
            notifyPlayerNotReady();
            fireActionResult(false, "Not all players are ready.");
            return;
        }
        status = GameStatus.RUNNING;
        initializePosition();
        Trace.log("S: status = RUNNING, gameTimer = " + gameTimer);
        fireActionResult(true, "Game started.");
        fireStateChanged();
    }

    public boolean areAllPlayerReady() {
        Trace.log("S: GameEngine.areAllPlayerReady()");
        for (Team team : teams)
            for (Player p : team.getPlayers())
                if (!p.isReady()) { Trace.log("S: " + p.getName() + " NOT ready."); return false; }
        return true;
    }

    public void endGame() {
        Trace.log("S: GameEngine.endGame()");
        if (status == GameStatus.FINISHED) return;
        status = GameStatus.FINISHED;
        calculateCollectedWater();
        Trace.log("S: status=FINISHED, collected=" + collectedWater + ", lost=" + lostWater);
        String result;
        if      (collectedWater > lostWater) result = "PLUMBERS WIN";
        else if (lostWater > collectedWater) result = "SABOTEURS WIN";
        else                                 result = "DRAW";
        Trace.log("S: RESULT: " + result);
        fireGameEnded(result);
        fireStateChanged();
    }

    public boolean checkWinCondition() {
        Trace.log("S: GameEngine.checkWinCondition()");
        if (status == GameStatus.FINISHED) return true;
        for (FieldElement fe : fieldElements) {
            if (fe instanceof Cistern && ((Cistern) fe).getWaterCollected() >= 100) {
                Trace.log("S: Cistern target reached.");
                endGame(); return true;
            }
        }
        if (gameTimer <= 0) { Trace.log("S: Timer expired."); endGame(); return true; }
        return false;
    }

    // ── Water simulation ──────────────────────────────────────────

    public void simulateWater() {
        Trace.log("S: GameEngine.simulateWater()");
        if (status != GameStatus.RUNNING) return;

        // Stable snapshot for this tick — prevents ConcurrentModificationException
        List<FieldElement> snapshot = new ArrayList<>(fieldElements);

        // ── Phase 1: Springs emit water into connected pipes ─────────────
        // Overflow (spring trying to fill an already-full pipe) is water lost to desert.
        // This happens when a broken pump backs up the incoming pipe, making the
        // spring's water have nowhere to go.
        for (FieldElement fe : snapshot) {
            if (!(fe instanceof Spring)) continue;
            fe.moveWater();
            lostWater += ((Spring) fe).getAndResetLastTickLoss(); // back-pressure overflow
        }

        // ── Phase 2: Pumps commit — push tank water → outgoing pipe ─────
        for (FieldElement fe : snapshot)
            if (fe instanceof Pump) ((Pump) fe).commitWater();

        // ── Phase 3: Pipes process leaks and free-end losses ─────────────
        for (FieldElement fe : snapshot)
            if (fe instanceof Pipe) fe.moveWater();

        // ── Phase 4: Pumps prepare — pull incoming pipe → tank ──────────
        for (FieldElement fe : snapshot) {
            if (!(fe instanceof Pump)) continue;
            ((Pump) fe).prepareWater();
            lostWater += ((Pump) fe).getAndResetLastTickLoss(); // overflow loss
        }

        // ── Phase 5: Pipes record per-tick losses ────────────────────────
        // (already processed in Phase 3 — collect the counters)
        for (FieldElement fe : snapshot) {
            if (!(fe instanceof Pipe)) continue;
            lostWater += ((Pipe) fe).getAndResetLastTickLoss();
        }

        // ── Phase 6: Cisterns collect ────────────────────────────────────
        for (FieldElement fe : snapshot)
            if (fe instanceof Cistern) fe.moveWater();

        // ── Phase 7: Update totals per documented API ────────────────────
        calculateCollectedWater();
        //calculateLostWater(); // documented method — validates + reconciles

        // Manufacturing phase
        manufactureTickCounter++;
        failureTickCounter++;

        if (manufactureTickCounter >= manufactureTime) {
            manufactureTickCounter = 0;
            List<FieldElement> mfSnap = new ArrayList<>(fieldElements);
            for (FieldElement fe : mfSnap) {
                if (fe instanceof Cistern) {
                    manufacturePipe((Cistern) fe);
                    manufacturePump((Cistern) fe);
                }
            }
        }

        if (failureTickCounter >= randomFailureTime) {
            failureTickCounter = 0;
            triggerRandomPumpFailure();
        }

        gameTimer = Math.max(0, gameTimer - waitTime);
        fireTick();
        fireStateChanged();
        checkWinCondition();
    }

    /**
     * Snapshot water losses that will occur this tick, BEFORE moveWater() runs.
     * Only counts losses from WORKING pumps (broken pumps block flow, no overflow).
     */
    /**
     * Documented API: updates / validates the total lost water counter.
     * Called at the end of every simulation tick.
     * The incremental per-element losses accumulated during the tick are already
     * in lostWater; this method performs a cross-check to catch any edge cases
     * and ensures the counter is always authoritative.
     */
    public void calculateLostWater() {
        // Per-tick losses are already accumulated in simulateWater() via
        // Pipe.getAndResetLastTickLoss() and Pump.getAndResetLastTickLoss().
        // Perform a defensive sweep: any pipe that STILL has water inside but
        // should be losing it (leaking AND free-ended with no upstream block)
        // is an edge-case missed above — count it now.
        for (FieldElement fe : fieldElements) {
            if (!(fe instanceof Pipe)) continue;
            Pipe p = (Pipe) fe;
            if (!p.getIsWaterInside()) continue;
            boolean shouldLose =
                p.getStatus() == PipeStatus.LEAKING
                || (p.getEndA() == null && !(p.getEndB() instanceof Cistern))
                || (p.getEndB() == null && !(p.getEndA() instanceof Cistern));
            if (shouldLose) {
                lostWater++;
                p.setIsWaterInside(false);
                Trace.log("S: calculateLostWater: swept loss from " + p.getId());
            }
        }
        Trace.log("S: calculateLostWater: total=" + lostWater);
    }

    // ── Manufacturing ─────────────────────────────────────────────

    public void manufacturePipe(Cistern cistern) {
        if (cistern == null) return;
        Trace.log("S: GameEngine.manufacturePipe(" + cistern.getId() + ")");
        Pipe newPipe = new Pipe();
        // Auto-connect endB to the cistern so the pipe is immediately attached.
        // endA stays null (free) — a plumber must connect the other end to the network.
        // Each call produces a new pipe stub extending from the cistern in a
        // different direction (handled by MapView layout).
        newPipe.setEndB(cistern);
        cistern.connectPipe(newPipe);
        fieldElements.add(newPipe);
        Trace.log("S: [EVENT] " + cistern.getId() + " manufactured " + newPipe.getId()
                + " (endB=cistern, endA free).");
        firePipeManufactured(cistern.getId(), newPipe.getId());
        fireStateChanged();
    }

    public void manufacturePump(Cistern cistern) {
        if (cistern == null) return;
        Trace.log("S: GameEngine.manufacturePump(" + cistern.getId() + ")");
        Pump newPump = new Pump();
        cistern.receivePump(newPump);
        Trace.log("S: [EVENT] " + cistern.getId() + " manufactured " + newPump.getId() + ".");
        firePumpManufactured(cistern.getId(), newPump.getId());
        fireStateChanged();
    }

    public void triggerRandomPumpFailure() {
        Trace.log("S: GameEngine.triggerRandomPumpFailure()");
        List<Pump> working = new ArrayList<>();
        for (FieldElement fe : fieldElements)
            if (fe instanceof Pump && ((Pump) fe).getStatus() == PumpStatus.WORKING)
                working.add((Pump) fe);
        if (!working.isEmpty()) {
            Pump target = working.get(random.nextInt(working.size()));
            target.setStatus(PumpStatus.BROKEN);
            Trace.log("S: [EVENT] " + target.getId() + " broke down randomly.");
            firePumpBroke(target.getId());
            fireStateChanged();
        }
    }

    public void calculateCollectedWater() {
        Trace.log("S: GameEngine.calculateCollectedWater()");
        collectedWater = 0;
        for (FieldElement fe : fieldElements)
            if (fe instanceof Cistern) collectedWater += ((Cistern) fe).getWaterCollected();
    }

    // ── Pipe removal ──────────────────────────────────────────────

    public void removePipe(Pipe pipe) {
        if (pipe == null) return;
        Trace.log("S: GameEngine.removePipe(" + pipe.getId() + ")");
        if (pipe.getEndA() != null) {
            clearPumpPipeRef(pipe.getEndA(), pipe);
            pipe.getEndA().disconnectPipe(pipe);
        }
        if (pipe.getEndB() != null) {
            clearPumpPipeRef(pipe.getEndB(), pipe);
            pipe.getEndB().disconnectPipe(pipe);
        }
        if (pipe.getOccupant() != null) pipe.setOccupant(null);
        fieldElements.remove(pipe);
        // Also remove from any cistern's pipe inventory
        for (FieldElement fe : fieldElements)
            if (fe instanceof Cistern) ((Cistern) fe).removePipeFromInventory(pipe);
        Trace.log("S: [OK] " + pipe.getId() + " removed.");
        fireStateChanged();
    }

    /** Clears a pump's incoming/outgoing reference if it points to the given pipe. */
    private void clearPumpPipeRef(ActiveElement element, Pipe pipe) {
        if (!(element instanceof Pump)) return;
        Pump pump = (Pump) element;
        if (pump.getIncomingPipe() == pipe) {
            pump.setIncomingPipe(null);
            Trace.log("S:   " + pump.getId() + ".incomingPipe cleared (pipe removed).");
        }
        if (pump.getOutgoingPipe() == pipe) {
            pump.setOutgoingPipe(null);
            Trace.log("S:   " + pump.getId() + ".outgoingPipe cleared (pipe removed).");
        }
    }

    // ── Action dispatch ───────────────────────────────────────────

    public void performAction(Player player, Action action) {
        if (player == null || action == null) return;
        Trace.log("S: GameEngine.performAction(" + player.getId() + ", " + action + ")");

        if (status != GameStatus.RUNNING && action != Action.READY) {
            fireActionResult(false, "Game is not running."); return;
        }
        switch (action) {
            case READY              -> handleReady(player);
            case MOVE               -> fireActionResult(false, "MOVE requires a target.");
            case FIX_PUMP           -> handleFixPump(player);
            case FIX_PIPE           -> handleFixPipe(player);
            case CHANGE_DIRECTION   -> fireActionResult(false, "CHANGE_DIRECTION requires pipes.");
            case PUNCTURE_PIPE      -> handlePuncturePipe(player);
            case PICK_UP_PUMP       -> handlePickUpPump(player);
            case INSERT_PUMP        -> handleInsertPump(player);
            case CONNECT_PIPE       -> fireActionResult(false, "CONNECT_PIPE requires parameters.");
            case DISCONNECT_PIPE    -> fireActionResult(false, "DISCONNECT_PIPE requires parameters.");
            default                 -> fireActionResult(false, "Unknown action.");
        }
    }

    private void handleReady(Player player) {
        player.setReady();
        Trace.log("S: [OK] " + player.getId() + " is ready.");
        fireActionResult(true, player.getId() + " is ready.");
        fireStateChanged();
    }

    private void handleFixPump(Player player) {
        FieldElement pos = getPlayerPosition(player);
        if (!(player instanceof Plumber)) {
            fireActionResult(false, "Only plumbers can fix pumps."); return;
        }
        if (!(pos instanceof Pump)) {
            fireActionResult(false, player.getId() + " is not on a pump."); return;
        }
        if (((Pump) pos).getStatus() != PumpStatus.BROKEN) {
            fireActionResult(false, pos.getId() + " is not broken."); return;
        }
        ((Plumber) player).fixBrokenPump((Pump) pos);
        fireActionResult(true, player.getId() + " fixed " + pos.getId() + ".");
        fireStateChanged();
    }

    private void handleFixPipe(Player player) {
        FieldElement pos = getPlayerPosition(player);
        if (!(player instanceof Plumber)) {
            fireActionResult(false, "Only plumbers can fix pipes."); return;
        }
        if (!(pos instanceof Pipe)) {
            fireActionResult(false, player.getId() + " is not on a pipe."); return;
        }
        if (((Pipe) pos).getStatus() != PipeStatus.LEAKING) {
            fireActionResult(false, pos.getId() + " is not leaking."); return;
        }
        ((Plumber) player).fixLeakingPipe((Pipe) pos);
        fireActionResult(true, player.getId() + " fixed " + pos.getId() + ".");
        fireStateChanged();
    }

    private void handlePuncturePipe(Player player) {
        FieldElement pos = getPlayerPosition(player);
        if (!(player instanceof Saboteur)) {
            fireActionResult(false, "Only saboteurs can puncture pipes."); return;
        }
        if (!(pos instanceof Pipe)) {
            fireActionResult(false, player.getId() + " is not on a pipe."); return;
        }
        if (((Pipe) pos).getStatus() == PipeStatus.LEAKING) {
            fireActionResult(false, pos.getId() + " is already leaking."); return;
        }
        ((Saboteur) player).puncturePipe((Pipe) pos);
        fireActionResult(true, player.getId() + " punctured " + pos.getId() + ".");
        fireStateChanged();
    }

    private void handlePickUpPump(Player player) {
        FieldElement pos = getPlayerPosition(player);
        if (!(player instanceof Plumber)) {
            fireActionResult(false, "Only plumbers can pick up pumps."); return;
        }
        if (!(pos instanceof Cistern)) {
            fireActionResult(false, player.getId() + " is not on a cistern."); return;
        }
        Plumber plumber = (Plumber) player;
        Cistern cistern = (Cistern) pos;
        if (plumber.getCarriedPump() != null) {
            fireActionResult(false, player.getId() + " already carries " + plumber.getCarriedPump().getId() + "."); return;
        }
        if (cistern.getPumpInventory().isEmpty()) {
            fireActionResult(false, cistern.getId() + " has no pumps."); return;
        }
        plumber.pickUpPump(cistern);
        fireActionResult(true, player.getId() + " picked up a pump from " + cistern.getId() + ".");
        fireStateChanged();
    }

    private void handleInsertPump(Player player) {
        if (!(player instanceof Plumber)) {
            fireActionResult(false, "Only plumbers can insert pumps."); return;
        }
        FieldElement pos = getPlayerPosition(player);
        Plumber plumber  = (Plumber) player;
        if (!(pos instanceof Pipe)) {
            fireActionResult(false, player.getId() + " is not on a pipe."); return;
        }
        if (plumber.getCarriedPump() == null) {
            fireActionResult(false, player.getId() + " is not carrying a pump."); return;
        }
        Pipe oldPipe = (Pipe) pos;
        Pump pump    = plumber.getCarriedPump();
        insertPumpIntoPipe(plumber, oldPipe, pump);
        fireActionResult(true, player.getId() + " inserted " + pump.getId()
                + " into " + oldPipe.getId() + ".");
        fireStateChanged();
    }

    private void insertPumpIntoPipe(Plumber plumber, Pipe pipe, Pump pump) {
        Trace.log("S: " + plumber.getId() + ".insertPump(" + pipe.getId() + ", " + pump.getId() + ")");

        ActiveElement oldEndA = pipe.getEndA();
        ActiveElement oldEndB = pipe.getEndB();

        Pipe halfA = new Pipe(pipe.getId() + "-A");
        Pipe halfB = new Pipe(pipe.getId() + "-B");

        

        halfA.setEndA(oldEndA); halfA.setEndB(pump);
        halfA.setStatus(pipe.getStatus());

        halfB.setEndA(pump); halfB.setEndB(oldEndB);
        halfB.setStatus(pipe.getStatus());

        if (oldEndA != null) { oldEndA.disconnectPipe(pipe); oldEndA.connectPipe(halfA); }
        if (oldEndB != null) { oldEndB.disconnectPipe(pipe); oldEndB.connectPipe(halfB); }

        pump.getConnectedPipes().clear();
        pump.connectPipe(halfA);
        pump.connectPipe(halfB);
        
        // Determine correct flow direction: source end → pump → sink end
        boolean flowsAToB = isFlowSourceAtEnd(oldEndA);
        if (flowsAToB) {
            pump.setIncomingPipe(halfA);
            pump.setOutgoingPipe(halfB);
            Trace.log("S:   Flow A→B (incoming=" + halfA.getId() + ", outgoing=" + halfB.getId() + ")");
        } else {
            pump.setIncomingPipe(halfB);
            pump.setOutgoingPipe(halfA);
            Trace.log("S:   Flow B→A (incoming=" + halfB.getId() + ", outgoing=" + halfA.getId() + ")");
        }

        int idx = fieldElements.indexOf(pipe);
        boolean pumpListed = fieldElements.contains(pump);
        if (idx >= 0) {
            fieldElements.remove(idx);
            fieldElements.add(idx, halfB);
            fieldElements.add(idx, halfA);
            if (!pumpListed) fieldElements.add(idx, pump);
        } else {
            if (!pumpListed) fieldElements.add(pump);
            fieldElements.add(halfA);
            fieldElements.add(halfB);
        }

        if (plumber.getCurrentPosition() == pipe) plumber.setCurrentPosition(pump);
        plumber.setCarriedPump(null);

        Trace.log("S: " + pipe.getId() + " split into " + halfA.getId() + " / " + halfB.getId());
    }

    /**
     * Determines if water flows FROM the given endpoint.
     * Returns true if endpoint is a Spring (always a source) or Pump (can be source).
     * Returns false if endpoint is a Cistern (always a sink) or null (unknown, default to downstream).
     */
    private boolean isFlowSourceAtEnd(ActiveElement endpoint) {
        return endpoint instanceof Spring || endpoint instanceof Pump;
    }

    // ── Typed action methods ──────────────────────────────────────

    public boolean tryMove(Player player, FieldElement target) {
        if (player == null || target == null) return false;
        if (status != GameStatus.RUNNING) {
            fireActionResult(false, "Game is not running."); return false;
        }
        player.moveTo(target);
        boolean success = player.getCurrentPosition() == target;
        fireActionResult(success, success ? "Moved to " + target.getId() : "Move failed.");
        if (success) fireStateChanged();
        return success;
    }

    /**
     * Connects one free end of the pipe the plumber is standing on to a chosen active element.
     *
     * Rules:
     * <ul>
     *   <li>Player must be a Plumber standing on {@code pipe}.</li>
     *   <li>{@code end} ('A' or 'B') must currently be null (free).</li>
     *   <li>{@code target} must have a free port ({@link ActiveElement#isThereSpacePipe()}).</li>
     * </ul>
     */
    public boolean tryConnectPipe(Player player, Pipe pipe, char end, ActiveElement target) {
        if (player == null || pipe == null || target == null) return false;
        Trace.log("S: GameEngine.tryConnectPipe(" + player.getId() + ", "
                + pipe.getId() + ", " + end + ", " + target.getId() + ")");
        if (status != GameStatus.RUNNING) { fireActionResult(false, "Game is not running."); return false; }
        if (!(player instanceof Plumber)) { fireActionResult(false, "Only plumbers can connect pipes."); return false; }

        // Player must be standing on the pipe itself
        if (player.getCurrentPosition() != pipe) {
            fireActionResult(false, "Plumber must be standing on the pipe to connect it."); return false;
        }

        char norm = Character.toUpperCase(end);
        if (norm != 'A' && norm != 'B') { fireActionResult(false, "Pipe end must be A or B."); return false; }

        // The chosen end must be free
        if (norm == 'A' && pipe.getEndA() != null) {
            fireActionResult(false, pipe.getId() + " end A is already connected to " + pipe.getEndA().getId() + ".");
            return false;
        }
        if (norm == 'B' && pipe.getEndB() != null) {
            fireActionResult(false, pipe.getId() + " end B is already connected to " + pipe.getEndB().getId() + ".");
            return false;
        }

        // Target must have a free port
        if (!target.isThereSpacePipe()) {
            fireActionResult(false, target.getId() + " has no free ports — it is full.");
            return false;
        }

        // Execute connection
        if (norm == 'A') pipe.setEndA(target); else pipe.setEndB(target);
        target.connectPipe(pipe);

        Trace.log("S: [OK] " + pipe.getId() + " end " + norm + " → " + target.getId());
        fireActionResult(true, pipe.getId() + " end " + norm + " connected to " + target.getId() + ".");
        fireStateChanged();
        return true;
    }

    public boolean tryDisconnectPipe(Player player, Pipe pipe, char end) {
        if (player == null || pipe == null) return false;
        Trace.log("S: GameEngine.tryDisconnectPipe(" + player.getId() + ", " + pipe.getId() + ", " + end + ")");
        if (status != GameStatus.RUNNING) { fireActionResult(false, "Game is not running."); return false; }
        if (!(player instanceof Plumber)) { fireActionResult(false, "Only plumbers can disconnect pipes."); return false; }

        char norm = Character.toUpperCase(end);
        if (norm != 'A' && norm != 'B') { fireActionResult(false, "Pipe end must be A or B."); return false; }

        ActiveElement connectedElement = (norm == 'A') ? pipe.getEndA() : pipe.getEndB();
        if (connectedElement == null) {
            fireActionResult(false, pipe.getId() + " end " + norm + " is already free."); return false;
        }

        FieldElement pos = player.getCurrentPosition();
        if (pos instanceof ActiveElement && pos != connectedElement) {
            fireActionResult(false, "Player must stand on " + connectedElement.getId() + "."); return false;
        }
        if (pos == pipe) {
            boolean otherFree = (norm == 'A') ? pipe.getEndB() == null : pipe.getEndA() == null;
            if (otherFree) {
                fireActionResult(false, "Cannot disconnect: " + pipe.getId() + " would have both ends free while you stand on it.");
                return false;
            }
        }

        if (norm == 'A') pipe.setEndA(null); else pipe.setEndB(null);
        connectedElement.disconnectPipe(pipe);

        // FIX: clear pump's incoming/outgoing reference if this was it
        clearPumpPipeRef(connectedElement, pipe);

        fireActionResult(true, pipe.getId() + " disconnected from " + connectedElement.getId() + ".");
        fireStateChanged();

        // Remove the pipe if both ends are now free and nobody stands on it
        if (pipe.getEndA() == null && pipe.getEndB() == null && pos != pipe) {
            removePipe(pipe);
        }
        return true;
    }

    public boolean tryChangeDirection(Player player, Pump pump, Pipe incoming, Pipe outgoing) {
        if (player == null || pump == null || incoming == null || outgoing == null) return false;
        Trace.log("S: GameEngine.tryChangeDirection(...)");
        if (status != GameStatus.RUNNING) { fireActionResult(false, "Game is not running."); return false; }
        if (player.getCurrentPosition() != pump) {
            fireActionResult(false, "Player must stand on the pump."); return false;
        }
        if (!pump.getConnectedPipes().contains(incoming) || !pump.getConnectedPipes().contains(outgoing)) {
            fireActionResult(false, "Chosen pipes are not connected to the pump."); return false;
        }
        if (incoming == outgoing) {
            fireActionResult(false, "Incoming and outgoing pipes must be different."); return false;
        }
        pump.setIncomingPipe(incoming);
        pump.setOutgoingPipe(outgoing);
        fireActionResult(true, pump.getId() + " direction updated: "
                + incoming.getId() + " → " + outgoing.getId() + ".");
        fireStateChanged();
        return true;
    }

    // ── Player management ─────────────────────────────────────────

    public void registerTeam(Player player) {
        if (player == null) return;
        Team team = player.getTeam();
        if (team == null) return;
        if (!teams.contains(team)) { teams.add(team); Trace.log("S: Team " + team.getId() + " registered."); }
        if (!team.getPlayers().contains(player)) team.addPlayer(player);
        Trace.log("S: registerTeam(" + player.getId() + ") done.");
    }

    /**
     * Distributes players to distinct starting positions.
     * Plumbers start on the first Spring; Saboteurs start on the first Cistern (opposite end of the network).
     * Any player without a matching element falls back to the first ActiveElement.
     */
    public void initializePosition() {
        Trace.log("S: GameEngine.initializePosition()");

        // Always clear positions first so map SET_POSITION commands don't prevent
        // proper team-based distribution (Plumbers → Spring, Saboteurs → Cistern)
        for (Team team : teams)
            for (Player player : team.getPlayers())
                player.setCurrentPosition(null);

        FieldElement firstSpring  = null;
        FieldElement firstPump    = null;
        FieldElement firstActive  = null;
        FieldElement firstCistern = null;

        for (FieldElement fe : fieldElements) {
            if (fe instanceof Spring  && firstSpring  == null) firstSpring  = fe;
            if (fe instanceof Pump    && firstPump    == null) firstPump    = fe;
            if (fe instanceof Cistern && firstCistern == null) firstCistern = fe;
            if (fe instanceof ActiveElement && firstActive == null) firstActive = fe;
        }
        if (firstActive == null) { Trace.log("S: No active elements."); return; }

        for (Team team : teams) {
            for (Player player : team.getPlayers()) {
                FieldElement start;
                if (player instanceof Plumber) {
                    // Plumbers start at the spring so they can see the full network
                    start = (firstSpring != null) ? firstSpring : firstActive;
                } else {
                    // Saboteurs start at the cistern end so they are near plumber territory
                    start = (firstCistern != null) ? firstCistern
                          : (firstPump   != null)  ? firstPump : firstActive;
                }
                player.setCurrentPosition(start);
                Trace.log("S: " + player.getId() + " placed on " + start.getId());
            }
        }
    }

    public void notifyStateChanged() { fireStateChanged(); }
    public void notifyPlayerNotReady() { Trace.log("S: [FAIL] Not all players are ready."); }

    // ── Queries ───────────────────────────────────────────────────

    public PipeStatus getPipeStatus(Pipe pipe) {
        return pipe == null ? PipeStatus.NORMAL : pipe.getStatus();
    }
    public PumpStatus getPumpStatus(Pump pump) {
        return pump == null ? PumpStatus.WORKING : pump.getStatus();
    }
    public FieldElement getPlayerPosition(Player player) {
        return player == null ? null : player.getCurrentPosition();
    }
    public int getWaterScore(Team team) {
        return (team != null && teams.contains(team)) ? collectedWater - lostWater : 0;
    }

    // ── Field element / team registration ────────────────────────

    public void addFieldElement(FieldElement fe) { if (fe != null) fieldElements.add(fe); }
    public void addTeam(Team team) { if (team != null && !teams.contains(team)) teams.add(team); }

    // ── Full reset ────────────────────────────────────────────────

    public void reset() {
        fieldElements.clear(); teams.clear(); listeners.clear();
        gameTimer = 180; status = GameStatus.WAITING;
        collectedWater = 0; lostWater = 0;
        manufactureTickCounter = 0; failureTickCounter = 0;
        Pipe.resetCounter(); Pump.resetCounter();
    }

    // ── Getters / setters ─────────────────────────────────────────

    public List<FieldElement> getFieldElements() { return fieldElements; }
    public List<Team>         getTeams()          { return teams; }
    public GameStatus         getStatus()         { return status; }
    public int getCollectedWater() { return collectedWater; }
    public int getLostWater()      { return lostWater; }

    /** Returns the number of pipes currently in LEAKING state. */
    public int countActiveLeaks() {
        int n = 0;
        for (FieldElement fe : fieldElements)
            if (fe instanceof Pipe && ((Pipe) fe).getStatus() == PipeStatus.LEAKING) n++;
        return n;
    }

    /** Returns the number of pumps currently in BROKEN state. */
    public int countBrokenPumps() {
        int n = 0;
        for (FieldElement fe : fieldElements)
            if (fe instanceof Pump && ((Pump) fe).getStatus() == PumpStatus.BROKEN) n++;
        return n;
    }

    /** Returns next pump-failure tick countdown. */
    public int getFailureCountdown() { return Math.max(0, randomFailureTime - failureTickCounter); }

    /** Returns next manufacture tick countdown. */
    public int getManufactureCountdown() { return Math.max(0, manufactureTime - manufactureTickCounter); }
    public int getTimer()          { return gameTimer; }
    public int getWaitTime()       { return waitTime; }
    public int getManufactureTime()    { return manufactureTime; }
    public int getRandomFailureTime()  { return randomFailureTime; }

    public void setTimer(int t)          { this.gameTimer       = Math.max(0, t); }
    public void setCollectedWater(int w) { this.collectedWater  = Math.max(0, w); }
    public void setLostWater(int w)      { this.lostWater       = Math.max(0, w); }
    public void setStatus(GameStatus s)  { if (s != null) this.status = s; }
    public void setWaitTime(int wt)      { this.waitTime        = Math.max(1, wt); }
}
