package com.example.chatts;

import net.minecraftforge.common.ForgeConfigSpec;

public class TTSConfig {

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.ConfigValue<String> LANGUAGE;
    public static final ForgeConfigSpec.ConfigValue<String> NPC_PREFIX;
    public static final ForgeConfigSpec.BooleanValue ONLY_PREFIXED;
    public static final ForgeConfigSpec.DoubleValue VOLUME;
    public static final ForgeConfigSpec.BooleanValue STRIP_PREFIX_BEFORE_SPEAKING;

    // --- Dialog GUI scanning (for mods like Custom NPCs that show dialogue in a separate window) ---
    public static final ForgeConfigSpec.BooleanValue SCREEN_ENABLED;
    public static final ForgeConfigSpec.ConfigValue<String> NPC_SCREEN_KEYWORD;
    public static final ForgeConfigSpec.ConfigValue<String> TARGET_FIELD_NAME;
    public static final ForgeConfigSpec.BooleanValue DEBUG_SCAN;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("chatts");

        ENABLED = builder
                .comment("Master on/off switch for chat text-to-speech.")
                .define("enabled", true);

        LANGUAGE = builder
                .comment("Language code passed to Google Translate TTS. Use 'ru' for Russian, 'en' for English, etc.")
                .define("language", "ru");

        NPC_PREFIX = builder
                .comment("Chat messages starting with this prefix will be spoken.",
                        "Have your NPC scripts broadcast dialogue lines to chat starting with this prefix, e.g. '[NPC] Привет, путник!'")
                .define("npcPrefix", "[NPC]");

        ONLY_PREFIXED = builder
                .comment("If true, ONLY messages starting with npcPrefix are spoken (recommended).",
                        "If false, ALL chat messages are spoken, which is noisy on busy servers.")
                .define("onlyPrefixed", true);

        STRIP_PREFIX_BEFORE_SPEAKING = builder
                .comment("If true, the npcPrefix itself is removed from the text before it is sent to the speech engine.")
                .define("stripPrefixBeforeSpeaking", true);

        VOLUME = builder
                .comment("Playback volume, 0.0 to 1.0.")
                .defineInRange("volume", 1.0, 0.0, 1.0);

        builder.pop();

        builder.push("dialogScreen");

        SCREEN_ENABLED = builder
                .comment("If true, the mod also scans dialog/GUI windows (e.g. Custom NPCs dialog screens) for text to speak.",
                        "Use this when NPC dialogue appears in its own window instead of the chat.")
                .define("enabled", true);

        NPC_SCREEN_KEYWORD = builder
                .comment("Only screens whose Java class name contains this text (case-insensitive) are scanned.",
                        "Default 'npc' matches most Custom NPCs GUI classes (they live in packages containing 'npcs').",
                        "Leave empty to scan every screen (NOT recommended, very noisy).")
                .define("npcScreenKeyword", "npc");

        TARGET_FIELD_NAME = builder
                .comment("Once you know exactly which field holds the dialogue text (see debugScan output),",
                        "put its name here so ONLY that field is spoken. Leave empty to use the automatic",
                        "'longest text found' heuristic instead (less precise, works out of the box).")
                .define("targetFieldName", "");

        DEBUG_SCAN = builder
                .comment("If true, every text field/value found in a matching screen is printed to your own chat",
                        "(locally, not sent to the server) as '[chatts-debug] ClassName.fieldName = ...'.",
                        "Use this once to find the right field name, then set targetFieldName and turn this off.")
                .define("debugScan", true);

        builder.pop();

        SPEC = builder.build();
    }

    /**
     * Applies the configured filtering rules to a raw chat line.
     * Returns the text that should be spoken, or null if this line should be ignored.
     */
    public static String stripPrefixAndClean(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return null;

        String prefix = NPC_PREFIX.get();
        boolean onlyPrefixed = ONLY_PREFIXED.get();

        if (onlyPrefixed) {
            if (prefix == null || prefix.isEmpty() || !trimmed.startsWith(prefix)) {
                return null;
            }
            if (STRIP_PREFIX_BEFORE_SPEAKING.get()) {
                trimmed = trimmed.substring(prefix.length()).trim();
            }
        } else {
            if (prefix != null && !prefix.isEmpty() && trimmed.startsWith(prefix)
                    && STRIP_PREFIX_BEFORE_SPEAKING.get()) {
                trimmed = trimmed.substring(prefix.length()).trim();
            }
        }

        return trimmed.isEmpty() ? null : trimmed;
    }
}
