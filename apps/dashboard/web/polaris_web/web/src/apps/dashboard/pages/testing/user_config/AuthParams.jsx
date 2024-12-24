import { Button, Card, Divider, LegacyCard, TextField , Text} from "@shopify/polaris";
import { DeleteMajor, CirclePlusMajor } from "@shopify/polaris-icons"
import { useEffect } from "react";
import Dropdown from "../../../components/layouts/Dropdown";
import Store from "../../../store";


function AuthParams({ authParams, setAuthParams, hideTitle }) {

    const setToastConfig = Store(state => state.setToastConfig)

    const authParamOptions = [
        { label: 'Header', value: 'HEADER' },
        { label: 'Body', value: 'BODY' }
    ]

    useEffect(() => {
        //load existing auth params
    }, [])

    function handleUpdate(targetIndex, field, value) {
        setAuthParams(prev => {
            return prev.map((authParam, index) => {
                if (index === targetIndex) {
                    return { ...authParam, [field]: value}
                } else 
                    return authParam
            })
        })
    }

    function handleAdd() {
        setAuthParams(prev => {
            return [ ...prev, {
                key: "",
                value: "",
                where: "HEADER",
                showHeader: true
            }]
        })
    }

    function handleRemove(removeIndex) {
        if (authParams.length === 1) {
            setToastConfig({ isActive: true, isError: true, message: "Atleast one Auth Param required" })
        } else {
            setAuthParams(prev => prev.filter(((authParam, index) => index !== removeIndex )))
        }
    }

    return (
        <LegacyCard title={hideTitle ? '' : "Extract"}>
            <br />
            <Divider />
            <LegacyCard.Section>
                <div>
                    {authParams.map((authParam, index) => {
                        return (
                            <div key={index} >
                                <div style={{ display: "grid", gridTemplateColumns: "auto max-content auto max-content auto max-content", gap: "30px", alignItems: "center"}}>
                                    <Dropdown
                                        id={"auth-param-menu"}
                                        menuItems={authParamOptions} initial={authParam.where}
                                        selected={(authParamLocation) => handleUpdate(index, "where", authParamLocation)} />
                                    <Text variant="bodyMd">Key: </Text>
                                    <TextField id={`auth-param-key-${index}`} value={authParam.key} onChange={(key) => handleUpdate(index, "key", key)} />
                                    <Text variant="bodyMd">Value: </Text>
                                    <TextField id={`auth-param-value-${index}`} value={authParam.value} onChange={(value) => handleUpdate(index, "value", value)} />
                                    <Button id={`delete-auth-param-${index}`} icon={DeleteMajor} onClick={() => handleRemove(index)} plain />
                                </div>
                                { index < authParams.length - 1 &&  <br /> }
                            </div>
                    )})}
                </div>
                <br />
                <Button id={"add-auth-param"} icon={CirclePlusMajor} onClick={handleAdd} plain />
            </LegacyCard.Section>
        </LegacyCard>
    )
}

export default AuthParams