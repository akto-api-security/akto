import "./CompactButton.css";

export default function CompactButton({ children, onClick }) {
    return (
        <button type="button" className="compact-button" onClick={onClick}>
            {children}
        </button>
    );
}
