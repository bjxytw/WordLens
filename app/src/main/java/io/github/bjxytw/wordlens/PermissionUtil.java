package io.github.bjxytw.wordlens;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

class PermissionUtil {
    private static final String TAG = "PermissionUtil";
    private static final int REQUEST_CODE = 1;

    static void getPermissions(Activity activity) {
        List<String> permissions = new ArrayList<>();
        for (String permission : getPermissionList(activity)) {
            if (isPermissionDenied(activity, permission))
                permissions.add(permission);
        }
        if (!permissions.isEmpty())
            ActivityCompat.requestPermissions(
                    activity, permissions.toArray(new String[0]), REQUEST_CODE);
    }

    static boolean isAllPermissionsGranted(Activity activity) {
        for (String permission : getPermissionList(activity))
            if (isPermissionDenied(activity, permission))
                return false;
        return true;
    }

    private static boolean isPermissionDenied(Context context, String permission) {
        return ContextCompat.checkSelfPermission(context, permission)
                == PackageManager.PERMISSION_DENIED;
    }

    private static String[] getPermissionList(Activity activity) {
        try {
            PackageInfo info = activity.getPackageManager()
                    .getPackageInfo(activity.getPackageName(), PackageManager.GET_PERMISSIONS);
            String[] ps = info.requestedPermissions;

            if (ps != null && ps.length > 0) return ps;
            else return new String[0];

        } catch (Exception e) {
            Log.e(TAG, e.toString());
            return new String[0];
        }
    }
}
