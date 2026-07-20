package view;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.awt.image.RescaleOp;
import java.util.*;
import java.util.List;
import javax.swing.JPanel;

import controller.GameController;
import controller.GameEventListener;
import model.elements.*;
import model.engine.GameEngine;
import model.player.*;
import util.UiAssets;

/**
 * Game map rendered with BFS-computed grid layout.
 * All sprites are drawn programmatically as fallbacks; PNG assets (if present)
 * are layered on top for richer visuals.
 */
public class MapView extends JPanel implements GameEventListener {

    // ── Layout constants ──────────────────────────────────────────
    private static final int CELL   = 170;
    private static final int ELEM   = 92;
    private static final int PIPE_W = 120;
    private static final int PIPE_H = 42;
    private static final int PLR_SZ = 72;  // ~78% of ELEM for good visibility

    // ── Colour palette ────────────────────────────────────────────
    private static final Color SEL_RING   = new Color(255, 215,   0);
    private static final Color ADJ_RING   = new Color( 60, 210,  60);
    private static final Color CUR_RING   = new Color( 60, 200, 255);
    private static final Color HOVER_FILL = new Color(255, 255, 255, 50);
    private static final Color LABEL_COL  = new Color( 55,  38,  15);

    // Element colours (fallback drawing)
    private static final Color COL_SPRING  = new Color( 55, 140, 220);
    private static final Color COL_CISTERN = new Color( 35, 160, 100);
    private static final Color COL_PUMP_OK = new Color( 75, 125, 210);
    private static final Color COL_PUMP_BK = new Color(200,  55,  55);
    private static final Color PIPE_WATER  = new Color( 55, 135, 230);
    private static final Color PIPE_LEAK   = new Color(215,  55,  55);
    private static final Color PIPE_DRY    = new Color(155, 155, 162);
    private static final Color TANK_FILL   = new Color( 55, 145, 240, 180);

    // ── Assets ───────────────────────────────────────────────────
    private final BufferedImage imgSpring, imgCistern, imgPumpOK, imgPumpBad;
    private final BufferedImage imgPipeH, imgPipeV, imgPipeLeak, imgPipeFree;
    private final BufferedImage imgPlumber, imgSaboteur, imgMountain, imgWater;

    // ── State ─────────────────────────────────────────────────────
    private final GameController controller;
    private final Map<FieldElement, Point> layout = new LinkedHashMap<>();
    private int lastHash = -1;
    private FieldElement hovered = null;

