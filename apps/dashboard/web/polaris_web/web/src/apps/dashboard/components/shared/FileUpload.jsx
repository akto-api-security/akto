import { Button, DropZone } from '@shopify/polaris'
import {UploadMajor} from "@shopify/polaris-icons"
import React, { useCallback } from 'react'
import "./style.css"
import func from "@/util/func"

function FileUpload(props) {
    const {fileType, acceptString, setSelectedFile,allowMultiple,allowedSize} = props;
    let limitSize = allowedSize ? allowedSize : 2*1024*1024

    const handleDropZoneDrop = useCallback(
        (_dropFiles, acceptedFiles, _rejectedFiles) =>{
            if(allowMultiple){
                setSelectedFile((files) => [...files, ...acceptedFiles]);
            }else{
                if(acceptedFiles[0].size > limitSize){
                    func.setToast(true,true,`Uploaded file size is greater than ${func.getSizeOfFile(limitSize)}.`)
                }else{
                    setSelectedFile(acceptedFiles[0])
                }
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
                validates={(file) => file.size <= limitSize}
            >
                <Button plain icon={UploadMajor} />
            </DropZone>
        </div>
    )
}

export default FileUpload