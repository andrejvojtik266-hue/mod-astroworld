package com.astroworld.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** JSON config: config/astroworld.json. Created with defaults on first launch. */
public class AstroConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static AstroConfig INSTANCE = new AstroConfig();

    // Gameplay / show
    public int preShowSeconds = 30;
    public int arrivalSeconds = 20;
    public int showSeconds = 120;
    public int chaosSeconds = 40;
    public int finaleSeconds = 20;
    public double bpm = 140.0;
    public int giantHeightBlocks = 40;      // 30..50 recommended
    public int stageDistance = 90;          // distance of the stage from the player who started the show
    public int effectRadius = 200;          // players within this radius are in the show
    public int cooldownSeconds = 10;        // after stop/finish before another show can start

    // Client
    public float musicVolume = 0.8f;        // 0..1
    public float cameraShake = 1.0f;        // 0 disables
    public boolean skyEffects = true;
    public boolean flashes = true;          // set false for photosensitivity
    public boolean lasers = true;
    public String musicFolder = "astroworld_music";   // relative to the game dir

    public static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("astroworld.json");
    }

    public static void load() {
        Path p = path();
        try {
            if (Files.exists(p)) {
                AstroConfig c = GSON.fromJson(Files.readString(p), AstroConfig.class);
                if (c != null) INSTANCE = c;
            }
            INSTANCE.giantHeightBlocks = Math.max(10, Math.min(80, INSTANCE.giantHeightBlocks));
            INSTANCE.bpm = Math.max(60, Math.min(220, INSTANCE.bpm));
            save();
        } catch (Exception e) {
            System.err.println("[Astroworld] config load failed, using defaults: " + e);
        }
    }

    public static void save() {
        try {
            Files.writeString(path(), GSON.toJson(INSTANCE));
        } catch (IOException e) {
            System.err.println("[Astroworld] config save failed: " + e);
        }
    }
}
