package view;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import controller.GameEventListener;

/**
 * Scrolling styled action log. Plumber-side messages are rendered in blue,
 * saboteur-side messages in red, and system/event messages in dark orange —
 * matching the visual style of the GUI specification screenshot.
 */
public class ActionLogPanel extends JPanel implements GameEventListener {

    private static final Color HEADER_BG   = new Color(80, 50, 22);
    private static final Color HEADER_TEXT = new Color(255, 240, 200);
    private static final Color LOG_BG      = new Color(250, 244, 230);
    private static final Color COL_OK      = new Color(30, 80, 170);
    private static final Color COL_FAIL    = new Color(180, 30, 30);
    private static final Color COL_EVENT   = new Color(140, 85, 15);
    private static final Color COL_TIME    = new Color(140, 120, 90);

    private final JTextPane pane = new JTextPane();
    private final SimpleDateFormat fmt = new SimpleDateFormat("HH:mm:ss");

    public ActionLogPanel() {
        setLayout(new BorderLayout());
        setBackground(new Color(240, 220, 185));

        add(new HeaderPanel("ACTION LOG"), BorderLayout.NORTH);

        pane.setEditable(false);
        pane.setBackground(LOG_BG);
        pane.setFont(new Font("SansSerif", Font.PLAIN, 12));
        pane.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));

        JScrollPane scroll = new JScrollPane(pane,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(LOG_BG);
        add(scroll, BorderLayout.CENTER);

        append("Session started.", COL_EVENT, Font.ITALIC);
    }

    @Override public void onStateChanged()                          { /* too noisy */ }
    @Override public void onTick(int s)                             { /* skip */ }
    @Override public void onActionResult(boolean ok, String msg)    {
        append((ok ? "✓ " : "✗ ") + msg, ok ? COL_OK : COL_FAIL,
               ok ? Font.PLAIN : Font.BOLD);
    }
    @Override public void onGameEnded(String r, int c, int l)       {
        append("GAME OVER: " + r + "  (collected=" + c + ", lost=" + l + ")",
               COL_EVENT, Font.BOLD);
    }
    @Override public void onPumpBroke(String id)                    {
        append("[EVENT] " + id + " broke down!", COL_EVENT, Font.BOLD);
    }
    @Override public void onPipeManufactured(String ci, String pi)  {
        append("[EVENT] " + ci + " manufactured pipe " + pi, COL_OK, Font.PLAIN);
    }
    @Override public void onPumpManufactured(String ci, String pi)  {
        append("[EVENT] " + ci + " manufactured pump " + pi, COL_OK, Font.PLAIN);
    }

    private void append(String msg, Color col, int style) {
        SwingUtilities.invokeLater(() -> {
            try {
                StyledDocument doc = pane.getStyledDocument();

                SimpleAttributeSet ts = new SimpleAttributeSet();
                StyleConstants.setForeground(ts, COL_TIME);
                StyleConstants.setFontFamily(ts, "Monospaced");
                StyleConstants.setFontSize(ts, 11);
                doc.insertString(doc.getLength(), "[" + fmt.format(new Date()) + "] ", ts);

                SimpleAttributeSet ms = new SimpleAttributeSet();
                StyleConstants.setForeground(ms, col);
                StyleConstants.setBold(ms, (style & Font.BOLD) != 0);
                StyleConstants.setItalic(ms, (style & Font.ITALIC) != 0);
                StyleConstants.setFontFamily(ms, "SansSerif");
                StyleConstants.setFontSize(ms, 12);
                doc.insertString(doc.getLength(), msg + "\n", ms);

                pane.setCaretPosition(doc.getLength());
            } catch (BadLocationException ignored) {}
        });
    }

    private static class HeaderPanel extends JPanel {
        private final String text;
        HeaderPanel(String text) {
            this.text = text;
            setPreferredSize(new Dimension(0, 34));
            setOpaque(false);
        }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setColor(HEADER_BG);
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                               RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setFont(new Font("SansSerif", Font.BOLD, 13));
            g.setColor(HEADER_TEXT);
            FontMetrics fm = g.getFontMetrics();
            int tx = 14;
            g.drawString(text, tx, (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
            g.dispose();
        }
    }

    @Override public Dimension getPreferredSize() { return new Dimension(800, 130); }
}
