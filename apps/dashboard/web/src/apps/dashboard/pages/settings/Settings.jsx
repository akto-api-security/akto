import { Card, Frame, Icon, Text } from "@shopify/polaris"
import { CancelMajor, SettingsMinor } from '@shopify/polaris-icons';
import { tokens } from "@shopify/polaris-tokens"
import { Outlet, useNavigate } from "react-router-dom"
import './settings.css'
import SettingsLeftNav from "./nav/SettingsLeftNav";
import homeFunctions from "../home/module";
import Store from "../../store";
import { useEffect } from "react";

const Settings = () => {
    const navigate = useNavigate();
    const setAllCollections = Store(state => state.setAllCollections)

    const fetchAllCollections = async()=>{
        let apiCollections = await homeFunctions.getAllCollections()
        setAllCollections(apiCollections)
    }

    useEffect(() => {
        fetchAllCollections()
    }, [])

    return (
        <Frame>
            <Card>
                <div style={{ display: "grid", gridTemplateColumns: "4vw auto 1vw"}}>
                    <Icon source={SettingsMinor} color="base" />
                    <Text variant="headingLg">
                        Settings
                    </Text>
                    <div onClick={() => navigate("/dashboard")}>
                        <Icon source={CancelMajor} color="base" />
                    </div>
                </div>
            </Card>

            <div style={{ background: tokens.color["color-bg-subdued"], display: "grid", gridTemplateColumns: "max-content auto" }}>
                <SettingsLeftNav />
                <div style={{ height: "100%" }}>
                    <Outlet />
                </div>
            </div>
        </Frame>
    )
}
export default Settings