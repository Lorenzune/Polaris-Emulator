package com.eu.habbo.habbohotel.items.interactions.wired;

public class WiredSettings {
    private int[] intParams;
    private String stringParam;
    private int[] furniIds;
    private int[] furniIds2;
    private int[] variableIds;
    private int[] furniSourceTypes;
    private int[] userSourceTypes;
    private int stuffTypeSelectionCode;
    private int delay;

    public WiredSettings(int[] intParams, String stringParam, int[] furniIds, int stuffTypeSelectionCode, int delay)
    {
        this(
            intParams,
            stringParam,
            furniIds,
            new int[0],
            new int[0],
            new int[0],
            new int[0],
            stuffTypeSelectionCode,
            delay);
    }

    public WiredSettings(
        int[] intParams,
        String stringParam,
        int[] furniIds,
        int[] furniIds2,
        int[] variableIds,
        int[] furniSourceTypes,
        int[] userSourceTypes,
        int stuffTypeSelectionCode,
        int delay)
    {
        this.furniIds = furniIds;
        this.furniIds2 = furniIds2;
        this.variableIds = variableIds;
        this.furniSourceTypes = furniSourceTypes;
        this.userSourceTypes = userSourceTypes;
        this.intParams = intParams;
        this.stringParam = stringParam;
        this.stuffTypeSelectionCode = stuffTypeSelectionCode;
        this.delay = delay;
    }

    public WiredSettings(int[] intParams, String stringParam, int[] furniIds, int stuffTypeSelectionCode)
    {
        this(intParams, stringParam, furniIds, stuffTypeSelectionCode, 0);
    }

    public int getStuffTypeSelectionCode() {
        return stuffTypeSelectionCode;
    }

    public void setStuffTypeSelectionCode(int stuffTypeSelectionCode) {
        this.stuffTypeSelectionCode = stuffTypeSelectionCode;
    }

    public int[] getFurniIds() {
        return furniIds;
    }

    public void setFurniIds(int[] furniIds) {
        this.furniIds = furniIds;
    }

    public int[] getFurniIds2() {
        return furniIds2;
    }

    public void setFurniIds2(int[] furniIds2) {
        this.furniIds2 = furniIds2;
    }

    public int[] getVariableIds() {
        return variableIds;
    }

    public void setVariableIds(int[] variableIds) {
        this.variableIds = variableIds;
    }

    public int[] getFurniSourceTypes() {
        return furniSourceTypes;
    }

    public void setFurniSourceTypes(int[] furniSourceTypes) {
        this.furniSourceTypes = furniSourceTypes;
    }

    public int[] getUserSourceTypes() {
        return userSourceTypes;
    }

    public void setUserSourceTypes(int[] userSourceTypes) {
        this.userSourceTypes = userSourceTypes;
    }

    public String getStringParam() {
        return stringParam;
    }

    public void setStringParam(String stringParam) {
        this.stringParam = stringParam;
    }

    public int[] getIntParams() {
        return intParams;
    }

    public void setIntParams(int[] intParams) {
        this.intParams = intParams;
    }

    public int getDelay() {
        return delay;
    }

    public void setDelay(int delay) {
        this.delay = delay;
    }
}
