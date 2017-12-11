package com.punuo.sys.app.groupvoice;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;

import static com.punuo.sys.app.groupvoice.SipInfo.serverIp;
import static com.punuo.sys.app.groupvoice.SipInfo.sipUser;


/**
 * Author chzjy
 * Date 2016/12/19.
 * 集群呼叫频道更换
 */
public class ChsChange extends Activity implements SipUser.ClusterNotifyListener {
    @Bind(R.id.btnCall)
    ImageButton btnCall;
    @Bind(R.id.title)
    TextView title;
    @Bind(R.id.btn1)
    Button btn1;
    @Bind(R.id.btn2)
    Button btn2;
    @Bind(R.id.btn3)
    Button btn3;
    @Bind(R.id.btn4)
    Button btn4;
    @Bind(R.id.btn5)
    Button btn5;
    @Bind(R.id.btn6)
    Button btn6;
    ListView clusterList;
    int i = 1;
    int j = 1;


    public ClusterAdapter clusterAdapter;

    public Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            new Thread() {
                @Override
                public void run() {
                    try {
                        GroupInfo.rtpAudio.changeParticipant(serverIp, GroupInfo.port);
                        GroupInfo.groupUdpThread = new GroupUdpThread(serverIp, GroupInfo.port);
                        GroupInfo.groupUdpThread.startThread();
                        GroupInfo.groupKeepAlive = new GroupKeepAlive();
                        GroupInfo.groupKeepAlive.startThread();
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                title.setText("频道更换(当前:频道" + (GroupInfo.port % 7006 + 1) + ")");
                                MyToast.show(ChsChange.this, "频道切换至" + (GroupInfo.port % 7006 + 1), Toast.LENGTH_SHORT);
                            }
                        });
                    }
                }
            }.start();
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chschange);
        ActivityCollector.addActivity(this);
        sipUser.setClusterNotifyListener(this);
        ButterKnife.bind(this);
        Log.e("task", "onCreate: "+getTaskId() );
        startService(new Intent(ChsChange.this,FloatWindowService.class));
        SipInfo.loginReplace = new Handler() {
            @Override
            public void handleMessage(android.os.Message msg) {
                SipInfo.sipUser.sendMessage(SipMessageFactory.createNotifyRequest(SipInfo.sipUser, SipInfo.user_to,
                        SipInfo.user_from, BodyFactory.createLogoutBody()));
                SipInfo.sipDev.sendMessage(SipMessageFactory.createNotifyRequest(SipInfo.sipDev, SipInfo.dev_to,
                        SipInfo.dev_from, BodyFactory.createLogoutBody()));
                //关闭PTT监听服务
                stopService(new Intent(ChsChange.this, PTTService.class));
                //关闭用户心跳
                SipInfo.keepUserAlive.stopThread();
                //关闭设备心跳
                SipInfo.keepDevAlive.stopThread();
                //重置登录状态
                SipInfo.userLogined = false;
                SipInfo.devLogined = false;
                //关闭集群呼叫
                GroupInfo.rtpAudio.removeParticipant();
                GroupInfo.groupUdpThread.stopThread();
                GroupInfo.groupKeepAlive.stopThread();
                AlertDialog loginReplace = new AlertDialog.Builder(getApplicationContext())
                        .setTitle("账号异地登录")
                        .setMessage("请重新登录")
                        .setPositiveButton("确定", null)
                        .create();
                loginReplace.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
                loginReplace.show();
                loginReplace.setCancelable(false);
                loginReplace.setCanceledOnTouchOutside(false);
                startActivity(new Intent(getApplicationContext(), MainActivity.class));
                super.handleMessage(msg);
            }
        };
        clusterList = (ListView) findViewById(R.id.cluster_list);
        title.setText("频道更换(当前:频道" + (GroupInfo.port % 7006 + 1) + ")");
        clusterAdapter = new ClusterAdapter(ChsChange.this);
        clusterList.setAdapter(clusterAdapter);
        org.zoolu.sip.message.Message query_channel = SipMessageFactory.createSubscribeRequest(
                sipUser, SipInfo.user_to, SipInfo.user_from, BodyFactory.createClusterGroupQueryBody(GroupInfo.port % 7006 + 1));
        sipUser.sendMessage(query_channel);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ButterKnife.unbind(this);
        ActivityCollector.removeActivity(this);
        SipInfo.loginReplace = null;
        SipInfo.keepUserAlive.stopThread();
        SipInfo.keepDevAlive.stopThread();
        GroupInfo.wakeLock.release();
        GroupInfo.rtpAudio.removeParticipant();
        GroupInfo.groupUdpThread.stopThread();
        GroupInfo.groupKeepAlive.stopThread();
        SipInfo.userLogined = false;
        SipInfo.devLogined = false;
        //停止PPT监听服务
        stopService(new Intent(this, PTTService.class));
        stopService(new Intent(this,FloatWindowService.class));
    }

    @OnClick({R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4, R.id.btn5, R.id.btn6})
    public void onClick(View view) {
        GroupInfo.groupUdpThread.stopThread();
        GroupInfo.groupKeepAlive.stopThread();
        switch (view.getId()) {
            case R.id.btn1:
                GroupInfo.port = 7006;
                org.zoolu.sip.message.Message notify_channel_1 = SipMessageFactory.createNotifyRequest(
                        sipUser, SipInfo.user_to, SipInfo.user_from, BodyFactory.createChangeDevClusterGroupBody(SipInfo.userId, i, "1"));
                sipUser.sendMessage(notify_channel_1);
                i = 1;
                break;
            case R.id.btn2:
                GroupInfo.port = 7007;
                org.zoolu.sip.message.Message notify_channel_2 = SipMessageFactory.createNotifyRequest(
                        sipUser, SipInfo.user_to, SipInfo.user_from, BodyFactory.createChangeDevClusterGroupBody(SipInfo.userId, i, "2"));
                sipUser.sendMessage(notify_channel_2);
                i = 2;
                break;
            case R.id.btn3:
                GroupInfo.port = 7008;
                org.zoolu.sip.message.Message notify_channel_3 = SipMessageFactory.createNotifyRequest(
                        sipUser, SipInfo.user_to, SipInfo.user_from, BodyFactory.createChangeDevClusterGroupBody(SipInfo.userId, i, "3"));
                sipUser.sendMessage(notify_channel_3);
                i = 3;
                break;
            case R.id.btn4:
                GroupInfo.port = 7009;
                org.zoolu.sip.message.Message notify_channel_4 = SipMessageFactory.createNotifyRequest(
                        sipUser, SipInfo.user_to, SipInfo.user_from, BodyFactory.createChangeDevClusterGroupBody(SipInfo.userId, i, "4"));
                sipUser.sendMessage(notify_channel_4);
                i = 4;
                break;
            case R.id.btn5:
                GroupInfo.port = 7010;
                org.zoolu.sip.message.Message notify_channel_5 = SipMessageFactory.createNotifyRequest(
                        sipUser, SipInfo.user_to, SipInfo.user_from, BodyFactory.createChangeDevClusterGroupBody(SipInfo.userId, i, "5"));
                sipUser.sendMessage(notify_channel_5);
                i = 5;
                break;
            case R.id.btn6:
                GroupInfo.port = 7011;
                org.zoolu.sip.message.Message notify_channel_6 = SipMessageFactory.createNotifyRequest(
                        sipUser, SipInfo.user_to, SipInfo.user_from, BodyFactory.createChangeDevClusterGroupBody(SipInfo.userId, i, "6"));
                sipUser.sendMessage(notify_channel_6);
                i = 6;
                break;
        }
        handler.sendEmptyMessage(0x1111);
    }

    @Override
    public void onNotify() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (!SipInfo.cacheClusters.isEmpty()) {
                    clusterAdapter.appendData(SipInfo.cacheClusters);
                }
            }
        });

    }

    @Nullable
    @Override
    public CharSequence onCreateDescription() {
        return super.onCreateDescription();
    }
    @Override
    public void onBackPressed() {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setCancelable(false)
                .setMessage("是否退出集群?")
                .setNegativeButton("否", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .setPositiveButton("是", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        sipUser.sendMessage(SipMessageFactory.createNotifyRequest(sipUser, SipInfo.user_to,
                                SipInfo.user_from, BodyFactory.createLogoutBody()));
                        SipInfo.sipDev.sendMessage(SipMessageFactory.createNotifyRequest(SipInfo.sipDev, SipInfo.dev_to,
                                SipInfo.dev_from, BodyFactory.createLogoutBody()));
                        ActivityCollector.finishAll();
                        dialog.dismiss();
                    }
                }).create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
    }
}
