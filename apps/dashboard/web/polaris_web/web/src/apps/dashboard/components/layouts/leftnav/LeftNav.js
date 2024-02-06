import {Navigation, Text} from "@shopify/polaris"
import {SettingsFilledMinor,AppsFilledMajor, InventoryFilledMajor, MarketingFilledMinor, FileFilledMinor, AnalyticsFilledMinor, OrdersFilledMinor} from "@shopify/polaris-icons"
import {useLocation, useNavigate} from "react-router-dom"

import './LeftNav.css'
import PersistStore from "../../../../main/PersistStore"
import func from "@/util/func"

export default function LeftNav(){

  const navigate = useNavigate();
  const location = useLocation();
  

  const active = PersistStore((state) => state.active)
  const setActive = PersistStore((state) => state.setActive)

  

  const currPathString = func.transformString(location.pathname)

    const navigationMarkup = (
      <div className={active}>
        <Navigation location="/"> 
          <Navigation.Section
            items={[
                {
                  label: <Text variant="bodyMd" fontWeight="medium">Quick Start</Text>,
                  icon: AppsFilledMajor,
                  onClick: ()=>{
                    setActive("normal")
                    navigate("/dashboard/quick-start")
                  },
                  selected: currPathString === 'dashboard_quick_start',
                  key: '1',
                },
                {
                  label: 'Dashboard',
                  icon: OrdersFilledMinor,
                  onClick: ()=>{
                    navigate("/dashboard/home")
                    setActive("normal")
                  },
                  selected: currPathString === 'dashboard_home',
                  key: '2',
                },
                {   
                  url: '#',
                  label: <Text variant="bodyMd" fontWeight="medium" color={currPathString.includes("inventory") ? (active === 'active' ? "subdued" : ""): ""}>API Inventory</Text>,
                  icon: InventoryFilledMajor,
                  onClick: ()=>{
                    setActive("normal")
                  },
                  selected: currPathString.includes('dashboard_observe'),
                  subNavigationItems:[
                      {
                        label: 'API Collections',
                        onClick: ()=>{
                          navigate('/dashboard/observe/inventory')
                          setActive('active')
                        },
                        selected: currPathString === "dashboard_observe_inventory"
                      },
                      {
                        label: 'API Changes',
                        onClick: ()=>{
                          navigate('/dashboard/observe/changes')
                          setActive('active')
                        },
                        selected: currPathString === "dashboard_observe_changes"
                      },
                      {
                        label: 'Sensitive data',
                        onClick: ()=>{
                          navigate('/dashboard/observe/sensitive')
                          setActive('active')
                        },
                        selected: currPathString === "dashboard_observe_sensitive"
                      }
                    ],
                    key: '3',
                },
                {
                  url: '#',
                  label: <Text variant="bodyMd" fontWeight="medium" color={currPathString.includes("testing") ? (active === 'active' ? "subdued" : ""): ""}>Testing</Text>,
                  icon: MarketingFilledMinor,
                  onClick: ()=>{
                      setActive("normal")
                  },
                  selected: currPathString.includes('_testing'),
                  subNavigationItems:[
                    {
                      label: 'Results',
                      onClick: ()=>{
                        navigate('/dashboard/testing')
                        setActive('active')
                      },
                      selected: currPathString === 'dashboard_testing'
                    },
                    {
                      label: 'Test roles',
                      onClick: ()=>{
                        navigate('/dashboard/testing/roles')
                        setActive('active')
                      },
                      selected: currPathString === 'dashboard_testing_roles'
                    },
                    {
                      label: 'User config',
                      onClick: ()=>{
                        navigate('/dashboard/testing/user-config')
                        setActive('active')
                      },
                      selected: currPathString === 'dashboard_testing_user_config'
                    }
                  ],
                  key: '4',
                },
                {
                  label: <Text variant="bodyMd" fontWeight="medium">Test Editor</Text>,
                  icon: FileFilledMinor,
                  onClick: ()=>{ 
                    navigate("/dashboard/test-editor/REMOVE_TOKENS")
                    setActive("normal")
                  },
                  selected: currPathString.includes("dashboard_test_editor"),
                  key: '5',
                },
                {
                  label: <Text variant="bodyMd" fontWeight="medium">Issues</Text>,
                  icon: AnalyticsFilledMinor,
                  onClick: ()=>{ 
                      navigate("/dashboard/issues")
                      setActive("normal")
                    },
                    selected: currPathString === 'dashboard_issues',
                    key: '6',
                },
              ]}
          />
          <Navigation.Section 
               items={[
                {
                  label:<Text variant="bodyMd" fontWeight="medium">Settings</Text>,
                  icon: SettingsFilledMinor,
                  onClick: ()=>{
                    navigate("/dashboard/settings/users")
                    setActive("normal")
                  },
                  selected: currPathString === 'settings',
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