package view;

import java.awt.AlphaComposite;
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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import controller.GameController;
import controller.GameEventListener;
import model.elements.ActiveElement;
import model.elements.Cistern;
import model.elements.FieldElement;
import model.elements.Pipe;
import model.elements.PipeStatus;
import model.elements.Pump;
import model.elements.PumpStatus;
import model.engine.GameStatus;
import model.player.Player;
import model.player.Plumber;

/**
 * Action sidebar. Contains:
 * <ul>
 *   <li>10 gameplay action buttons (5 × 2 grid)</li>
 *   <li>An <b>End Game</b> button (always visible when running)</li>
 *   <li>A <b>State</b> button for inspecting game state</li>
 * </ul>
 *
 * Buttons are enabled or disabled in real time based on the current player's
 * role and position. The controller is the only target — the view never
 * mutates the model directly.
 */
public class ControlPanel extends JPanel implements GameEventListener {

    // ── Colours ───────────────────────────────────────────────────
    private static final Color HEADER_BG    = new Color(80, 50, 22);
    private static final Color HEADER_TEXT  = new Color(255, 240, 200);
    private static final Color PANEL_BG     = new Color(240, 220, 185);
    private static final Color BTN_BG       = new Color(252, 246, 232);
    private static final Color BTN_BORDER   = new Color(200, 168, 115);
    private static final Color BTN_DISABLED = new Color(215, 210, 202);
    private static final Color BTN_HOVER    = new Color(240, 228, 200);
    private static final Color BTN_TEXT     = new Color(55, 90, 150);
    private static final Color BTN_DIS_TEXT = new Color(170, 160, 145);
    private static final Color END_BG       = new Color(160, 40, 40);
    private static final Color END_HOVER    = new Color(200, 60, 60);
    private static final Color END_TEXT     = new Color(255, 240, 240);

    private final GameController ctrl;

    // ── Action buttons ────────────────────────────────────────────
    private final ActionBtn btnMove        = new ActionBtn("Move",       "↕");
    private final ActionBtn btnFixPump     = new ActionBtn("FixPump",    "🔧");
    private final ActionBtn btnFixPipe     = new ActionBtn("FixPipe",    "🔧");
    private final ActionBtn btnDirection   = new ActionBtn("Direction",  "↗");
    private final ActionBtn btnPuncture    = new ActionBtn("Puncture",   "⚡");
    private final ActionBtn btnPickup      = new ActionBtn("PickupPump", "↑");
    private final ActionBtn btnInsert      = new ActionBtn("InsertPump", "↓");
    private final ActionBtn btnConnect     = new ActionBtn("Connect",    "🔗");
    private final ActionBtn btnDisconnect  = new ActionBtn("Disconnect", "🔗");
    private final ActionBtn btnState       = new ActionBtn("State",      "🔍");

    /** Always-visible End Game button — red, stands out from the other buttons. */
    private final EndGameBtn btnEndGame = new EndGameBtn("End Game");

    private final ActionBtn[] actionBtns = {
        btnMove, btnFixPump, btnFixPipe, btnDirection, btnPuncture,
        btnPickup, btnInsert, btnConnect, btnDisconnect, btnState
    };

