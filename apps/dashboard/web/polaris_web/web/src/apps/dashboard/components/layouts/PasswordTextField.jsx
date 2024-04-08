import { HideMinor, ViewMinor } from '@shopify/polaris-icons';
import { useState } from 'react';
import { Button, TextField } from '@shopify/polaris';

function PasswordTextField(props) {

    const [isTextVisible, setTextVisible] = useState(false);

    const handleValueChange = (val) => {
        if(props.onFunc){
            props.setField(val)
        }   
    }

    const toggleTextVisibility = () => {
        setTextVisible((prevState) => !prevState);
    };

    let type = isTextVisible ? 'string' : 'password'
    let buttonIcon = isTextVisible ? HideMinor : ViewMinor

    const toggleButton = (
        <Button icon={buttonIcon} onClick={toggleTextVisibility} plain />
    )

    return (
        <TextField suffix={toggleButton} value={props.field} type={type} helpText={props.helpText} 
                    onChange={handleValueChange} label={props.label ? props.label : null}
                    monospaces={props?.monospaced}
        />
    )
}

export default PasswordTextField