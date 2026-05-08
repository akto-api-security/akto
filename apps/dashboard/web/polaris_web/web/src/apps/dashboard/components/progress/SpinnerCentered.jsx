import { Spinner, Text } from "@shopify/polaris"

const SpinnerCentered = (
    {height, text}
) => {

    const heightValue = height ? height : "50vh"

    return (
        <div style={{width: "100%", height: heightValue, display: "flex", alignItems: "center", justifyContent: "center", gap: "8px"}}>
            <Spinner size="small"/>
            {text ? <Text variant="bodyMd" color="subdued">{text}</Text> : null}
        </div>
    )   
}

export default SpinnerCentered