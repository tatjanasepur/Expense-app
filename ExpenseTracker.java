public class ExpenseTracker {
    
}

import java.sql.*;
import java.util.Scanner;

public class ExpenseTracker {
    public static void main(String[] args) {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:expenses.db")) {
            // Kreiraj tabelu ako ne postoji
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS expenses (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "name TEXT," +
                        "category TEXT," +
                        "amount REAL)");
            }

            Scanner sc = new Scanner(System.in);
            while (true) {
                System.out.println("\n1. Dodaj trošak\n2. Prikaži sve\n3. Obriši trošak\n4. Izlaz");
                int izbor = sc.nextInt();
                sc.nextLine(); // potroši Enter

                if (izbor == 1) {
                    System.out.print("Naziv: ");
                    String name = sc.nextLine();
                    System.out.print("Kategorija: ");
                    String category = sc.nextLine();
                    System.out.print("Iznos: ");
                    double amount = sc.nextDouble();

                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO expenses(name, category, amount) VALUES(?,?,?)")) {
                        ps.setString(1, name);
                        ps.setString(2, category);
                        ps.setDouble(3, amount);
                        ps.executeUpdate();
                        System.out.println("✔ Trošak dodat!");
                    }
                } else if (izbor == 2) {
                    try (Statement stmt = conn.createStatement();
                            ResultSet rs = stmt.executeQuery("SELECT * FROM expenses")) {
                        System.out.println("\n--- SVI TROŠKOVI ---");
                        while (rs.next()) {
                            System.out.println(rs.getInt("id") + ". " +
                                    rs.getString("name") + " | " +
                                    rs.getString("category") + " | " +
                                    rs.getDouble("amount"));
                        }
                    }
                } else if (izbor == 3) {
                    System.out.print("Unesi ID troška za brisanje: ");
                    int id = sc.nextInt();
                    try (PreparedStatement ps = conn.prepareStatement("DELETE FROM expenses WHERE id=?")) {
                        ps.setInt(1, id);
                        int rows = ps.executeUpdate();
                        if (rows > 0)
                            System.out.println("✔ Trošak obrisan!");
                        else
                            System.out.println("⚠ Nije pronađen ID.");
                    }
                } else if (izbor == 4) {
                    System.out.println("Izlaz...");
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
