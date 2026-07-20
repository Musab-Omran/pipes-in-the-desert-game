package view;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Paints a rich desert scene using only Java2D — no image assets required.
 *
 * Visual language matches the reference art:
 *  - Deep night-to-dawn gradient sky with tiny stars
 *  - Large glowing sun centre-right
 *  - Layered blue-grey mountain silhouettes
 *  - Sandy orange ground
 *  - Decorative horizontal pipes at ground level
 *  - Green saguaro cacti with arms
 *  - Weathered wooden barrels
 */
public final class DesertPainter {

    // ── Palette ────────────────────────────────────────────────────
    private static final Color SKY_TOP      = new Color(8,  18,  65);
    private static final Color SKY_MID      = new Color(45, 90, 175);
    private static final Color SKY_BOTTOM   = new Color(90, 165, 230);
    private static final Color GROUND_TOP   = new Color(205, 115, 45);
    private static final Color GROUND_BOT   = new Color(160,  72, 22);
    private static final Color MTN_BACK     = new Color(45,  65, 105);
    private static final Color MTN_MID      = new Color(65,  95, 138);
    private static final Color MTN_FRONT    = new Color(88, 122, 165);
    private static final Color MTN_SNOW     = new Color(220, 232, 248, 210);
    private static final Color SUN_CORE     = new Color(255, 235,  70);
    private static final Color SUN_GLOW1    = new Color(255, 210,  50, 110);
    private static final Color SUN_GLOW2    = new Color(255, 190,  20,  55);
    private static final Color CACTUS       = new Color(55, 128,  55);
    private static final Color CACTUS_DARK  = new Color(35, 100,  35);
    private static final Color PIPE_LIGHT   = new Color(205, 210, 218);
    private static final Color PIPE_MID     = new Color(155, 162, 172);
    private static final Color PIPE_DARK    = new Color(110, 118, 128);
    private static final Color BARREL_TOP   = new Color(175, 100,  50);
    private static final Color BARREL_BOT   = new Color(125,  65,  22);
    private static final Color BARREL_BAND  = new Color( 85,  42,  15);

    private DesertPainter() {}

    // ── Public API ─────────────────────────────────────────────────

