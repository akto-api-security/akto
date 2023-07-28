import { useNavigate } from "react-router-dom";
import PageWithMultipleCards from "./layouts/PageWithMultipleCards";
import ContextualLayout from "./layouts/ContextualLayout";

function DetailsPage(props){

    const {pageTitle, saveAction, discardAction, isDisabled, components } = props

    const navigate = useNavigate()

    const navigateBack = () => {
        navigate(-1)
    }
    const pageMarkup = (
        <PageWithMultipleCards title={pageTitle}
            backAction={{ onAction: navigateBack }}
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