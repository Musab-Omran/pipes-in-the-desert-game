package util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import model.elements.ActiveElement;
import model.elements.Cistern;
import model.elements.FieldElement;
import model.elements.Pipe;
import model.elements.PipeStatus;
import model.elements.Pump;
import model.elements.PumpStatus;
import model.elements.Spring;
import model.engine.GameEngine;
import model.engine.GameStatus;
import model.player.Player;
import model.player.Plumber;
import model.player.Saboteur;
import model.player.Team;

/**
 * Loads a pipe-system description file into a GameEngine.
 * <p>
 * The parser supports the command-style configuration format used by the
 * prototype and the test folders.
 */
public final class MapLoader {

    private MapLoader() {
    }

    /**
     * Loads the default test map shipped with the project.
     *
     * @return loaded game engine
     */
    public static GameEngine loadDefault() {
        // 1. Filesystem path when running from the project root (IntelliJ / gradlew)
        Path path = Path.of("src", "model", "Test", "Test0", "pipeSystem.txt");
        if (Files.exists(path)) {
            return load(path);
        }

        // 2. When running from dist/ directory
        Path path2 = Path.of("..", "src", "model", "Test", "Test0", "pipeSystem.txt");
        if (Files.exists(path2)) {
            return load(path2);
        }

        // 3. Classpath resource inside the JAR (built by copying the map file to out/)
        for (String res : new String[]{
                "/model/Test/Test0/pipeSystem.txt",
                "/assets/default/pipeSystem.txt",
                "/pipeSystem.txt"}) {
            InputStream stream = MapLoader.class.getResourceAsStream(res);
            if (stream != null) {
                return load(stream);
            }
        }

        // 4. Built-in fallback with 4 players
        return createFallbackEngine();
    }

