import {Icon, Navigation, Tooltip} from "@shopify/polaris"
import {HomeMinor,OrdersMinor, CustomersMinor,AnalyticsMinor,DiscountsMinor,ProductsMinor,SettingsMinor} from "@shopify/polaris-icons"
import {useState} from "react"
import {useNavigate} from "react-router-dom"

import './LeftNav.css'
import Store from "../../../store"

export default function LeftNav(){

  const [selected, setSelected] = useState('');
  const navigate = useNavigate();
  const collapse = Store((state) => state.hideFullNav)


  const handleSelect = (selectedId) => {
    setSelected(selected => selected === selectedId ? null : selectedId);
  };

    const navigationMarkup = (
      <div className={collapse ? 'collapse' : ''}>
        <Navigation location="/"> 
          <Navigation.Section
            items={[
                {
                  label: collapse? (
                    <Tooltip content="Quick Start" preferredPosition="bottom" dismissOnMouseOut>
                      <Icon source={HomeMinor} />
                    </Tooltip>
                  ) : 'Quick Start',
                  icon: collapse ? '' : HomeMinor,
                  onClick: ()=>{
                    if(!collapse){
                      handleSelect("quick_start")
                      navigate("/dashboard/quick-start")
                    }
                  },
                  selected: selected === 'quick_start',
                  key: '1',
                },
                {
                  label: collapse ? (
                    <Tooltip content="Dashboard" preferredPosition="bottom" dismissOnMouseOut>
                      <Icon source={OrdersMinor} />
                    </Tooltip>
                  ) : 'Dashboard',
                  icon: collapse ? '' : OrdersMinor,
                  onClick: ()=>{
                    if(!collapse){
                      handleSelect("dashboard")
                      navigate("/dashboard")
                    }
                  },
                  selected: selected === 'dashboard',
                  key: '2',
                },
                {   
                  label: collapse? (
                    <Tooltip content="API Inventory" preferredPosition="bottom" dismissOnMouseOut>
                      <Icon source={ProductsMinor} />
                    </Tooltip>
                  ) : 'API Inventory',
                  icon: collapse ? '' : ProductsMinor,
                  onClick: ()=>{
                    if(!collapse){
                        handleSelect("inventory")
                        navigate("/dashboard/observe/inventory")
                      }
                    },
                    selected: selected === 'inventory',
                    key: '3',
                },
                {
                  url: '#',
                  label: collapse? (
                    <Tooltip content="Testing" preferredPosition="bottom" dismissOnMouseOut>
                      <Icon source={CustomersMinor} />
                    </Tooltip>
                  ) : 'Testing',
                  icon: collapse ? '' : CustomersMinor,
                  onClick: ()=>{
                    if(!collapse){
                      handleSelect('testing')
                    }
                  },
                  selected: selected === 'testing',
                  subNavigationItems:[
                    {
                      label: 'Create Tests',
                      onClick: ()=>{
                        navigate('/dashboard/testing')
                      }
                    },
                    {
                      label: 'Results',
                      onClick: ()=>{
                        navigate('/dashboard/testing')
                      }
                    }
                  ],
                  key: '4',
                },
                {
                  label: collapse? (
                    <Tooltip content="Test Editor" preferredPosition="bottom" dismissOnMouseOut>
                      <Icon source={DiscountsMinor} />
                    </Tooltip>
                  ) : 'Test Editor',
                  icon: collapse ? '' : DiscountsMinor,
                  onClick: ()=>{
                    if(!collapse){
                      handleSelect("test-editor")
                      navigate("/dashboard/test-editor")
                    }
                  },
                  selected: selected === 'test-editor',
                  key: '5',
                },
                {
                  label: collapse? (
                      <Tooltip content="Issues" preferredPosition="bottom" dismissOnMouseOut>
                        <Icon source={AnalyticsMinor} />
                      </Tooltip>
                    ) : 'Issues',
                  icon: collapse ? '' : AnalyticsMinor,
                  onClick: ()=>{
                      if(!collapse){
                        handleSelect("issues")
                        navigate("/dashboard/issues")
                      }
                    },
                    selected: selected === 'issues',
                    key: '6',
                },
              ]}
          />
          <Navigation.Section 
              items={[
                {
                  url: '#',
                  label: collapse? (
                    <Tooltip content="Settings" preferredPosition="bottom" dismissOnMouseOut>
                      <Icon source={SettingsMinor} />
                    </Tooltip>
                  ) : 'Settings',
                  icon: collapse ? '' : SettingsMinor,
                  onClick: ()=>{
                    if(!collapse){
                      handleSelect("settings")
                    }
                  },
                  selected: selected === 'settings',
                  subNavigationItems:[
                    {
                      label: 'Accounts',
                      onClick: ()=>{
                        navigate('/settings/accounts')
                      }
                    },
                    {
                      label: 'Users',
                      onClick: ()=>{
                        navigate('/settings/Users')
                      }
                    },
                    {
                      label: 'Alerts',
                      onClick: ()=>{
                        navigate('/settings/alerts')
                      }
                    },
                    {
                      label: 'CI/CD',
                      onClick: ()=>{
                        navigate('/settings/ci-cd')
                      }
                    },
                    {
                      label: 'Integrations',
                      onClick: ()=>{
                        navigate('/settings/integrations')
                      }
                    },
                    {
                      label: 'Health',
                      onClick: ()=>{
                        navigate('/settings/health')
                      }
                    },
                    {
                      label: 'Metrics',
                      onClick: ()=>{
                        navigate('/settings/metrics')
                      }
                    }
                  ],
                  key: '7',
                }
              ]}
          />
        </Navigation>
        </div>
      );

    return(
        navigationMarkup
    )
}