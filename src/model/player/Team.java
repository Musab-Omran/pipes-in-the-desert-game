package model.player;

import java.util.ArrayList;
import java.util.List;
import util.Trace;

/**
 * A team groups multiple players.
 */
public class Team {

    private String id;
    private String name;
    private List<Player> players;

    public Team(String id, String name) {
        this.id = id;
        this.name = name;
        this.players = new ArrayList<>();
        Trace.log("S: Team created: " + id + " (" + name + ")");
    }

    public void addPlayer(Player player) {
        if (player != null && !players.contains(player)) {
            players.add(player);
            Trace.log("S: Team." + id + ".addPlayer(" + player.getId() + ")");
        }
    }

    public void removePlayer(Player player) {
        if (player != null && players.remove(player)) {
            Trace.log("S: Team." + id + ".removePlayer(" + player.getId() + ")");
        }
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public List<Player> getPlayers() {
        return players;
    }

    @Override
    public String toString() {
        return name;
    }
}
