/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.bluetoothlegatt;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ExpandableListView;
import android.widget.ProgressBar;
import android.widget.SimpleExpandableListAdapter;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.StringTokenizer;

public class DeviceControlActivity extends Activity {
    private final static String TAG = DeviceControlActivity.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    private ProgressBar timeProgress;
    private TextView sensorView0, sensorView1, sensorView2, txtTimer;
    private TextView mConnectionState;
    private TextView mDataField;
    private String mDeviceName;
    private String mDeviceAddress;
    private BluetoothAdapter mBluetoothAdapter;
    private ExpandableListView mGattServicesList;
    private BluetoothLeService mBluetoothLeService;
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics =
            new ArrayList<ArrayList<BluetoothGattCharacteristic>>();
    private boolean mConnected = false;
    private BluetoothGattCharacteristic mNotifyCharacteristic;

    private final String LIST_NAME = "NAME";
    private final String LIST_UUID = "UUID";

    private String stringData;
    private String digits;
    private int orden = 1;

    private static final int REQUEST_ENABLE_BT = 1;

    private ArrayList<Float> ValoresX = new ArrayList<>();
    private ArrayList<Float> ValoresY = new ArrayList<>();
    private ArrayList<Float> ValoresZ = new ArrayList<>();

    private float numsensor0;
    private float numsensor1;
    private float numsensor2;

    ToggleButton toggle;
    private int status;
    private long startTime = 0L, timeInMilliseconds = 0L, timeSwapBuff = 0L, updateTime = 0L;
    private int startSecs = 0;

    private Handler customHandler = new Handler();

    private StringBuilder recDataString = new StringBuilder();

