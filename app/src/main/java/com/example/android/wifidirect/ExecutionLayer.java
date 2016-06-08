package com.example.android.wifidirect;

import android.util.Log;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Created by Lui on 6/5/16.
 */
public class ExecutionLayer {
    public static final String TAG = "ExecutionLayer";
    private TerminalFragment terminal_activity = null;

    //ExecutionLayer(){}
    ExecutionLayer(TerminalFragment activity)
    {
        terminal_activity = activity;
    }


    boolean isRunning = false;
    DataOutputStream writer;

    public void start(){

        if(isRunning)
        {
            return;
        }

        isRunning = true;

        try{
            //final Process su = Runtime.getRuntime().exec("sh"); // for android emulator testing
            final Process su = Runtime.getRuntime().exec("su");

            // Print Errors
            new Thread() {
                @Override
                public void run() {
                    BufferedReader ir = new BufferedReader(new InputStreamReader(su.getErrorStream()));
                    String line = null;
                    try {
                        while (isRunning && (line = ir.readLine()) != null) {
                            Log.d(ExecutionLayer.TAG, line + '\n');
                            terminal_activity.writeResultToTerminal(line + '\n');
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }.start();

            // Print response
            new Thread() {
                @Override
                public void run() {
                    BufferedReader ir = new BufferedReader(new InputStreamReader(su.getInputStream()));
                    String line = null;
                    try {
                        while (isRunning && (line = ir.readLine()) != null) {
                            Log.d(ExecutionLayer.TAG, line + '\n');
                            terminal_activity.writeResultToTerminal(line + '\n');
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }.start();

            // Print exit status
            new Thread() {
                @Override
                public void run() {
                    int exitCode = 0;
                    try {
                        exitCode = su.waitFor();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    Log.d(ExecutionLayer.TAG, "Exited with code" + exitCode + '\n');
                }
            }.start();

            writer = new DataOutputStream(su.getOutputStream());

        }catch(IOException e){
            Log.e(WiFiDirectActivity.TAG, "Launching Execution Layer failed");
            e.printStackTrace();
        }
    }
/* kumokay: dont need this
    public void stop(){
        isRunning = false;
    }*/


    public void executeCommand(String cmd){

        try{
            writer.writeBytes(cmd + '\n');
            writer.flush();
            Log.d(ExecutionLayer.TAG, cmd + '\n');

        } catch (IOException e){
            Log.e(ExecutionLayer.TAG, "Error: IOException encountered");
            e.printStackTrace();
        }
    }
}
