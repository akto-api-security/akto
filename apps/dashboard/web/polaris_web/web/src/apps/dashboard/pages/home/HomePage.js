import { Frame, Box } from "@shopify/polaris"
import Header from "../../components/layouts/header/Headers"
import LeftNav from "../../components/layouts/leftnav/LeftNav"
import Store from "../../store";
import { Outlet } from "react-router-dom";

function HomePage() {

  const leftNavCollapsed = Store(state => state.leftNavCollapsed)

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
    </Frame>
  );
}

export default HomePage