    private SharedPreferences sharedPref;
    private Float minX = 0f;
    private Float maxX = 0f;
    private Float minY = 0f;
    private Float maxY = 0f;
    private Float minZ = 0f;
    private Float maxZ = 0f;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.gatt_services_characteristics);

        final Intent intent = getIntent();
        mDeviceName = "LilyPad HAR";
        mDeviceAddress = "F8:76:6C:D1:B2:1C";

        // Sets up UI references.
        ((TextView) findViewById(R.id.device_address)).setText(mDeviceAddress);
        mConnectionState = (TextView) findViewById(R.id.connection_state);
        mDataField = (TextView) findViewById(R.id.data_value);
        toggle = (ToggleButton) findViewById(R.id.toggleButton);
        sensorView0 = (TextView) findViewById(R.id.textX);
        sensorView1 = (TextView) findViewById(R.id.textY);
        sensorView2 = (TextView) findViewById(R.id.textZ);
        timeProgress = (ProgressBar) findViewById(R.id.determinateBar);
        txtTimer = (TextView) findViewById(R.id.timerValue);


        status = 0;
        final AlertDialog.Builder alt_bld = new AlertDialog.Builder(DeviceControlActivity.this);
        alt_bld.setMessage("Do you wish to graph the accelerometer meseaurements?");
        alt_bld.setCancelable(false);
        alt_bld.setNegativeButton("No", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                ValoresX.clear();
                ValoresY.clear();
                ValoresZ.clear();
            }
        });
        alt_bld.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                Intent intent = new Intent(DeviceControlActivity.this, Graphs.class);
                intent.putExtra("Valores_de_X", ValoresX);
                intent.putExtra("Valores_de_Y", ValoresY);
                intent.putExtra("Valores_de_Z", ValoresZ);
                startActivity(intent);
                mBluetoothLeService.disconnect();
                ValoresX.clear();
                ValoresY.clear();
                ValoresZ.clear();
                finish();
            }
        });


        getActionBar().setTitle(mDeviceName);
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    status = 1;
                    startTime = 0L;
                    timeInMilliseconds = 0L;
                    timeSwapBuff = 0L;
                    updateTime = 0L;
                    timeProgress.setProgress(0);
                    startTime = SystemClock.uptimeMillis();
                    customHandler.postDelayed(updateTimerThread, 0);

                } else {
                    timeSwapBuff += timeInMilliseconds;
                    customHandler.removeCallbacks(updateTimerThread);
                    status = 0;
                    alt_bld.show();

                }
            }
        });

        sharedPref = this.getPreferences(Context.MODE_PRIVATE);

        if (sharedPref.getFloat("minX", -999) == -999) {
            Intent calibrate = new Intent(this, CalibrationActivity.class);
            startActivityForResult(calibrate,2);
        } else {
            minX = sharedPref.getFloat("minX", -999);
            maxX = sharedPref.getFloat("maxX", -999);
            minY = sharedPref.getFloat("minY", -999);
            maxY = sharedPref.getFloat("maxY", -999);
            minZ = sharedPref.getFloat("minZ", -999);
            maxZ = sharedPref.getFloat("maxZ", -999);
        }
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }

        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        if (!mBluetoothAdapter.isEnabled()) {
            if (!mBluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        }
    }


    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                updateConnectionState(R.string.connected);
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                updateConnectionState(R.string.disconnected);
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                displayGattServices(mBluetoothLeService.getSupportedGattServices());
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                stringData = intent.getStringExtra(BluetoothLeService.EXTRA_DATA);
                displayData(stringData);
                String readMessage = stringData;
                recDataString.append(readMessage);
                int endOfLineIndex = recDataString.indexOf("~");
                if (endOfLineIndex > 0) {
                    String dataInPrint = recDataString.substring(0, endOfLineIndex);
                    int dataLength = dataInPrint.length();


                    if (recDataString.charAt(0) == '#') {
                        StringTokenizer tokens = new StringTokenizer(dataInPrint, "+");
                        String sensor0 = tokens.nextToken().replace("#", "");
                        String sensor1 = tokens.nextToken();
                        String sensor2 = tokens.nextToken();

                        numsensor0 = standerdize(Float.parseFloat(sensor0), minX, maxX);  // Pin X
                        numsensor1 = standerdize(Float.parseFloat(sensor1), minY, maxY);  // Pin Y
                        numsensor2 = standerdize(Float.parseFloat(sensor2), minZ, maxZ);  // Pin Z


                        sensorView0.setText(" X Axis: Acceleration = " + String.format("%.2f", numsensor0) + "G");
                        sensorView1.setText(" Y Axis: Acceleration = " + String.format("%.2f", numsensor1) + "G");
                        sensorView2.setText(" Z Axis: Acceleration = " + String.format("%.2f", numsensor2) + "G");

                        if (status == 1) {
                            System.out.println("Estoy entrando al if de status");
                            ValoresX.add(numsensor0);
                            ValoresY.add(numsensor1);
                            ValoresZ.add(numsensor2);
                            Log.v("TAG","Size: "+ValoresX.size());

                        }


                    }
                    recDataString.delete(0, recDataString.length());


                }
            }
        }
    };


    public void startService() {
        if (mGattCharacteristics != null) {
            final BluetoothGattCharacteristic characteristic =
                    mGattCharacteristics.get(0).get(0);
            final int charaProp = characteristic.getProperties();
            if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
                if (mNotifyCharacteristic != null) {
                    mBluetoothLeService.setCharacteristicNotification(
                            mNotifyCharacteristic, false);
                    mNotifyCharacteristic = null;
                }
                mBluetoothLeService.readCharacteristic(characteristic);
            }
            if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                mNotifyCharacteristic = characteristic;
                mBluetoothLeService.setCharacteristicNotification(
                        characteristic, true);
            }
        }
    }


    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.gatt_services, menu);
        if (mConnected) {
            menu.findItem(R.id.menu_connect).setVisible(false);
            menu.findItem(R.id.menu_disconnect).setVisible(true);
        } else {
            menu.findItem(R.id.menu_connect).setVisible(true);
            menu.findItem(R.id.menu_disconnect).setVisible(false);
        }
        menu.findItem(R.id.menu_calibrate).setVisible(true);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_connect:
                mBluetoothLeService.connect(mDeviceAddress);
                return true;
            case R.id.menu_disconnect:
                mBluetoothLeService.disconnect();
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
            case R.id.menu_calibrate:
                Intent calibrate = new Intent(this, CalibrationActivity.class);
                startActivityForResult(calibrate, 2);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mConnectionState.setText(resourceId);
            }
        });
    }

    private void displayData(String data) {
        if (data != null) {
            mDataField.setText(data);
        }
    }

    private void displayGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null) return;
        String uuid = null;
        String unknownServiceString = getResources().getString(R.string.unknown_service);
        String unknownCharaString = getResources().getString(R.string.unknown_characteristic);
        ArrayList<HashMap<String, String>> gattServiceData = new ArrayList<HashMap<String, String>>();
        ArrayList<ArrayList<HashMap<String, String>>> gattCharacteristicData
                = new ArrayList<ArrayList<HashMap<String, String>>>();
        mGattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();

        for (BluetoothGattService gattService : gattServices) {
            uuid = gattService.getUuid().toString();
            if (SampleGattAttributes.lookup(uuid, unknownServiceString) != unknownServiceString) {
                HashMap<String, String> currentServiceData = new HashMap<String, String>();
                currentServiceData.put(
                        LIST_NAME, SampleGattAttributes.lookup(uuid, unknownServiceString));
                currentServiceData.put(LIST_UUID, uuid);
                gattServiceData.add(currentServiceData);

                ArrayList<HashMap<String, String>> gattCharacteristicGroupData =
                        new ArrayList<HashMap<String, String>>();
                List<BluetoothGattCharacteristic> gattCharacteristics =
                        gattService.getCharacteristics();
                ArrayList<BluetoothGattCharacteristic> charas =
                        new ArrayList<BluetoothGattCharacteristic>();

                for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                    charas.add(gattCharacteristic);
                    HashMap<String, String> currentCharaData = new HashMap<String, String>();
                    uuid = gattCharacteristic.getUuid().toString();
                    currentCharaData.put(
                            LIST_NAME, SampleGattAttributes.lookup(uuid, unknownCharaString));
                    currentCharaData.put(LIST_UUID, uuid);
                    if (SampleGattAttributes.lookup(uuid, unknownCharaString) != unknownCharaString) {
                        gattCharacteristicGroupData.add(currentCharaData);
                    }
                }
                mGattCharacteristics.add(charas);
                gattCharacteristicData.add(gattCharacteristicGroupData);
            }
        }

        SimpleExpandableListAdapter gattServiceAdapter = new SimpleExpandableListAdapter(
                this,
                gattServiceData,
                android.R.layout.simple_expandable_list_item_2,
                new String[]{LIST_NAME, LIST_UUID},
                new int[]{android.R.id.text1, android.R.id.text2},
                gattCharacteristicData,
                android.R.layout.simple_expandable_list_item_2,
                new String[]{LIST_NAME, LIST_UUID},
                new int[]{android.R.id.text1, android.R.id.text2}
        );
        startService();
        try {
            Thread.sleep(500);
        } catch (InterruptedException ex) {
            android.util.Log.d("Hello: ", ex.toString());
        }
        startService();
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    public float standerdize(float x, float in_min, float in_max) {
        float out_min = -1;
        float out_max = 1;
        return ((x - in_min) * (out_max - out_min) / (in_max - in_min) + out_min);
    }

    Runnable updateTimerThread = new Runnable() {
        @Override
        public void run() {
            timeInMilliseconds = SystemClock.uptimeMillis() - startTime;
            updateTime = timeSwapBuff + timeInMilliseconds;
            int secs = (int) (updateTime / 1000);
            int mins = secs / 60;
            secs %= 60;
            int milliseconds = (int) (updateTime % 1000);
            txtTimer.setText("" + mins + ":" + String.format("%02d", secs) + ":"
                    + String.format("%03d", milliseconds));
            customHandler.postDelayed(this, 0);
            if ((secs == startSecs + 1)) {
                System.out.println("Seconds: " + String.valueOf(secs));
                startSecs = secs;
                if (secs == 59) {
                    startSecs = 0;
                    timeProgress.incrementProgressBy(1);
                }
                timeProgress.incrementProgressBy(1);
            }
        }
    };

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 2) {
            if (resultCode == RESULT_OK) {
                minX = data.getFloatExtra("minX",0f);
                maxX = data.getFloatExtra("maxX",0f);
                minY = data.getFloatExtra("minY",0f);
                maxY = data.getFloatExtra("maxY",0f);
                minZ = data.getFloatExtra("minZ",0f);
                maxZ = data.getFloatExtra("maxZ",0f);
                SharedPreferences.Editor editor = sharedPref.edit();
                editor.putFloat("minX",minX);
                editor.putFloat("maxX",maxX);
                editor.putFloat("minY",minY);
                editor.putFloat("maxY",maxY);
                editor.putFloat("minZ",minZ);
                editor.putFloat("maxZ",maxZ);
                editor.apply();
            }
        }
    }
}
