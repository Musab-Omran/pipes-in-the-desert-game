package view;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.ScrollPaneConstants;

import controller.GameController;
import controller.GameEventListener;
import model.engine.GameEngine;
import model.player.Player;
import model.player.Team;

/**
 * The main gameplay screen.
 *
 * <pre>
 * ┌────────────────────────────────────────── title bar ──────┐
 * ├────────────────────────────────┬──────────────────────────┤
 * │                                │  PLAYERS  (always visible)│
 * │          MAP VIEW              ├──────────────────────────┤
 * │    (scrollable tile grid)      │  ACTIONS  (10 buttons)    │
 * │                                │  End Game                 │
 * │                                ├──────────────────────────┤
 * │                                │  STATUS   (stats + role)  │
 * ├────────────────────────────────┴──────────────────────────┤
 * │                    ACTION LOG                              │
 * └───────────────────────────────────────────────────────────┘
 * </pre>
 *
 * The PLAYERS panel is always at the top of the sidebar, never scrolled,
 * so players can always switch roles without hunting for the controls.
 */
public class GamePanel extends JPanel implements GameEventListener {

    private static final Color BG           = new Color(235, 215, 170);
    private static final Color TITLE_BG     = new Color(248, 230, 196);
    private static final Color TITLE_BORDER = new Color(185, 148, 90);
    private static final Color TITLE_TEXT   = new Color(70, 48, 20);

    private final MapView        mapView;
    private final ControlPanel   controlPanel;
    private final StatusPanel    statusPanel;
    private final PlayerPanel    playerPanel;
    private final ActionLogPanel logPanel;

    public GamePanel(MainFrame frame, GameController ctrl, GameEngine eng) {
        setBackground(BG);
        setLayout(new BorderLayout(6, 6));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // ── Title bar ─────────────────────────────────────────────
        add(new TitleBar("PIPES IN THE DESERT"), BorderLayout.NORTH);

        // ── Sidebar (east) — scrollable so STATUS is never crushed ─
        playerPanel  = new PlayerPanel(ctrl, eng);
        controlPanel = new ControlPanel(ctrl);
        statusPanel  = new StatusPanel(ctrl);

        // All three stacked in a single BoxLayout column
        JPanel sidebarContent = new JPanel();
        sidebarContent.setOpaque(false);
        sidebarContent.setLayout(new javax.swing.BoxLayout(
                sidebarContent, javax.swing.BoxLayout.Y_AXIS));
        sidebarContent.add(playerPanel);
        sidebarContent.add(javax.swing.Box.createVerticalStrut(4));
        sidebarContent.add(controlPanel);
        sidebarContent.add(javax.swing.Box.createVerticalStrut(4));
        sidebarContent.add(statusPanel);

        // Wrap in a scroll pane — vertical scrollbar appears only when window is
        // too short to show everything, so STATUS is always accessible.
        JScrollPane sidebarScroll = new JScrollPane(sidebarContent,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sidebarScroll.setPreferredSize(new Dimension(322, 0));
        sidebarScroll.setBorder(null);
        sidebarScroll.getViewport().setOpaque(false);
        sidebarScroll.setOpaque(false);
        sidebarScroll.getVerticalScrollBar().setUnitIncrement(16);
        add(sidebarScroll, BorderLayout.EAST);

        // ── Map (centre, scrollable) ──────────────────────────────
        mapView = new MapView(ctrl);
        JScrollPane scroll = new JScrollPane(mapView,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(158, 128, 82), 2));
        scroll.setBackground(new Color(215, 183, 140));
        add(scroll, BorderLayout.CENTER);

        // ── Action log (south) ────────────────────────────────────
        logPanel = new ActionLogPanel();
        add(logPanel, BorderLayout.SOUTH);

        // Default to first player
        if (!eng.getTeams().isEmpty() && !eng.getTeams().get(0).getPlayers().isEmpty()) {
            ctrl.setCurrentPlayer(eng.getTeams().get(0).getPlayers().get(0));
        }

        // ── Global keyboard shortcuts ─────────────────────────────
        registerGlobalKeys(ctrl, eng);
    }

