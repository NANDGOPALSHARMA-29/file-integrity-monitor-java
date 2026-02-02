import javax.swing.*;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Gui {

    public static void main(String[] args) {

        JFrame frame = new JFrame("FIM Tool");
        JButton start = new JButton("Start Monitoring");

        start.addActionListener(e -> {
            new Thread(() -> {
                try {
                    // 🔧 Folder to monitor
                    Path p = Paths.get("C:\\monitor");

                    // ✅ FIXED: correct Monitor API
                    Monitor.start(p);

                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }).start();
        });

        frame.add(start);
        frame.setSize(300, 150);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }
}
