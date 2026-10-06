import { useState } from "react"
import { Box } from "@shopify/polaris"

const isActivationKey = (key) => key === "Enter" || key === " "

function isFocusVisible(el) {
    try {
        return el.matches(":focus-visible")
    } catch (e) {
        return true
    }
}

// A clickable surface built on Box: button semantics, Enter/Space activation, and a focus ring
// shown for keyboard focus only. Not a Polaris Link, which underlines its content on hover even
// with removeUnderline. Background and border swap on hover. Box has no cursor prop, so the
// pointer comes from the [data-ask-pressable] rule in askOverlay.css.
export default function Pressable({
    onClick,
    accessibilityLabel,
    background,
    hoverBackground,
    borderColor,
    hoverBorderColor,
    onHover,
    children,
    ...boxProps
}) {
    const [hovered, setHovered] = useState(false)
    const [focusVisible, setFocusVisible] = useState(false)

    const handleMouseEnter = () => {
        setHovered(true)
        if (onHover) onHover()
    }

    const handleKeyDown = (e) => {
        if (!isActivationKey(e.key)) return
        e.preventDefault()
        // Consumed here, so an ancestor's own Enter handling (HomeView runs the active result)
        // doesn't fire as well.
        e.stopPropagation()
        if (onClick) onClick()
    }

    return (
        <Box
            {...boxProps}
            role="button"
            tabIndex={0}
            data-ask-pressable=""
            aria-label={accessibilityLabel}
            background={hovered && hoverBackground ? hoverBackground : background}
            borderColor={hovered && hoverBorderColor ? hoverBorderColor : borderColor}
            outlineColor={focusVisible ? "border-interactive-focus" : undefined}
            outlineWidth={focusVisible ? "2" : undefined}
            onClick={onClick}
            onKeyDown={handleKeyDown}
            onMouseEnter={handleMouseEnter}
            onMouseLeave={() => setHovered(false)}
            onFocus={(e) => setFocusVisible(isFocusVisible(e.currentTarget))}
            onBlur={() => setFocusVisible(false)}
        >
            {children}
        </Box>
    )
}
