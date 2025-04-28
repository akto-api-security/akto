import { Button, Frame, Icon, Text, Box, TopBar, HorizontalStack } from "@shopify/polaris"
import { CancelMajor, SettingsMinor } from '@shopify/polaris-icons';
import { Outlet, useNavigate } from "react-router-dom"
import './settings.css'
import SettingsLeftNav from "./nav/SettingsLeftNav";
import PersistStore from "../../../main/PersistStore";
import { useEffect } from "react";

function SettingsHeader() {
    const navigate = useNavigate();
    const setActive = PersistStore(state => state.setActive)

    const handleSettingsClose = () => {
        // Go back to previous page instead of hardcoded destination
        navigate(-1);
        setActive('active')
    }

    const buttonComp = (
        <div className="header-css">
            <HorizontalStack gap="2">
                <Box>
                    <Icon source={SettingsMinor}/>
                </Box>
                <Text variant="headingMd" as="h4">Settings</Text>
            </HorizontalStack>
            <Button icon={CancelMajor} onClick={handleSettingsClose} />
        </div>
    )

    return (
        <TopBar secondaryMenu={buttonComp} />
    )
}

const Settings = () => {
    const navigate = useNavigate();
    const setActive = PersistStore(state => state.setActive)

    const handleSettingsClose = () => {
        // Go back to previous page instead of hardcoded destination
        navigate(-1);
        setActive('active')
    }

    useEffect(() => {
        const handleEscKey = (event) => {
            if (event.key === "Escape") {
                handleSettingsClose();
            }
        };

        window.addEventListener("keydown", handleEscKey);

        // Cleanup the event listener on component unmount
        return () => {
            window.removeEventListener("keydown", handleEscKey);
        };
    }, [navigate, setActive]);

    return (
        <Frame navigation={<SettingsLeftNav />} topBar={<SettingsHeader />}>
            <Box paddingBlockEnd={"20"}>
                <Outlet />
            </Box>
        </Frame>
    )
}
export default Settings