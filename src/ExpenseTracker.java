import java.sql.*;

public class ExpenseTracker {
    private static final String DB_URL = "jdbc:sqlite:expenses.db";

    public static void initDatabase() {
        try (Connection conn = DriverManager.getConnection(DB_URL);
                Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS expenses (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "name TEXT," +
                    "category TEXT," +
                    "amount REAL," +
                    "date TEXT)");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void addExpense(String name, String category, double amount, String date) {
        String sql = "INSERT INTO expenses (name, category, amount, date) VALUES (?, ?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL);
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, category);
            ps.setDouble(3, amount);
            ps.setString(4, date);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static ResultSet getAllExpenses() throws SQLException {
        Connection conn = DriverManager.getConnection(DB_URL);
        Statement stmt = conn.createStatement();
        return stmt.executeQuery("SELECT * FROM expenses");
    }
}
