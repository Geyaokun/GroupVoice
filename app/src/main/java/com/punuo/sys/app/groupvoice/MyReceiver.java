package com.punuo.sys.app.groupvoice;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;
import android.widget.Toast;

import com.alibaba.fastjson.JSON;



public class MyReceiver extends BroadcastReceiver {
    public MyReceiver() {
    }

    @Override
    public synchronized void onReceive(Context context, Intent intent) {
        // TODO: This method is called when the BroadcastReceiver is receiving
        // an Intent broadcast.
        KeyEvent keyEvent = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
        int keyCode = keyEvent.getKeyCode();
        System.out.println(keyCode);
        int state = keyEvent.getAction();
        switch (keyCode) {

            case 261:
                MyToast.show(context, "PTT键按下", Toast.LENGTH_SHORT);
                System.out.println("state = " + state);
                if (state == 0) {
                    MyToast.show(context, "正在说话...", Toast.LENGTH_LONG);
                    if (GroupInfo.rtpAudio != null) {
                        System.out.println(111);
                        GroupInfo.rtpAudio.pttChanged(true);
                        waitFor();
                        GroupSignaling groupSignaling = new GroupSignaling();
                        groupSignaling.setStart(SipInfo.devId);
                        groupSignaling.setLevel(GroupInfo.level);
                        String start = JSON.toJSONString(groupSignaling);
                        GroupInfo.groupUdpThread.sendMsg(start.getBytes());
                    }
                } else {
                    MyToast.show(context, "结束说话...", Toast.LENGTH_LONG);
                    if (GroupInfo.rtpAudio != null) {
                        System.out.println(222);
                        GroupInfo.rtpAudio.pttChanged(false);
                        if (GroupInfo.isSpeak) {
                            GroupSignaling groupSignaling = new GroupSignaling();
                            groupSignaling.setEnd(SipInfo.devId);
                            String end = JSON.toJSONString(groupSignaling);
                            GroupInfo.groupUdpThread.sendMsg(end.getBytes());
                            waitFor();
                        }
                    }
                }
                break;
        }
    }

    private void waitFor() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