    public ControlPanel(GameController ctrl) {
        this.ctrl = ctrl;
        setBackground(PANEL_BG);
        setLayout(new BorderLayout());

        // ── ACTIONS header ────────────────────────────────────────
        add(new HeaderPanel("ACTIONS"), BorderLayout.NORTH);

        // ── 5 × 2 action grid ────────────────────────────────────
        JPanel body = new JPanel(new BorderLayout(0, 6));
        body.setBackground(PANEL_BG);
        body.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel grid = new JPanel(new GridLayout(5, 2, 7, 7));
        grid.setOpaque(false);
        for (ActionBtn b : actionBtns) grid.add(b);
        body.add(grid, BorderLayout.CENTER);

        // ── End Game below the grid ───────────────────────────────
        body.add(btnEndGame, BorderLayout.SOUTH);
        add(body, BorderLayout.CENTER);

        // ── Wire action handlers ──────────────────────────────────
        btnMove      .addActionListener(e -> doMove());
        btnFixPump   .addActionListener(e -> ctrl.fixPump());
        btnFixPipe   .addActionListener(e -> ctrl.fixPipe());
        btnDirection .addActionListener(e -> doChangeDirection());
        btnPuncture  .addActionListener(e -> ctrl.puncturePipe());
        btnPickup    .addActionListener(e -> ctrl.pickUpPump());
        btnInsert    .addActionListener(e -> ctrl.insertPump());
        btnConnect   .addActionListener(e -> doConnect());
        btnDisconnect.addActionListener(e -> doDisconnect());
        btnState     .addActionListener(e -> doState());
        btnEndGame   .addActionListener(e -> doEndGame());

        refresh();
    }

    // ─── GameEventListener ────────────────────────────────────────
    @Override public void onStateChanged()                         { refresh(); }
    @Override public void onTick(int s)                            { refresh(); }
    @Override public void onActionResult(boolean ok, String msg)   { refresh(); }
    @Override public void onGameEnded(String r, int c, int l)      { refresh(); }
    @Override public void onPumpBroke(String id)                   { refresh(); }
    @Override public void onPipeManufactured(String ci, String pi) { refresh(); }
    @Override public void onPumpManufactured(String ci, String pi) { refresh(); }

    // ─── Enable / disable logic ───────────────────────────────────
    public void refresh() {
        boolean running = ctrl.getEngine().getStatus() == GameStatus.RUNNING;
        Player cur = ctrl.getCurrentPlayer();

        btnEndGame.setEnabled(running);

        if (!running || cur == null) {
            for (ActionBtn b : actionBtns) b.setEnabled(false);
            btnState.setEnabled(true);
            return;
        }

        FieldElement pos = cur.getCurrentPosition();
        FieldElement sel = ctrl.getSelectedTarget();
        boolean isPl    = cur instanceof Plumber;
        boolean isSab   = !isPl;
        boolean onPipe  = pos instanceof Pipe;
        boolean onPump  = pos instanceof Pump;
        boolean onCist  = pos instanceof Cistern;
        boolean onActv  = pos instanceof ActiveElement;

        btnMove      .setEnabled(sel != null && sel != pos);
        btnFixPump   .setEnabled(isPl && onPump && ((Pump) pos).getStatus() == PumpStatus.BROKEN);
        btnFixPipe   .setEnabled(isPl && onPipe && ((Pipe) pos).getStatus() == PipeStatus.LEAKING);
        btnDirection .setEnabled(onPump && ((Pump) pos).getConnectedPipes().size() >= 2);
        // Per DOCX: saboteurs puncture PIPES only; on pumps they use Direction instead
        btnPuncture  .setEnabled(isSab && onPipe && ((Pipe) pos).getStatus() == PipeStatus.NORMAL);

        // Dynamic tooltips — explains why each button is disabled
        btnMove      .setToolTipText(sel == null ? "Click an adjacent element first, then Move"
                                                 : "Move to selected element");
        btnFixPump   .setToolTipText(!isPl    ? "Plumber only" :
                                     !onPump   ? "Must be standing on a pump" :
                                     ((Pump) pos).getStatus() == PumpStatus.WORKING
                                               ? "Pump is already working" : "Fix this broken pump");
        btnFixPipe   .setToolTipText(!isPl   ? "Plumber only" :
                                     !onPipe  ? "Must be standing on a pipe" :
                                     ((Pipe) pos).getStatus() != PipeStatus.LEAKING
                                              ? "Pipe is not leaking" : "Repair this leaking pipe");
        btnDirection .setToolTipText(!onPump ? "Must be standing on a pump" :
                                     ((Pump) pos).getConnectedPipes().size() < 2
                                              ? "Pump needs ≥2 connected pipes" : "Change pump flow direction");
        btnPuncture  .setToolTipText(!isSab  ? "Saboteur only" :
                                     !onPipe  ? "Move to a pipe first" :
                                     ((Pipe) pos).getStatus() == PipeStatus.LEAKING
                                              ? "Pipe already leaking" : "Puncture this pipe to create a leak");
        btnPickup    .setToolTipText(!isPl    ? "Plumber only" :
                                     !onCist  ? "Must be at a cistern" :
                                     ((Plumber) cur).getCarriedPump() != null
                                              ? "Already carrying a pump" : "Pick up a pump from cistern");
        btnInsert    .setToolTipText(!isPl    ? "Plumber only" :
                                     !onPipe  ? "Must be on a pipe" :
                                     ((Plumber) cur).getCarriedPump() == null
                                              ? "Pick up a pump first" : "Insert carried pump into this pipe");
        // Connect: plumber must be on a pipe that has at least one free end
        boolean pipeHasFreeEnd = onPipe
                && (((Pipe) pos).getEndA() == null || ((Pipe) pos).getEndB() == null);
        btnConnect   .setToolTipText(!isPl ? "Plumber only"
                                   : !onPipe ? "Must be standing on a pipe"
                                   : !pipeHasFreeEnd ? "Both ends of this pipe are already connected"
                                   : "Connect a free end of this pipe to an active element");
        btnDisconnect.setToolTipText(!isPl    ? "Plumber only" : !onActv ? "Must be on an active element" :
                                     ((ActiveElement) pos).getConnectedPipes().isEmpty()
                                              ? "No pipes connected here" : "Disconnect a pipe from this element");
        btnPickup    .setEnabled(isPl && onCist
                               && ((Plumber) cur).getCarriedPump() == null
                               && !((Cistern) pos).getPumpInventory().isEmpty());
        btnInsert    .setEnabled(isPl && onPipe && ((Plumber) cur).getCarriedPump() != null);
        btnConnect   .setEnabled(isPl && pipeHasFreeEnd);
        btnDisconnect.setEnabled(isPl && onActv
                               && !((ActiveElement) pos).getConnectedPipes().isEmpty());
        btnState     .setEnabled(true);
    }

