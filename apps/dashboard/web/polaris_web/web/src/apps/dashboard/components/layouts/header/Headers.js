import { TopBar, Icon, Text, ActionList, Modal, TextField, HorizontalStack, Box, Avatar } from '@shopify/polaris';
import { NotificationMajor, CustomerPlusMajor, LogOutMinor, ReplaceMajor, NoteMinor, ResourcesMajor, UpdateInventoryMajor, PageMajor, DynamicSourceMajor } from '@shopify/polaris-icons';
import { useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import Store from '../../../store';
import PersistStore from '../../../../main/PersistStore';
import './Headers.css'
import api from '../../../../signup/api';
import func from '@/util/func';

export default function Header() {
    const [isUserMenuOpen, setIsUserMenuOpen] = useState(false);
    const [isSearchActive, setIsSearchActive] = useState(false);
    const [searchValue, setSearchValue] = useState('');
    const [newAccount, setNewAccount] = useState('')
    const [showCreateAccount, setShowCreateAccount] = useState(false)

    const navigate = useNavigate()

    const setLeftNavSelected = Store((state) => state.setLeftNavSelected)
    const leftNavCollapsed = Store((state) => state.leftNavCollapsed)
    const toggleLeftNavCollapsed = Store(state => state.toggleLeftNavCollapsed)
    const username = Store((state) => state.username)
    const storeAccessToken = PersistStore(state => state.storeAccessToken)
    const accounts = Store(state => state.accounts)
    const activeAccount = Store(state => state.activeAccount)

    const allRoutes = Store((state) => state.allRoutes)
    const allCollections = PersistStore((state) => state.allCollections)
    const searchItemsArr = func.getSearchItemsArr(allRoutes, allCollections)

    const handleLeftNavCollapse = () => {
        if (!leftNavCollapsed) {
            setLeftNavSelected('')
        }

        toggleLeftNavCollapsed()
    }

    const toggleIsUserMenuOpen = useCallback(
        () => setIsUserMenuOpen((isUserMenuOpen) => !isUserMenuOpen),
        [],
    );

    const handleLogOut = async () => {
        storeAccessToken(null)
        api.logout().then(res => {
            if(res.logoutUrl){
                window.location.href = res.logoutUrl
            } else {
                navigate("/login")
            }
        }).catch(err => {
            navigate("/");
        })
    }

    const handleSwitchUI = async () => {
        let currPath = window.location.pathname
        await api.updateAktoUIMode({ aktoUIMode: "VERSION_1" })
        if(currPath.includes("sensitive")){
            let arr = currPath.split("/") ;
            let last = arr.pop()
            if(last !== 'sensitive'){
                let path = arr.toString().replace(/,/g, '/')
                let id = arr.pop()
                if(id !== 'sensitive'){
                    path="/dashboard/observe/inventory/" + id + '/' + last
                }
                window.location.pathname=path
            }else{
                window.location.reload()
            }
        }else if(currPath.includes('testing')){
            let child = currPath.split("testing")[1].split("/")
            let lastStr = child.length > 1 ? child[1] : '' ;
            if(lastStr === 'issues'){
                window.location.pathname = "/dashboard/issues"
            }
            else if(lastStr === 'roles' || lastStr.includes('user')){
                lastStr = ''
            }
            window.location.pathname = "/dashboard/testing/" + lastStr
        }else{
            window.location.reload()
        }
    }

    
    const accountsItems = Object.keys(accounts).map(accountId => {
        return {
            id: accountId,
            content: (<div style={{ color: accountId === activeAccount.toString() ? "var(--akto-primary)" :  "var(--p-text)"  }}>{accounts[accountId]}</div>),
            onAction: async () => {
                await api.goToAccount(accountId)
                func.setToast(true, false, `Switched to account ${accounts[accountId]}`)
                window.location.href = '/dashboard/observe/inventory'
            }
        }
    })

    function createNewAccount() {
        api.saveToAccount(newAccount).then(resp => {
          setShowCreateAccount(false)
          setNewAccount('')

          window.location.href="/dashboard/onboarding"
        })
    }
    
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

    const userMenuMarkup = (
        <TopBar.UserMenu
            actions={[
                {
                    items: accountsItems
                },
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
                        { id: "switch-ui", content: <ContentWithIcon text={"Switch to legacy"} icon={ReplaceMajor} />, onAction: handleSwitchUI },
                        { content: <ContentWithIcon text={"Documentation"} icon={NoteMinor} />, onAction: () => { window.open("https://docs.akto.io/readme") } },
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

    const handleSearchResultsDismiss = useCallback(() => {
        setIsSearchActive(false);
        setSearchValue('');
    }, []);

    const handleSearchChange = useCallback((value) => {
        setSearchValue(value);
        setIsSearchActive(value.length > 0);
    }, []);

    const handleNavigateSearch = (url) => {
        navigate(url)
        handleSearchResultsDismiss()
    }

    const searchItems = searchItemsArr.map((item) => {
        const icon = item.type === 'page' ? PageMajor : DynamicSourceMajor;
        return {
            value: item.content,
            content: <ContentWithIcon text={item.content} icon={icon} />,
            onAction: () => handleNavigateSearch(item.url),
        }
    })

    const searchResultsMarkup = (
        <ActionList
            items={searchItems.filter(x => x.value.toLowerCase().includes(searchValue.toLowerCase()))}
        />
    );

    const searchFieldMarkup = (
        <TopBar.SearchField
            placeholder="Search for API collections"
            showFocusBorder
            onChange={handleSearchChange}
            value={searchValue}
        />
    );

    const topBarMarkup = (
        <div className='topbar'>
            <TopBar
                showNavigationToggle
                userMenu={userMenuMarkup}
                searchField={searchFieldMarkup}
                searchResultsVisible={isSearchActive}
                searchResults={searchResultsMarkup}
                onSearchResultsDismiss={handleSearchResultsDismiss}
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