package com.akto.dto.feed;

import com.akto.dao.MCollection;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.List;

public abstract class Feed<T extends FeedSource> {

    int syncMarkerEpoch;

    int lastSentEpoch;

    String feedName;
}
