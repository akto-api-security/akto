import { Frame, Box } from "@shopify/polaris"
import Header from "../../components/layouts/header/Headers"
import LeftNav from "../../components/layouts/leftnav/LeftNav"
import Store from "../../store";
import { Outlet } from "react-router-dom";
import PersistStore from "../../../main/PersistStore";

function HomePage() {

  const leftNavCollapsed = Store(state => state.leftNavCollapsed)
  const dashboardCategory = PersistStore((state) => state.dashboardCategory) || "API Security";

  const logo = {
    width: 78,
    topBarSource:
      dashboardCategory === "Agentic Security" ? 'White%20logo.svg' : 'akto_name_with_logo.svg',
    url: '/dashboard/observe/inventory',
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