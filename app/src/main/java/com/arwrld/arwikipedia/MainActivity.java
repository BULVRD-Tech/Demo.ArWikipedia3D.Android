package com.arwrld.arwikipedia;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.net.Uri;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Range;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.arwrld.arwikipedia.ar.BackgroundRenderer;
import com.arwrld.arwikipedia.ar.DisplayRotationHelper;
import com.arwrld.arwikipedia.ar.ObjectRenderer;
import com.arwrld.arwikipedia.location.LocationApi;
import com.arwrld.arwikipedia.models.WikiResponse;
import com.arwrld.arwikipedia.models.ar.MarkerInfo;
import com.arwrld.arwikipedia.utils.Constants;
import com.google.ar.core.Camera;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingState;
import com.koushikdutta.async.future.FutureCallback;
import com.koushikdutta.ion.Ion;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;
import javax.vecmath.Vector3f;

import io.nlopez.smartlocation.OnLocationUpdatedListener;
import permissions.dispatcher.NeedsPermission;
import permissions.dispatcher.OnNeverAskAgain;
import permissions.dispatcher.OnPermissionDenied;
import permissions.dispatcher.OnShowRationale;
import permissions.dispatcher.PermissionRequest;
import permissions.dispatcher.RuntimePermissions;

@RuntimePermissions
public class MainActivity extends AppCompatActivity implements GLSurfaceView.Renderer, SensorEventListener {

    Context mContext;
    private GLSurfaceView mSurfaceView;
    private LinearLayout banner;
    private Session mSession;
    private Config mDefaultConfig;
    private BackgroundRenderer mBackgroundRenderer = new BackgroundRenderer();
    private GestureDetector mGestureDetector;
    private ObjectRenderer mVirtualObject = new ObjectRenderer();
    private DisplayRotationHelper mDisplayRotationHelper;

    private final float[] mAnchorMatrix = new float[16];

    // Tap handling and UI.
    private ArrayBlockingQueue<MotionEvent> mQueuedSingleTaps = new ArrayBlockingQueue<>(16);

    //Location-based stuff
    private SensorManager mSensorManager;

    public Location mLocation;

    private List<MarkerInfo> mMarkerList;
    private float[] mZeroMatrix = new float[16];

    float[] translation = new float[]{0.0f, -0.8f, -0.8f};
    float[] rotation = new float[]{0.0f, -1.00f, 0.0f, 0.3f};

    Pose mPose = new Pose(translation, rotation);

    private int attrSize = 0;
    private static final String extraVal = "ArWrldWikiArCoreDemo";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = this;
        setContentView(R.layout.activity_main);
        mSurfaceView = findViewById(R.id.surfaceview);
        banner = findViewById(R.id.banner);

        mSensorManager = (SensorManager) this.getSystemService(Context.SENSOR_SERVICE);
        mSession = new Session(this);
        mDefaultConfig = new Config(mSession);
        if (!mSession.isSupported(mDefaultConfig)) {
            Toast.makeText(this, "This device does not support AR", Toast.LENGTH_LONG).show();
        }
        mSession.configure(mDefaultConfig);
        mDisplayRotationHelper = new DisplayRotationHelper(this);

        Matrix.setIdentityM(mZeroMatrix, 0);
        mPose.toMatrix(mAnchorMatrix, 0);

