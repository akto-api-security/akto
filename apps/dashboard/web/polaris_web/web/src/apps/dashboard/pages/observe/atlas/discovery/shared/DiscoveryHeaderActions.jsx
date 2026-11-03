import { Button, HorizontalStack } from "@shopify/polaris";
import { ExportMinor } from "@shopify/polaris-icons";
import DateRangeFilter from "@/apps/dashboard/components/layouts/DateRangeFilter";
import OperatorDropdown from "@/apps/dashboard/components/layouts/OperatorDropdown";
import { DEVICE_SCOPES } from "../constants";

// Device scope, date range and Export: the controls in the header of the discovery page.
export default function DiscoveryHeaderActions({ filters, onExport, exporting }) {
    const { dateRange, dispatchDateRange, deviceScope, setDeviceScope } = filters;
    return (
        <HorizontalStack gap="2" blockAlign="center">
            <OperatorDropdown
                label={(DEVICE_SCOPES.find((scope) => scope.value === deviceScope) || DEVICE_SCOPES[0]).label}
                items={DEVICE_SCOPES}
                selected={setDeviceScope}
                designer
            />
            <DateRangeFilter
                initialDispatch={dateRange}
                dispatch={(dateObj) => dispatchDateRange({ type: "update", period: dateObj.period, title: dateObj.title, alias: dateObj.alias })}
            />
            <Button icon={ExportMinor} onClick={onExport} loading={exporting}>Export</Button>
        </HorizontalStack>
    );
}
