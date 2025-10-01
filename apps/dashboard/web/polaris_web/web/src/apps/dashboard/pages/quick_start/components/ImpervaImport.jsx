import { HorizontalStack, Text, Badge, VerticalStack, ButtonGroup, Button } from "@shopify/polaris";
import { CancelMajor } from "@shopify/polaris-icons"
import { useState } from "react";
import FileUpload from "../../../components/shared/FileUpload";
import api from "../api";
import func from "@/util/func";

function ImpervaImport() {

    const [files, setFiles] = useState(null)
    const [loading, setLoading] = useState(false)

    const setFilesCheck = (file) => {
        var reader = new FileReader()
        reader.readAsText(file)
        reader.onload = async () => {
            setFiles({ content: reader.result, name: file.name })
        }
    }

    const uploadFile = async() => {
        setLoading(true)
        const formData = new FormData();
        formData.append("impervaString", files.content)
        await api.importImpervaSchema(formData).then((res) => {
            setLoading(false)
            func.setToast(true, false, "Import is in process. Please check API Inventory page after sometime.")
            setFiles(null)
        }).catch((err) => {
            setLoading(false)
            console.log(err)
            func.setToast(true, true, err?.response?.data?.actionErrors?.[0] || "Failed to import Imperva file")
        })
    }

    return (
        <div className='card-items'>
            <Text variant='bodyMd'>
                Use Imperva file to add API endpoints. If you like what you see, we highly recommend using AWS or GCP traffic mirroring to get real user data for a smooth, automated and minimum false positive experience.
            </Text>

            <HorizontalStack gap="2" >
                {files ?
                    <Badge size='medium' status='success'>
                        {files.name}
                        <Button icon={CancelMajor} plain onClick={() => setFiles(null)} />
                    </Badge>
                    : null}
                File: <FileUpload
                    fileType="file"
                    acceptString=".json , .yaml, .yml"
                    setSelectedFile={setFilesCheck}
                    allowMultiple={false}
                    allowedSize={10*1024*1024} />
            </HorizontalStack>

            <VerticalStack gap="2">
                <ButtonGroup>
                    <Button
                    onClick={uploadFile}
                    primary
                    disabled={files === null}
                    loading={loading}>
                        Upload
                    </Button>
                </ButtonGroup>
            </VerticalStack>
        </div>
    )
}

export default ImpervaImport;