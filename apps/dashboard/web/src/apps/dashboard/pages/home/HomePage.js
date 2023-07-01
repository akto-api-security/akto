import { Frame, Page, Toast } from "@shopify/polaris"
import Header from "../../components/layouts/Headers"
import LeftNav from "../../components/layouts/LeftNav"
import LayoutWithTabs from "../../components/layouts/LayoutWithTabs";
import { Outlet } from "react-router-dom";
import Store from "../../store";
import { useNavigate } from "react-router-dom";
import { useEffect } from "react";

function HomePage() {
  const navigate = useNavigate();
  const storeAccessToken = Store(state => state.storeAccessToken)

  useEffect(() => {
    const access_token = localStorage.getItem("access_token")

    if (!access_token) {
      console.log("navigate")
      navigate("/login")  
    } else  {
      storeAccessToken(access_token)
    }

  }, [])

  const toastConfig = Store(state => state.toastConfig)
  const setToastConfig = Store(state => state.setToastConfig)
  
  const disableToast = () => {
    setToastConfig({
      isActive: false,
      isError: false,
      message: ""
    })
  }

  const toastMarkup = toastConfig.isActive ? (
    <Toast content={toastConfig.message} error={toastConfig.isError} onDismiss={disableToast} duration={1500} />
  ) : null;

  const logo = {
    width: 124,
    topBarSource:
      '/public/akto_name_with_logo.svg',
    url: '#',
    accessibilityLabel: 'Akto Icon',
  };

  return (
    <Page fullWidth={true}>
      <Frame navigation={<LeftNav />} topBar={<Header />} logo={logo} >
        <Outlet />
        {toastMarkup}
      </Frame>
      {/* <LayoutWithTabs tabs={tabs} /> */}
    </Page>
  );
}

export default HomePage