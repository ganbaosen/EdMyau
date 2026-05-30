package myau.lag.timeout;

public abstract class AbstractTimeout {
    private boolean timedOut = false;

    public final boolean isTimedOut() {
        if (!this.timedOut && this.shouldHaveTimedOut()) {
            this.timedOut = true;
        }
        return this.timedOut;
    }

    public final void forceTimeOut() {
        this.timedOut = true;
    }

    protected abstract boolean shouldHaveTimedOut();
}
