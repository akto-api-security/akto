import {TopBar, Icon, Text, Button} from '@shopify/polaris';
import {NotificationMajor} from '@shopify/polaris-icons';
import { useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import Store from '../../store';

export default function Header() {
    const [isUserMenuOpen, setIsUserMenuOpen] = useState(false);
    const [isSecondaryMenuOpen, setIsSecondaryMenuOpen] = useState(false);
    
    const storeAccessToken = Store(state => state.storeAccessToken)
    const navigate = useNavigate()

    const toggleIsUserMenuOpen = useCallback(
        () => setIsUserMenuOpen((isUserMenuOpen) => !isUserMenuOpen),
        [],
      );
    
    const toggleIsSecondaryMenuOpen = useCallback(
        () => setIsSecondaryMenuOpen((isSecondaryMenuOpen) => !isSecondaryMenuOpen),
        [],
    );

    const handleLogOut = () => {
        storeAccessToken(null)
        localStorage.removeItem("access_token")
        navigate("/login")
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
                    items: [{content: 'Community forums'}],
                },
            ]}
        />
    );

    const topBarMarkup = (
        <TopBar
            showNavigationToggle
            userMenu={userMenuMarkup}
            secondaryMenu={secondaryMenuMarkup}
            searchField={searchFieldMarkup}
        />
    );

    return (
        topBarMarkup
    );
}