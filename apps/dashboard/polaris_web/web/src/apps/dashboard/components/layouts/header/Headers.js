import {TopBar, Icon, Text, Tooltip, Button} from '@shopify/polaris';
import {NotificationMajor, CircleChevronRightMinor,CircleChevronLeftMinor} from '@shopify/polaris-icons';
import { useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import Store from '../../../store';
import PersistStore from '../../../../main/PersistStore';
import './Headers.css'
import api from '../../../../signup/api';
import func from '../../../../../util/func';

export default function Header() {
    const [isUserMenuOpen, setIsUserMenuOpen] = useState(false);
    const [isSecondaryMenuOpen, setIsSecondaryMenuOpen] = useState(false);
    
    const navigate = useNavigate()

    const setLeftNavSelected = Store((state) => state.setLeftNavSelected)
    const leftNavCollapsed = Store((state) => state.leftNavCollapsed)
    const toggleLeftNavCollapsed = Store(state => state.toggleLeftNavCollapsed)
    const username = Store((state) => state.username)
    const storeAccessToken = PersistStore(state => state.storeAccessToken)

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
        await api.logout()
        navigate("/login")  
    }

    const userMenuMarkup = (
        <TopBar.UserMenu
            actions={[
                {
                    items: [{id: "manage", content: 'Manage Account'}, {id: "log-out", content: 'Log out', onAction: handleLogOut}],
                },
                {
                    items: [{content: 'Documentation'},{content: 'Tutorials'},{content: 'Changelog'},{content: 'Discord Support'},{content: 'Star On Github'}],
                },
            ]}
            initials={func.initials(username)}
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

    const topBarMarkup = (
        <div className='topbar'>
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