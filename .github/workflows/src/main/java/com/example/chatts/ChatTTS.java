package com.example.chatts;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

@Mod("chatts")
public class ChatTTS {

    public ChatTTS() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, TTSConfig.SPEC);
        MinecraftForge.EVENT_BUS.register(new ChatListener());
        MinecraftForge.EVENT_BUS.register(new ScreenListener());
    }
}
