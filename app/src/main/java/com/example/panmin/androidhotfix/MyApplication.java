package com.example.panmin.androidhotfix;

import android.app.Application;
import android.content.Intent;
import android.content.SharedPreferences;
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

    private static String ISUPDATEPATCH = "ISUPDATEPATCH";

    @Override
    public void onCreate() {
        super.onCreate();

        SharedPreferences preferences = getSharedPreferences(getApplicationInfo().packageName,MODE_PRIVATE);
        boolean isUpdatePatch = preferences.getBoolean(ISUPDATEPATCH, false);

        patchManager = new PatchManager(this);
        patchManager.init("1.0");//current version
        patchManager.loadPatch();

        if(!isUpdatePatch) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(5000);//模拟网络下载耗时
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    String patchFileString = "/sdcard/patch.apatch";
                    File apatchPath = new File(patchFileString);
                    if (apatchPath.exists()) {
                        Log.i(TAG, "补丁文件存在");
                        try {
                            patchManager.removeAllPatch();
                            patchManager.addPatch(patchFileString);
                            Log.i(TAG, "add patch success");
                            //patchManager.loadPatch();
                            SharedPreferences preferences1 = getSharedPreferences(getApplicationInfo().packageName,MODE_PRIVATE);
                            SharedPreferences.Editor edit = preferences1.edit();
                            edit.putBoolean(ISUPDATEPATCH,true);
                            edit.commit();
                            Log.i(TAG, "SharedPreferences save success");
                            //重启代码
                            Intent i = getBaseContext().getPackageManager()
                                    .getLaunchIntentForPackage(getBaseContext().getPackageName());
                            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                            startActivity(i);
                            Log.i(TAG, "restart....");
                        } catch (IOException e) {
                            Log.i(TAG, "打补丁出错了" + e.getMessage());
                            e.printStackTrace();
                        }
                    } else {
                        Log.i(TAG, "补丁文件不存在");
                    }
                }
            }).start();
        }
    }
}
