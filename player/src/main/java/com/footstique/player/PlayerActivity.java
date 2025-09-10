package com.footstique.player;

import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListPopupWindow;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.core.graphics.Insets;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

// Now using JSON-based streams; no external or public model class exposed.

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PlayerActivity extends AppCompatActivity {

    // Deprecated: old Parcelable key (kept for backward compatibility but ignored now)
    public static final String EXTRA_STREAMS = "streams";
    // Public JSON extra key: host apps pass a JSON array string of stream objects
    public static final String EXTRA_STREAMS_JSON = "streams_json";
    private static final String TAG = "PlayerActivity";
    private static final String DEFAULT_USER_AGENT = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36";
    private ExoPlayer exoPlayer;
    private androidx.media3.ui.PlayerView playerView;
    private LinearLayout qualityBar;
    private android.widget.HorizontalScrollView qualityBarContainer;
    private LinearLayout playerBottomControls;
    private View loadingSpinner;
    private final List<Map<String, Object>> streams = new ArrayList<>();
    private final Map<String, List<LocalStream>> byQuality = new LinkedHashMap<>();

    // ------------------- المتغيرات الجديدة للتحكم بالواجهة -------------------
    private ConstraintLayout playerUnlockControls;
    private FrameLayout playerLockControls;
    private ImageButton lockControlsButton;
    private ImageButton unlockControlsButton;
    private ImageButton screenRotateButton;
    private ImageButton videoZoomButton;
    private ArrayList<LocalStream> streamsList = new ArrayList<>();

    private boolean isControlsLocked = false;
    private int currentZoomMode = 0; // 0=Fit, 1=Stretch, 2=Zoom


    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            Window window = getWindow();
            WindowManager.LayoutParams layoutParams = window.getAttributes();
            layoutParams.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(layoutParams);
        }
    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
    setContentView(R.layout.activity_player); // أزلنا النداء المكرر
    playerView = findViewById(R.id.player_view);
  
        playerView.setControllerAnimationEnabled(false);
 
    qualityBar = playerView.findViewById(R.id.quality_bar);
    qualityBarContainer = playerView.findViewById(R.id.quality_bar_container);
    playerBottomControls = playerView.findViewById(R.id.player_bottom_controls);
    loadingSpinner = playerView.findViewById(R.id.player_loading);
        qualityBar.setVisibility(INVISIBLE);
        playerView.setControllerVisibilityListener(new PlayerView.ControllerVisibilityListener() {
            @Override
            public void onVisibilityChanged(int visibility) {
               
                if (isControlsLocked) {
                    // إذا كان مقفل، إخفاء الأزرار والـ controls
                    qualityBar.setVisibility(INVISIBLE);
                    playerUnlockControls.setVisibility(INVISIBLE);
                    playerBottomControls.setVisibility(INVISIBLE);
                } else {
                    // إظهار/إخفاء الأزرار مع شريط التحكم
                    qualityBar.setVisibility(visibility);
                    playerUnlockControls.setVisibility(visibility);
                    playerBottomControls.setVisibility(visibility);
                }
            }
        });
        playerUnlockControls = findViewById(R.id.player_unlock_controls);
        playerLockControls = findViewById(R.id.player_lock_controls);
        lockControlsButton = findViewById(R.id.btn_lock_controls);
        unlockControlsButton = findViewById(R.id.btn_unlock_controls);
        screenRotateButton = findViewById(R.id.screen_rotate);
        videoZoomButton = findViewById(R.id.btn_video_zoom);

        // إخفاء واجهة القفل في البداية
        playerLockControls.setVisibility(INVISIBLE);

        // تفعيل وظائف الأزرار الجديدة
        setupClickListeners();

        // --- استدعاء دوالك القديمة ---
        readStreamsFromIntent();
        groupByQuality();
        setupQualityButtons();

    // تفعيل وضع edge-to-edge مع معالجة insets للأزرار فقط
    enableEdgeToEdgeForVideoOnly();
    hideSystemUI();

    }

    // القيم الأساسية للحشوات حتى لا تتضاعف عند كل إعادة تطبيق للـ insets
    private int baseQualityBarPadLeft, baseQualityBarPadTop, baseQualityBarPadRight, baseQualityBarPadBottom;
    private int baseBottomControlsPadLeft, baseBottomControlsPadTop, baseBottomControlsPadRight, baseBottomControlsPadBottom;
    private int baseUnlockBtnPadLeft, baseUnlockBtnPadTop, baseUnlockBtnPadRight, baseUnlockBtnPadBottom;

    private void enableEdgeToEdgeForVideoOnly() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        // حفظ القيم الأساسية
        if (qualityBarContainer != null) {
            baseQualityBarPadLeft = qualityBarContainer.getPaddingLeft();
            baseQualityBarPadTop = qualityBarContainer.getPaddingTop();
            baseQualityBarPadRight = qualityBarContainer.getPaddingRight();
            baseQualityBarPadBottom = qualityBarContainer.getPaddingBottom();
        }
        if (playerBottomControls != null) {
            baseBottomControlsPadLeft = playerBottomControls.getPaddingLeft();
            baseBottomControlsPadTop = playerBottomControls.getPaddingTop();
            baseBottomControlsPadRight = playerBottomControls.getPaddingRight();
            baseBottomControlsPadBottom = playerBottomControls.getPaddingBottom();
        }
        if (unlockControlsButton != null) {
            baseUnlockBtnPadLeft = unlockControlsButton.getPaddingLeft();
            baseUnlockBtnPadTop = unlockControlsButton.getPaddingTop();
            baseUnlockBtnPadRight = unlockControlsButton.getPaddingRight();
            baseUnlockBtnPadBottom = unlockControlsButton.getPaddingBottom();
        }

        View root = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            // ناخذ كل من أشرطة النظام + منطقة القطع (النوتش)
            Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets cut = insets.getInsets(WindowInsetsCompat.Type.displayCutout());
            int insetLeft = Math.max(sys.left, cut.left);
            int insetTop = Math.max(sys.top, cut.top);
            int insetRight = Math.max(sys.right, cut.right);
            int insetBottom = Math.max(sys.bottom, cut.bottom);

            // الفيديو نفسه (Surface داخل PlayerView) يمتد لكامل الشاشة، لا نضيف حشوات له.
            // نضيف الحشوات فقط لعناصر التحكم لكي لا تختلط مع النوتش أو أشرطة النظام.
            if (qualityBarContainer != null) {
                qualityBarContainer.setPadding(
                        baseQualityBarPadLeft + insetLeft,
                        baseQualityBarPadTop + insetTop, // حماية من النوتش / الشريط
                        baseQualityBarPadRight + insetRight,
                        baseQualityBarPadBottom
                );
            }
            if (playerBottomControls != null) {
                playerBottomControls.setPadding(
                        baseBottomControlsPadLeft + insetLeft,
                        baseBottomControlsPadTop,
                        baseBottomControlsPadRight + insetRight,
                        baseBottomControlsPadBottom + insetBottom // فوق شريط التنقل
                );
            }
            if (unlockControlsButton != null) {
                unlockControlsButton.setPadding(
                        baseUnlockBtnPadLeft,
                        baseUnlockBtnPadTop,
                        baseUnlockBtnPadRight,
                        baseUnlockBtnPadBottom + insetBottom
                );
            }
            // ملاحظة: لا نضيف أي حشوات إلى الجذر playerUnlockControls حتى يبقى مركزه الحقيقي وسط الشاشة.
            return insets; // لا نستهلكه حتى يبقى PlayerView ممتد
        });
        // نطلب تطبيق الـ Insets الآن (خاصة بعد إخفاء الأشرطة) لضمان أخذ قيم القطع
        ViewCompat.requestApplyInsets(root);
    }

    @OptIn(markerClass = UnstableApi.class)
    private void setupClickListeners() {
        // وظيفة زر قفل الشاشة
        lockControlsButton.setOnClickListener(v -> {
            playerUnlockControls.setVisibility(INVISIBLE);
            playerLockControls.setVisibility(VISIBLE);
            qualityBar.setVisibility(INVISIBLE); // إخفاء الأزرار عند القفل
            playerBottomControls.setVisibility(INVISIBLE); // إخفاء شريط التقدم أيضاً
            isControlsLocked = true;
        });

        // وظيفة زر فتح القفل
        unlockControlsButton.setOnClickListener(v -> {
            playerLockControls.setVisibility(INVISIBLE);
            playerUnlockControls.setVisibility(VISIBLE);
            isControlsLocked = false;
            qualityBar.setVisibility(VISIBLE); // إظهار الأزرار عند إلغاء القفل
            playerBottomControls.setVisibility(VISIBLE); // إظهار شريط التقدم أيضاً
            playerView.showController(); // إظهار شريط التحكم مرة أخرى
        });

        // وظيفة زر دوران الشاشة
        screenRotateButton.setOnClickListener(v -> {
            if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            } else {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            }
        });

        // وظيفة زر الزوم (تكبير وتصغير الفيديو)
        videoZoomButton.setOnClickListener(v -> {
            currentZoomMode = (currentZoomMode + 1) % 3; // Cycle through 0, 1, 2
            switch (currentZoomMode) {
                case 0: // BEST_FIT
                    playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
                    videoZoomButton.setImageResource(R.drawable.ic_fit_screen); // تأكد من وجود هذه الأيقونة
                    break;
                case 1: // STRETCH
                    playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FILL);
                    break;
                case 2: // CROP (ZOOM)
                    playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_ZOOM);
                    break;
            }
        });
    }

    private void hideSystemUI() {
        WindowInsetsControllerCompat windowInsetsController =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (windowInsetsController == null) {
            return;
        }
        // إخفاء شريط الإشعارات العلوي وشريط التنقل السفلي
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars());
        // جعل الأشرطة تظهر بشكل مؤقت عند السحب من الحواف
        windowInsetsController.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        );
    }
    @Override
    protected void onStart() {
        super.onStart();
        initializePlayer();

        if (!streamsList.isEmpty()) {
            LocalStream first = streamsList.get(0);
            playStream(first);
        }
    }
    @Override
    protected void onResume() {
        super.onResume();
        // إعادة إخفاء أشرطة النظام لضمان بقاء وضع ملء الشاشة
        hideSystemUI();
    }

    @Override
    protected void onStop() {
        super.onStop();
        releasePlayer();
    }

    @OptIn(markerClass = UnstableApi.class)
    private void initializePlayer() {
        if (exoPlayer == null) {
            DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(this)
                    .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER);

            DefaultTrackSelector trackSelector = new DefaultTrackSelector(this);

            exoPlayer = new ExoPlayer.Builder(this, renderersFactory)
                    .setTrackSelector(trackSelector)
                    .build();

            exoPlayer.addListener(new Player.Listener() {
                @Override
                public void onPlayerError(@NonNull PlaybackException error) {
                    Log.e(TAG, "Player Error: " + error.getErrorCodeName(), error);
                    Toast.makeText(PlayerActivity.this, "Playback Error: " + error.getErrorCodeName(), Toast.LENGTH_LONG).show();
                }

                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    if (loadingSpinner != null) {
                        if (playbackState == Player.STATE_BUFFERING) {
                            loadingSpinner.setVisibility(View.VISIBLE);
                        } else if (playbackState == Player.STATE_READY || playbackState == Player.STATE_ENDED) {
                            loadingSpinner.setVisibility(View.GONE);
                        }
                    }
                }
            });

            playerView.setPlayer(exoPlayer);
            exoPlayer.setPlayWhenReady(true);
        }
    }

    private void releasePlayer() {
        if (exoPlayer != null) {
            exoPlayer.release();
            exoPlayer = null;
        }
    }

    @OptIn(markerClass = UnstableApi.class)
    private void playStream(LocalStream stream) {
        if (exoPlayer == null || stream == null) return;

        String url = stream.getUrl();
        if (url == null || url.isEmpty()) {
            Log.e(TAG, "Stream URL is null or empty.");
            return;
        }

        Map<String, String> headers = new HashMap<>();
        if (stream.getReferer() != null) headers.put("Referer", stream.getReferer());
        if (stream.getOrigin() != null) headers.put("Origin", stream.getOrigin());
        if (stream.getCookie() != null) headers.put("Cookie", stream.getCookie());

        DataSource.Factory customDataSourceFactory = () -> {
            DefaultHttpDataSource dataSource = new DefaultHttpDataSource
                    .Factory()
                    .setUserAgent(stream.getUserAgent() != null ? stream.getUserAgent() : DEFAULT_USER_AGENT)
                    .setAllowCrossProtocolRedirects(true)
                    .createDataSource();
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                dataSource.setRequestProperty(entry.getKey(), entry.getValue());
            }
            return dataSource;
        };

        MediaItem mediaItem = new MediaItem.Builder().setUri(Uri.parse(url)).build();

        int type = Util.inferContentType(Uri.parse(url));
        if (type == C.CONTENT_TYPE_HLS) {
            HlsMediaSource hls = new HlsMediaSource.Factory(customDataSourceFactory).createMediaSource(mediaItem);
            exoPlayer.setMediaSource(hls);
        } else {
            ProgressiveMediaSource prog = new ProgressiveMediaSource.Factory(customDataSourceFactory).createMediaSource(mediaItem);
            exoPlayer.setMediaSource(prog);
        }

        exoPlayer.prepare();
        exoPlayer.play();
    }

    private void groupByQuality() {
        byQuality.clear();
    for (LocalStream s : streamsList) {
            String q = s.getLabel() != null ? s.getLabel() : "AUTO"; // أو إذا عندك حقل خاص بالجودة
                List<LocalStream> list = byQuality.computeIfAbsent(q, k -> new ArrayList<LocalStream>());
            list.add(s);
        }
    }

    private void setupQualityButtons() {
        qualityBar.removeAllViews();

        // إنشاء زر منفصل لكل Stream
        for (int i = 0; i < streamsList.size(); i++) {
            LocalStream stream = streamsList.get(i);

            // إنشاء زر بـ style مخصص
            Button btn = new Button(this, null, 0, R.style.CustomQualityButton);

            // النص على الزر هو الـ label أو نص افتراضي
            String buttonText = stream.getLabel() != null && !stream.getLabel().isEmpty()
                    ? stream.getLabel()
                    : "Stream " + (i + 1);
            btn.setText(buttonText);

            // عند الضغط على الزر، شغّل هذا الـ Stream مباشرة
            btn.setOnClickListener(v -> {
                playStream(stream);
                highlightSelectedButton(btn); // <-- هذا السطر يستدعي الدالة الثانية
            });

            // إعدادات المظهر الإضافية
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            lp.setMargins(8, 8, 8, 8);
            btn.setLayoutParams(lp);

            qualityBar.addView(btn);

            // تحديد الزر الأول كمحدد افتراضياً
            if (i == 0) {
                btn.setSelected(true); // <-- هذا السطر يحدد الحالة الافتراضية
            }
        }

        // إظهار أو إخفاء الشريط حسب عدد الأزرار
        if (streamsList.size() <= 1) {
            if (qualityBarContainer != null) {
                qualityBarContainer.setVisibility(View.GONE);
            }
        } else {
            if (qualityBarContainer != null) {
                qualityBarContainer.setVisibility(View.VISIBLE);
            }
        }
    }

    // دالة لتمييز الزر المحدد
    private void highlightSelectedButton(Button selectedBtn) {
        // إعادة تعيين جميع الأزرار للحالة الطبيعية
        for (int i = 0; i < qualityBar.getChildCount(); i++) {
            View child = qualityBar.getChildAt(i);
            if (child instanceof Button) {
                child.setSelected(false);
            }
        }
        // تمييز الزر المحدد
        selectedBtn.setSelected(true); // <-- هذا السطر يغير حالة الزر عند الضغط عليه
    }
    private void showLinksPopup(View anchor, String quality, List<LocalStream> links) {
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < links.size(); i++) {
            String label = links.get(i).getLabel() != null ? links.get(i).getLabel() : "Link " + (i + 1);
            labels.add(label);
        }

        ListPopupWindow popup = new ListPopupWindow(this);
        popup.setAnchorView(anchor);
        popup.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, labels));
        popup.setOnItemClickListener((parent, view, position, id) -> {
            LocalStream selected = links.get(position);
            playStream(selected);
            popup.dismiss();
        });
        popup.show();
    }

    private Map<String, Object> getFirstStream() {
        if (!streams.isEmpty()) return streams.get(0);
        return null;
    }

    @SuppressWarnings("unchecked")
    private void readStreamsFromIntent() {
        Intent intent = getIntent();
        // Prefer JSON string now
        String jsonArray = intent.getStringExtra(EXTRA_STREAMS_JSON);
        System.out.println("DJAAAAAAAAAAAAAAAAAAAAAAABEEER HHHHH   "+jsonArray);

        if (jsonArray != null && !jsonArray.isEmpty()) {
            try {
                org.json.JSONArray arr = new org.json.JSONArray(jsonArray);
                ArrayList<LocalStream> parsed = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    org.json.JSONObject o = arr.getJSONObject(i);
                    String url = o.optString("url", null);
                    if (url == null) continue; // skip invalid
                    String label = o.optString("label", null);
                    String ua = o.optString("userAgent", null);
                    String ref = o.optString("referer", null);
                    String cookie = o.optString("cookie", null);
                    String origin = o.optString("origin", null);
                    parsed.add(new LocalStream(url, label, ua, ref, cookie, origin));
                }
                if (!parsed.isEmpty()) {
                    streamsList = parsed;
                }
            } catch (org.json.JSONException e) {
                Log.e(TAG, "Failed to parse streams JSON", e);
            }
        }
    }

    /** Internal representation of a stream (not exposed as API). */
    private static class LocalStream {
        private final String url;
        private final String label;
        private final String userAgent;
        private final String referer;
        private final String cookie;
        private final String origin;

        LocalStream(String url, String label, String userAgent, String referer, String cookie, String origin) {
            this.url = url;
            this.label = label;
            this.userAgent = userAgent;
            this.referer = referer;
            this.cookie = cookie;
            this.origin = origin;
        }
        public String getUrl() { return url; }
        public String getLabel() { return label; }
        public String getUserAgent() { return userAgent; }
        public String getReferer() { return referer; }
        public String getCookie() { return cookie; }
        public String getOrigin() { return origin; }
    }

}