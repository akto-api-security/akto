import React from 'react';
import { Box, TextField } from '@shopify/polaris';
const InlineEditableText = (props) => {
    const {textValue, setTextValue, handleSaveClick, setIsEditing, placeholder, maxLength, minWidth = '320px', maxWidth = '20vw', fitParentWidth = false } = props;
    
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
        <Box minWidth={fitParentWidth ? "100%" : minWidth} maxWidth={fitParentWidth ? "100%" : maxWidth}>
            <div style={{width:"auto"}} onKeyDown={handleKeyDown} onBlur={()=>handleBlurEvent()}>
                <TextField
                    value={textValue}
                    onChange={(val) => setTextValue(val)}
                    autoFocus
                    autoComplete="off"
                    maxLength={maxLength}
                    showCharacterCount={maxLength ? true : false}
                    onKeyDown={handleKeyDown}
                    placeholder={placeholder}
                />
            </div>
        </Box>
        
    );
};

export default InlineEditableText;