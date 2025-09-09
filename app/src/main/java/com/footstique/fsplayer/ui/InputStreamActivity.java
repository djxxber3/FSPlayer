package com.footstique.fsplayer.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.footstique.player.PlayerActivity;
import com.footstique.fsplayer.R;

import java.util.ArrayList;

public class InputStreamActivity extends AppCompatActivity {

    private EditText urlField;
    private EditText labelField;
    private EditText userAgentField;
    private EditText refererField;
    private EditText cookieField;
    private EditText originField;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_input_stream);

        urlField = findViewById(R.id.input_url);
        labelField = findViewById(R.id.input_label);
        userAgentField = findViewById(R.id.input_useragent);
        refererField = findViewById(R.id.input_referer);
        cookieField = findViewById(R.id.input_cookie);
        originField = findViewById(R.id.input_origin);
        Button playButton = findViewById(R.id.btn_play);

        playButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launchPlayer();
            }
        });
    }

    private void launchPlayer() {
        String url = urlField.getText().toString().trim();
        if (TextUtils.isEmpty(url)) {
            urlField.setError("URL مطلوب");
            return;
        }
        String label = labelField.getText().toString().trim();
        String userAgent = userAgentField.getText().toString().trim();
        String referer = refererField.getText().toString().trim();
        String cookie = cookieField.getText().toString().trim();
        String origin = originField.getText().toString().trim();

        if (label.isEmpty()) label = "Stream";

        org.json.JSONArray arr = new org.json.JSONArray();
        org.json.JSONObject obj = new org.json.JSONObject();
        try {
            obj.put("url", url);
            obj.put("label", label);
            if (!userAgent.isEmpty()) obj.put("userAgent", userAgent);
            if (!referer.isEmpty()) obj.put("referer", referer);
            if (!cookie.isEmpty()) obj.put("cookie", cookie);
            if (!origin.isEmpty()) obj.put("origin", origin);
            arr.put(obj);
        } catch (org.json.JSONException ignored) {}

        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra(PlayerActivity.EXTRA_STREAMS_JSON, arr.toString());
        startActivity(intent);
    }
}
