//package com.akto.dto.feed;
//
//import com.akto.dao.MCollection;
//import com.mongodb.client.model.Filters;
//import org.bson.conversions.Bson;
//import org.bson.types.ObjectId;
//
//import java.util.ArrayList;
//import java.util.List;
//
//public class FeedsDao<T extends FeedSource> extends MCollection<Feed<T>> {
//
//    @Override
//    public String getDBName() {
//        return "feeds";
//    }
//
//    @Override
//    public String getCollName() {
//        return "continuous_feeds";
//    }
//
//
//    MCollection<T> dao;
//
//    List<T> getDtoSinceSyncMarkerEpoch() {
//        Bson filterQ = Filters.gt("_id", new ObjectId(syncMarkerEpoch, 0));
//        List<T> dtosToBeSent = new ArrayList<>();
//
//        for(T t: MCollection.getMCollection(dao.getDBName(), dao.getCollName(), dao.getClassT()).find(filterQ)) {
//            dtosToBeSent.add(t);
//        }
//
//        return dtosToBeSent;
//    }
//
//    abstract public int sendToDestination(List<T> dtos);
//
//
//    public void syncFeed() {
//        List<T> dtosToBeSent = getDtoSinceSyncMarkerEpoch();
//        int newMarkerEpoch = sendToDestination(dtosToBeSent);
//
//
//    }
//}
