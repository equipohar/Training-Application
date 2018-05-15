package com.example.android.bluetoothlegatt;

import android.app.ActionBar;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    private Button btnCSV, btnDB;

    private BluetoothAdapter mBluetoothAdapter;
    private static final int REQUEST_ENABLE_BT1 = 1;
    private static final int REQUEST_ENABLE_BT2 = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getSupportActionBar().setTitle(R.string.title_devices);
        getSupportActionBar().setDisplayOptions(ActionBar.DISPLAY_SHOW_HOME | ActionBar.DISPLAY_SHOW_TITLE);
        getSupportActionBar().setIcon(R.mipmap.ic_launcher);

        btnCSV = findViewById(R.id.btnCSV);
        btnDB = findViewById(R.id.btnDB);



        btnDB.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!(getApplicationContext().getPackageManager()).hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
                    Toast.makeText(getApplicationContext(), R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
                }

                final BluetoothManager bluetoothManager =
                        (BluetoothManager) getApplicationContext().getSystemService(Context.BLUETOOTH_SERVICE);
                mBluetoothAdapter = bluetoothManager.getAdapter();

                if (!mBluetoothAdapter.isEnabled()) {
                    if (!mBluetoothAdapter.isEnabled()) {
                        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT1);
                    }
                }else{
                    Intent i = new Intent(getApplicationContext(), DatabaseActivity.class);
                    startActivity(i);
                }
            }
        });

        btnCSV.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!(getApplicationContext().getPackageManager()).hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
                    Toast.makeText(getApplicationContext(), R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
                }

                final BluetoothManager bluetoothManager =
                        (BluetoothManager) getApplicationContext().getSystemService(Context.BLUETOOTH_SERVICE);
                mBluetoothAdapter = bluetoothManager.getAdapter();

                if (!mBluetoothAdapter.isEnabled()) {
                    if (!mBluetoothAdapter.isEnabled()) {
                        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT2);
                    }
                }else{
                    Intent i = new Intent(getApplicationContext(), DeviceControlActivity.class);
                    startActivity(i);
                }
            }
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == REQUEST_ENABLE_BT1){
            Intent i = new Intent(getApplicationContext(), DatabaseActivity.class);
            startActivity(i);
        }
        if(requestCode == REQUEST_ENABLE_BT2){
            Intent i = new Intent(getApplicationContext(), DeviceControlActivity.class);
            startActivity(i);
        }
    }
}
