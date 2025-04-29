import { Frame, Icon, Text, Box, TopBar, HorizontalStack } from "@shopify/polaris"
import { CancelMajor, SettingsMinor } from '@shopify/polaris-icons';
import { Outlet, useNavigate } from "react-router-dom"
import './settings.css'
import SettingsLeftNav from "./nav/SettingsLeftNav";
import { useEffect } from "react";

function SettingsHeader({ onHandleClose }) {
    const buttonComp = (
        <div className="header-css">
            <HorizontalStack gap="2">
                <Box>
                    <Icon source={SettingsMinor}/>
                </Box>
                <Text variant="headingMd" as="h4">Settings</Text>
            </HorizontalStack>
            <button className="Polaris-Modal-CloseButton" onClick={() => onHandleClose()}><Box><Icon source={CancelMajor} /></Box></button>
        </div>
    )

    return (
        <TopBar secondaryMenu={buttonComp} />
    )
}

const Settings = () => {
    const navigate = useNavigate();

    const handleSettingsClose = () => {
        navigate("/dashboard/observe/inventory");
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
    }, []);

    return (
        <Frame navigation={<SettingsLeftNav />} topBar={<SettingsHeader onHandleClose={handleSettingsClose} />}>
            <Box paddingBlockEnd={"20"}>
                <Outlet />
            </Box>
        </Frame>
    )
}
export default Settings