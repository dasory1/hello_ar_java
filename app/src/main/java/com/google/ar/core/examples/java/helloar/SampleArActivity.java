/*
 * Copyright 2017 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ar.core.examples.java.helloar;

import collision_detection.Object_Info;
import collision_detection.TrackingSystem;
import od_task.*;

import android.content.DialogInterface;
import android.content.res.Resources;
import android.media.Image;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.ar.core.Anchor;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.ArCoreApk.Availability;
import com.google.ar.core.Camera;
import com.google.ar.core.CameraIntrinsics;
import com.google.ar.core.Config;
import com.google.ar.core.Config.InstantPlacementMode;
import com.google.ar.core.Frame;
import com.google.ar.core.LightEstimate;
import com.google.ar.core.Plane;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import com.google.ar.core.examples.java.common.helpers.CameraPermissionHelper;
import com.google.ar.core.examples.java.common.helpers.DepthSettings;
import com.google.ar.core.examples.java.common.helpers.DisplayRotationHelper;
import com.google.ar.core.examples.java.common.helpers.FullScreenHelper;
import com.google.ar.core.examples.java.common.helpers.InstantPlacementSettings;
import com.google.ar.core.examples.java.common.helpers.SnackbarHelper;
import com.google.ar.core.examples.java.common.helpers.TapHelper;
import com.google.ar.core.examples.java.common.helpers.TrackingStateHelper;
import com.google.ar.core.examples.java.common.samplerender.Framebuffer;
import com.google.ar.core.examples.java.common.samplerender.GLError;
import com.google.ar.core.examples.java.common.samplerender.Mesh;
import com.google.ar.core.examples.java.common.samplerender.SampleRender;
import com.google.ar.core.examples.java.common.samplerender.Shader;
import com.google.ar.core.examples.java.common.samplerender.Texture;
import com.google.ar.core.examples.java.common.samplerender.VertexBuffer;
import com.google.ar.core.examples.java.common.samplerender.arcore.BackgroundRenderer;
import com.google.ar.core.examples.java.common.samplerender.arcore.PlaneRenderer;
import com.google.ar.core.examples.java.common.samplerender.arcore.SpecularCubemapFilter;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;

import org.tensorflow.lite.Delegate;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.GpuDelegate;
import org.tensorflow.lite.gpu.GpuDelegateFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.nio.ShortBuffer;
/**
 * This is a simple example that shows how to create an augmented reality (AR) application using the
 * ARCore API. The application will display any detected planes and will allow the user to tap on a
 * plane to place a 3D model.
 */
public class SampleArActivity extends AppCompatActivity implements SampleRender.Renderer {

    private static final String TAG = SampleArActivity.class.getSimpleName();

    private static final String SEARCHING_PLANE_MESSAGE = "Searching for surfaces...";
    private static final String WAITING_FOR_TAP_MESSAGE = "Tap on a surface to place an object.";

    // See the definition of updateSphericalHarmonicsCoefficients for an explanation of these
    // constants.
    private static final float[] sphericalHarmonicFactors = {
            0.282095f,
            -0.325735f,
            0.325735f,
            -0.325735f,
            0.273137f,
            -0.273137f,
            0.078848f,
            -0.273137f,
            0.136569f,
    };

    private static final float Z_NEAR = 0.1f;
    private static final float Z_FAR = 100f;

    private static final int CUBEMAP_RESOLUTION = 16;
    private static final int CUBEMAP_NUMBER_OF_IMPORTANCE_SAMPLES = 32;

    // Rendering. The Renderers are created here, and initialized when the GL surface is created.
    private GLSurfaceView surfaceView;

    private boolean installRequested;

    private Session session;
    private final SnackbarHelper messageSnackbarHelper = new SnackbarHelper();
    private DisplayRotationHelper displayRotationHelper;
    private final TrackingStateHelper trackingStateHelper = new TrackingStateHelper(this);
    private TapHelper tapHelper;
    private SampleRender render;

    private PlaneRenderer planeRenderer;
    private BackgroundRenderer backgroundRenderer;
    private Framebuffer virtualSceneFramebuffer;
    private boolean hasSetTextureNames = false;

    private final DepthSettings depthSettings = new DepthSettings();
    private boolean[] depthSettingsMenuDialogCheckboxes = new boolean[2];

    private final InstantPlacementSettings instantPlacementSettings = new InstantPlacementSettings();
    private boolean[] instantPlacementSettingsMenuDialogCheckboxes = new boolean[1];
    // Assumed distance from the device camera to the surface on which user will try to place objects.
    // This value affects the apparent scale of objects while the tracking method of the
    // Instant Placement point is SCREENSPACE_WITH_APPROXIMATE_DISTANCE.
    // Values in the [0.2, 2.0] meter range are a good choice for most AR experiences. Use lower
    // values for AR experiences where users are expected to place objects on surfaces close to the
    // camera. Use larger values for experiences where the user will likely be standing and trying to
    // place an object on the ground or floor in front of them.
    private static final float APPROXIMATE_DISTANCE_METERS = 2.0f;

