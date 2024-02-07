import { Box, Button, Spinner, Text, VerticalStack } from "@shopify/polaris"
import { useState } from "react";
import api from "../api";


function TableExpand({ data, childApiCollectionId, childUrl, childMethod, showEditModal }) {
    const [paramToValuesMap, setParamToValuesMap] = useState(null)
    const [loading, setLoading] = useState(true)

    function convertToText(ele) {
        const childParam = ele["childParam"]
        if (ele["value"]) {
            return childParam + " = " + ele["value"] + " (User supplied Input)"
        } else if (ele["parentParam"]) {
            return childParam + " = " + ele["parentMethod"] + " " + ele["parentUrl"] + " " + ele["parentParam"]
        } else if (paramToValuesMap && paramToValuesMap[childParam]) {
            return childParam + " = " + paramToValuesMap[childParam] + " (Picked from traffic)"
        } else {
            return childParam + " = " + "?"
        }
    }

    let params = []
    if (data && !paramToValuesMap) {
        data.forEach((ele) => {
            if (ele["value"] || ele["parentParam"]) return
            const param = ele["childParam"]
            params.push(param)
        })

        api.fetchValuesForParameters(childApiCollectionId, childUrl, childMethod, params).then((resp) => {
            setParamToValuesMap(resp["paramToValuesMap"])
            setLoading(false)
        })
    }

    const component = (
        <tr style={{ background: "#EDEEEF" }}>
            <td></td>
            <td colSpan={4}>
                <Box paddingInlineStart={4} paddingBlockEnd={2} paddingBlockStart={2}>
                    <VerticalStack gap={2}>
                        {data && data.map((ele, index) => {
                            return (
                                <Text key={convertToText(ele)}>
                                    {convertToText(ele)}
                                </Text>
                            )
                        })}
                    </VerticalStack>
                </Box>
            </td>
            <td style={{ width: '20%', verticalAlign: 'top', textAlign: 'right', padding: '10px' }}>
                <div style={{ display: 'flex', justifyContent: 'flex-end', height: '100%' }}>
                    <Button key={childMethod + "-" + childUrl} onClick={() => { showEditModal(childApiCollectionId, childUrl, childMethod, data) }}>Edit</Button>
                </div>
            </td>
        </tr>
    )

    return loading ?  <Spinner /> : component 
}

export default TableExpand