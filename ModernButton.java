import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;

public class ModernButton extends JButton {

    private final Color normalColor;
    private final Color hoverColor;
    private final Color pressedColor;
    private boolean isHovered = false;
    private boolean isPressed = false;

    public ModernButton(String text) {
        super(text);

        // Colors (Dark Theme Flats)
        this.normalColor = new Color(70, 70, 70);
        this.hoverColor = new Color(90, 90, 90);
        this.pressedColor = new Color(50, 50, 50);

        // General settings
        setFont(Theme.FONT_MAIN_BOLD);
        setForeground(Theme.FG_BRIGHT);
        setFocusPainted(false);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setOpaque(false);
        setCursor(new Cursor(Cursor.HAND_CURSOR));
        setMargin(new Insets(10, 20, 10, 20)); // Padding inside button

        // Hover functionality
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                isHovered = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                isHovered = false;
                repaint();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                isPressed = true;
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                isPressed = false;
                repaint();
            }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        // Determine background color
        Color bg = normalColor;
        if (!isEnabled()) {
            bg = new Color(40, 40, 40); // Disabled dark
        } else if (isPressed) {
            bg = pressedColor;
        } else if (isHovered) {
            bg = hoverColor;
        }

        // Draw rounded background
        g2.setColor(bg);
        g2.fill(new RoundRectangle2D.Float(0, 0, w, h, 10, 10));

        // Draw Text
        super.paintComponent(g);

        g2.dispose();
    }
}
