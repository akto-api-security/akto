// A small colored dot or square. Polaris has no shape for it, so this is the one hand-drawn piece.
function Dot({ color, size = 8, square = false }) {
    return <span style={{ display: 'inline-block', flex: 'none', width: size, height: size, borderRadius: square ? 3 : '50%', background: color }} />
}

export default Dot
