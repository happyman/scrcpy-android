package org.las2mile.scrcpy;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.AssetManager;
import android.os.IBinder;
import android.util.Base64;
import android.util.Log;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceView;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

public class ScrcpyHost implements Scrcpy.ServiceCallbacks{

    private Context context;
    //scrcpy related
    private Scrcpy scrcpy;
    private static boolean serviceBound = false;
    private static boolean first_time = true;

    private static int screenWidth;
    private static int screenHeight;
    private int videoBitrate;

    private String serverAdr = null;
    private int serverPrt = 5555;
    private Surface surface;
    private static float remote_device_width;
    private static float remote_device_height;

    private byte[] fileBase64;
    private SendCommands sendCommands;
    private String local_ip;

    ConnectCallBack connectCallBack;

    public void setConnectCallBack(ConnectCallBack connectCallBack) {
        this.connectCallBack = connectCallBack;
    }

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            scrcpy = ((Scrcpy.MyServiceBinder) iBinder).getService();
            scrcpy.setServiceCallbacks(ScrcpyHost.this);
            serviceBound = true;
            if (first_time) {
                scrcpy.start(surface, serverAdr, serverPrt, screenHeight, screenWidth);
                int count = 100;
                while (count!=0 && !scrcpy.check_socket_connection()){
                    count --;
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                if (count == 0){
                    if (serviceBound) {
                        scrcpy.StopService();
                        context.unbindService(serviceConnection);
                        serviceBound = false;

                    }
                }else{
                    int[] rem_res = scrcpy.get_remote_device_resolution();
                    remote_device_height = rem_res[1];
                    remote_device_width = rem_res[0];
                    first_time = false;
                    Log.d("Log", "onServiceConnected: "+remote_device_height+"|"+remote_device_width);
                    connectCallBack.onConnect(Math.min(remote_device_width, remote_device_height), Math.max(remote_device_width, remote_device_height));
                }
            } else {
                scrcpy.setParms(surface, screenWidth, screenHeight);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            serviceBound = false;
        }
    };


    private void exectJar(){
        AssetManager assetManager = context.getAssets();
        try {
            InputStream input_Stream = assetManager.open("scrcpy-server.jar");
            byte[] buffer = new byte[input_Stream.available()];
            input_Stream.read(buffer);
            fileBase64 = Base64.encode(buffer, 2);
        } catch (IOException e) {
            Log.e("Asset Manager", e.getMessage());
        }
    }


    public void connect(Context context, String clientIp, int port, int width, int height, int bitrate, Surface display){
        this.context = context;
        screenWidth = width;
        screenHeight = height;
        videoBitrate = bitrate;
        surface = display;
        serverAdr = clientIp;
        serverPrt = port;

        exectJar();
        sendCommands = new SendCommands();

        local_ip = wifiIpAddress();
        if ((!serverAdr.isEmpty()) && (serverPrt != 0)) {
            if (sendCommands.SendAdbCommands(context, fileBase64, serverAdr, serverPrt, local_ip, videoBitrate, Math.max(screenHeight, screenWidth)) == 0) {
                start_screen_copy_magic();
            }
        }
    }

    private void start_screen_copy_magic() {
        Intent intent = new Intent(context, Scrcpy.class);
        context.startService(intent);
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }


    protected String wifiIpAddress() {
//https://stackoverflow.com/questions/6064510/how-to-get-ip-address-of-the-device-from-code
        try {
            InetAddress ipv4 = null;
            InetAddress ipv6 = null;
            for (Enumeration<NetworkInterface> en = NetworkInterface
                    .getNetworkInterfaces(); en.hasMoreElements(); ) {
                NetworkInterface int_f = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = int_f
                        .getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (inetAddress instanceof Inet6Address) {
                        ipv6 = inetAddress;
                        continue;
                    }
                    if (inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                        ipv4 = inetAddress;
                        continue;
                    }
                    return inetAddress.getHostAddress();
                }
            }
            if (ipv6 != null) {
                return ipv6.getHostAddress();
            }
            if (ipv4 != null) {
                return ipv4.getHostAddress();
            }
            return null;
        } catch (SocketException ex) {
            ex.printStackTrace();
        }
        return null;
    }

    public boolean touch(MotionEvent motionEvent,int surfaceW,int surfaceH){
        return scrcpy.touchevent(motionEvent, surfaceW, surfaceH);
    }

    public void keyEvent(int keyCode){
        scrcpy.sendKeyevent(keyCode);
    }


    @Override
    public void loadNewRotation() {
        if (first_time){
            int[] rem_res = scrcpy.get_remote_device_resolution();
            remote_device_height = rem_res[1];
            remote_device_width = rem_res[0];
            first_time = false;
        }
        //TODO
    }

    public void destroy(){
        if (serviceBound) {
            scrcpy.StopService();
            context.unbindService(serviceConnection);
            Intent intent = new Intent(context, Scrcpy.class);
            context.stopService(intent);
        }
    }

    public interface ConnectCallBack{
        void onConnect(float w,float h);
    }
}
