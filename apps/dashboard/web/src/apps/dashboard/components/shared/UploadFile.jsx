import { Button, Tooltip } from "@shopify/polaris"
import { UploadMajor } from '@shopify/polaris-icons';
import { useRef } from "react"


function UploadFile({ fileFormat, fileChanged, tooltipText, label}) {

    const fileUploadRef = useRef("")

    function onPickFile () {
        fileUploadRef.current.click()  
    }
    
    function onFilePicked (event) {
        const files = event.target.files
        
        fileChanged(files[0])
        //this.$emit("fileChanged", {file: files[0], label: this.label, type: this.type})
        // If you select the same file twice... the onChange() function doesn't get activated
        // So by resetting the value it gets tricked :)
        event.target.value = null
    }


    return (
        <Tooltip content={tooltipText}>
            <Button icon={UploadMajor} primary onClick={onPickFile}>
                {label}
                <input
                    type="file"
                    style={{display: "none"}}
                    ref={fileUploadRef}
                    accept={fileFormat}
                    onChange={onFilePicked} 
                />
            </Button>
        </Tooltip>
    )
}

export default UploadFile