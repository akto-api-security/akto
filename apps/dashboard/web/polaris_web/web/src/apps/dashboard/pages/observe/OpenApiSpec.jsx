import React, { useEffect, useState } from 'react'
import FileUpload from '../../components/shared/FileUpload'
import { Box, TextField, VerticalStack } from '@shopify/polaris';
import api from './api';
import func from '@/util/func';
import SpinnerCentered from '../../components/progress/SpinnerCentered';
import SampleData from '../../components/shared/SampleData';

function OpenApiSpec({apiCollectionId}) {

    const [fileName, setFileName] = useState('')
    const [loading, setLoading] = useState(false)
    const [content, setContent] = useState(null)

    const saveFile = async(obj) => {
        setLoading(true)
        await api.saveContent(obj).then((resp)=>{
            setLoading(false)
            func.setToast(true,false,"File uploaded successfully.")
        })
    }

    const getFile = async() =>{
        setLoading(true)
        await api.loadContent(Number(apiCollectionId)).then((resp)=>{
            setLoading(false)
            setFileName(resp.fileName)
            setContent(resp.content)
        })
    }

    const setFilesCheck = (file) => {
        var reader = new FileReader();
        reader.readAsText(file);
        reader.onload = () => {
            setFileName(file.name)
            setContent(reader.result)
            const swaggerObj = {
                swaggerContent: reader.result, 
                filename: file.name, 
                apiCollectionId : Number(apiCollectionId)
            }
            saveFile(swaggerObj)
        }
    }

    useEffect(()=> {
        getFile()
    },[])

    const UploadFile = (
        <FileUpload fileType="file" acceptString=".json" setSelectedFile={setFilesCheck} allowMultiple={false} allowedSize={2*1024*1024}/>
    )

    const data = {
        message: content
    }

    return (
        <Box padding="4">
            {loading ? <SpinnerCentered /> :
                <VerticalStack gap={"4"}>
                    <TextField placeholder="Upload JSON file here" value={fileName} prefix={UploadFile} />
                    {content ? <SampleData minHeight="60vh" data={data} language="json"/> : null}
                </VerticalStack>
            }
        </Box>
       
    )
}

export default OpenApiSpec