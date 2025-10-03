package com.example.dentalbot.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.PreparedStatement;

public class DatabaseManager {
    private static final String DB_URL = "jdbc:sqlite:appointments.db";
    private static DatabaseManager instance;

    private DatabaseManager() {
        initDatabase();
    }

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }
    private void initDatabase() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            // Services jadvali - narx oralig'ini saqlash uchun
            stmt.execute("CREATE TABLE IF NOT EXISTS services (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "name TEXT UNIQUE NOT NULL, " +
                    "min_price INTEGER NOT NULL, " +  // Yangi: minimum narx
                    "max_price INTEGER NOT NULL, " +  // Yangi: maksimum narx
                    "active BOOLEAN DEFAULT 1)");

            // Appointments jadvali (o'zgarmadi)
            stmt.execute("CREATE TABLE IF NOT EXISTS appointments (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "chat_id LONG NOT NULL, " +
                    "full_name TEXT, " +
                    "phone TEXT, " +
                    "service_id INTEGER, " +
                    "appointment_time TEXT UNIQUE, " +
                    "reminder_sent_1day BOOLEAN DEFAULT 0, " +
                    "reminder_sent_2hours BOOLEAN DEFAULT 0, " +
                    "reminder_sent_30min BOOLEAN DEFAULT 0, " +
                    "FOREIGN KEY(service_id) REFERENCES services(id))");

            // Default services qo'shish
            initDefaultServices(conn);

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    private void initDefaultServices(Connection conn) {
        String sql = "INSERT OR IGNORE INTO services (name, min_price, max_price) VALUES (?, ?, ?)";
        String[][] defaultServices = {
                {"Tish oldirish", "50000", "150000"},
                {"Plomba qilish", "150000", "300000"},
                {"Tish qo'ydirish", "2000000", "3000000"},
                {"Maslahat olish", "0", "0"}
        };

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            for (String[] service : defaultServices) {
                pstmt.setString(1, service[0]);
                pstmt.setInt(2, Integer.parseInt(service[1]));
                pstmt.setInt(3, Integer.parseInt(service[2]));
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            // Ignore duplicate errors
        }
    }
}