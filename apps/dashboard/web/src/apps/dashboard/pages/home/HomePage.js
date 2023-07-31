import { Frame, Toast, Box } from "@shopify/polaris"
import Header from "../../components/layouts/header/Headers"
import LeftNav from "../../components/layouts/leftnav/LeftNav"
import Store from "../../store";
import { useLocation, useNavigate, Outlet } from "react-router-dom";
import { useEffect } from "react";
import homeFunctions from "./module";
import {history} from "@/util/history"

function HomePage() {

  history.location = useLocation();
  history.navigate = useNavigate();

  const setAllCollections = Store(state => state.setAllCollections)
  const setUsername = Store(state => state.setUsername);
  const fetchAllCollections = async () => {
    let apiCollections = await homeFunctions.getAllCollections()
    setAllCollections(apiCollections)
  }
  useEffect(() => {
    fetchAllCollections()
    setUsername(window.USER_NAME)
  }, [])

  const toastConfig = Store(state => state.toastConfig)
  const setToastConfig = Store(state => state.setToastConfig)
  const leftNavCollapsed = Store(state => state.leftNavCollapsed)

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
    url: '/dashboard',
    accessibilityLabel: 'Akto Icon',
  };

  return (
    <Frame navigation={leftNavCollapsed? undefined:<LeftNav />} topBar={<Header />} logo={logo} >
     <Box paddingBlockEnd={"20"}>
      <Outlet />
     </Box>
      {toastMarkup}
    </Frame>
  );
}

export default HomePage