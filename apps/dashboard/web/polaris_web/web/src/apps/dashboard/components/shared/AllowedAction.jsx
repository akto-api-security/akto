import React from "react"
import { Tooltip } from "@shopify/polaris"
import { NO_PERMISSION_REASON } from "@/util/permissions"

/*
 * Wraps a button the user may see but not use: disables it and explains why on hover.
 * <AllowedAction allowed={canCall('api/addSplunkIntegration')}><Button onClick={save}>Save</Button></AllowedAction>
 */
function AllowedAction({ allowed, reason, children }) {
    if (allowed) {
        return children
    }
    return (
        <Tooltip content={reason || NO_PERMISSION_REASON} dismissOnMouseOut>
            {React.Children.map(children, child => React.isValidElement(child) ? React.cloneElement(child, { disabled: true }) : child)}
        </Tooltip>
    )
}

export default AllowedAction
