package com.example.scanner_26;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelUuid;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;
import com.jjoe64.graphview.series.PointsGraphSeries;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

import static android.bluetooth.le.ScanSettings.PHY_LE_ALL_SUPPORTED;
import static android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_POWER;

public class MainActivity extends AppCompatActivity {
    int ManufacturerData_size = 24;  //ManufacturerData長度
    String TAG = "ble_scan";

    BluetoothManager btManager;
    BluetoothAdapter btAdapter;
    BluetoothLeScanner btScanner;

    Button startScanningButton;
    Button stopScanningButton;
    Button change_to_list;
    TextView peripheralTextView;
    String uuid_text;
    String add;

    GraphView graphview;

    private final static int REQUEST_ENABLE_BT = 1;
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;

    private List<String> list_device;
    private List<String> list_device_detail;

    ArrayList<ArrayList<Object>> matrix = new ArrayList<>();
    ArrayList<Integer> num_total = new ArrayList<>();
    ArrayList<Long> time_previous = new ArrayList<>();
    ArrayList<Long> mean_total = new ArrayList<>();

    private PointsGraphSeries<DataPoint> series1;
    private PointsGraphSeries<DataPoint> series2;
    private final Handler mHandler = new Handler();
    private Runnable mTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        graphview = findViewById(R.id.graphview);

        list_device = new ArrayList<>();
        list_device_detail = new ArrayList<>();
        peripheralTextView = findViewById(R.id.PeripheralTextView);
        peripheralTextView.setMovementMethod(new ScrollingMovementMethod()); //垂直滾動

