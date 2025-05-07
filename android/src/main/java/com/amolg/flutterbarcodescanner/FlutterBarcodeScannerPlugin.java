package com.amolg.flutterbarcodescanner;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;

import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.vision.barcode.Barcode;

import java.util.Map;

import io.flutter.embedding.android.FlutterActivity;
import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry;
import io.flutter.plugin.common.EventChannel.StreamHandler;
import io.flutter.embedding.engine.plugins.lifecycle.FlutterLifecycleAdapter;

public class FlutterBarcodeScannerPlugin implements MethodCallHandler, PluginRegistry.ActivityResultListener, StreamHandler, FlutterPlugin, ActivityAware {
    private static final String CHANNEL = "flutter_barcode_scanner";
    private static final String TAG = FlutterBarcodeScannerPlugin.class.getSimpleName();
    private static final int RC_BARCODE_CAPTURE = 9001;

    // Plugin state
    private MethodChannel channel;
    private EventChannel eventChannel;
    private ActivityPluginBinding activityBinding;
    private Application applicationContext;
    private Lifecycle lifecycle;
    private LifeCycleObserver observer;
    
    // Scanning state
    private static FlutterActivity activity;
    private static Result pendingResult;
    private Map<String, Object> arguments;
    public static String lineColor = "";
    public static boolean isShowFlashIcon = false;
    public static boolean isContinuousScan = false;
    static EventChannel.EventSink barcodeStream;

    public FlutterBarcodeScannerPlugin() {}

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
        setupChannels(binding.getBinaryMessenger(), (Application) binding.getApplicationContext());
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        teardownChannels();
    }

    @Override
    public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
        activityBinding = binding;
        activity = (FlutterActivity) binding.getActivity();
        applicationContext = (Application) activity.getApplicationContext();
        
        // Setup activity listeners
        binding.addActivityResultListener(this);
        lifecycle = FlutterLifecycleAdapter.getActivityLifecycle(binding);
        observer = new LifeCycleObserver(binding.getActivity());
        lifecycle.addObserver(observer);
    }

    @Override
    public void onDetachedFromActivity() {
        teardownActivity();
    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {
        onDetachedFromActivity();
    }

    @Override
    public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
        onAttachedToActivity(binding);
    }

    private void setupChannels(BinaryMessenger messenger, Application applicationContext) {
        channel = new MethodChannel(messenger, CHANNEL);
        channel.setMethodCallHandler(this);
        
        eventChannel = new EventChannel(messenger, "flutter_barcode_scanner_receiver");
        eventChannel.setStreamHandler(this);
        
        this.applicationContext = applicationContext;
    }

    private void teardownChannels() {
        if (channel != null) {
            channel.setMethodCallHandler(null);
            channel = null;
        }
        if (eventChannel != null) {
            eventChannel.setStreamHandler(null);
            eventChannel = null;
        }
    }

    private void teardownActivity() {
        if (activityBinding != null) {
            activityBinding.removeActivityResultListener(this);
            activityBinding = null;
        }
        if (lifecycle != null && observer != null) {
            lifecycle.removeObserver(observer);
            lifecycle = null;
        }
        if (applicationContext != null && observer != null) {
            applicationContext.unregisterActivityLifecycleCallbacks(observer);
            applicationContext = null;
        }
        activity = null;
    }

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
        pendingResult = result;

        if (call.method.equals("scanBarcode")) {
            try {
                if (!(call.arguments instanceof Map)) {
                    throw new IllegalArgumentException("Plugin not passing a map as parameter: " + call.arguments);
                }
                arguments = (Map<String, Object>) call.arguments;
                lineColor = (String) arguments.get("lineColor");
                isShowFlashIcon = (boolean) arguments.get("isShowFlashIcon");
                
                if (lineColor == null || lineColor.isEmpty()) {
                    lineColor = "#DC143C";
                }
                
                if (arguments.get("scanMode") != null) {
                    BarcodeCaptureActivity.SCAN_MODE = (int) arguments.get("scanMode");
                } else {
                    BarcodeCaptureActivity.SCAN_MODE = BarcodeCaptureActivity.SCAN_MODE_ENUM.QR.ordinal();
                }

                isContinuousScan = (boolean) arguments.get("isContinuousScan");
                startBarcodeScannerActivityView((String) arguments.get("cancelButtonText"), isContinuousScan);
            } catch (Exception e) {
                Log.e(TAG, "onMethodCall: " + e.getLocalizedMessage());
                result.error("ERROR", e.getMessage(), null);
            }
        } else {
            result.notImplemented();
        }
    }

    private void startBarcodeScannerActivityView(String buttonText, boolean isContinuousScan) {
        try {
            Intent intent = new Intent(activity, BarcodeCaptureActivity.class)
                .putExtra("cancelButtonText", buttonText);
            
            if (isContinuousScan) {
                activity.startActivity(intent);
            } else {
                activity.startActivityForResult(intent, RC_BARCODE_CAPTURE);
            }
        } catch (Exception e) {
            Log.e(TAG, "startView: " + e.getLocalizedMessage());
            if (pendingResult != null) {
                pendingResult.error("ERROR", e.getMessage(), null);
                pendingResult = null;
            }
        }
    }

    @Override
    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == RC_BARCODE_CAPTURE) {
            if (resultCode == CommonStatusCodes.SUCCESS && data != null) {
                try {
                    Barcode barcode = data.getParcelableExtra(BarcodeCaptureActivity.BarcodeObject);
                    pendingResult.success(barcode != null ? barcode.rawValue : "-1");
                } catch (Exception e) {
                    pendingResult.success("-1");
                }
            } else {
                pendingResult.success("-1");
            }
            pendingResult = null;
            arguments = null;
            return true;
        }
        return false;
    }

    @Override
    public void onListen(Object o, EventChannel.EventSink eventSink) {
        barcodeStream = eventSink;
    }

    @Override
    public void onCancel(Object o) {
        barcodeStream = null;
    }

    public static void onBarcodeScanReceiver(final Barcode barcode) {
        if (barcode != null && !barcode.displayValue.isEmpty() && barcodeStream != null) {
            activity.runOnUiThread(() -> barcodeStream.success(barcode.rawValue));
        }
    }

    private class LifeCycleObserver implements Application.ActivityLifecycleCallbacks, DefaultLifecycleObserver {
        private final Activity thisActivity;

        LifeCycleObserver(Activity activity) {
            this.thisActivity = activity;
            if (applicationContext != null) {
                applicationContext.registerActivityLifecycleCallbacks(this);
            }
        }

        @Override
        public void onActivityCreated(Activity activity, Bundle savedInstanceState) {}

        @Override
        public void onActivityStarted(Activity activity) {}

        @Override
        public void onActivityResumed(Activity activity) {}

        @Override
        public void onActivityPaused(Activity activity) {}

        @Override
        public void onActivityStopped(Activity activity) {}

        @Override
        public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}

        @Override
        public void onActivityDestroyed(Activity activity) {
            if (thisActivity == activity && applicationContext != null) {
                applicationContext.unregisterActivityLifecycleCallbacks(this);
            }
        }

        // DefaultLifecycleObserver methods
        @Override
        public void onCreate(@NonNull LifecycleOwner owner) {}

        @Override
        public void onStart(@NonNull LifecycleOwner owner) {}

        @Override
        public void onResume(@NonNull LifecycleOwner owner) {}

        @Override
        public void onPause(@NonNull LifecycleOwner owner) {}

        @Override
        public void onStop(@NonNull LifecycleOwner owner) {}

        @Override
        public void onDestroy(@NonNull LifecycleOwner owner) {}
    }
}
