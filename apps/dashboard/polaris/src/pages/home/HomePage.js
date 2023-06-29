import {Frame, Page} from "@shopify/polaris"
import Header from "../../components/layouts/Header" 
import LeftNav from "../../components/layouts/LeftNav"  
import LayoutWithTabs from "../../components/layouts/LayoutWithTabs";

function HomePage(){
      const logo = {
        width: 124,
        topBarSource:'',
        url: '#',
        accessibilityLabel: 'Akto Icon',
      };
    return(
        <Page>
            <Frame navigation={<LeftNav />} topBar={<Header />} logo={logo}  />
            {/* <LayoutWithTabs tabs={tabs} /> */}
        </Page>
    );
}

export default HomePage