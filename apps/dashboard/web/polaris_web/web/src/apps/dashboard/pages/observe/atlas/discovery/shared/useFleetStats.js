import { useEffect, useState } from "react";
import observeApi from "@/apps/dashboard/pages/observe/api";
import settingRequests from "@/apps/dashboard/pages/settings/api";

// The Devices & employees page's own sources: its fleet-wide AI device count (ignores the device scope) and managed devices in the scope.
export default function useFleetStats(deviceScope) {
    const [stats, setStats] = useState({ devicesWithAi: null, managedDevices: null });
    useEffect(() => {
        let ignore = false;
        Promise.all([observeApi.fetchUsersAndDevicesStats(), settingRequests.fetchEndpointShieldStats({ deviceScope })])
            .then(([discovery, shield]) => { if (!ignore) setStats({ devicesWithAi: discovery.devicesCount, managedDevices: shield?.deviceStats?.managedDevices ?? null }); })
            .catch(() => {});
        return () => { ignore = true; };
    }, [deviceScope]);
    return stats;
}
