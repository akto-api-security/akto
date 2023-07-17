package com.akto.dto.testing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public enum TestSuite {

    BUSINESS_LOGIC(
            "Business logic",
            "Automated tests that verify if an API's functionality aligns with business requirements and rules",
            generateBusinessLogicTests()
    ),
    MISCONFIGURATIONS(
            "Misconfigurations",
            "Automated tests that scan software systems for misconfigured settings and access controls that could lead to security vulnerabilities.",
            generateMisconfigurationTests()
    ),
    DEEP_SCAN(
            "Deep scan",
            "A comprehensive and thorough examination of APIs for security vulnerabilities and weaknesses.",
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
        return Arrays.asList("REPLACE_AUTH_TOKEN", "ADD_USER_ID", "CHANGE_METHOD", "REMOVE_TOKENS", "PARAMETER_POLLUTION", "REPLACE_AUTH_TOKEN_OLD_VERSION", "JWT_NONE_ALGO", "JWT_INVALID_SIGNATURE", "ADD_JKU_TO_JWT", "OPEN_REDIRECT", "PAGINATION_MISCONFIGURATION");
    }

    public static List<String> generateMisconfigurationTests() {
        return Arrays.asList(
            "AIRFLOW_CONFIGURATION_EXPOSURE", "AMAZON_DOCKER_CONFIG", "APACHE_CONFIG", "APPSPEC_YML_DISCLOSURE", "CGI_PRINTENV", "CIRCLECI_CONFIG", "CONFIG_JSON", "CONFIG_RUBY", "CONFIGURATION_LISTING", "COOKIE_MISCONFIGURATION", "DEBUG_VARS", "DEFAULT_LOGIN_CREDENTIALS", "DJANGO_DEFAULT_HOMEPAGE_ENABLED", "DOCKER_COMPOSE_CONFIG", "DOCKERFILE_HIDDEN_DISCLOSURE", "ESMTPRC_CONFIG", "EXPRESS_DEFAULT_HOMEPAGE_ENABLED", "EXPRESS_STACK_TRACE_ENABLED", "FIREBASE_CONFIG_EXPOSURE", "FLASK_DEBUG_MODE_ENABLED", "FTP_CREDENTIALS_EXPOSURE", "GIT_CONFIG", "GIT_CONFIG_NGINXOFFBYSLASH", "GIT_CREDENTIALS_DISCLOSURE", "GITHUB_WORKFLOW_DISCLOSURE", "GRAPHQL_DEBUG_MODE_ENABLED", "GRAPHQL_DEVELOPMENT_CONSOLE_EXPOSED", "GRAPHQL_FIELD_SUGGESTIONS_ENABLED", "GRAPHQL_INTROSPECTION_MODE_ENABLED", "GRAPHQL_TYPE_INTROSPECTION_ALLOWED", "JWT_SIGNING_IN_CLIENT_SIDE", "KUBERNETES_KUSTOMIZATION_DISCLOSURE", "LARAVEL_DEBUG_MODE_ENABLED", "LARAVEL_DEFAULT_HOMEPAGE_ENABLED", "LARAVEL_ENV", "LARAVEL_TELESCOPE_ENABLED", "MISCONFIGURED_DOCKER", "MSMTP_CONFIG", "NGINX_CONFIG", "NGINX_DEFAULT_PAGE_ENABLED", "NGINX_SERVER_VERSION_DISCLOSED", "OPEN_REDIRECT_HOST_HEADER_INJECTION", "OPEN_REDIRECT_IN_PATH", "OPEN_REDIRECT_SUBDOMAIN_WHITELIST", "ORACLE_EBS_CREDENTIALS", "PARAMETERS_CONFIG", "PROMETHEUS_METRICS", "RAILS_DEBUG_MODE_ENABLED", "RAILS_DEFAULT_HOMEPAGE_ENABLED", "REDIS_CONFIG", "ROBOMONGO_CREDENTIAL", "SERVER_PRIVATE_KEYS", "SESSION_FIXATION", "SFTP_CONFIG_EXPOSURE", "SONARQUBE_PUBLIC_PROJECTS", "SPRING_BOOT_BEANS_ACTUATOR_EXPOSED", "SPRING_BOOT_CONFIG_PROPS_ACTUATOR_EXPOSED", "SPRING_BOOT_ENV_ACTUATOR_EXPOSED", "SPRING_BOOT_HTTP_TRACE_ACTUATOR_EXPOSED", "SPRING_BOOT_THREAD_DUMP_ACTUATOR_EXPOSED", "SSH_AUTHORIZED_KEYS", "SSH_KNOWN_HOSTS", "STRUTS_DEBUG_MODE_ENABLED", "STRUTS_OGNL_CONSOLE_ENABLED", "TEXT_INJECTION_VIA_INVALID_URLS", "UNAUTHENTICATED_MONGO_EXPRESS", "WGETRC_CONFIG", "WPCONFIG_AWS_KEY"
        );
    }

    public static List<String> generateDeepScanTests() {
        List<String> result = new ArrayList<>(generateBusinessLogicTests().size() + generateMisconfigurationTests().size());
        result.addAll(generateBusinessLogicTests());
        result.addAll(generateMisconfigurationTests());

        return result;
    }


}
