import javax.swing.*;
import java.awt.Color;
import java.util.Enumeration;

public class Theme {

    // Dark Palette
    public static final Color BG_DARK = new Color(43, 43, 43); // Main Background
    public static final Color BG_LIGHTER = new Color(60, 63, 65); // Input Fields
    public static final Color FG_TEXT = new Color(187, 187, 187); // Main Text
    public static final Color FG_BRIGHT = new Color(220, 220, 220); // Headers
    public static final Color BORDER = new Color(85, 85, 85);

    // Event Colors (Darker Pastel)
    public static final Color COLOR_DELETED = new Color(100, 40, 40); // Dark Red
    public static final Color COLOR_NEW = new Color(40, 80, 40); // Dark Green
    public static final Color COLOR_MODIFIED = new Color(100, 80, 40); // Dark Orange
    public static final Color COLOR_RESTORED = new Color(40, 40, 100); // Dark Blue

    // Fonts
    public static final java.awt.Font FONT_MAIN = new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 14);
    public static final java.awt.Font FONT_MAIN_BOLD = new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 14);

    public static void applyDarkTheme() {
        try {
            // Global Fonts
            javax.swing.plaf.FontUIResource fontRes = new javax.swing.plaf.FontUIResource(FONT_MAIN);
            Enumeration<Object> keys = UIManager.getDefaults().keys();
            while (keys.hasMoreElements()) {
                Object key = keys.nextElement();
                Object value = UIManager.get(key);
                if (value instanceof javax.swing.plaf.FontUIResource) {
                    UIManager.put(key, fontRes);
                }
            }

            UIManager.put("ToolTip.background", BG_LIGHTER);
            UIManager.put("ToolTip.foreground", FG_TEXT);
            UIManager.put("ToolTip.border", BorderFactory.createLineBorder(BORDER));

            UIManager.put("Panel.background", BG_DARK);
            UIManager.put("Panel.foreground", FG_TEXT);

            UIManager.put("OptionPane.background", BG_DARK);
            UIManager.put("OptionPane.messageForeground", FG_TEXT);

            UIManager.put("Label.foreground", FG_TEXT);
            UIManager.put("Label.background", BG_DARK);
            UIManager.put("Label.font", FONT_MAIN);

            // Removing standard button defaults as we will use ModernButton
            UIManager.put("Button.background", BG_LIGHTER);
            UIManager.put("Button.foreground", FG_BRIGHT);
            UIManager.put("Button.font", FONT_MAIN_BOLD);

            UIManager.put("TextField.background", BG_LIGHTER);
            UIManager.put("TextField.foreground", FG_BRIGHT);
            UIManager.put("TextField.caretForeground", FG_BRIGHT);
            UIManager.put("TextField.border", BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(BORDER),
                    BorderFactory.createEmptyBorder(5, 5, 5, 5))); // Padding

            UIManager.put("TextArea.background", BG_LIGHTER);
            UIManager.put("TextArea.foreground", FG_BRIGHT);

            UIManager.put("Table.background", BG_DARK);
            UIManager.put("Table.foreground", FG_TEXT);
            UIManager.put("Table.gridColor", BORDER);
            UIManager.put("Table.selectionBackground", new Color(60, 90, 140));
            UIManager.put("Table.selectionForeground", Color.WHITE);
            UIManager.put("Table.font", FONT_MAIN);
            UIManager.put("Table.rowHeight", 28);

            UIManager.put("TableHeader.background", new Color(35, 35, 35));
            UIManager.put("TableHeader.foreground", FG_BRIGHT);
            UIManager.put("TableHeader.font", FONT_MAIN_BOLD);

            UIManager.put("ScrollPane.background", BG_DARK);
            UIManager.put("Viewport.background", BG_DARK);
            UIManager.put("ScrollPane.border", BorderFactory.createEmptyBorder());

            UIManager.put("CheckBox.background", BG_DARK);
            UIManager.put("CheckBox.foreground", FG_TEXT);
            UIManager.put("CheckBox.font", FONT_MAIN);

        } catch (Exception ignored) {
        }
    }
}
