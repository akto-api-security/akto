import { useState, useEffect } from "react";
import { Button } from "@shopify/polaris";
import { ExportMinor } from "@shopify/polaris-icons";
import func from "@/util/func";
import settingRequests from "../../../settings/api";
import transform from "./transform";
import PageWithMultipleCards from "../../../../components/layouts/PageWithMultipleCards";
import AskBar from "../../../../components/shared/AskBar";
import StatsRow from "./StatsRow";
import DeviceScopeDropdown, { DEVICE_SCOPES } from "./DeviceScopeDropdown";
import { ASK_ATLAS_PLACEHOLDER, ASK_ATLAS_SUGGESTIONS, ATLAS_CHAT_SCOPE } from "./constants";

function DevicesEmployeesPage() {
    const [deviceScope, setDeviceScope] = useState(DEVICE_SCOPES[0].id);
    const [stats, setStats] = useState(null);

    useEffect(() => {
        let ignore = false;
        settingRequests.fetchEndpointShieldStats({ deviceScope })
            .then((res) => {
                if (!ignore) setStats(transform.buildStatCards(res?.deviceStats));
            })
            .catch(() => {
                if (!ignore) func.setToast(true, true, "Failed to load device stats");
            });
        return () => { ignore = true; };
    }, [deviceScope]);

    return (
        <PageWithMultipleCards
            title="Devices & employees"
            subtitle="Who uses which AI tools, on which devices, and whether Atlas is protecting them"
            isFirstPage
            secondaryActions={
                <>
                    <DeviceScopeDropdown value={deviceScope} onChange={setDeviceScope} />
                    <Button icon={ExportMinor} disabled>Export</Button>
                </>
            }
            components={[
                <AskBar
                    key="ask-atlas"
                    label="Ask Atlas"
                    placeholder={ASK_ATLAS_PLACEHOLDER}
                    suggestions={ASK_ATLAS_SUGGESTIONS}
                    chatScope={ATLAS_CHAT_SCOPE}
                />,
                <StatsRow key="stats-row" stats={stats} />,
            ]}
        />
    );
}

export default DevicesEmployeesPage;
