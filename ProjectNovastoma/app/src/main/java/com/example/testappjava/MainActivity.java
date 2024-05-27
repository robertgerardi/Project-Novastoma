package com.example.testappjava;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;


import android.app.Application;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;

import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;

import java.lang.reflect.Array;
import java.math.BigInteger;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Random;


import android.content.pm.PackageManager;
import android.nfc.Tag;
import android.os.AsyncTask;
import android.os.Handler;

import android.util.Log;
import android.widget.Button;
import android.widget.ProgressBar;

import android.widget.TextView;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import android.os.Bundle;
import android.widget.Toast;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GridLabelRenderer;
import com.jjoe64.graphview.helper.StaticLabelsFormatter;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.time.*;


import java.io.*;
import java.net.Socket;

public class MainActivity extends AppCompatActivity {


    private final static String FILE_NAME = "example.txt";
    private final static int REQUEST_ENABLE_BT = 1;
    private final static String TAG = MainActivity.class.getSimpleName();

    //UUID's for the project, for some reason the phone would not connect off of UUID, so we had to connect off of name
    // which is "Novastoma nRF"
    final String NOVA_SERVICE_UUID = "000070a1-726d-4e69-8172-7eb59ca13ca8";
    final String COMMAND_UUID = "010070a1-726d-4e69-8172-7eb59ca13ca8";
    final String DATA_UUID = "020070a1-726d-4e69-8172-7eb59ca13ca8";
    final String BATTERY_UUID = "030070a1-726d-4e69-8172-7eb59ca13ca8";



    //BLUETOOTH ST
    BluetoothAdapter btAdapter;
    BluetoothManager btManager;
    BluetoothLeScanner btScanner;
    BluetoothDevice btDevice;
    BluetoothGatt btGatt;
    BluetoothGattCharacteristic commandCharacteristic;
    BluetoothGattCharacteristic dataCharacteristic;
    BluetoothGattCharacteristic batteryCharacteristic;

    List<BluetoothGattService> services;

    BluetoothGattDescriptor descriptor;

    //UI ELEMENTS
    TextView ble_status;
    Button ble_con;
    ProgressBar waste_level;
    TextView waste_text;
    TextView batt_volt;

    Button saveDatabtn;

    TextView average1day;
    TextView average3day;
    TextView average7day;

    //DP 0 - 0 to 0 stays at 0, 0 to 1 is 20ml, 0 to 2 is 140 ml, 0 to 3 is 350 ml
    //DP 1 - 1 to 0 adds 0, 1 to 1 adds 10ml, 1 to 2 adds 120ml, 1 to 3 adds 330ml
    //DP 2 - 2 to 0 adds 0, 2 to 1 assumes that they emptied the bag before it hit 3 so we add 50ml because it could be a little more that 20ml
    //       2 to 2 adds 10ml, 2 to 3 adds 210ml
    //DP 3 - 3 to 0 adds 0, 3 to 1 assumes that they emptied the bag and it went back up to 1 and maybe going a little over DP 3, so we add 40ml
    //       3 to 2 adds 160 assuming the user emptied and it was filled back up to 2, possibly going over DP 3 a little, so we add 160ml
    //       3 to 3 adds 10ml

    //Waste data array used for computing waste data, this is mostly accurate with the bag we used
    int waste2DArray[][] = {
            {0, 20, 140, 350},
            {0, 10, 120, 330},
            {0, 50, 10, 210},
            {0, 40, 160, 10}
    };
    // a copy was made for modifying

    int waste2DArrayCOPY[][] = waste2DArray;

    boolean bolBLE = false;

    //DB handler for SQLite database
    private DBHandler dbHandler;

    //Resumes the app
    @Override
    protected void onResume() {
        super.onResume();
    }

    //handler is called 5 seconds after services discovered to send time to MCU
    //handler calls runnable and writes over command characteristic
    private Handler testHand = new Handler();
    private Runnable testRun = new Runnable() {
        @Override
        public void run() {
            Integer temp = (int)(System.currentTimeMillis()/1000);

            commandCharacteristic.setValue(temp,BluetoothGattCharacteristic.FORMAT_UINT32,0);
            btGatt.writeCharacteristic(commandCharacteristic);
        }
    };

