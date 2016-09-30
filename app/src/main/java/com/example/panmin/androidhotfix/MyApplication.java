package com.example.panmin.androidhotfix;

import android.app.Application;
import android.util.Log;

import com.alipay.euler.andfix.patch.PatchManager;

import java.io.File;
import java.io.IOException;

/**
 * Created by panmin on 16-9-29.
 */

public class MyApplication extends Application {

    private static final String TAG = "MyApplication";
    private PatchManager patchManager;

    @Override
    public void onCreate() {
        super.onCreate();

        patchManager = new PatchManager(this);
        patchManager.init("1.0");//current version
        patchManager.loadPatch();

        String patchFileString = "/sdcard/patch.apatch";
        File apatchPath = new File(patchFileString);
        if (apatchPath.exists()) {
            Log.i(TAG, "补丁文件存在");
            try {
                patchManager.addPatch(patchFileString);
            } catch (IOException e) {
                Log.i(TAG, "打补丁出错了"+e.getMessage());
                e.printStackTrace();
            }
        } else {
            Log.i(TAG, "补丁文件不存在");
        }

    }
}
