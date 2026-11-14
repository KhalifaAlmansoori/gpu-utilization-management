package gpu;

import javax.swing.SwingUtilities;
import javax.swing.JFrame;
import javax.swing.JLabel;

/**
 * Entry point of the GPU Utilization Management application.
 * Temporary placeholder window: replace with the login frame once it exists.
 */
public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("GPU Utilization Management");
            frame.add(new JLabel("Project skeleton - login screen coming soon", JLabel.CENTER));
            frame.setSize(480, 200);
            frame.setLocationRelativeTo(null);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setVisible(true);
        });
    }
}
