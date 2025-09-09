FS Player Library
=================

طرق استخدام المكتبة:

1) Parcelable Streams
```
ArrayList<StreamSpec> list = new ArrayList<>();
list.add(new StreamSpec(url, label, userAgent, referer, cookie, origin));
startActivity(new Intent(context, PlayerActivity.class)
        .putParcelableArrayListExtra(PlayerActivity.EXTRA_STREAMS, list));
```

2) عبر PlayerLauncher (مستحسن)
```
PlayerLauncher.with(context)
    .addStream(url, "Live", null, null, null, null)
    .start();
```

3) JSON Array
```
String json = "[ {\"url\":\"https://example.com/live.m3u8\", \"label\":\"Live\"} ]";
startActivity(PlayerLauncher.fromJson(context, json));
```

حقول StreamSpec: url (إجباري) + اختياري: label,userAgent,referer,cookie,origin

أضف قاعدة ProGuard (في تطبيقك) إن تم تمكين التصغير:
```
-keepclassmembers class com.footstique.player.StreamSpec { *; }
```
