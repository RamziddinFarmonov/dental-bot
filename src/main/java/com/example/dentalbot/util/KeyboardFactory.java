package com.example.dentalbot.util;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class KeyboardFactory {

    public static InlineKeyboardMarkup createMainMenu() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(button("🦷 Tish oldirish", "service_tooth_removal")));
        rows.add(List.of(button("🔧 Plomba qilish", "service_filling")));
        rows.add(List.of(button("💎 Tish qo‘ydirish", "service_implant")));
        rows.add(List.of(button("🧑‍⚕️ Maslahat olish", "service_advice")));
        rows.add(List.of(button("Mening navbatlarim", "my_appointments")));
        rows.add(List.of(button("Stamatolog haqida", "doctor_menu")));
        markup.setKeyboard(rows);
        return markup;
    }

    public static InlineKeyboardMarkup createDoctorMenu() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(button("Shifokor haqida malumot olish", "doctor_info")));
        rows.add(List.of(button("Barcha navbatlar ro'yxatini ko'rish", "all_appointments")));
        markup.setKeyboard(rows);
        return markup;
    }

    public static InlineKeyboardMarkup createDaysKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        LocalDate today = LocalDate.now();
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        for (int i = 0; i < 7; i++) {
            LocalDate date = today.plusDays(i);
            rows.add(List.of(button("📅 " + date.format(dateFormatter), "show_day_" + date.format(dateFormatter))));
        }
        markup.setKeyboard(rows);
        return markup;
    }

    public static InlineKeyboardMarkup createTimesKeyboard(LocalDate date, Predicate<String> isBooked) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        for (int hour = 9; hour <= 18; hour++) {
            List<InlineKeyboardButton> row = new ArrayList<>();
            for (int minute = 0; minute < 60; minute += 30) {
                LocalDateTime slot = LocalDateTime.of(date, LocalTime.of(hour, minute));
                String slotStr = slot.format(formatter);
                if (!isBooked.test(slotStr)) {
                    row.add(button("⏰ " + slot.format(DateTimeFormatter.ofPattern("HH:mm")),
                            "select_time_" + slotStr));
                }
            }
            if (!row.isEmpty()) rows.add(row);
        }
        rows.add(List.of(button("🔙 Orqaga", "reject_time")));
        markup.setKeyboard(rows);
        return markup;
    }

    public static InlineKeyboardMarkup createConfirmationKeyboard(String time) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(button("✅ Ha", "confirm_time_" + time)));
        rows.add(List.of(button("❌ Yo'q", "reject_time")));
        markup.setKeyboard(rows);
        return markup;
    }

    public static InlineKeyboardMarkup createServiceKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(List.of(button("✅ Navbatga yozilish", "queue_register"))));
        return markup;
    }

    private static InlineKeyboardButton button(String text, String callback) {
        InlineKeyboardButton b = new InlineKeyboardButton();
        b.setText(text);
        b.setCallbackData(callback);
        return b;
    }
}