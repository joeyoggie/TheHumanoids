package thehumanoids.nasaspaceapps.com.dontcrashmydrone;

import android.content.Context;
import android.content.IntentSender;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Vibrator;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStates;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public class MainActivity extends AppCompatActivity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener, OnMapReadyCallback, SensorEventListener {

    //Default value set to Cairo, Egypt (will be overriden on app launch anyway)
    static double latitude = 30.048865;
    static double longitude = 31.235290;

    //mGoogleApiClient is responsible for handling connections related to Google Play Services APIs
    private GoogleApiClient mGoogleApiClient;

    //locationRequest object is responsible for handling the location request settings (refresh period and accuracy)
    LocationRequest locationRequest;

    //REQUEST_CHECK_SETTINGS is used for the dialog that will be shown if the Location Settings need to be adjusted
    static final int REQUEST_CHECK_SETTINGS = 1;

    //mLastLocation is used to store the last known location
    Location mLastLocation;

    private GoogleMap mMap;
    MapFragment mapFragment;

    //This is the marker that will mark the location of the drone on the map
    MarkerOptions marker;


    private SensorManager sManager;
    private Sensor gyroscope;

    private float lastX, lastY, lastZ;
    private Sensor accelerometer;
    private float deltaXMax = 0;
    private float deltaYMax = 0;
    private float deltaZMax = 0;

    private float deltaX = 0;
    private float deltaY = 0;
    private float deltaZ = 0;

    private float vibrateThreshold = 0;
    public Vibrator v;

    private int PORT_NUM = 7000;
    private Socket socket;
    private DataOutputStream dataOutputStream;
    private DataInputStream dataInputStream;
    private String response = null;
    private String line = null;
    private String ipAddress = "192.168.3.62";

    private socketclient sckclient;
    private sendSocketclient sndSckClient;

    boolean connectedToServer;

    String dataToSend;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Initialize the mGoogleApiClient object if it's null, and make sure to pass LocationServices API parameter
        if(mGoogleApiClient ==  null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addApi(LocationServices.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();
        }

        buildLocationSettingsRequest();

        mapFragment = (MapFragment) getFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);


        //get a hook to the sensor service
        sManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        //Register the Gyroscope sensor, if available
        if (sManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null) {
            // success! we have a gyroscope

            gyroscope = sManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            sManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_NORMAL);
        } else {
            // fai! we dont have a gyroscope!
            Toast.makeText(this, "Whops Your Device Doesn't have a Gyroscope", Toast.LENGTH_SHORT).show();
        }

        //Register the Accelerometer sensor, if available
        if (sManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null) {
            // success! we have an accelerometer
            accelerometer = sManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            sManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
            vibrateThreshold = accelerometer.getMaximumRange() / 2;
        } else {
            // fai! we dont have an accelerometer!
            Toast.makeText(this, "Whops Your Device Doesn't have an Accelerometer", Toast.LENGTH_SHORT).show();
        }

        //initialize vibration
        v = (Vibrator) this.getSystemService(Context.VIBRATOR_SERVICE);


        sckclient = new socketclient();
        sndSckClient = new sendSocketclient();

        new socketclient().start();
    }

    //Send the location to the webserver/operator
    private void sendToOperator() {

        if(connectedToServer == false) {
            new sendSocketclient().start();
        }
        else
        {
            new sendSocketclient().start();
        }

        /*String url = "http://192.168.1.44:8080/DontCrashMyDrone/droneParameters?longitude="+longitude+"&latitude="+latitude;

        //Request a string response from the provided URL.
        StringRequest request = new StringRequest(Request.Method.GET, url, new Response.Listener<String>(){
            @Override
            public void onResponse(String response) {
                if(response.length() > 0) {

                }
                else {
                    Log.d("Volley", "Received an empty response from server.");
                    Toast.makeText(MainActivity.this, "Received an empty response from server.", Toast.LENGTH_SHORT).show();
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.d("Volley","Volley response error.");
                Toast.makeText(MainActivity.this, "Volley response error.", Toast.LENGTH_SHORT).show();
            }
        });
        VolleyConnector.getInstance(this).addToRequestQueue(request);*/
    }

    @Override
    public void onLocationChanged(Location location) {
        //This is the callback that will handle the current location of the user which is updated every 5 seconds
        mLastLocation = location;
        longitude = mLastLocation.getLongitude();
        latitude = mLastLocation.getLatitude();
        Log.d("Latitude: ", String.valueOf(latitude));
        Log.d("Longitude: ", String.valueOf(longitude));
        displayCurrentValues();
        displayCurrentGyroValues();
        //dataToSend = "latitude="+latitude+"&longitude="+longitude+"&AccX="+deltaX+"&AccY="+deltaY+"&AccZ="+deltaZ+"&OrienX="+lastX+"&OrientY="+lastY+"&OrientZ="+lastZ;

        //Data that will be sent to the server
        dataToSend = latitude+","+longitude+","+deltaX+","+deltaY+","+deltaZ+","+lastX+","+lastY+","+lastZ;
        sendToOperator();
        mapFragment.getMapAsync(this);
    }

    private void buildLocationSettingsRequest() {
        //Initialize the locationRequest object and set the preferred interval to 5 seconds, and same for fastest interval
        //Also, use the HIGH_ACCURACY parameter to get a more precise location
        locationRequest = new LocationRequest();
        locationRequest.setInterval(2000);
        locationRequest.setFastestInterval(2000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        //Check whether the device's settings are enough to get a GPS position
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                .addLocationRequest(locationRequest);

        final PendingResult<LocationSettingsResult> result =
                LocationServices.SettingsApi.checkLocationSettings(mGoogleApiClient, builder.build());

        result.setResultCallback(new ResultCallback<LocationSettingsResult>() {
            @Override
            public void onResult(LocationSettingsResult result) {
                final Status status = result.getStatus();
                final LocationSettingsStates locationStates = result.getLocationSettingsStates();
                final int statusCode = status.getStatusCode();
                if(statusCode == LocationSettingsStatusCodes.SUCCESS)
                {
                    //Get the last known GPS location
                    mLastLocation = LocationServices.FusedLocationApi.getLastLocation(
                            mGoogleApiClient);
                    if (mLastLocation != null) {
                        latitude = mLastLocation.getLatitude();
                        longitude = mLastLocation.getLongitude();
                        mapFragment.getMapAsync(MainActivity.this);
                    }
                    else {
                        Toast.makeText(getApplicationContext(),"Please make sure your GPS is turned on",Toast.LENGTH_SHORT).show();
                    }
                }
                else if(statusCode == LocationSettingsStatusCodes.RESOLUTION_REQUIRED) {
                    //Show a dialog to change the Location Settings
                    try {
                        status.startResolutionForResult(MainActivity.this, REQUEST_CHECK_SETTINGS);
                    } catch (IntentSender.SendIntentException e) {
                        e.printStackTrace();
                    }
                }
                else if(statusCode == LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE) {
                    Toast.makeText(MainActivity.this, "Unable to use location settings!", Toast.LENGTH_SHORT);
                }
            }
        });
    }

    @Override
    public void onMapReady(GoogleMap map){
        //Show the user location on the map
        //map.setMyLocationEnabled(true);
        //Clear all old markers
        map.clear();

        //Add current marker (after getting the location from the operator)
        marker = new MarkerOptions()
                .position(new LatLng(latitude, longitude))
                .snippet("This is the current location of the drone.")
                .title("Current drone location")
                .icon(BitmapDescriptorFactory.fromResource(R.drawable.drone56))
                .flat(true);
        map.addMarker(marker)
                /*.showInfoWindow()*/;

        map.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(latitude, longitude), 15));
    }

    @Override
    public void onConnected(Bundle connectionHint) {
        // Connected to Google Play services!
        // The good stuff goes here.
        //Start listening for location updates, which will be resolved in the LocationListener.onLocationChanged callback
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, locationRequest, this);
    }

    @Override
    public void onConnectionSuspended(int cause) {
        // The connection has been interrupted.
        // Disable any UI components that depend on Google APIs
        // until onConnected() is called.
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        // This callback is important for handling errors that
        // may occur while attempting to connect with Google.
        //
        // More about this in the 'Handle Connection Failures' section.
        Toast.makeText(getApplicationContext(), "Connection to Google Play Services failed.", Toast.LENGTH_SHORT);
    }

    @Override
    protected void onStart() {
        super.onStart();
        //Connect to the Google Play Services
        mGoogleApiClient.connect();
    }

    @Override
    protected void onStop() {
        //Disconnect from the Google Play Services
        mGoogleApiClient.disconnect();
        super.onStop();

        //unregister the sensor listener
        sManager.unregisterListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        //Stop listening for locations to save battery
        LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
        //onPause() unregister the sensors for stop listening the events
        sManager.unregisterListener(this);
    }

    //when this Activity starts
    @Override
    protected void onResume() {
        super.onResume();
        /*register the sensor listener to listen to the gyroscope/accelerometer sensor, use the
        callbacks defined in this class, and gather the sensor information as quick
        as possible*/
        sManager.registerListener(this, sManager.getDefaultSensor(Sensor.TYPE_ORIENTATION),SensorManager.SENSOR_DELAY_NORMAL);
        sManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    public void onAccuracyChanged(Sensor arg0, int arg1) {
        //Do nothing.
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        Sensor sensor = event.sensor;
        if (sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            // clean current values
            displayCleanValues();
            // display the current x,y,z accelerometer values
            //displayCurrentValues();
            // display the max x,y,z accelerometer values
            displayMaxValues();

            // get the change of the x,y,z values of the accelerometer
            deltaX = lastX - event.values[0];
            deltaY = lastY - event.values[1];
            deltaZ = lastZ - event.values[2];

            // if the change is below 2, it is just plain noise
            if ((deltaX < 0.5) & (deltaX > 0))
                deltaX = 0;
            if ((deltaY < 0.8) & (deltaY > 0))
                deltaY = 0;
            if ((deltaZ < 1.2) & (deltaZ > 0))
                deltaZ = 0;

            /*if(deltaZ > (vibrateThreshold - 3)) {
                v.vibrate(new long[]{0, 100}, -1);
                turnedON = !turnedON;
                if(turnedON)
                    flashLightOn();
                else
                    flashLightOff();
            }*/
        }
        else if (sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            //if sensor is unreliable, return void
            if (event.accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE)
                Toast.makeText(this, "Whops Your Device Doesn't have a Gyroscope", Toast.LENGTH_LONG).show();

            //else it will output the Roll, Pitch and Yawn values
            lastX = event.values[2];
            lastY = event.values[1];
            lastZ = event.values[0];
            //displayCurrentGyroValues();
        /*tv.setText("Orientation X (Roll) :" + Float.toString(event.values[2]) + "\n"+
                "Orientation Y (Pitch) :" + Float.toString(event.values[1]) + "\n"+
                "Orientation Z (Yaw) :" + Float.toString(event.values[0]));*/
        }

    }

    /*public void flashLightOn() {
        try {
            if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) {
                cam = Camera.open();
                Camera.Parameters p = cam.getParameters();
                p.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                cam.setParameters(p);
                cam.startPreview();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(getBaseContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }*/

    /*public void flashLightOff() {
        try {
            if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) {
                Camera.Parameters p = cam.getParameters();
                p.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                cam.setParameters(p);
                cam.stopPreview();
                cam.release();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(getBaseContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }*/

    public void displayCleanValues() {
        /*currentX.setText("0.0");
        currentY.setText("0.0");
        currentZ.setText("0.0");*/
    }

    // display the current x,y,z accelerometer values
    public void displayCurrentValues() {
        /*currentX.setText(Float.toString(deltaX));
        currentY.setText(Float.toString(deltaY));
        currentZ.setText(Float.toString(deltaZ));*/
        Log.d("AccX", Float.toString(deltaX));
        Log.d("AccY", Float.toString(deltaY));
        Log.d("AccZ", Float.toString(deltaZ));
    }

    public void displayCurrentGyroValues(){
        Log.d("Orientation X (Roll)", Float.toString(lastX));
        Log.d("Orientation Y (Pitch)", Float.toString(lastY));
        Log.d("Orientation Z (Yaw)", Float.toString(lastZ));
    }

    // display the max x,y,z accelerometer values
    public void displayMaxValues() {
        if (deltaX > deltaXMax) {
            deltaXMax = deltaX;
            //maxX.setText(Float.toString(deltaXMax));
        }
        if (deltaY > deltaYMax) {
            deltaYMax = deltaY;
            //maxY.setText(Float.toString(deltaYMax));
        }
        if (deltaZ > deltaZMax) {
            deltaZMax = deltaZ;
            //maxZ.setText(Float.toString(deltaZMax));
        }
    }

    private class socketclient extends Thread {
        public void run() {
            try {
                socket = new Socket(ipAddress, PORT_NUM);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        /*ipText.setEnabled(false);
                        buttonConnect.setText("Connected");
                        buttonConnect.setEnabled(false);*/
                        connectedToServer = true;
                        Toast.makeText(MainActivity.this, "Connected", Toast.LENGTH_SHORT).show();
                    }
                });
            }
            catch (IOException e){
                Log.e(">> ", e.getMessage());
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        /*new AlertDialog.Builder(MainActivity.this)
                                .setTitle("Error")
                                .setMessage("connection refused")
                                .setPositiveButton("OK",
                                        new DialogInterface.OnClickListener() {
                                            public void onClick(DialogInterface dialog, int id) {
                                                dialog.cancel();
                                            }
                                        })
                                .show();*/
                        /*buttonConnect.setEnabled(true);
                        buttonConnect.setText("Connect");
                        ipText.setEnabled(true);*/
                        connectedToServer = false;
                        Toast.makeText(MainActivity.this, "Error, connection refused!", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }
    }

    private class sendSocketclient extends Thread {

        public void run() {
            try {
                dataOutputStream = new DataOutputStream(socket.getOutputStream());
                dataInputStream = new DataInputStream(socket.getInputStream());

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        //line = textOut.getText().toString();//insert data here
                        line = dataToSend;
                        Log.d("DATA", dataToSend);
                        try {
                            dataOutputStream.writeUTF(line);
                            dataOutputStream.flush();
                        }
                        catch (IOException e1) {
                            /*new AlertDialog.Builder(MainActivity.this)
                                    .setTitle("Error")
                                    .setMessage("server disconnected\nplease try again later...")
                                    .setPositiveButton("OK",
                                            new DialogInterface.OnClickListener() {
                                                public void onClick(DialogInterface dialog, int id) {
                                                    dialog.cancel();
                                                }
                                            })
                                    .show();*/
                            /*buttonConnect.setEnabled(true);
                            buttonConnect.setText("Connect");
                            ipText.setEnabled(true);*/
                            connectedToServer = false; //TODO garab sheel deh we e3mel el check foo2 fe sendToOperator if() bas
                            Toast.makeText(MainActivity.this, "Server disconnected. Try again later.", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
                response = dataInputStream.readUTF();
                if(!(response.equals(""))) {
                    response = response + "\n";
                }
                else
                    response = "";
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if(socket != null) {
                            if(response != null)
                                //textIn.append(response);
                                Toast.makeText(MainActivity.this, response, Toast.LENGTH_SHORT).show();
                            //textOut.setText(null);
                            /*scrlView.post(new Runnable() {
                                @Override
                                public void run() {
                                    scrlView.fullScroll(View.FOCUS_DOWN);
                                }
                            });*/
                        }
                    }
                });
            } catch(IOException e) {
                Log.wtf("Error: ", "IO Socket read Error: " + e.getMessage());
            } catch(RuntimeException e) {
                Log.wtf("Error: ", "RT Socket read Error: " + e.getMessage());
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        /*new AlertDialog.Builder(MainActivity.this)
                                .setTitle("Error")
                                .setMessage("can't reach server\nplease try again later...")
                                .setPositiveButton("OK",
                                        new DialogInterface.OnClickListener() {
                                            public void onClick(DialogInterface dialog, int id) {
                                                dialog.cancel();
                                            }
                                        })
                                .show();*/
                        /*buttonConnect.setEnabled(true);
                        buttonConnect.setText("Connect");
                        ipText.setEnabled(true);*/
                        connectedToServer = false;
                        Toast.makeText(MainActivity.this, "Can't reach server, please try again later.", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }
    }
}
