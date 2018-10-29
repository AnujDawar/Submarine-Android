package com.example.anujd.submarine_version1;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.graphics.Color;
import android.os.Handler;
import android.os.Message;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.cardiomood.android.controls.gauge.BatteryIndicatorGauge;
import com.cardiomood.android.controls.gauge.SpeedometerGauge;
//import com.cardiomood.android.speedometer.SpeedometerView;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;
import static com.example.anujd.submarine_version1.R.id.snackbarCoordinatorLayout;

public class MainActivity extends AppCompatActivity
{
    private final String TAG = "ANUJ_DAWAR_KA_TAG";

    private String messageFromSubmarine = "";

    private BluetoothAdapter btAdapter;
    private final UUID MY_UUID;

    private ConnectedThread connectedThread;
    private ConnectThread connectThread;

    private SpeedometerGauge speedometer;
    private ImageView gasPedal, brakePedal, connectivityStatus;
    private TextView speedValueTV, connectivityStatusTV, batteryPercentageTV;

    private com.example.anujd.submarine_version1.JoystickView joystick;
    private BatteryIndicatorGauge batteryIndicator;
    private Snackbar snackbar;
    private com.example.anujd.submarine_version1.HorizontalWheelView directionWheel, depthWheel;

    private String speedToSend = "0", directionToSend = "5a", depthToSend = "5a";

    private boolean isGasPressed = false, isBrakePressed = false, isConnectedToSubmarine = false, isBluetoothEnabledFlag = false;
    private double currentSpeedCounter = 0.0d;
    private int joystickAngle;
    private int directionValue, depthValue;

    @SuppressLint("HandlerLeak")
    Handler displaySpeedHandler = new Handler()
    {
        @Override
        public void handleMessage(Message msg)
        {
            super.handleMessage(msg);

            double tempSpeedValue = Math.round(Math.abs(currentSpeedCounter));
            speedometer.setSpeed(tempSpeedValue);

            int finalSpeedValue = (int) tempSpeedValue;
            speedValueTV.setText(String.valueOf(finalSpeedValue));
            speedToSend = Integer.toHexString(finalSpeedValue);
        }
    };

    @SuppressLint("HandlerLeak")
    Handler rotateScrollbarsHandler = new Handler()
    {
        @Override
        public void handleMessage(Message msg)
        {
            super.handleMessage(msg);

            setScrollWheelValues();

            directionToSend = String.valueOf(Integer.toHexString(directionValue + 90));
            depthToSend = String.valueOf(Integer.toHexString(depthValue + 90));
        }
    };

    @SuppressLint("HandlerLeak")
    Handler connectedToSubmarineHandler = new Handler()
    {
        @Override
        public void handleMessage(Message msg)
        {
            super.handleMessage(msg);

            isConnectedToSubmarine = true;

            Log.e("DATA: ", messageFromSubmarine);

            batteryIndicator.setValue(Integer.valueOf(messageFromSubmarine));
            batteryPercentageTV.setText(messageFromSubmarine.concat("%"));

            String dataToSend = "";
            dataToSend += speedToSend + ":";    // speed
            dataToSend += directionToSend + ":";    //  direction
            dataToSend += depthToSend;   // depth is initially 0 (surface)
            dataToSend += ";";  //  terminating char

            Log.e(TAG, "SENDING DATA: " + dataToSend);

            connectedThread.write(dataToSend.getBytes());
        }
    };

    @SuppressLint("HandlerLeak")
    Handler bluetoothDisconnectedHandler = new Handler()
    {
        @Override
        public void handleMessage(Message msg)
        {
            super.handleMessage(msg);

            if(connectedThread != null)
                connectedThread.cancel();

            Thread retryConnectingThread = new Thread(new Runnable()
            {
                @Override
                public void run()
                {
                    while(! isConnectedToSubmarine)
                    {
                        snackbar.show();

                        try
                        {
                            Log.e(TAG, "sleeping for 30 secs");
                            Thread.sleep(30000);
                        }
                        catch (InterruptedException e)
                        {
                            e.printStackTrace();
                        }

                        if(! isConnectedToSubmarine)
                            connectToSubmarine();
                    }
                }
            });

            retryConnectingThread.start();
        }
    };

