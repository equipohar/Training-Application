package com.example.android.bluetoothlegatt;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
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
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SimpleExpandableListAdapter;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.StringTokenizer;

public class CalibrationActivity extends Activity {

    private final static String TAG = DeviceControlActivity.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    private TextView sensorView;
    private TextView tvOnAxis,tvMinX,tvMaxX,tvMinY,tvMaxY,tvMinZ,tvMaxZ;
    private Button btnNext,btnPrev,btnOk;
    private String mDeviceName;
    private String mDeviceAddress;
    private ExpandableListView mGattServicesList;
    private BluetoothLeService mBluetoothLeService;
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics =
            new ArrayList<ArrayList<BluetoothGattCharacteristic>>();
    private boolean mConnected = false;
    private BluetoothGattCharacteristic mNotifyCharacteristic;

    private ImageView img;

    private final String LIST_NAME = "NAME";
    private final String LIST_UUID = "UUID";

    private String stringData;
    private String digits;
    private int orden = 1;

    private float numsensor;

    private int curBtn = 1;

    private Handler customHandler = new Handler();
    private StringBuilder recDataString = new StringBuilder();
    private SharedPreferences sharedPref;
    private Float minX=0f;
    private Float maxX=0f;
    private Float minY=0f;
    private Float maxY=0f;
    private Float minZ=0f;
    private Float maxZ=0f;
    private String handle;

