package com.example.ids_for_can;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewGroup.MarginLayoutParams;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.github.pires.obd.commands.ObdCommand;
import com.github.pires.obd.commands.SpeedCommand;
import com.github.pires.obd.commands.engine.RPMCommand;
import com.github.pires.obd.commands.engine.RuntimeCommand;
import com.github.pires.obd.enums.AvailableCommandNames;
import com.example.ids_for_can.R;
import com.example.ids_for_can.connectivity.ObdConfig;
import com.example.ids_for_can.connectivity.AbstractGatewayService;
import com.example.ids_for_can.connectivity.MockObdGatewayService;
import com.example.ids_for_can.connectivity.ObdCommandJob;
import com.example.ids_for_can.connectivity.ObdGatewayService;
import com.example.ids_for_can.connectivity.ObdProgressListener;
import com.example.ids_for_can.connectivity.ObdReading;
import com.example.ids_for_can.connectivity.ObdService;
import com.google.inject.Inject;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import roboguice.RoboGuice;
import roboguice.activity.RoboActivity;
import roboguice.inject.ContentView;
import roboguice.inject.InjectView;

@ContentView(R.layout.main)
public class MainActivity extends RoboActivity implements ObdProgressListener {

    private static final String TAG = MainActivity.class.getName();
    private static final int NO_BLUETOOTH_ID = 0;
    private static final int BLUETOOTH_DISABLED = 1;
    private static final int START_LIVE_DATA = 2;
    private static final int STOP_LIVE_DATA = 3;
    private static final int SETTINGS = 4;
    private static final int TABLE_ROW_MARGIN = 7;
    private static final int REQUEST_ENABLE_BT = 1234;
    private static boolean bluetoothDefaultIsEnable = false;

    public static final int PERMISSIONS_REQUEST_BLUETOOTH = 1;

    static {
        RoboGuice.setUseAnnotationDatabases(false);
    }

    public Map<String, String> commandResult = new HashMap<String, String>();
    
    @InjectView(R.id.BT_STATUS) TextView btStatusTextView;
    @InjectView(R.id.OBD_STATUS) TextView obdStatusTextView;
    @InjectView(R.id.vehicle_view) LinearLayout vv;
    @InjectView(R.id.data_table) TableLayout tl;
    @Inject SharedPreferences prefs;
    private boolean isServiceBound;
    private AbstractGatewayService service;
    private final Runnable mQueueCommands = new Runnable() {
        public void run() {
            if (service != null && service.isRunning() && service.queueEmpty()) {
                queueCommands();
                commandResult.clear();
            }
            // run again in period defined in preferences
            new Handler().postDelayed(mQueueCommands, ConfigActivity.getObdUpdatePeriod(prefs));
        }
    };

