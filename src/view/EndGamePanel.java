package view;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import javax.swing.JPanel;
import util.UiAssets;

import controller.GameEventListener;

/**
 * End-of-game screen styled after the reference image:
 * daytime desert background, large title, trophy icon,
 * winner text, water stats, and Play Again / Main Menu buttons.
 */
public class EndGamePanel extends JPanel implements GameEventListener {

    private final MainFrame frame;

    private String resultText = "PLUMBERS WIN";
    private int    collected  = 0;
    private int    lost       = 0;

    // 0 = Play Again, 1 = Main Menu
    private final boolean[] hover   = new boolean[2];
    private final boolean[] pressed = new boolean[2];

    private static final Color CARD_BG     = new Color(62, 38, 12, 238);
    private static final Color CARD_BORDER = new Color(185, 148, 48);
    private static final Color RESULT_GOLD = new Color(255, 220, 70);
    private static final Color STAT_TEXT   = new Color(235, 222, 195);
    private static final Color WATER_BLUE  = new Color(80, 160, 240);
    private static final Color WATER_GREY  = new Color(140, 148, 160);

    // Play Again = green, Main Menu = blue
    private static final Color[][] BTN_GRAD = {
        { new Color(60, 175, 60), new Color(30, 120, 30) },
        { new Color(55, 130, 220), new Color(30, 80, 175) }
    };
    private static final Color[] BTN_BDR = {
        new Color(20, 100, 20), new Color(20, 55, 140)
    };
    private static final String[] BTN_LABELS = { "Play Again", "Main Menu" };

