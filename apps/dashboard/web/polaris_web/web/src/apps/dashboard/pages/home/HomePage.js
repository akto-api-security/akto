import { Frame, Box } from "@shopify/polaris"
import Header from "../../components/layouts/header/Headers"
import LeftNav from "../../components/layouts/leftnav/LeftNav"
import Store from "../../store";
import { Outlet } from "react-router-dom";
import PersistStore from "../../../main/PersistStore";
import { CATEGORY_AGENTIC_SECURITY } from "../../../main/labelHelper";

function HomePage() {

  const leftNavCollapsed = Store(state => state.leftNavCollapsed)
  const dashboardCategory = PersistStore((state) => state.dashboardCategory) || "API Security";

  const logoUrl = dashboardCategory === CATEGORY_AGENTIC_SECURITY
    ? '/dashboard/observe/agentic-assets'
    : '/dashboard/observe/inventory';

  const logo = {
    width: 78,
    topBarSource:
      dashboardCategory === "Agentic Security" ? '/public/white_logo.svg' : '/public/akto_name_with_logo.svg',
    url: logoUrl,
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