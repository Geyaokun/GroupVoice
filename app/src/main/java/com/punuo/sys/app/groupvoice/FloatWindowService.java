package com.punuo.sys.app.groupvoice;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;


/**
 * Created by zimo on 15/12/15.
 */
public class FloatWindowService extends Service {

    private WindowManager wm;
    private WindowManager.LayoutParams wmParams;
    private CustomeMovebutton CustomeMovebutton;
    private Boolean state=true;

    @Override
    public void onCreate() {
        super.onCreate();
        createFloatView();
    }

    private void createFloatView() {
        wm = (WindowManager) getApplicationContext().getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int widthPixels = dm.widthPixels;
        int heightPixels = dm.heightPixels;
        wmParams = ((MyApplication) getApplication()).getMywmParams();
        wmParams.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
        wmParams.format= PixelFormat.RGBA_8888;//设置背景图片
        wmParams.flags= WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE ;//
        wmParams.gravity = Gravity.LEFT|Gravity.TOP;//
        wmParams.x = widthPixels-300;   //设置位置像素
        wmParams.y = heightPixels-300;
        wmParams.width=150; //设置图片大小
        wmParams.height=150;
        CustomeMovebutton = new CustomeMovebutton(getApplicationContext());
        CustomeMovebutton.setImageResource(R.drawable.folat_view);
        wm.addView(CustomeMovebutton, wmParams);
        CustomeMovebutton.setOnSpeakListener(new CustomeMovebutton.OnSpeakListener() {
            @Override
            public void onSpeakListener() {
                if (state){
                    state=false;
                    Intent home=new Intent(Intent.ACTION_MAIN);
                    home.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    home.addCategory(Intent.CATEGORY_HOME);
                    startActivity(home);
                }else{
                    state=true;
                    Intent activity=new Intent(FloatWindowService.this,ChsChange.class);
                    activity.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(activity);

                }
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if(CustomeMovebutton != null){
            wm.removeView(CustomeMovebutton);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

}
