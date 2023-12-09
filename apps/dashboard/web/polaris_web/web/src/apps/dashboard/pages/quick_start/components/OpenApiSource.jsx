import { HorizontalStack, Text, Badge, VerticalStack, ButtonGroup, Button, Link } from "@shopify/polaris";
import { CancelMajor } from "@shopify/polaris-icons"
import { useState } from "react";
import FileUpload from "../../../components/shared/FileUpload";
import api from "../api";
import func from "@/util/func";

function OpenApiSource() {

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
        formData.append("openAPIString", files.content)
        await api.importDataFromOpenApiSpec(formData).then((res) => {
            let apiCollectionId = res.apiCollectionId;
            setLoading(false)
            let link = `/dashboard/observe/inventory/`
            if(apiCollectionId){
                link += apiCollectionId
            }
            const forwardLink = (
                <HorizontalStack gap={1}>
                    <Text> File uploaded successfully. You can go to </Text>
                    <Link url={link}>API collections</Link>
                    <Text> to see your APIs.</Text>
                </HorizontalStack>
            )
            func.setToast(true, false, forwardLink);

            func.refreshApiCollections();

        }).catch((err) => {
            setLoading(false)
        })
    }

    const goToDocs = () => {
        window.open("#")
    }

    return (
        <div className='card-items'>
            <Text variant='bodyMd'>
                Use openAPI/swagger file to add API endpoints. If you like what you see, we highly recommend using AWS or GCP traffic mirroring to get real user data for a smooth, automated and minimum false positive experience.
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
                    allowMultiple={false} />
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
                    <Button onClick={goToDocs}>Go to docs</Button>
                </ButtonGroup>
            </VerticalStack>
        </div>
    )
}

export default OpenApiSource;