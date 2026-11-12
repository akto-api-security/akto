import observeFunc from "../../observe/transform";

export const EMPTY_VALUE = "—";

export function formatCount(n) {
    return n === undefined || n === null ? EMPTY_VALUE : observeFunc.formatNumberWithCommas(n);
}

export function plural(count, one, many) {
    return `${formatCount(count)} ${count === 1 ? one : many}`;
}
