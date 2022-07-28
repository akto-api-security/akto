import React, { useState } from 'react';

import { Handle, Position } from 'react-flow-renderer';
import Box from '@mui/material/Box'
import InputLabel from '@mui/material/InputLabel'
import Autocomplete from '@mui/material/Autocomplete';
import InputAdornment from '@mui/material/InputAdornment';
import TextField from '@mui/material/TextField'
import FormControl from '@mui/material/FormControl';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome'
import { faSearch } from '@fortawesome/free-solid-svg-icons'

import './start-node.css';
import useStore from './store'

const BlankNode = () => {

    const endpointsList = useStore(state => state.endpointsList)

    return (
        <div className="start-node nodrag">
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

            <div>
                {<FormControl sx={{ m: 1, minWidth: 120 }} size="small">
                    <Autocomplete
                        color="#6200EA"
                        options={endpointsList}
                        autoHighlight
                        disableClearable
                        getOptionLabel={option => {return option.method + " " + option.endpoint}}
                        renderOption={(props, option) => (
                              <Box  component="li" {...props}>{option.method} {option.endpoint}</Box>
                        )}
                        renderInput={(params) => (
                            <TextField
                              {...params}
                              label="Select API"
                              inputProps={{
                                ...params.inputProps,
                                autoComplete: 'new-password'
                              }}
                            />
                          )}
                        variant="standard"
                    ></Autocomplete>
                </FormControl>}
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