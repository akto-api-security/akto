import React from 'react';
import { Box, TextField } from '@shopify/polaris';
const InlineEditableText = (props) => {
    const {textValue, setTextValue, handleSaveClick, setIsEditing, placeholder, maxLength} = props;
    const handleKeyDown = (event) => {
        if (event.key === 'Enter') {
          handleSaveClick();
        } else if (event.key === 'Escape') {
            setIsEditing(false);
        }
      }
    
    const handleBlurEvent = () => {
        handleSaveClick();
        setIsEditing(false);
    }

    return (
        <Box minWidth='320px' maxWidth='20vw'>
            <div style={{width:"auto"}} onKeyDown={handleKeyDown} onBlur={()=>handleBlurEvent()}>
                <TextField
                    value={textValue}
                    onChange={(val) => setTextValue(val)}
                    autoFocus
                    autoComplete="off"
                    maxLength={maxLength? maxLength:24}
                    showCharacterCount
                    onKeyDown={handleKeyDown}
                    placeholder={placeholder}
                />
            </div>
        </Box>
        
    );
};

export default InlineEditableText;