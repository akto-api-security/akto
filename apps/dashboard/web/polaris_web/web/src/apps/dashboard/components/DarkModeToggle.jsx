import React from 'react';
import { Button } from '@shopify/polaris';
import LocalStore from '../../main/LocalStorageStore';

const DarkModeToggle = () => {
    const isDarkMode = LocalStore((state) => state.isDarkMode);
    const toggleDarkMode = LocalStore((state) => state.toggleDarkMode);

    const handleToggle = () => {
        toggleDarkMode();
        // Apply theme to document
        document.documentElement.setAttribute('data-theme', !isDarkMode ? 'dark' : 'light');
    };

    return (
        <Button
            plain
            monochrome
            onClick={handleToggle}
            >
            {isDarkMode ? 'Light Mode' : 'Dark Mode'}
        </Button>
    );
};

export default DarkModeToggle;
