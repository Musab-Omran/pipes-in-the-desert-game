package view;

import java.awt.*;
import javax.swing.*;
import controller.GameController;
import controller.GameEventListener;
import model.elements.*;
import model.engine.GameEngine;
import model.player.*;

/**
 * Live status panel — updates on every tick and state change.
 * Shows: active player, timer, collected/lost water, active leaks,
 * working/broken pumps, current position, and carried equipment.
 */
public class StatusPanel extends JPanel implements GameEventListener {

    private static final Color HEADER_BG   = new Color(80, 50, 22);
    private static final Color HEADER_TEXT = new Color(255, 240, 200);
    private static final Color PANEL_BG    = new Color(240, 220, 185);
    private static final Color KEY_COL     = new Color(65, 48, 28);
    private static final Color VAL_PLUMBER = new Color(30, 80, 200);
    private static final Color VAL_SABOTEUR= new Color(180, 30, 30);
    private static final Color VAL_WATER_C = new Color(30, 120, 200);
    private static final Color VAL_WATER_L = new Color(190, 50, 30);
    private static final Color VAL_LEAK    = new Color(220, 60, 20);
    private static final Color VAL_PUMP_OK = new Color(30, 140, 60);
    private static final Color VAL_PUMP_BK = new Color(200, 40, 40);
    private static final Color VAL_DEFAULT = new Color(50, 50, 50);

    private final GameController ctrl;

    private final JLabel valPlayer    = val("—",      VAL_DEFAULT);
    private final JLabel valTime      = val("00:00",   VAL_DEFAULT);
    private final JLabel valCollected = val("0 L",     VAL_WATER_C);
    private final JLabel valLost      = val("0 L",     VAL_WATER_L);
    private final JLabel valLeaks     = val("0",       VAL_LEAK);
    private final JLabel valPumps     = val("—",       VAL_PUMP_OK);
    private final JLabel valPos       = val("—",       VAL_DEFAULT);
    private final JLabel valCarried   = val("none",    VAL_DEFAULT);
    private final JLabel valRole      = val("—",       VAL_DEFAULT);

    public StatusPanel(GameController ctrl) {
        this.ctrl = ctrl;
        setBackground(PANEL_BG);
        setLayout(new BorderLayout());
        add(new HeaderPanel("STATUS"), BorderLayout.NORTH);

        JPanel body = new JPanel(new GridLayout(0, 2, 4, 4));
        body.setOpaque(false);
        body.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));

        body.add(key("Active Player:"));   body.add(valPlayer);
        body.add(key("Time:"));            body.add(valTime);
        body.add(key("Collected Water:")); body.add(valCollected);
        body.add(key("Lost Water:"));      body.add(valLost);
        body.add(key("Active Leaks:"));    body.add(valLeaks);
        body.add(key("Pumps (ok/bk):"));  body.add(valPumps);
        body.add(key("Position:"));        body.add(valPos);
        body.add(key("Carrying:"));        body.add(valCarried);
        body.add(key("Can do:"));          body.add(valRole);

        add(body, BorderLayout.CENTER);
        refresh();
    }

    @Override public void onStateChanged()                         { refresh(); }
    @Override public void onTick(int s)                            { refresh(); }
    @Override public void onActionResult(boolean ok, String m)     { refresh(); }
    @Override public void onGameEnded(String r, int c, int l)      { refresh(); }
    @Override public void onPumpBroke(String id)                   { refresh(); }
    @Override public void onPipeManufactured(String ci, String pi) { refresh(); }
    @Override public void onPumpManufactured(String ci, String pi) { refresh(); }

    public void refresh() {
        GameEngine eng = ctrl.getEngine();
        Player cur = ctrl.getCurrentPlayer();

        // Timer
        int t = Math.max(0, eng.getTimer());
        valTime.setText(String.format("%02d:%02d", t / 60, t % 60));

        // Water counts
        valCollected.setText(eng.getCollectedWater() + " L");
        valLost.setText(eng.getLostWater() + " L");
        // Highlight lost water red when > 0
        valLost.setForeground(eng.getLostWater() > 0 ? VAL_WATER_L : VAL_DEFAULT);

        // Use engine helper methods for clean count queries
        int leaks    = eng.countActiveLeaks();
        int pumpsBk  = eng.countBrokenPumps();
        int totalPumps = (int) eng.getFieldElements().stream()
                            .filter(fe -> fe instanceof Pump).count();
        int pumpsOk  = totalPumps - pumpsBk;
        valLeaks.setText(leaks + (leaks > 0 ? " ⚡" : ""));
        valLeaks.setForeground(leaks > 0 ? VAL_LEAK : VAL_DEFAULT);
        valPumps.setText(pumpsOk + " ok / " + pumpsBk + " broken"
                       + (pumpsBk > 0 ? " [fail in " + eng.getFailureCountdown() + "s]" : ""));
        valPumps.setForeground(pumpsBk > 0 ? VAL_PUMP_BK : VAL_PUMP_OK);

        // Player info
        if (cur == null) {
            valPlayer.setText("—"); valPlayer.setForeground(VAL_DEFAULT);
            valPos.setText("—"); valCarried.setText("—"); valRole.setText("—");
            valRole.setForeground(VAL_DEFAULT);
        } else {
            boolean isPl = cur instanceof Plumber;
            valPlayer.setForeground(isPl ? VAL_PLUMBER : VAL_SABOTEUR);
            valPlayer.setText(cur.getId() + " (" + (isPl ? "Plumber" : "Saboteur") + ")");

            FieldElement pos = cur.getCurrentPosition();
            valPos.setText(pos != null ? pos.getId() : "—");

            if (isPl) {
                Pump cp = ((Plumber) cur).getCarriedPump();
                valCarried.setText(cp != null ? cp.getId() : "none");
                valRole.setText("Fix · Connect · Insert");
                valRole.setForeground(VAL_PLUMBER);
            } else {
                valCarried.setText("n/a");
                // DOCX: saboteurs can puncture PIPES and change pump DIRECTION
                FieldElement sabPos = cur.getCurrentPosition();
                if (sabPos instanceof model.elements.Pump) {
                    valRole.setText("On pump: use Direction ↗");
                } else if (sabPos instanceof model.elements.Pipe) {
                    valRole.setText("On pipe: Puncture ⚡ or Move");
                } else {
                    valRole.setText("Move to a pipe → Puncture ⚡");
                }
                valRole.setForeground(VAL_SABOTEUR);
            }
        }
        repaint();
    }

    // ── Helpers ───────────────────────────────────────────────────
    private JLabel key(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("SansSerif", Font.PLAIN, 11));
        l.setForeground(KEY_COL);
        return l;
    }
    private static JLabel val(String text, Color col) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("SansSerif", Font.BOLD, 11));
        l.setForeground(col);
        return l;
    }

    private static class HeaderPanel extends JPanel {
        private final String text;
        HeaderPanel(String text) {
            this.text = text;
            setPreferredSize(new Dimension(0, 30));
            setOpaque(false);
        }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setColor(new Color(80, 50, 22));
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                               RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setFont(new Font("SansSerif", Font.BOLD, 13));
            g.setColor(new Color(255, 240, 200));
            FontMetrics fm = g.getFontMetrics();
            g.drawString(text, (getWidth() - fm.stringWidth(text)) / 2,
                         (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
            g.dispose();
        }
    }

    @Override public Dimension getPreferredSize() { return new Dimension(308, 220); }
}
