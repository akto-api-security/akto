import React, { useState } from 'react';

import { Handle, Position } from 'react-flow-renderer';
import Box from '@mui/material/Box'
import Autocomplete from '@mui/material/Autocomplete';
import TextField from '@mui/material/TextField'
import FormControl from '@mui/material/FormControl';
import InputArgumentsDialog from './InputArgumentsDialog.jsx'
import './start-node.css';
import useStore from './store'

const BlankNode = (nodeData) => {
    const endpointsList = useStore(state => state.endpointsList)
    const nodeEndpointMap = useStore(state => state.nodeEndpointMap)
    const addNodeEndpoint = useStore(state => state.addNodeEndpoint)

    const onEndpointChange = (event, endpointData) => {
        addNodeEndpoint(nodeData.id, endpointData)
    }

    const onEndpointInputChange = (event, newInputValue) => {
        let endpointData = nodeEndpointMap[nodeData.id]
        if (endpointData) {
            if (newInputValue !== (endpointData.method + " " + endpointData.endpoint)) {
                addNodeEndpoint(nodeData.id, null)
            }
        }
        
    }
    return (
        <div className={"start-node " + (nodeEndpointMap[nodeData.id] ? "green-boundary" : "red-boundary") }>
            <Handle
                type="target"
                position={Position.Top}
                isConnectable={false}
                style={{
                    height: '13px',
                    width: '13px',
                    bottom: '-6px'
                }}
            >
            </Handle>

            <div style={{display: "flex", justifyContent: "space-between"}}>
                {<FormControl sx={{ m: 1, minWidth: 120 }} fullWidth>
                    <Autocomplete
                        options={endpointsList}
                        autoHighlight
                        disableClearable
                        openOnFocus={!nodeEndpointMap[nodeData.id]}
                        freeSolo
                        onChange={onEndpointChange}
                        onInputChange={onEndpointInputChange}
                        getOptionLabel={option => {return option.endpoint || ""}}
                        renderOption={(props, option) => (
                            <Box  component="li" {...props}>
                                <span className="autocomplete-options ">
                                    <span method={option.method}>{option.method} </span>
                                    {option.endpoint}
                                </span>
                            </Box>
                        )}
                        renderInput={(params) => {
                            let method = nodeEndpointMap[nodeData.id] ? nodeEndpointMap[nodeData.id].method : ""
                            return (<TextField
                              {...params}
                              placeholder="Select API"
                              InputProps={{
                                ...params.InputProps,
                                componentsProps: { input: params.inputProps },
                                autoComplete: 'new-password',
                                disableUnderline: true,
                                startAdornment: (
                                    <span className={"MuiInput-input " + method}>
                                        {method}
                                    </span>
                                )
                              }}
                              
                              variant="standard"
                            />)
                        }}
                        
                    ></Autocomplete>
                </FormControl>}
                <InputArgumentsDialog/>
            </div>      
            <Handle
                type="source"
                position={Position.Bottom}
                isConnectable={false}
                style={{
                    height: '13px',
                    width: '13px',
                    bottom: '-6px'
                }}
                isConnectable={true}
            >
            </Handle>
        </div>
    )
}

export default BlankNode