import { Card } from "@shopify/polaris"

function FeatureWrapper(props) {

    const { children, featureLabel, skipCheck } = props

    let featureWiseAllowed = window.STIGG_FEATURE_WISE_ALLOWED

    if( skipCheck ) {
        return (
            <div>
                {children}
            </div>
        )
    }

    if (!featureWiseAllowed?.[featureLabel]?.isGranted) {
        return (
            <Card>
                This feature is not available in your plan. Please upgrade your plan to use this feature.
            </Card>
        )
    } else if (featureWiseAllowed?.[featureLabel]?.isOverageAfterGrace) {
        return (
            <Card>
                You have exceeded the limit of this feature. Please upgrade your plan to use this feature.
            </Card>
        )
    }

    return (
        <div>
            {children}
        </div>
    )
}

export default FeatureWrapper