    @SuppressLint("HandlerLeak")
    Handler bluetoothConnectedHandler = new Handler()
    {
        @Override
        public void handleMessage(Message msg)
        {
            super.handleMessage(msg);

            setViewsConnected();
        }
    };

    public MainActivity()
    {
        MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        View decorView = getWindow().getDecorView();
        // Hide both the navigation bar and the status bar.
        // SYSTEM_UI_FLAG_FULLSCREEN is only available on Android 4.1 and higher, but as
        // a general rule, you should design your app to hide the status bar whenever you
        // hide the navigation bar.
//        int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
//                | View.SYSTEM_UI_FLAG_FULLSCREEN;
//        decorView.setSystemUiVisibility(uiOptions);

        decorView.setOnSystemUiVisibilityChangeListener(
                new View.OnSystemUiVisibilityChangeListener() {
                    @Override
                    public void onSystemUiVisibilityChange(int visibility) {
                        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
                        getWindow().addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
                        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

                    }
                }
        );

        initViews();

        drawSpeedometer();
        setUpJoystick();

        setViewsDisconnected();

        manageSpeed();

        connectToSubmarine();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        }
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        bluetoothDisconnectedHandler.sendEmptyMessage(0);

//        View decorView = getWindow().getDecorView();
//        // Hide both the navigation bar and the status bar.
//        // SYSTEM_UI_FLAG_FULLSCREEN is only available on Android 4.1 and higher, but as
//        // a general rule, you should design your app to hide the status bar whenever you
//        // hide the navigation bar.
//        int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
//                | View.SYSTEM_UI_FLAG_FULLSCREEN;
//        decorView.setSystemUiVisibility(uiOptions);
    }

    @Override
    protected void onPause()
    {
        super.onPause();

        if(connectedThread != null)
            connectedThread.cancel();

        if(connectThread != null)
            connectThread.cancel();
    }

    private void initViews()
    {
        connectivityStatusTV = (TextView) findViewById(R.id.connectivityStatusTextView);
        connectivityStatus = (ImageView) findViewById(R.id.connectivityStatusImage);
        batteryIndicator = (BatteryIndicatorGauge) findViewById(R.id.batteryIndicator);
        batteryPercentageTV = (TextView) findViewById(R.id.batteryPercentage);
        speedometer = (SpeedometerGauge) findViewById(R.id.speedometer);
        gasPedal = (ImageView) findViewById(R.id.gasPedal);
        brakePedal = (ImageView) findViewById(R.id.brakePedal);
        speedValueTV = (TextView) findViewById(R.id.speedValue);
        directionWheel = (HorizontalWheelView) findViewById(R.id.horizontalWheelView);
        depthWheel = (HorizontalWheelView) findViewById(R.id.verticalWheelView);
        joystick = (JoystickView) findViewById(R.id.joystickView);

        CoordinatorLayout snackBarCoordinatorLayout = (CoordinatorLayout) findViewById(snackbarCoordinatorLayout);
        snackbar = Snackbar.make(snackBarCoordinatorLayout,"Submarine Disconnected. Trying to Reconnect", Snackbar.LENGTH_INDEFINITE);
        snackbar.setAction("OK", new View.OnClickListener() {
            @Override
            public void onClick(View v)
            {   }
        });
    }

    private void drawSpeedometer()
    {
        speedometer.setLabelConverter(new SpeedometerGauge.LabelConverter() {
            @Override
            public String getLabelFor(double progress, double maxProgress) {
                return String.valueOf((int) Math.round(progress));
            }
        });

        speedometer.setMaxSpeed(300);
        speedometer.setMajorTickStep(30);
        speedometer.setMinorTicks(2);

        speedometer.addColoredRange(30, 140, Color.GREEN);
        speedometer.addColoredRange(140, 180, Color.YELLOW);
        speedometer.addColoredRange(180, 400, Color.RED);
    }

    private void setUpJoystick()
    {
        joystick.setOnMoveListener(new JoystickView.OnMoveListener()
        {
            @Override
            public void onMove(int angle, int strength)
            {
                joystickAngle = angle;
                rotateScrollbarsHandler.sendEmptyMessage(0);
            }
        });
    }

    private void setViewsDisconnected()
    {
        connectivityStatus.setImageResource(R.mipmap.disconnected_circle);
        connectivityStatusTV.setText("Disconnected");
        batteryPercentageTV.setText("-%");
        joystick.setEnabled(false);
        joystick.setAlpha(0.2f);
        directionWheel.setAlpha(0.2f);
        depthWheel.setAlpha(0.2f);
        speedometer.setAlpha(0.2f);
        gasPedal.setEnabled(false);
        gasPedal.setAlpha(0.2f);
        brakePedal.setEnabled(false);
        brakePedal.setAlpha(0.2f);
        speedValueTV.setAlpha(0.2f);
    }

    private void setViewsConnected()
    {
        connectivityStatus.setImageResource(R.mipmap.connected_circle);
        connectivityStatusTV.setText("Connected");
        joystick.setEnabled(true);
        joystick.setAlpha(1);
        directionWheel.setAlpha(1);
        depthWheel.setAlpha(1);
        speedometer.setAlpha(1);
        gasPedal.setEnabled(true);
        gasPedal.setAlpha(1.0f);
        brakePedal.setEnabled(true);
        brakePedal.setAlpha(1.0f);
        speedValueTV.setAlpha(1.0f);
        snackbar.dismiss();
    }

    private void setScrollWheelValues()
    {
        //  check for extreme cases : 0, 90, 270 (360 == 0)
        if(joystickAngle % 90 == 0)
        {
            if(joystickAngle == 0 || joystickAngle == 360)
            {
                if(directionValue < 70)
                    directionValue++;

                directionWheel.setDegreesAngle(-directionValue);
            }

            else if(joystickAngle == 90)
            {
                if (depthValue < 70)
                    depthValue++;

                depthWheel.setDegreesAngle(depthValue);
            }

            else if(joystickAngle == 180)
            {
                if(directionValue > -70)
                    directionValue--;

                directionWheel.setDegreesAngle(-directionValue);
            }

            else if(joystickAngle == 270)
            {
                if(depthValue > -70)
                    depthValue--;

                depthWheel.setDegreesAngle(depthValue);
            }
        }

        //  if joystick is in first quadrant, increment depth and direction
        else if(joystickAngle > 0 && joystickAngle < 90)
        {
            if(joystickAngle < 70)
                if(directionValue < 70)
                    directionValue++;

            if(joystickAngle > 20)
                if(depthValue < 70)
                    depthValue++;

            directionWheel.setDegreesAngle(-directionValue);
            depthWheel.setDegreesAngle(depthValue);
        }

        else if(joystickAngle > 90 && joystickAngle < 180)
        {
            if(joystickAngle > 110)
                if (directionValue > -70)
                    directionValue--;

            if(joystickAngle < 160)
                if (depthValue < 70)
                    depthValue++;

            directionWheel.setDegreesAngle(-directionValue);
            depthWheel.setDegreesAngle(depthValue);
        }

        else if(joystickAngle > 180 && joystickAngle < 270)
        {
            if(joystickAngle < 250)
                if(directionValue > -70)
                    directionValue--;

            if(joystickAngle > 200)
                if(depthValue > -70)
                    depthValue--;

            directionWheel.setDegreesAngle(-directionValue);
            depthWheel.setDegreesAngle(depthValue);
        }

        else if(joystickAngle > 270 && joystickAngle < 360)
        {
            if(joystickAngle > 290)
                if(directionValue < 70)
                    directionValue++;

            if(joystickAngle < 340)
                if(depthValue < 70)
                    depthValue--;

            directionWheel.setDegreesAngle(-directionValue);
            depthWheel.setDegreesAngle(depthValue);
        }
    }

    private void manageSpeed()
    {
        manageGasPedal();
        manageBrakePedal();
        startSpeedController();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void manageGasPedal()
    {
        gasPedal.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(final View v, MotionEvent event)
            {
                if(event.getAction() == MotionEvent.ACTION_MOVE && !isBrakePressed)
                {
                    v.setRotationX(40);
                    isGasPressed = true;
                    return true;
                }

                if(event.getAction() == MotionEvent.ACTION_UP || isBrakePressed)
                {
                    v.setRotationX(0);
                    isGasPressed = false;
                    return true;
                }

                return false;
            }
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    private void manageBrakePedal()
    {
        brakePedal.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(final View v, MotionEvent event)
            {
                if(event.getAction() == MotionEvent.ACTION_MOVE)
                {
                    v.setRotationX(40);
                    isBrakePressed = true;
                    return true;
                }

                if(event.getAction() == MotionEvent.ACTION_UP)
                {
                    v.setRotationX(0);
                    isBrakePressed = false;
                    return true;
                }

                return false;
            }
        });
    }

    private void connectToSubmarine()
    {
        Thread bluetoothCompatibility = new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                checkBluetoothCompatibility();

                while(! isBluetoothEnabledFlag);
                getBondedDevices();
            }
        });

        bluetoothCompatibility.start();
    }

    private void startSpeedController()
    {
        Thread speedControllerThread = new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                while(true)
                {
                    if(currentSpeedCounter < 0)
                        currentSpeedCounter = 0;

                    else if(isGasPressed && currentSpeedCounter < 250)
                    {
                        if(currentSpeedCounter > 210)
                            currentSpeedCounter += 0.1d;

                        else if(currentSpeedCounter > 180)
                            currentSpeedCounter += 0.2d;

                        else if(currentSpeedCounter > 150)
                            currentSpeedCounter += 0.3d;

                        else if(currentSpeedCounter > 120)
                            currentSpeedCounter += 0.4d;

                        else if(currentSpeedCounter >= 0 && currentSpeedCounter < 30)
                            currentSpeedCounter += 0.2d;

                        else if(currentSpeedCounter >= 30 && currentSpeedCounter < 60)
                            currentSpeedCounter += 0.3d;

                        else
                            currentSpeedCounter += 0.5d;
                    }

                    else if(isBrakePressed && currentSpeedCounter > 0)
                        currentSpeedCounter -= 1d;

                    else if(currentSpeedCounter > 0)
                        currentSpeedCounter -= 0.1d;

                    displaySpeedHandler.sendEmptyMessage(0);

                    try
                    {
                        Thread.sleep(50);
                    }
                    catch (InterruptedException e)
                    {
                        e.printStackTrace();
                    }
                }
            }
        });

        speedControllerThread.start();
    }

    private void checkBluetoothCompatibility()
    {
        btAdapter = BluetoothAdapter.getDefaultAdapter();

        if(btAdapter == null)
            Toast.makeText(MainActivity.this, "not supported", Toast.LENGTH_SHORT).show();

        else
        {
            if(! btAdapter.isEnabled())
            {
                Log.e(TAG, "supported hai bluetooth");

                Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBluetooth, 1);
            }

            else
                isBluetoothEnabledFlag = true;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);

        if(requestCode == RESULT_CANCELED)
        {
            Toast.makeText(this, "Bluetooth must be enabled to continue", Toast.LENGTH_SHORT).show();
            finish();
        }

        else if(resultCode == RESULT_OK)
            isBluetoothEnabledFlag = true;
    }

    private void getBondedDevices()
    {
        Set<BluetoothDevice> bondedDevices = btAdapter.getBondedDevices();

        if(bondedDevices.size() > 0)
        {
            for (BluetoothDevice device : bondedDevices)
            {
                if(device.getName().equalsIgnoreCase("HC05"))
                {
                    connectThread = new ConnectThread(device);
                    connectThread.run();
                }
            }
        }
    }

    private class ConnectThread extends Thread
    {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device)
        {
            BluetoothSocket tempSocket = null;
            mmDevice = device;

            try
            {
                tempSocket = device.createRfcommSocketToServiceRecord(MY_UUID);
            }
            catch (IOException e)
            {
                Log.e(TAG, e.getMessage());
            }

            mmSocket = tempSocket;
        }

        public void run()
        {
            try
            {
                mmSocket.connect();
                Log.e(TAG, "connected");
                bluetoothConnectedHandler.sendEmptyMessage(0);
            }
            catch (IOException connectException)
            {
                try
                {
                    mmSocket.close();
                }
                catch (IOException closeException)
                {
                    Log.e(TAG, closeException.getMessage());
                }

                cancel();

                return;
            }

            manageMyConnectedSocket(mmSocket);
        }

        void cancel()
        {
            try
            {
                mmSocket.close();
                isConnectedToSubmarine = false;
            }
            catch (IOException e)
            {
                Log.e(TAG, e.getMessage());
            }
        }
    }

    public void manageMyConnectedSocket(BluetoothSocket socket)
    {
        connectedThread = new ConnectedThread(socket);
        String ACK_REMOTE = "HELLO";
        Log.e(TAG, "sending ACK_REMOTE : " + ACK_REMOTE);
        connectedThread.write(ACK_REMOTE.getBytes());
        connectedThread.run();
    }

    private class ConnectedThread extends Thread
    {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInputStream;
        private final OutputStream mmOutputStream;
        private byte[] mmBuffer;

        ConnectedThread(BluetoothSocket socket)
        {
            mmSocket = socket;
            InputStream tempInputStream = null;
            OutputStream tempOutputStream = null;

            try
            {
                tempInputStream = socket.getInputStream();
            }
            catch (IOException e)
            {
                Log.e(TAG, "Error occurred in getting input stream", e);
            }

            try
            {
                tempOutputStream = socket.getOutputStream();
            }
            catch (IOException e)
            {
                Log.e(TAG, "Error occurred in getting output stream", e);
            }

            mmInputStream = tempInputStream;
            mmOutputStream = tempOutputStream;
        }

        public void run()
        {
            Log.e(TAG, "inside connected RUN");
            mmBuffer = new byte[1];
            int numBytes;

            isConnectedToSubmarine = true;

            while(true)
            {
                try
                {
                    numBytes = mmInputStream.read(mmBuffer);
                    messageFromSubmarine += new String(mmBuffer, 0, numBytes);

                    if(messageFromSubmarine.contains("S") && messageFromSubmarine.contains("E"))
                    {
                        messageFromSubmarine = messageFromSubmarine.substring(messageFromSubmarine.lastIndexOf("S") + 1, messageFromSubmarine.lastIndexOf("E"));
                        connectedToSubmarineHandler.sendEmptyMessage(0);
                    }
                    else
                        continue;

                    Log.e("Message from Submarine", messageFromSubmarine);

                    if(! isConnectedToSubmarine)
                    {
                        break;
                    }
                }
                catch (IOException e)
                {
                    Log.e(TAG, "Input Stream was disconnected", e);
                    bluetoothDisconnectedHandler.sendEmptyMessage(0);
                    break;
                }
            }
        }

        void write(byte[] bytes)
        {
            try
            {
                mmOutputStream.write(bytes);
                mmOutputStream.flush();
                Log.e(TAG, "sent");
            }
            catch (IOException e)
            {
                Log.e(TAG, "Error occurred while sending data", e);
            }
        }

        void cancel()
        {
            try
            {
                mmInputStream.close();
                mmOutputStream.close();
                mmSocket.close();
                isConnectedToSubmarine = false;
            }
            catch (IOException e)
            {
                Log.e(TAG, "Could not close socket", e);
            }
        }
    }
}