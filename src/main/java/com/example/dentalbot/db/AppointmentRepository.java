package com.example.dentalbot.db;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

public class AppointmentRepository {
    private static final String DB_URL = "jdbc:sqlite:appointments.db";

    public AppointmentRepository() {
        initDatabase();
    }

    private void initDatabase() {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS appointments (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "chat_id LONG NOT NULL, " +
                    "full_name TEXT, " +
                    "phone TEXT, " +
                    "service TEXT, " +
                    "appointment_time TEXT UNIQUE)");
            // Ustunlarni qo'shish, agar mavjud bo'lmasa
            stmt.execute("ALTER TABLE appointments ADD COLUMN reminder_sent_1day BOOLEAN DEFAULT 0");
            stmt.execute("ALTER TABLE appointments ADD COLUMN reminder_sent_2hours BOOLEAN DEFAULT 0");
        } catch (Exception e) {
            if (!e.getMessage().contains("duplicate column name")) { // Mavjud bo'lsa xatolikni yut
                e.printStackTrace();
            }
        }
    }

    public void saveAppointment(long chatId, String appointment_time, String phone, String fullName, String service) {
        if (isTimeBooked(appointment_time)) {
            throw new IllegalStateException("Vaqt allaqachon band!");
        }
        String sql = "INSERT INTO appointments(chat_id, full_name, phone, service, appointment_time) VALUES(?, ?, ?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, chatId);
            pstmt.setString(2, fullName);
            pstmt.setString(3, phone);
            pstmt.setString(4, service);
            pstmt.setString(5, appointment_time);
            pstmt.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean isTimeBooked(String appointment_time) {
        String sql = "SELECT COUNT(*) FROM appointments WHERE appointment_time = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, appointment_time);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public String getAllAppointments() {
        StringBuilder sb = new StringBuilder("Barcha navbatlar:\n");
        String sql = "SELECT * FROM appointments ORDER BY appointment_time";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                sb.append("ID: ").append(rs.getInt("id"))
                        .append(", Ism: ").append(rs.getString("full_name"))
                        .append(", Telefon: ").append(rs.getString("phone"))
                        .append(", Xizmat: ").append(rs.getString("service"))
                        .append(", Vaqt: ").append(rs.getString("appointment_time"))
                        .append("\n");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "Navbatlar topilmadi.";
        }
        return sb.toString();
    }

    public String getUserAppointments(long chatId) {
        StringBuilder sb = new StringBuilder("Sizning navbatlaringiz:\n");
        String sql = "SELECT * FROM appointments WHERE chat_id = ? ORDER BY appointment_time";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, chatId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                sb.append("ID: ").append(rs.getInt("id"))
                        .append(", Xizmat: ").append(rs.getString("service"))
                        .append(", Vaqt: ").append(rs.getString("appointment_time"))
                        .append("\n");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "Navbatlar topilmadi.";
        }
        return sb.toString();
    }

    public void deleteAppointment(int id) {
        String sql = "DELETE FROM appointments WHERE id = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            pstmt.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void checkAndSendReminders(TelegramLongPollingBot bot) {
        String sql = "SELECT * FROM appointments WHERE appointment_time > CURRENT_TIMESTAMP";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                LocalDateTime appointmentTime = LocalDateTime.parse(rs.getString("appointment_time"), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
                long chatId = rs.getLong("chat_id");
                String fullName = rs.getString("full_name");
                String service = rs.getString("service");
                int id = rs.getInt("id");
                boolean sent1Day = rs.getBoolean("reminder_sent_1day");
                boolean sent2Hours = rs.getBoolean("reminder_sent_2hours");

                LocalDateTime now = LocalDateTime.now();
                LocalDateTime oneDayBefore = appointmentTime.minusDays(1);
                LocalDateTime twoHoursBefore = appointmentTime.minusHours(2);

                if (!sent1Day && now.isAfter(oneDayBefore)) {
                    sendReminder(bot, chatId, "Hurmatli " + fullName + ", sizning " + service + " navbatingizga 1 kun qoldi (" + appointmentTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) + ").");
                    updateReminderSent(id, "reminder_sent_1day", true);
                }

                if (!sent2Hours && now.isAfter(twoHoursBefore)) {
                    sendReminder(bot, chatId, "Hurmatli " + fullName + ", sizning " + service + " navbatingizga 2 soat qoldi (" + appointmentTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) + ").");
                    updateReminderSent(id, "reminder_sent_2hours", true);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateReminderSent(int id, String column, boolean value) {
        String sql = "UPDATE appointments SET " + column + " = ? WHERE id = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setBoolean(1, value);
            pstmt.setInt(2, id);
            pstmt.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendReminder(TelegramLongPollingBot bot, long chatId, String message) {
        SendMessage msg = new SendMessage(String.valueOf(chatId), message);
        try {
            bot.execute(msg);
            System.out.println("Eslatma yuborildi: " + chatId);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}