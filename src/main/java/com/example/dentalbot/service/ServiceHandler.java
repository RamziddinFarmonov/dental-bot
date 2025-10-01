package com.example.dentalbot.service;

import com.example.dentalbot.util.MarkdownUtil;
import com.example.dentalbot.util.KeyboardFactory;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

public class ServiceHandler {

    public static SendMessage getServiceInfo(long chatId, String serviceName, int price, boolean withQueue) {
        String text;
        if (price == 0) {
            text = "*" + MarkdownUtil.escapeMarkdownV2(serviceName) + "*";
        } else {
            text = "*" + MarkdownUtil.escapeMarkdownV2(serviceName) + "*\nNarxi: " + price + " so‘m";
        }

        SendMessage message = new SendMessage(String.valueOf(chatId), text);
        message.enableMarkdownV2(true);

        if (withQueue) {
            message.setReplyMarkup(KeyboardFactory.createServiceKeyboard());
        }

        return message;
    }
}