    /** Full desert scene for Main Menu and End Game screens. */
    public static void paint(Graphics2D g0, int w, int h) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,         RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,    RenderingHints.VALUE_STROKE_PURE);

        int groundY = (int)(h * 0.58);

        paintSky       (g, w, h, groundY);
        paintStars     (g, w, groundY);
        paintSun       (g, w, groundY);
        paintMountains (g, w, groundY);
        paintGround    (g, w, h, groundY);
        paintDecorPipes(g, w, h, groundY);
        paintCacti     (g, w, h, groundY);
        paintBarrels   (g, w, h, groundY);
        g.dispose();
    }

    /** Lightweight sand-tile grid for the gameplay map background. */
    public static void paintMapGrid(Graphics2D g, int x, int y, int w, int h, int cellSize) {
        // Sandy base
        g.setPaint(new GradientPaint(0, y, new Color(225, 195, 150),
                                     0, y + h, new Color(200, 168, 120)));
        g.fillRect(x, y, w, h);

        // Faint tile grid
        g.setColor(new Color(185, 155, 105, 130));
        g.setStroke(new BasicStroke(0.8f));
        for (int gx = x; gx <= x + w; gx += cellSize) g.drawLine(gx, y, gx, y + h);
        for (int gy = y; gy <= y + h; gy += cellSize) g.drawLine(x, gy, x + w, gy);

        // Alternating tile tint
        g.setColor(new Color(195, 162, 110, 45));
        for (int col = 0; col * cellSize < w; col++)
            for (int row = 0; row * cellSize < h; row++)
                if ((col + row) % 2 == 0)
                    g.fillRect(x + col*cellSize+1, y + row*cellSize+1, cellSize-2, cellSize-2);

        g.setStroke(new BasicStroke(1f));
    }

    // ── Private layers ─────────────────────────────────────────────

    private static void paintSky(Graphics2D g, int w, int h, int groundY) {
        g.setPaint(new GradientPaint(0, 0, SKY_TOP, 0, groundY * 0.6f, SKY_MID));
        g.fillRect(0, 0, w, (int)(groundY * 0.6));
        g.setPaint(new GradientPaint(0, (int)(groundY * 0.6), SKY_MID, 0, groundY, SKY_BOTTOM));
        g.fillRect(0, (int)(groundY * 0.6), w, groundY - (int)(groundY * 0.6) + 1);
    }

    private static void paintStars(Graphics2D g, int w, int groundY) {
        int[][] stars = {
            {4,7},{14,22},{27,9},{58,17},{88,5},{115,14},{155,3},{194,12},
            {235,20},{275,8},{318,16},{355,4},{398,12},{445,2},{488,18},
            {525,8},{568,14},{608,3},{648,10},{695,18},{738,5},{775,14},
            {818,2},{856,16},{898,6},{938,20},{75,28},{195,33},{395,26},
            {595,30},{795,24},{96,2},{296,4},{495,1},{695,6},{840,10},
            {120,40},{350,38},{550,42},{750,36},{950,40}
        };
        for (int[] s : stars) {
            int sx = (int)(s[0] / 1000.0 * w);
            int sy = (int)(s[1] / 100.0  * groundY);
            int alpha = 150 + (int)(80 * Math.random());
            g.setColor(new Color(255, 255, 240, Math.min(255, alpha)));
            int sz = sy < groundY * 0.25 ? 2 : 1;
            g.fillOval(sx - sz/2, sy - sz/2, sz + 1, sz + 1);
        }
    }

    private static void paintSun(Graphics2D g, int w, int groundY) {
        int cx = (int)(w * 0.75);
        int cy = (int)(groundY * 0.24);
        int r  = (int)(Math.min(w, groundY) * 0.072);

        // Three-ring glow
        g.setColor(SUN_GLOW2);
        g.fillOval(cx - r*3, cy - r*3, r*6, r*6);
        g.setColor(SUN_GLOW1);
        g.fillOval(cx - (int)(r*1.8), cy - (int)(r*1.8), (int)(r*3.6), (int)(r*3.6));

        // Rays
        g.setColor(new Color(255, 220, 60, 160));
        g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < 12; i++) {
            double ang = i * Math.PI / 6;
            g.drawLine((int)(cx + (r+5)*Math.cos(ang)), (int)(cy + (r+5)*Math.sin(ang)),
                       (int)(cx + (r+16)*Math.cos(ang)), (int)(cy + (r+16)*Math.sin(ang)));
        }
        g.setStroke(new BasicStroke(1f));

        // Core disc
        g.setColor(SUN_CORE);
        g.fillOval(cx - r, cy - r, r*2, r*2);
        // Hot-spot highlight
        g.setColor(new Color(255, 255, 220, 190));
        g.fillOval(cx - r/2, cy - r/2, r, r/2);
    }

    private static void paintMountains(Graphics2D g, int w, int groundY) {
        // Back layer
        drawMtnLayer(g, MTN_BACK, w, groundY,
            new int[][]{{5,82},{18,58},{32,72},{48,46},{62,65},{77,52},{90,70},{100,40}},
            0.16, false);
        // Mid layer
        drawMtnLayer(g, MTN_MID, w, groundY,
            new int[][]{{8,75},{22,52},{38,68},{55,43},{68,60},{82,48},{95,66}},
            0.14, false);
        // Front layer with snow caps
        drawMtnLayer(g, MTN_FRONT, w, groundY,
            new int[][]{{14,72},{30,50},{48,65},{63,42},{76,58},{90,45}},
            0.12, true);
    }

    private static void drawMtnLayer(Graphics2D g, Color col, int w, int groundY,
                                      int[][] peaks, double widthFrac, boolean snow) {
        g.setColor(col);
        for (int[] p : peaks) {
            int cx  = (int)(p[0] / 100.0 * w);
            int ty  = (int)(groundY * p[1] / 100.0);
            int bw  = (int)(w * widthFrac);
            Path2D mtn = new Path2D.Double();
            mtn.moveTo(cx - bw, groundY); mtn.lineTo(cx, ty); mtn.lineTo(cx + bw, groundY);
            mtn.closePath(); g.fill(mtn);

            if (snow) {
                int sh = (int)((groundY - ty) * 0.20);
                g.setColor(MTN_SNOW);
                Path2D cap = new Path2D.Double();
                cap.moveTo(cx - sh*1.8, ty + sh); cap.lineTo(cx, ty); cap.lineTo(cx + sh*1.8, ty + sh);
                cap.closePath(); g.fill(cap);
                g.setColor(col);
            }
        }
        // Subtle outline
        g.setColor(new Color(col.getRed()-20, col.getGreen()-20, col.getBlue()-20, 70));
        g.setStroke(new BasicStroke(0.8f));
        for (int[] p : peaks) {
            int cx = (int)(p[0] / 100.0 * w);
            int ty = (int)(groundY * p[1] / 100.0);
            int bw = (int)(w * widthFrac);
            g.drawLine(cx - bw, groundY, cx, ty);
            g.drawLine(cx, ty, cx + bw, groundY);
        }
        g.setStroke(new BasicStroke(1f));
    }

    private static void paintGround(Graphics2D g, int w, int h, int groundY) {
        g.setPaint(new GradientPaint(0, groundY, GROUND_TOP, 0, h, GROUND_BOT));
        g.fillRect(0, groundY, w, h - groundY);
        // Horizon shadow line
        g.setColor(new Color(130, 55, 15, 160));
        g.setStroke(new BasicStroke(3.5f));
        g.drawLine(0, groundY, w, groundY);
        g.setStroke(new BasicStroke(1f));
        // Ground texture — subtle horizontal striping
        g.setColor(new Color(225, 125, 55, 30));
        for (int yy = groundY + 10; yy < h; yy += 22)
            g.drawLine(0, yy, w, yy);
    }

    private static void paintDecorPipes(Graphics2D g, int w, int h, int groundY) {
        int pipeY  = groundY + (int)((h - groundY) * 0.40);
        int pipeH  = Math.max(18, (int)((h - groundY) * 0.13));
        int pipeR  = pipeH / 2;
        // Left pipe segment
        drawHPipe(g, 0, pipeY, (int)(w * 0.28), pipeH, pipeR);
        // Right pipe segment
        drawHPipe(g, (int)(w * 0.72), pipeY, w, pipeH, pipeR);
    }

    private static void drawHPipe(Graphics2D g, int x1, int y, int x2, int h, int r) {
        int len = x2 - x1;
        // Body
        g.setPaint(new GradientPaint(x1, y, PIPE_LIGHT, x1, y + h, PIPE_MID));
        g.fillRoundRect(x1, y, len, h, r, r);
        // Top highlight
        g.setColor(new Color(255, 255, 255, 65));
        g.fillRoundRect(x1 + 2, y + 2, len - 4, h / 3, r/2, r/2);
        // Shadow line
        g.setColor(new Color(90, 98, 108, 100));
        g.drawLine(x1 + r, y + h * 2/3, x2 - r, y + h * 2/3);
        // Outline
        g.setColor(PIPE_DARK);
        g.setStroke(new BasicStroke(1.5f));
        g.drawRoundRect(x1, y, len, h, r, r);
        // Joint caps
        int cw = (int)(h * 0.55), ch = h;
        for (int cx : new int[]{x1 + h/3, x2 - h/3 - cw}) {
            g.setPaint(new GradientPaint(cx, y, PIPE_MID, cx, y + ch, PIPE_DARK));
            g.fillRoundRect(cx, y, cw, ch, cw/3, cw/3);
            g.setColor(PIPE_DARK);
            g.drawRoundRect(cx, y, cw, ch, cw/3, cw/3);
        }
        g.setStroke(new BasicStroke(1f));
    }

    private static void paintCacti(Graphics2D g, int w, int h, int groundY) {
        int bodyH = (int)((h - groundY) * 0.65);
        drawCactus(g, (int)(w * 0.20), groundY, bodyH);
        drawCactus(g, (int)(w * 0.80), groundY, (int)(bodyH * 0.78));
    }

    private static void drawCactus(Graphics2D g, int bx, int gy, int bh) {
        int tw  = (int)(bh * 0.22);
        int armH = (int)(bh * 0.40);
        int armW = (int)(bh * 0.19);
        // Left arm
        g.setColor(CACTUS);
        g.fillRoundRect(bx - tw/2 - armW - 2, gy - bh + bh/3, armW, armH, armW/3, armW/3);
        g.fillRoundRect(bx - tw/2 - armW,     gy - bh + bh/8, armW, (int)(armH*0.45), armW/3, armW/3);
        // Right arm
        g.fillRoundRect(bx + tw/2 + 2, gy - bh + (int)(bh*0.42), armW, armH, armW/3, armW/3);
        g.fillRoundRect(bx + tw/2,     gy - bh + bh/5,            armW, (int)(armH*0.45), armW/3, armW/3);
        // Trunk
        g.fillRoundRect(bx - tw/2, gy - bh, tw, bh, tw/3, tw/3);
        // Rib lines
        g.setColor(CACTUS_DARK);
        g.setStroke(new BasicStroke(1.2f));
        for (int i = 1; i < 4; i++)
            g.drawLine(bx - tw/2 + 2, gy - bh + i*bh/4, bx + tw/2 - 2, gy - bh + i*bh/4);
        g.setStroke(new BasicStroke(1f));
    }

    private static void paintBarrels(Graphics2D g, int w, int h, int groundY) {
        int bh = (int)((h - groundY) * 0.58);
        drawBarrel(g, (int)(w * 0.055), groundY, bh);
        drawBarrel(g, (int)(w * 0.945), groundY, bh);
    }

    private static void drawBarrel(Graphics2D g, int cx, int gy, int bh) {
        int bw = (int)(bh * 0.68);
        int x = cx - bw/2, y = gy - bh;
        // Body
        g.setPaint(new GradientPaint(x, y, BARREL_TOP, x + bw, y, BARREL_BOT));
        g.fillRoundRect(x, y, bw, bh, bw/5, bw/5);
        // Bands
        g.setColor(BARREL_BAND);
        g.setStroke(new BasicStroke(3f));
        g.drawRoundRect(x+2, y+bh/5, bw-4, bh*3/5, bw/7, bw/7);
        g.drawLine(x+4, y+bh/2, x+bw-4, y+bh/2);
        // Highlight
        g.setColor(new Color(230, 155, 90, 75));
        g.fillRoundRect(x+bw/5, y+3, bw/5, bh/3, 4, 4);
        // Outline
        g.setColor(BARREL_BAND);
        g.setStroke(new BasicStroke(1.5f));
        g.drawRoundRect(x, y, bw, bh, bw/5, bw/5);
        g.setStroke(new BasicStroke(1f));
    }
}
