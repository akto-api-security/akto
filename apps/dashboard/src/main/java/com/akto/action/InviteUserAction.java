package com.akto.action;

import com.akto.dao.CustomRoleDao;
import com.akto.dao.PendingInviteCodesDao;
import com.akto.dao.RBACDao;
import com.akto.dao.UsersDao;
import com.akto.dao.context.Context;
import com.akto.dto.*;
import com.akto.dto.RBAC.Role;
import com.akto.log.LoggerMaker;
import com.akto.notifications.email.SendgridEmail;
import com.akto.usage.UsageMetricCalculator;
import com.akto.util.DashboardMode;
import com.akto.utils.JWT;
import com.akto.utils.Utils;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.opensymphony.xwork2.Action;
import com.sendgrid.helpers.mail.Mail;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

public class InviteUserAction extends UserAction{

    private String inviteeName;
    private String inviteeEmail;
    private String websiteHostName;

    public static final String INVALID_EMAIL_ERROR = "Invalid email";
    public static final String DIFFERENT_ORG_EMAIL_ERROR = "Email must belong to same organisation";
    public static final String NOT_ALLOWED_TO_INVITE = "you're not authorised to invite for this role";
    public static final String AKTO_DOMAIN = "akto.io";
    public static final String INVALID_PRODUCT_SCOPE = "Invalid product scope: user account does not have access to this scope";

    public static Map<String, String> commonOrganisationsMap = new HashMap<>();
    private static final ExecutorService executor = Executors.newFixedThreadPool(1);

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    /**
     * Validates that a product scope is accessible for the current account.
     * Based on STIGG feature grants for the account.
     *
     * @param scope the scope to validate
     * @return true if scope is accessible, false otherwise
     */
    private boolean isValidProductScope(String scope) {
        if (scope == null || scope.isEmpty()) {
            return false;
        }
        Set<String> accessibleScopes = UsageMetricCalculator.getAccessibleProductScopes(Context.accountId.get());
        return accessibleScopes.contains(scope);
    }

    /**
     * Validates a role string and returns its base role.
     * Handles both standard roles (ADMIN, MEMBER) and custom roles.
     *
     * @param roleStr the role string to validate
     * @return the base Role if valid, null if invalid
     */
    private Role validateAndGetBaseRole(String roleStr) {
        Role baseRole = null;
        CustomRole customRole = CustomRoleDao.instance.findRoleByName(roleStr);

        try {
            if (customRole != null) {
                baseRole = Role.valueOf(customRole.getBaseRole());
            } else {
                baseRole = Role.valueOf(roleStr);
            }
        } catch (Exception e) {
            addActionError("Invalid role: " + roleStr);
            return null;
        }

        // Get current user's role for hierarchy validation
        Role currentUserRole = RBACDao.getCurrentRoleForUser(getSUser().getId(), Context.accountId.get());

        if (!Arrays.asList(currentUserRole.getRoleHierarchy()).contains(baseRole)) {
            addActionError("User not allowed to invite for role: " + roleStr);
            return null;
        }

        return baseRole;
    }

    static {
        commonOrganisationsMap.put("blinkhealth.com", "blinkhealth.com");
        commonOrganisationsMap.put("blinkrx.com", "blinkhealth.com");
        commonOrganisationsMap.put("hollywoodbets.net ", "betsoftware.com ");
        commonOrganisationsMap.put("betsoftware.com ", "hollywoodbets.net ");
        commonOrganisationsMap.put("lambdatest.com", "testmuai.com");
        commonOrganisationsMap.put("testmuai.com", "lambdatest.com");
    }

    public static String validateEmail(String email, String adminLogin) {
        if (email == null) return INVALID_EMAIL_ERROR;

        String[] inviteeEmailArr = email.split("@");
        if (inviteeEmailArr.length != 2) {
            return INVALID_EMAIL_ERROR;
        }

        if (!EMAIL_PATTERN.matcher(email).matches()) {
            return INVALID_EMAIL_ERROR;
        }

        // validating if same organisation or not
        String[] loginArr = adminLogin.split("@");
        if (loginArr.length != 2) return "Invalid admin login";
        String domain = loginArr[1];
        String inviteeEmailDomain = inviteeEmailArr[1];

        loggerMaker.debugAndAddToDb("inviteeEmailDomain: " + inviteeEmailDomain);
        loggerMaker.debugAndAddToDb("admin domain: " + domain);

        boolean isSameDomainVal = isSameDomain(inviteeEmailDomain, domain);
        loggerMaker.debugAndAddToDb("is same domain: " + isSameDomainVal);

        if (!isSameDomainVal && !inviteeEmailDomain.equals(AKTO_DOMAIN))  {
            return DIFFERENT_ORG_EMAIL_ERROR;
        }

        return null;
    }

