import React from "react"
import { Text } from "@shopify/polaris"

function SelectedChoices(props) {

    const { userInput } = props

    if (userInput?.selectedOptions) {

        const options = userInput?.selectedOptions
        let data = "Selected option(s): "

        if (Array.isArray(options)) {
            data += options.filter((i, x) => i < 3).join(", ")
        } else if (typeof options === "object") {
            let j = 0;
            for (let i in options) {
                if (j > 3) {
                    break;
                }
                j++;
                data += i + "-> " + options[i] + ","
            }
        }

        console.log("userInput", userInput, options)

        return <Text as={"dd"}>{data}</Text>
    }
    return <></>
}

export default SelectedChoices;