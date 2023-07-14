import {TopBar, Icon, Text, Tooltip, Button} from '@shopify/polaris';
import {NotificationMajor, CircleChevronRightMinor,CircleChevronLeftMinor} from '@shopify/polaris-icons';
import { useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import Store from '../../../store';
import './Headers.css'
import api from '../../../../signup/api';

export default function Header() {
    const [isUserMenuOpen, setIsUserMenuOpen] = useState(false);
    const [isSecondaryMenuOpen, setIsSecondaryMenuOpen] = useState(false);
    
    const storeAccessToken = Store(state => state.storeAccessToken)
    const navigate = useNavigate()
    let hideFullNav = Store((state) => state.hideFullNav)
    const toggleNavbar = Store(state => state.toggleLeftNav)

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
        await api.logout()
        navigate("/login")  
    }

    const toggleLeftBar = () =>{
        hideFullNav = !hideFullNav
        toggleNavbar(hideFullNav)
    }

    const userMenuMarkup = (
        <TopBar.UserMenu
            actions={[
                {
                    items: [{content: 'Manage Account'}, {content: 'Log out', onAction: handleLogOut}],
                },
                {
                    items: [{content: 'Documentation'},{content: 'Tutorials'},{content: 'Changelog'},{content: 'Discord Support'},{content: 'Star On Github'}],
                },
            ]}
            initials="AK"
            open={isUserMenuOpen}
            onToggle={toggleIsUserMenuOpen}
        />
    );

    const searchFieldMarkup = (
        <TopBar.SearchField
            placeholder="Search"
            showFocusBorder
        />
    );

    const secondaryMenuMarkup = (
        <TopBar.Menu 
            activatorContent={
                <span>
                <Icon source={NotificationMajor}/>
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
                        prefix: <div style={{marginLeft: '14px'}} id='beamer-btn'>Updates</div>
                    }],
                },
            ]}
        />
    );

    let icon = hideFullNav ? CircleChevronRightMinor : CircleChevronLeftMinor

    const topBarMarkup = (
        <div className='topbar'>
            <div className='collapse_btn' onClick={toggleLeftBar}>
                <Tooltip content={hideFullNav ? 'Show Navbar' : 'Hide Navbar'}>
                    <Icon source= {hideFullNav ? CircleChevronRightMinor : CircleChevronLeftMinor }/>
                </Tooltip>
            </div>
            <TopBar
                showNavigationToggle
                userMenu={userMenuMarkup}
                secondaryMenu={secondaryMenuMarkup}
                searchField={searchFieldMarkup}
            />
        </div>
    );

    return (
        topBarMarkup
    );
}