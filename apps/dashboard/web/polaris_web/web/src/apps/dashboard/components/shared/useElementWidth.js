import { useLayoutEffect, useState } from "react";

// Callback ref plus the element's current width in px (0 until measured).
export default function useElementWidth() {
    const [node, setNode] = useState(null);
    const [width, setWidth] = useState(0);
    useLayoutEffect(() => {
        if (!node) return undefined;
        const observer = new ResizeObserver(() => setWidth(node.clientWidth));
        observer.observe(node);
        return () => observer.disconnect();
    }, [node]);
    return [setNode, width];
}
