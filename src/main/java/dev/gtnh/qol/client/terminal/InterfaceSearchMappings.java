package dev.gtnh.qol.client.terminal;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import net.minecraft.client.Minecraft;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

/** Per-client-account interface search overrides that survive worlds and restarts. */
public final class InterfaceSearchMappings {

    private static final String FILE_NAME = "gtnh_qol_improvements-interface-search-mappings.cfg";
    private static Configuration configuration;

    private InterfaceSearchMappings() {}

    public static synchronized String resolve(String mappingKey, String defaultValue) {
        String custom = getCustom(mappingKey);
        return custom == null ? safe(defaultValue) : custom;
    }

    public static synchronized String getCustom(String mappingKey) {
        if (mappingKey == null || mappingKey.isEmpty()) return null;
        ConfigCategory category = config().getCategory(playerCategory());
        Property property = category.get(encodedKey(mappingKey));
        return property == null ? null : property.getString();
    }

    public static synchronized void setCustom(String mappingKey, String defaultValue, String value) {
        if (mappingKey == null || mappingKey.isEmpty()) return;
        Configuration config = config();
        ConfigCategory category = config.getCategory(playerCategory());
        String propertyKey = encodedKey(mappingKey);
        String normalized = safe(value);
        if (normalized.equals(safe(defaultValue))) {
            category.remove(propertyKey);
        } else {
            Property property = config.get(playerCategory(), propertyKey, normalized);
            property.set(normalized);
        }
        config.save();
    }

    public static synchronized void reset(String mappingKey) {
        if (mappingKey == null || mappingKey.isEmpty()) return;
        Configuration config = config();
        config.getCategory(playerCategory())
            .remove(encodedKey(mappingKey));
        config.save();
    }

    private static Configuration config() {
        if (configuration == null) {
            File configDirectory = new File(Minecraft.getMinecraft().mcDataDir, "config");
            configuration = new Configuration(new File(configDirectory, FILE_NAME), "1");
            configuration.load();
        }
        return configuration;
    }

    private static String playerCategory() {
        String player = Minecraft.getMinecraft()
            .getSession()
            .getPlayerID();
        if (player == null || player.isEmpty()) {
            player = Minecraft.getMinecraft()
                .getSession()
                .getUsername();
        }
        return "player_" + safe(player).replaceAll("[^A-Za-z0-9_-]", "_");
    }

    private static String encodedKey(String mappingKey) {
        return "pool_" + Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(mappingKey.getBytes(StandardCharsets.UTF_8));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
