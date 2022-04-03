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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Configuration
 */
public class ConfigActivity extends PreferenceActivity implements OnPreferenceChangeListener {

    public static final String ENABLE_BT_KEY = "enable_bluetooth_preference";
    public static final String BLUETOOTH_LIST_KEY = "bluetooth_list_preference";
    public static final String VEHICLE_LIST_KEY = "vehicle_list_preference";
    public static final String VEHICLE_DELETE_KEY = "vehicle_delete_preference";
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
        }

        String selected_vehicle = null;
        if (vehiclePreference.contains("SELECTED_VEHICLE")) {
            selected_vehicle = vehiclePreference.getString("SELECTED_VEHICLE", new String());
        }

        ListPreference vehicleListPreference = (ListPreference) getPreferenceScreen()
                .findPreference(VEHICLE_LIST_KEY);
        vehicleListPreference.setEntries(all_vehicles.toArray(new CharSequence[0]));
        vehicleListPreference.setEntryValues(all_vehicles.toArray(new CharSequence[0]));
        vehicleListPreference.setOnPreferenceChangeListener(this);

        ListPreference vehicleDeletePreference = (ListPreference) getPreferenceScreen()
                .findPreference(VEHICLE_DELETE_KEY);
        vehicleDeletePreference.setEntries(all_vehicles.toArray(new CharSequence[0]));
        vehicleDeletePreference.setEntryValues(all_vehicles.toArray(new CharSequence[0]));
        vehicleDeletePreference.setOnPreferenceChangeListener(this);

        /*
         * Available OBD protocols
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
         */
        String[] prefKeys = new String[]{OBD_UPDATE_PERIOD_KEY};
        for (String prefKey : prefKeys) {
            EditTextPreference txtPref = (EditTextPreference) getPreferenceScreen()
                    .findPreference(prefKey);
            txtPref.setOnPreferenceChangeListener(this);
        }

        /*
         * Available OBD commands
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

            // Should not occur
            Toast.makeText(this, "This device does not support Bluetooth.",
                    Toast.LENGTH_LONG).show();

            return;
        }

        /*
         * Listen for preferences click.
         */
        final Activity thisActivity = this;
        listBtDevices.setEntries(new CharSequence[1]);
        listBtDevices.setEntryValues(new CharSequence[1]);
        listBtDevices.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
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
    }

    /**
     * OnPreferenceChangeListener method that will validate a preference new
     * value when it's changed.
     *
     * @param preference the changed preference
     * @param newValue   the value to be validated and set if valid
     */
    public boolean onPreferenceChange(Preference preference, Object newValue) {

        Log.d(TAG, "preference: " + preference);
        Log.d(TAG, "newValue: " + newValue);

        if (VEHICLE_LIST_KEY.equals(preference.getKey())) {
            // We need to check if we have a profile/matrix for this vehicle
            // If we do, then training is complete,
            // and we can pull up the profile/matrix and run the IDS

            // If not, then training is not complete,
            // and the IDS is unavailable

            try {
                SharedPreferences vehiclePreference = getApplicationContext().getSharedPreferences("VEHICLE_PREFERENCE", MODE_MULTI_PROCESS);

                HashSet<String> resultHashSet = new HashSet<>(vehiclePreference.getStringSet("ALL_VEHICLES", new HashSet<>()));

                String resultString = vehiclePreference.getString("SELECTED_VEHICLE", new String());

                String resultJSON = vehiclePreference.getString("PROFILES", new String());

                JSONArray storedJSON = new JSONArray(resultJSON);

                SharedPreferences.Editor editor = vehiclePreference.edit();
                editor.putString("SELECTED_VEHICLE", newValue.toString());
                boolean commitResult = editor.commit();

                String updatedResultString = vehiclePreference.getString("SELECTED_VEHICLE", new String());

                JSONObject obj = null;
                for (int i = 0; i < storedJSON.length(); i++) {
                    JSONObject temp_obj = storedJSON.getJSONObject(i);
                    if (temp_obj.get("profileName").equals(newValue.toString())) {
                        obj = temp_obj;
                        break;
                    }
                }

                String recoveredOrder[] = null;
                boolean recoveredMatrix[][] = null;

                JSONArray orderJSON = null;
                JSONArray matrixParent = null;

                if (obj != null) {
                    // If the array exists, it should be at least 1
                    orderJSON = obj.getJSONArray("order");
                    recoveredOrder = new String[orderJSON.length()];

                    for (int i = 0; i < orderJSON.length(); i++) {
                        recoveredOrder[i] = orderJSON.getString(i);
                    }

                    // If the matrix exists, it should be at least 1 x 1
                    matrixParent = obj.getJSONArray("matrix");
                    int rows = matrixParent.length();
                    int cols = matrixParent.getJSONArray(0).length();
                    recoveredMatrix = new boolean[rows][cols];

                    for (int i = 0; i < matrixParent.length(); i++) {
                        JSONArray matrixChild = matrixParent.getJSONArray(i);
                        for (int j = 0; j < matrixChild.length(); j++) {
                            recoveredMatrix[i][j] = matrixChild.getBoolean(j);
                        }
                    }
                }

                if (recoveredOrder != null && recoveredMatrix != null) {
                    if (recoveredOrder != null) {
                        Log.d(TAG, "Printing recoveredOrder...");
                        Log.d(TAG, Arrays.toString(recoveredOrder));
                    }

                    Log.d(TAG, "Printing recoveredMatrix...");
                    for (boolean[] arr : recoveredMatrix) {
                        Log.d(TAG, Arrays.toString(arr));
                    }

                    MainActivity.ATMAOrder = recoveredOrder;
                    MainActivity.profileMatrix = recoveredMatrix;
                    MainActivity.trainingComplete = true;
                } else {
                    MainActivity.ATMAOrder = null;
                    MainActivity.profileMatrix = null;
                    MainActivity.trainingComplete = false;
                }

                // We need to change the preference,
                // even if we don't have a matrix/profile
                // so the user can train the vehicle if desired
                return true;

            } catch (JSONException jsonException) {
                jsonException.printStackTrace();
            }
        }

        if (VEHICLE_DELETE_KEY.equals(preference.getKey())) {
            // Delete a previously trained vehicle and update the SharedPreferences store

            try {
                SharedPreferences vehiclePreference = getApplicationContext().getSharedPreferences("VEHICLE_PREFERENCE", MODE_MULTI_PROCESS);

                HashSet<String> resultHashSet = new HashSet<>(vehiclePreference.getStringSet("ALL_VEHICLES", new HashSet<>()));

                String resultString = vehiclePreference.getString("SELECTED_VEHICLE", new String());

                String resultJSON = vehiclePreference.getString("PROFILES", new String());

                JSONArray storedJSON = new JSONArray(resultJSON);

                SharedPreferences.Editor editor = vehiclePreference.edit();

                resultHashSet.remove(newValue.toString());
                editor.putStringSet("ALL_VEHICLES", resultHashSet);

                if (resultString.equals(newValue.toString())) {
                    // We are deleting the currently selected vehicle
                    editor.putString("SELECTED_VEHICLE", null);
                    MainActivity.trainingComplete = false;
                }

                JSONObject obj = null;
                for (int i = 0; i < storedJSON.length(); i++) {
                    JSONObject temp_obj = storedJSON.getJSONObject(i);
                    if (temp_obj.get("profileName").equals(newValue.toString())) {
                        obj = temp_obj;
                        // Remove the old JSONObject
                        storedJSON.remove(i);
                        break;
                    }
                }

                editor.putString("PROFILES", storedJSON.toString());

                boolean commitResult = editor.commit();

                HashSet<String> updatedResultHashSet = new HashSet<>(vehiclePreference.getStringSet("ALL_VEHICLES", new HashSet<>()));
                Log.d(TAG, "updatedResultHashSet: " + updatedResultHashSet);

                String updatedResultString = vehiclePreference.getString("SELECTED_VEHICLE", new String());
                Log.d(TAG, "updatedResultString: " + updatedResultString);

                String updatedResultJSON = vehiclePreference.getString("PROFILES", new String());
                Log.d(TAG, "updatedResultJSON: " + updatedResultJSON);

                ListPreference vehicleListPreference = (ListPreference) getPreferenceScreen()
                        .findPreference(VEHICLE_LIST_KEY);
                vehicleListPreference.setEntries(resultHashSet.toArray(new CharSequence[0]));
                vehicleListPreference.setEntryValues(resultHashSet.toArray(new CharSequence[0]));
                vehicleListPreference.setOnPreferenceChangeListener(this);

                ListPreference vehicleDeletePreference = (ListPreference) getPreferenceScreen()
                        .findPreference(VEHICLE_DELETE_KEY);
                vehicleDeletePreference.setEntries(resultHashSet.toArray(new CharSequence[0]));
                vehicleDeletePreference.setEntryValues(resultHashSet.toArray(new CharSequence[0]));
                vehicleDeletePreference.setOnPreferenceChangeListener(this);

                return true;

            } catch (JSONException jsonException) {
                jsonException.printStackTrace();
            }
        }

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

    public static int getObdUpdatePeriod(SharedPreferences prefs) {
        String periodString = prefs.
                getString(ConfigActivity.OBD_UPDATE_PERIOD_KEY, "4"); // 4 as in seconds
        int period = 4000; // by default 4000ms

        try {
            period = (int) (Double.parseDouble(periodString) * 1000);
        } catch (Exception e) {
        }

        if (period <= 0) {
            period = 4000;
        }

        return period;
    }

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

    public static String[] getReaderConfigCommands(SharedPreferences prefs) {
        String cmdsStr = prefs.getString(CONFIG_READER_KEY, "atsp0\natz");
        String[] cmds = cmdsStr.split("\n");
        return cmds;
    }
}