import { Box, TextField } from "@shopify/polaris"
import TooltipText from "../../../components/shared/TooltipText"

function EditGlobalDataFields(ele, handleTextFieldChange) {
    let currentHost = ele["currentHost"]
    let newHost = ele["newHost"]
    return (
        <TextField
            label=""
            key={currentHost}
            value={newHost}
            onChange={(newVal) => {handleTextFieldChange(newVal, currentHost)}}
            autoComplete="off"
            connectedLeft={
                <Box width='160px'>
                    <TooltipText tooltip={currentHost} text={currentHost} textProps={{ color: "subdued", variant: "bodyLg" }} />
                </Box>
            }
        />
    )
}


export default EditGlobalDataFields