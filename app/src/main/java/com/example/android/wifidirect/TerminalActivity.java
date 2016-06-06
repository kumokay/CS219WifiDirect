package com.example.android.wifidirect;

import android.os.Bundle;
import android.app.Activity;
import android.text.method.ScrollingMovementMethod;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public class TerminalActivity extends Activity {

    ExecutionLayer executionLayer = null;

    private void start_executionLayer()
    {
        if(executionLayer == null)
        {
            executionLayer = new ExecutionLayer(this);
            executionLayer.start();
        }
    }
    private void stop_executionLayer()
    {
        if(executionLayer != null)
        {
            executionLayer.stop();
            executionLayer = null;
        }
    }
    private void setHadoopButtonText(Button btn_startStopHadoop)
    {
        if(executionLayer == null)
        {
            btn_startStopHadoop.setText("Start Hadoop");
        }
        else
        {
            btn_startStopHadoop.setText("Stop Hadoop");
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_terminal);
        getActionBar().setDisplayHomeAsUpEnabled(true);

        final Button btn_send = (Button) findViewById(R.id.button_Send);
        final Button btn_clear = (Button) findViewById(R.id.button_Clear);
        final Button btn_startStopHadoop = (Button) findViewById(R.id.button_startStopHadoop);
        final EditText editText_cmd = (EditText) findViewById(R.id.editText_Command);
        final TextView textView_terminal = (TextView) findViewById(R.id.textView_Terminal);
        //textView_terminal.setMaxLines(30);
        textView_terminal.setVerticalScrollBarEnabled(true);
        textView_terminal.setGravity(Gravity.BOTTOM);
        //textView_terminal.setMovementMethod(new ScrollingMovementMethod());

        setHadoopButtonText(btn_startStopHadoop);
        btn_startStopHadoop.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if(btn_startStopHadoop.getText().equals("Start Hadoop"))
                {
                    start_executionLayer();
                }
                else
                {
                    stop_executionLayer();
                }
                setHadoopButtonText(btn_startStopHadoop);
            }
        });
        btn_clear.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                editText_cmd.setText("");
            }
        });
        btn_send.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // if execution layer is not running, start it
                start_executionLayer();
                // send command
                String command = editText_cmd.getText().toString();
                textView_terminal.append("cmd> " + command + "\n");
                sendCommand(command);
            }
        });
    }

    public void writeResultToTerminal(String result)
    {
        final String str = result;
        runOnUiThread(new Runnable() {
            public void run() {
                final TextView textView_terminal = (TextView) findViewById(R.id.textView_Terminal);
                textView_terminal.append(str);
            }
        });
    }

    private void sendCommand(String command)
    {
        executionLayer.executeCommand(command);
    }
}
