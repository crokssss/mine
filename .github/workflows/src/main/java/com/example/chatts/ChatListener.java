package com.example.chatts;

import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class ChatListener {

    @SubscribeEvent
    public void onChat(ClientChatReceivedEvent event) {
        if (!TTSConfig.ENABLED.get()) return;

        String raw = event.getMessage().getString();
        String text = TTSConfig.stripPrefixAndClean(raw);
        if (text == null) return;

        TTSManager.getInstance().speak(text);
    }
}
