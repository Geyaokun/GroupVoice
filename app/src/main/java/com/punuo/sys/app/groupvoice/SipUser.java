package com.punuo.sys.app.groupvoice;

import android.content.Context;
import android.media.MediaPlayer;
import android.os.Environment;
import android.util.Log;


import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.zoolu.sip.address.NameAddress;
import org.zoolu.sip.address.SipURL;
import org.zoolu.sip.message.Message;
import org.zoolu.sip.provider.SipProvider;
import org.zoolu.sip.provider.Transport;
import org.zoolu.sip.provider.TransportConnId;

import java.io.StringReader;
import java.util.Collections;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Author chzjy
 * Date 2016/12/19.
 */

public class SipUser extends SipProvider {
    private Context context;
    public static String TAG ="SipUser";
    public static String[] PROTOCOLS = {"udp"};
    //线程池
    private ExecutorService pool = Executors.newFixedThreadPool(3);
    //集群监听
    private ClusterNotifyListener clusterNotifyListener;


    public SipUser(String via_addr, int host_port, Context context) {
        super(via_addr, host_port, PROTOCOLS, null);
        this.context = context;
    }

    public TransportConnId sendMessage(Message msg) {
        return sendMessage(msg, SipInfo.serverIp, SipInfo.SERVER_PORT_USER);
    }

