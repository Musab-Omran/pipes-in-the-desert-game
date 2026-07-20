package view;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import controller.GameController;
import controller.GameEventListener;
import model.elements.FieldElement;
import model.engine.GameEngine;
import model.player.Player;
import model.player.Plumber;
import model.player.Saboteur;
import model.player.Team;

/**
 * Permanent player-select bar, always at the top of the sidebar.
 *
 * <pre>
 * ┌────────────────────────────── PLAYERS ─────────────────── [?] ─┐
 * │  ► PL-1      PL-2       SA-1       SA-2                        │
 * │   Plumber   Plumber   Saboteur   Saboteur                      │
 * │   @CI-1     @SP-1      @PI-1      @SP-1                        │
 * └────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * Every player has a large coloured button: blue for Plumbers, red for
 * Saboteurs. The active player is fully saturated; idle players are
 * washed-out so the eye immediately finds who is acting.
 *
 * Clicking a button calls {@link GameController#setCurrentPlayer(Player)},
 * which clears the old target selection and fires {@code onStateChanged()}
 * so all other panels refresh immediately.
 *
 * The {@code [?]} button opens a game-rules cheat-sheet.
 */
public class PlayerPanel extends JPanel implements GameEventListener {

    // ── Colours ───────────────────────────────────────────────────
    private static final Color HDR_BG      = new Color(40, 24, 8);
    private static final Color HDR_TEXT    = new Color(255, 240, 200);
    private static final Color PANEL_BG    = new Color(60, 35, 12);

    // Plumber: bright blue when active, pale blue when idle
    private static final Color PLB_ACT_TOP = new Color(55, 110, 230);
    private static final Color PLB_ACT_BOT = new Color(25,  70, 175);
    private static final Color PLB_IDL_TOP = new Color(130, 160, 210);
    private static final Color PLB_IDL_BOT = new Color( 95, 125, 175);

    // Saboteur: bright red when active, pale red when idle
    private static final Color SAB_ACT_TOP = new Color(210, 45, 45);
    private static final Color SAB_ACT_BOT = new Color(150, 20, 20);
    private static final Color SAB_IDL_TOP = new Color(200, 140, 140);
    private static final Color SAB_IDL_BOT = new Color(165, 100, 100);

    private static final Color LABEL_ACTIVE = new Color(255, 248, 230);
    private static final Color LABEL_IDLE   = new Color(220, 220, 235);

    // ── State ─────────────────────────────────────────────────────
    private final GameController ctrl;
    private final GameEngine     eng;
    private final JPanel         btnRow = new JPanel();

    public PlayerPanel(GameController ctrl, GameEngine eng) {
        this.ctrl = ctrl;
        this.eng  = eng;
        setBackground(PANEL_BG);
        setLayout(new BorderLayout(0, 0));

        add(buildHeader(), BorderLayout.NORTH);

        btnRow.setBackground(PANEL_BG);
        btnRow.setBorder(BorderFactory.createEmptyBorder(4, 6, 6, 6));
        add(btnRow, BorderLayout.CENTER);

        buildButtons();
    }

    // ── GameEventListener ─────────────────────────────────────────
    @Override public void onStateChanged()                          { buildButtons(); }
    @Override public void onTick(int s)                             { buildButtons(); }
    @Override public void onActionResult(boolean ok, String m)      { buildButtons(); }
    @Override public void onGameEnded(String r, int c, int l)       { buildButtons(); }
    @Override public void onPumpBroke(String id)                    { buildButtons(); }
    @Override public void onPipeManufactured(String ci, String pi)  { buildButtons(); }
    @Override public void onPumpManufactured(String ci, String pi)  { buildButtons(); }

    // ── Button construction ───────────────────────────────────────

    private void buildButtons() {
        List<Player> all = allPlayers();
        btnRow.removeAll();
        btnRow.setLayout(new GridLayout(1, Math.max(1, all.size()), 5, 0));

        Player cur = ctrl.getCurrentPlayer();
        for (Player p : all) {
            btnRow.add(makeBtn(p, p == cur));
        }
        btnRow.revalidate();
        btnRow.repaint();
    }

