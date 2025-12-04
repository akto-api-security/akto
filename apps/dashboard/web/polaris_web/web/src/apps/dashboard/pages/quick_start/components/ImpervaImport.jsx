import { useState } from "react";
import FileUploadCard from "../../../components/shared/FileUploadCard";
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

    const goToDocs = () => {
        window.open("https://docs.akto.io/traffic-connector/manual/imperva", '_blank')
    }

    return (
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
}

export default ImpervaImport;