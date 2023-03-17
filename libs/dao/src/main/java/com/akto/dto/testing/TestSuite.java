package com.akto.dto.testing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public enum TestSuite {

    BUSINESS_LOGIC(
            "Business logic",
            "Business logic tests",
            generateBusinessLogicTests()
    ),
    PASSIVE_SCAN(
            "Passive scan",
            "Passive scan your APIs",
            generatePassiveScanTests()
    ),
    DEEP_SCAN(
            "Deep scan",
            "Deep scan your APIs",
            generateDeepScanTests()
    );

    public final String name;
    public final String description;
    public final List<String> tests;

    TestSuite(String name, String description, List<String> tests) {
        this.name = name;
        this.description = description;
        this.tests = tests;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getTests() {
        return tests;
    }

    
    public static List<String> generateBusinessLogicTests() {
        return Arrays.asList("REPLACE_AUTH_TOKEN", "ADD_USER_ID", "CHANGE_METHOD", "REMOVE_TOKENS", "PARAMETER_POLLUTION", "REPLACE_AUTH_TOKEN_OLD_VERSION", "JWT_NONE_ALGO", "JWT_INVALID_SIGNATURE", "JWT_INVALID_SIGNATURE", "ADD_JKU_TO_JWT", "OPEN_REDIRECT", "PAGINATION_MISCONFIGURATION");
    }

    public static List<String> generatePassiveScanTests() {
        return Arrays.asList(
                "https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/configs/prometheus-metrics.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/swagger-detection/swagger_file_detection_SecLists.yaml","https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/swagger-detection/swagger_file_basic.yml","https://github.com/akto-api-security/tests-library/blob/master/Injection/host-injection/host-header-injection.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Injection/lfi/generic-j2ee-lfi.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Injection/lfi/generic-linux-lfi.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Injection/lfi/generic-windows-lfi.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Injection/lfi/linux-lfi-fuzzing.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Injection/method-injection/put-method-enabled.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Injection/sqli/error-based-sql-injection.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Injection/xss/basic-xss-prober.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Injection/xss/crlf-injection.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Injection/xss/top-xss-params.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/backups/php-backup-files.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/backups/settings-php-files.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/backups/sql-dump.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/backups/zip-backup-files.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/cache/cache-poisoning.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/configs/airflow-configuration-exposure.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/configs/amazon-docker-config.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/configs/apache-config.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/configs/appspec-yml-disclosure.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/configs/cgi-printenv.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/configs/circleci-config.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/configs/config-json.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/configs/config-rb.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/configs/configuration-listing.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/configs/debug-vars.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/configs/docker-compose-config.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/configs/dockerfile-hidden-disclosure.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/configs/esmtprc-config.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/configs/firebase-config-exposure.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/configs/ftp-credentials-exposure.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/configs/git-config-nginxoffbyslash.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/configs/git-config.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/configs/git-credentials-disclosure.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/configs/github-workflows-disclosure.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/configs/kubernetes-kustomization-disclosure.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/configs/laravel-env.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/configs/misconfigured-docker.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/configs/msmtp-config.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/configs/nginx-config.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/configs/oracle-ebs-credentials.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/configs/parameters-config.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/configs/redis-config.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/configs/robomongo-credential.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/configs/server-private-keys.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/configs/sftp-config-exposure.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/configs/sonarqube-public-projects.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/configs/ssh-authorized-keys.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/configs/ssh-known-hosts.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/configs/unauthenticated-mongo-express.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/configs/wgetrc-config.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/configs/wpconfig-aws-keys.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/smuggling/cl-te-http-request-smuggling.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/smuggling/te-cl-http-smuggling.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/unprotected-files/apache-licenserc.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/unprotected-files/appsettings-file-disclosure.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/unprotected-files/cloud-config.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/unprotected-files/composer-auth-json.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/unprotected-files/credentials-json.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/unprotected-files/database-credentials.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/unprotected-files/db-xml-file.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/unprotected-files/django-secret-key.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/unprotected-files/docker-cloud.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/unprotected-files/environment-rb.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/unprotected-files/exposed-alps-spring.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/unprotected-files/gcloud-access-token.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/unprotected-files/gcloud-credentials.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/unprotected-files/git-mailmap.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/unprotected-files/github-gemfile-files.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/unprotected-files/go-mod-disclosure.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/unprotected-files/gogs-install-exposure.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/unprotected-files/google-api-private-key.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/unprotected-files/google-services-json.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/unprotected-files/kubernetes-etcd-keys.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/unprotected-files/oauth-credentials-json.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/unprotected-files/openstack-user-secrets.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/unprotected-files/php-user-ini-disclosure.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/unprotected-files/phpunit-result-cache-exposure.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/unprotected-files/putty-private-key-disclosure.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/unprotected-files/rails-secret-token-disclosure.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/unprotected-files/ruby-rail-storage.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/unprotected-files/secret-token-rb.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/unprotected-files/secrets-file.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/unprotected-files/sendgrid-env.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/unprotected-files/sensitive-storage-exposure.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/unprotected-files/service-account-credentials.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/unprotected-files/service-pwd.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/unprotected-files/token-json.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/unprotected-files/travis-ci-disclosure.yaml", "https://github.com/akto-api-security/tests-library/blob/master/Misconfiguration/unprotected-files/vscode-sftp.yaml"
        );
    }

    public static List<String> generateDeepScanTests() {
        List<String> result = new ArrayList<>(generateBusinessLogicTests().size() + generatePassiveScanTests().size());
        result.addAll(generateBusinessLogicTests());
        result.addAll(generatePassiveScanTests());

        return result;
    }


}