    public TransportConnId sendMessage(final Message msg, final String destAddr, final int destPort) {
        Log.i(TAG, "<----------send sip message---------->");
        Log.i(TAG, msg.toString());
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
    public void shutdown() {
        pool.shutdown();
    }

    public synchronized void onReceivedMessage(Transport transport, Message msg) {
        Log.i(TAG, "<----------received sip message---------->");
        Log.i(TAG, msg.toString());
        //sip消息来源的RemoteProt,6060为设备,6061为用户
        int port = msg.getRemotePort();
        if (port == SipInfo.SERVER_PORT_USER) {
            Log.i(TAG, "PORT = " + port);
            if (msg.isRequest()) {// 请求消息
                requestParse(msg);
            } else { // 响应消息
                int code = msg.getStatusLine().getCode();
                switch (code) {
                    case 200:
                        responseParse(msg);
                        break;
                    case 401://密码错误
                        SipInfo.loginTimeout = false;
                        SipInfo.isAccountExist = true;
                        SipInfo.passwordError = true;
                        break;
                    case 402://账号不存在
                        SipInfo.passwordError = false;
                        SipInfo.loginTimeout = false;
                        SipInfo.isAccountExist = false;
                        break;
                }
            }
        }
    }

    //请求解析
    private boolean requestParse(final Message msg) {
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
                final String type = root.getTagName();
                switch (type) {
                    case "cluster_users":
                        if (SipInfo.finish){
                        SipInfo.cacheClusters.clear();}
                        final NodeList clusters = root.getElementsByTagName("cluster_user");
                        Log.d(TAG, "requestParse: " + clusters.getLength());
                        for (int i = 0; i < clusters.getLength(); i++) {
                            Cluster cluster = new Cluster();
                            Element clusterElement = (Element) clusters.item(i);
                            Element nameElement = (Element) clusterElement.getElementsByTagName("name").item(0);
                            if ( nameElement.getFirstChild().getNodeValue().equals("超级用户")||nameElement.getFirstChild().getNodeValue().indexOf("单兵")!=-1) {
                                continue;
                            }
                            cluster.setName(nameElement.getFirstChild().getNodeValue());
                            SipInfo.cacheClusters.add(cluster);
                            Log.d(TAG, "requestParse: " + "添加完毕"+SipInfo.cacheClusters.size());
                        }
                        Element f = (Element) root.getElementsByTagName("finish").item(0);
                        int isfinish = Integer.parseInt(f.getFirstChild().getNodeValue());
                        if (isfinish == 1) {
                            if (clusterNotifyListener != null && SipInfo.cacheClusters != null) {
                                SipInfo.finish=true;
                                Collections.sort(SipInfo.cacheClusters);
                                Log.d(TAG, "requestParse: " + "更新");
                                clusterNotifyListener.onNotify();
                                SipInfo.sipUser.sendMessage(SipMessageFactory.createResponse(msg, 200, "OK", ""));
                            }
                        }else {
                            SipInfo.finish=false;
                            SipInfo.sipUser.sendMessage(SipMessageFactory.createResponse(msg, 200, "OK", ""));
                        }
                        return true;
                    case "session_notify":
                        if (SipInfo.loginReplace != null) {
                            SipInfo.loginReplace.sendEmptyMessage(0x1111);
                        }
                        return true;
                    case "user_notify_for_cluster":
                        Log.d(TAG, Thread.currentThread().getName());
                        Element LiveElement = (Element) root.getElementsByTagName("live").item(0);
                        Element userIdElement = (Element) root.getElementsByTagName("userid").item(0);
                        if (userIdElement.getFirstChild().getNodeValue() != SipInfo.userAccount) {
                            Cluster user = new Cluster();
                            user.setName(userIdElement.getFirstChild().getNodeValue());
                            int indexOfUser = SipInfo.cacheClusters.indexOf(user);
                            if (indexOfUser != -1) {
                                if (LiveElement.getFirstChild().getNodeValue().equals("False")) {
                                    SipInfo.cacheClusters.remove(indexOfUser);
                                    if (clusterNotifyListener != null) {
                                        clusterNotifyListener.onNotify();
                                    }
                                }
                            } else {
                                org.zoolu.sip.message.Message query_channel = SipMessageFactory.createNotifyRequest(
                                        SipInfo.sipUser, SipInfo.user_to, SipInfo.user_from, BodyFactory.createQueryClusterIdBody(SipInfo.userId));
                                SipInfo.sipUser.sendMessage(query_channel);
                                if (clusterNotifyListener != null) {
                                    clusterNotifyListener.onNotify();
                                }
                            }
                        }
                        return true;
                }
            } catch (Exception e) {
                Log.e(TAG, "requestParse: ", e);
            }
        } else {
            Log.i(TAG + "requestParse", "BODY IS NULL");
            return true;
        }
        return false;
    }

    //响应解析
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
                final String type = root.getTagName();
                Element codeElement;
                String code;
                switch (type) {
                    case "negotiate_response":/*注册第一步响应*/
                        Element seedElement = (Element) root.getElementsByTagName("seed").item(0);
                        Element userIdElement = (Element) root.getElementsByTagName("user_id").item(0);
                        if (userIdElement != null) {//如果掉线服务器会当成设备注册第一步
                            Element saltElement = (Element) root.getElementsByTagName("salt").item(0);
                            Element phoneNumElement = (Element) root.getElementsByTagName("phone_num").item(0);
                            Element realNameElement = (Element) root.getElementsByTagName("real_name").item(0);
                            SipInfo.userId = userIdElement.getFirstChild().getNodeValue();
                            SipInfo.userRealname = realNameElement.getFirstChild().getNodeValue();
                            SipURL local = new SipURL(SipInfo.userId, SipInfo.serverIp, SipInfo.SERVER_PORT_USER);
                            SipInfo.user_from.setAddress(local);
                            SipInfo.userPhoneNumber = phoneNumElement.getFirstChild().getNodeValue();
                            SipInfo.seed = seedElement.getFirstChild().getNodeValue();
                            SipInfo.salt = saltElement.getFirstChild().getNodeValue();
                            Log.i(TAG, "收到用户注册第一步响应");
                            SHA1 sha1 = SHA1.getInstance();
                            String password = sha1.hashData(SipInfo.salt + SipInfo.passWord);
                            password = sha1.hashData(SipInfo.seed + password);
                            Message register = SipMessageFactory.createRegisterRequest(
                                    SipInfo.sipUser, SipInfo.user_to, SipInfo.user_from,
                                    BodyFactory.createRegisterBody(password));
                            SipInfo.sipUser.sendMessage(register);
                        } else {
                            Log.e(TAG, "掉线");
                            SipInfo.userLogined = false;
                            SipURL local = new SipURL(SipInfo.REGISTER_ID, SipInfo.serverIp, SipInfo.SERVER_PORT_USER);
                            NameAddress from = new NameAddress(SipInfo.userAccount, local);
                            Message register = SipMessageFactory.createRegisterRequest(
                                    SipInfo.sipUser, SipInfo.user_to, from);
                            SipInfo.sipUser.sendMessage(register);
                        }
                        return true;
                    case "login_response":
                        if (SipInfo.userLogined) {
                            SipInfo.user_heartbeatResponse = true;
                            Log.i(TAG, "收到用户心跳回复");
                            Log.i(TAG, "用户在线!");
                        } else {
                            SipInfo.userLogined = true;
                            SipInfo.loginTimeout = false;
                            Log.i(TAG, "用户注册成功");
                        }
                        return true;
                    case "query_cluster_id_response":
                        Element clusterIdElement = (Element) root.getElementsByTagName("clusterId").item(0);
                        String clusterId = clusterIdElement.getFirstChild().getNodeValue();
                        org.zoolu.sip.message.Message query_channel = SipMessageFactory.createSubscribeRequest(
                                SipInfo.sipUser, SipInfo.user_to, SipInfo.user_from, BodyFactory.createClusterGroupQueryBody(Integer.parseInt(clusterId)));
                        SipInfo.sipUser.sendMessage(query_channel);
                        return true;
                }
            } catch (Exception e) {
                Log.e(TAG, "responseParse: ", e);
            }
        } else {
            Log.i(TAG + "responseParse", "BODY IS NULL");
            return true;
        }
        return false;
    }

    public void setClusterNotifyListener(ClusterNotifyListener clusterNotifyListener) {
        this.clusterNotifyListener = clusterNotifyListener;
    }

    public interface ClusterNotifyListener {
        void onNotify();
    }

}
