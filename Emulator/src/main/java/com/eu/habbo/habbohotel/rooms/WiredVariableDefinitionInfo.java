package com.eu.habbo.habbohotel.rooms;

public class WiredVariableDefinitionInfo {
    private final int itemId;
    private final String name;
    private final boolean hasValue;
    private final int availability;
    private final boolean textConnected;
    private final boolean readOnly;
    private boolean canCreateAndDelete;
    private boolean canReadCreationTime;
    private boolean canReadLastUpdateTime;

    public WiredVariableDefinitionInfo(
            int itemId, String name, boolean hasValue, int availability, boolean textConnected, boolean readOnly) {
        this.itemId = itemId;
        this.name = name;
        this.hasValue = hasValue;
        this.availability = availability;
        this.textConnected = textConnected;
        this.readOnly = readOnly;
        this.canCreateAndDelete = !readOnly;
        this.canReadCreationTime = true;
        this.canReadLastUpdateTime = hasValue;
    }

    public WiredVariableDefinitionInfo withCapabilities(
            boolean createDelete, boolean creationTime, boolean updateTime) {
        this.canCreateAndDelete = createDelete;
        this.canReadCreationTime = creationTime;
        this.canReadLastUpdateTime = updateTime;
        return this;
    }

    public boolean canCreateAndDelete() {
        return canCreateAndDelete;
    }

    public boolean canReadCreationTime() {
        return canReadCreationTime;
    }

    public boolean canReadLastUpdateTime() {
        return canReadLastUpdateTime;
    }

    public int getItemId() {
        return this.itemId;
    }

    public String getName() {
        return this.name;
    }

    public boolean hasValue() {
        return this.hasValue;
    }

    public int getAvailability() {
        return this.availability;
    }

    public boolean isTextConnected() {
        return this.textConnected;
    }

    public boolean isReadOnly() {
        return this.readOnly;
    }
}
