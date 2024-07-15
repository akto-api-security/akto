package com.akto.dao;

import com.akto.dto.Groups;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.conversions.Bson;

import java.util.List;
import java.util.Set;

public class GroupsDao extends AccountsContextDao<Groups> {
    public static final GroupsDao instance = new GroupsDao();

    public void updateGroups(List<Integer> collectionIds, Set<Integer> userIds, String createdBy) {
        String uniqueName = createdBy;
        Bson filter = Filters.eq(Groups.NAME, uniqueName);
        Groups groups = instance.findOne(filter);

        if(groups != null) {
            userIds.addAll(groups.getUserIds());
            groups.setUserIds(userIds);
            instance.updateOne(filter, Updates.set("userIds", groups.getUserIds()));
        } else {
            groups = new Groups(collectionIds, userIds, createdBy);
            instance.insertOne(groups);
        }
    }

    @Override
    public String getCollName() {
        return "groups";
    }

    @Override
    public Class<Groups> getClassT() {
        return Groups.class;
    }

}
