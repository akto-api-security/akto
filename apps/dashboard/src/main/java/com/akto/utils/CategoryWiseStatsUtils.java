package com.akto.utils;

import com.akto.dao.testing.TestingRunResultDao;
import com.akto.util.enums.GlobalEnums;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;

import java.util.*;

/**
 * Utility class for fetching category-wise statistics across different data sources:
 * - Testing/Red Teaming (TestingRunResult)
 * - Threat Detection (SuspectSampleData) 
 * - Guardrails/Policy (future implementation)
 */
public class CategoryWiseStatsUtils {

    public enum DataSource {
        REDTEAMING,
        THREAT_DETECTION, 
        GUARDRAILS
    }

    /**
     * Generic method to get category-wise scores for any data source
     * Categories are automatically filtered based on dashboard context
     */
    public static List<Map<String, Object>> getCategoryWiseScores(
            DataSource dataSource,
            int startTimestamp, 
            int endTimestamp, 
            String dashboardCategory
    ) {
        // Get relevant categories based on data source and dashboard
        List<String> relevantCategories = getRelevantCategories(dataSource, dashboardCategory);
        
        switch (dataSource) {
            case REDTEAMING:
                return getTestingCategoryWiseScores(startTimestamp, endTimestamp, relevantCategories);
            case THREAT_DETECTION:
                return getThreatDetectionCategoryWiseScores(startTimestamp, endTimestamp, relevantCategories);
            case GUARDRAILS:
                return getGuardrailsCategoryWiseScores(startTimestamp, endTimestamp, relevantCategories);
            default:
                return new ArrayList<>();
        }
    }

    /**
     * Get relevant categories for any data source and dashboard combination
     */
    private static List<String> getRelevantCategories(DataSource dataSource, String dashboardCategory) {
        if (dashboardCategory == null) {
            return null; // No filtering
        }
        
        switch (dataSource) {
            case REDTEAMING:
                return getTestingCategoriesForDashboard(dashboardCategory);
            case THREAT_DETECTION:
                return getThreatDetectionCategoriesForDashboard(dashboardCategory);
            case GUARDRAILS:
                return getGuardrailsCategoriesForDashboard(dashboardCategory);
            default:
                return null;
        }
    }

    /**
     * Get category-wise scores for testing/red teaming data
     */
    private static List<Map<String, Object>> getTestingCategoryWiseScores(
            int startTimestamp, 
            int endTimestamp, 
            List<String> relevantCategories
    ) {
        return TestingRunResultDao.instance.getCategoryWiseScores(startTimestamp, endTimestamp, relevantCategories);
    }

    /**
     * Get category-wise scores for threat detection data
     * TODO: Implement threat detection category aggregation using SuspectSampleData
     */
    private static List<Map<String, Object>> getThreatDetectionCategoryWiseScores(
            int startTimestamp, 
            int endTimestamp, 
            List<String> relevantCategories
    ) {
        // TODO: Implement threat detection aggregation
        return new ArrayList<>();
    }

    /**
     * Get category-wise scores for guardrails/policy data
     * TODO: Implement when guardrails data structure is available
     */
    private static List<Map<String, Object>> getGuardrailsCategoryWiseScores(
            int startTimestamp, 
            int endTimestamp, 
            List<String> relevantCategories
    ) {
        // TODO: Implement based on guardrails data
        
        return new ArrayList<>();
    }

    /**
     * Get relevant categories for testing data source using TestTemplateUtils
     */
    public static List<String> getTestingCategoriesForDashboard(String dashboardCategory) {
        List<String> categories = new ArrayList<>();
        
        // Map dashboard category to CONTEXT_SOURCE
        CONTEXT_SOURCE contextSource;
        switch (dashboardCategory) {
            case "MCP Security":
                contextSource = CONTEXT_SOURCE.MCP;
                break;
            case "Gen AI":
                contextSource = CONTEXT_SOURCE.GEN_AI;
                break;
            case "API Security":
            default:
                contextSource = CONTEXT_SOURCE.API;
                break;
        }
        
        // Use existing TestTemplateUtils to get categories for the context
        GlobalEnums.TestCategory[] testCategories = TestTemplateUtils.getAllTestCategoriesWithinContext(contextSource);
        
        // Convert enum to database format
        for (GlobalEnums.TestCategory category : testCategories) {
            categories.add(category.getName());
        }
        
        return categories;
    }

    /**
     * Get relevant categories for threat detection
     */
    public static List<String> getThreatDetectionCategoriesForDashboard(String dashboardCategory) {
        List<String> categories = new ArrayList<>();
        
        // TODO: Define threat detection categories based on filterId values
        switch (dashboardCategory) {
            case "MCP Security":
                // MCP-specific threat filters
                break;
            case "Gen AI":
                // Gen AI-specific threat filters  
                break;
            case "API Security":
            default:
                // All threat categories
                break;
        }
        
        return categories;
    }

    /**
     * Get relevant categories for guardrails
     */
    public static List<String> getGuardrailsCategoriesForDashboard(String dashboardCategory) {
        List<String> categories = new ArrayList<>();
        
        // TODO: Define guardrails categories based on policy types
        switch (dashboardCategory) {
            case "MCP Security":
                // MCP-specific policy categories
                break;
            case "Gen AI":
                // Gen AI-specific policy categories
                break;
            case "API Security":
            default:
                // All policy categories
                break;
        }
        
        return categories;
    }
}
