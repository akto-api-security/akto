import { Spinner } from "@shopify/polaris"

const SpinnerCentered = (
    {height}
) => {

    const heightValue = height ? height : "50vh"

    return (
        <div style={{width: "100%", height: heightValue, display: "flex", alignItems: "center", justifyContent: "center"}}>
            <Spinner size="small"/>
        </div>
    )   
}

export default SpinnerCentered