    private static boolean isSameDomain(String inviteeDomain, String adminDomain) {
        if (inviteeDomain == null || adminDomain == null) return false;
        if (inviteeDomain.equalsIgnoreCase(adminDomain)) return true;

        if (("consulting-for."+adminDomain).equals(inviteeDomain)) return true;

        String inviteeOrg = commonOrganisationsMap.get(inviteeDomain);
        String adminOrg = commonOrganisationsMap.get(adminDomain);

        loggerMaker.debugAndAddToDb("inviteeOrg: " + inviteeOrg);
        loggerMaker.debugAndAddToDb("adminOrg: " + adminOrg);

        // Check if inviteeDomain maps directly to adminDomain or vice versa
        if (inviteeOrg != null && adminDomain.equalsIgnoreCase(inviteeOrg)) return true;
        if (adminOrg != null && inviteeDomain.equalsIgnoreCase(adminOrg)) return true;

        // Check if both map to the same canonical org
        if (inviteeOrg != null && adminOrg != null && inviteeOrg.equalsIgnoreCase(adminOrg)) return true;

        loggerMaker.debugAndAddToDb("inviteeOrg and adminOrg different");
        return false;
    }

    private String finalInviteCode;
    private String inviteeRole;
    private Map<String, String> scopeRoleMapping;

    private static final LoggerMaker loggerMaker = new LoggerMaker(InviteUserAction.class, LoggerMaker.LogDb.DASHBOARD);

    @Override
    public String execute() {
        inviteeEmail = inviteeEmail != null ? inviteeEmail.toLowerCase() : null;

        if(inviteeEmail == null) {
            addActionError("Invalid email");
            return ERROR.toUpperCase();
        }

        Integer accountId = Context.accountId.get();
        if (Utils.allowNewUserInviteViaDashboard(accountId, getSUser())) {
            addActionError("Inviting new users is not allowed for this account.");
            return ERROR.toUpperCase();
        }

        int user_id = getSUser().getId();
        loggerMaker.debugAndAddToDb(user_id + " inviting " + inviteeEmail);

        User user = UsersDao.instance.findOne(Filters.and(
                Filters.eq(User.LOGIN, inviteeEmail),
                Filters.eq(User.ACCOUNTS+"."+accountId+".accountId", accountId)
        ));
        if(user != null) {
            addActionError("User already exists");
            return ERROR.toUpperCase();
        }


        User admin = UsersDao.instance.getFirstUser(Context.accountId.get());
        if (admin == null) {
            loggerMaker.debugAndAddToDb("admin not found for organization");
            return ERROR.toUpperCase();
        }

        loggerMaker.debugAndAddToDb("admin user: " + admin.getLogin());
        String code = validateEmail(this.inviteeEmail, admin.getLogin());

        if (code != null) {
            addActionError(code);
            return ERROR.toUpperCase();
        }

        // Validate scopeRoleMapping if provided, otherwise fall back to old fields for backward compatibility
        Map<String, String> scopeRoleToSave = new HashMap<>();

        if (this.scopeRoleMapping != null && !this.scopeRoleMapping.isEmpty()) {
            // New n:n mapping approach
            for (Map.Entry<String, String> entry : this.scopeRoleMapping.entrySet()) {
                String scope = entry.getKey();
                String roleStr = entry.getValue();

                Role baseRole = validateAndGetBaseRole(roleStr);

                if (baseRole == null) {
                    return ERROR.toUpperCase();
                }

                // Allow NO_ACCESS assignments for any scope (NO_ACCESS is explicit deny, not a privilege)
                // Only validate scope accessibility for actual role assignments (non-NO_ACCESS)
                if (!baseRole.equals(Role.NO_ACCESS) && !isValidProductScope(scope)) {
                    addActionError(INVALID_PRODUCT_SCOPE + scope);
                    loggerMaker.errorAndAddToDb("Invalid product scope attempted: " + scope + " for user invitation");
                    return ERROR.toUpperCase();
                }

                // Save the role name (custom or standard) to scopeRoleMapping.
                // Custom roles will be resolved to their baseRole at access time via RBAC.getRoleForScope()
                // This preserves the custom role assignment for auditing and future enhancements.
                scopeRoleToSave.put(scope, roleStr);
            }

            if (scopeRoleToSave.isEmpty()) {
                // Default to API + NO_ACCESS if no scopes selected
                loggerMaker.debugAndAddToDb("scopeRoleMapping is empty, defaulting to API + NO_ACCESS");
                scopeRoleToSave.put("API", RBAC.Role.NO_ACCESS.name());
            }
            loggerMaker.debugAndAddToDb("After ensuring complete mapping: " + scopeRoleToSave);
        } else if (this.inviteeRole != null && !this.inviteeRole.isEmpty()) {
            // Backward compatibility: old single role
            Role baseRole = validateAndGetBaseRole(this.inviteeRole);
            if (baseRole == null) {
                return ERROR.toUpperCase();
            }

            // If any case only invitee role is present and no scope then map it to "API"
            scopeRoleToSave.put("API", this.inviteeRole);

        } else {
            addActionError("Either scopeRoleMapping or inviteeRole must be provided");
            return ERROR.toUpperCase();
        }

        Map<String,Object> claims = new HashMap<>();
        claims.put("email", inviteeEmail);

        String inviteCode;

        try {
            inviteCode = JWT.createJWT(
                    "/home/avneesh/Desktop/akto/dashboard/private.pem",
                    claims,
                    "Akto",
                    "invite_user",
                    Calendar.WEEK_OF_MONTH,
                    1
            );
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | IOException e) {
            // TODO: find better error
            return Action.ERROR.toUpperCase();
        }

        // to get expiry date

        try {
            Jws<Claims> jws = JWT.parseJwt(inviteCode,"");
            /*
             * There should only be one invite code per user per account.
             * So if we update with upsert:true per account-inviteeEmail
             */
            PendingInviteCodesDao.instance.updateOne(
                    Filters.and(
                        Filters.eq(PendingInviteCode.ACCOUNT_ID, Context.accountId.get()),
                        Filters.eq(PendingInviteCode.INVITEE_EMAIL_ID, inviteeEmail)
                    ),
                    Updates.combine(
                        Updates.set(PendingInviteCode.SCOPE_ROLE_MAPPING, scopeRoleToSave),
                        Updates.set(PendingInviteCode.INVITE_CODE, inviteCode),
                        Updates.set(PendingInviteCode._EXPIRY, jws.getBody().getExpiration().getTime()),
                        Updates.set(PendingInviteCode._ISSUER, user_id)
                    )
            );

            loggerMaker.infoAndAddToDb("Invitation sent with scopeRoleMapping: " + scopeRoleToSave);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | IOException e) {
            e.printStackTrace();
            return ERROR.toUpperCase();
        }

        // check if user who is being invited has sso-signup 
        boolean hasSSOSignup = false;
        if (StringUtils.isNotBlank(inviteeEmail) && !inviteeEmail.isEmpty()) {
            User invitedUser = UsersDao.instance.findOne(Filters.and(
                Filters.eq(User.LOGIN, inviteeEmail)
            ));

            if(invitedUser != null && invitedUser.getSignupInfoMap() != null && invitedUser.getSignupInfoMap().size() > 0) {
                for (SignupInfo signupInfo : invitedUser.getSignupInfoMap().values()) {
                    hasSSOSignup = Config.isConfigSSOType(signupInfo.getConfigType());
                    if(hasSSOSignup) {
                        break;
                    }
                }
            }
        }

        String endpoint = DashboardMode.isSaasDeployment() ? "/addUserToAccount" : "/signup";
        if(hasSSOSignup && DashboardMode.isSaasDeployment()) {
            endpoint = "/sso-login";
        }
        finalInviteCode = websiteHostName + endpoint + "?signupInvitationCode=" + inviteCode + "&signupEmailId=" + inviteeEmail;

        String inviteFrom = getSUser().getName();
        Mail email = SendgridEmail.getInstance().buildInvitationEmail(inviteeName, inviteeEmail, inviteFrom, finalInviteCode);
        if (!DashboardMode.isOnPremDeployment()) {
            sendInviteEmail(email);
        } else {
            executor.submit(() -> {
                sendInviteEmail(email);
            });
        }

        return Action.SUCCESS.toUpperCase();
    }

