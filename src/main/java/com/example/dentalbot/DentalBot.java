package com.example.dentalbot;

import com.example.dentalbot.db.AppointmentRepository;
import com.example.dentalbot.service.ServiceHandler;
import com.example.dentalbot.util.KeyboardFactory;
import com.example.dentalbot.util.MarkdownUtil;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.api.objects.InputFile;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class DentalBot extends TelegramLongPollingBot {

    private final AppointmentRepository repo = new AppointmentRepository();
    private final Map<Long, UserState> userStates = new HashMap<>();
    private static final long ADMIN_CHAT_ID = 8135506421L; // Admin chat ID

    private static class UserState {
        String phone;
        String fullname;
        String service;
        String selectedTime;
        Stage stage = Stage.NONE;
    }

    private enum Stage { NONE, WAITING_PHONE, WAITING_FULLNAME }

    public DentalBot() {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(() -> repo.checkAndSendReminders(this), 0, 1, TimeUnit.HOURS);
    }

    @Override
    public String getBotUsername() {
        return BotConfig.BOT_USERNAME;
    }

    @Override
    public String getBotToken() {
        return BotConfig.BOT_TOKEN;
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasMessage() && update.getMessage().hasText()) {
                long chatId = update.getMessage().getChatId();
                handleText(chatId, update.getMessage().getText());
            } else if (update.hasCallbackQuery()) {
                CallbackQuery query = update.getCallbackQuery();
                handleCallback(query.getMessage().getChatId(), query.getData());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleText(long chatId, String text) throws TelegramApiException {
        UserState state = userStates.getOrDefault(chatId, new UserState());
        if (state == null) state = new UserState();

        if (state.stage == Stage.WAITING_PHONE) {
            if (isValidPhone(text)) {
                state.phone = text.trim();
                state.stage = Stage.WAITING_FULLNAME;
                userStates.put(chatId, state);
                sendPlain(chatId, "✅ Telefon raqamingiz qabul qilindi.\n\nEndi iltimos, ism va familiyangizni kiriting (kamida 5 harf):");
            } else {
                sendPlain(chatId, "❌ Noto'g'ri format! +998 XX XXX XX XX shaklida kiriting.");
            }
            return;
        } else if (state.stage == Stage.WAITING_FULLNAME) {
            if (text.trim().length() >= 5) {
                state.fullname = text.trim();
                state.stage = Stage.NONE;
                userStates.put(chatId, state);
                SendMessage msg = new SendMessage(String.valueOf(chatId),
                        "*Bo‘sh kunlarni tanlang \\(keyingi 7 kun\\)*:");
                msg.enableMarkdownV2(true);
                msg.setReplyMarkup(KeyboardFactory.createDaysKeyboard());
                executeSilently(msg);
            } else {
                sendPlain(chatId, "❌ Ism va familiya kamida 5 harfdan iborat bo‘lishi kerak. Qaytadan kiriting:");
            }
            return;
        }

        switch (text) {
            case "/start" -> {
                SendMessage msg = new SendMessage(String.valueOf(chatId),
                        "Assalomu alaykum! 👋\nQuyidagi stomatologiya xizmatlaridan birini tanlang:");
                msg.setReplyMarkup(KeyboardFactory.createMainMenu());
                executeSilently(msg);
            }
            case "/available_times" -> {
                SendMessage msg = new SendMessage(String.valueOf(chatId),
                        "*Bo‘sh kunlarni tanlang \\(keyingi 7 kun\\)*:");
                msg.enableMarkdownV2(true);
                msg.setReplyMarkup(KeyboardFactory.createDaysKeyboard());
                executeSilently(msg);
            }
            case "/my_appointments" -> {
                String appointments = repo.getUserAppointments(chatId);
                SendMessage msg = new SendMessage(String.valueOf(chatId), appointments.isEmpty() ? "Hozircha navbatlaringiz yo‘q." : appointments);
                msg.setReplyMarkup(getCancelKeyboard(chatId));
                executeSilently(msg);
            }
            case "/admin_appointments" -> {
                if (chatId == ADMIN_CHAT_ID) {
                    sendPlain(chatId, repo.getAllAppointments());
                } else {
                    sendPlain(chatId, "❌ Bu buyruq faqat admin uchun!");
                }
            }
            default -> sendPlain(chatId, "Noto'g'ri buyruq! /start orqali boshlang.");
        }
    }

    private void handleCallback(long chatId, String data) throws TelegramApiException {
        if (data == null) return;

        if (data.startsWith("show_day_")) {
            String date = data.substring("show_day_".length());
            SendMessage msg = new SendMessage(String.valueOf(chatId),
                    "*Bo‘sh vaqtlar \\(" + MarkdownUtil.escapeMarkdownV2(date) + "\\)*:");
            msg.enableMarkdownV2(true);
            msg.setReplyMarkup(KeyboardFactory.createTimesKeyboard(
                    LocalDate.parse(date), repo::isTimeBooked));
            executeSilently(msg);
        } else if (data.startsWith("select_time_")) {
            String time = data.substring("select_time_".length());
            UserState state = userStates.getOrDefault(chatId, new UserState());
            if (state == null) state = new UserState();
            state.selectedTime = time;
            userStates.put(chatId, state);
            SendMessage msg = new SendMessage(String.valueOf(chatId),
                    "Ishonchingiz komilmi? Shu vaqtga \\(" + MarkdownUtil.escapeMarkdownV2(time) + "\\) yozilasizmi?");
            msg.enableMarkdownV2(true);
            msg.setReplyMarkup(KeyboardFactory.createConfirmationKeyboard(time));
            executeSilently(msg);
        } else if (data.startsWith("confirm_time_")) {
            String time = data.substring("confirm_time_".length());
            UserState state = userStates.get(chatId);
            if (state == null || state.phone == null || state.fullname == null || state.service == null) {
                sendPlain(chatId, "❌ Xatolik! Avval xizmat tanlang va ism/telefon kiriting.");
                return;
            }
            if (repo.isTimeBooked(time)) {
                sendPlain(chatId, "❌ Afsus, bu vaqt allaqachon band. Iltimos boshqa vaqt tanlang.");
                return;
            }
            repo.saveAppointment(chatId, time, state.phone, state.fullname, state.service);
            StringBuilder sb = new StringBuilder();
            sb.append("✅ Navbatingiz muvaffaqiyatli saqlandi!\n\n");
            sb.append("🕒 Vaqt: ").append(time).append("\n");
            sb.append("👤 Ism: ").append(state.fullname).append("\n");
            sb.append("📞 Telefon: ").append(state.phone).append("\n");
            sb.append("🛠 Xizmat: ").append(state.service).append("\n\n");
            sb.append("Rahmat! /my_appointments orqali ko‘rishingiz mumkin.");
            sendPlain(chatId, sb.toString());
            userStates.remove(chatId);
        } else if ("reject_time".equals(data)) {
            sendPlain(chatId, "✅ Bekor qilindi. Boshqa vaqt tanlang yoki /available_times buyrug‘idan foydalaning.");
        } else if ("queue_register".equals(data)) {
            UserState state = userStates.getOrDefault(chatId, new UserState());
            if (state == null) state = new UserState();
            state.stage = Stage.WAITING_PHONE;
            userStates.put(chatId, state);
            sendPlain(chatId, "📞 Iltimos, telefon raqamingizni +998 XX XXX XX XX formatida kiriting:");
        } else if (data.equals("doctor_menu")) {
            SendMessage msg = new SendMessage(String.valueOf(chatId), "Stamatolog haqida:");
            msg.setReplyMarkup(KeyboardFactory.createDoctorMenu());
            executeSilently(msg);
        } else if (data.equals("doctor_info")) {
            sendDoctorInfo(chatId);
        } else if (data.equals("all_appointments")) {
            if (chatId == ADMIN_CHAT_ID) {
                sendPlain(chatId, repo.getAllAppointments());
            } else {
                sendPlain(chatId, "❌ Bu funksiya faqat admin uchun!");
            }
        } else if (data.equals("my_appointments")) {
            String appointments = repo.getUserAppointments(chatId);
            SendMessage msg = new SendMessage(String.valueOf(chatId), appointments.isEmpty() ? "Hozircha navbatlaringiz yo‘q." : appointments);
            msg.setReplyMarkup(getCancelKeyboard(chatId));
            executeSilently(msg);
        } else if (data.startsWith("cancel_")) {
            try {
                int id = Integer.parseInt(data.substring("cancel_".length()));
                repo.deleteAppointment(id);
                sendPlain(chatId, "✅ Navbat ID " + id + " bekor qilindi. Yangi ro'yxat: " + repo.getUserAppointments(chatId));
            } catch (NumberFormatException e) {
                sendPlain(chatId, "❌ Noto'g'ri ID format!");
            }
        } else {
            String serviceName = switch (data) {
                case "service_tooth_removal" -> "Tish oldirish";
                case "service_filling" -> "Plomba qilish";
                case "service_implant" -> "Tish qo‘ydirish";
                case "service_advice" -> "Maslahat olish";
                default -> "";
            };
            if (!serviceName.isEmpty()) {
                if (serviceName.equals("Maslahat olish")) {
                    sendPlain(chatId, "Maslahat olish bepul va navbatsiz, vaqt topib shifokor huzuriga o'tishingiz mumkin.");
                } else {
                    UserState state = userStates.getOrDefault(chatId, new UserState());
                    if (state == null) state = new UserState();
                    state.service = serviceName;
                    userStates.put(chatId, state);
                    executeSilently(ServiceHandler.getServiceInfo(chatId, serviceName, getPrice(serviceName), true));
                }
            }
        }
    }

    private int getPrice(String serviceName) {
        return switch (serviceName) {
            case "Tish oldirish" -> 150000;
            case "Plomba qilish" -> 200000;
            case "Tish qo‘ydirish" -> 2500000;
            default -> 0;
        };
    }

    private boolean isValidPhone(String phone) {
        Pattern pattern = Pattern.compile("^\\+998 \\d{2} \\d{3} \\d{2} \\d{2}$");
        return pattern.matcher(phone).matches();
    }

    private void sendPlain(long chatId, String text) {
        try {
            SendMessage msg = new SendMessage(String.valueOf(chatId), text);
            execute(msg);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void executeSilently(SendMessage msg) {
        try {
            execute(msg);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendDoctorInfo(long chatId) throws TelegramApiException {
        SendPhoto photo = new SendPhoto();
        photo.setChatId(String.valueOf(chatId));
        photo.setPhoto(new InputFile("YOUR_PHOTO_URL_OR_FILE_ID_HERE")); // O'zingiz rasm URL yoki Telegram file ID joylashtiring
        photo.setCaption("Shifokor haqida malumot:\n\nIsmi: Doktor Xujamov\nMutaxassislik: Stomatolog\nTajriba: 10 yil\nKontakt: +998 XX XXX XX XX");
        execute(photo);
    }

    private InlineKeyboardMarkup getCancelKeyboard(long chatId) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        String sql = "SELECT id, service, appointment_time FROM appointments WHERE chat_id = ?";
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:appointments.db");
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, chatId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                int id = rs.getInt("id");
                String info = rs.getString("service") + " - " + rs.getString("appointment_time");
                List<InlineKeyboardButton> row = new ArrayList<>();
                InlineKeyboardButton button = new InlineKeyboardButton();
                button.setText("Bekor qilish: " + info);
                button.setCallbackData("cancel_" + id);
                row.add(button);
                rows.add(row);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (rows.isEmpty()) {
            List<InlineKeyboardButton> row = new ArrayList<>();
            InlineKeyboardButton button = new InlineKeyboardButton();
            button.setText("Navbatlar yo‘q");
            button.setCallbackData("none");
            row.add(button);
            rows.add(row);
        }
        markup.setKeyboard(rows);
        return markup;
    }
}