    public MapView(GameController controller) {
        this.controller = controller;
        setOpaque(true);
        setBackground(new Color(210, 178, 135));
        setPreferredSize(new Dimension(700, 520));

        imgSpring   = UiAssets.load("images/tank.png");
        imgCistern  = UiAssets.load("images/cistern.png");
        imgPumpOK   = UiAssets.load("images/pump_working.png");
        imgPumpBad  = UiAssets.load("images/pump_broken.png");
        imgPipeH    = UiAssets.load("images/pipe_horizontal.png");
        imgPipeV    = UiAssets.load("images/pipe_vertical.png");
        imgPipeLeak = UiAssets.load("images/pipe_broken.png");
        imgPipeFree = UiAssets.load("images/pipe_free_end.png");
        imgPlumber  = UiAssets.load("images/plumber.png");
        imgSaboteur = UiAssets.load("images/saboteur.png");
        imgMountain = UiAssets.load("images/mountains.png");
        imgWater    = UiAssets.load("images/water_waves.png"); // Water_spring.png

        // Gentle animation timer for leaking pipe drip effect (250ms intervals)
        new javax.swing.Timer(250, e -> {
            GameEngine eng = controller.getEngine();
            boolean hasLeak = eng.getFieldElements().stream()
                .anyMatch(fe -> fe instanceof Pipe && ((Pipe)fe).getStatus() == PipeStatus.LEAKING);
            if (hasLeak) repaint();
        }).start();

        MouseAdapter ma = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                Point off = getCentreOffset();
                Point loc = new Point(e.getX() - off.x, e.getY() - off.y);
                FieldElement hit = hitTest(loc);
                if (e.getClickCount() == 2 && hit != null) {
                    Player cur = controller.getCurrentPlayer();
                    if (cur != null && computeAdjacent(cur).contains(hit)) {
                        controller.move(hit);
                        controller.clearSelection();
                        repaint(); return;
                    }
                }
                controller.setSelectedTarget(hit);
                repaint();
            }
            @Override public void mouseMoved(MouseEvent e) {
                Point off = getCentreOffset();
                FieldElement was = hovered;
                hovered = hitTest(new Point(e.getX() - off.x, e.getY() - off.y));
                if (hovered != was) repaint();
            }
            @Override public void mouseExited(MouseEvent e) { hovered = null; repaint(); }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);
    }

    // ── Listener ──────────────────────────────────────────────────
    @Override public void onStateChanged()                         { repaint(); }
    @Override public void onTick(int s)                            { repaint(); }
    @Override public void onActionResult(boolean ok, String msg)   { repaint(); }
    @Override public void onGameEnded(String r, int c, int l)      { repaint(); }
    @Override public void onPumpBroke(String id)                   { repaint(); }
    @Override public void onPipeManufactured(String ci, String pi) { repaint(); }
    @Override public void onPumpManufactured(String ci, String pi) { repaint(); }

    // ── Layout ────────────────────────────────────────────────────

    private void rebuildLayout() {
        GameEngine eng = controller.getEngine();
        List<FieldElement> elements = eng.getFieldElements();
        int hash = hashElements(elements);
        if (hash == lastHash && !layout.isEmpty()) return;
        lastHash = hash;
        layout.clear();

        Map<FieldElement, Integer> depth   = new HashMap<>();
        Set<FieldElement>          visited = new HashSet<>();
        Deque<FieldElement>        queue   = new ArrayDeque<>();

        for (FieldElement fe : elements)
            if (fe instanceof Spring) { depth.put(fe, 0); queue.add(fe); visited.add(fe); }
        if (queue.isEmpty())
            for (FieldElement fe : elements)
                if (fe instanceof ActiveElement && visited.add(fe)) { depth.put(fe,0); queue.add(fe); break; }

        while (!queue.isEmpty()) {
            FieldElement cur = queue.poll();
            int d = depth.getOrDefault(cur, 0);
            if (cur instanceof ActiveElement)
                for (Pipe p : ((ActiveElement) cur).getConnectedPipes()) {
                    ActiveElement nxt = p.getEndA() == cur ? p.getEndB() : p.getEndA();
                    if (nxt != null && visited.add(nxt)) { depth.put(nxt, d+1); queue.add(nxt); }
                }
        }

        Map<Integer, Integer> rowCounter = new HashMap<>();
        for (FieldElement fe : elements) {
            if (!(fe instanceof ActiveElement)) continue;
            int col = depth.getOrDefault(fe, 0);
            int row = rowCounter.getOrDefault(col, 0);
            layout.put(fe, new Point(col * CELL + CELL/2, row * CELL + CELL/2));
            rowCounter.put(col, row + 1);
        }

        // ── Step 1: group fully-connected pipes by their endpoint pair ────────────
        // Pipes sharing the same two endpoints must be offset perpendicular to their
        // connection axis so they don't render on top of each other.
        Map<String, List<Pipe>> pairGroups = new LinkedHashMap<>();
        for (FieldElement fe : elements) {
            if (!(fe instanceof Pipe)) continue;
            Pipe p = (Pipe) fe;
            if (p.getEndA() == null || p.getEndB() == null) continue; // free-ended handled below
            String idA = p.getEndA().getId(), idB = p.getEndB().getId();
            // Canonical key — same regardless of which end is A or B
            String key = idA.compareTo(idB) <= 0 ? idA + "|" + idB : idB + "|" + idA;
            pairGroups.computeIfAbsent(key, k -> new ArrayList<>()).add(p);
        }

        // ── Step 2: assign positions for fully-connected pipes ───────────────────
        // Pipes between the same pair get a perpendicular offset so they fan out.
        // Offset sequence for group of N: 0, +d, -d, +2d, -2d …
        // This keeps the first pipe on the natural midpoint and adds visual lanes.
        final int LANE = 48; // 48px centre-to-centre: ≥20px gap at element edge with 14px stroke
        for (List<Pipe> group : pairGroups.values()) {
            for (int i = 0; i < group.size(); i++) {
                Pipe p = group.get(i);
                Point a = layout.get(p.getEndA());
                Point b = layout.get(p.getEndB());
                if (a == null || b == null) continue;
                Point mid = mid(a, b);
                if (group.size() == 1) {
                    layout.put(p, mid);
                } else {
                    // Perpendicular unit vector to the A→B axis
                    int dx = b.x - a.x, dy = b.y - a.y;
                    double len = Math.sqrt((double) dx * dx + (double) dy * dy);
                    double px = (len > 0) ? -dy / len : 0.0;
                    double py = (len > 0) ?  dx / len : 1.0;
                    // rank: 0→0, 1→+1, 2→-1, 3→+2, 4→-2 …
                    int rank = (i == 0) ? 0 : (i % 2 == 1 ? (i + 1) / 2 : -(i / 2));
                    int offX = (int)(rank * LANE * px);
                    int offY = (int)(rank * LANE * py);
                    layout.put(p, new Point(mid.x + offX, mid.y + offY));
                }
            }
        }

        // ── Step 3: free-ended and orphan pipes ──────────────────────────────────
        // Track how many one-ended pipes already hang off each anchor element,
        // so each successive pipe gets a different direction.
        Map<FieldElement, Integer> anchorCount = new HashMap<>();
        int orphan = 0;
        for (FieldElement fe : elements) {
            if (!(fe instanceof Pipe)) continue;
            Pipe p = (Pipe) fe;
            if (layout.containsKey(p)) continue; // already placed in step 2
            Point a = p.getEndA() != null ? layout.get(p.getEndA()) : null;
            Point b = p.getEndB() != null ? layout.get(p.getEndB()) : null;
            Point pos;
            if (a != null) {
                // endA connected, endB free
                FieldElement anchor = p.getEndA();
                int idx = anchorCount.getOrDefault(anchor, 0);
                anchorCount.put(anchor, idx + 1);
                pos = freeEndOffset(a, idx);
            } else if (b != null) {
                // endB connected, endA free (manufactured pipe stub)
                FieldElement anchor = p.getEndB();
                int idx = anchorCount.getOrDefault(anchor, 0);
                anchorCount.put(anchor, idx + 1);
                pos = freeEndOffset(b, idx);
            } else {
                // Both ends null — orphan
                pos = new Point(CELL / 2, (8 + orphan++) * 55);
            }
            layout.put(fe, pos);
        }
    }

    /**
     * Computes an offset position for the idx-th pipe hanging off an anchor point.
     * Cycles through 6 distinct directions so that manufactured pipes never overlap.
     *
     * Direction order (chosen so successive stubs fan out visibly):
     *   0 → below, 1 → above, 2 → right, 3 → below-right, 4 → above-right, 5 → left
     */
    private Point freeEndOffset(Point anchor, int idx) {
        int[][] dirs = {
            {  0,  CELL },          // 0: below
            {  0, -CELL },          // 1: above
            {  CELL,  0 },          // 2: right
            {  CELL,  CELL },       // 3: below-right
            {  CELL, -CELL },       // 4: above-right
            { -CELL,  0 },          // 5: left  (toward network — last resort)
        };
        int d = idx % dirs.length;
        return new Point(anchor.x + dirs[d][0], anchor.y + dirs[d][1]);
    }

    private Point getCentreOffset() {
        if (layout.isEmpty()) return new Point(0, 0);
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        // Only use active elements (Spring, Pump, Cistern) for the bounding box.
        // Manufactured pipe stubs extend outward and would shift the whole map if included.
        for (Map.Entry<FieldElement, Point> e : layout.entrySet()) {
            if (!(e.getKey() instanceof ActiveElement)) continue;
            Point p = e.getValue();
            if (p.x < minX) minX = p.x; if (p.x > maxX) maxX = p.x;
            if (p.y < minY) minY = p.y; if (p.y > maxY) maxY = p.y;
        }
        if (minX == Integer.MAX_VALUE) return new Point(0, 0); // no active elements yet
        // Add extra top padding for player sprites that extend above element centres
        int topPad  = PLR_SZ + 28;  // sprite height + label + glow ring
        int sidePad = ELEM / 2 + 20;
        int cw = maxX - minX + ELEM + sidePad * 2;
        int ch = maxY - minY + ELEM + topPad + 40;
        int pw = Math.max(getWidth(), 400), ph = Math.max(getHeight(), 300);
        // Bias toward top-center so the network is never clipped
        int ox = (pw - cw) / 2 - minX + sidePad;
        int oy = Math.max(topPad, (ph - ch) / 2) - minY + topPad;
        return new Point(ox, oy);
    }

    // ── Painting ──────────────────────────────────────────────────

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        rebuildLayout();
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,    RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,   RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = getWidth(), h = getHeight();

        // Sand-tile background
        DesertPainter.paintMapGrid(g, 0, 0, w, h, CELL);

        // Mountain corner decorations (like the reference game design)
        if (imgMountain != null && imgMountain.getWidth() > 1) {
            int mh = Math.min(90, h / 6);
            int mw = mh * imgMountain.getWidth() / imgMountain.getHeight();
            // Top-left corner mountain
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.55f));
            g.drawImage(imgMountain, 6, 6, mw, mh, null);
            // Top-right corner mountain
            g.drawImage(imgMountain, w - mw - 6, 6, mw, mh, null);
            g.setComposite(AlphaComposite.SrcOver);
        }

        // Apply centering transform
        Point off = getCentreOffset();
        g.translate(off.x, off.y);

        GameEngine eng = controller.getEngine();
        List<FieldElement> elements = eng.getFieldElements();

        // Pipes (behind elements)
        for (FieldElement fe : elements) if (fe instanceof Pipe)         drawPipe(g, (Pipe) fe);
        // Active elements
        for (FieldElement fe : elements) if (fe instanceof ActiveElement) drawActive(g, fe);

        // Players grouped by position
        Map<FieldElement, List<Player>> pm = buildPlayerMap(eng);
        for (Map.Entry<FieldElement, List<Player>> e : pm.entrySet()) {
            List<Player> pl = e.getValue();
            for (int i = 0; i < pl.size(); i++) drawPlayer(g, pl.get(i), i, pl.size());
        }

        // Adjacency hints
        Player curP = controller.getCurrentPlayer();
        Set<FieldElement> adj = computeAdjacent(curP);
        FieldElement sel = controller.getSelectedTarget();
        for (FieldElement a : adj) if (a != sel) drawAdjHint(g, a);

        // Selection / hover / current-player rings
        if (sel != null)    drawRing(g, sel,  SEL_RING, 3.5f);
        if (hovered != null && hovered != sel) drawRing(g, hovered, HOVER_FILL, 2f);
        if (curP != null && curP.getCurrentPosition() != null)
            drawRing(g, curP.getCurrentPosition(), CUR_RING, 2.2f);

        g.dispose();

        // Instruction bar drawn in screen-space
        drawHint((Graphics2D) g0, w, h, sel, curP, adj);
    }

    // ── Element drawing ───────────────────────────────────────────

    private void drawActive(Graphics2D g, FieldElement fe) {
        Point pt = layout.get(fe);
        if (pt == null) return;
        int half = ELEM / 2;

        // Mountain backdrop behind cistern elements
        if (fe instanceof Cistern && imgMountain != null && imgMountain.getWidth() > 1) {
            int mw = (int)(ELEM * 1.8), mh = (int)(ELEM * 1.2);
            g.drawImage(imgMountain, pt.x - mw/2, pt.y - mh + half/2, mw, mh, null);
        }

        if (fe instanceof Spring) {
            drawSprite(g, imgSpring, pt, half, COL_SPRING, "⊕");
            String lbl = fe.getId();
            drawLabel(g, pt, half + 17, lbl, new Color(20, 90, 180));
        } else if (fe instanceof Cistern) {
            Cistern ci = (Cistern) fe;
            drawSprite(g, imgCistern, pt, half, COL_CISTERN, "⬡");
            // Water fill gauge inside sprite area
            drawCisternWater(g, pt, half, ci.getWaterCollected());
            drawLabel(g, pt, half + 17, fe.getId() + "  " + ci.getWaterCollected() + " L",
                      new Color(20, 130, 70));
        } else if (fe instanceof Pump) {
            Pump pm = (Pump) fe;
            boolean ok = pm.getStatus() == PumpStatus.WORKING;
            drawSprite(g, ok ? imgPumpOK : imgPumpBad, pt, half,
                       ok ? COL_PUMP_OK : COL_PUMP_BK, ok ? "⊙" : "✕");
            drawPumpDirection(g, pm, pt, half);
            drawLabel(g, pt, half + 17,
                      fe.getId() + "  " + pm.getTankCurrentWater() + "/" + pm.getTankCapacity(),
                      ok ? new Color(30, 70, 175) : new Color(180, 30, 30));
        }
    }

    private void drawSprite(Graphics2D g, BufferedImage img, Point pt, int half, Color fallback, String sym) {
        if (img != null && img.getWidth() > 1) {
            // Render with aspect-ratio preservation inside ELEM × ELEM bounding box
            int iw = img.getWidth(), ih = img.getHeight();
            int dw, dh;
            if (iw >= ih) {
                dw = ELEM; dh = Math.max(20, ELEM * ih / iw);
            } else {
                dh = ELEM; dw = Math.max(20, ELEM * iw / ih);
            }
            g.drawImage(img, pt.x - dw / 2, pt.y - dh / 2, dw, dh, null);
        } else {
            // Fallback: gradient rounded rectangle
            g.setPaint(new GradientPaint(pt.x - half, pt.y - half, fallback.brighter(),
                                         pt.x + half, pt.y + half, fallback.darker()));
            g.fillRoundRect(pt.x - half, pt.y - half, ELEM, ELEM, 16, 16);
            g.setColor(fallback.darker());
            g.setStroke(new BasicStroke(2.5f));
            g.drawRoundRect(pt.x - half, pt.y - half, ELEM, ELEM, 16, 16);
            g.setStroke(new BasicStroke(1f));
            // Symbol
            g.setFont(new Font("SansSerif", Font.BOLD, 28));
            g.setColor(new Color(255, 255, 255, 200));
            FontMetrics fm = g.getFontMetrics();
            g.drawString(sym, pt.x - fm.stringWidth(sym)/2, pt.y + fm.getAscent()/2 - 2);
        }
    }

    private void drawCisternWater(Graphics2D g, Point pt, int half, int water) {
        if (water <= 0) return;
        int pct  = Math.min(100, water);
        int fillH = (int)(ELEM * 0.6 * pct / 100.0);
        int fillY = pt.y + half - fillH - 4;
        g.setColor(TANK_FILL);
        g.fillRoundRect(pt.x - half + 6, fillY, ELEM - 12, fillH, 6, 6);
        // Wave on top of fill
        if (imgWater != null && imgWater.getWidth() > 1)
            g.drawImage(imgWater, pt.x - 20, fillY - 8, 40, 14, null);
    }

    private void drawPumpDirection(Graphics2D g, Pump pm, Point pt, int half) {
        Pipe inc = pm.getIncomingPipe();
        Pipe out = pm.getOutgoingPipe();
        if (inc == null || out == null) return;
        Point pi = layout.get(inc), po = layout.get(out);
        if (pi == null || po == null) return;
        // Small arrow from incoming side to outgoing side, through the pump centre
        drawArrow(g, pi, pt, new Color(255, 230, 80, 180));
        drawArrow(g, pt, po, new Color(255, 230, 80, 200));
    }

    private void drawArrow(Graphics2D g, Point from, Point to, Color col) {
        double dx = to.x - from.x, dy = to.y - from.y;
        double len = Math.sqrt(dx*dx + dy*dy);
        if (len < 1) return;
        double ux = dx/len, uy = dy/len;
        // Midpoint
        double mx = (from.x + to.x) / 2.0, my = (from.y + to.y) / 2.0;
        // Arrowhead
        int ax = (int)(mx + ux*8), ay = (int)(my + uy*8);
        int lx = (int)(mx - ux*6 - uy*6), ly = (int)(my - uy*6 + ux*6);
        int rx = (int)(mx - ux*6 + uy*6), ry = (int)(my - uy*6 - ux*6);
        g.setColor(col);
        g.fillPolygon(new int[]{ax, lx, rx}, new int[]{ay, ly, ry}, 3);
    }

    private void drawPipe(Graphics2D g, Pipe p) {
        Point pt = layout.get(p);
        if (pt == null) return;
        Point a = p.getEndA() != null ? layout.get(p.getEndA()) : null;
        Point b = p.getEndB() != null ? layout.get(p.getEndB()) : null;

        boolean horiz = (a != null && b != null)
                        ? Math.abs(a.x - b.x) >= Math.abs(a.y - b.y) : true;

        Color lc = p.getStatus() == PipeStatus.LEAKING  ? PIPE_LEAK
                 : p.getIsWaterInside()                  ? PIPE_WATER
                 : PIPE_DRY;

        // Draw connection line(s) first (thick, behind sprite).
        // IMPORTANT: route THROUGH pt (the pipe's layout position) rather than drawing
        // directly from a to b.  This ensures that when multiple pipes share the same
        // two endpoints but have been offset perpendicular to their axis (step 2 of
        // rebuildLayout), each pipe's rendered path actually follows its own lane
        // instead of all stacking on the same straight line.
        if (a != null || b != null) {
            if (a != null && b != null) {
                // Two-segment polyline: endA → pipe-midpoint → endB
                g.setColor(lc.darker());
                g.setStroke(new BasicStroke(14f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawLine(a.x, a.y, pt.x, pt.y);
                g.drawLine(pt.x, pt.y, b.x, b.y);
                g.setColor(lc);
                g.setStroke(new BasicStroke(10f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawLine(a.x, a.y, pt.x, pt.y);
                g.drawLine(pt.x, pt.y, b.x, b.y);
            } else {
                // Free-ended stub: only one anchor, draw to pipe position
                Point src = (a != null) ? a : b;
                g.setColor(PIPE_DRY);
                g.setStroke(new BasicStroke(9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawLine(src.x, src.y, pt.x, pt.y);
            }
            g.setStroke(new BasicStroke(1f));
        }

        // Sprite overlay
        boolean isInventory = (p.getEndA() == null && p.getEndB() == null);
        boolean hasFreeEnd  = !isInventory && (p.getEndA() == null || p.getEndB() == null);
        BufferedImage spr;
        if      (p.getStatus() == PipeStatus.LEAKING)  spr = imgPipeLeak;
        else if (hasFreeEnd)                            spr = imgPipeFree;  // one end free (dangling)
        else if (isInventory)                           spr = imgPipeH;     // both ends null = inventory
        else                                            spr = horiz ? imgPipeH : imgPipeV;

        int sw = horiz ? PIPE_W : PIPE_H, sh = horiz ? PIPE_H : PIPE_W;

        if (spr != null && spr.getWidth() > 1) {
            // Preserve aspect ratio within the (sw × sh) slot
            int iw = spr.getWidth(), ih = spr.getHeight();
            int dw, dh;
            float srcRatio = (float) iw / ih;
            float dstRatio = (float) sw / sh;
            if (srcRatio > dstRatio) { dw = sw; dh = Math.max(6, (int)(sw / srcRatio)); }
            else                     { dh = sh; dw = Math.max(6, (int)(sh * srcRatio)); }
            // Tint leaking pipe red-ish
            if (p.getStatus() == PipeStatus.LEAKING) {
                // Draw with red tint using composite
                g.drawImage(spr, pt.x - dw / 2, pt.y - dh / 2, dw, dh, null);
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f));
                g.setColor(new Color(200, 30, 30));
                g.fillRect(pt.x - dw / 2, pt.y - dh / 2, dw, dh);
                g.setComposite(AlphaComposite.SrcOver);
            } else {
                // Dim inventory pipes so they're clearly secondary
                if (isInventory) {
                    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.6f));
                }
                g.drawImage(spr, pt.x - dw / 2, pt.y - dh / 2, dw, dh, null);
                g.setComposite(AlphaComposite.SrcOver);
            }
        } else {
            // Fallback pipe rect
            g.setColor(lc);
            g.fillRoundRect(pt.x - sw/2, pt.y - sh/2, sw, sh, sh/2, sh/2);
            g.setColor(lc.darker());
            g.setStroke(new BasicStroke(1.5f));
            g.drawRoundRect(pt.x - sw/2, pt.y - sh/2, sw, sh, sh/2, sh/2);
            g.setStroke(new BasicStroke(1f));
        }

        // Water-wave overlay
        if (p.getIsWaterInside() && p.getStatus() != PipeStatus.LEAKING
                && imgWater != null && imgWater.getWidth() > 1) {
            g.drawImage(imgWater, pt.x - 18, pt.y + sh/2 + 2, 36, 12, null);
        }

        // Leaking pipe — red overlay + water drops
        if (p.getStatus() == PipeStatus.LEAKING) {
            // Animated offset using system time for visual pulse
            long t = System.currentTimeMillis();
            int dripOff = (int)(((t / 400) % 3) * 6);  // 0, 6, or 12 px offset
            // Red warning indicator
            g.setColor(new Color(220, 30, 30, 160));
            g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                        0, new float[]{6, 4}, (t / 200) % 10));
            g.drawLine(pt.x - sw/2, pt.y, pt.x + sw/2, pt.y);
            g.setStroke(new BasicStroke(1f));
            // Water drops (animated)
            g.setColor(new Color(60, 145, 255, 220));
            g.fillOval(pt.x - 5, pt.y + sh/2 + 2 + dripOff, 10, 13);
            g.setColor(new Color(40, 95, 210, 170));
            if (dripOff < 8)
                g.fillOval(pt.x - 3, pt.y + sh/2 + 18 + dripOff, 7, 9);
            // Highlight on drop
            g.setColor(new Color(220, 240, 255, 140));
            g.fillOval(pt.x - 1, pt.y + sh/2 + 4 + dripOff, 4, 5);
        }

        // ID label
        g.setFont(new Font("SansSerif", Font.PLAIN, 10));
        boolean inv = (p.getEndA() == null && p.getEndB() == null);
        g.setColor(inv ? new Color(90, 130, 90) : LABEL_COL);
        String pLabel = p.getId() + (inv ? " [inv]" : "");
        g.drawString(pLabel, pt.x - 18, pt.y - sh/2 - 3);
    }

    // ── Player drawing ────────────────────────────────────────────

    private Map<FieldElement, List<Player>> buildPlayerMap(GameEngine eng) {
        Map<FieldElement, List<Player>> map = new LinkedHashMap<>();
        for (Team t : eng.getTeams())
            for (Player pl : t.getPlayers()) {
                FieldElement pos = pl.getCurrentPosition();
                if (pos != null) map.computeIfAbsent(pos, k -> new ArrayList<>()).add(pl);
            }
        return map;
    }

    private void drawPlayer(Graphics2D g, Player pl, int idx, int total) {
        FieldElement pos = pl.getCurrentPosition();
        if (pos == null) return;
        Point p = layout.get(pos);
        if (p == null) return;

        // Keep players inside the element tile.
        // For single player: draw at element centre.
        // For multiple: fan around centre, clamped to tile radius.
        // Keep players inside tile — with PLR_SZ=72 the max safe offset is very small
        int maxR = Math.max(0, ELEM / 2 - PLR_SZ / 2 - 4);
        int r = 0;
        if (total > 1) r = Math.min(maxR, 14);  // slight spread, never outside tile
        double ang = total <= 1 ? -Math.PI/2 : (2 * Math.PI * idx / total) - Math.PI / 2;
        int cx = p.x + (int)(r * Math.cos(ang));
        int cy = p.y + (int)(r * Math.sin(ang));

        BufferedImage spr = (pl instanceof Plumber) ? imgPlumber : imgSaboteur;
        boolean isActive = (pl == controller.getCurrentPlayer());

        // Compute display size: preserve image aspect ratio, fit in PLR_SZ
        int sw = PLR_SZ, sh = PLR_SZ;
        if (spr != null && spr.getWidth() > 1 && spr.getHeight() > 0) {
            float ratio = (float) spr.getWidth() / spr.getHeight();
            if (ratio < 1.0f) {          // portrait (taller than wide)
                sw = Math.max(22, (int)(PLR_SZ * ratio));
                sh = PLR_SZ;
            } else if (ratio > 1.0f) {   // landscape
                sw = PLR_SZ;
                sh = Math.max(22, (int)(PLR_SZ / ratio));
            }
        }
        int halfW = sw / 2, halfH = sh / 2;

        // Active glow ring
        if (isActive) {
            g.setColor(new Color(255, 225, 40, 100));
            g.fillOval(cx - halfW - 6, cy - halfH - 6, sw + 12, sh + 12);
            g.setColor(new Color(255, 210, 0));
            g.setStroke(new BasicStroke(2f));
            g.drawOval(cx - halfW - 6, cy - halfH - 6, sw + 12, sh + 12);
            g.setStroke(new BasicStroke(1f));
        }

        // Draw sprite or programmatic fallback
        if (spr != null && spr.getWidth() > 1) {
            g.drawImage(spr, cx - halfW, cy - halfH, sw, sh, null);
        } else {
            // Programmatic fallback — clear, identifiable icon
            drawPlayerFallback(g, pl, cx, cy, sw, sh, isActive);
        }

        // Name label with shadow for readability
        g.setFont(new Font("SansSerif", Font.BOLD, 10));
        FontMetrics fm = g.getFontMetrics();
        String tag = pl.getId();
        int lx = cx - fm.stringWidth(tag) / 2;
        int ly = cy - halfH - 2;
        g.setColor(new Color(0, 0, 0, 160));
        g.drawString(tag, lx + 1, ly + 1);
        g.setColor(isActive ? new Color(255, 215, 0) : LABEL_COL);
        g.drawString(tag, lx, ly);
    }

    /** Programmatic player icon when sprite image is unavailable or invalid. */
    private void drawPlayerFallback(Graphics2D g, Player pl, int cx, int cy, int sw, int sh,
                                    boolean isActive) {
        boolean isPlumber = (pl instanceof Plumber);
        int bw = Math.max(22, sw), bh = Math.max(28, sh);

        // Body
        Color bodyCol = isPlumber ? new Color(40, 100, 210) : new Color(20, 20, 28);
        g.setColor(bodyCol);
        g.fillRoundRect(cx - bw/2, cy - bh/4, bw, bh*3/4, 8, 8);

        // Head
        Color headCol = isPlumber ? new Color(210, 165, 120) : new Color(20, 20, 25);
        g.setColor(headCol);
        g.fillOval(cx - bw/3, cy - bh*3/4, bw*2/3, bh/2);

        if (isPlumber) {
            // Blue hard hat
            g.setColor(new Color(30, 80, 200));
            g.fillArc(cx - bw/3, cy - bh, bw*2/3, bh/2, 0, 180);
            // Water drop on hat
            g.setColor(new Color(100, 185, 255));
            g.fillOval(cx - 4, cy - bh + bh/8, 8, 10);
        } else {
            // Balaclava / evil mask
            g.setColor(new Color(18, 18, 22));
            g.fillOval(cx - bw/3, cy - bh*3/4, bw*2/3, bh/2);
            // Red eyes
            g.setColor(new Color(220, 30, 30));
            g.fillOval(cx - bw/5 - 2, cy - bh/2 - 2, 6, 6);
            g.fillOval(cx + bw/8, cy - bh/2 - 2, 6, 6);
        }

        // Outline
        g.setColor(isActive ? new Color(255, 215, 0) : (isPlumber ? new Color(20, 60, 180) : new Color(120, 20, 20)));
        g.setStroke(new BasicStroke(1.5f));
        g.drawRoundRect(cx - bw/2, cy - bh/4, bw, bh*3/4, 8, 8);
        g.drawOval(cx - bw/3, cy - bh*3/4, bw*2/3, bh/2);
        g.setStroke(new BasicStroke(1f));
    }

    // ── Adjacency & highlights ────────────────────────────────────

    private Set<FieldElement> computeAdjacent(Player player) {
        Set<FieldElement> res = new LinkedHashSet<>();
        if (player == null) return res;
        FieldElement pos = player.getCurrentPosition();
        if (pos == null) return res;
        if (pos instanceof Pipe) {
            Pipe p = (Pipe) pos;
            if (p.getEndA() != null) res.add(p.getEndA());
            if (p.getEndB() != null) res.add(p.getEndB());
        } else if (pos instanceof ActiveElement) {
            res.addAll(((ActiveElement) pos).getConnectedPipes());
        }
        return res;
    }

    private void drawAdjHint(Graphics2D g, FieldElement fe) {
        Point pt = layout.get(fe);
        if (pt == null) return;
        boolean isPipe = fe instanceof Pipe;
        int hs = isPipe ? PIPE_W/2 + 9 : ELEM/2 + 11;
        int hh = isPipe ? PIPE_H/2 + 9 : ELEM/2 + 11;
        g.setColor(new Color(60, 220, 60, 32));
        g.fillRoundRect(pt.x - hs, pt.y - hh, hs*2, hh*2, 14, 14);
        g.setColor(new Color(40, 200, 40, 190));
        g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                                    10f, new float[]{7f, 4f}, 0f));
        g.drawRoundRect(pt.x - hs, pt.y - hh, hs*2, hh*2, 14, 14);
        g.setStroke(new BasicStroke(1f));
        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        g.setColor(new Color(20, 155, 20));
        g.drawString("↕", pt.x - 6, pt.y - hs + 2);
    }

    private void drawRing(Graphics2D g, FieldElement fe, Color col, float sw) {
        Point pt = layout.get(fe);
        if (pt == null) return;
        boolean isPipe = fe instanceof Pipe;
        int hs = isPipe ? PIPE_W/2 + 12 : ELEM/2 + 13;
        int hh = isPipe ? PIPE_H/2 + 12 : ELEM/2 + 13;
        g.setColor(col);
        g.setStroke(new BasicStroke(sw, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawRoundRect(pt.x - hs, pt.y - hh, hs*2, hh*2, 14, 14);
        g.setStroke(new BasicStroke(1f));
    }

    private void drawLabel(Graphics2D g, Point pt, int yOff, String text, Color col) {
        g.setFont(new Font("SansSerif", Font.BOLD, 11));
        g.setColor(new Color(255, 255, 255, 180));
        FontMetrics fm = g.getFontMetrics();
        int tw = fm.stringWidth(text);
        g.fillRoundRect(pt.x - tw/2 - 4, pt.y + yOff - fm.getAscent(), tw + 8, fm.getHeight(), 6, 6);
        g.setColor(col);
        g.drawString(text, pt.x - tw/2, pt.y + yOff);
    }

    // ── Instruction overlay ───────────────────────────────────────

    /** Rich contextual hint bar — explains available actions for the active player. */
    private void drawHint(Graphics2D g, int pw, int ph, FieldElement sel, Player cur, Set<FieldElement> adj) {
        String l1, l2;
        Color c1, c2;
        c2 = new Color(180, 175, 155);

        if (cur == null) {
            l1 = "▶  Select a player — click a button in the PLAYERS bar →";
            l2 = ""; c1 = new Color(220, 200, 145);
        } else {
            FieldElement pos = cur.getCurrentPosition();
            boolean isSab = cur instanceof Saboteur;
            boolean isPl  = cur instanceof Plumber;
            String who = cur.getId() + " (" + (isSab ? "Saboteur" : "Plumber") + ")";

            if (sel != null) {
                l1 = "Selected: " + sel.getId()
                   + "  ·  Press [ Move ] to go there  OR  double-click the green ring";
                l2 = "Active: " + who + "  ·  Esc = cancel  ·  Tab = next player";
                c1 = new Color(255, 215, 55);
            } else if (adj.isEmpty()) {
                l1 = "Active: " + who + " @ " + (pos != null ? pos.getId() : "?")
                   + "  ·  No adjacent elements reachable — use Connect to extend network";
                l2 = "Tab = next player  |  1–4 = jump to player";
                c1 = new Color(220, 175, 75);
            } else if (pos instanceof Pipe) {
                Pipe pipe = (Pipe) pos;
                if (isSab) {
                    if (pipe.getStatus() == PipeStatus.NORMAL) {
                        l1 = "Active: " + who + " @ " + pos.getId()
                           + "  ·  Press [ Puncture ⚡ ] to create a leak  OR  DOUBLE-CLICK a ring to move";
                        l2 = "Tab = next player  |  1–4 = jump";
                        c1 = new Color(235, 85, 55);
                    } else {
                        l1 = "Active: " + who + " @ " + pos.getId()
                           + " [LEAKING]  ·  Pipe already damaged — DOUBLE-CLICK a ring to move elsewhere";
                        l2 = "Tab = next player  |  1–4 = jump";
                        c1 = new Color(235, 145, 55);
                    }
                } else {
                    if (pipe.getStatus() == PipeStatus.LEAKING) {
                        l1 = "Active: " + who + " @ " + pos.getId()
                           + " [LEAKING]  ·  Press [ FixPipe 🔧 ] to repair  OR  move to adjacent element";
                        c1 = new Color(235, 165, 55);
                    } else {
                        Plumber pl = (Plumber) cur;
                        String extra = pl.getCarriedPump() != null
                            ? "  ·  [ InsertPump ] to insert carried pump into this pipe"
                            : "  ·  [ Connect ] to link end  |  [ Disconnect ] to unlink end";
                        l1 = "Active: " + who + " @ " + pos.getId() + extra;
                        c1 = new Color(70, 200, 255);
                    }
                    l2 = "DOUBLE-CLICK a green ring to move  ·  Tab = next player  |  1–4 = jump";
                }
            } else if (pos instanceof Pump) {
                Pump pump = (Pump) pos;
                if (isPl && pump.getStatus() == PumpStatus.BROKEN) {
                    l1 = "Active: " + who + " @ " + pos.getId()
                       + " [BROKEN]  ·  Press [ FixPump 🔧 ] to repair it";
                    c1 = new Color(235, 100, 55);
                } else {
                    l1 = "Active: " + who + " @ " + pos.getId()
                       + "  ·  Press [ Direction ↗ ] to change pump flow  OR  DOUBLE-CLICK a ring to move";
                    c1 = isSab ? new Color(235, 165, 55) : new Color(100, 200, 100);
                }
                l2 = "DOUBLE-CLICK a green ring to move  ·  Tab = next player  |  1–4 = jump";
            } else {
                l1 = "Active: " + who + "  ·  DOUBLE-CLICK a green ↕ ring to move  OR  click + press [ Move ]";
                l2 = "Tab = next player  |  1–4 = jump";
                c1 = new Color(70, 235, 70);
            }
        }

        // Draw hint bar background
        g.setColor(new Color(15, 10, 5, 175));
        g.fillRect(0, ph - 50, pw, 50);
        g.setColor(new Color(100, 80, 40, 80));
        g.drawLine(0, ph - 50, pw, ph - 50);

        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font("SansSerif", Font.BOLD, 12)); g.setColor(c1);
        g.drawString(l1, 10, ph - 29);
        if (!l2.isEmpty()) {
            g.setFont(new Font("SansSerif", Font.PLAIN, 11)); g.setColor(c2);
            g.drawString(l2, 10, ph - 11);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────

    private FieldElement hitTest(Point local) {
        FieldElement best = null; double bestD = 55;
        for (Map.Entry<FieldElement, Point> e : layout.entrySet()) {
            double d = e.getValue().distance(local);
            if (d < bestD) { bestD = d; best = e.getKey(); }
        }
        return best;
    }

    private static Point mid(Point a, Point b) { return new Point((a.x+b.x)/2, (a.y+b.y)/2); }
    /**
     * Hash that changes whenever the element list OR any pipe's connections change.
     * Without connection state the layout cache would never refresh after connect/disconnect.
     */
    private static int hashElements(List<FieldElement> list) {
        int h = list.size();
        for (FieldElement e : list) {
            h = h * 31 + System.identityHashCode(e);
            if (e instanceof Pipe) {
                Pipe p = (Pipe) e;
                h = h * 31 + System.identityHashCode(p.getEndA());
                h = h * 31 + System.identityHashCode(p.getEndB());
            }
        }
        return h;
    }

}
