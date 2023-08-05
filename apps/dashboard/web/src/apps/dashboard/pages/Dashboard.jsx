import { Outlet, useLocation, useNavigate } from "react-router-dom"
import { history } from "@/util/history";
import Store from "../store";
import ObserveStore from "../pages/observe/observeStore"
import homeFunctions from "./home/module";
import { useEffect, useState } from "react";
import { Avatar, Button, Frame, HorizontalStack, Toast } from "@shopify/polaris";
import "./dashboard.css"
import settingRequests from "./settings/api";
import dashboardFunc from "./transform"

function Dashboard() {

    const location = useLocation();
    history.location = location
    history.navigate = useNavigate();

    const setAllCollections = Store(state => state.setAllCollections)
    const isInsideCollection = ObserveStore(state => state.inventoryFlyout)
    const filteredEndpoints = ObserveStore(state => state.filteredItems)
    const sampleData = ObserveStore(state => state.samples)

    const fetchAllCollections = async () => {
        let apiCollections = await homeFunctions.getAllCollections()
        setAllCollections(apiCollections)
    }

    const [isGptActive, setIsGptActive] = useState(false)
    const [prompts, setPrompts] = useState([])

    useEffect(() => {
        fetchAllCollections()
    }, [])

    const checkGptActive = async(path) => {
        if(path.includes('observe/inventory/')){
            const apiCollectionId = Number(path.split("inventory/")[1])
            if(apiCollectionId){
                await settingRequests.fetchAktoGptConfig(apiCollectionId).then((resp)=>{
                    if(resp.currentState[0].state === "ENABLED"){
                        setIsGptActive(true)
                    }
                })

                let requestObj = {}
                if(isInsideCollection){
                    requestObj = {
                        key: "PARAMETER",
                        jsonStr: sampleData[0],
                        apiCollectionId: apiCollectionId
                    }
                }else{
                    requestObj = {
                        key: "Collection",
                        filteredItems: filteredEndpoints,
                        apiCollectionId: apiCollectionId
                    }
                }

                const activePrompts = dashboardFunc.getPrompts(requestObj)
                setPrompts(activePrompts)
            }
        }else if(path.includes('observe/sensitive')){
            setIsGptActive(true)
        }
    }

    useEffect(()=> {
        setIsGptActive(false)
        checkGptActive(location.pathname)
    },[location])

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
                <Button removeUnderline plain monochrome>
                    <HorizontalStack gap="1">
                        Ask AktoGpt
                        <Avatar customer size="small" name="gpt" source="/public/gpt_logo.svg" />
                    </HorizontalStack>
                </Button>
            </div> : null}
            <Outlet />
            {toastMarkup}
        </Frame>
        </div>
    )
}

export default Dashboard