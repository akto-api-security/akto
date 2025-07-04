import { Button, FormLayout, HorizontalStack, Icon, Text, TextField, Tooltip } from "@shopify/polaris"
import { InfoMinor } from "@shopify/polaris-icons"
import { useState } from "react";
import api from "../api"
import Store from "../../../store";
import { useEffect } from "react";
import TestingStore from "../testingStore";
import AuthParams from './AuthParams';

function SampleDataAuth({showOnlyApi, extractInformation, setInformation}) {

    const authMechanism = TestingStore(state => state.authMechanism)
    const [authParams, setAuthParams] = useState([{
        key: "",
        value: "",
        where: "HEADER",
        showHeader: true
    }])

    useEffect(() => {
        if (authMechanism && authMechanism?.type.toUpperCase() === "SAMPLE_DATA") {
            setAuthParams(authMechanism.authParams)
        }
    }, [authMechanism])

    useEffect(()=> {
        if(extractInformation){
            setInformation({authParams})
        }else{
            return ;
        }
    },[authParams])

    return (
        <div>
            <Text variant="headingMd">Use tokens from traffic</Text>
            <br />
            <AuthParams authParams={authParams} setAuthParams={setAuthParams} hideTitle={true} />
            <br />
        </div>
    )
}

export default SampleDataAuth