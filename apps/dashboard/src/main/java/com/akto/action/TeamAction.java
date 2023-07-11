package com.akto.action;

import com.akto.dao.PendingInviteCodesDao;
import com.akto.dao.RBACDao;
import com.akto.dao.UsersDao;
import com.akto.dao.context.Context;
import com.akto.dto.PendingInviteCode;
import com.akto.dto.RBAC;
import com.akto.dto.RBAC.Role;
import com.akto.dto.User;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.DeleteResult;
import com.opensymphony.xwork2.Action;

import org.bson.conversions.Bson;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TeamAction extends UserAction {

    int id;
    BasicDBList users;

    public String fetchTeamData() {
        List<RBAC> allRoles = RBACDao.instance.findAll(RBAC.ACCOUNT_ID, Context.accountId.get());

        Map<Integer, RBAC> userToRBAC = new HashMap<>();
        for(RBAC rbac: allRoles) {
            userToRBAC.put(rbac.getUserId(), rbac);
        }   

        users = UsersDao.instance.getAllUsersInfoForTheAccount(Context.accountId.get());
        for(Object obj: users) {
            BasicDBObject userObj = (BasicDBObject) obj;
            RBAC rbac = userToRBAC.get(userObj.getInt("id"));
            String status = rbac == null ? "Member" : rbac.getRole().name();
            userObj.append("role", status);
        }

        List<PendingInviteCode> pendingInviteCodes = PendingInviteCodesDao.instance.findAll(RBAC.ACCOUNT_ID, Context.accountId.get());

        for(PendingInviteCode pendingInviteCode: pendingInviteCodes) {
            users.add(
                new BasicDBObject("id", pendingInviteCode.getIssuer())
                .append("login", pendingInviteCode.getInviteeEmailId())
                .append("name", "-")
                .append("role", "Invitation sent")
            );
        }

        return SUCCESS.toUpperCase();
    }

    String email;
    public String removeUser() {
        int currUserId = getSUser().getId();
        RBAC record = RBACDao.instance.findOne("userId", currUserId, "role", Role.ADMIN);
        boolean isAdmin = RBACDao.instance.isAdmin(currUserId, Context.accountId.get());
        if (!isAdmin) {
            return Action.ERROR.toUpperCase();
        } else {
            int accId = Context.accountId.get();

            Bson findQ = Filters.eq(User.LOGIN, email);
            User userDetails = UsersDao.instance.findOne(findQ);
            boolean userExists =  userDetails != null;
            if (userExists && userDetails.getId() == currUserId) {
                return Action.ERROR.toUpperCase();
            }

            if (userExists) {
                UsersDao.instance.updateOne(findQ, Updates.unset("accounts."+accId));
                return Action.SUCCESS.toUpperCase();
            } else {
                DeleteResult delResult = PendingInviteCodesDao.instance.getMCollection().deleteMany(Filters.eq("inviteeEmailId", email));
                if (delResult.getDeletedCount() > 0) {
                    return Action.SUCCESS.toUpperCase();
                } else {
                    return Action.ERROR.toUpperCase();
                }
            }
        }
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public BasicDBList getUsers() {
        return users;
    }

    public void setUsers(BasicDBList users) {
        this.users = users;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getEmail() {
        return this.email;
    }

}