    //############################ ON CREATE #############################################
    //Creates the UI
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //sets UI
        setContentView(R.layout.activity_main);

        ble_status = findViewById(R.id.ble_status);
        ble_con = findViewById(R.id.ble_btn);
        saveDatabtn = findViewById(R.id.saveFilebtn);
        waste_level = findViewById((R.id.progressBar));

        waste_text = findViewById(R.id.WasteLevelText);
        batt_volt = findViewById(R.id.BatteryVoltage);
        ble_con.setOnClickListener(v -> BLE_Click());
        saveDatabtn.setOnClickListener(v -> saveData());
        average1day = findViewById(R.id.average1day);
        average3day = findViewById(R.id.average3day);
        average7day = findViewById(R.id.average7day);
        // FileSend clas is created for database transfer to remote server

        new FileSend();

        //DB handler class is created
        dbHandler = new DBHandler(MainActivity.this);


        //Bluetooth stuff is setup
        btManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        btAdapter = btManager.getAdapter();
        btScanner = btAdapter.getBluetoothLeScanner();
        if (btAdapter == null || !btAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        //read data points, show averages on screen.
        ArrayList<dataPoint> averageChecker1DAY = dbHandler.readDataPoints1Day();
        ArrayList<dataPoint> averageChecker3DAY = dbHandler.readDataPoints3Day();
        ArrayList<dataPoint> averageCheckerWEEK = dbHandler.readDataPointsWeek();

        displayAverages(averageChecker1DAY,1);
        displayAverages(averageChecker3DAY,3);
        displayAverages(averageCheckerWEEK,7);


    }

    //############################ ON CREATE  END #############################################


    //############################ BLE FUNCTIONS #############################################


    // SCAN CALLBACK - may not need ##############
    //Orignially we used it, then we didnt because we used bonded devices, but after we switched to nRF board, we had to implement
    //it again because the device would unbond on connection

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override