    private void sendInviteEmail(Mail email) {
        try {
            SendgridEmail.getInstance().send(email);
        } catch (IOException e) {
            loggerMaker.errorAndAddToDb("invite email sending failed" + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
            e.printStackTrace();
        }
    }

    private String invitationCodeToDelete;
    public String deleteInvitationCode() {
        PendingInviteCodesDao.instance.getMCollection().deleteOne(Filters.eq(PendingInviteCode.INVITE_CODE, invitationCodeToDelete));
        return Action.SUCCESS.toUpperCase();
    }

    public void setInvitationCodeToDelete(String invitationCodeToDelete) {
        this.invitationCodeToDelete = invitationCodeToDelete;
    }

    public void setInviteeName(String inviteeName) {
        this.inviteeName = inviteeName;
    }

    public void setInviteeEmail(String inviteeEmail) {
        this.inviteeEmail = inviteeEmail;
    }

    public void setWebsiteHostName(String websiteHostName) {
        this.websiteHostName = websiteHostName;
    }

    public String getFinalInviteCode() {
        return finalInviteCode;
    }

    public String getInviteeRole() {
        return inviteeRole;
    }

    public void setInviteeRole(String inviteeRole) {
        this.inviteeRole = inviteeRole;
    }

    public Map<String, String> getScopeRoleMapping() {
        return scopeRoleMapping;
    }

    public void setScopeRoleMapping(Map<String, String> scopeRoleMapping) {
        this.scopeRoleMapping = scopeRoleMapping;
    }
}
