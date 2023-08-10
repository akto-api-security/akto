import React from 'react';
import './start-node.css';

const TextFieldCloseable = ({text, usePureJs=false}) => {

    let r = /\#\[.*?]#/g;
    let m = r.exec(text)

    if (usePureJs) {
        return <span className="request-editor">{JavaScriptBlock(text)}</span>
    }

    return (
        <span>
            {m && <span className="request-editor">{VariableOutside(text.substr(0, m.index))}</span>}
            {m && <span className="request-editor">{JavaScriptBlock(m[0])}</span>}
            {m && <TextFieldCloseable text={text.substr(m.index + m[0].length, text.length)}/>}
            {!m && <span className="request-editor">{VariableOutside(text)}</span>}
        </span>
    );
  
        
}

const VariableOutside = (text) => {

    let r = /\$\{((x(\d+)\.([\w\-\[\]\.]+))|(AKTO.changes_info\..*?))}/g;
    let m = r.exec(text)
    return (
        <span>
            {m && <span className="request-editor">{text.substr(0, m.index)}</span>}
            {m && <span className="request-editor request-editor-variable">{m[0]}</span>}
            {m && <span>{VariableOutside(text.substr(m.index + m[0].length, text.length))}</span>}
            {!m && <span className="request-editor">{text}</span>}
        </span>
    );


}


const JavaScriptBlock = (text) => {

    let r = /\$\{x(\d+)\.([\w\[\]\.]+)\}/g;
    let m = r.exec(text)
    return (
        <span>
            {m && <span className=" request-editor-matched">{text.substr(0, m.index)}</span>}
            {m && <span className="request-editor-matched request-editor-variable">{m[0]}</span>}
            {m && <span>{JavaScriptBlock(text.substr(m.index + m[0].length, text.length))}</span>}
            {!m && <span className="request-editor-matched">{text}</span>}
        </span>
    );


}

export default TextFieldCloseable