    public MapView         getMapView()       { return mapView; }
    public ControlPanel    getControlPanel()  { return controlPanel; }
    public StatusPanel     getStatusPanel()   { return statusPanel; }
    public PlayerPanel     getPlayerPanel()   { return playerPanel; }
    public ActionLogPanel  getLogPanel()      { return logPanel; }

    // ── GameEventListener ─────────────────────────────────────────
    @Override public void onStateChanged()                          { }
    @Override public void onTick(int s)                             { }
    @Override public void onActionResult(boolean ok, String msg)    { logPanel.onActionResult(ok, msg); }
    @Override public void onGameEnded(String r, int c, int l)       { logPanel.onGameEnded(r, c, l); }
    @Override public void onPumpBroke(String id)                    { logPanel.onPumpBroke(id); }
    @Override public void onPipeManufactured(String ci, String pi)  { logPanel.onPipeManufactured(ci, pi); }
    @Override public void onPumpManufactured(String ci, String pi)  { logPanel.onPumpManufactured(ci, pi); }

    // ── Keyboard shortcuts ────────────────────────────────────────

    private void registerGlobalKeys(GameController ctrl, GameEngine eng) {
        // Tab → next player
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0), "next",
             () -> cyclePlayer(ctrl, eng, +1));
        // Shift+Tab → previous player
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_TAB,
                java.awt.event.InputEvent.SHIFT_DOWN_MASK), "prev",
             () -> cyclePlayer(ctrl, eng, -1));
        // M → move to selected target
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_M, 0), "moveKey", () -> {
            if (ctrl.getSelectedTarget() != null && ctrl.getCurrentPlayer() != null) {
                ctrl.move(ctrl.getSelectedTarget());
                ctrl.clearSelection();
            }
        });
        // Escape → clear selection
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "clearSel", () -> {
            ctrl.clearSelection();
            eng.notifyStateChanged();
        });
        // Number keys 1–4 → jump to player by index
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            bind(KeyStroke.getKeyStroke((char)('1' + i)), "player" + i,
                 () -> jumpToPlayer(ctrl, eng, idx));
        }
    }

    private void bind(KeyStroke ks, String name, Runnable action) {
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ks, name);
        getActionMap().put(name, new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                action.run();
            }
        });
    }

    private void cyclePlayer(GameController ctrl, GameEngine eng, int dir) {
        List<Player> all = allPlayers(eng);
        if (all.isEmpty()) return;
        Player cur = ctrl.getCurrentPlayer();
        int idx = all.indexOf(cur);
        ctrl.setCurrentPlayer(all.get(((idx + dir) % all.size() + all.size()) % all.size()));
    }

    private void jumpToPlayer(GameController ctrl, GameEngine eng, int idx) {
        List<Player> all = allPlayers(eng);
        if (idx < all.size()) ctrl.setCurrentPlayer(all.get(idx));
    }

    private List<Player> allPlayers(GameEngine eng) {
        List<Player> all = new ArrayList<>();
        for (Team t : eng.getTeams()) all.addAll(t.getPlayers());
        return all;
    }

    // ── TitleBar ──────────────────────────────────────────────────

    private static class TitleBar extends JPanel {
        private final String title;
        TitleBar(String title) {
            this.title = title;
            setBackground(TITLE_BG);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(TITLE_BORDER, 2, true),
                    BorderFactory.createEmptyBorder(7, 14, 7, 14)));
            setPreferredSize(new Dimension(0, 46));
        }
        @Override protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                               RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setFont(new Font("Serif", Font.BOLD, 22));
            g.setColor(TITLE_TEXT);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(title, 14, (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
            // Keyboard hint on the right
            g.setFont(new Font("SansSerif", Font.PLAIN, 10));
            g.setColor(new Color(130, 100, 55));
            String hint = "Keys:  Tab=next player  |  1-4=jump to player  |  M=move  |  Esc=cancel";
            g.drawString(hint, getWidth() - g.getFontMetrics().stringWidth(hint) - 14,
                         (getHeight() + g.getFontMetrics().getAscent() - g.getFontMetrics().getDescent()) / 2);
            g.dispose();
        }
    }
}
