package myau.util;

public class Timer {
    private final float duration;
    private long startTime;
    private boolean started;

    public Timer(float durationMs) {
        this.duration = durationMs;
        this.started = false;
    }

    public void start() {
        this.startTime = System.currentTimeMillis();
        this.started = true;
    }

    public float getValueFloat(float start, float end, int type) {
        if (!started) return start;

        long elapsed = System.currentTimeMillis() - startTime;
        float progress = Math.min(1.0f, elapsed / duration);

        // Apply easing based on type
        switch (type) {
            case 1: // Linear
                break;
            case 2: // Ease out
                progress = (float) (1 - Math.pow(1 - progress, 3));
                break;
            case 3: // Ease in
                progress = (float) Math.pow(progress, 3);
                break;
            default:
                break;
        }

        return start + (end - start) * progress;
    }

    public int getValueInt(int start, int end, int type) {
        return (int) getValueFloat(start, end, type);
    }

    public boolean isStarted() {
        return started;
    }
}