    /**
     * Loads a map from a filesystem path.
     *
     * @param path path to pipeSystem.txt
     * @return loaded game engine
     */
    public static GameEngine load(Path path) {
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return load(reader);
        } catch (IOException ex) {
            Trace.log("S: [FAIL] Could not load map: " + ex.getMessage());
            return createFallbackEngine();
        }
    }

    /**
     * Loads a map from a classpath resource stream.
     *
     * @param stream input stream
     * @return loaded game engine
     */
    public static GameEngine load(InputStream stream) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            return load(reader);
        } catch (IOException ex) {
            Trace.log("S: [FAIL] Could not load map: " + ex.getMessage());
            return createFallbackEngine();
        }
    }

    private static GameEngine load(BufferedReader reader) throws IOException {
        GameEngine engine = new GameEngine();

        Map<String, FieldElement> elements = new HashMap<>();
        Map<String, Team> teams = new HashMap<>();
        Map<String, Player> players = new HashMap<>();

        String line;
        while ((line = reader.readLine()) != null) {
            line = stripComments(line).trim();
            if (line.isEmpty()) {
                continue;
            }

            String[] parts = line.split("\\s+");
            String command = parts[0].toUpperCase(Locale.ROOT);

            switch (command) {
                case "SPRING":
                    parseSpring(parts, engine, elements);
                    break;
                case "PUMP":
                    parsePump(parts, engine, elements);
                    break;
                case "CISTERN":
                    parseCistern(parts, engine, elements);
                    break;
                case "PIPE":
                    parsePipe(parts, engine, elements);
                    break;
                case "CONNECT":
                    parseConnect(parts, elements);
                    break;
                case "PUMP_DIRECTION":
                    parsePumpDirection(parts, elements);
                    break;
                case "TEAM":
                    parseTeam(parts, engine, teams);
                    break;
                case "PLUMBER":
                    parsePlumber(parts, engine, teams, players);
                    break;
                case "SABOTEUR":
                    parseSaboteur(parts, engine, teams, players);
                    break;
                case "READY":
                    parseReady(parts, players);
                    break;
                case "START":
                case "STARTS":
                    engine.startGame();
                    break;
                case "SET_POSITION":
                    parseSetPosition(parts, players, elements);
                    break;
                case "SET_PIPE_STATUS":
                    parseSetPipeStatus(parts, elements);
                    break;
                case "SET_PIPE_WATER":
                    parseSetPipeWater(parts, elements);
                    break;
                case "SET_PUMP_STATUS":
                    parseSetPumpStatus(parts, elements);
                    break;
                case "SET_PUMP_TANK":
                    parseSetPumpTank(parts, elements);
                    break;
                case "SET_WATER_COLLECTED":
                    parseSetCollectedWater(parts, engine);
                    break;
                case "SET_CISTERN_WATER":
                    parseSetCisternWater(parts, elements);
                    break;
                case "SET_WATER_LOST":
                case "SET_LOST_WATER":
                    parseSetWaterLost(parts, engine);
                    break;
                case "SET_TIMER":
                    parseSetTimer(parts, engine);
                    break;
                case "SET_STATUS":
                    parseSetStatus(parts, engine);
                    break;
                case "LEAK":
                    parseLeak(parts, elements);
                    break;
                default:
                    // Unknown setup directives are ignored to remain tolerant.
                    break;
            }
        }

        return engine;
    }

    private static String stripComments(String line) {
        int idx = line.indexOf('#');
        return idx >= 0 ? line.substring(0, idx) : line;
    }

    private static void parseSpring(String[] parts, GameEngine engine, Map<String, FieldElement> elements) {
        if (parts.length < 3) {
            return;
        }
        String id = parts[1];
        int ports = parseInt(parts[2], 4);
        int transferRate = parts.length >= 4 ? parseInt(parts[3], 1) : 1;
        Spring spring = new Spring(id, ports, transferRate);
        engine.addFieldElement(spring);
        elements.put(id, spring);
    }

    private static void parsePump(String[] parts, GameEngine engine, Map<String, FieldElement> elements) {
        if (parts.length < 2) {
            return;
        }
        String id = parts[1];
        int ports = parts.length >= 3 ? parseInt(parts[2], 4) : 4;
        Pump pump = new Pump(id, ports);
        engine.addFieldElement(pump);
        elements.put(id, pump);
    }

    private static void parseCistern(String[] parts, GameEngine engine, Map<String, FieldElement> elements) {
        if (parts.length < 2) {
            return;
        }
        String id = parts[1];
        Cistern cistern = new Cistern(id);
        engine.addFieldElement(cistern);
        elements.put(id, cistern);
    }

    private static void parsePipe(String[] parts, GameEngine engine, Map<String, FieldElement> elements) {
        if (parts.length < 2) {
            return;
        }
        String id = parts[1];
        Pipe pipe = new Pipe(id);
        engine.addFieldElement(pipe);
        elements.put(id, pipe);
    }

    private static void parseConnect(String[] parts, Map<String, FieldElement> elements) {
        if (parts.length < 4) {
            return;
        }
        FieldElement pipeEl = elements.get(parts[1]);
        FieldElement endA = isFreeEnd(parts[2]) ? null : elements.get(parts[2]);
        FieldElement endB = isFreeEnd(parts[3]) ? null : elements.get(parts[3]);
        if (!(pipeEl instanceof Pipe)) {
            return;
        }
        if (endA != null && !(endA instanceof ActiveElement)) {
            return;
        }
        if (endB != null && !(endB instanceof ActiveElement)) {
            return;
        }
        Pipe pipe = (Pipe) pipeEl;
        pipe.setEndA((ActiveElement) endA);
        pipe.setEndB((ActiveElement) endB);
        if (endA != null) {
            ((ActiveElement) endA).connectPipe(pipe);
        }
        if (endB != null) {
            ((ActiveElement) endB).connectPipe(pipe);
        }
    }

    private static boolean isFreeEnd(String text) {
        return text == null
                || "FREE".equalsIgnoreCase(text)
                || "NULL".equalsIgnoreCase(text)
                || "NONE".equalsIgnoreCase(text);
    }

    private static void parsePumpDirection(String[] parts, Map<String, FieldElement> elements) {
        if (parts.length < 4) {
            return;
        }
        FieldElement pumpEl = elements.get(parts[1]);
        FieldElement inEl = elements.get(parts[2]);
        FieldElement outEl = elements.get(parts[3]);
        if (!(pumpEl instanceof Pump) || !(inEl instanceof Pipe) || !(outEl instanceof Pipe)) {
            return;
        }
        Pump pump = (Pump) pumpEl;
        pump.setIncomingPipe((Pipe) inEl);
        pump.setOutgoingPipe((Pipe) outEl);
    }

    private static void parseTeam(String[] parts, GameEngine engine, Map<String, Team> teams) {
        if (parts.length < 3) {
            return;
        }
        String id = parts[1];
        String name = join(parts, 2, parts.length);
        Team team = new Team(id, name);
        teams.put(id, team);
        engine.addTeam(team);
    }

    private static void parsePlumber(String[] parts, GameEngine engine, Map<String, Team> teams, Map<String, Player> players) {
        if (parts.length < 4) {
            return;
        }
        String id = parts[1];
        String teamId = parts[parts.length - 1];
        String name = join(parts, 2, parts.length - 1);
        Team team = teams.computeIfAbsent(teamId, t -> {
            Team created = new Team(t, t);
            engine.addTeam(created);
            return created;
        });
        Plumber player = new Plumber(id, name, team);
        players.put(id, player);
        engine.registerTeam(player);
    }

    private static void parseSaboteur(String[] parts, GameEngine engine, Map<String, Team> teams, Map<String, Player> players) {
        if (parts.length < 4) {
            return;
        }
        String id = parts[1];
        String teamId = parts[parts.length - 1];
        String name = join(parts, 2, parts.length - 1);
        Team team = teams.computeIfAbsent(teamId, t -> {
            Team created = new Team(t, t);
            engine.addTeam(created);
            return created;
        });
        Saboteur player = new Saboteur(id, name, team);
        players.put(id, player);
        engine.registerTeam(player);
    }

    private static void parseReady(String[] parts, Map<String, Player> players) {
        if (parts.length < 2) {
            return;
        }
        Player player = players.get(parts[1]);
        if (player != null) {
            player.setReady();
        }
    }

    private static void parseSetPosition(String[] parts, Map<String, Player> players, Map<String, FieldElement> elements) {
        if (parts.length < 3) {
            return;
        }
        Player player = players.get(parts[1]);
        FieldElement target = elements.get(parts[2]);
        if (player != null && target != null) {
            player.setCurrentPosition(target);
        }
    }

    private static void parseSetPipeStatus(String[] parts, Map<String, FieldElement> elements) {
        if (parts.length < 3) {
            return;
        }
        FieldElement el = elements.get(parts[1]);
        if (el instanceof Pipe) {
            ((Pipe) el).setStatus(parsePipeStatus(parts[2]));
        }
    }

    private static void parseSetPipeWater(String[] parts, Map<String, FieldElement> elements) {
        if (parts.length < 3) {
            return;
        }
        FieldElement el = elements.get(parts[1]);
        if (el instanceof Pipe) {
            ((Pipe) el).setIsWaterInside(Boolean.parseBoolean(parts[2]));
        }
    }

    private static void parseSetPumpStatus(String[] parts, Map<String, FieldElement> elements) {
        if (parts.length < 3) {
            return;
        }
        FieldElement el = elements.get(parts[1]);
        if (el instanceof Pump) {
            ((Pump) el).setStatus(parsePumpStatus(parts[2]));
        }
    }

    private static void parseSetPumpTank(String[] parts, Map<String, FieldElement> elements) {
        if (parts.length < 3) {
            return;
        }
        FieldElement el = elements.get(parts[1]);
        if (el instanceof Pump) {
            ((Pump) el).setTankCurrentWater(parseInt(parts[2], 0));
        }
    }

    private static void parseSetCollectedWater(String[] parts, GameEngine engine) {
        if (parts.length < 2) {
            return;
        }
        engine.setCollectedWater(parseInt(parts[1], 0));
    }

    private static void parseSetCisternWater(String[] parts, Map<String, FieldElement> elements) {
        if (parts.length < 3) {
            return;
        }
        FieldElement el = elements.get(parts[1]);
        if (el instanceof Cistern) {
            ((Cistern) el).setWaterCollected(parseInt(parts[2], 0));
        }
    }

    private static void parseSetWaterLost(String[] parts, GameEngine engine) {
        if (parts.length < 2) {
            return;
        }
        engine.setLostWater(parseInt(parts[1], 0));
    }

    private static void parseSetTimer(String[] parts, GameEngine engine) {
        if (parts.length < 2) {
            return;
        }
        engine.setTimer(parseInt(parts[1], 180));
    }

    private static void parseSetStatus(String[] parts, GameEngine engine) {
        if (parts.length < 2) {
            return;
        }
        try {
            engine.setStatus(GameStatus.valueOf(parts[1].toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ignored) {
            // ignored
        }
    }

    private static void parseLeak(String[] parts, Map<String, FieldElement> elements) {
        if (parts.length < 2) {
            return;
        }
        FieldElement el = elements.get(parts[1]);
        if (el instanceof Pipe) {
            ((Pipe) el).setStatus(PipeStatus.LEAKING);
        }
    }

    private static PipeStatus parsePipeStatus(String text) {
        try {
            return PipeStatus.valueOf(text.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return PipeStatus.NORMAL;
        }
    }

    private static PumpStatus parsePumpStatus(String text) {
        try {
            return PumpStatus.valueOf(text.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return PumpStatus.WORKING;
        }
    }

    private static int parseInt(String text, int fallback) {
        try {
            return Integer.parseInt(text.trim());
        } catch (Exception ex) {
            return fallback;
        }
    }

    private static String join(String[] parts, int start, int endExclusive) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < endExclusive; i++) {
            if (i > start) {
                sb.append(' ');
            }
            sb.append(parts[i]);
        }
        return sb.toString().trim();
    }

    private static GameEngine createFallbackEngine() {
        GameEngine engine = new GameEngine();

        // ── Field elements ───────────────────────────────────────
        Spring  spring  = new Spring("SP-1", 4, 3);  // transferRate=3 to support 3 parallel paths
        Pump    pump    = new Pump("PU-1", 4);
        Pump    pump2   = new Pump("PU-2", 4);
        Pump   pump3   = new Pump("PU-3", 4);
        Cistern cistern = new Cistern("CI-1");
        Pipe    p1      = new Pipe("PI-1");
        Pipe    p2      = new Pipe("PI-2");
        Pipe    p3      = new Pipe("PI-3");
        Pipe    p4      = new Pipe("PI-4");
        Pipe    p5      = new Pipe("PI-5");
        Pipe    p6      = new Pipe("PI-6");

        engine.addFieldElement(spring);
        engine.addFieldElement(pump);
        engine.addFieldElement(pump2);
        engine.addFieldElement(pump3);
        engine.addFieldElement(cistern);
        engine.addFieldElement(p1);
        engine.addFieldElement(p2);
        engine.addFieldElement(p3);
        engine.addFieldElement(p4);
        engine.addFieldElement(p5);
        engine.addFieldElement(p6);

        // Connect SP-1 ──PI-1──► PU-1 ──PI-2──► CI-1
        p1.setEndA(spring);  p1.setEndB(pump);
        p2.setEndA(pump);    p2.setEndB(cistern);
        spring.connectPipe(p1);
        pump.connectPipe(p1);
        pump.connectPipe(p2);
        cistern.connectPipe(p2);
        pump.setIncomingPipe(p1);
        pump.setOutgoingPipe(p2);
        

        // Connect SP-1 ──PI-3──► PU-2 ──PI-4──► CI-1
        p3.setEndA(spring);  p3.setEndB(pump2);
        p4.setEndA(pump2);   p4.setEndB(cistern);
        spring.connectPipe(p3);
        pump2.connectPipe(p3);
        pump2.connectPipe(p4);
        cistern.connectPipe(p4);
        pump2.setIncomingPipe(p3);
        pump2.setOutgoingPipe(p4);
        // Connect SP-1 ──PI-5──► PU-3 ──PI-6──► CI-1
        p5.setEndA(spring);  p5.setEndB(pump3);
        p6.setEndA(pump3);   p6.setEndB(cistern);
        spring.connectPipe(p5);
        pump3.connectPipe(p5);
        pump3.connectPipe(p6);
        cistern.connectPipe(p6);
        pump3.setIncomingPipe(p5);
        pump3.setOutgoingPipe(p6);

        // ── Teams ─────────────────────────────────────────────────
        Team t1 = new Team("T-1", "Plumbers");
        Team t2 = new Team("T-2", "Saboteurs");
        engine.addTeam(t1);
        engine.addTeam(t2);

        // ── Players — 2 plumbers + 2 saboteurs ────────────────────
        Plumber  pl1 = new Plumber("PL-1",  "Alice",   t1);
        //Plumber  pl2 = new Plumber("PL-2",  "Bob",     t1);
        Saboteur sa1 = new Saboteur("SA-1", "Mallory", t2);
        //Saboteur sa2 = new Saboteur("SA-2", "Eve",     t2);

        engine.registerTeam(pl1);
        //engine.registerTeam(pl2);
        engine.registerTeam(sa1);
        //engine.registerTeam(sa2);

        pl1.setReady(); //pl2.setReady();
        sa1.setReady(); //sa2.setReady();

        engine.startGame();
        return engine;
    }
}