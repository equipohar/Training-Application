package com.example.android.bluetoothlegatt;

import android.app.ActionBar;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.CompoundButton;
import android.widget.ExpandableListView;
import android.widget.ProgressBar;
import android.widget.SimpleExpandableListAdapter;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.StringTokenizer;

public class DatabaseActivity extends AppCompatActivity {

    private ProgressBar timeProgress;
    private TextView sensorView0, sensorView1, sensorView2, txtTimer;
    private TextView mConnectionState;
    private TextView mDataField;
    private String mDeviceName;
    private String mDeviceAddress;
    private BluetoothLeService mBluetoothLeService;
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics = new ArrayList<>();
    private boolean mConnected = false;
    private BluetoothGattCharacteristic mNotifyCharacteristic;

    private final String LIST_NAME = "NAME";
    private final String LIST_UUID = "UUID";

    private String stringData;

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
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_database);

        mDeviceName = "LilyPad HAR";
        mDeviceAddress = "F8:76:6C:D1:B2:1C";

        getSupportActionBar().setTitle(mDeviceName);
        getSupportActionBar().setDisplayOptions(ActionBar.DISPLAY_SHOW_HOME | ActionBar.DISPLAY_SHOW_TITLE);
        getSupportActionBar().setIcon(R.mipmap.ic_launcher);

        ((TextView) findViewById(R.id.device_address)).setText(mDeviceAddress);
        mConnectionState = findViewById(R.id.connection_state);
        mDataField =  findViewById(R.id.data_value);
        toggle =  findViewById(R.id.btnRecord);
        sensorView0 =  findViewById(R.id.textX);
        sensorView1 =  findViewById(R.id.textY);
        sensorView2 = findViewById(R.id.textZ);
        timeProgress = findViewById(R.id.determinateBar);
        txtTimer = findViewById(R.id.timerValue);

        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

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
                }
            }
        });

    }

    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e("TAG", "Unable to initialize Bluetooth");
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

                        String str1 =" X Axis: Acceleration = " + String.format("%.2f", numsensor0) + "G";
                        String str2 =" Y Axis: Acceleration = " + String.format("%.2f", numsensor1) + "G";
                        String str3 =" Z Axis: Acceleration = " + String.format("%.2f", numsensor2) + "G";

                        sensorView0.setText(str1);
                        sensorView1.setText(str2);
                        sensorView2.setText(str3);

                        if (status == 1) {
                            ValoresX.add(numsensor0);
                            ValoresY.add(numsensor1);
                            ValoresZ.add(numsensor2);

                            if(ValoresX.size() == 20){
                                if(status){
                                    new SendHttp().execute(createQuery(ValuesX,ValuesY,ValuesZ));
                                }
                                ValoresX.clear();
                                ValoresY.clear();
                                ValoresZ.clear();
                            }
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
            Log.d("TAG", "Connect request result=" + result);
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
        ArrayList<HashMap<String, String>> gattServiceData = new ArrayList<>();
        ArrayList<ArrayList<HashMap<String, String>>> gattCharacteristicData
                = new ArrayList<>();
        mGattCharacteristics = new ArrayList<>();

        for (BluetoothGattService gattService : gattServices) {
            uuid = gattService.getUuid().toString();
            if (SampleGattAttributes.lookup(uuid, unknownServiceString) != unknownServiceString) {
                HashMap<String, String> currentServiceData = new HashMap<>();
                currentServiceData.put(
                        LIST_NAME, SampleGattAttributes.lookup(uuid, unknownServiceString));
                currentServiceData.put(LIST_UUID, uuid);
                gattServiceData.add(currentServiceData);

                ArrayList<HashMap<String, String>> gattCharacteristicGroupData =
                        new ArrayList<>();
                List<BluetoothGattCharacteristic> gattCharacteristics =
                        gattService.getCharacteristics();
                ArrayList<BluetoothGattCharacteristic> charas =
                        new ArrayList<>();

                for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                    charas.add(gattCharacteristic);
                    HashMap<String, String> currentCharaData = new HashMap<>();
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

    private String createQuery(ArrayList<Float> valuesX, ArrayList<Float> valuesY, ArrayList<Float> valuesZ) {
        StringBuilder query = new StringBuilder();
        StringBuilder arrayX = new StringBuilder();
        StringBuilder arrayY = new StringBuilder();
        StringBuilder arrayZ = new StringBuilder();
        arrayX.append("[");
        arrayY.append("[");
        arrayZ.append("[");
        for (int i = 0; i < valuesX.size() ; i++) {
            arrayX.append(valuesX.get(i));
            arrayY.append(valuesY.get(i));
            arrayZ.append(valuesZ.get(i));

            arrayX.append(",");
            arrayY.append(",");
            arrayZ.append(",");
        }
        arrayX.setLength(arrayX.length()-1);
        arrayY.setLength(arrayY.length()-1);
        arrayZ.setLength(arrayZ.length()-1);
        arrayX.append("]");
        arrayY.append("]");
        arrayZ.append("]");

        query.append(getString(R.string.root));
        query.append("?value_x=");
        query.append(arrayX.toString());
        query.append("&value_y=");
        query.append(arrayY.toString());
        query.append("&value_z=");
        query.append(arrayZ.toString());
        query.append("&hr=");
        query.append(curHR);
        query.append("&idPersonal=%22");
        query.append(email);
        query.append("%22");

        return query.toString();
    }

    class SendHttp extends AsyncTask<String,Void,Void> {
        @Override
        public Void doInBackground(String...params){
            try{
                final RequestQueue requestQueue = Volley.newRequestQueue(getApplicationContext());
                StringRequest stringRequest = new StringRequest(
                        Request.Method.GET,
                        params[0],
                        new Response.Listener<String>() {
                            @Override
                            public void onResponse(String response) {
                            }
                        },
                        new Response.ErrorListener() {
                            @Override
                            public void onErrorResponse(VolleyError error) {
                                Log.v("TAG","Error");
                            }
                        }
                );
                requestQueue.add(stringRequest);
                Log.v("TAG",params[0]);
            }catch (Exception e){
                Log.v("TAG",e.getMessage());
            }
            return null;
        }
    }

}
