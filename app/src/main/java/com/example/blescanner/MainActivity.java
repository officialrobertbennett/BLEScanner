package com.example.blescanner;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.database.MatrixCursor;
import android.media.audiofx.DynamicsProcessing;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;

import static android.R.layout.simple_spinner_item;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, AdapterView.OnItemClickListener {
    private final static String TAG = com.example.blescanner.MainActivity.class.getSimpleName();

    public static final int REQUEST_ENABLE_BT = 1;
    public static final int BTLE_SERVICES = 2;

    private HashMap<String, BTLE_Device> mBTDevicesHashMap;
    private ArrayList<BTLE_Device> mBTDevicesArrayList;
    private ListAdapter_BTLE_Devices adapter;
    private ListView listView;

    private Button btn_Scan;
    private Button btn_Send;

    private BroadcastReceiver_BTState mBTStateUpdateReceiver;
    private Scanner_BTLE mBTLeScanner;

    //URL for locations table API
    private String URLstring = "https://utech-asset-tracker.herokuapp.com/api/locations";
    private static ProgressDialog mProgressDialog;
    private ArrayList<ModelData> goodModelArrayList;
    private ArrayList<String> names = new ArrayList<String>();
    private Spinner spinner;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //assigning the location spinner element to the variable
        spinner = findViewById(R.id.location_spinner);

        retrieveJSON();

        // Use this check to determine whether BLE is supported on the device. Then
        // you can selectively disable BLE-related features.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Utils.toast(getApplicationContext(), "BLE not supported");
            finish();
        }

        mBTStateUpdateReceiver = new BroadcastReceiver_BTState(getApplicationContext());
        mBTLeScanner = new Scanner_BTLE(this, 5000, -75);

        mBTDevicesHashMap = new HashMap<>();
        mBTDevicesArrayList = new ArrayList<>();

        adapter = new ListAdapter_BTLE_Devices(this, R.layout.btle_device_list_item, mBTDevicesArrayList);

        listView = new ListView(this);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(this);

        btn_Scan = (Button) findViewById(R.id.btn_scan);
        ((ScrollView) findViewById(R.id.scrollView)).addView(listView);
        findViewById(R.id.btn_scan).setOnClickListener(this);

        btn_Send = (Button) findViewById(R.id.btn_send_to_database);

        btn_Send.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    updateBeaconsLocation();
                } catch (JSONException e) {
                    e.printStackTrace();
                    Toast.makeText(MainActivity.this.getApplicationContext(), "Failed to update locations", Integer.parseInt("2000")).show();
                }
            }
        });

    }

    private void updateBeaconsLocation() throws JSONException {
        // calls function that send the data to API
        sendRequest(formatDataToJSON());
    }

    private JSONObject formatDataToJSON() throws JSONException {

        // JSON array which ALL object (beacon_uuid & location_id) from your array list
        JSONArray beaconsArray = new JSONArray();

        JSONObject currentData = new JSONObject(); // will hold data from each object inside the loop iteration
        ArrayList<String> beacon_addresses = new ArrayList<String>();

        //looping through the scanned devices and selecting the UUIDs
        for (int i = 0; i < mBTDevicesArrayList.size(); i++) {
            beacon_addresses.add(mBTDevicesArrayList.get(i).getAddress());
        }
        //getting the current/selected location_id of the dropdown/spinner
        int spinner_location_id = (int) (spinner.getSelectedItemId() + 1);  // + 1 because it started counting from 0

        //loop through all the beacon UUIDs and add them to the JSON object
        for (int i = 0; i < beacon_addresses.size(); i++){
            JSONObject JSONloopObj = new JSONObject();
            JSONloopObj.put("beacon_uuid", beacon_addresses.get(i));
            JSONloopObj.put("location_id", spinner_location_id);
            beaconsArray.put(JSONloopObj);
        }

        //CURRENT DATA ISNT NECESSASRY BECAUSE THAT ONLY TRANSFER 1 SET OF VALUES AND NOT ALL. IT ONLY SENDS THE LAST
            //DETECTED UUID AND NOT ALL
        //currentData.put("beacon_uuid", beacon_addresses.toString());
        //currentData.put("location_id", spinner_location_id);

        //currentData.put("beacon_uuid", "4A:F3:41:50:E4:F9"); //hardcode for testing
        //currentData.put("location_id", 1); //hardcode for testing

        JSONObject beaconsObj = new JSONObject();

        Log.d("JSON Beacons Array Data",beaconsArray.toString());//to be removed

        JSONObject abc = beaconsObj.put("beacons", beaconsArray); //to be removed
        Log.d("JSON Becaons Object",abc.toString()); //to be removed
        //return abc; //to be removed

        return beaconsObj.put("beacons", beaconsArray);
    }

    private void sendRequest(JSONObject data) {
        String url = "http://utech-asset-tracker.herokuapp.com/api/beacon/update";

        // sets content type
        MediaType JSON = MediaType.parse("application/json; charset=utf-8");

        //preparing and making the HTTP request
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();

        RequestBody body = RequestBody.create(JSON, String.valueOf(data)); // formats data for sending
        //RequestBody body = RequestBody.create(String.valueOf(data), JSON); // formats data for sending
        okhttp3.Request request = new okhttp3.Request.Builder()
                .url(url)
                .put(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                Log.d("fail", "fail");
                Toast.makeText(MainActivity.this.getApplicationContext(), "Failed to update locations", Integer.parseInt("2000")).show();
                e.printStackTrace();
            }
            @Override
            public void onResponse(@NotNull Call call, @NotNull okhttp3.Response response) throws IOException {
                if (response.isSuccessful()) {
                    Log.d("Is Success " , "Success");
                    Log.d("Is Success " , response.body().string());
                    MainActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this.getApplicationContext(), "Succesfully Updated", Integer.parseInt("2000")).show();
                        }
                    });
                }
            }

        });
    }


    //method to fetch JSON asset locations
    private void retrieveJSON() {
        //showSimpleProgressDialog(this, "Loading...","Fetching Asset Locations",true);

        StringRequest stringRequest = new StringRequest(Request.Method.GET, URLstring,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {

                        Log.d("strrrrr", ">>" + response);
                        try {
                            JSONObject obj = new JSONObject(response);

                            goodModelArrayList = new ArrayList<>();
                            JSONArray dataArray = obj.getJSONArray("data");

                            for (int i = 0; i < dataArray.length(); i++) {

                                ModelData playerModel = new ModelData();
                                JSONObject dataobj = dataArray.getJSONObject(i);

                                playerModel.setLocation_id(dataobj.getInt("location_id"));
                                playerModel.setName(dataobj.getString("name"));

                                goodModelArrayList.add(playerModel);
                            }

                            for (int i = 0; i < goodModelArrayList.size(); i++) {
                                names.add(goodModelArrayList.get(i).getName().toString());
                            }

                            ArrayAdapter<String> spinnerArrayAdapter = new ArrayAdapter<String>(MainActivity.this, simple_spinner_item, names);
                            spinnerArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); // The drop down view
                            spinner.setAdapter(spinnerArrayAdapter);
                            removeSimpleProgressDialog();
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        //displaying the error in toast if occurrs
                        Toast.makeText(getApplicationContext(), error.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });

        // request queue
        RequestQueue requestQueue = Volley.newRequestQueue(this);

        requestQueue.add(stringRequest);
    }

    public static void removeSimpleProgressDialog() {
        try {
            if (mProgressDialog != null) {
                if (mProgressDialog.isShowing()) {
                    mProgressDialog.dismiss();
                    mProgressDialog = null;
                }
            }
        } catch (IllegalArgumentException ie) {
            ie.printStackTrace();

        } catch (RuntimeException re) {
            re.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public static void showSimpleProgressDialog(Context context, String title,
                                                String msg, boolean isCancelable) {
        try {
            if (mProgressDialog == null) {
//                mProgressDialog = ProgressDialog.show(context, title, msg);
//                mProgressDialog.setCancelable(isCancelable);
            }

            if (!mProgressDialog.isShowing()) {
                mProgressDialog.show();
            }

        } catch (IllegalArgumentException ie) {
            ie.printStackTrace();
        } catch (RuntimeException re) {
            re.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        registerReceiver(mBTStateUpdateReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
    }

    @Override
    protected void onResume() {
        super.onResume();

//        registerReceiver(mBTStateUpdateReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
    }

    @Override
    protected void onPause() {
        super.onPause();

//        unregisterReceiver(mBTStateUpdateReceiver);
        stopScan();
    }

    @Override
    protected void onStop() {
        super.onStop();

        unregisterReceiver(mBTStateUpdateReceiver);
        stopScan();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        // Check which request we're responding to

        //super.onActivityResult(requestCode, resultCode, data);
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BT) {
            // Make sure the request was successful
            if (resultCode == RESULT_OK) {
//                Utils.toast(getApplicationContext(), "Thank you for turning on Bluetooth");
            } else if (resultCode == RESULT_CANCELED) {
                Utils.toast(getApplicationContext(), "Please turn on Bluetooth");
            }
        } else if (requestCode == BTLE_SERVICES) {
            // Do something
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        Context context = view.getContext();

//      Utils.toast(context, "List Item clicked");

        // do something with the text views and start the next activity.

        stopScan();

        String name = mBTDevicesArrayList.get(position).getName();
        String address = mBTDevicesArrayList.get(position).getAddress();

        Intent intent = new Intent(this, Activity_BTLE_Services.class);
        intent.putExtra(Activity_BTLE_Services.EXTRA_NAME, name);
        intent.putExtra(Activity_BTLE_Services.EXTRA_ADDRESS, address);
        startActivityForResult(intent, BTLE_SERVICES);
    }

    @Override
    public void onClick(View v) {

        switch (v.getId()) {

            case R.id.btn_scan:
                Utils.toast(getApplicationContext(), "Scan Button Pressed");

                if (!mBTLeScanner.isScanning()) {
                    startScan();
                } else {
                    stopScan();
                }

                break;
            default:
                break;
        }

    }

    public void addDevice(BluetoothDevice device, int rssi) {

        String address = device.getAddress();
        if (!mBTDevicesHashMap.containsKey(address)) {
            BTLE_Device btleDevice = new BTLE_Device(device);
            btleDevice.setRSSI(rssi);

            mBTDevicesHashMap.put(address, btleDevice);
            mBTDevicesArrayList.add(btleDevice);
        } else {
            mBTDevicesHashMap.get(address).setRSSI(rssi);
        }

        adapter.notifyDataSetChanged();
    }

    public void startScan() {
        btn_Scan.setText("Scanning...");

        mBTDevicesArrayList.clear();
        mBTDevicesHashMap.clear();

        mBTLeScanner.start();
    }

    public void stopScan() {
        btn_Scan.setText("Scan Again");

        mBTLeScanner.stop();
    }

    public void sendResultsToDatabase() {
        btn_Send.setText("Sending Results to Database...");

    }
}