        startScanningButton = (Button) findViewById(R.id.StartScanButton);
        startScanningButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startScanning();
            }
        });

        stopScanningButton = (Button) findViewById(R.id.StopScanButton);
        stopScanningButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                stopScanning();
            }
        });
        stopScanningButton.setVisibility(View.INVISIBLE);


        change_to_list = (Button) findViewById(R.id.change_to_list);
        change_to_list.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {

                if (v.getId() == R.id.change_to_list) {
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("device list")
                            .setItems(list_device.toArray(new String[list_device.size()]), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    String list = list_device_detail.get(which);
                                    //Log.d("which",String.valueOf(which));
                                    Toast.makeText(getApplicationContext(), list, Toast.LENGTH_SHORT).show();
                                }
                            })
                            .setPositiveButton("close", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {

                                }
                            })
                            .show();
                }
            }

        });

        btManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        btAdapter = btManager.getAdapter();
        btScanner = btAdapter.getBluetoothLeScanner();
        permission();
        set_graph();
    }

    private static String hexToAscii(String hexStr) {
        StringBuilder output = new StringBuilder("");

        for (int i = 0; i < hexStr.length(); i += 2) {
            String str = hexStr.substring(i, i + 2);
            output.append((char) Integer.parseInt(str, 16));
        }
        //String[] strArray = new String[] {output.toString()};
        return output.toString();
    }

    public String byte2HexStr(byte[] b) {
        String stmp = "";
        StringBuilder sb = new StringBuilder("");
        for (int n = 0; n < b.length; n++) {
            stmp = Integer.toHexString(b[n] & 0xFF);
            sb.append((stmp.length() == 1) ? "0" + stmp : stmp);
        }
        //Log.e(TAG,"length"+Integer.toString(b.length));
        //String[] strArray = new String[] {sb.toString().toUpperCase().trim()};
        //Log.e(TAG,"::"+sb.toString().trim());
        return sb.toString().trim();
        //trim:去掉前後空格 ； toUpperCase:變成大寫
    }

    public static int byteArrayToInt(byte[] b) {
        return b[3] & 0xFF |
                (b[2] & 0xFF) << 8 |
                (b[1] & 0xFF) << 16 |
                (b[0] & 0xFF) << 24;
    }


    // Device scan callback.
    private ScanCallback leScanCallback = new ScanCallback() {

        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            add = result.getDevice().getAddress();
            String msg;
            String currentTime = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
            msg = "Device Name: " + result.getDevice().getName() + "\n" + "rssi: " + result.getRssi() + "\n" + "add: " + result.getDevice().getAddress() + "\n"
                    + "service uuid: " + result.getScanRecord().getServiceUuids() + "\n" + "time: " + currentTime + "\n"
                    + "getTimestampNanos: " + result.getTimestampNanos() + "\n\n";

            Log.e(TAG, "name: " + result.getDevice().getName() + " result: " + result.getScanRecord());
            String address = result.getDevice().getAddress();
            if (!list_device.contains(address)) {
                list_device.add(address);
                list_device_detail.add(msg);
            }
//            if(list_device.contains(address)){
//                list_device.set(list_device.indexOf(address),address);
//                list_device_detail.set(list_device.indexOf(address),msg);
//            }
        /*
        -----------|---------------------
        address    |
        -----------|---------------------
        time_pre   |
        -----------|---------------------
        time_now   |
        -----------|---------------------
        interval   |
        -----------|---------------------
        num        |
        -----------|---------------------
        mean_total |
        -----------|---------------------
        mean       |
        -----------|---------------------
        */

            long TimestampMillis = result.getTimestampNanos() / 1000000;

            int index = list_device.indexOf(address);
            Log.e(TAG, "index: " + index);
            int initial = 0;

            if (!matrix.get(0).contains(index)) {
                matrix.get(0).add(index);                 //address
                matrix.get(1).add(initial);               //time_pre
                matrix.get(2).add(TimestampMillis);       //time_now
                matrix.get(3).add(initial);               //interval
                matrix.get(4).add(num_total.get(index));  //num
                matrix.get(5).add(mean_total.get(index));                  //mean_total
                matrix.get(6).add(mean_total.get(index) / num_total.get(index));     //mean
                time_previous.set(index, TimestampMillis);
                num_total.set(index, num_total.get(index) + 1);
                mean_total.set(index, TimestampMillis - time_previous.get(index));
            } else {
                long interval = TimestampMillis - time_previous.get(index);
                mean_total.set(index, mean_total.get(index) + interval);
                matrix.get(1).set(index, time_previous.get(index));
                matrix.get(2).set(index, TimestampMillis);
                matrix.get(3).set(index, interval);
                matrix.get(4).set(index, num_total.get(index));
                matrix.get(5).set(index, mean_total.get(index));
                matrix.get(6).set(index, mean_total.get(index) / num_total.get(index));
                time_previous.set(index, TimestampMillis);
                num_total.set(index, num_total.get(index) + 1);
            }
//            time_previous.set(index,TimestampMillis);
//            num_total.set(index,num_total.get(index)+1);
//            mean_total.set(index,mean_total.get(index)+TimestampMillis-time_previous.get(index));


            Log.e(TAG, "matrix: " + "\n"
                    + matrix.get(0).toString() + "\n"
                    + matrix.get(1).toString() + "\n"
                    + matrix.get(2).toString() + "\n"
                    + matrix.get(3).toString() + "\n"
                    + matrix.get(4).toString() + "\n"
                    + matrix.get(5).toString() + "\n"
                    + matrix.get(6).toString() + "\n");

//            if (num_total>1){
//                Log.e(TAG,"rxTimestampMillis_mean: "+ mean);
//                long time_interval = rxTimestampMillis-time_previous;
//                mean_total=mean_total+time_interval;
//                mean = mean_total/(num_total-1);
//                Log.e(TAG,"rxTimestampMillis_mean: "+ mean );
//            }
//            num_total++;
//
//            Log.e(TAG,"rxTimestampMillis: "+rxTimestampMillis);

            //peripheralTextView.append(msg);


            //list_device.add(result.getDevice().getName());
            Log.e(TAG, "list: " + Arrays.toString(list_device.toArray()));
            Log.e(TAG, msg);


            // auto scroll for text view
            final int scrollAmount = peripheralTextView.getLayout().getLineTop(peripheralTextView.getLineCount()) - peripheralTextView.getHeight();
            // if there is no need to scroll, scrollAmount will be <=0
            if (scrollAmount > 0)
                peripheralTextView.scrollTo(0, scrollAmount);
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            Log.d("onScanFailed: ", String.valueOf(errorCode));
        }
    };

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_COARSE_LOCATION: {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    System.out.println("coarse location permission granted");
                } else {
                    final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Functionality limited");
                    builder.setMessage("Since location access has not been granted, this app will not be able to discover beacons when in the background.");
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.setOnDismissListener(new DialogInterface.OnDismissListener() {

                        @Override
                        public void onDismiss(DialogInterface dialog) {
                        }

                    });
                    builder.show();
                }
                return;
            }
        }
    }

    public void startScanning() {
        list_device.clear();
        list_device_detail.clear();
        num_total.clear();
        time_previous.clear();
        mean_total.clear();
        matrix.clear();

        long xx = 0;
        for (int ii = 0; ii < 100; ii++) {  //20:mac address數量上限
            num_total.add(1);
            time_previous.add(xx);
            mean_total.add(xx);
        }

        //add six row
        matrix.add(new ArrayList<>());
        matrix.add(new ArrayList<>());
        matrix.add(new ArrayList<>());
        matrix.add(new ArrayList<>());
        matrix.add(new ArrayList<>());
        matrix.add(new ArrayList<>());
        matrix.add(new ArrayList<>());

        Log.e(TAG, "startscanning");
        peripheralTextView.setText("");
        startScanningButton.setVisibility(View.INVISIBLE);
        stopScanningButton.setVisibility(View.VISIBLE);

        String data = "0";
        for (int c = data.length(); (data.length() + 4) % ManufacturerData_size != 0; c++) {
            data = data + "0";
        }

        byte[] id_byte = new byte[]{7, 39, 116, 18};
        byte[] data_all = new byte[id_byte.length + data.getBytes().length];
        System.arraycopy(id_byte, 0, data_all, 1, id_byte.length);
        System.arraycopy(data.getBytes(), 0, data_all, id_byte.length, data.getBytes().length);
        // ManufacturerData : packet編號(1) + id(4) + data(19)

        byte[] data_mask = new byte[]{0x00, 0x11, 0x11, 0x11, 0x11, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
        Log.e(TAG, "data_all: " + byte2HexStr(data_all) + "\n"
                + "data_mask: " + byte2HexStr(data_mask));
        ScanFilter UUID_Filter_M = new ScanFilter.Builder().setManufacturerData(0xffff, data_all, data_mask).build();

        ArrayList<ScanFilter> filters = new ArrayList<>();
        filters.add(UUID_Filter_M);

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(SCAN_MODE_LOW_POWER)
                .setPhy(PHY_LE_ALL_SUPPORTED)
                .setLegacy(false)
//                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
//                .setCallbackType(ScanSettings.CALLBACK_TYPE_FIRST_MATCH)  //Fails to start power optimized scan as this feature is not supported
                .build();
//        btScanner.flushPendingScanResults(leScanCallback);
        btScanner.startScan(filters, settings, leScanCallback);

    }

    public void stopScanning() {
        System.out.println("stopping scanning");
        peripheralTextView.append("Stopped Scanning");
        startScanningButton.setVisibility(View.VISIBLE);
        stopScanningButton.setVisibility(View.INVISIBLE);
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                btScanner.stopScan(leScanCallback);
            }
        });
    }

    public void permission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
        }
        if (!btAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, 1);
        }
    }

    public void set_graph() {
        if (!list_device.isEmpty()){
            series1 = new PointsGraphSeries<>(adddata());   //initializing/defining series to get the data from the method 'data()'
            graphview.addSeries(series1);                   //adding the series to the GraphView
            series1.setShape(PointsGraphSeries.Shape.POINT);
            series1.setSize(10); //點的大小
            graphview.getViewport().setMinY(0);
            graphview.getViewport().setMaxY(300);
            graphview.getViewport().setMinX(0);
            graphview.getViewport().setMaxX(20);
        }else{
            series1 = new PointsGraphSeries<>(adddata_());   //initializing/defining series to get the data from the method 'data()'
            graphview.addSeries(series1);                   //adding the series to the GraphView
            series1.setShape(PointsGraphSeries.Shape.POINT);
            series1.setSize(10); //點的大小
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mTimer = new Runnable() {
            @Override
            public void run() {
                series1.resetData(adddata());
                graphview.getViewport().setMinY(0);
                graphview.getViewport().setMaxY(300);
                graphview.getViewport().setMinX(0);
                graphview.getViewport().setMaxX(20);
                mHandler.postDelayed(this, 500);
            }
        };
        mHandler.postDelayed(mTimer, 500);
    }

    @Override
    public void onPause() {
        mHandler.removeCallbacks(mTimer);
        super.onPause();
    }

    public DataPoint[] adddata() {
        if(list_device.isEmpty()){
            DataPoint[] values = new DataPoint[1];
            DataPoint v = new DataPoint(0,0 );
            values[0] = v;
            return values;
        }
        else {
            int count = matrix.get(0).size();
            DataPoint[] values = new DataPoint[count];
            for (int i = 0; i < count; i++) {
                double x = i;
                double f = Double.parseDouble(matrix.get(0).get(i).toString())+1;
                double y = Double.parseDouble(matrix.get(3).get(i).toString());  //interval
                Log.e(TAG, "x= " + f + ", y= " + y);
                DataPoint v = new DataPoint(f, y);
                values[i] = v;
                Log.e(TAG, "values= " + values[i]);
            }
            return values;
        }
    }

    public DataPoint[] adddata_() {
        double[] x= new double[] {1,2,3,4,5,6,7,8,9,10,11,12};
        double[] y= new double[] {1,2,3,4,5,6,7,8,9,10,11,12};
        int n = x.length;
        DataPoint[] values = new DataPoint[n];     //creating an object of type DataPoint[] of size 'n'
        for(int i=0;i<n;i++){
            DataPoint v = new DataPoint(x[i],y[i]);
            values[i] = v;
        }
        return values;
    }
}