    /** One large player-select button. */
    private JButton makeBtn(Player p, boolean active) {
        boolean isPl = p instanceof Plumber;
        FieldElement pos = p.getCurrentPosition();
        String posStr = (pos != null) ? "@" + pos.getId() : "—";

        // Carrying pump?
        String carry = "";
        if (isPl && ((Plumber) p).getCarriedPump() != null) {
            carry = "<br><font color='#ffe0a0'>[" + ((Plumber) p).getCarriedPump().getId() + "]</font>";
        }

        // Number key hint (1 = PL-1, 2 = PL-2, etc.)
        List<Player> all = allPlayers();
        int idx = all.indexOf(p) + 1;
        String keyHint = idx > 0 && idx <= 4 ? " [" + idx + "]" : "";

        String html = "<html><center>"
                + (active ? "<b>► " + p.getId() + keyHint + "</b>" : p.getId() + keyHint)
                + "<br><font size='2'>" + (isPl ? "Plumber" : "Saboteur") + "</font>"
                + "<br><font size='2'>" + posStr + "</font>"
                + carry
                + "</center></html>";

        JButton btn = new JButton(html) {
            @Override protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                   RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth(), h = getHeight();

                // Background gradient
                Color top, bot;
                if (active) {
                    top = isPl ? PLB_ACT_TOP : SAB_ACT_TOP;
                    bot = isPl ? PLB_ACT_BOT : SAB_ACT_BOT;
                } else {
                    top = isPl ? PLB_IDL_TOP : SAB_IDL_TOP;
                    bot = isPl ? PLB_IDL_BOT : SAB_IDL_BOT;
                }
                if (getModel().isRollover() && !active) {
                    top = top.brighter();
                    bot = bot.brighter();
                }
                g.setPaint(new GradientPaint(0, 0, top, 0, h, bot));
                g.fillRoundRect(0, 0, w, h, 10, 10);

                // Bright border for active player
                if (active) {
                    g.setColor(new Color(255, 240, 150));
                    g.setStroke(new BasicStroke(2.5f));
                    g.drawRoundRect(1, 1, w - 3, h - 3, 10, 10);
                } else {
                    g.setColor(bot.darker());
                    g.setStroke(new BasicStroke(1f));
                    g.drawRoundRect(0, 0, w - 1, h - 1, 10, 10);
                }
                g.dispose();

                // Let JButton render the HTML text on top
                super.paintComponent(g0);
            }
        };
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setForeground(active ? LABEL_ACTIVE : LABEL_IDLE);
        btn.setFont(new Font("SansSerif", Font.PLAIN, 10));
        btn.setOpaque(false);
        btn.addActionListener(e -> ctrl.setCurrentPlayer(p));
        return btn;
    }

    // ── Header bar ────────────────────────────────────────────────

    private JPanel buildHeader() {
        JPanel hdr = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                g.setColor(HDR_BG);
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                                   RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setFont(new Font("SansSerif", Font.BOLD, 13));
                g.setColor(HDR_TEXT);
                FontMetrics fm = g.getFontMetrics();
                g.drawString("PLAYERS — click a button or press 1 · 2 · 3 · 4",
                             10, (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
                g.dispose();
            }
        };
        hdr.setOpaque(false);
        hdr.setPreferredSize(new Dimension(0, 28));

        // Help / rules button
        JButton help = new JButton("?") {
            @Override protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                   RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(getModel().isRollover()
                           ? new Color(220, 175, 50) : new Color(190, 145, 40));
                g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);
                g.setFont(new Font("SansSerif", Font.BOLD, 13));
                g.setColor(new Color(40, 20, 5));
                FontMetrics fm = g.getFontMetrics();
                g.drawString("?", (getWidth() - fm.stringWidth("?")) / 2,
                             (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
                g.dispose();
            }
        };
        help.setContentAreaFilled(false);
        help.setBorderPainted(false);
        help.setFocusPainted(false);
        help.setPreferredSize(new Dimension(28, 28));
        help.setToolTipText("Game rules & controls");
        help.addActionListener(e -> showHelp());
        hdr.add(help, BorderLayout.EAST);
        return hdr;
    }

    // ── Help dialog ───────────────────────────────────────────────

    private static final String HELP_TEXT =
