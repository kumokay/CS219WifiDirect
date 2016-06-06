package com.example.android.wifidirect;

import java.io.Serializable;

/**
 * Created by Lui on 6/4/16.
 */
public class IPStatus implements Serializable {
    private Boolean started;
    private Integer missedHeartbeat;

    public IPStatus(boolean started){
        this.started = started;
        this.missedHeartbeat = 0;
    }

    public IPStatus(){
        this.started = false;
        this.missedHeartbeat = 0;
    }

    public String toString(){
        return "" + started.toString() + "\t" + missedHeartbeat.toString();
    }

    public void updateStatus(Boolean update){
        started = update;
    }

    public Boolean getStartStatus() {
        return started;
    }

    public void incHeartBeat(){
        missedHeartbeat = new Integer(missedHeartbeat + 1);
    }

    public void decHeartBeat(){
        missedHeartbeat = new Integer(missedHeartbeat - 1);
    }
}