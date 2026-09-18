package com.example.chatts;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class DebugEcho {

    public static void print(String msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal(msg), false);
        } else {
            System.out.println(msg);
        }
    }
}
