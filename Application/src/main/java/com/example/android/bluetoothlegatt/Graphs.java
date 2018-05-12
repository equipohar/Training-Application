package com.example.android.bluetoothlegatt;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.PopupMenu;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;


import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.edmodo.rangebar.RangeBar;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.opencsv.CSVWriter;


import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.ExecutionException;

public class Graphs extends AppCompatActivity{

    private LineChart lineChart;
    private Button btnSend;
    private Spinner spinner;
    private String Label;
    private EditText editID,editSesion;
    private int numCSV;

    private static final String[] paths = {"Standing Still", "Walking", "Jogging", "Going Up Stairs",
            "Going Down Stairs", "Jumping", "Laying Down", "Laying Up", "Squatting", "Push Ups"};


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_graphs);
        editID = findViewById(R.id.editID);
        editSesion = findViewById(R.id.editSesion);
        btnSend = findViewById(R.id.btnSend);
        spinner = findViewById(R.id.spinner);

        ArrayAdapter adapter = new ArrayAdapter(Graphs.this,
                android.R.layout.simple_spinner_item,paths);

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Label = Integer.toString(position+1);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        final ArrayList<Float> ValoresX =  (ArrayList<Float>)getIntent().getSerializableExtra("Valores_de_X");
        final ArrayList<Float> ValoresY =  (ArrayList<Float>)getIntent().getSerializableExtra("Valores_de_Y");
        final ArrayList<Float> ValoresZ =  (ArrayList<Float>)getIntent().getSerializableExtra("Valores_de_Z");

        final SharedPreferences sharedPref = Graphs.this.getPreferences(Context.MODE_PRIVATE);
        int defaultValue = getResources().getInteger(R.integer.saved_high_score_default_key);
        numCSV = sharedPref.getInt(getString(R.string.saved_high_score_key), defaultValue);

        editSesion.setText(String.valueOf(numCSV));

        final AlertDialog.Builder alt_bld = new AlertDialog.Builder(Graphs.this);
        alt_bld.setMessage("Please enter a valid label or a valid selection range.");
        alt_bld.setCancelable(true);
        alt_bld.setPositiveButton("Ok",new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {

            }
        });

        final AlertDialog.Builder alt_Confirm = new AlertDialog.Builder(Graphs.this);
        alt_Confirm.setTitle("CSV File saved successfully.");
        alt_Confirm.setMessage("Do you wish to record new data again?");
        alt_Confirm.setCancelable(true);
        alt_Confirm.setPositiveButton("Yes",new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                Intent retBack = new Intent(Graphs.this,DeviceControlActivity.class);
                startActivity(retBack);
                finish();
            }
        });
        alt_Confirm.setNegativeButton("No", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                editSesion.setText(String.valueOf(numCSV));
            }
        });




        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if ((Label!=null)){
                    try {
                        CreateCSV(ValoresX,ValoresY,ValoresZ);
                        numCSV = Integer.valueOf(editSesion.getText().toString());
                        numCSV++;
                        SharedPreferences.Editor editor = sharedPref.edit();
                        editor.putInt(getString(R.string.saved_high_score_key), numCSV);
                        editor.apply();
                        alt_Confirm.show();
                    }
                    catch (IOException e){
                        System.out.println("No se porque pero no funciono esta vaina");
                        System.out.println(e.getMessage());
                    }
                }else{
                    alt_bld.show();
                }
            }
        });


        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("GMT+1:00"));
        Date currentLocalTime = cal.getTime();
        DateFormat date = new SimpleDateFormat("mm:ss");


        lineChart = findViewById(R.id.lineChart);

        ArrayList<Entry> yAXESx = new ArrayList<>();
        ArrayList<Entry> yAXESy = new ArrayList<>();
        ArrayList<Entry> yAXESz = new ArrayList<>();

        for(int i=0;i<(ValoresX.size());i++){
            yAXESx.add(new Entry(new Float ((i-1)*0.05),ValoresX.get(i)));
            yAXESy.add(new Entry(new Float ((i-1)*0.05),ValoresY.get(i)));
            yAXESz.add(new Entry(new Float ((i-1)*0.05),ValoresZ.get(i)));
        }

        ArrayList<ILineDataSet> lineDataSets = new ArrayList<>();

        LineDataSet lineDataSet1 = new LineDataSet(yAXESx,"X Axis");
        lineDataSet1.setDrawCircles(false);
        lineDataSet1.setColor(Color.BLUE);

        LineDataSet lineDataSet2 = new LineDataSet(yAXESy,"Y Axis");
        lineDataSet2.setDrawCircles(false);
        lineDataSet2.setColor(Color.RED);

        LineDataSet lineDataSet3 = new LineDataSet(yAXESz,"Z Axis");
        lineDataSet3.setDrawCircles(false);
        lineDataSet3.setColor(Color.GREEN);

        lineDataSets.add(lineDataSet1);
        lineDataSets.add(lineDataSet2);
        lineDataSets.add(lineDataSet3);

        lineChart.setData(new LineData(lineDataSets));

        lineChart.getDescription().setEnabled(false);

        lineChart.setVisibleXRangeMaximum(10f);


    }

    public void CreateCSV(final ArrayList<Float> ValoresX, final ArrayList<Float> ValoresY, final ArrayList<Float> ValoresZ) throws IOException{
        File mediaStorageDir = new File(Environment.getExternalStorageDirectory(), "CSVFolder");
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                Log.d("App", "failed to create directory");
            }
        }
        File mypath=new File(mediaStorageDir,(editSesion.getText().toString()+".csv"));
        CSVWriter writer = new CSVWriter(new FileWriter(mypath,false));
        System.out.println(mypath);
        List<String[]> data = new ArrayList<String[]>();

        for(int i = 0; i < ValoresX.size(); i++){
            Log.v("TAG",editSesion.getText().toString()+" "+String.valueOf(ValoresX.get(i))+" "+String.valueOf(ValoresY.get(i))+" "+String.valueOf(ValoresZ.get(i))+" "+Label+" "+editID.getText().toString());
            data.add(new String[] {editSesion.getText().toString(),String.valueOf(ValoresX.get(i)),String.valueOf(ValoresY.get(i)),String.valueOf(ValoresZ.get(i)),Label,editID.getText().toString()});
        }
        writer.writeAll(data);
        writer.close();
    }
}