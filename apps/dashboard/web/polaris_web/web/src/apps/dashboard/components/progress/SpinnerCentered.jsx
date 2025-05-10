import { Spinner } from "@shopify/polaris"

const SpinnerCentered = () => {

    return (
        <div style={{width: "100%", height: "50vh", display: "flex", alignItems: "center", justifyContent: "center"}}>
            <Spinner size="small"/>
        </div>
    )   
}

export default SpinnerCentered