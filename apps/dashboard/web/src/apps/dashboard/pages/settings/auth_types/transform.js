import func from "@/util/func";

const transform = {
    convertArrayToPredicates: (arr, option) => {
        return arr.map((val) => {
            return { value: val, type: option.value }
        })
    },
    convertPredicateToArray: (conditions) => {
        return conditions.map((condition) => {
            return condition.value;
        })
    },
    fillInitialState: (initialItems, option) => {
        return {
            name: initialItems.name ? initialItems.name : "",
            active: initialItems.active!=undefined ? func.toSentenceCase(initialItems.active.toString()) : undefined,
            headerConditions: transform.convertArrayToPredicates(
                initialItems.headerConditions ? initialItems.headerConditions : [], option),
            payloadConditions: transform.convertArrayToPredicates(
                initialItems.payloadConditions ? initialItems.payloadConditions : [], option)
        }
    }
}

export default transform;