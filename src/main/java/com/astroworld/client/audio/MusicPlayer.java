package com.astroworld.client.audio;

import com.astroworld.ShowPhase;
import com.astroworld.config.AstroConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.openal.AL10;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * Plays user-supplied OGG Vorbis files through OpenAL (the same audio backend Minecraft uses).
 * Drop files into  <game dir>/astroworld_music/  named by phase:
 *   preshow.ogg  arrival.ogg  show.ogg (or show1.ogg, show2.ogg ...)  chaos.ogg  finale.ogg
 * Any extra *.ogg files are added to the "show" playlist. No audio is bundled with the mod.
 */
public final class MusicPlayer {
    private static int source = 0;
    private static int buffer = 0;
    private static ShowPhase playing = ShowPhase.IDLE;
    private static final Map<ShowPhase, List<Path>> LIBRARY = new EnumMap<>(ShowPhase.class);
    private static int showIndex = 0;
    private static boolean warnedMissing = false;

    private MusicPlayer() {}

    public static Path folder() {
        return FabricLoader.getInstance().getGameDir().resolve(AstroConfig.INSTANCE.musicFolder);
    }

    public static void scan() {
        LIBRARY.clear();
        try {
            Files.createDirectories(folder());
            Path readme = folder().resolve("README.txt");
            if (!Files.exists(readme)) Files.writeString(readme,
                    "Put your own .ogg (Vorbis) files here:\n preshow.ogg, arrival.ogg, show.ogg (or show1.ogg, show2.ogg...), chaos.ogg, finale.ogg\n"
                  + "Convert MP3 to OGG first, e.g.: ffmpeg -i song.mp3 -c:a libvorbis song.ogg\n");
            try (Stream<Path> s = Files.list(folder())) {
                s.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".ogg")).sorted().forEach(p -> {
                    String n = p.getFileName().toString().toLowerCase();
                    ShowPhase ph = n.startsWith("preshow") || n.startsWith("pre_show") ? ShowPhase.PRE_SHOW
                            : n.startsWith("arrival") ? ShowPhase.ARRIVAL
                            : n.startsWith("chaos") ? ShowPhase.ASTROWORLD_CHAOS
                            : n.startsWith("finale") ? ShowPhase.FINALE : ShowPhase.THE_SHOW;
                    LIBRARY.computeIfAbsent(ph, k -> new ArrayList<>()).add(p);
                });
            }
        } catch (IOException e) {
            System.err.println("[Astroworld] music scan failed: " + e);
        }
    }

    /** Called every client tick with the current phase. Switches track on phase change. */
    public static void update(ShowPhase phase, float atmosphere) {
        if (phase != playing) {
            stop();
            playing = phase;
            if (phase != ShowPhase.IDLE) startFor(phase);
        }
        if (source != 0) {
            float master = MinecraftClient.getInstance().options.getSoundVolume(net.minecraft.sound.SoundCategory.MASTER);
            float v = AstroConfig.INSTANCE.musicVolume * master * Math.max(0.15f, atmosphere);
            AL10.alSourcef(source, AL10.AL_GAIN, Math.max(0f, Math.min(1f, v)));
            // finished a show track early? roll to the next one in the playlist
            if (AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) == AL10.AL_STOPPED && playing == ShowPhase.THE_SHOW) {
                stopSource();
                startFor(ShowPhase.THE_SHOW);
            }
        }
    }

    private static void startFor(ShowPhase phase) {
        List<Path> list = LIBRARY.get(phase);
        if ((list == null || list.isEmpty()) && phase != ShowPhase.THE_SHOW) {
            list = null;   // missing optional phase track -> silence (previous track already stopped)
        }
        if (list == null || list.isEmpty()) {
            if (!warnedMissing) {
                warnedMissing = true;
                var p = MinecraftClient.getInstance().player;
                if (p != null) p.sendMessage(net.minecraft.text.Text.literal(
                        "[Astroworld] No music found. Put .ogg files into: " + folder()), false);
            }
            return;
        }
        Path file = list.get(phase == ShowPhase.THE_SHOW ? (showIndex++ % list.size()) : 0);
        try {
            byte[] raw = Files.readAllBytes(file);
            ByteBuffer data = MemoryUtil.memAlloc(raw.length).put(raw).flip();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer ch = stack.mallocInt(1), rate = stack.mallocInt(1);
                ShortBuffer pcm = STBVorbis.stb_vorbis_decode_memory(data, ch, rate);
                if (pcm == null) throw new IOException("not a valid OGG Vorbis file: " + file.getFileName());
                buffer = AL10.alGenBuffers();
                AL10.alBufferData(buffer, ch.get(0) == 1 ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16, pcm, rate.get(0));
                MemoryUtil.memFree(pcm);
            } finally {
                MemoryUtil.memFree(data);
            }
            source = AL10.alGenSources();
            AL10.alSourcei(source, AL10.AL_BUFFER, buffer);
            AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);   // plays "in your head", like a festival PA
            AL10.alSourcei(source, AL10.AL_LOOPING, phase == ShowPhase.THE_SHOW ? AL10.AL_FALSE : AL10.AL_FALSE);
            AL10.alSourcePlay(source);
        } catch (Throwable t) {
            System.err.println("[Astroworld] cannot play " + file + ": " + t);
            stopSource();
        }
    }

    private static void stopSource() {
        if (source != 0) { AL10.alSourceStop(source); AL10.alDeleteSources(source); source = 0; }
        if (buffer != 0) { AL10.alDeleteBuffers(buffer); buffer = 0; }
    }

    public static void stop() { stopSource(); playing = ShowPhase.IDLE; }

    public static void resetSession() { stop(); showIndex = 0; warnedMissing = false; }
}
