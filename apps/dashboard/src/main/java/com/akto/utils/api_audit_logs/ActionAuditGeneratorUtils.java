package com.akto.utils.api_audit_logs;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dto.ApiCollection;

public class ActionAuditGeneratorUtils {
    
    /**
     * Returns the count of items in the provided collection.
     * If the collection is null, it returns 0.
     * 
     * @param collection the collection whose items are to be counted
     * @return the count of items in the collection, or 0 if the collection is null
     */
    public static int getItemsCount(List<?> collection) {
        return collection != null ? collection.size() : 0;
    }

    /**
     * Generates a comma-separated string of property values extracted from the items in the provided list.
     * It filters out null list elements, null property values, and empty strings.
     * 
     * @param <T> the type of items in the list
     * @param list the list of items from which to extract property values
     * @param propertyMapper a function that extracts the desired property from each item      
     * @return a comma-separated string of the extracted property values
     */ 
    public static <T> String getItemsCS(
            List<T> list,
            Function<T, String> propertyMapper) {
        return Optional.ofNullable(list)
                .orElseGet(Collections::emptyList)
                .stream()
                .filter(Objects::nonNull) // 1. Filter out null list elements.
                .map(propertyMapper) // 2. Transform the stream by applying the property extraction function.
                .filter(Objects::nonNull) // 3. Filter out null strings.
                .filter(s -> !s.isEmpty()) // 4. Filter out empty strings ("").
                .collect(Collectors.joining(", ")); // 5. Collect the remaining valid strings.
    }

    /**
     * Generates a comma-separated string of enum names extracted from the items in the provided list.
     * It filters out null list elements and null enum values.
     * 
     * @param <T> the type of items in the list
     * @param <E> the enum type of the property being extracted
     * @param list the list of items from which to extract enum names
     * @param propertyMapper a function that extracts the desired enum property from each item      
     * @return a comma-separated string of the extracted enum names
     */
    public static <T, E extends Enum<E>> String getItemsCSFromEnum(
            List<T> list,
            Function<T, E> propertyMapper) {
        return getItemsCS(list, item -> {
            E enumValue = propertyMapper.apply(item);
            return enumValue != null ? enumValue.name() : null; // Convert enum to its name string
        });     
    }

    /**
     * Retrieves the display name of an API collection given its ID.
     * 
     * @param apiCollectionId the ID of the API collection
     * @return the display name of the API collection, or an empty string if not found
     */
    public static String getApiCollectionDisplayName(int apiCollectionId) {
        ApiCollection apiCollection = ApiCollectionsDao.instance.getMetaForId(apiCollectionId);
        return apiCollection != null ? apiCollection.getDisplayName() : "";
    }
}
