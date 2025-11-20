const opTypeEnums = {
    CREATE_SINGLE: "CREATE_SINGLE",
    CREATE_MULTIPLE: "CREATE_MULTIPLE"
}

const resourceEnums = {
    API_COLLECTION: "API collection"
}

const constructAuditLog = (opType, resource, description) => {
    let auditLog;

    switch (opType) {
        case opTypeEnums.CREATE_SINGLE:
            auditLog = `created ${resource} with name: ${description}`;
            break;
        default:
            auditLog = null;
    }

    // Add user details
    auditLog = auditLog !== null ? `User ${auditLog}` : null;

    return auditLog;
}


export { constructAuditLog, opTypeEnums, resourceEnums };


