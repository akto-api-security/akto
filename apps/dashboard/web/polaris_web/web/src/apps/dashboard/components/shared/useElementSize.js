import { useLayoutEffect, useState } from "react";

const UNMEASURED = { width: 0, height: 0 };

// Callback ref plus the element's current { width, height } in px (0 until measured).
export default function useElementSize() {
    const [node, setNode] = useState(null);
    const [size, setSize] = useState(UNMEASURED);
    useLayoutEffect(() => {
        if (!node) return undefined;
        const observer = new ResizeObserver(() => {
            const width = node.clientWidth;
            const height = node.offsetHeight;
            setSize((current) => (current.width === width && current.height === height ? current : { width, height }));
        });
        observer.observe(node);
        return () => observer.disconnect();
    }, [node]);
    return [setNode, size];
}
