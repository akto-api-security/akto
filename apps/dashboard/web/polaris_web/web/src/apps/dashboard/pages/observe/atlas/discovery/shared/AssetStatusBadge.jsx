import { Badge } from "@shopify/polaris";
import { STATUS_META } from "../constants";

export default function AssetStatusBadge({ status }) {
    const meta = STATUS_META[status];
    return meta ? <Badge size="small" status={meta.badge}>{meta.label}</Badge> : null;
}
