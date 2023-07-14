import { Frame, Toast, Box} from "@shopify/polaris"
import Header from "../../components/layouts/header/Headers"
import LeftNav from "../../components/layouts/leftnav/LeftNav"
import Store from "../../store";
import { useNavigate, Outlet } from "react-router-dom";
import { useEffect } from "react";
import homeFunctions from "./module";

function HomePage() {
  const navigate = useNavigate();
  const accessToken = Store(state => state.accessToken);
  const setAllCollections = Store(state => state.setAllCollections)

  const fetchAllCollections = async()=>{
    let apiCollections = await homeFunctions.getAllCollections()
    setAllCollections(apiCollections)
  }
  useEffect(() => {
    const access_token = accessToken
    if (!access_token) {
      navigate("/login")  
    } 
    else  {
      fetchAllCollections()
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
    url: '/dashboard',
    accessibilityLabel: 'Akto Icon',
  };

  return (
      <Frame navigation={<LeftNav />} topBar={<Header />} logo={logo} >
          <Outlet />
        {toastMarkup}
      </Frame>
  );
}

export default HomePage