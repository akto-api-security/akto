package com.akto.audit_logs_util;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.akto.dto.audit_logs.Operation;
import com.akto.dto.audit_logs.Resource;

/**
 * Annotation to specify audit logging details for action method.
 *
 * <p><b>Example Usage:</b></p>
 * <pre>
 * {@code
 *  &#64;Audit(description = "User created a collection", resource = Resource.API_COLLECTION, operation = Operation.CREATE)
 * public String createCollection() &#123; ... &#125;
 * }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Audit {

    /**
     * <b>Required.</b> A human-readable description of the action being performed.
     *
     * @return the audit description
     */
    String description();

    /**
     * The type of resource.
     * Defaults to {@link Resource#NOT_SPECIFIED} if not provided.
     *
     * @return the targeted resource
     */
    Resource resource() default Resource.NOT_SPECIFIED;

    /**
     * The type of operation being performed.
     * Defaults to {@link Operation#NOT_SPECIFIED} if not provided.
     *
     * @return the operation type
     */
    Operation operation() default Operation.NOT_SPECIFIED;

    /**
     * A list of method names that generate additional metadata for the audit log.
     *
     * <p><b>Example Usage:</b></p>
     * <pre>
     * {@code
     * public String getCollectionName() &#123; return this.collectionName; &#125;
     * 
     * &#64;Audit(description = "User created a collection", resource = Resource.API_COLLECTION, operation = Operation.CREATE)
     *     metadataGenerators = &#123;"getCollectionName"&#125;
     * )
     * public String createCollection() &#123; ... &#125;
     * }
     * </pre>
     * 
     * Generated audit metadata:
     * <pre>
     * {@code
     * {"collectionName": "MyCollectionName"}
     * </pre>
     * 
     * @return an array of metadata generator identifiers
     */
    String[] metadataGenerators() default {};
}