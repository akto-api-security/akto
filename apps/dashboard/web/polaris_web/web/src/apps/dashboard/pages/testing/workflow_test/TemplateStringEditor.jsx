import React, { useState, useEffect } from 'react';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faEdit, faCheckSquare } from '@fortawesome/free-regular-svg-icons';

import InputBase from '@mui/material/TextField';
import IconButton from '@mui/material/IconButton';

import './start-node.css';

const TemplateStringEditor = ({ defaultText, onChange, usePureJs = false, storageKey }) => {
  const [toggle, setToggle] = useState(true);
  const [text, setText] = useState(() => {
    // Retrieve data from localStorage on component initialization
    const storedData = localStorage.getItem(storageKey);
    return storedData || defaultText;
  });

  const toggleChecked = () => {
    if (!toggle) {
      // Save the data to localStorage when toggling
      localStorage.setItem(storageKey, text);
      onChange(text);
    }
    setToggle((prevToggle) => !prevToggle);
  };

  const onChangeInputBase = (e) => {
    const updatedText = e.target.value;
    setText(updatedText);

    if (toggle) {
      // Save the data to localStorage as the user types
      localStorage.setItem(storageKey, updatedText);
    }
  };

  return (
    <div style={{ position: 'relative' }}>
      {toggle && <TextFieldCloseable text={text} usePureJs={usePureJs} />}
      {!toggle && (
        <InputBase
          value={text}
          onChange={onChangeInputBase}
          fullWidth
          multiline
          inputProps={{ className: 'request-editor' }}
          variant="standard"
        />
      )}
      <div style={{ position: 'absolute', top: '4px', right: '10px' }}>
        <IconButton onClick={toggleChecked}>
          <FontAwesomeIcon icon={toggle ? faEdit : faCheckSquare} className="primary-btn" />
        </IconButton>
      </div>
    </div>
  );
};

export default TemplateStringEditor;
