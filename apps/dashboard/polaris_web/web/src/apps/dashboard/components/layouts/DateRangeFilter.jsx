import { Button, Popover } from "@shopify/polaris";
import { useState } from "react";
import { CalendarMinor } from "@shopify/polaris-icons"
import DateRangePicker from "./DateRangePicker";
import values from "../../../../util/values";

function DateRangeFilter(props){

    const [popoverActive, setPopoverActive] = useState(false);
    const [buttonValue, setButtonValue] = useState(values.ranges[0].title);
    const handleDate = (dateRange) =>{
        props.getDate(dateRange)
    }
    
    const handlePopoverState = (popoverState) =>{
        setPopoverActive(popoverState)
    }

    const handleButtonValue = (buttonValue) => {
        setButtonValue(buttonValue)
    }
    
    return (
        <Popover
        active={popoverActive}
        autofocusTarget="none"
        preferredAlignment="left"
        preferredPosition="below"
        fluidContent
        sectioned={false}
        fullHeight
        activator={
          <Button
            icon={CalendarMinor}
            onClick={() => setPopoverActive(!popoverActive)}
          >
            {buttonValue}
          </Button>
        }
        onClose={() => setPopoverActive(false)}
      >
        <DateRangePicker 
            ranges={values.ranges}
            getDate={handleDate} 
            setPopoverState={handlePopoverState} 
            setButtonValue={handleButtonValue}/>
        </Popover>
    )

}

export default DateRangeFilter