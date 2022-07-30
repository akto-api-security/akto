import React from 'react';

import TextField from "@mui/material/TextField"

import './start-node.css';

const TextFieldCloseable = ({text}) => {

    var r = /\$\{x(\d+)\.([\w\.]+)\}/g;
    let m = r.exec(text)

    return (
        <span>
            {m && <span className="request-editor">{text.substr(0, m.index)}</span>}
            {m && <span className="request-editor request-editor-matched">{m[0]}</span>}
            {m && <TextFieldCloseable text={text.substr(m.index + m[0].length, text.length)}/>}
            {!m && <span className="request-editor">{text}</span>}
        </span>
    );
  
        
}

export default TextFieldCloseable