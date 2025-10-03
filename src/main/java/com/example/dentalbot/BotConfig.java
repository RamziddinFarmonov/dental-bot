package com.example.dentalbot;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public class BotConfig {
    public static final String BOT_USERNAME = "xujamovDc_bot";
    public static final String BOT_TOKEN = "8186277178:AAEgxZP5WlA1R4tIrQv6yyci1XppPmk9g7g";


    // Barcha adminlar ro'yxati
    public static final Set<Long> ADMIN_CHAT_IDS = Set.of(
            8135506421L,
            6581382127L,
            636564858L,
            7693708552L
    );

    // Admin tekshirish metodi
    public static boolean isAdmin(Long chatId) {
        return ADMIN_CHAT_IDS.contains(chatId);
    }
}