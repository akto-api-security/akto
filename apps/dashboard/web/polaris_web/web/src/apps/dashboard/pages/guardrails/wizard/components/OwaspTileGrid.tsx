import { OwaspTile } from '../types';

interface OwaspTileGridProps {
  tiles: OwaspTile[];
}

export default function OwaspTileGrid({ tiles }: OwaspTileGridProps) {
  return (
    <div className="gw-owasp-grid">
      {tiles.map((tile) => (
        <div key={tile.code} title={tile.name} className={`gw-owasp-tile ${tile.status}`}>
          {tile.code}
        </div>
      ))}
    </div>
  );
}
