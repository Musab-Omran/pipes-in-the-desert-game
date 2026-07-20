package view;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import javax.swing.JPanel;
import util.UiAssets;

/**
 * Main menu panel styled after the reference image:
 * daytime desert sky, large sun, mountains, mesas, cacti,
 * pipe title ornaments, and three coloured menu buttons.
 */
public class MainMenuPanel extends JPanel {

    private final MainFrame frame;

    // Button indices
    private static final int BTN_START    = 0;
    private static final int BTN_HOW      = 1;
    private static final int BTN_EXIT     = 2;
    private static final int BTN_COUNT    = 3;

    private static final String[] BTN_LABELS = { "START GAME", "HOW TO PLAY", "EXIT GAME" };
    private static final String[] BTN_ICONS  = { "▶", "?", "✕" };

    // Button palette: green, blue, red
    private static final Color[][] BTN_GRAD = {
        { new Color(60, 175, 60),  new Color(30, 120, 30)  },
        { new Color(55, 130, 220), new Color(30,  80, 175) },
        { new Color(210, 55,  45), new Color(155, 25,  20) }
    };
    private static final Color[] BTN_BORDER = {
        new Color(20, 100, 20),
        new Color(20,  55, 140),
        new Color(110,  10,  10)
    };

    private final boolean[] hover   = new boolean[BTN_COUNT];
    private final boolean[] pressed = new boolean[BTN_COUNT];

    // Cached background
    private BufferedImage bgCache;
    private int bgW, bgH;

