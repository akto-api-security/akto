package com.akto.action;

import com.akto.dao.CustomRoleDao;
import com.akto.dao.PendingInviteCodesDao;
import com.akto.dao.RBACDao;
import com.akto.dao.UsersDao;
import com.akto.dao.context.Context;
import com.akto.dto.CustomRole;
import com.akto.dto.PendingInviteCode;
import com.akto.dto.RBAC;
import com.akto.dto.RBAC.Role;
import com.akto.dto.rbac.UsersCollectionsList;
import com.akto.dto.User;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.password_reset.PasswordResetUtils;
import com.akto.util.Pair;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.DeleteResult;
import com.opensymphony.xwork2.Action;

import org.apache.struts2.interceptor.ServletRequestAware;
import org.apache.struts2.interceptor.ServletResponseAware;
import org.bson.conversions.Bson;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.akto.util.Constants.TWO_HOURS_TIMESTAMP;

public class TeamAction extends UserAction implements ServletResponseAware, ServletRequestAware {

    int id;
    BasicDBList users;

    private static final LoggerMaker loggerMaker = new LoggerMaker(TeamAction.class, LogDb.DASHBOARD);

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
        Set<String> userSet = new HashSet<>();
        for(Object obj: users) {
            BasicDBObject userObj = (BasicDBObject) obj;
            RBAC rbac = userToRBAC.get(userObj.getInt("id"));
            String status = (rbac == null || rbac.getRole() == null) ? Role.MEMBER.getName() : rbac.getRole();
            userObj.append("role", status);
            try {
                String login = userObj.getString(User.LOGIN);
                if (login != null) {
                    userSet.add(login);
                }
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "Error in fetchTeamData " + e.getMessage());
            }
        }

        List<PendingInviteCode> pendingInviteCodes = PendingInviteCodesDao.instance.findAll(Filters.or(
                Filters.eq(RBAC.ACCOUNT_ID, Context.accountId.get()),
                Filters.exists(RBAC.ACCOUNT_ID, false)
        ));

        for(PendingInviteCode pendingInviteCode: pendingInviteCodes) {
            if (pendingInviteCode.getAccountId() == 0) {//case where account id doesn't exists belonged to older 1_000_000 account
                pendingInviteCode.setAccountId(1_000_000);
            }
            String inviteeRole = pendingInviteCode.getInviteeRole();
            String roleText = "Invitation sent ";
            if (inviteeRole == null) {
                roleText += "for Security Engineer";
            } else {
                roleText += "for " + inviteeRole;
            }
            /*
             * Do not send invitation code, if already a member.
             */
            if (pendingInviteCode.getAccountId() == accountId &&
                    !userSet.contains(pendingInviteCode.getInviteeEmailId())) {
                users.add(
                        new BasicDBObject("id", pendingInviteCode.getIssuer())
                                .append("login", pendingInviteCode.getInviteeEmailId())
                                .append("name", "-")
                                .append("role", roleText)
                                .append("isInvitation", true)
                );
            }
        }
        return SUCCESS.toUpperCase();
    }
    private enum ActionType {
        REMOVE_USER,
        UPDATE_USER_ROLE
    }
    
    String email;
    public String performAction(ActionType action, String reqUserRole) {
        int currUserId = getSUser().getId();
        int accId = Context.accountId.get();

        Bson findQ = Filters.eq(User.LOGIN, email);
        User userDetails = UsersDao.instance.findOne(findQ);
        boolean userExists =  userDetails != null;

        Bson filterRbac = Filters.and(
            Filters.eq(RBAC.USER_ID, userDetails.getId()),
            Filters.eq(RBAC.ACCOUNT_ID, accId));

        if (userExists && userDetails.getId() == currUserId) {
            addActionError("You cannot perform this action on yourself");
            return Action.ERROR.toUpperCase();
        }

        Role currentUserRole = RBACDao.getCurrentRoleForUser(currUserId, accId);
        Role userRole = RBACDao.getCurrentRoleForUser(userDetails.getId(), accId); // current role of the user whose role is changing

        CustomRole customRole = CustomRoleDao.instance.findRoleByName(reqUserRole);

        Role requestedRole = null;
        try {
            if (customRole != null) {
                requestedRole = Role.valueOf(customRole.getBaseRole());
            } else {
                if(reqUserRole != null && !action.equals(ActionType.REMOVE_USER)){
                    requestedRole = Role.valueOf(reqUserRole);
                }
            }
        } catch (Exception e) {
            addActionError("Invalid user role");
            return Action.ERROR.toUpperCase();
        }

        switch (action) {
            case REMOVE_USER:
                if (userExists) {
                    UsersDao.instance.updateOne(findQ, Updates.unset("accounts." + accId));
                    RBACDao.instance.deleteAll(filterRbac);
                    return Action.SUCCESS.toUpperCase();
                } else {
                    DeleteResult delResult = PendingInviteCodesDao.instance.getMCollection().deleteMany(Filters.eq("inviteeEmailId", email));
                    if (delResult.getDeletedCount() > 0) {
                        return Action.SUCCESS.toUpperCase();
                    } else {
                        return Action.ERROR.toUpperCase();
                    }
                }

            case UPDATE_USER_ROLE:
                if (userExists) {
                    try {
                        Role[] rolesHierarchy = currentUserRole.getRoleHierarchy();
                        boolean isValidUpdateRole = false; // cannot change for a user role higher than yourself
                        boolean shouldChangeRole = false; // cannot change to a role higher than yourself

                        for(Role role: rolesHierarchy){
                            if(role.equals(userRole)){
                                isValidUpdateRole = true;
                            }
                            if(role.equals(requestedRole)){
                                shouldChangeRole = true;
                            }
                        }
                        if(isValidUpdateRole && shouldChangeRole){
                            /*
                             * We do only want to update the role, if it exists.
                             */
                            RBACDao.instance.updateOneNoUpsert(
                                filterRbac,
                                // Saving the custom role here.
                                Updates.set(RBAC.ROLE, reqUserRole));

                                RBACDao.instance.deleteUserEntryFromCache(new Pair<>(userDetails.getId(), accId));
                                UsersCollectionsList.deleteCollectionIdsFromCache(userDetails.getId(), accId);
                            return Action.SUCCESS.toUpperCase();
                        }else{
                            addActionError("User doesn't have access to modify this role.");
                            return Action.ERROR.toUpperCase();
                        }
                    } catch (Exception e) {
                        addActionError("User role doesn't exist.");
                        return Action.ERROR.toUpperCase();
                    }
                    
                } else {
                    addActionError("User doesn't exist");
                    return Action.ERROR.toUpperCase();
                }

            default:
                break;
        }
        RBACDao.instance.deleteUserEntryFromCache(new Pair<>(userDetails.getId(), accId));
        UsersCollectionsList.deleteCollectionIdsFromCache(userDetails.getId(), accId);
        return Action.SUCCESS.toUpperCase();
    }

    public String removeUser() {
        return performAction(ActionType.REMOVE_USER, null);
    }

    private String userRole;

    public String makeAdmin(){
        return performAction(ActionType.UPDATE_USER_ROLE, this.userRole.toUpperCase());
    }

    private Role[] userRoleHierarchy;

    public String getRoleHierarchy(){
        try {
            Role currentRole = RBACDao.getCurrentRoleForUser(getSUser().getId(), Context.accountId.get());
            this.userRoleHierarchy = currentRole.getRoleHierarchy();
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            addActionError("User role doesn't exist.");
            return Action.ERROR.toUpperCase();
        }
    }

    String userEmail;
    String passwordResetToken;
    public String resetUserPassword() {
        if(userEmail == null || userEmail.isEmpty()) {
            addActionError("Email cannot be null or empty");
            return Action.ERROR.toUpperCase();
        }

        User user = getSUser();
        if(user == null) {
            addActionError("User cannot be null or empty");
            return Action.ERROR.toUpperCase();
        }

        User forgotPasswordUser = UsersDao.instance.findOne(Filters.eq(User.LOGIN, userEmail));
        if(forgotPasswordUser == null) {
            addActionError("User not found.");
            return Action.ERROR.toUpperCase();
        }

        int lastPasswordResetToken = forgotPasswordUser.getLastPasswordResetToken();
        int timeElapsed = Context.now() - lastPasswordResetToken;
        if(timeElapsed < TWO_HOURS_TIMESTAMP) {
            int remainingTime = (TWO_HOURS_TIMESTAMP - timeElapsed) / 60;
            addActionError("Please wait " + remainingTime + " minute" + (remainingTime > 1 ? "s" : "") + " for another password reset.");
            return Action.ERROR.toUpperCase();
        }

        String scheme = servletRequest.getScheme();
        String serverName = servletRequest.getServerName();
        int serverPort = servletRequest.getServerPort();
        String websiteHostName;
        if (serverPort == 80 || serverPort == 443) {
            websiteHostName = scheme + "://" + serverName;
        } else {
            websiteHostName = scheme + "://" + serverName + ":" + serverPort;
        }

        passwordResetToken = PasswordResetUtils.insertPasswordResetToken(userEmail, websiteHostName);

        if(passwordResetToken == null || passwordResetToken.isEmpty()) {
            return Action.ERROR.toUpperCase();
        }

        return Action.SUCCESS.toUpperCase();
    }



    public String removeInvitation() {
        User sUser = getSUser();

        PendingInviteCode pendingInviteCode = PendingInviteCodesDao.instance.findOne(Filters.eq(PendingInviteCode.INVITEE_EMAIL_ID, email));
        Role currentRole = RBACDao.getCurrentRoleForUser(getSUser().getId(), Context.accountId.get());

        boolean isIssuer = pendingInviteCode.getIssuer() == sUser.getId();
        boolean isAdmin = Role.ADMIN.name().equals(currentRole.name());

        if(isIssuer || isAdmin) {
            Bson filters = Filters.and(
                    Filters.eq(PendingInviteCode.ACCOUNT_ID, Context.accountId.get()),
                    Filters.eq(PendingInviteCode.INVITEE_EMAIL_ID, email)
            );
            PendingInviteCodesDao.instance.getMCollection().deleteOne(filters);
            return SUCCESS.toUpperCase();
        } else {
            addActionError("User is not allowed to remove this invitation.");
            return Action.ERROR.toUpperCase();
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

    public void setUserRole(String userRole) {
        this.userRole = userRole;
    }

    public Role[] getUserRoleHierarchy() {
        return userRoleHierarchy;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public String getPasswordResetToken() {
        return passwordResetToken;
    }

    protected HttpServletResponse servletResponse;
    @Override
    public void setServletResponse(HttpServletResponse httpServletResponse) {
        this.servletResponse= httpServletResponse;
    }

    protected HttpServletRequest servletRequest;
    @Override
    public void setServletRequest(HttpServletRequest httpServletRequest) {
        this.servletRequest = httpServletRequest;
    }
}