"╔══════════════════════════════════════════════════════════╗\n"
+"║           PIPES IN THE DESERT — GAME RULES              ║\n"
+"╚══════════════════════════════════════════════════════════╝\n"
+"\n"
+"GOAL\n"
+"  Plumbers collect 100 L of water in the cistern before time\n"
+"  runs out.  Saboteurs try to stop them by causing leaks.\n"
+"\n"
+"HOW WATER FLOWS\n"
+"  Spring ──pipe──► Pump ──pipe──► Cistern\n"
+"  Each tick the pump moves water toward the cistern.\n"
+"  Leaking pipes lose water to the desert.\n"
+"\n"
+"╔══════════════╦══════════════════════════════════════════╗\n"
+"║ Player role  ║  What they should do                    ║\n"
+"╠══════════════╬══════════════════════════════════════════╣\n"
+"║ PL-1         ║  • Fix broken pumps (FixPump)           ║\n"
+"║ PL-2         ║  • Fix leaking pipes (FixPipe)          ║\n"
+"║ (Plumbers)   ║  • Pick up new pumps from the cistern   ║\n"
+"║              ║    (PickupPump) and insert them into    ║\n"
+"║              ║    pipes to reroute water (InsertPump)  ║\n"
+"║              ║  • Connect / disconnect pipe ends to    ║\n"
+"║              ║    rebuild or extend the network        ║\n"
+"╠══════════════╬══════════════════════════════════════════╣\n"
+"║ SA-1         ║  • Puncture pipes to cause water leaks  ║\n"
+"║ SA-2         ║    (Puncture — only on normal pipes)    ║\n"
+"║ (Saboteurs)  ║  • Redirect pumps to cut water supply   ║\n"
+"║              ║    (Direction — stand on any pump)      ║\n"
+"╚══════════════╩══════════════════════════════════════════╝\n"
+"\n"
+"CONTROLS\n"
+"  Switch player:  click a button above  |  Tab / Shift+Tab\n"
+"  Jump directly:  press 1 2 3 4 on keyboard\n"
+"  Move player:    DOUBLE-CLICK a green ↕ ring on the map\n"
+"                  OR single-click to select, then [ Move ]\n"
+"  Keyboard:       M = Move · Esc = cancel · Tab = next player\n"
+"\n"
+"TYPICAL SEQUENCE (example)\n"
+"  1. SA-1: move to PI-1 → Puncture (pipe starts leaking)\n"
+"  2. PL-1: move to CI-1 → PickupPump (grab new pump)\n"
+"         → move to PI-1 → InsertPump (split pipe, add pump)\n"
+"  3. PL-2: move to PU-1 → FixPump   (repair broken pump)\n"
+"  4. SA-2: move to PU-1 → Direction (redirect water away)\n"
+"  ...and so on until time runs out or 100 L collected.\n"
+"\n"
+"VISUAL GUIDE\n"
+"  Cyan ring    = where the active player currently stands\n"
+"  Green ↕ ring = adjacent — double-click to move there\n"
+"  Gold ring    = selected target (press M or click Move)\n"
+"  Blue buttons = action is legal right now\n"
+"  Red X pump   = pump broken → Plumber needs to FixPump\n"
+"  Pink pipe    = pipe leaking → Plumber needs to FixPipe\n";

    private void showHelp() {
        JTextArea ta = new JTextArea(HELP_TEXT);
        ta.setEditable(false);
        ta.setFont(new Font("Monospaced", Font.PLAIN, 12));
        ta.setBackground(new Color(248, 240, 220));
        ta.setForeground(new Color(50, 30, 10));
        ta.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        JScrollPane sp = new JScrollPane(ta);
        sp.setPreferredSize(new Dimension(650, 500));
        JOptionPane.showMessageDialog(this, sp,
            "Game Rules & Controls", JOptionPane.PLAIN_MESSAGE);
    }

    // ── Helpers ───────────────────────────────────────────────────

    private List<Player> allPlayers() {
        List<Player> all = new ArrayList<>();
        for (Team t : eng.getTeams()) all.addAll(t.getPlayers());
        return all;
    }

    @Override public Dimension getPreferredSize() {
        return new Dimension(314, 96);
    }
}
