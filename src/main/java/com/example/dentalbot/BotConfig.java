package com.example.dentalbot;

public class BotConfig {
    // Environment variables dan o'qish tavsiya etiladi
    public static final String BOT_USERNAME = System.getenv().getOrDefault("BOT_USERNAME", "xujamovDc_bot");
    public static final String BOT_TOKEN = System.getenv().getOrDefault("BOT_TOKEN", "8186277178:AAEgxZP5WlA1R4tIrQv6yyci1XppPmk9g7g");

    // Admin chat ID
    public static final long ADMIN_CHAT_ID = Long.parseLong(
            System.getenv().getOrDefault("ADMIN_CHAT_ID", "8135506421")
    );
}