    public EndGamePanel(MainFrame frame, controller.GameController ctrl,
                        model.engine.GameEngine eng) {
        this.frame = frame;
        setOpaque(true);
        setPreferredSize(new Dimension(1280, 800));

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseMoved(MouseEvent e) { updateHover(e.getX(), e.getY()); }
        });
        addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e)  { updatePress(e.getX(), e.getY(), true); }
            @Override public void mouseReleased(MouseEvent e) {
                int idx = hitBtn(e.getX(), e.getY());
                updatePress(e.getX(), e.getY(), false);
                if (idx >= 0) handleClick(idx);
            }
        });
    }

    public void setResult(String result, int collected, int lost) {
        this.resultText = result;
        this.collected  = collected;
        this.lost       = lost;
        repaint();
    }

    @Override protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,  RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        int w = getWidth(), h = getHeight();

        // Paint desert scene programmatically — reference images already contain pre-drawn
        // UI cards and buttons, so drawing them as backgrounds creates visual duplicates.
        paintDesertBg(g, w, h);

        // Title at top
        paintTitle(g, w, h);
        // Central result card
        paintCard(g, w, h);

        g.dispose();
    }

    // ── Desert background — delegates to shared rich painter ─────
    private void paintDesertBg(Graphics2D g, int w, int h) {
        // Use DesertPainter for the same rich scene as the main menu
        DesertPainter.paint(g, w, h);
        // Overlay mountain asset image if available
        java.awt.image.BufferedImage mtn = util.UiAssets.load("images/mountains.png");
        if (mtn != null && mtn.getWidth() > 1) {
            int groundY = (int)(h * 0.58);
            int mh = (int)(groundY * 0.60);
            int mw = mh * mtn.getWidth() / mtn.getHeight();
            int xStart = (w - mw) / 2;
            g.drawImage(mtn, xStart, groundY - mh, mw, mh, null);
        }
    }

    // ── Title ─────────────────────────────────────────────────────
    private void paintTitle(Graphics2D g, int w, int h) {
        String title = "PIPES IN THE DESERT";
        int sz = Math.max(42, (int)(w / 22f));
        Font f = new Font("Serif", Font.BOLD, sz);
        g.setFont(f);
        FontMetrics fm = g.getFontMetrics();
        int tx = (w - fm.stringWidth(title)) / 2;
        int ty = (int)(h * 0.13);

        for (int dx = -3; dx <= 3; dx++)
            for (int dy = -3; dy <= 3; dy++)
                if (dx != 0 || dy != 0) {
                    g.setColor(new Color(70, 35, 5)); g.drawString(title, tx+dx, ty+dy);
                }
        g.setPaint(new GradientPaint(tx, ty - fm.getAscent(),
            new Color(255, 235, 80), tx, ty, new Color(210, 145, 20)));
        g.drawString(title, tx, ty);

        // Side pipe ornaments
        paintPipeOrnament(g, tx - 70, ty - fm.getHeight()/2, false);
        paintPipeOrnament(g, tx + fm.stringWidth(title) + 10, ty - fm.getHeight()/2, true);
    }

    private void paintPipeOrnament(Graphics2D g, int x, int cy, boolean right) {
        int pr = 12, segLen = 50;
        Color pc = new Color(175, 185, 200), pb = new Color(100, 115, 135);
        int sx = right ? x : x - segLen;
        g.setColor(pb); g.fillRoundRect(sx - 2, cy - pr - 2, segLen + 4, pr*2+4, pr, pr);
        g.setColor(pc); g.fillRoundRect(sx, cy - pr, segLen, pr*2, pr, pr);
        int jx = right ? x + segLen - pr - 4 : x - pr - 4;
        g.setColor(new Color(200, 170, 80)); g.fillOval(jx, cy - pr - 4, (pr+4)*2, (pr+4)*2);
        g.setColor(pc); g.fillOval(jx + 4, cy - pr, pr*2, pr*2);
    }

    // ── Result card ───────────────────────────────────────────────
    private void paintCard(Graphics2D g, int w, int h) {
        int cardW = (int) Math.min(580, w * 0.50);
        int cardH = (int) Math.min(440, h * 0.60);
        int cardX = (w - cardW) / 2;
        int cardY = (int)(h * 0.24);

        // Shadow
        g.setColor(new Color(0, 0, 0, 90));
        g.fillRoundRect(cardX + 5, cardY + 5, cardW, cardH, 22, 22);
        // Body
        g.setColor(CARD_BG);
        g.fillRoundRect(cardX, cardY, cardW, cardH, 22, 22);
        // Gold border
        g.setStroke(new BasicStroke(3f));
        g.setColor(CARD_BORDER);
        g.drawRoundRect(cardX, cardY, cardW, cardH, 22, 22);
        g.setStroke(new BasicStroke(1.2f));
        g.setColor(new Color(225, 185, 80, 90));
        g.drawRoundRect(cardX + 7, cardY + 7, cardW - 14, cardH - 14, 16, 16);
        g.setStroke(new BasicStroke(1));

        int cx = cardX + cardW / 2;

        // Trophy icon
        int trophyY = cardY + (int)(cardH * 0.19);
        paintTrophy(g, cx, trophyY, (int)(cardH * 0.18));

        // Result text
        int rSz = Math.max(34, (int)(cardW / 13f));
        g.setFont(new Font("Serif", Font.BOLD, rSz));
        FontMetrics fm = g.getFontMetrics();
        int tw = fm.stringWidth(resultText);
        int ry = cardY + (int)(cardH * 0.50);
        // Stars
        g.setFont(new Font("SansSerif", Font.BOLD, rSz - 10));
        g.setColor(RESULT_GOLD);
        g.drawString("★", cx - tw/2 - 42, ry);
        g.drawString("★", cx + tw/2 + 10, ry);
        // Text shadow + fill
        g.setFont(new Font("Serif", Font.BOLD, rSz));
        g.setColor(new Color(60, 32, 8));
        g.drawString(resultText, cx - tw/2 + 2, ry + 2);
        g.setColor(RESULT_GOLD);
        g.drawString(resultText, cx - tw/2, ry);

        // Separator
        g.setColor(new Color(150, 108, 40)); g.setStroke(new BasicStroke(1.5f));
        g.drawLine(cardX + 28, ry + 14, cardX + cardW - 28, ry + 14);
        g.setStroke(new BasicStroke(1));

        // Stats
        int rowH = (int)(cardH * 0.14);
        paintStatRow(g, cardX, ry + 28, cardW, rowH, "Water Collected:", "" + collected, WATER_BLUE, true);
        paintStatRow(g, cardX, ry + 28 + rowH + 8, cardW, rowH, "Water Lost:", "" + lost, WATER_GREY, false);

        // Buttons
        int btnW = (int)(cardW * 0.38);
        int btnH = (int)(cardH * 0.12);
        int btnGap = (int)(cardW * 0.06);
        int bTotalW = btnW * 2 + btnGap;
        int bx0 = cx - bTotalW / 2;
        int by  = cardY + cardH - btnH - 20;
        paintEndBtn(g, 0, bx0,              by, btnW, btnH);
        paintEndBtn(g, 1, bx0 + btnW + btnGap, by, btnW, btnH);
    }

    private void paintTrophy(Graphics2D g, int cx, int cy, int size) {
        int r = size / 2;
        // Golden pipe cross
        Color gold = new Color(195, 152, 38);
        Color goldH = new Color(245, 205, 80);
        g.setColor(gold);
        g.fillRoundRect(cx - r, cy - r/4, r*2, r/2, 8, 8);
        g.fillRoundRect(cx - r/4, cy - r, r/2, r*2, 8, 8);
        // Four joints (gold balls)
        for (int[] d : new int[][]{{-r,0},{r,0},{0,-r},{0,r}}) {
            g.setColor(goldH);
            g.fillOval(cx+d[0]-r/3, cy+d[1]-r/3, r*2/3, r*2/3);
            g.setColor(gold.darker());
            g.setStroke(new BasicStroke(1.5f));
            g.drawOval(cx+d[0]-r/3, cy+d[1]-r/3, r*2/3, r*2/3);
            g.setStroke(new BasicStroke(1));
        }
        // Water drop (blue)
        g.setColor(new Color(80, 160, 235));
        int[] dropX = {cx, cx - r/4, cx + r/4};
        int[] dropY = {cy - r/4, cy + r/5, cy + r/5};
        g.fillPolygon(dropX, dropY, 3);
        g.fillOval(cx - r/4, cy - r/4, r/2, r/2);
        // Laurels (green arcs)
        g.setColor(new Color(55, 145, 50));
        g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawArc(cx - r - 14, cy - r/2, r + 4, r + 4, 190, 140);
        g.drawArc(cx + 10,     cy - r/2, r + 4, r + 4, -10, -140);
        g.setStroke(new BasicStroke(1));
    }

    private void paintStatRow(Graphics2D g, int cardX, int y, int cardW, int rowH,
                               String key, String val, Color valCol, boolean blueIcon) {
        // Row bg
        g.setColor(new Color(35, 22, 8, 130));
        g.fillRoundRect(cardX + 20, y, cardW - 40, rowH, 8, 8);
        // Drop icon
        int ix = cardX + 42, iy = y + (rowH - 20) / 2;
        g.setColor(valCol);
        int[] dxp = {ix + 10, ix + 3, ix + 17}; int[] dyp = {iy + 20, iy + 10, iy + 10};
        g.fillPolygon(dxp, dyp, 3); g.fillOval(ix + 3, iy, 14, 14);
        // Key
        g.setFont(new Font("SansSerif", Font.BOLD, 17));
        g.setColor(STAT_TEXT);
        g.drawString(key, ix + 28, y + (rowH + g.getFontMetrics().getAscent() - g.getFontMetrics().getDescent()) / 2);
        // Value (right-aligned)
        g.setFont(new Font("SansSerif", Font.BOLD, 21));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(valCol);
        g.drawString(val, cardX + cardW - 42 - fm.stringWidth(val),
                     y + (rowH + fm.getAscent() - fm.getDescent()) / 2);
    }

    private void paintEndBtn(Graphics2D g, int idx, int x, int y, int w, int h) {
        Color top = BTN_GRAD[idx][0], bot = BTN_GRAD[idx][1];
        if (hover[idx])   { top = top.brighter(); bot = bot.brighter(); }
        if (pressed[idx]) { top = bot.darker(); bot = bot.darker(); }

        g.setColor(new Color(0,0,0,75)); g.fillRoundRect(x+2, y+3, w, h, 12, 12);
        g.setPaint(new GradientPaint(x, y, top, x, y+h, bot));
        g.fillRoundRect(x, y, w, h, 12, 12);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.22f));
        g.setColor(Color.WHITE); g.fillRoundRect(x+4, y+3, w-8, h/3, 7, 7);
        g.setComposite(AlphaComposite.SrcOver);
        g.setColor(BTN_BDR[idx]); g.setStroke(new BasicStroke(2.2f));
        g.drawRoundRect(x, y, w, h, 12, 12); g.setStroke(new BasicStroke(1));

        g.setFont(new Font("SansSerif", Font.BOLD, Math.max(14, h * 38/100)));
        FontMetrics fm = g.getFontMetrics();
        int tw = fm.stringWidth(BTN_LABELS[idx]);
        int tx = x + (w - tw) / 2, ty = y + (h + fm.getAscent() - fm.getDescent()) / 2;
        g.setColor(new Color(0,0,0,130)); g.drawString(BTN_LABELS[idx], tx+1, ty+1);
        g.setColor(Color.WHITE); g.drawString(BTN_LABELS[idx], tx, ty);
    }

    // ── Button hit-testing ────────────────────────────────────────
    private int[] btnBounds(int idx) {
        int w = getWidth(), h = getHeight();
        int cardW = (int) Math.min(580, w * 0.50);
        int cardH = (int) Math.min(440, h * 0.60);
        int cardX = (w - cardW) / 2;
        int cardY = (int)(h * 0.24);
        int cx = cardX + cardW / 2;
        int btnW = (int)(cardW * 0.38), btnH = (int)(cardH * 0.12);
        int btnGap = (int)(cardW * 0.06);
        int bTotalW = btnW * 2 + btnGap;
        int bx0 = cx - bTotalW / 2;
        int by  = cardY + cardH - btnH - 20;
        int bx  = bx0 + idx * (btnW + btnGap);
        return new int[]{bx, by, btnW, btnH};
    }

    private int hitBtn(int mx, int my) {
        for (int i = 0; i < 2; i++) {
            int[] b = btnBounds(i); if (mx>=b[0]&&mx<=b[0]+b[2]&&my>=b[1]&&my<=b[1]+b[3]) return i;
        }
        return -1;
    }

    private void updateHover(int mx, int my) {
        int hit = hitBtn(mx, my); boolean changed = false;
        for (int i = 0; i < 2; i++) { boolean h = (i==hit); if (hover[i]!=h){hover[i]=h;changed=true;} }
        setCursor(hit>=0?Cursor.getPredefinedCursor(Cursor.HAND_CURSOR):Cursor.getDefaultCursor());
        if (changed) repaint();
    }

    private void updatePress(int mx, int my, boolean down) {
        int hit = hitBtn(mx, my); boolean changed = false;
        for (int i = 0; i < 2; i++) { boolean p = down&&(i==hit); if (pressed[i]!=p){pressed[i]=p;changed=true;} }
        if (changed) repaint();
    }

    private void handleClick(int idx) {
        if (idx == 0) frame.loadGame(frame.getSelectedScenario());
        else          frame.showMenu();
    }

    @Override public void onStateChanged()                         { }
    @Override public void onTick(int s)                            { }
    @Override public void onActionResult(boolean ok, String m)     { }
    @Override public void onGameEnded(String r, int c, int l)      { setResult(r, c, l); }
    @Override public void onPumpBroke(String id)                   { }
    @Override public void onPipeManufactured(String ci, String pi) { }
    @Override public void onPumpManufactured(String ci, String pi) { }
}
