import { Button, DropZone } from '@shopify/polaris'
import {UploadMajor} from "@shopify/polaris-icons"
import React, { useCallback } from 'react'
import "./style.css"

function FileUpload(props) {
    const {fileType, acceptString, setSelectedFile,allowMultiple} = props;
    const handleDropZoneDrop = useCallback(
        (_dropFiles, acceptedFiles, _rejectedFiles) =>{
            if(allowMultiple){
                setSelectedFile((files) => [...files, ...acceptedFiles]);
            }else{
                setSelectedFile(acceptedFiles[0])
            }
        },
        [],
    );
    return (
        <div style={{height: '20px !important', width: 'fit-content'}}>
            <DropZone
                accept={acceptString}
                type={fileType}
                onDrop={handleDropZoneDrop}
                allowMultiple={allowMultiple}
            >
                <Button plain icon={UploadMajor} />
            </DropZone>
        </div>
    )
}

export default FileUpload