    // Point Cloud
    private VertexBuffer pointCloudVertexBuffer;
    private Mesh pointCloudMesh;
    private Shader pointCloudShader;
    // Keep track of the last point cloud rendered to avoid updating the VBO if point cloud
    // was not changed.  Do this using the timestamp since we can't compare PointCloud objects.
    private long lastPointCloudTimestamp = 0;

    // Virtual object (ARCore pawn)
    private Mesh virtualObjectMesh;
    private Shader virtualObjectShader;
    private Texture virtualObjectAlbedoTexture;
    private Texture virtualObjectAlbedoInstantPlacementTexture;

    //Detection result
    private Mesh detectionMesh ;
    private Shader detectionShader ;
    private final List<WrappedAnchor1> wrappedAnchors = new ArrayList<>();

    // Environmental HDR
    private Texture dfgTexture;
    private SpecularCubemapFilter cubemapFilter;

    // Temporary matrix allocated here to reduce number of allocations for each frame.
    private final float[] modelMatrix = new float[16];
    private final float[] viewMatrix = new float[16];
    private final float[] projectionMatrix = new float[16];
    private final float[] modelViewMatrix = new float[16]; // view x model
    private final float[] modelViewProjectionMatrix = new float[16]; // projection x view x model
    private final float[] sphericalHarmonicsCoefficients = new float[9 * 3];
    private final float[] viewInverseMatrix = new float[16];
    private final float[] worldLightDirection = {0.0f, 0.0f, 0.0f, 0.0f};
    private final float[] viewLightDirection = new float[4]; // view x world light direction

    private MappedByteBuffer modelBuffer;
    private String modelhash, nativeLibraryDir, CacheDir;

    String modelPath = "efficientdet.tflite";   //"yolo_v5.tflite"
    String labelPath = "labels.txt";            //"coco_labels_2014_2017.txt"
    private final DetectionOptions extra_options = new DetectionOptions(5, 0.5f);
    private OD_processor odProcessor;
    private Labelloader labelload;
    private Interpreter TFInterpreter;
    private GpuDelegate gpuDelegate;
    private ObjectDetectionOverlay overlayView;
    List<Detectionclass> OD_detections;
    TrackingSystem trackingSystem;
    long current_time;
    long prev_time =0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        surfaceView = findViewById(R.id.surfaceview);
        displayRotationHelper = new DisplayRotationHelper(/* context= */ this);
        trackingSystem = new TrackingSystem(this, overlayView);
        //OD Model SET UP
        try {
            modelBuffer = ModelLoader.loadModelFile(this, modelPath);
            modelhash = ModelLoader.calculateModelHash(this, modelPath);
            nativeLibraryDir =  this.getApplicationInfo().nativeLibraryDir;
            CacheDir = this.getCacheDir().getAbsolutePath();
            labelload = new Labelloader(this, labelPath); //coco paper dataset labels
            OD_detections = new ArrayList<>();

        } catch (IOException | NoSuchAlgorithmException e) {
            e.printStackTrace();
        }

        overlayView = findViewById(R.id.overlay_view);

        // Set up touch listener.
        tapHelper = new TapHelper(/* context= */ this);
        surfaceView.setOnTouchListener(tapHelper);

        // Set up renderer.
        render = new SampleRender(surfaceView, this, getAssets());

        installRequested = false;

