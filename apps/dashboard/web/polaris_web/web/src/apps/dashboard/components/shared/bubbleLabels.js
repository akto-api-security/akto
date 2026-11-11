export const LABEL_GAP_PX = 4;
const LABEL_HEIGHT_PX = 18;
const LABEL_CHAR_PX = 7;
const LABEL_PAD_PX = 6;
const FLIP_FROM_PCT = 80;

const rectOf = (left, bottom, width, height) => ({ left, right: left + width, bottom, top: bottom + height });
const overlaps = (a, b) => a.left < b.right && a.right > b.left && a.bottom < b.top && a.top > b.bottom;

function circleRect(bubble, width, height) {
    const centerX = Math.min(Math.max((bubble.x / 100) * width, bubble.radius), width - bubble.radius);
    const centerY = Math.min(Math.max((bubble.y / 100) * height, 0), height);
    return { centerX, centerY, rect: rectOf(centerX - bubble.radius, centerY - bubble.radius, bubble.radius * 2, bubble.radius * 2) };
}

// Picks which names are drawn and on which side so none covers another: { [key]: "right" | "left" }.
export function placeLabels(bubbles, width, height) {
    const circles = bubbles.map((bubble) => ({ bubble, ...circleRect(bubble, width, height) }));
    const placed = [];
    const sides = {};
    bubbles
        .filter((bubble) => bubble.label)
        .sort((a, b) => (b.priority || 0) - (a.priority || 0))
        .forEach((bubble) => {
            const { centerX, centerY } = circleRect(bubble, width, height);
            const labelWidth = bubble.label.length * LABEL_CHAR_PX + LABEL_PAD_PX;
            const bottom = centerY - LABEL_HEIGHT_PX / 2;
            const right = rectOf(centerX + bubble.radius + LABEL_GAP_PX, bottom, labelWidth, LABEL_HEIGHT_PX);
            const left = rectOf(centerX - bubble.radius - LABEL_GAP_PX - labelWidth, bottom, labelWidth, LABEL_HEIGHT_PX);
            const options = bubble.x > FLIP_FROM_PCT ? [["left", left], ["right", right]] : [["right", right], ["left", left]];
            const fits = ([, rect]) => rect.left >= 0 && rect.right <= width
                && !placed.some((other) => overlaps(rect, other))
                && !circles.some((circle) => circle.bubble.key !== bubble.key && overlaps(rect, circle.rect));
            const choice = options.find(fits);
            if (choice) {
                sides[bubble.key] = choice[0];
                placed.push(choice[1]);
            }
        });
    return sides;
}
