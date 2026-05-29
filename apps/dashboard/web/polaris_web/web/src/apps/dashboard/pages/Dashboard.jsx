import { Outlet, useLocation, useNavigate} from "react-router-dom"
import { history } from "@/util/history";
import Store from "../store";
import homeFunctions from "./home/module";
import { useEffect, useState } from "react";
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

    const allCollections = PersistStore(state => state.allCollections)
    const collectionsMap = PersistStore(state => state.collectionsMap)

    const subCategoryMap = LocalStore(state => state.subCategoryMap)
    const [eventForUser, setEventForUser] = useState({})
    const [showNoAccessAlert, setShowNoAccessAlert] = useState(false)
    const [noAccessMessage, setNoAccessMessage] = useState("")

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

    // Monitor NO_ACCESS alert flag
    // Skip alert monitoring during onboarding since APIs may return 403 during setup
    useEffect(() => {
        selectItems([])
        if (location.pathname.includes('/onboarding')) {
            return;
        }

        const checkInterval = setInterval(() => {
            if (window.SHOW_NO_ACCESS_ALERT) {
                setShowNoAccessAlert(true);
                setNoAccessMessage(window.NO_ACCESS_ALERT_MESSAGE || "");
            }
        }, 100);

        return () => clearInterval(checkInterval);
    }, [location.pathname]);

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

    const shouldShowWelcomeBackModal = window.IS_SAAS === "true" && window?.USER_NAME?.length > 0 && (window?.USER_FULL_NAME?.length === 0 || (window?.USER_ROLE === 'ADMIN' && window?.ORGANIZATION_NAME?.length === 0))

    const isAskAiRoute = location.pathname.includes('/ask-ai')
    const isOnboardingRoute = location.pathname.includes('/onboarding')

    return (
        <div className={`dashboard ${isAskAiRoute ? 'ask-ai-route' : ''}`}>
        {showNoAccessAlert && !isOnboardingRoute && (
            <div style={{
                position: "fixed",
                top: "120px",
                left: "50%",
                transform: "translateX(-50%)",
                backgroundColor: "#FED7D7",
                border: "1px solid #FC8181",
                borderRadius: "4px",
                padding: "10px 16px",
                paddingRight: "40px",
                zIndex: 1000,
                maxWidth: "600px",
                boxShadow: "0 2px 8px rgba(0,0,0,0.15)"
            }}>
                <button onClick={() => {
                    setShowNoAccessAlert(false);
                    window.SHOW_NO_ACCESS_ALERT = false;
                }} style={{
                    position: "absolute",
                    top: "8px",
                    right: "8px",
                    background: "none",
                    border: "none",
                    cursor: "pointer",
                    fontSize: "20px",
                    color: "#AE191C",
                    padding: "0",
                    width: "24px",
                    height: "24px",
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center"
                }}>×</button>
                <Text variant="bodyMd" color="critical">{noAccessMessage}</Text>
            </div>
        )}
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
            {func.checkLocal() && !(location.pathname.includes("test-editor") || location.pathname.includes("settings") || location.pathname.includes("onboarding") || location.pathname.includes("summary") || location.pathname.includes("report")) ?<div className="call-banner" style={{marginBottom: "1rem"}}>
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
