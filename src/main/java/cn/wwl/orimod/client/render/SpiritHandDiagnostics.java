package cn.wwl.orimod.client.render;

/** Render-thread diagnostic lease. Only the development bridge opts in; shipping clients leave it off. */
public final class SpiritHandDiagnostics {
    private boolean recording;
    private int queryFrames;

    public boolean enabled() { return recording || queryFrames > 0; }

    public void recording(boolean active) { recording = active; }

    /** A query reads the last sample and requests fresh measurements for the next two rendered frames. */
    public void requestSample() { queryFrames = 2; }

    public void frameComplete() { if (queryFrames > 0) queryFrames--; }

    public void reset() { recording = false; queryFrames = 0; }
}