    private String textMin,textMax;
    private AlertDialog alert,alertDone;
    private  ProgressDialog waitDialog;
    private Switch switchMode;
    private boolean switchBol;
    private ArrayList<Float> buffer = new ArrayList<>();
    private int counter=0;
    private Thread threadMin,threadMax;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calibration);

        mDeviceName = "LilyPad HAR";
        mDeviceAddress = "F8:76:6C:D1:B2:1C";

        tvOnAxis = (TextView) findViewById(R.id.tvCurVal);
        sensorView = (TextView) findViewById(R.id.tvAxis);
        btnNext = (Button) findViewById(R.id.btnNext);
        btnPrev = (Button) findViewById(R.id.btnPre);
        btnOk = (Button) findViewById(R.id.btnOk);
        tvMaxX = (TextView) findViewById(R.id.tvXMax);
        tvMinX = (TextView) findViewById(R.id.tvXMin);
        tvMaxY = (TextView) findViewById(R.id.tvYMax);
        tvMinY = (TextView) findViewById(R.id.tvYMin);
        tvMaxZ = (TextView) findViewById(R.id.tvZMax);
        tvMinZ = (TextView) findViewById(R.id.tvZMin);

        textMax = getResources().getString(R.string.textMax);
        textMin = getResources().getString(R.string.textMin);

        img = (ImageView) findViewById(R.id.imageView);
        img.setImageResource(R.drawable.minx720);

        getActionBar().setTitle("");
        getActionBar().setDisplayHomeAsUpEnabled(true);
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        AlertDialog.Builder builder = new AlertDialog.Builder(CalibrationActivity.this);
        builder.setTitle("One or more values has not been confirmed.");
        builder.setNeutralButton("Ok", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

            }
        });
        alert = builder.create();

        AlertDialog.Builder builderDone = new AlertDialog.Builder(CalibrationActivity.this);
        builderDone.setTitle("All values have been confirmed");
        builderDone.setMessage("Do you wish to return to training?");
        builderDone.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Intent returnIntent = new Intent();
                returnIntent.putExtra("minX", minX);
                returnIntent.putExtra("maxX", maxX);
                returnIntent.putExtra("minY", minY);
                returnIntent.putExtra("maxY", maxY);
                returnIntent.putExtra("minZ", minZ);
                returnIntent.putExtra("maxZ", maxZ);
                setResult(Activity.RESULT_OK, returnIntent);
                finish();
            }
        });
        builderDone.setNeutralButton("No", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

            }
        });
        alertDone = builderDone.create();

        waitDialog = new ProgressDialog(this);
        waitDialog.setTitle("Calculating Minimum/Maximum Value");
        waitDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        waitDialog.setCancelable(false);
    }

    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
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
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                displayGattServices(mBluetoothLeService.getSupportedGattServices());
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                stringData = intent.getStringExtra(BluetoothLeService.EXTRA_DATA);
                String readMessage = stringData;
                recDataString.append(readMessage);
                int endOfLineIndex = recDataString.indexOf("~");
                if (endOfLineIndex > 0) {
                    String dataInPrint = recDataString.substring(0, endOfLineIndex);
                    int dataLength = dataInPrint.length();
                    if (recDataString.charAt(0) == '#')
                    {
                        StringTokenizer tokens = new StringTokenizer(dataInPrint, "+");
                        String sensor0 = tokens.nextToken().replace("#","");
                        String sensor1 = tokens.nextToken();
                        String sensor2 = tokens.nextToken();


                        switch (curBtn){
                            case (1):
                            case (2):
                                handle = "X axis: ";
                                break;
                            case (3):
                            case (4):
                                handle = "Y axis: ";
                                break;
                            case (5):
                            case (6):
                                handle = "Z axis: ";
                                break;
                        }

                        switch (curBtn){
                            case (1):
                                numsensor = Float.valueOf(sensor0);
                                break;
                            case (2):
                                numsensor = Float.valueOf(sensor0);
                                break;
                            case (3):
                                numsensor = Float.valueOf(sensor1);
                                break;
                            case (4):
                                numsensor = Float.valueOf(sensor1);
                                break;
                            case (5):
                                numsensor = Float.valueOf(sensor2);
                                break;
                            case (6):
                                numsensor = Float.valueOf(sensor2);
                                break;
                        }

                        sensorView.setText(handle + String.format("%.2f", numsensor));
                        buffer.add(counter,numsensor);
                        counter++;
                        if(counter==100){
                            counter = 0;
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
                // If there is an active notification on a characteristic, clear
                // it first so it doesn't update the data field on the user interface.
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
            if(SampleGattAttributes.lookup(uuid, unknownServiceString)!=unknownServiceString) {
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

                // Loops through available Characteristics.
                for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                    charas.add(gattCharacteristic);
                    HashMap<String, String> currentCharaData = new HashMap<String, String>();
                    uuid = gattCharacteristic.getUuid().toString();
                    currentCharaData.put(
                            LIST_NAME, SampleGattAttributes.lookup(uuid, unknownCharaString));
                    currentCharaData.put(LIST_UUID, uuid);
                    if(SampleGattAttributes.lookup(uuid, unknownCharaString)!=unknownCharaString){
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
                new String[] {LIST_NAME, LIST_UUID},
                new int[] { android.R.id.text1, android.R.id.text2 },
                gattCharacteristicData,
                android.R.layout.simple_expandable_list_item_2,
                new String[] {LIST_NAME, LIST_UUID},
                new int[] { android.R.id.text1, android.R.id.text2 }
        );
        startService();
        try { Thread.sleep(500); }
        catch (InterruptedException ex) { android.util.Log.d("Hello: ", ex.toString()); }
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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.calibrate_menu, menu);
        menu.findItem(R.id.menu_done).setVisible(true);
        menu.findItem(R.id.myswitch).setVisible(true);
        switchMode = (Switch) menu.findItem(R.id.myswitch).getActionView().findViewById(R.id.switchForActionBar);

        switchMode.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                switchBol = isChecked;
                if(isChecked){
                    btnOk.setText("Start");
                }else{
                    btnOk.setText("Confirm Value");
                }

            }
        });
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_connect:
                mBluetoothLeService.connect(mDeviceAddress);
                return true;
            case R.id.menu_disconnect:
                mBluetoothLeService.disconnect();
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
            case R.id.menu_done:
                if(minX==0f || maxX==0f || minY==0f || maxY==0f || minZ==0f || maxZ==0f){
                    alert.show();
                }else {
                    Intent returnIntent = new Intent();
                    returnIntent.putExtra("minX", minX);
                    returnIntent.putExtra("maxX", maxX);
                    returnIntent.putExtra("minY", minY);
                    returnIntent.putExtra("maxY", maxY);
                    returnIntent.putExtra("minZ", minZ);
                    returnIntent.putExtra("maxZ", maxZ);
                    setResult(Activity.RESULT_OK, returnIntent);
                    finish();
                }
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void onClick(View view) {
        switch (view.getId()){
            case R.id.btnNext:
                if(curBtn<6){
                    curBtn++;
                }
                break;
            case R.id.btnPre:
                if(curBtn>1){
                    curBtn--;
                }
                break;
            case R.id.btnOk:
                if(switchBol){
                    System.out.println("Si estoy Automatico");
                        if (curBtn==6){
                            searchForMinMax(curBtn);
                            if(minX!=0f && maxX!=0f && minY!=0f && maxY!=0f && minZ!=0f && maxZ!=0f){
                                alertDone.show();
                            }
                        }else{
                            searchForMinMax(curBtn);
                        }
                }else{
                    System.out.println("No estoy Automatico");
                    switch (curBtn){
                        case (1):
                            minX = numsensor;
                            tvMinX.setText(textMin + String.valueOf(numsensor));
                            break;
                        case (2):
                            maxX = numsensor;
                            tvMaxX.setText(textMax + String.valueOf(numsensor));
                            break;
                        case (3):
                            minY = numsensor;
                            tvMinY.setText(textMin + String.valueOf(numsensor));
                            break;
                        case (4):
                            maxY = numsensor;
                            tvMaxY.setText(textMax + String.valueOf(numsensor));
                            break;
                        case (5):
                            minZ = numsensor;
                            tvMinZ.setText(textMin + String.valueOf(numsensor));
                            break;
                        case (6):
                            maxZ = numsensor;
                            tvMaxZ.setText(textMax + String.valueOf(numsensor));
                            if(minX!=0f && maxX!=0f && minY!=0f && maxY!=0f && minZ!=0f && maxZ!=0f){
                                alertDone.show();
                            }
                            break;
                    }
                    if(curBtn<6){
                        curBtn++;
                    }
                }
                break;
        }




        switch (curBtn){
            case (1):
                tvOnAxis.setText("Find the Minimum Value of X");
                img.setImageResource(R.drawable.minx720);
                break;
            case (2):
                tvOnAxis.setText("Find the Maximum Value of X");
                img.setImageResource(R.drawable.maxx720);
                break;
            case (3):
                tvOnAxis.setText("Find the Minimum Value of Y");
                img.setImageResource(R.drawable.miny720);
                break;
            case (4):
                tvOnAxis.setText("Find the Maximum Value of Y");
                img.setImageResource(R.drawable.maxy720);
                break;
            case (5):
                tvOnAxis.setText("Find the Minimum Value of Z");
                img.setImageResource(R.drawable.minz720);
                break;
            case (6):
                tvOnAxis.setText("Find the Maximum Value of Z");
                img.setImageResource(R.drawable.maxz720);
                break;
        }

        if(switchBol){
            counter=0;
        }

        System.out.println("Boton es: "+curBtn);
    }

    private void searchForMinMax(final int button) {
        waitDialog.show();

        Thread mThread = new Thread() {
            @Override
            public void run() {
                try {
                    Thread.sleep(5000);
                }
                catch (InterruptedException ex) {
                }
                runOnUiThread(new Runnable() {
                    public void run() {
                        System.out.println("Curbuton: "+button);
                        switch (button){
                            case (1):
                                minX = buffer.get(buffer.indexOf(Collections.min(buffer)));
                                tvMinX.setText(textMin + String.valueOf(minX));
                                break;
                            case (2):
                                maxX = buffer.get(buffer.indexOf(Collections.max(buffer)));
                                tvMaxX.setText(textMax + String.valueOf(maxX));
                                break;
                            case (3):
                                minY = buffer.get(buffer.indexOf(Collections.min(buffer)));
                                tvMinY.setText(textMin + String.valueOf(minY));
                                break;
                            case (4):
                                maxY = buffer.get(buffer.indexOf(Collections.max(buffer)));
                                tvMaxY.setText(textMax + String.valueOf(maxY));
                                break;
                            case (5):
                                minZ = buffer.get(buffer.indexOf(Collections.min(buffer)));
                                tvMinZ.setText(textMin + String.valueOf(minZ));
                                break;
                            case (6):
                                maxZ = buffer.get(buffer.indexOf(Collections.max(buffer)));
                                tvMaxZ.setText(textMax + String.valueOf(maxZ));
                                break;
                        }
                    }
                });
                waitDialog.dismiss();
            }
        };

        mThread.start();

    }

}