        // Set up tap listener.
        mGestureDetector = new GestureDetector(mContext, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                onSingleTap(e);
                return true;
            }

            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }
        });

        mSurfaceView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return mGestureDetector.onTouchEvent(event);
            }
        });

        // Set up renderer.
        mSurfaceView.setPreserveEGLContextOnPause(true);
        mSurfaceView.setEGLContextClientVersion(2);
        mSurfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        mSurfaceView.setRenderer(this);
        mSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

        mMarkerList = new ArrayList<>();

        banner.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse("http://arwrld.com" + "?ref=" + extraVal + "?utm_source=" + extraVal + "?from=" + extraVal));
                mContext.startActivity(i);
            }
        });
    }

    private void runQuery() {
        String url = "https://en.wikipedia.org/w/api.php?action=query&list=geosearch&format=json&gscoord="
                + mLocation.getLatitude() + "%7C" + mLocation.getLongitude() + "&gsradius=10000&gslimit=20";

        Ion.with(mContext).load(url)
                .asString()
                .setCallback(new FutureCallback<String>() {
                    @Override
                    public void onCompleted(Exception e, String result) {
                        if (e != null) {
                            Toast.makeText(mContext, "Error loading tweets", Toast.LENGTH_LONG).show();
                            return;
                        }
                        WikiResponse wikiResponse = Constants.gson.fromJson(result, WikiResponse.class);
                        attrSize = wikiResponse.getQuery().getGeosearch().size();

                        for (int i = 0; i < attrSize; i++) {
                            mMarkerList.add(new MarkerInfo(wikiResponse.getQuery().getGeosearch().get(i)));
                            Log.d("ARCore", wikiResponse.getQuery().getGeosearch().get(i).getTitle());
                        }

                        MarkerInfo marker;
                        for (int i = 0; i < mMarkerList.size(); i++) {
                            marker = mMarkerList.get(i);
                            marker.setDistance(mLocation.distanceTo(marker.returnLocation()));
                        }

                    }
                });
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            // Standard Android full-screen functionality.
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        MainActivityPermissionsDispatcher.showCameraWithPermissionCheck(MainActivity.this);
    }

    @Override
    public void onPause() {
        super.onPause();

        mSurfaceView.onPause();
        mSession.pause();
        LocationApi.killAllUpdateListeners(mContext);
        mSensorManager.unregisterListener(this);
        mDisplayRotationHelper.onPause();
    }

    private void registerSensors() {
        mSensorManager.registerListener(this,
                mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR),
                SensorManager.SENSOR_DELAY_FASTEST);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {

            float azimuth, pitch, bearing;
            Range<Float> azimuthRange, pitchRange;

            float[] rotationMatrixFromVector = new float[16];
            float[] updatedRotationMatrix = new float[16];
            float[] orientationValues = new float[3];

            SensorManager.getRotationMatrixFromVector(rotationMatrixFromVector, event.values);
            SensorManager
                    .remapCoordinateSystem(rotationMatrixFromVector,
                            SensorManager.AXIS_X, SensorManager.AXIS_Y,
                            updatedRotationMatrix);
            SensorManager.getOrientation(updatedRotationMatrix, orientationValues);

            if (mMarkerList.isEmpty()) {
                return;
            }

            for (int i = 0; i < mMarkerList.size(); i++) {

                MarkerInfo marker = mMarkerList.get(i);

                bearing = mLocation.bearingTo(marker.returnLocation());
                azimuth = (float) Math.toDegrees(orientationValues[0]);
                pitch = (float) Math.toDegrees(orientationValues[1]);

                azimuthRange = new Range<>(bearing - 10, bearing + 10);
                pitchRange = new Range<>(-90.0f, -45.0f);

                if (azimuthRange.contains(azimuth) && pitchRange.contains(pitch)) {
                    marker.setInRange(true);
                } else {
                    marker.setInRange(false);
                }
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);

        // Create the texture and pass it to ARCore session to be filled during update().
        mBackgroundRenderer.createOnGlThread(this);
        if (mSession != null) {
            mSession.setCameraTextureName(mBackgroundRenderer.getTextureId());
        }

        // Prepare the other rendering objects.
        try {
            mVirtualObject.createOnGlThread(this, "earth_ball.obj", "earth_ball.jpg");
            mVirtualObject.setMaterialProperties(0.0f, 3.5f, 1.0f, 6.0f);
        } catch (IOException e) {
            Log.e("AR", "Failed to read obj file");
        }

    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        mDisplayRotationHelper.onSurfaceChanged(width, height);
        GLES20.glViewport(0, 0, width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        // Clear screen to notify driver it should not load any pixels from previous frame.
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        if (mSession == null) {
            return;
        }
        // Notify ARCore session that the view size changed so that the perspective matrix and
        // the video background can be properly adjusted.
        mDisplayRotationHelper.updateSessionIfNeeded(mSession);

        try {
            // Obtain the current frame from ARSession. When the configuration is set to
            // UpdateMode.BLOCKING (it is by default), this will throttle the rendering to the
            // camera framerate.
            Frame frame = mSession.update();
            Camera camera = frame.getCamera();

            MotionEvent tap = mQueuedSingleTaps.poll();

            // Draw background.
            mBackgroundRenderer.draw(frame);

            // If not tracking, don't draw 3d objects.
            if (camera.getTrackingState() == TrackingState.PAUSED) {
                return;
            }

            // Get projection matrix.
            float[] projmtx = new float[16];
            camera.getProjectionMatrix(projmtx, 0, 0.1f, 100.0f);

            // Get camera matrix and draw.
            float[] viewmtx = new float[16];
            camera.getViewMatrix(viewmtx, 0);

            // Compute lighting from average intensity of the image.
            final float lightIntensity = frame.getLightEstimate().getPixelIntensity();

            float scaleFactor = 0.02f;

            MarkerInfo marker;

            if (mMarkerList.isEmpty()) {
                return;
            }

            for (int i = 0; i < mMarkerList.size(); i++) {

                marker = mMarkerList.get(i);

                if (marker.getInRange()) {
                    if (marker.getZeroMatrix() == null) {
                        marker.setZeroMatrix(getCalibrationMatrix(frame));
                    }
                }

                if (marker.getZeroMatrix() == null) {
                    break;
                }

                Matrix.multiplyMM(viewmtx, 0, viewmtx, 0, marker.getZeroMatrix(), 0);

                mVirtualObject.updateModelMatrix(mAnchorMatrix, scaleFactor);
                mVirtualObject.draw(viewmtx, projmtx, lightIntensity);

                if (tap != null) {
                    String url = "https://en.wikipedia.org/?curid=" + marker.geosearch.getPageid();
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setData(Uri.parse(url));
                    mContext.startActivity(intent);
                }
            }

        } catch (Throwable t) {
            // Avoid crashing the application due to unhandled exceptions.
            Log.e("ARCore", "Exception on the OpenGL thread", t);
        }
    }

    private void onSingleTap(MotionEvent e) {
        // Queue tap if there is space. Tap is lost if queue is full.
        mQueuedSingleTaps.offer(e);
    }

    public float[] getCalibrationMatrix(Frame frame) {
        float[] t = new float[3];
        float[] m = new float[16];

        frame.getCamera().getPose().getTranslation(t, 0);
        float[] z = frame.getCamera().getPose().getZAxis();
        Vector3f zAxis = new Vector3f(z[0], z[1], z[2]);
        zAxis.y = 0;
        zAxis.normalize();

        double rotate = Math.atan2(zAxis.x, zAxis.z);

        Matrix.setIdentityM(m, 0);
        Matrix.translateM(m, 0, t[0], t[1], t[2]);
        Matrix.rotateM(m, 0, (float) Math.toDegrees(rotate), 0, 1, 0);
        return m;
    }

    @NeedsPermission({Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION})
    void showCamera() {
        if(mSession != null) {
            mSession.resume();
        }
        mSurfaceView.onResume();
        mDisplayRotationHelper.onResume();

        LocationApi.setUpLocationUpdates(mContext,
                new OnLocationUpdatedListener() {
                    @Override
                    public void onLocationUpdated(Location location) {
                        if (mLocation == null) {
                            mLocation = location;
                            registerSensors();
                        } else {
                            mLocation = location;
                        }

                        runQuery();
                    }
                });
    }

    @OnShowRationale({Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION})
    void showRationaleForCamera(final PermissionRequest request) {

    }

    @OnPermissionDenied({Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION})
    void showDeniedForCamera() {
        Toast.makeText(mContext, "Augmented Reality features require camera permissions!", Toast.LENGTH_SHORT).show();
    }

    @OnNeverAskAgain({Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION})
    void showNeverAskForCamera() {
        Toast.makeText(mContext, "Augmented Reality features require camera permissions!", Toast.LENGTH_SHORT).show();
    }

}
