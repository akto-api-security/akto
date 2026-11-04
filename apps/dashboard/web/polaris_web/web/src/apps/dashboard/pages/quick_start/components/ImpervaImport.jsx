import { useState } from "react";
import { RadioButton, VerticalStack } from "@shopify/polaris";
import FileUploadCard from "../../../components/shared/FileUploadCard";
import ImpervaApiImport from "./ImpervaApiImport";
import api from "../api";
import func from "@/util/func";

function ImpervaImport() {

    const [files, setFiles] = useState(null)
    const [loading, setLoading] = useState(false)
    // importing with Imperva API credentials needs the IMPERVA_API_IMPORT feature (granted in stigg, so not on-prem)
    const apiImportAllowed = window?.STIGG_FEATURE_WISE_ALLOWED?.IMPERVA_API_IMPORT?.isGranted === true
    const [type, setType] = useState(apiImportAllowed ? "api" : "file")

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

    const goToDocs = () => {
        window.open("https://docs.akto.io/traffic-connector/third-party-software/imperva", '_blank')
    }

    const fileUploadCard = (
        <FileUploadCard
            description="Use Imperva file to add API endpoints. If you like what you see, we highly recommend using AWS or GCP traffic mirroring to get real user data for a smooth, automated and minimum false positive experience."
            files={files}
            onFileRemove={() => setFiles(null)}
            acceptString=".json"
            setSelectedFile={setFilesCheck}
            allowMultiple={false}
            allowedSize={10*1024*1024}
            onUpload={uploadFile}
            loading={loading}
            onSecondaryAction={goToDocs}
            secondaryActionLabel="Go to docs"
        />
    )

    if (!apiImportAllowed) {
        return fileUploadCard
    }

    return (
        <VerticalStack gap="4">
            <VerticalStack gap="1">
                <RadioButton id="impervaApi" label="Import using Imperva API credentials" checked={type === "api"} onChange={() => setType("api")} />
                <RadioButton id="impervaFile" label="Import using Imperva file" checked={type === "file"} onChange={() => setType("file")} />
            </VerticalStack>
            {type === "api" ? <ImpervaApiImport /> : fileUploadCard}
        </VerticalStack>
    )
}

export default ImpervaImport;