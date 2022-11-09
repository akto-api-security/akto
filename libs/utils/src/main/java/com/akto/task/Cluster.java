package com.akto.task;

import java.util.UUID;

import com.akto.dao.context.Context;
import com.akto.dao.DibsDao;
import com.akto.dto.Dibs;
import com.mongodb.MongoCommandException;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;

import org.bson.conversions.Bson;

public class Cluster {
    
    public static final String RUNTIME_MERGER = "runtime-merger";

    public static final String winnerId = UUID.randomUUID().toString();

    public static boolean callDibs(String prize, int expiryPeriod, int freqInSeconds) {
        int now = Context.now();
        int expiryTs = expiryPeriod + now;
        Bson setOnInsert = Updates.setOnInsert("_id", prize);

        Bson updates = Updates.combine(
            Updates.set("winner", winnerId),
            Updates.set("expiryTs", expiryTs),
            Updates.set("startTs", now),
            Updates.set("freqInSeconds", freqInSeconds),
            Updates.set("lastPing", now),
            setOnInsert
        );

        FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER);

        Dibs dibs;
        Bson findKeyQ = Filters.and(
            Filters.eq("_id", prize),
            Filters.exists("expiryTs", false)
        );

        try {    
            dibs = DibsDao.instance.getMCollection().findOneAndUpdate(findKeyQ, updates, options);
            System.out.println("try" + dibs);
        } catch (MongoCommandException e) {
            // already present
            Bson findExpiredKeyQ = Filters.and(
                Filters.eq("_id", prize),
                Filters.lte("expiryTs", now - freqInSeconds)
            );
    
            try {
                dibs = DibsDao.instance.getMCollection().findOneAndUpdate(findExpiredKeyQ, updates, options);
                System.out.println("catch1" + dibs);
            } catch (MongoCommandException eInside) {
                dibs = DibsDao.instance.findOne(Filters.eq("_id", prize));
                System.out.println("catch2" + dibs);
            }
        }

        System.out.println("final: " + dibs);

        return (dibs == null || (dibs.getWinner().equals(winnerId) && dibs.getExpiryTs() == expiryTs));
    }

}
