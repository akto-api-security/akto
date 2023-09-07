import { Outlet, useLocation, useNavigate } from "react-router-dom"
import { history } from "@/util/history";
import Store from "../store";
import homeFunctions from "./home/module";
import { useEffect } from "react";
import { Frame, Toast } from "@shopify/polaris";
import "./dashboard.css"
import func from "@/util/func"
import transform from "./testing/transform";

function Dashboard() {

    const location = useLocation();
    history.location = location
    history.navigate = useNavigate();
    const navigate = useNavigate()

    const setAllCollections = Store(state => state.setAllCollections)
    const setCollectionsMap = Store(state => state.setCollectionsMap)

    const fetchAllCollections = async () => {
        let apiCollections = await homeFunctions.getAllCollections()
        const allCollectionsMap = func.mapCollectionIdToName(apiCollections)
        setCollectionsMap(allCollectionsMap)
        setAllCollections(apiCollections)
    }

    useEffect(() => {
        fetchAllCollections()
        transform.setTestMetadata();
        if(location.hash?.length > 0){
            let newPath = location.pathname
            if(location.hash.includes("Data")){
                newPath = '/dashboard/observe/sensitive'
            }
            else if(newPath.includes("settings")){
                newPath = newPath + "/" + location.hash.split("#")[1]
            }
            navigate(newPath)
        }
    }, [])

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
        </Frame>
        </div>
    )
}

export default Dashboard