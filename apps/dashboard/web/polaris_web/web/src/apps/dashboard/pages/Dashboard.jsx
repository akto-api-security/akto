import { Outlet, useLocation, useNavigate} from "react-router-dom"
import { history } from "@/util/history";
import Store from "../store";
import homeFunctions from "./home/module";
import { useEffect, useState, useRef} from "react";
import { Frame, Toast, VerticalStack, Banner, Button, Text } from "@shopify/polaris";
import "./dashboard.css"
import func from "@/util/func"
import transform from "./testing/transform";
import PersistStore from "../../main/PersistStore";
import LocalStore, { localStorePersistSync } from "../../main/LocalStorageStore";
import ConfirmationModal from "../components/shared/ConfirmationModal";
import AlertsBanner from "./AlertsBanner";
import dashboardFunc from "./transform";
import homeRequests from "./home/api";
import WelcomeBackDetailsModal from "../components/WelcomeBackDetailsModal";
import useTable from "../components/tables/TableContext";
import threatDetectionRequests from "./threat_detection/api";
import SessionStore from "../../main/SessionStore";
import { updateThreatFiltersStore } from "./threat_detection/utils/threatFilters";

function Dashboard() {

    const location = useLocation();
    history.location = location
    history.navigate = useNavigate();
    const setAllCollections = PersistStore(state => state.setAllCollections)
    const setCollectionsMap = PersistStore(state => state.setCollectionsMap)
    const setTagCollectionsMap = PersistStore(state => state.setTagCollectionsMap)
    const setHostNameMap = PersistStore(state => state.setHostNameMap)
    const threatFiltersMap = SessionStore(state => state.threatFiltersMap);

    const { selectItems } = useTable()

    const navigate = useNavigate();

    const allCollections = PersistStore(state => state.allCollections)
    const collectionsMap = PersistStore(state => state.collectionsMap)

    const subCategoryMap = LocalStore(state => state.subCategoryMap)
    const [eventForUser, setEventForUser] = useState({})
    
    const sendEventOnLogin = LocalStore(state => state.sendEventOnLogin)
    const setSendEventOnLogin = LocalStore(state => state.setSendEventOnLogin)
    const fetchAllCollections = async () => {
        let apiCollections = []
        if(allCollections && allCollections.length > 0){
            apiCollections = allCollections
        }else{
            apiCollections = await homeFunctions.getAllCollections()
            setAllCollections(apiCollections)
        }
        apiCollections = apiCollections.filter((x) => x?.deactivated !== true)
        const allCollectionsMap = func.mapCollectionIdToName(apiCollections)
        const allHostNameMap = func.mapCollectionIdToHostName(apiCollections)
        const allTagCollectionsMap = func.mapCollectionIdsToTagName(apiCollections)
        setHostNameMap(allHostNameMap)
        setCollectionsMap(allCollectionsMap)
        setTagCollectionsMap(allTagCollectionsMap)
    }
    const trafficAlerts = PersistStore(state => state.trafficAlerts)
    const setTrafficAlerts = PersistStore(state => state.setTrafficAlerts)
    const [displayItems, setDisplayItems] = useState([])

    const timeoutRef = useRef(null);
    const inactivityTime = 10 * 60 * 1000;

    const fetchMetadata = async () => {
        await transform.setTestMetadata();
    };

    const getEventForIntercom = async() => {
        let resp = await homeRequests.getEventForIntercom();
        if(resp !== null){
            setEventForUser(resp)
        }
    }

    const fetchFilterYamlTemplates = () => {
        threatDetectionRequests.fetchFilterYamlTemplate().then((res) => {
            updateThreatFiltersStore(res?.templates || [])
        })
    }

    useEffect(() => {
        if(trafficAlerts == null && window.USER_NAME.length > 0 && window.USER_NAME.includes('akto.io')){
            homeRequests.getTrafficAlerts().then((resp) => {
                setDisplayItems(dashboardFunc.sortAndFilterAlerts(resp))
                setTrafficAlerts(resp)
            })
        }else{
            setDisplayItems((prev) => {
                return dashboardFunc.sortAndFilterAlerts(trafficAlerts)
            })
        }
    },[trafficAlerts.length])

    useEffect(() => {
        if(((allCollections && allCollections.length === 0) || (Object.keys(collectionsMap).length === 0)) && location.pathname !== "/dashboard/observe/inventory"){
            fetchAllCollections()
        }
        if (!subCategoryMap || (Object.keys(subCategoryMap).length === 0)) {
            fetchMetadata();
        }
        if(Object.keys(threatFiltersMap).length === 0 && window?.STIGG_FEATURE_WISE_ALLOWED?.THREAT_DETECTION?.isGranted){
            fetchFilterYamlTemplates()
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

        Object.keys(sessionStorage).forEach((key) => {
            if (key === "undefined" || key === "persistedStore") {
                sessionStorage.removeItem(key);
            }
        });
        Object.keys(localStorage).forEach((key) => {
            if (key === "undefined") {
                localStorage.removeItem(key);
            }
        });

        const cleanUpLocalStorePersistSync = localStorePersistSync(LocalStore);

        return () => {
            cleanUpLocalStorePersistSync();
        };
    }, [])

    useEffect(() => {
        selectItems([])
    },[location.pathname])

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
    const handleOnDismiss = async(index) => {
        let alert = displayItems[index];
        let newTrafficFilters = []
        trafficAlerts.forEach((a) => {
            if(func.deepComparison(a, alert)){
                a.lastDismissed = func.timeNow()
            }
            newTrafficFilters.push(a);
        })
        setDisplayItems(dashboardFunc.sortAndFilterAlerts(newTrafficFilters));
        setTrafficAlerts(newTrafficFilters)
        alert.lastDismissed = func.timeNow();
        await homeRequests.markAlertAsDismissed(alert);
    }

    const refreshFunc = () => {
        if(document.visibilityState === 'hidden'){
            PersistStore.getState().resetAll();
            LocalStore.getState().resetStore();
            navigate("/dashboard/observe/inventory")
            window.location.reload();
        }
    }

    const initializeTimer = () => {
        if (timeoutRef.current) {
            clearTimeout(timeoutRef.current); // Clear existing timeout to prevent duplicates
          }
          timeoutRef.current = setTimeout(refreshFunc, inactivityTime);
    }

    const handleVisibilityChange = () => {
        if (document.visibilityState === 'hidden') {
          initializeTimer(); 
        } else {
          clearTimeout(timeoutRef.current);
        }
    };

    useEffect(() => {
        initializeTimer();
        document.addEventListener('visibilitychange', handleVisibilityChange);
        return () => {
            document.removeEventListener('visibilitychange', handleVisibilityChange);
            clearTimeout(timeoutRef.current);
        };

    },[])

    const shouldShowWelcomeBackModal = window.IS_SAAS === "true" && window?.USER_NAME?.length > 0 && (window?.USER_FULL_NAME?.length === 0 || (window?.USER_ROLE === 'ADMIN' && window?.ORGANIZATION_NAME?.length === 0))

    return (
        <div className="dashboard">
        <Frame>
            <Outlet />
            {shouldShowWelcomeBackModal && <WelcomeBackDetailsModal isAdmin={window.USER_ROLE === 'ADMIN'} />}
            {toastMarkup}
            {ConfirmationModalMarkup}
            {displayItems.length > 0 ? <div className="alerts-banner">
                    <VerticalStack gap={"2"}>
                        {displayItems.map((alert, index) => {
                            return(
                                <AlertsBanner key={index} 
                                    type={dashboardFunc.getAlertMessageFromType(alert.alertType)} 
                                    content={dashboardFunc.replaceEpochWithFormattedDate(alert.content)}
                                    severity={dashboardFunc.getBannerStatus(alert.severity)}
                                    onDismiss= {handleOnDismiss}
                                    index={index}
                                />
                            )
                        })}
                    </VerticalStack>
            </div> : null}
            {func.checkLocal() && !(location.pathname.includes("test-editor") || location.pathname.includes("settings") || location.pathname.includes("onboarding") || location.pathname.includes("summary")) ?<div className="call-banner" style={{marginBottom: "1rem"}}>
                <Banner hideIcon={true}>
                    <Text variant="headingMd">Need a 1:1 experience?</Text>
                    <Button plain monochrome onClick={() => {
                        window.open("https://akto.io/api-security-demo", "_blank")
                    }}><Text variant="bodyMd">Book a call</Text></Button>
                </Banner>
            </div> : null}
            {window.TRIAL_MSG && !(location.pathname.includes("test-editor") || location.pathname.includes("settings") || location.pathname.includes("onboarding") || location.pathname.includes("summary")) ?<div className="call-banner">
                <Banner hideIcon={true}>
                    <Text variant="bodyMd">{window.TRIAL_MSG}</Text>
                </Banner>
            </div> : null}
            {location.pathname.includes("agent-team") && window.AGENTTRIAL_MSG ? (
                <div className="call-banner">
                    <Banner hideIcon={true}>
                        <Text variant="bodyMd">{window.AGENTTRIAL_MSG}</Text>
                    </Banner>
                </div>
            ) : null}
            {location.pathname.startsWith("/dashboard/protection/") && window.PROTECTIONTRIAL_MSG ? (
                <div className="call-banner">
                    <Banner hideIcon={true}>
                        <Text variant="bodyMd">{window.PROTECTIONTRIAL_MSG}</Text>
                    </Banner>
                </div>
            ) : null}
        </Frame>
        </div>
    )
}

export default Dashboard
