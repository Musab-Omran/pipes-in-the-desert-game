package view;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.filechooser.FileNameExtensionFilter;

import controller.GameController;
import controller.GameEventListener;
import model.engine.GameEngine;
import model.engine.GameStatus;
import util.MapLoader;
import util.Trace;

/**
 * Top-level application frame. Manages three full-screen cards — main menu,
 * gameplay, end game — and drives the simulation via a {@link Timer}.
 */
public class MainFrame extends JFrame implements GameEventListener {

    private static final Path DEFAULT_MAP =
            Path.of("src", "model", "Test", "Test0", "pipeSystem.txt");

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel     cards      = new JPanel(cardLayout);
    private final MainMenuPanel menuPanel;

    private GameController controller;
    private GamePanel      gamePanel;
    private EndGamePanel   endGamePanel;
    private Timer          timer;

    private Path selectedScenario = DEFAULT_MAP;
    private int  waitTimeSeconds  = 1;

    public MainFrame() {
        super("Pipes in the Desert");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setResizable(false);
        setSize(1280, 840);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        menuPanel = new MainMenuPanel(this);
        cards.add(menuPanel, "menu");
        add(cards, BorderLayout.CENTER);
        cardLayout.show(cards, "menu");
    }

    // ─── Navigation ───────────────────────────────────────────────

    public void showMenu() {
        if (timer != null) timer.stop();
        cardLayout.show(cards, "menu");
    }
    public void showGame() { cardLayout.show(cards, "game"); }
    public void showEndScreen() { cardLayout.show(cards, "end"); }

    public GameController getController()       { return controller; }
    public Path           getSelectedScenario() { return selectedScenario; }
    public int            getWaitTimeSeconds()  { return waitTimeSeconds; }

    // ─── Actions from menu ────────────────────────────────────────

    public void startSelectedGame() {
        loadGame(selectedScenario != null ? selectedScenario : DEFAULT_MAP);
    }

    public void chooseScenario() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Select pipeSystem.txt");
        fc.setFileFilter(new FileNameExtensionFilter("Text files", "txt"));
        fc.setCurrentDirectory(Path.of("src", "model", "Test").toFile());
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION
                && fc.getSelectedFile() != null) {
            selectedScenario = fc.getSelectedFile().toPath();
            JOptionPane.showMessageDialog(this,
                    "Scenario selected:\n" + selectedScenario,
                    "Scenario", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    public void openSettingsDialog() {
        JSpinner sp = new JSpinner(new SpinnerNumberModel(waitTimeSeconds, 1, 10, 1));
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.gridx = 0; gbc.gridy = 0;
        p.add(new JLabel("Tick speed (seconds):"), gbc);
        gbc.gridx = 1;
        p.add(sp, gbc);
        if (JOptionPane.showConfirmDialog(this, p, "Settings",
                JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
            waitTimeSeconds = ((Number) sp.getValue()).intValue();
            if (controller != null) controller.getEngine().setWaitTime(waitTimeSeconds);
            if (timer != null) {
                timer.stop();
                timer.setDelay(waitTimeSeconds * 1000);
                if (controller != null && controller.getEngine().getStatus() == GameStatus.RUNNING)
                    timer.start();
            }
        }
    }

    // ─── Game loading ─────────────────────────────────────────────

    public void loadGame(Path path) {
        Trace.setEnabled(false);
        if (timer != null) timer.stop();
        if (path != null) selectedScenario = path;

        GameEngine engine = (selectedScenario != null && Files.exists(selectedScenario))
                ? MapLoader.load(selectedScenario)
                : MapLoader.loadDefault();
        engine.setWaitTime(waitTimeSeconds);

        controller = new GameController(engine);
        gamePanel     = new GamePanel(this, controller, engine);
        endGamePanel  = new EndGamePanel(this, controller, engine);

        cards.removeAll();
        cards.add(menuPanel,   "menu");
        cards.add(gamePanel,   "game");
        cards.add(endGamePanel,"end");
        cards.revalidate();
        cards.repaint();

        // Subscribe all panels to controller events
        controller.addListener(this);
        controller.addListener(gamePanel);
        controller.addListener(gamePanel.getMapView());
        controller.addListener(gamePanel.getPlayerPanel());   // ← always update the player bar
        controller.addListener(gamePanel.getStatusPanel());
        controller.addListener(gamePanel.getControlPanel());
        controller.addListener(gamePanel.getLogPanel());
        controller.addListener(endGamePanel);

        if (engine.getStatus() != GameStatus.RUNNING) {
            controller.startGame();
        }

        timer = new Timer(waitTimeSeconds * 1000, e -> {
            if (controller != null
                    && controller.getEngine().getStatus() == GameStatus.RUNNING) {
                controller.tick();
            }
        });
        timer.start();
        showGame();
    }

    // ─── GameEventListener ────────────────────────────────────────

    @Override public void onStateChanged() { }
    @Override public void onTick(int s)    {
        setTitle("Pipes in the Desert — " + s + "s remaining");
    }
    @Override public void onActionResult(boolean ok, String msg) { }
    @Override public void onGameEnded(String result, int collected, int lost) {
        if (timer != null) timer.stop();
        if (endGamePanel != null) endGamePanel.setResult(result, collected, lost);
        SwingUtilities.invokeLater(this::showEndScreen);
    }
    @Override public void onPumpBroke(String id)                { }
    @Override public void onPipeManufactured(String ci, String pi) { }
    @Override public void onPumpManufactured(String ci, String pi) { }
}