    public MainMenuPanel(MainFrame frame) {
        this.frame = frame;
        setOpaque(true);
        setPreferredSize(new Dimension(1280, 800));

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseMoved(MouseEvent e) { updateHover(e.getX(), e.getY()); }
        });
        addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e)  { updatePress(e.getX(), e.getY(), true);  }
            @Override public void mouseReleased(MouseEvent e) {
                int idx = hitBtn(e.getX(), e.getY());
                updatePress(e.getX(), e.getY(), false);
                if (idx >= 0) handleClick(idx);
            }
        });
    }

    @Override protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,  RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        int w = getWidth(), h = getHeight();

        // Paint desert scene programmatically (no image background - reference images contain
        // pre-drawn UI and would cause duplicate buttons/elements if drawn as backgrounds)
        paintDesertBackground(g, w, h);

        // Title
        paintTitle(g, w, h);
        // Card + buttons
        paintCard(g, w, h);

        g.dispose();
    }

    // ── Desert background (fallback) ──────────────────────────────
    private void paintDesertBackground(Graphics2D g, int w, int h) {
        // Sky gradient – daytime blue
        GradientPaint sky = new GradientPaint(0, 0, new Color(55, 130, 220),
                                              0, h * 0.55f, new Color(135, 195, 255));
        g.setPaint(sky);
        g.fillRect(0, 0, w, (int)(h * 0.56));

        // Sun
        int sx = (int)(w * 0.50), sy = (int)(h * 0.07), sr = (int)(h * 0.11);
        // Rays
        g.setColor(new Color(255, 235, 80, 120));
        g.setStroke(new BasicStroke(5, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int a = 0; a < 360; a += 30) {
            double rad = Math.toRadians(a);
            g.drawLine((int)(sx + sr * Math.cos(rad)), (int)(sy + sr * Math.sin(rad)),
                       (int)(sx + (sr + 28) * Math.cos(rad)), (int)(sy + (sr + 28) * Math.sin(rad)));
        }
        g.setStroke(new BasicStroke(1));
        g.setPaint(new RadialGradientPaint(sx, sy, sr, new float[]{0f, 0.7f, 1f},
            new Color[]{Color.WHITE, new Color(255, 220, 60), new Color(235, 165, 30)}));
        g.fillOval(sx - sr, sy - sr, sr * 2, sr * 2);

        // Clouds
        paintCloud(g, (int)(w*0.15), (int)(h*0.10), 90);
        paintCloud(g, (int)(w*0.72), (int)(h*0.08), 110);

        // Mountains (grey/blue)
        int groundY = (int)(h * 0.55);
        paintMountains(g, w, groundY);

        // Desert ground
        GradientPaint ground = new GradientPaint(0, groundY, new Color(200, 95, 30),
                                                  0, h, new Color(155, 65, 15));
        g.setPaint(ground);
        g.fillRect(0, groundY, w, h - groundY);

        // Mesas (buttes) on sides
        paintMesa(g, (int)(w * 0.07), groundY, (int)(w * 0.15), (int)(h * 0.28));
        paintMesa(g, (int)(w * 0.78), groundY, (int)(w * 0.15), (int)(h * 0.25));

        // Cacti
        paintCactus(g, (int)(w * 0.13), groundY - 10, 75);
        paintCactus(g, (int)(w * 0.22), groundY - 5,  55);
        paintCactus(g, (int)(w * 0.80), groundY - 8,  80);
        paintCactus(g, (int)(w * 0.88), groundY - 3,  52);

        // Pipe decorations on sides (horizontal pipes)
        BufferedImage pipeH = UiAssets.load("images/pipe_horizontal.png");
        if (pipeH != null && pipeH.getWidth() > 1) {
            int ph = 35, pw = 140;
            g.drawImage(pipeH, 0, (int)(h * 0.63) - ph/2, pw, ph, null);
            g.drawImage(pipeH, w - pw, (int)(h * 0.63) - ph/2, pw, ph, null);
        }
    }

    private void paintCloud(Graphics2D g, int cx, int cy, int s) {
        g.setColor(new Color(255, 255, 255, 200));
        g.fillOval(cx - s/2, cy - s/3, s, s * 2/3);
        g.fillOval(cx - s/3, cy - s/2, s * 2/3, s * 2/3);
        g.fillOval(cx + s/8, cy - s/3, s * 2/3, s/2);
    }

    private void paintMountains(Graphics2D g, int w, int groundY) {
        // Try to use the mountain asset image first
        java.awt.image.BufferedImage mtn = util.UiAssets.load("images/mountains.png");
        if (mtn != null && mtn.getWidth() > 1) {
            // Draw mountain image spanning the full width, bottom-aligned to groundY
            int mh = (int)(groundY * 0.65);
            int mw = mh * mtn.getWidth() / mtn.getHeight();
            // Draw centered and tiled if needed
            int xStart = (w - mw) / 2;
            g.drawImage(mtn, xStart, groundY - mh, mw, mh, null);
            // Also draw partial copies for full coverage
            if (xStart > 0) g.drawImage(mtn, xStart - mw, groundY - mh, mw, mh, null);
            if (xStart + mw < w) g.drawImage(mtn, xStart + mw, groundY - mh, mw, mh, null);
            return;
        }
        // Fallback: painted mountains
        int[] xs = {0, w/8, w/5, 3*w/8, w/2, 5*w/8, 3*w/4, 7*w/8, w};
        int[] ys = {groundY, groundY - (int)(groundY*0.35), groundY - (int)(groundY*0.50),
                    groundY - (int)(groundY*0.38), groundY - (int)(groundY*0.55),
                    groundY - (int)(groundY*0.42), groundY - (int)(groundY*0.58),
                    groundY - (int)(groundY*0.35), groundY};
        g.setColor(new Color(95, 118, 155));
        g.fillPolygon(xs, ys, xs.length);
        g.setColor(new Color(225, 235, 245));
        int[][] peaks = {{w/5, groundY - (int)(groundY*0.50)},
                         {w/2, groundY - (int)(groundY*0.55)},
                         {3*w/4, groundY - (int)(groundY*0.58)}};
        for (int[] pk : peaks) {
            int[] px = {pk[0]-20, pk[0], pk[0]+20};
            int[] py = {pk[1]+30, pk[1], pk[1]+30};
            g.fillPolygon(px, py, 3);
        }
    }

    private void paintMesa(Graphics2D g, int cx, int groundY, int width, int height) {
        int x = cx - width / 2;
        GradientPaint mesa = new GradientPaint(x, groundY - height, new Color(185, 85, 38),
                                               x, groundY, new Color(145, 62, 22));
        g.setPaint(mesa);
        int[] xs = {x, x + width/8, x + width - width/8, x + width};
        int[] ys = {groundY, groundY - height, groundY - height, groundY};
        g.fillPolygon(xs, ys, 4);
        g.setColor(new Color(210, 110, 50));
        g.fillRect(x + width/8, groundY - height, width - width/4, 10);
    }

    private void paintCactus(Graphics2D g, int x, int baseY, int h) {
        int tw = h / 5;
        g.setColor(new Color(45, 145, 55));
        // Main trunk
        g.fillRoundRect(x - tw/2, baseY - h, tw, h, tw/2, tw/2);
        // Left arm
        g.fillRoundRect(x - tw*2, baseY - (int)(h * 0.7), tw*2, tw, tw/2, tw/2);
        g.fillRoundRect(x - tw*2, baseY - (int)(h * 0.85), tw, (int)(h * 0.2), tw/2, tw/2);
        // Right arm
        g.fillRoundRect(x + tw/2, baseY - (int)(h * 0.6), tw*2, tw, tw/2, tw/2);
        g.fillRoundRect(x + tw + tw/2, baseY - (int)(h * 0.75), tw, (int)(h * 0.2), tw/2, tw/2);
    }

    // ── Title ─────────────────────────────────────────────────────
    private void paintTitle(Graphics2D g, int w, int h) {
        String title = "PIPES IN THE DESERT";
        float sz = Math.max(48f, w / 21f);
        Font f = new Font("Serif", Font.BOLD, (int) sz);
        g.setFont(f);
        FontMetrics fm = g.getFontMetrics();
        int tx = (w - fm.stringWidth(title)) / 2;
        int ty = (int)(h * 0.14);

        // Thick dark outline (multiple pass)
        g.setColor(new Color(70, 35, 5));
        for (int dx = -3; dx <= 3; dx++)
            for (int dy = -3; dy <= 3; dy++)
                if (dx != 0 || dy != 0) g.drawString(title, tx + dx, ty + dy);

        // Gold gradient fill via TextLayout
        GradientPaint grad = new GradientPaint(tx, ty - fm.getAscent(),
            new Color(255, 235, 80), tx, ty, new Color(210, 145, 20));
        g.setPaint(grad);
        g.drawString(title, tx, ty);

        // Pipe ornaments on left and right of title
        paintPipeOrnament(g, tx - 65, ty - fm.getHeight()/2, false);
        paintPipeOrnament(g, tx + fm.stringWidth(title) + 10, ty - fm.getHeight()/2, true);
    }

    private void paintPipeOrnament(Graphics2D g, int x, int cy, boolean flipped) {
        int pr = 12; // pipe radius
        Color pipeCol = new Color(175, 185, 200);
        Color pipeBdr = new Color(100, 115, 135);
        // Horizontal segment
        int segLen = 50;
        int sx = flipped ? x : x - segLen;
        g.setColor(pipeBdr);
        g.fillRoundRect(sx - 2, cy - pr - 2, segLen + 4, pr * 2 + 4, pr, pr);
        g.setColor(pipeCol);
        g.fillRoundRect(sx, cy - pr, segLen, pr * 2, pr, pr);
        // Joint cap
        int jx = flipped ? x + segLen - pr - 4 : x - pr - 4;
        g.setColor(new Color(200, 170, 80));
        g.fillOval(jx, cy - pr - 4, (pr + 4) * 2, (pr + 4) * 2);
        g.setColor(pipeCol);
        g.fillOval(jx + 4, cy - pr, pr * 2, pr * 2);
    }

    // ── Menu card ─────────────────────────────────────────────────
    private void paintCard(Graphics2D g, int w, int h) {
        int cardW = (int) Math.min(440, w * 0.38);
        int btnH  = (int)(h * 0.085);
        int gap   = (int)(h * 0.018);
        int cardH = BTN_COUNT * btnH + (BTN_COUNT + 1) * gap + 24;
        int cardX = (w - cardW) / 2;
        int cardY = (int)(h * 0.43);

        // Card shadow
        g.setColor(new Color(0, 0, 0, 90));
        g.fillRoundRect(cardX + 4, cardY + 4, cardW, cardH, 18, 18);

        // Card body
        g.setColor(new Color(60, 38, 14, 230));
        g.fillRoundRect(cardX, cardY, cardW, cardH, 18, 18);

        // Card border (gold)
        g.setStroke(new BasicStroke(3f));
        g.setColor(new Color(185, 145, 45));
        g.drawRoundRect(cardX, cardY, cardW, cardH, 18, 18);
        g.setColor(new Color(230, 195, 90, 80));
        g.setStroke(new BasicStroke(1.2f));
        g.drawRoundRect(cardX + 6, cardY + 6, cardW - 12, cardH - 12, 12, 12);
        g.setStroke(new BasicStroke(1));

        // Pipe joint decorations on card sides (horizontal pipe stubs)
        int stubY = cardY + cardH / 2;
        paintCardPipeStub(g, cardX - 50, stubY, false);
        paintCardPipeStub(g, cardX + cardW, stubY, true);

        // Buttons
        int bx = cardX + gap;
        int bw = cardW - gap * 2;
        for (int i = 0; i < BTN_COUNT; i++) {
            int by = cardY + gap + i * (btnH + gap);
            paintButton(g, i, bx, by, bw, btnH);
        }
    }

    private void paintCardPipeStub(Graphics2D g, int x, int cy, boolean right) {
        int pr = 10;
        Color pc = new Color(175, 185, 200);
        Color pb = new Color(90, 105, 125);
        int len = 48;
        int sx = right ? x : x - len;
        g.setColor(pb); g.fillRoundRect(sx - 2, cy - pr - 2, len + 4, pr*2+4, pr, pr);
        g.setColor(pc); g.fillRoundRect(sx, cy - pr, len, pr*2, pr, pr);
        // Blue round joint
        g.setColor(new Color(50, 100, 200));
        g.fillOval(right ? x + len - 14 : x - 16, cy - 10, 20, 20);
        g.setColor(new Color(120, 170, 255));
        g.fillOval(right ? x + len - 10 : x - 12, cy - 6, 12, 12);
    }

    private void paintButton(Graphics2D g, int idx, int x, int y, int w, int h) {
        Color top = BTN_GRAD[idx][0];
        Color bot = BTN_GRAD[idx][1];
        if (hover[idx])   { top = top.brighter(); bot = bot.brighter(); }
        if (pressed[idx]) { top = bot.darker(); bot = bot.darker(); }

        // Shadow
        g.setColor(new Color(0, 0, 0, 80));
        g.fillRoundRect(x + 2, y + 3, w, h, 12, 12);

        // Body
        g.setPaint(new GradientPaint(x, y, top, x, y + h, bot));
        g.fillRoundRect(x, y, w, h, 12, 12);

        // Highlight
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.25f));
        g.setColor(Color.WHITE);
        g.fillRoundRect(x + 4, y + 3, w - 8, h / 3, 7, 7);
        g.setComposite(AlphaComposite.SrcOver);

        // Border
        g.setColor(BTN_BORDER[idx]);
        g.setStroke(new BasicStroke(2.2f));
        g.drawRoundRect(x, y, w, h, 12, 12);
        g.setStroke(new BasicStroke(1));

        // Text
        int fontSize = Math.max(14, h * 38 / 100);
        g.setFont(new Font("SansSerif", Font.BOLD, fontSize));
        FontMetrics fm = g.getFontMetrics();
        String label = BTN_LABELS[idx];
        int tw = fm.stringWidth(label);
        int tx = x + (w - tw) / 2 - 14;
        int ty = y + (h + fm.getAscent() - fm.getDescent()) / 2;
        g.setColor(new Color(0, 0, 0, 130));
        g.drawString(label, tx + 1, ty + 1);
        g.setColor(Color.WHITE);
        g.drawString(label, tx, ty);

        // Icon on right
        g.setFont(new Font("SansSerif", Font.PLAIN, fontSize));
        g.setColor(new Color(255, 255, 255, 200));
        g.drawString(BTN_ICONS[idx], x + w - fm.stringWidth(BTN_ICONS[idx]) - 16, ty);
    }

    // ── Button interaction ────────────────────────────────────────
    private int[] btnBounds(int idx) {
        int w = getWidth(), h = getHeight();
        int cardW = (int) Math.min(440, w * 0.38);
        int btnH  = (int)(h * 0.085);
        int gap   = (int)(h * 0.018);
        int cardH = BTN_COUNT * btnH + (BTN_COUNT + 1) * gap + 24;
        int cardX = (w - cardW) / 2;
        int cardY = (int)(h * 0.43);
        int bx = cardX + gap;
        int bw = cardW - gap * 2;
        int by = cardY + gap + idx * (btnH + gap);
        return new int[]{bx, by, bw, btnH};
    }

    private int hitBtn(int mx, int my) {
        for (int i = 0; i < BTN_COUNT; i++) {
            int[] b = btnBounds(i);
            if (mx >= b[0] && mx <= b[0]+b[2] && my >= b[1] && my <= b[1]+b[3]) return i;
        }
        return -1;
    }

    private void updateHover(int mx, int my) {
        int hit = hitBtn(mx, my);
        boolean changed = false;
        for (int i = 0; i < BTN_COUNT; i++) {
            boolean h = (i == hit);
            if (hover[i] != h) { hover[i] = h; changed = true; }
        }
        setCursor(hit >= 0 ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                           : Cursor.getDefaultCursor());
        if (changed) repaint();
    }

    private void updatePress(int mx, int my, boolean down) {
        int hit = hitBtn(mx, my);
        boolean changed = false;
        for (int i = 0; i < BTN_COUNT; i++) {
            boolean p = down && (i == hit);
            if (pressed[i] != p) { pressed[i] = p; changed = true; }
        }
        if (changed) repaint();
    }

    private void handleClick(int idx) {
        switch (idx) {
            case BTN_START -> frame.loadGame(frame.getSelectedScenario());
            case BTN_HOW   -> showHowToPlay();
            case BTN_EXIT  -> System.exit(0);
        }
    }

    private void showHowToPlay() {
        String[] sections = {
            "OBJECTIVE",
            "Plumbers try to collect as much water as possible in the cistern.\n" +
            "Saboteurs try to cause water to leak and be lost.",

            "PLUMBERS",
            "\u2022 Move between pipes, pumps, springs, and cisterns.\n" +
            "\u2022 Connect or disconnect pipe ends to build/modify the network.\n" +
            "\u2022 Pick up a pump from the cistern and insert it into a pipe.\n" +
            "\u2022 Change a pump's flow direction.",

            "SABOTEURS",
            "\u2022 Move around the field.\n" +
            "\u2022 Puncture pipes to cause leaks.\n" +
            "\u2022 Change a pump's flow direction to disrupt the network.",

            "WATER FLOW",
            "Water flows from the Spring through pipes, driven by pumps, into the Cistern.\n" +
            "A broken pump stops flow. A leaking pipe loses water each tick.\n" +
            "Free pipe ends also cause water loss.",

            "WINNING",
            "The team with the better score at game end wins.\n" +
            "Plumbers score for water collected; Saboteurs score for water lost."
        };

        javax.swing.JPanel panel = new javax.swing.JPanel();
        panel.setLayout(new java.awt.GridLayout(0, 1, 0, 6));
        panel.setBackground(new Color(245, 235, 210));

        for (int i = 0; i < sections.length; i += 2) {
            javax.swing.JLabel title = new javax.swing.JLabel(sections[i]);
            title.setFont(new Font("SansSerif", Font.BOLD, 13));
            title.setForeground(new Color(140, 80, 10));
            panel.add(title);

            javax.swing.JTextArea body = new javax.swing.JTextArea(sections[i + 1]);
            body.setFont(new Font("SansSerif", Font.PLAIN, 12));
            body.setEditable(false);
            body.setBackground(new Color(245, 235, 210));
            body.setLineWrap(true);
            body.setWrapStyleWord(true);
            panel.add(body);
        }

        javax.swing.JScrollPane scroll = new javax.swing.JScrollPane(panel);
        scroll.setPreferredSize(new Dimension(440, 320));
        scroll.setBorder(null);

        javax.swing.JOptionPane.showMessageDialog(
            frame, scroll, "How to Play \u2014 Pipes in the Desert",
            javax.swing.JOptionPane.PLAIN_MESSAGE
        );
    }
}