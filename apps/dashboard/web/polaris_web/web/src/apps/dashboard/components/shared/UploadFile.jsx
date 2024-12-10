import { Button, Tooltip } from "@shopify/polaris"
import { UploadIcon } from "@shopify/polaris-icons";
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
        (<Tooltip content={tooltipText}>
            <Button 
                // icon={UploadMajor} 
                {
                    ...(
                      ((primary === undefined || primary === true) && (plain === undefined || plain === true))
                        ? { variant: "tertiary" }
                        : (
                            (primary === false && plain === false)
                              ? {}
                              : (primary === false ? { variant: "monochromePlain" } : { variant: "primary" })
                          )
                    )
                  }
                removeUnderline
                onClick={onPickFile}>
                {label}
                <input
                    type="file"
                    style={{display: "none"}}
                    ref={fileUploadRef}
                    accept={fileFormat}
                    onChange={onFilePicked} 
                />
            </Button>
        </Tooltip>)
    );
}

export default UploadFile