/** Shared rules for MCP server row actions (drawer + audit bulk bar). */
export function getServerActionFlags(auditItem, { registryConfigured, addHandlerAvailable }) {
    const remarks = auditItem?.remarks
    const isPending =
        remarks == null ||
        remarks === "" ||
        (typeof remarks === "string" && remarks.trim() === "")
    const isRejected = remarks === "Rejected"
    const isApproved = remarks === "Approved"
    const isConditional = remarks === "Conditionally Approved"

    const nowEpochSec = Math.floor(Date.now() / 1000)
    const expiresAtRaw = auditItem?.approvalConditions?.expiresAt
    const expiresAtNum =
        typeof expiresAtRaw === "number"
            ? expiresAtRaw
            : (typeof expiresAtRaw === "string" && expiresAtRaw !== "" ? parseInt(expiresAtRaw, 10) : NaN)
    const conditionalExpired =
        isConditional &&
        Number.isFinite(expiresAtNum) &&
        expiresAtNum > 0 &&
        (expiresAtNum > 1e12 ? expiresAtNum < Date.now() : expiresAtNum < nowEpochSec)

    const remarkFlags = () => {
        let allow = false
        let block = false
        let conditional = false
        if (isPending) {
            block = true
        } else if (isRejected) {
            allow = true
            conditional = true
        } else if (isConditional) {
            if (conditionalExpired) {
                allow = true
                conditional = true
            } else {
                allow = true
                block = true
                conditional = true
            }
        } else if (isApproved) {
            block = true
        }
        return { allow, block, conditional }
    }

    let allow = false
    let block = false
    let conditional = false
    let add = false

    if (auditItem?.verified) {
        const f = remarkFlags()
        allow = f.allow
        block = f.block
        conditional = f.conditional
    } else {
        if (registryConfigured && addHandlerAvailable && auditItem?.isEndpointSource) {
            add = true
        }
        if (!add) {
            const f = remarkFlags()
            allow = f.allow
            block = f.block
            conditional = f.conditional
        }
    }

    return { allow, block, conditional, add }
}

export function intersectServerActionFlags(rows, ctx) {
    if (!rows?.length) return { allow: false, block: false, conditional: false, add: false }
    let allow = true
    let block = true
    let conditional = true
    let add = true
    for (const row of rows) {
        const f = getServerActionFlags(row, ctx)
        allow = allow && f.allow
        block = block && f.block
        conditional = conditional && f.conditional
        add = add && f.add
    }
    return { allow, block, conditional, add }
}
