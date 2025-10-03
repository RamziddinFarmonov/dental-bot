package com.example.dentalbot.util;

import com.example.dentalbot.db.ServiceRepository;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class KeyboardFactory {
    private static final ServiceRepository serviceRepo = new ServiceRepository();

    public static InlineKeyboardMarkup createMainMenu() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        var services = serviceRepo.getAllServices();
        for (var service : services) {
            String callbackData = "service_" + service.getId();
            String buttonText = "🦷 " + service.getName();
            // NARX KO'RINMASLIGI KERAK - faqat xizmat nomi
            List<InlineKeyboardButton> row = new ArrayList<>();
            row.add(createInlineButton(buttonText, callbackData));
            rows.add(row);
        }

        // Qo'shimcha tugmalar
        rows.add(createButtonRow("📋 Mening navbatlarim", "my_appointments"));
        rows.add(createButtonRow("👨‍⚕️ Stomatolog haqida", "doctor_menu"));

        markup.setKeyboard(rows);
        return markup;
    }

    public static ReplyKeyboardMarkup createContactKeyboard() {
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setResizeKeyboard(true);
        markup.setOneTimeKeyboard(true);

        KeyboardRow row = new KeyboardRow();
        KeyboardButton contactButton = new KeyboardButton("📞 Telefon raqamimni yuborish");
        contactButton.setRequestContact(true);
        row.add(contactButton);

        List<KeyboardRow> keyboard = new ArrayList<>();
        keyboard.add(row);
        markup.setKeyboard(keyboard);

        return markup;
    }

    public static InlineKeyboardMarkup createAdminMenu() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        rows.add(createButtonRow("📊 Statistika", "admin_stats"));
        rows.add(createButtonRow("🛠 Xizmatlarni boshqarish", "manage_services"));
        rows.add(createButtonRow("📋 Barcha navbatlar", "all_appointments"));
        rows.add(createButtonRow("🔙 Asosiy menyu", "main_menu"));

        markup.setKeyboard(rows);
        return markup;
    }

    public static InlineKeyboardMarkup createServiceManagementMenu() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        rows.add(createButtonRow("➕ Xizmat qo'shish", "add_service"));
        rows.add(createButtonRow("✏️ Xizmatni tahrirlash", "edit_services"));
        rows.add(createButtonRow("🔙 Admin menyu", "admin_menu"));

        markup.setKeyboard(rows);
        return markup;
    }

    public static InlineKeyboardMarkup createServicesListForEdit() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        var services = serviceRepo.getAllServices();
        for (var service : services) {
            String buttonText = "✏️ " + service.getName() + " (" + service.getPriceRange() + ")";
            rows.add(createButtonRow(buttonText, "edit_service_" + service.getId()));
        }

        rows.add(createButtonRow("🔙 Orqaga", "manage_services"));
        markup.setKeyboard(rows);
        return markup;
    }

    public static InlineKeyboardMarkup createDaysKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        LocalDate today = LocalDate.now();
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        DateTimeFormatter displayFormatter = DateTimeFormatter.ofPattern("MM/dd EEE");

        // 14 kun (2 hafta) uchun
        for (int i = 0; i < 14; i++) {
            LocalDate date = today.plusDays(i);
            String displayDate = date.format(displayFormatter);
            rows.add(createButtonRow("📅 " + displayDate, "show_day_" + date.format(dateFormatter)));
        }

        markup.setKeyboard(rows);
        return markup;
    }

    public static InlineKeyboardMarkup createTimesKeyboard(LocalDate date, Predicate<String> isBooked) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

        for (int hour = 9; hour <= 18; hour++) {
            List<InlineKeyboardButton> row = new ArrayList<>();
            for (int minute = 0; minute < 60; minute += 30) {
                LocalDateTime slot = LocalDateTime.of(date, LocalTime.of(hour, minute));
                String slotStr = slot.format(formatter);

                if (slot.isAfter(LocalDateTime.now()) && !isBooked.test(slotStr)) {
                    row.add(createInlineButton("⏰ " + slot.format(timeFormatter), "select_time_" + slotStr));
                }
            }
            if (!row.isEmpty()) {
                rows.add(row);
            }
        }

        if (rows.isEmpty()) {
            rows.add(createButtonRow("❌ Bu kunda bo'sh vaqt yo'q", "no_time"));
        }

        rows.add(createButtonRow("🔙 Kunni o'zgartirish", "change_day"));
        markup.setKeyboard(rows);
        return markup;
    }

    public static InlineKeyboardMarkup createConfirmationKeyboard(String time) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        List<InlineKeyboardButton> confirmRow = new ArrayList<>();
        confirmRow.add(createInlineButton("✅ Tasdiqlash", "confirm_time_" + time));
        confirmRow.add(createInlineButton("❌ Bekor qilish", "reject_time"));
        rows.add(confirmRow);

        rows.add(createButtonRow("🔄 Boshqa vaqt", "change_time"));
        markup.setKeyboard(rows);
        return markup;
    }

    public static InlineKeyboardMarkup createServiceKeyboard(int serviceId) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        var service = serviceRepo.getServiceById(serviceId);
        if (service != null && service.getName().equals("Maslahat olish")) {
            rows.add(createButtonRow("🔙 Asosiy menyu", "main_menu"));
        } else {
            List<InlineKeyboardButton> row = new ArrayList<>();
            row.add(createInlineButton("✅ Navbatga yozilish", "queue_register_" + serviceId));
            row.add(createInlineButton("🔄 Boshqa xizmat", "change_service"));
            rows.add(row);
        }

        markup.setKeyboard(rows);
        return markup;
    }

    public static InlineKeyboardMarkup createBackToMainKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(createButtonRow("🔙 Asosiy menyu", "main_menu"));
        markup.setKeyboard(rows);
        return markup;
    }

    // Yangi: Xizmatni o'chirish uchun keyboard
    public static InlineKeyboardMarkup createServiceDeleteKeyboard(int serviceId) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        List<InlineKeyboardButton> row = new ArrayList<>();
        row.add(createInlineButton("🗑️ O'chirish", "delete_service_" + serviceId));
        row.add(createInlineButton("✏️ Tahrirlash", "edit_service_" + serviceId));
        rows.add(row);

        rows.add(createButtonRow("🔙 Orqaga", "manage_services"));
        markup.setKeyboard(rows);
        return markup;
    }

    // Yangi: Admin statistikasi uchun qo'shimcha tugmalar
    public static InlineKeyboardMarkup createStatsKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        rows.add(createButtonRow("📈 Kunlik statistika", "daily_stats"));
        rows.add(createButtonRow("📊 Haftalik statistika", "weekly_stats"));
        rows.add(createButtonRow("📅 Oylik statistika", "monthly_stats"));
        rows.add(createButtonRow("🔙 Admin menyu", "admin_menu"));

        markup.setKeyboard(rows);
        return markup;
    }

    // Yangi: Navbatni tasdiqlash/bekor qilish uchun keyboard
    public static InlineKeyboardMarkup createAppointmentActionsKeyboard(int appointmentId) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        List<InlineKeyboardButton> row = new ArrayList<>();
        row.add(createInlineButton("✅ Tasdiqlash", "confirm_appointment_" + appointmentId));
        row.add(createInlineButton("❌ Bekor qilish", "cancel_appointment_" + appointmentId));
        rows.add(row);

        rows.add(createButtonRow("✏️ Vaqtni o'zgartirish", "reschedule_" + appointmentId));
        rows.add(createButtonRow("🔙 Orqaga", "all_appointments"));

        markup.setKeyboard(rows);
        return markup;
    }

    // Yangi: Kunlik vaqtlar uchun qo'shimcha variant
    public static InlineKeyboardMarkup createTimesKeyboardWithMoreOptions(LocalDate date, Predicate<String> isBooked) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

        // 30 daqiqa interval bilan
        for (int hour = 9; hour <= 18; hour++) {
            List<InlineKeyboardButton> row = new ArrayList<>();
            for (int minute = 0; minute < 60; minute += 30) {
                LocalDateTime slot = LocalDateTime.of(date, LocalTime.of(hour, minute));
                String slotStr = slot.format(formatter);

                if (slot.isAfter(LocalDateTime.now()) && !isBooked.test(slotStr)) {
                    String buttonText = "🕒 " + slot.format(timeFormatter);
                    row.add(createInlineButton(buttonText, "select_time_" + slotStr));
                }
            }
            if (!row.isEmpty()) {
                rows.add(row);
            }
        }

        // Agar bo'sh vaqtlar kam bo'lsa
        if (rows.isEmpty()) {
            rows.add(createButtonRow("❌ Bu kunda bo'sh vaqt yo'q", "no_time_available"));
            rows.add(createButtonRow("📅 Boshqa kun tanlash", "change_day"));
            rows.add(createButtonRow("🔄 Yangilash", "refresh_times_" + date));
        } else {
            rows.add(createButtonRow("🔄 Yangilash", "refresh_times_" + date));
            rows.add(createButtonRow("📅 Boshqa kun", "change_day"));
        }

        markup.setKeyboard(rows);
        return markup;
    }

    // Yangi: Xizmat tanlash uchun kengaytirilgan variant
    public static InlineKeyboardMarkup createServicesKeyboardWithIcons() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        var services = serviceRepo.getAllServices();
        for (var service : services) {
            String callbackData = "service_" + service.getId();
            String buttonText = getServiceIcon(service.getName()) + " " + service.getName();

            List<InlineKeyboardButton> row = new ArrayList<>();
            row.add(createInlineButton(buttonText, callbackData));
            rows.add(row);
        }

        // Qo'shimcha tugmalar
        rows.add(createButtonRow("📋 Mening navbatlarim", "my_appointments"));
        rows.add(createButtonRow("👨‍⚕️ Doktor haqida", "doctor_menu"));
        rows.add(createButtonRow("📍 Manzil", "location_info"));
        rows.add(createButtonRow("📞 Aloqa", "contact_info"));

        markup.setKeyboard(rows);
        return markup;
    }

    // Xizmat nomiga qarab icon tanlash
    private static String getServiceIcon(String serviceName) {
        if (serviceName.contains("oldirish") || serviceName.contains("o'ldirish")) {
            return "🦷";
        } else if (serviceName.contains("plomba") || serviceName.contains("Plomba")) {
            return "🔧";
        } else if (serviceName.contains("qo'ydirish") || serviceName.contains("qoydirish")) {
            return "⭐";
        } else if (serviceName.contains("maslahat") || serviceName.contains("Maslahat")) {
            return "💡";
        } else if (serviceName.contains("implant") || serviceName.contains("Implant")) {
            return "🦴";
        } else if (serviceName.contains("tozalash") || serviceName.contains("Tozalash")) {
            return "✨";
        } else {
            return "🦷";
        }
    }

    // Yangi: Boshqa xizmatlar uchun keyboard
    public static InlineKeyboardMarkup createOtherServicesKeyboard(int currentServiceId) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        var services = serviceRepo.getAllServices();
        for (var service : services) {
            if (service.getId() != currentServiceId) {
                String buttonText = "🔁 " + service.getName();
                rows.add(createButtonRow(buttonText, "service_" + service.getId()));
            }
        }

        rows.add(createButtonRow("🔙 Asosiy menyu", "main_menu"));
        markup.setKeyboard(rows);
        return markup;
    }

    // Asosiy yordamchi metodlar
    private static List<InlineKeyboardButton> createButtonRow(String text, String callback) {
        List<InlineKeyboardButton> row = new ArrayList<>();
        row.add(createInlineButton(text, callback));
        return row;
    }

    private static InlineKeyboardButton createInlineButton(String text, String callback) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(text);
        button.setCallbackData(callback);
        return button;
    }

    // Yangi: Ikki qatorli tugmalar yaratish
    private static List<InlineKeyboardButton> createDoubleButtonRow(String text1, String callback1, String text2, String callback2) {
        List<InlineKeyboardButton> row = new ArrayList<>();
        row.add(createInlineButton(text1, callback1));
        row.add(createInlineButton(text2, callback2));
        return row;
    }

    // Yangi: Emergency yoki tez yordam tugmalari
    public static InlineKeyboardMarkup createEmergencyKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        rows.add(createButtonRow("🚑 Shoshilinch yordam", "emergency_help"));
        rows.add(createButtonRow("📞 Qo'ng'iroq qilish", "call_doctor"));
        rows.add(createButtonRow("📍 Manzilni ko'rsatish", "show_location"));
        rows.add(createButtonRow("🔙 Asosiy menyu", "main_menu"));

        markup.setKeyboard(rows);
        return markup;
    }

    // Yangi: Sozlamalar menyusi
    public static InlineKeyboardMarkup createSettingsKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        rows.add(createButtonRow("🔔 Eslatmalarni sozlash", "notification_settings"));
        rows.add(createButtonRow("🌐 Tilni o'zgartirish", "language_settings"));
        rows.add(createButtonRow("👤 Profil ma'lumotlari", "profile_info"));
        rows.add(createButtonRow("🔙 Asosiy menyu", "main_menu"));

        markup.setKeyboard(rows);
        return markup;
    }
}