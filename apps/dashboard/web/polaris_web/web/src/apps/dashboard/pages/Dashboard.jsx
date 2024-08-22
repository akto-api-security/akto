import { Outlet, useLocation, useNavigate } from "react-router-dom"
import { history } from "@/util/history";
import Store from "../store";
import homeFunctions from "./home/module";
import { useEffect, useState } from "react";
import { Frame, Toast } from "@shopify/polaris";
import "./dashboard.css"
import func from "@/util/func"
import transform from "./testing/transform";
import PersistStore from "../../main/PersistStore";
import LocalStore from "../../main/LocalStorageStore";
import ConfirmationModal from "../components/shared/ConfirmationModal";
import homeRequests from "./home/api";

function Dashboard() {

    const location = useLocation();
    history.location = location
    history.navigate = useNavigate();
    const setAllCollections = PersistStore(state => state.setAllCollections)
    const setCollectionsMap = PersistStore(state => state.setCollectionsMap)
    const setHostNameMap = PersistStore(state => state.setHostNameMap)

    const allCollections = PersistStore(state => state.allCollections)
    const collectionsMap = PersistStore(state => state.collectionsMap)

    const subCategoryMap = LocalStore(state => state.subCategoryMap)
    const [eventForUser, setEventForUser] = useState({})
    
    const sendEventOnLogin = LocalStore(state => state.sendEventOnLogin)
    const setSendEventOnLogin = LocalStore(state => state.setSendEventOnLogin)
    const fetchAllCollections = async () => {
        let apiCollections = await homeFunctions.getAllCollections()
        const allCollectionsMap = func.mapCollectionIdToName(apiCollections)
        const allHostNameMap = func.mapCollectionIdToHostName(apiCollections)
        setHostNameMap(allHostNameMap)
        setCollectionsMap(allCollectionsMap)
        setAllCollections(apiCollections)
    }

    const fetchMetadata = async () => {
        await transform.setTestMetadata();
    };

    const getEventForIntercom = async() => {
        let resp = await homeRequests.getEventForIntercom();
        if(resp !== null){
            setEventForUser(resp)
        }
    }

    useEffect(() => {
        if((allCollections && allCollections.length === 0) || (Object.keys(collectionsMap).length === 0)){
            fetchAllCollections()
        }
        if (!subCategoryMap || (Object.keys(subCategoryMap).length === 0)) {
            fetchMetadata();
        }
        if(window.Beamer){
            window.Beamer.init();
        }
        if(window?.Intercom){
            if(!sendEventOnLogin){
                setSendEventOnLogin(true)
                getEventForIntercom()
                if(Object.keys(eventForUser).length > 0){
                    window?.Intercom("trackEvent","metrics", eventForUser)
                }
            }
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

    const confirmationModalConfig = Store(state => state.confirmationModalConfig)

    const ConfirmationModalMarkup = <ConfirmationModal
        modalContent={confirmationModalConfig.modalContent}
        primaryActionContent={confirmationModalConfig.primaryActionContent}
        primaryAction={confirmationModalConfig.primaryAction}
    />

    return (
        <div className="dashboard">
        <Frame>
            <Outlet />
            {toastMarkup}
            {ConfirmationModalMarkup}
        </Frame>
        </div>
    )
}

export default Dashboard