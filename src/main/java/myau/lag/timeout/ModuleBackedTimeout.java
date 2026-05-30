package myau.lag.timeout;

import myau.module.Module;

public final class ModuleBackedTimeout extends AbstractTimeout {
    private final Module module;
    private final AbstractTimeout secondaryTimeout;
    private boolean hasModuleDisabled = false;

    public ModuleBackedTimeout(Module module, AbstractTimeout secondaryTimeout) {
        this.module = module;
        this.secondaryTimeout = secondaryTimeout;
        if (!module.isEnabled()) {
            this.hasModuleDisabled = true;
        }
    }

    public ModuleBackedTimeout(Module module) {
        this(module, null);
    }

    @Override
    protected boolean shouldHaveTimedOut() {
        if (!this.module.isEnabled()) {
            this.hasModuleDisabled = true;
        }
        if (this.hasModuleDisabled) {
            return true;
        }
        return this.secondaryTimeout != null && this.secondaryTimeout.isTimedOut();
    }
}
