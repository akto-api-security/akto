import { Button, Tooltip } from "@shopify/polaris"
import { useRef } from "react"


function UploadFile({ fileFormat, fileChanged, tooltipText, label, primary, plain}) {

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
        <Button 
            // icon={UploadMajor} 
            primary={primary !== undefined ? primary : true}
            plain={plain === undefined ? true: plain}
            monochrome removeUnderline
            onClick={onPickFile}>
            <Tooltip content={tooltipText} dismissOnMouseOut width="wide">{label}</Tooltip>
            <input
                type="file"
                style={{display: "none"}}
                ref={fileUploadRef}
                accept={fileFormat}
                onChange={onFilePicked} 
            />
        </Button>
    )
}

export default UploadFile