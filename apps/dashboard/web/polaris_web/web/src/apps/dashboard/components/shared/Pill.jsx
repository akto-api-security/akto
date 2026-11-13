export default function Pill({ background, color, children, style }) {
    return (
        <span
            style={{
                display: "inline-flex",
                justifyContent: "center",
                alignItems: "center",
                padding: "2px 8px",
                borderRadius: "999px",
                fontSize: "12px",
                lineHeight: "16px",
                whiteSpace: "nowrap",
                background,
                color,
                ...style,
            }}
        >
            {children}
        </span>
    );
}
