import { Card } from "@shopify/polaris";
function CenteredCard(props) {
    const { message } = props
    return (
        <div style={{
            display: "flex",
            justifyContent: "center",
            alignItems: "center",
            height: "100vh"
        }}>
            <Card
                padding={"4"}>
                {message}
            </Card>
        </div>
    )
}
export default CenteredCard;