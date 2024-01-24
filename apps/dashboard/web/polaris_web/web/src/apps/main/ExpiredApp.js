import CenteredCard from "../dashboard/components/shared/CenteredCard";

function ExpiredApp() {
    const message = "Please upgrade to the latest version to continue using akto. For any queries contact support@akto.io."
    return (
        <CenteredCard message={message} />
    )
}

export default ExpiredApp;