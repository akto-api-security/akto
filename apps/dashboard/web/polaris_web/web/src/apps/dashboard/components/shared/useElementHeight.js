import { useLayoutEffect, useState } from "react";

// Callback ref plus the element's current height in px (0 until measured).
export default function useElementHeight() {
    const [node, setNode] = useState(null);
    const [height, setHeight] = useState(0);
    useLayoutEffect(() => {
        if (!node) return undefined;
        const observer = new ResizeObserver(() => setHeight(node.offsetHeight));
        observer.observe(node);
        return () => observer.disconnect();
    }, [node]);
    return [setNode, height];
}
