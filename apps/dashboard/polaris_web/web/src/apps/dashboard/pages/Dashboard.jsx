import { Outlet, useLocation, useNavigate } from "react-router-dom"
import { history } from "@/util/history";
import Store from "../store";
import homeFunctions from "./home/module";
import { useEffect } from "react";
import { Frame, Toast } from "@shopify/polaris";
import "./dashboard.css"
import func from "@/util/func"

function Dashboard() {

    const location = useLocation();
    history.location = location
    history.navigate = useNavigate();

    const setAllCollections = Store(state => state.setAllCollections)
    const setCollectionsMap = Store(state => state.setCollectionsMap)
    // const isInsideCollection = ObserveStore(state => state.inventoryFlyout)
    // const filteredEndpoints = ObserveStore(state => state.filteredItems)
    // const sampleData = ObserveStore(state => state.samples)
    // const selectedUrl = ObserveStore(state => state.selectedUrl)

    const fetchAllCollections = async () => {
        let apiCollections = await homeFunctions.getAllCollections()
        const allCollectionsMap = func.mapCollectionIdToName(apiCollections)
        setCollectionsMap(allCollectionsMap)
        setAllCollections(apiCollections)
    }

    useEffect(() => {
        fetchAllCollections()
    }, [])

    // const checkGptActive = async(path) => {
    //     if(path.includes('observe/inventory/')){
    //         let apiCollectionIdCopy = Number(path.split("inventory/")[1])
    //         setApiCollectionId(apiCollectionIdCopy)
    //         if(apiCollectionIdCopy){
    //             await settingRequests.fetchAktoGptConfig(apiCollectionIdCopy).then((resp)=>{
    //                 if(resp.currentState[0].state === "ENABLED"){
    //                     setIsGptActive(true)
    //                 }
    //             })

    //             let requestObj = {}
    //             if(isInsideCollection){
    //                 requestObj = {
    //                     key: "PARAMETER",
    //                     jsonStr: sampleData,
    //                     apiCollectionId: apiCollectionIdCopy
    //                 }
    //             }else{
    //                 requestObj = {
    //                     key: "COLLECTION",
    //                     filteredItems: filteredEndpoints,
    //                     apiCollectionId: apiCollectionIdCopy
    //                 }
    //             }

    //             const activePrompts = dashboardFunc.getPrompts(requestObj)
    //             setPrompts(activePrompts)
    //         }
    //     }else if(path.includes('observe/sensitive')){
    //         setIsGptActive(true)
    //         const activePrompts = dashboardFunc.getPrompts({key: "DATA_TYPES"})
    //         setPrompts(activePrompts)
    //     } else {
    //         setIsGptActive(false)
    //     }
    // }

    // const runTests = async(testsList) => {
    //     setIsGptScreenActive(false)
    //     const apiKeyInfo={
    //         apiCollectionId: apiCollectionId,
    //         url: selectedUrl.url,
    //         method: selectedUrl.method
    //     }
    //     await api.scheduleTestForCustomEndpoints(apiKeyInfo,func.timNow(),false,testsList,"akto_gpt_test",-1,-1)
    //     func.setToast(true,false,"Triggered tests successfully!")
    // }

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
        <Toast content={toastConfig.message} error={toastConfig.isError} onDismiss={disableToast} duration={2000} />
    ) : null;

    return (
        <div className="dashboard">
        <Frame>
            <Outlet />
            {toastMarkup}
            {/* <div>
                <Modal large open={isGptScreenActive} onClose={()=> setIsGptScreenActive(false)} title="Akto GPT">
                    <Modal.Section flush>
                        <AktoGptLayout prompts={prompts} apiCollectionId={apiCollectionId} closeModal={()=> setIsGptScreenActive(false)} />
                    </Modal.Section>
                </Modal>
            </div> */}
        </Frame>
        </div>
    )
}

export default Dashboard