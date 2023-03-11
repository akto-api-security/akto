import React, { useState, useEffect, useRef } from 'react';
import InputBase from "@mui/material/TextField"
import './start-node.css';

const TemplateStringEditor = ({defaultText, onChange, usePureJs=false}) => {

    let [text, setText] = useState(defaultText);

    const onChangeInputBase = (a, b) => {
        setText(a.target.value);
    }

    useEffect(()=>{
      onChange(text);
    },[text]);

    return (
       <div style={{position: "relative"}}>
          <InputBase value={text} onChange={onChangeInputBase} fullWidth multiline inputProps={{className: 'request-editor'}} variant="standard"/>
       </div>
    );
  }

export default TemplateStringEditor