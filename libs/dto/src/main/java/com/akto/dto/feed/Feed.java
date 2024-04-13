package com.akto.dto.feed;

public abstract class Feed<T extends FeedSource> {

    int syncMarkerEpoch;

    int lastSentEpoch;

    String feedName;
}
