package com.example.dentalbot;

import com.example.dentalbot.db.AppointmentRepository;
import com.example.dentalbot.db.ServiceRepository;
import com.example.dentalbot.util.KeyboardFactory;
import com.example.dentalbot.util.MarkdownUtil;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Contact;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class DentalBot extends TelegramLongPollingBot {
    private final AppointmentRepository appointmentRepo = new AppointmentRepository();
    private final ServiceRepository serviceRepo = new ServiceRepository();
    private final Map<Long, UserState> userStates = new HashMap<>();
    private static final long ADMIN_CHAT_ID = 8135506421L;

    private static class UserState {
        String phone;
        String fullname;
        Integer serviceId;
        String selectedTime;
        Stage stage = Stage.NONE;
        AdminStage adminStage = AdminStage.NONE;
        String tempData;
    }

    private enum Stage {
        NONE, WAITING_PHONE, WAITING_FULLNAME, WAITING_SERVICE_NAME, WAITING_SERVICE_PRICE
    }

    private enum AdminStage {
        NONE, WAITING_SERVICE_NAME, WAITING_SERVICE_PRICE, WAITING_EDIT_SERVICE_PRICE
    }

    public DentalBot() {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(() -> appointmentRepo.checkAndSendReminders(this), 0, 30, TimeUnit.MINUTES);
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
                handleMessage(update.getMessage());
            } else if (update.hasMessage() && update.getMessage().hasContact()) {
                handleContact(update.getMessage());
            } else if (update.hasCallbackQuery()) {
                handleCallback(update.getCallbackQuery());
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (update.hasMessage()) {
                sendPlain(update.getMessage().getChatId(), "❌ Texnik xatolik yuz berdi. Iltimos keyinroq urinib ko'ring.");
            }
        }
    }

    private void handleMessage(Message message) throws TelegramApiException {
        long chatId = message.getChatId();
        String text = message.getText();
        UserState state = userStates.getOrDefault(chatId, new UserState());

        // Admin staging ni tekshirish
        if (chatId == ADMIN_CHAT_ID && state.adminStage != AdminStage.NONE) {
            handleAdminStage(chatId, text, state);
            return;
        }

        // User staging ni tekshirish
        if (state.stage != Stage.NONE) {
            handleUserStage(chatId, text, state);
            return;
        }

        // Asosiy komandalar
        switch (text) {
            case "/start" -> showMainMenu(chatId);
            case "/admin" -> {
                if (chatId == ADMIN_CHAT_ID) {
                    showAdminMenu(chatId);
                } else {
                    sendPlain(chatId, "❌ Bu buyruq faqat admin uchun!");
                }
            }
            case "/stats" -> {
                if (chatId == ADMIN_CHAT_ID) {
                    showStatistics(chatId);
                }
            }
            default -> sendPlain(chatId, "Noto'g'ri buyruq! /start orqali boshlang.");
        }
    }

    private void handleContact(Message message) {
        long chatId = message.getChatId();
        Contact contact = message.getContact();
        UserState state = userStates.getOrDefault(chatId, new UserState());

        if (contact != null && contact.getPhoneNumber() != null) {
            state.phone = contact.getPhoneNumber();
            state.stage = Stage.WAITING_FULLNAME;
            userStates.put(chatId, state);

            sendPlain(chatId, "✅ Telefon raqamingiz qabul qilindi: " + contact.getPhoneNumber() +
                    "\n\nEndi iltimos, ism va familiyangizni kiriting (kamida 5 harf):");
        }
    }

    private void handleUserStage(long chatId, String text, UserState state) {
        switch (state.stage) {
            case WAITING_PHONE -> {
                if (isValidPhone(text)) {
                    state.phone = text.trim();
                    state.stage = Stage.WAITING_FULLNAME;
                    userStates.put(chatId, state);
                    sendPlain(chatId, "✅ Telefon raqamingiz qabul qilindi.\n\nEndi ism va familiyangizni kiriting (kamida 5 harf):");
                } else {
                    sendPlain(chatId, "❌ Noto'g'ri format! +998 XX XXX XX XX shaklida kiriting yoki telefon raqamingizni yuborish tugmasidan foydalaning.");
                }
            }
            case WAITING_FULLNAME -> {
                if (text.trim().length() >= 5) {
                    state.fullname = text.trim();
                    state.stage = Stage.NONE;
                    userStates.put(chatId, state);

                    // Kunlarni tanlash menyusini ko'rsatish
                    SendMessage msg = new SendMessage(String.valueOf(chatId),
                            "*📅 Navbat uchun kun tanlang \\(keyingi 7 kun\\)*:");
                    msg.enableMarkdownV2(true);
                    msg.setReplyMarkup(KeyboardFactory.createDaysKeyboard());
                    executeSilently(msg);
                } else {
                    sendPlain(chatId, "❌ Ism va familiya kamida 5 harfdan iborat bo'lishi kerak. Qaytadan kiriting:");
                }
            }
        }
    }

    private void handleAdminStage(long chatId, String text, UserState state) {
        switch (state.adminStage) {
            case WAITING_SERVICE_NAME -> {
                state.tempData = text.trim();
                state.adminStage = AdminStage.WAITING_SERVICE_PRICE;
                userStates.put(chatId, state);
                sendPlain(chatId, "✅ Xizmat nomi qabul qilindi.\n\nEndi xizmat narxini kiriting (so'mda, faqat raqam):");
            }
            case WAITING_SERVICE_PRICE -> {
                try {
                    int price = Integer.parseInt(text.trim());
                    String serviceName = state.tempData;

                    if (serviceRepo.addService(serviceName, price)) {
                        sendPlain(chatId, "✅ Xizmat muvaffaqiyatli qo'shildi: " + serviceName + " - " + price + " so'm");
                    } else {
                        sendPlain(chatId, "❌ Xizmat qo'shishda xatolik!");
                    }

                    state.adminStage = AdminStage.NONE;
                    state.tempData = null;
                    userStates.put(chatId, state);
                    showServiceManagementMenu(chatId);

                } catch (NumberFormatException e) {
                    sendPlain(chatId, "❌ Noto'g'ri narx formati! Faqat raqam kiriting:");
                } catch (TelegramApiException e) {
                    throw new RuntimeException(e);
                }
            }
            case WAITING_EDIT_SERVICE_PRICE -> {
                try {
                    int newPrice = Integer.parseInt(text.trim());
                    int serviceId = Integer.parseInt(state.tempData);

                    var service = serviceRepo.getServiceById(serviceId);
                    if (service != null && serviceRepo.updateService(serviceId, service.getName(), newPrice)) {
                        sendPlain(chatId, "✅ Xizmat narxi yangilandi: " + service.getName() + " - " + newPrice + " so'm");
                    } else {
                        sendPlain(chatId, "❌ Xizmat yangilashda xatolik!");
                    }

                    state.adminStage = AdminStage.NONE;
                    state.tempData = null;
                    userStates.put(chatId, state);
                    showServiceManagementMenu(chatId);

                } catch (NumberFormatException e) {
                    sendPlain(chatId, "❌ Noto'g'ri narx formati! Faqat raqam kiriting:");
                } catch (TelegramApiException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private void handleCallback(CallbackQuery query) throws TelegramApiException {
        long chatId = query.getMessage().getChatId();
        String data = query.getData();

        if (data == null) return;

        // Admin callbacklar
        if (chatId == ADMIN_CHAT_ID) {
            if (handleAdminCallback(chatId, data)) return;
        }

        // User callbacklari
        if (data.equals("main_menu")) {
            showMainMenu(chatId);
        } else if (data.startsWith("service_")) {
            int serviceId = Integer.parseInt(data.substring("service_".length()));
            handleServiceSelection(chatId, serviceId);
        } else if (data.startsWith("queue_register_")) {
            int serviceId = Integer.parseInt(data.substring("queue_register_".length()));
            startQueueRegistration(chatId, serviceId);
        } else if (data.equals("change_service")) {
            showMainMenu(chatId);
        } else if (data.startsWith("show_day_")) {
            String date = data.substring("show_day_".length());
            showTimesForDay(chatId, date);
        } else if (data.startsWith("select_time_")) {
            String time = data.substring("select_time_".length());
            handleTimeSelection(chatId, time);
        } else if (data.startsWith("confirm_time_")) {
            String time = data.substring("confirm_time_".length());
            confirmAppointment(chatId, time);
        } else if (data.equals("reject_time")) {
            sendPlain(chatId, "✅ Navbat band qilish bekor qilindi.");
            showMainMenu(chatId);
        } else if (data.equals("change_time") || data.equals("change_day")) {
            SendMessage msg = new SendMessage(String.valueOf(chatId),
                    "*📅 Navbat uchun kun tanlang \\(keyingi 7 kun\\)*:");
            msg.enableMarkdownV2(true);
            msg.setReplyMarkup(KeyboardFactory.createDaysKeyboard());
            executeSilently(msg);
        } else if (data.equals("my_appointments")) {
            showUserAppointments(chatId);
        } else if (data.startsWith("cancel_")) {
            int appointmentId = Integer.parseInt(data.substring("cancel_".length()));
            cancelAppointment(chatId, appointmentId);
        } else if (data.equals("doctor_menu")) {
            sendDoctorInfo(chatId);
        }
    }

    private boolean handleAdminCallback(long chatId, String data) throws TelegramApiException {
        UserState state = userStates.getOrDefault(chatId, new UserState());

        if (data.equals("admin_menu")) {
            showAdminMenu(chatId);
            return true;
        } else if (data.equals("admin_stats")) {
            showStatistics(chatId);
            return true;
        } else if (data.equals("manage_services")) {
            showServiceManagementMenu(chatId);
            return true;
        } else if (data.equals("add_service")) {
            state.adminStage = AdminStage.WAITING_SERVICE_NAME;
            userStates.put(chatId, state);
            sendPlain(chatId, "Yangi xizmat nomini kiriting:");
            return true;
        } else if (data.equals("edit_services")) {
            showServicesForEdit(chatId);
            return true;
        } else if (data.startsWith("edit_service_")) {
            int serviceId = Integer.parseInt(data.substring("edit_service_".length()));
            state.adminStage = AdminStage.WAITING_EDIT_SERVICE_PRICE;
            state.tempData = String.valueOf(serviceId);
            userStates.put(chatId, state);

            var service = serviceRepo.getServiceById(serviceId);
            if (service != null) {
                sendPlain(chatId, "Xizmat: " + service.getName() + "\nJoriy narx: " + service.getPrice() + " so'm\n\nYangi narxni kiriting:");
            }
            return true;
        } else if (data.equals("all_appointments")) {
            String appointments = appointmentRepo.getAllAppointments();
            sendPlain(chatId, appointments.isEmpty() ? "Hozircha navbatlar yo'q." : appointments);
            return true;
        }
        return false;
    }

    private void handleServiceSelection(long chatId, int serviceId) throws TelegramApiException {
        var service = serviceRepo.getServiceById(serviceId);
        if (service == null) {
            sendPlain(chatId, "❌ Xizmat topilmadi.");
            return;
        }

        String text;
        if (service.getPrice() == 0) {
            text = "*" + MarkdownUtil.escapeMarkdownV2(service.getName()) + "*\n\nBepul maslahat xizmati";
        } else {
            text = "*" + MarkdownUtil.escapeMarkdownV2(service.getName()) + "*\nNarxi: " + service.getPrice() + " so'm";
        }

        SendMessage message = new SendMessage(String.valueOf(chatId), text);
        message.enableMarkdownV2(true);
        message.setReplyMarkup(KeyboardFactory.createServiceKeyboard(serviceId));
        executeSilently(message);
    }

    private void startQueueRegistration(long chatId, int serviceId) {
        UserState state = userStates.getOrDefault(chatId, new UserState());
        state.serviceId = serviceId;
        state.stage = Stage.WAITING_PHONE;
        userStates.put(chatId, state);

        // Telefon raqamini so'rash yoki contact so'rash
        SendMessage msg = new SendMessage(String.valueOf(chatId),
                "📞 Navbatga yozilish uchun telefon raqamingizni kiriting:\n\n" +
                        "Format: +998 XX XXX XX XX\n\n" +
                        "Yoki quyidagi tugma orqali yuboring:");
        msg.setReplyMarkup(KeyboardFactory.createContactKeyboard());
        executeSilently(msg);
    }

    private void showTimesForDay(long chatId, String date) throws TelegramApiException {
        LocalDate localDate = LocalDate.parse(date);
        SendMessage msg = new SendMessage(String.valueOf(chatId),
                "*⏰ Bo'sh vaqtlar \\(" + MarkdownUtil.escapeMarkdownV2(date) + "\\)*:");
        msg.enableMarkdownV2(true);
        msg.setReplyMarkup(KeyboardFactory.createTimesKeyboard(localDate, appointmentRepo::isTimeBooked));
        executeSilently(msg);
    }

    private void handleTimeSelection(long chatId, String time) throws TelegramApiException {
        UserState state = userStates.get(chatId);
        if (state == null || state.serviceId == null) {
            sendPlain(chatId, "❌ Xatolik! Avval xizmat tanlang.");
            return;
        }

        state.selectedTime = time;
        userStates.put(chatId, state);

        SendMessage msg = new SendMessage(String.valueOf(chatId),
                "❓ Navbatni tasdiqlaysizmi?\n\n" +
                        "🕒 Vaqt: " + time + "\n" +
                        "🛠 Xizmat: " + serviceRepo.getServiceById(state.serviceId).getName() + "\n\n" +
                        "Shu vaqtga yozilasizmi?");
        msg.setReplyMarkup(KeyboardFactory.createConfirmationKeyboard(time));
        executeSilently(msg);
    }

    private void confirmAppointment(long chatId, String time) {
        UserState state = userStates.get(chatId);
        if (state == null || state.phone == null || state.fullname == null || state.serviceId == null) {
            sendPlain(chatId, "❌ Xatolik! Ma'lumotlar to'liq emas.");
            return;
        }

        if (appointmentRepo.isTimeBooked(time)) {
            // Agar vaqt band bo'lsa, keyingi bo'sh vaqtni taklif qilish
            String nextTime = appointmentRepo.findNextAvailableTime(time);
            if (nextTime != null) {
                sendPlain(chatId, "❌ Afsus, bu vaqt allaqachon band.\n\n" +
                        "📅 Taklif etamiz: " + nextTime + "\n\n" +
                        "Bu vaqtga yozilishni xohlaysizmi?");

                state.selectedTime = nextTime;
                userStates.put(chatId, state);

                SendMessage msg = new SendMessage(String.valueOf(chatId),
                        "❓ Taklif qilingan vaqtga yozilasizmi?\n\n" +
                                "🕒 Vaqt: " + nextTime);
                msg.setReplyMarkup(KeyboardFactory.createConfirmationKeyboard(nextTime));
                executeSilently(msg);
            } else {
                sendPlain(chatId, "❌ Afsus, bu vaqt allaqachon band. Keyinroq urinib ko'ring.");
            }
            return;
        }

        try {
            appointmentRepo.saveAppointment(chatId, time, state.phone, state.fullname, state.serviceId);

            var service = serviceRepo.getServiceById(state.serviceId);
            StringBuilder sb = new StringBuilder();
            sb.append("✅ Navbatingiz muvaffaqiyatli saqlandi!\n\n");
            sb.append("👤 Ism: ").append(state.fullname).append("\n");
            sb.append("📞 Telefon: ").append(state.phone).append("\n");
            sb.append("🛠 Xizmat: ").append(service.getName()).append("\n");
            sb.append("🕒 Vaqt: ").append(time).append("\n\n");
            sb.append("📍 Manzil: Toshkent shahar, Yunusobod tumani\n");
            sb.append("📞 Telefon: +998 90 123 45 67\n\n");
            sb.append("⏰ Eslatmalar avtomatik yuboriladi.\n");
            sb.append("📋 /my_appointments - Navbatlaringizni ko'rish");

            sendPlain(chatId, sb.toString());
            userStates.remove(chatId);

        } catch (Exception e) {
            sendPlain(chatId, "❌ Navbat saqlashda xatolik: " + e.getMessage());
        }
    }

    private void showUserAppointments(long chatId) {
        String appointments = appointmentRepo.getUserAppointments(chatId);
        SendMessage msg = new SendMessage(String.valueOf(chatId), appointments);

        // Bekor qilish tugmalarini qo'shish
        var appointmentsList = appointmentRepo.getUserAppointmentsList(chatId);
        if (!appointmentsList.isEmpty()) {
            var markup = new org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup();
            var rows = new ArrayList<List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton>>();

            for (var appointment : appointmentsList) {
                var row = new ArrayList<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton>();
                var button = new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton();
                button.setText("❌ Bekor qilish: " + appointment.getServiceName() + " - " + appointment.getAppointmentTime());
                button.setCallbackData("cancel_" + appointment.getId());
                row.add(button);
                rows.add(row);
            }

            // Asosiy menyuga qaytish tugmasi
            var backRow = new ArrayList<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton>();
            var backButton = new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton();
            backButton.setText("🔙 Asosiy menyu");
            backButton.setCallbackData("main_menu");
            backRow.add(backButton);
            rows.add(backRow);

            markup.setKeyboard(rows);
            msg.setReplyMarkup(markup);
        } else {
            msg.setReplyMarkup(KeyboardFactory.createBackToMainKeyboard());
        }

        executeSilently(msg);
    }

    private void cancelAppointment(long chatId, int appointmentId) {
        appointmentRepo.deleteAppointment(appointmentId);
        sendPlain(chatId, "✅ Navbat bekor qilindi.");
        showUserAppointments(chatId);
    }

    private void showMainMenu(long chatId) throws TelegramApiException {
        SendMessage msg = new SendMessage(String.valueOf(chatId),
                "👋 Assalomu alaykum! Stomatologiya xizmatlaridan foydalaning:");
        msg.setReplyMarkup(KeyboardFactory.createMainMenu());
        executeSilently(msg);
    }

    private void showAdminMenu(long chatId) throws TelegramApiException {
        SendMessage msg = new SendMessage(String.valueOf(chatId), "👨‍💼 Admin paneli:");
        msg.setReplyMarkup(KeyboardFactory.createAdminMenu());
        executeSilently(msg);
    }

    private void showServiceManagementMenu(long chatId) throws TelegramApiException {
        SendMessage msg = new SendMessage(String.valueOf(chatId), "🛠 Xizmatlarni boshqarish:");
        msg.setReplyMarkup(KeyboardFactory.createServiceManagementMenu());
        executeSilently(msg);
    }

    private void showServicesForEdit(long chatId) throws TelegramApiException {
        SendMessage msg = new SendMessage(String.valueOf(chatId), "✏️ Tahrirlash uchun xizmatni tanlang:");
        msg.setReplyMarkup(KeyboardFactory.createServicesListForEdit());
        executeSilently(msg);
    }

    private void showStatistics(long chatId) {
        String stats = appointmentRepo.getServiceStatistics();
        int monthlyCount = appointmentRepo.getMonthlyAppointmentCount(2024, 1);

        String message = "📊 **Statistika**\n\n" +
                "📈 Oyilik navbatlar: " + monthlyCount + " ta\n\n" +
                stats +
                "\n━━━━━━━━━━━━━━━━━━━━\n" +
                "ℹ️ Statistika har 30 daqiqa yangilanadi";

        SendMessage msg = new SendMessage(String.valueOf(chatId), message);
        msg.enableMarkdownV2(true);
        executeSilently(msg);
    }

    private void sendDoctorInfo(long chatId) {
        String info = "👨‍⚕️ **Doktor Xujamov haqida**\n\n" +
                "🎓 **Ta'lim:** Toshkent Tibbiyot Akademiyasi\n" +
                "📅 **Tajriba:** 10+ yil\n" +
                "🦷 **Mutaxassislik:** Stomatolog, implantolog\n" +
                "🏥 **Ish joyi:** Yunusobod tumani, 5-kvartal\n" +
                "📞 **Aloqa:** +998 90 123 45 67\n\n" +
                "⏰ **Ish vaqti:** 9:00 - 18:00\n" +
                "📅 **Dam olish:** Yakshanba\n\n" +
                "💡 **Xizmatlar:**\n" +
                "• Tish oldirish\n" +
                "• Plomba qilish  \n" +
                "• Implantatsiya\n" +
                "• Maslahat";

        SendMessage msg = new SendMessage(String.valueOf(chatId), info);
        msg.enableMarkdownV2(true);
        msg.setReplyMarkup(KeyboardFactory.createBackToMainKeyboard());
        executeSilently(msg);
    }

    // Yordamchi methodlar
    private boolean isValidPhone(String phone) {
        Pattern pattern = Pattern.compile("^\\+998\\d{2}\\d{3}\\d{2}\\d{2}$");
        return pattern.matcher(phone).matches();
    }

    private void sendPlain(long chatId, String text) {
        try {
            execute(new SendMessage(String.valueOf(chatId), text));
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
}