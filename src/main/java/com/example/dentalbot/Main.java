package com.example.dentalbot;

import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

public class Main {
    public static void main(String[] args) {
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class); // Debug mode
            botsApi.registerBot(new DentalBot());
            System.out.println("Bot ishga tushdi...");
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }


}