Player Module
==============

This library is now fully self-contained. Host apps only need to depend on :player and pass streams via the Parcelable class `StreamSpec`.

Usage snippet (Java):

ArrayList<StreamSpec> streams = new ArrayList<>();
streams.add(new StreamSpec("https://example.com/stream.m3u8", "Label", null, null, null, null));
Intent i = new Intent(context, PlayerActivity.class);
i.putParcelableArrayListExtra(PlayerActivity.EXTRA_STREAMS, streams);
context.startActivity(i);

Key design points:
 - No dependency on external model module.
 - Parcelable for efficient Intent transport.
 - Extend `StreamSpec` if new headers needed.
