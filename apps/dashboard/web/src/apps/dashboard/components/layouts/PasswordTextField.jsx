import React from 'react'
import { ResourcesMajor } from '@shopify/polaris-icons';
import { useState } from 'react';
import { Button, Icon, TextField } from '@shopify/polaris';

function PasswordTextField(props) {

    const [isTextVisible, setTextVisible] = useState(false);

    const toggleTextVisibility = () => {
        setTextVisible((prevState) => !prevState);
    };

    let type = isTextVisible ? 'string' : 'password'
    let buttonIcon = ResourcesMajor

    const toggleButton = (
        <Button icon={buttonIcon} onClick={toggleTextVisibility} plain />
    )

    return (
        <TextField suffix={toggleButton} value={props.text} type={type} />
    )
}

export default PasswordTextField