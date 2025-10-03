import { Text, Modal, DescriptionList, RadioButton, Icon, Tooltip } from "@shopify/polaris";
import { useState } from "react";
import FileUploadCard from "../../../components/shared/FileUploadCard";
import api from "../api";
import func from "@/util/func";
import SpinnerCentered from "../../../components/progress/SpinnerCentered"
import { QuestionMarkMinor } from "@shopify/polaris-icons"

function OpenApiSource() {

    const [files, setFiles] = useState(null)
    const [loading, setLoading] = useState(false)
    const [showImportDetailsModal, setShowImportDetailsModal] = useState(false);
    const [uploadObj, setUploadObj] = useState({})
    const [importType, setImportType] = useState('ONLY_SUCCESSFUL_APIS');
    const [uploadId, setUploadId] = useState('');
    const [intervalId, setIntervalId] = useState(null);

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
            let uploadId = res.uploadId;
            setLoading(false)
            setShowImportDetailsModal(true);
            setUploadId(uploadId);
            const id = setInterval(async () => {
                api.fetchSwaggerImportLogs(uploadId).then(resp => {
                    if(resp.uploadDetails.uploadStatus === 'SUCCEEDED' || resp.uploadDetails.uploadStatus === 'FAILED'){
                        clearInterval(id);
                        setIntervalId(null);
                        setUploadObj(resp.uploadDetails);
                        if(resp.uploadDetails.uploadStatus === 'FAILED'){
                            setUploadId(uploadId);
                        }
                    }
                });
            }, 5000);
            setIntervalId(id);

        }).catch((err) => {
            setLoading(false)
        })
    }

    const goToDocs = () => {
        window.open("https://docs.akto.io/traffic-connections/traffic-data-sources/openapi", '_blank')
    }

    const toggleImport = (val) => {
        setImportType(val)
    }

    const startImport = async() => {
        setShowImportDetailsModal(false)
        setUploadObj({})
        api.ingestSwagger(uploadId, importType).then(resp => {
            func.setToast(true, false, "File import has begun, refresh inventory page to view the imported APIs")
        })
    }

    const closeModal = async() => {
        setShowImportDetailsModal(false)
        if(intervalId != null){
            clearInterval(this.intervalId)
            setIntervalId(null)
        }
        api.deleteImportedPostman(uploadId).then(resp => {
        })
        setUploadObj({})
    }

    let successModalContent = (
        <div>
            <Text>Total APIs in the uploaded file: {uploadObj.totalCount}</Text>
            <Text>Total APIs (including all response codes) parsed correctly by Akto: {uploadObj.correctlyParsedApis}</Text>
            {
                uploadObj.apisWithErrorsAndCannotBeImported + uploadObj.apisWithErrorsAndParsed > 0 &&
                <div>
                    <Text>Total APIs parsed with errors by Akto (can still be imported): {uploadObj.apisWithErrorsAndParsed}</Text>
                    <Text>Total APIs which cannot be imported: {uploadObj.apisWithErrorsAndCannotBeImported}</Text>
                    { uploadObj.apisWithErrorsAndParsed > 0 && 
                    <div>
                        <div style={{display: "flex"}}>
                            <RadioButton id="forceImport" label="Force import all APIs" checked={importType === "ALL_APIS"} onChange={()=>toggleImport("ALL_APIS")} />
                            <div style={{margin: "auto 12px"}}>
                                <Tooltip content={`We will import ${uploadObj.correctlyParsedApis + uploadObj.apisWithErrorsAndParsed} apis i.e. all the apis that have been correctly parsed and apis which have errors and can still be imported`} dismissOnMouseOut width="wide">
                                    <Icon source={QuestionMarkMinor} color="base" />
                                </Tooltip>
                            </div>
                        </div>
                        <div style={{display: "flex"}}>
                            <RadioButton id="successFulApis" label="Import only correctly formatted APIs" checked={importType === "ONLY_SUCCESSFUL_APIS"} onChange={()=>toggleImport("ONLY_SUCCESSFUL_APIS")}/>
                            <div style={{margin: "auto 12px"}}>
                                <Tooltip content={`We will import ${uploadObj.correctlyParsedApis} apis i.e. all the apis that have been correctly parsed only`} dismissOnMouseOut width="wide">
                                    <Icon source={QuestionMarkMinor} color="base" />
                                </Tooltip>
                            </div>
                        </div>
                    </div>
                    }
                </div>
            }
            {uploadObj.collectionErrors > 0 && 
                <div>
                    {uploadObj.collectionErrors.forEach(error => {
                        <Text>{{error}}</Text>
                    })}
                </div>
            }
            {uploadObj.collectionErrors && uploadObj.collectionErrors.length > 0 &&
                <div>
                    <Text>File level errors</Text>
                    <DescriptionList
                        items={uploadObj.collectionErrors}
                    />
                </div>
            }

            {uploadObj.logs && uploadObj.logs.length > 0 &&
                <div>
                    <Text>Url level errors</Text>
                    <DescriptionList
                        items={uploadObj.logs}
                    />
                </div>
            }

                
            
        </div>
    )
    return (
        <>
            <FileUploadCard
                description="Use openAPI/swagger file to add API endpoints. If you like what you see, we highly recommend using AWS or GCP traffic mirroring to get real user data for a smooth, automated and minimum false positive experience."
                files={files}
                onFileRemove={() => setFiles(null)}
                acceptString=".json , .yaml, .yml"
                setSelectedFile={setFilesCheck}
                allowMultiple={false}
                allowedSize={5*1024*1024}
                onUpload={uploadFile}
                loading={loading}
                onSecondaryAction={goToDocs}
                secondaryActionLabel="Go to docs"
            />
            <Modal
                open={showImportDetailsModal}
                onClose={() => {closeModal()}}
                title="Swagger Import details"
                key="swagger-import-details-modal"
                primaryAction={{
                    content: "Import",
                    onAction: startImport,
                    disabled: uploadObj.uploadStatus !== 'SUCCEEDED' || ( uploadObj.uploadStatus === 'SUCCEEDED' && (uploadObj.apisWithErrorsAndParsed + uploadObj.correctlyParsedApis === 0))
                }}
                secondaryActions={[
                    {
                        content: 'Cancel',
                        onAction: closeModal,
                    },
                ]}
            >
                <Modal.Section>
                    {uploadObj.uploadStatus === 'SUCCEEDED' ?
                        successModalContent
                    : uploadObj.uploadStatus === 'FAILED' ?
                    <Text>Something went wrong while processing this postman import. Please reach out to us at help@akto.io with the following uploadId: {uploadId} for further support.</Text> :
                    <div><Text>We are analyzing the swagger file before we import it in Akto</Text><SpinnerCentered></SpinnerCentered></div>
                }
                </Modal.Section>
            </Modal>
        </>
    )
}

export default OpenApiSource;