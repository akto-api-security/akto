import { TopBar, Icon, Text, ActionList, Modal, TextField, HorizontalStack, Box, Avatar, VerticalStack, Button, Scrollable } from '@shopify/polaris';
import { NotificationMajor, CustomerPlusMajor, LogOutMinor, NoteMinor, ResourcesMajor, UpdateInventoryMajor, PhoneMajor, ChatMajor, SettingsMajor } from '@shopify/polaris-icons';
import { useState, useCallback, useMemo, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import Store from '../../../store';
import PersistStore from '../../../../main/PersistStore';
import './Headers.css'
import api from '../../../../signup/api';
import func from '@/util/func';
import SemiCircleProgress from '../../shared/SemiCircleProgress';
import { usePolling } from '../../../../main/PollingProvider';
import { debounce } from 'lodash';
import LocalStore from '../../../../main/LocalStorageStore';
import SessionStore from '../../../../main/SessionStore';
import IssuesStore from '../../../pages/issues/issuesStore';
import Dropdown from '../Dropdown';

function ContentWithIcon({icon,text, isAvatar= false}) {
    return(
        <HorizontalStack gap={2}>
            <Box width='20px'>
                {isAvatar ? <div className='reduce-size'><Avatar size="extraSmall" source={icon} /> </div>:
                <Icon source={icon} color="base" />}
            </Box>
            <Text>{text}</Text>
        </HorizontalStack>
    )
}

export default function Header() {
    const [isUserMenuOpen, setIsUserMenuOpen] = useState(false);
    const [searchValue, setSearchValue] = useState('');
    const [newAccount, setNewAccount] = useState('')
    const [showCreateAccount, setShowCreateAccount] = useState(false)
    const { currentTestsObj, clearPollingInterval } = usePolling();
    const navigate = useNavigate()

    const username = Store((state) => state.username)
    const resetAll = PersistStore(state => state.resetAll)
    const resetStore = LocalStore(state => state.resetStore)
    const resetSession  = SessionStore(state => state.resetStore)
    const resetFields = IssuesStore(state => state.resetStore)

    const dashboardCategory = PersistStore.getState().dashboardCategory;
    const setDashboardCategory = PersistStore.getState().setDashboardCategory

useEffect(() => {
        if (window.beamer_config) {
            const isOnPrem = window.DASHBOARD_MODE === 'ON_PREM';
            const isAgentic = dashboardCategory === 'Agentic Security';

            const productId = isAgentic
                ? (isOnPrem ? 'shUignSe80215' : 'ijUqfdSQ80078')
                : (isOnPrem ? 'rggteHBr72897' : 'cJtNevEq80216');

            const filterTag = isOnPrem ? 'onprem' : 'saas';

            if (window.beamer_config.product_id !== productId || window.beamer_config.filter !== filterTag) {
                window.beamer_config.product_id = productId;
                window.beamer_config.filter = filterTag;
                if (window.Beamer) {
                    window.Beamer.destroy();
                    window.Beamer.init();
                }
            }
        }
    }, [dashboardCategory]);


    const logoSrc = dashboardCategory === "Agentic Security" ? "/public/white_logo.svg" : "/public/akto_name_with_logo.svg";
    const stiggFeatures = window?.STIGG_FEATURE_WISE_ALLOWED || {};
    const agenticSecurityGranted =
        stiggFeatures?.SECURITY_TYPE_AGENTIC?.isGranted || true
    const mcpSecurityGranted =
        stiggFeatures?.MCP_SECURITY?.isGranted || true;
    const dastGranted = func.checkForFeatureSaas("AKTO_DAST")

    const disabledDashboardCategories = useMemo(() => {
        const disabled = [];
        if (mcpSecurityGranted === false) {
            disabled.push("MCP Security");
        }
        if (agenticSecurityGranted === false) {
            disabled.push("Agentic Security");
        }
        if (dastGranted === false) {
            disabled.push("DAST")
        }
        return disabled;
    }, [mcpSecurityGranted, agenticSecurityGranted]);

    const dropdownInitial = disabledDashboardCategories.includes(dashboardCategory)
        ? "API Security"
        : (dashboardCategory || "API Security");

    useEffect(() => {
        if (disabledDashboardCategories.includes(dashboardCategory) && dashboardCategory !== "API Security") {
            setDashboardCategory("API Security");
        }
    }, [dashboardCategory, disabledDashboardCategories, setDashboardCategory]);

    /* Search bar */
    //const allRoutes = Store((state) => state.allRoutes)
    const allCollections = PersistStore((state) => state.allCollections)
    const subCategoryMap = LocalStore(state => state.subCategoryMap)
    const searchItemsArr = useMemo(() => func.getSearchItemsArr(allCollections, subCategoryMap), [allCollections, subCategoryMap])
    const [filteredItemsArr, setFilteredItemsArr] = useState(searchItemsArr);
    
    useEffect(() => {
        setFilteredItemsArr(searchItemsArr);
    }, [searchItemsArr]);

    const debouncedSearch = useMemo(() => debounce(async (searchQuery) => {    
        if (searchQuery.length === 0) {
            setFilteredItemsArr(searchItemsArr);
        } else {
            const resultArr = searchItemsArr.filter((x) => x.content.toLowerCase().includes(searchQuery));
            setFilteredItemsArr(resultArr);
        }
    }, 500), [searchItemsArr]); 

    const handleSearchChange = useCallback((value) => {
        setSearchValue(value);
        debouncedSearch(value.toLowerCase());
    }, [debouncedSearch]);

    const handleNavigateSearch = useCallback((url) => {
            navigate(url);
            handleSearchChange('');  
    }, [navigate, handleSearchChange]);

    const searchResultSections = useMemo(() => func.getSearchResults(filteredItemsArr, handleNavigateSearch), [filteredItemsArr, handleNavigateSearch])

    const searchResultsMarkup = (
        <Scrollable key={searchValue} style={{maxHeight: '500px'}} shadow>
        <ActionList
            sections={searchResultSections}
        />
        </Scrollable>
    );

    const searchFieldMarkup = (
        <TopBar.SearchField
            placeholder="Search collections, tests and connectors"
            showFocusBorder
            onChange={handleSearchChange}
            value={searchValue}
        />
    );
    /* Search bar */


    const toggleIsUserMenuOpen = useCallback(
        () => setIsUserMenuOpen((isUserMenuOpen) => !isUserMenuOpen),
        [],
    );

    const handleLogOut = async () => { 
        clearPollingInterval()
        api.logout().then(res => {
            resetAll();
            resetStore() ;
            resetSession();
            resetFields();
            if(res.logoutUrl){
                window.location.href = res.logoutUrl
            } else {
                navigate("/login")
            }
        }).catch(err => {
            navigate("/");
        })
    }

    const handleDashboardChange = (value) => {
        PersistStore.getState().setAllCollections([]);
        PersistStore.getState().setCollectionsMap({});
        PersistStore.getState().setHostNameMap({});
        PersistStore.getState().setLastCalledSensitiveInfo(0);
        PersistStore.getState().setLastFetchedInfo({ lastRiskScoreInfo: 0, lastSensitiveInfo: 0 });
        LocalStore.getState().setCategoryMap({}); 
        LocalStore.getState().setSubCategoryMap({});
        SessionStore.getState().setThreatFiltersMap({});
        setDashboardCategory(value);
        window.location.reload();
        window.location.href("/dashboard/observe/inventory")
    }

    function createNewAccount() {
        api.saveToAccount(newAccount).then(resp => {
          setShowCreateAccount(false)
          setNewAccount('')
          resetAll();
          resetStore();
          window.location.href="/dashboard/onboarding"
        })
    }

    const getColorForIcon = () => {
        switch (window.DASHBOARD_MODE){
            case "ON_PREM":
                return "onprem_icon";
            case "LOCAL_DEPLOY":
                if(window.IS_SAAS !== "true") 
                    return "local_icon"
                return "";
            default:
                return ""
        }
    }

    const userMenuMarkup = (
        <TopBar.UserMenu
            actions={[
                {
                    items: [
                        (window.IS_SAAS !== "true" && (window?.DASHBOARD_MODE === 'LOCAL_DEPLOY' || window?.DASHBOARD_MODE === "ON_PREM")) ? {} :
                        { id: "create_account", content: <ContentWithIcon icon={CustomerPlusMajor} text={"Create account"} />, onAction: () => setShowCreateAccount(true)},
                        // { id: "manage", content: 'Manage account' },
                        { id: "log-out", content: <ContentWithIcon icon={LogOutMinor} text={"Logout"} /> , onAction: handleLogOut }
                    ],
                },
                {
                    items: [
                        { content: <ContentWithIcon text={"Documentation"} icon={NoteMinor} />, onAction: () => { window.open("https://docs.akto.io/readme") } },
                        { content: <ContentWithIcon text={"Book a call"} icon={PhoneMajor}/>, onAction: () => { window.open("https://akto.io/api-security-demo") } },
                        { content: <ContentWithIcon text={"Contact Us"} icon={ChatMajor}/>, onAction: () => { 
                            if (window?.Intercom) {
                                window.Intercom('show');
                            }
                        } },
                        { content: <ContentWithIcon text={"Tutorials"} icon={ResourcesMajor}/>, onAction: () => { window.open("https://www.youtube.com/@aktodotio") } },
                        { content: <ContentWithIcon icon={UpdateInventoryMajor} text={"Changelog"} />, onAction: () => { window.open("https://app.getbeamer.com/akto/en") } },
                        { content: <ContentWithIcon icon="/public/discord.svg" text={"Discord Support"} isAvatar={true}/>, onAction: () => { window.open("https://discord.com/invite/Wpc6xVME4s") } },
                        { content: <ContentWithIcon icon="/public/github_icon.svg" text={"Star On Github"} isAvatar={true}/>, onAction: () => { window.open("https://github.com/akto-api-security/akto") } }
                    ],
                },
            ]}
            initials={func.initials(username)}
            open={isUserMenuOpen}
            onToggle={toggleIsUserMenuOpen}
        />
    );

    const handleTestingNavigate = () => {
        let navUrl = "/dashboard/testing"
        if(currentTestsObj.testRunsArr.length === 1){
            navUrl = navUrl + "/" + currentTestsObj.testRunsArr[0].testingRunId
        }
        navigate(navUrl)
    }

    const progress = useMemo(() => {
        return currentTestsObj.totalTestsInitiated === 0 ? 0 : Math.floor((currentTestsObj.totalTestsCompleted * 100) / currentTestsObj.totalTestsInitiated);
    }, [currentTestsObj.totalTestsCompleted, currentTestsObj.totalTestsInitiated]);


    const secondaryMenuMarkup = (
        <HorizontalStack gap="1">
            {(Object.keys(currentTestsObj).length > 0 && currentTestsObj?.testRunsArr?.length !== 0 && currentTestsObj?.totalTestsCompleted > 0) ? 
            <HorizontalStack gap="1">
                <Button plain monochrome onClick={() => {handleTestingNavigate()}}>
                 <SemiCircleProgress key={"progress"} progress={Math.min(progress, 100)} size={60} height={55} width={75}/>
                </Button>
                <VerticalStack gap="1">
                    <Text fontWeight="medium">Test run status</Text>
                    <Text color="subdued" variant="bodySm">{`${currentTestsObj.totalTestsQueued} tests queued`}</Text>
                </VerticalStack>
            </HorizontalStack> : null}
            <TopBar.Menu
                activatorContent={
                    <span id="beamer-btn" className={getColorForIcon()}>
                        <Icon source={NotificationMajor}/> 
                    </span>
                }
                actions={[]}
            />
            <TopBar.Menu
                activatorContent={
                    <Button plain monochrome icon={SettingsMajor} onClick={() => navigate("/dashboard/settings/about")} />
                }
            />
        </HorizontalStack>
    );

    const topBarMarkup = (
        <div className='topbar'>
            <TopBar
                showNavigationToggle
                userMenu={userMenuMarkup}
                contextControl={
                    <Box paddingInlineStart={3} paddingInlineEnd={3}>
                        <HorizontalStack gap={4} wrap={false}>
                            <div style={{ cursor: 'pointer' }} onClick={() => window.location.href = "/dashboard/observe/inventory"} className='logo'>
                                <img src={logoSrc} alt="Akto Logo" style={{ maxWidth: '78px' }} />
                            </div>

                            <Box minWidth='170px'>
                                <Dropdown
                                    menuItems={[
                                        { value: "API Security", label: "API Security", id: "api-security" },
                                        { value: "Agentic Security", label: "Agentic Security", id: "agentic-security" },
                                        { value: "DAST", label: "DAST", id: "dast" },
                                    ]}
                                    initial={dropdownInitial}
                                    selected={(val) => handleDashboardChange(val)}
                                    disabledOptions={disabledDashboardCategories}
                                />
                            </Box>
                        </HorizontalStack>
                    </Box>
                }
                searchField={searchFieldMarkup}
                searchResultsVisible={searchValue.length > 0}
                searchResults={searchResultsMarkup}
                onSearchResultsDismiss={() =>handleSearchChange('')}
                secondaryMenu={secondaryMenuMarkup}
            />
            <Modal
                open={showCreateAccount}
                onClose={() => setShowCreateAccount(false)}
                title="Create new account"
                primaryAction={{
                    content: 'Create',
                    onAction: createNewAccount,
                }}
            >
                <Modal.Section>

                    <TextField
                        id="create-account-name"
                        label="Name"
                        helpText="Enter name for new account"
                        value={newAccount}
                        onChange={(input) => setNewAccount(input)}
                        autoComplete="off"
                        maxLength="24"
                       
                        autoFocus
                    />


                </Modal.Section>
            </Modal>
        </div>
    );

    return (
        topBarMarkup
    );
}