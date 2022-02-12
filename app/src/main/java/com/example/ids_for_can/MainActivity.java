package com.example.ids_for_can;

import static android.content.ContentValues.TAG;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;

import android.text.InputType;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewGroup.MarginLayoutParams;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.github.pires.obd.commands.MonitorAllCommand;
import com.github.pires.obd.commands.ObdCommand;
import com.github.pires.obd.commands.protocol.EchoOffCommand;
import com.github.pires.obd.commands.protocol.HeadersOnCommand;
import com.github.pires.obd.commands.protocol.LineFeedOnCommand;
import com.github.pires.obd.commands.protocol.ObdWarmStartCommand;
import com.github.pires.obd.commands.protocol.SelectProtocolCommand;
import com.github.pires.obd.commands.protocol.SpacesOnCommand;
import com.github.pires.obd.enums.AvailableCommandNames;
import com.example.ids_for_can.connectivity.ObdConfig;
import com.example.ids_for_can.connectivity.AbstractGatewayService;
import com.example.ids_for_can.connectivity.MockObdGatewayService;
import com.example.ids_for_can.connectivity.ObdCommandJob;
import com.example.ids_for_can.connectivity.ObdGatewayService;
import com.example.ids_for_can.connectivity.ObdProgressListener;
import com.github.pires.obd.enums.ObdProtocols;
import com.google.inject.Inject;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
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
    private static final int TRAIN_IDS = 3;
    private static final int RETRAIN_IDS = 4;
    private static final int START_IDS = 5;
    private static final int STOP_LIVE_DATA_OR_IDS = 6;
    private static final int START_LOGGING = 7;
    private static final int SETTINGS = 8;
    private static final int QUIT_APPLICATION = 9;
    private static final int TABLE_ROW_MARGIN = 7;
    private static final int REQUEST_ENABLE_BT = 1234;
    private static boolean bluetoothDefaultIsEnable = false;

    // This variable runs the commands needed to train or start the IDS
    // Without this variable, the app runs the commands needed to fetch diagnostic data
    private static boolean initIDSDone = false;

    // This variable determines if the IDS has been trained on the current vehicle
    public static boolean trainingComplete = false;

    // This variable is a counter for the training data
    public static int trainingCounter = 0;

    // This variable is a threshold for the training data
    // When the trainingCounter reaches this threshold, we have sufficient data to create the matrix
    // About 10 CAN messages are captured in one 'AT MA' command,
    // so the threshold is 10x the value of this variable
    public static int trainingThreshold = 3;

    // The IDS is currently training/re-training
    public static boolean IDSTrainOrRetrain = false;

    // The IDS is explicitly re-training (update the old matrix/profile)
    public static boolean retrainONLY = false;

    // The IDS is currently running
    public static boolean IDSOn = false;

    // Logging to external storage is enabled
    public static boolean loggingOn = false;

    // Default name is "My Vehicle"
    private String userText = "My Vehicle";

    // Waiting on user input
    public static boolean waitingOnUser = false;

    public static String[] ATMAOrder = null;
    public static boolean profileMatrix[][] = null;

    private static final int PERMISSIONS_REQUEST_BLUETOOTH = 1;
    private static final int PERMISSIONS_REQUEST_READ_AND_WRITE_EXTERNAL_STORAGE = 2;

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

    private final Runnable mMonitorAllCommands = new Runnable() {
        public void run() {
            if (service != null && service.isRunning() && service.queueEmpty()) {
                monitorAllCommands();
                commandResult.clear();
            }
            // run again in period defined in preferences
            new Handler().postDelayed(mMonitorAllCommands, ConfigActivity.getObdUpdatePeriod(prefs));
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
                initIDSDone = false;
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
        String rawCmdResult = "";
        final String cmdID = LookUpCommand(cmdName);

        if (job.getState().equals(ObdCommandJob.ObdCommandJobState.EXECUTION_ERROR)) {
            cmdResult = job.getCommand().getResult();
            if (cmdResult != null && isServiceBound && !cmdName.equals("Monitor all")) {
                obdStatusTextView.setText(cmdResult.toLowerCase());
            } else {
                if (IDSTrainOrRetrain) {
                    obdStatusTextView.setText(getString(R.string.ids_training));
                } else if (IDSOn) {
                    obdStatusTextView.setText(getString(R.string.ids_active));
                } else {
                    obdStatusTextView.setText(getString(R.string.unknown_state));
                }
            }
        } else if (job.getState().equals(ObdCommandJob.ObdCommandJobState.BROKEN_PIPE)) {
            if (isServiceBound)
                stopLiveData();
        } else if (job.getState().equals(ObdCommandJob.ObdCommandJobState.NOT_SUPPORTED)) {
            cmdResult = getString(R.string.status_obd_no_support);
        } else {
            cmdResult = job.getCommand().getFormattedResult();
            rawCmdResult = job.getCommand().getResult();
            if(isServiceBound)
                obdStatusTextView.setText(getString(R.string.status_obd_data));
        }

        if (vv.findViewWithTag(cmdID) != null) {
            TextView existingTV = (TextView) vv.findViewWithTag(cmdID);
            existingTV.setText(cmdResult);
        } else addTableRow(cmdID, cmdName, cmdResult);
        commandResult.put(cmdID, cmdResult);

        Log.d(TAG, "cmdID: " + cmdID + ", cmdResult: " + cmdResult);
        Log.d(TAG, "cmdID: " + cmdID + ", rawCmdResult: " + rawCmdResult);
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

        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "READ_AND_WRITE_EXTERNAL_STORAGE PERMISSION GRANTED!");
        } else {
            // You can directly ask for the permission.
            // The registered ActivityResultCallback gets the result of this request.
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.READ_EXTERNAL_STORAGE},
                    PERMISSIONS_REQUEST_READ_AND_WRITE_EXTERNAL_STORAGE);
            Log.d(TAG, "READ_AND_WRITE_EXTERNAL_STORAGE PERMISSION REQUESTED!");
        }
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
            case PERMISSIONS_REQUEST_READ_AND_WRITE_EXTERNAL_STORAGE:
                if (grantResults.length > 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED
                        && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "READ_AND_WRITE_EXTERNAL_STORAGE GRANTED");
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
        IDSOn = false;
        IDSTrainOrRetrain = false;
        trainingComplete = false;
        trainingCounter = 0;
        loggingOn = false;

        if (isServiceBound) {
            //we don't want to unbind the service
            //we want the IDS to continue receiving and processing data
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
        menu.add(0, TRAIN_IDS, 0, getString(R.string.train_ids));
        menu.add(0, RETRAIN_IDS, 0, getString(R.string.re_train_ids));
        menu.add(0, START_IDS, 0, getString(R.string.start_ids));
        menu.add(0, STOP_LIVE_DATA_OR_IDS, 0, getString(R.string.stop_live_data_ids));
        menu.add(0, START_LOGGING, 0, getString(R.string.start_logging));
        menu.add(0, SETTINGS, 0, getString(R.string.menu_settings));
        menu.add(0, QUIT_APPLICATION, 0, getString(R.string.quit_application));
        Log.d(TAG, "Creating menu...");
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        //sendNotification();
        switch (item.getItemId()) {
            case START_LIVE_DATA:
                startLiveData();
                return true;
            case TRAIN_IDS:
                Log.d(TAG, "Train IDS");
                retrainONLY = false;
                trainIDS();
                return true;
            case RETRAIN_IDS:
                Log.d(TAG, "Re-train IDS");
                retrainONLY = true;
                trainIDS();
                return true;
            case START_IDS:
                startIDS();
                return true;
            case STOP_LIVE_DATA_OR_IDS:
                stopLiveData();
                return true;
            case START_LOGGING:
                startLogging();
                return true;
            case SETTINGS:
                updateConfig();
                return true;
            case QUIT_APPLICATION:
                finishAndRemoveTask();
                return true;
            default:
                return false;
        }
    }

    private void startLiveData() {
        Log.d(TAG, "Starting live data...");

        tl.removeAllViews(); //start fresh
        doBindService();

        // start command execution
        new Handler().post(mQueueCommands);
    }

    private void trainIDS() {
        Log.d(TAG, "Training IDS...");
        IDSTrainOrRetrain = true;
        trainingComplete = false;
        trainingCounter = 0;

        tl.removeAllViews(); //start fresh
        doBindService();

        // start command execution
        new Handler().post(mMonitorAllCommands);
    }

    private void startIDS() {
        Log.d(TAG, "Starting IDS...");
        IDSOn = true;

        tl.removeAllViews(); //start fresh
        doBindService();

        // start command execution
        new Handler().post(mMonitorAllCommands);
    }

    private void stopLiveData() {
        Log.d(TAG, "Stopping live data...");
        initIDSDone = false;
        IDSOn = false;
        IDSTrainOrRetrain = false;
        trainingCounter = 0;

        doUnbindService();
    }

    private void startLogging() {
        Log.d(TAG, "Starting logging...");

        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "READ_AND_WRITE_EXTERNAL_STORAGE PERMISSION GRANTED!");
        } else {
            // You can directly ask for the permission.
            // The registered ActivityResultCallback gets the result of this request.
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.READ_EXTERNAL_STORAGE},
                    PERMISSIONS_REQUEST_READ_AND_WRITE_EXTERNAL_STORAGE);
            Log.d(TAG, "READ_AND_WRITE_EXTERNAL_STORAGE PERMISSION REQUESTED!");
        }

        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "LOGGING ON!");
            loggingOn = true;
        }
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
        MenuItem trainItem = menu.findItem(TRAIN_IDS);
        MenuItem retrainItem = menu.findItem(RETRAIN_IDS);
        MenuItem idsItem = menu.findItem(START_IDS);
        MenuItem stopItem = menu.findItem(STOP_LIVE_DATA_OR_IDS);
        MenuItem loggingItem = menu.findItem(START_LOGGING);
        MenuItem settingsItem = menu.findItem(SETTINGS);

        if (service != null && service.isRunning()) {
            startItem.setEnabled(false);
            trainItem.setEnabled(false);
            retrainItem.setEnabled(false);
            idsItem.setEnabled(false);
            stopItem.setEnabled(true);
            settingsItem.setEnabled(false);
        } else {
            stopItem.setEnabled(false);
            trainItem.setEnabled(true);
            startItem.setEnabled(true);
            if (trainingComplete) {
                retrainItem.setEnabled(true);
                idsItem.setEnabled(true);
            } else {
                retrainItem.setEnabled(false);
                idsItem.setEnabled(false);
            }
            settingsItem.setEnabled(true);
        }

        if (loggingOn) {
            loggingItem.setEnabled(false);
        } else {
            loggingItem.setEnabled(true);
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

    private void monitorAllCommands() {
        if (isServiceBound && !waitingOnUser) {
            if (!initIDSDone) {
                service.queueJob(new ObdCommandJob(new ObdWarmStartCommand()));
                service.queueJob(new ObdCommandJob(new LineFeedOnCommand()));
                service.queueJob(new ObdCommandJob(new EchoOffCommand()));
                service.queueJob(new ObdCommandJob(new SpacesOnCommand()));
                service.queueJob(new ObdCommandJob(new HeadersOnCommand()));
                service.queueJob(new ObdCommandJob(new SelectProtocolCommand(ObdProtocols.ISO_15765_4_CAN)));
                initIDSDone = true;
            }
            service.queueJob(new ObdCommandJob(new MonitorAllCommand()));
            trainingCounter++;
            Log.d(TAG, "trainingCounter: " + trainingCounter);

            // We are in training mode, and we have sufficient data to create the matrix
            if (IDSTrainOrRetrain && trainingCounter >= trainingThreshold) {
                createMatrix();
            }

            if (IDSOn) {
                idsDetect();
            }

            if (ObdCommand.ATMAMap.containsKey("ELM327") && ObdCommand.ATMAMap.get("ELM327").contains("v1.5")) {
                sendNotification();
                ObdCommand.ATMAMap.remove("ELM327");
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

    public void createMatrix() {
        // Create the matrix/profile for this vehicle, which enables the IDS to function

        // TODO: Create and store the matrix/profile for this vehicle

        // We need to associate the vehicle nickname with the matrix/profile,
        // so that we can check if the matrix/profile exists to determine if we should allow "Start IDS" or not

//        ATMAOrder = new String[2];
//        ATMAOrder[0] = "XY1";
//        ATMAOrder[0] = "VW2";
//
//        profileMatrix = new boolean[2][2];
//        profileMatrix[0][0] = true;
//        profileMatrix[1][1] = true;

        ATMAOrder = new String[3];
        ATMAOrder[0] = "AB1";
        ATMAOrder[1] = "CD2";
        ATMAOrder[2] = "EF3";

        profileMatrix = new boolean[3][3];
        profileMatrix[0][0] = true;
        profileMatrix[2][2] = true;

        Log.d(TAG, "ATMAOrder: " + ATMAOrder);
        Log.d(TAG, "profileMatrix: " + profileMatrix);

        Log.d(TAG, "Creating the matrix...");
        Log.d(TAG, ObdCommand.ATMATrace.toString());

        // Now that we have a matrix/profile for this vehicle, we need to save this vehicle as an option in preferences
        Log.d(TAG, "Saving the vehicle in preferences...");

        if (!retrainONLY) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Enter a nickname for this vehicle: ");
            EditText input = new EditText(this);
            input.setInputType(InputType.TYPE_CLASS_TEXT);
            builder.setView(input);

            builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    userText = input.getText().toString();
                    try {
                        appendToSharedPreferences();
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            });
            builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.cancel();
                    try {
                        appendToSharedPreferences();
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            });

            waitingOnUser = true;
            Log.d(TAG, "waitingOnUser: " + waitingOnUser);
            builder.show();
        } else {
            try {
                updateSharedPreferences();
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    public void appendToSharedPreferences() throws JSONException {
        Log.d(TAG, "Appending to SharedPreferences...");

        SharedPreferences vehiclePreference = getApplicationContext().getSharedPreferences("VEHICLE_PREFERENCE", MODE_MULTI_PROCESS);

        // Retrieve the HashSet of all vehicles
        HashSet<String> all_vehicles = new HashSet<>();
        if (vehiclePreference.contains("ALL_VEHICLES")) {
            all_vehicles = new HashSet<>(vehiclePreference.getStringSet("ALL_VEHICLES", new HashSet<>()));
        } else {
            //all_vehicles.add("NEW");
        }
        Log.d(TAG, "all_vehicles: " + all_vehicles);

        // Retrieve the string that represents the "profiles" JSON object
        String jsonString = null;
        if (vehiclePreference.contains("PROFILES")) {
            jsonString = vehiclePreference.getString("PROFILES", new String());
        }
        Log.d(TAG, "jsonString: " + jsonString);

        JSONArray profiles = null;
        if (jsonString != null) {
            profiles = new JSONArray(jsonString);
        }
        Log.d(TAG, "profiles: " + profiles);

        if (profiles == null) {
            profiles = new JSONArray();
        }

        // We need to enforce unique names (we have a HashSet).
        // So if the name exists in the HashSet, we append a number.
        // If the name still exists in the HashSet, we increment the number and append.
        // On and on, until we find a name that is not in the HashSet.

        if (userText.isEmpty()) {
            userText = "My Vehicle";
        }

        String newUserText = userText;
        int counter = 1;
        while (all_vehicles.contains(newUserText)) {
            newUserText = userText + " (" + counter + ")";
            counter++;
        }

        all_vehicles.add(newUserText);
        String selected_vehicle = newUserText;

        // Create the JSON for the new vehicle matrix/profile
        JSONObject newProfile = new JSONObject();
        newProfile.put("profileName", newUserText);

        JSONArray orderArray = new JSONArray(ATMAOrder);
        //JSONArray orderArray = new JSONArray(Arrays.asList(ATMAOrder));
        newProfile.put("order", orderArray);

        JSONArray parentArray = new JSONArray();
        // loop by row, then by column
        for (int i = 0;  i < profileMatrix.length; i++){
            JSONArray childArray = new JSONArray();
            for (int j = 0; j < profileMatrix[i].length; j++){
                childArray.put(profileMatrix[i][j]);
            }
            parentArray.put(childArray);
        }

        newProfile.put("matrix", parentArray);
        Log.d(TAG, "newProfile: " + newProfile);

        // Add the new vehicle matrix/profile to the "profiles" JSON object
        profiles.put(newProfile);
        String updatedJSONData = profiles.toString();
        Log.d(TAG, "updatedJSONData: " + updatedJSONData);

        SharedPreferences.Editor editor = vehiclePreference.edit();
        //editor.clear();
        editor.putStringSet("ALL_VEHICLES", all_vehicles);
        editor.putString("SELECTED_VEHICLE", selected_vehicle);
        editor.putString("PROFILES", updatedJSONData);
        boolean commitResult = editor.commit();
        Log.d(TAG, "commitResult: " + commitResult);

        // START - VALIDATION CODE
        // **
        HashSet<String> resultHashSet = new HashSet<>(vehiclePreference.getStringSet("ALL_VEHICLES", new HashSet<>()));
        Log.d(TAG, "resultHashSet: " + resultHashSet);

        String resultString = vehiclePreference.getString("SELECTED_VEHICLE", new String());
        Log.d(TAG, "resultString: " + resultString);

        String resultJSON = vehiclePreference.getString("PROFILES", new String());
        Log.d(TAG, "resultJSON: " + resultJSON);

        JSONArray storedJSON = new JSONArray(resultJSON);
        Log.d(TAG, "storedJSON: " + storedJSON);

        JSONObject obj = null;
        for (int i = 0; i < storedJSON.length(); i++) {
            JSONObject temp_obj = storedJSON.getJSONObject(i);
            Log.d(TAG, "temp_obj: " + temp_obj);
            if (temp_obj.get("profileName").equals(selected_vehicle)) {
                obj = temp_obj;
                Log.d(TAG, "MATCH!  obj: " + obj);
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

        if (recoveredOrder != null) {
            Log.d(TAG, "Printing recoveredOrder...");
            Log.d(TAG, Arrays.toString(recoveredOrder));
        }

        if (recoveredMatrix != null) {
            Log.d(TAG, "Printing recoveredMatrix...");
            for (boolean[] arr : recoveredMatrix) {
                Log.d(TAG, Arrays.toString(arr));
            }
        }
        // **
        // END - VALIDATION CODE

        Log.d(TAG, "Training complete, starting IDS...");

        trainingComplete = true;
        IDSTrainOrRetrain = false;
        IDSOn = true;
        waitingOnUser = false;
    }

    public void updateSharedPreferences() throws JSONException {
        Log.d(TAG, "Updating SharedPreferences...");

        SharedPreferences vehiclePreference = getApplicationContext().getSharedPreferences("VEHICLE_PREFERENCE", MODE_MULTI_PROCESS);

        // Retrieve the HashSet of all vehicles
        HashSet<String> all_vehicles = new HashSet<>();
        if (vehiclePreference.contains("ALL_VEHICLES")) {
            all_vehicles = new HashSet<>(vehiclePreference.getStringSet("ALL_VEHICLES", new HashSet<>()));
        } else {
            //all_vehicles.add("NEW");
        }
        Log.d(TAG, "all_vehicles: " + all_vehicles);

        // Retrieve the String of the selected vehicle
        String selected_vehicle = null;
        if (vehiclePreference.contains("SELECTED_VEHICLE")) {
            selected_vehicle = vehiclePreference.getString("SELECTED_VEHICLE", new String());
        }
        Log.d(TAG, "selected_vehicle: " + selected_vehicle);

        // Retrieve the string that represents the "profiles" JSON object
        String jsonString = null;
        if (vehiclePreference.contains("PROFILES")) {
            jsonString = vehiclePreference.getString("PROFILES", new String());
        }
        Log.d(TAG, "jsonString: " + jsonString);

        JSONArray profiles = null;
        if (jsonString != null) {
            profiles = new JSONArray(jsonString);
        }
        Log.d(TAG, "profiles: " + profiles);

        if (profiles == null) {
            // If we don't have a pre-existing matrix/profile,
            // then we need to add to the shared preferences
            // because there is no existing matrix/profile to update
            appendToSharedPreferences();
        }

        JSONObject profileToUpdate = null;
        for (int i = 0; i < profiles.length(); i++) {
            JSONObject temp_obj = profiles.getJSONObject(i);
            Log.d(TAG, "temp_obj: " + temp_obj);
            if (temp_obj.get("profileName").equals(selected_vehicle)) {
                profileToUpdate = temp_obj;
                Log.d(TAG, "MATCH!  obj: " + profileToUpdate);
                break;
            }
        }

        if (profileToUpdate == null) {
            // If we don't have a pre-existing matrix/profile,
            // then we need to add to the shared preferences
            // because there is no existing matrix/profile to update
            appendToSharedPreferences();
        }

        JSONArray orderArray = new JSONArray(ATMAOrder);
        //JSONArray orderArray = new JSONArray(Arrays.asList(ATMAOrder));
        profileToUpdate.put("order", orderArray);

        JSONArray parentArray = new JSONArray();
        // loop by row, then by column
        for (int i = 0;  i < profileMatrix.length; i++){
            JSONArray childArray = new JSONArray();
            for (int j = 0; j < profileMatrix[i].length; j++){
                childArray.put(profileMatrix[i][j]);
            }
            parentArray.put(childArray);
        }

        profileToUpdate.put("matrix", parentArray);
        Log.d(TAG, "profileToUpdate: " + profileToUpdate);

        String updatedJSONData = profiles.toString();
        Log.d(TAG, "updatedJSONData: " + updatedJSONData);

        SharedPreferences.Editor editor = vehiclePreference.edit();
        //editor.clear();
        editor.putStringSet("ALL_VEHICLES", all_vehicles);
        editor.putString("SELECTED_VEHICLE", selected_vehicle);
        editor.putString("PROFILES", updatedJSONData);
        boolean commitResult = editor.commit();
        Log.d(TAG, "commitResult: " + commitResult);

        // START - VALIDATION CODE
        // **
        HashSet<String> resultHashSet = new HashSet<>(vehiclePreference.getStringSet("ALL_VEHICLES", new HashSet<>()));
        Log.d(TAG, "resultHashSet: " + resultHashSet);

        String resultString = vehiclePreference.getString("SELECTED_VEHICLE", new String());
        Log.d(TAG, "resultString: " + resultString);

        String resultJSON = vehiclePreference.getString("PROFILES", new String());
        Log.d(TAG, "resultJSON: " + resultJSON);

        JSONArray storedJSON = new JSONArray(resultJSON);
        Log.d(TAG, "storedJSON: " + storedJSON);

        JSONObject obj = null;
        for (int i = 0; i < storedJSON.length(); i++) {
            JSONObject temp_obj = storedJSON.getJSONObject(i);
            Log.d(TAG, "temp_obj: " + temp_obj);
            if (temp_obj.get("profileName").equals(selected_vehicle)) {
                obj = temp_obj;
                Log.d(TAG, "MATCH!  obj: " + obj);
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

        if (recoveredOrder != null) {
            Log.d(TAG, "Printing recoveredOrder...");
            Log.d(TAG, Arrays.toString(recoveredOrder));
        }

        if (recoveredMatrix != null) {
            Log.d(TAG, "Printing recoveredMatrix...");
            for (boolean[] arr : recoveredMatrix) {
                Log.d(TAG, Arrays.toString(arr));
            }
        }
        // **
        // END - VALIDATION CODE

        Log.d(TAG, "Training complete, starting IDS...");

        trainingComplete = true;
        IDSTrainOrRetrain = false;
        IDSOn = true;
        waitingOnUser = false;
    }

    public void idsDetect() {
        // Use the matrix/profile to check current traffic,
        // update false positives,
        // and raise alerts

        if (ObdCommand.currentIDs.isEmpty()) {
            // No data received; no alert
            return;
        }

        if (false) {
            sendNotification();
        }
    }

    public void sendNotification() {
        Log.d(TAG, "Sending notification...");

        //Get an instance of NotificationManager
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.scribble)
                .setContentTitle("ALERT: Potential attack detected!")
                .setContentText("Suspicious traffic has been detected, indicative of a potential attack.")
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText("Suspicious traffic has been detected, indicative of a potential attack."))
                        //.bigText("Detail of the suspicious traffic and possibility of an an attack."))
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        // Gets an instance of the NotificationManager service

        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d(TAG, "Creating notification channel...");
            CharSequence name = "IDS ALERTS";
            String description = "Alerts from the intrusion detection system (IDS)";
            NotificationChannel channel = new NotificationChannel("001", name, NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription(description);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            mNotificationManager.createNotificationChannel(channel);
            mBuilder.setChannelId("001");
        }

        // When you issue multiple notifications about the same type of event,
        // it’s best practice for your app to try to update an existing notification
        // with this new information, rather than immediately creating a new notification.
        // If you want to update this notification at a later date, you need to assign it an ID.
        // You can then use this ID whenever you issue a subsequent notification.
        // If the previous notification is still visible, the system will update this existing notification,
        // rather than create a new one. In this example, the notification’s ID is 001

        mNotificationManager.notify(001, mBuilder.build());
        Log.d(TAG, "Notification sent...");
    }
}