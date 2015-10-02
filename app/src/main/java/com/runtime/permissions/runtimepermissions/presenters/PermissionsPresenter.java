package com.runtime.permissions.runtimepermissions.presenters;


import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;

import com.pushtorefresh.storio.sqlite.queries.Query;
import com.runtime.permissions.runtimepermissions.presenters.db.DbModule;
import com.runtime.permissions.runtimepermissions.presenters.db.entities.Permission;
import com.runtime.permissions.runtimepermissions.presenters.db.tables.PermissionTable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PermissionsPresenter implements IPermissionsPresenter {

    private final IPermissionsView view;

    private final int[] supportedRequestCodes;
    private final IPermissionRequestDecision permissionRequestDecision = new IPermissionRequestDecision() {
        @Override
        public void forcePermissionsRequest(int requestCode, Activity activity, String[] permissions) {
            runActionUnderPermissions(requestCode, true, permissions);
        }
    };

    public PermissionsPresenter(IPermissionsView view) {
        this(view, null);
    }

    public PermissionsPresenter(IPermissionsView view, int[] supportedRequestCodes) {
        this.view = view;
        this.supportedRequestCodes = supportedRequestCodes;
    }

    @Override
    public void runActionUnderPermissions(int requestCode, boolean forced, @NonNull String... permissions) {
        if (!isRequestCodeSupported(requestCode))
            throw new RuntimeException("Request code does not supported");

        Activity activity = getActivity();

        if (isPermissionGranted(activity, permissions)) {
            view.permissionsGrantResult(requestCode, IPermissionsView.PERMISSIONS_ARE_ALREADY_GRANTED, Arrays.asList(permissions), Arrays.asList(permissions));
            return;
        }

        if (isShouldToShowRequest(activity, forced, permissions)) {
            view.decideShouldRequestPermissions(requestCode, permissions, permissionRequestDecision);
        } else {
            requestPermissions(activity, requestCode, permissions);
        }
    }

    
    /* private methods */

    @Override
    public void runActionUnderPermissionsNotForced(int requestCode, @NonNull String... permissions) {
        runActionUnderPermissions(requestCode, false, permissions);
    }

    @Override
    public boolean onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (!isRequestCodeSupported(requestCode))
            return false;

        List<String> requestedPermissions = new ArrayList<>(Arrays.asList(permissions));

        List<String> grantedPermissions = new ArrayList<>();
        for (int i = 0; i < grantResults.length; i++) {
            String permission = permissions[i];
            int grantResult = grantResults[i];

            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                grantedPermissions.add(permission);
            }
        }

        int permissionsGrantResult;

        if (!grantedPermissions.isEmpty()) {
            if (grantedPermissions.size() < requestedPermissions.size()) {
                permissionsGrantResult = IPermissionsView.PERMISSIONS_GRANT_RESULT_ALLOW_PARTIALLY;
            } else {
                permissionsGrantResult = IPermissionsView.PERMISSIONS_GRANT_RESULT_ALLOW_ALL;
            }
        } else {
            permissionsGrantResult = IPermissionsView.PERMISSIONS_GRANT_RESULT_DENY_ALL;
        }

        view.permissionsGrantResult(requestCode, permissionsGrantResult, requestedPermissions, grantedPermissions);

        return true;
    }

    private boolean isAppropriateVersionCode() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;// Marshmallow+
    }

    private void requestPermissions(Activity activity, int requestCode, String... permissions) {
        ActivityCompat.requestPermissions(activity, permissions, requestCode);
    }

    private boolean isRequestCodeSupported(int requestCode) {
        if (supportedRequestCodes == null)
            return true;

        for (int supportedRequestCode : supportedRequestCodes) {
            if (supportedRequestCode == requestCode)
                return true;
        }

        return false;
    }

    private Activity getActivity() {
        return view.getActivity();
    }

    private boolean isPermissionGranted(Context context, String... permissions) {
        boolean isPermissionsGranted = true;
        if (isAppropriateVersionCode()) {
            for (String permission : permissions) {
                isPermissionsGranted = isPermissionsGranted
                        && (ActivityCompat.checkSelfPermission(context, permission)
                        == PackageManager.PERMISSION_GRANTED);
            }
            return isPermissionsGranted;
        } else {
            DbModule dbModule = new DbModule(getActivity().getApplicationContext());
            List<Permission> permissionsFromDB = dbModule.getPermissionsByQuery(Query.builder()
                    .table(PermissionTable.TABLE)
                    .where(PermissionTable.COLUMN_PERMISSION_NAME + " == ?")
                    .whereArgs(permissions)
                    .build());
            for (Permission permission : permissionsFromDB) {
                if (permission.isGranted == 1) {
                    return true;
                }
            }
            return false;
        }
    }

    private boolean isShouldToShowRequest(Activity activity, boolean forced, String... permissions) {
        boolean isShouldShowRequest = true;
        if (isAppropriateVersionCode()) {
            for (String permission : permissions) {
                isShouldShowRequest = isShouldShowRequest && !forced && ActivityCompat.shouldShowRequestPermissionRationale(activity, permission);
            }
            return isShouldShowRequest;
        } else {
            DbModule dbModule = new DbModule(getActivity().getApplicationContext());
            List<Permission> permissionsFromDB = dbModule.getPermissionsByQuery(Query.builder()
                    .table(PermissionTable.TABLE)
                    .where(PermissionTable.COLUMN_PERMISSION_NAME + " == ?")
                    .whereArgs(permissions)
                    .build());
            for (Permission permission : permissionsFromDB) {
                if (permission.isNeedToShowRequest == 1) {
                    return true;
                }
            }
            return false;
        }
    }
}
