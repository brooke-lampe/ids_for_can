package com.example.ids_for_can;

import static android.content.ContentValues.TAG;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import com.example.ids_for_can.Log;
import android.widget.Toast;

import com.github.pires.obd.commands.ObdCommand;
import com.github.pires.obd.enums.ObdProtocols;
import com.example.ids_for_can.R;
import com.example.ids_for_can.connectivity.ObdConfig;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * Configuration
 */
public class ConfigActivity extends PreferenceActivity implements OnPreferenceChangeListener {

    public static final String ENABLE_BT_KEY = "enable_bluetooth_preference";
    public static final String BLUETOOTH_LIST_KEY = "bluetooth_list_preference";
    public static final String VEHICLE_LIST_KEY = "vehicle_list_preference";
    public static final String PROTOCOLS_LIST_KEY = "obd_protocols_preference";
    public static final String IMPERIAL_UNITS_KEY = "imperial_units_preference";
    public static final String OBD_UPDATE_PERIOD_KEY = "obd_update_period_preference";
    public static final String CONFIG_READER_KEY = "reader_config_preference";
    public static final String COMMANDS_SCREEN_KEY = "obd_commands_screen";

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        /*
         * Read preferences resources available at res/xml/preferences.xml
         */
        addPreferencesFromResource(R.xml.preferences);

        SharedPreferences vehiclePreference = getApplicationContext().getSharedPreferences("VEHICLE_PREFERENCE", MODE_MULTI_PROCESS);
        HashSet<String> all_vehicles = new HashSet<>();
        if (vehiclePreference.contains("ALL_VEHICLES")) {
            all_vehicles = new HashSet<>(vehiclePreference.getStringSet("ALL_VEHICLES", new HashSet<>()));
        } else {
            all_vehicles.add("NEW");
        }
        Log.d(TAG, "all_vehicles: " + all_vehicles);

        String selected_vehicle = "NEW";
        if (vehiclePreference.contains("SELECTED_VEHICLE")) {
            selected_vehicle = vehiclePreference.getString("SELECTED_VEHICLE", new String());
        }
        Log.d(TAG, "selected_vehicle: " + selected_vehicle);

        ListPreference vehicleListPreference = (ListPreference) getPreferenceScreen()
                .findPreference(VEHICLE_LIST_KEY);
        vehicleListPreference.setEntries(all_vehicles.toArray(new CharSequence[0]));
        vehicleListPreference.setEntryValues(all_vehicles.toArray(new CharSequence[0]));

        /*
         * Available OBD protocols
         *
         */
        ArrayList<CharSequence> protocolStrings = new ArrayList<>();
        ListPreference listProtocols = (ListPreference) getPreferenceScreen()
                .findPreference(PROTOCOLS_LIST_KEY);

        for (ObdProtocols protocol : ObdProtocols.values()) {
            protocolStrings.add(protocol.name());
        }
        listProtocols.setEntries(protocolStrings.toArray(new CharSequence[0]));
        listProtocols.setEntryValues(protocolStrings.toArray(new CharSequence[0]));

        /*
         * OBD update period (in seconds)
         *
         */
        String[] prefKeys = new String[]{OBD_UPDATE_PERIOD_KEY};
        for (String prefKey : prefKeys) {
            EditTextPreference txtPref = (EditTextPreference) getPreferenceScreen()
                    .findPreference(prefKey);
            txtPref.setOnPreferenceChangeListener(this);
        }

        /*
         * Available OBD commands
         *
         *
         */
        ArrayList<ObdCommand> cmds = ObdConfig.getCommands();
        PreferenceScreen cmdScr = (PreferenceScreen) getPreferenceScreen()
                .findPreference(COMMANDS_SCREEN_KEY);
        for (ObdCommand cmd : cmds) {
            CheckBoxPreference cpref = new CheckBoxPreference(this);
            cpref.setTitle(cmd.getName());
            cpref.setKey(cmd.getName());
            cpref.setChecked(true);
            cmdScr.addPreference(cpref);
        }

