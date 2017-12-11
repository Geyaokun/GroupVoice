package com.punuo.sys.app.groupvoice;

import android.app.Application;
import android.view.WindowManager;

/**
 * Created by asus on 2017/12/7.
 */

public class MyApplication extends Application {
    private WindowManager.LayoutParams wmParams=new WindowManager.LayoutParams();
    public WindowManager.LayoutParams getMywmParams(){
        return wmParams;
    }
}
