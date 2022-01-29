package com.github.pires.obd.commands;

import static android.content.ContentValues.TAG;

import android.util.Log;

import com.github.pires.obd.commands.ObdCommand;
import com.github.pires.obd.enums.AvailableCommandNames;

public class MonitorAllCommand extends ObdCommand {

    /**
     * Default ctor.
     */
    public MonitorAllCommand() {
        super("AT MA");
    }

    /**
     * Copy ctor.
     */
    public MonitorAllCommand(MonitorAllCommand other) {
        super(other);
    }

    @Override
    protected void performCalculations() {
    }

    @Override
    public String getFormattedResult() {
        Log.d(TAG, "rawData: " + rawData);
        Log.d(TAG, "buffer: " + buffer);
        return rawData;
    }

    @Override
    public String getCalculatedResult() {
        Log.d(TAG, "!! rawData: " + rawData);
        Log.d(TAG, "!! buffer: " + buffer);
        return rawData;
    }

    @Override
    public String getName() {
        return AvailableCommandNames.MONITOR_ALL.getValue();
    }
}
