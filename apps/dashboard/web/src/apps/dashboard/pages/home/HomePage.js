import {Frame, Page} from "@shopify/polaris"
import { Outlet } from "react-router-dom";
import Header from "../../components/layouts/Headers" 
import LeftNav from "../../components/layouts/LeftNav"  
import LayoutWithTabs from "../../components/layouts/LayoutWithTabs";

function HomePage(){
    const logo = {
      width: 124,
      topBarSource:
        '/public/akto_name_with_logo.svg',
      url: '#',
      accessibilityLabel: 'Akto Icon',
    };
    return(
        <Page fullWidth={true}>
            <Frame navigation={<LeftNav />} topBar={<Header />} logo={logo} >
            <Outlet/>
            </Frame>
            {/* <LayoutWithTabs tabs={tabs} /> */}
        </Page>
    );
}

export default HomePage