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
            if (service.getPrice() > 0) {
                buttonText += " (" + service.getPrice() + " so'm)";
            }
            List<InlineKeyboardButton> row = new ArrayList<>();
            row.add(createInlineButton(buttonText, callbackData));
            rows.add(row);
        }

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
            String buttonText = "✏️ " + service.getName() + " (" + service.getPrice() + " so'm)";
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

        // 14 kun (2 hafta) uchun o'zgartirildi
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
}