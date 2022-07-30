import React, { useState, useEffect } from 'react';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome'
import { faEdit, faCheckSquare } from '@fortawesome/free-regular-svg-icons'

import InputBase from "@mui/material/TextField"
import IconButton from "@mui/material/IconButton"
import TextFieldCloseable from './TextFieldCloseable.jsx'


import './start-node.css';

const TemplateStringEditor = ({defaultText}) => {

    const [toggle, setToggle] = useState(true);
    const toggleChecked = () => setToggle(toggle => !toggle);
    let [text, setText] = React.useState(defaultText);

    const onChange = (a, b) => {
        setText(a.target.value)
    }

    return (
       <div style={{position: "relative", width: "550px"}}>
          {toggle && <TextFieldCloseable text={text}/> }
          {!toggle && <InputBase value={text} onChange={onChange} fullWidth multiline inputProps={{className: 'request-editor'}} variant="standard"/>}
          <div style={{position: "absolute", top: "4px", right: "10px"}}>
            <IconButton onClick={toggleChecked}>
                <FontAwesomeIcon icon={toggle ? faEdit : faCheckSquare} className="primary-btn" />
            </IconButton>
          </div>
       </div>
    );
  }

export default TemplateStringEditor