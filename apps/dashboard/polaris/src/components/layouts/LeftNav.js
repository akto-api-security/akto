import {Icon, Navigation, Tooltip} from "@shopify/polaris"
import {HomeMinor,OrdersMinor, CustomersMinor,AnalyticsMinor,DiscountsMinor,ProductsMinor} from "@shopify/polaris-icons"
import {useState,useCallback} from "react"
import {useNavigate} from "react-router-dom"

export default function LeftNav(){

  const [selected, setSelected] = useState('');
  const [collapse, setCollapse] = useState(true);
  const navigate = useNavigate();


  const handleSelect = (selectedId) => {
    setSelected(selected => selected === selectedId ? null : selectedId);
  };

  const toggleCollapse = useCallback(
    () => setCollapse((collapse) => !collapse),
    [],
  );

    const navigationMarkup = (
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
                    if(collapse){
                      toggleCollapse()
                    }else{
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
                    if(collapse){
                      toggleCollapse()
                    }else{
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
                    if(collapse){
                      toggleCollapse()
                    }else{
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
                    if(collapse){
                      toggleCollapse()
                    }
                    handleSelect('testing')
                  },
                  selected: selected === 'testing',
                  subNavigationItems:[
                    {
                      label: 'Create Tests',
                      onClick: ()=>{
                        navigate('/tests/create')
                      }
                    },
                    {
                      label: 'Results',
                      onClick: ()=>{
                        navigate('/tests/result')
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
                    if(collapse){
                      toggleCollapse()
                    }else{
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
                      if(collapse){
                        toggleCollapse()
                      }else{
                        handleSelect("issues")
                        navigate("/dashboard/issues")
                      }
                    },
                    selected: selected === 'issues',
                    key: '6',
                },
              ]}
          />
        </Navigation>
      );

    return(
        navigationMarkup
    )
}