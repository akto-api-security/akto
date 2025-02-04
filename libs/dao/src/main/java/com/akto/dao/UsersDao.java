package com.akto.dao;

import com.akto.dao.context.Context;
import com.akto.dto.*;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;

import java.util.*;
import java.util.regex.Pattern;

import org.bson.conversions.Bson;

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Projections.include;
import static com.mongodb.client.model.Updates.combine;
import static com.mongodb.client.model.Updates.set;

public class UsersDao extends CommonContextDao<User> {

    public void createIndicesIfAbsent() {

        String[] fieldNames = { User.LOGIN };
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, true);

    }

    public static User addUser(String login, String name, String password, boolean emailValidated) {
        // Checking if the user with same login exists or not
        if (UsersDao.instance.getMCollection().find(eq(User.LOGIN,login)).first() != null) {
            return null;
        }
        String salt = "39yu";
        String passHash = Integer.toString((salt + password).hashCode());
        return null;
    }

    public static User addAccount(String login, int accountId, String name) {
        BasicDBObject setQ = new BasicDBObject(User.ACCOUNTS + "." + accountId,new UserAccountEntry(accountId, name));

        User tempUser = UsersDao.instance.getMCollection().findOneAndUpdate(
            eq(User.LOGIN, login), new BasicDBObject(SET, setQ),
            new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
        );
        return tempUser;
    }

    public static void addNewAccount(String login, Account account) {
        BasicDBObject setQ = new BasicDBObject(User.ACCOUNTS + "." + account.getId(),
                new UserAccountEntry(account.getId(), account.getName()));
        UsersDao.instance.getMCollection().updateOne(eq(User.LOGIN, login), new BasicDBObject(SET, setQ),
                new UpdateOptions().upsert(false));
    }

    public User insertSignUp(String email, String name, SignupInfo info, int accountId) {
        User user = findOne(User.LOGIN, email);
        User ret;
        UserAccountEntry userAccountEntry = new UserAccountEntry();
        userAccountEntry.setAccountId(accountId);
        userAccountEntry.setDefault(true);
        Map<String, UserAccountEntry> accountAccessMap = new HashMap<>();
        accountAccessMap.put(accountId+"", userAccountEntry);

        if (user == null) {
            ret = User.create(name, email, info, accountAccessMap);
            insertOne(ret);
        } else {
            Map<String, SignupInfo> infoMap = user.getSignupInfoMap();
            if (infoMap == null) {
                infoMap = new HashMap<>();
            }
            infoMap.put(info.getKey(), info);

            Map<String, UserAccountEntry> userAccountEntryMap = user.getAccounts();
            if (userAccountEntryMap == null) {
                userAccountEntryMap = new HashMap<>();
            }

            userAccountEntryMap.putAll(accountAccessMap);

            this.getMCollection().updateOne(eq("login", email), combine(set("signupInfoMap", infoMap), set("accounts", accountAccessMap)));
        }

        return findOne("login", email);
    }

    public void insertPushSubscription(String login, BasicDBObject subscription) {
        updateOne(eq("login", login), set("signupInfoMap.WEBPUSH-ankush", new SignupInfo.WebpushSubscriptionInfo(subscription)));
    }


    public static User validateEmail(String email) {
        return UsersDao.instance.getMCollection().findOneAndUpdate(
                Filters.eq("login",email),
                set("emailValidated",true)
        );
    }

    final public static UsersDao instance = new UsersDao();

    // this returns the first non akto user
    // if non akto user not found then we will return the first akto user
    public User getFirstUser(int accountId) {
        Bson findQ = Filters.exists("accounts."+accountId);
        MongoCursor<User> cursor = instance.getMCollection().find(findQ).sort(Sorts.ascending("_id")).limit(10).cursor();
        User aktoUser = null;
        while (cursor.hasNext()) {
            User user = cursor.next();
            if (!user.getLogin().contains("@akto.io")) return user;
            if (aktoUser == null) aktoUser = user;
        }

        return aktoUser;
    }

    public Map<Integer, String> getUsernames(Collection<Integer> userIds) {
        MongoCursor<User> cursor = instance.getMCollection().find(in("_id", userIds)).projection(new BasicDBObject("name", 1)).cursor();

        Map<Integer, String> ret = new HashMap<Integer, String>();

        while (cursor.hasNext()) {
            User user = cursor.next();
            ret.put(user.getId(), user.getName());
        }
        return ret;
    }


    // TODO: account id
    public BasicDBObject getUserInfo(int user_id) {
        User user = instance.getMCollection().find(eq("_id",user_id)).projection(
                new BasicDBObject("_id",1)
                        .append("login",1)
                        .append("name",1)
        ).first();

        if (user == null) {
            return null;
        }
        return User.convertUserToUserDetails(user);
    }

    public Map<Integer, User> getUsersInfo(List<Integer> userIds) {

        MongoCursor<User> cursor = UsersDao.instance.getMCollection().find(in("_id", userIds)).projection(include("login", "name", "_id")).cursor();

        Map<Integer, User> ret = new HashMap<>();

        while (cursor.hasNext()) {
            User user = cursor.next();
            ret.put(user.getId(), user);
        }

        return ret;
    }

    public BasicDBList getAllUsersInfoForTheAccount(int accountId) {
        List<User> users = instance.findAll(Filters.exists("accounts."+accountId));
        BasicDBList result = new BasicDBList();

        for (User user: users) {
            result.add(User.convertUserToUserDetails(user));
        }

        return result;
    }

    public List<Integer> getAllUsersIdsForTheAccount(int accountId) {
        List<User> users = instance.findAll(Filters.exists("accounts."+accountId));
        List<Integer> result = new ArrayList<>();

        for (User user: users) {
            result.add(user.getId());
        }

        return result;
    }

    public BasicDBList getUsersAutoComplete(int accountId, String expression) {
        List<User> users = instance.findAll(
                Filters.and(
                        Filters.exists("accounts."+accountId),
                        Filters.regex("login", Pattern.compile(expression, Pattern.CASE_INSENSITIVE))
                )
        );
        BasicDBList result = new BasicDBList();
        for (User user: users) {
            result.add(User.convertUserToUserDetails(user));
        }
        return result;
    }

    public Integer fetchUserLasLoginTs(int userId) {
        User user = instance.getMCollection().find(eq("_id", userId)).projection(new BasicDBObject(User.LAST_LOGIN_TS,1)).first();
        if (user == null) return null;

        return user.getLastLoginTs();
    }

    @Override
    public String getCollName() {
        return "users";
    }

    @Override
    public Class<User> getClassT() {
        return User.class;
    }
}
