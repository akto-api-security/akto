import func from "@/util/func";

const transform = {
    
    fillInitialState: (initialItems) => {
        return {
            name: initialItems?.name!=undefined ? initialItems.name : "",
            active: initialItems.active!=undefined ? func.toSentenceCase(initialItems.active.toString()) : undefined,
            keyConditions: initialItems?.keyConditions?.predicates!=undefined ? initialItems.keyConditions.predicates : [],
            operator: initialItems?.keyConditions?.operator!=undefined ? initialItems.keyConditions.operator : "OR"
        }
    },
    preparePredicatesForApi(predicates) {
        let result = []
        if (!predicates) return result
        predicates.forEach((predicate) => {
            let type = predicate["type"]
            let obj = {"type": type}

            let valueMap = {}
            switch (type) {
                case "STARTS_WITH":
                case "ENDS_WITH":
                case "REGEX":
                case "EQUALS_TO":
                    valueMap["value"] = predicate["value"]
                    break;

                case "IS_NUMBER":
                    break;

            }

            obj["valueMap"] = valueMap

            result.push(obj)
        })
        return result;
    },

}

export default transform;