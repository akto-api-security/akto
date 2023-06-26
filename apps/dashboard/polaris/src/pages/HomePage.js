import {Frame, Layout, Navigation, Page} from "@shopify/polaris"
import {HomeMinor,OrdersMinor, CustomersMinor,AnalyticsMinor,MarketingMinor,DiscountsMinor} from "@shopify/polaris-icons"
import Header from "../components/Header"

function HomePage(){
    const navigationMarkup = (
        <Navigation location="/">
          <Navigation.Section
            items={[
                {
                  url: '#',
                  excludePaths: ['#'],
                  label: 'Home',
                  icon: HomeMinor,
                },
                {
                  url: '#',
                  label: 'Testing',
                  icon: OrdersMinor,
                  selected: true,
                  subNavigationItems: [
                    {
                      url: '#',
                      excludePaths: ['#'],
                      disabled: false,
                      label: 'Run Tests',
                    },
                    {
                      url: '#',
                      excludePaths: ['#'],
                      disabled: false,
                      label: 'Test Results',
                      badge: 11,
                    },
                  ],
                },
                {
                    url: '#',
                    excludePaths: ['#'],
                    label: 'Observe',
                    icon: CustomersMinor,
                },
                {
                    url: '#',
                    excludePaths: ['#'],
                    label: 'Issues',
                    icon: AnalyticsMinor,
                },
                {
                    url: '#',
                    excludePaths: ['#'],
                    label: 'Tests Library',
                    icon: MarketingMinor,
                },
                {
                    url: '#',
                    excludePaths: ['#'],
                    label: 'Test Editor',
                    icon: DiscountsMinor,
                  },
              ]}
          />
        </Navigation>
      );
    return(
        <Page>
            <Header/>
            <Frame navigation={navigationMarkup} />
        </Page>
    );
}

export default HomePage