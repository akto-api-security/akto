import { useEffect, useRef, useState } from "react";
import func from "@/util/func";
import discoveryApi from "../discoveryApi";

// The overview for a device scope. `dataScope` is the scope `data` was fetched for; `failed` is set while the current scope has nothing to show.
export default function useDiscoveryOverview(deviceScope, refreshKey) {
    const [data, setData] = useState(null);
    const [dataScope, setDataScope] = useState("");
    // The scope whose overview failed to load; a new scope starts clean.
    const [failedScope, setFailedScope] = useState("");
    const loadedFor = useRef("");
    const dataRef = useRef(null);
    dataRef.current = data;

    useEffect(() => {
        let ignore = false;
        // A new scope starts from spinners; a refresh after a decision keeps the cards on screen.
        if (loadedFor.current !== deviceScope) {
            loadedFor.current = deviceScope;
            setData(null);
        }
        discoveryApi.fetchAtlasDiscoveryOverview(deviceScope)
            .then((response) => {
                if (ignore) return;
                setData(response || {});
                setDataScope(deviceScope);
                setFailedScope("");
            })
            .catch(() => {
                if (ignore) return;
                // A failed refresh keeps the cards that are already on screen.
                if (!dataRef.current) setFailedScope(deviceScope);
                func.setToast(true, true, "Failed to load agentic discovery");
            });
        return () => { ignore = true; };
    }, [deviceScope, refreshKey]);

    return { data, dataScope, failed: failedScope === deviceScope };
}
