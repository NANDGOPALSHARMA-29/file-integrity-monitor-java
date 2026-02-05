import javax.swing.table.AbstractTableModel;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class EventTableModel extends AbstractTableModel {

    private static final String[] COLUMNS = {"Time", "Type", "File Path"};
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withLocale(Locale.US)
            .withZone(ZoneId.systemDefault());

    private final List<AlertEvent> events = Collections.synchronizedList(new ArrayList<>());
    private static final int MAX_EVENTS = 1000;

    public void addEvent(AlertEvent event) {
        events.add(0, event); // Add to top
        if (events.size() > MAX_EVENTS) {
            events.remove(events.size() - 1);
        }
        fireTableRowsInserted(0, 0);
    }

    public void clear() {
        events.clear();
        fireTableDataChanged();
    }

    public AlertEvent getEventAt(int row) {
        if (row < 0 || row >= events.size()) return null;
        return events.get(row);
    }

    @Override
    public int getRowCount() {
        return events.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNS[column];
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        AlertEvent e = events.get(rowIndex);
        switch (columnIndex) {
            case 0: return TIME_FMT.format(e.timestamp);
            case 1: return e.type;
            case 2: return e.path;
            default: return "";
        }
    }
}
