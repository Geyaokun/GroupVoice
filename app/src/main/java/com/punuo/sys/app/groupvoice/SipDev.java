package com.punuo.sys.app.groupvoice;

import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.util.Log;


import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.zoolu.sip.address.SipURL;
import org.zoolu.sip.message.Message;
import org.zoolu.sip.provider.SipProvider;
import org.zoolu.sip.provider.Transport;
import org.zoolu.sip.provider.TransportConnId;

import java.io.IOException;
import java.io.StringReader;
import java.net.SocketException;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;


/**
 * Author chzjy
 * Date 2016/12/19.
 */

public class SipDev extends SipProvider {
    public static final String TAG = "SipDev";
    public static final String[] PROTOCOLS = {"udp"};
    private Context context;
    private ExecutorService pool = Executors.newFixedThreadPool(3);
    private WorkerLoginListener workerLoginListener;

    public SipDev(Context context, String viaAddr, int hostPort) {
        super(viaAddr, hostPort, PROTOCOLS, null);
        this.context = context;
    }

    public TransportConnId sendMessage(Message msg) {
        return sendMessage(msg, SipInfo.serverIp, SipInfo.SERVER_PORT_DEV);
    }

    public TransportConnId sendMessage(final Message msg, final String destAddr, final int destPort) {
        Log.d(TAG, "<----------send sip message---------->");
        Log.d(TAG, msg.toString());
        TransportConnId id = null;
        try {
            id = pool.submit(new Callable<TransportConnId>() {
                public TransportConnId call() {
                    return sendMessage(msg, "udp", destAddr, destPort, 0);
                }
            }).get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        return id;
    }
    //结束线程池
    public void shutdown(){
        pool.shutdown();
    }
    public synchronized void onReceivedMessage(Transport transport, Message msg) {
        Log.d(TAG, "<----------received sip message---------->");
        Log.d(TAG, msg.toString());
        int port = msg.getRemotePort();
        if (port == SipInfo.SERVER_PORT_DEV) {
            Log.e(TAG, "onReceivedMessage: " + port);
            String body = msg.getBody();
             // 响应消息
                int code = msg.getStatusLine().getCode();
                if (code == 200) {
                    if (!responseParse(msg)) {
                        bodyParse(body);
                    }
                } else if (code == 401) {
                    SipInfo.dev_loginTimeout = false;
                } else if (code == 402) {

                }
            }

    }

    private int bodyParse(String body) {
        if (body != null) {
            StringReader sr = new StringReader(body);
            InputSource is = new InputSource(sr);
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder;
            Document document;
            try {
                builder = factory.newDocumentBuilder();
                document = builder.parse(is);
                Element root = document.getDocumentElement();
                String type = root.getTagName();
                switch (type) {
                    case "negotiate_response"://注册第一步响应
                        Element seedElement = (Element) root.getElementsByTagName("seed").item(0);
                        SipURL local = new SipURL(SipInfo.devId, SipInfo.serverIp, SipInfo.SERVER_PORT_DEV);
                        SipInfo.dev_from.setAddress(local);
                        Log.d(TAG, "收到设备注册第一步响应");
                        String password = "123456";
                        Message register = SipMessageFactory.createRegisterRequest(
                                SipInfo.sipDev, SipInfo.dev_to, SipInfo.dev_from,
                                BodyFactory.createRegisterBody(/*随便输*/password));
                        SipInfo.sipDev.sendMessage(register);
                        return 0;
                    case "login_response"://注册成功响应，心跳回复
                        if (SipInfo.devLogined) {
                            SipInfo.dev_heartbeatResponse = true;
                            Log.d(TAG, "设备收到心跳回复");
                        } else {
                            //获取电源锁,用于防止手机静默之后,心跳线程暂停
                            PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                            GroupInfo.wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, context.getClass().getCanonicalName());
                            GroupInfo.wakeLock.acquire();

                            SipInfo.devLogined = true;
                            SipInfo.dev_loginTimeout = false;
                            Log.d(TAG, "设备注册成功");
                            /*群组呼叫组别查询*/
                            SipInfo.sipDev.sendMessage(SipMessageFactory.createSubscribeRequest(SipInfo.sipDev,
                                    SipInfo.dev_to, SipInfo.dev_from, BodyFactory.createGroupSubscribeBody(SipInfo.devId)));
                        }
                        return 1;
                }
            } catch (ParserConfigurationException e) {
                e.printStackTrace();
            } catch (SAXException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }

        } else {
            Log.d(TAG, "body is null");
        }
        return -1;
    }



    private boolean responseParse(Message msg) {
        String body = msg.getBody();
        if (body != null) {
            StringReader sr = new StringReader(body);
            InputSource is = new InputSource(sr);
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder;
            Document document;
            try {
                builder = factory.newDocumentBuilder();
                document = builder.parse(is);
                Element root = document.getDocumentElement();
                String type = root.getTagName();
                switch (type) {
                    case "subscribe_grouppn_response":
                        Element codeElement = (Element) root.getElementsByTagName("code").item(0);
                        String code = codeElement.getFirstChild().getNodeValue();
                        if (code.equals("200")) {
                            Element groupNumElement = (Element) root.getElementsByTagName("group_num").item(0);
                            Element peerElement = (Element) root.getElementsByTagName("peer").item(0);
                            Element levelElement = (Element) root.getElementsByTagName("level").item(0);
                            Element nameElement = (Element) root.getElementsByTagName("name").item(0);
                            GroupInfo.groupNum = groupNumElement.getFirstChild().getNodeValue();
                            String peer = peerElement.getFirstChild().getNodeValue();
                            GroupInfo.ip = peer.substring(0, peer.indexOf("UDP")).trim();
                            GroupInfo.port = Integer.parseInt(peer.substring(peer.indexOf("UDP") + 3).trim())+6;
                            GroupInfo.level = levelElement.getFirstChild().getNodeValue();
                            SipInfo.devName = nameElement.getFirstChild().getNodeValue();
                            Thread groupVoice = new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        GroupInfo.rtpAudio = new RtpAudio(SipInfo.serverIp, GroupInfo.port);
                                        GroupInfo.groupUdpThread = new GroupUdpThread(SipInfo.serverIp, GroupInfo.port);
                                        GroupInfo.groupUdpThread.startThread();
                                        GroupInfo.groupKeepAlive = new GroupKeepAlive();
                                        GroupInfo.groupKeepAlive.startThread();
                                        Intent PTTIntent = new Intent(context, PTTService.class);
                                        context.startService(PTTIntent);
                                    } catch (SocketException e) {
                                        e.printStackTrace();
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }, "groupVoice");
                            groupVoice.start();
                        }
                        return true;
                    default:
                        return false;
                }
            } catch (ParserConfigurationException e) {
                e.printStackTrace();
            } catch (SAXException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }


        } else {
            Log.d(TAG, "BODY IS NULL");
            return true;
        }
        return false;
    }

    public interface WorkerLoginListener {
        void loginRes(String name);

        void loginAckRes(String result);
    }

    public void setWorkerLoginListener(WorkerLoginListener workerLoginListener) {
        this.workerLoginListener = workerLoginListener;
    }
}
