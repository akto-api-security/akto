import { Page, VerticalStack } from "@shopify/polaris";

const PageWithMultipleCards = (props) => {

    return (
        <Page fullWidth
            title={props?.title}
            backAction={props?.backAction}
            primaryAction={props?.primaryAction}
            secondaryActions={props?.secondaryActions}
            divider={props.divider}
        >
            <VerticalStack gap="4">
                {props?.components?.filter((component) => {
                    return component
                })}
            </VerticalStack>
        </Page>
    )
}

export default PageWithMultipleCards