import { useNavigate, useLocation } from "react-router-dom";
import PageWithMultipleCards from "./layouts/PageWithMultipleCards";
import ContextualLayout from "./layouts/ContextualLayout";

function DetailsPage(props){

    const {pageTitle, saveAction, discardAction, isDisabled, components } = props

    const location = useLocation();
    const navigate = useNavigate()
    const isNewTab = location.key=='default' || !(window.history.state && window.history.state.idx > 0)

    const navigateBack = () => {
        navigate(-1)
    }
    const pageMarkup = (
        <PageWithMultipleCards title={pageTitle}
            backAction={isNewTab ? null : { onAction: navigateBack }}
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