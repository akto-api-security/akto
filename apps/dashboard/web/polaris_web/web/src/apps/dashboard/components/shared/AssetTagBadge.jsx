import Pill from "./Pill";

const TAG_COLORS = {
    Approved: ["#AEE9D1", "#0C5132"],
    Unapproved: ["#FED3D1", "#8E1F0B"],
    Personal: ["#FFEA8A", "#5C4600"],
};

export default function AssetTagBadge({ tag }) {
    const colors = TAG_COLORS[tag];
    if (!colors) return null;
    return <Pill background={colors[0]} color={colors[1]} style={{padding: "5px 15px"}}>{tag}</Pill>;
}