        public void onScanResult(int callbackType, ScanResult result) {


            System.out.println("At Scan Callback");
            super.onScanResult(callbackType, result);
            btDevice = result.getDevice();

            if (btDevice.getName() != null) {


                if (btDevice.getName().equals("Novastoma nRF")) {
                    //this really should be using the novastoma UUID for better security
                    btScanner.stopScan(scanCallback);
                    BLE_Connect();
                }

            }
        }

    };

    // GATT CALLBACK #############
    //function gets the gatt callback and sets up the services
    BluetoothGattCallback gattCallBack = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            if (newState == BluetoothProfile.STATE_CONNECTED) {

                btGatt.discoverServices();

            }
            if (newState == BluetoothProfile.STATE_DISCONNECTED){
                BLE_Disconnect();
                //update progress if disconnected
                Update_Progress(4);
            }

        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                services = gatt.getServices();
                Get_Characteristics(services);
                bolBLE = true;
                Update_Button();

                // when services are discovered, it sets up the characteristics and the button

                btGatt.setCharacteristicNotification(dataCharacteristic,true);
                btGatt.setCharacteristicNotification(batteryCharacteristic,true);
                btGatt.setCharacteristicNotification(commandCharacteristic,true);

                for(BluetoothGattDescriptor descriptor : dataCharacteristic.getDescriptors()){
                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
                    btGatt.writeDescriptor(descriptor);
                }

                //SEND TIME - might need to be moved if its too fast
                testHand.postDelayed(testRun,5000);



            } else {
                BLE_Disconnect();
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic){
            super.onCharacteristicChanged(gatt,characteristic);

            if(characteristic.equals(dataCharacteristic)){
                //if it equals data characteristic, grab datapoint and then get the time and add it to database
                String fullData;
                fullData = dataCharacteristic.getStringValue(0);

                int DP = fullData.charAt(10) - '0';

                try {
                    //gets timestamp
                    fullData = fullData.substring(0,10);
                    int integerData = Integer.parseInt(fullData);
                    Log.d(TAG, String.valueOf(integerData));
                    Instant instant = Instant.ofEpochSecond(integerData);
                    LocalDateTime ldt = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
                    int year = ldt.getYear();
                    int month = ldt.getMonthValue();
                    int day = ldt.getDayOfMonth();
                    int hour = ldt.getHour();
                    int minute = ldt.getMinute();
                    //data point is added to database
                    dbHandler.addNewDataPoint(year,month,day,hour,minute,DP);
                    Update_Progress(DP);
                }
                catch(NumberFormatException e){
                    Log.d(TAG,"NUMBER FORMAT FAILURE");
                }



            }else {
                if (characteristic.equals(batteryCharacteristic)) {
                    int volt;
                    volt = batteryCharacteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8,0);
                    Update_Batt(String.valueOf(volt));
                    // show voltage if battery characteristic
                }
            }
        }
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {

        }

    };

    // GET CHARACTERISTICS #####################

    //setup for characteristics
    private void Get_Characteristics(List<BluetoothGattService> gattServices) {
        for (BluetoothGattService gattService : gattServices) {

            new ArrayList<HashMap<String, String>>();
            List<BluetoothGattCharacteristic> gattCharacteristics = gattService.getCharacteristics();
            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                if (gattCharacteristic.getUuid().toString().equals(DATA_UUID)) {
                    dataCharacteristic = gattCharacteristic;
                }
                else if (gattCharacteristic.getUuid().toString().equals(BATTERY_UUID))
                    batteryCharacteristic = gattCharacteristic;
                else if (gattCharacteristic.getUuid().toString().equals(COMMAND_UUID))
                    commandCharacteristic = gattCharacteristic;
            }
        }
    }

    // BLE SCAN ###################
    private void BLE_Scan() {
        btScanner.startScan(scanCallback);

        // I will keep the code below here
        //This was called instead of the callback when we were working off of paired devices
        //This worked a lot faster than looking for the device

        /*
        Set<BluetoothDevice> pairedDevices = btAdapter.getBondedDevices();

        for(BluetoothDevice d: pairedDevices){
            if(d.getName().equals("Novastoma nRF")){
                btDevice = d;
                BLE_Connect();

                try {
                    Method getUuidsMethod = BluetoothAdapter.class.getDeclaredMethod("getUuids", null);

                    ParcelUuid[] uuids = (ParcelUuid[]) getUuidsMethod.invoke(btAdapter, null);
                    for(ParcelUuid u : uuids){
                        Log.d(TAG,u.toString());
                        Log.d(TAG,"test");
                        if(u.toString() == NOVA_SERVICE_UUID) {
                            btDevice = d;
                            BLE_Connect();
                            break;
                        }
                    }
                }
                catch(Exception e){

                }*/
                //ParcelUuid[] uuids = d.getUuids();


         //   }
       // }
    }

    // BLE CONNECT ##############
    private void BLE_Connect() {
        btGatt = btDevice.connectGatt(this,
                false,
                gattCallBack,
                BluetoothDevice.TRANSPORT_LE);

    }

    // BLE DISCONNECT ##############
    private void BLE_Disconnect() {
        if (btGatt != null)
            btGatt.disconnect();
        btGatt = null;
        services = null;
        bolBLE = false;
        Update_Button();
    }


    // BLE CLICK ##############
    private void BLE_Click() {

        if (!bolBLE) {
            ble_status.setText(R.string.ble_connecting);
            ble_con.setEnabled(false);
            BLE_Scan();
        } else {
            BLE_Disconnect();
        }
    }
    
    // UPDATE PROGRESS - updates the progress bar ##############
    private void Update_Progress(int data){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {


                switch(data) {

                    case 0:
                        waste_level.setProgress(5,true);
                        waste_text.setText(R.string.ZeroLevel);
                        waste_text.setTextColor(getColor(R.color.NovaBlue));
                    //no sensor




                        break;
                    case 1:
                        waste_level.setProgress(33,true);
                        waste_text.setText(R.string.LowLevel);
                        waste_text.setTextColor(getColor(R.color.green));
                    //1st sensor


                        break;
                    case 2:
                        waste_level.setProgress(50,true);
                        waste_text.setText(R.string.MediumLevel);
                        waste_text.setTextColor(getColor(R.color.NovaYellow));
                    //2nd sensor


                        break;
                    case 3:
                        waste_level.setProgress(100,true);
                        waste_text.setText(R.string.HighLevel);
                        waste_text.setTextColor(getColor(R.color.red));
                    //3rd sensor


                        break;
                    case 4:
                        waste_level.setProgress(0,true);
                        waste_text.setText(R.string.ble_disconnected);
                        waste_text.setTextColor(getColor(R.color.red));
                        //disconnected state

                }


            }
        });
    }

    // updates battery voltage ##############
    //shows battery voltage on screen, not the most accurate
    private void Update_Batt(String percentage){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
               String test = percentage ;
                String text = "Device Battery Percentage: "+ test + "%";
                batt_volt.setText(text);
            }
        });
    }

    //Class updates the button with states
    private void Update_Button() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {

                if (bolBLE) {
                    ble_con.setText(R.string.ble_disconnect);
                    ble_status.setBackgroundColor(getResources().getColor(R.color.green));
                    ble_status.setText(R.string.ble_connected);
                } else {
                    ble_con.setText(R.string.ble_connect);
                    ble_status.setBackgroundColor(getResources().getColor(R.color.red));
                    ble_status.setText(R.string.ble_disconnected);
                }

                ble_con.setEnabled(true);
            }
        });
    }

    //dont use this but could use it in the future
    String byteArrayToString(byte[] in) {
        char out[] = new char[in.length * 2];
        for (int i = 0; i < in.length; i++) {
            out[i * 2] = "0123456789ABCDEF".charAt((in[i] >> 4) & 15);
            out[i * 2 + 1] = "0123456789ABCDEF".charAt(in[i] & 15);
        }
        return new String(out);
    }

    //this function is bound to the send data function
    //it executes the async file send task that sends the DB file to the remote server

    private void saveData(){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                //FILE SAVE CALL
                new FileSend().execute("");


                //dbHandler.addNewDataPoint(2024,month,day,hour,minute,DP);
                Toast.makeText(getApplicationContext(), "Waste sent to server",Toast.LENGTH_LONG).show();

                /*
                byte[] array = {0x00,0x01,0x02};
                //String test = array.toString();
            String text = " THIS IS A TEST";
            FileOutputStream fos = null;
                try {
                    fos = openFileOutput(FILE_NAME, MODE_PRIVATE);
                    fos.write(byteArrayToString(array).getBytes());






                    Toast.makeText(getApplicationContext(), "Saved to " + getFilesDir() + "/" + FILE_NAME,
                            Toast.LENGTH_LONG).show();
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    if (fos != null) {
                        try {
                            fos.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                } */



            }

        });
    }


    //this displays averages on the screen for the past amount of days specified

    private void displayAverages(ArrayList<dataPoint> temp, int days){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                //0 - 0ml
                //1 - ~20ml
                //2 - ~140ml
                //3 - ~350ml
                //2D array with values?
                /*
                int waste2DArray[][] = {
                        {0, 20, 140, 350},
                        {0, 10, 120, 330},
                        {0, 50, 10, 210},
                        {0, 40, 160, 10}
                }; */

                int total = 0;

                //algorithm subtracts from next array level if the system detects the same sensor multiple times in a row

                if(days == 1) {
                    Collections.reverse(temp);
                    for (int i = 1; i < temp.size(); i++) {
                        if(temp.get(i).DP == temp.get(i-1).DP){
                            if(temp.get(i).DP != 3 && temp.get(i).DP != 0){
                                waste2DArrayCOPY[temp.get(i).DP][temp.get(i).DP + 1] -= 10;
                            }
                        }
                        if(temp.get(i-1).DP !=0 && temp.get(i).DP == 0){
                            waste2DArrayCOPY = waste2DArray;
                        }
                        total += waste2DArrayCOPY[temp.get(i-1).DP][temp.get(i).DP];

                    }

                    String av = String.valueOf(total);
                    average1day.setText("Today: ~"+ av + "mL");
                }
                if(days == 3) {
                    for (int i = 1; i < temp.size(); i++) {
                        if(temp.get(i).DP == temp.get(i-1).DP){
                            if(temp.get(i).DP != 3 && temp.get(i).DP != 0){
                                waste2DArrayCOPY[temp.get(i).DP][temp.get(i).DP + 1] -= 10;
                            }
                        }
                        if(temp.get(i-1).DP !=0 && temp.get(i).DP == 0){
                            waste2DArrayCOPY = waste2DArray;
                        }
                        total += waste2DArrayCOPY[temp.get(i-1).DP][temp.get(i).DP];
                    }
                    String av = String.valueOf(total);
                    average3day.setText("3 days: ~"+ av + "mL");
                }
                if(days == 7) {
                    for (int i = 1; i < temp.size(); i++) {
                        if(temp.get(i).DP == temp.get(i-1).DP){
                            if(temp.get(i).DP != 3 && temp.get(i).DP != 0){
                                waste2DArrayCOPY[temp.get(i).DP][temp.get(i).DP + 1] -= 10;
                            }
                        }
                        if(temp.get(i-1).DP !=0 && temp.get(i).DP == 0){
                            waste2DArrayCOPY = waste2DArray;
                        }
                        total += waste2DArrayCOPY[temp.get(i-1).DP][temp.get(i).DP];
                    }
                    String av = String.valueOf(total);
                    average7day.setText("7 days: ~"+ av + "mL");
                }


            }
        });
    }




}

