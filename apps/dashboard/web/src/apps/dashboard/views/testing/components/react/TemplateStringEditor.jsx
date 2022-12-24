import React, { useState, useEffect } from 'react';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome'
import { faEdit, faCheckSquare } from '@fortawesome/free-regular-svg-icons'

import InputBase from "@mui/material/TextField"
import IconButton from "@mui/material/IconButton"
import TextFieldCloseable from './TextFieldCloseable.jsx'


import './start-node.css';

const TemplateStringEditor = ({defaultText, onChange, usePureJs=false}) => {

    const [toggle, setToggle] = useState(true);
    const toggleChecked = (event) => { 
      if (!toggle) {
        onChange(text)
      }
      setToggle(toggle => true);
      event.stopPropagation();
    }


    const forceChecked = (event) => {
      setToggle(toggle => false);
    }



    let [text, setText] = React.useState(defaultText);

    const onChangeInputBase = (a, b) => {
        setText(a.target.value)
    }

    return (
       <div style={{position: "relative"}} onClick={forceChecked} className={toggle && "text-summary"}>
          {toggle && <TextFieldCloseable text={text} usePureJs={usePureJs} /> }
          {!toggle && <InputBase value={text} onChange={onChangeInputBase} fullWidth multiline inputProps={{className: 'request-editor'}} variant="standard"/>}
          {!toggle && <div style={{position: "absolute", top: "4px", right: "10px"}}>
            <IconButton onClick={toggleChecked}>
                <FontAwesomeIcon icon={toggle ? faEdit : faCheckSquare} className="primary-btn" />
            </IconButton>
          </div>}
       </div>
    );
  }

export default TemplateStringEditor