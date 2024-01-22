import { Box, Button, Text, VerticalStack } from "@shopify/polaris"


function convertToText(ele) {
    if (ele["value"]) {
        return ele["childParam"] + " = " + ele["value"]
    } else if (ele["parentParam"]) {
        return ele["childParam"] + " = " + ele["parentMethod"] + " " + ele["parentUrl"] + " " + ele["parentParam"]
    } else {
        return ele["childParam"] + " = " + "?"
    }
}


function TableExpand(data, childApiCollectionId, childUrl, childMethod, showEditModal) {
    return (
        <tr style={{ background: "#EDEEEF" }}>
            <td></td>
            <td colSpan={4}>
                <Box paddingInlineStart={4} paddingBlockEnd={2} paddingBlockStart={2}>
                    <VerticalStack gap={2}>
                        {data.map((ele, index) => {
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
}

export default TableExpand