// this async tasks runs if the button is pressed
// it creates a server on the specified IP, phone needs to be connected to internet

class FileSend extends AsyncTask<String, Void, String>{
    private Exception exception;

    protected String doInBackground(String... strings){
        try (Socket socket = new Socket("10.153.15.215", 2222)) {
             DataOutputStream dataOutputStream = null;
             DataInputStream dataInputStream = null;

            dataInputStream = new DataInputStream(
                    socket.getInputStream());
            dataOutputStream = new DataOutputStream(
                    socket.getOutputStream());
            System.out.println(
                    "Sending the File to the Server");
            // Call SendFile Method
            //sendFile(
            //        "/data/data/com.example.testappjava/files/example.txt");
            String path = "/data/data/com.example.testappjava/databases/WasteDatadb";
            int bytes = 0;
            // Open the File where he located in your pc
            File file = new File(path);
            FileInputStream fileInputStream
                    = new FileInputStream(file);

            // Here we send the File to Server
            dataOutputStream.writeLong(file.length());
            // Here we  break file into chunks
            byte[] buffer = new byte[4 * 1024];
            while ((bytes = fileInputStream.read(buffer))
                    != -1) {
                // Send the file to Server Socket
                dataOutputStream.write(buffer, 0, bytes);
                dataOutputStream.flush();
            }
            // close the file here
            fileInputStream.close();

            dataInputStream.close();

        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return " ";
    }




}


 class dataPoint{
    int year;
    int month;
    int day;
    int hour;
    int minute;
    int DP;
    public dataPoint(int Year, int Month, int Day, int Hour, int Minute, int DP){
        this.year = Year;
        this.month = Month;
        this.day = Day;
        this.hour = Hour;
        this.minute = Minute;
        this.DP = DP;
    }
    public int getMonth(){
        return this.month;
    }
     public int getYear(){
         return this.year;
     }
     public int getDay(){
         return this.day;
     }
     public int getHour(){
         return this.hour;
     }
     public int getMinute(){
         return this.minute;
     }
     public int getDP(){
         return this.DP;
     }
};