import { CalloutCard, HorizontalStack, ProgressBar, VerticalStack } from "@shopify/polaris";
import PersistStore from "../../../main/PersistStore";
import { useNavigate } from "react-router-dom";
import "./style.css"

const UsageType = {
    INVENTORY: "inventory",
    TESTING: "testing"
}

function UsageBar(props) {

    const { title, content, usageType } = props
    const navigate = useNavigate()

    const showUsageBanner = PersistStore(state => state.showUsageBanner);
    const setShowUsageBanner = PersistStore(state => state.setShowUsageBanner);

    let featureWiseAllowed = window.STIGG_FEATURE_WISE_ALLOWED

    let featureMap = null

    if (usageType === UsageType.INVENTORY) {
        featureMap = featureWiseAllowed.ACTIVE_ENDPOINTS
    } else if (usageType === UsageType.TESTING) {
        featureMap = featureWiseAllowed.TEST_RUNS
    }

    if (featureMap == null || featureMap == undefined) {
        return <></>
    }

    if (featureMap.usageLimit == -1) {
        return <></>
    }

    let usagePercentage = (featureMap.usage / featureMap.usageLimit) * 100
    let dismissible = false
    if (usagePercentage < 80) {
        dismissible = true
    }

    if (usagePercentage >= 95) {
        setShowUsageBanner({ ...showUsageBanner, [usageType]: true })
    }

    if (!showUsageBanner[usageType] && usagePercentage < 80) {
        return <></>
    }

    return <div className="usage-bar">
        <CalloutCard
            title={title}
            primaryAction={{
                id: "primary-action",
                content: "Upgrade plan",
                onAction: () => { navigate("/dashboard/settings/billing") }
            }}
            secondaryAction={{
                id: "secondary-action",
                content: "Contact us",
                onAction: () => { window.open("https://www.akto.io/contact-us") }
            }}
            {...(dismissible ? {
                onDismiss: () => {
                    setShowUsageBanner({ ...showUsageBanner, [usageType]: false })
                }
            } : {})}
        >
            <VerticalStack gap={4}>
                {content}
                <HorizontalStack gap={4} align="start" blockAlign="center">
                    <div style={{ width: "90%" }}>
                        <ProgressBar progress={usagePercentage} color={usagePercentage < 95 ? 'primary' : 'critical'} />
                    </div>
                    {`${Math.trunc(usagePercentage)} %`}
                </HorizontalStack>
            </VerticalStack>
        </CalloutCard>
    </div>

}

export { UsageBar, UsageType };