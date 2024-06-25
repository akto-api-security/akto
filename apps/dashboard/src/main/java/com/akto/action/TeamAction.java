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
        int accountId = Context.accountId.get();
        List<RBAC> allRoles = RBACDao.instance.findAll(Filters.or(
                Filters.eq(RBAC.ACCOUNT_ID, accountId),
                Filters.exists(RBAC.ACCOUNT_ID, false)
        ));

        Map<Integer, RBAC> userToRBAC = new HashMap<>();
        for(RBAC rbac: allRoles) {
            if (rbac.getAccountId() == 0) {//case where account id doesn't exists belonged to older 1_000_000 account
                rbac.setAccountId(1_000_000);
            }
            if (rbac.getAccountId() == accountId) {
                userToRBAC.put(rbac.getUserId(), rbac);
            }
        }

        users = UsersDao.instance.getAllUsersInfoForTheAccount(Context.accountId.get());
        for(Object obj: users) {
            BasicDBObject userObj = (BasicDBObject) obj;
            RBAC rbac = userToRBAC.get(userObj.getInt("id"));
            String status = rbac == null ? "Member" : rbac.getRole().name();
            userObj.append("role", status);
        }

        List<PendingInviteCode> pendingInviteCodes = PendingInviteCodesDao.instance.findAll(Filters.or(
                Filters.eq(RBAC.ACCOUNT_ID, Context.accountId.get()),
                Filters.exists(RBAC.ACCOUNT_ID, false)
        ));

        for(PendingInviteCode pendingInviteCode: pendingInviteCodes) {
            if (pendingInviteCode.getAccountId() == 0) {//case where account id doesn't exists belonged to older 1_000_000 account
                pendingInviteCode.setAccountId(1_000_000);
            }
            if (pendingInviteCode.getAccountId() == accountId) {
                users.add(
                        new BasicDBObject("id", pendingInviteCode.getIssuer())
                                .append("login", pendingInviteCode.getInviteeEmailId())
                                .append("name", "-")
                                .append("role", "Invitation sent")
                );
            }
        }

        return SUCCESS.toUpperCase();
    }

    private enum ActionType {
        REMOVE_USER,
        MAKE_ADMIN
    }
    
    String email;
    public String performAction(ActionType action) {
        int currUserId = getSUser().getId();
        boolean isAdmin = RBACDao.instance.isAdmin(currUserId, Context.accountId.get());
        if (!isAdmin) {
            addActionError("You are not authorized to perform this action");
            return Action.ERROR.toUpperCase();
        } else {
            int accId = Context.accountId.get();

            Bson findQ = Filters.eq(User.LOGIN, email);
            User userDetails = UsersDao.instance.findOne(findQ);
            boolean userExists =  userDetails != null;
            if (userExists && userDetails.getId() == currUserId) {
                addActionError("You cannot perform this action on yourself");
                return Action.ERROR.toUpperCase();
            }

            switch (action) {
                case REMOVE_USER:
                    if (userExists) {
                        UsersDao.instance.updateOne(findQ, Updates.unset("accounts." + accId));
                        RBACDao.instance.deleteAll(
                                Filters.and(
                                        Filters.eq(RBAC.USER_ID, userDetails.getId()),
                                        Filters.eq(RBAC.ACCOUNT_ID, accId)));
                        return Action.SUCCESS.toUpperCase();
                    } else {
                        DeleteResult delResult = PendingInviteCodesDao.instance.getMCollection().deleteMany(Filters.eq("inviteeEmailId", email));
                        if (delResult.getDeletedCount() > 0) {
                            return Action.SUCCESS.toUpperCase();
                        } else {
                            return Action.ERROR.toUpperCase();
                        }
                    }

                case MAKE_ADMIN:
                    if (userExists) {
                        RBACDao.instance.updateOne(
                                Filters.and(
                                        Filters.eq(RBAC.USER_ID, userDetails.getId()),
                                        Filters.eq(RBAC.ACCOUNT_ID, accId)),
                                Updates.set(RBAC.ROLE, Role.ADMIN));
                        return Action.SUCCESS.toUpperCase();
                    } else {
                        addActionError("User doesn't exist");
                        return Action.ERROR.toUpperCase();
                    }

                default:
                    break;
            }
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String removeUser() {
        return performAction(ActionType.REMOVE_USER);
    }

    public String makeAdmin(){
        return performAction(ActionType.MAKE_ADMIN);
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
