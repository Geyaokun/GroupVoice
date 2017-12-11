package com.punuo.sys.app.groupvoice;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.zoolu.sip.address.NameAddress;
import org.zoolu.sip.address.SipURL;
import org.zoolu.sip.message.Message;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;
import java.util.Random;

import butterknife.Bind;
import butterknife.ButterKnife;
import static java.lang.Thread.sleep;




public class MainActivity extends AppCompatActivity {
    @Bind(R.id.username_Edit)
    EditText usernameEdit;
    @Bind(R.id.login)
    Button login;
    @Bind(R.id.main)
    LinearLayout main;
    private String SdCard;
    //配置文件路径
    private String configPath;
    private static final String TAG = "MainActivity";
    public static SharedPreferences pref;
    private CustomProgressDialog registering;
    private SharedPreferences.Editor editor;
    private Runnable devConnecting;
    private Runnable connecting;
    private Handler handler = new Handler();
    //账号不存在
    private AlertDialog accountNotExistDialog;
    //网络连接失败窗口
    private AlertDialog newWorkConnectedDialog;
    //登陆超时
    private AlertDialog timeOutDialog;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        ActivityCollector.addActivity(this);
        isNetworkreachable();
        SdCard = Environment.getExternalStorageDirectory().getAbsolutePath() + "/NPS";
        configPath = SdCard + "/config.properties";
        createDirs(SdCard);
        pref = PreferenceManager.getDefaultSharedPreferences(this);
        SipInfo.userAccount=pref.getString("account","");
        Log.d(TAG, "onCreate: "+SipInfo.userAccount);
        usernameEdit.setText(SipInfo.userAccount);
        login.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (SipInfo.isNetworkConnected) {
                    SipInfo.userAccount= usernameEdit.getText().toString();
                    Log.d(TAG, "onClick: "+SipInfo.userAccount);
                    if (SipInfo.userAccount.isEmpty()) {
                        Toast.makeText(MainActivity.this, "账号不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    SipInfo.devId = "310023000100"+SipInfo.userAccount.substring(2,4)+"0001";
                    SipInfo.userId = "310023000000"+SipInfo.userAccount.substring(2,4)+"4992";
                    Log.d(TAG, "onClick: "+SipInfo.userId);
                    beforeLogin();
                    registering = new CustomProgressDialog(MainActivity.this);
                    registering.setCancelable(false);
                    registering.setCanceledOnTouchOutside(false);
                    registering.show();
                    new Thread(connecting).start();
                }else{
                    handler.post(networkConnectedFailed);
                }
            }
        });
        connecting = new Runnable() {
            @Override
            public void run() {
                try {
                    int hostPort = new Random().nextInt(5000) + 2000;
                    SipInfo.sipUser = new SipUser(null, hostPort,MainActivity.this);
                    Message register = SipMessageFactory.createRegisterRequest(
                            SipInfo.sipUser, SipInfo.user_to, SipInfo.user_from);
                    SipInfo.sipUser.sendMessage(register);
                    sleep(1000);
                    for (int i = 0; i < 2; i++) {
                        if (!SipInfo.isAccountExist) {
                            //用户账号不存在
                            break;
                        }
                        if (!SipInfo.loginTimeout) {
                            //没有超时
                            break;
                        }
                        SipInfo.sipUser.sendMessage(register);
                        sleep(1000);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {

                    if (!SipInfo.isAccountExist) {
                        registering.dismiss();
                        /**账号不存在提示*/
                        handler.post(accountNotExist);
                    }  else if (SipInfo.loginTimeout) {
                        registering.dismiss();
                        //超时
                        handler.post(timeOut);
                    } else {
                        if (SipInfo.userLogined) {
                            Log.i(TAG, "用户登录成功!");
                            //开启用户保活心跳包
                            SipInfo.keepUserAlive = new KeepAlive();
                            SipInfo.keepUserAlive.setType(0);
                            SipInfo.keepUserAlive.startThread();

                            //启动设备注册线程
                            new Thread(devConnecting).start();
                        }
                    }
                }
            }
        };
        //设备注册线程
        devConnecting = new Runnable() {
            @Override
            public void run() {
                try {
                    int hostPort = new Random().nextInt(5000) + 2000;
                    SipInfo.sipDev = new SipDev(MainActivity.this, null, hostPort);//无网络时在主线程操作会报异常
                    Message register = SipMessageFactory.createRegisterRequest(
                            SipInfo.sipDev, SipInfo.dev_to, SipInfo.dev_from);
                    for (int i = 0; i < 3; i++) {//如果没有回应,最多重发2次
                        SipInfo.sipDev.sendMessage(register);
                        sleep(2000);
                        if (!SipInfo.dev_loginTimeout) {
                            break;
                        }
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    registering.dismiss();
                    if (SipInfo.devLogined) {
                        Log.d(TAG, "设备注册成功!");
                        Log.d(TAG, "设备心跳包发送!");
                        editor=pref.edit();
                        editor.putString("account",SipInfo.userAccount);
                        editor.apply();
                        for (int i=0;i<3;i++) {
                            org.zoolu.sip.message.Message query_channel = SipMessageFactory.createNotifyRequest(
                                    SipInfo.sipUser, SipInfo.user_to, SipInfo.user_from, BodyFactory.createQueryClusterIdBody(SipInfo.userId));
                            SipInfo.sipUser.sendMessage(query_channel);
                        }
                        startActivity(new Intent(MainActivity.this,ChsChange.class));
                        //启动设备心跳线程
                        SipInfo.keepDevAlive = new KeepAlive();
                        SipInfo.keepDevAlive.setType(1);
                        SipInfo.keepDevAlive.startThread();
                    } else {
                        new Handler(MainActivity.this.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this,"账号不存在！！",Toast.LENGTH_SHORT).show();
                                Log.e(TAG, "设备注册失败!");
                            }
                        });
                    }
                }
            }
        };
    }
    private Runnable timeOut = new Runnable() {
        @Override
        public void run() {
            if (timeOutDialog == null || !timeOutDialog.isShowing()) {
                timeOutDialog = new AlertDialog.Builder(MainActivity.this)
                        .setTitle("连接超时,请检查网络")
                        .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {

                            }
                        })
                        .create();
                timeOutDialog.show();
                timeOutDialog.setCancelable(false);
                timeOutDialog.setCanceledOnTouchOutside(false);
            }
        }
    };
    //账号不存在
    private Runnable accountNotExist = new Runnable() {
        @Override
        public void run() {
            if (accountNotExistDialog == null || !accountNotExistDialog.isShowing()) {
                accountNotExistDialog = new AlertDialog.Builder(MainActivity.this)
                        .setTitle("不存在该账号")
                        .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {

                            }
                        })
                        .create();
                accountNotExistDialog.show();
                accountNotExistDialog.setCancelable(false);
                accountNotExistDialog.setCanceledOnTouchOutside(false);
            }
        }
    };
    // 网络是否连接
    private Runnable networkConnectedFailed = new Runnable() {
        @Override
        public void run() {
            if (newWorkConnectedDialog == null || !newWorkConnectedDialog.isShowing()) {
                newWorkConnectedDialog = new AlertDialog.Builder(MainActivity.this)
                        .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                                Intent mIntent = new Intent(Settings.ACTION_SETTINGS);
                                startActivity(mIntent);
                            }
                        })
                        .setTitle("当前无网络,请检查网络连接")
                        .create();
                newWorkConnectedDialog.setCancelable(false);
                newWorkConnectedDialog.setCanceledOnTouchOutside(false);
                newWorkConnectedDialog.show();
            }
        }
    };
    //检查网络是否连接
    public boolean isNetworkreachable() {
        ConnectivityManager manager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo info = manager.getActiveNetworkInfo();
        if (info == null) {
            SipInfo.isNetworkConnected = false;
        } else {
            SipInfo.isNetworkConnected = info.getState() == NetworkInfo.State.CONNECTED;
        }
        return SipInfo.isNetworkConnected;
    }

    private void beforeLogin() {
        SipInfo.isAccountExist = true;
        SipInfo.passwordError = false;
        SipInfo.userLogined = false;
        SipInfo.loginTimeout = true;
        SipURL local = new SipURL(SipInfo.REGISTER_ID, SipInfo.serverIp, SipInfo.SERVER_PORT_USER);
        SipURL remote = new SipURL(SipInfo.SERVER_ID, SipInfo.serverIp, SipInfo.SERVER_PORT_USER);
        SipInfo.user_from = new NameAddress(SipInfo.userAccount, local);
        SipInfo.user_to = new NameAddress(SipInfo.SERVER_NAME, remote);
        SipInfo.devLogined = false;
        SipInfo.dev_loginTimeout = true;
        SipURL local_dev = new SipURL(SipInfo.devId, SipInfo.serverIp, SipInfo.SERVER_PORT_DEV);
        SipURL remote_dev = new SipURL(SipInfo.SERVER_ID, SipInfo.serverIp, SipInfo.SERVER_PORT_DEV);
        SipInfo.dev_from = new NameAddress(SipInfo.devId, local_dev);
        SipInfo.dev_to = new NameAddress(SipInfo.SERVER_NAME, remote_dev);
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

    /**
     * 保存配置文件
     */
    public boolean saveConfig(String configPath, Properties properties) {
        try {
            File config = new File(configPath);
            if (!config.exists())
                config.createNewFile();
            FileOutputStream s = new FileOutputStream(config);
            properties.store(s, "");
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }
    //创建文件夹
    private boolean createDirs(String dir) {
        try {
            File dirPath = new File(dir);
            if (!dirPath.exists()) {
                dirPath.mkdirs();
            }
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
            return false;
        }
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ButterKnife.unbind(this);
        ActivityCollector.removeActivity(this);
    }
}
