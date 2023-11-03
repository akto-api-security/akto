import { TopBar, Icon, Text, Tooltip, Button, ActionList, Modal, TextField } from '@shopify/polaris';
import { NotificationMajor, CircleChevronRightMinor, CircleChevronLeftMinor, StatusActiveMajor } from '@shopify/polaris-icons';
import { useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import Store from '../../../store';
import PersistStore from '../../../../main/PersistStore';
import './Headers.css'
import api from '../../../../signup/api';
import func from '@/util/func';

export default function Header() {
    const [isUserMenuOpen, setIsUserMenuOpen] = useState(false);
    const [isSecondaryMenuOpen, setIsSecondaryMenuOpen] = useState(false);
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
    const resetAll = PersistStore(state => state.resetAll)

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

    const toggleIsSecondaryMenuOpen = useCallback(
        () => setIsSecondaryMenuOpen((isSecondaryMenuOpen) => !isSecondaryMenuOpen),
        [],
    );

    const handleLogOut = async () => {
        storeAccessToken(null)
        resetAll();
        await api.logout()
        navigate("/login")
    }

    const handleSwitchUI = async () => {
        resetAll();
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
                resetAll();
                window.location.href = '/dashboard/observe/inventory'
            }
        }
    })

    function createNewAccount() {
        api.saveToAccount(newAccount).then(resp => {
          setShowCreateAccount(false)
          setNewAccount('')
          resetAll();
          window.location.href="/dashboard/onboarding"
        })
    }      

    const userMenuMarkup = (
        <TopBar.UserMenu
            actions={[
                {
                    items: accountsItems
                },
                {
                    items: [
                        { id: "create_account", content: 'Create account', onAction: () => setShowCreateAccount(true)},
                        { id: "manage", content: 'Manage account' },
                        { id: "log-out", content: 'Log out', onAction: handleLogOut }
                    ],
                },
                {
                    items: [
                        { id: "switch-ui", content: 'Switch to legacy', onAction: handleSwitchUI },
                        { content: 'Documentation', onAction: () => { window.open("https://docs.akto.io/readme") } },
                        { content: 'Tutorials', onAction: () => { window.open("https://www.youtube.com/@aktodotio") } },
                        { content: 'Changelog', onAction: () => { window.open("https://app.getbeamer.com/akto/en") } },
                        { content: 'Discord Support', onAction: () => { window.open("https://discord.com/invite/Wpc6xVME4s") } },
                        { content: 'Star On Github', onAction: () => { window.open("https://github.com/akto-api-security/akto") } }
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
        return {
            content: item.content,
            onAction: () => handleNavigateSearch(item.url),
        }
    })

    const searchResultsMarkup = (
        <ActionList
            items={searchItems.filter(x => x.content.toLowerCase().includes(searchValue.toLowerCase()))}
        />
    );

    const searchFieldMarkup = (
        <TopBar.SearchField
            placeholder="Search for pages and API collections"
            showFocusBorder
            onChange={handleSearchChange}
            value={searchValue}
        />
    );

    const secondaryMenuMarkup = (
        <TopBar.Menu
            activatorContent={
                <span>
                    <Icon source={NotificationMajor} />
                    <Text as="span" visuallyHidden>
                        Secondary menu
                    </Text>
                </span>
            }
            open={isSecondaryMenuOpen}
            onOpen={toggleIsSecondaryMenuOpen}
            onClose={toggleIsSecondaryMenuOpen}
            actions={[
                {
                    items: [{
                        prefix: <div style={{ marginLeft: '14px' }} id='beamer-btn'>Updates</div>
                    }],
                },
            ]}
        />
    );

    const topBarMarkup = (
        <div className='topbar'>
            <TopBar
                showNavigationToggle
                userMenu={userMenuMarkup}
                secondaryMenu={secondaryMenuMarkup}
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