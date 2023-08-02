import PageWithMultipleCards from "./layouts/PageWithMultipleCards";
import ContextualLayout from "./layouts/ContextualLayout";

function DetailsPage(props){

    const {pageTitle, saveAction, discardAction, isDisabled, components } = props

    const pageMarkup = (
        <PageWithMultipleCards title={pageTitle}
            divider
            components={components}
        />
    )

    return (
        <ContextualLayout
            saveAction={saveAction}
            discardAction={discardAction}
            isDisabled={isDisabled}
            pageMarkup={pageMarkup}
        />
    )
}

export default DetailsPage