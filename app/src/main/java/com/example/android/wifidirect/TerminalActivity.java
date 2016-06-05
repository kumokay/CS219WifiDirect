package com.example.android.wifidirect;

import android.os.Bundle;
import android.app.Activity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public class TerminalActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_terminal);
        getActionBar().setDisplayHomeAsUpEnabled(true);


        final Button btn_send = (Button) findViewById(R.id.button_Send);
        final Button btn_clear = (Button) findViewById(R.id.button_Clear);
        final EditText editText_cmd = (EditText) findViewById(R.id.editText_Command);
        final TextView textView_terminal = (TextView) findViewById(R.id.textView_Terminal);

        btn_clear.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                editText_cmd.setText("");
            }
        });

        btn_send.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Perform action on click
                String command = editText_cmd.getText().toString();
                textView_terminal.append("cmd> " + command + "\n");
                // sendCmd
                String result = doSendCommandAndWaitResult(command);
                textView_terminal.append("\t" + result + "\n");
            }
        });
    }

    private String doSendCommandAndWaitResult(String command)
    {
        // send command
        // get result
        return "OK";
    }
}