    private boolean preRequisites = true;
    private ServiceConnection serviceConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder binder) {
            Log.d(TAG, className.toString() + " service is bound");
            isServiceBound = true;
            service = ((AbstractGatewayService.AbstractGatewayServiceBinder) binder).getService();
            service.setContext(MainActivity.this);
            Log.d(TAG, "START LIVE DATA");
            try {
                service.startService();
                if (preRequisites)
                    btStatusTextView.setText(getString(R.string.status_bluetooth_connected));
            } catch (IOException ioe) {
                Log.e(TAG, "FAILED TO START LIVE DATA");
                btStatusTextView.setText(getString(R.string.status_bluetooth_error_connecting));
                doUnbindService();
            }
        }

        @Override
        protected Object clone() throws CloneNotSupportedException {
            return super.clone();
        }

        // This method is *only* called when the connection to the service is lost unexpectedly
        // and *not* when the client unbinds (http://developer.android.com/guide/components/bound-services.html)
        // So the isServiceBound attribute should also be set to false when we unbind from the service.
        @Override
        public void onServiceDisconnected(ComponentName className) {
            Log.d(TAG, className.toString() + " service is unbound");
            isServiceBound = false;
        }
    };

    public static String LookUpCommand(String txt) {
        for (AvailableCommandNames item : AvailableCommandNames.values()) {
            if (item.getValue().equals(txt)) return item.name();
        }
        return txt;
    }

    public void updateTextView(final TextView view, final String txt) {
        new Handler().post(new Runnable() {
            public void run() {
                view.setText(txt);
            }
        });
    }

    public void stateUpdate(final ObdCommandJob job) {
        final String cmdName = job.getCommand().getName();
        String cmdResult = "";
        final String cmdID = LookUpCommand(cmdName);

        if (job.getState().equals(ObdCommandJob.ObdCommandJobState.EXECUTION_ERROR)) {
            cmdResult = job.getCommand().getResult();
            if (cmdResult != null && isServiceBound) {
                obdStatusTextView.setText(cmdResult.toLowerCase());
            }
        } else if (job.getState().equals(ObdCommandJob.ObdCommandJobState.BROKEN_PIPE)) {
            if (isServiceBound)
                stopLiveData();
        } else if (job.getState().equals(ObdCommandJob.ObdCommandJobState.NOT_SUPPORTED)) {
            cmdResult = getString(R.string.status_obd_no_support);
        } else {
            cmdResult = job.getCommand().getFormattedResult();
            if(isServiceBound)
                obdStatusTextView.setText(getString(R.string.status_obd_data));
        }

        if (vv.findViewWithTag(cmdID) != null) {
            TextView existingTV = (TextView) vv.findViewWithTag(cmdID);
            existingTV.setText(cmdResult);
        } else addTableRow(cmdID, cmdName, cmdResult);
        commandResult.put(cmdID, cmdResult);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //setContentView(R.layout.main);

        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.BLUETOOTH) ==
                PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(
                        this, Manifest.permission.BLUETOOTH_ADMIN) ==
                        PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(
                this, Manifest.permission.BLUETOOTH_ADVERTISE) ==
                PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(
                this, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(
                this, Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "BLUETOOTH PERMISSION GRANTED!");
        } else {
            // You can directly ask for the permission.
            // The registered ActivityResultCallback gets the result of this request.
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.BLUETOOTH,
                            Manifest.permission.BLUETOOTH_ADMIN,
                            Manifest.permission.BLUETOOTH_ADVERTISE,
                            Manifest.permission.BLUETOOTH_CONNECT,
                            Manifest.permission.BLUETOOTH_SCAN},
                    PERMISSIONS_REQUEST_BLUETOOTH);
            Log.d(TAG, "BLUETOOTH PERMISSION REQUESTED!");
        }

        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.BLUETOOTH) ==
                PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(
                this, Manifest.permission.BLUETOOTH_ADMIN) ==
                PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(
                this, Manifest.permission.BLUETOOTH_ADVERTISE) ==
                PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(
                this, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(
                this, Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED) {
            final BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
            if (btAdapter != null) {
                bluetoothDefaultIsEnable = btAdapter.isEnabled();
                if (!bluetoothDefaultIsEnable) {
                    btAdapter.enable();
                }
            }
        }

        obdStatusTextView.setText(getString(R.string.status_obd_disconnected));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST_BLUETOOTH:
                if (grantResults.length > 4 && grantResults[0] == PackageManager.PERMISSION_GRANTED
                        && grantResults[1] == PackageManager.PERMISSION_GRANTED
                        && grantResults[2] == PackageManager.PERMISSION_GRANTED
                        && grantResults[3] == PackageManager.PERMISSION_GRANTED
                        && grantResults[4] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "BLUETOOTH GRANTED");
                } else {
                    Log.d(TAG, "BLUETOOTH DENIED");
                }
                break;
            default:
                Log.d(TAG, "UNEXPECTED SWITCH CASE");
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "Entered onStart...");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Entered onDestroy...");

        if (isServiceBound) {
            doUnbindService();
        }

        final BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
        if (btAdapter != null && btAdapter.isEnabled() && !bluetoothDefaultIsEnable)
            btAdapter.disable();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "Pausing...");
    }

    protected void onResume() {
        super.onResume();
        Log.d(TAG, "Resuming...");

        // get Bluetooth device
        final BluetoothAdapter btAdapter = BluetoothAdapter
                .getDefaultAdapter();

        preRequisites = btAdapter != null && btAdapter.isEnabled();
        if (!preRequisites && prefs.getBoolean(ConfigActivity.ENABLE_BT_KEY, false)) {
            preRequisites = btAdapter != null && btAdapter.enable();
        }

        if (!preRequisites) {
            showDialog(BLUETOOTH_DISABLED);
            btStatusTextView.setText(getString(R.string.status_bluetooth_disabled));
        } else {
            btStatusTextView.setText(getString(R.string.status_bluetooth_ok));
        }
    }

    private void updateConfig() {
        startActivity(new Intent(this, ConfigActivity.class));
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, START_LIVE_DATA, 0, getString(R.string.menu_start_live_data));
        menu.add(0, STOP_LIVE_DATA, 0, getString(R.string.menu_stop_live_data));
        menu.add(0, SETTINGS, 0, getString(R.string.menu_settings));
        Log.d(TAG, "Creating menu...");
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case START_LIVE_DATA:
                startLiveData();
                return true;
            case STOP_LIVE_DATA:
                stopLiveData();
                return true;
            case SETTINGS:
                updateConfig();
                return true;
        }
        return false;
    }

    private void startLiveData() {
        Log.d(TAG, "Starting live data...");

        tl.removeAllViews(); //start fresh
        doBindService();

        // start command execution
        new Handler().post(mQueueCommands);
    }

    private void stopLiveData() {
        Log.d(TAG, "Stopping live data...");

        doUnbindService();
    }

    protected Dialog onCreateDialog(int id) {
        AlertDialog.Builder build = new AlertDialog.Builder(this);
        switch (id) {
            case NO_BLUETOOTH_ID:
                build.setMessage(getString(R.string.text_no_bluetooth_id));
                return build.create();
            case BLUETOOTH_DISABLED:
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                return build.create();
        }
        return null;
    }

    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem startItem = menu.findItem(START_LIVE_DATA);
        MenuItem stopItem = menu.findItem(STOP_LIVE_DATA);
        MenuItem settingsItem = menu.findItem(SETTINGS);

        if (service != null && service.isRunning()) {
            startItem.setEnabled(false);
            stopItem.setEnabled(true);
            settingsItem.setEnabled(false);
        } else {
            stopItem.setEnabled(false);
            startItem.setEnabled(true);
            settingsItem.setEnabled(true);
        }

        return true;
    }

    private void addTableRow(String id, String key, String val) {

        TableRow tr = new TableRow(this);
        MarginLayoutParams params = new ViewGroup.MarginLayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        params.setMargins(TABLE_ROW_MARGIN, TABLE_ROW_MARGIN, TABLE_ROW_MARGIN,
                TABLE_ROW_MARGIN);
        tr.setLayoutParams(params);

        TextView name = new TextView(this);
        name.setGravity(Gravity.RIGHT);
        name.setText(key + ": ");
        TextView value = new TextView(this);
        value.setGravity(Gravity.LEFT);
        value.setText(val);
        value.setTag(id);
        tr.addView(name);
        tr.addView(value);
        tl.addView(tr, params);
    }

    /**
     *
     */
    private void queueCommands() {
        if (isServiceBound) {
            for (ObdCommand Command : ObdConfig.getCommands()) {
                if (prefs.getBoolean(Command.getName(), true))
                    service.queueJob(new ObdCommandJob(Command));
            }
        }
    }

    private void doBindService() {
        if (!isServiceBound) {
            Log.d(TAG, "Binding OBD service..");
            if (preRequisites) {
                btStatusTextView.setText(getString(R.string.status_bluetooth_connecting));
                Intent serviceIntent = new Intent(this, ObdGatewayService.class);
                bindService(serviceIntent, serviceConn, Context.BIND_AUTO_CREATE);
            } else {
                btStatusTextView.setText(getString(R.string.status_bluetooth_disabled));
                Intent serviceIntent = new Intent(this, MockObdGatewayService.class);
                bindService(serviceIntent, serviceConn, Context.BIND_AUTO_CREATE);
            }
        }
    }

    private void doUnbindService() {
        if (isServiceBound) {
            if (service.isRunning()) {
                service.stopService();
                if (preRequisites)
                    btStatusTextView.setText(getString(R.string.status_bluetooth_ok));
            }
            Log.d(TAG, "Unbinding OBD service..");
            unbindService(serviceConn);
            isServiceBound = false;
            obdStatusTextView.setText(getString(R.string.status_obd_disconnected));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == Activity.RESULT_OK) {
                btStatusTextView.setText(getString(R.string.status_bluetooth_connected));
            } else {
                Toast.makeText(this, R.string.text_bluetooth_disabled, Toast.LENGTH_LONG).show();
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
}