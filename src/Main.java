import javax.swing.SwingUtilities;
import view.MainFrame;
import util.Trace;

/**
 * Application entry point.
 */
public class Main {

    public static void main(String[] args) {
        Trace.setEnabled(false); // GUI mode
        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame();
            frame.setVisible(true);
        });
    }
}