        depthSettings.onCreate(this);
        instantPlacementSettings.onCreate(this);
        ImageButton settingsButton = findViewById(R.id.settings_button);
        settingsButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        PopupMenu popup = new PopupMenu(SampleArActivity.this, v);
                        popup.setOnMenuItemClickListener(SampleArActivity.this::settingsMenuClick);
                        popup.inflate(R.menu.settings_menu);
                        popup.show();
                    }
                });
    }

    /** Menu button to launch feature specific settings. */
    protected boolean settingsMenuClick(MenuItem item) {
        if (item.getItemId() == R.id.depth_settings) {
            launchDepthSettingsMenuDialog();
            return true;
        } else if (item.getItemId() == R.id.instant_placement_settings) {
            launchInstantPlacementSettingsMenuDialog();
            return true;
        }
        return false;
    }

    @Override
    protected void onDestroy() {
        if (session != null) {
            // Explicitly close ARCore Session to release native resources.
            // Review the API reference for important considerations before calling close() in apps with
            // more complicated lifecycle requirements:
            // https://developers.google.com/ar/reference/java/arcore/reference/com/google/ar/core/Session#close()
            session.close();
            session = null;
        }
        if (TFInterpreter != null) {
            TFInterpreter.close();
            TFInterpreter = null;
            Log.d("Message","Interpreter is closed : ondestroy");
        }
        if (gpuDelegate != null) {
            gpuDelegate.close();
            gpuDelegate = null;
        }
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (session == null) {
            Exception exception = null;
            String message = null;
            try {
                // Always check the latest availability.
                Availability availability = ArCoreApk.getInstance().checkAvailability(this);

                // In all other cases, try to install ARCore and handle installation failures.
                if (availability != Availability.SUPPORTED_INSTALLED) {
                    switch (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
                        case INSTALL_REQUESTED:
                            installRequested = true;
                            return;
                        case INSTALLED:
                            break;
                    }
                }

                // ARCore requires camera permissions to operate. If we did not yet obtain runtime
                // permission on Android M and above, now is a good time to ask the user for it.
                if (!CameraPermissionHelper.hasCameraPermission(this)) {
                    CameraPermissionHelper.requestCameraPermission(this);
                    return;
                }

                // Create the session.
                session = new Session(/* context= */ this);
            } catch (UnavailableArcoreNotInstalledException
                     | UnavailableUserDeclinedInstallationException e) {
                message = "Please install ARCore";
                exception = e;
            } catch (UnavailableApkTooOldException e) {
                message = "Please update ARCore";
                exception = e;
            } catch (UnavailableSdkTooOldException e) {
                message = "Please update this app";
                exception = e;
            } catch (UnavailableDeviceNotCompatibleException e) {
                message = "This device does not support AR";
                exception = e;
            } catch (Exception e) {
                message = "Failed to create AR session";
                exception = e;
            }

            if (message != null) {
                messageSnackbarHelper.showError(this, message);
                Log.e(TAG, "Exception creating session", exception);
                return;
            }
        }

        // Note that order matters - see the note in onPause(), the reverse applies here.
        try {
            configureSession();
            // To record a live camera session for later playback, call
            // `session.startRecording(recordingConfig)` at anytime. To playback a previously recorded AR
            // session instead of using the live camera feed, call
            // `session.setPlaybackDatasetUri(Uri)` before calling `session.resume()`. To
            // learn more about recording and playback, see:
            // https://developers.google.com/ar/develop/java/recording-and-playback
            session.resume();
        } catch (CameraNotAvailableException e) {
            messageSnackbarHelper.showError(this, "Camera not available. Try restarting the app.");
            session = null;
            return;
        }

        surfaceView.onResume();
        displayRotationHelper.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (session != null) {
            // Note that the order matters - GLSurfaceView is paused first so that it does not try
            // to query the session. If Session is paused before GLSurfaceView, GLSurfaceView may
            // still call session.update() and get a SessionPausedException.
            displayRotationHelper.onPause();
            surfaceView.onPause();
            session.pause();
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
            // Use toast instead of snackbar here since the activity will exit.
            Toast.makeText(this, "Camera permission is needed to run this application", Toast.LENGTH_LONG)
                    .show();
            if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
                // Permission denied with checking "Do not ask again".
                CameraPermissionHelper.launchPermissionSettings(this);
            }
            finish();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        FullScreenHelper.setFullScreenOnWindowFocusChanged(this, hasFocus);
    }


    @Override
    public void onSurfaceCreated(SampleRender render) {


        Interpreter.Options options = new Interpreter.Options();

//        QNN options
//        QnnDelegate.Options qnnOptions = new QnnDelegate.Options();
//        qnnOptions.setSkelLibraryDir(nativeLibraryDir);
//        qnnOptions.setLogLevel(QnnDelegate.Options.LogLevel.LOG_LEVEL_WARN);
//        qnnOptions.setCacheDir(CacheDir);
//        qnnOptions.setModelToken(modelhash);
//
//        if (QnnDelegate.checkCapability(QnnDelegate.Capability.DSP_RUNTIME)) {
//            qnnOptions.setBackendType(QnnDelegate.Options.BackendType.DSP_BACKEND);
//            qnnOptions.setDspOptions(QnnDelegate.Options.DspPerformanceMode.DSP_PERFORMANCE_BURST, QnnDelegate.Options.DspPdSession.DSP_PD_SESSION_ADAPTIVE);
//        } else {
//            boolean hasHTP_FP16 = QnnDelegate.checkCapability(QnnDelegate.Capability.HTP_RUNTIME_FP16);
//            boolean hasHTP_QUANT = QnnDelegate.checkCapability(QnnDelegate.Capability.HTP_RUNTIME_QUANTIZED);
//
//            if (!hasHTP_FP16 && !hasHTP_QUANT) {
//                Log.e(TAG, "QNN with NPU backend is not supported on this device.");
//                return;
//            }
//            qnnOptions.setBackendType(QnnDelegate.Options.BackendType.HTP_BACKEND);
//            qnnOptions.setHtpUseConvHmx(QnnDelegate.Options.HtpUseConvHmx.HTP_CONV_HMX_ON);
//            qnnOptions.setHtpPerformanceMode(QnnDelegate.Options.HtpPerformanceMode.HTP_PERFORMANCE_BURST);
//
//            if (hasHTP_FP16) {
//                qnnOptions.setHtpPrecision(QnnDelegate.Options.HtpPrecision.HTP_PRECISION_FP16);
//            }
//        }
//        Delegate QnnDelegate = new QnnDelegate(qnnOptions);
//        options.addDelegate(QnnDelegate);

        //GPU options
        GpuDelegateFactory.Options gpuOptions = new GpuDelegateFactory.Options();
        gpuOptions.setInferencePreference(GpuDelegateFactory.Options.INFERENCE_PREFERENCE_SUSTAINED_SPEED);
        gpuOptions.setPrecisionLossAllowed(true);
        gpuOptions.setSerializationParams(CacheDir, modelhash);
        Delegate gpuDelegate =  new GpuDelegate(gpuOptions);
        options.addDelegate(gpuDelegate);


        TFInterpreter = new Interpreter(modelBuffer,options);
        odProcessor = new OD_processor(TFInterpreter);
        odProcessor.setLabelloader(labelload);

        // Prepare the rendering objects. This involves reading shaders and 3D model files, so may throw
        // an IOException.
        try {

            backgroundRenderer = new BackgroundRenderer(render);
            virtualSceneFramebuffer = new Framebuffer(render, /* width= */ 1, /* height= */ 1);

            cubemapFilter =
                    new SpecularCubemapFilter(
                            render, CUBEMAP_RESOLUTION, CUBEMAP_NUMBER_OF_IMPORTANCE_SAMPLES);
            // Load DFG lookup table for environmental lighting
            dfgTexture =
                    new Texture(
                            render,
                            Texture.Target.TEXTURE_2D,
                            Texture.WrapMode.CLAMP_TO_EDGE,
                            /* useMipmaps= */ false);
            // The dfg.raw file is a raw half-float texture with two channels.
            final int dfgResolution = 64;
            final int dfgChannels = 2;
            final int halfFloatSize = 2;

            ByteBuffer buffer =
                    ByteBuffer.allocateDirect(dfgResolution * dfgResolution * dfgChannels * halfFloatSize);
            try (InputStream is = getAssets().open("models/dfg.raw")) {
                is.read(buffer.array());
            }
            // SampleRender abstraction leaks here.
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, dfgTexture.getTextureId());
            GLError.maybeThrowGLException("Failed to bind DFG texture", "glBindTexture");
            GLES30.glTexImage2D(
                    GLES30.GL_TEXTURE_2D,
                    /* level= */ 0,
                    GLES30.GL_RG16F,
                    /* width= */ dfgResolution,
                    /* height= */ dfgResolution,
                    /* border= */ 0,
                    GLES30.GL_RG,
                    GLES30.GL_HALF_FLOAT,
                    buffer);
            GLError.maybeThrowGLException("Failed to populate DFG texture", "glTexImage2D");



            // Virtual object to render (ARCore pawn)
            virtualObjectAlbedoTexture =
                    Texture.createFromAsset(
                            render,
                            "models/pawn_albedo.png",    //"models/pawn_albedo.png",
                            Texture.WrapMode.CLAMP_TO_EDGE,
                            Texture.ColorFormat.SRGB);
            virtualObjectAlbedoInstantPlacementTexture =
                    Texture.createFromAsset(
                            render,
                            "models/pawn_albedo_instant_placement.png",
                            Texture.WrapMode.CLAMP_TO_EDGE,
                            Texture.ColorFormat.SRGB);
            Texture virtualObjectPbrTexture =
                    Texture.createFromAsset(
                            render,
                            "models/pawn_roughness_metallic_ao.png",
                            Texture.WrapMode.CLAMP_TO_EDGE,
                            Texture.ColorFormat.LINEAR);

            virtualObjectMesh = Mesh.createFromAsset(render, "models/pawn.obj");
//
//            virtualObjectShader = Shader.createFromAssets(
//                    render,
//                    "shaders/simple_color.vert", // 단순 셰이더 사용
//                    "shaders/simple_color.frag",
//                    /* defines= */ null
//            ).setVec4("u_Color", new float[]{1.0f, 0.5f, 0.2f, 1.0f});
            virtualObjectShader =
                    Shader.createFromAssets(
                                    render,
                                    "shaders/environmental_hdr.vert",
                                    "shaders/environmental_hdr.frag",
                                    /* defines= */ new HashMap<String, String>() {
                                        {
                                            put(
                                                    "NUMBER_OF_MIPMAP_LEVELS",
                                                    Integer.toString(cubemapFilter.getNumberOfMipmapLevels()));
                                        }
                                    })
                            .setTexture("u_AlbedoTexture", virtualObjectAlbedoTexture)
                            .setTexture("u_RoughnessMetallicAmbientOcclusionTexture", virtualObjectPbrTexture)
                            .setTexture("u_Cubemap", cubemapFilter.getFilteredCubemapTexture())
                            .setTexture("u_DfgTexture", dfgTexture);



        } catch (IOException e) {
            Log.e(TAG, "Failed to read a required asset file", e);
            messageSnackbarHelper.showError(this, "Failed to read a required asset file: " + e);
        }
    }

    @Override
    public void onSurfaceChanged(SampleRender render, int width, int height) {
        displayRotationHelper.onSurfaceChanged(width, height);
        virtualSceneFramebuffer.resize(width, height);
    }



    @Override
    public void onDrawFrame(SampleRender render) {
        Log.d("Performance", "//////////////////////////////////////////////////////////////////////////");
        long startTime = System.nanoTime();
        Log.d("Performance", "Frame start");

        if (session == null) {
            return;
        }

        // Texture names should only be set once on a GL thread unless they change.
        if (!hasSetTextureNames) {
            session.setCameraTextureNames(
                    new int[] {backgroundRenderer.getCameraColorTexture().getTextureId()});
            hasSetTextureNames = true;
        }

        // Update per-frame state
        displayRotationHelper.updateSessionIfNeeded(session);

        Frame frame;
        try {
            frame = session.update(); // ARCore 프레임 업데이트
        } catch (CameraNotAvailableException e) {
            Log.e(TAG, "Camera not available during onDrawFrame", e);
            messageSnackbarHelper.showError(this, "Camera not available. Try restarting the app.");
            return;
        }
        Camera camera = frame.getCamera();

        if (!TrackingSystem.CameraInfo.isInitialized()) {
            TrackingSystem.CameraInfo.initializeIntrinsics(frame);
        }
            Image frameimage=null;
            try {



                frameimage = frame.acquireCameraImage();
                //recent frame period record
                float deltaTime;
                current_time = System.nanoTime();
                if(prev_time>0)
                    deltaTime = (current_time - prev_time)/1_000_000_000f;
                else
                    deltaTime = 0;
               // Log.d("time","prev_time:"+ prev_time +"  current_time:"+ current_time);
                prev_time = current_time;


                //Object detection execution
                OD_detections = odProcessor.OD_process(this, frameimage, extra_options);
                frameimage.close();

                if(OD_detections.isEmpty()){
                    Log.d("Detection", "Nothing detected");
                }
                if(deltaTime>0) {
                    Pose camerapose = frame.getCamera().getPose();
                    List<Object_Info> activeobjects = trackingSystem.processFrame(OD_detections, frame.acquireDepthImage16Bits(),camerapose , deltaTime);

                    List<Object_Info> render_objects = activeobjects;
                    runOnUiThread(() -> {
                        //overlayView.setobjects(render_objects);
                        overlayView.setobjects_pose(render_objects,camerapose);

                    });
                }
                Log.d("time","deltatime:"+ String.format("%.2f",deltaTime*1000)+"ms");

                //For depth rendering
//                List<detection_depth> detection_list = new ArrayList<>();
//                int index = 0;
//                for (Detectionclass detection : OD_detections) {
//                    int mid_x = (int)(detection.getBoundingBox().left + detection.getBoundingBox().right)/2;
//                    int mid_y = (int)(detection.getBoundingBox().top + detection.getBoundingBox().bottom)/2;
//                    float depthvalue = calculatedepth(mid_x,mid_y,frame.acquireDepthImage16Bits());
//                    detection_list.add(new detection_depth(detection,depthvalue));
//                    Log.d("Detection", +index++ + " detected");
//                    Log.d("Detection", "bounding box: " + detection.getBoundingBox().toString());
//                    Log.d("Detection", "label: " + detection.getCategories().getLabel());
//                }


                // ---- 2D OD 결과를 Canvas에 렌더링 ---- //
//                if (!OD_detections.isEmpty()) {
                    //List<Detectionclass> finalDetections = OD_detections;

                    //Rendertime.renderingStartTime = System.nanoTime();
//                    runOnUiThread(() -> {
//                        overlayView.setDetections(render_objects);
//                       // overlayView.setDetectionswithdepth(withdepth);
//                    });
//                }


            } catch (Exception e) {
                Log.e(TAG, "Error detecting object", e);
            } finally {
                if(frameimage!=null) {
                    frameimage.close();
                }
            }


        // Update BackgroundRenderer state
        try {
            backgroundRenderer.setUseDepthVisualization(
                    render, depthSettings.depthColorVisualizationEnabled());
            backgroundRenderer.setUseOcclusion(render, depthSettings.useDepthForOcclusion());
        } catch (IOException e) {
            Log.e(TAG, "Failed to read a required asset file", e);
            messageSnackbarHelper.showError(this, "Failed to read a required asset file: " + e);
            return;
        }
        backgroundRenderer.updateDisplayGeometry(frame);
//
//        // Use Customized Depth Data
//        if (camera.getTrackingState() == TrackingState.TRACKING
//                && (depthSettings.useDepthForOcclusion()
//                || depthSettings.depthColorVisualizationEnabled())) {
//            try {
//                // Replace ARCore Depth Image with Customized Depth Image
//                //Image customDepthImage = customDepthAPI.getDepthImage(); // Custom Depth API 호출
//                Image customDepthImage = frame.acquireDepthImage16Bits();
//                if (customDepthImage != null) {
//                    backgroundRenderer.updateCameraDepthTexture(customDepthImage);
//                    customDepthImage.close(); // Custom Image 리소스 해제
//                }
//            } catch (Exception e) {
//                Log.e(TAG, "Error using Custom Depth Data", e);
//            }
//        }

//        // Handle one tap per frame.
//        handleTapWithCustomDepth(frame, camera); // Custom Depth 기반 객체 배치
//
//        // Update screen lock state based on tracking.
//        trackingStateHelper.updateKeepScreenOnFlag(camera.getTrackingState());
//
//        // Show user messages (same as original code).
//        String message = null;
//        if (camera.getTrackingState() == TrackingState.PAUSED) {
//            if (camera.getTrackingFailureReason() == TrackingFailureReason.NONE) {
//                message = SEARCHING_PLANE_MESSAGE;
//            } else {
//                message = TrackingStateHelper.getTrackingFailureReasonString(camera);
//            }
//        } else if (hasTrackingPlane()) {
//            if (wrappedAnchors.isEmpty()) {
//                message = WAITING_FOR_TAP_MESSAGE;
//            }
//        } else {
//            message = SEARCHING_PLANE_MESSAGE;
//        }
//        if (message == null) {
//            messageSnackbarHelper.hide(this);
//        } else {
//            messageSnackbarHelper.showMessage(this, message);
//        }
//
//        // Render camera background
        if (frame.getTimestamp() != 0) {
            backgroundRenderer.drawBackground(render);      //배경 렌더링
        }

//        // If not tracking, don't draw 3D objects.
//        if (camera.getTrackingState() == TrackingState.PAUSED) {
//            return;
//        }
//
//        // Get projection matrix.
//        camera.getProjectionMatrix(projectionMatrix, 0, Z_NEAR, Z_FAR);
//        camera.getViewMatrix(viewMatrix, 0);
//
//        // Update lighting parameters in the shader
//        updateLightEstimation(frame.getLightEstimate(), viewMatrix);
//
//        // Draw anchors created by touch
//        render.clear(virtualSceneFramebuffer, 0f, 0f, 0f, 0f);
//        for (WrappedAnchor1 wrappedAnchor : wrappedAnchors) {
//
//            Anchor anchor = wrappedAnchor.getAnchor();
//            // Log.d("ith anchor", +i+"th anchor of pose:" + anchor.getPose().toString());
//
//            // Get the pose of the anchor and convert it to a matrix
//            anchor.getPose().toMatrix(modelMatrix, 0);
//
//            // Compute the model-view-projection matrix
//            Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0, modelMatrix, 0);
//            Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix, 0, modelViewMatrix, 0);
//
//            // Set shader matrices for rendering
//            virtualObjectShader.setMat4("u_ModelView", modelViewMatrix);
//            virtualObjectShader.setMat4("u_ModelViewProjection", modelViewProjectionMatrix);
//
//            // Set the texture for rendering the virtual object
//
//            virtualObjectShader.setTexture("u_AlbedoTexture",virtualObjectAlbedoTexture );
//            // Draw the virtual object at the anchor's pose
//            render.draw(virtualObjectMesh, virtualObjectShader, virtualSceneFramebuffer);
//        }


        // Compose the virtual scene with the background
        backgroundRenderer.drawVirtualScene(render, virtualSceneFramebuffer, Z_NEAR, Z_FAR);

        long endtime = System.nanoTime();
            long durationInMillis = (endtime - startTime) / 1_000; // Convert to milliseconds

            //Log.d("Performance", "Total frame time: " + String.format("%.2f", durationInMillis/1000.0) + " ms");
        Log.d("frame", "Frame END");

    }

    private float[] multiply_quarternion(float[] q1, float[] q2){
        float x1 = q1[0],y1 = q1[1], z1 = q1[2], w1 = q1[3];
        float x2 = q2[0],y2 = q2[1], z2 = q2[2], w2 = q2[3];
        return new float[]{
                w1*x2 + x1*w2 + y1*z2 - z1*y2, //x
                w1*y2 - x1*z2 + y1*w2 + z1*x2, //y
                w1*z2 + x1*y2 - y1*x2 + z1*w2, //z
                w1*w2 - x1*x2 - y1*y2 - z1*z2  //w
        };

    }
    public void addAnchor(Anchor anchor) {
        if (wrappedAnchors.size() >= 20) {
            // 오래된 Anchor 제거
            wrappedAnchors.get(0).getAnchor().detach();
            wrappedAnchors.remove(0);
        }
        wrappedAnchors.add(new WrappedAnchor1(anchor));
    }
    int num=0;
    private void handleTapWithCustomDepth(Frame frame, Camera camera) {
        // 터치 이벤트 가져오기
        MotionEvent tap = tapHelper.poll(); // TapHelper는 터치 이벤트 큐를 관리
        if (tap == null || camera.getTrackingState() != TrackingState.TRACKING) {
            return; // 터치 이벤트가 없거나 카메라가 추적 상태가 아닐 경우 처리 안 함
        }

        // 터치된 화면 좌표 가져오기
        int tapped_x = (int) tap.getX();
        int tapped_y = (int) tap.getY();

        Image customDepthImage= null;
        try {
            customDepthImage = frame.acquireDepthImage16Bits();

            // Map screen tap to depth image coordinates
            int displayWidth = Resources.getSystem().getDisplayMetrics().widthPixels;
            int displayHeight = Resources.getSystem().getDisplayMetrics().heightPixels;
            int depthX = tapped_x * customDepthImage.getHeight() / displayWidth;
            int depthY = tapped_y * customDepthImage.getWidth() / displayHeight;

            final Image.Plane depthImagePlane = customDepthImage.getPlanes()[0];
            ByteBuffer depthByteBufferOriginal = depthImagePlane.getBuffer();
            ByteBuffer depthByteBuffer = ByteBuffer.allocate(depthByteBufferOriginal.capacity());
            depthByteBuffer.order(ByteOrder.LITTLE_ENDIAN);
            while (depthByteBufferOriginal.hasRemaining()) {
                depthByteBuffer.put(depthByteBufferOriginal.get());
            }
            depthByteBuffer.rewind(); // Reset the buffer's position to the start
            ShortBuffer depthBuffer = depthByteBuffer.asShortBuffer();
            Log.d("DepthImage", "Width:"+customDepthImage.getWidth()+"  Height:"+customDepthImage.getHeight());
            int depthMillimeters = depthBuffer.get(depthX * customDepthImage.getWidth() + depthY);
            Log.d("Depth", "Depth:"+depthMillimeters/1000.0f + "  X:"+depthX+ " Y:"+depthY );
            if (depthMillimeters <= 0) {
                Log.e("Depth", "Invalid depth value at (" + tapped_x + ", " + tapped_y + ")");
                return;
            }

            float depthMeters = depthMillimeters / 1000.0f;
            CameraIntrinsics intrinsics = frame.getCamera().getTextureIntrinsics();
            int[] dimensions = intrinsics.getImageDimensions();
            float fx = intrinsics.getFocalLength()[0];
            float fy = intrinsics.getFocalLength()[1];
            float cx = intrinsics.getPrincipalPoint()[0];
            float cy = intrinsics.getPrincipalPoint()[1];

            // Convert depth pixel to 3D camera coordinates
            float[] cameraCoords = new float[3];

            cameraCoords[0] = depthMeters * (tapped_y*dimensions[0]/(float)displayHeight-cx) / fx; // X
            cameraCoords[1] = depthMeters * (tapped_x*dimensions[1]/(float)displayWidth-cy) / fy;// Y
            cameraCoords[2] = -depthMeters;
            Log.d("Camera", "Camera width: " + dimensions[0]+ ", Camera Height: " +dimensions[1]);
            Log.d("Display", "Display_Width: " + displayWidth+ ", Display_Height: " +displayHeight);
            Log.d("X", "tapX: " + tapped_x+ ", fx: " +fx+", cx: "+cx);
            Log.d("Y", "tapY: " + tapped_y+ ", fy: " +fy+", cy: "+cy);
            Log.d("X,Y,Z", "3d_coord: " + cameraCoords[0]+ ", " + cameraCoords[1] + ", "+cameraCoords[2]);

            // Convert camera coordinates to world coordinates
            Pose cameraPose = frame.getCamera().getPose();
            float[] translation = cameraPose.getTranslation();
            float[] q1 = cameraPose.getRotationQuaternion();
            float[] p1 = new float[]{cameraCoords[0],cameraCoords[1],cameraCoords[2],0};
            float[] q_star = new float[]{-q1[0],-q1[1],-q1[2],q1[3]};
            float[] p_prime = multiply_quarternion(multiply_quarternion(q1,p1),q_star);
            float[] worldpoint = new float[] {p_prime[0]+translation[0],p_prime[1]+translation[1],p_prime[2]+translation[2]};


            Log.d("CameraPose:","CameraPose: "+cameraPose.toString());

            Pose worldPose = new Pose(worldpoint,new float[]{0.0f, 0.0f, 0.0f, 1.0f});

            Log.d("WorldPose:","WorldPose: "+worldPose.toString());
            Anchor anchor = session.createAnchor(worldPose);
            addAnchor(anchor); // 객체 배치 메서드 호출
            Log.d("CustomDepth", "Object placed at world pose: " + worldPose.toString());

        } catch (Exception e) {
            Log.e("CustomDepth", "Error processing tap with custom depth data", e);
        } finally {
            if (customDepthImage != null) {
                customDepthImage.close(); // 리소스 해제
            }
        }
    }

    private void launchInstantPlacementSettingsMenuDialog() {
        resetSettingsMenuDialogCheckboxes();
        Resources resources = getResources();
        new AlertDialog.Builder(this)
                .setTitle(R.string.options_title_instant_placement)
                .setMultiChoiceItems(
                        resources.getStringArray(R.array.instant_placement_options_array),
                        instantPlacementSettingsMenuDialogCheckboxes,
                        (DialogInterface dialog, int which, boolean isChecked) ->
                                instantPlacementSettingsMenuDialogCheckboxes[which] = isChecked)
                .setPositiveButton(
                        R.string.done,
                        (DialogInterface dialogInterface, int which) -> applySettingsMenuDialogCheckboxes())
                .setNegativeButton(
                        android.R.string.cancel,
                        (DialogInterface dialog, int which) -> resetSettingsMenuDialogCheckboxes())
                .show();
    }

    /** Shows checkboxes to the user to facilitate toggling of depth-based effects. */
    private void launchDepthSettingsMenuDialog() {
        // Retrieves the current settings to show in the checkboxes.
        resetSettingsMenuDialogCheckboxes();

        // Shows the dialog to the user.
        Resources resources = getResources();
        if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
            // With depth support, the user can select visualization options.
            new AlertDialog.Builder(this)
                    .setTitle(R.string.options_title_with_depth)
                    .setMultiChoiceItems(
                            resources.getStringArray(R.array.depth_options_array),
                            depthSettingsMenuDialogCheckboxes,
                            (DialogInterface dialog, int which, boolean isChecked) ->
                                    depthSettingsMenuDialogCheckboxes[which] = isChecked)
                    .setPositiveButton(
                            R.string.done,
                            (DialogInterface dialogInterface, int which) -> applySettingsMenuDialogCheckboxes())
                    .setNegativeButton(
                            android.R.string.cancel,
                            (DialogInterface dialog, int which) -> resetSettingsMenuDialogCheckboxes())
                    .show();
        } else {
            // Without depth support, no settings are available.
            new AlertDialog.Builder(this)
                    .setTitle(R.string.options_title_without_depth)
                    .setPositiveButton(
                            R.string.done,
                            (DialogInterface dialogInterface, int which) -> applySettingsMenuDialogCheckboxes())
                    .show();
        }
    }

    private void applySettingsMenuDialogCheckboxes() {
        depthSettings.setUseDepthForOcclusion(depthSettingsMenuDialogCheckboxes[0]);
        depthSettings.setDepthColorVisualizationEnabled(depthSettingsMenuDialogCheckboxes[1]);
        instantPlacementSettings.setInstantPlacementEnabled(
                instantPlacementSettingsMenuDialogCheckboxes[0]);
        configureSession();
    }

    private void resetSettingsMenuDialogCheckboxes() {
        depthSettingsMenuDialogCheckboxes[0] = depthSettings.useDepthForOcclusion();
        depthSettingsMenuDialogCheckboxes[1] = depthSettings.depthColorVisualizationEnabled();
        instantPlacementSettingsMenuDialogCheckboxes[0] =
                instantPlacementSettings.isInstantPlacementEnabled();
    }

    /** Checks if we detected at least one plane. */
    private boolean hasTrackingPlane() {
        for (Plane plane : session.getAllTrackables(Plane.class)) {
            if (plane.getTrackingState() == TrackingState.TRACKING) {
                return true;
            }
        }
        return false;
    }

    /** Update state based on the current frame's light estimation. */
    private void updateLightEstimation(LightEstimate lightEstimate, float[] viewMatrix) {
        if (lightEstimate.getState() != LightEstimate.State.VALID) {
            virtualObjectShader.setBool("u_LightEstimateIsValid", false);
            return;
        }
        virtualObjectShader.setBool("u_LightEstimateIsValid", true);

        Matrix.invertM(viewInverseMatrix, 0, viewMatrix, 0);
        virtualObjectShader.setMat4("u_ViewInverse", viewInverseMatrix);

        updateMainLight(
                lightEstimate.getEnvironmentalHdrMainLightDirection(),
                lightEstimate.getEnvironmentalHdrMainLightIntensity(),
                viewMatrix);
        updateSphericalHarmonicsCoefficients(
                lightEstimate.getEnvironmentalHdrAmbientSphericalHarmonics());
        cubemapFilter.update(lightEstimate.acquireEnvironmentalHdrCubeMap());
    }

    private void updateMainLight(float[] direction, float[] intensity, float[] viewMatrix) {
        // We need the direction in a vec4 with 0.0 as the final component to transform it to view space
        worldLightDirection[0] = direction[0];
        worldLightDirection[1] = direction[1];
        worldLightDirection[2] = direction[2];
        Matrix.multiplyMV(viewLightDirection, 0, viewMatrix, 0, worldLightDirection, 0);
        virtualObjectShader.setVec4("u_ViewLightDirection", viewLightDirection);
        virtualObjectShader.setVec3("u_LightIntensity", intensity);
    }

    private void updateSphericalHarmonicsCoefficients(float[] coefficients) {
        // Pre-multiply the spherical harmonics coefficients before passing them to the shader. The
        // constants in sphericalHarmonicFactors were derived from three terms:
        //
        // 1. The normalized spherical harmonics basis functions (y_lm)
        //
        // 2. The lambertian diffuse BRDF factor (1/pi)
        //
        // 3. A <cos> convolution. This is done to so that the resulting function outputs the irradiance
        // of all incoming light over a hemisphere for a given surface normal, which is what the shader
        // (environmental_hdr.frag) expects.
        //
        // You can read more details about the math here:
        // https://google.github.io/filament/Filament.html#annex/sphericalharmonics

        if (coefficients.length != 9 * 3) {
            throw new IllegalArgumentException(
                    "The given coefficients array must be of length 27 (3 components per 9 coefficients");
        }

        // Apply each factor to every component of each coefficient
        for (int i = 0; i < 9 * 3; ++i) {
            sphericalHarmonicsCoefficients[i] = coefficients[i] * sphericalHarmonicFactors[i / 3];
        }
        virtualObjectShader.setVec3Array(
                "u_SphericalHarmonicsCoefficients", sphericalHarmonicsCoefficients);
    }

    /** Configures the session with feature settings. */
    private void configureSession() {
        Config config = session.getConfig();
        config.setLightEstimationMode(Config.LightEstimationMode.ENVIRONMENTAL_HDR);
        if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
            config.setDepthMode(Config.DepthMode.AUTOMATIC);
        } else {
            config.setDepthMode(Config.DepthMode.DISABLED);
        }
        if (instantPlacementSettings.isInstantPlacementEnabled()) {
            config.setInstantPlacementMode(InstantPlacementMode.LOCAL_Y_UP);
        } else {
            config.setInstantPlacementMode(InstantPlacementMode.DISABLED);
        }
        session.configure(config);
    }
}
class WrappedAnchor1 {
    private Anchor anchor;
    public WrappedAnchor1(Anchor anchor) {
        this.anchor = anchor;
    }
    public Anchor getAnchor() {
        return anchor;
    }
}