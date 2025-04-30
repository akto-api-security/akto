import { Box, TextField } from "@shopify/polaris"
import { useEffect, useState } from "react"
import DropdownSearch from "../../../components/shared/DropdownSearch"
import TooltipText from "../../../components/shared/TooltipText"

const editOptions = [
    { label: "Hardcoded", value: "Hardcoded" },
    { label: "Dependent", value: "Dependent" },
]


function EditTextField(ele, modifyEditData) {

    const handleTextFieldChange = (newVal) => {
        modifyEditData(ele["childParam"], newVal)
    }

    return (
        <TextField
            label=""
            key={ele["childParam"]}
            value={ele["value"] ? ele["value"] : ele["parentParam"] ? ele["parentMethod"] + " " + ele["parentUrl"] + " " + ele["parentParam"] : ""}
            onChange={(newVal) => { handleTextFieldChange(newVal) }}
            autoComplete="off"
            connectedLeft={
                <Box width="500px">
                    <TooltipText tooltip={ele["childParam"]} text={ele["childParam"]} textProps={{ color: "subdued", variant: "bodyLg" }} />
                </Box>
            }
        />
    )
}

export default EditTextField