        /*
         * Let's use this device Bluetooth adapter to select which paired OBD-II
         * compliant device we'll use.
         */
        ArrayList<CharSequence> pairedDeviceStrings = new ArrayList<>();
        ArrayList<CharSequence> vals = new ArrayList<>();
        ListPreference listBtDevices = (ListPreference) getPreferenceScreen()
                .findPreference(BLUETOOTH_LIST_KEY);

        final BluetoothAdapter mBtAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBtAdapter == null) {
            listBtDevices.setEntries(pairedDeviceStrings.toArray(new CharSequence[0]));
            listBtDevices.setEntryValues(vals.toArray(new CharSequence[0]));

            // we shouldn't get here, still warn user
            Toast.makeText(this, "This device does not support Bluetooth.",
                    Toast.LENGTH_LONG).show();

            return;
        }

        /*
         * Listen for preferences click.
         *
         *
         */
        final Activity thisActivity = this;
        listBtDevices.setEntries(new CharSequence[1]);
        listBtDevices.setEntryValues(new CharSequence[1]);
        listBtDevices.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                Log.d(TAG, "!! " + listBtDevices.getValue());
                Log.d(TAG, "!! " + String.valueOf(listBtDevices.getEntry()));
                Log.d(TAG, "!! " + String.valueOf(listBtDevices.getEntryValues()));
                // see what I mean in the previous comment?
                if (mBtAdapter == null || !mBtAdapter.isEnabled()) {
                    Toast.makeText(thisActivity,
                            "This device does not support Bluetooth or it is disabled.",
                            Toast.LENGTH_LONG).show();
                    return false;
                }
                return true;
            }
        });

        /*
         * Get paired devices and populate preference list.
         */
        Set<BluetoothDevice> pairedDevices = mBtAdapter.getBondedDevices();
        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                pairedDeviceStrings.add(device.getName() + "\n" + device.getAddress());
                vals.add(device.getAddress());
            }
        }
        listBtDevices.setEntries(pairedDeviceStrings.toArray(new CharSequence[0]));
        listBtDevices.setEntryValues(vals.toArray(new CharSequence[0]));
        Log.d(TAG, "?? " + listBtDevices.getValue());
        Log.d(TAG, "?? " + String.valueOf(listBtDevices.getEntry()));
        Log.d(TAG, "?? " + String.valueOf(listBtDevices.getEntryValues()));
    }

    /**
     * OnPreferenceChangeListener method that will validate a preference new
     * value when it's changed.
     *
     * @param preference the changed preference
     * @param newValue   the value to be validated and set if valid
     */
    public boolean onPreferenceChange(Preference preference, Object newValue) {

        if (OBD_UPDATE_PERIOD_KEY.equals(preference.getKey())) {
            try {
                Double.parseDouble(newValue.toString().replace(",", "."));
                return true;
            } catch (Exception e) {
                Toast.makeText(this,
                        "Couldn't parse '" + newValue.toString() + "' as a number.",
                        Toast.LENGTH_LONG).show();
            }
        }
        return false;
    }

    /**
     * @param prefs
     * @return
     */
    public static int getObdUpdatePeriod(SharedPreferences prefs) {
        String periodString = prefs.
                getString(ConfigActivity.OBD_UPDATE_PERIOD_KEY, "1"); // 1 as in seconds
        int period = 1000; // by default 1000ms

        try {
            period = (int) (Double.parseDouble(periodString) * 1000);
        } catch (Exception e) {
        }

        if (period <= 0) {
            period = 1000;
        }

        return period;
    }

    /**
     * @param prefs
     * @return
     */
    public static ArrayList<ObdCommand> getObdCommands(SharedPreferences prefs) {
        ArrayList<ObdCommand> cmds = ObdConfig.getCommands();
        ArrayList<ObdCommand> ucmds = new ArrayList<>();
        for (int i = 0; i < cmds.size(); i++) {
            ObdCommand cmd = cmds.get(i);
            boolean selected = prefs.getBoolean(cmd.getName(), true);
            if (selected)
                ucmds.add(cmd);
        }
        return ucmds;
    }

    /**
     * @param prefs
     * @return
     */
    public static String[] getReaderConfigCommands(SharedPreferences prefs) {
        String cmdsStr = prefs.getString(CONFIG_READER_KEY, "atsp0\natz");
        String[] cmds = cmdsStr.split("\n");
        return cmds;
    }
}