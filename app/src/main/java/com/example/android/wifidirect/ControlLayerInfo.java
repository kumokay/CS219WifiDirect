package com.example.android.wifidirect;

import java.io.Serializable;
import java.net.ServerSocket;
import java.util.HashMap;

/**
 * Created by kumokay on 6/7/2016.
 */
public class ControlLayerInfo implements Serializable
{
    static public String goIP = null;
    static public String myIP = null;
    public boolean isOwner;
    public HashMap<String, IPStatus> peerIP;

    ControlLayerInfo(String goIP, String myIP, boolean isOwner, HashMap<String, IPStatus> peerIP)
    {
        this.goIP = goIP;
        this.myIP = myIP;
        this.isOwner = isOwner;
        this.peerIP = new HashMap<>(peerIP);
    }
    ControlLayerInfo(ControlLayerInfo info)
    {
        this.goIP = info.goIP;
        this.myIP = info.myIP;
        this.isOwner = info.isOwner;
        this.peerIP = new HashMap<>(info.peerIP);
    }
}
