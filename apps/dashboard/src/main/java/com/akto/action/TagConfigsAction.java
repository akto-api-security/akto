package com.akto.action;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.akto.action.CustomDataTypeAction.ConditionFromUser;
import com.akto.dao.TagConfigsDao;
import com.akto.dao.UsersDao;
import com.akto.dao.context.Context;
import com.akto.dto.CustomDataType;
import com.akto.dto.TagConfig;
import com.akto.dto.User;
import com.akto.dto.data_types.Conditions;
import com.akto.dto.data_types.Predicate;
import com.akto.utils.AktoCustomException;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;
import com.opensymphony.xwork2.Action;

public class TagConfigsAction extends UserAction {

    private BasicDBObject tagConfigs;
    public String fetchTagConfigs() {
        List<TagConfig> tagConfigs = TagConfigsDao.instance.findAll(new BasicDBObject());
        Collections.reverse(tagConfigs);

        Set<Integer> userIds = new HashSet<>();
        for (TagConfig customDataType: tagConfigs) {
            userIds.add(customDataType.getCreatorId());
        }
        userIds.add(getSUser().getId());

        Map<Integer,String> usersMap = UsersDao.instance.getUsernames(userIds);

        this.tagConfigs = new BasicDBObject();
        this.tagConfigs.put("tagConfigs", tagConfigs);
        this.tagConfigs.put("usersMap", usersMap);

        return Action.SUCCESS.toUpperCase();
    }

    private TagConfig tagConfig = null;
    private boolean createNew = false;
    private String name = null;
    private boolean active = false;
    private String keyOperator;
    private List<ConditionFromUser> keyConditionFromUsers;


    public TagConfig generateTagConfig(int userId) throws AktoCustomException {
        // TODO: handle errors
        if (name == null || name.length() == 0) throw new AktoCustomException("Name cannot be empty");
        if (name.split(" ").length > 1) throw new AktoCustomException("Name has to be single word");
        name = name.trim();
        name = name.toUpperCase();
        if (!(name.matches("[A-Z_0-9]+"))) throw new AktoCustomException("Name can only contain alphabets, numbers and underscores");

        if (keyOperator == null) {
            keyOperator = "AND";
        }

        Conditions keyConditions = null;
        Conditions.Operator kOperator = Conditions.Operator.valueOf(keyOperator);
        if (keyConditionFromUsers != null) {
            List<Predicate> predicates = new ArrayList<>();
            for (ConditionFromUser conditionFromUser: keyConditionFromUsers) {
                Predicate predicate = Predicate.generatePredicate(conditionFromUser.type, conditionFromUser.valueMap);
                if (predicate == null) {
                    throw new AktoCustomException("Invalid key conditions");
                } else {
                    predicates.add(predicate);
                }
            }

            if (predicates.size() > 0) {
                keyConditions = new Conditions(predicates, kOperator);
            }
        }

        if (keyConditions == null || keyConditions.getPredicates() == null || keyConditions.getPredicates().size() == 0)  {

            throw new AktoCustomException("Both key and value conditions can't be empty");
        }

        return new TagConfig(name, userId, true, keyConditions);
    }


    public String saveTagConfig() {
        User user = getSUser();
        tagConfig = null;
        try {
            tagConfig = generateTagConfig(user.getId());
        } catch (AktoCustomException e) {
            addActionError(e.getMessage());
            return ERROR.toUpperCase();
        }

        if (this.createNew) {
            TagConfig tagConfigFromDb = TagConfigsDao.instance.findOne(Filters.eq(TagConfig.NAME, name));
            if (tagConfigFromDb != null) {
                addActionError("Tag with same name exists");
                return ERROR.toUpperCase();
            }
            // id is automatically set when inserting in pojo
            TagConfigsDao.instance.insertOne(tagConfig);
        } else {
            FindOneAndUpdateOptions options = new FindOneAndUpdateOptions();
            options.returnDocument(ReturnDocument.AFTER);
            options.upsert(false);
            tagConfig = TagConfigsDao.instance.getMCollection().findOneAndUpdate(
                Filters.eq("name", tagConfig.getName()),
                Updates.combine(
                    Updates.set(CustomDataType.KEY_CONDITIONS,tagConfig.getKeyConditions()),
                    Updates.set(CustomDataType.TIMESTAMP,Context.now()),
                    Updates.set(CustomDataType.ACTIVE,active)
                ),
                options
            );

            if (tagConfig == null) {
                addActionError("Failed to update tag");
                return ERROR.toUpperCase();
            }
        }

        return Action.SUCCESS.toUpperCase();        
    }
    
    public String toggleActiveTagConfig() {
        FindOneAndUpdateOptions options = new FindOneAndUpdateOptions();
        options.returnDocument(ReturnDocument.AFTER);
        options.upsert(false);
        tagConfig = TagConfigsDao.instance.getMCollection().findOneAndUpdate(
                Filters.eq(CustomDataType.NAME, this.name),
                Updates.set(CustomDataType.ACTIVE, active),
                options
        );

        if (tagConfig == null) {
            String v = active ? "activate" : "deactivate";
            addActionError("Failed to "+ v +" data type");
            return ERROR.toUpperCase();
        }

        return Action.SUCCESS.toUpperCase();
    }

    public BasicDBObject getTagConfigs() {
        return this.tagConfigs;
    }

    public void setTagConfigs(BasicDBObject tagConfigs) {
        this.tagConfigs = tagConfigs;
    }

    public TagConfig getTagConfig() {
        return this.tagConfig;
    }

    public void setTagConfig(TagConfig tagConfig) {
        this.tagConfig = tagConfig;
    }

    public boolean getCreateNew() {
        return this.createNew;
    }

    public void setCreateNew(boolean createNew) {
        this.createNew = createNew;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isActive() {
        return this.active;
    }

    public boolean getActive() {
        return this.active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getKeyOperator() {
        return this.keyOperator;
    }

    public void setKeyOperator(String keyOperator) {
        this.keyOperator = keyOperator;
    }

    public List<ConditionFromUser> getKeyConditionFromUsers() {
        return this.keyConditionFromUsers;
    }

    public void setKeyConditionFromUsers(List<ConditionFromUser> keyConditionFromUsers) {
        this.keyConditionFromUsers = keyConditionFromUsers;
    }
}