    // ─── Action handlers ──────────────────────────────────────────

    private void doMove() {
        FieldElement t = ctrl.getSelectedTarget();
        if (t != null) { ctrl.move(t); ctrl.clearSelection(); }
    }

    private void doConnect() {
        Player cur = ctrl.getCurrentPlayer();
        if (cur == null || !(cur.getCurrentPosition() instanceof Pipe)) return;
        Pipe pipe = (Pipe) cur.getCurrentPosition();

        // Collect only the free ends of the current pipe
        List<String> freeEnds = new ArrayList<>();
        if (pipe.getEndA() == null) freeEnds.add("A");
        if (pipe.getEndB() == null) freeEnds.add("B");
        if (freeEnds.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Both ends of " + pipe.getId() + " are already connected.",
                "Connect Pipe", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Collect active elements that still have a free port
        List<ActiveElement> targets = new ArrayList<>();
        for (FieldElement fe : ctrl.getEngine().getFieldElements()) {
            if (fe instanceof ActiveElement && ((ActiveElement) fe).isThereSpacePipe())
                targets.add((ActiveElement) fe);
        }
        if (targets.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "No active element has a free port to connect to.",
                "Connect Pipe", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Build dialog: end selector + target selector
        JComboBox<String>        endBox    = new JComboBox<>(freeEnds.toArray(new String[0]));
        JComboBox<ActiveElement> targetBox = new JComboBox<>(targets.toArray(new ActiveElement[0]));

        JPanel dlg = new JPanel(new java.awt.GridLayout(2, 2, 6, 6));
        dlg.add(new JLabel("Free end of " + pipe.getId() + ":"));
        dlg.add(endBox);
        dlg.add(new JLabel("Connect to:"));
        dlg.add(targetBox);

        if (JOptionPane.showConfirmDialog(this, dlg,
                "Connect " + pipe.getId(),
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {

            char chosenEnd    = ((String) endBox.getSelectedItem()).charAt(0);
            ActiveElement tgt = (ActiveElement) targetBox.getSelectedItem();
            ctrl.connectPipe(pipe, chosenEnd, tgt);
        }
    }

    private void doDisconnect() {
        Player cur = ctrl.getCurrentPlayer();
        if (cur == null || !(cur.getCurrentPosition() instanceof ActiveElement)) return;
        ActiveElement at = (ActiveElement) cur.getCurrentPosition();
        if (at.getConnectedPipes().isEmpty()) return;
        JComboBox<Pipe> pipeBox = new JComboBox<>(at.getConnectedPipes().toArray(new Pipe[0]));
        JComboBox<String> endBox = new JComboBox<>(new String[]{"A", "B"});
        JPanel p = new JPanel();
        p.add(new JLabel("Pipe:")); p.add(pipeBox);
        p.add(new JLabel("End:")); p.add(endBox);
        if (JOptionPane.showConfirmDialog(this, p, "Disconnect pipe end",
                JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
            ctrl.disconnectPipe((Pipe) pipeBox.getSelectedItem(),
                                ((String) endBox.getSelectedItem()).charAt(0));
        }
    }

    private void doChangeDirection() {
        Player cur = ctrl.getCurrentPlayer();
        if (cur == null || !(cur.getCurrentPosition() instanceof Pump)) return;
        Pump pump = (Pump) cur.getCurrentPosition();
        Pipe[] conn = pump.getConnectedPipes().toArray(new Pipe[0]);
        if (conn.length < 2) return;
        JComboBox<Pipe> inBox  = new JComboBox<>(conn);
        JComboBox<Pipe> outBox = new JComboBox<>(conn);
        JPanel p = new JPanel();
        p.add(new JLabel("Incoming:")); p.add(inBox);
        p.add(new JLabel("Outgoing:")); p.add(outBox);
        if (JOptionPane.showConfirmDialog(this, p, "Change pump direction",
                JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
            ctrl.changeDirection((Pipe) inBox.getSelectedItem(),
                                 (Pipe) outBox.getSelectedItem());
        }
    }

    private void doState() {
        StringBuilder sb = new StringBuilder();
        sb.append("Game status: ").append(ctrl.getEngine().getStatus()).append('\n');
        sb.append("Timer:       ").append(ctrl.getEngine().getTimer()).append("s\n");
        sb.append("Collected:   ").append(ctrl.getEngine().getCollectedWater()).append(" L\n");
        sb.append("Lost:        ").append(ctrl.getEngine().getLostWater()).append(" L\n");
        sb.append("Elements:    ").append(ctrl.getEngine().getFieldElements().size()).append('\n');
        Player cur = ctrl.getCurrentPlayer();
        if (cur != null) {
            sb.append('\n');
            sb.append("Active player: ").append(cur.getId()).append(" (").append(cur.getName()).append(")\n");
            sb.append("Role:          ").append(cur instanceof Plumber ? "Plumber" : "Saboteur").append('\n');
            sb.append("Position:      ").append(cur.getCurrentPosition() != null
                ? cur.getCurrentPosition().getId() : "—").append('\n');
            if (cur instanceof Plumber) {
                var cp = ((Plumber) cur).getCarriedPump();
                sb.append("Carrying:      ").append(cp != null ? cp.getId() : "none").append('\n');
            }
        }
        JOptionPane.showMessageDialog(this, sb.toString(),
            "Game State", JOptionPane.INFORMATION_MESSAGE);
    }

    private void doEndGame() {
        int choice = JOptionPane.showConfirmDialog(this,
            "End the game now?\n\nThis will stop the timer and show the final result.",
            "End Game", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice == JOptionPane.YES_OPTION) {
            ctrl.endGame();
        }
    }

    // ─── Helper inner classes ─────────────────────────────────────

    private static class HeaderPanel extends JPanel {
        private final String text;
        HeaderPanel(String text) {
            this.text = text;
            setPreferredSize(new Dimension(0, 36));
            setOpaque(false);
        }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(HEADER_BG);
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setFont(new Font("SansSerif", Font.BOLD, 14));
            g.setColor(HEADER_TEXT);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(text, (getWidth() - fm.stringWidth(text)) / 2,
                         (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
            g.dispose();
        }
    }

    /** Standard action button with icon area on the left. */
    private static class ActionBtn extends JButton {
        private final String icon;
        private boolean hover = false;
        ActionBtn(String label, String icon) {
            super(label);
            this.icon = icon;
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setFont(new Font("SansSerif", Font.PLAIN, 12));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true;  repaint(); }
                @Override public void mouseExited(MouseEvent e)  { hover = false; repaint(); }
            });
        }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            RoundRectangle2D shape = new RoundRectangle2D.Double(0, 0, w - 1, h - 1, 10, 10);
            boolean enabled = isEnabled();
            g.setColor(enabled ? (hover ? BTN_HOVER : BTN_BG) : BTN_DISABLED);
            g.fill(shape);
            int iw = h;
            g.setColor(enabled ? new Color(220, 205, 170) : new Color(200, 195, 185));
            g.fillRoundRect(0, 0, iw, h, 10, 10);
            g.fillRect(iw / 2, 0, iw / 2, h);
            g.setColor(enabled ? BTN_BORDER : new Color(190, 182, 165));
            g.setStroke(new BasicStroke(1.2f));
            g.draw(shape);
            g.setFont(new Font("SansSerif", Font.PLAIN, 14));
            g.setColor(enabled ? new Color(70, 80, 110) : BTN_DIS_TEXT);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(icon, (iw - fm.stringWidth(icon)) / 2,
                         (h + fm.getAscent() - fm.getDescent()) / 2);
            g.setFont(new Font("SansSerif", Font.BOLD, 11));
            fm = g.getFontMetrics();
            g.setColor(enabled ? BTN_TEXT : BTN_DIS_TEXT);
            g.drawString(getText(), iw + 6, (h + fm.getAscent() - fm.getDescent()) / 2);
            g.dispose();
        }
        @Override public Dimension getPreferredSize() { return new Dimension(130, 36); }
    }

    /**
     * Distinct red "End Game" button — never mistaken for a gameplay action,
     * always visible so players can stop the game at any time.
     */
    private static class EndGameBtn extends JButton {
        private boolean hover = false;
        EndGameBtn(String label) {
            super(label);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setFont(new Font("SansSerif", Font.BOLD, 13));
            setPreferredSize(new Dimension(0, 38));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true;  repaint(); }
                @Override public void mouseExited(MouseEvent e)  { hover = false; repaint(); }
            });
        }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            boolean en = isEnabled();
            Color top = en ? (hover ? END_HOVER : END_BG) : new Color(180, 150, 150);
            Color bot = en ? top.darker() : new Color(160, 130, 130);
            RoundRectangle2D shape = new RoundRectangle2D.Double(0, 0, w - 1, h - 1, 10, 10);
            g.setPaint(new GradientPaint(0, 0, top, 0, h, bot));
            g.fill(shape);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.15f));
            g.setColor(Color.WHITE);
            g.fillRoundRect(4, 3, w - 8, h / 3, 8, 8);
            g.setComposite(AlphaComposite.SrcOver);
            g.setColor(new Color(100, 20, 20));
            g.setStroke(new BasicStroke(1.5f));
            g.draw(shape);
            g.setFont(new Font("SansSerif", Font.BOLD, 13));
            FontMetrics fm = g.getFontMetrics();
            String t = "⏹  " + getText();
            g.setColor(en ? END_TEXT : new Color(210, 190, 190));
            g.drawString(t, (w - fm.stringWidth(t)) / 2,
                         (h + fm.getAscent() - fm.getDescent()) / 2);
            g.dispose();
        }
    }

    @Override public Dimension getPreferredSize() { return new Dimension(300, 300); }
}
