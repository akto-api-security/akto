import { Outlet, useLocation, useNavigate } from "react-router-dom"
import { history } from "@/util/history";
import Store from "../store";
import ObserveStore from "../pages/observe/observeStore"
import homeFunctions from "./home/module";
import { useEffect, useState } from "react";
import { Avatar, Button, Frame, HorizontalStack, Modal, Toast } from "@shopify/polaris";
import "./dashboard.css"
import settingRequests from "./settings/api";
import dashboardFunc from "./transform"
import AktoGptLayout from "../components/aktoGpt/AktoGptLayout";
import api from "./observe/api";
import func from "@/util/func"

function Dashboard() {

    const location = useLocation();
    history.location = location
    history.navigate = useNavigate();

    const setAllCollections = Store(state => state.setAllCollections)
    const isInsideCollection = ObserveStore(state => state.inventoryFlyout)
    const filteredEndpoints = ObserveStore(state => state.filteredItems)
    const sampleData = ObserveStore(state => state.samples)
    const selectedUrl = ObserveStore(state => state.selectedUrl)

    const fetchAllCollections = async () => {
        let apiCollections = await homeFunctions.getAllCollections()
        setAllCollections(apiCollections)
    }

    const [isGptActive, setIsGptActive] = useState(false)
    const [prompts, setPrompts] = useState([])
    const [isGptScreenActive, setIsGptScreenActive] = useState(false)
    const [apiCollectionId, setApiCollectionId] = useState(-1)

    useEffect(() => {
        fetchAllCollections()
    }, [])

    const checkGptActive = async(path) => {
        if(path.includes('observe/inventory/')){
            let apiCollectionIdCopy = Number(path.split("inventory/")[1])
            setApiCollectionId(apiCollectionIdCopy)
            if(apiCollectionIdCopy){
                await settingRequests.fetchAktoGptConfig(apiCollectionIdCopy).then((resp)=>{
                    if(resp.currentState[0].state === "ENABLED"){
                        setIsGptActive(true)
                    }
                })

                let requestObj = {}
                if(isInsideCollection){
                    requestObj = {
                        key: "PARAMETER",
                        jsonStr: sampleData,
                        apiCollectionId: apiCollectionIdCopy
                    }
                }else{
                    requestObj = {
                        key: "COLLECTION",
                        filteredItems: filteredEndpoints,
                        apiCollectionId: apiCollectionIdCopy
                    }
                }

                const activePrompts = dashboardFunc.getPrompts(requestObj)
                setPrompts(activePrompts)
            }
        }else if(path.includes('observe/sensitive')){
            setIsGptActive(true)
            const activePrompts = dashboardFunc.getPrompts({key: "DATA_TYPES"})
            setPrompts(activePrompts)
        } else {
            setIsGptActive(false)
        }
    }

    useEffect(()=> {
        checkGptActive(location.pathname)
    },[location,filteredEndpoints,isInsideCollection,sampleData])

    const runTests = async(testsList) => {
        setIsGptScreenActive(false)
        const apiKeyInfo={
            apiCollectionId: apiCollectionId,
            url: selectedUrl.url,
            method: selectedUrl.method
        }
        await api.scheduleTestForCustomEndpoints(apiKeyInfo,func.timNow(),false,testsList,"akto_gpt_test",-1,-1)
        func.setToast(true,false,"Triggered tests successfully!")
    }

    const toastConfig = Store(state => state.toastConfig)
    const setToastConfig = Store(state => state.setToastConfig)

    const disableToast = () => {
        setToastConfig({
            isActive: false,
            isError: false,
            message: ""
        })
    }

    const toastMarkup = toastConfig.isActive ? (
        <Toast content={toastConfig.message} error={toastConfig.isError} onDismiss={disableToast} duration={4000} />
    ) : null;

    return (
        <div className="dashboard">
        <Frame>
            {isGptActive ? <div className="aktoButton">
                <Button removeUnderline plain monochrome onClick={()=> setIsGptScreenActive(!isGptScreenActive)}>
                    <HorizontalStack gap="1">
                        Ask AktoGpt
                        <Avatar customer size="small" name="gpt" source="/public/gpt_logo.svg" />
                    </HorizontalStack>
                </Button>
            </div> : null}
            <Outlet />
            {toastMarkup}
            <div>
                <Modal large open={isGptScreenActive} onClose={()=> setIsGptScreenActive(false)} title="Akto GPT">
                    <Modal.Section flush>
                        <AktoGptLayout prompts={prompts} apiCollectionId={apiCollectionId} closeModal={()=> setIsGptScreenActive(false)} runCustomTests={(tests)=> runTests(tests)}/>
                    </Modal.Section>
                </Modal>
            </div>
        </Frame>
        </div>
    )
}

export default Dashboard