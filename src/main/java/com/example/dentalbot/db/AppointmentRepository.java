package com.example.dentalbot.db;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class AppointmentRepository {
    private final DatabaseManager dbManager = DatabaseManager.getInstance();
    private final ServiceRepository serviceRepo = new ServiceRepository();

    public static class Appointment {
        private int id;
        private long chatId;
        private String fullName;
        private String phone;
        private int serviceId;
        private String serviceName;
        private String appointmentTime;

        public Appointment(int id, long chatId, String fullName, String phone,
                           int serviceId, String serviceName, String appointmentTime) {
            this.id = id;
            this.chatId = chatId;
            this.fullName = fullName;
            this.phone = phone;
            this.serviceId = serviceId;
            this.serviceName = serviceName;
            this.appointmentTime = appointmentTime;
        }

        public int getId() { return id; }
        public long getChatId() { return chatId; }
        public String getFullName() { return fullName; }
        public String getPhone() { return phone; }
        public int getServiceId() { return serviceId; }
        public String getServiceName() { return serviceName; }
        public String getAppointmentTime() { return appointmentTime; }
    }

    public void saveAppointment(long chatId, String appointmentTime, String phone,
                                String fullName, int serviceId) {
        if (isTimeBooked(appointmentTime)) {
            throw new IllegalStateException("Vaqt allaqachon band!");
        }

        String sql = "INSERT INTO appointments(chat_id, full_name, phone, service_id, appointment_time) " +
                "VALUES(?, ?, ?, ?, ?)";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, chatId);
            pstmt.setString(2, fullName);
            pstmt.setString(3, phone);
            pstmt.setInt(4, serviceId);
            pstmt.setString(5, appointmentTime);
            pstmt.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Navbat saqlashda xatolik: " + e.getMessage());
        }
    }

    public boolean isTimeBooked(String appointmentTime) {
        String sql = "SELECT COUNT(*) FROM appointments WHERE appointment_time = ?";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, appointmentTime);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public String getAllAppointments() {
        StringBuilder sb = new StringBuilder("Barcha navbatlar:\n\n");
        String sql = "SELECT a.*, s.name as service_name FROM appointments a " +
                "LEFT JOIN services s ON a.service_id = s.id " +
                "ORDER BY a.appointment_time";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                sb.append("🆔 ID: ").append(rs.getInt("id"))
                        .append("\n👤 Ism: ").append(rs.getString("full_name"))
                        .append("\n📞 Tel: ").append(rs.getString("phone"))
                        .append("\n🛠 Xizmat: ").append(rs.getString("service_name"))
                        .append("\n🕒 Vaqt: ").append(rs.getString("appointment_time"))
                        .append("\n━━━━━━━━━━━━━━━━━━━━\n");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return "Navbatlar topilmadi.";
        }
        return sb.toString();
    }

    public String getUserAppointments(long chatId) {
        StringBuilder sb = new StringBuilder("Sizning navbatlaringiz:\n\n");
        String sql = "SELECT a.*, s.name as service_name FROM appointments a " +
                "LEFT JOIN services s ON a.service_id = s.id " +
                "WHERE a.chat_id = ? ORDER BY a.appointment_time";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, chatId);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                sb.append("🆔 ID: ").append(rs.getInt("id"))
                        .append("\n🛠 Xizmat: ").append(rs.getString("service_name"))
                        .append("\n🕒 Vaqt: ").append(rs.getString("appointment_time"))
                        .append("\n━━━━━━━━━━━━━━━━━━━━\n");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return "Navbatlar topilmadi.";
        }
        return sb.toString().isEmpty() ? "Hozircha navbatlaringiz yo'q." : sb.toString();
    }

    public List<Appointment> getUserAppointmentsList(long chatId) {
        List<Appointment> appointments = new ArrayList<>();
        String sql = "SELECT a.*, s.name as service_name FROM appointments a " +
                "LEFT JOIN services s ON a.service_id = s.id " +
                "WHERE a.chat_id = ? ORDER BY a.appointment_time";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, chatId);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                appointments.add(new Appointment(
                        rs.getInt("id"),
                        rs.getLong("chat_id"),
                        rs.getString("full_name"),
                        rs.getString("phone"),
                        rs.getInt("service_id"),
                        rs.getString("service_name"),
                        rs.getString("appointment_time")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return appointments;
    }

    public void deleteAppointment(int id) {
        String sql = "DELETE FROM appointments WHERE id = ?";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, id);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void checkAndSendReminders(TelegramLongPollingBot bot) {
        String sql = "SELECT a.*, s.name as service_name FROM appointments a " +
                "LEFT JOIN services s ON a.service_id = s.id " +
                "WHERE a.appointment_time > datetime('now')";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                LocalDateTime appointmentTime = LocalDateTime.parse(
                        rs.getString("appointment_time"),
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                );

                long chatId = rs.getLong("chat_id");
                String fullName = rs.getString("full_name");
                String serviceName = rs.getString("service_name");
                int id = rs.getInt("id");

                boolean sent1Day = rs.getBoolean("reminder_sent_1day");
                boolean sent2Hours = rs.getBoolean("reminder_sent_2hours");
                boolean sent30Min = rs.getBoolean("reminder_sent_30min");

                LocalDateTime now = LocalDateTime.now();
                LocalDateTime oneDayBefore = appointmentTime.minusDays(1);
                LocalDateTime twoHoursBefore = appointmentTime.minusHours(2);
                LocalDateTime thirtyMinBefore = appointmentTime.minusMinutes(30);

                // 1 kun oldin eslatma
                if (!sent1Day && now.isAfter(oneDayBefore) && now.isBefore(appointmentTime)) {
                    sendReminder(bot, chatId,
                            "⏰ Eslatma: Hurmatli " + fullName +
                                    ", sizning " + serviceName + " navbatingizga 1 kun qoldi." +
                                    "\n🕒 Sana: " + appointmentTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) +
                                    "\n\n📍 Manzil: Toshkent shahar, Yunusobod tumani" +
                                    "\n📞 Telefon: +998 90 123 45 67");
                    updateReminderSent(id, "reminder_sent_1day", true);
                }

                // 2 soat oldin eslatma
                if (!sent2Hours && now.isAfter(twoHoursBefore) && now.isBefore(appointmentTime)) {
                    sendReminder(bot, chatId,
                            "⏰ Eslatma: Hurmatli " + fullName +
                                    ", sizning " + serviceName + " navbatingizga 2 soat qoldi." +
                                    "\n🕒 Sana: " + appointmentTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
                    updateReminderSent(id, "reminder_sent_2hours", true);
                }

                // 30 daqiqa oldin eslatma
                if (!sent30Min && now.isAfter(thirtyMinBefore) && now.isBefore(appointmentTime)) {
                    sendReminder(bot, chatId,
                            "⏰ Eslatma: Hurmatli " + fullName +
                                    ", sizning " + serviceName + " navbatingizga 30 daqiqa qoldi." +
                                    "\n🕒 Sana: " + appointmentTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) +
                                    "\n\n❗ Iltimos, vaqtida kelishingizni so'raymiz.");
                    updateReminderSent(id, "reminder_sent_30min", true);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    public String findNextAvailableTime(String preferredTime) {
        LocalDateTime preferred = LocalDateTime.parse(preferredTime,
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));

        // Keyingi 2 kun ichida bo'sh vaqt qidirish
        for (int day = 0; day < 2; day++) {
            // YANGI: 8:00 dan 19:00 gacha
            for (int hour = 8; hour <= 19; hour++) {
                for (int minute = 0; minute < 60; minute += 30) {
                    LocalDateTime slot = preferred.plusDays(day)
                            .withHour(hour)
                            .withMinute(minute)
                            .withSecond(0)
                            .withNano(0);

                    if (slot.isAfter(preferred)) {
                        String slotStr = slot.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
                        if (!isTimeBooked(slotStr)) {
                            return slotStr;
                        }
                    }
                }
            }
        }
        return null;
    }


    private void updateReminderSent(int id, String column, boolean value) {
        String sql = "UPDATE appointments SET " + column + " = ? WHERE id = ?";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setBoolean(1, value);
            pstmt.setInt(2, id);
            pstmt.executeUpdate();
        } catch (SQLException e) {
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

    public int getMonthlyAppointmentCount(int year, int month) {
        String sql = "SELECT COUNT(*) FROM appointments WHERE strftime('%Y-%m', appointment_time) = ?";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            String monthStr = String.format("%04d-%02d", year, month);
            pstmt.setString(1, monthStr);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public String getServiceStatistics() {
        StringBuilder sb = new StringBuilder("📊 Xizmatlar statistikasi:\n\n");
        String sql = "SELECT s.name, COUNT(a.id) as count " +
                "FROM services s LEFT JOIN appointments a ON s.id = a.service_id " +
                "WHERE s.active = 1 " +
                "GROUP BY s.name ORDER BY count DESC";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                sb.append("🛠 ").append(rs.getString("name"))
                        .append(": ").append(rs.getInt("count"))
                        .append(" ta\n");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return "Statistika topilmadi.";
        }
        return sb.toString();
    }
}