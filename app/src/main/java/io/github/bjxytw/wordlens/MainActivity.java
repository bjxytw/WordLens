package io.github.bjxytw.wordlens;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import io.github.bjxytw.wordlens.camera.CameraSource;
import io.github.bjxytw.wordlens.camera.CameraSourcePreview;
import io.github.bjxytw.wordlens.camera.GraphicOverlay;

public final class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUESTS = 1;
    private CameraSource cameraSource = null;
    private CameraSourcePreview preview;
    private GraphicOverlay graphicOverlay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");

        setContentView(R.layout.activity_main);

        preview = findViewById(R.id.firePreview);
        if (preview == null)
            Log.d(TAG, "Preview is null");

        graphicOverlay = findViewById(R.id.fireFaceOverlay);
        if (graphicOverlay == null)
            Log.d(TAG, "graphicOverlay is null");

        if (allPermissionsGranted())
            createCameraSource();
        else getRuntimePermissions();
    }

    private void createCameraSource() {
        if (cameraSource == null)
            cameraSource = new CameraSource(this, graphicOverlay);
    }

    private void startCameraSource() {
        if (cameraSource != null) {
            try {
                if (preview == null)
                    Log.d(TAG, "resume: Preview is null");
                if (graphicOverlay == null)
                    Log.d(TAG, "resume: graphOverlay is null");
                preview.start(cameraSource, graphicOverlay);
            } catch (IOException e) {
                Log.e(TAG, "Unable to start camera source.", e);
                cameraSource.release();
                cameraSource = null;
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        startCameraSource();
    }

    @Override
    protected void onPause() {
        super.onPause();
        preview.stop();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (cameraSource != null)
            cameraSource.release();
    }

    private void getRuntimePermissions() {
        List<String> allNeededPermissions = new ArrayList<>();
        for (String permission : getRequiredPermissions()) {
            Log.i(TAG, "requiredPermission: " + permission);

            if (isPermissionNotGranted(this, permission))
                allNeededPermissions.add(permission);
        }

        if (!allNeededPermissions.isEmpty())
            ActivityCompat.requestPermissions(this,
                    allNeededPermissions.toArray(new String[0]), PERMISSION_REQUESTS);
    }

    private String[] getRequiredPermissions() {
        try {
            PackageInfo info = this.getPackageManager()
                    .getPackageInfo(this.getPackageName(), PackageManager.GET_PERMISSIONS);
            String[] ps = info.requestedPermissions;

            if (ps != null && ps.length > 0) return ps;
            else return new String[0];

        } catch (Exception e) {
            return new String[0];
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        Log.i(TAG, "Permission granted!");

        if (allPermissionsGranted())
            createCameraSource();

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private boolean allPermissionsGranted() {
        for (String permission : getRequiredPermissions())
            if (isPermissionNotGranted(this, permission))
                return false;
        return true;
    }

    private static boolean isPermissionNotGranted(Context context, String permission) {
        return ContextCompat.checkSelfPermission(context, permission)
                != PackageManager.PERMISSION_GRANTED;
    }
}
