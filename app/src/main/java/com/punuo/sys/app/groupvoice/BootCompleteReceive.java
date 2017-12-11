package com.punuo.sys.app.groupvoice;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

public class BootCompleteReceive extends BroadcastReceiver {
    String SdCard = Environment.getExternalStorageDirectory().getAbsolutePath() + "/NPS";
    String configPath = SdCard + "/config.properties";
    private static final String TAG = "BootCompleteReceive";
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.e(TAG, "onReceive: "+PreferenceManager.getDefaultSharedPreferences(context).getString("account","") );
       if (!PreferenceManager.getDefaultSharedPreferences(context).getString("account","").isEmpty()&&SipInfo.isNetworkConnected){
           Intent intent_n = new Intent(context, MainActivity.class);
           intent_n.setAction("android.intent.action.MAIN");
           intent_n.addCategory("android.intent.category.LAUNCHER");
           intent_n.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
           context.startActivity(intent_n);
           Toast.makeText(context,"已开启",Toast.LENGTH_SHORT).show();
       }
    }
    private void loadProperties() {
        //读取配置文件
        Properties properties;
        File config = new File(configPath);
        if (config.exists()) {
            properties = loadConfig(configPath);
            if (properties != null) {
                //配置信息
                SipInfo.userAccount= properties.getProperty("account");
            }
        } else {
           return;
        }

    }
    /**
     * 读取配置文件
     */
    private Properties loadConfig(String file) {
        Properties properties = new Properties();
        try {
            FileInputStream s = new FileInputStream(file);
            properties.load(s);
        } catch (Exception e) {
            return null;
        }
        return properties;
    }
}
