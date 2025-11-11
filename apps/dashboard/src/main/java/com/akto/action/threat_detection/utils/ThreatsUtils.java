package com.akto.action.threat_detection.utils;

import com.akto.dao.context.Context;
import com.akto.dao.monitoring.FilterYamlTemplateDao;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.akto.util.enums.GlobalEnums.SUB_CATEGORY_SOURCE;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ThreatsUtils {

    public static List<String> getTemplates(List<String> latestAttack) {
        Set<String> contextTemplatesForAccount = FilterYamlTemplateDao.getContextTemplatesForAccount(Context.accountId.get(), Context.contextSource.get());
//        Set<String> contextTemplatesForAccount = getContextTemplatesWithSubCategoryFilter(
//            Context.accountId.get(),
//            Context.contextSource.get(),
//            Context.getSubCategory()
//        );
//
//        System.out.println("ThreatsUtils.getTemplates: Context=" + Context.contextSource.get() +
//                          ", SubCategory=" + Context.getSubCategory() +
//                          ", Templates count=" + contextTemplatesForAccount.size());

        if(latestAttack == null || latestAttack.isEmpty()) {
            return new ArrayList<>(contextTemplatesForAccount);
        }

        return latestAttack.stream().filter(contextTemplatesForAccount::contains).collect(Collectors.toList());
    }

    /**
     * Get templates filtered by context and subcategory.
     * Implements the same filtering logic as UsersCollectionsList.getContextCollections()
     */
//    private static Set<String> getContextTemplatesWithSubCategoryFilter(int accountId, CONTEXT_SOURCE contextSource, SUB_CATEGORY_SOURCE subCategory) {
//        // For non-AGENTIC contexts, use existing logic without subcategory filtering
//        if (contextSource != CONTEXT_SOURCE.AGENTIC) {
//            System.out.println("ThreatsUtils: Using standard context filtering for " + contextSource);
//            return FilterYamlTemplateDao.getContextTemplatesForAccount(accountId, contextSource,subCategory);
//        }
//
//        // Handle null subcategory
//        if (subCategory == null) {
//            subCategory = SUB_CATEGORY_SOURCE.DEFAULT;
//        }
//
//        System.out.println("ThreatsUtils: Processing AGENTIC context with SubCategory: " + subCategory);
//
//        // Apply subcategory-based filtering similar to UsersCollectionsList.getContextCollections()
//        if (subCategory == SUB_CATEGORY_SOURCE.ENDPOINT_SECURITY) {
//            // For endpoint security, use only MCP templates
//            System.out.println("ThreatsUtils: Using MCP templates for Endpoint Security");
//            return FilterYamlTemplateDao.getContextTemplatesForAccount(accountId, CONTEXT_SOURCE.MCP,subCategory);
//
//        } else if (subCategory == SUB_CATEGORY_SOURCE.CLOUD_SECURITY) {
//            // For cloud security, use only GenAI templates
//            System.out.println("ThreatsUtils: Using GenAI templates for Cloud Security");
//            return FilterYamlTemplateDao.getContextTemplatesForAccount(accountId, CONTEXT_SOURCE.GEN_AI,subCategory);
//
//        } else {
//            // For DEFAULT subcategory or other cases, use full AGENTIC templates (both MCP and GenAI)
//            System.out.println("ThreatsUtils: Using all AGENTIC templates (DEFAULT subcategory)");
//            return FilterYamlTemplateDao.getContextTemplatesForAccount(accountId, CONTEXT_SOURCE.AGENTIC,subCategory);
//        }
//    }
}
