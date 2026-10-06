import PageWithMultipleCards from "./layouts/PageWithMultipleCards";
import ContextualLayout from "./layouts/ContextualLayout";

function DetailsPage(props){

    const {pageTitle, saveAction, discardAction, isDisabled, components, backUrl, titleMetadata, subtitle, secondaryActions, isSaving } = props

    const pageMarkup = (
        <PageWithMultipleCards title={pageTitle}
            backUrl={backUrl}
            divider
            components={components}
            titleMetadata={titleMetadata}
            subtitle={subtitle}
            secondaryActions={secondaryActions}
        />
    )

    return (
        <ContextualLayout
            saveAction={saveAction}
            discardAction={discardAction}
            isDisabled={isDisabled}
            isSaving={isSaving}
            pageMarkup={pageMarkup}
        />
    )
}

export default DetailsPage