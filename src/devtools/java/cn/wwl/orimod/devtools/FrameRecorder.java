package cn.wwl.orimod.devtools;

import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import java.io.IOException;
import java.io.BufferedOutputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;

/** Captures the game's render target. No desktop capture or operating-system input. */
final class FrameRecorder {
    private final ExecutorService writer = Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon().name("Ori video writer").factory());
    private final AtomicInteger pending = new AtomicInteger();
    private final ArrayList<Map<String, Object>> frames = new ArrayList<>();
    private Path directory;
    private long start, end, interval;
    private int nextSlot, totalSlots, encodedSlots;
    private Process encoder;
    private OutputStream stream;
    private byte[] previousPixels;
    private boolean recording;
    private int dropped;
    private volatile String error = "";

    void start(Path destination, int fps, int seconds) throws IOException {
        if (recording || pending.get() != 0) throw new IllegalStateException("Recording still active");
        if (fps < 1 || fps > 60 || seconds < 1 || seconds > 30) throw new IllegalArgumentException("Invalid recording duration/rate");
        Files.createDirectory(destination);
        directory = destination;
        var target = Minecraft.getInstance().getMainRenderTarget();
        encoder = new ProcessBuilder("ffmpeg", "-hide_banner", "-loglevel", "warning", "-n",
                "-f", "rawvideo", "-pixel_format", "rgba", "-video_size", target.width + "x" + target.height,
                "-framerate", Integer.toString(fps), "-i", "pipe:0", "-an", "-c:v", "libx264",
                "-preset", "veryfast", "-crf", "18", "-pix_fmt", "yuv420p", "-movflags", "+faststart",
                directory.resolve("capture.mp4").toAbsolutePath().toString())
                .redirectError(directory.resolve("encoder.log").toFile()).start();
        stream = new BufferedOutputStream(encoder.getOutputStream(), 262144);
        previousPixels = null;
        nextSlot = encodedSlots = 0;
        totalSlots = fps * seconds;
        frames.clear();
        dropped = 0;
        error = "";
        start = System.nanoTime();
        end = start + seconds * 1_000_000_000L;
        interval = 1_000_000_000L / fps;
        recording = true;
    }

    void frame(long now) {
        if (!recording) return;
        if (now >= end) {
            recording = false;
            pending.incrementAndGet();
            writer.execute(() -> {
                try {
                    if (previousPixels != null) {
                        while (encodedSlots < totalSlots) { stream.write(previousPixels); encodedSlots++; }
                    }
                    stream.close();
                    if (!encoder.waitFor(30, TimeUnit.SECONDS)) {
                        encoder.destroy();
                        throw new IOException("Encoder did not finish; inspect encoder.log");
                    }
                    if (encoder.exitValue() != 0) throw new IOException("Encoder failed; inspect encoder.log");
                } catch (Exception failure) { error = failure.toString(); }
                finally { pending.decrementAndGet(); }
            });
            return;
        }
        int slot = Math.min(totalSlots - 1, (int) ((now - start) / interval));
        if (slot < nextSlot || pending.get() >= 4) return;
        nextSlot = slot + 1;
        var image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget());
        frames.add(Map.of("slot", slot, "seconds", (now - start) / 1_000_000_000.0));
        dropped = nextSlot - frames.size();
        pending.incrementAndGet();
        writer.execute(() -> {
            try (image) {
                byte[] pixels = new byte[image.getWidth() * image.getHeight() * 4];
                ByteBuffer.wrap(pixels).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().put(image.getPixelsRGBA());
                // Missed capture slots hold the preceding frame instead of speeding up the video.
                while (encodedSlots < slot) { stream.write(previousPixels == null ? pixels : previousPixels); encodedSlots++; }
                stream.write(pixels);
                encodedSlots++;
                previousPixels = pixels;
            }
            catch (Exception failure) { error = failure.toString(); }
            finally { pending.decrementAndGet(); }
        });
    }

    Map<String, Object> finish() throws IOException {
        if (recording || pending.get() != 0) throw new IllegalStateException("Recording still active");
        if (directory == null || frames.size() < 2 || !error.isEmpty()) throw new IllegalStateException("No complete video: " + error);
        Path manifest = directory.resolve("frames.json");
        Files.writeString(manifest, new GsonBuilder().setPrettyPrinting().create().toJson(Map.of(
                "durationSeconds", (end - start) / 1_000_000_000.0, "frames", frames,
                "dropped", totalSlots - frames.size(), "video", "capture.mp4", "outputFrames", encodedSlots)));
        return Map.of("manifest", manifest.toString(), "count", frames.size(), "dropped", dropped);
    }

    Map<String, Object> state() {
        return Map.of("recording", recording, "pending", pending.get(), "frames", frames.size(), "dropped", dropped, "error", error);
    }
}
