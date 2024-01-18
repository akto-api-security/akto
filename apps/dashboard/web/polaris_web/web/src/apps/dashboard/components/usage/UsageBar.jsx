import { HorizontalStack, ProgressBar, VerticalStack, Card, Button, Text, Icon, Box } from "@shopify/polaris";
import PersistStore from "../../../main/PersistStore";
import { useNavigate } from "react-router-dom";
import "./style.css"
import { CancelMinor } from '@shopify/polaris-icons';

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

    if (featureMap == null ||
        featureMap == undefined ||
        featureMap?.usageLimit == -1) {
        return <></>
    }

    let usagePercentage = (featureMap.usage / featureMap.usageLimit) * 100
    let dismissible = false

    const usageThreshold = 95;

    if (usagePercentage < usageThreshold) {
        dismissible = true
    }

    if (!showUsageBanner[usageType] && usagePercentage < usageThreshold) {
        return <></>
    }

    return (
        <Card>
            <VerticalStack gap={4}>

                <HorizontalStack align={"space-between"}>
                    <Text as="h2" variant="headingSm">
                        {title}
                    </Text>
                    {
                        dismissible ? <Button
                            plain
                            monochrome
                            onClick={() => { setShowUsageBanner({ ...showUsageBanner, [usageType]: false }) }}
                            accessibilityLabel="Close"
                        >
                            <Icon source={CancelMinor} />
                        </Button> : <></>
                    }
                </HorizontalStack>
                {content}
                <div style={{ "display": "flex", "justifyContent": "space-between", "alignItems": "center" }}>
                    <div style={{ "flex": "1" }} className={dismissible ? "" : "critical-usage-bar"}>
                        <ProgressBar
                            progress={usagePercentage}
                            size={"small"}
                            color={dismissible ? 'primary' : 'critical'}
                        />
                    </div>
                    <div style={{ "flex": "0", "textAlign": "end", "marginLeft": "12px" }}>
                        {`${Math.trunc(usagePercentage)}%`}
                    </div>
                </div>
                <HorizontalStack gap={"4"}>
                    <Box
                        {...(dismissible ? {} : { borderColor: "border-critical" })}
                        borderRadius={"1"}
                        borderWidth={"1"}
                        paddingBlockEnd={"2"}
                        paddingBlockStart={"2"}
                        paddingInlineEnd={"4"}
                        paddingInlineStart={"4"}
                    >
                        <Text
                            fontWeight={"semibold"}
                            {...(dismissible ? {} : { color: "critical" })}
                        >
                            <Button
                                plain
                                monochrome
                                removeUnderline
                                onClick={() => {
                                    setShowUsageBanner({ ...showUsageBanner, [usageType]: true });
                                    navigate("/dashboard/settings/billing")
                                }}
                            >
                                Upgrade plan
                            </Button>
                        </Text>
                    </Box>
                    <Button
                        plain
                        removeUnderline
                        onClick={() => {
                            setShowUsageBanner({ ...showUsageBanner, [usageType]: true });
                            window.open("https://www.akto.io/contact-us")
                        }}
                    >
                        Contact us
                    </Button>
                </HorizontalStack>
            </VerticalStack>
        </Card>
    )
}

export { UsageBar, UsageType };