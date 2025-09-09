package com.footstique.player;

import android.content.Context;
import android.content.Intent;

import java.util.ArrayList;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class PlayerLauncher {

    private final Context context;
    private final JSONArray streams = new JSONArray();

    private PlayerLauncher(Context context) {
        this.context = context;
    }

    public static PlayerLauncher with(Context context) {
        return new PlayerLauncher(context);
    }

    public PlayerLauncher addStream(String url, String label, String userAgent, String referer, String cookie, String origin) {
        try {
            JSONObject o = new JSONObject();
            o.put("url", url);
            if (label != null) o.put("label", label);
            if (userAgent != null) o.put("userAgent", userAgent);
            if (referer != null) o.put("referer", referer);
            if (cookie != null) o.put("cookie", cookie);
            if (origin != null) o.put("origin", origin);
            streams.put(o);
        } catch (JSONException ignored) {}
        return this;
    }

    public Intent buildIntent() {
        Intent intent = new Intent(context, PlayerActivity.class);
        if (streams.length() > 0) {
            intent.putExtra(PlayerActivity.EXTRA_STREAMS_JSON, streams.toString());
        }
        return intent;
    }

    public void start() {
        context.startActivity(buildIntent());
    }

    /**
     * Create intent from JSON array string (see class javadoc for format).
     */
    public static Intent fromJson(Context context, String jsonArrayString) {
    Intent intent = new Intent(context, PlayerActivity.class);
    intent.putExtra(PlayerActivity.EXTRA_STREAMS_JSON, jsonArrayString);
    return intent;
    }
}
