import { Button, HorizontalStack } from "@shopify/polaris";
import { ExportMinor } from "@shopify/polaris-icons";
import OperatorDropdown from "@/apps/dashboard/components/layouts/OperatorDropdown";
import { DEVICE_SCOPES } from "../constants";

// Device scope and Export: the controls in the header of the discovery page.
export default function DiscoveryHeaderActions({ filters, onExport, exporting }) {
    const { deviceScope, setDeviceScope } = filters;
    return (
        <HorizontalStack gap="2" blockAlign="center">
            <OperatorDropdown
                label={(DEVICE_SCOPES.find((scope) => scope.value === deviceScope) || DEVICE_SCOPES[0]).label}
                items={DEVICE_SCOPES}
                selected={setDeviceScope}
                designer
            />
            <Button icon={ExportMinor} onClick={onExport} loading={exporting}>Export</Button>
        </HorizontalStack>
    );
}
