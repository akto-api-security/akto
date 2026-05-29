import { useEffect } from 'react'

// Module-level reference counts so that two components requesting the same body
// class don't fight each other's cleanup. The class stays on <body> until the
// last consumer unmounts.
const refCounts = new Map()

export function useBodyClass(className) {
    useEffect(() => {
        if (!className) return undefined

        const next = (refCounts.get(className) || 0) + 1
        refCounts.set(className, next)
        if (next === 1) {
            document.body.classList.add(className)
        }

        return () => {
            const current = refCounts.get(className) || 0
            const updated = current - 1
            if (updated <= 0) {
                refCounts.delete(className)
                document.body.classList.remove(className)
            } else {
                refCounts.set(className, updated)
            }
        }
    }, [className])
}
