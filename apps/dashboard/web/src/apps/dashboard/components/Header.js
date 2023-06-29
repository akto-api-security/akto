import {TopBar, ActionList, Icon, Frame, Text} from '@shopify/polaris';
import {ArrowLeftMinor, NotificationMajor} from '@shopify/polaris-icons';
import { useState, useCallback } from 'react';


export default function Header() {
    const [isUserMenuOpen, setIsUserMenuOpen] = useState(false);
    const [isSecondaryMenuOpen, setIsSecondaryMenuOpen] = useState(false);

    const toggleIsUserMenuOpen = useCallback(
        () => setIsUserMenuOpen((isUserMenuOpen) => !isUserMenuOpen),
        [],
      );
    
    const toggleIsSecondaryMenuOpen = useCallback(
        () => setIsSecondaryMenuOpen((isSecondaryMenuOpen) => !isSecondaryMenuOpen),
        [],
    );
    const logo = {
        width: 124,
        topBarSource:
        '/public/akto_name_with_logo.svg',
        url: '#',
        accessibilityLabel: 'Jaded Pixel',
    };

    const userMenuMarkup = (
        <TopBar.UserMenu
            actions={[
                {
                    items: [{content: 'Back', icon: ArrowLeftMinor}],
                },
                {
                    items: [{content: 'Community forums'}],
                },
            ]}
            name=""
            detail=""
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
        <div style={{height: '0px'}}>
            <Frame topBar={topBarMarkup} logo={logo} />
        </div>
    );
}