package com.liy.ancientdragon.client.atmosphere;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Lightweight, file-based presentation controls; absent or invalid values safely use defaults. */
record DragonAtmosphereClientOptions(float cameraIntensity, boolean reducedFlashes) {
    static final DragonAtmosphereClientOptions DEFAULT = new DragonAtmosphereClientOptions(1.0F, true);

    static DragonAtmosphereClientOptions load(Path configDirectory) {
        Path file = configDirectory.resolve("ancient_dragon-atmosphere.properties");
        if (!Files.isRegularFile(file)) {
            return DEFAULT;
        }
        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(file)) {
            properties.load(reader);
            return new DragonAtmosphereClientOptions(
                    bounded(properties.getProperty("camera_intensity"), DEFAULT.cameraIntensity),
                    Boolean.parseBoolean(properties.getProperty("reduced_flashes", "true")));
        } catch (IOException | IllegalArgumentException ignored) {
            return DEFAULT;
        }
    }

    private static float bounded(String value, float fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Math.clamp(Float.parseFloat(value), 0.0F, 1.0F);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
