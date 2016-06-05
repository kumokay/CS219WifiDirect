package com.example.android.wifidirect;

import java.io.Serializable;
import java.util.HashMap;

/**
 * Created by Lui on 6/4/16.
 */

public class Request implements Serializable {
    public final String type;
    public final HashMap<String, Boolean> map;

    public Request(String type, HashMap<String, Boolean> map){
        this.type = type;
        this.map = map;

        // HashMap is not serializable
//            this.keys = new HashSet<Map.Entry<String, Boolean>>(map.entrySet());
    }

//        public HashMap<String, Boolean> getHashMap(){
//            HashMap<String, Boolean> map = new HashMap<String, Boolean>();
//            for(Map.Entry<String, Boolean> entry : keys){
//                map.put(entry.getKey(), entry.getValue());
//            }
//            return map;
//        }
}