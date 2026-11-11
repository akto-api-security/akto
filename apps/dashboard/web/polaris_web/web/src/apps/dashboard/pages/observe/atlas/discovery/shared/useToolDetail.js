import { useEffect, useRef, useState } from "react";
import discoveryApi from "../discoveryApi";

// One tool's detail for a device scope; `failed` is set while the open tool has nothing to show.
export default function useToolDetail(key, deviceScope, refreshKey) {
    const [fetched, setDetail] = useState(null);
    const [failed, setFailed] = useState(false);
    const loadedKey = useRef(null);

    useEffect(() => {
        if (!key) {
            loadedKey.current = null;
            setDetail(null);
            setFailed(false);
            return undefined;
        }
        let ignore = false;
        // Another tool starts from a spinner; a refresh after an action keeps the open flyout.
        if (loadedKey.current !== key) {
            loadedKey.current = key;
            setDetail(null);
        }
        setFailed(false);
        discoveryApi.fetchAtlasToolDetail(key, deviceScope)
            .then((response) => { if (!ignore) setDetail(response); })
            .catch(() => { if (!ignore) setFailed(true); });
        return () => { ignore = true; };
    }, [key, deviceScope, refreshKey]);

    // Never the previous tool's detail while the next one loads.
    return { detail: fetched?.key === key ? fetched : null, failed };
}
